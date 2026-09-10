package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SeaProfile(repo: SeaRepo) {
    SectionTitle("Diver badge", "profile · capabilities · clock-out · logout")
    LagoonCard {
        Text(repo.currentUser.name.uppercase(), style = SeaType.displaySmall)
        Spacer(Modifier.height(4.dp))
        TideChip(repo.currentUser.role, SeaPalette.Coral)
        Spacer(Modifier.height(SeaPadSm))
        Text("CAPABILITIES", style = SeaType.titleSmall)
        if (repo.currentUser.capabilities.isEmpty()) {
            Text("none — badgeless water", style = SeaType.bodyMedium, color = SeaPalette.Mist)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repo.currentUser.capabilities.forEach { capability ->
                    Text("≈ " + capability, style = SeaType.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(SeaPadSm))
        Text("Home: " + repo.currentBranch.name + " · " + repo.currentBranch.kind, style = SeaType.bodySmall)
        Text(
            if (repo.clockedIn) "Status: clocked in" else "Status: beached",
            style = SeaType.bodySmall,
        )
    }
    Spacer(Modifier.height(SeaPadMd))
    Row(horizontalArrangement = Arrangement.spacedBy(SeaPadSm)) {
        OutlinedButton(
            onClick = {
                repo.clockedIn = false
                repo.appendLog(repo.actor(), "CLOCK_OUT", repo.currentUser.name + " clocked out")
            },
            enabled = repo.clockedIn,
        ) {
            Text("Clock out", style = SeaType.labelMedium, color = SeaPalette.SunBuoy)
        }
        OutlinedButton(onClick = {
            repo.authed = false
            repo.branchPicked = false
            repo.appendLog(repo.actor(), "LOGOUT", repo.currentUser.name + " surfaced")
        }) {
            Text("Log out", style = SeaType.labelMedium, color = SeaPalette.Siren)
        }
    }
    Spacer(Modifier.height(4.dp))
    NoteLine("logging out returns to the shore gate; the tide board keeps its fake state")
}
