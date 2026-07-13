package dev.ccpocket.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalMarkdownTest {
    @Test fun splitsAtCompletedParagraph() {
        assertEquals(7, stableMarkdownBoundary("hello\n\nworld"))
    }

    @Test fun doesNotSplitInsideOpenFence() {
        assertEquals(0, stableMarkdownBoundary("```kotlin\nval x = 1\n\nstill code"))
    }

    @Test fun resumesSplittingAfterClosedFence() {
        val text = "```kotlin\nval x = 1\n```\n\nafter"
        assertEquals(text.indexOf("after"), stableMarkdownBoundary(text))
    }
}
