package dev.ccpocket.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodexModelOptionsTest {
    @Test
    fun presets_match_the_documented_codex_models() {
        assertEquals(
            listOf(
                "gpt-5.6-sol",
                "gpt-5.6-terra",
                "gpt-5.6-luna",
                "gpt-5.5",
                "gpt-5.3-codex-spark",
                "gpt-5.4",
                "gpt-5.4-mini",
            ),
            CODEX_MODEL_OPTIONS,
        )
        assertFalse(CODEX_MODEL_OPTIONS.any { it in setOf("gpt-5.1-codex", "gpt-5.1-codex-mini", "gpt-5-codex") })
    }
}
