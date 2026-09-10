package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LunchRushClients(repo: LunchRushRepo) {
    RushSectionHeader("Regulars", "global registry — every counter sees the same book")
    RushNote("At most one PENDING ticket per client: the starred row below holds the live ticket. Double-books raise a bottleneck flag on the rush board.")
    Spacer(Modifier.height(RushPadMd))
    val pendingByClient = repo.pendingSessions.groupBy { it.clientId }
    Column(Modifier.fillMaxWidth()) {
        repo.clients.forEach { client ->
            val selected = repo.selectedClientId == client.id
            RushTicketCard(hot = selected, onClick = { repo.selectedClientId = client.id }) {
                RushRow(
                    left = {
                        Column {
                            Row {
                                Text(client.name, style = LunchRushType.titleMedium)
                                if (pendingByClient.containsKey(client.id)) {
                                    Text("  * PENDING", style = LunchRushType.labelMedium, color = LunchRushPalette.Turmeric)
                                }
                            }
                            Text(
                                "${client.gender} / ${client.age} — ${client.phone}",
                                style = LunchRushType.bodySmall,
                            )
                        }
                    },
                    right = {
                        if (client.anonymized) RushBadge("ANONYMIZED", LunchRushPalette.Steam)
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(RushPadMd))
    val detail = repo.clients.firstOrNull { it.id == repo.selectedClientId }
    if (detail != null) {
        RushTicketCard(hot = true) {
            Text("REGULAR ${detail.id}", style = LunchRushType.labelMedium)
            Text(detail.name, style = LunchRushType.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Gender ${detail.gender} — age ${detail.age}", style = LunchRushType.bodyMedium)
            Text("Contact: ${detail.phone}", style = LunchRushType.bodyMedium)
            val tickets = repo.sessions.filter { it.clientId == detail.id }
            Text("Tickets on record: ${tickets.size}", style = LunchRushType.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = { repo.anonymizeClient(detail.id) },
                    enabled = !detail.anonymized,
                ) { Text(if (detail.anonymized) "Anonymized" else "Anonymize view") }
            }
            Spacer(Modifier.height(8.dp))
            RushNote("Anonymized view nullifies name + contact and keeps gender/age for reporting. The Client stays global — anonymizing here anonymizes every counter.")
        }
    }
}
