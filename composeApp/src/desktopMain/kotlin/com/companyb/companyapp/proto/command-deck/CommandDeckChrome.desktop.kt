package com.companyb.companyapp.proto.commanddeck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #828 — deck chrome: command strip, mission-clock day banner, station rail,
// and the restrained alert board (klaxon red only, never routine glow).

@Composable
fun CdCommandStrip(store: CdStore) {
    val branch = store.currentBranch
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CdConsole)
            .border(1.dp, CdGlowDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("◆ COMMAND DECK — STARSHIP OPERATIONS", fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, fontWeight = FontWeight.Bold)
                Text(
                    branch?.let { "${it.name} · ${it.kind.name}" } ?: "no vessel",
                    fontFamily = CdSans,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = CdPaper,
                )
                Text(
                    "mission clock 04:00 Asia/Manila — the deck clock, never the wall clock",
                    fontFamily = CdMono,
                    fontSize = 11.sp,
                    color = CdFog,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("OFFICER", fontFamily = CdMono, fontSize = 10.sp, color = CdFog)
                Text(
                    store.currentUser?.name ?: "—",
                    fontFamily = CdMono,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CdPaper,
                )
                Text(
                    if (store.clockedIn) "● ON SHIFT" else "○ OFF SHIFT",
                    fontFamily = CdMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (store.clockedIn) CdPhos else CdFog,
                )
            }
        }
    }
}

@Composable
fun CdDayBanner(store: CdStore) {
    val days = listOf(
        Triple("2026-09-07", "Tondo · REMITTED", CdDayStatus.REMITTED),
        Triple("2026-09-08", "Cebu · PAST", CdDayStatus.PAST),
        Triple("2026-09-09", "Makati · OPEN", CdDayStatus.OPEN),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { (date, label, status) ->
            val on = store.dayFilter == date
            val tone = when (status) {
                CdDayStatus.OPEN -> CdPhos
                CdDayStatus.PAST -> CdAmber
                CdDayStatus.REMITTED -> CdGlow
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) CdDeckSoft else CdConsole)
                    .border(1.dp, if (on) tone else CdLine, RoundedCornerShape(8.dp))
                    .clickable { store.dayFilter = date }
                    .padding(10.dp),
            ) {
                Column {
                    Text(date, fontFamily = CdMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                    Text(label, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                    Text(status.name, fontFamily = CdMono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tone)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "day ${store.dayFilter}: PAST locks at 04:00 Manila, REMITTED needs a coordinator key. Drafts overlap freely.",
        fontFamily = CdMono,
        fontSize = 11.sp,
        color = CdFog,
    )
}

@Composable
fun CdAlertBoard(store: CdStore) {
    val klaxons = store.klaxons()
    if (klaxons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CdConsole)
                .border(1.dp, CdGlowDim, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("● ", fontFamily = CdMono, fontSize = 12.sp, color = CdPhos)
                Text(
                    "ALL DECKS QUIET — klaxon silent by policy; routine counts glow, they never scream",
                    fontFamily = CdMono,
                    fontSize = 12.sp,
                    color = CdFog,
                )
            }
        }
        return
    }
    klaxons.forEach { (level, body) ->
        val tone = if (level == "KLAXON") CdKlaxon else CdAmber
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CdConsole)
                .border(1.dp, tone, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CdGlowTag(level, tone)
                Spacer(Modifier.width(10.dp))
                Text(body, fontFamily = CdMono, fontSize = 12.sp, color = CdPaper, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val stationLabels = listOf(
    CdStation.HELM to "◈ HELM",
    CdStation.SESSIONS to "◉ SESSIONS",
    CdStation.CLIENTS to "◎ CLIENTS",
    CdStation.FINANCE to "▣ FINANCE",
    CdStation.TEAM to "▦ TEAM",
    CdStation.MAIL to "✉ MAIL",
    CdStation.AUDIT to "⎙ AUDIT",
    CdStation.PROFILE to "◐ PROFILE",
)

@Composable
fun CdStationRail(store: CdStore) {
    Column(
        modifier = Modifier
            .width(172.dp)
            .fillMaxHeight()
            .background(CdDeck)
            .border(1.dp, CdLine)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("STATIONS", fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, fontWeight = FontWeight.Bold)
        stationLabels.forEach { (station, label) ->
            val on = store.station == station
            val unread = if (station == CdStation.MAIL) store.notifications.count { !it.read } else 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) CdGlow else CdConsole)
                    .border(1.dp, if (on) CdGlow else CdLine, RoundedCornerShape(6.dp))
                    .clickable { store.station = station }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unread > 0) "$label ($unread)" else label,
                    fontFamily = CdMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) CdVoid else CdPaper,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "stations stay dark for ONBOARDING — the observation deck is the whole ship",
            fontFamily = CdMono,
            fontSize = 10.sp,
            color = CdFog,
            lineHeight = 14.sp,
        )
    }
}

@Composable
fun CdSignalTicket(number: String, name: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CdDeckSoft)
            .border(1.dp, CdGlowDim, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(CdConsole, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("SIGNAL", fontFamily = CdMono, fontSize = 9.sp, color = CdGlow)
            Text(number, fontFamily = CdMono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CdPaper)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = CdSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CdPaper)
            Text(hint, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
}
