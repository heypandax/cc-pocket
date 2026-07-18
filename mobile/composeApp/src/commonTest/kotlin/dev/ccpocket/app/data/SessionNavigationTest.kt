package dev.ccpocket.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionNavigationTest {
    @Test
    fun late_previous_session_cannot_replace_selected_list_row() {
        assertFalse(sessionLiveMatchesPendingOpen(opening = true, expectedSessionId = "new", incomingSessionId = "old"))
        assertTrue(sessionLiveMatchesPendingOpen(opening = true, expectedSessionId = "new", incomingSessionId = "new"))
    }

    @Test
    fun new_sessions_and_unsolicited_reattach_keep_existing_behavior() {
        assertTrue(sessionLiveMatchesPendingOpen(opening = true, expectedSessionId = null, incomingSessionId = "created"))
        assertTrue(sessionLiveMatchesPendingOpen(opening = false, expectedSessionId = "current", incomingSessionId = "current"))
    }
}
