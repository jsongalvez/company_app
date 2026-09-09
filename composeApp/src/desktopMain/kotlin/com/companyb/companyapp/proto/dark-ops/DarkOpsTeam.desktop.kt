package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsTeam(repo: DarkOpsRepo) {
    Column {
        OpsSectionHeader("team", "slot order · relief sorts last") {
            OpsBadge(repo.staff.size.toString() + " people", DarkOpsPalette.Cyan)
        }
        OpsPanel(modifier = Modifier.fillMaxWidth()) {
            TeamHead()
            repo.staff.sortedWith(compareBy({ it.relief }, { if (it.slot == 0) 99 else it.slot })).forEach { member ->
                OpsRow(selected = member.id == repo.currentUserId, onClick = { }) {
                    Text(
                        "slot " + member.slot.toString().padStart(2, '0'),
                        style = DarkOpsType.bodySmall,
                        color = DarkOpsPalette.Faint,
                        modifier = Modifier.width(64.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(member.name, style = DarkOpsType.bodyMedium)
                            if (member.relief) OpsBadge("RELIEF", DarkOpsPalette.Amber)
                            if (member.role == "ONBOARDING") OpsBadge("LOCKED", DarkOpsPalette.Red)
                        }
                        Text(member.role + " · home " + member.home, style = DarkOpsType.bodySmall)
                    }
                    OpsBadge(
                        if (member.clockedIn) "IN" else "OUT",
                        if (member.clockedIn) DarkOpsPalette.Phosphor else DarkOpsPalette.Faint,
                    )
                }
            }
        }
        Spacer(Modifier.height(OpsPadSm))
        RolePanel()
    }
}

@Composable
private fun TeamHead() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("roster", style = DarkOpsType.labelSmall, modifier = Modifier.weight(1f))
        Text("presence", style = DarkOpsType.labelSmall)
    }
}

@Composable
private fun RolePanel() {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("ROLES // capability bundles", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        RoleLine("MANAGER", "coordinator + user mgmt + delegates")
        RoleLine("Coordinator", "finance + PAST/REMITTED edits")
        RoleLine("Practitioner", "log sessions + inventory")
        RoleLine("Accountant", "read-only, all branches")
        RoleLine("ONBOARDING", "empty bundle — locked out")
        Spacer(Modifier.height(4.dp))
        OpsNote("runtime checks capabilities, never role names")
    }
}

@Composable
private fun RoleLine(
    role: String,
    blurb: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(role.padEnd(14), style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Cyan)
        Text(blurb, style = DarkOpsType.bodySmall)
    }
}
