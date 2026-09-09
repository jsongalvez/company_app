package com.companyb.companyapp.proto.delegationboard

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

private enum class DAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class DTab { BOARD, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun DelegationBoardProtoApp() {
    DelegationBoardTheme {
        val repo = remember { DelegationBoardFakeRepo() }
        var phase by remember { mutableStateOf(DAuthPhase.LOGIN) }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                DAuthPhase.LOGIN -> {
                    DLogin(
                        onLogin = {
                            logInfo("DelegationBoard", "login submitted")
                            phase = DAuthPhase.BRANCH_SELECT
                        },
                        onOnboardingDemo = { phase = DAuthPhase.ONBOARDING_LOCKED },
                    )
                }

                DAuthPhase.ONBOARDING_LOCKED -> {
                    DOnboardingLocked(
                        onBack = { phase = DAuthPhase.LOGIN },
                    )
                }

                DAuthPhase.BRANCH_SELECT -> {
                    DBranchSelect(
                        repo = repo,
                        onPick = { phase = DAuthPhase.APP },
                        onBack = { phase = DAuthPhase.LOGIN },
                    )
                }

                DAuthPhase.APP -> {
                    DShell(
                        repo = repo,
                        onLogout = { phase = DAuthPhase.LOGIN },
                    )
                }
            }
        }
    }
}

@Composable
private fun DLogin(
    onLogin: () -> Unit,
    onOnboardingDemo: () -> Unit,
) {
    var email by remember { mutableStateOf("rhea@lingap.example") }
    var password by remember { mutableStateOf("mission-014") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(460.dp)) {
            ManifestCode("DELEGATION MANIFEST · MSN-014 · DAY 14")
            Spacer(Modifier.height(8.dp))
            Text(text = "Mission delegation board", fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(
                text = "Roster, coverage gaps, and invite flow for the Lingap medical mission — fake data, no network.",
                fontSize = 13.sp,
                color = ManifestMuted,
            )
            Spacer(Modifier.height(16.dp))
            ManifestCard(accent = DispatchTeal) {
                SectionHeader(index = "01", title = "Clock in to dispatch")
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
                    colors = ButtonDefaults.buttonColors(containerColor = DispatchTeal),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open the delegation board")
                }
                TextButton(onClick = onOnboardingDemo) {
                    Text("Preview the ONBOARDING welcome", color = TriageRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DOnboardingLocked(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(520.dp)) {
            ManifestCard(accent = TriageAmber) {
                SectionHeader(index = "00", title = "Welcome, delegate")
                Text(text = "Jojo K. · ONBOARDING", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                TriageTag(text = "Locked — empty capability bundle", color = TriageAmber, soft = TriageAmberSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        "A freshly registered user holds zero capabilities — locked out even after a branch " +
                            "assignment — until MANAGE_USERS grants a real role. Ask a MANAGER to tag you in.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                BoardGhostButton(text = "Back to login", onClick = onBack)
            }
        }
    }
}

@Composable
private fun DBranchSelect(
    repo: DelegationBoardFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(560.dp)) {
            ManifestCode("STEP 02 · PICK YOUR POST")
            Spacer(Modifier.height(8.dp))
            Text(text = "Where are you posted today?", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            repo.branches.forEach { branch ->
                ManifestCard(accent = if (branch.id == repo.currentBranchId.value) DispatchTeal else null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = branch.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            ManifestCode("${branch.code} · ${branch.kind}")
                        }
                        StatusTag(branch.dayStatus.name)
                        Spacer(Modifier.width(8.dp))
                        BoardButton(
                            text = if (branch.id == repo.currentBranchId.value) "Posted" else "Post here",
                            onClick = {
                                repo.currentBranchId.value = branch.id
                                logInfo("DelegationBoard", "branch picked ${branch.id}")
                                onPick()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            BoardLink(text = "Back to login", onClick = onBack)
        }
    }
}

@Composable
private fun DShell(
    repo: DelegationBoardFakeRepo,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(DTab.BOARD) }
    val branch = repo.currentBranch()
    val unread = repo.notes.count { !it.read }
    val pendingInvites = repo.invites.count { it.status == DInviteStatus.PENDING && it.direction == "IN" }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .width(248.dp)
                    .fillMaxHeight()
                    .background(ManifestCard)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            ManifestCode("MSN-014 · DAY 14")
            Text(text = "Delegation", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(text = "Lingap mission dispatch", fontSize = 12.sp, color = ManifestMuted)
            Spacer(Modifier.height(12.dp))
            RailItem("Board", tab == DTab.BOARD, if (pendingInvites > 0) "$pendingInvites" else "") { tab = DTab.BOARD }
            RailItem("Sessions", tab == DTab.SESSIONS) { tab = DTab.SESSIONS }
            RailItem("Clients", tab == DTab.CLIENTS) { tab = DTab.CLIENTS }
            RailItem("Finance", tab == DTab.FINANCE) { tab = DTab.FINANCE }
            RailItem("Team", tab == DTab.TEAM) { tab = DTab.TEAM }
            RailItem("Mailbox", tab == DTab.MAIL, if (unread > 0) "$unread" else "") { tab = DTab.MAIL }
            RailItem("Audit log", tab == DTab.AUDIT) { tab = DTab.AUDIT }
            RailItem("Profile", tab == DTab.PROFILE) { tab = DTab.PROFILE }
            Spacer(Modifier.height(12.dp))
            ManifestCode("ON DUTY: ${repo.currentUser().name.uppercase()}")
            ManifestCode(repo.currentUser().role.uppercase())
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            StampBanner(
                state = branch.dayStatus.name,
                branchName = "${branch.name} · ${branch.code}",
                note =
                    "Branch day boundary 04:00 Asia/Manila — stays editable until 04:00 the next morning, " +
                        "then flips OPEN to PAST.",
            )
            Spacer(Modifier.height(12.dp))
            when (tab) {
                DTab.BOARD -> DBoardScreen(repo = repo, onOpenMail = { tab = DTab.MAIL })
                DTab.SESSIONS -> DSessionsScreen(repo = repo)
                DTab.CLIENTS -> DClientsScreen(repo = repo)
                DTab.FINANCE -> DFinanceScreen(repo = repo)
                DTab.TEAM -> DTeamScreen(repo = repo)
                DTab.MAIL -> DMailScreen(repo = repo)
                DTab.AUDIT -> DAuditScreen(repo = repo)
                DTab.PROFILE -> DProfileScreen(repo = repo, onLogout = onLogout)
            }
        }
    }
}
