package dev.ccpocket.daemon.codex

/**
 * Turns Codex's first-prompt-style thread names into compact list labels. Codex currently seeds
 * `session_index.jsonl` with the prompt on some clients; preserving that value verbatim makes the
 * session list look like a second copy of the chat. Explicitly renamed / genuinely generated names
 * are left untouched.
 */
internal fun sessionDisplayTitle(firstPrompt: String, indexedTitle: String?): String {
    val firstLine = firstPrompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val indexed = indexedTitle?.trim()?.takeIf { it.isNotEmpty() }
    if (indexed != null && !looksCopiedFromPrompt(indexed, firstLine)) return indexed

    var title = firstLine
        .replace(Regex("https?://github\\.com/([^/\\s]+)/([^/\\s?#]+?)(?:\\.git)?(?=[\\s?#]|$)")) {
            it.groupValues[2].removeSuffix(".git")
        }
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[`*_#]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    title = title.replace(
        Regex("^(?:是这样的[，,:：]?|然后[，,:：]?|最后[，,:：]?|现在[，,:：]?|目前[，,:：]?|请|麻烦|帮我|我想(?:要)?|我需要|能不能|可不可以)+"),
        "",
    ).trim()
    title = title.replace(Regex("^(?:please\\s+|could you\\s+|can you\\s+|i (?:want|need) (?:you )?to\\s+)+", RegexOption.IGNORE_CASE), "").trim()
    title = title.trim(' ', '，', ',', '。', '.', '？', '?', '！', '!', ':', '：')

    val hasCjk = title.any { it.code in 0x3400..0x9fff }
    val limit = if (hasCjk) 24 else 52
    if (title.length > limit) {
        val boundary = title.indexOfAny(charArrayOf('，', ',', '。', '.', '；', ';', '？', '?', '！', '!', '：', ':'), startIndex = 10)
        title = if (boundary in 10..limit) title.substring(0, boundary) else title.take(limit)
        title = title.trimEnd()
    }
    return title.ifBlank { firstLine.take(if (hasCjk) 24 else 52) }
}

private fun looksCopiedFromPrompt(title: String, prompt: String): Boolean {
    fun comparable(value: String) = value.lowercase().replace(Regex("[\\s`*_#\\p{Punct}。，？！：；]+"), "")
    val t = comparable(title)
    val p = comparable(prompt)
    return t == p || (t.length >= 24 && p.startsWith(t))
}
