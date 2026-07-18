package dev.ccpocket.app.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingRelayMigrationTest {
    @Test
    fun old_official_relays_move_to_txx_but_custom_relays_stay_custom() {
        assertEquals(Pairing.DEFAULT_RELAY, Pairing.canonicalRelay("ws://cc.dmitt.com:6002"))
        assertEquals(Pairing.DEFAULT_RELAY, Pairing.canonicalRelay("wss://pocket.ark-nexus.cc/"))
        assertEquals("wss://mine.example", Pairing.canonicalRelay("wss://mine.example/"))
    }

    @Test
    fun stored_binding_keeps_credentials_when_its_relay_is_migrated() {
        val old = PairedDaemon(
            relay = "ws://cc.dmitt.com:6002",
            accountId = "acct",
            daemonPub = "pub",
            deviceId = "device",
            credential = "secret",
        )

        val migrated = Pairing.migrateLegacyRelays(listOf(old)).single()

        assertEquals(Pairing.DEFAULT_RELAY, migrated.relay)
        assertEquals(old.copy(relay = Pairing.DEFAULT_RELAY), migrated)
    }

    @Test
    fun old_qr_url_is_canonicalized_during_parse() {
        val info = Pairing.parse("ccpocket://pair?relay=ws://cc.dmitt.com:6002&acct=a&dpk=p&ticket=t")
        assertEquals(Pairing.DEFAULT_RELAY, info?.relay)
    }
}
