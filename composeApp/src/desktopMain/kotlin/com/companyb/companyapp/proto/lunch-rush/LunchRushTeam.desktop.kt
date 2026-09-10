package com.companyb.companyapp.proto.lunchrush

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
internal fun LunchRushTeam(repo: LunchRushRepo) {
    RushSectionHeader("Crew", "${repo.clockedCrew} on the floor — relief sorts last")
    val ordered = repo.staff.sortedWith(compareBy({ it.relief }, { it.slot }))
    ordered.forEach { member ->
        RushTicketCard(hot = member.relief && !member.clockedIn) {
            RushRow(
                left = {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("#${member.slot} ${member.name}", style = LunchRushType.titleMedium)
                            if (member.relief) RushBadge("RELIEF", LunchRushPalette.Steam)
                            if (member.role == "ONBOARDING") RushBadge("LOCKED", LunchRushPalette.Char)
                        }
                        Text("${member.role} — home ${member.home}", style = LunchRushType.bodySmall)
                    }
                },
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RushBadge(
                            if (member.clockedIn) "IN" else "OUT",
                            if (member.clockedIn) LunchRushPalette.Leaf else LunchRushPalette.Faint,
                        )
                        if (member.relief && !member.clockedIn) {
                            OutlinedButton(onClick = { repo.grantRelief(member.id) }) { Text("Call in") }
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(RushPadMd))
    RushNote(
        "Role bundles: Coordinator edits sessions + submits remittance; MANAGER adds undo + user manage; " +
            "Practitioner writes sessions; ONBOARDING holds no capabilities until cleared.",
    )
}
