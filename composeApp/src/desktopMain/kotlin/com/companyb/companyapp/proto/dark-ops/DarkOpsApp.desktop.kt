package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "DarkOps"

@Composable
internal fun DarkOpsApp() {
    val repo = rememberDarkOpsRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "dark-ops console open (fake data, no network)") }
    DarkOpsTheme {
        when {
            !repo.authed -> DarkOpsAuthGate(repo)
            repo.currentUser.locked -> DarkOpsOnboardingLock(repo)
            !repo.branchPicked -> DarkOpsBranchSelect(repo)
            else -> DarkOpsShell(repo)
        }
    }
}

@Composable
private fun DarkOpsShell(repo: DarkOpsRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkOpsPalette.Void)
                .onPreviewKeyEvent { event ->
                    handleGlobalKey(
                        repo,
                        event.key,
                        event.type,
                        event.isCtrlPressed || event.isMetaPressed,
                    )
                },
    ) {
        Column(Modifier.fillMaxSize()) {
            DayBanner(repo)
            HorizontalDivider(color = DarkOpsPalette.Border)
            Row(Modifier.weight(1f)) {
                NavRail(repo)
                Box(Modifier.weight(1f).padding(OpsPadMd)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (repo.screen) {
                            OpsScreen.HOME -> DarkOpsHome(repo)
                            OpsScreen.SESSIONS -> DarkOpsSessions(repo)
                            OpsScreen.CLIENTS -> DarkOpsClients(repo)
                            OpsScreen.FINANCE -> DarkOpsFinance(repo)
                            OpsScreen.TEAM -> DarkOpsTeam(repo)
                            OpsScreen.MAIL -> DarkOpsMailbox(repo)
                            OpsScreen.PROFILE -> DarkOpsProfile(repo)
                        }
                    }
                }
            }
            HorizontalDivider(color = DarkOpsPalette.Border)
            StatusBar(repo)
        }
    }
    if (repo.paletteOpen) CommandPalette(repo)
    if (repo.shortcutsOpen) ShortcutsDialog(repo)
}

private fun handleGlobalKey(
    repo: DarkOpsRepo,
    key: Key,
    type: KeyEventType,
    mod: Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (mod && key == Key.K) {
        repo.paletteOpen = !repo.paletteOpen
        return true
    }
    if (repo.paletteOpen || repo.shortcutsOpen) return false
    if (key == Key.Slash) {
        repo.shortcutsOpen = true
        return true
    }
    val digit = digitScreen(key) ?: return false
    repo.screen = digit
    return true
}

private fun digitScreen(key: Key): OpsScreen? =
    when (key) {
        Key.One -> OpsScreen.HOME
        Key.Two -> OpsScreen.SESSIONS
        Key.Three -> OpsScreen.CLIENTS
        Key.Four -> OpsScreen.FINANCE
        Key.Five -> OpsScreen.TEAM
        Key.Six -> OpsScreen.MAIL
        Key.Seven -> OpsScreen.PROFILE
        else -> null
    }

@Composable
private fun DayBanner(repo: DarkOpsRepo) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    DarkOpsPalette.Panel,
                ).padding(horizontal = OpsPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BRANCH-DAY", style = DarkOpsType.labelSmall)
        Spacer(Modifier.width(OpsPadSm))
        repo.days.forEachIndexed { index, day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OpsBadgeClickable(day.date + " " + day.state.label, index == repo.dayIndex) { repo.dayIndex = index }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        OpsBadge(repo.currentBranch.name + " · " + repo.currentBranch.kind, DarkOpsPalette.Violet)
        Spacer(Modifier.width(6.dp))
        OpsBadge(
            if (repo.clockedIn) "IN-SHIFT" else "OFF-DUTY",
            if (repo.clockedIn) DarkOpsPalette.Phosphor else DarkOpsPalette.Faint,
        )
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    DarkOpsPalette.Panel,
                ).padding(horizontal = OpsPadMd)
                .padding(bottom = 6.dp),
    ) {
        OpsNote("boundary 04:00 Asia/Manila · " + repo.currentDay.note)
    }
}

@Composable
private fun NavRail(repo: DarkOpsRepo) {
    Column(
        modifier =
            Modifier
                .width(OpsRailWidth)
                .fillMaxHeight()
                .background(DarkOpsPalette.Panel)
                .padding(OpsPadSm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "DARK-OPS",
            style = DarkOpsType.titleSmall,
            color = DarkOpsPalette.Phosphor,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OpsScreen.entries.forEach { screen ->
            val active = repo.screen == screen
            OpsRow(selected = active, onClick = { repo.screen = screen }) {
                Text(screen.key, style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
                Spacer(Modifier.width(6.dp))
                Text(
                    screen.title,
                    style = DarkOpsType.bodyMedium,
                    color = if (active) DarkOpsPalette.Phosphor else DarkOpsPalette.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (screen == OpsScreen.MAIL &&
                    repo.unreadCount > 0
                ) {
                    OpsBadge(repo.unreadCount.toString(), DarkOpsPalette.Phosphor)
                }
                if (screen == OpsScreen.SESSIONS &&
                    repo.pendingCount > 0
                ) {
                    OpsBadge(repo.pendingCount.toString(), DarkOpsPalette.Amber)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            OpsKbd("^K")
            Text("palette", style = DarkOpsType.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            OpsKbd("?")
            Text("keys", style = DarkOpsType.bodySmall)
        }
    }
}

@Composable
private fun StatusBar(repo: DarkOpsRepo) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    DarkOpsPalette.Panel,
                ).padding(horizontal = OpsPadMd, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            repo.currentDay.date + " " + repo.currentDay.state.label,
            style = DarkOpsType.bodySmall,
            color = dayColor(repo.currentDay.state),
        )
        StatusSep()
        Text(repo.currentBranch.name, style = DarkOpsType.bodySmall)
        StatusSep()
        Text(repo.currentUser.name + " (" + repo.currentUser.role + ")", style = DarkOpsType.bodySmall)
        StatusSep()
        Text("pending " + repo.pendingCount, style = DarkOpsType.bodySmall, color = DarkOpsPalette.Amber)
        StatusSep()
        Text("unread " + repo.unreadCount, style = DarkOpsType.bodySmall, color = DarkOpsPalette.Violet)
        Spacer(Modifier.weight(1f))
        OpsKbd("j/k")
        Text(" move", style = DarkOpsType.bodySmall)
        StatusSep()
        OpsKbd("1-7")
        Text(" jump", style = DarkOpsType.bodySmall)
    }
}

@Composable
private fun StatusSep() {
    Text("  ·  ", style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
}

private data class OpsCommand(
    val label: String,
    val run: (DarkOpsRepo) -> Unit,
)

private fun paletteCommands(): List<OpsCommand> =
    listOf(
        OpsCommand("go: home") { it.screen = OpsScreen.HOME },
        OpsCommand("go: sessions") { it.screen = OpsScreen.SESSIONS },
        OpsCommand("go: clients") { it.screen = OpsScreen.CLIENTS },
        OpsCommand("go: finance") { it.screen = OpsScreen.FINANCE },
        OpsCommand("go: team") { it.screen = OpsScreen.TEAM },
        OpsCommand("go: mail") { it.screen = OpsScreen.MAIL },
        OpsCommand("go: profile") { it.screen = OpsScreen.PROFILE },
        OpsCommand("shift: toggle clock") { it.toggleClock() },
        OpsCommand("mail: drain all") { it.markAllNoticesRead() },
        OpsCommand("finance: submit SESSION") { it.submitRemittance("SESSION") },
    )

@Composable
private fun CommandPalette(repo: DarkOpsRepo) {
    var query by remember { mutableStateOf("") }
    val hits = paletteCommands().filter { it.label.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = { repo.paletteOpen = false },
        title = { Text("> command palette", style = DarkOpsType.titleMedium, color = DarkOpsPalette.Phosphor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                    },
                    label = {
                        Text("type to filter, enter runs first", style = DarkOpsType.bodySmall)
                    },
                    singleLine = true,
                    textStyle = DarkOpsType.bodyMedium,
                    colors = fieldColors(),
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
                hits.take(8).forEach { cmd ->
                    OpsRow(selected = false, onClick = {
                        cmd.run(repo)
                        repo.paletteOpen = false
                    }) {
                        Text(cmd.label, style = DarkOpsType.bodyMedium)
                    }
                }
                if (hits.isEmpty()) OpsNote("no match")
            }
        },
        confirmButton = {
            TextButton(onClick = { repo.paletteOpen = false }) {
                Text("esc", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Dim)
            }
        },
        containerColor = DarkOpsPalette.PanelRaised,
    )
}

@Composable
private fun ShortcutsDialog(repo: DarkOpsRepo) {
    AlertDialog(
        onDismissRequest = { repo.shortcutsOpen = false },
        title = { Text("keys", style = DarkOpsType.titleMedium, color = DarkOpsPalette.Phosphor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ShortcutLine("^K / ctrl-K", "command palette")
                ShortcutLine("j / k", "move selection in lists")
                ShortcutLine("enter", "open / acknowledge row")
                ShortcutLine("1 – 7", "jump to rail section")
                ShortcutLine("?", "this overlay")
                ShortcutLine("esc", "close dialogs")
            }
        },
        confirmButton = {
            TextButton(onClick = { repo.shortcutsOpen = false }) {
                Text("close", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Dim)
            }
        },
        containerColor = DarkOpsPalette.PanelRaised,
    )
}

@Composable
private fun ShortcutLine(
    keys: String,
    what: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OpsKbd(keys)
        Spacer(Modifier.width(OpsPadSm))
        Text(what, style = DarkOpsType.bodyMedium)
    }
}
