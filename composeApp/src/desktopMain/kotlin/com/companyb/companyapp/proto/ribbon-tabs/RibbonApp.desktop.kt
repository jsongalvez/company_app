package com.companyb.companyapp.proto.ribbontabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.companyb.companyapp.util.logInfo

private enum class RibbonPhase { LOGIN, ONBOARDING, BRANCHES, APP }

enum class RibbonTab(val label: String) {
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAIL("Mail"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun RibbonApp() {
    logInfo("RibbonTabs", "ribbon-tabs prototype opened")
    RibbonTheme {
        var phase by remember { mutableStateOf(RibbonPhase.LOGIN) }
        var repo by remember { mutableStateOf(RibbonFakeRepo()) }
        Box(modifier = Modifier.fillMaxSize().background(RibbonPage)) {
            when (phase) {
                RibbonPhase.LOGIN ->
                    RibbonLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = RibbonPhase.BRANCHES
                        },
                        onPreviewOnboarding = { phase = RibbonPhase.ONBOARDING },
                    )
                RibbonPhase.ONBOARDING -> RibbonOnboardingLocked(onBack = { phase = RibbonPhase.LOGIN })
                RibbonPhase.BRANCHES ->
                    RibbonBranchSelect(
                        repo = repo,
                        onPick = { phase = RibbonPhase.APP },
                        onBack = { phase = RibbonPhase.LOGIN },
                    )
                RibbonPhase.APP ->
                    RibbonShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = RibbonPhase.LOGIN
                        },
                        onReset = { repo = RibbonFakeRepo().also { it.email = repo.email; it.branchId = repo.branchId } },
                        onSwitchBranch = { phase = RibbonPhase.BRANCHES },
                    )
            }
        }
    }
}

@Composable
private fun RibbonShell(
    repo: RibbonFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    var tab by remember { mutableStateOf(RibbonTab.HOME) }
    var sessionFilter by remember { mutableStateOf("ALL") }
    var veilLifted by remember { mutableStateOf(false) }
    val unread = repo.notices.count { !it.read }
    val branch = repo.currentBranch()
    Column(modifier = Modifier.fillMaxSize()) {
        RibbonTitleBar(repo = repo, unread = unread, onOpenMail = { tab = RibbonTab.MAIL }, onSwitchBranch = onSwitchBranch)
        RibbonTabStrip(tab = tab, unread = unread, onPick = { tab = it })
        RibbonCommandBar(
            tab = tab,
            repo = repo,
            sessionFilter = sessionFilter,
            onFilter = { sessionFilter = it },
            veilLifted = veilLifted,
            onVeil = { veilLifted = !veilLifted },
            onReset = onReset,
        )
        RibbonDayBanner(branch = branch)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(RibbonWash)) {
            when (tab) {
                RibbonTab.HOME -> RibbonHome(repo)
                RibbonTab.SESSIONS -> RibbonSessions(repo, sessionFilter)
                RibbonTab.CLIENTS -> RibbonClients(repo, veilLifted)
                RibbonTab.FINANCE -> RibbonFinance(repo)
                RibbonTab.TEAM -> RibbonTeam(repo)
                RibbonTab.MAIL -> RibbonMailbox(repo)
                RibbonTab.AUDIT -> RibbonAudit(repo)
                RibbonTab.PROFILE -> RibbonProfile(repo, onLogout = onLogout, onReset = onReset, onSwitchBranch = onSwitchBranch)
            }
        }
        RibbonStatusBar(repo = repo)
    }
}

@Composable
private fun RibbonTitleBar(
    repo: RibbonFakeRepo,
    unread: Int,
    onOpenMail: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RibbonBlue).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◫", fontSize = 16.sp, color = RibbonBlueInk)
            Text("CompanyApp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RibbonBlueInk)
            Text("— Ribbon", fontSize = 12.sp, color = RibbonBlueInk.copy(alpha = 0.75f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                repo.currentBranch().name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RibbonBlueInk,
                modifier = Modifier.clickable { onSwitchBranch() },
            )
            Text(
                if (unread > 0) "Mail ($unread)" else "Mail",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RibbonBlueInk,
                modifier = Modifier.clickable { onOpenMail() },
            )
            Text(
                if (repo.clockedIn) "● On shift" else "○ Off shift",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RibbonBlueInk,
            )
        }
    }
}

@Composable
private fun RibbonTabStrip(
    tab: RibbonTab,
    unread: Int,
    onPick: (RibbonTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RibbonBlueDark).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RibbonTab.entries.forEach { t ->
            val on = t == tab
            val badge = t == RibbonTab.MAIL && unread > 0
            Box(
                modifier = Modifier
                    .background(if (on) RibbonBar else RibbonBlueDark)
                    .clickable { onPick(t) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    t.label + if (badge) " ($unread)" else "",
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                    color = if (on) RibbonBlueDark else RibbonBlueInk.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun RibbonCommandBar(
    tab: RibbonTab,
    repo: RibbonFakeRepo,
    sessionFilter: String,
    onFilter: (String) -> Unit,
    veilLifted: Boolean,
    onVeil: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RibbonBar).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (tab) {
            RibbonTab.HOME -> {
                RibbonGroup("Shift") {
                    if (repo.clockedIn) {
                        RibbonOutline("Clock out", onClick = { repo.clock(false) })
                    } else {
                        RibbonButton("Clock in", onClick = { repo.clock(true) })
                    }
                    RibbonOutline("Day: ${repo.currentBranch().day.name}", onClick = {})
                }
                RibbonGroup("Relief") {
                    RibbonOutline("Mark all mail read", onClick = { repo.markAllRead() })
                    RibbonOutline("Reset demo", onClick = onReset)
                }
            }
            RibbonTab.SESSIONS -> {
                RibbonGroup("Views") {
                    listOf("ALL", "PEND", "DONE", "N-SHOW", "CXLD").forEach { f ->
                        if (f == sessionFilter) {
                            RibbonButton(f, onClick = { onFilter(f) })
                        } else {
                            RibbonOutline(f, onClick = { onFilter(f) })
                        }
                    }
                }
                RibbonGroup("New") {
                    RibbonOutline("Walk-in +", onClick = { repo.addWalkIn("", "") })
                }
            }
            RibbonTab.CLIENTS -> {
                RibbonGroup("Directory") {
                    if (veilLifted) {
                        RibbonButton("Veil: lifted", onClick = onVeil)
                    } else {
                        RibbonOutline("Veil: anonymized", onClick = onVeil)
                    }
                }
                RibbonGroup("Policy") {
                    RibbonNote("At most one PENDING per client")
                }
            }
            RibbonTab.FINANCE -> {
                RibbonGroup("Draft") {
                    RibbonOutline("Session draft +", onClick = { repo.addDraft(RibbonDraftKind.SESSION, "Session takings", 1200.0, 1) })
                    RibbonOutline("Product draft +", onClick = { repo.addDraft(RibbonDraftKind.PRODUCT, "Retail × 1", 450.0, 1) })
                }
                RibbonGroup("Window") {
                    RibbonNote("Undo within 48h")
                }
            }
            RibbonTab.TEAM -> {
                RibbonGroup("Staff") {
                    RibbonNote("${repo.mates.size} people · 1 ONBOARDING locked")
                }
            }
            RibbonTab.MAIL -> {
                RibbonGroup("Mailbox") {
                    RibbonOutline("Mark all read", onClick = { repo.markAllRead() })
                    RibbonNote("${repo.notices.count { !it.read }} open")
                }
            }
            RibbonTab.AUDIT -> {
                RibbonGroup("Log") {
                    RibbonNote("${repo.audits.size} entries")
                }
            }
            RibbonTab.PROFILE -> {
                RibbonGroup("Account") {
                    if (repo.clockedIn) {
                        RibbonOutline("Clock out", onClick = { repo.clock(false) })
                    } else {
                        RibbonButton("Clock in", onClick = { repo.clock(true) })
                    }
                    RibbonOutline("Reset demo", onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun RibbonDayBanner(branch: RibbonBranch) {
    val copy =
        when (branch.day) {
            RibbonDay.OPEN -> "OPEN — sheet takes bookings and walk-ins"
            RibbonDay.PAST -> "PAST — read-only, book nothing new"
            RibbonDay.REMITTED -> "REMITTED — sealed, snapshot filed"
        }
    val color =
        when (branch.day) {
            RibbonDay.OPEN -> RibbonGreen
            RibbonDay.PAST -> RibbonAmber
            RibbonDay.REMITTED -> RibbonBlueDark
        }
    Row(
        modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f)).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RibbonStamp(branch.day.name, color)
            Text("${branch.name} · $copy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
        }
        Text("Branch day rolls at 04:00 Asia/Manila", fontSize = 11.sp, color = RibbonInkSoft)
    }
}

@Composable
private fun RibbonStatusBar(repo: RibbonFakeRepo) {
    val branch = repo.currentBranch()
    Row(
        modifier = Modifier.fillMaxWidth().background(RibbonBlueDark).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${branch.name} · ${branch.day.name} · ${if (repo.clockedIn) "On shift" else "Off shift"}",
            fontSize = 11.sp,
            color = RibbonBlueInk,
        )
        Text(
            "${repo.branchSessions().count { it.status == RibbonStatus.PENDING }} pending · ${repo.notices.count { !it.read }} unread · ${repo.audits.size} audit",
            fontSize = 11.sp,
            color = RibbonBlueInk.copy(alpha = 0.85f),
        )
    }
}
