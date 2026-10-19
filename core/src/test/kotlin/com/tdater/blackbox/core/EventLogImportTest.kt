package com.tdater.blackbox.core

import com.tdater.blackbox.core.integrations.EventImportException
import com.tdater.blackbox.core.integrations.EventImportLimits
import com.tdater.blackbox.core.integrations.EventLogParser
import com.tdater.blackbox.core.model.TimelineType
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventLogImportTest {
    private val now = 1_760_000_000_000L
    private fun id() = UUID.randomUUID().toString()

    private val doc = """
        {"format":"blackbox-events","version":1,"app":"KITT","appVersion":"2.3.1","device":"Pixel Fold","timezone":"America/Chicago",
         "events":[
          {"t":"2026-10-18T21:00:00Z","level":"info","type":"obd.connect","message":"ELM327 connected"},
          {"t":"2026-10-18T16:01:00","level":"error","type":"obd.write","message":"Broken pipe","data":{"bytes":0}},
          {"type":"screen.off","mark":true},
          {"t":1792357320000,"level":"debug","type":"poll"}
         ]}
    """.trimIndent()

    @Test
    fun documentParsesTimesZonesAndUnknowns() {
        val log = EventLogParser.parse(doc)
        assertEquals("KITT", log.app)
        assertEquals(4, log.events.size)
        assertEquals(1_792_357_200_000L, log.events[0].timestamp)
        // Local time resolved with the declared zone (CDT = UTC-5).
        assertEquals(1_792_357_260_000L, log.events[1].timestamp)
        assertNull(log.events[2].timestamp)
        assertEquals("""{"bytes":0}""", log.events[1].data)
        assertTrue(log.warnings.any { "no usable time" in it })
    }

    @Test
    fun localTimeWithoutDeclaredZoneStaysUnknown() {
        val log = EventLogParser.parse("""{"events":[{"t":"2026-10-18T16:01:00","type":"x"}]}""")
        assertNull(log.events.single().timestamp)
        assertEquals("2026-10-18T16:01:00", log.events.single().rawTime)
    }

    @Test
    fun jsonLinesSkipMalformedLinesWithWarning() {
        val log = EventLogParser.parse("""{"t":"2026-10-18T21:00:00Z","type":"a"}
            |not json
            |
            |{"type":"b","level":"ERROR"}""".trimMargin())
        assertEquals(listOf("a", "b"), log.events.map { it.type })
        assertTrue(log.warnings.any { "Line 2" in it })
    }

    @Test
    fun rejectsForeignFormatsAndGarbage() {
        assertFailsWith<EventImportException> { EventLogParser.parse("""{"format":"something-else","events":[]}""") }
        assertFailsWith<EventImportException> { EventLogParser.parse("""{"format":"blackbox-events","version":99,"events":[{"type":"a"}]}""") }
        assertFailsWith<EventImportException> { EventLogParser.parse("hello world") }
        assertFailsWith<EventImportException> { EventLogParser.parse("") }
        assertFailsWith<EventImportException> { EventLogParser.parse("""{"events":[]}""") }
        assertFailsWith<EventImportException> {
            EventLogParser.parse("""{"events":[{"type":"a"},{"type":"b"}]}""", EventImportLimits(maxEvents = 1))
        }
    }

    @Test
    fun planPutsOnlyNotableEventsOnTimelineAndBoundsText() {
        val log = EventLogParser.parse(doc)
        val p = EventLogParser.plan(log, ::id, "p1", "i1", "e1", now)
        assertEquals(2, p.timeline.size) // the error and the marked event
        assertTrue(p.timeline.all { it.type == TimelineType.IMPORTED_EVENT && it.relatedEntityId == "e1" })
        assertEquals(1_792_357_260_000L, p.timeline[0].sourceTimestamp)
        assertNull(p.timeline[1].sourceTimestamp)
        assertTrue("source time unknown" in p.timeline[1].summary)
        assertEquals(1_792_357_200_000L, p.originalTimestamp)
        assertTrue("Broken pipe" in p.text && "time unknown" in p.text)
        assertTrue(p.title.startsWith("KITT 2.3.1 event log (4 events, 2 notable"))

        val many = EventLogParser.parse((1..500).joinToString("\n") { """{"type":"e$it","level":"error","message":"${"x".repeat(100)}"}""" })
        val bounded = EventLogParser.plan(many, ::id, "p1", null, "e1", now, EventImportLimits(maxRenderedChars = 5_000, maxTimelineEvents = 10))
        assertEquals(10, bounded.timeline.size)
        assertEquals(490, bounded.notableSkipped)
        assertTrue(bounded.text.length < 5_200 && "Truncated" in bounded.text)
    }

    @Test
    fun shippedSampleParsesCleanly() {
        val log = EventLogParser.parse(java.io.File("../docs/samples/kitt-events.json").readText())
        assertEquals(6, log.events.size)
        assertTrue(log.events.all { it.timestamp != null })
        assertEquals(3, log.events.count { it.notable })
    }
}
