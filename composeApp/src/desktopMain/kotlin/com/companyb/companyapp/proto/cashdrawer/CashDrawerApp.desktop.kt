package com.companyb.companyapp.proto.cashdrawer

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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

// #785 — cash-drawer shell: counting-room entry, login, branch select, rail.

@Composable
fun ProtoCashDrawerApp(onBack: () -> Unit) {
    val repo = remember { CashDrawerRepo() }
    var user by remember { mutableStateOf<CdUser?>(null) }
    var branch by remember { mutableStateOf<CdBranch?>(null) }
    var entered by remember { mutableStateOf(false) }
    CdRoot {
        if (!entered) {
            CdEntry(onDone = {
                entered = true
                logInfo("CashDrawer", "counting room entered")
            }, onBack = onBack)
        } else if (user == null) {
            CdLogin(repo.users, onPick = { user = it }, onBack = { entered = false })
        } else if (user!!.locked) {
            CdLockedOut(user!!, onBack = { user = null })
        } else if (branch == null) {
            CdBranchSelect(repo, user!!, onPick = {
                branch = it
                repo.openCount(it.id)
                repo.log(user!!.login, "open drawer count @ " + it.name)
            }, onBack = { user = null })
        } else {
            CdShell(repo, user!!, branch!!,
                onBranchChange = { branch = null },
                onLogout = {
                    repo.log(user!!.login, "logout")
                    user = null
                    branch = null
                },
                onExit = onBack)
        }
    }
}

@Composable
private fun CdEntry(onDone: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        CdDashedTape("night audit · drawer 02 · manila")
        CdTitle("THE COUNTING ROOM")
        Text(
            "Count every strap. Flag every variance. Hand a clean drawer to remittance.",
            fontSize = 14.sp,
            color = CdColors.CreamDim,
        )
        CdRule()
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CdCounterStat("STRAPS", "11")
            CdCounterStat("FLOOR", "₱0")
            CdCounterStat("NET LINK", "DOWN")
        }
        CdNote("Fake-data build. No network — every figure on this counter is local.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CdBrassButton("OPEN THE DRAWER", onClick = onDone)
            CdGhostButton("LEAVE", onClick = onBack)
        }
    }
}

@Composable
private fun CdCounterStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CdFiguresText(value, size = 26, color = CdColors.Brass)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CdColors.CreamDim)
    }
}

@Composable
private fun CdLogin(users: List<CdUser>, onPick: (CdUser) -> Unit, onBack: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        CdTitle("Who is counting?")
        CdNote("Pick a cashier. ONBOARDING accounts carry an empty role bundle and stay locked.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("filter cashier", fontSize = 12.sp, color = CdColors.CreamDim) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = CdColors.Cream,
                unfocusedTextColor = CdColors.Cream,
                focusedContainerColor = CdColors.Rail,
                unfocusedContainerColor = CdColors.Rail,
                cursorColor = CdColors.Brass,
            ),
        )
        Spacer(Modifier.height(12.dp))
        users.filter { it.login.contains(filter.trim(), ignoreCase = true) }.forEach { u ->
            Row(
                Modifier.fillMaxWidth()
                    .background(CdColors.Rail, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = CdColors.Cream)
                    Text(
                        "${u.login} · ${u.role}" + if (u.locked) " · LOCKED" else "",
                        fontSize = 12.sp,
                        color = if (u.locked) CdColors.Short else CdColors.Brass,
                    )
                }
                CdBrassButton(if (u.locked) "VIEW" else "CLOCK IN", onClick = { onPick(u) })
            }
            Spacer(Modifier.height(8.dp))
        }
        CdTextLink("← back to counter", onBack)
    }
}

@Composable
private fun CdLockedOut(user: CdUser, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(60.dp))
        CdSheet {
            CdTitle("${user.name} is ONBOARDING", ink = true)
            CdNote(
                "Empty role bundle: no COUNT_DRAWER, no LOG_SESSIONS, nothing to count yet. " +
                    "A MANAGER activates this account from TEAM before first clock-in.",
                ink = true,
            )
            CdRule(ink = true)
            CdPaperButton("PICK ANOTHER CASHIER", onClick = onBack)
        }
    }
}

@Composable
private fun CdBranchSelect(repo: CashDrawerRepo, user: CdUser, onPick: (CdBranch) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
        CdTitle("Which drawer, ${user.login}?")
        CdNote("One drawer per Branch Day. Boundary 04:00 Asia/Manila: sales before 04:00 belong to yesterday.")
        Spacer(Modifier.height(12.dp))
        repo.branches.forEach { b ->
            Row(
                Modifier.fillMaxWidth()
                    .background(CdColors.Rail, RoundedCornerShape(4.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = CdColors.Cream)
                    Text(b.kind, fontSize = 12.sp, color = CdColors.CreamDim)
                }
                CdDayChip(b.dayStatus)
                Spacer(Modifier.width(10.dp))
                CdBrassButton("OPEN", onClick = { onPick(b) })
            }
            Spacer(Modifier.height(8.dp))
        }
        CdTextLink("← switch cashier", onBack)
    }
}

@Composable
fun CdDayChip(status: CdDayStatus) {
    val bg = when (status) {
        CdDayStatus.OPEN -> CdColors.OkDeep
        CdDayStatus.PAST -> CdColors.Over
        CdDayStatus.REMITTED -> CdColors.Muted
    }
    val fg = if (status == CdDayStatus.PAST) CdColors.CounterDeep else CdColors.Cream
    Box(
        Modifier.background(bg, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(status.name, fontSize = 12.sp, fontWeight = FontWeight.Black, color = fg)
    }
}

@Composable
private fun CdShell(
    repo: CashDrawerRepo,
    user: CdUser,
    branch: CdBranch,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    var screen by remember { mutableStateOf(CdScreen.COUNT) }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(240.dp).fillMaxHeight().background(CdColors.Rail)
                .padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("DRAWER 02", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CdColors.Brass)
            Text(branch.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = CdColors.Cream)
            Text("${user.login} · ${user.role}", fontSize = 12.sp, color = CdColors.CreamDim)
            Spacer(Modifier.height(6.dp))
            CdRailItem("Count", screen == CdScreen.COUNT, badge = flagBadge(repo), onClick = { screen = CdScreen.COUNT })
            CdRailItem("Home", screen == CdScreen.HOME, onClick = { screen = CdScreen.HOME })
            CdRailItem("Sessions", screen == CdScreen.SESSIONS, onClick = { screen = CdScreen.SESSIONS })
            CdRailItem("Clients", screen == CdScreen.CLIENTS, onClick = { screen = CdScreen.CLIENTS })
            CdRailItem("Finance", screen == CdScreen.FINANCE, onClick = { screen = CdScreen.FINANCE })
            CdRailItem("Team", screen == CdScreen.TEAM, onClick = { screen = CdScreen.TEAM })
            CdRailItem(
                "Mailbox", screen == CdScreen.MAIL,
                badge = repo.notices.count { !it.read }.takeIf { it > 0 }?.toString(),
                onClick = { screen = CdScreen.MAIL },
            )
            CdRailItem("Audit", screen == CdScreen.AUDIT, onClick = { screen = CdScreen.AUDIT })
            CdRailItem("Profile", screen == CdScreen.PROFILE, onClick = { screen = CdScreen.PROFILE })
            Spacer(Modifier.weight(1f))
            CdTextLink("⇄ drawers", onBranchChange)
            CdTextLink("⏻ logout", onLogout)
            CdTextLink("✕ close", onExit)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
            CdDayBanner(branch)
            Spacer(Modifier.height(12.dp))
            when (screen) {
                CdScreen.COUNT -> CdCountScreen(repo, user, branch)
                CdScreen.HOME -> CdHomeScreen(repo, user)
                CdScreen.SESSIONS -> CdSessionsScreen(repo, user, branch)
                CdScreen.CLIENTS -> CdClientsScreen(repo, user)
                CdScreen.FINANCE -> CdFinanceScreen(repo, user)
                CdScreen.TEAM -> CdTeamScreen(repo, user)
                CdScreen.MAIL -> CdMailScreen(repo, user)
                CdScreen.AUDIT -> CdAuditScreen(repo)
                CdScreen.PROFILE -> CdProfileScreen(repo, user, onLogout)
            }
        }
    }
}

private fun flagBadge(repo: CashDrawerRepo): String? {
    if (repo.countedLines == 0) return null
    val v = repo.varianceTotal
    return if (v == 0) "✓" else if (v < 0) "−" + php(-v).drop(1).take(6) else "+" + php(v).drop(1).take(6)
}

@Composable
fun CdDayBanner(branch: CdBranch) {
    val blurb = when (branch.dayStatus) {
        CdDayStatus.OPEN -> "OPEN — count, edit and seal freely. Cutoff 04:00 Asia/Manila."
        CdDayStatus.PAST -> "PAST — read-only unless a Coordinator+ applies EDIT_PAST."
        CdDayStatus.REMITTED -> "REMITTED — snapshot frozen. Undo inside 48h only."
    }
    Row(
        Modifier.fillMaxWidth().background(CdColors.Rail, RoundedCornerShape(4.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CdDayChip(branch.dayStatus)
        Spacer(Modifier.width(12.dp))
        Text(blurb, fontSize = 12.sp, color = CdColors.CreamDim, modifier = Modifier.weight(1f))
        Text("BRANCH DAY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CdColors.Brass)
    }
}
