package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #818 — owner-desk folios and guest index: intake wizard, folio book, masked guests.

@Composable
fun OwnerIntake(
    repo: OwnerFakeRepo,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    var pickedClient by remember { mutableStateOf<String?>(null) }
    var guestLabel by remember { mutableStateOf("") }
    var pickedType by remember { mutableStateOf("Standard") }
    var walkIn by remember { mutableStateOf(true) }
    var ticket by remember { mutableStateOf<OwnerTicket?>(null) }
    val types = listOf("Standard" to 1200, "Deep Tissue" to 1500, "Hot Stone" to 1800, "Sports" to 1600)

    OwnerHeading("A new folio.", 34)
    OwnerSub("Three leaves: who, what, sealed. Walk-ins skip the books entirely.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1 to "Who", 2 to "What", 3 to "Sealed").forEach { (n, label) ->
            val active = n == step
            val done = n < step
            Text(
                "$n · $label",
                color = if (active || done) OwnerColors.NameplateInk else OwnerColors.Paper.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active || done) OwnerColors.Brass else Color.Transparent)
                    .border(1.dp, OwnerColors.Brass.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .clickable(enabled = done) { step = n }
                    .padding(vertical = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    when (step) {
        1 -> {
            OwnerLedger(title = "Who is it for?") {
                repo.clients.take(6).forEach { client ->
                    OwnerPickRow(
                        title = if (client.anonymized) "Guest ${client.id}" else client.name,
                        subtitle = "open PENDING: ${repo.pendingCountFor(client.id)} (at most one)",
                        picked = pickedClient == client.id,
                        onPick = { pickedClient = client.id },
                        trailing = "${client.gender}·${client.age}",
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = guestLabel,
                    onValueChange = { guestLabel = it },
                    label = { Text("Or ink a fresh walk-in label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OwnerBigButton(
                    "Next — pick care",
                    enabled = pickedClient != null || guestLabel.isNotBlank(),
                    onClick = { step = 2 },
                )
            }
        }

        2 -> {
            OwnerLedger(title = "What care?") {
                types.forEach { (name, price) ->
                    OwnerPickRow(
                        title = name,
                        subtitle = "₱$price base rate, settled at completion",
                        picked = pickedType == name,
                        onPick = { pickedType = name },
                        trailing = "₱$price",
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (walkIn) "Walk-in ✓" else "Mark walk-in",
                        color = if (walkIn) OwnerColors.NameplateInk else OwnerColors.InkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (walkIn) OwnerColors.Brass else Color.Transparent)
                            .border(1.dp, OwnerColors.Brass, RoundedCornerShape(6.dp))
                            .clickable { walkIn = !walkIn }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Walk-ins can never be NO_SHOW or CANCELLED.",
                        color = OwnerColors.InkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val price = types.first { it.first == pickedType }.second
                OwnerBigButton("Seal it — ₱$price") {
                    val returning = pickedClient?.takeIf { guestLabel.isBlank() }
                    val label =
                        if (guestLabel.isNotBlank()) {
                            guestLabel
                        } else {
                            repo.clients.firstOrNull { it.id == returning }?.name ?: "Guest"
                        }
                    ticket = repo.openSession(label, pickedType, price, returning, walkIn, "now")
                    guestLabel = ""
                    step = 3
                }
            }
        }

        else -> {
            val done = ticket
            OwnerLedger(title = "Folio ${done?.no ?: ""}", seal = "SEALED") {
                Text(
                    done?.no ?: "—",
                    color = OwnerColors.Ink,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OwnerSerif,
                )
                Text(
                    "${done?.guest} · ${done?.care} · ${repo.currentBranch.name}",
                    color = OwnerColors.InkSoft,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (repo.queueAhead() <= 1) {
                        "They are next — show them to the chair."
                    } else {
                        "${repo.queueAhead() - 1} ahead — show them to the chair."
                    },
                    color = OwnerColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                OwnerBigButton("Back to the folio book", onClick = onDone)
            }
        }
    }
}

@Composable
fun OwnerQueue(repo: OwnerFakeRepo) {
    var filter by remember { mutableStateOf<OwnerSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var opError by remember { mutableStateOf<String?>(null) }

    OwnerHeading("The folio book.", 34)
    OwnerSub("Every session at ${repo.currentBranch.name}. Open a folio to rule on it.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OwnerFilterChip("All", filter == null) { filter = null }
        OwnerSessionStatus.entries.forEach { status ->
            OwnerFilterChip(status.name, filter == status) {
                filter = if (filter == status) null else status
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OwnerNoteCard(
        "Walk-in folios refuse NO_SHOW and CANCELLED — they were never booked, so there is nothing to miss. " +
            "Only PENDING folios change standing.",
    )
    Spacer(Modifier.height(12.dp))
    val rows = repo.sessions.filter { it.branchId == repo.currentBranchId && (filter == null || it.status == filter) }
    if (rows.isEmpty()) {
        OwnerNoteCard("Nothing filed under this standing. A new folio fixes that in three leaves.")
    }
    rows.forEach { session ->
        OwnerLedger(
            title = "${session.bookedTime} · ${session.clientName}",
            subtitle = "${session.type} · ₱${session.price}${if (session.walkIn) " · walk-in" else ""}" +
                "${if (session.voided) " · VOID" else ""}",
            seal = session.status.name,
        ) {
            Text(
                "Hands on file: ${session.practitioner}${session.ticketNo?.let { " · folio $it" } ?: ""}",
                color = OwnerColors.InkSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (openId == session.id) "Close ruling ↑" else "Rule on this folio ↓",
                color = OwnerColors.BrassDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.clickable {
                    openId = if (openId == session.id) null else session.id
                    voidReason = ""
                    opError = null
                }.padding(vertical = 4.dp),
            )
            if (openId == session.id) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnerSessionStatus.entries.forEach { next ->
                        Text(
                            next.name.take(4),
                            color = if (session.status == next) Color.White else OwnerColors.InkSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (session.status == next) next.dot() else Color.White)
                                .border(1.dp, OwnerColors.PaperEdge, RoundedCornerShape(6.dp))
                                .clickable { opError = repo.setSessionStatus(session.id, next) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    label = { Text("Void / unvoid reason (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OwnerGhostButton(if (session.voided) "Unvoid" else "Void") {
                    opError = repo.setVoid(session.id, !session.voided, voidReason)
                    if (opError == null) voidReason = ""
                }
                if (session.voided && session.voidReason != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Void reason on file: ${session.voidReason}",
                        color = OwnerColors.Risk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (opError != null) {
                    Spacer(Modifier.height(6.dp))
                    OwnerPaperNote(opError!!)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OwnerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) OwnerColors.NameplateInk else OwnerColors.Paper.copy(alpha = 0.75f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) OwnerColors.Brass else Color.Transparent)
            .border(1.dp, OwnerColors.Brass.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun OwnerGuests(repo: OwnerFakeRepo) {
    OwnerHeading("Guest index.", 34)
    OwnerSub(
        "Clients are global — every house reads the same index. At most one PENDING folio each. " +
            "Names stay masked until you lift the cover.",
    )
    Spacer(Modifier.height(12.dp))
    repo.clients.forEach { client ->
        val open = repo.pendingCountFor(client.id)
        OwnerLedger(title = if (client.anonymized) "Guest ${client.id}" else client.name) {
            Text(
                if (client.anonymized) {
                    "Masked: name covered, gender ${client.gender} + age ${client.age} kept for the owner's eye only."
                } else {
                    "${client.gender} · age ${client.age} · open PENDING: $open"
                },
                color = OwnerColors.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            val sessionsFor = repo.sessions.filter { it.clientId == client.id }
            Text(
                if (sessionsFor.isEmpty()) {
                    "No folios on file."
                } else {
                    sessionsFor.joinToString(" · ") {
                        "${it.bookedTime} ${it.status}"
                    }
                },
                color = OwnerColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth()) {
                OwnerGhostButton(if (client.anonymized) "Lift the cover" else "Lower the cover") {
                    repo.toggleAnonymized(client.id)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
