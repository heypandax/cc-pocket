package dev.ccpocket.daemon.claude

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeRateLimitsClientTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun obj(raw: String) = json.parseToJsonElement(raw) as JsonObject

    // shape captured live from api.anthropic.com/api/oauth/usage (claude 2.1.x era, 2026-07)
    private val captured = """
        {
          "five_hour": {"utilization": 19.0, "resets_at": "2026-07-13T10:59:59.865509+00:00",
                        "limit_dollars": null, "used_dollars": null, "remaining_dollars": null},
          "seven_day": {"utilization": 2.0, "resets_at": "2026-07-15T11:59:59.865535+00:00"},
          "seven_day_opus": null,
          "extra_usage": {"is_enabled": false},
          "limits": [{"kind": "session", "percent": 19, "resets_at": "2026-07-13T10:59:59.865509+00:00"}]
        }
    """

    @Test
    fun parses_captured_response_shape() {
        val limits = ClaudeRateLimitsClient.parse(obj(captured), plan = "pro", now = 123L)!!
        assertEquals("pro", limits.planType)
        assertEquals(123L, limits.capturedAt)
        assertEquals(19.0, limits.session!!.usedPercent)
        assertEquals(300, limits.session!!.windowMinutes)
        assertEquals(1783940399L, limits.session!!.resetsAt) // 2026-07-13T10:59:59Z
        assertEquals(2.0, limits.weekly!!.usedPercent)
        assertEquals(7 * 24 * 60, limits.weekly!!.windowMinutes)
        assertNull(limits.weeklyOpus) // JSON null block must not become a window
    }

    @Test
    fun opus_weekly_window_is_kept_when_reported() {
        val limits = ClaudeRateLimitsClient.parse(
            obj("""{"seven_day_opus": {"utilization": 55.5, "resets_at": "2026-07-15T11:59:59+00:00"}}"""),
            plan = "max", now = 1L,
        )!!
        assertEquals(55.5, limits.weeklyOpus!!.usedPercent)
        assertNull(limits.session)
    }

    @Test
    fun no_recognizable_window_means_null() {
        assertNull(ClaudeRateLimitsClient.parse(obj("""{"unexpected": {"shape": 1}}"""), plan = null, now = 1L))
        // a window without a parseable reset instant is dropped, not mis-rendered
        assertNull(ClaudeRateLimitsClient.parse(obj("""{"five_hour": {"utilization": 10.0, "resets_at": "garbage"}}"""), plan = null, now = 1L))
    }

    @Test
    fun credentials_parse_and_expiry_guard() {
        val fresh = """{"claudeAiOauth":{"accessToken":"tok-1","refreshToken":"r","expiresAt":${System.currentTimeMillis() + 3_600_000},"subscriptionType":"pro"}}"""
        val c = ClaudeRateLimitsClient.parseCredentials(fresh)!!
        assertEquals("tok-1", c.accessToken)
        assertEquals("pro", c.plan)

        val expired = """{"claudeAiOauth":{"accessToken":"tok-2","expiresAt":1000,"subscriptionType":"pro"}}"""
        assertNull(ClaudeRateLimitsClient.parseCredentials(expired)) // stale token would only 401 — skip it
        assertNull(ClaudeRateLimitsClient.parseCredentials("not json"))
        assertNull(ClaudeRateLimitsClient.parseCredentials("""{"claudeAiOauth":{"expiresAt":9999999999999}}"""))
    }
}
