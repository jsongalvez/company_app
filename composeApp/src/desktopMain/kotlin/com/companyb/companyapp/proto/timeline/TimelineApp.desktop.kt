package com.companyb.companyapp.proto.timeline

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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

// #760 — timeline prototype shell: login rail, branch select, timeline-first nav.

enum class TlDest(val label: String) {
    HOME("Home & Clock"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoTimelineApp(onBack: () -> Unit, repo: TimelineRepo = remember { TimelineRepo() }) {
    TlTheme {
        val user = repo.currentUser
        when {
            user == null -> TlLoginGate(repo)
            user.locked -> TlLockedGate(repo)
            repo.currentBranchId.isBlank() -> TlBranchGate(repo)
            else -> TlShell(repo, onBack)
        }
    }
}

@Composable
private fun TlLoginGate(repo: TimelineRepo) {
    Box(Modifier.fillMaxSize().background(TlColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("THE DAYBOOK", color = TlColors.Rail, fontSize = 13.sp, fontWeight = FontWeight.Black)
            TlHeadline("Timeline-first sessions")
            TlNote("Pick a practitioner to walk the day rail. Fake sign-in — no network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                TlCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TlRailDot(if (u.locked) TlColors.Slate else TlColors.Teal)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = TlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = TlColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        TlPrimary("Sign in") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.log("${u.name} signed in (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TlLockedGate(repo: TimelineRepo) {
    Box(Modifier.fillMaxSize().background(TlColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TlHeadline("Locked out — ONBOARDING")
            TlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                            "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS.",
                        color = TlColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    TlNote("Fake-data flow: the gate renders instead of any dashboard content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TlGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun TlBranchGate(repo: TimelineRepo) {
    Box(Modifier.fillMaxSize().background(TlColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SIGNED IN AS ${repo.currentUser?.name?.uppercase()}", color = TlColors.Rail, fontSize = 12.sp, fontWeight = FontWeight.Black)
            TlHeadline("Choose today's branch")
            repo.branches.forEach { b ->
                TlCard {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} selected branch ${b.name}")
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = TlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · day ${b.dayStatus.name}", color = TlColors.Faded, fontSize = 12.sp)
                        }
                        TlLink("Enter") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} selected branch ${b.name}")
                        }
                    }
                }
            }
            TlGhost("Back to sign-in") { repo.currentUser = null }
        }
    }
}

@Composable
private fun TlShell(repo: TimelineRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(TlDest.SESSIONS) }
    val branch = repo.currentBranch()
    val unread = repo.notices.count { !it.read }
    Row(Modifier.fillMaxSize().background(TlColors.Paper)) {
        Column(
            Modifier.width(240.dp).fillMaxHeight().background(TlColors.Card).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("THE DAYBOOK", color = TlColors.Rail, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentUser?.name} · ${repo.currentUser?.role}",
                color = TlColors.Faded,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text("${branch.name} · ${branch.dayStatus.name}", color = TlColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TlDest.entries.forEach { d ->
                val label = if (d == TlDest.MAILBOX && unread > 0) "${d.label} ($unread)" else d.label
                TlNavItem(label, dest == d) { dest = d }
            }
            Spacer(Modifier.height(12.dp))
            TlLink("‹ Switch branch") { repo.currentBranchId = "" }
            TlLink("‹ Sign out") {
                repo.log("${repo.currentUser?.name} signed out")
                repo.currentUser = null
            }
            TlLink("‹ Exit prototype") { onBack() }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TlDayBanner(branch.dayStatus, branch.name)
                when (dest) {
                    TlDest.HOME -> TlHomeScreen(repo)
                    TlDest.SESSIONS -> TlSessionsScreen(repo)
                    TlDest.CLIENTS -> TlClientsScreen(repo)
                    TlDest.FINANCE -> TlFinanceScreen(repo)
                    TlDest.TEAM -> TlTeamScreen(repo)
                    TlDest.MAILBOX -> TlMailboxScreen(repo)
                    TlDest.AUDIT -> TlAuditScreen(repo)
                    TlDest.PROFILE -> TlProfileScreen(repo, onBack)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
