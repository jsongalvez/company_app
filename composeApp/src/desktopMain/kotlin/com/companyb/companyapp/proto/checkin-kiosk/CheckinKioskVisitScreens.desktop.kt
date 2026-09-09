package com.companyb.companyapp.proto.checkinkiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #782 — checkin-kiosk arrival + line + guest screens: 3-tap check-in with queue ticket.

private val CareMenu = listOf(
    Triple("Standard", "45 min · easy first visit", 1200),
    Triple("Deep Tissue", "60 min · firm pressure", 1500),
    Triple("Hot Stone", "75 min · warm stones", 1800),
    Triple("Sports", "60 min · for training bodies", 1600),
)

@Composable
fun CheckinArrival(
    repo: CheckinFakeRepo,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    var returningId by remember { mutableStateOf<String?>(null) }
    var guestLabel by remember { mutableStateOf("") }
    var care by remember { mutableStateOf(CareMenu.first()) }
    var issued by remember { mutableStateOf<CheckinTicket?>(null) }

    CheckinHeading("Arrive in 3 taps.", 44)
    CheckinSub("No staff words, no clipboards — just you, your care, your ticket.")
    CheckinStepDots(step, 3, listOf("Who's here", "Pick care", "Take ticket"))

    if (issued != null) {
        CheckinTicketStub(
            ticketNo = issued!!.no,
            guest = issued!!.guest,
            care = issued!!.care,
            branch = repo.currentBranch.name,
            ahead = repo.queueAhead() - 1,
        )
        CheckinNoteCard("Session is PENDING on today's line. Walk-ins stay simple: they finish as COMPLETED — never NO_SHOW or CANCELLED.")
        CheckinBigButton("Done — see the line", onClick = onDone)
        return
    }

    when (step) {
        1 -> {
            CheckinPanel {
                CheckinHeading("Tap 1 — who's here?", 26)
                CheckinSub("Been here before? Tap your name. New face? Type what we call you.")
                Spacer(Modifier.height(10.dp))
                repo.clients.take(5).forEach { client ->
                    val label = if (client.anonymized) "Guest ${client.id.uppercase()} · ${client.gender} · ${client.age}" else client.name
                    CheckinPickRow(
                        title = label,
                        subtitle = if (client.anonymized) "Private record — gender + age kept" else "${client.gender} · ${client.age} · ${repo.pendingCountFor(client.id)} open",
                        picked = returningId == client.id,
                        onPick = {
                            returningId = client.id
                            guestLabel = label
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = guestLabel,
                    onValueChange = {
                        guestLabel = it
                        returningId = null
                    },
                    label = { Text("Or type a guest label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = CheckinColors.Cream,
                        unfocusedTextColor = CheckinColors.Cream,
                        focusedLabelColor = CheckinColors.Marigold,
                        unfocusedLabelColor = CheckinColors.FaintOnNight,
                    ),
                )
            }
            CheckinBigButton(
                "Next — pick care",
                enabled = guestLabel.isNotBlank(),
                onClick = { step = 2 },
            )
        }
        2 -> {
            CheckinPanel {
                CheckinHeading("Tap 2 — pick your care.", 26)
                CheckinSub("Hello, $guestLabel. What sounds good today?")
                Spacer(Modifier.height(10.dp))
                CareMenu.forEach { option ->
                    CheckinPickRow(
                        title = option.first,
                        subtitle = option.second,
                        picked = care == option,
                        onPick = { care = option },
                        trailing = "₱${option.third}",
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { CheckinGhostButton("Back") { step = 1 } }
                Box(Modifier.weight(1f)) {
                    CheckinBigButton("Next — ticket", onClick = { step = 3 })
                }
            }
        }
        else -> {
            CheckinPanel {
                CheckinHeading("Tap 3 — take your ticket.", 26)
                CheckinSub("$guestLabel · ${care.first} · ₱${care.third} · ${repo.currentBranch.name}")
                Spacer(Modifier.height(10.dp))
                CheckinNoteCard("${repo.queueAhead()} ahead of you right now. Your number prints next.")
                Spacer(Modifier.height(10.dp))
                CheckinBigButton("🎟  Check in now", onClick = {
                    issued = repo.checkIn(
                        guestLabel = guestLabel.ifBlank { "Walk-in guest" },
                        type = care.first,
                        price = care.third,
                        returningClientId = returningId,
                    )
                })
            }
            CheckinGhostButton("Back") { step = 2 }
        }
    }
}

@Composable
fun CheckinQueue(repo: CheckinFakeRepo) {
    var filter by remember { mutableStateOf<CheckinSessionStatus?>(null) }
    var manageId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    CheckinHeading("Today's line.", 44)
    CheckinSub("${repo.currentBranch.name} · every session at this branch, live.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckinChip("All", filter == null) { filter = null }
        CheckinSessionStatus.entries.forEach { status ->
            CheckinChip(status.name, filter == status) { filter = if (filter == status) null else status }
        }
    }
    val rows = repo.sessions.filter { it.branchId == repo.currentBranchId && (filter == null || it.status == filter) }
    if (rows.isEmpty()) {
        CheckinNoteCard("Nobody here under this filter. New arrivals land at the top of the line.")
    }
    rows.forEach { session ->
        CheckinPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●", color = session.status.dot(), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${session.ticketNo ?: session.bookedTime} · ${session.clientName}",
                        color = CheckinColors.Cream,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${session.type} · ${session.status}${if (session.walkIn) " · walk-in" else ""}${if (session.voided) " · VOID" else ""} · ₱${session.price}",
                        color = CheckinColors.FaintOnNight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (session.voided && session.voidReason != null) {
                        Text("Void: ${session.voidReason}", color = CheckinColors.Coral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.padding(top = 4.dp)) {
                    CheckinChip(if (manageId == session.id) "Close" else "Manage", manageId == session.id) {
                        manageId = if (manageId == session.id) null else session.id
                        error = null
                        reason = ""
                    }
                }
            }
            if (manageId == session.id) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CheckinSessionStatus.COMPLETED, CheckinSessionStatus.NO_SHOW, CheckinSessionStatus.CANCELLED).forEach { next ->
                        CheckinChip(next.name, false) {
                            error = repo.setSessionStatus(session.id, next)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Void reason (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = CheckinColors.Cream,
                        unfocusedTextColor = CheckinColors.Cream,
                        focusedLabelColor = CheckinColors.Marigold,
                        unfocusedLabelColor = CheckinColors.FaintOnNight,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        CheckinGhostButton(if (session.voided) "Unvoid" else "Void") {
                            error = repo.setVoid(session.id, !session.voided, reason)
                            if (error == null) {
                                manageId = null
                                reason = ""
                            }
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    CheckinNoteCard(error!!)
                }
            }
        }
    }
    CheckinNoteCard("House rule, enforced above: walk-in sessions cannot be NO_SHOW or CANCELLED — they simply complete or stay open.")
}

@Composable
fun CheckinCare(repo: CheckinFakeRepo) {
    CheckinHeading("Care menu.", 44)
    CheckinSub("Same four hands, every branch. Prices before you sit down.")
    CareMenu.forEach { option ->
        CheckinPanel {
            Text(option.first, color = CheckinColors.Cream, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(option.second, color = CheckinColors.FaintOnNight, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text("₱${option.third}", color = CheckinColors.Marigold, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
    CheckinNoteCard("Types auto-assign from guest history on the real system — here you just pick. ${repo.sessions.count { it.status == CheckinSessionStatus.PENDING }} sessions are waiting today.")
}

@Composable
fun CheckinGuests(repo: CheckinFakeRepo) {
    CheckinHeading("Guests.", 44)
    CheckinSub("One global book across every branch. A guest holds at most one open session.")
    repo.clients.forEach { client ->
        CheckinPanel {
            val label = if (client.anonymized) "Guest ${client.id.uppercase()}" else client.name
            Text(label, color = CheckinColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                if (client.anonymized) "Private — gender ${client.gender} + age ${client.age} kept for reports" else "${client.gender} · ${client.age} · ${repo.pendingCountFor(client.id)} open session(s)",
                color = CheckinColors.FaintOnNight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            CheckinGhostButton(if (client.anonymized) "Reveal (fake)" else "Make private (fake)") {
                repo.toggleAnonymized(client.id)
            }
        }
    }
    CheckinNoteCard("At-most-one-PENDING is a global rule: the desk refuses a second open session for the same guest until the first closes.")
}
