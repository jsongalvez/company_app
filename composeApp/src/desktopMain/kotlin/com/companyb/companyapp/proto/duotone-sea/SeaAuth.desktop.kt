package com.companyb.companyapp.proto.duotonesea

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SeaAuthGate(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SeaPalette.Abyss)
            .verticalScroll(rememberScrollState())
            .padding(SeaPadLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("≈≈≈", style = SeaType.displaySmall, color = SeaPalette.SeaGlass)
        Text("COASTAL CLINIC", style = SeaType.titleSmall)
        Text("duotone-sea tide board", style = SeaType.displaySmall)
        Text("pick a crew member to wade in — fake data only, no network", style = SeaType.bodySmall)
        Spacer(Modifier.height(SeaPadLg))
        Column(verticalArrangement = Arrangement.spacedBy(SeaPadSm), modifier = Modifier.fillMaxWidth(0.6f)) {
            repo.users.forEach { user ->
                DriftRow(selected = repo.currentUserId == user.id, onClick = { repo.currentUserId = user.id }) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = SeaType.titleMedium)
                        Text(user.role, style = SeaType.bodySmall, color = SeaPalette.Mist)
                    }
                    if (user.locked) {
                        TideChip("locked", SeaPalette.Siren)
                    } else {
                        TideChip("swim in", SeaPalette.SeaGlass)
                    }
                }
            }
        }
        Spacer(Modifier.height(SeaPadLg))
        Button(
            onClick = { repo.authed = true },
            colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.Coral),
        ) {
            Text("DIVE IN", style = SeaType.labelLarge, color = SeaPalette.Abyss)
        }
        Spacer(Modifier.height(SeaPadSm))
        WaveDivider()
    }
}

@Composable
internal fun SeaOnboardingLock(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SeaPalette.Abyss)
            .padding(SeaPadLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("≈≈≈", style = SeaType.displaySmall, color = SeaPalette.Siren)
        Text("ONBOARDING hold", style = SeaType.displaySmall)
        Text(
            repo.currentUser.name + " has no badge yet — zero capabilities, calm water only.",
            style = SeaType.bodyMedium,
        )
        Spacer(Modifier.height(SeaPadSm))
        NoteLine("ONBOARDING crew cannot clock in, edit, or seal remittance.")
        Spacer(Modifier.height(SeaPadLg))
        OutlinedButton(onClick = { repo.authed = false }) {
            Text("Back to the shore", style = SeaType.labelMedium, color = SeaPalette.SeaGlass)
        }
    }
}

@Composable
internal fun SeaBranchSelect(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SeaPalette.Abyss)
            .verticalScroll(rememberScrollState())
            .padding(SeaPadLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("CHOOSE YOUR COVE", style = SeaType.titleSmall)
        Text("where does this tide break?", style = SeaType.displaySmall)
        Spacer(Modifier.height(SeaPadLg))
        Column(verticalArrangement = Arrangement.spacedBy(SeaPadSm), modifier = Modifier.fillMaxWidth(0.6f)) {
            repo.branches.forEach { branch ->
                DriftRow(selected = repo.branchId == branch.id, onClick = { repo.branchId = branch.id }) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, style = SeaType.titleMedium)
                        Text(branch.kind, style = SeaType.bodySmall, color = SeaPalette.Mist)
                    }
                    TideChip("anchor", SeaPalette.Coral)
                }
            }
        }
        Spacer(Modifier.height(SeaPadLg))
        Row(horizontalArrangement = Arrangement.spacedBy(SeaPadSm)) {
            OutlinedButton(onClick = { repo.authed = false }) {
                Text("Back", style = SeaType.labelMedium, color = SeaPalette.Mist)
            }
            Button(
                onClick = { repo.branchPicked = true },
                colors = ButtonDefaults.buttonColors(containerColor = SeaPalette.Coral),
            ) {
                Text("SET SAIL", style = SeaType.labelLarge, color = SeaPalette.Abyss)
            }
        }
        Spacer(Modifier.height(SeaPadSm))
        Row(modifier = Modifier.clickable { repo.authed = false }) {
            Text("signed in as " + repo.currentUser.name, style = SeaType.bodySmall, color = SeaPalette.Faint)
        }
    }
}
