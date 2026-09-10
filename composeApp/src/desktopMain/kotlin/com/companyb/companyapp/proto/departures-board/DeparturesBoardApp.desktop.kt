package com.companyb.companyapp.proto.departuresboard

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

// #830 — departures-board shell: station header, day banner, platform rail + board canvas.

enum class BoardScreen(val board: String) {
    WELCOME("Ticket Hall"),
    SIGNIN("Gates"),
    PLATFORMS("Platforms"),
    CONCOURSE("Concourse"),
    DEPARTURES("Departures"),
    PASSENGERS("Passengers"),
    TICKETS("Ticket Office"),
    CREW("Crew"),
    SIGNALS("Signals"),
    LEDGER("Logbook"),
    ME("My Pass"),
}

@Composable
fun DeparturesBoardApp(onBack: () -> Unit) {
    val repo = remember { BoardFakeRepo() }
    var screen by remember { mutableStateOf(BoardScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("DeparturesBoard", "departures-board prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(BoardColors.Yard)) {
        BoardMasthead(repo = repo)
        BoardDayBanner(status = repo.dayStatus, date = repo.operationalDate, onPick = { repo.dayStatus = it })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (loggedIn) {
                BoardPlatformRail(
                    screen = screen,
                    unread = repo.notifications.count { !it.read },
                    onPick = { screen = it },
                    onBack = onBack,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (screen) {
                        BoardScreen.WELCOME -> BoardWelcome(repo = repo, onNext = { screen = BoardScreen.SIGNIN })
                        BoardScreen.SIGNIN -> BoardSignin(repo = repo, onNext = { screen = BoardScreen.PLATFORMS })
                        BoardScreen.PLATFORMS -> BoardPlatforms(repo = repo, onNext = { screen = BoardScreen.CONCOURSE })
                        BoardScreen.CONCOURSE -> BoardConcourse(repo = repo, go = { screen = it })
                        BoardScreen.DEPARTURES -> BoardDepartures(repo = repo)
                        BoardScreen.PASSENGERS -> BoardPassengers(repo = repo)
                        BoardScreen.TICKETS -> BoardTicketOffice(repo = repo)
                        BoardScreen.CREW -> BoardCrew(repo = repo)
                        BoardScreen.SIGNALS -> BoardSignals(repo = repo)
                        BoardScreen.LEDGER -> BoardLedger(repo = repo)
                        BoardScreen.ME -> BoardMe(repo = repo, onLogout = { screen = BoardScreen.SIGNIN })
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BoardMasthead(repo: BoardFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(BoardColors.YardDeep)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "COMPANY RAIL ● ALL LINES",
                color = BoardColors.Amber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.name.uppercase(),
                color = BoardColors.Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Platform ${repo.currentBranch.platformNo} — ${repo.currentBranch.lineNote}",
                color = BoardColors.Faint,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardFlapCell("08")
                Text(":", color = BoardColors.Amber, fontSize = 18.sp, fontWeight = FontWeight.Black)
                BoardFlapCell("42")
                Text(":", color = BoardColors.Amber, fontSize = 18.sp, fontWeight = FontWeight.Black)
                BoardFlapCell("MNL")
            }
            Text(
                if (repo.clockedIn) "● STATION OPEN" else "○ STATION CLOSED",
                color = if (repo.clockedIn) BoardColors.Green else BoardColors.Faint,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                (repo.currentUser?.name ?: "no pass holder").uppercase(),
                color = BoardColors.CreamDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BoardDayBanner(
    status: BoardDayStatus,
    date: String,
    onPick: (BoardDayStatus) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(BoardColors.Panel)
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoardFlagChip(text = status.name, color = status.band())
            Text(
                "Branch day $date",
                color = BoardColors.Cream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            BoardDayStatus.entries.forEach { option ->
                BoardFlagChip(
                    text = option.name,
                    color = option.band(),
                    selected = option == status,
                    onClick = { onPick(option) },
                )
            }
        }
        Text(
            "Timetable rolls over at 04:00 Asia/Manila — afterwards this day reads PAST and only a Coordinator edits it.",
            color = BoardColors.Faint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BoardPlatformRail(
    screen: BoardScreen,
    unread: Int,
    onPick: (BoardScreen) -> Unit,
    onBack: () -> Unit,
) {
    val stops = listOf(
        BoardScreen.CONCOURSE,
        BoardScreen.DEPARTURES,
        BoardScreen.PASSENGERS,
        BoardScreen.TICKETS,
        BoardScreen.CREW,
        BoardScreen.SIGNALS,
        BoardScreen.LEDGER,
        BoardScreen.ME,
    )
    Column(
        Modifier.width(218.dp).fillMaxHeight().background(BoardColors.YardDeep)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PLATFORMS", color = BoardColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Black)
        stops.forEach { stop ->
            val active = stop == screen
            val label = if (stop == BoardScreen.SIGNALS && unread > 0) "${stop.board} ($unread)" else stop.board
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) BoardColors.Amber else BoardColors.Flap)
                    .clickable { onPick(stop) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    label.uppercase(),
                    color = if (active) BoardColors.AmberInk else BoardColors.Cream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BoardColors.Flap)
                .clickable { onBack() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("EXIT STATION", color = BoardColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}
