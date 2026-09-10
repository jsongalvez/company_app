package com.companyb.companyapp.proto.kanday

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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

@Composable
internal fun KbHomeTab(onOpenBoard: () -> Unit) {
    val repo = KanbanDayFakeRepo
    val scroll = rememberScrollState()
    var dutyNote by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KbDayBanner()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KbLaneCard(
                modifier = Modifier.weight(1f),
                title = if (repo.clockedIn.value) "Clocked in" else "Clock-in",
                meta = "${repo.currentUserName.value} - ${repo.branchName(repo.clockedBranchId.value)}",
            ) {
                if (repo.clockedIn.value) {
                    Text("Shift running at ${repo.branchName(repo.clockedBranchId.value)}.", color = InkBoard)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            repo.clockedIn.value = false
                            repo.log("Clock-out", repo.branchName(repo.clockedBranchId.value))
                        },
                    ) { Text("Clock out") }
                } else {
                    Text("Pin yourself to this Branch wall to start pulling notes.", color = InkBoard)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            repo.clockedIn.value = true
                            repo.log("Clock-in", repo.branchName(repo.clockedBranchId.value))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RailDark),
                    ) { Text("Clock in") }
                }
            }
            KbLaneCard(
                modifier = Modifier.weight(1f),
                title = "Board pulse",
                meta = "Live lane counts - tap to open the wall",
            ) {
                KbLaneOrder.forEach { status ->
                    val count = repo.sessions.count { it.status == status }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape).background(noteColorFor(status))
                                .border(1.dp, InkBoard, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(status.name, style = MaterialTheme.typography.labelLarge, color = InkBoard)
                        Spacer(Modifier.weight(1f))
                        Text("$count", fontWeight = FontWeight.Black, color = InkBoard)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                val over = repo.pendingCount() > KbPendingWipLimit
                if (over) {
                    Text(
                        "OVER WIP - PENDING holds ${repo.pendingCount()} notes against a limit of $KbPendingWipLimit.",
                        color = WipAlert,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenBoard,
                    colors = ButtonDefaults.buttonColors(containerColor = RailDark),
                ) { Text("Open the board") }
            }
        }
        KbLaneCard(title = "Relief Duty wall", meta = "Duty, invites and requests ride one strip") {
            repo.relief.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(LanePaper)
                        .border(1.dp, InkBoard, MaterialTheme.shapes.small).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.kind}: ${item.who}", fontWeight = FontWeight.Bold, color = InkBoard)
                        Text("${item.branch} - ${item.date} - ${item.state}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = dutyNote,
                onValueChange = { dutyNote = it },
                label = { Text("Broadcast a Relief Request (branch + day)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val target = dutyNote.ifBlank { "Harbor Provincial Tour - Sat" }
                        repo.relief.add(
                            0,
                            KbRelief("rq${repo.relief.size + 1}", "Relief Request", repo.currentUserName.value, target, "Today", "Broadcast - waiting for grant"),
                        )
                        repo.log("Relief Request broadcast", target)
                        dutyNote = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RailDark),
                ) { Text("Broadcast request") }
                OutlinedButton(
                    onClick = {
                        repo.relief.add(
                            0,
                            KbRelief("iv${repo.relief.size + 1}", "Relief Invite", "Jose Ramos", repo.branchName(repo.clockedBranchId.value), "Sun", "Invite waiting for reply"),
                        )
                        repo.log("Relief Invite sent", "Jose Ramos - ${repo.branchName(repo.clockedBranchId.value)}")
                    },
                ) { Text("Send invite") }
                OutlinedButton(
                    onClick = {
                        val first = repo.relief.firstOrNull { it.state.contains("waiting") }
                        if (first != null) {
                            repo.relief[repo.relief.indexOf(first)] = first.copy(state = "Granted - pinned to wall")
                            repo.log("Relief Duty granted", "${first.who} - ${first.branch}")
                        }
                    },
                ) { Text("Grant first waiting") }
            }
        }
    }
}

@Composable
internal fun KbBoardTab() {
    val repo = KanbanDayFakeRepo
    var branchFilter by remember { mutableStateOf("ALL") }
    var showCreator by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapeTag("SESSION LANES")
            Spacer(Modifier.width(10.dp))
            Text("Pull notes lane to lane - the wall slides them home.", color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showCreator = !showCreator },
                colors = ButtonDefaults.buttonColors(containerColor = NotePending, contentColor = InkBoard),
            ) { Text(if (showCreator) "Close creator" else "+ Pin a session") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Walk-ins never take NO_SHOW or CANCELLED - the lane arrows hide for them.",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KbFilterPill("ALL", branchFilter == "ALL") { branchFilter = "ALL" }
            repo.branches.forEach { branch ->
                KbFilterPill(branch.name, branchFilter == branch.id) { branchFilter = branch.id }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(visible = showCreator) {
            KbSessionCreator(branchDefault = if (branchFilter == "ALL") "b1" else branchFilter)
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KbLaneOrder.forEach { status ->
                KbLane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    status = status,
                    branchFilter = branchFilter,
                )
            }
        }
    }
}

@Composable
internal fun KbFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small)
            .background(if (selected) NotePending else BoardCream.copy(alpha = 0.85f))
            .border(1.dp, InkBoard, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkBoard, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun KbLane(modifier: Modifier, status: KbSessionStatus, branchFilter: String) {
    val repo = KanbanDayFakeRepo
    val notes = repo.sessions.filter {
        it.status == status && (branchFilter == "ALL" || it.branchId == branchFilter)
    }
    val isPending = status == KbSessionStatus.PENDING
    val over = isPending && repo.pendingCount() > KbPendingWipLimit
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).background(LanePaper)
            .border(2.dp, if (over) WipAlert else InkBoard, MaterialTheme.shapes.medium).padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(14.dp).clip(CircleShape).background(noteColorFor(status))
                    .border(1.dp, InkBoard, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(status.name, style = MaterialTheme.typography.titleSmall, color = InkBoard)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(CircleShape).background(if (over) WipAlert else InkBoard)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("${notes.size}", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (isPending) {
            Text(
                if (over) "OVER WIP ${notes.size}/$KbPendingWipLimit - finish notes first" else "WIP ${notes.size}/$KbPendingWipLimit",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) WipAlert else InkSoft,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                .background(TapeStrip.copy(alpha = 0.9f)).border(1.dp, InkSoft, MaterialTheme.shapes.small),
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(notes, key = { it.id }) { session ->
                KbStickyNote(
                    session = session,
                    modifier = Modifier.animateItem().fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun KbStickyNote(session: KbSession, modifier: Modifier = Modifier) {
    val repo = KanbanDayFakeRepo
    var expanded by remember(session.id) { mutableStateOf(false) }
    var reason by remember(session.id) { mutableStateOf("") }
    var reasonError by remember(session.id) { mutableStateOf(false) }
    val laneIndex = KbLaneOrder.indexOf(session.status)
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.small).background(noteColorFor(session.status))
            .border(1.dp, InkBoard, MaterialTheme.shapes.small).clickable { expanded = !expanded }
            .padding(10.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PinRed))
            Spacer(Modifier.width(6.dp))
            Text(session.time, style = MaterialTheme.typography.labelLarge, color = InkBoard)
            Spacer(Modifier.weight(1f))
            if (session.walkIn) KbChip("WALK-IN", InkBoard)
            if (session.voided) KbChip("VOID", WipAlert)
        }
        Text(session.clientName, fontWeight = FontWeight.Bold, color = InkBoard, fontSize = 15.sp)
        Text(
            "${session.type} - P${session.price} - ${repo.branchName(session.branchId)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkBoard,
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canLeft = laneIndex > 0
                    val canRight = laneIndex < KbLaneOrder.lastIndex
                    OutlinedButton(onClick = { repo.moveSession(session.id, KbLaneOrder[laneIndex - 1]) }, enabled = canLeft) {
                        Text("< Pull")
                    }
                    OutlinedButton(
                        onClick = {
                            val target = KbLaneOrder[laneIndex + 1]
                            repo.moveSession(session.id, target)
                        },
                        enabled = canRight && !(session.walkIn && (KbLaneOrder[laneIndex + 1] == KbSessionStatus.NO_SHOW || KbLaneOrder[laneIndex + 1] == KbSessionStatus.CANCELLED)),
                    ) {
                        Text("Push >")
                    }
                }
                if (session.walkIn) {
                    Text("Walk-in rule: this note can only ride PENDING or COMPLETED.", style = MaterialTheme.typography.bodySmall, color = InkBoard)
                }
                Spacer(Modifier.height(6.dp))
                if (!session.voided) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; reasonError = false },
                        label = { Text("Void reason (required)") },
                        singleLine = true,
                        isError = reasonError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (reasonError) Text("A reason pins the Void to the Audit Log.", color = WipAlert, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (reason.isBlank()) {
                                    reasonError = true
                                } else {
                                    val index = repo.sessions.indexOfFirst { it.id == session.id }
                                    repo.sessions[index] = session.copy(voided = true, voidReason = reason)
                                    repo.log("Void", "Session ${session.id} (${session.clientName})", reason)
                                }
                            },
                        ) { Text("Void note") }
                    }
                    if (session.voidReason.isNotEmpty()) {
                        Text("Void reason on file: ${session.voidReason}", style = MaterialTheme.typography.bodySmall, color = InkBoard)
                    }
                } else {
                    Text("Voided: ${session.voidReason}", style = MaterialTheme.typography.bodySmall, color = InkBoard)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val index = repo.sessions.indexOfFirst { it.id == session.id }
                            repo.sessions[index] = session.copy(voided = false, voidReason = "")
                            repo.log("Unvoid", "Session ${session.id} (${session.clientName})")
                        },
                    ) { Text("Unvoid note") }
                }
            }
        }
    }
}

@Composable
internal fun KbSessionCreator(branchDefault: String) {
    val repo = KanbanDayFakeRepo
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Follow-up") }
    var price by remember { mutableStateOf("800") }
    var time by remember { mutableStateOf("16:00") }
    var walkIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(BoardCream)
            .border(2.dp, InkBoard, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Text("Pin a fresh note to PENDING", style = MaterialTheme.typography.titleMedium, color = InkBoard)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, singleLine = true, modifier = Modifier.weight(2f))
            OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, singleLine = true, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                Text("Walk-in", color = InkBoard)
            }
        }
        if (error.isNotEmpty()) Text(error, color = WipAlert, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val amount = price.toIntOrNull()
                if (name.isBlank()) {
                    error = "Name the note first."
                } else if (amount == null || amount <= 0) {
                    error = "Price must be a positive number."
                } else {
                    val id = repo.nextSessionId()
                    repo.sessions.add(
                        KbSession(id, name.trim(), branchDefault, KbSessionStatus.PENDING, type.ifBlank { "Follow-up" }, amount, walkIn, time = time.ifBlank { "--:--" }),
                    )
                    repo.log("Session pinned", "Session $id ($name)")
                    name = ""
                    price = "800"
                    error = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RailDark),
        ) { Text("Pin to PENDING") }
    }
}

@Composable
internal fun KbClientsTab() {
    val repo = KanbanDayFakeRepo
    var hideNames by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KbLaneCard(title = "Global Clients", meta = "One wall sees every Branch - at most one PENDING Session per Client") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anonymized view", color = InkBoard, modifier = Modifier.weight(1f))
                TextButton(onClick = { hideNames = !hideNames }) { Text(if (hideNames) "Show names" else "Hide names") }
            }
            Text("Anonymized keeps gender and age, drops the name.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        repo.clients.forEach { client ->
            val shown = repo.clients.indexOf(client)
            KbLaneCard(
                title = if (hideNames || client.anonymized) "Client ${client.id.uppercase()} (anonymized)" else client.name,
                meta = client.detail + if (client.hasPending) " - 1 PENDING (limit reached)" else " - no PENDING",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (client.hasPending) KbChip("PENDING x1", WipAlert) else KbChip("CLEAR", Color(0xFF5B7F3C))
                    OutlinedButton(
                        onClick = {
                            repo.clients[shown] = client.copy(anonymized = !client.anonymized)
                        },
                    ) { Text(if (client.anonymized) "Reveal" else "Anonymize") }
                }
            }
        }
    }
}

@Composable
internal fun KbFinanceTab() {
    val repo = KanbanDayFakeRepo
    var kind by remember { mutableStateOf(KbRemitKind.SESSION) }
    var amount by remember { mutableStateOf("1200") }
    var qty by remember { mutableStateOf("2") }
    var unitPrice by remember { mutableStateOf("450") }
    var undoReason by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KbLaneCard(title = "Draft a Remittance", meta = "SESSION nets income - PRODUCT prices quantity") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KbFilterPill("SESSION", kind == KbRemitKind.SESSION) { kind = KbRemitKind.SESSION }
                KbFilterPill("PRODUCT", kind == KbRemitKind.PRODUCT) { kind = KbRemitKind.PRODUCT }
            }
            Spacer(Modifier.height(8.dp))
            if (kind == KbRemitKind.SESSION) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Net income amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text("Unit price") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                val total = (qty.toIntOrNull() ?: 0) * (unitPrice.toIntOrNull() ?: 0)
                Text("PRODUCT total: P$total (price x quantity)", color = InkBoard, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val total = if (kind == KbRemitKind.SESSION) {
                        amount.toIntOrNull() ?: 0
                    } else {
                        (qty.toIntOrNull() ?: 0) * (unitPrice.toIntOrNull() ?: 0)
                    }
                    if (total > 0) {
                        val id = repo.nextRemitId()
                        repo.remittances.add(0, KbRemittance(id, kind, KbRemitStatus.DRAFT, total, "Today at ${repo.branchName(repo.clockedBranchId.value)}"))
                        repo.log("Remittance drafted", "$id $kind P$total")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RailDark),
            ) { Text("Draft $kind") }
            Spacer(Modifier.height(4.dp))
            Text("Commission Split note: submitted SESSION income splits Practitioner / Branch per the posted table.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Text("Remittance ledger", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
        repo.remittances.forEach { remit ->
            KbLaneCard(title = "${remit.id} - ${remit.kind} P${remit.amount}", meta = "${remit.status} - ${remit.dayLabel}") {
                if (remit.snapshot.isNotEmpty()) Text("Snapshot ${remit.snapshot} (${remit.submittedAt})", color = InkBoard, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                when (remit.status) {
                    KbRemitStatus.DRAFT -> {
                        Button(
                            onClick = {
                                val index = repo.remittances.indexOfFirst { it.id == remit.id }
                                val snap = "SNAP-0914-${remit.kind}-${remit.amount}"
                                repo.remittances[index] = remit.copy(status = KbRemitStatus.SUBMITTED, snapshot = snap, submittedAt = "submitted just now")
                                repo.log("Remittance submitted", "${remit.id} $snap")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RailDark),
                        ) { Text("Submit - seal snapshot") }
                    }
                    KbRemitStatus.SUBMITTED -> {
                        if (remit.undoLocked) {
                            Text("Undo window closed - older than 48h, snapshot stands.", color = WipAlert, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedTextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Undo reason (required)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    if (undoReason.isNotBlank()) {
                                        val index = repo.remittances.indexOfFirst { it.id == remit.id }
                                        repo.remittances[index] = remit.copy(status = KbRemitStatus.DRAFT, snapshot = "", submittedAt = "")
                                        repo.log("Remittance undone (48h)", remit.id, undoReason)
                                        undoReason = ""
                                    }
                                },
                            ) { Text("Undo within 48h") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KbTeamTab() {
    val repo = KanbanDayFakeRepo
    val scroll = rememberScrollState()
    val blurbs = mapOf(
        "Practitioner" to "Pulls Session notes, pins voids with reasons.",
        "Coordinator" to "Runs the wall, grants Relief Duty, pins ONBOARDING up.",
        "MANAGER" to "Owns the Branch wall, flips Branch Day states.",
        "Accountant" to "Seals Remittance snapshots, guards the 48h undo.",
        "ONBOARDING" to "Locked lane - no Capability until granted.",
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TapeTag("WHO PINS WHAT")
        repo.users.forEach { user ->
            KbLaneCard(title = user.name, meta = "${user.role} - home ${repo.branchName(user.homeBranchId)}") {
                Text(blurbs[user.role] ?: user.role, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                if (user.onboarding) {
                    Spacer(Modifier.height(6.dp))
                    Text("ONBOARDING is locked out of every lane.", color = WipAlert, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val index = repo.users.indexOfFirst { it.id == user.id }
                            repo.users[index] = user.copy(role = "Practitioner", onboarding = false)
                            repo.log("ONBOARDING granted Practitioner", user.name)
                        },
                    ) { Text("Grant Practitioner") }
                }
            }
        }
    }
}

@Composable
internal fun KbMailTab() {
    val repo = KanbanDayFakeRepo
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapeTag("NOTIFICATION RAIL")
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    repo.mailbox.forEachIndexed { index, mail ->
                        repo.mailbox[index] = mail.copy(read = true)
                    }
                    repo.log("Mailbox cleared", "all Notifications read")
                },
            ) { Text("Mark all read", color = androidx.compose.ui.graphics.Color.White) }
        }
        repo.mailbox.forEach { mail ->
            val index = repo.mailbox.indexOf(mail)
            KbLaneCard(
                title = (if (mail.read) "" else "[NEW] ") + mail.title,
                meta = mail.day,
            ) {
                Text(mail.body, style = MaterialTheme.typography.bodyMedium, color = InkBoard)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { repo.mailbox[index] = mail.copy(read = !mail.read) }) {
                    Text(if (mail.read) "Mark unread" else "Mark read")
                }
            }
        }
    }
}

@Composable
internal fun KbAuditTab() {
    val repo = KanbanDayFakeRepo
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TapeTag("AUDIT LOG TAPE")
        Spacer(Modifier.height(8.dp))
        if (repo.audits.isEmpty()) {
            KbLaneCard(title = "Tape is blank", meta = "Pull a note and the wall writes here") { }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(repo.audits, key = { it.id }) { audit ->
                    KbLaneCard(
                        modifier = Modifier.animateItem(),
                        title = audit.action,
                        meta = "${audit.actor} - ${audit.whenText} - ${audit.record}",
                    ) {
                        if (audit.reason.isNotEmpty()) Text("Reason: ${audit.reason}", color = InkBoard, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KbProfileTab(onLogout: () -> Unit) {
    val repo = KanbanDayFakeRepo
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KbLaneCard(title = repo.currentUserName.value, meta = "Practitioner - ${repo.branchName(repo.clockedBranchId.value)}") {
            Text("Shift: ${if (repo.clockedIn.value) "clocked in" else "clocked out"} - Branch Day ${repo.dayStatus.value}.", color = InkBoard)
        }
        KbLaneCard(title = "Flip the Branch Day", meta = "OPEN / PAST / REMITTED - banner follows") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KbDayStatus.entries.forEach { status ->
                    KbFilterPill(status.name, repo.dayStatus.value == status) {
                        repo.dayStatus.value = status
                        repo.log("Branch Day flipped", status.name)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Day boundary 04:00 Asia/Manila always applies.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        KbLaneCard(title = "Leave the wall", meta = "Clock-out writes to the Audit Log") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        repo.clockedIn.value = false
                        repo.log("Clock-out", repo.branchName(repo.clockedBranchId.value))
                    },
                ) { Text("Clock out") }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = RailDark),
                ) { Text("Log out") }
                OutlinedButton(
                    onClick = { repo.reset() },
                ) { Text("Reset demo") }
            }
        }
    }
}
