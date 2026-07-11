package dev.ccpocket.app.ui

import dev.ccpocket.protocol.AgentModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun cursor_presets_include_account_default_and_current_families() {
        assertEquals("auto", CURSOR_MODEL_OPTIONS.first())
        assertTrue("composer-2.5" in CURSOR_MODEL_OPTIONS)
        assertTrue("gpt-5.6-sol-medium" in CURSOR_MODEL_OPTIONS)
        assertTrue("claude-fable-5-high" in CURSOR_MODEL_OPTIONS)
        assertTrue("claude-fable-5-thinking-high" in CURSOR_MODEL_OPTIONS)
        assertTrue("claude-opus-4-8-high" in CURSOR_MODEL_OPTIONS)
    }

    @Test
    fun cursor_live_catalog_is_merged_with_bundled_fable_models() {
        val merged = mergedCursorModels(
            listOf(AgentModel("auto", "Auto (current, default)"), AgentModel("gpt-5.3-codex", "Codex 5.3")),
        )

        assertEquals("Auto (current, default)", merged.first().first)
        assertEquals(1, merged.count { it.second == "auto" })
        assertTrue(merged.any { it.second == "claude-fable-5-high" })
        assertTrue(merged.any { it.second == "claude-fable-5-thinking-high" })
    }

    @Test
    fun models_are_grouped_into_families_for_fast_scanning() {
        assertEquals("Recommended", modelFamily("auto"))
        assertEquals("Fable", modelFamily("claude-fable-5-high"))
        assertEquals("Codex", modelFamily("gpt-5.3-codex-high"))
        assertEquals("GPT", modelFamily("gpt-5.2"))
        assertEquals("Gemini", modelFamily("gemini-3.1-pro"))
    }
}
