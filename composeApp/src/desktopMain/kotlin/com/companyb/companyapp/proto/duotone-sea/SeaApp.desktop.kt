package com.companyb.companyapp.proto.duotonesea

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "DuotoneSea"

@Composable
internal fun SeaApp() {
    val repo = rememberSeaRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "duotone-sea board open (fake data, no network)") }
    SeaTheme {
        when {
            !repo.authed -> SeaAuthGate(repo)
            repo.currentUser.locked -> SeaOnboardingLock(repo)
            !repo.branchPicked -> SeaBranchSelect(repo)
            else -> SeaHarbor(repo)
        }
    }
}

@Composable
private fun SeaHarbor(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SeaPalette.Abyss),
    ) {
        TideBanner(repo)
        WaveDivider()
        Row(Modifier.weight(1f)) {
            KelpRail(repo)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(SeaPadMd),
            ) {
                when (repo.screen) {
                    SeaScreen.LAGOON -> SeaHome(repo)
                    SeaScreen.TIDES -> SeaSessions(repo)
                    SeaScreen.DRIFT -> SeaClients(repo)
                    SeaScreen.HARBOR -> SeaFinance(repo)
                    SeaScreen.CREW -> SeaTeam(repo)
                    SeaScreen.BUOY -> SeaMailbox(repo)
                    SeaScreen.DIVER -> SeaProfile(repo)
                }
            }
        }
        WaveDivider(crest = SeaPalette.SeaGlass, trough = SeaPalette.Coral)
        TideFooter(repo)
    }
}

@Composable
private fun TideBanner(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SeaPalette.DeepSea)
            .padding(horizontal = SeaPadMd, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TIDE BOARD", style = SeaType.displaySmall)
            Spacer(Modifier.width(SeaPadSm))
            Text("≈", style = SeaType.bodyMedium, color = SeaPalette.SeaGlass)
            Spacer(Modifier.width(SeaPadSm))
            Text(repo.currentBranch.name.uppercase(), style = SeaType.labelLarge, color = SeaPalette.SeaGlass)
            Spacer(Modifier.weight(1f))
            Text("BRANCH DAY", style = SeaType.labelSmall)
            Spacer(Modifier.width(6.dp))
            repo.days.forEachIndexed { index, day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { repo.dayIndex = index }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (index == repo.dayIndex) "▣" else "▢",
                                style = SeaType.bodySmall,
                                color = SeaPalette.Coral,
                            )
                            Text(day.date, style = SeaType.bodySmall)
                            DayFoam(day.state)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Text(
            "The day rolls at 04:00 Asia/Manila — never midnight. " + repo.currentDay.note + ".",
            style = SeaType.bodySmall,
            color = SeaPalette.Mist,
        )
    }
}

@Composable
private fun KelpRail(repo: SeaRepo) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(SeaRailWidth)
            .background(SeaPalette.DeepSea)
            .padding(SeaPadSm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("SWIM LANES", style = SeaType.titleSmall)
        SeaScreen.entries.forEach { lane ->
            DriftRow(selected = repo.screen == lane, onClick = { repo.screen = lane }) {
                Column(Modifier.weight(1f)) {
                    Text(lane.key + " · " + lane.title, style = SeaType.labelLarge)
                    Text(lane.hint, style = SeaType.bodySmall, color = SeaPalette.Faint)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        LagoonCard {
            Text(repo.currentUser.name, style = SeaType.labelLarge)
            Text(repo.currentUser.role, style = SeaType.bodySmall)
        }
    }
}

@Composable
private fun TideFooter(repo: SeaRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SeaPalette.DeepSea)
            .padding(horizontal = SeaPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(repo.currentDay.date + " · " + repo.currentDay.state.label, style = SeaType.bodySmall)
        Spacer(Modifier.width(SeaPadMd))
        Text(repo.pendingCount.toString() + " pending", style = SeaType.bodySmall, color = SeaPalette.SunBuoy)
        Spacer(Modifier.width(SeaPadSm))
        Text(repo.unreadCount.toString() + " unread", style = SeaType.bodySmall, color = SeaPalette.Coral)
        Spacer(Modifier.weight(1f))
        Text("duotone-sea · fake tide, no network", style = SeaType.bodySmall, color = SeaPalette.Faint)
    }
}
