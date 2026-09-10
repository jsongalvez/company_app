package com.companyb.companyapp.proto.forestcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #807 — forest-calm screens. Every flow clickable against ForestCalmRepo.

private const val TAG = "ForestCalm"

@Composable
private fun FcButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = FcMoss, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, fontFamily = FcSans, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun FcGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, fontFamily = FcSans, fontSize = 13.sp, color = FcMossDeep) }
}

@Composable
private fun FcHead(title: String, sub: String) {
    Column {
        Text(text = title, fontFamily = FcSerif, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FcBark)
        Text(text = sub, fontFamily = FcSans, fontSize = 13.sp, color = FcInkSoft)
        Spacer(Modifier.height(4.dp))
        FcTrailDivider()
    }
}

@Composable
fun FcLogin(users: List<FcUser>, onPick: (FcUser) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(FcPaper).padding(48.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "❧", fontSize = 34.sp)
        Text(text = "Forest floor", fontFamily = FcSerif, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = FcBark)
        Text(
            text = "Quiet nature operations. Choose who walks in today.",
            fontFamily = FcSans, fontSize = 14.sp, color = FcInkSoft,
        )
        FcTrailDivider()
        users.forEach { u ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (u.locked) FcPaperDeep else FcCardBg)
                    .clickable { onPick(u) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontFamily = FcSerif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FcInk)
                        Text(
                            "${u.role} · login ${u.login}",
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                        )
                    }
                    FcStatusChip(if (u.locked) "ONBOARDING" else u.role)
                }
            }
        }
        FcNote("ONBOARDING accounts stay at the trailhead until a MANAGER grants a role.")
    }
}

@Composable
fun FcLockedOut(user: FcUser, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(FcPaper).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FcHead("Trailhead hold", "${user.name} · ONBOARDING locked")
        FcCard {
            Text(
                "Eli's bundle is empty — no capabilities, no Branch, no clock. A MANAGER grants a role from Team before this account can walk any trail.",
                fontFamily = FcSans, fontSize = 13.sp, color = FcInk,
            )
        }
        FcGhost("BACK TO LOGIN") { onBack() }
    }
}

@Composable
fun FcBranchSelect(repo: ForestCalmRepo, user: FcUser, onPick: (FcBranch) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(FcPaper).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FcHead("Choose a grove", "${user.name} · ${user.role} · home ${user.homeBranchId}")
        repo.branches.forEach { b ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FcCardBg)
                    .clickable {
                        repo.log(user.login, "select branch " + b.name)
                        logInfo(TAG, "branch select " + b.id)
                        onPick(b)
                    }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontFamily = FcSerif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = FcInk)
                        Text(b.kind, fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft)
                    }
                    FcStatusChip(b.dayStatus.name)
                }
            }
        }
        FcNote("Branch Day boundary 04:00 Asia/Manila: OPEN edits freely, PAST needs EDIT_PAST, REMITTED is sealed.")
        FcGhost("SWITCH WALKER") { onBack() }
    }
}

@Composable
fun FcDayBanner(branch: FcBranch) {
    val rule = when (branch.dayStatus) {
        FcDayStatus.OPEN -> "edits grow freely today"
        FcDayStatus.PAST -> "edits need EDIT_PAST capability"
        FcDayStatus.REMITTED -> "sealed — read-only, remittance frozen"
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FcMossDeep)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${branch.name} · Branch Day ${branch.dayStatus}",
                    fontFamily = FcSans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Text(
                    "04:00 Asia/Manila boundary · $rule",
                    fontFamily = FcSans, fontSize = 12.sp, color = FcFernPale,
                )
            }
            FcChip(branch.dayStatus.name, FcFern, FcMossDeep)
        }
    }
}

@Composable
fun FcHome(repo: ForestCalmRepo, user: FcUser, branch: FcBranch) {
    var broadcastDate by remember { mutableStateOf("") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Morning clearing", "${user.name} · ${branch.name}")
        FcCard {
            FcSectionTitle("Sun clock")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (repo.clockedIn) "Clocked in — roots down." else "Not clocked in yet.",
                    fontFamily = FcSans, fontSize = 13.sp, color = FcInk, modifier = Modifier.weight(1f),
                )
                if (repo.clockedIn) {
                    FcGhost("CLOCK OUT") {
                        repo.clockedIn = false
                        repo.log(user.login, "clock-out")
                    }
                } else {
                    FcButton("CLOCK IN") {
                        repo.clockedIn = true
                        repo.log(user.login, "clock-in @" + branch.name)
                    }
                }
            }
        }
        FcCard {
            FcSectionTitle("Relief duties", "${repo.reliefDuties.size} active")
            repo.reliefDuties.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.text, fontFamily = FcSans, fontSize = 13.sp, color = FcInk, modifier = Modifier.weight(1f))
                    FcStatusChip(d.state)
                }
            }
            FcHairline()
            FcSectionTitle("Broadcast a request")
            FcNote("One live request per date — the floor stays quiet.")
            OutlinedTextField(
                value = broadcastDate,
                onValueChange = { broadcastDate = it },
                label = { Text("Date YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FcButton("BROADCAST") {
                    val date = broadcastDate.ifBlank { "2026-09-12" }
                    val live = repo.reliefRequests.any { it.state == "live" }
                    if (live) {
                        repo.log(user.login, "broadcast refused: one live per date")
                    } else {
                        val item = FcReliefItem("q-${date}", "REQUEST", "broadcast: relief @$date", "live")
                        repo.reliefRequests.add(item)
                        repo.log(user.login, "broadcast relief $date")
                    }
                    broadcastDate = ""
                }
            }
            repo.reliefRequests.forEach { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.text, fontFamily = FcSans, fontSize = 13.sp, color = FcInk, modifier = Modifier.weight(1f))
                    if (q.state == "live") {
                        FcGhost("WITHDRAW") {
                            val i = repo.reliefRequests.indexOf(q)
                            repo.reliefRequests[i] = q.copy(state = "withdrawn")
                            repo.log(user.login, "withdraw " + q.id)
                        }
                    } else {
                        FcStatusChip(q.state)
                    }
                }
            }
            FcHairline()
            FcSectionTitle("Invites", "${repo.reliefInvites.count { it.state == "pending" }} pending")
            repo.reliefInvites.forEach { inv ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inv.text, fontFamily = FcSans, fontSize = 13.sp, color = FcInk, modifier = Modifier.weight(1f))
                    if (inv.state == "pending") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FcButton("ACCEPT") {
                                val i = repo.reliefInvites.indexOf(inv)
                                repo.reliefInvites[i] = inv.copy(state = "active")
                                repo.log(user.login, "accept " + inv.id)
                            }
                            FcGhost("DECLINE") {
                                val i = repo.reliefInvites.indexOf(inv)
                                repo.reliefInvites[i] = inv.copy(state = "declined")
                                repo.log(user.login, "decline " + inv.id)
                            }
                        }
                    } else {
                        FcStatusChip(inv.state)
                    }
                }
            }
        }
    }
}

@Composable
fun FcSessions(repo: ForestCalmRepo, user: FcUser, branch: FcBranch) {
    var showVoidFor by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    val list = repo.sessions.filter { it.branchId == branch.id }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Session grove", "${list.size} sessions rooted at ${branch.name}")
        FcNote("Walk-in sessions may only COMPLETE — NO_SHOW / CANCELLED are unavailable for walk-ins.")
        list.forEach { s ->
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.time} · ${s.clientName}",
                            fontFamily = FcSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FcInk,
                        )
                        Text(
                            "${s.id} · ${s.kind}${if (s.walkIn) " · walk-in" else ""} · ₱${s.price} · ${s.practitioners}",
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                        )
                        if (s.voided) {
                            Text(
                                "voided: ${s.voidReason}",
                                fontFamily = FcSans, fontSize = 12.sp, color = FcBerry,
                            )
                        }
                    }
                    FcStatusChip(if (s.voided) "VOIDED" else s.status.name)
                }
                if (s.voided) {
                    FcGhost("UNVOID") {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FcButton("CONFIRM VOID") {
                            if (voidReason.isNotBlank()) {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(voided = true, voidReason = voidReason.trim())
                                repo.log(user.login, "void " + s.id + " reason=" + voidReason.trim())
                                voidReason = ""
                                showVoidFor = null
                            }
                        }
                        FcGhost("KEEP") { showVoidFor = null }
                    }
                } else if (s.status == FcSessionStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FcButton("COMPLETE") {
                            val i = repo.sessions.indexOf(s)
                            repo.sessions[i] = s.copy(status = FcSessionStatus.COMPLETED)
                            repo.log(user.login, "complete " + s.id)
                        }
                        if (!s.walkIn) {
                            FcGhost("NO-SHOW") {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(status = FcSessionStatus.NO_SHOW)
                                repo.log(user.login, "no-show " + s.id)
                            }
                            FcGhost("CANCEL") {
                                val i = repo.sessions.indexOf(s)
                                repo.sessions[i] = s.copy(status = FcSessionStatus.CANCELLED)
                                repo.log(user.login, "cancel " + s.id)
                            }
                        }
                        FcGhost("VOID") { showVoidFor = s.id }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "settled as ${s.status}",
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        )
                        FcGhost("VOID") { showVoidFor = s.id }
                    }
                }
            }
        }
        FcCard {
            FcSectionTitle("Plant a session")
            OutlinedTextField(
                value = newClient,
                onValueChange = { newClient = it },
                label = { Text("Client name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                Text("Walk-in seedling", fontFamily = FcSans, fontSize = 13.sp, color = FcInk)
                Spacer(Modifier.width(12.dp))
                FcButton("LOG SESSION") {
                    val id = "s-%02d".format(repo.sessionCounter)
                    repo.sessionCounter += 1
                    repo.sessions.add(
                        FcSession(
                            id, "16:00", newClient.ifBlank { "New Seedling" }, branch.id, "Follow-up",
                            walkIn, FcSessionStatus.PENDING, 1200, user.name,
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
fun FcClients(repo: ForestCalmRepo, user: FcUser) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Client mycelium", "global network · shared across groves")
        FcNote("At most one PENDING session per Client — the floor refuses a second sprout.")
        FcCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Anonymized view",
                    fontFamily = FcSans, fontSize = 13.sp, color = FcInk, modifier = Modifier.weight(1f),
                )
                Checkbox(checked = repo.anonymizedView, onCheckedChange = { repo.anonymizedView = it })
            }
            Text(
                "Anonymized view keeps gender + age, withholds contact.",
                fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
            )
        }
        repo.clients.forEach { c ->
            val masked = c.anonymized || repo.anonymizedView
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontFamily = FcSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FcInk)
                        Text(
                            if (masked) (if (c.gender.isNotEmpty()) "${c.gender} · age ${c.age}" else "identity withheld") else c.contact,
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                        )
                    }
                    FcStatusChip("${c.pendingCount} PENDING")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FcGhost("BOOK PENDING") {
                        if (c.pendingCount >= 1) {
                            repo.log(user.login, "refuse second PENDING for " + c.id)
                        } else {
                            val i = repo.clients.indexOf(c)
                            repo.clients[i] = c.copy(pendingCount = c.pendingCount + 1)
                            repo.log(user.login, "book PENDING for " + c.id)
                        }
                    }
                    FcGhost("SETTLE ONE") {
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
fun FcFinance(repo: ForestCalmRepo, user: FcUser) {
    var flow by remember { mutableStateOf("SESSION") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Remittance hollow", "SESSION + PRODUCT drafts, snapshots, undo")
        FcNote("Undo lives 48h. Snapshots older than 72h are petrified — permanent.")
        FcNote("Commission split: practitioner share roots from sealed SESSION snapshots; PRODUCT flows settle separately.")
        FcCard {
            FcSectionTitle("Gather a draft")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SESSION", "PRODUCT").forEach { f ->
                    if (f == flow) FcButton(f) { flow = f } else FcGhost(f) { flow = f }
                }
                Spacer(Modifier.weight(1f))
                FcButton("ADD ₱1,000 DRAFT") {
                    val id = "r-%02d".format(repo.draftCounter)
                    repo.draftCounter += 1
                    repo.remittances.add(FcRemittance(id, flow, "DRAFT", 1000, "Makati Grove"))
                    repo.log(user.login, "draft $id $flow 1000")
                }
            }
        }
        repo.remittances.forEach { r ->
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.id} · ${r.flow} · ₱${r.amount}",
                            fontFamily = FcSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FcInk,
                        )
                        Text(
                            r.branchName + if (r.ageHours != null) " · sealed ${r.ageHours}h ago" else " · open draft",
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                        )
                    }
                    FcStatusChip(r.stage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.stage == "DRAFT") {
                        FcButton("SUBMIT SNAPSHOT") {
                            val i = repo.remittances.indexOf(r)
                            repo.remittances[i] = r.copy(stage = "SEALED", ageHours = 0)
                            repo.log(user.login, "submit snapshot " + r.id)
                            logInfo(TAG, "snapshot sealed " + r.id)
                        }
                    } else {
                        val age = r.ageHours ?: 0
                        if (age <= 48) {
                            FcGhost("UNDO") {
                                val i = repo.remittances.indexOf(r)
                                repo.remittances[i] = r.copy(stage = "DRAFT", ageHours = null)
                                repo.log(user.login, "undo snapshot " + r.id)
                            }
                        } else {
                            Text(
                                "petrified — undo window passed",
                                fontFamily = FcSans, fontSize = 12.sp, color = FcBerry,
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
fun FcTeam(repo: ForestCalmRepo, user: FcUser) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Grove keepers", "users, roles, capabilities")
        FcNote("MANAGER holds the superset: MANAGE_USERS, ASSIGN_DELEGATES, SUBMIT_REMITTANCE, EDIT_PAST.")
        repo.users.forEach { u ->
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontFamily = FcSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FcInk)
                        Text(
                            "${u.role} · home ${u.homeBranchId}",
                            fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
                        )
                        Text(
                            if (u.capabilities.isEmpty()) "capabilities: none (trailhead hold)" else "capabilities: " + u.capabilities.joinToString(", "),
                            fontFamily = FcSans, fontSize = 12.sp, color = FcMossDeep,
                        )
                    }
                    FcStatusChip(if (u.locked) "ONBOARDING" else u.role)
                }
                if (u.locked && user.role == "MANAGER") {
                    FcGhost("GRANT PRACTITIONER") {
                        repo.log(user.login, "grant Practitioner to " + u.login)
                    }
                }
            }
        }
    }
}

@Composable
fun FcMail(repo: ForestCalmRepo) {
    val unread = repo.notices.count { !it.read }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Message nest", "$unread unread spores")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FcGhost("MARK ALL READ") {
                repo.notices.forEachIndexed { i, n -> if (!n.read) repo.notices[i] = n.copy(read = true) }
                repo.log("you", "mark all mail read")
            }
        }
        repo.notices.forEach { n ->
            FcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontFamily = FcSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FcInk)
                        Text(n.body, fontFamily = FcSans, fontSize = 13.sp, color = FcInk)
                    }
                    FcStatusChip(if (n.read) "read" else "fresh")
                }
                if (!n.read) {
                    FcGhost("MARK READ") {
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
fun FcAudit(repo: ForestCalmRepo) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Ring log", "audit trail · newest rings first")
        repo.audit.forEach { e ->
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FcCardBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row {
                    Text(
                        "#${e.seq}",
                        fontFamily = FcSans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FcFern,
                        modifier = Modifier.width(44.dp),
                    )
                    Column {
                        Text(e.action, fontFamily = FcSans, fontSize = 13.sp, color = FcInk)
                        Text("by ${e.actor}", fontFamily = FcSans, fontSize = 11.sp, color = FcInkSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun FcProfile(
    repo: ForestCalmRepo,
    user: FcUser,
    branch: FcBranch?,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcHead("Keeper profile", user.name)
        FcCard {
            FcSectionTitle("Role bundle")
            Text("${user.role} · login ${user.login}", fontFamily = FcSans, fontSize = 14.sp, color = FcInk)
            Text(
                "Capabilities: " + if (user.capabilities.isEmpty()) "none" else user.capabilities.joinToString(", "),
                fontFamily = FcSans, fontSize = 13.sp, color = FcMossDeep,
            )
            Text(
                "Grove: " + (branch?.name ?: "none") + " · clock: " + if (repo.clockedIn) "in" else "out",
                fontFamily = FcSans, fontSize = 13.sp, color = FcInkSoft,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FcButton("LOGOUT") { onLogout() }
            FcGhost("CLOCK-OUT + LOGOUT") {
                repo.clockedIn = false
                repo.log(user.login, "clock-out + logout")
                onLogout()
            }
            TextButton(onClick = onExit) { Text("Leave forest", color = FcBerry) }
        }
    }
}
