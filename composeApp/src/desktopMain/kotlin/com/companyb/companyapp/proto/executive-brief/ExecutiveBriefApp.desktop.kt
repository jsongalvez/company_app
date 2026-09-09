package com.companyb.companyapp.proto.executivebrief

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #770 — executive-brief shell: masthead gates (login, ONBOARDING lock, branch select),
// then the one-screen morning brief with drill-down rail nav.

private const val TAG = "ExecutiveBrief"

@Composable
fun ProtoExecutiveBriefApp(onBack: () -> Unit, repo: EbRepo = remember { EbRepo() }) {
    EbTheme {
        logInfo(TAG, "morning brief opened")
        val user = repo.currentUser
        when {
            user == null -> EbLoginGate(repo)
            user.locked -> EbLockedGate(repo)
            repo.currentBranchId.isBlank() -> EbBranchGate(repo)
            else -> EbShell(repo, onBack)
        }
    }
}

@Composable
private fun EbLoginGate(repo: EbRepo) {
    Box(Modifier.fillMaxSize().background(EbColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EbMasthead("Fake edition — sign in to open your brief", "EST. FAKE 2026")
            EbNote("Pick a reader. The Owner gets the full morning edition; every role walks the same fake branch day. No network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                EbCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = EbColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = EbColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        if (u.role == "OWNER") EbBrass("Open brief") { signIn(repo, u) }
                        else EbGhost("Sign in") { signIn(repo, u) }
                    }
                }
            }
        }
    }
}

private fun signIn(repo: EbRepo, u: EbUser) {
    repo.currentUser = u
    repo.currentBranchId = ""
    repo.log("${u.name} signed in (fake)")
    logInfo(TAG, "${u.name} signed in as ${u.role}")
}

@Composable
private fun EbLockedGate(repo: EbRepo) {
    Box(Modifier.fillMaxSize().background(EbColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EbKicker("Locked edition")
            EbHeadline("ONBOARDING holds no keys")
            EbCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos carries the ONBOARDING bundle: zero capabilities, so nothing derives — " +
                            "even with a branch assignment. A MANAGER or OWNER must grant a real role via MANAGE_USERS.",
                        color = EbColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    EbNote("Fake-data flow: the gate renders instead of any brief content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EbGhost("Switch reader") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun EbBranchGate(repo: EbRepo) {
    Box(Modifier.fillMaxSize().background(EbColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "READER · ${(repo.currentUser?.name ?: "?").uppercase()}",
                color = EbColors.Brass,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            EbHeadline("Which branch are you reading today?")
            repo.branches.forEach { b ->
                EbCard(onClick = {
                    repo.currentBranchId = b.id
                    repo.log("${repo.currentUser?.name} opened ${b.name} (fake)")
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = EbColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · day ${b.dayStatus.name}", color = EbColors.Faded, fontSize = 12.sp)
                        }
                        EbLink("Open") { repo.currentBranchId = b.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun EbShell(repo: EbRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(EbDest.BRIEF) }
    Row(Modifier.fillMaxSize().background(EbColors.Paper)) {
        Column(
            Modifier.width(240.dp).fillMaxHeight()
                .background(EbColors.Card)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MORNING BRIEF", color = EbColors.Pine, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                (repo.currentUser?.name ?: "?") + " · " + (repo.currentUser?.role ?: "?"),
                color = EbColors.Faded,
                fontSize = 12.sp,
            )
            EbRule()
            EbShellNav(current = dest, unread = repo.unreadCount()) { dest = it }
            Spacer(Modifier.weight(1f))
            EbGhost("Exit prototype") { onBack() }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EbDayBanner(repo.currentBranch())
            when (dest) {
                EbDest.BRIEF -> EbBriefScreen(repo, onDrill = { dest = it })
                EbDest.HOME -> EbHomeScreen(repo)
                EbDest.SESSIONS -> EbSessionsScreen(repo)
                EbDest.CLIENTS -> EbClientsScreen(repo)
                EbDest.FINANCE -> EbFinanceScreen(repo)
                EbDest.TEAM -> EbTeamScreen(repo)
                EbDest.MAILBOX -> EbMailboxScreen(repo)
                EbDest.AUDIT -> EbAuditScreen(repo)
                EbDest.PROFILE -> EbProfileScreen(repo, onBack)
            }
        }
    }
}

@Composable
private fun EbBriefScreen(repo: EbRepo, onDrill: (EbDest) -> Unit) {
    val total = repo.totalYesterday()
    val target = repo.totalTarget()
    val pct = if (target > 0) (total * 100 / target) else 100
    EbMasthead("Yesterday across ${repo.branches.size} branches · all figures fake", if (repo.clockedIn) "ON DUTY" else "OFF DUTY")

    Box(
        Modifier.fillMaxWidth()
            .background(EbColors.Pine, RoundedCornerShape(12.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EbKicker("Yesterday vs target", light = true)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("₱$total", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(12.dp))
                Text(
                    "of ₱$target · $pct%",
                    color = EbColors.BrassBright,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            EbBar(if (target > 0) total.toFloat() / target else 1f, EbColors.BrassBright)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EbHeroStat("${repo.branches.sumOf { it.sessionsDone }}", "sessions done")
                EbHeroStat("${repo.branches.sumOf { it.noShows }}", "no-shows")
                EbHeroStat("${repo.openDrafts()}", "open drafts")
                EbHeroStat("${repo.unreadCount()}", "unread mail")
            }
            EbNote("Tap any figure to drill down — the brief never scrolls past one screen of panic.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EbDrillChip("Sessions") { onDrill(EbDest.SESSIONS) }
                EbDrillChip("Finance") { onDrill(EbDest.FINANCE) }
                EbDrillChip("Mailbox") { onDrill(EbDest.MAILBOX) }
            }
        }
    }

    EbSubhead("Needs you — exceptions only (${repo.exceptions.size})")
    if (repo.exceptions.isEmpty()) {
        EbCard {
            Text("All clear. Nothing needs the Owner this morning — the house runs itself.", color = EbColors.Sage, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        repo.exceptions.forEach { x ->
            EbCard(onClick = { onDrill(x.dest) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.background(
                            if (x.severity == "ACTION") EbColors.Alert else EbColors.Brass,
                            RoundedCornerShape(6.dp),
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(x.severity, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(x.headline, color = EbColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(x.detail, color = EbColors.Faded, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EbLink("Drill down") { onDrill(x.dest) }
                        EbLink("Acknowledge") { repo.dismissException(x.id) }
                    }
                }
            }
        }
    }

    EbSubhead("Yesterday by branch — vs target")
    repo.branches.forEach { b ->
        EbCard(onClick = { onDrill(EbDest.FINANCE) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, color = EbColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(b.dayStatus.name, color = EbColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (b.yesterdayTarget > 0) {
                    EbBar(b.yesterdayRevenue.toFloat() / b.yesterdayTarget, if (b.yesterdayRevenue >= b.yesterdayTarget) EbColors.Sage else EbColors.Brass)
                    Text(
                        "₱${b.yesterdayRevenue} of ₱${b.yesterdayTarget} · ${b.sessionsDone}/${b.sessionsPlanned} sessions · ${b.noShows} no-show",
                        color = EbColors.Faded,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        "${b.sessionsDone}/${b.sessionsPlanned} free-care sessions · mission branch carries no revenue target",
                        color = EbColors.Faded,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EbHeroStat(value: String, label: String) {
    Column(
        Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = EbColors.BrassBright, fontSize = 11.sp)
    }
}

@Composable
private fun EbDrillChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text("Drill: $label →", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
