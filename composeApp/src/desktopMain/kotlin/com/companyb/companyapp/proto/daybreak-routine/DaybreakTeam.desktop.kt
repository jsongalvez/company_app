package com.companyb.companyapp.proto.daybreakroutine

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
internal fun DaybreakTeam(repo: DaybreakRepo) {
    val ordered = repo.staff.sortedWith(compareBy({ it.relief }, { if (it.slot == 0) 99 else it.slot }))
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Team", "slot order · relief sorts last")
        DawnNote(
            "Slot 1 = senior. Relief Practitioners sort after home slots. " +
                "ONBOARDING row stays locked with an empty bundle.",
        )
        Spacer(Modifier.height(8.dp))
        ordered.forEach { member ->
            DawnPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${member.slot} " + member.name + " · " + member.role, style = DaybreakType.titleMedium)
                        Text(
                            "home " + member.home + (if (member.relief) " · RELIEF" else ""),
                            style = DaybreakType.bodySmall,
                        )
                    }
                    if (member.clockedIn) {
                        DawnBadge(
                            "IN",
                            DaybreakPalette.Sage,
                        )
                    } else {
                        DawnBadge("OUT", DaybreakPalette.Faint)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (member.relief && !member.clockedIn) {
                        OutlinedButton(onClick = { repo.grantRelief(member.id) }) { Text("Grant") }
                    }
                }
                if (member.role == "ONBOARDING") {
                    Text("Locked: empty role bundle, nothing derives.", style = DaybreakType.bodySmall)
                } else {
                    Text(roleBlurb(member.role), style = DaybreakType.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        DawnPanel(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Clock crew now", style = DaybreakType.titleMedium)
            Text(
                repo.clockCrew().joinToString(", ") { it.name }.ifBlank { "nobody clocked in" },
                style = DaybreakType.bodyMedium,
            )
            Text("Commission pool splits across this crew.", style = DaybreakType.bodySmall)
        }
    }
}

private fun roleBlurb(role: String): String =
    when (role) {
        "MANAGER" -> "Superset of Coordinator: users, delegates, finance."
        "Coordinator" -> "Finance + remittance; sole editor of PAST/REMITTED."
        "Practitioner" -> "Logs Sessions, reads Clients, holds home + checked-in Branches."
        else -> "Read-only across Branches."
    }
