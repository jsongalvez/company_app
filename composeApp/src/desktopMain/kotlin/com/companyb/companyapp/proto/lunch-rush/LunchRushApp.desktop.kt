package com.companyb.companyapp.proto.lunchrush

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

private const val TAG = "LunchRush"

@Composable
internal fun LunchRushApp() {
    val repo = rememberLunchRushRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "lunch-rush board open (fake data, no network)") }
    LunchRushTheme {
        when {
            !repo.authed -> LunchRushAuthGate(repo)
            repo.currentUser.locked -> LunchRushOnboardingLock(repo)
            !repo.branchPicked -> LunchRushBranchSelect(repo)
            else -> LunchRushShell(repo)
        }
    }
}

@Composable
private fun LunchRushShell(repo: LunchRushRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(LunchRushPalette.Paper)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.One -> repo.screen = RushScreen.HOME
                        Key.Two -> repo.screen = RushScreen.SESSIONS
                        Key.Three -> repo.screen = RushScreen.CLIENTS
                        Key.Four -> repo.screen = RushScreen.FINANCE
                        Key.Five -> repo.screen = RushScreen.TEAM
                        Key.Six -> repo.screen = RushScreen.MAIL
                        Key.Seven -> repo.screen = RushScreen.PROFILE
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
    ) {
        Column(Modifier.fillMaxSize()) {
            DayBanner(repo)
            HorizontalDivider(color = LunchRushPalette.Border)
            Row(Modifier.weight(1f)) {
                NavRail(repo)
                Box(Modifier.weight(1f).padding(RushPadMd)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (repo.shortcutsOpen) {
                            LunchRushShortcutsDialog(repo)
                            Spacer(Modifier.height(RushPadMd))
                        }
                        when (repo.screen) {
                            RushScreen.HOME -> LunchRushHome(repo)
                            RushScreen.SESSIONS -> LunchRushSessions(repo)
                            RushScreen.CLIENTS -> LunchRushClients(repo)
                            RushScreen.FINANCE -> LunchRushFinance(repo)
                            RushScreen.TEAM -> LunchRushTeam(repo)
                            RushScreen.MAIL -> LunchRushMailbox(repo)
                            RushScreen.PROFILE -> LunchRushProfile(repo)
                        }
                    }
                }
            }
            HorizontalDivider(color = LunchRushPalette.Border)
            StatusBar(repo)
        }
    }
}

@Composable
private fun DayBanner(repo: LunchRushRepo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LunchRushPalette.Wok)
            .padding(horizontal = RushPadMd, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MIDDAY RUSH  ", style = LunchRushType.labelLarge, color = LunchRushPalette.CardHot)
            repo.days.forEachIndexed { index, day ->
                val active = index == repo.dayIndex
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .clickable { repo.dayIndex = index }
                        .background(
                            if (active) LunchRushPalette.Turmeric else LunchRushPalette.Wok,
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${day.date} ${day.state.label}",
                        style = LunchRushType.labelSmall,
                        color = if (active) LunchRushPalette.Wok else LunchRushPalette.CardHot,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(repo.currentBranch.name, style = LunchRushType.labelSmall, color = LunchRushPalette.CardHot)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Branch Day ${repo.currentDay.date} ${repo.currentDay.state.label} — ${repo.currentDay.note}. " +
                "Day boundary 04:00 Asia/Manila: walk-ins before 04:00 count to yesterday.",
            style = LunchRushType.bodySmall,
            color = LunchRushPalette.CardHot.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun NavRail(repo: LunchRushRepo) {
    Column(
        Modifier
            .width(RushRailWidth)
            .fillMaxHeight()
            .background(LunchRushPalette.Card)
            .padding(RushPadMd),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("COUNTER", style = LunchRushType.labelSmall)
        RushScreen.entries.forEach { item ->
            val active = repo.screen == item
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { repo.screen = item }
                    .background(
                        if (active) LunchRushPalette.Char else LunchRushPalette.CardHot,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.key,
                        style = LunchRushType.labelSmall,
                        color = if (active) LunchRushPalette.CardHot else LunchRushPalette.Dim,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            item.title,
                            style = LunchRushType.labelLarge,
                            color = if (active) androidx.compose.ui.graphics.Color.White else LunchRushPalette.Ink,
                        )
                        Text(
                            item.hint,
                            style = LunchRushType.bodySmall,
                            color = if (active) LunchRushPalette.CardHot.copy(alpha = 0.8f) else LunchRushPalette.Dim,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (repo.bottleneckFlags.isNotEmpty()) {
            Text("${repo.bottleneckFlags.size} FIRES", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
        }
        Box(Modifier.fillMaxWidth().clickable { repo.shortcutsOpen = !repo.shortcutsOpen }.padding(4.dp)) {
            Text("? shortcuts", style = LunchRushType.labelSmall)
        }
    }
}

@Composable
private fun StatusBar(repo: LunchRushRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LunchRushPalette.CardHot)
            .padding(horizontal = RushPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("heat ${repo.rushLevel}", style = LunchRushType.labelMedium, color = LunchRushPalette.Char)
        Spacer(Modifier.width(12.dp))
        Text("${repo.pendingSessions.size} pending", style = LunchRushType.bodySmall)
        Spacer(Modifier.width(12.dp))
        Text("${repo.unreadCount} unread", style = LunchRushType.bodySmall)
        Spacer(Modifier.width(12.dp))
        Text(if (repo.surgeActive) "SURGE +10% walk-ins" else "menu price", style = LunchRushType.bodySmall)
        Spacer(Modifier.weight(1f))
        Text("${repo.currentDay.date} — ${repo.currentUser.name}", style = LunchRushType.bodySmall)
    }
}
