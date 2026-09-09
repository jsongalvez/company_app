package com.companyb.companyapp.proto.a11ymax

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "A11yMax"

private enum class AuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

@Composable
fun A11yMaxProtoApp(onExit: () -> Unit = {}) {
    LaunchedEffect(Unit) { logInfo(TAG, "a11y-max prototype open (fake data, no network)") }
    var phase by remember { mutableStateOf(AuthPhase.LOGIN) }
    A11yMaxTheme(scale = A11yMaxFakeRepo.textScale.value) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                AuthPhase.LOGIN -> MaxLogin(
                    onLogin = {
                        A11yMaxFakeRepo.say("Signed in. Pick a Branch.")
                        phase = AuthPhase.BRANCH_SELECT
                    },
                    onOnboardingDemo = { phase = AuthPhase.ONBOARDING_LOCKED },
                )
                AuthPhase.ONBOARDING_LOCKED -> MaxOnboardingLocked(onBack = { phase = AuthPhase.LOGIN })
                AuthPhase.BRANCH_SELECT -> MaxBranchSelect(
                    onPick = { id ->
                        A11yMaxFakeRepo.clockedBranchId.value = id
                        A11yMaxFakeRepo.audit("Demo user", "BRANCH_SELECT", A11yMaxFakeRepo.branchName(id))
                        A11yMaxFakeRepo.say("Branch ${A11yMaxFakeRepo.branchName(id)} selected. Home tab.")
                        phase = AuthPhase.APP
                    },
                    onBack = { phase = AuthPhase.LOGIN },
                )
                AuthPhase.APP -> MaxShell(onLogout = { phase = AuthPhase.LOGIN }, onExit = onExit)
            }
        }
    }
}

@Composable
internal fun MaxSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun MaxNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MaxBigButton(text: String, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = {
            onClick()
        },
        modifier = Modifier.height(56.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(4.dp, A11yFocus, RoundedCornerShape(10.dp)) else Modifier)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun MaxOutlineButton(text: String, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(52.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(4.dp, A11yFocus, RoundedCornerShape(10.dp)) else Modifier)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MaxLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var email by remember { mutableStateOf("") }
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { contentFocus.requestFocus() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "CompanyApp sign in",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Max-accessibility prototype. Every control is keyboard reachable. Tab moves, Enter activates.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Work email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(contentFocus),
        )
        MaxBigButton("Sign in (any email works)", "Sign in. Any email works in this demo.") { onLogin() }
        MaxOutlineButton("Preview the ONBOARDING locked account", "Preview the locked ONBOARDING account.") {
            onOnboardingDemo()
        }
        MaxNote("Keyboard map: Alt+1 to Alt+8 jump between sections once signed in. Press / any time for the full map.")
    }
}

@Composable
private fun MaxOnboardingLocked(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MaxSectionTitle("ONBOARDING account is locked")
        MaxNote(
            "A freshly registered user holds zero capabilities. The role bundle is empty, so nothing derives, " +
                "even after a Branch assignment. A MANAGER must grant a real role with MANAGE_USERS before " +
                "anything unlocks. Fake demo only.",
        )
        Text(text = "Capabilities: none. Branch assignment: Sunrise Clinic. Doors opened: 0 of 8.", style = MaterialTheme.typography.bodyLarge)
        MaxOutlineButton("Back to sign in", "Back to sign in.") { onBack() }
    }
}

@Composable
private fun MaxBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MaxSectionTitle("Pick a Branch")
        A11yMaxFakeRepo.branches.forEach { branch ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth()
                    .border(if (focused) 4.dp else 2.dp, if (focused) A11yFocus else MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = branch.name, style = MaterialTheme.typography.titleMedium, color = A11yInk)
                    Text(
                        text = "Type ${branch.kind}. Own inventory, sessions, and financial records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = A11yInkSoft,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MaxBigButton("Enter", "Enter ${branch.name}.") { onPick(branch.id) }
            }
        }
        MaxOutlineButton("Back to sign in", "Back to sign in.") { onBack() }
    }
}

@Composable
private fun MaxShell(onLogout: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(MaxTab.HOME) }
    var helpOpen by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    fun go(target: MaxTab) {
        tab = target
        A11yMaxFakeRepo.say("${target.name} section. ${tabHelp(target)}")
    }
    Column(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.isAltPressed) {
                    val target = when (event.key) {
                        Key.One -> MaxTab.HOME
                        Key.Two -> MaxTab.SESSIONS
                        Key.Three -> MaxTab.CLIENTS
                        Key.Four -> MaxTab.FINANCE
                        Key.Five -> MaxTab.TEAM
                        Key.Six -> MaxTab.MAIL
                        Key.Seven -> MaxTab.AUDIT
                        Key.Eight -> MaxTab.PROFILE
                        else -> null
                    }
                    if (target != null) {
                        go(target)
                        return@onPreviewKeyEvent true
                    }
                }
                if (event.key == Key.Slash) {
                    helpOpen = true
                    return@onPreviewKeyEvent true
                }
                false
            },
    ) {
        MaxDayBanner()
        MaxLiveRegion()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { contentFocus.requestFocus() }) {
                Text("Skip to content", fontWeight = FontWeight.Bold, color = A11yFocus)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { helpOpen = true }) {
                Text("Keyboard map (/)", fontWeight = FontWeight.Bold, color = A11yFocus)
            }
        }
        MaxTabBar(current = tab, onPick = ::go)
        HorizontalDivider(color = A11yFocus, thickness = 2.dp)
        Box(Modifier.weight(1f).padding(20.dp)) {
            Column(Modifier.focusRequester(contentFocus).focusable()) {
                when (tab) {
                    MaxTab.HOME -> MaxHome()
                    MaxTab.SESSIONS -> MaxSessions()
                    MaxTab.CLIENTS -> MaxClients()
                    MaxTab.FINANCE -> MaxFinance()
                    MaxTab.TEAM -> MaxTeam()
                    MaxTab.MAIL -> MaxMailbox()
                    MaxTab.AUDIT -> MaxAuditLog()
                    MaxTab.PROFILE -> MaxProfile(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
    if (helpOpen) {
        AlertDialog(
            onDismissRequest = {
                helpOpen = false
                A11yMaxFakeRepo.say("Keyboard map closed. Focus returned to content.")
            },
            title = { Text("Full keyboard map", color = A11yInk) },
            text = {
                Text(
                    "Alt+1 Home. Alt+2 Sessions. Alt+3 Clients. Alt+4 Finance. Alt+5 Team. " +
                        "Alt+6 Mailbox. Alt+7 Audit log. Alt+8 Profile. Slash reopens this map. " +
                        "Escape closes dialogs. Tab and Shift+Tab move through every control. Enter activates.",
                    color = A11yInk,
                )
            },
            confirmButton = {
                Button(onClick = {
                    helpOpen = false
                    A11yMaxFakeRepo.say("Keyboard map closed. Focus returned to content.")
                }) { Text("Close") }
            },
            containerColor = A11yPaper,
        )
    }
}

private fun tabHelp(tab: MaxTab): String = when (tab) {
    MaxTab.HOME -> "Clock in, relief duty, requests, and invites."
    MaxTab.SESSIONS -> "Filter, advance status, void with reason."
    MaxTab.CLIENTS -> "Global list with anonymized view."
    MaxTab.FINANCE -> "SESSION and PRODUCT drafts, submit, undo within 48 hours."
    MaxTab.TEAM -> "Users, roles, and the ONBOARDING lock."
    MaxTab.MAIL -> "Read and unread mailbox."
    MaxTab.AUDIT -> "Every mutation, newest first."
    MaxTab.PROFILE -> "Text size, branch day, clock out, sign out."
}

@Composable
private fun MaxDayBanner() {
    val status = A11yMaxFakeRepo.dayStatus.value
    val copy = when (status) {
        MaxDayStatus.OPEN -> "Branch day OPEN at ${A11yMaxFakeRepo.branchName(A11yMaxFakeRepo.clockedBranchId.value)} — editable by all on-duty users."
        MaxDayStatus.PAST -> "Branch day PAST — the 04:00 Asia/Manila boundary passed. Coordinator-only edits."
        MaxDayStatus.REMITTED -> "Branch day REMITTED — covered by a submitted remittance. Coordinator-only edits with flagged audit entries."
    }
    Box(
        Modifier.fillMaxWidth().background(A11yFocus).padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { contentDescription = "Branch day status. $copy" },
    ) {
        Text(text = "BRANCH DAY ${status.name}: $copy", color = A11yBlack, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun MaxLiveRegion() {
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Text(
            text = "Announcement: ${A11yMaxFakeRepo.announcement.value}",
            color = A11yOnDark,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MaxTabBar(current: MaxTab, onPick: (MaxTab) -> Unit) {
    val tabs = MaxTab.entries
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.take(4).forEach { tab -> MaxTabButton(tab, current, onPick) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.drop(4).forEach { tab -> MaxTabButton(tab, current, onPick) }
        }
    }
}

@Composable
private fun MaxTabButton(current: MaxTab, selected: MaxTab, onPick: (MaxTab) -> Unit) {
    val isSelected = current == selected
    var focused by remember { mutableStateOf(false) }
    val label = current.name + if (current == MaxTab.MAIL && A11yMaxFakeRepo.unreadCount > 0) " (${A11yMaxFakeRepo.unreadCount} unread)" else ""
    val number = MaxTab.entries.indexOf(current) + 1
    if (isSelected) {
        Button(
            onClick = { onPick(current) },
            modifier = Modifier.height(52.dp)
                .onFocusChanged { focused = it.isFocused }
                .then(if (focused) Modifier.border(4.dp, A11yOnDark, RoundedCornerShape(10.dp)) else Modifier)
                .semantics { contentDescription = "${current.name} section, selected. Shortcut Alt plus $number." },
            colors = ButtonDefaults.buttonColors(containerColor = A11yAction, contentColor = A11yOnAction),
        ) {
            Text("● $label", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = { onPick(current) },
            modifier = Modifier.height(52.dp)
                .onFocusChanged { focused = it.isFocused }
                .then(if (focused) Modifier.border(4.dp, A11yFocus, RoundedCornerShape(10.dp)) else Modifier)
                .semantics { contentDescription = "${current.name} section. Shortcut Alt plus $number." },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = A11yOnDark),
            border = androidx.compose.foundation.BorderStroke(2.dp, A11yOnDark),
        ) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
