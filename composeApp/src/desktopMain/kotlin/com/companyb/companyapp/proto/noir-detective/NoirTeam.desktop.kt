package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NoirTeam(repo: NoirRepo) {
    FolderTab(title = "The squad room", right = "slot order · relief sorts last") {}
    Column(verticalArrangement = Arrangement.spacedBy(NoirPadSm)) {
        CaseFolder(Modifier.fillMaxWidth()) {
            val ordered = repo.squad.sortedWith(compareBy({ it.relief }, { if (it.slot == 0) 99 else it.slot }))
            ordered.forEach { member ->
                FileRow(selected = false, onClick = {}) {
                    Text(
                        if (member.slot == 0) "—" else member.slot.toString().padStart(2, '0'),
                        style = NoirType.bodyMedium,
                        color = NoirPalette.LampAmber,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(member.name, style = NoirType.bodyLarge)
                            if (member.relief) NoirBadge("RELIEF", NoirPalette.NeonBlue)
                            if (!member.clockedIn && member.role != "ONBOARDING") {
                                NoirBadge("OFF DUTY", NoirPalette.Dim)
                            }
                        }
                        Text(
                            member.role + " · home " + member.home,
                            style = NoirType.bodySmall,
                            color = NoirPalette.Dim,
                        )
                    }
                    RoleInk(member.role)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        DeskLampNote("ONBOARDING carries an empty capability bundle — badgeless until MANAGE_USERS speaks.")
    }
}

@Composable
private fun RoleInk(role: String) {
    val color =
        when (role) {
            "MANAGER" -> NoirPalette.LampAmber
            "Coordinator" -> NoirPalette.NeonBlue
            "Practitioner" -> NoirPalette.EvidenceGreen
            "Accountant" -> NoirPalette.Dim
            else -> NoirPalette.SirenRed
        }
    CaseStamp(role, color)
}
