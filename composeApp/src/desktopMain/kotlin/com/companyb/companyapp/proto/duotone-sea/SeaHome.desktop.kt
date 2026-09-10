package com.companyb.companyapp.proto.duotonesea

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
internal fun SeaHome(repo: SeaRepo) {
    SectionTitle("Lagoon", "clock-in home · relief duty · invite · request")
    TideNumbers(repo)
    Spacer(Modifier.height(SeaPadMd))
    ClockCard(repo)
    Spacer(Modifier.height(SeaPadMd))
    ReliefDutyCard(repo)
    Spacer(Modifier.height(SeaPadMd))
    ReliefInviteCard(repo)
    Spacer(Modifier.height(SeaPadMd))
    ReliefRequestCard(repo)
}

@Composable
private fun TideNumbers(repo: SeaRepo) {
    Row(horizontalArrangement = Arrangement.spacedBy(SeaPadSm), modifier = Modifier.fillMaxWidth()) {
        LagoonCard(Modifier.weight(1f)) {
            Text(repo.pendingCount.toString(), style = SeaType.displaySmall)
            Text("pending swims", style = SeaType.bodySmall)
        }
        LagoonCard(Modifier.weight(1f)) {
            Text(repo.unreadCount.toString(), style = SeaType.displaySmall)
            Text("unread signals", style = SeaType.bodySmall)
        }
        LagoonCard(Modifier.weight(1f)) {
            Text("₱" + repo.sessionNet().toString(), style = SeaType.displaySmall)
            Text("session net", style = SeaType.bodySmall)
        }
    }
}

@Composable
private fun ClockCard(repo: SeaRepo) {
    LagoonCard {
        Text("TODAY AT " + repo.currentBranch.name.uppercase(), style = SeaType.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            if (repo.clockedIn) "Clocked in — riding the " + repo.currentDay.date + " tide." else "Beached — clock in to start the day.",
            style = SeaType.titleMedium,
        )
        Spacer(Modifier.height(SeaPadSm))
        Row(horizontalArrangement = Arrangement.spacedBy(SeaPadSm)) {
            if (repo.clockedIn) {
                OutlinedButton(onClick = {
                    repo.clockedIn = false
                    repo.appendLog(repo.actor(), "CLOCK_OUT", repo.currentUser.name + " clocked out")
                }) {
                    Text("Clock out", style = SeaType.labelMedium, color = SeaPalette.Coral)
                }
            } else {
                Button(
                    onClick = {
                        repo.clockedIn = true
                        repo.appendLog(repo.actor(), "CLOCK_IN", repo.currentUser.name + " clocked in")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.SeaGlass),
                ) {
                    Text("Clock in", style = SeaType.labelLarge, color = SeaPalette.Abyss)
                }
            }
        }
        if (repo.dayLocked) {
            Spacer(Modifier.height(4.dp))
            NoteLine("day is " + repo.currentDay.state.label + " — edits locked for " + repo.currentUser.role)
        }
    }
}

@Composable
private fun ReliefDutyCard(repo: SeaRepo) {
    val relief = repo.crew.firstOrNull { it.id == "u-relief" }
    LagoonCard {
        Text("RELIEF DUTY", style = SeaType.titleSmall)
        Text(
            "P. Gomez · Cebu Crossing · " + if (relief?.clockedIn == true) "on deck" else "awaiting grant",
            style = SeaType.titleMedium,
        )
        Spacer(Modifier.height(SeaPadSm))
        OutlinedButton(
            onClick = { repo.grantRelief() },
            enabled = relief?.clockedIn != true,
        ) {
            Text("Grant relief edit", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
        }
        Spacer(Modifier.height(4.dp))
        NoteLine("grants are logged to the harbor log with actor + clock")
    }
}

@Composable
private fun ReliefInviteCard(repo: SeaRepo) {
    LagoonCard {
        Text("RELIEF INVITE", style = SeaType.titleSmall)
        if (repo.reliefInviteOpen) {
            Text("Cebu Crossing needs cover on 2026-09-10.", style = SeaType.titleMedium)
            Spacer(Modifier.height(SeaPadSm))
            Row(horizontalArrangement = Arrangement.spacedBy(SeaPadSm)) {
                Button(
                    onClick = { repo.acceptInvite() },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.Kelp),
                ) {
                    Text("Accept", style = SeaType.labelLarge, color = SeaPalette.Abyss)
                }
                OutlinedButton(onClick = { repo.declineInvite() }) {
                    Text("Decline", style = SeaType.labelMedium, color = SeaPalette.Siren)
                }
            }
        } else {
            Text("Invite answered — the buoy box holds the record.", style = SeaType.bodyMedium)
        }
    }
}

@Composable
private fun ReliefRequestCard(repo: SeaRepo) {
    LagoonCard {
        Text("REQUEST COVER", style = SeaType.titleSmall)
        Text(
            if (repo.reliefRequested) "Request sent — watch the buoy box." else "Ask the crew to cover your next tide.",
            style = SeaType.titleMedium,
        )
        Spacer(Modifier.height(SeaPadSm))
        OutlinedButton(onClick = { repo.requestRelief() }, enabled = !repo.reliefRequested) {
            Text("Send relief request", style = SeaType.labelMedium, color = SeaPalette.Coral)
        }
    }
}
