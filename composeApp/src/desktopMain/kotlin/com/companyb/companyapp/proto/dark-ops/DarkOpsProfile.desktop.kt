package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsProfile(repo: DarkOpsRepo) {
    Column {
        OpsSectionHeader("profile", "me · capabilities · exit") { }
        OpsPanel(modifier = Modifier.fillMaxWidth()) {
            Text(repo.currentUser.name.uppercase(), style = DarkOpsType.displaySmall, color = DarkOpsPalette.Phosphor)
            Spacer(Modifier.height(4.dp))
            Text("role: " + repo.currentUser.role, style = DarkOpsType.bodyMedium)
            Text("home: Makati · slot 01", style = DarkOpsType.bodyMedium)
            Spacer(Modifier.height(OpsPadSm))
            Text("CAPABILITIES", style = DarkOpsType.labelSmall)
            if (repo.currentUser.capabilities.isEmpty()) {
                Text("(none) — ONBOARDING bundle is empty", style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Red)
            } else {
                repo.currentUser.capabilities.forEach { cap ->
                    Text("· " + cap, style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Cyan)
                }
            }
            Spacer(Modifier.height(OpsPadSm))
            Row(horizontalArrangement = Arrangement.spacedBy(OpsPadSm)) {
                Button(onClick = { repo.toggleClock() }, colors = greenButton(), enabled = repo.clockedIn) {
                    Text("CLOCK OUT", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
                }
                Button(onClick = { repo.logout() }, colors = greenButton()) {
                    Text("LOGOUT", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
                }
            }
            Spacer(Modifier.height(4.dp))
            OpsNote("logout kills the session and returns to sign-in")
        }
    }
}
