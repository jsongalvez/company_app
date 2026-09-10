package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val RushFilters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED")

@Composable
internal fun LunchRushSessions(repo: LunchRushRepo) {
    RushSectionHeader("Ticket rail", "${repo.pendingSessions.size} pending on the rail") {
        OutlinedButton(onClick = { repo.createOpen = true }) { Text("+ Book ticket") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RushFilters.forEach { filter ->
            val active = repo.sessionFilter == filter
            if (active) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Wok),
                ) { Text(filter) }
            } else {
                OutlinedButton(onClick = { repo.sessionFilter = filter }) { Text(filter) }
            }
        }
    }
    Spacer(Modifier.height(RushPadMd))
    val visible =
        repo.sessions.filter { session ->
            when (repo.sessionFilter) {
                "ALL" -> true
                "VOIDED" -> session.voided
                else -> !session.voided && session.status.name == repo.sessionFilter
            }
        }
    if (visible.isEmpty()) {
        RushNote("Rail is clear for this filter. Book a ticket or switch filters.")
    }
    visible.forEach { session ->
        val selected = repo.selectedSessionId == session.id
        RushTicketCard(hot = selected, onClick = { repo.selectedSessionId = session.id }) {
            RushRow(
                left = {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.time, style = LunchRushType.labelLarge)
                            Text(session.id, style = LunchRushType.labelSmall)
                            if (session.walkIn) RushBadge("WALK-IN", LunchRushPalette.Steam)
                            if (session.voided) RushBadge("VOIDED", LunchRushPalette.Char)
                        }
                        Text("${session.clientName} — ${session.service}", style = LunchRushType.titleMedium)
                        Text(
                            "${session.practitioner} — ${peso(session.price)}",
                            style = LunchRushType.bodySmall,
                        )
                    }
                },
                right = {
                    if (!session.voided) RushBadge(session.status.label, statusColor(session.status))
                },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(RushPadMd))
    val detail = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
    if (detail != null) {
        RushTicketCard(hot = true) {
            Text("TICKET ${detail.id}", style = LunchRushType.labelMedium)
            Text("${detail.clientName} — ${detail.service}", style = LunchRushType.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Status ${detail.status.label} — ${if (detail.walkIn) "walk-in" else "booked"} — ${peso(detail.price)}",
                style = LunchRushType.bodyMedium,
            )
            if (detail.voided) Text("Void reason: ${detail.voidReason}", style = LunchRushType.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { repo.setStatus(detail.id, RushSessionStatus.COMPLETED) },
                    enabled = !detail.voided && detail.status != RushSessionStatus.COMPLETED,
                ) { Text("Complete") }
                OutlinedButton(
                    onClick = { repo.setStatus(detail.id, RushSessionStatus.NO_SHOW) },
                    enabled = !detail.voided && !detail.walkIn && detail.status == RushSessionStatus.PENDING,
                ) { Text("No-show") }
                OutlinedButton(
                    onClick = { repo.setStatus(detail.id, RushSessionStatus.CANCELLED) },
                    enabled = !detail.voided && !detail.walkIn && detail.status == RushSessionStatus.PENDING,
                ) { Text("Cancel") }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!detail.voided) {
                    OutlinedButton(onClick = { repo.voidTargetId = detail.id }) { Text("Void with reason") }
                } else {
                    OutlinedButton(onClick = { repo.unvoidSession(detail.id) }) { Text("Unvoid") }
                }
            }
            Spacer(Modifier.height(8.dp))
            RushNote("Walk-in rule: walk-in tickets cannot take NO_SHOW or CANCELLED — they were never booked. Void with reason stays available for every ticket.")
        }
    }
    if (repo.voidTargetId != null) {
        AlertDialog(
            onDismissRequest = { repo.voidTargetId = null },
            title = { Text("Void ${repo.voidTargetId}?") },
            text = {
                Column {
                    Text("A reason is required — it prints on the audit pass.", style = LunchRushType.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repo.voidReason,
                        onValueChange = { repo.voidReason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = repo.voidTargetId
                        if (target != null) repo.voidSession(target, repo.voidReason.ifBlank { "rush error" })
                        repo.voidTargetId = null
                        repo.voidReason = ""
                    },
                ) { Text("Void ticket") }
            },
            dismissButton = { TextButton(onClick = { repo.voidTargetId = null }) { Text("Keep") } },
        )
    }
    if (repo.createOpen) {
        AlertDialog(
            onDismissRequest = { repo.createOpen = false },
            title = { Text("Book a ticket") },
            text = {
                Column {
                    OutlinedTextField(
                        value = repo.createName,
                        onValueChange = { repo.createName = it },
                        label = { Text("Client name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repo.createService,
                        onValueChange = { repo.createService = it },
                        label = { Text("Service") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Checkbox(
                            checked = repo.createWalkIn,
                            onCheckedChange = { repo.createWalkIn = it },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Walk-in (no NO_SHOW/CANCELLED later)")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.createSession(
                            repo.createName.ifBlank { "Walk-in Guest" },
                            repo.createWalkIn,
                            repo.createService,
                        )
                        repo.createOpen = false
                        repo.createName = ""
                    },
                ) { Text("Book") }
            },
            dismissButton = { TextButton(onClick = { repo.createOpen = false }) { Text("Close") } },
        )
    }
}
