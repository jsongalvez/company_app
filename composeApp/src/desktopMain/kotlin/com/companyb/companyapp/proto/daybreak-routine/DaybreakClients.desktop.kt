package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakClients(repo: DaybreakRepo) {
    val pending = repo.pendingClientIds()
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Clients", "global registry · ${repo.clients.size}") {
            if (pending.isNotEmpty()) DawnBadge("* ${pending.size} PENDING", DaybreakPalette.Gold)
        }
        DawnNote(
            "Global record, at most one PENDING Session per Client. " +
                "Star marks the live one. Anonymize keeps gender/age, nulls PII.",
        )
        Spacer(Modifier.height(8.dp))
        repo.clients.forEach { client ->
            DawnPanel(
                modifier = Modifier.fillMaxWidth().clickable { repo.detailClientId = client.id },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (pending.contains(client.id)) "* " else "") + client.name,
                        style = DaybreakType.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (client.anonymized) DawnBadge("ANON", DaybreakPalette.Violet)
                    Spacer(Modifier.weight(1f))
                    Text(client.gender + " · " + client.age, style = DaybreakType.labelMedium)
                }
                Text(client.phone, style = DaybreakType.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
        }
        val detail = repo.clients.firstOrNull { it.id == repo.detailClientId }
        if (detail != null) {
            DawnPanel(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                DawnSectionHeader("Client " + detail.id, detail.name)
                DawnRow("Gender", detail.gender)
                DawnRow("Age", detail.age.toString())
                DawnRow("Phone", detail.phone)
                DawnRow("Live PENDING", if (pending.contains(detail.id)) "YES *" else "none")
                Row {
                    OutlinedButton(onClick = { repo.anonymizeClient(detail.id) }, enabled = !detail.anonymized) {
                        Text(if (detail.anonymized) "Anonymized view" else "Anonymize")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { repo.detailClientId = null }) { Text("Close") }
                }
                if (detail.anonymized) {
                    DawnNote(
                        "Anonymized view: name replaced, phone withheld, gender/age kept for reporting.",
                    )
                }
            }
        }
    }
}
