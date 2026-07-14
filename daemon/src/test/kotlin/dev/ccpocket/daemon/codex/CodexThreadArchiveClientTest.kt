package dev.ccpocket.daemon.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexThreadArchiveClientTest {
    @Test
    fun archived_list_maps_official_thread_fields_and_filters_cwd() {
        val result = Json.parseToJsonElement(
            """{"data":[
                {"id":"t1","name":"Saved title","preview":"first prompt","cwd":"/repo","updatedAt":100,"cliVersion":"0.144.1"},
                {"id":"t2","name":null,"preview":"wrong project","cwd":"/other","updatedAt":200,"cliVersion":"0.144.1"}
            ]}""",
        ) as JsonObject
        val sessions = CodexThreadArchiveClient.parseList(result, "/repo")
        assertEquals(1, sessions.size)
        assertEquals("t1", sessions.single().sessionId)
        assertEquals("Saved title", sessions.single().title)
        assertEquals(100_000, sessions.single().lastModified)
    }
}
