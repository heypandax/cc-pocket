package dev.ccpocket.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.DirectoryEntry

/** A row in the project browser, computed from the flat [DirectoryEntry] list client-side. */
sealed interface DirRow {
    data class Header(val label: String) : DirRow

    /** [direct] lets a pinned project jump into its live session instead of opening the session list first. */
    data class Dir(val entry: DirectoryEntry, val showPath: Boolean, val direct: Boolean = false) : DirRow
}

// Project paths arrive in the daemon HOST's native format: Unix "/a/b" OR Windows "C:\a\b". Split on
// either separator, but rebuild paths with the ORIGINAL separator so equality against DirectoryEntry.path
// (e.g. base == entry.path, here and in App.kt) still holds on a Windows daemon.
private val PATH_SEP = Regex("""[/\\]""")
internal fun sepOf(path: String): Char = if (path.contains('\\')) '\\' else '/' // shared with App.kt's NewPathSheet seed

/** Collapse $HOME to ~ (so paths stop repeating /Users/<name>/ everywhere) — one home-detection
 *  rule for the whole file: [homePrefix]. */
fun tilde(path: String): String =
    homePrefix(path)?.let { "~" + path.substring(it.length).replace('\\', '/') } ?: path

/** Drop a trailing separator so a session never opens at "/foo/bar/", keeping a bare root ("/",
 *  "C:\") intact — shared by the two new-session inputs (mobile NewPathSheet, desktop popover). */
fun trimTrailingSep(s: String): String = s.trimEnd('/', '\\').ifEmpty { s }

/** Just the project folder ("cc-pocket") — for tight surfaces like the chat header's meta line, where
 *  even a tail-truncated path is noise. Handles both host separators and trailing slashes; a bare
 *  root ("/", "C:\") falls back to the path itself. */
fun folderName(path: String?): String {
    val p = path?.trimEnd('/', '\\') ?: return ""
    return p.split(PATH_SEP).lastOrNull { it.isNotBlank() } ?: path
}

/** The home-dir prefix of one absolute path ("/Users/x", "/home/x", "C:\Users\x"), else null. Keeps
 *  seg[0] (the drive on Windows, "" on Unix) so the result stays a real prefix of the input. */
fun homePrefix(path: String): String? {
    val s = path.split(PATH_SEP)
    return if (s.size > 3 && (s[1] == "Users" || s[1] == "home")) s.take(3).joinToString(sepOf(path).toString()) else null
}

private val DRIVE_PREFIX = Regex("""^[A-Za-z]:[\\/].*""")

/** Light "absolute or ~ path" check shared by the new-session inputs (mobile NewPathSheet, desktop
 *  popover); the daemon stays the authority on whether the dir is actually usable. */
fun looksAbsolutePath(s: String): Boolean = s.startsWith("/") || s.startsWith("~") || DRIVE_PREFIX.matches(s)

/**
 * A one-line monospace path that overflows from the FRONT — the project folder (the tail) is what
 * identifies a workdir, so a long path renders as "…app/cc-pocket" instead of "/Users/yourname/…".
 * Compose 1.7 has no TextOverflow.StartEllipsis; this trims via onTextLayout until the tail fits.
 */
@Composable
fun TailPathText(path: String, modifier: Modifier = Modifier, color: Color = Tok.tx2, fontSize: TextUnit = 11.sp) {
    val full = tilde(path)
    var drop by remember(full) { mutableStateOf(0) }
    val shown = if (drop <= 0) full else "…" + full.takeLast((full.length - drop).coerceAtLeast(4))
    Text(
        shown, color = color, fontFamily = FontFamily.Monospace, fontSize = fontSize,
        maxLines = 1, softWrap = false,
        onTextLayout = { r ->
            // monospace → proportional first jump, then settle in a couple of passes
            if (r.hasVisualOverflow && drop < full.length - 4) {
                drop = (drop + (full.length / 6).coerceAtLeast(2)).coerceAtMost(full.length - 4)
            }
        },
        modifier = modifier,
    )
}

/** The entries for the pinned [paths] (in pin order) that are still present in [dirs]. */
fun pinnedEntries(dirs: List<DirectoryEntry>, paths: List<String>): List<DirectoryEntry> =
    paths.mapNotNull { p -> dirs.firstOrNull { it.path == p } }

/** The backend of the entry's linked live session ([DirectoryEntry.activeSessionId]). CLAUDE when the
 *  daemon didn't say (older daemon / transcript-derived active — those are always Claude transcripts):
 *  resuming must NOT fall back to the user's default-agent preference, which forked Claude sessions
 *  under the Codex backend and vice versa. */
fun liveAgent(e: DirectoryEntry): AgentKind =
    e.activeSessions.firstOrNull { it.sessionId == e.activeSessionId }?.agent ?: AgentKind.CLAUDE

fun buildDirRows(
    dirs: List<DirectoryEntry>,
    query: String,
    pinned: List<String>,
    pinnedLabel: String,
    projectsLabel: String,
): List<DirRow> {
    val q = query.trim()
    // match path + project name + the LIVE session's title (what the card shows). Idle-session titles and
    // transcript content aren't in this flat list — searching those needs a daemon-side session search.
    val filtered = if (q.isEmpty()) dirs else dirs.filter {
        it.path.contains(q, ignoreCase = true) ||
            it.name.contains(q, ignoreCase = true) ||
            it.latestSessionTitle?.contains(q, ignoreCase = true) == true ||
            it.activeSessionTitle?.contains(q, ignoreCase = true) == true
    }
    // One flat browser: pinned projects first in pin order, then every matching project in the daemon's
    // recency order. A pin intentionally retains its copy in Projects so the complete list stays stable.
    val pins = pinnedEntries(filtered, pinned)
    val rows = ArrayList<DirRow>()
    fun section(label: String, items: List<DirectoryEntry>, direct: Boolean) {
        if (items.isEmpty()) return
        if (label.isNotEmpty()) rows += DirRow.Header(label)
        items.forEach { rows += DirRow.Dir(it, showPath = true, direct = direct) }
    }
    section(pinnedLabel, pins, direct = true)
    section(if (pins.isNotEmpty()) projectsLabel else "", filtered, direct = false)
    return rows
}
