package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakProfile(repo: DaybreakRepo) {
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Profile", repo.currentUser.name)
        DawnPanel(Modifier.fillMaxWidth()) {
            Text(repo.currentUser.name + " · " + repo.currentUser.role, style = DaybreakType.titleLarge)
            Text(
                "Serving ${repo.currentBranch.name} on ${repo.currentDay.date} ${repo.currentDay.state.label}.",
                style = DaybreakType.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text("Capabilities", style = DaybreakType.titleSmall)
            if (repo.currentUser.capabilities.isEmpty()) {
                Text(
                    "EMPTY bundle: ONBOARDING cannot act until MANAGE_USERS grants a role.",
                    style = DaybreakType.bodySmall,
                )
            } else {
                repo.currentUser.capabilities.forEach { cap -> DawnRow(cap, "GRANTED") }
            }
            Spacer(Modifier.height(6.dp))
            DawnRow("Clock", if (repo.clockedIn) "IN at ${repo.currentBranch.name}" else "OUT")
            DawnRow("Opener", "${repo.ritualStepsDone}/4 steps")
        }
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedButton(onClick = { repo.toggleClock() }) {
                Text(if (repo.clockedIn) "Clock out" else "Clock in")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { repo.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Ink),
            ) { Text("Logout") }
        }
        Spacer(Modifier.height(8.dp))
        DawnNote(
            "Logout returns to the key rack. Clock-out closes the personal day; " +
                "relief pay settles from the relief drawer.",
        )
    }
}
