package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NoirAuthGate(repo: NoirRepo) {
    RainBackdrop {
        Column(
            Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NoirPadMd),
        ) {
            Text("CITY PRECINCT — NIGHT DESK", style = NoirType.labelMedium, color = NoirPalette.NeonBlue)
            Text("The rain never stops. Sign the night log.", style = NoirType.displaySmall)
            Text(
                "Midnight noir field board · fake sign-in, any operative walks in",
                style = NoirType.bodySmall,
                color = NoirPalette.Dim,
            )
            CaseFolder(Modifier.fillMaxWidth()) {
                repo.users.forEach { user ->
                    FileRow(selected = repo.currentUserId == user.id, onClick = { repo.currentUserId = user.id }) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = NoirType.bodyLarge)
                            Text(
                                user.role + if (user.locked) " · badgeless" else "",
                                style = NoirType.bodySmall,
                                color = NoirPalette.Dim,
                            )
                        }
                        if (user.locked) CaseStamp("locked", NoirPalette.SirenRed)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Button(
                onClick = { repo.authed = true },
                colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.LampAmber),
            ) {
                Text("CLOCK THE NIGHT IN", style = NoirType.labelLarge, color = NoirPalette.NightRain)
            }
        }
    }
}

@Composable
internal fun NoirOnboardingLock(repo: NoirRepo) {
    RainBackdrop {
        Column(
            Modifier.width(520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NoirPadMd),
        ) {
            CaseStamp("badgeless", NoirPalette.SirenRed)
            Text("No badge, no beat.", style = NoirType.displaySmall)
            Text(
                "K. Dela Pena holds the ONBOARDING role: zero capabilities, " +
                    "locked out even with a branch assignment until MANAGE_USERS grants a real role.",
                style = NoirType.bodyMedium,
                color = NoirPalette.Dim,
            )
            DeskLampNote("Ask a MANAGER to issue a role — the desk cannot help you tonight.")
            OutlinedButton(onClick = { repo.authed = false }) {
                Text("Back to the night log", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
            }
        }
    }
}

@Composable
internal fun NoirBranchSelect(repo: NoirRepo) {
    RainBackdrop {
        Column(
            Modifier.width(600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NoirPadMd),
        ) {
            Text("PICK YOUR PRECINCT", style = NoirType.labelMedium, color = NoirPalette.NeonBlue)
            Text("Three doors. One rainy city.", style = NoirType.titleLarge)
            CaseFolder(Modifier.fillMaxWidth()) {
                repo.branches.forEach { branch ->
                    FileRow(selected = repo.branchId == branch.id, onClick = { repo.branchId = branch.id }) {
                        Column(Modifier.weight(1f)) {
                            Text(branch.name, style = NoirType.bodyLarge)
                            Text(branch.kind, style = NoirType.bodySmall, color = NoirPalette.Dim)
                        }
                        Text("→", style = NoirType.bodyLarge, color = NoirPalette.LampAmber)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NoirPadSm)) {
                OutlinedButton(onClick = { repo.authed = false }) {
                    Text("Sign out", style = NoirType.labelLarge, color = NoirPalette.Dim)
                }
                Button(
                    onClick = { repo.branchPicked = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NoirPalette.LampAmber),
                ) {
                    Text("OPEN THE CASE BOARD", style = NoirType.labelLarge, color = NoirPalette.NightRain)
                }
            }
        }
    }
}

@Composable
internal fun RainBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(NoirPalette.NightRain)
            .padding(NoirPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂", style = NoirType.bodySmall, color = NoirPalette.BlindLight)
            Spacer(Modifier.height(NoirPadMd))
            Box(
                Modifier
                    .background(NoirPalette.Precinct, RoundedCornerShape(8.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
            Spacer(Modifier.height(NoirPadMd))
            Text("⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂ ⁂", style = NoirType.bodySmall, color = NoirPalette.BlindLight)
        }
    }
}
