package com.companyb.companyapp.proto.threecolumn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #809 — three-column command shell: login, branch select, then a persistent
// nav | content | inspector board. The inspector never leaves the screen.

@Composable
fun ThreeColumnApp(onBack: () -> Unit) {
    val repo = remember { ThreeColumnRepo() }
    var user by remember { mutableStateOf<TcUser?>(null) }
    var branch by remember { mutableStateOf<TcBranch?>(null) }
    var screen by remember { mutableStateOf(TcScreen.HOME) }
    var sel by remember { mutableStateOf<TcSel>(TcSel.None) }
    TcRoot {
        val u = user
        val b = branch
        when {
            u == null -> TcLogin(repo.users, onPick = {
                user = it
                sel = TcSel.None
                repo.log(it.login, "login command board")
                logInfo("ThreeColumn", "login " + it.login)
            })
            u.locked -> TcLockedOut(u, onBack = { user = null })
            b == null -> TcBranchSelect(repo, u, onPick = {
                branch = it
                sel = TcSel.None
            }, onBack = { user = null })
            else -> TcShell(
                repo, u, b, screen, sel,
                onScreen = {
                    screen = it
                    sel = TcSel.None
                },
                onSelect = { sel = it },
                onBranchChange = {
                    branch = null
                    sel = TcSel.None
                },
                onLogout = {
                    repo.log(u.login, "logout")
                    user = null
                    branch = null
                    screen = TcScreen.HOME
                    sel = TcSel.None
                },
                onExit = onBack,
            )
        }
    }
}

@Composable
private fun TcShell(
    repo: ThreeColumnRepo,
    user: TcUser,
    branch: TcBranch,
    screen: TcScreen,
    sel: TcSel,
    onScreen: (TcScreen) -> Unit,
    onSelect: (TcSel) -> Unit,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val unread = repo.notices.count { !it.read }
    Row(modifier = Modifier.fillMaxSize().background(TcDeck)) {
        Column(
            modifier = Modifier.width(216.dp).fillMaxHeight()
                .background(TcRail)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TcMicro("CMD // DESK-03", TcRailDim)
            Text(
                text = "three-column",
                fontFamily = TcSans,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "${user.name} · ${user.role}",
                fontFamily = TcMono,
                fontSize = 11.sp,
                color = TcRailDim,
            )
            Spacer(Modifier.height(6.dp))
            TcNavItem("Ops home", "01", screen == TcScreen.HOME) { onScreen(TcScreen.HOME) }
            TcNavItem("Sessions", "02", screen == TcScreen.SESSIONS) { onScreen(TcScreen.SESSIONS) }
            TcNavItem("Clients", "03", screen == TcScreen.CLIENTS) { onScreen(TcScreen.CLIENTS) }
            TcNavItem("Finance", "04", screen == TcScreen.FINANCE) { onScreen(TcScreen.FINANCE) }
            TcNavItem("Team", "05", screen == TcScreen.TEAM) { onScreen(TcScreen.TEAM) }
            TcNavItem("Mailbox", "06", screen == TcScreen.MAIL, unread) { onScreen(TcScreen.MAIL) }
            TcNavItem("Audit", "07", screen == TcScreen.AUDIT) { onScreen(TcScreen.AUDIT) }
            TcNavItem("Profile", "08", screen == TcScreen.PROFILE) { onScreen(TcScreen.PROFILE) }
            Spacer(Modifier.weight(1f))
            TcRule()
            Spacer(Modifier.height(4.dp))
            Text(
                branch.name,
                fontFamily = TcMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "DAY ${branch.dayStatus}",
                fontFamily = TcMono,
                fontSize = 11.sp,
                color = TcRailDim,
            )
            Text(
                "SWITCH POST",
                fontFamily = TcSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TcSignal,
                modifier = Modifier.clickableNoRipple(onBranchChange).padding(vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TcDayBanner(branch)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    TcScreen.HOME -> TcHome(repo, user, branch, onSelect)
                    TcScreen.SESSIONS -> TcSessions(repo, user, branch, sel, onSelect)
                    TcScreen.CLIENTS -> TcClients(repo, user, sel, onSelect)
                    TcScreen.FINANCE -> TcFinance(repo, user, sel, onSelect)
                    TcScreen.TEAM -> TcTeam(repo, user, onSelect)
                    TcScreen.MAIL -> TcMail(repo, sel, onSelect)
                    TcScreen.AUDIT -> TcAudit(repo)
                    TcScreen.PROFILE -> TcProfile(repo, user, branch, onLogout = onLogout, onExit = onExit)
                }
            }
        }
        Column(
            modifier = Modifier.width(296.dp).fillMaxHeight()
                .background(TcPanel)
                .border(1.dp, TcLine)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TcMicro("INSPECTOR — ALWAYS ON")
            TcRule()
            TcInspector(repo, user, branch, screen, sel, onSelect)
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick,
        ),
    )

@Composable
private fun TcInspector(
    repo: ThreeColumnRepo,
    user: TcUser,
    branch: TcBranch,
    screen: TcScreen,
    sel: TcSel,
    onSelect: (TcSel) -> Unit,
) {
    when (sel) {
        is TcSel.Session -> {
            val s = repo.sessions.firstOrNull { it.id == sel.id }
            if (s == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcInspectorSession(repo, user, s)
            }
        }
        is TcSel.Client -> {
            val c = repo.clients.firstOrNull { it.id == sel.id }
            if (c == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcInspectorClient(repo, user, c)
            }
        }
        is TcSel.Remit -> {
            val r = repo.remittances.firstOrNull { it.id == sel.id }
            if (r == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcInspectorRemit(repo, user, r)
            }
        }
        is TcSel.Notice -> {
            val n = repo.notices.firstOrNull { it.id == sel.id }
            if (n == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcPanelBox {
                    TcMicro("SIGNAL // ${n.id}")
                    Text(n.title, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
                    TcMonoLine(n.body, soft = true)
                    TcLed(if (n.read) "read" else "PENDING")
                    if (!n.read) {
                        TcBtn("MARK READ") {
                            val i = repo.notices.indexOf(n)
                            repo.notices[i] = n.copy(read = true)
                            repo.log(user.login, "read " + n.id)
                        }
                    }
                }
            }
        }
        is TcSel.Relief -> {
            val item = repo.reliefDuties.firstOrNull { it.id == sel.id }
                ?: repo.reliefRequests.firstOrNull { it.id == sel.id }
                ?: repo.reliefInvites.firstOrNull { it.id == sel.id }
            if (item == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcInspectorRelief(repo, user, item)
            }
        }
        is TcSel.User -> {
            val t = repo.users.firstOrNull { it.id == sel.id }
            if (t == null) {
                TcNote("Record ${sel.id} left the board. Pick another row.")
            } else {
                TcPanelBox {
                    TcMicro("OPERATOR // ${t.id}")
                    Text(t.name, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
                    TcMonoLine("${t.role} · home ${t.homeBranchId}", soft = true)
                    TcMonoLine(
                        if (t.capabilities.isEmpty()) "capabilities: none" else "caps: " + t.capabilities.joinToString(","),
                        soft = true,
                    )
                    TcLed(if (t.locked) "ONBOARDING" else t.role)
                    if (t.locked && user.role == "MANAGER") {
                        TcBtn("GRANT PRACTITIONER") { repo.log(user.login, "grant Practitioner to " + t.login) }
                    }
                }
            }
        }
        TcSel.None -> TcInspectorContext(repo, user, branch, screen, onSelect)
    }
}

@Composable
private fun TcInspectorContext(
    repo: ThreeColumnRepo,
    user: TcUser,
    branch: TcBranch,
    screen: TcScreen,
    onSelect: (TcSel) -> Unit,
) {
    TcPanelBox {
        TcMicro("POST CONTEXT")
        Text(branch.name, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
        TcMonoLine("day ${branch.dayStatus} · 04:00 Asia/Manila", soft = true)
        TcMonoLine("clock: " + if (repo.clockedIn) "IN" else "OUT" + " · op ${user.login}", soft = true)
    }
    when (screen) {
        TcScreen.HOME -> {
            TcPanelBox {
                TcMicro("SHIFT TOTALS")
                TcMonoLine("duties ${repo.reliefDuties.size} · live req ${repo.reliefRequests.count { it.state == "live" }}")
                TcMonoLine("invites pending ${repo.reliefInvites.count { it.state == "pending" }}")
            }
            TcNote("Nothing pinned. Click any duty, request, or invite to inspect it here.")
        }
        TcScreen.SESSIONS -> {
            val list = repo.sessions.filter { it.branchId == branch.id }
            TcPanelBox {
                TcMicro("QUEUE TALLY")
                TcMonoLine("PENDING ${list.count { it.status == TcSessionStatus.PENDING && !it.voided }}")
                TcMonoLine("DONE ${list.count { it.status == TcSessionStatus.COMPLETED }}")
                TcMonoLine("NO_SHOW ${list.count { it.status == TcSessionStatus.NO_SHOW }}")
                TcMonoLine("CXLD ${list.count { it.status == TcSessionStatus.CANCELLED }}")
            }
            TcNote("Click a session row to pin its docket here with stepper + void.")
        }
        TcScreen.CLIENTS -> {
            TcPanelBox {
                TcMicro("NETWORK TALLY")
                TcMonoLine("clients ${repo.clients.size} · global")
                TcMonoLine("pending holds ${repo.clients.sumOf { it.pendingCount }}")
            }
            TcNote("At most one PENDING session per Client. Click a row to pin it.")
        }
        TcScreen.FINANCE -> {
            val drafts = repo.remittances.filter { it.stage == "DRAFT" }
            TcPanelBox {
                TcMicro("VAULT TALLY")
                TcMonoLine("drafts ${drafts.size} · ₱${drafts.sumOf { it.amount }} open")
                TcMonoLine("sealed ${repo.remittances.count { it.stage == "SEALED" }} snapshots")
            }
            TcNote("Undo lives 48h. Click a remittance row to pin it.")
        }
        TcScreen.TEAM -> TcNote("Click an operator row to pin role bundle + grant control.")
        TcScreen.MAIL -> TcNote("Click a signal to pin it with mark-read control.")
        TcScreen.AUDIT -> {
            val top = repo.audit.firstOrNull()
            TcPanelBox {
                TcMicro("LATEST ENTRY")
                TcMonoLine(top?.let { "#${it.seq} ${it.action}" } ?: "no entries", soft = true)
            }
            TcNote("Entries append newest-first as you work the board.")
        }
        TcScreen.PROFILE -> TcNote("Role bundle, logout, and clock-out live in the center column.")
    }
    if (screen != TcScreen.HOME && screen != TcScreen.AUDIT && screen != TcScreen.PROFILE) {
        TcGhostBtn("CLEAR PIN") { onSelect(TcSel.None) }
    }
}
