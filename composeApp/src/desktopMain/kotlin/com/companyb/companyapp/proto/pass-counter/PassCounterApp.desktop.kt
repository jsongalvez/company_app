package com.companyb.companyapp.proto.passcounter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #832 — pass-counter shell: heat-lamp banner, branch-day band, ticket-rail nav,
// screen router. The pass rail is home: pending tickets, the bell, the waste log.

enum class PcScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Stations"),
    PASS("The Pass"),
    TICKETS("Tickets"),
    GUESTS("Guests"),
    CASHUP("Cash-up"),
    CREW("Crew"),
    MAILBOX("Mailbox"),
    LEDGER("Ledger"),
    PROFILE("Profile"),
}

@Composable
fun PassCounterProtoApp() {
    val repo = remember { PassCounterFakeRepo() }
    var screen by remember { mutableStateOf(PcScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("PassCounterProto", "pass-counter prototype launched") }

    PassCounterTheme {
        val loggedIn = repo.currentUserId != null
        Column(Modifier.fillMaxSize().background(PcColors.Char)) {
            PcBanner(repo = repo)
            PcDayBand(repo = repo)
            Row(Modifier.fillMaxSize()) {
                PcRail(
                    screen = screen,
                    loggedIn = loggedIn,
                    unread = repo.notices.count { !it.read },
                    bell = repo.bellCount(),
                    onPick = { screen = it },
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(PcColors.Paper)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                ) {
                    when (screen) {
                        PcScreen.ONBOARDING -> PcOnboarding(repo = repo, onNext = { screen = PcScreen.LOGIN })
                        PcScreen.LOGIN -> PcLogin(repo = repo, onNext = { screen = PcScreen.BRANCHES })
                        PcScreen.BRANCHES -> PcStationSelect(repo = repo, onNext = { screen = PcScreen.PASS })
                        PcScreen.PASS -> PcPass(repo = repo, go = { screen = it })
                        PcScreen.TICKETS -> PcTickets(repo = repo)
                        PcScreen.GUESTS -> PcGuests(repo = repo)
                        PcScreen.CASHUP -> PcCashUp(repo = repo)
                        PcScreen.CREW -> PcCrew(repo = repo)
                        PcScreen.MAILBOX -> PcMailbox(repo = repo)
                        PcScreen.LEDGER -> PcLedger(repo = repo)
                        PcScreen.PROFILE -> PcProfile(repo = repo, onLogout = { screen = PcScreen.LOGIN })
                    }
                }
            }
        }
    }
}

@Composable
private fun PcBanner(repo: PassCounterFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(PcColors.Char).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("THE PASS", color = PcColors.LampSoft, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentBranch.name.uppercase()}  ·  ${repo.serviceDate}  ·  KITCHEN PASS COUNTER",
                color = PcColors.Lamp, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PcSlip,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (repo.clockedIn) "● ON THE LINE" else "○ OFF THE LINE",
                color = if (repo.clockedIn) PcColors.Lamp else PcColors.Muted,
                fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = PcSlip,
            )
            Text(
                (repo.currentUser?.name ?: "signed out").uppercase(),
                color = PcColors.Ticket, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "${repo.pendingFor(repo.currentBranchId).size} firing · " +
                    "${repo.bellCount()} belled · ₱${repo.branchBanked(repo.currentBranchId)} banked",
                color = PcColors.Muted, fontSize = 12.sp, fontFamily = PcSlip,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(4.dp).background(PcColors.Lamp))
}

@Composable
private fun PcDayBand(repo: PassCounterFakeRepo) {
    val status = repo.dayStatus
    val band = when (status) {
        PcDayStatus.OPEN -> PcColors.Fire
        PcDayStatus.PAST -> PcColors.Past
        PcDayStatus.REMITTED -> PcColors.Remitted
    }
    Column(Modifier.fillMaxWidth().background(band).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SERVICE DAY: ${status.name}",
                color = PcColors.Ticket, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = PcSlip,
            )
            Spacer(Modifier.width(16.dp))
            PcDayStatus.entries.forEach { option ->
                val selected = option == status
                Box(
                    Modifier.background(
                        if (selected) PcColors.Ticket else band,
                        RoundedCornerShape(12.dp),
                    ).clickable { repo.setDayStatus(repo.currentBranchId, option) }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        option.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PcSlip,
                        color = if (selected) band else PcColors.Ticket,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Day boundary 04:00 Asia/Manila",
                color = PcColors.Ticket, fontSize = 11.sp, fontFamily = PcSlip,
            )
        }
    }
}

@Composable
private fun PcRail(
    screen: PcScreen,
    loggedIn: Boolean,
    unread: Int,
    bell: Int,
    onPick: (PcScreen) -> Unit,
) {
    Column(
        Modifier.width(180.dp).fillMaxHeight().background(PcColors.Steel)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Text(
            "PASS RAIL", color = PcColors.Lamp, fontSize = 11.sp, fontWeight = FontWeight.Black,
            fontFamily = PcSlip, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        PcScreen.entries.forEach { entry ->
            val locked = !loggedIn && entry != PcScreen.ONBOARDING && entry != PcScreen.LOGIN
            val selected = entry == screen
            var label = entry.title
            if (entry == PcScreen.MAILBOX && unread > 0) label = "${entry.title} ($unread)"
            if (entry == PcScreen.PASS) label = "${entry.title} ($bell 🔔)"
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        if (selected) PcColors.Lamp else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp),
                    ).clickable { if (!locked) onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    (if (locked) "◌ " else if (entry == PcScreen.PASS) "◉ " else "# ") + label,
                    color = when {
                        selected -> PcColors.Char
                        locked -> PcColors.SteelSoft
                        else -> PcColors.Ticket
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Heat lamps on.\nTickets face the line.",
            color = PcColors.Muted, fontSize = 11.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}
