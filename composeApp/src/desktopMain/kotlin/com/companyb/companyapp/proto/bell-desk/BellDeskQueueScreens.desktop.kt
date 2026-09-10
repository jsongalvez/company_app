package com.companyb.companyapp.proto.belldesk

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

@Composable
internal fun BdArrivalsTab() {
    val repo = BellDeskFakeRepo
    val branchId = repo.clockedBranchId.value
    val me = repo.currentUser.value
    var lanePick by remember { mutableStateOf(repo.lanes[0]) }
    val waiting = repo.sessions.filter { it.branchId == branchId && it.status == BdSessionStatus.PENDING && it.lane.isEmpty() }
    val seated = repo.sessions.filter { it.branchId == branchId && it.status == BdSessionStatus.PENDING && it.lane.isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("ARRIVALS · WHO JUST WALKED IN", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("Oldest bell first. Seat each one in three taps.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Spacer(Modifier.height(8.dp))
        Text(
            "House rule note: walk-ins never take NO_SHOW or CANCELLED — they are seated, completed, or voided with a reason.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftLobby,
        )
        Spacer(Modifier.height(10.dp))
        Text("Seat lane for one-tap claim: $lanePick", style = MaterialTheme.typography.bodyMedium, color = InkLobby)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.lanes.forEach { l ->
                Box(
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .background(if (l == lanePick) BrassDeep else BrassWash)
                        .border(1.dp, BrassDeep, MaterialTheme.shapes.small)
                        .clickable { lanePick = l }.padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(l, style = MaterialTheme.typography.labelMedium, color = if (l == lanePick) Color.White else InkLobby)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (waiting.isEmpty()) {
            BdCard(title = "COUNTER CLEAR") {
                Text("No unseated arrivals. Ring the bell from the Bell tab.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        waiting.forEach { s ->
            BdSlipRow(session = s, onSeat = {
                repo.updateSession(s.id) { it.copy(lane = lanePick) }
                repo.stamp(me.name, "ASSIGN", "${s.bellNo} -> $lanePick")
            })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("SEATED · AWAITING SERVICE (${seated.size})", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        seated.forEach { s ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.small).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${s.bellNo} · ${s.clientName}", style = MaterialTheme.typography.titleMedium, color = InkLobby)
                    Text("${s.service} · ${s.lane} · ${s.time}", style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
                }
                BdSmallButton("Complete") {
                    repo.updateSession(s.id) { it.copy(status = BdSessionStatus.COMPLETED) }
                    repo.stamp(me.name, "COMPLETE", s.bellNo)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
internal fun BdSlipRow(session: BdSession, onSeat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(SlipBellPending)
            .border(1.dp, BrassDeep, MaterialTheme.shapes.medium).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${session.bellNo} · ${session.clientName}", style = MaterialTheme.typography.titleMedium, color = InkLobby)
            Text(
                "${session.service} · ₱${session.price} · ${if (session.walkIn) "walk-in" else "booked"} · ${session.time}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoftLobby,
            )
        }
        Button(onClick = onSeat, colors = ButtonDefaults.buttonColors(containerColor = BrassDeep)) {
            Text("Seat")
        }
    }
}

@Composable
internal fun BdLedgerTab() {
    val repo = BellDeskFakeRepo
    val me = repo.currentUser.value
    var filter by remember { mutableStateOf("ALL") }
    var detailId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newService by remember { mutableStateOf("Follow-up") }
    var voidTarget by remember { mutableStateOf<BdSession?>(null) }
    var voidReason by remember { mutableStateOf("") }
    val branchId = repo.clockedBranchId.value
    val all = repo.sessions.filter { it.branchId == branchId }
    val shown = if (filter == "ALL") all else all.filter { it.status.name == filter }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("SESSION LEDGER · EVERY BELL, EVERY OUTCOME", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("PENDING → COMPLETED / NO_SHOW / CANCELLED.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Text(
            "Walk-in rule: a walk-in bell is never marked NO_SHOW or CANCELLED; void it with a reason instead.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftLobby,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
                val sel = filter == f
                Box(
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .background(if (sel) BrassDeep else BrassWash)
                        .clickable { filter = f }.padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(f, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else InkLobby)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BdCard(title = "NEW BELL TICKET") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Guest name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newService,
                    onValueChange = { newService = it },
                    label = { Text("Service") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val no = repo.nextBell()
                        repo.sessions.add(
                            0,
                            BdSession(
                                id = "s-$no",
                                bellNo = no,
                                clientName = newName.ifBlank { "Walk-in $no" },
                                branchId = branchId,
                                status = BdSessionStatus.PENDING,
                                service = newService.ifBlank { "Follow-up" },
                                price = 900,
                                walkIn = true,
                                time = "now",
                            ),
                        )
                        repo.stamp(me.name, "CREATE_SESSION", no)
                        newName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                ) { Text("Stamp") }
            }
        }
        Spacer(Modifier.height(10.dp))
        shown.forEach { s ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(if (s.voided) VoidLobby else MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.bellNo} · ${s.clientName}${if (s.voided) " · VOID" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkLobby,
                        )
                        Text(
                            "${s.status} · ${s.service} · ₱${s.price} · ${if (s.walkIn) "walk-in" else "booked"}" +
                                (if (s.lane.isNotEmpty()) " · ${s.lane}" else " · unseated") +
                                (if (s.voided) " · reason: ${s.voidReason}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftLobby,
                        )
                    }
                    BdStatusDot(s.status)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BdSmallButton(if (detailId == s.id) "Hide" else "Detail") {
                        detailId = if (detailId == s.id) null else s.id
                    }
                    if (s.status == BdSessionStatus.PENDING && !s.voided) {
                        BdSmallButton("Complete") {
                            repo.updateSession(s.id) { it.copy(status = BdSessionStatus.COMPLETED) }
                            repo.stamp(me.name, "COMPLETE", s.bellNo)
                        }
                        if (!s.walkIn) {
                            BdSmallButton("No-show") {
                                repo.updateSession(s.id) { it.copy(status = BdSessionStatus.NO_SHOW) }
                                repo.stamp(me.name, "NO_SHOW", s.bellNo)
                            }
                            BdSmallButton("Cancel") {
                                repo.updateSession(s.id) { it.copy(status = BdSessionStatus.CANCELLED) }
                                repo.stamp(me.name, "CANCEL", s.bellNo)
                            }
                        }
                        BdSmallButton("Void") { voidTarget = s; voidReason = "" }
                    }
                    if (s.voided) {
                        BdSmallButton("Unvoid") {
                            repo.updateSession(s.id) { it.copy(voided = false, voidReason = "") }
                            repo.stamp(me.name, "UNVOID", s.bellNo)
                        }
                    }
                }
                if (detailId == s.id) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Slip ${s.bellNo}: guest ${s.clientName}, ${s.service} at ₱${s.price}, " +
                            "branch ${repo.branchName(s.branchId)}, rung ${s.time}. " +
                            "Lane ${s.lane.ifBlank { "unseated" }}. PENDING sessions per guest: " +
                            "${repo.pendingCountFor(s.clientName)} (house cap: at most one).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkLobby,
                    )
                }
                if (voidTarget?.id == s.id) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Void reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BdSmallButton("Confirm void") {
                            if (voidReason.isNotBlank()) {
                                repo.updateSession(s.id) { it.copy(voided = true, voidReason = voidReason.trim()) }
                                repo.stamp(me.name, "VOID", s.bellNo, voidReason.trim())
                                voidTarget = null
                            }
                        }
                        BdSmallButton("Keep slip") { voidTarget = null }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) {
            Text("No slips under $filter at this counter.", style = MaterialTheme.typography.bodyMedium, color = InkSoftLobby)
        }
    }
}

@Composable
internal fun BdStatusDot(status: BdSessionStatus) {
    val color = when (status) {
        BdSessionStatus.PENDING -> Brass
        BdSessionStatus.COMPLETED -> BellGreen
        BdSessionStatus.NO_SHOW -> BellLavender
        BdSessionStatus.CANCELLED -> BellRed
    }
    Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(status.name, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
internal fun BdClientsTab() {
    val repo = BellDeskFakeRepo
    var query by remember { mutableStateOf("") }
    var masked by remember { mutableStateOf(true) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    val shown = repo.clients.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.detail.contains(query.trim(), ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("CLIENT BOOK · GLOBAL", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("One guest, every branch. The book never splits.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Text(
            "House cap note: at most one PENDING session per guest — the bell refuses a second ring " +
                "while one is still open. Anonymized rows stay masked unless the cover is lifted.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftLobby,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search the book") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(onClick = { masked = !masked }) { Text(if (masked) "Lift cover" else "Mask cover") }
        }
        Spacer(Modifier.height(10.dp))
        shown.forEach { c ->
            val pending = repo.pendingCountFor(c.name)
            val displayName = if (c.anonymized && masked) "•••• ${c.genderAge}" else "${c.name} · ${c.genderAge}"
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleMedium, color = InkLobby)
                        Text(
                            if (c.anonymized && masked) "Anonymized view: gender + age kept, history kept."
                            else c.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftLobby,
                        )
                    }
                    if (pending > 0) {
                        Box(
                            modifier = Modifier.clip(MaterialTheme.shapes.small).background(Brass)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("PENDING ×$pending", style = MaterialTheme.typography.labelMedium, color = InkLobby) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BdSmallButton(if (pickedId == c.id) "Close file" else "Open file") {
                        pickedId = if (pickedId == c.id) null else c.id
                    }
                    BdSmallButton("Ring for guest") {
                        val no = repo.nextBell()
                        val already = repo.pendingCountFor(c.name) > 0
                        if (!already) {
                            repo.sessions.add(
                                0,
                                BdSession(
                                    id = "s-$no",
                                    bellNo = no,
                                    clientName = c.name,
                                    branchId = repo.clockedBranchId.value,
                                    status = BdSessionStatus.PENDING,
                                    service = "Follow-up",
                                    price = 850,
                                    walkIn = false,
                                    time = "now",
                                ),
                            )
                            repo.stamp(repo.currentUser.value.name, "BELL_RING", "$no for ${c.name}")
                        }
                    }
                }
                if (pickedId == c.id) {
                    Spacer(Modifier.height(6.dp))
                    val history = repo.sessions.filter { it.clientName == c.name }
                    Text(
                        if (history.isEmpty()) "No slips yet for this guest."
                        else "Slips: " + history.joinToString { "${it.bellNo}/${it.status}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkLobby,
                    )
                    if (pending > 0) {
                        Text(
                            "Second ring blocked: this guest already holds a PENDING bell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BellRed,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) Text("Nobody matches “$query”.", style = MaterialTheme.typography.bodyMedium)
    }
}
