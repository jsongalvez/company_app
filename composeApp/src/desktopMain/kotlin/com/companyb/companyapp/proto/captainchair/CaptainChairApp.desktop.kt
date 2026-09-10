package com.companyb.companyapp.proto.captainchair

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

// #825 — captain-chair shell: sign-in gate, ONBOARDING lock, branch gate,
// helm shell with branch-day banner and helm-console rail.

enum class CcDest(val label: String) {
    HELM("Helm"),
    HANDOVER("Handover"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoCaptainChairApp(onBack: () -> Unit, repo: CaptainRepo = remember { CaptainRepo() }) {
    CcTheme {
        val user = repo.currentUser
        when {
            user == null -> CcLoginGate(repo)
            user.locked -> CcLockedGate(repo)
            repo.currentBranchId.isBlank() -> CcBranchGate(repo)
            else -> CcShell(repo, onBack)
        }
    }
}

@Composable
private fun CcLoginGate(repo: CaptainRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Bridge).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CcHelmPlate("BRANCH CAPTAIN CHAIR · HELM PROTOTYPE")
            CcHeadline("Take the chair. The whole watch answers here.")
            CcNote("Shift-lead helm: crew, relief, exceptions, handover at a glance. Fake sign-in — no network, no backend.")
            CcHelmWheel()
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                CcRowCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CcLamp(if (u.locked) CcColors.Faded else CcColors.Brass)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = CcColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name} · station ${u.station}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = CcColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        CcPrimary("Take chair") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.clockedIn = false
                            repo.onRelief = false
                            repo.reliefAccess = CcReliefState.NONE
                            repo.log("${u.name} signed in (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CcLockedGate(repo: CaptainRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Bridge).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CcHelmPlate("LOCKED · BELOW DECKS")
            CcHeadline("No chair yet — ONBOARDING")
            CcCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                            "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS before " +
                            "the helm, sessions, relief, or remittance become visible.",
                        color = CcColors.Paper,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    CcNote("Fake-data flow: the lock gate renders instead of any helm content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcGhost("Switch sailor") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun CcBranchGate(repo: CaptainRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Bridge).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "SIGNED IN AS ${repo.currentUser?.name?.uppercase()}",
                color = CcColors.Brass,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            CcHeadline("Which branch are you taking the chair for?")
            CcNote("Taking a non-home branch starts Relief Duty: helm view-only until a relief grant lands.")
            repo.branches.forEach { b ->
                CcRowCard(onClick = {
                    repo.currentBranchId = b.id
                    val home = repo.currentUser?.homeBranchId == b.id
                    repo.onRelief = !home
                    repo.reliefAccess = if (home) CcReliefState.NONE else CcReliefState.DUTY_VIEW_ONLY
                    repo.clockedIn = true
                    repo.log("CLOCK_IN ${b.name}" + if (home) " (home)" else " (relief duty, view-only)")
                }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CcLamp(b.dayStatus.lamp())
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = CcColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · ${b.watchLabel}", color = CcColors.Faded, fontSize = 12.sp)
                        }
                        CcChip(b.dayStatus.name, b.dayStatus.lamp())
                    }
                }
            }
            CcNote("Branch Day boundary: 04:00 Asia/Manila. A day stays OPEN until 04:00, then PAST; REMITTED after both SESSION + PRODUCT submit.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcGhost("Switch sailor") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun CcShell(repo: CaptainRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(CcDest.HELM) }
    val branch = repo.currentBranch()
    Row(Modifier.fillMaxSize().background(CcColors.Bridge)) {
        Column(
            Modifier.width(216.dp).fillMaxHeight().background(CcColors.Console)
                .padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CcHelmPlate("CAPTAIN CHAIR")
            Text(repo.currentUser?.name ?: "—", color = CcColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "${repo.currentUser?.role} · ${branch?.name ?: "—"}" + if (repo.onRelief) " · RELIEF" else "",
                color = CcColors.Faded,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            CcHelmWheel()
            Spacer(Modifier.height(6.dp))
            CcDest.entries.forEach { d ->
                val selected = d == dest
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selected) CcColors.Brass else androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        )
                        .clickable { dest = d }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.label,
                            color = if (selected) CcColors.BrassInk else CcColors.Paper,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (d == CcDest.MAILBOX && repo.notices.any { !it.read }) {
                            Spacer(Modifier.width(6.dp))
                            CcLamp(CcColors.Port)
                        }
                        if (d == CcDest.HELM && repo.exceptions.any { !it.cleared }) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${repo.exceptions.count { !it.cleared }}",
                                color = if (selected) CcColors.BrassInk else CcColors.Brass,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            CcLink("Leave helm (exit)") { onBack() }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CcDayBanner(repo)
            when (dest) {
                CcDest.HELM -> CcHelmScreen(repo, go = { dest = it })
                CcDest.HANDOVER -> CcHandoverScreen(repo)
                CcDest.SESSIONS -> CcSessionsScreen(repo)
                CcDest.CLIENTS -> CcClientsScreen(repo)
                CcDest.FINANCE -> CcFinanceScreen(repo)
                CcDest.TEAM -> CcTeamScreen(repo)
                CcDest.MAILBOX -> CcMailboxScreen(repo)
                CcDest.AUDIT -> CcAuditScreen(repo)
                CcDest.PROFILE -> CcProfileScreen(repo, onBack)
            }
        }
    }
}

@Composable
fun CcDayBanner(repo: CaptainRepo) {
    val branch = repo.currentBranch() ?: return
    Box(
        Modifier.fillMaxWidth()
            .background(branch.dayStatus.banner(), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CcLamp(branch.dayStatus.lamp())
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${branch.name} · Branch Day ${branch.dayStatus.name}",
                    color = CcColors.Paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Boundary 04:00 Asia/Manila · ${branch.watchLabel}" +
                        if (repo.onRelief) " · RELIEF ${repo.reliefAccess.name} (view-only until granted)" else "",
                    color = CcColors.Faded,
                    fontSize = 12.sp,
                )
            }
            CcChip(if (repo.clockedIn) "ON WATCH" else "OFF WATCH", if (repo.clockedIn) CcColors.Starboard else CcColors.Faded)
        }
    }
}
