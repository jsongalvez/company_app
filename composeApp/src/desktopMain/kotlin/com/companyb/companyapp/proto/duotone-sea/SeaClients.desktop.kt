package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SeaClients(repo: SeaRepo) {
    SectionTitle("Drift registry", "global clients · at most one PENDING swim each")
    val pendingIds = repo.pendingDrifterIds()
    NoteLine("global registry — every branch fishes the same waters")
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repo.drifters.forEach { drifter ->
            DriftRow(selected = drifter.id == repo.selectedDrifterId, onClick = { repo.selectedDrifterId = drifter.id }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (drifter.anonymized) "REDACTED " + drifter.id.uppercase() else drifter.name,
                        style = SeaType.titleMedium,
                    )
                    Text(
                        drifter.gender + " · " + drifter.age.toString() + "y · " + drifter.phone,
                        style = SeaType.bodySmall,
                    )
                }
                if (drifter.id in pendingIds) {
                    TideChip("★ pending", SeaPalette.SunBuoy)
                }
                Spacer(Modifier.width(SeaPadSm))
                if (drifter.anonymized) TideChip("veiled", SeaPalette.SeaGlass)
            }
        }
    }
    Spacer(Modifier.height(SeaPadMd))
    DrifterDetail(repo, pendingIds)
}

@Composable
private fun DrifterDetail(
    repo: SeaRepo,
    pendingIds: Set<String>,
) {
    val drifter = repo.drifters.firstOrNull { it.id == repo.selectedDrifterId } ?: return
    LagoonCard {
        Text("DRIFTER " + drifter.id.uppercase(), style = SeaType.titleSmall)
        Text(
            if (drifter.anonymized) "REDACTED " + drifter.id.uppercase() else drifter.name,
            style = SeaType.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text("Gender: " + drifter.gender + " · Age: " + drifter.age.toString(), style = SeaType.bodyMedium)
        Text("Phone: " + drifter.phone, style = SeaType.bodyMedium)
        if (drifter.id in pendingIds) {
            Spacer(Modifier.height(4.dp))
            Text("★ rides one live PENDING swim", style = SeaType.bodySmall, color = SeaPalette.SunBuoy)
        }
        Spacer(Modifier.height(4.dp))
        Text("Swim history", style = SeaType.titleSmall)
        val history = repo.swims.filter { it.clientId == drifter.id }
        if (history.isEmpty()) {
            Text("no swims logged yet", style = SeaType.bodySmall, color = SeaPalette.Mist)
        } else {
            history.forEach { swim ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(swim.time + " " + swim.service, style = SeaType.bodySmall, modifier = Modifier.weight(1f))
                    StatusFoam(swim.status, swim.voided)
                }
            }
        }
        Spacer(Modifier.height(SeaPadSm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { repo.anonymizeDrifter(drifter.id) }, enabled = !drifter.anonymized) {
                Text("Anonymize", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
            }
        }
        Spacer(Modifier.height(4.dp))
        NoteLine("anonymizing nulls PII but keeps gender and age for reporting")
    }
}
