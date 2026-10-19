package com.tdater.blackbox.core.integrations

import com.tdater.blackbox.core.model.TimelineEvent
import com.tdater.blackbox.core.model.TimelineType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Structured event logs exported by apps Travis owns (KITT, RetroTV, ...). Import-only: BLACKBOX never
 * listens for events. Content is untrusted data; it is parsed, bounded and rendered, never interpreted.
 * Format: docs/EVENT_FORMAT.md.
 */
class EventImportException(message: String) : Exception(message)

data class EventImportLimits(
    val maxChars: Int = 4_000_000,
    val maxEvents: Int = 50_000,
    val maxFieldChars: Int = 4_000,
    /** Rendered text is stored in the database row; the full file stays in the attachment. */
    val maxRenderedChars: Int = 256_000,
    val maxTimelineEvents: Int = 200,
)

data class ImportedEvent(
    val index: Int,
    /** Epoch millis, or null when the source time was missing or had no time zone. */
    val timestamp: Long?,
    val rawTime: String?,
    val level: String?,
    val type: String,
    val message: String,
    val data: String?,
    val mark: Boolean,
) {
    val notable: Boolean get() = mark || level?.uppercase() in NOTABLE_LEVELS

    companion object {
        val NOTABLE_LEVELS = setOf("WARN", "WARNING", "ERROR", "FATAL", "CRITICAL", "ASSERT")
    }
}

data class EventLog(
    val app: String?,
    val appVersion: String?,
    val device: String?,
    val session: String?,
    val events: List<ImportedEvent>,
    val warnings: List<String>,
)

/** What the repository writes: one LOG evidence record plus timeline entries for notable events. */
data class EventImportPlan(
    val title: String,
    val text: String,
    val originalTimestamp: Long?,
    val labels: List<String>,
    val timeline: List<TimelineEvent>,
    val notableSkipped: Int,
)

object EventLogParser {
    const val FORMAT = "blackbox-events"
    const val VERSION = 1
    private val json = Json { isLenient = false }

    fun parse(text: String, limits: EventImportLimits = EventImportLimits()): EventLog {
        if (text.length > limits.maxChars) throw EventImportException("Event log is larger than ${limits.maxChars / 1_000_000} MB of text.")
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) throw EventImportException("The file is empty.")
        val whole = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        return when {
            whole is JsonObject && "events" in whole -> parseDocument(whole, limits)
            whole is JsonArray -> parseEvents(whole.toList(), null, limits, mutableListOf()).let {
                EventLog(null, null, null, null, it, warnings(it))
            }
            else -> parseJsonLines(trimmed, limits)
        }
    }

    private fun parseDocument(o: JsonObject, limits: EventImportLimits): EventLog {
        val format = o.str("format")
        if (format != null && format != FORMAT) throw EventImportException("Unrecognized format \"${format.take(60)}\" (expected \"$FORMAT\").")
        val version = (o["version"] as? JsonPrimitive)?.longOrNull
        if (version != null && version > VERSION) throw EventImportException("Event log version $version is newer than this app supports ($VERSION).")
        val zone = o.str("timezone")?.let { z ->
            runCatching { ZoneId.of(z) }.getOrElse { throw EventImportException("Unknown time zone \"${z.take(60)}\".") }
        }
        val arr = o["events"] as? JsonArray ?: throw EventImportException("\"events\" must be an array.")
        val warnings = mutableListOf<String>()
        val events = parseEvents(arr.toList(), zone, limits, warnings)
        return EventLog(o.str("app")?.clip(200), o.str("appVersion")?.clip(100), o.str("device")?.clip(200),
            o.str("session")?.clip(200), events, warnings + warnings(events))
    }

    private fun parseJsonLines(text: String, limits: EventImportLimits): EventLog {
        val warnings = mutableListOf<String>()
        val elements = mutableListOf<JsonElement>()
        var bad = 0
        text.lineSequence().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val el = runCatching { json.parseToJsonElement(line) }.getOrNull()
            if (el is JsonObject) elements += el
            else if (++bad <= 5) warnings += "Line ${i + 1} is not a JSON object; skipped."
        }
        if (elements.isEmpty()) throw EventImportException("Not a BLACKBOX event log: expected a JSON document with \"events\" or one JSON object per line.")
        if (bad > 5) warnings += "${bad - 5} more malformed line(s) skipped."
        val events = parseEvents(elements, null, limits, warnings)
        return EventLog(null, null, null, null, events, warnings + warnings(events))
    }

    private fun parseEvents(items: List<JsonElement>, zone: ZoneId?, limits: EventImportLimits, warnings: MutableList<String>): List<ImportedEvent> {
        if (items.size > limits.maxEvents) throw EventImportException("Event log has ${items.size} events; the limit is ${limits.maxEvents}.")
        var skipped = 0
        val out = items.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: run { skipped++; return@mapIndexedNotNull null }
            val timeEl = o["t"] ?: o["time"] ?: o["timestamp"]
            val (ts, raw) = parseTime(timeEl, zone)
            ImportedEvent(
                index = i,
                timestamp = ts,
                rawTime = raw?.clip(64),
                level = o.str("level")?.clip(16),
                type = (o.str("type") ?: o.str("event") ?: o.str("name"))?.clip(120) ?: "event",
                message = (o.str("message") ?: o.str("msg")).orEmpty().clip(limits.maxFieldChars),
                data = o["data"]?.takeIf { it !is JsonNull }?.toString()?.clip(limits.maxFieldChars),
                mark = (o["mark"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }
        if (skipped > 0) warnings += "$skipped event(s) were not JSON objects and were skipped."
        if (out.isEmpty()) throw EventImportException("The event log contains no events.")
        return out
    }

    /** Returns (epoch millis or null, raw text). Times without a zone stay unknown rather than guessed. */
    private fun parseTime(el: JsonElement?, zone: ZoneId?): Pair<Long?, String?> {
        val p = el as? JsonPrimitive ?: return null to null
        if (!p.isString) return p.longOrNull to p.content
        val s = p.content.trim()
        runCatching { return Instant.parse(s).toEpochMilli() to s }
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() to s } catch (_: DateTimeParseException) {}
        try {
            val local = LocalDateTime.parse(s)
            return (zone?.let { local.atZone(it).toInstant().toEpochMilli() }) to s
        } catch (_: DateTimeParseException) {}
        return null to s
    }

    private fun warnings(events: List<ImportedEvent>): List<String> = buildList {
        val noTime = events.count { it.timestamp == null }
        if (noTime > 0) add("$noTime event(s) have no usable time (missing, unparseable, or no time zone); their time is recorded as unknown.")
        val times = events.mapNotNull { it.timestamp }
        if (times.zipWithNext().any { (a, b) -> b < a }) add("Event times are not in order; the original order is kept.")
    }

    fun render(log: EventLog, limits: EventImportLimits = EventImportLimits()): String {
        val sb = StringBuilder()
        sb.append("Imported event log: ").append(listOfNotNull(log.app, log.appVersion).joinToString(" ").ifBlank { "unknown app" })
        log.device?.let { sb.append(" · device ").append(it) }
        log.session?.let { sb.append(" · session ").append(it) }
        sb.append('\n').append(log.events.size).append(" event(s). Source text is untrusted data.\n")
        log.warnings.forEach { sb.append("Note: ").append(it).append('\n') }
        sb.append('\n')
        var written = 0
        for (e in log.events) {
            val line = buildString {
                append(e.timestamp?.let { Instant.ofEpochMilli(it).toString() } ?: "time unknown" + (e.rawTime?.let { " ($it)" } ?: ""))
                e.level?.let { append(" [").append(it.uppercase()).append(']') }
                append(' ').append(e.type)
                if (e.message.isNotEmpty()) append(" — ").append(e.message)
                e.data?.let { append(' ').append(it) }
                if (e.mark) append(" ★")
                append('\n')
            }
            if (sb.length + line.length > limits.maxRenderedChars) {
                sb.append("\n[Truncated: ${log.events.size - written} more event(s) are in the attached original file.]\n")
                break
            }
            sb.append(line)
            written++
        }
        return sb.toString()
    }

    fun plan(
        log: EventLog, newId: () -> String, projectId: String, investigationId: String?, evidenceId: String,
        now: Long, limits: EventImportLimits = EventImportLimits(),
    ): EventImportPlan {
        val notable = log.events.filter { it.notable }
        val kept = notable.take(limits.maxTimelineEvents)
        val label = log.app ?: "app"
        val timeline = kept.map { e ->
            TimelineEvent(newId(), projectId, investigationId, null, e.timestamp ?: now, e.timestamp, TimelineType.IMPORTED_EVENT,
                evidenceId, "$label: " + listOfNotNull(e.level?.uppercase(), e.type).joinToString(" ") +
                    (if (e.message.isNotEmpty()) " — " + e.message.clip(200) else "") +
                    (if (e.timestamp == null) " (source time unknown)" else ""))
        }
        val title = listOfNotNull(log.app, log.appVersion).joinToString(" ").ifBlank { "App" } +
            " event log (${log.events.size} events" + (if (notable.isNotEmpty()) ", ${notable.size} notable" else "") + ")"
        return EventImportPlan(
            title = title,
            text = render(log, limits),
            originalTimestamp = log.events.mapNotNull { it.timestamp }.minOrNull(),
            labels = listOfNotNull("instrumentation", log.app?.lowercase()?.clip(40)),
            timeline = timeline,
            notableSkipped = notable.size - kept.size,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun String.clip(n: Int) = if (length <= n) this else take(n) + "…"
}
