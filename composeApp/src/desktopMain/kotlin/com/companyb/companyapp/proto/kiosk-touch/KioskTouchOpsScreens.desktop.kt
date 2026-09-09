package com.companyb.companyapp.proto.kiosktouch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

// #762 — kiosk-touch ops: sessions with walk-in rule + void flow, clients, team.

@Composable
fun KioskSessions(repo: KioskFakeRepo) {
    var filter by remember { mutableStateOf<KioskSessionStatus?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Sessions", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("PENDING → COMPLETED / NO_SHOW / CANCELLED.", color = KioskColors.Muted, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KioskChip(text = "ALL", selected = filter == null, onClick = { filter = null })
            KioskSessionStatus.entries.forEach { status ->
                KioskChip(text = status.name, selected = filter == status, onClick = { filter = status })
            }
        }
        Spacer(Modifier.height(16.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        if (list.isEmpty()) {
            KioskCard {
                Column {
                    Text("Nothing here.", color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    KioskNote("Try another filter, or add a walk-in from Home in 3 taps.")
                }
            }
        }
        list.forEach { session ->
            KioskCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = session.status.dot(), fontSize = 30.sp)
                        KioskRowGap()
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${session.bookedTime}  ${session.clientName}",
                                color = KioskColors.Ink,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "${session.type} · ₱${session.price} · ${session.practitioner}" +
                                    (if (session.walkIn) " · WALK-IN" else ""),
                                color = KioskColors.Muted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                session.status.name,
                                color = session.status.dot(),
                                fontWeight = FontWeight.Black,
                                fontSize = 19.sp,
                            )
                            if (session.voided) {
                                Text("VOIDED", color = KioskColors.Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val expanded = selectedId == session.id
                    KioskSecondary(
                        text = if (expanded) "Close" else "Manage — big buttons",
                        onClick = { selectedId = if (expanded) null else session.id },
                    )
                    if (expanded) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "SET STATUS (from PENDING)",
                            color = KioskColors.Muted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KioskSessionStatus.entries.forEach { next ->
                                KioskChip(text = next.name, selected = session.status == next, onClick = {
                                    error = repo.setSessionStatus(session.id, next)
                                })
                            }
                        }
                        KioskNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = reason,
                            onValueChange = { reason = it; error = null },
                            label = { Text(if (session.voided) "Unvoid reason" else "Void reason — required") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (session.voided) {
                                KioskRowButton(text = "Unvoid", onClick = {
                                    error = repo.setVoid(session.id, false, reason)
                                    if (error == null) reason = ""
                                })
                            } else {
                                KioskRowButton(text = "Void it", onClick = {
                                    error = repo.setVoid(session.id, true, reason)
                                    if (error == null) reason = ""
                                })
                            }
                        }
                        if (session.voidReason != null) KioskNote("Void reason on record: ${session.voidReason}")
                        KioskError(error)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun KioskClients(repo: KioskFakeRepo) {
    Column {
        Text("Clients", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text(
            "One global record per person — every branch sees the same list.",
            color = KioskColors.Muted,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        KioskCard {
            Column {
                Text(
                    "At most one PENDING session per client.",
                    color = KioskColors.Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                KioskNote("The kiosk refuses a second open booking — finish or cancel the first one.")
            }
        }
        Spacer(Modifier.height(14.dp))
        repo.clients.forEach { client ->
            val pending = repo.pendingCountFor(client.id)
            val shownName = if (client.anonymized) "Anonymized · ${client.gender}/${client.age}" else client.name
            KioskCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(shownName, color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${client.gender} · age ${client.age} · $pending PENDING",
                            color = KioskColors.Muted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (client.anonymized) {
                            KioskNote("Anonymized view: PII masked, gender + age kept for reporting.")
                        }
                    }
                    KioskRowGap()
                    KioskGhostButton(
                        text = if (client.anonymized) "Reveal" else "Mask",
                        onClick = { repo.toggleAnonymized(client.id) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
fun KioskTeam(repo: KioskFakeRepo) {
    Column {
        Text("Team", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("Who works where, and what they may touch.", color = KioskColors.Muted, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        repo.users.forEach { user ->
            val home = repo.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: user.homeBranchId
            KioskCard {
                Column {
                    Text(user.name, color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${user.role.name} · slot ${user.slot} · home $home",
                        color = KioskColors.Muted,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (user.role == KioskRole.ONBOARDING) {
                        KioskNote(
                        "ONBOARDING: locked. Empty capability bundle — " +
                            "nothing derives until MANAGE_USERS grants a role.",
                    )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        KioskCard {
            Column {
                KioskSection("Role glance")
                Text(
                    "Practitioner: sessions + clients. Coordinator: money + PAST days. " +
                        "Manager: + people. Accountant: read-only.",
                    color = KioskColors.Ink,
                    fontSize = 20.sp,
                )
                KioskNote("Branch Slot orders names in reports (1 = senior). Relief sorts after home slots.")
            }
        }
    }
}
