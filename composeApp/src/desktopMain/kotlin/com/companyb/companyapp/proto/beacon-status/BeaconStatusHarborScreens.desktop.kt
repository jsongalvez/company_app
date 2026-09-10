package com.companyb.companyapp.proto.beaconstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun BsPanel(
    title: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(HarborDeep)
                .border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = BeaconGold)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = FogDim)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun BsScroll(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
internal fun BsBeaconTab() {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    val clocked by repo.clockedIn
    val branchId = repo.clockedBranchId.value
    val relief by repo.reliefEdit
    BsScroll {
        Text(
            "BEACON · ${repo.branchName(branchId).uppercase()}",
            style = MaterialTheme.typography.headlineSmall,
            color = FogWhite,
        )
        Text("Clock the watch, work the relief traffic. View-only on relief until a grant lands.", color = FogDim)
        BsPanel(
            "WATCH BELL",
            if (clocked) "On watch at ${repo.branchName(branchId)}." else "Off watch — ring in to start.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(me.name + " · " + me.role, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                    Text(
                        if (clocked) "CLOCKED IN" else "CLOCKED OUT",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (clocked) LampGreen else LampAmber,
                    )
                    if (branchId != me.homeBranchId) {
                        Text(
                            "Relief Duty at a non-home Branch · " + if (relief) "edit grant held" else "view-only",
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        repo.clockedIn.value = !clocked
                        repo.log(me.name, if (!clocked) "CLOCK IN" else "CLOCK OUT", repo.branchName(branchId))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeaconGold, contentColor = HarborNight),
                ) {
                    Text(if (clocked) "Clock out" else "Clock in")
                }
            }
        }
        BsPanel("RELIEF INVITES · branch offers you a day", "Accept writes the day grant · Decline sends it back.") {
            if (repo.invites.isEmpty()) Text("No invites on the wire.", color = FogDim)
            repo.invites.forEach { invite ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(invite.branchName, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                        Text(
                            "${invite.day} · ${invite.state}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                    OutlinedButton(onClick = {
                        repo.invites.remove(invite)
                        repo.reliefEdit.value = true
                        repo.log(me.name, "ACCEPT INVITE", invite.branchName)
                    }) { Text("Accept", color = LampGreen) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = {
                        repo.invites.remove(invite)
                        repo.log(me.name, "DECLINE INVITE", invite.branchName)
                    }) { Text("Decline", color = LampRed) }
                }
            }
        }
        BsPanel(
            "RELIEF REQUESTS · the broadcast board",
            "Any branch member grants or denies · yours can be withdrawn.",
        ) {
            if (repo.requests.isEmpty()) Text("Board is quiet.", color = FogDim)
            repo.requests.toList().forEach { req ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(req.branchName, style = MaterialTheme.typography.titleMedium, color = FogWhite)
                        Text(
                            "${req.day} · ${if (req.mine) "yours" else "crew ask"} · ${req.state}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                    if (req.mine) {
                        OutlinedButton(onClick = {
                            repo.requests.remove(req)
                            repo.log(me.name, "WITHDRAW REQUEST", req.branchName)
                        }) { Text("Withdraw", color = BeaconGold) }
                    } else {
                        OutlinedButton(onClick = {
                            repo.requests.remove(req)
                            repo.log(me.name, "GRANT REQUEST", req.branchName)
                        }) { Text("Grant", color = LampGreen) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = {
                            repo.requests.remove(req)
                            repo.log(me.name, "DENY REQUEST", req.branchName)
                        }) { Text("Deny", color = LampRed) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                repo.requests.add(
                    BsRequest("q-${repo.requests.size + 1}", "Tondo Medical Mission", "Tomorrow", mine = true),
                )
                repo.log(me.name, "ASK RELIEF", "Tondo Medical Mission")
            }) { Text("Ask another Branch for relief", color = SeaFoam) }
        }
        val (lamp, reason) = repo.lampFor(branchId)
        BsPanel("THIS BERTH'S LAMP", "The single light the harbor sees.") {
            Text(
                lamp.name + " · " + reason,
                style = MaterialTheme.typography.titleMedium,
                color =
                    when (lamp) {
                        BsLamp.GREEN -> LampGreen
                        BsLamp.AMBER -> LampAmber
                        BsLamp.RED -> LampRed
                    },
            )
        }
    }
}

@Composable
internal fun BsSignalsTab() {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    var lane by remember { mutableStateOf(repo.lanes[0]) }
    val branchId = repo.clockedBranchId.value
    val incoming =
        repo.sessions.filter {
            it.branchId == branchId && it.status == BsSessionStatus.PENDING &&
                it.lane.isEmpty()
        }
    val seated =
        repo.sessions.filter {
            it.branchId == branchId && it.status == BsSessionStatus.PENDING &&
                it.lane.isNotEmpty()
        }
    BsScroll {
        Text("SIGNALS · ARRIVAL BOARD", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text(
            "Unseated PENDING first, oldest on top. Walk-in signals never go NO_SHOW or CANCELLED — house rule.",
            color = FogDim,
        )
        BsPanel("SEAT A SIGNAL", "Pick a lane, then tap Seat on any incoming signal.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.lanes.forEach { option -> BsChip(option, lane == option) { lane = option } }
            }
        }
        BsPanel("INCOMING · ${incoming.size}", "One tap seats the signal to the picked lane.") {
            if (incoming.isEmpty()) Text("Water is calm — nothing incoming.", color = FogDim)
            incoming.forEach { session ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${session.signalNo} · ${session.clientName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = FogWhite,
                        )
                        Text(
                            "${session.service} · ${session.time}" + if (session.walkIn) " · walk-in" else " · booked",
                            style = MaterialTheme.typography.bodySmall,
                            color = FogDim,
                        )
                    }
                    OutlinedButton(onClick = {
                        repo.sessions.remove(session)
                        repo.sessions.add(0, session.copy(lane = lane))
                        repo.log(me.name, "SEAT", "${session.signalNo} → $lane")
                    }) { Text("Seat", color = LampGreen) }
                }
            }
        }
        BsPanel("SEATED · ${seated.size}", "Seated signals wait out the visit — complete them from the LEDGER.") {
            if (seated.isEmpty()) Text("No one seated yet.", color = FogDim)
            seated.forEach { session ->
                Text("${session.signalNo} · ${session.clientName} · ${session.lane}", color = FogWhite)
            }
        }
    }
}

@Composable
internal fun BsLedgerTab() {
    val repo = BeaconStatusFakeRepo
    var filter by remember { mutableStateOf<BsSessionStatus?>(null) }
    var detail by remember { mutableStateOf<BsSession?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val branchId = repo.clockedBranchId.value
    BsScroll {
        Text("LEDGER · SESSION RECORD", style = MaterialTheme.typography.headlineSmall, color = FogWhite)
        Text(
            "PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-in signals never go NO_SHOW or CANCELLED — house rule.",
            color = FogDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BsChip("ALL", filter == null) { filter = null }
            BsSessionStatus.values().forEach { status ->
                BsChip(status.name, filter == status) { filter = if (filter == status) null else status }
            }
        }
        val rows = repo.sessions.filter { it.branchId == branchId && (filter == null || it.status == filter) }
        if (rows.isEmpty()) Text("No signals on this frequency.", color = FogDim)
        rows.forEach { session ->
            BsSignalRow(session, onOpen = { detail = session })
        }
        OutlinedButton(onClick = { showNew = true }) { Text("Log new signal", color = BeaconGold) }
        if (showNew) BsNewSignalSheet(branchId = branchId, onClose = { showNew = false })
        detail?.let { BsSignalDetail(session = it, onClose = { detail = null }) }
    }
}

@Composable
internal fun BsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) BeaconGold else HarborPanel)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HarborNight else FogDim)
    }
}

@Composable
internal fun BsSignalRow(
    session: BsSession,
    onOpen: () -> Unit,
) {
    val statusColor =
        when (session.status) {
            BsSessionStatus.PENDING -> LampAmber
            BsSessionStatus.COMPLETED -> LampGreen
            BsSessionStatus.NO_SHOW -> LampRed
            BsSessionStatus.CANCELLED -> FogDim
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(HarborDeep)
                .border(1.dp, HarborEdge, MaterialTheme.shapes.medium)
                .clickable { onOpen() }
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${session.signalNo} · ${session.clientName}",
                style = MaterialTheme.typography.titleMedium,
                color = FogWhite,
            )
            Text(
                "${session.service} · ₱${session.price} · ${session.time}" +
                    (if (session.walkIn) " · walk-in" else "") +
                    (if (session.lane.isNotEmpty()) " · ${session.lane}" else "") +
                    (if (session.voided) " · VOID ${session.voidReason}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = FogDim,
            )
        }
        Text(session.status.name, style = MaterialTheme.typography.labelLarge, color = statusColor)
    }
}

@Composable
internal fun BsSignalDetail(
    session: BsSession,
    onClose: () -> Unit,
) {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    var reason by remember { mutableStateOf("") }
    BsPanel("SIGNAL ${session.signalNo}", "${session.clientName} · tap a verdict.") {
        Text("Status ${session.status.name} · voided=${session.voided}", color = FogDim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                repo.sessions.remove(session)
                repo.sessions.add(0, session.copy(status = BsSessionStatus.COMPLETED))
                repo.log(me.name, "COMPLETE", session.signalNo)
                onClose()
            }) { Text("Complete", color = LampGreen) }
            if (!session.walkIn) {
                OutlinedButton(onClick = {
                    repo.sessions.remove(session)
                    repo.sessions.add(0, session.copy(status = BsSessionStatus.NO_SHOW))
                    repo.log(me.name, "MARK NO_SHOW", session.signalNo)
                    onClose()
                }) { Text("No-show", color = LampAmber) }
                OutlinedButton(onClick = {
                    repo.sessions.remove(session)
                    repo.sessions.add(0, session.copy(status = BsSessionStatus.CANCELLED))
                    repo.log(me.name, "CANCEL", session.signalNo)
                    onClose()
                }) { Text("Cancel", color = LampRed) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Void reason (required to void)") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FogWhite, unfocusedTextColor = FogWhite),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                if (reason.isBlank()) return@OutlinedButton
                repo.sessions.remove(session)
                repo.sessions.add(0, session.copy(voided = true, voidReason = reason))
                repo.log(me.name, "VOID", session.signalNo, reason)
                onClose()
            }) { Text("Void", color = LampRed) }
            if (session.voided) {
                OutlinedButton(onClick = {
                    repo.sessions.remove(session)
                    repo.sessions.add(0, session.copy(voided = false, voidReason = ""))
                    repo.log(me.name, "UNVOID", session.signalNo)
                    onClose()
                }) { Text("Unvoid", color = SeaFoam) }
            }
            OutlinedButton(onClick = onClose) { Text("Close", color = FogDim) }
        }
    }
}

@Composable
internal fun BsNewSignalSheet(
    branchId: String,
    onClose: () -> Unit,
) {
    val repo = BeaconStatusFakeRepo
    val me = repo.currentUser.value
    var name by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(true) }
    BsPanel("NEW SIGNAL", "Fake stamp — lands PENDING on this berth.") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Client name") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FogWhite, unfocusedTextColor = FogWhite),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BsChip("WALK-IN", walkIn) { walkIn = true }
            BsChip("BOOKED", !walkIn) { walkIn = false }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val no = "S-${repo.signalSeq++}"
                    repo.sessions.add(
                        0,
                        BsSession(
                            "s-$no",
                            no,
                            name.trim(),
                            branchId,
                            BsSessionStatus.PENDING,
                            "Follow-up",
                            850,
                            walkIn = walkIn,
                            time = "now",
                        ),
                    )
                    repo.log(me.name, "LOG SIGNAL", no)
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BeaconGold, contentColor = HarborNight),
            ) {
                Text("Stamp it")
            }
            OutlinedButton(onClick = onClose) { Text("Close", color = FogDim) }
        }
    }
}
