package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SeaMailbox(repo: SeaRepo) {
    SectionTitle("Signal buoy", "notifications mailbox · read / unread")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("BUOY BOX", style = SeaType.titleSmall, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { repo.markAllRead() }) {
            Text("Drain all", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
        }
    }
    Spacer(Modifier.height(SeaPadSm))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repo.buoy.forEach { signal ->
            DriftRow(selected = false, onClick = { repo.markRead(signal.id) }) {
                Column(Modifier.weight(1f)) {
                    Text(signal.title, style = SeaType.titleMedium)
                    Text(signal.body, style = SeaType.bodySmall)
                    Text(signal.kind + " · " + signal.dayRef, style = SeaType.bodySmall, color = SeaPalette.Faint)
                }
                if (!signal.read) {
                    TideChip("new", SeaPalette.Coral)
                } else {
                    TideChip("read", SeaPalette.Faint)
                }
            }
        }
    }
    Spacer(Modifier.height(SeaPadMd))
    SectionTitle("Harbor log", "audit trail · newest first · every mutation lands here")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repo.harborLog.forEach { entry ->
            LagoonCard {
                Text(
                    "#" + entry.seq.toString() + " · " + entry.clock + " · " + entry.actor,
                    style = SeaType.titleSmall,
                )
                Text(entry.action, style = SeaType.labelLarge, color = SeaPalette.Coral)
                Text(entry.detail, style = SeaType.bodySmall)
            }
        }
    }
}
