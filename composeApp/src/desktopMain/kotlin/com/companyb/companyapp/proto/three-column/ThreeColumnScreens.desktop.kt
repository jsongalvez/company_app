package com.companyb.companyapp.proto.threecolumn

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #809 — three-column command screens. Every flow clickable against
// ThreeColumnRepo. Rows pin their record into the always-on inspector.

private const val TAG = "ThreeColumn"

@Composable
fun TcBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = TcSignal, contentColor = Color.White),
        shape = RoundedCornerShape(6.dp),
    ) { Text(label, fontFamily = TcSans, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun TcGhostBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
    ) { Text(label, fontFamily = TcSans, fontSize = 12.sp, color = TcInk) }
}

@Composable
private fun TcHead(title: String, sub: String) {
    Column {
        TcMicro("DESK-03 // " + sub.uppercase())
        Text(text = title, fontFamily = TcSans, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TcInk)
        Spacer(Modifier.height(4.dp))
        TcRule()
    }
}

@Composable
fun TcLogin(users: List<TcUser>, onPick: (TcUser) -> Unit) {
    Row(modifier = Modifier.fillMaxSize().background(TcRail)) {
        Column(
            modifier = Modifier.width(380.dp).fillMaxSize().padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TcMicro("CMD // DESK-03", TcRailDim)
            Text("three-column", fontFamily = TcSans, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Nav, content, inspector — all three live on screen. Punch in to take the board.",
                fontFamily = TcSans, fontSize = 13.sp, color = TcRailDim,
            )
            Spacer(Modifier.height(8.dp))
            TcNote("ONBOARDING badges never leave the gate until a MANAGER grants a role.")
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().background(TcDeck).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TcMicro("SELECT OPERATOR")
            users.forEach { u ->
                TcRowShell(false, onSelect = { onPick(u) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontFamily = TcSans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TcInk)
                            TcMonoLine("${u.role} · login ${u.login}", soft = true)
                        }
                        TcLed(if (u.locked) "ONBOARDING" else u.role)
                    }
                }
            }
        }
    }
}

@Composable
fun TcLockedOut(user: TcUser, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(TcDeck).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TcHead("Gate hold", "${user.name} · ONBOARDING locked")
        TcPanelBox {
            TcMonoLine("Bundle empty: no capabilities, no post, no clock.", soft = false)
            TcMonoLine("A MANAGER grants a role from Team before this badge works the board.", soft = true)
        }
        TcGhostBtn("BACK TO GATE") { onBack() }
    }
}

@Composable
fun TcBranchSelect(repo: ThreeColumnRepo, user: TcUser, onPick: (TcBranch) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(TcDeck).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TcHead("Take a post", "${user.name} · ${user.role} · home ${user.homeBranchId}")
        repo.branches.forEach { b ->
            TcRowShell(false, onSelect = {
                repo.log(user.login, "select post " + b.name)
                logInfo(TAG, "branch select " + b.id)
                onPick(b)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontFamily = TcSans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TcInk)
                        TcMonoLine(b.kind, soft = true)
                    }
                    TcLed(b.dayStatus.name)
                }
            }
        }
        TcNote("Branch Day boundary 04:00 Asia/Manila: OPEN edits freely, PAST needs EDIT_PAST, REMITTED is sealed.")
        TcGhostBtn("SWITCH OPERATOR") { onBack() }
    }
}

@Composable
fun TcDayBanner(branch: TcBranch) {
    val rule = when (branch.dayStatus) {
        TcDayStatus.OPEN -> "edits clear across the board"
        TcDayStatus.PAST -> "edits need EDIT_PAST capability"
        TcDayStatus.REMITTED -> "sealed — read-only, vault frozen"
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(TcRail).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${branch.name} · Branch Day ${branch.dayStatus}",
                    fontFamily = TcSans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Text("04:00 Asia/Manila boundary · $rule", fontFamily = TcMono, fontSize = 11.sp, color = TcRailDim)
            }
            TcLed(branch.dayStatus.name)
        }
    }
}

@Composable
fun TcHome(repo: ThreeColumnRepo, user: TcUser, branch: TcBranch, onSelect: (TcSel) -> Unit) {
    var broadcastDate by remember { mutableStateOf("") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Ops home", "${user.name} · ${branch.name}")
        TcPanelBox {
            TcMicro("CLOCK")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TcMonoLine(
                    if (repo.clockedIn) "IN — shift running." else "OUT — punch in to start.",
                    modifier = Modifier.weight(1f),
                )
                if (repo.clockedIn) {
                    TcGhostBtn("CLOCK OUT") {
                        repo.clockedIn = false
                        repo.log(user.login, "clock-out")
                    }
                } else {
                    TcBtn("CLOCK IN") {
                        repo.clockedIn = true
                        repo.log(user.login, "clock-in @" + branch.name)
                    }
                }
            }
        }
        TcPanelBox {
            TcMicro("RELIEF DUTIES // ${repo.reliefDuties.size} ACTIVE")
            repo.reliefDuties.forEach { d ->
                TcRowShell(false, onSelect = { onSelect(TcSel.Relief(d.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TcMonoLine(d.text, modifier = Modifier.weight(1f))
                        TcLed(d.state)
                    }
                }
            }
        }
        TcPanelBox {
            TcMicro("RELIEF REQUESTS — ONE LIVE PER DATE")
            OutlinedTextField(
                value = broadcastDate,
                onValueChange = { broadcastDate = it },
                label = { Text("Date YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TcBtn("BROADCAST") {
                val date = broadcastDate.ifBlank { "2026-09-12" }
                val live = repo.reliefRequests.any { it.state == "live" }
                if (live) {
                    repo.log(user.login, "broadcast refused: one live per date")
                } else {
                    val item = TcReliefItem("q-$date", "REQUEST", "broadcast: relief @$date", "live")
                    repo.reliefRequests.add(item)
                    repo.log(user.login, "broadcast relief $date")
                }
                broadcastDate = ""
            }
            repo.reliefRequests.forEach { q ->
                TcRowShell(false, onSelect = { onSelect(TcSel.Relief(q.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TcMonoLine(q.text, modifier = Modifier.weight(1f))
                        if (q.state == "live") {
                            TcGhostBtn("WITHDRAW") {
                                val i = repo.reliefRequests.indexOf(q)
                                repo.reliefRequests[i] = q.copy(state = "withdrawn")
                                repo.log(user.login, "withdraw " + q.id)
                            }
                        } else {
                            TcLed(q.state)
                        }
                    }
                }
            }
        }
        TcPanelBox {
            TcMicro("INVITES // ${repo.reliefInvites.count { it.state == "pending" }} PENDING")
            repo.reliefInvites.forEach { inv ->
                TcRowShell(false, onSelect = { onSelect(TcSel.Relief(inv.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TcMonoLine(inv.text, modifier = Modifier.weight(1f))
                        if (inv.state == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TcBtn("ACCEPT") {
                                    val i = repo.reliefInvites.indexOf(inv)
                                    repo.reliefInvites[i] = inv.copy(state = "active")
                                    repo.log(user.login, "accept " + inv.id)
                                }
                                TcGhostBtn("DECLINE") {
                                    val i = repo.reliefInvites.indexOf(inv)
                                    repo.reliefInvites[i] = inv.copy(state = "declined")
                                    repo.log(user.login, "decline " + inv.id)
                                }
                            }
                        } else {
                            TcLed(inv.state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcSessions(repo: ThreeColumnRepo, user: TcUser, branch: TcBranch, sel: TcSel, onSelect: (TcSel) -> Unit) {
    var showVoidFor by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    val list = repo.sessions.filter { it.branchId == branch.id }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Sessions", "${list.size} dockets at ${branch.name}")
        TcNote("Walk-in dockets may only COMPLETE — NO_SHOW / CANCELLED never offered for walk-ins.")
        list.forEach { s ->
            TcRowShell(sel == TcSel.Session(s.id), onSelect = { onSelect(TcSel.Session(s.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.time} · ${s.clientName}",
                            fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk,
                        )
                        TcMonoLine(
                            "${s.id} · ${s.kind}${if (s.walkIn) " · walk-in" else ""} · ₱${s.price} · ${s.practitioners}",
                            soft = true,
                        )
                        if (s.voided) {
                            Text("voided: ${s.voidReason}", fontFamily = TcMono, fontSize = 11.sp, color = TcRed)
                        }
                    }
                    TcLed(if (s.voided) "VOIDED" else s.status.name)
                }
                if (s.voided) {
                    TcGhostBtn("UNVOID") {
                        val i = repo.sessions.indexOf(s)
                        repo.sessions[i] = s.copy(voided = false, voidReason = "")
                        repo.log(user.login, "unvoid " + s.id)
                        showVoidFor = null
                    }
                } else if (showVoidFor == s.id) {
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Void reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TcBtn("CONFIRM VOID") {
                            if (voidReason.isNotBlank()) {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(voided = true, voidReason = voidReason.trim())
                                repo.log(user.login, "void " + s.id + " reason=" + voidReason.trim())
                                voidReason = ""
                                showVoidFor = null
                            }
                        }
                        TcGhostBtn("KEEP") { showVoidFor = null }
                    }
                } else if (s.status == TcSessionStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TcBtn("COMPLETE") {
                            val i = repo.sessions.indexOf(s)
                            repo.sessions[i] = s.copy(status = TcSessionStatus.COMPLETED)
                            repo.log(user.login, "complete " + s.id)
                        }
                        if (!s.walkIn) {
                            TcGhostBtn("NO-SHOW") {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(status = TcSessionStatus.NO_SHOW)
                                repo.log(user.login, "no-show " + s.id)
                            }
                            TcGhostBtn("CANCEL") {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(status = TcSessionStatus.CANCELLED)
                                repo.log(user.login, "cancel " + s.id)
                            }
                        }
                        TcGhostBtn("VOID") { showVoidFor = s.id }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TcMonoLine(
                            "settled as ${s.status}",
                            soft = true,
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        )
                        TcGhostBtn("VOID") { showVoidFor = s.id }
                    }
                }
            }
        }
        TcPanelBox {
            TcMicro("LOG A DOCKET")
            OutlinedTextField(
                value = newClient,
                onValueChange = { newClient = it },
                label = { Text("Client name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                TcMonoLine("walk-in")
                Spacer(Modifier.width(12.dp))
                TcBtn("LOG SESSION") {
                    val id = "s-%02d".format(repo.sessionCounter)
                    repo.sessionCounter += 1
                    repo.sessions.add(
                        TcSession(
                            id, "16:00", newClient.ifBlank { "New Walk-in" }, branch.id, "Follow-up",
                            walkIn, TcSessionStatus.PENDING, 1200, user.name,
                        ),
                    )
                    repo.log(user.login, "log session $id walkIn=$walkIn")
                    newClient = ""
                    walkIn = false
                }
            }
        }
    }
}

@Composable
fun TcClients(repo: ThreeColumnRepo, user: TcUser, sel: TcSel, onSelect: (TcSel) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Clients", "global network · shared across posts")
        TcNote("At most one PENDING session per Client — the board refuses a second hold.")
        TcPanelBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TcMonoLine("Anonymized view", modifier = Modifier.weight(1f))
                Checkbox(checked = repo.anonymizedView, onCheckedChange = { repo.anonymizedView = it })
            }
            TcMonoLine("Anonymized view keeps gender + age, withholds contact.", soft = true)
        }
        repo.clients.forEach { c ->
            val masked = c.anonymized || repo.anonymizedView
            TcRowShell(sel == TcSel.Client(c.id), onSelect = { onSelect(TcSel.Client(c.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
                        TcMonoLine(
                            if (masked) {
                                if (c.gender.isNotEmpty()) "${c.gender} · age ${c.age}" else "identity withheld"
                            } else {
                                c.contact
                            },
                            soft = true,
                        )
                    }
                    TcLed("${c.pendingCount} PENDING")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TcGhostBtn("BOOK PENDING") {
                        if (c.pendingCount >= 1) {
                            repo.log(user.login, "refuse second PENDING for " + c.id)
                        } else {
                            val i = repo.clients.indexOf(c)
                            repo.clients[i] = c.copy(pendingCount = c.pendingCount + 1)
                            repo.log(user.login, "book PENDING for " + c.id)
                        }
                    }
                    TcGhostBtn("SETTLE ONE") {
                        if (c.pendingCount > 0) {
                            val i = repo.clients.indexOf(c)
                            repo.clients[i] = c.copy(pendingCount = c.pendingCount - 1)
                            repo.log(user.login, "settle PENDING for " + c.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcFinance(repo: ThreeColumnRepo, user: TcUser, sel: TcSel, onSelect: (TcSel) -> Unit) {
    var flow by remember { mutableStateOf("SESSION") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Finance vault", "SESSION + PRODUCT drafts, snapshots, undo")
        TcNote("Undo lives 48h. Snapshots older than 72h are locked — permanent.")
        TcNote("Commission split: practitioner share settles from sealed SESSION snapshots; PRODUCT flows settle separately.")
        TcPanelBox {
            TcMicro("OPEN A DRAFT")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SESSION", "PRODUCT").forEach { f ->
                    if (f == flow) TcBtn(f) { flow = f } else TcGhostBtn(f) { flow = f }
                }
                Spacer(Modifier.weight(1f))
                TcBtn("ADD ₱1,000 DRAFT") {
                    val id = "r-%02d".format(repo.draftCounter)
                    repo.draftCounter += 1
                    repo.remittances.add(TcRemittance(id, flow, "DRAFT", 1000, "Makati Command"))
                    repo.log(user.login, "draft $id $flow 1000")
                }
            }
        }
        repo.remittances.forEach { r ->
            TcRowShell(sel == TcSel.Remit(r.id), onSelect = { onSelect(TcSel.Remit(r.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.id} · ${r.flow} · ₱${r.amount}",
                            fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk,
                        )
                        TcMonoLine(
                            r.branchName + if (r.ageHours != null) " · sealed ${r.ageHours}h ago" else " · open draft",
                            soft = true,
                        )
                    }
                    TcLed(r.stage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.stage == "DRAFT") {
                        TcBtn("SUBMIT SNAPSHOT") {
                            val i = repo.remittances.indexOf(r)
                            repo.remittances[i] = r.copy(stage = "SEALED", ageHours = 0)
                            repo.log(user.login, "submit snapshot " + r.id)
                            logInfo(TAG, "snapshot sealed " + r.id)
                        }
                    } else {
                        val age = r.ageHours ?: 0
                        if (age <= 48) {
                            TcGhostBtn("UNDO") {
                                val i = repo.remittances.indexOf(r)
                                repo.remittances[i] = r.copy(stage = "DRAFT", ageHours = null)
                                repo.log(user.login, "undo snapshot " + r.id)
                            }
                        } else {
                            Text(
                                "locked — undo window passed",
                                fontFamily = TcMono, fontSize = 11.sp, color = TcRed,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcTeam(repo: ThreeColumnRepo, user: TcUser, onSelect: (TcSel) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Roster", "operators, roles, capabilities")
        TcNote("MANAGER holds the superset: MANAGE_USERS, ASSIGN_DELEGATES, SUBMIT_REMITTANCE, EDIT_PAST.")
        repo.users.forEach { u ->
            TcRowShell(false, onSelect = { onSelect(TcSel.User(u.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
                        TcMonoLine("${u.role} · home ${u.homeBranchId}", soft = true)
                        TcMonoLine(
                            if (u.capabilities.isEmpty()) "capabilities: none (gate hold)" else "capabilities: " + u.capabilities.joinToString(", "),
                            soft = true,
                        )
                    }
                    TcLed(if (u.locked) "ONBOARDING" else u.role)
                }
                if (u.locked && user.role == "MANAGER") {
                    TcGhostBtn("GRANT PRACTITIONER") {
                        repo.log(user.login, "grant Practitioner to " + u.login)
                    }
                }
            }
        }
    }
}

@Composable
fun TcMail(repo: ThreeColumnRepo, sel: TcSel, onSelect: (TcSel) -> Unit) {
    val unread = repo.notices.count { !it.read }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Mailbox", "$unread unread signals")
        TcGhostBtn("MARK ALL READ") {
            repo.notices.forEachIndexed { i, n -> if (!n.read) repo.notices[i] = n.copy(read = true) }
            repo.log("you", "mark all mail read")
        }
        repo.notices.forEach { n ->
            TcRowShell(sel == TcSel.Notice(n.id), onSelect = { onSelect(TcSel.Notice(n.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
                        TcMonoLine(n.body, soft = true)
                    }
                    TcLed(if (n.read) "read" else "fresh")
                }
                if (!n.read) {
                    TcGhostBtn("MARK READ") {
                        val i = repo.notices.indexOf(n)
                        repo.notices[i] = n.copy(read = true)
                        repo.log("you", "read " + n.id)
                    }
                }
            }
        }
    }
}

@Composable
fun TcAudit(repo: ThreeColumnRepo) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TcHead("Audit tape", "newest entries first")
        repo.audit.forEach { e ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(TcPanel)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Row {
                    TcMonoLine("#${e.seq}", modifier = Modifier.width(48.dp))
                    Column {
                        TcMonoLine(e.action)
                        TcMonoLine("by ${e.actor}", soft = true)
                    }
                }
            }
        }
    }
}

@Composable
fun TcProfile(
    repo: ThreeColumnRepo,
    user: TcUser,
    branch: TcBranch?,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TcHead("Operator card", user.name)
        TcPanelBox {
            TcMicro("ROLE BUNDLE")
            TcMonoLine("${user.role} · login ${user.login}")
            TcMonoLine(
                "Capabilities: " + if (user.capabilities.isEmpty()) "none" else user.capabilities.joinToString(", "),
                soft = true,
            )
            TcMonoLine(
                "Post: " + (branch?.name ?: "none") + " · clock: " + if (repo.clockedIn) "IN" else "OUT",
                soft = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TcBtn("LOGOUT") { onLogout() }
            TcGhostBtn("CLOCK-OUT + LOGOUT") {
                repo.clockedIn = false
                repo.log(user.login, "clock-out + logout")
                onLogout()
            }
            TextButton(onClick = onExit) { Text("Leave board", color = TcRed) }
        }
    }
}

@Composable
fun TcInspectorSession(repo: ThreeColumnRepo, user: TcUser, s: TcSession) {
    var armed by remember(s.id) { mutableStateOf(false) }
    var reason by remember(s.id) { mutableStateOf("") }
    TcPanelBox(selected = true) {
        TcMicro("DOCKET // ${s.id}")
        Text(
            "${s.time} · ${s.clientName}",
            fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk,
        )
        TcMonoLine("${s.kind}${if (s.walkIn) " · walk-in" else ""} · ₱${s.price}", soft = true)
        TcMonoLine("by ${s.practitioners}", soft = true)
        TcLed(if (s.voided) "VOIDED" else s.status.name)
        if (s.voided) {
            TcMonoLine("reason: ${s.voidReason}", soft = true)
            TcGhostBtn("UNVOID") {
                val i = repo.sessions.indexOf(s)
                repo.sessions[i] = s.copy(voided = false, voidReason = "")
                repo.log(user.login, "unvoid " + s.id)
                armed = false
            }
        } else if (armed) {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Void reason (required)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TcBtn("CONFIRM VOID") {
                if (reason.isNotBlank()) {
                    val i = repo.sessions.indexOf(s)
                    repo.sessions[i] = s.copy(voided = true, voidReason = reason.trim())
                    repo.log(user.login, "void " + s.id + " reason=" + reason.trim())
                    reason = ""
                    armed = false
                }
            }
            TcGhostBtn("KEEP") { armed = false }
        } else if (s.status == TcSessionStatus.PENDING) {
            TcBtn("COMPLETE") {
                val i = repo.sessions.indexOf(s)
                repo.sessions[i] = s.copy(status = TcSessionStatus.COMPLETED)
                repo.log(user.login, "complete " + s.id)
            }
            if (!s.walkIn) {
                TcGhostBtn("NO-SHOW") {
                    val i = repo.sessions.indexOf(s)
                    repo.sessions[i] = s.copy(status = TcSessionStatus.NO_SHOW)
                    repo.log(user.login, "no-show " + s.id)
                }
                TcGhostBtn("CANCEL") {
                    val i = repo.sessions.indexOf(s)
                    repo.sessions[i] = s.copy(status = TcSessionStatus.CANCELLED)
                    repo.log(user.login, "cancel " + s.id)
                }
            } else {
                TcMonoLine("walk-in: NO_SHOW / CANCELLED withheld", soft = true)
            }
            TcGhostBtn("VOID") { armed = true }
        } else {
            TcMonoLine("settled as ${s.status}", soft = true)
            TcGhostBtn("VOID") { armed = true }
        }
    }
}

@Composable
fun TcInspectorClient(repo: ThreeColumnRepo, user: TcUser, c: TcClient) {
    val masked = c.anonymized || repo.anonymizedView
    TcPanelBox(selected = true) {
        TcMicro("CLIENT // ${c.id}")
        Text(c.name, fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk)
        TcMonoLine(
            if (masked) {
                if (c.gender.isNotEmpty()) "${c.gender} · age ${c.age}" else "identity withheld"
            } else {
                c.contact
            },
            soft = true,
        )
        TcLed("${c.pendingCount} PENDING")
        TcGhostBtn("BOOK PENDING") {
            if (c.pendingCount >= 1) {
                repo.log(user.login, "refuse second PENDING for " + c.id)
            } else {
                val i = repo.clients.indexOf(c)
                repo.clients[i] = c.copy(pendingCount = c.pendingCount + 1)
                repo.log(user.login, "book PENDING for " + c.id)
            }
        }
        TcGhostBtn("SETTLE ONE") {
            if (c.pendingCount > 0) {
                val i = repo.clients.indexOf(c)
                repo.clients[i] = c.copy(pendingCount = c.pendingCount - 1)
                repo.log(user.login, "settle PENDING for " + c.id)
            }
        }
    }
}

@Composable
fun TcInspectorRemit(repo: ThreeColumnRepo, user: TcUser, r: TcRemittance) {
    TcPanelBox(selected = true) {
        TcMicro("VAULT // ${r.id}")
        Text(
            "${r.flow} · ₱${r.amount}",
            fontFamily = TcSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TcInk,
        )
        TcMonoLine(r.branchName + if (r.ageHours != null) " · sealed ${r.ageHours}h ago" else " · open draft", soft = true)
        TcLed(r.stage)
        if (r.stage == "DRAFT") {
            TcBtn("SUBMIT SNAPSHOT") {
                val i = repo.remittances.indexOf(r)
                repo.remittances[i] = r.copy(stage = "SEALED", ageHours = 0)
                repo.log(user.login, "submit snapshot " + r.id)
                logInfo(TAG, "snapshot sealed " + r.id)
            }
        } else {
            val age = r.ageHours ?: 0
            if (age <= 48) {
                TcGhostBtn("UNDO") {
                    val i = repo.remittances.indexOf(r)
                    repo.remittances[i] = r.copy(stage = "DRAFT", ageHours = null)
                    repo.log(user.login, "undo snapshot " + r.id)
                }
            } else {
                TcMonoLine("locked — undo window passed", soft = true)
            }
        }
    }
}

@Composable
fun TcInspectorRelief(repo: ThreeColumnRepo, user: TcUser, item: TcReliefItem) {
    TcPanelBox(selected = true) {
        TcMicro("${item.kind} // ${item.id}")
        TcMonoLine(item.text)
        TcLed(item.state)
        if (item.kind == "REQUEST" && item.state == "live") {
            TcGhostBtn("WITHDRAW") {
                val list = repo.reliefRequests
                val i = list.indexOf(item)
                if (i >= 0) {
                    list[i] = item.copy(state = "withdrawn")
                    repo.log(user.login, "withdraw " + item.id)
                }
            }
        }
        if (item.kind == "INVITE" && item.state == "pending") {
            TcBtn("ACCEPT") {
                val list = repo.reliefInvites
                val i = list.indexOf(item)
                if (i >= 0) {
                    list[i] = item.copy(state = "active")
                    repo.log(user.login, "accept " + item.id)
                }
            }
            TcGhostBtn("DECLINE") {
                val list = repo.reliefInvites
                val i = list.indexOf(item)
                if (i >= 0) {
                    list[i] = item.copy(state = "declined")
                    repo.log(user.login, "decline " + item.id)
                }
            }
        }
    }
}
