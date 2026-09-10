package com.companyb.companyapp.proto.bottomdock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

// #810 — bottom-dock sessions and clients: New window, Queue manager, Guests book.

@Composable
fun DockArrival(
    repo: DockFakeRepo,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    var pickedClient by remember { mutableStateOf<String?>(null) }
    var guestLabel by remember { mutableStateOf("") }
    var pickedType by remember { mutableStateOf("Standard") }
    var walkIn by remember { mutableStateOf(true) }
    var ticket by remember { mutableStateOf<DockTicket?>(null) }
    val types = listOf("Standard" to 1200, "Deep Tissue" to 1500, "Hot Stone" to 1800, "Sports" to 1600)

    DockHeading("New session.", 34)
    DockSub("Three windows: who, what, done. Walk-ins skip the books entirely.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1 to "Who", 2 to "What", 3 to "Done").forEach { (n, label) ->
            val active = n == step
            val done = n < step
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active || done) DockColors.Grape else Color.White)
                    .border(
                        1.dp,
                        if (active ||
                            done
                        ) {
                            DockColors.Grape
                        } else {
                            DockColors.CardEdge
                        },
                        RoundedCornerShape(14.dp),
                    ).clickable(enabled = done) { step = n }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n · $label",
                    color = if (active || done) Color.White else DockColors.InkSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (step) {
        1 -> {
            DockWindow(title = "Who is it for?") {
                repo.clients.take(6).forEach { client ->
                    DockPickRow(
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
                    label = { Text("Or type a fresh walk-in label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                DockBigButton(
                    "Next — pick care",
                    enabled = pickedClient != null || guestLabel.isNotBlank(),
                    onClick = { step = 2 },
                )
            }
        }

        2 -> {
            DockWindow(title = "What care?") {
                types.forEach { (name, price) ->
                    DockPickRow(
                        title = name,
                        subtitle = "₱$price base rate, overridable at completion",
                        picked = pickedType == name,
                        onPick = { pickedType = name },
                        trailing = "₱$price",
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (walkIn) DockColors.Grape else DockColors.WindowBar)
                            .clickable { walkIn = !walkIn }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (walkIn) "Walk-in ✓" else "Mark walk-in",
                            color = if (walkIn) Color.White else DockColors.InkSoft,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Walk-ins can never be NO_SHOW or CANCELLED.",
                        color = DockColors.InkSoft,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val price = types.first { it.first == pickedType }.second
                DockBigButton("Open it — ₱$price") {
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
            DockWindow(title = "Ticket ${done?.no ?: ""}") {
                Text(
                    done?.no ?: "—",
                    color = DockColors.Ink,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${done?.guest} · ${done?.care} · ${repo.currentBranch.name}",
                    color = DockColors.InkSoft,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (repo.queueAhead() <=
                        1
                    ) {
                        "They are next — point at the beanbag."
                    } else {
                        "${repo.queueAhead() - 1} ahead — point at the beanbag."
                    },
                    color = DockColors.Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                DockBigButton("Back to the queue", onClick = onDone)
            }
        }
    }
}

@Composable
fun DockQueue(repo: DockFakeRepo) {
    var filter by remember { mutableStateOf<DockSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var opError by remember { mutableStateOf<String?>(null) }

    DockHeading("The queue.", 34)
    DockSub("Every session at ${repo.currentBranch.name}. Tap a row to manage it.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DockFilterChip("All", filter == null) { filter = null }
        DockSessionStatus.entries.forEach { status ->
            DockFilterChip(status.name, filter == status) {
                filter = if (filter == status) null else status
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    DockNoteCard(
        "Walk-in sessions refuse NO_SHOW and CANCELLED — they were never booked, so there is nothing to miss. Only PENDING sessions change status.",
    )
    Spacer(Modifier.height(12.dp))
    val rows = repo.sessions.filter { it.branchId == repo.currentBranchId && (filter == null || it.status == filter) }
    if (rows.isEmpty()) {
        DockNoteCard("Nothing here under this filter. The + tile fixes that in three windows.")
    }
    rows.forEach { session ->
        DockWindow(title = "${session.bookedTime} · ${session.clientName}") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(session.status.dot())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(session.status.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${session.type} · ₱${session.price}${if (session.walkIn) " · walk-in" else ""}${if (session.voided) " · VOID" else ""}",
                    color = DockColors.InkSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Hands: ${session.practitioner}${session.ticketNo?.let { " · ticket $it" } ?: ""}",
                color = DockColors.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        openId = if (openId == session.id) null else session.id
                        voidReason = ""
                        opError = null
                    }.padding(vertical = 4.dp),
            ) {
                Text(
                    if (openId == session.id) "Close manager ↑" else "Manage ↓",
                    color = DockColors.Grape,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            if (openId == session.id) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DockSessionStatus.entries.forEach { next ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (session.status == next) next.dot() else DockColors.WindowBar)
                                .clickable { opError = repo.setSessionStatus(session.id, next) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                next.name.take(4),
                                color = if (session.status == next) Color.White else DockColors.InkSoft,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        DockGhostButton(if (session.voided) "Unvoid" else "Void") {
                            opError = repo.setVoid(session.id, !session.voided, voidReason)
                            if (opError == null) voidReason = ""
                        }
                    }
                }
                if (session.voided && session.voidReason != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Void reason on file: ${session.voidReason}",
                        color = DockColors.Coral,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (opError != null) {
                    Spacer(Modifier.height(6.dp))
                    DockNoteCard(opError!!)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DockFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) DockColors.Ink else Color.White)
            .border(1.dp, if (selected) DockColors.Ink else DockColors.CardEdge, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else DockColors.InkSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun DockGuests(repo: DockFakeRepo) {
    DockHeading("Address book.", 34)
    DockSub("Clients are global — every branch sees the same people. At most one PENDING session each.")
    Spacer(Modifier.height(12.dp))
    repo.clients.forEach { client ->
        val open = repo.pendingCountFor(client.id)
        DockWindow(title = if (client.anonymized) "Guest ${client.id}" else client.name) {
            Text(
                if (client.anonymized) {
                    "Anonymized: name hidden, gender ${client.gender} + age ${client.age} kept for reporting."
                } else {
                    "${client.gender} · age ${client.age} · open PENDING: $open"
                },
                color = DockColors.InkSoft,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            val sessionsFor = repo.sessions.filter { it.clientId == client.id }
            Text(
                if (sessionsFor.isEmpty()) {
                    "No sessions on file."
                } else {
                    sessionsFor.joinToString(" · ") {
                        "${it.bookedTime} ${it.status}"
                    }
                },
                color = DockColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            DockGhostButton(if (client.anonymized) "Reveal name" else "Anonymize") {
                repo.toggleAnonymized(client.id)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
