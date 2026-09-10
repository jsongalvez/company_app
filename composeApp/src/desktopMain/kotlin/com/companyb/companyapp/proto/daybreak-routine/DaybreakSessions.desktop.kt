package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakSessions(repo: DaybreakRepo) {
    var filter by remember { mutableStateOf<DawnSessionStatus?>(null) }
    var newService by remember { mutableStateOf("Sunrise 45m") }
    val visible = repo.sessions.filter { filter == null || it.status == filter }
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Sessions", "${repo.sessions.size} on ${repo.currentDay.date}") {
            OutlinedButton(onClick = { repo.createOpen = true }) { Text("+ Book") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { filter = null }) { Text("ALL") }
            DawnSessionStatus.entries.forEach { status ->
                OutlinedButton(onClick = { filter = status }) { Text(status.label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        DawnNote("Walk-in Sessions cannot be NO_SHOW or CANCELLED: they either happen or never existed.")
        Spacer(Modifier.height(8.dp))
        visible.forEach { session ->
            DawnPanel(
                modifier = Modifier.fillMaxWidth().clickable { repo.detailSessionId = session.id },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.time + " " + session.clientName, style = DaybreakType.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    DawnBadge(session.status.label, dawnStatusColor(session.status))
                    if (session.walkIn) {
                        Spacer(Modifier.width(6.dp))
                        DawnBadge("WALK-IN", DaybreakPalette.Sky)
                    }
                    if (session.voided) {
                        Spacer(Modifier.width(6.dp))
                        DawnBadge("VOID", DaybreakPalette.Rose)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(dawnPeso(session.price), style = DaybreakType.labelMedium)
                }
                Text(session.service + " · " + session.practitioner, style = DaybreakType.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
        }
        val detail = repo.sessions.firstOrNull { it.id == repo.detailSessionId }
        if (detail != null) SessionDetail(repo, detail)
        if (repo.createOpen) {
            var clientPick by remember { mutableStateOf(repo.clients.first().id) }
            AlertDialog(
                onDismissRequest = { repo.createOpen = false },
                title = { Text("Book a Session") },
                text = {
                    Column {
                        Text("Client: $clientPick (global, at most one PENDING each).", style = DaybreakType.bodySmall)
                        OutlinedTextField(
                            value = newService,
                            onValueChange = { newService = it },
                            label = { Text("Service") },
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val client = repo.clients.first { it.id == clientPick }
                            repo.createSession(client, false, newService, 900)
                            repo.createOpen = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Sunrise),
                    ) { Text("Book") }
                },
                dismissButton = { TextButton(onClick = { repo.createOpen = false }) { Text("Close") } },
            )
        }
        val voidTarget = repo.sessions.firstOrNull { it.id == repo.voidTargetId }
        if (voidTarget != null) {
            AlertDialog(
                onDismissRequest = { repo.voidTargetId = null },
                title = { Text("Void ${voidTarget.id} with reason") },
                text = {
                    OutlinedTextField(
                        value = repo.voidReason,
                        onValueChange = { repo.voidReason = it },
                        label = { Text("Reason") },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repo.voidSession(voidTarget.id, repo.voidReason.ifBlank { "operator error" })
                            repo.voidTargetId = null
                            repo.voidReason = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Rose),
                    ) { Text("Void") }
                },
                dismissButton = { TextButton(onClick = { repo.voidTargetId = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun SessionDetail(
    repo: DaybreakRepo,
    session: DawnSession,
) {
    DawnPanel(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        DawnSectionHeader("Detail " + session.id, session.clientName + " · " + session.time)
        DawnRow("Service", session.service)
        DawnRow("Practitioner", session.practitioner)
        DawnRow("Price", dawnPeso(session.price))
        DawnRow("Walk-in", if (session.walkIn) "YES: NO_SHOW/CANCELLED refused" else "booked")
        if (session.voided) DawnRow("Void reason", session.voidReason)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { repo.setStatus(session.id, DawnSessionStatus.COMPLETED) }) { Text("Complete") }
            OutlinedButton(
                onClick = { repo.setStatus(session.id, DawnSessionStatus.NO_SHOW) },
                enabled = !session.walkIn,
            ) { Text("No-show") }
            OutlinedButton(
                onClick = { repo.setStatus(session.id, DawnSessionStatus.CANCELLED) },
                enabled = !session.walkIn,
            ) { Text("Cancel") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!session.voided) {
                OutlinedButton(onClick = { repo.voidTargetId = session.id }) { Text("Void w/ reason") }
            } else {
                OutlinedButton(onClick = { repo.unvoidSession(session.id) }) { Text("Unvoid") }
            }
            OutlinedButton(onClick = { repo.detailSessionId = null }) { Text("Close") }
        }
        if (session.walkIn) DawnNote("Rule note: walk-in ${session.id} refuses NO_SHOW/CANCELLED in this build.")
        if (repo.dayLocked) DawnNote("Day ${repo.currentDay.state.label}: edits need Coordinator or MANAGER.")
    }
}
