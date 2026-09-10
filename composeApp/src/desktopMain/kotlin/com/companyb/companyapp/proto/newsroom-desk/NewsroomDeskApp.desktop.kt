package com.companyb.companyapp.proto.newsroomdesk

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

// #833 — newsroom-desk shell: edition masthead, day budget banner, bureau rail + desk canvas.

enum class DeskScreen(val desk: String) {
    WELCOME("Budget Meeting"),
    SIGNIN("Press Passes"),
    BUREAUS("Bureaus"),
    ASSIGNMENT("Assignment Desk"),
    STORIES("Stories"),
    SOURCES("Sources"),
    BUSINESS("Business Office"),
    MASTHEAD("Masthead"),
    WIRE("Wire"),
    LEDGER("Ledger"),
    ME("My Press Card"),
}

@Composable
fun NewsroomDeskApp(onBack: () -> Unit) {
    val repo = remember { DeskFakeRepo() }
    var screen by remember { mutableStateOf(DeskScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("NewsroomDesk", "newsroom-desk prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(DeskColors.Paper)) {
        DeskMasthead(repo = repo)
        DeskDayBanner(status = repo.dayStatus, date = repo.operationalDate, onPick = { repo.dayStatus = it })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (loggedIn) {
                DeskBureauRail(
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
                        DeskScreen.WELCOME -> DeskWelcome(repo = repo, onNext = { screen = DeskScreen.SIGNIN })
                        DeskScreen.SIGNIN -> DeskSignin(repo = repo, onNext = { screen = DeskScreen.BUREAUS })
                        DeskScreen.BUREAUS -> DeskBureaus(repo = repo, onNext = { screen = DeskScreen.ASSIGNMENT })
                        DeskScreen.ASSIGNMENT -> DeskAssignment(repo = repo, go = { screen = it })
                        DeskScreen.STORIES -> DeskStories(repo = repo)
                        DeskScreen.SOURCES -> DeskSources(repo = repo)
                        DeskScreen.BUSINESS -> DeskBusinessOffice(repo = repo)
                        DeskScreen.MASTHEAD -> DeskRoster(repo = repo)
                        DeskScreen.WIRE -> DeskWire(repo = repo)
                        DeskScreen.LEDGER -> DeskLedger(repo = repo)
                        DeskScreen.ME -> DeskMyPass(repo = repo, onLogout = { screen = DeskScreen.SIGNIN })
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DeskMasthead(repo: DeskFakeRepo) {
    Column(Modifier.fillMaxWidth().background(DeskColors.Ink)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "THE DAILY LEDGER ● MORNING EDITION",
                color = DeskColors.Highlighter,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (repo.clockedIn) "● ON DEADLINE" else "○ OFF DEADLINE",
                color = if (repo.clockedIn) DeskColors.Highlighter else DeskColors.Faint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    repo.currentBranch.name.uppercase(),
                    color = DeskColors.Paper,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Desk ${repo.currentBranch.deskNo} — ${repo.currentBranch.bureauNote}",
                    color = DeskColors.Faint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeskSlugCell("08")
                    Text(":", color = DeskColors.Paper, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    DeskSlugCell("42")
                    Text(":", color = DeskColors.Paper, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    DeskSlugCell("MNL")
                }
                Text(
                    (repo.currentUser?.name ?: "no press card").uppercase(),
                    color = DeskColors.Paper,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DeskDayBanner(
    status: DeskDayStatus,
    date: String,
    onPick: (DeskDayStatus) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(DeskColors.Highlighter)
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeskFlagChip(text = status.name, color = status.band())
            Text(
                "Branch day $date",
                color = DeskColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            DeskDayStatus.entries.forEach { option ->
                DeskFlagChip(
                    text = option.name,
                    color = option.band(),
                    selected = option == status,
                    onClick = { onPick(option) },
                )
            }
        }
        Text(
            "Edition rolls over at 04:00 Asia/Manila — afterwards this day reads PAST and only a Coordinator edits it.",
            color = DeskColors.InkSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DeskBureauRail(
    screen: DeskScreen,
    unread: Int,
    onPick: (DeskScreen) -> Unit,
    onBack: () -> Unit,
) {
    val stops = listOf(
        DeskScreen.ASSIGNMENT,
        DeskScreen.STORIES,
        DeskScreen.SOURCES,
        DeskScreen.BUSINESS,
        DeskScreen.MASTHEAD,
        DeskScreen.WIRE,
        DeskScreen.LEDGER,
        DeskScreen.ME,
    )
    Column(
        Modifier.width(224.dp).fillMaxHeight().background(DeskColors.PaperDeep)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("BUREAUS", color = DeskColors.Pencil, fontSize = 12.sp, fontWeight = FontWeight.Black)
        stops.forEach { stop ->
            val active = stop == screen
            val label = if (stop == DeskScreen.WIRE && unread > 0) "${stop.desk} ($unread)" else stop.desk
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) DeskColors.Ink else DeskColors.Card)
                    .clickable { onPick(stop) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    label.uppercase(),
                    color = if (active) DeskColors.Highlighter else DeskColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DeskColors.Card)
                .clickable { onBack() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("LEAVE NEWSROOM", color = DeskColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}
