package dev.ccpocket.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelChipLabelTest {
    @Test fun long_gateway_id_keeps_both_ends() =
        assertEquals("deepseek…v3.2", modelChipLabel("deepseek-chat-v3.2"))

    @Test fun short_custom_id_is_unchanged() = assertEquals("kimi-k2", modelChipLabel("kimi-k2"))

    @Test fun claude_model_uses_family_alias() =
        assertEquals("opus", modelChipLabel("claude-opus-4-8[1m]"))

    @Test fun blank_model_stays_blank_for_caller_fallback() = assertEquals("", modelChipLabel("  "))
}
