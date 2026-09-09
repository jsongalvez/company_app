package com.companyb.companyapp.proto.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog

// #760 — sessions as a vertical day timeline: time rail left, seal nodes, detail popover.

@Composable
fun TlSessionsScreen(repo: TimelineRepo) {
    var showCreate by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val branch = repo.currentBranch()
    val items = repo.branchSessions(branch.id)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TlHeadline("Today's rail — ${branch.name}")
        Spacer(Modifier.weight(1f))
        TlPrimary("+ Log session") { showCreate = true }
    }
    TlNote("Sessions run PENDING → COMPLETED / NO_SHOW / CANCELLED. Status moves are drag-free stepper taps — no drag gestures anywhere.")
    if (items.isEmpty()) {
        TlEmptyLine("Quiet rail", "No sessions at this branch yet — log one above.")
    }
    items.forEach { s ->
        TlRailRow {
            TlCard {
                Column(
                    Modifier.fillMaxWidth().clickable { detailId = s.id },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.time, color = TlColors.Rail, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(s.clientName, color = TlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (s.walkIn) {
                            Spacer(Modifier.width(8.dp))
                            Text("WALK-IN", color = TlColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.weight(1f))
                        TlStatusSeal(s.status)
                    }
                    Text(
                        "${s.kind} · ₱${s.price} · ${s.practitioners}" +
                            if (s.voided) " · VOIDED (${s.voidReason})" else "",
                        color = TlColors.Faded,
                        fontSize = 12.sp,
                    )
                    if (s.walkIn) {
                        TlNote("Walk-in rule: cannot be marked NO_SHOW or CANCELLED — the rail only offers COMPLETED.")
                    }
                }
            }
        }
    }

    if (showCreate) {
        TlCreateDialog(repo, onClose = { showCreate = false })
    }
    val detail = detailId?.let { id -> repo.sessions.firstOrNull { it.id == id } }
    if (detail != null) {
        TlDetailPopover(repo, detail, onClose = { detailId = null })
    }
}

@Composable
private fun TlRailRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TlRailDot(TlColors.Rail)
            Box(Modifier.width(2.dp).height(14.dp).background(TlColors.RailFaint))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun TlDetailPopover(repo: TimelineRepo, s: TlSession, onClose: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    Dialog(onCloseRequest = onClose) {
        Box(
            Modifier.width(560.dp).background(TlColors.Card, RoundedCornerShape(12.dp)).padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TlHeadline("Session ${s.id}")
                    Spacer(Modifier.weight(1f))
                    TlLink("Close") { onClose() }
                }
                TlStatusSeal(s.status)
                Text("${s.time} · ${s.clientName} · ${s.kind}", color = TlColors.Ink, fontSize = 14.sp)
                Text(
                    "Final price ₱${s.price} (defaults to base rate, overridable) · Practitioners: ${s.practitioners}",
                    color = TlColors.Faded,
                    fontSize = 12.sp,
                )
                TlSubhead("Drag-free status transitions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TlPrimary("Completed", enabled = s.status == TlSessionStatus.PENDING && !s.voided) {
                        repo.advance(s.id, TlSessionStatus.COMPLETED)
                    }
                    TlGhost(
                        "No-show",
                        enabled = s.status == TlSessionStatus.PENDING && !s.walkIn && !s.voided,
                        onClick = { repo.advance(s.id, TlSessionStatus.NO_SHOW) },
                    )
                    TlGhost(
                        "Cancel",
                        enabled = s.status == TlSessionStatus.PENDING && !s.walkIn && !s.voided,
                        onClick = { repo.advance(s.id, TlSessionStatus.CANCELLED) },
                    )
                }
                TlSubhead("Void / unvoid")
                if (s.voided) {
                    Text("Voided: ${s.voidReason}", color = TlColors.Oxblood, fontSize = 13.sp)
                    TlGhost("Unvoid — restore record") { repo.unvoidSession(s.id) }
                } else {
                    TextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Void reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TlGhost("Void with reason", enabled = reason.isNotBlank()) {
                        repo.voidSession(s.id, reason.trim())
                        reason = ""
                    }
                }
            }
        }
    }
}

@Composable
private fun TlCreateDialog(repo: TimelineRepo, onClose: () -> Unit) {
    var time by remember { mutableStateOf("16:30") }
    var client by remember { mutableStateOf("New Client") }
    var price by remember { mutableStateOf("1200") }
    var walkIn by remember { mutableStateOf(false) }
    Dialog(onCloseRequest = onClose) {
        Box(
            Modifier.width(520.dp).background(TlColors.Card, RoundedCornerShape(12.dp)).padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TlHeadline("Log session")
                    Spacer(Modifier.weight(1f))
                    TlLink("Close") { onClose() }
                }
                TextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:MM)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(value = client, onValueChange = { client = it }, label = { Text("Client") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Price (PHP)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                    Spacer(Modifier.width(6.dp))
                    Text("Walk-in (no NO_SHOW / CANCELLED allowed)", color = TlColors.Ink, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TlPrimary("Create PENDING") {
                        repo.addSession(time.ifBlank { "17:00" }, client.ifBlank { "New Client" }, repo.currentBranchId, if (walkIn) "Walk-in" else "Follow-up", walkIn, price.toIntOrNull() ?: 1200)
                        onClose()
                    }
                    TlGhost("Cancel") { onClose() }
                }
            }
        }
    }
}
