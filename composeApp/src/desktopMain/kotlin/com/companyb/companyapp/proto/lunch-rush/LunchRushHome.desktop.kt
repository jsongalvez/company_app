package com.companyb.companyapp.proto.lunchrush

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
internal fun LunchRushHome(repo: LunchRushRepo) {
    RushSectionHeader("Rush board", "${repo.currentBranch.name} — ${repo.currentDay.date} ${repo.currentDay.state.label}")
    RushMeter(level = repo.rushLevel)
    Spacer(Modifier.height(RushPadMd))

    if (repo.bottleneckFlags.isEmpty()) {
        RushNote("Queue is calm. No bottleneck flags raised — keep the tickets moving.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.bottleneckFlags.forEach { RushFlag(it) }
        }
    }
    Spacer(Modifier.height(RushPadMd))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            RushTicketCard(hot = !repo.clockedIn) {
                Text("CLOCK", style = LunchRushType.labelMedium)
                Text(
                    if (repo.clockedIn) "You are on the floor." else "You are off the floor.",
                    style = LunchRushType.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repo.toggleClock() },
                        colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Leaf),
                    ) {
                        Text(if (repo.clockedIn) "CLOCK OUT" else "CLOCK IN")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Today: ${repo.pendingSessions.size} pending, ${repo.clockedCrew} crew in, ${repo.unreadCount} unread.", style = LunchRushType.bodySmall)
            }
        }
        Column(Modifier.weight(1f)) {
            RushTicketCard(hot = true) {
                Text("RELIEF CALL", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
                Text("Ring the bell for backup hands", style = LunchRushType.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Broadcasts to off-floor crew for 12:30-15:00 cover.", style = LunchRushType.bodySmall)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { repo.callRelief() },
                    enabled = !repo.reliefCallSent,
                    colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Char),
                ) {
                    Text(if (repo.reliefCallSent) "RELIEF CALLED — HELP IS COMING" else "RING RELIEF BELL")
                }
            }
        }
    }
    Spacer(Modifier.height(RushPadMd))

    RushTicketCard {
        Text("RELIEF DUTY", style = LunchRushType.labelMedium)
        val invite = repo.staff.firstOrNull { it.relief && !it.clockedIn }
        if (invite == null) {
            Text("Nobody waiting on relief. Floor is staffed by the home crew.", style = LunchRushType.bodyMedium)
        } else {
            RushRow(
                left = {
                    Column {
                        Text("${invite.name} offered cover", style = LunchRushType.titleMedium)
                        Text("${invite.role} — home ${invite.home}", style = LunchRushType.bodySmall)
                    }
                },
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repo.grantRelief(invite.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Leaf),
                        ) { Text("ACCEPT") }
                        OutlinedButton(onClick = { repo.screen = RushScreen.TEAM }) { Text("Review crew") }
                    }
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        RushNote("Relief request card: Cebu van crew covers Makati 12:30-15:00. Relief hands sort last on the roster and get floor edit rights on accept.")
    }
    Spacer(Modifier.height(RushPadMd))

    RushNote(
        "Surge pricing note: when the rush meter hits 5+, walk-in Express tickets bill a 10% peak surcharge at the till. " +
            "Booked regulars keep menu price — the surcharge never touches the commission split.",
    )
}
