package com.companyb.companyapp.proto.shifthandover

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

// #771 — shift-handover prototype shell: sign-in gate, ONBOARDING lock, branch gate,
// changeover shell with branch-day banner and outgoing/incoming crew tape.

enum class ShDest(val label: String) {
    BOARD("Changeover"),
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
fun ProtoShiftHandoverApp(onBack: () -> Unit, repo: ShiftRepo = remember { ShiftRepo() }) {
    ShTheme {
        val user = repo.currentUser
        when {
            user == null -> ShLoginGate(repo)
            user.locked -> ShLockedGate(repo)
            repo.currentBranchId.isBlank() -> ShBranchGate(repo)
            else -> ShShell(repo, onBack)
        }
    }
}

@Composable
private fun ShLoginGate(repo: ShiftRepo) {
    Box(Modifier.fillMaxSize().background(ShColors.Depot).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShTape("SHIFT CHANGEOVER · PROTOTYPE")
            ShHeadline("Clock in. Hand over. Nothing drops.")
            ShNote("Shift-change obsessed dashboard: clock-out writes a handover summary, the incoming crew sees open items, relief context carries across. Fake sign-in — no network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                ShRowCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ShDot(if (u.locked) ShColors.Faded else ShColors.Tape)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = ShColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = ShColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        ShPrimary("Clock in") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.clockedIn = false
                            repo.onRelief = false
                            repo.reliefAccess = ShReliefState.NONE
                            repo.log("${u.name} signed in (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShLockedGate(repo: ShiftRepo) {
    Box(Modifier.fillMaxSize().background(ShColors.Depot).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(540.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShTape("LOCKED")
            ShHeadline("Locked out — ONBOARDING")
            ShCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                            "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS before " +
                            "clock-in, handover, or relief become visible.",
                        color = ShColors.Paper,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    ShNote("Fake-data flow: the lock gate renders instead of any dashboard content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun ShBranchGate(repo: ShiftRepo) {
    Box(Modifier.fillMaxSize().background(ShColors.Depot).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "SIGNED IN AS ${repo.currentUser?.name?.uppercase()}",
                color = ShColors.Tape,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            ShHeadline("Which branch are you clocking into?")
            ShNote("Clocking into a non-home branch starts Relief Duty: view-only until a relief grant lands.")
            repo.branches.forEach { b ->
                ShRowCard(onClick = {
                    repo.currentBranchId = b.id
                    val home = repo.currentUser?.homeBranchId == b.id
                    repo.onRelief = !home
                    repo.reliefAccess = if (home) ShReliefState.NONE else ShReliefState.DUTY_VIEW_ONLY
                    repo.clockedIn = true
                    repo.log("CLOCK_IN ${b.name}" + if (home) " (home)" else " (relief duty, view-only)")
                }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = ShColors.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · ${b.shiftLabel}", color = ShColors.Faded, fontSize = 12.sp)
                        }
                        ShStatusChip(b.dayStatus.name, chipFor(b.dayStatus))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

fun chipFor(day: ShDayStatus): androidx.compose.ui.graphics.Color = when (day) {
    ShDayStatus.OPEN -> ShColors.Incoming
    ShDayStatus.PAST -> ShColors.Tape
    ShDayStatus.REMITTED -> ShColors.Teal
}

@Composable
private fun ShShell(repo: ShiftRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(ShDest.BOARD) }
    val branch = repo.currentBranch()
    Row(Modifier.fillMaxSize().background(ShColors.Depot)) {
        Column(
            Modifier.width(212.dp).fillMaxHeight().background(ShColors.Panel)
                .padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ShTape("CHANGEOVER")
            Text(repo.currentUser?.name ?: "—", color = ShColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "${repo.currentUser?.role} · ${branch?.name ?: "—"}" + if (repo.onRelief) " · RELIEF" else "",
                color = ShColors.Faded,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            ShDest.entries.forEach { d ->
                val selected = d == dest
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selected) ShColors.Tape else androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        )
                        .clickable { dest = d }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.label,
                            color = if (selected) ShColors.TapeInk else ShColors.Paper,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (d == ShDest.MAILBOX && repo.notices.any { !it.read }) {
                            Spacer(Modifier.width(6.dp))
                            ShDot(ShColors.Outgoing)
                        }
                        if (d == ShDest.BOARD && repo.openItems.any { it.state == ShOpenState.OPEN }) {
                            Spacer(Modifier.width(6.dp))
                            ShDot(ShColors.Outgoing)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            ShLink("Exit prototype") { onBack() }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
            branch?.let { ShDayBanner(repo, it) }
            Spacer(Modifier.height(12.dp))
            when (dest) {
                ShDest.BOARD -> ShBoardScreen(repo) { dest = it }
                ShDest.HANDOVER -> ShHandoverScreen(repo)
                ShDest.SESSIONS -> ShSessionsScreen(repo)
                ShDest.CLIENTS -> ShClientsScreen(repo)
                ShDest.FINANCE -> ShFinanceScreen(repo)
                ShDest.TEAM -> ShTeamScreen(repo)
                ShDest.MAILBOX -> ShMailboxScreen(repo)
                ShDest.AUDIT -> ShAuditScreen(repo)
                ShDest.PROFILE -> ShProfileScreen(repo, onBack) { dest = it }
            }
        }
    }
}

@Composable
fun ShDayBanner(repo: ShiftRepo, branch: ShBranch) {
    Box(
        Modifier.fillMaxWidth()
            .background(branch.dayStatus.banner(), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShStatusChip("BRANCH DAY · ${branch.dayStatus.name}", chipFor(branch.dayStatus))
                Spacer(Modifier.width(10.dp))
                Text(branch.name, color = ShColors.Paper, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (repo.onRelief) ShStatusChip("RELIEF · ${repo.reliefAccess.name}", ShColors.Outgoing)
                if (repo.clockedIn) ShStatusChip("CLOCKED IN", ShColors.Incoming)
            }
            ShNote(
                "Operational-day boundary 04:00 Asia/Manila — this day stays editable until 04:00 tomorrow, then turns PAST lazily. " +
                    (if (repo.onRelief) "Relief duty here expires 04:00 Manila; pay comes from this branch drawer." else branch.shiftLabel),
            )
        }
    }
}
