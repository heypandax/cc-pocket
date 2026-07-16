package dev.ccpocket.app.ui

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlBoundaryTest {
    @Test
    fun markdownLinkKeepsClosingParenOut() {
        val text = "[执行副本](https://x.dev/base/abc)（token）"
        val linked = AnnotatedString(text).withUrlLinks()
        val links = linked.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        assertEquals("https://x.dev/base/abc", text.substring(links[0].start, links[0].end))
    }

    @Test
    fun fullwidthPunctuationStaysOut() {
        val text = "文档在 https://x.dev/a，稍后看"
        val linked = AnnotatedString(text).withUrlLinks()
        val link = linked.getLinkAnnotations(0, text.length).single()
        assertEquals("https://x.dev/a", text.substring(link.start, link.end))
    }

    @Test
    fun urlMachinerySurvivesIntact() {
        val url = "https://x.dev/p-a_b~c/(v1)/f.php?q=a+b&r[]=c%20d,e;f=g:h@i#frag"
        val linked = AnnotatedString("see $url now").withUrlLinks()
        val link = linked.getLinkAnnotations(0, linked.length).single()
        assertEquals(url, linked.text.substring(link.start, link.end))
    }
}
