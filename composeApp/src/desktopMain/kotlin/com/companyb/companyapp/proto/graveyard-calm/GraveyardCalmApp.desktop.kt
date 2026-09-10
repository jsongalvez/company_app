package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "GraveyardCalm"

@Composable
internal fun GraveyardCalmApp() {
    val repo = rememberCalmRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "graveyard-calm night desk open (fake data, no network)") }
    GraveyardCalmTheme {
        when {
            !repo.authed -> GraveyardCalmAuthGate(repo)
            repo.currentUser.locked -> GraveyardCalmOnboardingLock(repo)
            !repo.branchPicked -> GraveyardCalmBranchSelect(repo)
            else -> GraveyardCalmShell(repo)
        }
    }
}

@Composable
private fun GraveyardCalmShell(repo: CalmRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GraveyardCalmPalette.Night)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.One -> repo.screen = CalmScreen.HOME
                        Key.Two -> repo.screen = CalmScreen.SESSIONS
                        Key.Three -> repo.screen = CalmScreen.CLIENTS
                        Key.Four -> repo.screen = CalmScreen.FINANCE
                        Key.Five -> repo.screen = CalmScreen.TEAM
                        Key.Six -> repo.screen = CalmScreen.MAIL
                        Key.Seven -> repo.screen = CalmScreen.PROFILE
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
    ) {
        Column(Modifier.fillMaxSize()) {
            CalmDayBanner(repo)
            HorizontalDivider(color = GraveyardCalmPalette.BorderFaint)
            Row(Modifier.weight(1f)) {
                CalmNavRail(repo)
                Box(Modifier.weight(1f).padding(CalmPadMd)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (repo.screen) {
                            CalmScreen.HOME -> GraveyardCalmHome(repo)
                            CalmScreen.SESSIONS -> GraveyardCalmSessions(repo)
                            CalmScreen.CLIENTS -> GraveyardCalmClients(repo)
                            CalmScreen.FINANCE -> GraveyardCalmFinance(repo)
                            CalmScreen.TEAM -> GraveyardCalmTeam(repo)
                            CalmScreen.MAIL -> GraveyardCalmMailbox(repo)
                            CalmScreen.PROFILE -> GraveyardCalmProfile(repo)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            HorizontalDivider(color = GraveyardCalmPalette.BorderFaint)
            CalmStatusBar(repo)
        }
    }
}

@Composable
private fun CalmDayBanner(repo: CalmRepo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GraveyardCalmPalette.Rail)
            .padding(horizontal = CalmPadMd, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☾ NIGHT  ", style = GraveyardCalmType.labelLarge, color = GraveyardCalmPalette.Lamp)
            repo.days.forEachIndexed { index, day ->
                val active = index == repo.dayIndex
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .clickable { repo.dayIndex = index }
                        .background(
                            if (active) GraveyardCalmPalette.Lamp.copy(alpha = 0.18f) else GraveyardCalmPalette.Rail,
                            RoundedCornerShape(8.dp),
                        ).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.date,
                            style = GraveyardCalmType.labelMedium,
                            color = if (active) GraveyardCalmPalette.Lamp else GraveyardCalmPalette.Faint,
                        )
                        Spacer(Modifier.width(6.dp))
                        CalmDayTag(day.state)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(repo.currentBranch.name, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "${repo.currentDay.date} · ${repo.currentDay.state.label} — ${repo.currentDay.note}. Branch days roll at 04:00 Asia/Manila.",
            style = GraveyardCalmType.labelSmall,
            color = GraveyardCalmPalette.Faint,
        )
    }
}

@Composable
private fun CalmNavRail(repo: CalmRepo) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(CalmRailWidth)
            .background(GraveyardCalmPalette.Rail)
            .padding(CalmPadMd),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("the desk", style = GraveyardCalmType.titleSmall, color = GraveyardCalmPalette.Faint)
        Spacer(Modifier.height(4.dp))
        CalmScreen.entries.forEach { s ->
            val active = repo.screen == s
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { repo.screen = s }
                    .background(
                        if (active) GraveyardCalmPalette.Card else GraveyardCalmPalette.Rail,
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.key,
                        style = GraveyardCalmType.labelSmall,
                        color = if (active) GraveyardCalmPalette.Lamp else GraveyardCalmPalette.Faint,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            s.title,
                            style = GraveyardCalmType.labelLarge,
                            color = if (active) GraveyardCalmPalette.Ink else GraveyardCalmPalette.Dim,
                        )
                        Text(s.hint, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
                    }
                    if (s == CalmScreen.MAIL && repo.unreadCount > 0) {
                        Spacer(Modifier.weight(1f))
                        CalmTag("${repo.unreadCount}", GraveyardCalmPalette.Lamp)
                    }
                    if (s == CalmScreen.SESSIONS && repo.pendingCount > 0) {
                        Spacer(Modifier.weight(1f))
                        CalmTag("${repo.pendingCount}", GraveyardCalmPalette.Moon)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (repo.clockedIn) "● lamp lit" else "○ lamp dim",
            style = GraveyardCalmType.labelSmall,
            color = if (repo.clockedIn) GraveyardCalmPalette.Sage else GraveyardCalmPalette.Faint,
        )
        Text(repo.currentUser.name, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
    }
}

@Composable
private fun CalmStatusBar(repo: CalmRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GraveyardCalmPalette.Rail)
            .padding(horizontal = CalmPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${repo.currentDay.date} ${repo.currentDay.state.label} · ${repo.pendingCount} awake · ${repo.voidedCount} voided",
            style = GraveyardCalmType.labelSmall,
            color = GraveyardCalmPalette.Faint,
        )
        Spacer(Modifier.weight(1f))
        Text("fake data · no network · keys 1–7", style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
    }
}
