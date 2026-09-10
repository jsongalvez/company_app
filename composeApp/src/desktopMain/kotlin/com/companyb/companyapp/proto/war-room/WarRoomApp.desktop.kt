package com.companyb.companyapp.proto.warroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #836 — war-room shell: bunker masthead, branch-day incident strip, exception rail.

enum class WarScreen(val room: String) {
    BRIEF("Brief"),
    SIGNIN("Access"),
    SECTORS("Sectors"),
    FLOOR("Floor"),
    TRIAGE("Triage"),
    DOSSIER("Dossier"),
    VAULT("Vault"),
    ROSTER("Roster"),
    SIGNALS("Signals"),
    LEDGER("Ledger"),
    ME("My Tag"),
}

@Composable
fun WarRoomApp(onBack: () -> Unit = {}) {
    val repo = remember { WarFakeRepo() }
    var screen by remember { mutableStateOf(WarScreen.BRIEF) }

    LaunchedEffect(Unit) { logInfo("WarRoom", "war-room prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(WarColors.Bunker)) {
        WarMasthead(repo = repo)
        WarDayStrip(repo = repo)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (loggedIn) {
                WarRail(
                    screen = screen,
                    repo = repo,
                    onPick = { screen = it },
                    onBack = onBack,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (screen) {
                        WarScreen.BRIEF -> WarBrief(repo = repo, onNext = { screen = WarScreen.SIGNIN }, go = { screen = it })
                        WarScreen.SIGNIN -> WarSignin(repo = repo, onNext = { screen = WarScreen.SECTORS })
                        WarScreen.SECTORS -> WarSectors(repo = repo, onNext = { screen = WarScreen.FLOOR })
                        WarScreen.FLOOR -> WarFloor(repo = repo, go = { screen = it })
                        WarScreen.TRIAGE -> WarTriage(repo = repo)
                        WarScreen.DOSSIER -> WarDossier(repo = repo)
                        WarScreen.VAULT -> WarVault(repo = repo)
                        WarScreen.ROSTER -> WarRoster(repo = repo)
                        WarScreen.SIGNALS -> WarSignals(repo = repo)
                        WarScreen.LEDGER -> WarLedger(repo = repo)
                        WarScreen.ME -> WarMe(repo = repo, onLogout = { screen = WarScreen.SIGNIN })
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun WarMasthead(repo: WarFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(WarColors.BunkerDeep)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "WAR ROOM ● EXCEPTIONS ONLY",
                color = WarColors.Siren,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.name.uppercase(),
                color = WarColors.Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "${repo.currentBranch.sector} — ${repo.exceptionCount} live exceptions",
                color = WarColors.Faint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarSirenCell("VOIDS ${repo.voidQueue.size}")
                WarSirenCell("GAPS ${repo.reliefGapCount}")
            }
            Text(
                if (repo.clockedIn) "● ON THE FLOOR" else "○ OFF THE FLOOR",
                color = if (repo.clockedIn) WarColors.Ok else WarColors.Faint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                (repo.currentUser?.name ?: "no tag issued").uppercase(),
                color = WarColors.InkDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WarDayStrip(repo: WarFakeRepo) {
    val tone = repo.dayStatus.band()
    Row(
        Modifier.fillMaxWidth().background(WarColors.Quiet)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WarChip(repo.dayStatus.name, tone)
        Spacer(Modifier.width(10.dp))
        Text(
            "Branch Day ${repo.operationalDate} ● 04:00 Asia/Manila boundary",
            color = WarColors.InkDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        WarDayStatus.entries.forEach { option ->
            val active = repo.dayStatus == option
            Text(
                option.name,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (active) Modifier.background(option.band()) else Modifier)
                    .clickable { repo.dayStatus = option }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (active) WarColors.BunkerDeep else WarColors.Faint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun WarRail(
    screen: WarScreen,
    repo: WarFakeRepo,
    onPick: (WarScreen) -> Unit,
    onBack: () -> Unit,
) {
    val unread = repo.notifications.count { !it.read }
    Column(
        Modifier.width(208.dp).fillMaxHeight()
            .background(WarColors.BunkerDeep)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("EXCEPTION RAIL", color = WarColors.Siren, fontSize = 11.sp, fontWeight = FontWeight.Black)
        WarRailItem("Brief", screen == WarScreen.BRIEF, null) { onPick(WarScreen.BRIEF) }
        WarRailItem("Sectors", screen == WarScreen.SECTORS, null) { onPick(WarScreen.SECTORS) }
        WarRailItem("Floor", screen == WarScreen.FLOOR, null) { onPick(WarScreen.FLOOR) }
        WarRailItem("Triage", screen == WarScreen.TRIAGE, repo.voidQueue.size + repo.varianceQueue.size) { onPick(WarScreen.TRIAGE) }
        WarRailItem("Dossier", screen == WarScreen.DOSSIER, null) { onPick(WarScreen.DOSSIER) }
        WarRailItem("Vault", screen == WarScreen.VAULT, repo.undoWindows.size) { onPick(WarScreen.VAULT) }
        WarRailItem("Roster", screen == WarScreen.ROSTER, repo.reliefGapCount) { onPick(WarScreen.ROSTER) }
        WarRailItem("Signals", screen == WarScreen.SIGNALS, unread) { onPick(WarScreen.SIGNALS) }
        WarRailItem("Ledger", screen == WarScreen.LEDGER, null) { onPick(WarScreen.LEDGER) }
        WarRailItem("My Tag", screen == WarScreen.ME, null) { onPick(WarScreen.ME) }
        Spacer(Modifier.weight(1f))
        Text(
            "QUIT ROOM",
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBack).padding(8.dp),
            color = WarColors.Faint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun WarRailItem(label: String, active: Boolean, badge: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) WarColors.SirenDim else WarColors.BunkerDeep)
            .border(1.dp, if (active) WarColors.Siren else WarColors.PanelLine, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = WarColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        if (badge != null && badge > 0) {
            Text(
                "$badge",
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(WarColors.Siren).padding(horizontal = 8.dp, vertical = 2.dp),
                color = WarColors.BunkerDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
