package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NoirProfile(repo: NoirRepo) {
    FolderTab(title = "The badge", right = repo.currentUser.name) {}
    Column(verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
        CaseFolder(Modifier.fillMaxWidth()) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(repo.currentUser.name, style = NoirType.titleLarge)
                    Text(
                        repo.currentUser.role + " · " + repo.currentBranch.name,
                        style = NoirType.bodyMedium,
                        color = NoirPalette.Dim,
                    )
                }
                CaseStamp(repo.currentUser.role, NoirPalette.LampAmber)
            }
            Spacer(Modifier.height(NoirPadSm))
            Text("CAPABILITIES ON THE BADGE", style = NoirType.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (repo.currentUser.capabilities.isEmpty()) {
                Text("Empty bundle — badgeless.", style = NoirType.bodyMedium, color = NoirPalette.SirenRed)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repo.currentUser.capabilities.forEach { capability ->
                        NoirBadge(capability, NoirPalette.NeonBlue)
                    }
                }
            }
        }
        CaseFolder(Modifier.fillMaxWidth()) {
            Text("END OF SHIFT", style = NoirType.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Clock out to end the shift; log out to hand the desk to the next operative.",
                style = NoirType.bodyMedium,
                color = NoirPalette.Dim,
            )
            Spacer(Modifier.height(NoirPadSm))
            Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                OutlinedButton(onClick = {
                    repo.clockedIn = false
                    repo.appendLog(repo.actor(), "CLOCK_OUT", repo.currentUser.name + " clocked out")
                }) {
                    Text("Clock out", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
                }
                Button(
                    onClick = {
                        repo.appendLog(repo.actor(), "LOGOUT", repo.currentUser.name + " left the desk")
                        repo.authed = false
                        repo.branchPicked = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.SirenRed),
                ) {
                    Text("Log out", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                }
            }
        }
    }
}
