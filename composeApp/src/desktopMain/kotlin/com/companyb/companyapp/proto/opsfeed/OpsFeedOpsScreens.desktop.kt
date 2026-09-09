package com.companyb.companyapp.proto.opsfeed

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

// #790 — ops-feed service screens: session queue + client roster, both echoing into the log.

@Composable
fun OfSessions(repo: OpsFeedFakeRepo) {
    var filter by remember { mutableStateOf<OfSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }

    OfCard {
        OfSectionTitle("Session queue — ${repo.currentBranch.name}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfChip("ALL", filter == null) { filter = null }
            OfSessionStatus.entries.forEach { status ->
                OfChip(status.name, filter == status) { filter = status }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Walk-in rule: walk-in sessions never take NO_SHOW or CANCELLED — they simply leave the queue.",
            color = OfColors.Muted, fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    val visible = repo.sessions.filter {
        it.branchId == repo.currentBranchId && (filter == null || it.status == filter)
    }
    if (visible.isEmpty()) {
        OfCard { Text("No sessions on this channel.", color = OfColors.Muted, fontSize = 13.sp) }
    }
    visible.forEach { session ->
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.bookedTime, color = OfColors.Muted, fontSize = 13.sp, fontFamily = OfMono,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OfTag(
                    if (session.voided) "VOIDED" else session.status.name,
                    if (session.voided) OfColors.Bad else when (session.status) {
                        OfSessionStatus.PENDING -> OfColors.Warn
                        OfSessionStatus.COMPLETED -> OfColors.Session
                        OfSessionStatus.NO_SHOW -> OfColors.Bad
                        OfSessionStatus.CANCELLED -> OfColors.Muted
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.clientName} · ${session.type} · ₱${session.price}",
                color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "${session.id} · ${session.practitioner}" + if (session.walkIn) " · walk-in" else "",
                color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono,
            )
            if (session.voidReason != null) {
                Text("Void: ${session.voidReason}", color = OfColors.Bad, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            if (expanded == session.id) {
                if (session.status == OfSessionStatus.PENDING && !session.voided) {
                    OfRowButtons(
                        {
                            OfGhost("Complete") {
                                repo.sessions[repo.sessions.indexOf(session)] =
                                    session.copy(status = OfSessionStatus.COMPLETED)
                                repo.post(OfDomain.SESSION, "Session ${session.id} completed",
                                    "${session.clientName} · ₱${session.price}.")
                            }
                        },
                        {
                            OfGhost("No-show") {
                                if (session.walkIn) {
                                    repo.post(OfDomain.SESSION, "Walk-in rule held",
                                        "Session ${session.id} refused NO_SHOW: walk-ins leave the queue instead.")
                                } else {
                                    repo.sessions[repo.sessions.indexOf(session)] =
                                        session.copy(status = OfSessionStatus.NO_SHOW)
                                    repo.post(OfDomain.SESSION, "Session ${session.id} no-show",
                                        "${session.clientName} missed the grace window.")
                                }
                            }
                        },
                        {
                            OfGhost("Cancel") {
                                if (session.walkIn) {
                                    repo.post(OfDomain.SESSION, "Walk-in rule held",
                                        "Session ${session.id} refused CANCELLED: walk-ins leave the queue instead.")
                                } else {
                                    repo.sessions[repo.sessions.indexOf(session)] =
                                        session.copy(status = OfSessionStatus.CANCELLED)
                                    repo.post(OfDomain.SESSION, "Session ${session.id} cancelled",
                                        "${session.clientName} cancelled ahead of cut-off.")
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                TextField(
                    value = voidReason,
                    onValueChange = { voidReason = it },
                    placeholder = { Text("Void / unvoid reason (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OfColors.Paper,
                        unfocusedContainerColor = OfColors.Paper,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                OfRowButtons(
                    {
                        if (!session.voided) {
                            OfGhost("Void") {
                                if (voidReason.isNotBlank()) {
                                    repo.sessions[repo.sessions.indexOf(session)] =
                                        session.copy(voided = true, voidReason = voidReason.trim())
                                    repo.audit("VOID", "Session ${session.id}", voidReason.trim())
                                    voidReason = ""
                                }
                            }
                        } else {
                            OfGhost("Unvoid") {
                                if (voidReason.isNotBlank()) {
                                    repo.sessions[repo.sessions.indexOf(session)] =
                                        session.copy(voided = false, voidReason = null)
                                    repo.audit("UNVOID", "Session ${session.id}", voidReason.trim())
                                    voidReason = ""
                                }
                            }
                        }
                    },
                    { OfLink("Collapse") { expanded = null } },
                )
            } else {
                OfLink("Manage") { expanded = session.id }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OfClients(repo: OpsFeedFakeRepo) {
    OfCard {
        OfSectionTitle("Client roster — global")
        Text(
            "Clients are global across branches. At most one PENDING session per client at a time.",
            color = OfColors.Muted, fontSize = 12.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    repo.clients.forEach { client ->
        val pending = repo.pendingForClient(client.id)
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (client.anonymized) "Client ${client.id.uppercase()}" else client.name,
                        color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (client.anonymized) "${client.gender} · age ${client.age} (masked)"
                        else "${client.gender} · age ${client.age} · ${client.id}",
                        color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono,
                    )
                }
                OfTag("$pending PENDING", if (pending > 1) OfColors.Bad else OfColors.Session)
                Spacer(Modifier.weight(0.05f))
                OfLink(if (client.anonymized) "Reveal" else "Mask") {
                    repo.clients[repo.clients.indexOf(client)] =
                        client.copy(anonymized = !client.anonymized)
                    repo.post(OfDomain.NOTE, "Client view ${if (client.anonymized) "revealed" else "masked"}",
                        "Anonymized view keeps gender + age only.")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
