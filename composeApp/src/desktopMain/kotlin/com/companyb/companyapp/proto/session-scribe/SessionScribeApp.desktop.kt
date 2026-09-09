package com.companyb.companyapp.proto.sessionscribe

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class SAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class STab { TODAY, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun SessionScribeProtoApp() {
    SessionScribeTheme {
        val repo = remember { SessionScribeFakeRepo() }
        var phase by remember { mutableStateOf(SAuthPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                SAuthPhase.LOGIN -> ScribeLogin(
                    onLogin = {
                        logInfo("ScribeLogin", "login submitted")
                        phase = SAuthPhase.BRANCH_SELECT
                    },
                    onOnboardingDemo = { phase = SAuthPhase.ONBOARDING_LOCKED },
                )
                SAuthPhase.ONBOARDING_LOCKED -> ScribeOnboardingLocked(onBack = { phase = SAuthPhase.LOGIN })
                SAuthPhase.BRANCH_SELECT -> ScribeBranchSelect(
                    repo = repo,
                    onPick = { phase = SAuthPhase.APP },
                    onBack = { phase = SAuthPhase.LOGIN },
                )
                SAuthPhase.APP -> ScribeShell(repo = repo, onLogout = { phase = SAuthPhase.LOGIN })
            }
        }
    }
}

@Composable
private fun ScribeLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var email by remember { mutableStateOf("nadia@quiapo.example") }
    var password by remember { mutableStateOf("scribe-042") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(480.dp)) {
            ScribeCode("SESSION SCRIBE · FAST LOGGING DESK")
            Spacer(Modifier.height(8.dp))
            Text(text = "Scribe the day, not the form.", fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text(
                text = "One-tap session logging with smart defaults from client history — fake data, no network.",
                fontSize = 13.sp,
                color = ScribeMuted,
            )
            Spacer(Modifier.height(16.dp))
            ScribeCard(accent = ScribeTeal) {
                SectionHeader(index = "01", title = "Clock in to scribe")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = ScribeInk),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Open my scribe desk")
                }
                TextButton(onClick = onOnboardingDemo) {
                    Text("Preview the ONBOARDING welcome", color = ScribeRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScribeOnboardingLocked(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(520.dp)) {
            ScribeCard(accent = ScribeAmber) {
                SectionHeader(index = "00", title = "Welcome, scribe")
                Text(text = "Jojo K. · ONBOARDING", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                ScribeTag(text = "Locked — empty capability bundle", color = ScribeAmber, soft = ScribeAmberSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "A freshly registered user holds zero capabilities — locked out even after a branch " +
                        "assignment — until MANAGE_USERS grants a real role. Ask a MANAGER to hand you a pen.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                ScribeGhostButton(text = "Back to login", onClick = onBack)
            }
        }
    }
}

@Composable
private fun ScribeBranchSelect(repo: SessionScribeFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(580.dp)) {
            ScribeCode("STEP 02 · PICK YOUR DESK")
            Spacer(Modifier.height(8.dp))
            Text(text = "Which branch are you scribing?", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            repo.branches.forEach { branch ->
                ScribeCard(accent = if (branch.id == repo.currentBranchId.value) ScribeTeal else null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = branch.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            ScribeCode("${branch.code} · ${branch.kind}")
                        }
                        StatusTag(branch.dayStatus.name)
                        Spacer(Modifier.width(8.dp))
                        ScribeButton(
                            text = if (branch.id == repo.currentBranchId.value) "Scribing" else "Scribe here",
                            onClick = {
                                repo.currentBranchId.value = branch.id
                                logInfo("ScribeBranch", "branch picked ${branch.id}")
                                onPick()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ScribeLink(text = "Back to login", onClick = onBack)
        }
    }
}

@Composable
private fun ScribeShell(repo: SessionScribeFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(STab.TODAY) }
    val branch = repo.currentBranch()
    val unread = repo.notes.count { !it.read }
    val pendingCount = repo.pendingFor(repo.currentBranchId.value).size
    val inboxPending = repo.invites.count { it.status == SInviteStatus.PENDING && it.direction == "IN" }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .background(ScribeRail)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ScribeCode("SCRIBE DESK · QUIAPO")
            Text(text = "Session scribe", fontWeight = FontWeight.Black, fontSize = 20.sp, color = ScribeRailInk)
            Text(text = "Minimal taps, smart defaults", fontSize = 12.sp, color = ScribeRailMuted)
            Spacer(Modifier.height(12.dp))
            RailItem("Today", tab == STab.TODAY, if (pendingCount > 0) "$pendingCount" else "") { tab = STab.TODAY }
            RailItem("Sessions", tab == STab.SESSIONS, if (pendingCount > 0) "$pendingCount" else "") { tab = STab.SESSIONS }
            RailItem("Clients", tab == STab.CLIENTS) { tab = STab.CLIENTS }
            RailItem("Finance", tab == STab.FINANCE) { tab = STab.FINANCE }
            RailItem("Team", tab == STab.TEAM, if (inboxPending > 0) "$inboxPending" else "") { tab = STab.TEAM }
            RailItem("Mailbox", tab == STab.MAIL, if (unread > 0) "$unread" else "") { tab = STab.MAIL }
            RailItem("Audit log", tab == STab.AUDIT) { tab = STab.AUDIT }
            RailItem("Profile", tab == STab.PROFILE) { tab = STab.PROFILE }
            Spacer(Modifier.height(12.dp))
            ScribeCode("ON DUTY: ${repo.currentUser().name.uppercase()}")
            ScribeCode(repo.currentUser().role.uppercase())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DayBanner(
                state = branch.dayStatus.name,
                branchName = "${branch.name} · ${branch.code}",
                note = "Branch day stays editable until 04:00 Asia/Manila next morning, then flips OPEN to PAST.",
            )
            Spacer(Modifier.height(12.dp))
            when (tab) {
                STab.TODAY -> ScribeTodayScreen(repo = repo, onOpenSessions = { tab = STab.SESSIONS })
                STab.SESSIONS -> ScribeSessionsScreen(repo = repo)
                STab.CLIENTS -> ScribeClientsScreen(repo = repo)
                STab.FINANCE -> ScribeFinanceScreen(repo = repo)
                STab.TEAM -> ScribeTeamScreen(repo = repo, onOpenMail = { tab = STab.MAIL })
                STab.MAIL -> ScribeMailScreen(repo = repo)
                STab.AUDIT -> ScribeAuditScreen(repo = repo)
                STab.PROFILE -> ScribeProfileScreen(repo = repo, onLogout = onLogout)
            }
        }
    }
}
