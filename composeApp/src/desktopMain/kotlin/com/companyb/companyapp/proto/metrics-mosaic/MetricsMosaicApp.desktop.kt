package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

// #779 — metrics-mosaic shell: login, ONBOARDING lock, branch select,
// mosaic home with sparkline tiles, exception tiles, dense drill-downs.

@Composable
fun ProtoMetricsMosaicApp(onBack: () -> Unit) {
    val repo = remember { MmRepo() }
    var user by remember { mutableStateOf<MmUser?>(null) }
    var branch by remember { mutableStateOf<MmBranch?>(null) }
    var screen by remember { mutableStateOf(MmScreen.MOSAIC) }
    MmRoot {
        when {
            user == null -> MmLogin(repo.users, onPick = {
                user = it
                logInfo("proto-metrics-mosaic", "login " + it.login)
            }, onExit = onBack)
            user!!.locked -> MmLockedOut(user!!, onBack = { user = null })
            branch == null -> MmBranchSelect(repo, user!!, onPick = {
                branch = it
                repo.log(user!!.login + " opened " + it.name)
            }, onBack = { user = null })
            else -> MmFrame(repo, user!!, branch!!, screen,
                onScreen = { screen = it },
                onBranchChange = {
                    branch = null
                    screen = MmScreen.MOSAIC
                },
                onLogout = {
                    repo.log(user!!.login + " logged out")
                    user = null
                    branch = null
                    screen = MmScreen.MOSAIC
                },
                onExit = onBack)
        }
    }
}

@Composable
private fun MmLogin(users: List<MmUser>, onPick: (MmUser) -> Unit, onExit: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(48.dp))
        MmSection("CompanyApp prototype · metrics mosaic")
        MmTitle("Morning numbers, at a glance")
        MmNote("Pick a profile. Fake data only — nothing leaves this window.")
        Spacer(Modifier.height(16.dp))
        MmField(filter, { filter = it }, "Filter logins")
        Spacer(Modifier.height(12.dp))
        users.filter { it.login.contains(filter.trim(), ignoreCase = true) }.forEach { u ->
            MmTile(onClick = { onPick(u) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmDot(if (u.locked) MmColors.Faint else MmColors.Teal)
                    Spacer(Modifier.width(10.dp))
                    MmText(u.name, bold = true)
                    Spacer(Modifier.width(8.dp))
                    MmText("@${u.login} · ${u.role}", color = MmColors.Soft, size = 12)
                }
                if (u.locked) MmNote("ONBOARDING — empty role bundle, locked until granted")
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        MmGhost("Exit prototype") { onExit() }
    }
}

@Composable
private fun MmLockedOut(user: MmUser, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(80.dp))
        MmSection("Onboarding")
        MmTitle("Hi ${user.name} — not yet")
        MmNote("ONBOARDING accounts carry an empty role bundle, so every flow stays locked")
        MmNote("until a MANAGER grants a role. This is the locked state, shown on purpose.")
        MmTile(alert = true) {
            MmText("Locked: no capabilities", bold = true, color = MmColors.Clay)
            MmNote("Ask a MANAGER to grant Practitioner, Coordinator, MANAGER, or Accountant.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmGhost("Back to login") { onBack() }
        }
    }
}

@Composable
private fun MmBranchSelect(repo: MmRepo, user: MmUser, onPick: (MmBranch) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        MmSection("Branch select · ${user.login} (${user.role})")
        MmTitle("Where are we measuring today?")
        MmNote("Branch Day boundary 04:00 Asia/Manila. PAST locks edits except MANAGER; REMITTED is read-only.")
        Spacer(Modifier.height(14.dp))
        repo.branches.forEach { b ->
            val today = repo.branchSessions(b.id).size
            MmTile(onClick = { onPick(b) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmText(b.name, bold = true)
                    Spacer(Modifier.width(8.dp))
                    MmDayBadge(b.dayStatus)
                    Spacer(Modifier.weight(1f))
                    MmText("$today sessions", color = MmColors.Soft, size = 12)
                }
                MmNote("${b.kind} · 7-day sessions")
                MmSparkline(b.weekSessions, MmColors.Teal)
            }
            Spacer(Modifier.height(10.dp))
        }
        MmGhost("Switch user") { onBack() }
    }
}

@Composable
private fun MmFrame(
    repo: MmRepo,
    user: MmUser,
    branch: MmBranch,
    screen: MmScreen,
    onScreen: (MmScreen) -> Unit,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MmTopBar(repo, user, branch, onBranchChange, onExit)
        MmDayBanner(branch)
        Row(Modifier.fillMaxSize()) {
            MmRail(screen, repo.unreadCount(), onScreen)
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (screen) {
                    MmScreen.MOSAIC -> MmMosaic(repo, user, branch, onScreen)
                    MmScreen.SESSIONS -> MmSessions(repo, user, branch)
                    MmScreen.CLIENTS -> MmClients(repo, user)
                    MmScreen.FINANCE -> MmFinance(repo, user, branch)
                    MmScreen.TEAM -> MmTeam(repo, user)
                    MmScreen.MAIL -> MmMail(repo)
                    MmScreen.AUDIT -> MmAudit(repo)
                    MmScreen.PROFILE -> MmProfile(repo, user, branch, onLogout)
                }
            }
        }
    }
}

@Composable
private fun MmTopBar(repo: MmRepo, user: MmUser, branch: MmBranch, onBranchChange: () -> Unit, onExit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MmColors.Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MmText("▦ mosaic", bold = true)
        Spacer(Modifier.width(10.dp))
        MmText("${user.name} · ${user.role}", color = MmColors.Soft, size = 12)
        Spacer(Modifier.weight(1f))
        MmGhost("Branch: ${branch.name}") { onBranchChange() }
        Spacer(Modifier.width(8.dp))
        MmNote("${repo.unreadCount()} unread")
        Spacer(Modifier.width(8.dp))
        MmGhost("Exit") { onExit() }
    }
}

@Composable
private fun MmDayBanner(branch: MmBranch) {
    val (wash, note) = when (branch.dayStatus) {
        MmDayStatus.OPEN -> MmColors.TealWash to "OPEN — booking and edits allowed"
        MmDayStatus.PAST -> MmColors.AmberWash to "PAST — locked except MANAGER override"
        MmDayStatus.REMITTED -> MmColors.Edge to "REMITTED — read-only, snapshot sealed"
    }
    Row(
        Modifier.fillMaxWidth().background(wash).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MmDayBadge(branch.dayStatus)
        Spacer(Modifier.width(10.dp))
        MmText("${branch.name} Branch Day · $note · boundary 04:00 Asia/Manila", size = 12)
    }
}

@Composable
private fun MmRail(current: MmScreen, unread: Int, onPick: (MmScreen) -> Unit) {
    Column(
        Modifier.background(MmColors.Panel).padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MmScreen.entries.forEach { s ->
            val label = s.name.lowercase().replaceFirstChar { it.uppercase() } +
                if (s == MmScreen.MAIL && unread > 0) " ($unread)" else ""
            val active = s == current
            MmTile(
                modifier = Modifier.background(if (active) MmColors.TealWash else MmColors.Panel),
                onClick = { onPick(s) },
            ) {
                MmText(label, bold = active, size = 13)
            }
        }
    }
}
