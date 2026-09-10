package com.companyb.companyapp.proto.readonlyaudit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class RaPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

@Composable
fun ReadonlyAuditProtoApp() {
    logInfo("ReadonlyAudit", "glass-box audit prototype start")
    AuditGlassTheme {
        var phase by remember { mutableStateOf(RaPhase.LOGIN) }
        val repo = remember { ReadonlyAuditRepo() }
        Box(Modifier.fillMaxSize().background(AuditGlass.CanvasBrush)) {
            when (phase) {
                RaPhase.LOGIN -> RaLogin(
                    repo = repo,
                    onAuditSeat = { phase = RaPhase.BRANCH_SELECT },
                    onOnboarding = { phase = RaPhase.ONBOARDING_LOCKED },
                )
                RaPhase.ONBOARDING_LOCKED -> RaOnboardingLocked(onBack = { phase = RaPhase.LOGIN })
                RaPhase.BRANCH_SELECT -> RaBranchSelect(
                    repo = repo,
                    onPick = { phase = RaPhase.APP },
                    onBack = { phase = RaPhase.LOGIN },
                )
                RaPhase.APP -> RaShell(
                    repo = repo,
                    onLogout = { phase = RaPhase.LOGIN },
                    onSwitchBranch = { phase = RaPhase.BRANCH_SELECT },
                )
            }
        }
    }
}

@Composable
private fun RaLogin(repo: ReadonlyAuditRepo, onAuditSeat: () -> Unit, onOnboarding: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(520.dp), alpha = 0.08f) {
            Text("GLASS-BOX AUDIT", color = AuditGlass.Ledger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            AuditHeader("Sign in", "This prototype is pinned to the read-only audit seat. " +
                "Every action stays visible — and every lock carries its reason.")
            Spacer(Modifier.height(16.dp))
            repo.users.forEach { user ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = AuditGlass.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(user.role.label + " · " + repo.branches.first { it.id == user.homeBranchId }.name,
                            color = AuditGlass.Muted, fontSize = 12.sp)
                    }
                    when {
                        user.role == RaRole.ACCOUNTANT -> AuditChip("Sign in", true) {
                            repo.currentUserId = user.id
                            onAuditSeat()
                        }
                        user.role.locked -> AuditChip("View lockout", false, onClick = onOnboarding)
                        else -> Column(horizontalAlignment = Alignment.End) {
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("Sign in", color = AuditGlass.Faint, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Seat pinned to audit",
                                color = AuditGlass.Amber, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            NoteCard("ONBOARDING users hold zero capabilities: the role bundle is empty, so nothing " +
                "derives — even after a branch assignment — until MANAGE_USERS grants a real role.")
        }
    }
}

@Composable
private fun RaOnboardingLocked(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(520.dp), alpha = 0.08f) {
            StatusPill("ONBOARDING · NO CAPABILITIES", AuditGlass.Rose)
            Spacer(Modifier.height(12.dp))
            AuditHeader("Locked out by design",
                "K. Dela Pena signed in fine — then hit a glass wall. Zero capabilities means " +
                    "zero derived access, at every branch, on every day.")
            Spacer(Modifier.height(12.dp))
            LockedAction("Open home", "ONBOARDING role bundle is empty — nothing derives from it")
            Spacer(Modifier.height(4.dp))
            LockedAction("View sessions", "ONBOARDING role bundle is empty — nothing derives from it")
            Spacer(Modifier.height(4.dp))
            LockedAction("View finance", "ONBOARDING role bundle is empty — nothing derives from it")
            Spacer(Modifier.height(12.dp))
            NoteCard("The way out is a human one: a MANAGER grants a real role via MANAGE_USERS. " +
                "Until then the audit seat can watch this screen — and nothing else.",
                tint = AuditGlass.Amber)
            Spacer(Modifier.height(12.dp))
            AuditChip("Back to sign in", false, onClick = onBack)
        }
    }
}

@Composable
private fun RaBranchSelect(repo: ReadonlyAuditRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(640.dp)) {
            AuditHeader("Pick a Branch to audit",
                "Branches hold their own inventory, sessions and financial records. " +
                    "Audit view is global — switching branches only changes the lens.")
            Spacer(Modifier.height(16.dp))
            repo.branches.forEach { branch ->
                GlassPanel(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    alpha = if (branch.id == repo.currentBranchId) 0.12f else 0.06f,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(branch.name, color = AuditGlass.Paper, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(branch.kind.label, color = AuditGlass.Muted, fontSize = 12.sp)
                        }
                        AuditChip(
                            if (branch.id == repo.currentBranchId) "Auditing" else "Audit",
                            branch.id == repo.currentBranchId,
                        ) {
                            repo.currentBranchId = branch.id
                            onPick()
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditChip("Back", false, onClick = onBack)
            }
        }
    }
}

@Composable
private fun RaShell(repo: ReadonlyAuditRepo, onLogout: () -> Unit, onSwitchBranch: () -> Unit) {
    var screen by remember { mutableStateOf(RaScreen.HOME) }
    Column(Modifier.fillMaxSize()) {
        RaTopStrip(repo = repo, onSwitchBranch = onSwitchBranch)
        Row(Modifier.weight(1f)) {
            Column(
                Modifier.width(208.dp).fillMaxHeight()
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RaScreen.entries.forEach { entry ->
                    val badge = if (entry == RaScreen.MAIL && repo.unreadCount > 0) {
                        " (" + repo.unreadCount + ")"
                    } else {
                        ""
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (screen == entry) AuditGlass.Ledger else Color.Transparent)
                            .clickable { screen = entry }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(entry.label + badge,
                            color = if (screen == entry) Color(0xFF04120B) else AuditGlass.Muted,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                NoteCard("READ-ONLY SEAT — " + repo.unreadCount + " unread in the mailbox, " +
                    "the only counter that moves here.", tint = AuditGlass.Ledger)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    RaScreenBody(repo = repo, screen = screen,
                        onOpenDay = { screen = RaScreen.HOME },
                        onLogout = onLogout, onSwitchBranch = onSwitchBranch)
                }
            }
            RaVerdictRail(repo = repo, screen = screen,
                Modifier.width(288.dp).fillMaxHeight().padding(top = 12.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun RaTopStrip(repo: ReadonlyAuditRepo, onSwitchBranch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f))
            .border(BorderStroke(0.dp, Color.Transparent))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("READ-ONLY AUDIT SEAT", AuditGlass.Ledger)
            Spacer(Modifier.width(10.dp))
            Text(repo.currentBranch.name + " · " + repo.currentUser.name + " (Accountant)",
                color = AuditGlass.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            AuditChip("Switch Branch", false, onClick = onSwitchBranch)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BRANCH DAY", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            repo.days.filter { it.branchId == repo.currentBranchId }.forEach { day ->
                val tint = when (day.state) {
                    RaDayState.OPEN -> AuditGlass.Ledger
                    RaDayState.PAST -> AuditGlass.Sky
                    RaDayState.REMITTED -> AuditGlass.Violet
                }
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (day.id == repo.currentDayId) tint.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.05f))
                        .border(BorderStroke(1.dp, tint.copy(alpha = 0.5f)), RoundedCornerShape(999.dp))
                        .clickable { repo.currentDayId = day.id }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day.date + " · " + day.state.label, color = tint, fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Day boundary 04:00 Asia/Manila — OPEN stays editable until 04:00 the next morning.",
                color = AuditGlass.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RaVerdictRail(repo: ReadonlyAuditRepo, screen: RaScreen, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel(Modifier.fillMaxWidth(), alpha = 0.09f) {
            Text("GLASS BOX", color = AuditGlass.Ledger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Why everything is locked", color = AuditGlass.Paper, fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(repo.lockLine(screen), color = AuditGlass.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        GlassPanel(Modifier.fillMaxWidth(), alpha = 0.06f) {
            Text("SEAT CAPABILITIES", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            repo.currentUser.capabilities.forEach { cap ->
                Text("· " + cap, color = AuditGlass.Paper, fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("NOT HELD: EDIT_BRANCH_DATA · SUBMIT_REMITTANCE · MANAGE_CLIENTS · MANAGE_USERS",
                color = AuditGlass.Rose, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        GlassPanel(Modifier.fillMaxWidth(), alpha = 0.06f) {
            Text("DAY VERDICT", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val day = repo.currentDay
            val tint = when (day.state) {
                RaDayState.OPEN -> AuditGlass.Ledger
                RaDayState.PAST -> AuditGlass.Sky
                RaDayState.REMITTED -> AuditGlass.Violet
            }
            StatusPill(day.date + " · " + day.state.label, tint)
            Spacer(Modifier.height(8.dp))
            Text(
                when (day.state) {
                    RaDayState.OPEN -> "Editable by on-duty holders — but not by this seat."
                    RaDayState.PAST -> "Lazy-closed at 04:00 Manila; Coordinator-only edits with flagged audit entries."
                    RaDayState.REMITTED -> "Covered by a submitted remittance; Coordinator-only edits, fully flagged."
                },
                color = AuditGlass.Muted, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun RaScreenBody(
    repo: ReadonlyAuditRepo,
    screen: RaScreen,
    onOpenDay: (String) -> Unit,
    onLogout: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
        when (screen) {
            RaScreen.HOME -> RaHome(repo)
            RaScreen.SESSIONS -> RaSessions(repo)
            RaScreen.CLIENTS -> RaClients(repo)
            RaScreen.FINANCE -> RaFinance(repo)
            RaScreen.TEAM -> RaTeam(repo)
            RaScreen.MAIL -> RaMail(repo, onOpenDay)
            RaScreen.AUDIT -> RaAudit(repo)
            RaScreen.PROFILE -> RaProfile(repo, onLogout, onSwitchBranch)
        }
        Spacer(Modifier.height(24.dp))
    }
}
