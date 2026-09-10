package com.companyb.companyapp.proto.floatingpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class FloatPhase { LOGIN, ONBOARDING, BRANCHES, APP }

enum class FloatTab(val label: String) {
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
fun FloatApp() {
    logInfo("FloatingPanels", "floating-panels prototype opened")
    FloatTheme {
        var phase by remember { mutableStateOf(FloatPhase.LOGIN) }
        var repo by remember { mutableStateOf(FloatFakeRepo()) }
        Box(modifier = Modifier.fillMaxSize().background(FloatCanvasDeep)) {
            when (phase) {
                FloatPhase.LOGIN ->
                    FloatLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = FloatPhase.BRANCHES
                        },
                        onPreviewOnboarding = { phase = FloatPhase.ONBOARDING },
                    )
                FloatPhase.ONBOARDING -> FloatOnboardingLocked(onBack = { phase = FloatPhase.LOGIN })
                FloatPhase.BRANCHES ->
                    FloatBranchSelect(
                        repo = repo,
                        onPick = { phase = FloatPhase.APP },
                        onBack = { phase = FloatPhase.LOGIN },
                    )
                FloatPhase.APP ->
                    FloatShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = FloatPhase.LOGIN
                        },
                        onReset = { repo = FloatFakeRepo().also { it.email = repo.email; it.branchId = repo.branchId } },
                        onSwitchBranch = { phase = FloatPhase.BRANCHES },
                    )
            }
        }
    }
}

@Composable
private fun FloatShell(
    repo: FloatFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    var tab by remember { mutableStateOf(FloatTab.HOME) }
    var sessionFilter by remember { mutableStateOf("ALL") }
    var veilLifted by remember { mutableStateOf(false) }
    val unread = repo.notices.count { !it.read }
    val branch = repo.currentBranch()
    Column(modifier = Modifier.fillMaxSize().background(FloatCanvas)) {
        FloatCanvasBar(repo = repo, unread = unread, onOpenMail = { tab = FloatTab.MAIL }, onSwitchBranch = onSwitchBranch)
        FloatDock(
            tab = tab,
            unread = unread,
            onPick = { tab = it },
            repo = repo,
            sessionFilter = sessionFilter,
            onFilter = { sessionFilter = it },
            veilLifted = veilLifted,
            onVeil = { veilLifted = !veilLifted },
            onReset = onReset,
        )
        FloatDayChip(branch = branch)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            FloatGhostStack(tab = tab)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(16.dp, RoundedCornerShape(20.dp))
                    .background(FloatPanel, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        FloatGrip()
                    }
                    FloatGap(6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            FloatKicker("Floating panel · ${tab.label}")
                            Text(
                                FloatPanelTitle(tab, repo),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = FloatInk,
                            )
                        }
                        FloatStamp("IN FOCUS", FloatAccent)
                    }
                    FloatGap(8)
                    when (tab) {
                        FloatTab.HOME -> FloatHome(repo)
                        FloatTab.SESSIONS -> FloatSessions(repo, sessionFilter)
                        FloatTab.CLIENTS -> FloatClients(repo, veilLifted)
                        FloatTab.FINANCE -> FloatFinance(repo)
                        FloatTab.TEAM -> FloatTeam(repo)
                        FloatTab.MAIL -> FloatMailbox(repo)
                        FloatTab.AUDIT -> FloatAudit(repo)
                        FloatTab.PROFILE ->
                            FloatProfile(
                                repo,
                                onLogout = onLogout,
                                onReset = onReset,
                                onSwitchBranch = onSwitchBranch,
                            )
                    }
                }
            }
        }
        FloatStatusLine(repo = repo)
    }
}

private fun FloatPanelTitle(tab: FloatTab, repo: FloatFakeRepo): String =
    when (tab) {
        FloatTab.HOME -> "Good morning — ${repo.currentBranch().name}"
        FloatTab.SESSIONS -> "Sessions — ${repo.currentBranch().name}"
        FloatTab.CLIENTS -> "Client directory — global"
        FloatTab.FINANCE -> "Finance — remittance bench"
        FloatTab.TEAM -> "Team — people & capabilities"
        FloatTab.MAIL -> "Mailbox"
        FloatTab.AUDIT -> "Audit log"
        FloatTab.PROFILE -> "Profile & sign-out"
    }

@Composable
private fun FloatGhostStack(tab: FloatTab) {
    val tabs = FloatTab.entries
    val i = tabs.indexOf(tab)
    val behind = tabs[(i + tabs.size - 1) % tabs.size]
    val ahead = tabs[(i + 1) % tabs.size]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 34.dp, top = 22.dp, end = 6.dp, bottom = 2.dp)
            .background(FloatPanel.copy(alpha = 0.16f), RoundedCornerShape(20.dp)),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 11.dp, end = 3.dp, bottom = 1.dp)
            .background(FloatPanel.copy(alpha = 0.30f), RoundedCornerShape(20.dp)),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 26.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("◧ ${behind.label}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FloatCanvasSoft.copy(alpha = 0.7f))
        Text("${ahead.label} ◨", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FloatCanvasSoft.copy(alpha = 0.7f))
    }
}

@Composable
private fun FloatCanvasBar(
    repo: FloatFakeRepo,
    unread: Int,
    onOpenMail: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FloatCanvasDeep).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FloatCanvasStamp("❖ Float")
            Text("CompanyApp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FloatCanvasInk)
            Text("— drift over the dimmed canvas", fontSize = 12.sp, color = FloatCanvasSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                repo.currentBranch().name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FloatCanvasInk,
                modifier = Modifier.clickable { onSwitchBranch() },
            )
            Text(
                if (unread > 0) "Mail ($unread)" else "Mail",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FloatCanvasInk,
                modifier = Modifier.clickable { onOpenMail() },
            )
            Text(
                if (repo.clockedIn) "● On shift" else "○ Off shift",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (repo.clockedIn) FloatShiftOn else FloatCanvasSoft,
            )
        }
    }
}

@Composable
private fun FloatDock(
    tab: FloatTab,
    unread: Int,
    onPick: (FloatTab) -> Unit,
    repo: FloatFakeRepo,
    sessionFilter: String,
    onFilter: (String) -> Unit,
    veilLifted: Boolean,
    onVeil: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(FloatCanvas).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FloatTab.entries.forEach { t ->
                val on = t == tab
                val badge = t == FloatTab.MAIL && unread > 0
                Box(
                    modifier = Modifier
                        .background(
                            if (on) FloatAccent else FloatCanvasLine,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onPick(t) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        t.label + if (badge) " ($unread)" else "",
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                        color = if (on) androidx.compose.ui.graphics.Color.White else FloatCanvasSoft,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            when (tab) {
                FloatTab.HOME -> {
                    FloatDockGroup("Shift") {
                        if (repo.clockedIn) {
                            FloatDockGhost("Clock out", onClick = { repo.clock(false) })
                        } else {
                            FloatDockSolid("Clock in", onClick = { repo.clock(true) })
                        }
                        FloatCanvasNote("Day: ${repo.currentBranch().day.name}")
                    }
                    FloatDockGroup("Relief") {
                        FloatDockGhost("Mark all mail read", onClick = { repo.markAllRead() })
                        FloatDockGhost("Reset demo", onClick = onReset)
                    }
                }
                FloatTab.SESSIONS -> {
                    FloatDockGroup("Views") {
                        listOf("ALL", "PEND", "DONE", "N-SHOW", "CXLD").forEach { f ->
                            if (f == sessionFilter) {
                                FloatDockSolid(f, onClick = { onFilter(f) })
                            } else {
                                FloatDockGhost(f, onClick = { onFilter(f) })
                            }
                        }
                    }
                    FloatDockGroup("New") {
                        FloatDockGhost("Walk-in +", onClick = { repo.addWalkIn("", "") })
                    }
                }
                FloatTab.CLIENTS -> {
                    FloatDockGroup("Directory") {
                        if (veilLifted) {
                            FloatDockSolid("Veil: lifted", onClick = onVeil)
                        } else {
                            FloatDockGhost("Veil: anonymized", onClick = onVeil)
                        }
                    }
                    FloatDockGroup("Policy") {
                        FloatCanvasNote("At most one PENDING per client")
                    }
                }
                FloatTab.FINANCE -> {
                    FloatDockGroup("Draft") {
                        FloatDockGhost("Session draft +", onClick = { repo.addDraft(FloatDraftKind.SESSION, "Session takings", 1200.0, 1) })
                        FloatDockGhost("Product draft +", onClick = { repo.addDraft(FloatDraftKind.PRODUCT, "Retail × 1", 450.0, 1) })
                    }
                    FloatDockGroup("Window") {
                        FloatCanvasNote("Undo within 48h")
                    }
                }
                FloatTab.TEAM -> {
                    FloatDockGroup("Staff") {
                        FloatCanvasNote("${repo.mates.size} people · 1 ONBOARDING locked")
                    }
                }
                FloatTab.MAIL -> {
                    FloatDockGroup("Mailbox") {
                        FloatDockGhost("Mark all read", onClick = { repo.markAllRead() })
                        FloatCanvasNote("${repo.notices.count { !it.read }} open")
                    }
                }
                FloatTab.AUDIT -> {
                    FloatDockGroup("Log") {
                        FloatCanvasNote("${repo.audits.size} entries")
                    }
                }
                FloatTab.PROFILE -> {
                    FloatDockGroup("Account") {
                        if (repo.clockedIn) {
                            FloatDockGhost("Clock out", onClick = { repo.clock(false) })
                        } else {
                            FloatDockSolid("Clock in", onClick = { repo.clock(true) })
                        }
                        FloatDockGhost("Reset demo", onClick = onReset)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatDockSolid(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(FloatAccent, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun FloatDockGhost(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(FloatCanvasDeep, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FloatCanvasInk)
    }
}

@Composable
private fun FloatDayChip(branch: FloatBranch) {
    val copy =
        when (branch.day) {
            FloatDay.OPEN -> "OPEN — sheet takes bookings and walk-ins"
            FloatDay.PAST -> "PAST — read-only, book nothing new"
            FloatDay.REMITTED -> "REMITTED — sealed, snapshot filed"
        }
    val color =
        when (branch.day) {
            FloatDay.OPEN -> FloatGreen
            FloatDay.PAST -> FloatGold
            FloatDay.REMITTED -> FloatAccentDeep
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(FloatCanvasLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FloatStamp(branch.day.name, color)
            Text("${branch.name} · $copy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FloatCanvasInk)
        }
        Text("Rolls 04:00 Asia/Manila", fontSize = 11.sp, color = FloatCanvasSoft)
    }
}

@Composable
private fun FloatStatusLine(repo: FloatFakeRepo) {
    val branch = repo.currentBranch()
    Row(
        modifier = Modifier.fillMaxWidth().background(FloatCanvasDeep).padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${branch.name} · ${branch.day.name} · ${if (repo.clockedIn) "On shift" else "Off shift"}",
            fontSize = 11.sp,
            color = FloatCanvasInk,
        )
        Text(
            "${repo.branchSessions().count { it.status == FloatStatus.PENDING }} pending · ${repo.notices.count { !it.read }} unread · ${repo.audits.size} audit",
            fontSize = 11.sp,
            color = FloatCanvasSoft,
        )
    }
}
