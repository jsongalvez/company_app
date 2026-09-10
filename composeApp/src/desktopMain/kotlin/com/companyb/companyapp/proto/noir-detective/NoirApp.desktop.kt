package com.companyb.companyapp.proto.noirdetective

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

private const val TAG = "NoirDetective"

@Composable
internal fun NoirApp() {
    val repo = rememberNoirRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "noir-detective board open (fake data, no network)") }
    NoirTheme {
        when {
            !repo.authed -> NoirAuthGate(repo)
            repo.currentUser.locked -> NoirOnboardingLock(repo)
            !repo.branchPicked -> NoirBranchSelect(repo)
            else -> NoirPrecinct(repo)
        }
    }
}

@Composable
private fun NoirPrecinct(repo: NoirRepo) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NoirPalette.NightRain),
    ) {
        NightBanner(repo)
        VenetianBlinds()
        Row(Modifier.weight(1f)) {
            EvidenceRail(repo)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(NoirPadMd),
            ) {
                when (repo.screen) {
                    NoirScreen.DOSSIER -> NoirHome(repo)
                    NoirScreen.CASES -> NoirSessions(repo)
                    NoirScreen.PERSONS -> NoirClients(repo)
                    NoirScreen.LEDGER -> NoirFinance(repo)
                    NoirScreen.SQUAD -> NoirTeam(repo)
                    NoirScreen.WIRE -> NoirMailbox(repo)
                    NoirScreen.BADGE -> NoirProfile(repo)
                }
            }
        }
        VenetianBlinds()
        NightFooter(repo)
    }
}

@Composable
private fun NightBanner(repo: NoirRepo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NoirPalette.Precinct)
            .padding(horizontal = NoirPadMd, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NIGHT DESK", style = NoirType.displaySmall)
            Spacer(Modifier.width(NoirPadSm))
            Text("⁂", style = NoirType.bodyMedium, color = NoirPalette.NeonBlue)
            Spacer(Modifier.width(NoirPadSm))
            Text(repo.currentBranch.name.uppercase(), style = NoirType.labelLarge, color = NoirPalette.NeonBlue)
            Spacer(Modifier.weight(1f))
            Text("BRANCH DAY", style = NoirType.labelSmall)
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
                                style = NoirType.bodySmall,
                                color = NoirPalette.LampAmber,
                            )
                            Text(day.date, style = NoirType.bodySmall)
                            DayInk(day.state)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Text(
            "The day rolls at 04:00 Asia/Manila — never midnight. " + repo.currentDay.note + ".",
            style = NoirType.bodySmall,
            color = NoirPalette.Dim,
        )
    }
}

@Composable
private fun EvidenceRail(repo: NoirRepo) {
    Column(
        Modifier
            .width(NoirRailWidth)
            .fillMaxHeight()
            .background(NoirPalette.Precinct)
            .padding(NoirPadSm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("EVIDENCE WALL", style = NoirType.labelSmall, color = NoirPalette.Dim)
        NoirScreen.entries.forEach { screen ->
            val active = repo.screen == screen
            FileRow(selected = active, onClick = { repo.screen = screen }) {
                Text(screen.key, style = NoirType.bodySmall, color = NoirPalette.LampAmber)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(screen.title, style = NoirType.bodyMedium)
                    Text(screen.hint, style = NoirType.bodySmall, color = NoirPalette.Dim)
                }
                if (screen == NoirScreen.WIRE && repo.unreadCount > 0) {
                    NoirBadge(repo.unreadCount.toString(), NoirPalette.SirenRed)
                }
                if (screen == NoirScreen.CASES && repo.pendingCount > 0) {
                    NoirBadge(repo.pendingCount.toString(), NoirPalette.LampAmber)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        CaseFolder(Modifier.fillMaxWidth()) {
            Text(repo.currentUser.name, style = NoirType.bodyMedium)
            Text(repo.currentUser.role, style = NoirType.bodySmall, color = NoirPalette.Dim)
            Text(
                if (repo.clockedIn) "● on the clock" else "○ off duty",
                style = NoirType.bodySmall,
                color = if (repo.clockedIn) NoirPalette.EvidenceGreen else NoirPalette.Dim,
            )
        }
    }
}

@Composable
private fun NightFooter(repo: NoirRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NoirPalette.Precinct)
            .padding(horizontal = NoirPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⁂ raining", style = NoirType.bodySmall, color = NoirPalette.NeonBlue)
        Spacer(Modifier.width(8.dp))
        Text(repo.currentDay.date + " " + repo.currentDay.state.label, style = NoirType.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text(repo.pendingCount.toString() + " open · " + repo.unreadCount + " unread", style = NoirType.bodySmall)
        Spacer(Modifier.weight(1f))
        Text("fake board · no network", style = NoirType.bodySmall, color = NoirPalette.Faint)
    }
}
