package dev.ccpocket.daemon

import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.update.ReleaseClient
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun codex_only_host_does_not_require_a_claude_backend() {
        val factories = backendFactories(null, null, Path.of("/usr/bin/codex"), null)

        assertFalse(AgentKind.CLAUDE in factories)
        assertTrue(AgentKind.CODEX in factories)
        assertFalse(AgentKind.CURSOR in factories)
    }

    @Test
    fun installed_claude_registers_its_backend() {
        val factories = backendFactories(Path.of("/usr/bin/claude"), null, null, null)

        assertTrue(AgentKind.CLAUDE in factories)
    }

    @Test
    fun distribution_updates_come_from_the_fork() {
        assertEquals("ac54u-mobile/cc-pocket", ReleaseClient.DEFAULT_REPO)
    }

    @Test
    fun legacy_official_relay_is_migrated_but_custom_relays_are_preserved() {
        assertEquals(DEFAULT_RELAY, canonicalRelayUrl("ws://cc.dmitt.com:6002"))
        assertEquals(DEFAULT_RELAY, canonicalRelayUrl("wss://pocket.ark-nexus.cc/"))
        assertEquals("wss://self-hosted.example", canonicalRelayUrl("wss://self-hosted.example/"))
    }
}
