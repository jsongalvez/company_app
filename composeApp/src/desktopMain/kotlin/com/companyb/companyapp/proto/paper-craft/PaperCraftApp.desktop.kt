package com.companyb.companyapp.proto.papercraft

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #842 — paper-craft desk shell: kraft desk board, washi day strip, pasted banner, tray rail.

enum class PcScreen(val title: String) {
    ONBOARDING("Cover · Onboarding"),
    LOGIN("Sheet 1 · Login"),
    BRANCHES("Sheet 2 · Branches"),
    HOME("Sheet 3 · Desk"),
    DAY("Sheet 4 · Branch Day"),
    SESSIONS("Sheet 5 · Sessions"),
    CLIENTS("Sheet 6 · Clients"),
    FINANCE("Sheet 7 · Remittance"),
    TEAM("Sheet 8 · Team"),
    MAILBOX("Sheet 9 · Mailbox"),
    AUDIT("Sheet 10 · Audit"),
    PROFILE("Sheet 11 · Profile"),
}

@Composable
fun PaperCraftProtoApp(onBack: () -> Unit) {
    val repo = remember { PaperCraftFakeRepo() }
    var screen by remember { mutableStateOf(PcScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("PaperCraftProto", "paper-craft prototype opened") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(PcColors.Desk)) {
        PcMasthead(repo = repo)
        PcDayStrip(repo = repo, onOpenDay = { screen = PcScreen.DAY })
        PcPastedBanner(day = repo.selectedDay)
        Row(Modifier.fillMaxSize()) {
            PcTray(
                screen = screen,
                loggedIn = loggedIn,
                unread = repo.notifications.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                when (screen) {
                    PcScreen.ONBOARDING -> PcOnboarding(repo = repo, onNext = { screen = PcScreen.LOGIN })
                    PcScreen.LOGIN -> PcLogin(repo = repo, onNext = { screen = PcScreen.BRANCHES })
                    PcScreen.BRANCHES -> PcBranchSelect(repo = repo, onNext = { screen = PcScreen.HOME })
                    PcScreen.HOME -> PcDesk(repo = repo, go = { screen = it })
                    PcScreen.DAY -> PcBranchDay(repo = repo, go = { screen = it })
                    PcScreen.SESSIONS -> PcSessions(repo = repo)
                    PcScreen.CLIENTS -> PcClients(repo = repo)
                    PcScreen.FINANCE -> PcFinance(repo = repo)
                    PcScreen.TEAM -> PcTeam(repo = repo)
                    PcScreen.MAILBOX -> PcMailbox(repo = repo)
                    PcScreen.AUDIT -> PcAuditList(repo = repo)
                    PcScreen.PROFILE -> PcProfile(repo = repo, onLogout = { screen = PcScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun PcMasthead(repo: PaperCraftFakeRepo) {
    Column(Modifier.fillMaxWidth().background(PcColors.Ink).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.background(PcColors.WashiPink, RoundedCornerShape(2.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("✂ PC", color = PcColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    fontFamily = PcHand)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    repo.currentBranch.name + " — Paper Craft Desk",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = PcHand,
                )
                Text(
                    "cut-paper layers · taped-down cards · " +
                        (repo.currentUser?.name ?: "no cut-out signed in") + " · " +
                        if (repo.clockedIn) "scissors down, on duty" else "scissors up, off duty",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = PcHand,
                )
                Text(
                    "04:00 Asia/Manila · ${repo.currentBranch.kind.name}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = PcHand,
                )
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().height(14.dp)
            .background(PcColors.WashiTeal),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(20) {
                Spacer(Modifier.width(8.dp).height(14.dp).background(Color.White.copy(alpha = 0.3f)))
            }
        }
    }
}

@Composable
private fun PcDayStrip(repo: PaperCraftFakeRepo, onOpenDay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PcColors.Desk)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repo.days.forEachIndexed { index, day ->
            val count = repo.sessions.count { it.dayId == day.id && it.branchId == repo.currentBranchId }
            val selected = day.id == repo.selectedDayId
            Column(
                Modifier.weight(1f)
                    .background(PcColors.Paper, RoundedCornerShape(2.dp))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) PcColors.Ink else PcColors.CutEdge.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp),
                    )
                    .clickable {
                        repo.selectedDayId = day.id
                        logInfo("PaperCraftProto", "picked sheet ${day.id}")
                        onOpenDay()
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(12.dp)
                        .background(washiFor(index), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(day.dow, color = PcColors.Faint, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, fontFamily = PcHand)
                Text(day.dateLabel, color = PcColors.Ink, fontSize = 16.sp,
                    fontWeight = FontWeight.Black, fontFamily = PcHand)
                Text(day.status.name, color = day.status.stamp(), fontSize = 10.sp,
                    fontWeight = FontWeight.Black, fontFamily = PcHand)
                Text("₱${repo.dayTotal(day.id)}", color = PcColors.Ink, fontSize = 12.sp,
                    fontFamily = PcHand)
                Text("$count cut-outs", color = PcColors.Faint, fontSize = 11.sp,
                    fontStyle = FontStyle.Italic, fontFamily = PcHand)
                if (day.isToday) {
                    Text("◉ today", color = PcColors.StampVoid, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, fontFamily = PcHand)
                }
            }
        }
    }
}

@Composable
private fun PcPastedBanner(day: PcDay) {
    Column(Modifier.fillMaxWidth().background(PcColors.Paper)) {
        PcWashi(color = PcColors.WashiPink, label = "${day.status.name} · Folio ${day.dow} ${day.dateLabel}")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PcSticker(text = day.status.name, color = day.status.stamp())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Sheet ${day.dow} ${day.dateLabel}",
                    color = PcColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PcHand,
                )
                Text(
                    "The sheet stays OPEN until 04:00 Asia/Manila next morning — then it is pasted to PAST.",
                    color = PcColors.Faint,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = PcHand,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PcTray(
    screen: PcScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (PcScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().background(PcColors.DeskDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text("CUT-OUT TRAY", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
            fontWeight = FontWeight.Black, fontFamily = PcHand)
        Spacer(Modifier.height(8.dp))
        PcScreen.entries.forEachIndexed { index, entry ->
            val locked = !loggedIn && entry != PcScreen.ONBOARDING && entry != PcScreen.LOGIN
            val label = entry.title + if (entry == PcScreen.MAILBOX && unread > 0) " ($unread)" else ""
            Text(
                label,
                color = when {
                    locked -> Color.White.copy(alpha = 0.4f)
                    entry == screen -> PcColors.Ink
                    else -> Color.White
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PcHand,
                modifier = Modifier
                    .background(
                        if (entry == screen) washiFor(index) else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    )
                    .clickable(enabled = !locked) { onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        PcCutGhost(text = "Fold up") { onBack() }
    }
}
