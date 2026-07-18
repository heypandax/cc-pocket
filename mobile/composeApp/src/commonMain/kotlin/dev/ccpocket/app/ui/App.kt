@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.ccpocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.ccpocket.app.media.rememberFileAttacher
import dev.ccpocket.app.media.rememberImageAttacher
import dev.ccpocket.app.media.rememberVideoAttacher
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import dev.ccpocket.app.defaultDaemonUrl
import dev.ccpocket.app.data.ChatItem
import dev.ccpocket.app.data.ConnPhase
import dev.ccpocket.app.data.PocketRepository
import dev.ccpocket.app.data.StatusMsg
import dev.ccpocket.app.data.VoiceState
import dev.ccpocket.app.pairing.displayName
import dev.ccpocket.app.ui.fleet.crossMachineAttention
import dev.ccpocket.app.ui.fleet.fleetAttention
import dev.ccpocket.app.resources.*
import dev.ccpocket.app.theme.LocalFontScale
import dev.ccpocket.app.theme.GlassBackdrop
import dev.ccpocket.app.theme.PocketTheme
import dev.ccpocket.app.theme.PocketMotion
import dev.ccpocket.app.theme.ThemeMode
import dev.ccpocket.app.theme.Tok
import dev.ccpocket.app.voice.openAppSettings
import dev.ccpocket.protocol.AgentKind
import dev.ccpocket.protocol.CommandSource
import dev.ccpocket.protocol.DEFAULT_CONTEXT_WINDOW
import dev.ccpocket.protocol.Decision
import dev.ccpocket.protocol.DirectoryEntry
import dev.ccpocket.protocol.isQuestion
import dev.ccpocket.protocol.isSubagentTool
import dev.ccpocket.protocol.PermissionMode
import dev.ccpocket.protocol.SessionSummary
import dev.ccpocket.protocol.SlashCommand
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource

@Composable
fun App(scope: CoroutineScope) {
    val repo = remember { PocketRepository(scope) }
    // one live link per paired computer: the primary repo keeps its exact semantics; the coordinator
    // maintains pinned satellites for the other bindings so the whole fleet is live at once
    remember { dev.ccpocket.app.data.FleetCoordinator(scope, repo).also { dev.ccpocket.app.data.FleetRuntime.coordinator = it; it.start() } }
    // fleet surfaces (machine-first triage) overlay the content stack from anywhere: header machine
    // name → Fleet home; attention banner / cross-machine banner → inbox. UI-local like the sheets.
    var fleetOpen by remember { mutableStateOf(false) }
    var inboxOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dev.ccpocket.app.telemetry.Telemetry.track(dev.ccpocket.app.telemetry.TelEvent.AppLaunch)
        if (repo.paired.value != null) repo.startRelay() // already paired -> straight to the list
    }
    val pendingLink by dev.ccpocket.app.DeepLink.pending.collectAsState()
    LaunchedEffect(pendingLink) { pendingLink?.let { repo.handlePairUrl(it); dev.ccpocket.app.DeepLink.pending.value = null } }
    // a tapped task-complete push deep-links straight into its session (connecting first if needed)
    val pushOpen by dev.ccpocket.app.PushRoute.pending.collectAsState()
    LaunchedEffect(pushOpen) { pushOpen?.let { repo.requestOpenSession(it.workdir, it.sessionId); dev.ccpocket.app.PushRoute.pending.value = null } }
    val appLock = repo.appLock
    val connectionPhase = repo.phase.value
    LaunchedEffect(connectionPhase) {
        // Keep the home-screen widget useful without requiring a visit to the Usage page. WidgetKit reads
        // the resulting snapshot offline; it never owns a daemon socket or credentials.
        if (connectionPhase == ConnPhase.Ready) repo.fetchUsage(1)
    }
    dev.ccpocket.app.OnAppForeground { // iOS kills sockets in background — reconnect the whole fleet on return
        repo.onAppForeground()
        if (repo.phase.value == ConnPhase.Ready) repo.fetchUsage(1)
        dev.ccpocket.app.data.FleetRuntime.coordinator?.onAppForeground()
        appLock.onForeground() // App Lock (issue #109): re-lock per policy / drop the cover on return
    }
    // App Lock: arm auto-lock when fully backgrounded; draw the opaque privacy cover the instant the app is
    // obscured (before the OS app-switcher snapshot) so a session is never visible in the task switcher.
    dev.ccpocket.app.OnAppBackground { appLock.onBackground() }
    dev.ccpocket.app.OnAppObscured { appLock.onWillObscure() }
    // Android system back walks the in-app stack (chat → sessions → directories) instead of leaving
    // the app; at the root it stays disabled so the system default (exit) applies. An open sheet
    // registers its own handler later in composition, which wins while it is showing (LIFO).
    dev.ccpocket.app.SystemBackHandler(
        enabled = repo.sessionActive.value && (repo.convoId.value != null || repo.sessionsDir.value != null),
    ) {
        if (repo.convoId.value != null) repo.backToBrowse() else repo.backToDirectories()
    }
    // registered after the content handler so it wins (LIFO) while a fleet overlay is up
    dev.ccpocket.app.SystemBackHandler(enabled = fleetOpen || inboxOpen) {
        if (inboxOpen) inboxOpen = false else fleetOpen = false
    }
    // Mobile always follows the phone's current light/dark appearance. Desktop keeps its own override.
    PocketTheme(mode = ThemeMode.SYSTEM, fontScale = repo.fontScale.value) {
        Box(Modifier.fillMaxSize()) {
          GlassBackdrop(Modifier.fillMaxSize(), ambientGlows = false) {
            // Paint the mobile page canvas before consuming system-bar insets. Otherwise the inset-only
            // status/home-indicator bands expose GlassBackdrop's warm/cool corner glows and read as two
            // unrelated solid strips in both themes. Content still stays inside the safe area.
            Column(
                Modifier.fillMaxSize()
                    .background(Tok.raised)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding(),
            ) {
                // pushes content down instead of overlaying the header; steady while retrying (no flicker)
                // preview/recording mode hides the demo banner for a clean marketing capture
                if (repo.demoMode.value && !dev.ccpocket.app.isPreviewMode()) StatusBanner(Tok.accent, stringResource(Res.string.demo_banner))
                if (repo.sessionActive.value && repo.phase.value == ConnPhase.Reconnecting) StatusBanner(Tok.danger, stringResource(Res.string.reconnect_banner))
                // entering a chat before the first link-up used to look identical to "connected" — say so (issue #41)
                if (repo.sessionActive.value && repo.phase.value == ConnPhase.Connecting && repo.convoId.value != null) StatusBanner(Tok.warn, stringResource(Res.string.conn_connecting_banner))
                if (repo.openTimedOut.value) {
                    StatusBanner(Tok.warn, stringResource(Res.string.open_session_timeout))
                    LaunchedEffect(Unit) { delay(6000); repo.openTimedOut.value = false } // transient; leaves composition → effect cancels
                }
                Box(Modifier.weight(1f)) {
                    when {
                        // a dead transport does NOT leave the content screens — ConnectionGate + auto-retry handle it
                        !repo.sessionActive.value ->
                            if (repo.addingDevice.value || repo.pairedList.isEmpty()) PairingScreen(repo) else ConnectScreen(repo)
                        repo.demoConnecting.value -> DemoConnectScreen { repo.finishDemoConnect() } // PREVIEW opener
                        else -> Box(Modifier.fillMaxSize()) {
                            ConnectionGate(repo) {
                                when {
                                    repo.convoId.value != null -> ChatScreen(repo, onOpenFleet = { fleetOpen = true }, onOpenInbox = { inboxOpen = true })
                                    repo.sessionsDir.value != null -> SessionsScreen(repo)
                                    else -> DirectoryScreen(repo, onOpenFleet = { fleetOpen = true })
                                }
                            }
                            // fleet overlays ride ABOVE the gate: the fleet view is exactly where you
                            // want to be while this machine is reconnecting or another one has news
                            if (fleetOpen) dev.ccpocket.app.ui.fleet.FleetHomeScreen(repo, onBack = { fleetOpen = false }, onOpenInbox = { inboxOpen = true })
                            if (inboxOpen) dev.ccpocket.app.ui.fleet.AttentionInboxScreen(repo) { inboxOpen = false }
                        }
                    }
                }
            }
            // a permission decision never needs typing — drop the keyboard so the sheet isn't cramped
            val rootFocus = LocalFocusManager.current
            LaunchedEffect(repo.pendingAsk.value?.askId) {
                if (repo.pendingAsk.value != null) rootFocus.clearFocus()
            }
            // AskUserQuestion (ask.questions != null) renders as the docked QuestionCard inside
            // ChatScreen instead — questions are conversation, not a safety gate, and the user
            // should be able to scroll the chat for context while answering.
            repo.pendingAsk.value?.takeIf { !it.isQuestion }?.let { ask ->
                PermissionSheet(
                    ask, repo.workdir.value,
                    onDeny = { repo.resolve(Decision.DENY) },
                    onOnce = { repo.resolve(Decision.ALLOW) },
                    onAlways = { repo.resolve(Decision.ALLOW, remember = true) },
                    onDismiss = { repo.dismissAsk() },
                )
            }
        }
        // App Lock (issue #109): the gate blocks ALL content (incl. the permission sheet) until biometrics
        // pass; the cover masks the app-switcher snapshot while briefly backgrounded. Both reuse the same
        // branded lockup. Desktop never reaches App(), so this overlay is Android/iOS-only by construction.
        if (appLock.locked.value) AppLockGate(appLock)
        else if (appLock.covered.value) AppLockCover()
      }
    }
}

/** Slim status strip above the content — reconnecting (danger-red) or computer-offline (amber). */
@Composable
private fun StatusBanner(color: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f)).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** PREVIEW: a brief "connecting → end-to-end encrypted" opener shown when entering the demo (scene 1). */
@Composable
private fun DemoConnectScreen(onDone: () -> Unit) {
    var secured by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500); secured = true
        delay(2200); onDone()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CC Pocket", color = Tok.tx, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Rounded.Smartphone, null, tint = Tok.tx2, modifier = Modifier.size(40.dp))
            Icon(
                if (secured) Icons.Rounded.Lock else Icons.Rounded.MoreHoriz, null,
                tint = if (secured) Tok.ok else Tok.muted, modifier = Modifier.size(if (secured) 24.dp else 28.dp),
            )
            Icon(Icons.Rounded.Computer, null, tint = Tok.tx2, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PulseDot(if (secured) Tok.ok else Tok.warn, size = 8.dp)
            Text(
                stringResource(if (secured) Res.string.preview_encrypted else Res.string.preview_connecting),
                color = if (secured) Tok.ok else Tok.tx2,
                fontSize = 15.sp, fontWeight = if (secured) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Gates the content screens on the honest connection [ConnPhase]. Replaces "blank screen on any failure"
 * with explicit, actionable states; self-heals (auto-retry) and only escalates to a full screen once a
 * failure persists. Reconnecting/Ready just show the content (the slim banner rides above it).
 */
@Composable
private fun ConnectionGate(repo: PocketRepository, content: @Composable () -> Unit) {
    when (repo.phase.value) {
        ConnPhase.PairingInvalid -> CenteredState(
            Tok.danger,
            stringResource(Res.string.conn_pairing_invalid_title),
            stringResource(Res.string.conn_pairing_invalid_body),
            stringResource(Res.string.conn_repair), { repo.unpairActive() },
        )
        ConnPhase.RelayUnreachable -> CenteredState(
            Tok.warn,
            stringResource(Res.string.conn_relay_unreachable_title),
            stringResource(Res.string.conn_relay_unreachable_body),
            stringResource(Res.string.conn_retry), { repo.retryConnection() }, onExit = { repo.disconnect() },
        )
        ConnPhase.ComputerOffline ->
            if (repo.convoId.value != null) {
                // Keep transcript history usable, but give the offline banner real layout space.
                // Emitting both directly into the parent's Box overlaid the banner on the chat header.
                Column(Modifier.fillMaxSize()) {
                    StatusBanner(Tok.warn, stringResource(Res.string.conn_computer_offline_banner))
                    Box(Modifier.weight(1f)) { content() }
                }
            }
            else CenteredState(
                Tok.warn,
                stringResource(Res.string.conn_computer_offline_title),
                stringResource(Res.string.conn_computer_offline_body),
                stringResource(Res.string.conn_retry), { repo.retryConnection() }, onExit = { repo.disconnect() },
                hint = stringResource(Res.string.conn_computer_offline_hint),
                recoveryCommand = "cc-pocket-daemon service-install --apply",
            )
        ConnPhase.Connecting ->
            if (repo.directoriesLoaded.value || repo.convoId.value != null) content() else DirectorySkeleton(repo)
        ConnPhase.Reconnecting, ConnPhase.Ready -> content()
    }
}

/** A centered dot + title + body + primary action (+ optional hint / exit). Shared by the gate states. */
@Composable
private fun CenteredState(
    dot: Color, title: String, body: String, primary: String, onPrimary: () -> Unit,
    onExit: (() -> Unit)? = null, hint: String? = null, recoveryCommand: String? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PulseDot(dot, size = 10.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = Tok.tx, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Tok.tx2, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, color = Tok.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (recoveryCommand != null) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.conn_run_on_computer), color = Tok.muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().widthIn(max = 360.dp).clip(RoundedCornerShape(10.dp))
                    .background(Tok.surface).border(1.dp, Tok.hair, RoundedCornerShape(10.dp))
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    recoveryCommand, color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                CopyChip(recoveryCommand)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onPrimary, Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)) { Text(primary) }
        if (onExit != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onExit) { Text(stringResource(Res.string.exit), color = Tok.muted, fontSize = 12.sp) }
        }
    }
}

/** The Projects top bar (title + status dot + monospace machine line, then [actions]) — ONE implementation
 *  shared by [DirectoryScreen] and its connect/switch skeleton, so the skeleton→list swap can never drift. */
@Composable
private fun ProjectsTopBar(
    title: String,
    dotColor: Color,
    machine: String?,
    machineLineModifier: Modifier = Modifier,
    machineTrailing: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().background(Tok.surface).padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Tok.tx, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp).then(machineLineModifier)) {
                PulseDot(dotColor, size = 6.dp)
                Spacer(Modifier.width(6.dp))
                machine?.let {
                    Text(it, color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                machineTrailing()
            }
        }
        actions()
    }
}

/** Connect/switch placeholder: the REAL Projects header (amber dot while the link comes up) over
 *  shimmering rows — so landing on a machine only swaps skeleton→list and amber→green, instead of
 *  flashing a differently-shaped "Choose a directory" screen first. */
@Composable
private fun DirectorySkeleton(repo: PocketRepository) {
    val shimmer by rememberInfiniteTransition().animateFloat(
        initialValue = 0.25f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Column(Modifier.fillMaxSize()) {
        ProjectsTopBar(stringResource(Res.string.dir_projects), Tok.warn, repo.paired.value?.displayName())
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) {
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).graphicsLayer { alpha = shimmer }.background(Tok.surface))
            }
        }
    }
}

/** Real empty-list state — connected, but the computer has no projects open yet (not a blank screen). */
@Composable
private fun EmptyDirectories(onAdd: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.dir_empty_title), color = Tok.tx, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.dir_empty_body), color = Tok.tx2, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        Spacer(Modifier.height(20.dp))
        Button(onAdd, Modifier.fillMaxWidth().widthIn(max = 320.dp).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.dir_empty_start))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onRefresh, Modifier.fillMaxWidth().widthIn(max = 320.dp).heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.dir_refresh))
        }
    }
}

/** Disconnected, with at least one bound computer: the device picker. Tap one to connect, or add another. */
@Composable
private fun ConnectScreen(repo: PocketRepository) {
    var url by remember { mutableStateOf(defaultDaemonUrl()) }
    var advanced by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CC Pocket", color = Tok.tx, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(Res.string.choose_computer), color = Tok.tx2, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        DeviceList(repo, onSwitch = { repo.switchDaemon(it) }, onAdd = { repo.beginAddDevice() })
        Spacer(Modifier.height(10.dp))
        Text(repo.status.value.resolve(), color = Tok.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(16.dp))
        TextButton({ advanced = !advanced }) {
            Text(stringResource(if (advanced) Res.string.hide_advanced else Res.string.advanced_direct_lan), color = Tok.muted, fontSize = 12.sp)
        }
        if (advanced) {
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(Res.string.daemon_ws_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ repo.startDirect(url) }, Modifier.fillMaxWidth()) { Text(stringResource(Res.string.connect_direct)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryScreen(repo: PocketRepository, onOpenFleet: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) { SettingsScreen(repo, onBack = { showSettings = false }); return } // full-screen, replaces this screen
    // pull-only list: refresh NOW — entering (and RE-entering, back from a session) shows fresh state
    // instead of the pre-session snapshot — then keep re-pulling quietly
    LaunchedEffect(Unit) { while (true) { repo.refreshDirectoriesSilently(); delay(10_000) } }

    // Snapshot state lists only when their own contents change. DirectoryScreen also observes scroll,
    // connection, and refresh state; rebuilding these lists on those unrelated frames creates avoidable
    // allocations precisely while the user is dragging.
    val dirsSnapshot by remember(repo) { derivedStateOf { repo.directories.toList() } }

    val projectsLabel = stringResource(Res.string.dir_projects)
    val pinnedLabel = stringResource(Res.string.dir_pinned)
    val computersLabel = stringResource(Res.string.open_computers)
    val pinnedSnapshot by remember(repo) { derivedStateOf { repo.pinnedPaths.toList() } }
    val flatRows = remember(dirsSnapshot, query, pinnedSnapshot, pinnedLabel, projectsLabel) {
        buildDirRows(dirsSnapshot, query, pinnedSnapshot, pinnedLabel, projectsLabel)
    }
    // long-press a project → a small sheet to pin/unpin it
    var actionTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    // "+" → type an arbitrary path to start a session in a folder with no prior history (issue #7)
    var showNewPath by remember { mutableStateOf(false) }
    var newPathTarget by remember { mutableStateOf<String?>(null) }
    // "open a project folder" browser (issue #152): the "+" entries land here for an OWNER; a guest
    // keeps the manual path sheet (its browse anchor "~" is outside the share and daemon-denied anyway)
    var showDirPicker by remember { mutableStateOf(false) }
    val openFolderEntry = { if (isGuestDirView(dirsSnapshot)) showNewPath = true else showDirPicker = true }

    // typing in the filter then scrolling the list dismisses the keyboard (fires once per scroll gesture)
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState(repo.directoryScrollIndex, repo.directoryScrollOffset)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            repo.directoryScrollIndex = index
            repo.directoryScrollOffset = offset
        }
    }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // ── top bar: "Projects" + connection sub-line · new path · settings ──
        ProjectsTopBar(
            projectsLabel,
            if (repo.phase.value == ConnPhase.Ready) Tok.ok else Tok.warn,
            repo.paired.value?.displayName(),
            // the machine line is the doorway into the fleet: tap → Your computers (live overview)
            machineLineModifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenFleet)
                .semantics { contentDescription = computersLabel }.padding(vertical = 2.dp),
            machineTrailing = {
                val waiting = repo.fleetAttention().size
                if (repo.pairedList.size > 1 || waiting > 0) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = Tok.muted, modifier = Modifier.size(13.dp))
                }
                if (waiting > 0) {
                    Spacer(Modifier.width(4.dp))
                    dev.ccpocket.app.ui.fleet.AttentionBadge(waiting)
                }
            },
        ) {
            IconButton(openFolderEntry, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, stringResource(Res.string.new_path_open), tint = Tok.tx2, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            IconButton({ showSettings = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Settings, stringResource(Res.string.settings_open), tint = Tok.tx2, modifier = Modifier.size(20.dp))
            }
        }
        val homeAttention = repo.fleetAttention().size
        if (homeAttention > 0) {
            HomeAttentionCard(homeAttention, onOpenFleet)
        }
        OutlinedTextField(
            query, { query = it }, placeholder = { Text(stringResource(Res.string.filter_hint)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // discoverable entry to open ANY folder — the project list only shows folders that already have
        // history, and the top-bar "+" reads as "new", so this spells out how to reach a fresh folder
        // (#32); both now land in the #152 browser (guests keep the manual sheet)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = openFolderEntry).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Tok.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.new_path_open_row), color = Tok.tx2, fontSize = 13.sp)
        }
        PullToRefreshBox(isRefreshing = repo.refreshing.value, onRefresh = { repo.refreshDirectories() }, modifier = Modifier.fillMaxSize()) {
            when {
                repo.directories.isEmpty() && repo.directoriesLoaded.value && query.isBlank() ->
                    EmptyDirectories(onAdd = { showNewPath = true }) { repo.refreshDirectories() }
                flatRows.isEmpty() && repo.directoriesLoaded.value ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.dir_no_matches), color = Tok.muted, fontSize = 13.sp)
                    }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        items = flatRows,
                        key = { row ->
                            when (row) {
                                is DirRow.Header -> "header:${row.label}"
                                is DirRow.Dir -> "project:${row.direct}:${row.entry.path}"
                            }
                        },
                    ) { row ->
                        when (row) {
                            is DirRow.Header -> Label(row.label)
                            is DirRow.Dir -> ProjectCell(repo, row.entry, showPath = row.showPath, direct = row.direct, onLongPress = { actionTarget = row.entry })
                        }
                    }
                }
            }
        }
    }
        actionTarget?.let { ProjectActionsSheet(repo, it) { actionTarget = null } }
        if (showNewPath) NewPathSheet(
            parent = null,
            agent = repo.defaultAgent.value,
            mode = repo.defaultMode.value,
            onDismiss = { showNewPath = false },
            onOptions = { p -> showNewPath = false; newPathTarget = p },
        ) { p -> showNewPath = false; repo.openSession(p) } // one tap: start with the defaults right away
        // "open a project folder" (issue #152): browse the computer's home and start a session in ANY
        // existing directory — same two bottom actions as NewPathSheet (defaults chip → picker; primary →
        // open right away), and the manual sheet stays one tap away for off-home paths
        if (showDirPicker) DirectoryPickerSheet(
            repo,
            onDismiss = { showDirPicker = false },
            onTypePath = { showDirPicker = false; showNewPath = true },
            onOptions = { p -> showDirPicker = false; newPathTarget = p },
            onStart = { p -> showDirPicker = false; repo.openSession(p) },
        )
        // wants a different agent/mode for the new path → the standard picker, then open the session there
        newPathTarget?.let { path ->
            StartSessionModeSheet(
                workdir = path,
                selected = repo.defaultMode.value,
                agent = repo.defaultAgent.value,
                onPick = { m, a -> newPathTarget = null; repo.setDefaultAgent(a); repo.openSession(path, startMode = m, agent = a) },
                onDismiss = { newPathTarget = null },
            )
        }
    }
}

/** High-priority home entry: decisions waiting on any paired computer outrank project browsing. */
@Composable
private fun HomeAttentionCard(count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(shape)
            .background(Tok.warn.copy(alpha = 0.10f)).border(1.dp, Tok.warn.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PulseDot(Tok.warn, size = 8.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.home_attention_title, count), color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.home_attention_body), color = Tok.tx2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = Tok.warn, modifier = Modifier.size(18.dp))
    }
}

/** Start a session in a folder that has no prior cc-pocket/claude history by typing its absolute path (issue #7).
 *  The daemon validates the path is a readable directory; a not-yet-created folder can be made first via the
 *  in-chat terminal. [onStart] opens the session immediately with the default agent/mode (shown on the chip);
 *  [onOptions] routes the path through the full new-session picker instead. */
@Composable
private fun NewPathSheet(
    parent: String?, agent: AgentKind, mode: PermissionMode,
    onDismiss: () -> Unit, onOptions: (String) -> Unit, onStart: (String) -> Unit,
) {
    // drilled into a folder → seed the field with "<folder>/" and park the cursor at the end, so the user types
    // only the new project's leaf name. sepOf() keeps a Windows daemon's "\" paths native (issue #7).
    var field by remember(parent) {
        val seed = parent?.let { it.trimEnd('/', '\\') + sepOf(it) } ?: ""
        mutableStateOf(TextFieldValue(seed, selection = TextRange(seed.length)))
    }
    val trimmed = field.text.trim()
    // drop a trailing separator so we never open a session at "/foo/bar/", but keep a bare root ("/") intact
    val target = trimTrailingSep(trimmed)
    val looksAbsolute = looksAbsolutePath(trimmed)
    PocketSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 16.dp, top = 4.dp)) {
            Text(stringResource(Res.string.new_path_title), color = Tok.tx, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.new_path_sub), color = Tok.muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(
                field, { field = it },
                placeholder = { Text("/Users/me/new-project", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (looksAbsolute) onStart(target) }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).alpha(if (looksAbsolute) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // default agent+mode preview; tap → the full picker for this path (52dp to pair with the button)
                SessionDefaultsChip(agent, mode, Modifier.height(52.dp), enabled = looksAbsolute) { onOptions(target) }
                SheetButton(
                    stringResource(Res.string.new_path_start),
                    Modifier.weight(1f),
                    bg = Tok.accent, fg = Tok.base,
                ) { if (looksAbsolute) onStart(target) }
            }
        }
    }
}

/** "/" autocomplete: the query while the user is still typing the command word (no space yet), else null. */
internal fun slashQueryOf(input: String): String? =
    input.takeIf { it.startsWith("/") && ' ' !in it && '\n' !in it }?.drop(1)

/** What picking a command puts in the composer. Trailing space always: it closes the menu
 *  ([slashQueryOf] bails on a space) and leaves the cursor ready for arguments; send trims it off
 *  a bare command. Shared by the mobile composer and desktop ChatPane. */
internal fun SlashCommand.completion(): String = "/$name "

/** Matching commands for [query], prefix matches first — shared by the mobile composer and desktop ChatPane. */
internal fun slashSuggestions(query: String?, commands: List<SlashCommand>): List<SlashCommand> =
    if (query == null) emptyList()
    else commands.filter { it.name.contains(query, ignoreCase = true) }
        .sortedBy { !it.name.startsWith(query, ignoreCase = true) }

/** Glued-to-newest tracker for a transcript list: a user scroll away unpins, scrolling back to the very
 *  bottom re-pins. Shared by the mobile ChatScreen and desktop ChatPane — the unpin heuristic is subtle
 *  and has been tuned before; keep it in one place.
 *
 *  [userGesturesOnly] (touch platforms): only a real drag (and its fling tail) may change the pin.
 *  Programmatic follows — the ime-follow and stream-follow snaps — briefly sample as "scrolling & not
 *  at bottom" while the keyboard resizes the viewport, which used to permanently unpin after the first
 *  keyboard open. Desktop passes false: mouse-wheel scrolls emit no DragInteraction, and with no ime
 *  there is no corrupting resize in the first place. */
@Composable
internal fun rememberBottomPinned(
    listState: LazyListState,
    vararg resetKeys: Any?,
    userGesturesOnly: Boolean = true,
    initialPinned: Boolean = true,
): MutableState<Boolean> {
    val pinned = remember(*resetKeys) { mutableStateOf(initialPinned) }
    // keyed on the pinned INSTANCE too: resetKeys mint a fresh state, and a collector keyed only on
    // the list would keep writing the previous one (desktop resets per selected session)
    LaunchedEffect(listState, userGesturesOnly, pinned) {
        var userDriven = !userGesturesOnly
        if (userGesturesOnly) launch {
            listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) userDriven = true }
        }
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canFwd) ->
                if (scrolling && userDriven) pinned.value = !canFwd
                if (!scrolling && userGesturesOnly) userDriven = false // gesture + fling fully settled
            }
    }
    return pinned
}

/** Leaf recomposition scope for the keyboard-follow: reads the ime inset in composition (required on
 *  iOS) so the per-frame invalidation during the keyboard animation stays inside this empty leaf
 *  instead of re-executing the whole ChatScreen. ONE collector scrolls (no restart per frame). */
@Composable
private fun ImeFollower(listState: LazyListState, repo: PocketRepository, pinned: () -> Boolean) {
    val imeBottom = rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current))
    LaunchedEffect(listState) {
        snapshotFlow { imeBottom.value }.collectLatest { bottom ->
            if (pinned() && bottom > 0 && repo.messages.isNotEmpty()) {
                // IME insets animate frame-by-frame. Scrolling on every frame forces the transcript
                // through repeated measure/layout passes and makes typing feel sticky, especially on
                // iOS. collectLatest + this short settle window collapses the animation into one
                // correction at its current resting height; a newer inset cancels the pending one.
                delay(PocketMotion.imeSettleMs)
                if (pinned()) scrollToTranscriptEnd(listState, repo.messages.lastIndex)
            }
        }
    }
}

/** Scroll to the visual transcript tail, including synthetic live-status rows after [messageLastIndex]. */
private suspend fun scrollToTranscriptEnd(listState: LazyListState, messageLastIndex: Int) {
    if (messageLastIndex < 0) return
    val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(messageLastIndex)
    listState.scrollToItem(last, Int.MAX_VALUE)
}

/** Tap a project: jump straight into its live session when one is running, else open its session list.
 *  The resume pins the session's OWN backend (liveAgent) — the default-agent preference must not decide
 *  how someone else's live session is re-opened. */
private fun PocketRepository.openProject(e: DirectoryEntry) {
    val sid = e.activeSessionId
    if (e.open && sid != null) openSession(e.path, sid, title = e.activeSessionTitle, agent = liveAgent(e)) else listSessions(e.path)
}

/** A project row: jumps into the live session (when [direct] and running) or opens its session list. */
@Composable
private fun ProjectCell(repo: PocketRepository, e: DirectoryEntry, showPath: Boolean, direct: Boolean, onLongPress: (() -> Unit)? = null) {
    val sid = e.activeSessionId
    val pinned = repo.isPinned(e.path)
    if (direct && e.open && sid != null) {
        // the 历史 badge lists this project's sessions (issue #49) — the row itself keeps auto-resuming
        LiveProjectCell(e, pinned, onLongPress, onBrowse = { repo.listSessions(e.path) }) { repo.openProject(e) }
    }
    else if (e.recentSessions.isNotEmpty()) ProjectConversationCard(repo, e, pinned, onLongPress)
    else DirCell(
        e.latestSessionTitle?.takeIf { it.isNotBlank() } ?: e.name.ifBlank { e.path },
        if (showPath) tilde(e.path) else null, indent = false, pinned = pinned, onLongPress = onLongPress,
    ) { repo.listSessions(e.path) }
}

/** Happy-inspired project group: deterministic avatar + directory header, with recent conversations inside
 * one clipped bordered card. Implemented in Compose and cc-pocket tokens rather than copying RN styling. */
@Composable
private fun ProjectConversationCard(
    repo: PocketRepository,
    e: DirectoryEntry,
    pinned: Boolean,
    onLongPress: (() -> Unit)?,
) {
    var confirmDelete by remember(e.path) { mutableStateOf<SessionSummary?>(null) } // long-pressed conversation row
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = { repo.listSessions(e.path) }, onLongClick = onLongPress)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectAvatar(e.path, e.recentSessions.firstOrNull()?.agent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name.ifBlank { folderName(e.path) }, color = Tok.tx, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TailPathText(e.path, color = Tok.muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
            }
            if (pinned) { PinGlyph(); Spacer(Modifier.width(5.dp)) }
            IconButton({ repo.openSession(e.path) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Add, "New session", tint = Tok.tx2, modifier = Modifier.size(18.dp))
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tok.surface)
                .border(1.dp, Tok.hair, RoundedCornerShape(12.dp)),
        ) {
            e.recentSessions.forEachIndexed { index, session ->
                val current = repo.workdir.value == e.path && repo.sessionKey.value == session.sessionId
                Row(
                    Modifier.fillMaxWidth().background(if (current) Tok.accent.copy(alpha = 0.09f) else Color.Transparent).combinedClickable(
                        onClick = { repo.openSession(e.path, session.sessionId, title = session.title, agent = session.agent ?: AgentKind.CLAUDE) },
                        onLongClick = { confirmDelete = session },
                    ).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val active = session.live || session.busy || session.waitingPermission || session.executing
                    val statusColor = when {
                        session.waitingPermission -> Tok.warn
                        session.busy -> Tok.info
                        session.executing -> Tok.accent
                        session.live -> Tok.ok
                        else -> Tok.muted
                    }
                    Box(Modifier.width(15.dp), contentAlignment = Alignment.CenterStart) {
                        if (session.waitingPermission || session.busy || session.executing) PulseDot(statusColor, 7.dp)
                        else if (session.live) Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                        else Box(Modifier.size(6.dp).clip(CircleShape).background(Tok.muted.copy(alpha = 0.55f)))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.title, color = if (active || current) Tok.tx else Tok.tx2, fontSize = 13.5.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                session.waitingPermission -> stringResource(Res.string.session_status_permission)
                                session.busy -> stringResource(Res.string.session_status_background)
                                session.executing -> stringResource(Res.string.session_status_thinking)
                                session.live -> stringResource(Res.string.session_status_idle)
                                else -> relativeTime(session.lastModified)
                            },
                            color = if (active) statusColor else Tok.muted, fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    AgentBadge(session.agent, gap = 6.dp)
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = Tok.muted, modifier = Modifier.size(16.dp))
                }
                if (index < e.recentSessions.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 29.dp).height(1.dp).background(Tok.hair))
            }
            if (e.recentSessionsTotal > e.recentSessions.size) {
                Box(Modifier.fillMaxWidth().padding(start = 29.dp).height(1.dp).background(Tok.hair))
                Row(
                    Modifier.fillMaxWidth().clickable { repo.listSessions(e.path) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(15.dp))
                    Text(
                        stringResource(Res.string.project_view_all, e.recentSessionsTotal), color = Tok.accent,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = Tok.accent, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Tok.raised,
            titleContentColor = Tok.tx,
            textContentColor = Tok.tx2,
            title = { Text(stringResource(Res.string.session_delete_title)) },
            text = { Text(stringResource(Res.string.session_delete_confirm, s.title), color = Tok.tx2, fontSize = 14.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton({ confirmDelete = null; repo.deleteSessionFromProject(e.path, s) }) {
                    Text(stringResource(Res.string.session_delete_action), color = Tok.danger)
                }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(Res.string.cancel), color = Tok.muted) } },
        )
    }
}

@Composable
private fun ProjectAvatar(seed: String, agent: AgentKind?) {
    val colors = listOf(Tok.accent, Tok.codex, Tok.info, Tok.ok, Tok.warn)
    val index = (seed.hashCode().toLong().let { if (it < 0) -it else it } % colors.size).toInt()
    val a = colors[index]
    val b = colors[(index + 2) % colors.size]
    // the badge lives OUTSIDE the clipped avatar circle: drawn inside it, the parent's CircleShape
    // clip sliced the badge's outer half off (it sits at the circle's edge by construction)
    Box(Modifier.size(38.dp)) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(15.dp).clip(CircleShape).background(Tok.surface)
                .border(1.dp, Tok.hair, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AgentGlyph(agent ?: AgentKind.CLAUDE, color = agentColor(agent ?: AgentKind.CLAUDE), size = 11)
        }
    }
}

/** Long-press a project → pin it to the top, or unpin it. Small sheet, mirrors the app's other actions. */
@Composable
private fun ProjectActionsSheet(repo: PocketRepository, e: DirectoryEntry, onDismiss: () -> Unit) {
    val pinned = repo.isPinned(e.path)
    PocketSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp, top = 4.dp)) {
            Text(e.name.ifBlank { e.path }, color = Tok.tx, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TailPathText(e.path, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tok.surface)
                    .clickable { repo.togglePin(e.path); onDismiss() }.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, null, tint = Tok.accent, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (pinned) Res.string.unpin_project else Res.string.pin_project),
                    color = Tok.tx, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** A small filled pin marking a project that's pinned to the top. */
@Composable
private fun PinGlyph() = Icon(Icons.Filled.PushPin, null, tint = Tok.accent, modifier = Modifier.size(13.dp))

/** The terracotta "history" pill shown on a dir/folder/leaf that has Claude history. (internal: the
 *  #152 DirectoryPicker stamps the same pill on browsed folders that are already projects.) */
@Composable
internal fun HistoryBadge(onClick: (() -> Unit)? = null) {
    val base = Modifier.clip(RoundedCornerShape(999.dp)).background(Tok.accent.copy(alpha = 0.14f))
    Text(
        stringResource(Res.string.history_badge), color = Tok.accent, fontSize = 10.5.sp,
        modifier = (if (onClick != null) base.heightIn(min = 44.dp).clickable(onClick = onClick) else base)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Path breadcrumb shown when drilled into a subfolder: back ‹ + tappable mono segments (current bolded).
 *  (internal: the #152 DirectoryPicker renders the same crumb over its home-anchored browse.) */
@Composable
internal fun Breadcrumb(segs: List<String>, onUp: () -> Unit, onSegment: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("‹", color = Tok.tx2, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onUp).padding(end = 2.dp))
        segs.forEachIndexed { i, s ->
            val last = i == segs.lastIndex
            Text(
                s, color = if (last) Tok.tx else Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clickable(enabled = !last) { onSegment(i) },
            )
            if (!last) Text("›", color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DirCell(name: String, path: String?, indent: Boolean, pinned: Boolean = false, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp)).background(Tok.surface).combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Tok.tx, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
            if (path != null) Text(path, color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
        }
        if (pinned) PinGlyph()
    }
}

/** A live session row: the session title leads, the folder + branch demote to metadata — tap resumes it.
 *  [onBrowse] is the secondary affordance (issue #49): open the project's session LIST instead of the
 *  live session, so a running project's history stays reachable — the row tap only ever auto-resumes. */
@Composable
private fun LiveProjectCell(e: DirectoryEntry, pinned: Boolean, onLongPress: (() -> Unit)?, onBrowse: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                e.activeSessionTitle ?: stringResource(Res.string.session_fallback), color = Tok.tx, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (pinned) { Spacer(Modifier.width(6.dp)); PinGlyph() }
            if (onBrowse != null && e.hasSessions) { Spacer(Modifier.width(8.dp)); HistoryBadge(onClick = onBrowse) }
            Spacer(Modifier.width(8.dp))
            val active = e.executing || e.busy // background work counts as "running" even when the turn is idle
            if (active) {
                PulseDot(Tok.accent)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                stringResource(if (active) Res.string.running else Res.string.idle),
                color = if (active) Tok.accent else Tok.muted, fontSize = 11.sp,
            )
        }
        Text(
            buildString { append(e.name); e.gitBranch?.let { append(" · ⑂ ").append(it) } },
            color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Removable filter chip pinned atop the Sessions list when a single agent is selected (issue #31). */
@Composable
private fun AgentFilterChip(filter: String, onClear: () -> Unit) {
    val color = when (filter) { "codex" -> Tok.codex; "cursor" -> Tok.info; else -> Tok.accent }
    val label = stringResource(
        when (filter) { "codex" -> Res.string.af_codex_only; "cursor" -> Res.string.af_cursor_only; else -> Res.string.af_claude_only },
    )
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClear).heightIn(min = 44.dp)
            .padding(start = 11.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text("✕", color = color, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun SessionsScreen(repo: PocketRepository) { // internal: driven end-to-end by MobileNewSessionUiTest (demo mode)
    val dir = repo.sessionsDir.value ?: return
    var pickMode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SessionSummary?>(null) } // long-pressed row awaiting the delete confirm
    // an open is in flight (the screen only switches once the daemon answers with the live convo).
    // Repo-owned so every entry point is guarded: entries disable — a double-tap can't open two fresh
    // sessions — and the repo clears it on SessionLive/PocketError (8s safety net).
    val starting = repo.opening.value
    val archived = repo.sessionsArchived.value
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) { SettingsScreen(repo, onBack = { showSettings = false }); return } // full-screen, replaces this screen
    val savedScroll = remember(dir) { repo.sessionScrollPosition(dir) }
    val sessionListState = rememberLazyListState(savedScroll.first, savedScroll.second)
    LaunchedEffect(dir, sessionListState) {
        snapshotFlow { sessionListState.firstVisibleItemIndex to sessionListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> repo.saveSessionScrollPosition(dir, index, offset) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Tok.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ repo.backToDirectories() }) { Text("←", color = Tok.tx2, fontSize = 18.sp) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.sessions_title), color = Tok.tx, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) { // connection bar: honest dot (green only when Ready) + workdir
                        PulseDot(if (repo.phase.value == ConnPhase.Ready) Tok.ok else Tok.warn)
                        Spacer(Modifier.width(5.dp))
                        TailPathText(dir)
                    }
                }
                IconButton({ showSettings = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Settings, stringResource(Res.string.settings_open), tint = Tok.tx2, modifier = Modifier.size(20.dp))
                }
            }
            val af = repo.agentFilter.value
            val filtered by remember(repo, archived, af) {
                derivedStateOf {
                    if (archived) repo.sessions.toList() else repo.sessions.filter {
                        when (af) {
                            "claude" -> (it.agent ?: AgentKind.CLAUDE) == AgentKind.CLAUDE
                            "codex" -> it.agent == AgentKind.CODEX
                            "cursor" -> it.agent == AgentKind.CURSOR
                            else -> true
                        }
                    }
                }
            }
            PullToRefreshBox(isRefreshing = repo.sessionsRefreshing.value, onRefresh = { repo.refreshSessions() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                state = sessionListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tok.surface).padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf(false to Res.string.sessions_current, true to Res.string.sessions_archived).forEach { (value, label) ->
                            val selected = archived == value
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .then(if (selected) Modifier.background(Tok.raised) else Modifier)
                                    .clickable(enabled = !repo.sessionsRefreshing.value) { repo.showArchivedSessions(value) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(label), color = if (selected) Tok.tx else Tok.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (!archived && af != "both") item { AgentFilterChip(af) { repo.setAgentFilter("both") } }
                if (!archived) item {
                    // one tap starts right away with the persisted defaults (openSession's own fallbacks);
                    // the trailing chip shows those defaults and opens the full agent+mode picker instead
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tok.accent.copy(alpha = 0.16f))
                            .clickable(enabled = !starting) { repo.openSession(dir) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.new_session_cta), color = Tok.accent, fontWeight = FontWeight.SemiBold)
                                if (starting) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(Modifier.size(12.dp), color = Tok.accent, strokeWidth = 1.5.dp)
                                }
                            }
                            Text(
                                stringResource(Res.string.start_agent_in, agentName(repo.defaultAgent.value), tilde(dir)),
                                color = Tok.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        SessionDefaultsChip(repo.defaultAgent.value, repo.defaultMode.value, enabled = !starting) { pickMode = true }
                    }
                }
                items(filtered, key = { it.sessionId }) { s ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tok.surface)
                            // delete rides a long-press (kept off the tap path); running sessions refuse daemon-side
                            .combinedClickable(
                                onClick = {
                                    if (archived) repo.setSessionArchived(dir, s.sessionId, false)
                                    else repo.openSession(dir, s.sessionId, title = s.title, agent = s.agent ?: AgentKind.CLAUDE)
                                },
                                onLongClick = { if (!archived) confirmDelete = s },
                            ).padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.title, color = Tok.tx, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            AgentBadge(s.agent, gap = 8.dp) // shows only for Codex (so resume opens the right backend)
                            if (s.agent == AgentKind.CODEX && !s.live && !s.busy) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(if (archived) Res.string.session_restore_action else Res.string.session_archive_action),
                                    color = Tok.accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                        repo.setSessionArchived(dir, s.sessionId, !archived)
                                    }.padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                            if (s.live || s.busy) { // running, or idle with background work still going
                                Spacer(Modifier.width(8.dp))
                                PulseDot(Tok.ok)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.running), color = Tok.ok, fontSize = 11.sp)
                            }
                        }
                        if (s.firstPrompt.isNotBlank()) Text(
                            s.firstPrompt, color = Tok.tx2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            "💬 ${s.messageCount} · ⑂ ${s.gitBranch ?: "-"} · ${relativeTime(s.lastModified)}",
                            color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (archived && filtered.isEmpty() && !repo.sessionsRefreshing.value) item {
                    Text(
                        stringResource(Res.string.sessions_archived_empty), color = Tok.muted, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 30.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            }
        }
        confirmDelete?.let { s ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                containerColor = Tok.raised,
                titleContentColor = Tok.tx,
                textContentColor = Tok.tx2,
                title = { Text(stringResource(Res.string.session_delete_title)) },
                text = { Text(stringResource(Res.string.session_delete_confirm, s.title), color = Tok.tx2, fontSize = 14.sp, lineHeight = 21.sp) },
                confirmButton = {
                    TextButton({ confirmDelete = null; repo.deleteSession(dir, s) }) {
                        Text(stringResource(Res.string.session_delete_action), color = Tok.danger)
                    }
                },
                dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(Res.string.cancel), color = Tok.muted) } },
            )
        }
        if (pickMode) {
            StartSessionModeSheet(
                workdir = dir,
                selected = repo.defaultMode.value,
                agent = repo.defaultAgent.value,
                onPick = { m, a -> pickMode = false; repo.setDefaultAgent(a); repo.openSession(dir, startMode = m, agent = a) },
                onDismiss = { pickMode = false },
            )
        }
        if (!pickMode && confirmDelete == null) EdgeSwipeBack { repo.backToDirectories() }
    }
}

@Composable
private fun ChatScreen(repo: PocketRepository, onOpenFleet: () -> Unit = {}, onOpenInbox: () -> Unit = {}) {
    // Restore the composer draft (keyed per conversation, workdir for a brand-new session). Re-inits on a
    // REAL switch only — keyed off composerEpoch, NOT draftKey (#29 semantics kept): the key chain flips in
    // place mid-typing (brand-new session materializing, forked resume corrected by SessionLive), and
    // re-reading the ≤400ms-stale draft then yanked the live text out from under the IME — on the iOS pinyin
    // keyboard that committed the space-segmented marked text as raw letters, "claude"→"c l a u d e" (#108,
    // #93's wild signature). The debounced saver below re-homes the text under the flipped key.
    val draftKey = repo.composerKey()
    var input by remember(repo.composerEpoch.value) { mutableStateOf(repo.draftFor(draftKey)) }
    var viewer by remember { mutableStateOf<Pair<List<ByteArray>, Int>?>(null) } // tapped sent images → full-screen
    var videoViewer by remember { mutableStateOf<dev.ccpocket.app.data.SentFile?>(null) } // tapped sent video → player (issue #98)
    var showSwitcher by remember { mutableStateOf(false) } // machine name in the connection bar → switch computer
    var showModeSheet by remember { mutableStateOf(false) }
    var confirmBypassOnModeOpen by remember { mutableStateOf(false) }
    var showSessionInfo by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var quickActionSection by remember { mutableStateOf(QuickActionSection.MAIN) }
    var showBgJobs by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var showChangedFiles by remember { mutableStateOf(false) }
    if (showTerminal) { TerminalScreen(repo) { showTerminal = false }; return } // full-screen, replaces chat (issue #3)
    if (repo.viewedFilePath.value != null) { // changed-file viewer (issue #36); back → the still-open files list, ✕ → chat (issue #53)
        FileViewerScreen(repo, onExit = if (showChangedFiles) ({ repo.closeFileViewer(); showChangedFiles = false }) else null) { repo.closeFileViewer() }
        return
    }
    // platform picker resizes/compresses on-device; the repo budgets the picked photos against the 256 KiB frame
    val launchPicker = rememberImageAttacher { added -> repo.attachImages(added) }
    val launchFilePicker = rememberFileAttacher { picked -> repo.attachFiles(picked) }
    val launchVideoPicker = rememberVideoAttacher { picked -> repo.attachFiles(picked) }
    var attachSheet by remember { mutableStateOf(false) }
    val scrollKey = repo.convoId.value ?: draftKey ?: "new:${repo.workdir.value.orEmpty()}"
    // A conversation always opens at its live tail. Restoring an old reading position made a resumed
    // session look stale and hid new work below the fold; history remains available by scrolling up.
    val listState = remember(scrollKey) { LazyListState() }
    // stick to the bottom only while the user is there ("pinned"); scrolling up unpins and shows
    // the Jump-to-latest pill instead of yanking the viewport down on every streamed chunk.
    var pinned by rememberBottomPinned(
        listState, scrollKey,
        initialPinned = true,
    )
    // Snapshot the transcript when the user leaves the bottom. The latest assistant bubble grows in
    // place while streaming (the list size does not change), so retain both count and tail identity:
    // the jump affordance can honestly signal fresh activity in either case.
    var unseenAnchor by remember(repo.convoId.value) { mutableStateOf<Pair<Int, ChatItem?>?>(null) }
    LaunchedEffect(pinned) {
        unseenAnchor = if (pinned) null else unseenAnchor ?: (repo.messages.size to repo.messages.lastOrNull())
    }
    // keep the message list hidden until it's first parked at the bottom, so opening a session with
    // history doesn't flash the top then visibly scroll down. Resets per session (convoId); a short
    // grace reveals an empty/new session that has no history to position on.
    var landed by remember(repo.convoId.value) { mutableStateOf(false) }
    LaunchedEffect(repo.convoId.value) { delay(180); landed = true }
    // a just-created session opens on an empty chat — focus the composer and raise the keyboard
    // right away instead of making the user tap the field first. openSession arms the flag only
    // for resumeId == null (never on resume/reattach/fleet-follow); consumed here exactly once.
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val chatScope = rememberCoroutineScope()
    fun revealLatest() {
        pinned = true
        chatScope.launch {
            // Let an IME resize or the just-sent user bubble enter the LazyColumn before measuring the tail.
            delay(16)
            scrollToTranscriptEnd(listState, repo.messages.lastIndex)
            landed = true
        }
    }
    LaunchedEffect(repo.convoId.value) {
        if (repo.convoId.value != null && repo.autoFocusComposer.value && !repo.observing.value) {
            repo.autoFocusComposer.value = false
            delay(250) // let the screen land (180ms grace above) before the IME animates in
            composerFocus.requestFocus()
            keyboard?.show()
        }
    }
    // persist the composer draft per project (debounced) so leaving mid-message doesn't lose it
    LaunchedEffect(input, draftKey) { delay(400); repo.saveDraft(draftKey, input) }
    // Follow streaming output at a bounded cadence. Keying a LaunchedEffect directly with the last
    // message restarted it for every protocol chunk; each restart immediately forced a list layout.
    // snapshotFlow conflates changes while this collector is delayed, capping layout/scroll follow at
    // ~30 Hz. The window still renders at 60/120 Hz; this only bounds expensive tail remeasurement.
    LaunchedEffect(listState) {
        snapshotFlow { Triple(repo.messages.size, repo.messages.lastOrNull(), repo.streaming.value) }.collect {
            delay(PocketMotion.streamSampleMs)
            if (pinned && repo.messages.isNotEmpty()) {
                scrollToTranscriptEnd(listState, repo.messages.lastIndex)
                landed = true
            }
        }
    }
    // keyboard-follow lives in its own leaf composable: the ime inset must be a COMPOSITION read
    // (iOS misses the animation otherwise), and reading it here would re-execute all of ChatScreen
    // every animation frame — the leaf confines that per-frame invalidation to itself.
    ImeFollower(listState, repo) { pinned }
    val focus = LocalFocusManager.current
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() } // scrolling dismisses the keyboard
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Tok.surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ repo.saveDraft(repo.workdir.value, input); repo.backToBrowse() }) { Text("←", color = Tok.tx2, fontSize = 18.sp) }
                Column(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { showSessionInfo = true }.padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // session title leads (design); the generic "Chat" only before the first prompt names it
                    Text(
                        repo.chatTitle.value ?: stringResource(Res.string.chat_title),
                        color = Tok.tx, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) { // connection bar: honest dot (green only when Ready) + machine · folder · model
                        PulseDot(if (repo.phase.value == ConnPhase.Ready) Tok.ok else Tok.warn)
                        Spacer(Modifier.width(5.dp))
                        // ONE style for every segment — the machine name used to force lineHeight=11 while
                        // the path/model kept the font's default, so the three never shared a baseline
                        val metaStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                        // which computer this conversation lives on — its own tap target: switch machines
                        // without leaving the chat (the surrounding column still opens session info)
                        repo.paired.value?.let { d ->
                            Text(
                                d.displayName(), color = Tok.tx2, style = metaStyle, maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showSwitcher = true }
                                    .padding(horizontal = 2.dp),
                            )
                            Text("·", color = Tok.muted, style = metaStyle, modifier = Modifier.padding(horizontal = 3.dp))
                        }
                        // just the project FOLDER: the full path routinely ate the whole line on a phone,
                        // and it's one tap away in the session-info sheet (this row opens it)
                        Text(
                            folderName(repo.workdir.value), color = Tok.tx2, style = metaStyle,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        // Model/effort/mode already live in ComposerStatusBar. Repeating the model here
                        // crowded the primary navigation row without adding a new decision.
                        AgentBadge(repo.sessionAgent.value) // shows only for Codex; Claude stays quiet
                    }
                }
                if (!repo.observing.value) {
                    // execution state gets its own persistent header chip (issue #52): re-entering a mid-turn
                    // session showed nothing alive — the connection dot only says "linked", not "working",
                    // and the composer stays enabled (queueing), which read as "disconnected".
                    if (repo.streaming.value) Row(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(Tok.raised)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        PulseDot(Tok.accent)
                        Text(stringResource(Res.string.chat_running), color = Tok.accent, fontSize = 11.sp)
                    }
                    // mode switching moved into the ⋯ quick-actions sheet — the persistent badge was one
                    // more thing crowding the header for a setting touched a few times per session
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable {
                            quickActionSection = QuickActionSection.MAIN
                            showQuickActions = true
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋯", color = Tok.tx2, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Box(Modifier.weight(1f)) {
                // reserve a gutter below the last message so the floating context pill sits in empty
                // space (with a gap) instead of covering the last line + its copy button (issue #15).
                // The pill's MEASURED height drives the reserve (issue #81): a fixed 30.dp only cleared
                // it at font-scale 1 — the pill grows with the system/app text size while a hardcoded
                // gutter doesn't, so a long reply's tail slid under the pill on larger text. Measuring
                // keeps the pill floating (no layout footprint → never pushes the composer) yet always
                // leaves the last line above it. Falls back to the old gutter until the pill measures.
                val bottomGutter = 16.dp
                LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp).graphicsLayer { alpha = if (landed) 1f else 0f }
                        .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
                    state = listState, verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = bottomGutter),
                ) {
                    if (repo.messages.isEmpty() && !repo.streaming.value) {
                        item {
                            EmptyChatStarter { suggestion ->
                                input = suggestion
                                composerFocus.requestFocus()
                                keyboard?.show()
                            }
                        }
                    }
                    items(repo.messages) { m ->
                        // a prompt the daemon hasn't acknowledged while the link is down — or while the link
                        // CLAIMS up but receipts stalled past the deadline (issue #78, multi-computer links):
                        // say so under the bubble instead of letting it look sent (issue #41 — frames queue
                        // silently offline)
                        val undelivered = m is ChatItem.User && m.pending && (repo.phase.value != ConnPhase.Ready || repo.sendStalled.value)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MessageItem(
                                m,
                                live = repo.streaming.value && m === repo.messages.lastOrNull(),
                                onOpenImages = { imgs, i -> viewer = imgs to i },
                                onOpenVideo = { videoViewer = it },
                            )
                            when {
                                undelivered -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    PulseDot(Tok.warn, size = 5.dp)
                                    Text(stringResource(Res.string.msg_pending_undelivered), color = Tok.warn, fontSize = 11.sp)
                                }
                                // link is up but the daemon hasn't receipted yet (issue #66): quiet "sending…"
                                // after a short grace so a normal instant ack never flashes it
                                m is ChatItem.User && m.pending -> {
                                    var slow by remember(m) { mutableStateOf(false) }
                                    LaunchedEffect(m) { delay(1200); slow = true }
                                    if (slow) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        PulseDot(Tok.muted, size = 5.dp)
                                        Text(stringResource(Res.string.msg_sending), color = Tok.muted, fontSize = 11.sp)
                                    }
                                }
                                // receipted (issue #66) — shows until the reply starts streaming (this bubble
                                // stops being the last item), so a slow agent start still reads as "it got there"
                                m is ChatItem.User && m.delivered && m == repo.messages.lastOrNull() ->
                                    Text("✓ " + stringResource(Res.string.msg_delivered), color = Tok.muted, fontSize = 11.sp)
                            }
                        }
                    }
                    // a running turn ALWAYS ends the stream with something alive (issue #52 — desktop's
                    // blinking tail cursor equivalent): the full "Thinking…" row when nothing live is on
                    // screen yet, else just a pulsing dot — a replayed Assistant tail (re-entered mid-turn
                    // session) doesn't move on its own, so without this the screen looks dead.
                    val last = repo.messages.lastOrNull()
                    val liveContent = (last is ChatItem.Thinking && last.seconds == null) || last is ChatItem.Assistant
                    when {
                        // delivered, but the agent produced no turn within the deadline (issue #104): the prompt
                        // was swallowed (wedged / mid-relaunch). Offer a resend instead of an endless spinner.
                        repo.turnStalled.value -> item { NoResponseRow { repo.resendStalledPrompt() } }
                        repo.streaming.value -> item { if (liveContent) PulseDot(Tok.accent) else WorkingRow() }
                    }
                }
                if (!pinned) {
                    val pillScope = rememberCoroutineScope()
                    val anchor = unseenAnchor
                    val newItems = (repo.messages.size - (anchor?.first ?: repo.messages.size)).coerceAtLeast(0)
                    val hasFreshActivity = anchor != null && (newItems > 0 || anchor.second != repo.messages.lastOrNull())
                    JumpToLatestPill(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        newItems = newItems,
                        hasFreshActivity = hasFreshActivity,
                    ) {
                        pillScope.launch {
                            val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(last, Int.MAX_VALUE)
                            pinned = true
                        }
                    }
                }
                // approvals waiting on OTHER machines pull you over without reflowing this stream —
                // floats under the connection bar; this machine's own ask keeps its sheet (Fleet ⑤)
                dev.ccpocket.app.ui.fleet.CrossMachineBanner(
                    repo.crossMachineAttention(),
                    onReview = onOpenInbox,
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp).padding(top = 8.dp),
                )
            }
            // session health (issue #65): a degraded session (recent turns all API failures) or a nearly
            // full context window gets a persistent strip right above the composer — warn BEFORE the next
            // prompt goes in, not after it fails
            if (repo.sessionDegraded.value) {
                StatusBanner(Tok.danger, stringResource(Res.string.session_degraded_banner))
            } else {
                val used = repo.contextUsed.value
                val window = repo.contextWindow.value
                if (used != null && window != null && used.toFloat() / window >= 0.9f) {
                    StatusBanner(Tok.warn, stringResource(Res.string.context_high_banner, "${(used.toFloat() / window * 100).toInt()}%"))
                }
            }
            // Claude paused its turn to ask questions — the card docks above the composer; the
            // stream above stays scrollable so the user can re-read context before answering.
            // While one of the card's text fields owns input, the composer hides (design ③).
            var cardOwnsInput by remember(repo.pendingAsk.value?.askId) { mutableStateOf(false) }
            val questionAsk = repo.pendingAsk.value?.takeIf { it.isQuestion }
            questionAsk?.let { ask ->
                val skipMessage = stringResource(Res.string.question_skip_message)
                QuestionCard(
                    ask,
                    onAnswer = { answers, response -> repo.answerQuestions(answers, response) },
                    onSkip = { repo.resolve(Decision.DENY, message = skipMessage) },
                    onOwnsInput = { cardOwnsInput = it },
                )
            }
            if (questionAsk != null && cardOwnsInput) {
                // composer yields while the card's field has the keyboard
            } else if (repo.observing.value) {
                Row(Modifier.fillMaxWidth().background(Tok.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.observing_notice), color = Tok.tx2, fontSize = 13.sp)
                        if (repo.activityEvents.isNotEmpty()) Text(
                            stringResource(Res.string.handoff_activity_summary, repo.activityEvents.size),
                            color = Tok.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Button({ repo.takeOver() }) { Text(stringResource(Res.string.continue_here)) }
                }
            } else {
                val hasReady = repo.hasReadyImages()
                val hasLanded = repo.hasLandedFiles()      // files already in the workspace inbox (issue #90)
                val uploadsBusy = repo.uploadsBusy()       // uploads still moving → send waits
                val voiceState = repo.voice.value
                // the timer stays visible (frozen) through S3, after Recording stopped carrying it
                var recElapsed by remember { mutableStateOf(0L) }
                if (voiceState is VoiceState.Recording) recElapsed = voiceState.elapsedMs
                val slashQuery = slashQueryOf(input)
                val slashCommands by remember(repo) { derivedStateOf { repo.slashCommands.toList() } }
                val suggestions = remember(slashQuery, slashCommands) {
                    slashSuggestions(slashQuery, slashCommands)
                }
                // "@file" completion (issue #75): tap-only and cursor-at-end (the common mobile case) — the
                // daemon browses the session cwd, a folder tap drills in, a file tap inserts its path. Yields
                // to the slash menu when that's showing. sep is the daemon host's separator (Windows-safe).
                val atSep = repo.workdir.value?.let { if (it.contains('\\')) '\\' else '/' } ?: '/'
                val atToken = if (suggestions.isEmpty()) atTokenAt(input, input.length) else null
                val atDir = atToken?.let { atDirOf(it.query, atSep) } ?: ""
                val atLeaf = atToken?.let { atLeafOf(it.query, atSep) } ?: ""
                LaunchedEffect(atToken != null, atDir) {
                    if (atToken != null) { repo.refreshAgencyAgents(); repo.browseFiles(atDir) }
                }
                val atListing = repo.pathListing.value
                val atFileMatches = remember(atListing, atToken, atDir, atLeaf) {
                    if (atToken == null || atListing?.subPath != atDir) emptyList() else atMatches(atListing.entries, atLeaf)
                }
                val agencyAgents by remember(repo) { derivedStateOf { repo.agencyAgents.toList() } }
                val atAgentMatches = remember(agencyAgents, atToken) {
                    val q = atToken?.query.orEmpty()
                    if (atToken == null || q.contains('/') || q.contains('\\')) emptyList()
                    else agencyAgents.filter {
                        q.isEmpty() || it.nameZh.contains(q, true) || it.summaryZh.contains(q, true) ||
                            it.categoryZh.contains(q, true) || it.id.contains(q, true)
                    }.take(50)
                }
                val composerDockShape = RoundedCornerShape(20.dp)
                // The dock and the system-bar inset are one continuous bottom surface. A regular glassPanel
                // double-composited its translucent raised fill and drew a bottom border right above the
                // Home Indicator, splitting the bottom into two visibly different strips in light mode.
                val composerDockFill = Tok.raised.compositeOver(Tok.base)
                Column(
                    Modifier.fillMaxWidth()
                        .padding(6.dp)
                        .shadow(18.dp, composerDockShape, clip = false)
                        .clip(composerDockShape)
                        .background(composerDockFill),
                ) {
                    BackgroundJobsStrip(repo.backgroundJobs) { showBgJobs = true } // ≥1 running bg task → tap to expand
                    ComposerStatusBar(
                        repo = repo,
                        onMode = { confirmBypassOnModeOpen = true; showModeSheet = true },
                    )
                    val capturing = voiceState is VoiceState.Recording || voiceState is VoiceState.Transcribing
                    LaunchedEffect(capturing) { if (capturing) attachSheet = false }
                    if (suggestions.isNotEmpty() && !capturing) {
                        SlashCommandMenu(suggestions) { cmd -> input = cmd.completion() }
                    } else if ((atAgentMatches.isNotEmpty() || atFileMatches.isNotEmpty()) && !capturing) {
                        AtCompletionMenu(
                            agents = atAgentMatches, entries = atFileMatches, dir = atDir, sep = atSep,
                            onAgent = { agent ->
                                atToken?.let { input = input.substring(0, it.at) + "@${agent.nameZh} " + input.substring(it.end) }
                            },
                            onFile = { entry ->
                                atToken?.let { input = input.substring(0, it.at + 1) + atInsertText(atDir, entry, atSep) + input.substring(it.end) }
                            },
                        )
                    }
                    // attach sheet (issue #90/#98): Photo keeps the image flow, File opens the document
                    // picker, Video opens the movie-filtered picker (same chunk-upload into the workspace)
                    if (attachSheet && !capturing) {
                        AttachSheet(
                            onPhoto = { attachSheet = false; launchPicker() },
                            onFile = { attachSheet = false; launchFilePicker() },
                            onVideo = { attachSheet = false; launchVideoPicker() },
                        )
                    }
                    PendingFilesStrip(repo.pendingFiles, onCancel = repo::removePendingFile, onRetry = repo::retryPendingFile)
                    AttachTray(repo.pendingImages, repo::removePendingImage)
                    repo.voiceNotice.value?.let { n ->
                        Text(stringResource(n), color = Tok.tx2, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    }
                    if (capturing) {
                        if (repo.liveDictation.value && voiceState is VoiceState.Recording) {
                            LiveTranscriptField(repo.liveFinal.value, repo.livePartial.value)
                        }
                        RecordingBar(
                            elapsedMs = recElapsed,
                            transcribing = voiceState is VoiceState.Transcribing,
                            levels = repo.voiceLevels,
                            onCancel = repo::cancelVoice,
                            onDone = repo::stopVoice,
                        )
                    } else {
                        val failed = voiceState as? VoiceState.Failed
                        if (failed != null) VoiceErrorChip(failed.detail ?: stringResource(failed.res))
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                            val attachInteraction = remember { MutableInteractionSource() }
                            val attachPressed by attachInteraction.collectIsPressedAsState()
                            // "+" now opens the attach sheet (Photo · File) and rotates into "×" while
                            // it's up (issue #90, design: file-attach.jsx); the image flow is one tap
                            // deeper but unchanged.
                            IconButton(onClick = { attachSheet = !attachSheet }, interactionSource = attachInteraction, modifier = Modifier.size(44.dp)) {
                                AttachPlusGlyph(
                                    open = attachSheet,
                                    tint = if (attachSheet || repo.pendingImages.isNotEmpty() || repo.pendingFiles.isNotEmpty() || attachPressed) Tok.accent else Tok.tx2,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ComposerField(
                                input, {
                                    input = it
                                    // Editing means the user is composing the next turn: keep its context and
                                    // the latest response visible even if they had previously scrolled upward.
                                    if (!pinned) revealLatest()
                                },
                                // mid-turn the field stays enabled (sends queue into the running turn) — say so,
                                // or an editable composer under a "running" session reads as disconnected (issue #52)
                                placeholder = stringResource(
                                    when {
                                        repo.pendingImages.isNotEmpty() || repo.pendingFiles.isNotEmpty() -> Res.string.add_message_hint
                                        repo.streaming.value -> Res.string.message_queued_hint
                                        else -> when (repo.sessionAgent.value ?: AgentKind.CLAUDE) {
                                            AgentKind.CLAUDE -> Res.string.message_claude_hint
                                            AgentKind.CODEX -> Res.string.message_codex_hint
                                            AgentKind.CURSOR -> Res.string.message_cursor_hint
                                        }
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                                focusRequester = composerFocus,
                                onFocused = ::revealLatest,
                            )
                            Spacer(Modifier.width(8.dp))
                            // while a turn runs the ■ stays put; typed text adds Send NEXT TO it instead of
                            // replacing it — mirrors Claude Code, where interrupt (Esc) and queue-a-message
                            // (Enter) coexist. Claude's stream-json input queues a mid-turn user message and
                            // weaves it into the running turn at the next tool boundary (verified on 2.1.201).
                            if (repo.streaming.value && (input.isNotBlank() || hasReady || hasLanded)) {
                                StopButton { repo.cancelTurn() }
                                Spacer(Modifier.width(8.dp))
                            }
                            when {
                                // uploads still moving → send WAITS (spinner ring around a muted arrow,
                                // design: file-attach.jsx) — landing must finish before the @-refs exist
                                uploadsBusy -> {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(Tok.base).border(1.dp, Tok.hair, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SpinnerRing(30.dp, 2.dp)
                                        Icon(SendArrowIcon, null, tint = Tok.muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                // text/image/file staged -> SEND, even mid-turn (claude queues it; see above)
                                input.isNotBlank() || hasReady || hasLanded -> {
                                    val sendLabel = stringResource(Res.string.send)
                                    RoundActionButton(
                                        onClick = {
                                            val t = input.trim()
                                            // a gated send (degraded session, issue #65) returns false — keep the text for the retry
                                            if ((t.isNotBlank() || hasReady || hasLanded) && repo.sendPrompt(t)) {
                                                input = ""
                                                repo.clearDraft(draftKey)
                                                revealLatest()
                                            }
                                        },
                                        filled = true, contentDescription = sendLabel,
                                    ) { Icon(SendArrowIcon, sendLabel, tint = Tok.base, modifier = Modifier.size(18.dp)) }
                                }
                                // generating with an empty composer -> the slot is Stop (interrupts the turn, session stays)
                                repo.streaming.value -> StopButton { repo.cancelTurn() }
                                else -> {
                                    val dictateLabel = stringResource(Res.string.dictate)
                                    RoundActionButton(
                                        onClick = { if (failed != null) repo.retryVoice() else repo.startVoice() },
                                        filled = false, contentDescription = dictateLabel,
                                    ) { Icon(MicIcon, dictateLabel, tint = if (failed != null) Tok.accent else Tok.tx2, modifier = Modifier.size(22.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        viewer?.let { (imgs, idx) -> ImageViewer(imgs, idx) { viewer = null } }
        videoViewer?.let { VideoPlayerOverlay(it) { videoViewer = null } } // issue #98
        if (repo.micPermissionSheet.value) {
            MicPermissionSheet(
                onOpenSettings = { openAppSettings(); repo.dismissMicSheet() },
                onDismiss = repo::dismissMicSheet,
            )
        }
        if (showModeSheet) {
            ModeSheet(
                current = repo.mode.value, rules = repo.allowRules, switching = repo.switching.value, workdir = repo.workdir.value,
                confirmBypassInitially = confirmBypassOnModeOpen,
                onSelect = { repo.switchMode(it) }, // keep the sheet open so the "switching" state shows
                onClearRule = { repo.clearRule(it) }, onClearAll = { repo.clearAllRules() },
                onDismiss = { showModeSheet = false; confirmBypassOnModeOpen = false },
            )
        }
        if (showSessionInfo) SessionInfoSheet(repo) { showSessionInfo = false }
        if (showQuickActions) {
            QuickActionsSheet(
                repo,
                onTerminal = { showTerminal = true },
                onMode = { confirmBypassOnModeOpen = false; showModeSheet = true },
                onFiles = { repo.fetchChangedFiles(); showChangedFiles = true },
                initialSection = quickActionSection,
            ) { showQuickActions = false }
        }
        if (showChangedFiles) ChangedFilesSheet(repo, onOpen = { repo.openChangedFile(it) }) { showChangedFiles = false }
        if (showBgJobs) BackgroundJobsSheet(repo.backgroundJobs, onStop = { repo.stopBackgroundJob(it.id) }) { showBgJobs = false }
        if (showSwitcher) dev.ccpocket.app.ui.fleet.MachineSwitcherSheet(repo, onDismiss = { showSwitcher = false }, onManage = onOpenFleet)
        if (viewer == null && !repo.micPermissionSheet.value && !showModeSheet && !showSessionInfo &&
            !showQuickActions && !showChangedFiles && !showBgJobs && !showSwitcher
        ) {
            EdgeSwipeBack {
                repo.saveDraft(draftKey, input)
                repo.backToBrowse()
            }
        }
    }
}

/** iOS-style interactive affordance shared by the two drill-down levels. Keeping the hit target on the
 *  extreme edge avoids stealing horizontal gestures from messages, code blocks, and sheets. */
@Composable
internal fun EdgeSwipeBack(onBack: () -> Unit) {
    val latestBack by rememberUpdatedState(onBack)
    val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var distance by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxHeight().width(24.dp).pointerInput(thresholdPx) {
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onDragCancel = { distance = 0f },
                onDragEnd = {
                    if (distance >= thresholdPx) latestBack()
                    distance = 0f
                },
                onHorizontalDrag = { change, amount ->
                    distance = (distance + amount).coerceAtLeast(0f)
                    change.consume()
                },
            )
        },
    )
}

/** The "/" autocomplete panel above the composer: tap a row to fill the input with the command. */
@Composable
private fun SlashCommandMenu(commands: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).background(Tok.raised).padding(vertical = 4.dp)) {
        items(commands) { cmd ->
            Column(Modifier.fillMaxWidth().clickable { onPick(cmd) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "/${cmd.name}", color = Tok.accent, fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    cmd.argumentHint?.let {
                        Text(" $it", color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(
                            when (cmd.source) {
                                CommandSource.BUILTIN -> Res.string.cmd_source_builtin
                                CommandSource.USER -> Res.string.cmd_source_user
                                CommandSource.PROJECT -> Res.string.cmd_source_project
                                CommandSource.SKILL -> Res.string.cmd_source_skill
                            },
                        ),
                        color = Tok.muted, fontSize = 10.sp,
                    )
                }
                if (cmd.description.isNotBlank()) {
                    Text(cmd.description, color = Tok.tx2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The composer's "@file" completion panel (issue #75): tap a row to insert its relative path — a folder
 *  drills in (trailing separator, the daemon re-lists it), a file completes the reference. */
@Composable
private fun AtCompletionMenu(
    agents: List<dev.ccpocket.protocol.AgencyAgent>,
    entries: List<dev.ccpocket.protocol.PathEntry>,
    dir: String,
    sep: Char,
    onAgent: (dev.ccpocket.protocol.AgencyAgent) -> Unit,
    onFile: (dev.ccpocket.protocol.PathEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Tok.raised)) {
        Text(
            "@ " + dir.ifEmpty { "." },
            color = Tok.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(bottom = 4.dp)) {
            if (agents.isNotEmpty()) {
                item { Text("AGENTS", color = Tok.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                items(agents, key = { "agent:${it.id}" }) { agent ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAgent(agent) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(agent.emoji ?: "◎", fontSize = 16.sp, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.nameZh, color = Tok.tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(agent.summaryZh, color = Tok.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(agent.categoryZh, color = Tok.muted, fontSize = 10.sp)
                    }
                }
            }
            if (entries.isNotEmpty()) item { Text("文件", color = Tok.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            items(entries) { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable { onFile(entry) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (entry.isDir) "▸" else " ", color = Tok.muted,
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(16.dp),
                    )
                    Text(
                        entry.name + if (entry.isDir) sep.toString() else "",
                        color = if (entry.isDir) Tok.tx else Tok.tx2,
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        fontWeight = if (entry.isDir) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    m: ChatItem,
    live: Boolean = false,
    onOpenImages: (List<ByteArray>, Int) -> Unit = { _, _ -> },
    onOpenVideo: (dev.ccpocket.app.data.SentFile) -> Unit = {},
) {
    when (m) {
        // accent-rail user turn (design: User Turn Styles.html, direction B) — the terracotta
        // rail + warm tint mark "what I said" as a quote; no label, assistant flow untouched
        is ChatItem.User -> {
            // Exact Happy "blue" palette: #E8F2FF light / #17324D dark, with its matching indicator.
            val dark = isSystemInDarkTheme()
            val bubbleBlue = if (dark) Color(0xFF17324D) else Color(0xFFE8F2FF)
            val indicatorBlue = if (dark) Color(0xFF64B5FF) else Color(0xFF0A84FF)
            Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 4.dp))
                .background(bubbleBlue),
        ) {
            Box(Modifier.fillMaxHeight().width(2.dp).clip(RoundedCornerShape(2.dp)).background(indicatorBlue))
            Column(
                Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (m.images.isNotEmpty()) SentImages(m.images) { i -> onOpenImages(m.images, i) }
                // uploaded files (issue #90): chip per file with its @inbox landing path. Videos (issue
                // #98) render as a 16:9 card that opens the player; both share the "in workspace" grammar.
                m.files.forEach { f ->
                    if (isVideoAttachment(f.mediaType, f.name)) SentVideoCard(f) { onOpenVideo(f) } else SentFileChip(f)
                }
                if (m.text.isNotBlank()) {
                    // renderClip: this row is a single Text paragraph — an ~800 KB replayed prompt
                    // (skill injection) OOM'd iOS on open; render a prefix, copy keeps the whole thing
                    val shown = renderClip(m.text)
                    SelectionContainer { Text(shown, color = Tok.tx, fontSize = 14.sp * LocalFontScale.current) } // drag-select to copy (no native toolbar on iOS)
                    if (shown.length < m.text.length) TruncatedNote(m.text.length)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        CopyChip(m.text) // one-tap copy — the reliable path on iOS where select-to-copy has no menu (issue #5)
                    }
                }
            }
        }
        }
        is ChatItem.Assistant -> Column {
            // Protocol chunks can arrive much faster than a phone can parse and lay out Markdown.
            // Sample only the live tail; completed/history turns still render immediately and fully.
            SelectionContainer { StreamingMarkdownText(m.text, Tok.tx, live) }
            if (m.text.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CopyChip(m.text) // one-tap copy of the whole turn
            }
        }
        is ChatItem.Thinking -> ThinkingRow(m)
        is ChatItem.Tool -> if (isSubagentTool(m.tool)) SubagentCard(m) else {
            val isPlan = m.tool == "ExitPlanMode" || m.tool == "exit_plan_mode"
            var expanded by remember(m) { mutableStateOf(isPlan) } // plans read open by default (issue #10)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Tok.raised)
                    .clickable { expanded = !expanded }.padding(8.dp),
            ) {
                Text(if (isPlan) "⚙ Plan" else "⚙ ${m.tool}", color = Tok.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (m.preview.isNotBlank()) {
                    if (isPlan && expanded) Box(Modifier.padding(top = 4.dp)) { MarkdownText(m.preview, Tok.tx2) } // plan rendered as markdown
                    else Text(
                        m.preview, color = Tok.tx2, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        is ChatItem.Sys -> Text(stringResource(Res.string.error_prefix, m.text), color = Tok.danger, fontSize = 12.sp)
        is ChatItem.PermissionDecision -> PermissionDecisionRow(m)
        is ChatItem.RuleChip -> AllowChip(m.rule)
        // the quiet residue of a question exchange: an expandable answered row / a muted withdrawn note
        is ChatItem.QuestionsAnswered -> QuestionsAnsweredRow(m.items)
        is ChatItem.QuestionsWithdrawn -> QuestionsWithdrawnRow()
        // a live turn's end: quiet ✓ line so "finished" stays visible in the transcript
        is ChatItem.TurnEnded -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "✓ " + stringResource(Res.string.turn_done_marker) + (m.seconds?.let { " · ${turnDurLabel(it)}" } ?: ""),
                color = Tok.ok, fontSize = 11.sp,
            )
        }
    }
}

/** Durable-in-session audit residue for a phone approval decision. */
@Composable
private fun PermissionDecisionRow(m: ChatItem.PermissionDecision) {
    val color = if (m.allowed) Tok.ok else Tok.danger
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (m.allowed) "✓" else "×", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(
                when {
                    !m.allowed -> Res.string.activity_permission_denied
                    m.remembered -> Res.string.activity_permission_always
                    else -> Res.string.activity_permission_allowed
                },
                m.tool,
            ),
            color = Tok.tx2, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        Text(relativeTime(m.at), color = Tok.muted, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
    }
}

/** A turn's wall-clock as "42s" / "1m 3s". `internal` so the desktop ChatPane renderer shares it. */
internal fun turnDurLabel(s: Int) = if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"

/** The ■ interrupt button in the composer action slot — same glyph whether it rides beside Send or stands alone. */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val stopLabel = stringResource(Res.string.stop)
    RoundActionButton(onClick = onClick, filled = false, contentDescription = stopLabel) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Tok.accent))
    }
}

@Composable
private fun Label(text: String) =
    Text(text, color = Tok.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

/**
 * "Thinking…" activity row: a pulsing dot + label shown while a turn is running but nothing live is on
 * screen yet (just-sent, or re-entered mid-turn where the streamed reasoning wasn't in the transcript).
 */
@Composable
private fun WorkingRow() {
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PulseDot(Tok.muted)
        Text(stringResource(Res.string.thinking_streaming), color = Tok.muted, fontSize = 12.5.sp, fontStyle = FontStyle.Italic)
    }
}

/**
 * A new session should offer a useful first move instead of an empty transcript. Suggestions fill
 * the composer (never auto-send), preserving the user's chance to review or tailor the instruction.
 */
@Composable
private fun EmptyChatStarter(onPick: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(Res.string.chat_starter_status_label) to stringResource(Res.string.chat_starter_status_prompt),
        stringResource(Res.string.chat_starter_changes_label) to stringResource(Res.string.chat_starter_changes_prompt),
        stringResource(Res.string.chat_starter_next_label) to stringResource(Res.string.chat_starter_next_prompt),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tok.surface)
            .border(1.dp, Tok.hair, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.chat_starter_title), color = Tok.tx, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.chat_starter_body), color = Tok.tx2, fontSize = 13.sp, lineHeight = 19.sp)
        suggestions.forEach { (label, prompt) ->
            OutlinedButton(
                onClick = { onPick(prompt) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(label, fontSize = 13.5.sp) }
        }
    }
}

/** Delivered-but-no-turn cue (issue #104): a restrained, tappable row that replaces the "thinking" tail
 *  when a prompt was acked but produced nothing. Warn-toned (not an alarming error), one tap re-runs it. */
@Composable
private fun NoResponseRow(onResend: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onResend)
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PulseDot(Tok.warn, size = 5.dp)
        Text(stringResource(Res.string.msg_no_response), color = Tok.warn, fontSize = 12.5.sp)
    }
}

@Composable
private fun ComposerStatusBar(repo: PocketRepository, onMode: () -> Unit) {
    var openMenu by remember { mutableStateOf<String?>(null) }
    val agent = repo.sessionAgent.value ?: AgentKind.CLAUDE
    val cursorModels by remember(repo) { derivedStateOf { repo.cursorModels.toList() } }
    LaunchedEffect(agent) { if (agent == AgentKind.CURSOR) repo.refreshCursorModels() }
    val cursorVariant = if (repo.sessionAgent.value == AgentKind.CURSOR) {
        cursorModelForVariant(cursorModels, repo.model.value)?.variants?.firstOrNull { it.id == repo.model.value }
    } else null
    val cursorModel = if (agent == AgentKind.CURSOR) cursorModelForVariant(cursorModels, repo.model.value) else null
    val model = when (agent) {
        AgentKind.CLAUDE -> modelAlias(repo.model.value)
        AgentKind.CODEX -> repo.model.value.orEmpty()
        AgentKind.CURSOR -> cursorModel?.name ?: repo.model.value.orEmpty()
    }.ifBlank { stringResource(Res.string.value_model_default) }
    val effort = cursorVariant?.name ?: repo.effort.value ?: stringResource(Res.string.value_default)
    val mode = stringResource(MODE_BY[repo.mode.value]?.short ?: MODES.first().short)
    val modelOptions = when (agent) {
        AgentKind.CLAUDE -> CLAUDE_MODEL_OPTIONS
        AgentKind.CODEX -> CODEX_MODEL_OPTIONS.map { it to it }
        AgentKind.CURSOR -> mergedCursorModels(cursorModels)
    }
    val selectedModel = when (agent) {
        AgentKind.CLAUDE -> modelAlias(repo.model.value)
        AgentKind.CODEX -> repo.model.value.orEmpty()
        AgentKind.CURSOR -> cursorModelForVariant(cursorModels, repo.model.value)?.id ?: repo.model.value.orEmpty()
    }
    val effortOptions = when (agent) {
        AgentKind.CURSOR -> cursorModel?.variants.orEmpty().map { it.name to it.id }
        else -> EFFORT_OPTIONS.map { it to it }
    }
    val selectedEffort = cursorVariant?.id ?: repo.effort.value.orEmpty()
    val modeOptions = MODES.map { stringResource(it.short) to it.key.name }
    val used = repo.contextUsed.value ?: 0L
    val window = repo.contextWindow.value
    val fraction = if (window != null && window > 0) (used.toFloat() / window).coerceIn(0f, 1f) else 0f
    val contextDescription = if (window != null && window > 0) {
        "${stringResource(Res.string.label_context)} ${(fraction * 100).toInt()}%"
    } else {
        "${stringResource(Res.string.label_context)} ~${if (used >= 1000) "${used / 1000}k" else used}"
    }
    Box(Modifier.fillMaxWidth()) {
        if (openMenu == "model") {
            HappyStatusOptionMenu(
                options = modelOptions,
                selectedKey = selectedModel,
                onDismiss = { openMenu = null },
            ) { (_, id) -> repo.switchModel(id); openMenu = null }
        } else if (openMenu == "effort") {
            HappyStatusOptionMenu(
                options = effortOptions,
                selectedKey = selectedEffort,
                onDismiss = { openMenu = null },
            ) { (_, id) ->
                if (agent == AgentKind.CURSOR) repo.switchModel(id) else repo.switchEffort(id)
                openMenu = null
            }
        } else if (openMenu == "mode") {
            HappyStatusOptionMenu(
                options = modeOptions,
                selectedKey = repo.mode.value.name,
                onDismiss = { openMenu = null },
            ) { (_, id) ->
                val selected = PermissionMode.valueOf(id)
                openMenu = null
                // Full-auto retains the existing second-step warning; the safe modes switch directly,
                // matching the model/effort popup interaction without weakening the permission boundary.
                if (selected == PermissionMode.BYPASS_PERMISSIONS && repo.mode.value != selected) onMode()
                else repo.switchMode(selected)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            HappyStatusChip(Icons.Outlined.Memory, model, active = openMenu == "model") {
                openMenu = if (openMenu == "model") null else "model"
            }
            Spacer(Modifier.width(12.dp))
            HappyStatusChip(Icons.Outlined.Bolt, effort, active = openMenu == "effort") {
                if (effortOptions.isNotEmpty()) openMenu = if (openMenu == "effort") null else "effort"
            }
            Spacer(Modifier.width(12.dp))
            HappyStatusChip(Icons.Outlined.Shield, mode, active = openMenu == "mode") {
                openMenu = if (openMenu == "mode") null else "mode"
            }
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(18.dp).semantics { contentDescription = contextDescription }) {
            val stroke = 3.dp.toPx()
            drawCircle(Tok.hair, style = Stroke(stroke))
            when {
                fraction > 0f -> drawArc(
                    contextColor(fraction, Tok.info),
                    startAngle = -90f,
                    // A truthful sub-1% value is sub-pixel at 18dp. Keep the semantic percentage exact,
                    // but guarantee a visible 9° tick so non-zero context never looks like an empty ring.
                    sweepAngle = 360f * fraction.coerceAtLeast(0.025f),
                    useCenter = false,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                used > 0L -> {
                    // Some Codex/Cursor models report occupancy but no context-window denominator. A quarter
                    // arc + center dot means "active, capacity unknown" — deliberately not a fake percentage.
                    drawArc(
                        Tok.info, startAngle = -90f, sweepAngle = 90f, useCenter = false,
                        style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                    drawCircle(Tok.info, radius = 1.5.dp.toPx())
                }
            }
            }
        }
    }
}

/** Happy's SessionStatusBar popup geometry: 236dp, max 280dp, 12dp radius, radio rows. */
@Composable
private fun HappyStatusOptionMenu(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onDismiss: () -> Unit,
    onSelect: (Pair<String, String>) -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.BottomEnd,
        offset = with(density) { IntOffset((-8).dp.roundToPx(), (-36).dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier.width(236.dp).heightIn(max = 280.dp).shadow(10.dp, shape)
                .clip(shape).background(Tok.surface).border(1.dp, Tok.hair, shape)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                val selected = option.second.equals(selectedKey, ignoreCase = true)
                val selectWithHaptic = rememberHapticClick { onSelect(option) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 42.dp).clickable(onClick = selectWithHaptic)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.padding(top = 2.dp).size(14.dp).clip(CircleShape)
                            .border(2.dp, if (selected) Tok.accent else Tok.muted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Box(Modifier.size(5.dp).clip(CircleShape).background(Tok.accent))
                    }
                    Text(
                        option.first,
                        color = if (selected) Tok.accent else Tok.tx,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HappyStatusChip(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) Tok.accent else Tok.muted
    Row(
        Modifier.heightIn(min = 24.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Text(
            label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 168.dp),
        )
    }
}

@Composable
private fun StatusBarChip(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, color = Tok.muted, fontSize = 13.sp)
        Text(
            label, color = Tok.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 104.dp),
        )
    }
}

/** Extended reasoning, collapsed to one italic line; expands to the full text behind a hairline rule. */
@Composable
private fun ThinkingRow(m: ChatItem.Thinking) {
    var expanded by remember(m.seconds == null) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾ " else "▸ ", color = Tok.muted, fontSize = 11.sp)
            Text(
                m.seconds?.let { stringResource(Res.string.thought_for, it) } ?: stringResource(Res.string.thinking_streaming),
                color = Tok.muted, fontSize = 12.5.sp, fontStyle = FontStyle.Italic,
            )
        }
        if (expanded && m.text.isNotBlank()) {
            Row(Modifier.height(IntrinsicSize.Min).padding(start = 5.dp, top = 2.dp)) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Tok.hair))
                Text(
                    m.text, color = Tok.muted, fontSize = 12.5.sp, fontStyle = FontStyle.Italic, lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 13.dp),
                )
            }
        }
    }
}

/** The design's connection/live indicator: a softly pulsing dot (scale 1→0.82, alpha 1→0.45, ~1.25s). */
@Composable
internal fun PulseDot(color: Color, size: Dp = 6.dp) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(625), RepeatMode.Reverse),
    )
    Box(
        Modifier.size(size)
            .graphicsLayer { alpha = pulse; scaleX = 0.82f + 0.18f * pulse; scaleY = 0.82f + 0.18f * pulse }
            .clip(CircleShape).background(color),
    )
}

/** Floating pill over the message list when the user has scrolled away from the bottom. */
@Composable
private fun JumpToLatestPill(modifier: Modifier, newItems: Int, hasFreshActivity: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier.shadow(6.dp, shape).clip(shape).background(Tok.raised).border(1.dp, Tok.hair, shape)
            .clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Tok.tx2, modifier = Modifier.size(14.dp))
        Text(
            when {
                newItems > 0 -> stringResource(Res.string.jump_to_latest_count, newItems)
                hasFreshActivity -> stringResource(Res.string.jump_to_latest_activity)
                else -> stringResource(Res.string.jump_to_latest)
            },
            color = if (hasFreshActivity) Tok.accent else Tok.tx2,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** "2h ago"-style relative time; tolerates daemon timestamps in seconds or millis. */
@Composable
internal fun relativeTime(epoch: Long): String {
    val ms = if (epoch < 1_000_000_000_000L) epoch * 1000 else epoch
    val min = ((dev.ccpocket.app.epochMillis() - ms).coerceAtLeast(0)) / 60_000
    return when {
        min < 1 -> stringResource(Res.string.time_just_now)
        min < 60 -> stringResource(Res.string.time_minutes_ago, min)
        min < 24 * 60 -> stringResource(Res.string.time_hours_ago, min / 60)
        min < 48 * 60 -> stringResource(Res.string.time_yesterday)
        else -> stringResource(Res.string.time_days_ago, min / (24 * 60))
    }
}

/** Resolve a repo [StatusMsg] into display text — substitutes the optional %1$s detail argument. */
@Composable
internal fun StatusMsg.resolve(): String = arg?.let { stringResource(res, it) } ?: stringResource(res)
