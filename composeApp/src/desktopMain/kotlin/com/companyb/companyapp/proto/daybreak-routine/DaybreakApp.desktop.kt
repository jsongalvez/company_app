package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "Daybreak"

@Composable
internal fun DaybreakApp() {
    val repo = rememberDaybreakRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "daybreak-routine opener open (fake data, no network)") }
    DaybreakTheme {
        when {
            !repo.authed -> DaybreakAuthGate(repo)
            repo.currentUser.locked -> DaybreakOnboardingLock(repo)
            !repo.branchPicked -> DaybreakBranchSelect(repo)
            else -> DaybreakShell(repo)
        }
    }
}

@Composable
private fun DaybreakShell(repo: DaybreakRepo) {
    Box(modifier = Modifier.fillMaxSize().background(DaybreakPalette.Paper)) {
        Column(Modifier.fillMaxSize()) {
            DayBanner(repo)
            HorizontalDivider(color = DaybreakPalette.Line)
            Row(Modifier.weight(1f)) {
                DawnRail(repo)
                Box(Modifier.weight(1f).padding(DawnPadMd)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (repo.screen) {
                            DawnScreen.RITUAL -> DaybreakRitual(repo)
                            DawnScreen.SESSIONS -> DaybreakSessions(repo)
                            DawnScreen.CLIENTS -> DaybreakClients(repo)
                            DawnScreen.FINANCE -> DaybreakFinance(repo)
                            DawnScreen.TEAM -> DaybreakTeam(repo)
                            DawnScreen.MAIL -> DaybreakMailbox(repo)
                            DawnScreen.PROFILE -> DaybreakProfile(repo)
                        }
                    }
                }
            }
            DawnStatus(repo)
        }
    }
}

@Composable
private fun DawnRail(repo: DaybreakRepo) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(DawnRailWidth)
                .background(DaybreakPalette.PaperDeep)
                .padding(DawnPadSm),
    ) {
        Text("SUNRISE RAIL", style = DaybreakType.labelSmall, modifier = Modifier.padding(4.dp))
        DawnScreen.entries.forEach { screen ->
            val selected = repo.screen == screen
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) DaybreakPalette.Card else DaybreakPalette.PaperDeep)
                        .clickable { repo.screen = screen }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(screen.key + " ", style = DaybreakType.labelSmall, color = DaybreakPalette.SunriseDeep)
                        Text(screen.title, style = DaybreakType.labelLarge)
                        Spacer(Modifier.weight(1f))
                        if (screen == DawnScreen.MAIL && repo.unreadCount > 0) {
                            DawnBadge("${repo.unreadCount}", DaybreakPalette.Sunrise)
                        }
                        if (screen == DawnScreen.SESSIONS && repo.pendingCount > 0) {
                            DawnBadge("${repo.pendingCount}", DaybreakPalette.Gold)
                        }
                    }
                    Text(screen.hint, style = DaybreakType.labelSmall)
                }
            }
            Spacer(Modifier.padding(2.dp))
        }
        Spacer(Modifier.weight(1f))
        DawnPanel(Modifier.fillMaxWidth()) {
            Text("Opener ${repo.ritualStepsDone}/4", style = DaybreakType.labelLarge)
            Text(if (repo.ritualDone) "doors open" else "finish the ritual", style = DaybreakType.bodySmall)
        }
    }
}

@Composable
private fun DawnStatus(repo: DaybreakRepo) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DaybreakPalette.Ink).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            repo.currentDay.date + " " + repo.currentDay.state.label + " · " + repo.currentBranch.name +
                " · " + repo.currentUser.name,
            style = DaybreakType.bodySmall,
            color = androidx.compose.ui.graphics.Color.White,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "PENDING ${repo.pendingCount} · unread ${repo.unreadCount} · fake only",
            style = DaybreakType.labelSmall,
            color =
                androidx.compose.ui.graphics
                    .Color(0xFFB9AE9C),
        )
    }
}
