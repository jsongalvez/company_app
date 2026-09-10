package com.companyb.companyapp.proto.onebutton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #852 — one-button shell: every screen shows exactly one giant primary action.
// Everything else sits above it, deliberately quiet, behind the button.

private enum class ObAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

data class ObHero(
    val label: String,
    val enabled: Boolean = true,
    val sub: String = "",
    val onPress: () -> Unit = {},
)

@Composable
fun OneButtonProtoApp() {
    OneButtonTheme {
        var phase by remember { mutableStateOf(ObAuthPhase.LOGIN) }
        val repo = remember { OneButtonFakeRepo() }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                ObAuthPhase.LOGIN -> ObLogin(
                    onLogin = {
                        logInfo("OneButton", "login single press")
                        phase = ObAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = ObAuthPhase.ONBOARDING_LOCKED },
                )
                ObAuthPhase.ONBOARDING_LOCKED -> ObOnboardingLocked(onBack = { phase = ObAuthPhase.LOGIN })
                ObAuthPhase.BRANCH_SELECT -> ObBranchSelect(
                    repo = repo,
                    onPick = { phase = ObAuthPhase.APP },
                    onBack = { phase = ObAuthPhase.LOGIN },
                )
                ObAuthPhase.APP -> ObShell(repo = repo, onLogout = { phase = ObAuthPhase.LOGIN })
            }
        }
    }
}

@Composable
private fun ObShell(repo: OneButtonFakeRepo, onLogout: () -> Unit) {
    var screen by remember { mutableStateOf(ObScreen.HOME) }
    var voidTarget by remember { mutableStateOf<ObSession?>(null) }
    var undoTarget by remember { mutableStateOf<ObRemittance?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        ObDayBanner(repo)
        ObScreenStrip(current = screen, unread = repo.notes.any { !it.read }, onPick = { screen = it })
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                when (screen) {
                    ObScreen.HOME -> ObHomeScreen(repo)
                    ObScreen.SESSIONS -> ObSessionsScreen(repo, onVoid = { voidTarget = it })
                    ObScreen.CLIENTS -> ObClientsScreen(repo)
                    ObScreen.FINANCE -> ObFinanceScreen(repo, onUndo = { undoTarget = it })
                    ObScreen.TEAM -> ObTeamScreen(repo)
                    ObScreen.MAIL -> ObMailboxScreen(repo)
                    ObScreen.AUDIT -> ObAuditScreen(repo)
                    ObScreen.PROFILE -> ObProfileScreen(repo, onLogout = onLogout)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        ObHeroButton(hero = obHeroFor(screen, repo, onLogout))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Everything else lives above — the button is only the next step.",
            fontSize = 12.sp,
            color = ObGhost,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp),
        )
    }
    voidTarget?.let { target ->
        ObReasonDialog(
            title = "Void ${target.id} with one press?",
            hint = "Reason (kept on the audit line)",
            confirm = "VOID IT",
            onDismiss = { voidTarget = null },
            onConfirm = { reason ->
                repo.voidSession(target.id, reason.ifBlank { "No reason given" })
                voidTarget = null
            },
        )
    }
    undoTarget?.let { target ->
        ObReasonDialog(
            title = "Undo ${target.id} within 48h?",
            hint = "Reason (kept on the audit line)",
            confirm = "UNDO IT",
            onDismiss = { undoTarget = null },
            onConfirm = { reason ->
                repo.undoRemittance(target.id, reason.ifBlank { "Pressed by mistake" })
                undoTarget = null
            },
        )
    }
}

private fun obHeroFor(screen: ObScreen, repo: OneButtonFakeRepo, onLogout: () -> Unit): ObHero {
    return when (screen) {
        ObScreen.HOME -> {
            val on = repo.clockedIn.value
            ObHero(
                label = if (on) "CLOCK OUT" else "CLOCK IN",
                sub = if (on) "One press ends the shift at ${repo.branchName(repo.branchId.value)}"
                else "One press starts the shift at ${repo.branchName(repo.branchId.value)}",
                onPress = { repo.clockToggle() },
            )
        }
        ObScreen.SESSIONS -> {
            val next = repo.nextPending()
            if (next != null) {
                ObHero(
                    label = "FINISH NEXT: ${next.clientName.uppercase()}",
                    sub = "${next.id} · ${next.type} · ${next.time} — one press completes it",
                    onPress = { repo.completeSession(next.id) },
                )
            } else {
                ObHero(
                    label = "ADD WALK-IN",
                    sub = "Queue is clear — one press seats the next guest",
                    onPress = { repo.addWalkIn("Walk-in guest", "Drop-in check", 500) },
                )
            }
        }
        ObScreen.CLIENTS -> ObHero(
            label = "ADD CLIENT",
            sub = "One press starts a single-press intake",
            onPress = { repo.addClient("New guest ${repo.clients.size + 1}") },
        )
        ObScreen.FINANCE -> {
            val draft = repo.remittances.firstOrNull { it.state == ObRemitState.DRAFT }
            val undoable = repo.remittances.firstOrNull { it.state == ObRemitState.SUBMITTED && it.undoable }
            when {
                draft != null -> ObHero(
                    label = "SUBMIT ${draft.id.uppercase()}",
                    sub = "${draft.kind.name} · ₱${draft.amount} — one press seals snapshot",
                    onPress = { repo.submitRemittance(draft.id) },
                )
                undoable != null -> ObHero(
                    label = "UNDO ${undoable.id.uppercase()}",
                    sub = "Submitted ${undoable.submittedAt} — still inside the 48h window",
                    onPress = { repo.undoRemittance(undoable.id, "Pressed by mistake") },
                )
                else -> ObHero(
                    label = "NEW SESSION DRAFT",
                    sub = "Nothing left to press — one press opens a fresh envelope",
                    onPress = { repo.addDraft(ObRemitKind.SESSION) },
                )
            }
        }
        ObScreen.TEAM -> ObHero(
            label = "SEND RELIEF INVITE",
            sub = "One press asks for one more pair of hands at ${repo.branchName(repo.branchId.value)}",
            onPress = { repo.addRelief("Invite", "Single-press call for Saturday cover") },
        )
        ObScreen.MAIL -> {
            val unread = repo.notes.count { !it.read }
            ObHero(
                label = "MARK ALL READ",
                enabled = unread > 0,
                sub = if (unread > 0) "$unread unread — one press clears the pile"
                else "Pile is clear — nothing to press",
                onPress = { repo.markAllRead() },
            )
        }
        ObScreen.AUDIT -> ObHero(
            label = "LOG SUMMARY",
            sub = "${repo.audits.size} lines — one press stamps a summary line",
            onPress = {
                repo.stamp("SUMMARY", "Audit log", "${repo.audits.size} lines reviewed in one sitting")
                logInfo("OneButton", "audit summary pressed lines=${repo.audits.size}")
            },
        )
        ObScreen.PROFILE -> ObHero(
            label = "LOG OUT",
            sub = "One press ends this sitting for ${repo.me.value.name}",
            onPress = {
                logInfo("OneButton", "logout single press")
                onLogout()
            },
        )
    }
}

@Composable
private fun ObDayBanner(repo: OneButtonFakeRepo) {
    val status = repo.dayStatus.value
    val (dot, copy) = when (status) {
        ObDayStatus.OPEN -> ObGreen to "Branch Day OPEN at ${repo.branchName(repo.branchId.value)}"
        ObDayStatus.PAST -> ObAmber to "Branch Day PAST at ${repo.branchName(repo.branchId.value)}"
        ObDayStatus.REMITTED -> ObBlue to "Branch Day REMITTED at ${repo.branchName(repo.branchId.value)}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(ObPanel).padding(horizontal = 40.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(ObShapes.extraSmall).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text = copy, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObInk)
        Spacer(Modifier.width(16.dp))
        Text(
            text = if (repo.clockedIn.value) "● clocked in" else "○ clocked out",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (repo.clockedIn.value) ObGreen else ObDim,
        )
        Spacer(Modifier.weight(1f))
        Text(text = "Day boundary 04:00 Asia/Manila", fontSize = 12.sp, color = ObGhost)
    }
}

@Composable
private fun ObScreenStrip(current: ObScreen, unread: Boolean, onPick: (ObScreen) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ObPanel).padding(horizontal = 40.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ObScreen.entries.forEach { s ->
            val on = s == current
            val badge = s == ObScreen.MAIL && unread
            Box(
                modifier = Modifier.clip(ObShapes.small)
                    .background(if (on) ObRaised else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = s.name + if (badge) " •" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) ObAmber else ObDim,
                )
            }
        }
    }
}

@Composable
private fun ObHeroButton(hero: ObHero) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
        if (hero.sub.isNotEmpty()) {
            Text(text = hero.sub, fontSize = 13.sp, color = ObDim, modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(
            onClick = hero.onPress,
            enabled = hero.enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = ObShapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = ObAmber,
                contentColor = ObAmberInk,
                disabledContainerColor = ObRaised,
                disabledContentColor = ObGhost,
            ),
        ) {
            Text(text = hero.label, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun ObQuietButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = ObShapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ObGray),
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
