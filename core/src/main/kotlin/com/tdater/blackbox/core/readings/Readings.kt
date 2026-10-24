package com.tdater.blackbox.core.readings

import com.tdater.blackbox.core.model.EvidenceKind
import com.tdater.blackbox.core.model.ProjectBundle

/** One numeric value, from a finished run's measurements or a logged MEASUREMENT record. */
data class Reading(
    val name: String,
    val value: Double,
    val unit: String?,
    val at: Long,
    val sourceId: String,
    val fromRun: Boolean,
    val investigationId: String?,
)

/** All readings that share a name (case-insensitive), oldest first. */
data class ReadingSeries(val name: String, val unit: String?, val points: List<Reading>) {
    val latest: Reading get() = points.last()
    val min: Double get() = points.minOf { it.value }
    val max: Double get() = points.maxOf { it.value }
    val average: Double get() = points.sumOf { it.value } / points.size
    /** Units seen in this series other than [unit]; mixed units make a chart misleading, so the UI warns. */
    val otherUnits: Set<String> get() = points.mapNotNull { it.unit }.toSet() - setOfNotNull(unit)
}

object Readings {
    // "12.4 V", "-3,5 °C", "Voltage: 12.4V", "1,234.5 mi", "98.6"
    private val withName = Regex("""^\s*([^:=\n]{1,60}?)\s*[:=]\s*(.+)$""")
    private val number = Regex("""^\s*([-+]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:[.,]\d+)?)\s*(.*?)\s*$""")

    /** Parses "value unit" (comma or dot decimals; thousands commas). Null when the text is not a number. */
    fun parseValue(text: String): Pair<Double, String?>? {
        val m = number.find(text.lineSequence().firstOrNull().orEmpty()) ?: return null
        val raw = m.groupValues[1].let {
            // "1,234" / "1,234.5" are thousands separators; "3,5" is a decimal comma.
            if (it.matches(Regex("""[-+]?\d{1,3}(,\d{3})+(\.\d+)?"""))) it.replace(",", "") else it.replace(',', '.')
        }
        val v = raw.toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].trim().take(20).ifEmpty { null }
        return v to unit
    }

    /** Parses "Name: value unit", falling back to [fallbackName] for a bare "value unit". */
    fun parseLine(text: String, fallbackName: String): Triple<String, Double, String?>? {
        val first = text.lineSequence().firstOrNull()?.trim().orEmpty()
        withName.find(first)?.let { m ->
            parseValue(m.groupValues[2])?.let { (v, u) -> return Triple(m.groupValues[1].trim(), v, u) }
        }
        val name = fallbackName.trim().ifEmpty { return null }
        return parseValue(first)?.let { (v, u) -> Triple(name, v, u) }
    }

    fun collect(b: ProjectBundle): List<ReadingSeries> {
        val all = mutableListOf<Reading>()
        b.runs.filter { it.isFinished }.forEach { r ->
            r.measurements.forEach { m ->
                val v = m.value ?: return@forEach
                if (m.name.isBlank()) return@forEach
                all += Reading(m.name.trim(), v, m.unit?.ifBlank { null }, r.endedAt ?: r.startedAt, r.id, true, r.investigationId)
            }
        }
        b.evidence.filter { it.kind == EvidenceKind.MEASUREMENT && it.withdrawnAt == null }.forEach { e ->
            parseLine(e.text, e.title)?.let { (n, v, u) ->
                all += Reading(n, v, u, e.originalTimestamp ?: e.capturedAt, e.id, false, e.investigationId)
            }
        }
        return all.groupBy { it.name.lowercase() }.values.map { pts ->
            val sorted = pts.sortedBy { it.at }
            val unit = sorted.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ReadingSeries(sorted.last().name, unit, sorted)
        }.sortedByDescending { it.latest.at }
    }

    /** Short, human trend sentence. Says nothing it can't back with at least two points. */
    fun describe(s: ReadingSeries): String {
        if (s.points.size < 2) return "One reading so far."
        val first = s.points.first().value
        val last = s.latest.value
        val u = s.unit?.let { " $it" } ?: ""
        val delta = last - first
        val dir = when {
            delta == 0.0 -> "unchanged"
            delta > 0 -> "up ${fmt(delta)}$u"
            else -> "down ${fmt(-delta)}$u"
        }
        return "${s.points.size} readings, $dir since the first. Range ${fmt(s.min)}–${fmt(s.max)}$u, average ${fmt(s.average)}$u."
    }

    fun fmt(v: Double): String = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e12) v.toLong().toString()
        else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
}
