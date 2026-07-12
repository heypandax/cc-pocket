package dev.ccpocket.daemon.disk

import dev.ccpocket.protocol.AgentKind
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the aggregation against a temp projects tree via the injectable roots (never the real ~/.claude|~/.codex). */
class UsageServiceTest {

    private fun withProjects(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("ccp-usage-projects")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun today_buckets_by_hour_and_filters_zero_models() {
        withProjects { root ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun at(hour: Int, min: Int) = today.atTime(hour, min).atZone(zone).toInstant().toString()

            val proj = root.resolve("-Users-x-proj").also { it.createDirectories() }
            proj.resolve("s1.jsonl").writeText(
                listOf(
                    // real model, two turns in hour 3 → 150 + 150
                    """{"type":"assistant","timestamp":"${at(3, 10)}","requestId":"r1","message":{"id":"m1","model":"claude-opus-4-8","usage":{"input_tokens":100,"output_tokens":50}}}""",
                    """{"type":"assistant","timestamp":"${at(3, 40)}","requestId":"r2","message":{"id":"m2","model":"claude-opus-4-8","usage":{"input_tokens":100,"output_tokens":50}}}""",
                    // real model, one turn in hour 14 → 200
                    """{"type":"assistant","timestamp":"${at(14, 5)}","requestId":"r3","message":{"id":"m3","model":"claude-opus-4-8","usage":{"input_tokens":200,"output_tokens":0}}}""",
                    // a <synthetic> zero-token turn — must NOT surface as a by-model row
                    """{"type":"assistant","timestamp":"${at(9, 0)}","requestId":"r4","message":{"id":"m4","model":"<synthetic>","usage":{"input_tokens":0,"output_tokens":0}}}""",
                ).joinToString("\n") + "\n",
            )

            val u = UsageService.aggregate(1, projectsRoot = root, codexFiles = emptyList(), journal = emptyList())

            val hours = assertNotNull(u.hours, "Today range must carry 24 hourly buckets")
            assertEquals(24, hours.size)
            assertEquals(300L, hours[3].tokens)
            assertEquals(200L, hours[14].tokens)
            assertEquals(0L, hours[0].tokens)
            assertEquals("03:00", hours[3].label)
            assertEquals(500L, u.tokensToday)
            // the zero-token <synthetic> entry is filtered at the source, so exactly one model remains
            val m = u.models.single()
            assertEquals("claude-opus-4-8", m.model)
            assertEquals(500L, m.tokens)
            assertEquals(AgentKind.CLAUDE, m.agent)
        }
    }

    @Test
    fun week_range_has_no_hours_and_dates_every_day_bucket() {
        withProjects { root ->
            val today = LocalDate.now(ZoneId.systemDefault())
            val u = UsageService.aggregate(7, projectsRoot = root, codexFiles = emptyList(), journal = emptyList())
            assertNull(u.hours, "only the Today range fills hours")
            assertEquals(7, u.days.size)
            assertTrue(u.days.all { it.date != null }, "every day bucket carries an ISO date")
            assertEquals(today.toString(), u.days.last().date)
        }
    }

    @Test
    fun journaled_cursor_turns_join_the_aggregate_with_their_agent_tag() {
        withProjects { root ->
            val zone = ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone).withHour(8).withMinute(0)
            val entries = listOf(
                UsageJournal.Entry(ts = now.toInstant().toEpochMilli(), agent = AgentKind.CURSOR, model = "claude-fable-5", input = 100, output = 40, cacheRead = 60),
                UsageJournal.Entry(ts = now.plusMinutes(5).toInstant().toEpochMilli(), agent = AgentKind.CURSOR, model = "claude-fable-5", input = 50, output = 50),
            )
            val u = UsageService.aggregate(1, projectsRoot = root, codexFiles = emptyList(), journal = entries)
            assertEquals(300L, u.tokensToday)
            assertEquals(2L, u.requestsToday)
            assertEquals(300L, assertNotNull(u.hours)[8].tokens)
            val m = u.models.single()
            assertEquals("claude-fable-5", m.model)
            // journaled agent wins over the name heuristic — "claude-fable-5" must NOT bucket as Claude
            assertEquals(AgentKind.CURSOR, m.agent)
        }
    }

    @Test
    fun journal_notes_and_reads_back_entries_and_skips_zero_usage() {
        val file = java.io.File.createTempFile("ccp-usage-journal", ".jsonl")
        try {
            UsageJournal.note(AgentKind.CURSOR, "auto", dev.ccpocket.protocol.TokenUsage(10, 5, 2, 3), ts = 111, file = file)
            UsageJournal.note(AgentKind.CURSOR, null, dev.ccpocket.protocol.TokenUsage(0, 0), ts = 222, file = file) // no tokens -> dropped
            val entries = UsageJournal.read(file)
            val e = entries.single()
            assertEquals(111, e.ts)
            assertEquals("auto", e.model)
            assertEquals(20, e.total)
        } finally {
            file.delete()
        }
    }
}
