package dev.ccpocket.daemon.relay

import dev.ccpocket.daemon.DaemonCore
import dev.ccpocket.daemon.identity.Identity
import dev.ccpocket.protocol.DaemonInfo
import dev.ccpocket.protocol.Envelope
import dev.ccpocket.protocol.Frame
import dev.ccpocket.protocol.PocketJson
import dev.ccpocket.protocol.SendPrompt
import dev.ccpocket.protocol.SessionGone
import dev.ccpocket.protocol.e2e.E2ECrypto
import dev.ccpocket.protocol.e2e.E2ESession
import dev.ccpocket.protocol.e2e.Wire
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceSessionsPskDeadlockTest {
    private class Harness(dir: File) {
        val identity = Identity.loadOrCreate(File(dir, "identity.json"))
        val outbound = Channel<Pair<String, ByteArray>>(Channel.UNLIMITED)
        val sessions = DeviceSessions(
            core = DaemonCore(emptyMap()),
            identity = identity,
            store = File(dir, "devices.json"),
        ) { deviceId, payload -> outbound.trySend(deviceId to payload) }
    }

    private suspend fun pair(h: Harness, deviceId: String, ticket: String): E2ECrypto.KeyPair {
        h.sessions.onMintedTicket(ticket)
        val keys = E2ECrypto.generateKeyPair()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keys.publicRaw)
        h.sessions.onDevicePaired(deviceId, encoded)
        return keys
    }

    private suspend fun handshake(
        h: Harness,
        deviceId: String,
        keys: E2ECrypto.KeyPair,
        ticket: String?,
    ): E2ESession {
        val initiator = E2ESession.initiator(
            keys.privateRaw,
            keys.publicRaw,
            h.identity.e2ePubRaw,
            (ticket ?: "").encodeToByteArray(),
        )
        h.sessions.onFrame(deviceId, Wire.payload(Wire.HANDSHAKE, initiator.ephPublic))
        val response = h.outbound.receive().second
        assertEquals(Wire.HANDSHAKE, Wire.payloadType(response))
        return initiator.finish(Wire.payloadBody(response))
    }

    private suspend fun send(h: Harness, deviceId: String, session: E2ESession, frame: Frame) {
        val envelope = Envelope("0", 0L, body = frame)
        val encrypted = session.seal(PocketJson.encodeToString(envelope).encodeToByteArray())
        h.sessions.onFrame(deviceId, Wire.payload(Wire.TRANSPORT, encrypted))
    }

    private inline fun <reified T : Frame> decode(session: E2ESession, framed: ByteArray): T {
        val plaintext = session.open(Wire.payloadBody(framed)) ?: error("expected decryptable frame")
        return PocketJson.decodeFromString<Envelope>(plaintext.decodeToString()).body as T
    }

    @Test
    fun interruptedFirstAttemptRecoversWithoutDaemonRestart() = runBlocking {
        val dir = createTempDirectory("ccp-psk-recovery").toFile()
        val h = Harness(dir)
        val keys = pair(h, "device", "ticket")

        handshake(h, "device", keys, "ticket")
        h.outbound.receive() // DaemonInfo for the interrupted attempt
        assertTrue(h.sessions.firstContactPending("device"))

        val recovered = handshake(h, "device", keys, null)
        val blindInfo = h.outbound.receive().second
        assertNull(recovered.open(Wire.payloadBody(blindInfo)))

        send(h, "device", recovered, SendPrompt("missing", "hi"))
        assertTrue(decode<Frame>(recovered, h.outbound.receive().second) is DaemonInfo)
        assertEquals("missing", decode<SessionGone>(recovered, h.outbound.receive().second).convoId)
        assertFalse(h.sessions.firstContactPending("device"))
    }

    @Test
    fun normalTicketBoundFirstContactIsUnchanged() = runBlocking {
        val dir = createTempDirectory("ccp-psk-normal").toFile()
        val h = Harness(dir)
        val keys = pair(h, "device", "ticket")
        val session = handshake(h, "device", keys, "ticket")

        assertTrue(decode<Frame>(session, h.outbound.receive().second) is DaemonInfo)
        send(h, "device", session, SendPrompt("missing", "hi"))
        assertEquals("missing", decode<SessionGone>(session, h.outbound.receive().second).convoId)
        assertFalse(h.sessions.firstContactPending("device"))
        assertTrue(h.outbound.tryReceive().isFailure)
    }
}
