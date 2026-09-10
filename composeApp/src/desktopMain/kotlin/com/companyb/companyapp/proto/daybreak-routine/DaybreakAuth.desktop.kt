package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakAuthGate(repo: DaybreakRepo) {
    var picked by remember { mutableStateOf(repo.currentUserId) }
    Column(
        modifier = Modifier.fillMaxSize().background(DaybreakPalette.Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DAYBREAK ROUTINE", style = DaybreakType.labelMedium, color = DaybreakPalette.SunriseDeep)
        Text("Unlock the clinic at sunrise", style = DaybreakType.displaySmall)
        Text("Step 1 of the opener: who is turning the key?", style = DaybreakType.bodyMedium)
        Spacer(Modifier.height(16.dp))
        repo.users.forEach { user ->
            val selected = picked == user.id
            DawnPanel(
                modifier =
                    Modifier.fillMaxWidth(0.6f).clickable {
                        picked = user.id
                        repo.currentUserId = user.id
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name + " · " + user.role, style = DaybreakType.titleMedium)
                        Text(
                            if (user.locked) {
                                "ONBOARDING: zero capabilities, locked"
                            } else {
                                user.capabilities
                                    .joinToString(
                                        ", ",
                                    )
                            },
                            style = DaybreakType.bodySmall,
                        )
                    }
                    if (selected) DawnBadge("KEY", DaybreakPalette.Sunrise)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { repo.authed = true },
            colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Sunrise),
        ) {
            Text("UNLOCK WITH THIS KEY")
        }
        Spacer(Modifier.height(8.dp))
        DawnNote("Fake sign-in only: any key turns. K. Dela Pena is ONBOARDING and lands on the locked screen.")
    }
}

@Composable
internal fun DaybreakOnboardingLock(repo: DaybreakRepo) {
    Column(
        modifier = Modifier.fillMaxSize().background(DaybreakPalette.PaperDeep).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HELD AT THE DOOR", style = DaybreakType.labelMedium, color = DaybreakPalette.Rose)
        Text("Onboarding is not a role yet", style = DaybreakType.displaySmall)
        DawnPanel(Modifier.fillMaxWidth(0.6f)) {
            Text(
                "K. Dela Pena carries zero capabilities. The opener stops here by design.",
                style = DaybreakType.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            DawnRow("Role bundle", "EMPTY")
            DawnRow("Branch access", "NONE DERIVES")
            DawnRow("Next", "MANAGE_USERS grants a real role")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { repo.authed = false }) { Text("Hand the key back") }
    }
}

@Composable
internal fun DaybreakBranchSelect(repo: DaybreakRepo) {
    Column(
        modifier = Modifier.fillMaxSize().background(DaybreakPalette.Paper).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CHOOSE TODAY'S DOOR", style = DaybreakType.labelMedium, color = DaybreakPalette.SunriseDeep)
        Text("Which Branch wakes up first?", style = DaybreakType.displaySmall)
        Spacer(Modifier.height(14.dp))
        repo.branches.forEach { branch ->
            DawnPanel(
                modifier =
                    Modifier.fillMaxWidth(0.55f).clip(RoundedCornerShape(10.dp)).clickable {
                        repo.branchId = branch.id
                        repo.branchPicked = true
                    },
            ) {
                Text(branch.name, style = DaybreakType.titleLarge)
                Text(branch.kind, style = DaybreakType.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        DawnNote("Branch is the day's home: CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION.")
    }
}
