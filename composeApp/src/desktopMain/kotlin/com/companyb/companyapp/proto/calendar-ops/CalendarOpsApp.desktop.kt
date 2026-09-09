package com.companyb.companyapp.proto.calendarops

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #775 — calendar-ops shell: week-first planner, day band, nav rail, screen router. Fake data only.

enum class CoScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    WEEK("Week"),
    DAY("Day"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun CalendarOpsProtoApp(onBack: () -> Unit) {
    val repo = remember { CalendarOpsFakeRepo() }
    var screen by remember { mutableStateOf(CoScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("CalendarOpsProto", "calendar-ops prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(CoColors.Paper)) {
        CoMasthead(repo = repo)
        CoWeekStrip(repo = repo, onOpenDay = { screen = CoScreen.DAY })
        CoDayBanner(day = repo.selectedDay)
        Row(Modifier.fillMaxSize()) {
            CoRail(
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
                    CoScreen.ONBOARDING -> CoOnboarding(repo = repo, onNext = { screen = CoScreen.LOGIN })
                    CoScreen.LOGIN -> CoLogin(repo = repo, onNext = { screen = CoScreen.BRANCHES })
                    CoScreen.BRANCHES -> CoBranchSelect(repo = repo, onNext = { screen = CoScreen.WEEK })
                    CoScreen.WEEK -> CoWeekHome(repo = repo, go = { screen = it })
                    CoScreen.DAY -> CoDayDetail(repo = repo, go = { screen = it })
                    CoScreen.SESSIONS -> CoSessions(repo = repo)
                    CoScreen.CLIENTS -> CoClients(repo = repo)
                    CoScreen.FINANCE -> CoFinance(repo = repo)
                    CoScreen.TEAM -> CoTeam(repo = repo)
                    CoScreen.MAILBOX -> CoMailbox(repo = repo)
                    CoScreen.AUDIT -> CoAuditList(repo = repo)
                    CoScreen.PROFILE -> CoProfile(repo = repo, onLogout = { screen = CoScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun CoMasthead(repo: CalendarOpsFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(CoColors.Paper)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                repo.currentBranch.name.uppercase() + " — WEEK PLANNER",
                color = CoColors.Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Sep 7 – 13, 2026  ·  ${repo.currentBranch.kind.name}  ·  operational boundary 04:00 Asia/Manila",
                color = CoColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.weight(1f))
        val user = repo.currentUser
        Column(horizontalAlignment = Alignment.End) {
            Text(
                user?.name ?: "SIGNED OUT",
                color = CoColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (repo.clockedIn) "● CLOCKED IN" else "○ OFF DUTY",
                color = if (repo.clockedIn) CoColors.Open else CoColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CoWeekStrip(repo: CalendarOpsFakeRepo, onOpenDay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CoColors.Paper)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repo.days.forEach { day ->
            val count = repo.sessions.count { it.dayId == day.id && it.branchId == repo.currentBranchId }
            val done = repo.sessions.count {
                it.dayId == day.id && it.branchId == repo.currentBranchId &&
                    it.status == CoSessionStatus.COMPLETED
            }
            val selected = day.id == repo.selectedDayId
            Column(
                Modifier.weight(1f)
                    .background(day.status.band(), RoundedCornerShape(10.dp))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (day.isToday) CoColors.Today else CoColors.Line,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        repo.selectedDayId = day.id
                        logInfo("CalendarOpsProto", "opened day ${day.id}")
                        onOpenDay()
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.dow, color = CoColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(day.dateLabel, color = CoColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    day.status.name,
                    color = day.status.ink(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("$done/$count done", color = CoColors.Ink, fontSize = 11.sp)
                if (day.isToday) {
                    Text("TODAY", color = CoColors.Today, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun CoDayBanner(day: CoDay) {
    Row(
        Modifier.fillMaxWidth().background(day.status.band())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BRANCH DAY ${day.dow} ${day.dateLabel}: ${day.status.name}",
            color = day.status.ink(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Editable until 04:00 Asia/Manila next morning — then the day turns PAST lazily.",
            color = CoColors.Ink,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CoRail(
    screen: CoScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (CoScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.width(168.dp).fillMaxHeight().background(CoColors.Paper)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        CoScreen.entries.forEach { entry ->
            val locked = !loggedIn && entry != CoScreen.ONBOARDING && entry != CoScreen.LOGIN
            val label = if (entry == CoScreen.MAILBOX && unread > 0) {
                "${entry.title} ($unread)"
            } else {
                entry.title
            }
            Text(
                label,
                color = when {
                    locked -> CoColors.Muted.copy(alpha = 0.5f)
                    entry == screen -> CoColors.Paper
                    else -> CoColors.Ink
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
                    .background(
                        if (entry == screen) CoColors.Ink else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(enabled = !locked) { onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "← Exit prototype",
            color = CoColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
    }
}
