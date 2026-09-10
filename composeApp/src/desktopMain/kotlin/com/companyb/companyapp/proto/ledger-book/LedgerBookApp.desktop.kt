package com.companyb.companyapp.proto.ledgerbook

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

// #835 — ledger-book shell: bookplate masthead, ribbon day markers, stamped banner, contents rail.

enum class LbScreen(val title: String) {
    ONBOARDING("Preface · Onboarding"),
    LOGIN("Ch. I · Login"),
    BRANCHES("Ch. II · Branches"),
    HOME("Ch. III · Counter"),
    DAY("Ch. IV · Day Folio"),
    SESSIONS("Ch. V · Sessions"),
    CLIENTS("Ch. VI · Clients"),
    FINANCE("Ch. VII · Remittance"),
    TEAM("Ch. VIII · Team"),
    MAILBOX("Ch. IX · Mailbox"),
    AUDIT("Ch. X · Audit"),
    PROFILE("Ch. XI · Profile"),
}

@Composable
fun LedgerBookProtoApp(onBack: () -> Unit) {
    val repo = remember { LedgerBookFakeRepo() }
    var screen by remember { mutableStateOf(LbScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("LedgerBookProto", "ledger-book prototype opened") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(LbColors.Page)) {
        LbBookplate(repo = repo)
        LbRibbon(repo = repo, onOpenDay = { screen = LbScreen.DAY })
        LbStampBanner(day = repo.selectedDay)
        Row(Modifier.fillMaxSize()) {
            LbContents(
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
                    LbScreen.ONBOARDING -> LbOnboarding(repo = repo, onNext = { screen = LbScreen.LOGIN })
                    LbScreen.LOGIN -> LbLogin(repo = repo, onNext = { screen = LbScreen.BRANCHES })
                    LbScreen.BRANCHES -> LbBranchSelect(repo = repo, onNext = { screen = LbScreen.HOME })
                    LbScreen.HOME -> LbCounter(repo = repo, go = { screen = it })
                    LbScreen.DAY -> LbDayFolio(repo = repo, go = { screen = it })
                    LbScreen.SESSIONS -> LbSessions(repo = repo)
                    LbScreen.CLIENTS -> LbClients(repo = repo)
                    LbScreen.FINANCE -> LbFinance(repo = repo)
                    LbScreen.TEAM -> LbTeam(repo = repo)
                    LbScreen.MAILBOX -> LbMailbox(repo = repo)
                    LbScreen.AUDIT -> LbAuditList(repo = repo)
                    LbScreen.PROFILE -> LbProfile(repo = repo, onLogout = { screen = LbScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun LbBookplate(repo: LedgerBookFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(LbColors.Spine)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.border(1.dp, LbColors.Page.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ex Libris", color = LbColors.Page.copy(alpha = 0.75f), fontSize = 11.sp,
                fontStyle = FontStyle.Italic, fontFamily = LbSerif)
            Text("CB", color = LbColors.Page, fontSize = 20.sp, fontWeight = FontWeight.Black,
                fontFamily = LbSerif)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                repo.currentBranch.name + " — Bound Ledger",
                color = LbColors.Page,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LbSerif,
            )
            Text(
                "kept in a handwritten voice · ruled & stamped · " +
                    (repo.currentUser?.name ?: "unsigned hand") + " · " +
                    if (repo.clockedIn) "pen down, on duty" else "pen up, off duty",
                color = LbColors.Page.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = LbSerif,
            )
            Text(
                "04:00 Asia/Manila · ${repo.currentBranch.kind.name}",
                color = LbColors.Page.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontFamily = LbSerif,
            )
        }
    }
}

@Composable
private fun LbRibbon(repo: LedgerBookFakeRepo, onOpenDay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(LbColors.Page)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repo.days.forEach { day ->
            val count = repo.sessions.count { it.dayId == day.id && it.branchId == repo.currentBranchId }
            val selected = day.id == repo.selectedDayId
            Column(
                Modifier.weight(1f)
                    .background(LbColors.Card, RoundedCornerShape(2.dp))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (day.isToday) LbColors.Margin else LbColors.Ruled,
                        shape = RoundedCornerShape(2.dp),
                    )
                    .clickable {
                        repo.selectedDayId = day.id
                        logInfo("LedgerBookProto", "turned to folio ${day.id}")
                        onOpenDay()
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.dow, color = LbColors.Faint, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, fontFamily = LbSerif)
                Text(day.dateLabel, color = LbColors.Ink, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                Text(day.status.name, color = day.status.stamp(), fontSize = 10.sp,
                    fontWeight = FontWeight.Black, fontFamily = LbSerif)
                Text("₱${repo.dayTotal(day.id)}", color = LbColors.Ink, fontSize = 12.sp,
                    fontFamily = LbSerif)
                Text("$count entries", color = LbColors.Faint, fontSize = 11.sp,
                    fontStyle = FontStyle.Italic, fontFamily = LbSerif)
                if (day.isToday) {
                    Text("❦ to-day", color = LbColors.Margin, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, fontFamily = LbSerif)
                }
            }
        }
    }
}

@Composable
private fun LbStampBanner(day: LbDay) {
    Row(
        Modifier.fillMaxWidth().background(LbColors.Card)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LbStamp(text = day.status.name, color = day.status.stamp())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Folio ${day.dow} ${day.dateLabel}",
                color = LbColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LbSerif,
            )
            Text(
                "The day is ruled OPEN until 04:00 Asia/Manila next morning — then it yellows to PAST.",
                color = LbColors.Faint,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = LbSerif,
            )
        }
    }
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).background(LbColors.Margin.copy(alpha = 0.6f)),
    )
}

@Composable
private fun LbContents(
    screen: LbScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (LbScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().background(LbColors.Spine)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text("CONTENTS", color = LbColors.Page.copy(alpha = 0.7f), fontSize = 11.sp,
            fontWeight = FontWeight.Black, fontFamily = LbSerif)
        Spacer(Modifier.height(8.dp))
        LbScreen.entries.forEach { entry ->
            val locked = !loggedIn && entry != LbScreen.ONBOARDING && entry != LbScreen.LOGIN
            val label = entry.title + if (entry == LbScreen.MAILBOX && unread > 0) " ($unread)" else ""
            Text(
                label,
                color = when {
                    locked -> LbColors.SpineLight
                    entry == screen -> LbColors.Spine
                    else -> LbColors.Page
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LbSerif,
                modifier = Modifier
                    .background(
                        if (entry == screen) LbColors.Page else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    )
                    .clickable(enabled = !locked) { onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        LbPencil(text = "Close book") { onBack() }
    }
}
