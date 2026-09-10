package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SeaTeam(repo: SeaRepo) {
    SectionTitle("Crew", "team · users · roles — relief sorts last")
    val ordered = repo.crew.sortedWith(compareBy({ it.relief }, { it.slot }))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { sailor ->
            DriftRow(selected = false, onClick = {}) {
                Column(Modifier.weight(1f)) {
                    Text(sailor.name, style = SeaType.titleMedium)
                    Text(
                        sailor.role + " · slot " + sailor.slot.toString() + " · " + sailor.home,
                        style = SeaType.bodySmall,
                    )
                }
                if (sailor.role == "ONBOARDING") {
                    Text("badgeless", style = SeaType.bodySmall, color = SeaPalette.Faint)
                } else {
                    TideChip(sailor.role, SeaPalette.SeaGlass)
                }
                Spacer(Modifier.width(SeaPadSm))
                if (sailor.relief) {
                    TideChip("relief", SeaPalette.SunBuoy)
                } else if (sailor.clockedIn) {
                    TideChip("in", SeaPalette.Kelp)
                } else {
                    TideChip("out", SeaPalette.Faint)
                }
            }
        }
    }
    Spacer(Modifier.height(SeaPadSm))
    NoteLine("ONBOARDING crew sail badgeless with zero capabilities until badged")
    NoteLine("relief crew always sort last regardless of slot")
}
