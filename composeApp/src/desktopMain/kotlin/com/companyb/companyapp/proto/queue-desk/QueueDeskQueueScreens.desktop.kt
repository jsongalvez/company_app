package com.companyb.companyapp.proto.queuedesk

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
internal fun QdTriageTab() {
    val me = QueueDeskFakeRepo.currentUser.value
    var showNew by remember { mutableStateOf(false) }
    val inbox = QueueDeskFakeRepo.sessions.filter { it.status == QdSessionStatus.PENDING && it.claimedBy.isEmpty() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRIAGE INBOX · ${inbox.size} UNCLAIMED", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
                Text("Oldest ticket first. Claim-next takes the top slip.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
            }
            Button(
                onClick = {
                    val top = QueueDeskFakeRepo.sessions
                        .filter { it.status == QdSessionStatus.PENDING && it.claimedBy.isEmpty() }
                        .minByOrNull { it.ticketNo }
                    if (top != null) {
                        QueueDeskFakeRepo.updateSession(top.id) { it.copy(claimedBy = me.name) }
                        QueueDeskFakeRepo.stamp(me.name, "CLAIM_NEXT", "Session ${top.ticketNo} (${top.clientName})")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClaimBlue),
                enabled = inbox.isNotEmpty(),
            ) { Text(if (inbox.isNotEmpty()) "Claim-next (${inbox.minBy { it.ticketNo }.ticketNo})" else "Belt clear") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showNew = !showNew }) { Text(if (showNew) "Close new-ticket slip" else "New walk-in ticket") }
        if (showNew) {
            Spacer(Modifier.height(8.dp))
            QdNewTicketSlip()
        }
        Spacer(Modifier.height(12.dp))
        if (inbox.isEmpty()) {
            QdEmptyNote("Belt clear. Every slip has hands on it.")
        } else {
            inbox.sortedBy { it.ticketNo }.forEach { session ->
                QdTicketSlip(session = session, mine = false)
            }
        }
    }
}

@Composable
internal fun QdNewTicketSlip() {
    val me = QueueDeskFakeRepo.currentUser.value
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Initial") }
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White)
            .border(1.dp, ClaimBlue, MaterialTheme.shapes.medium).padding(14.dp),
    ) {
        Text("NEW WALK-IN SLIP", style = MaterialTheme.typography.titleSmall, color = ClaimBlue)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Initial", "Follow-up", "Maintenance").forEach { option ->
                Box(
                    modifier = Modifier.clip(MaterialTheme.shapes.small)
                        .background(if (kind == option) ClaimBlue else TicketEdge)
                        .clickable { kind = option }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(option, style = MaterialTheme.typography.labelMedium, color = if (kind == option) Color.White else InkQueue)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val ticket = QueueDeskFakeRepo.nextTicket()
                val label = name.ifBlank { "Walk-in guest" }
                QueueDeskFakeRepo.sessions.add(
                    0,
                    QdSession(
                        id = "s-$ticket",
                        ticketNo = ticket,
                        clientName = label,
                        branchId = QueueDeskFakeRepo.clockedBranchId.value,
                        status = QdSessionStatus.PENDING,
                        type = kind,
                        price = 900,
                        walkIn = true,
                        time = "now",
                    ),
                )
                if (QueueDeskFakeRepo.clients.none { it.name == label }) {
                    QueueDeskFakeRepo.clients.add(QdClient("c-$ticket", label, "Walk-in, first visit", "— · —"))
                }
                QueueDeskFakeRepo.stamp(me.name, "CREATE_SESSION", "Session $ticket ($label, walk-in)")
                name = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = StampGreen),
        ) { Text("Stamp ticket (PENDING)") }
    }
}

@Composable
internal fun QdTrayTab() {
    val me = QueueDeskFakeRepo.currentUser.value
    val mine = QueueDeskFakeRepo.sessions.filter { it.status == QdSessionStatus.PENDING && it.claimedBy == me.name }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("MY TRAY · ${mine.size} IN HAND", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text("Finish, stamp NO_SHOW, or release back to triage.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(12.dp))
        if (mine.isEmpty()) {
            QdEmptyNote("Tray empty. Hit claim-next on the triage inbox.")
        } else {
            mine.sortedBy { it.ticketNo }.forEach { session ->
                QdTicketSlip(session = session, mine = true)
            }
        }
    }
}

@Composable
internal fun QdDoneTab() {
    val done = QueueDeskFakeRepo.sessions.filter { it.status != QdSessionStatus.PENDING }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("DONE PILE · ${done.size} STAMPED", style = MaterialTheme.typography.labelLarge, color = StampGreen)
        Text("Completed, no-show, cancelled. Void keeps the slip visible.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(12.dp))
        if (done.isEmpty()) {
            QdEmptyNote("Nothing finished yet today.")
        } else {
            done.sortedBy { it.ticketNo }.forEach { session ->
                QdTicketSlip(session = session, mine = false)
            }
        }
    }
}

@Composable
internal fun QdTicketSlip(session: QdSession, mine: Boolean) {
    val me = QueueDeskFakeRepo.currentUser.value
    var expanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf(session.voidReason) }
    val slipColor = when (session.status) {
        QdSessionStatus.PENDING -> SlipPending
        QdSessionStatus.COMPLETED -> SlipCompleted
        QdSessionStatus.NO_SHOW -> SlipNoShow
        QdSessionStatus.CANCELLED -> SlipCancelled
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(MaterialTheme.shapes.medium)
            .background(if (session.voided) VoidWash else Color.White)
            .border(1.dp, TicketEdge, MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(slipColor).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(session.ticketNo, style = MaterialTheme.typography.labelLarge, color = InkQueue)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.clientName + if (session.voided) " · VOID" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkQueue,
                )
                Text(
                    "${session.type} · ₱${session.price} · ${session.time}" +
                        if (session.walkIn) " · walk-in" else "" +
                        if (session.claimedBy.isNotEmpty()) " · held by ${session.claimedBy}" else " · unclaimed",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            Text(session.status.name, style = MaterialTheme.typography.labelMedium, color = statusInk(session.status))
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                if (session.status == QdSessionStatus.PENDING) {
                    if (session.claimedBy.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                QueueDeskFakeRepo.updateSession(session.id) { it.copy(claimedBy = me.name) }
                                QueueDeskFakeRepo.stamp(me.name, "CLAIM", "Session ${session.ticketNo}")
                            }, colors = ButtonDefaults.buttonColors(containerColor = ClaimBlue)) { Text("Claim") }
                            OutlinedButton(onClick = { finishSession(session, QdSessionStatus.COMPLETED, me.name) }) { Text("Complete") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { finishSession(session, QdSessionStatus.COMPLETED, me.name) }) { Text("Complete") }
                            OutlinedButton(onClick = { finishSession(session, QdSessionStatus.NO_SHOW, me.name) }) { Text("No-show") }
                            OutlinedButton(onClick = { finishSession(session, QdSessionStatus.CANCELLED, me.name) }) { Text("Cancel") }
                        }
                        if (mine || session.claimedBy == me.name) {
                            OutlinedButton(onClick = {
                                QueueDeskFakeRepo.updateSession(session.id) { it.copy(claimedBy = "") }
                                QueueDeskFakeRepo.stamp(me.name, "RELEASE", "Session ${session.ticketNo} back to triage")
                            }) { Text("Release to triage") }
                        }
                    }
                    if (session.walkIn) {
                        Text(
                            "Rule: walk-in sessions cannot be stamped NO_SHOW or CANCELLED.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StampAmber,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (session.voided) {
                    Text("Voided: ${session.voidReason}", style = MaterialTheme.typography.bodySmall, color = StampRed)
                    OutlinedButton(onClick = {
                        QueueDeskFakeRepo.updateSession(session.id) { it.copy(voided = false, voidReason = "") }
                        QueueDeskFakeRepo.stamp(me.name, "UNVOID", "Session ${session.ticketNo}")
                    }) { Text("Unvoid") }
                } else {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Void reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        if (reason.isNotBlank()) {
                            QueueDeskFakeRepo.updateSession(session.id) { it.copy(voided = true, voidReason = reason) }
                            QueueDeskFakeRepo.stamp(me.name, "VOID", "Session ${session.ticketNo}", reason)
                        }
                    }, enabled = reason.isNotBlank()) { Text("Void (excluded from finance, slip stays)") }
                }
            }
        }
    }
}

private fun finishSession(session: QdSession, target: QdSessionStatus, actor: String) {
    if (session.walkIn && (target == QdSessionStatus.NO_SHOW || target == QdSessionStatus.CANCELLED)) return
    QueueDeskFakeRepo.updateSession(session.id) { it.copy(status = target) }
    QueueDeskFakeRepo.stamp(actor, target.name, "Session ${session.ticketNo} (${session.clientName})")
}

private fun statusInk(status: QdSessionStatus): androidx.compose.ui.graphics.Color = when (status) {
    QdSessionStatus.PENDING -> StampAmber
    QdSessionStatus.COMPLETED -> StampGreen
    QdSessionStatus.NO_SHOW -> androidx.compose.ui.graphics.Color(0xFF5E548E)
    QdSessionStatus.CANCELLED -> StampRed
}

@Composable
internal fun QdEmptyNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White)
            .border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    }
}

@Composable
internal fun QdClientsTab() {
    var query by remember { mutableStateOf("") }
    var lifted by remember { mutableStateOf(setOf<String>()) }
    val me = QueueDeskFakeRepo.currentUser.value
    val shown = QueueDeskFakeRepo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("CLIENT INDEX · GLOBAL", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text("One person, every branch. At most one PENDING session each.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search clients") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        shown.forEach { client ->
            val pending = QueueDeskFakeRepo.pendingCountFor(client.name)
            val isLifted = lifted.contains(client.id)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (client.anonymized && !isLifted) "Client ${client.id.uppercase()} (masked)" else client.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = InkQueue,
                        )
                        Text(
                            if (client.anonymized && !isLifted) "Anonymized: PII removed, kept ${client.genderAge} for reporting"
                            else "${client.detail} · ${client.genderAge}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                    }
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (pending > 0) SlipPending else SlipCompleted)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(if (pending > 0) "$pending PENDING" else "NO OPEN", style = MaterialTheme.typography.labelMedium, color = InkQueue)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (client.anonymized) {
                        OutlinedButton(onClick = {
                            lifted = if (isLifted) lifted - client.id else lifted + client.id
                        }) { Text(if (isLifted) "Mask again" else "Lift cover") }
                    }
                    OutlinedButton(
                        onClick = {
                            val ticket = QueueDeskFakeRepo.nextTicket()
                            QueueDeskFakeRepo.sessions.add(
                                0,
                                QdSession(
                                    id = "s-$ticket",
                                    ticketNo = ticket,
                                    clientName = client.name,
                                    branchId = QueueDeskFakeRepo.clockedBranchId.value,
                                    status = QdSessionStatus.PENDING,
                                    type = "Follow-up",
                                    price = 850,
                                    walkIn = false,
                                    time = "now",
                                ),
                            )
                            QueueDeskFakeRepo.stamp(me.name, "CREATE_SESSION", "Session $ticket (${client.name})")
                        },
                        enabled = pending == 0,
                    ) { Text(if (pending == 0) "Open session" else "Blocked: one PENDING already") }
                }
                if (pending > 0) {
                    Text("Note: a client holds at most one PENDING session at a time.", style = MaterialTheme.typography.bodySmall, color = StampAmber)
                }
            }
        }
    }
}
