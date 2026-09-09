package com.companyb.companyapp.proto.franchisemap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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

@Composable
private fun FmPanel(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FmCard),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FmDisplay)
            content()
        }
    }
}

@Composable
fun FmDayScreen(repo: FranchiseMapFakeRepo) {
    val branch = repo.currentBranch()
    var inviteNote by remember(branch.id) { mutableStateOf("") }
    var requestNote by remember(branch.id) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Branch day - ${branch.name}", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        Text("${branch.territory} territory - P${branch.gross} of P${branch.target} - ${branch.onShift} on shift",
            style = MaterialTheme.typography.bodyMedium)
        FmPanel("Clock-in (home branch: Uptown Flagship)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (repo.meClockedIn.value) "Mara is clocked in" else "Mara is clocked out",
                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                if (repo.meClockedIn.value) {
                    OutlinedButton(onClick = { repo.setClockedIn(branch.id, false) }) { Text("Clock out") }
                } else {
                    Button(onClick = { repo.setClockedIn(branch.id, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = FmOpen)) { Text("Clock in here") }
                }
            }
        }
        FmPanel("Relief duty board") {
            if (repo.reliefs.isEmpty()) Text("No open relief calls.", fontSize = 13.sp)
            repo.reliefs.forEach { r ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${r.asker} - ${repo.branchName(r.branchId)}", fontWeight = FontWeight.Bold,
                            fontSize = 13.sp)
                        Text("${r.note} [${r.state}]", fontSize = 12.sp, color = FmPast)
                    }
                    if (r.state == FmReliefState.OPEN) {
                        TextButton(onClick = { repo.setRelief(r.id, FmReliefState.GRANTED) }) { Text("Grant") }
                        TextButton(onClick = { repo.setRelief(r.id, FmReliefState.FOLDED) }) { Text("Fold") }
                    }
                }
            }
            TextField(value = requestNote, onValueChange = { requestNote = it },
                label = { Text("Broadcast a relief request for ${branch.name}") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repo.openRelief(branch.id, requestNote); requestNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = FmInk)) { Text("Broadcast request") }
            TextField(value = inviteNote, onValueChange = { inviteNote = it },
                label = { Text("Relief invite note (sent to Jonas at Sunrise)") },
                modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                if (inviteNote.isNotBlank()) {
                    repo.openRelief("sunrise", "Invite for Jonas: $inviteNote"); inviteNote = ""
                }
            }) { Text("Send relief invite") }
        }
        FmPanel("Session mix today") {
            val mix = repo.sessions.filter { it.branchId == branch.id }
            if (mix.isEmpty()) Text("No sessions pinned to this branch yet.", fontSize = 13.sp)
            mix.forEach { s ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.id} - ${s.clientName} - ${s.type}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    FmChip(s.status.name, s.status.chipColor())
                }
            }
        }
    }
}

@Composable
fun FmSessionsScreen(repo: FranchiseMapFakeRepo) {
    var branchPick by remember { mutableStateOf("ALL") }
    var statusPick by remember { mutableStateOf("ALL") }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var bookName by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sessions ledger", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        Text("Walk-ins never take NO_SHOW or CANCELLED - the buttons are withheld on walk-in rows.",
            fontSize = 12.sp, color = FmPast)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "sunrise", "uptown", "harbor", "lingap").forEach { b ->
                val active = branchPick == b
                Box(
                    modifier = Modifier.background(
                        if (active) FmInk else FmCard, RoundedCornerShape(8.dp))
                        .border(1.dp, FmRoad, RoundedCornerShape(8.dp))
                        .clickable { branchPick = b }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(if (b == "ALL") "All pins" else repo.branchName(b),
                        color = if (active) FmParchment else FmInk, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { s ->
                val active = statusPick == s
                Box(
                    modifier = Modifier.background(
                        if (active) FmBrass else FmCard, RoundedCornerShape(8.dp))
                        .border(1.dp, FmBrass, RoundedCornerShape(8.dp))
                        .clickable { statusPick = s }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(s, color = if (active) FmParchment else FmInk, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        repo.sessions
            .filter { branchPick == "ALL" || it.branchId == branchPick }
            .filter { statusPick == "ALL" || it.status.name == statusPick }
            .forEach { s ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = FmCard),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.id} - ${s.clientName} - P${s.price}", fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, modifier = Modifier.weight(1f))
                            FmChip((if (s.voided) "VOIDED " else "") + s.status.name,
                                if (s.voided) FmPast else s.status.chipColor())
                        }
                        Text("${repo.branchName(s.branchId)} - ${s.type}${if (s.walkIn) " - walk-in" else ""}",
                            fontSize = 12.sp, color = FmPast)
                        if (s.voided) {
                            Text("Void reason: ${s.voidReason}", fontSize = 12.sp)
                            TextButton(onClick = { repo.unvoidSession(s.id) }) { Text("Lift void") }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FmSessionStatus.values()
                                    .filter { it != s.status }
                                    .filter { !(s.walkIn && (it == FmSessionStatus.NO_SHOW ||
                                        it == FmSessionStatus.CANCELLED)) }
                                    .forEach { to ->
                                        TextButton(onClick = { repo.moveSession(s.id, to) }) {
                                            Text("To ${to.name}", fontSize = 12.sp)
                                        }
                                    }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { voidTarget = s.id; voidReason = "" }) {
                                    Text("Void", fontSize = 12.sp)
                                }
                            }
                            if (voidTarget == s.id) {
                                TextField(value = voidReason, onValueChange = { voidReason = it },
                                    label = { Text("Void reason (required)") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { repo.voidSession(s.id, voidReason); voidTarget = null },
                                        enabled = voidReason.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = androidx.compose.ui.graphics.Color(0xFF9B2C2C)),
                                    ) { Text("Confirm void") }
                                    TextButton(onClick = { voidTarget = null }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        FmPanel("Book a PENDING session at ${branch.name}") {
            TextField(value = bookName, onValueChange = { bookName = it },
                label = { Text("Client label, e.g. Client J-02") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { repo.bookSession(branch.id, bookName, "Swedish 60"); bookName = "" },
                enabled = bookName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FmOpen)) { Text("Book PENDING") }
        }
    }
}

private fun FmAnonName(name: String): String = "Client " + name.filter { it.isDigit() }.let {
    if (it.isEmpty()) "...." else it
}

private fun FmAnonDetail(detail: String): String = detail.substringBefore(" - ") + " - traits withheld"

@Composable
fun FmClientsScreen(repo: FranchiseMapFakeRepo) {
    var globalAnon by remember { mutableStateOf(false) }
    var perCardAnon by remember { mutableStateOf(setOf<String>()) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Client atlas (global)", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        Text("Clients are global across branches. Rule: at most one PENDING session per client - " +
            "booking warns when the count is already 1.", fontSize = 12.sp, color = FmPast)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anonymized survey view", modifier = Modifier.weight(1f), fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = { globalAnon = !globalAnon }) {
                Text(if (globalAnon) "Show names" else "Anonymize all")
            }
        }
        repo.clients.forEach { c ->
            val anon = globalAnon || perCardAnon.contains(c.id)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FmCard),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (anon) FmAnonName(c.name) else c.name, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp)
                        Text(if (anon) FmAnonDetail(c.detail) else c.detail, fontSize = 12.sp, color = FmPast)
                        Text("PENDING sessions: ${c.pendingCount}" +
                            if (c.pendingCount >= 1) " - booking blocked by rule" else " - booking open",
                            fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        perCardAnon = if (perCardAnon.contains(c.id)) perCardAnon - c.id
                        else perCardAnon + c.id
                    }) { Text(if (anon) "Reveal" else "Anonymize", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun FmFinanceScreen(repo: FranchiseMapFakeRepo) {
    val branch = repo.currentBranch()
    var draftAmount by remember(branch.id) { mutableStateOf("") }
    var draftKind by remember(branch.id) { mutableStateOf(FmRemitKind.SESSION) }
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Remittance chest - ${branch.name}", style = MaterialTheme.typography.displaySmall,
            fontFamily = FmDisplay)
        Text("Commission split note: SESSION gross splits practitioner / branch / house at payout; " +
            "PRODUCT margin routes to the branch chest. Snapshots below are computed after the split.",
            fontSize = 12.sp, color = FmPast)
        FmPanel("Draft a remittance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FmRemitKind.values().forEach { k ->
                    Box(
                        modifier = Modifier.background(
                            if (draftKind == k) FmInk else FmCard, RoundedCornerShape(8.dp))
                            .border(1.dp, FmRoad, RoundedCornerShape(8.dp))
                            .clickable { draftKind = k }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(k.name, color = if (draftKind == k) FmParchment else FmInk, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            TextField(value = draftAmount, onValueChange = { draftAmount = it.filter { c -> c.isDigit() } },
                label = { Text("Amount in pesos") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    repo.draftRemit(branch.id, draftKind, draftAmount.toIntOrNull() ?: 0)
                    draftAmount = ""
                },
                enabled = (draftAmount.toIntOrNull() ?: 0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = FmInk),
            ) { Text("Save draft") }
        }
        repo.remits.filter { it.branchId == branch.id }.forEach { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FmCard),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.kind} - P${r.amount}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        FmChip(r.status.name, if (r.status == FmRemitStatus.DRAFT) FmBrass else FmOpen)
                    }
                    if (r.snapshot.isNotBlank()) Text("Snapshot: ${r.snapshot}", fontSize = 12.sp)
                    if (r.undoReason.isNotBlank()) Text("Undone: ${r.undoReason}", fontSize = 12.sp)
                    if (r.status == FmRemitStatus.DRAFT) {
                        OutlinedButton(onClick = { repo.submitRemit(r.id) }) { Text("Submit + seal snapshot") }
                    } else {
                        TextButton(onClick = { undoTarget = r.id; undoReason = "" }) { Text("Undo within 48h") }
                        if (undoTarget == r.id) {
                            TextField(value = undoReason, onValueChange = { undoReason = it },
                                label = { Text("Undo reason (required)") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { repo.undoRemit(r.id, undoReason); undoTarget = null },
                                    enabled = undoReason.isNotBlank(),
                                ) { Text("Confirm undo") }
                                TextButton(onClick = { undoTarget = null }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FmTeamScreen(repo: FranchiseMapFakeRepo) {
    val blurbs = mapOf(
        "MANAGER" to "Opens pins, flips branch days, grants capability bundles.",
        "Practitioner" to "Takes sessions, accepts relief duty, clocks in at any pin.",
        "Coordinator" to "Runs the roster, broadcasts relief requests, reads the chest.",
        "Accountant" to "Seals snapshots, audits voids, never edits sessions.",
        "ONBOARDING" to "Locked: empty bundle until a MANAGER grants Practitioner.",
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Crew chart", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        repo.users.forEach { u ->
            Card(
                modifier = Modifier.fillMaxWidth()
                    .then(if (u.onboarding) Modifier.border(2.dp, FmBrass, RoundedCornerShape(12.dp))
                    else Modifier),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (u.onboarding) FmRemittedSoft else FmCard),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        FmChip(u.role, if (u.onboarding) FmBrass else FmInk)
                    }
                    Text("Home: ${repo.branchName(u.homeBranchId)} - " +
                        if (u.clockedIn) "clocked in" else "clocked out", fontSize = 12.sp, color = FmPast)
                    Text(blurbs[u.role] ?: "", fontSize = 12.sp)
                    if (u.onboarding) {
                        Button(onClick = { repo.grantOnboarding() },
                            colors = ButtonDefaults.buttonColors(containerColor = FmOpen)) {
                            Text("Grant Practitioner bundle")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FmMailScreen(repo: FranchiseMapFakeRepo) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mailbox", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { repo.markAllRead() }) { Text("Mark all read") }
        }
        repo.notices.forEach { n ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { repo.toggleNotice(n.id) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (n.read) FmCard.copy(alpha = 0.65f) else FmCard),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.width(8.dp).height(40.dp)
                            .background(
                                if (n.read) FmPast.copy(alpha = 0.4f) else FmBrass,
                                RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(n.title, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                            fontSize = 14.sp)
                        Text(n.body, fontSize = 12.sp, color = FmPast)
                    }
                    Text(if (n.read) "READ" else "NEW", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (n.read) FmPast else FmBrass)
                }
            }
        }
    }
}

@Composable
fun FmAuditScreen(repo: FranchiseMapFakeRepo) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Survey log", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        repo.audit.forEachIndexed { i, entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${repo.audit.size - i}.", fontSize = 12.sp, color = FmBrass,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                Text(entry, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun FmProfileScreen(repo: FranchiseMapFakeRepo, onLogout: () -> Unit) {
    val branch = repo.currentBranch()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Surveyor profile", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
        FmPanel("Mara Villanueva - MANAGER") {
            Text("Home pin: Uptown Flagship - ${if (repo.meClockedIn.value) "clocked in" else "clocked out"}",
                fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.meClockedIn.value) {
                    OutlinedButton(onClick = { repo.setClockedIn(branch.id, false) }) { Text("Clock out") }
                } else {
                    Button(onClick = { repo.setClockedIn(branch.id, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = FmOpen)) {
                        Text("Clock in at ${branch.name}")
                    }
                }
                OutlinedButton(onClick = onLogout) { Text("Log out") }
            }
        }
        FmPanel("Survey controls (demo)") {
            Text("Flip ${branch.name} branch day:", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FmDayStatus.values().forEach { d ->
                    Box(
                        modifier = Modifier.background(
                            if (branch.dayStatus == d) d.dayColor() else FmCard, RoundedCornerShape(8.dp))
                            .border(1.dp, d.dayColor(), RoundedCornerShape(8.dp))
                            .clickable { repo.flipDay(branch.id, d) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(d.name,
                            color = if (branch.dayStatus == d) FmParchment else d.dayColor(),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(onClick = { repo.reset() }) { Text("Reset demo survey data") }
        }
    }
}
