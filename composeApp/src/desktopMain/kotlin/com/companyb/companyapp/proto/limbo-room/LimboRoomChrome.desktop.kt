package com.companyb.companyapp.proto.limboroom

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

// #824 — hall chrome: neon now-serving board, branch-day banner, door rail.

@Composable
fun LrNowServing(store: LrStore) {
    val branch = store.currentBranch
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color0x0B0A1A())
            .border(1.dp, LrAmberDim, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✦ LIMBO ROOM — WAITING HALL", fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, fontWeight = FontWeight.Bold)
                Text(
                    branch?.let { "${it.name} · ${it.kind.name}" } ?: "no branch",
                    fontFamily = LrSans,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = LrCream,
                )
                Text(
                    "operational boundary 04:00 Asia/Manila — the hall clock, never the wall clock",
                    fontFamily = LrMono,
                    fontSize = 11.sp,
                    color = LrMuted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("NOW SERVING", fontFamily = LrMono, fontSize = 10.sp, color = LrMuted)
                Text("A-103", fontFamily = LrMono, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = LrAmber)
            }
        }
    }
}

private fun Color0x0B0A1A() = androidx.compose.ui.graphics.Color(0xFF0B0A1A)

@Composable
fun LrDayBanner(store: LrStore) {
    val days = listOf(
        Triple("2026-09-07", "Tondo · REMITTED", LrDayStatus.REMITTED),
        Triple("2026-09-08", "Cebu · PAST", LrDayStatus.PAST),
        Triple("2026-09-09", "Makati · OPEN", LrDayStatus.OPEN),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { (date, label, status) ->
            val on = store.dayFilter == date
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) LrAmber else LrNightSoft)
                    .border(1.dp, if (on) LrAmber else LrNightLine, RoundedCornerShape(10.dp))
                    .clickable { store.dayFilter = date }
                    .padding(10.dp),
            ) {
                Column {
                    Text(date, fontFamily = LrMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) LrNight else LrCream)
                    Text(label, fontFamily = LrMono, fontSize = 11.sp, color = if (on) LrNight else LrMuted)
                    Text(status.name, fontFamily = LrMono, fontSize = 10.sp, color = if (on) LrNight else LrSky)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "day ${store.dayFilter}: PAST locks at 04:00 Manila, REMITTED needs a coordinator key. Drafts overlap freely.",
        fontFamily = LrMono,
        fontSize = 11.sp,
        color = LrMuted,
    )
}

private val doorLabels = listOf(
    LrRoom.HOME to "⌂ Lobby",
    LrRoom.SESSIONS to "◉ Sessions",
    LrRoom.CLIENTS to "◎ Clients",
    LrRoom.FINANCE to "▣ Finance",
    LrRoom.TEAM to "▦ Team",
    LrRoom.MAIL to "✉ Mail",
    LrRoom.AUDIT to "⎙ Audit",
    LrRoom.PROFILE to "◐ Profile",
)

@Composable
fun LrDoorRail(store: LrStore) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .fillMaxHeight()
            .background(LrNightSoft)
            .border(1.dp, LrNightLine)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("DOORS", fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, fontWeight = FontWeight.Bold)
        doorLabels.forEach { (room, label) ->
            val on = store.room == room
            val unread = if (room == LrRoom.MAIL) store.notifications.count { !it.read } else 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(if (on) LrAmber else Color0x241D47())
                    .border(1.dp, if (on) LrAmber else LrNightLine, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .clickable { store.room = room }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unread > 0) "$label ($unread)" else label,
                    fontFamily = LrMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) LrNight else LrCream,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "arch doors stay shut for ONBOARDING — the limbo room is the whole hall",
            fontFamily = LrMono,
            fontSize = 10.sp,
            color = LrMuted,
            lineHeight = 14.sp,
        )
    }
}

private fun Color0x241D47() = androidx.compose.ui.graphics.Color(0xFF241D47)

@Composable
fun LrTicketStub(number: String, name: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LrCream)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(LrNight, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("TICKET", fontFamily = LrMono, fontSize = 9.sp, color = LrAmber)
            Text(number, fontFamily = LrMono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LrCream)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = LrSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LrInk)
            Text(hint, fontFamily = LrMono, fontSize = 11.sp, color = Color0x6B5F45())
        }
    }
}

private fun Color0x6B5F45() = androidx.compose.ui.graphics.Color(0xFF6B5F45)
