package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GraveyardCalmAuthGate(repo: CalmRepo) {
    Box(
        Modifier.fillMaxSize().background(GraveyardCalmPalette.Night).padding(CalmPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("☾", style = GraveyardCalmType.displaySmall, color = GraveyardCalmPalette.Lamp)
            Text("graveyard calm", style = GraveyardCalmType.displaySmall, color = GraveyardCalmPalette.Ink)
            Text(
                "overnight desk · skeleton crew · nothing loud",
                style = GraveyardCalmType.labelSmall,
                color = GraveyardCalmPalette.Faint,
            )
            Spacer(Modifier.height(16.dp))
            repo.users.forEachIndexed { index, user ->
                Box(
                    Modifier
                        .clickable { repo.login(index) }
                        .background(GraveyardCalmPalette.Card, RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .width(340.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = GraveyardCalmType.bodyLarge, color = GraveyardCalmPalette.Ink)
                            Text(user.role, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                        }
                        if (user.locked) CalmTag("ONBOARDING · locked", GraveyardCalmPalette.Violet)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "fake sign-in only — no network, no backend",
                style = GraveyardCalmType.labelSmall,
                color = GraveyardCalmPalette.Faint,
            )
        }
    }
}

@Composable
internal fun GraveyardCalmOnboardingLock(repo: CalmRepo) {
    Box(
        Modifier.fillMaxSize().background(GraveyardCalmPalette.Night).padding(CalmPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("☾", style = GraveyardCalmType.displaySmall, color = GraveyardCalmPalette.Violet)
            Text("still onboarding", style = GraveyardCalmType.titleLarge, color = GraveyardCalmPalette.Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "${repo.currentUser.name} holds the ONBOARDING role: zero capabilities,",
                style = GraveyardCalmType.bodyMedium,
                color = GraveyardCalmPalette.Dim,
            )
            Text(
                "no desk, no queue, no till. A coordinator unlocks you at dawn.",
                style = GraveyardCalmType.bodyMedium,
                color = GraveyardCalmPalette.Dim,
            )
            Spacer(Modifier.height(16.dp))
            CalmGhost("step back outside") { repo.logout() }
        }
    }
}

@Composable
internal fun GraveyardCalmBranchSelect(repo: CalmRepo) {
    Box(
        Modifier.fillMaxSize().background(GraveyardCalmPalette.Night).padding(CalmPadLg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "choose tonight's post, ${repo.currentUser.name}",
                style = GraveyardCalmType.titleLarge,
                color = GraveyardCalmPalette.Ink,
            )
            Text(
                repo.currentUser.role + " · clock-in happens at the desk",
                style = GraveyardCalmType.labelSmall,
                color = GraveyardCalmPalette.Faint,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repo.branches.forEachIndexed { index, branch ->
                    Box(
                        Modifier
                            .clickable { repo.pickBranch(index) }
                            .background(GraveyardCalmPalette.Card, RoundedCornerShape(10.dp))
                            .padding(CalmPadMd)
                            .width(210.dp),
                    ) {
                        Column {
                            Text(branch.name, style = GraveyardCalmType.titleMedium, color = GraveyardCalmPalette.Ink)
                            Spacer(Modifier.height(4.dp))
                            CalmTag(branch.kind, GraveyardCalmPalette.Moon)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            CalmQuiet("not me — sign out") { repo.logout() }
        }
    }
}
