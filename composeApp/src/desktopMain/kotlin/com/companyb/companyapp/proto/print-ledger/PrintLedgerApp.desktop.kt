package com.companyb.companyapp.proto.printledger

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

// #776 — print-ledger shell: receipt masthead, ledger day rows, stamp banner, index rail.

enum class PlScreen(val title: String) {
    ONBOARDING("00 Onboarding"),
    LOGIN("01 Login"),
    BRANCHES("02 Branches"),
    HOME("03 Counter"),
    DAY("04 Day Sheet"),
    SESSIONS("05 Sessions"),
    CLIENTS("06 Clients"),
    FINANCE("07 Receipts"),
    TEAM("08 Team"),
    MAILBOX("09 Mailbox"),
    AUDIT("10 Audit"),
    PROFILE("11 Profile"),
}

@Composable
fun PrintLedgerProtoApp(onBack: () -> Unit) {
    val repo = remember { PrintLedgerFakeRepo() }
    var screen by remember { mutableStateOf(PlScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("PrintLedgerProto", "print-ledger prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(PlColors.Sheet)) {
        PlMasthead(repo = repo)
        PlDayLedger(repo = repo, onOpenDay = { screen = PlScreen.DAY })
        PlStampBanner(day = repo.selectedDay)
        Row(Modifier.fillMaxSize()) {
            PlIndex(
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
                    PlScreen.ONBOARDING -> PlOnboarding(repo = repo, onNext = { screen = PlScreen.LOGIN })
                    PlScreen.LOGIN -> PlLogin(repo = repo, onNext = { screen = PlScreen.BRANCHES })
                    PlScreen.BRANCHES -> PlBranchSelect(repo = repo, onNext = { screen = PlScreen.HOME })
                    PlScreen.HOME -> PlCounter(repo = repo, go = { screen = it })
                    PlScreen.DAY -> PlDaySheet(repo = repo, go = { screen = it })
                    PlScreen.SESSIONS -> PlSessions(repo = repo)
                    PlScreen.CLIENTS -> PlClients(repo = repo)
                    PlScreen.FINANCE -> PlFinance(repo = repo)
                    PlScreen.TEAM -> PlTeam(repo = repo)
                    PlScreen.MAILBOX -> PlMailbox(repo = repo)
                    PlScreen.AUDIT -> PlAuditList(repo = repo)
                    PlScreen.PROFILE -> PlProfile(repo = repo, onLogout = { screen = PlScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun PlMasthead(repo: PrintLedgerFakeRepo) {
    Column(
        Modifier.fillMaxWidth().background(PlColors.Slip)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "* * *  C O M P A N Y B  * * *",
            color = PlColors.Faint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlMono,
        )
        Text(
            repo.currentBranch.name.uppercase() + " — PRINT LEDGER",
            color = PlColors.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val user = repo.currentUser
            Text(
                "TEL 04:00 Asia/Manila  ·  ${repo.currentBranch.kind.name}  ·  " +
                    (user?.name ?: "SIGNED OUT") + "  ·  " +
                    if (repo.clockedIn) "ON DUTY" else "OFF DUTY",
                color = PlColors.Faint,
                fontSize = 12.sp,
                fontFamily = PlMono,
            )
        }
    }
}

@Composable
private fun PlDayLedger(repo: PrintLedgerFakeRepo, onOpenDay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PlColors.Sheet)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repo.days.forEach { day ->
            val count = repo.sessions.count { it.dayId == day.id && it.branchId == repo.currentBranchId }
            val selected = day.id == repo.selectedDayId
            Column(
                Modifier.weight(1f)
                    .background(PlColors.Slip, RoundedCornerShape(4.dp))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (day.isToday) PlColors.StampVoid else PlColors.Rule,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .clickable {
                        repo.selectedDayId = day.id
                        logInfo("PrintLedgerProto", "opened day ${day.id}")
                        onOpenDay()
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.dow, color = PlColors.Faint, fontSize = 11.sp,
                    fontWeight = FontWeight.Black, fontFamily = PlMono)
                Text(day.dateLabel, color = PlColors.Ink, fontSize = 15.sp,
                    fontWeight = FontWeight.Black, fontFamily = PlMono)
                Text(
                    day.status.name,
                    color = day.status.stamp(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = PlMono,
                )
                Text("PHP ${repo.dayTotal(day.id)}", color = PlColors.Ink, fontSize = 12.sp,
                    fontFamily = PlMono)
                Text("$count lines", color = PlColors.Faint, fontSize = 11.sp, fontFamily = PlMono)
                if (day.isToday) {
                    Text("TODAY", color = PlColors.StampVoid, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, fontFamily = PlMono)
                }
            }
        }
    }
}

@Composable
private fun PlStampBanner(day: PlDay) {
    Row(
        Modifier.fillMaxWidth().background(PlColors.Slip)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlStamp(text = day.status.name, color = day.status.stamp())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "BRANCH DAY ${day.dow} ${day.dateLabel}",
                color = PlColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = PlMono,
            )
            Text(
                "Editable until 04:00 Asia/Manila next morning — then the day turns PAST lazily.",
                color = PlColors.Faint,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PlIndex(
    screen: PlScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (PlScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxHeight().background(PlColors.Slip)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text("INDEX", color = PlColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Black,
            fontFamily = PlMono)
        Spacer(Modifier.height(8.dp))
        PlScreen.entries.forEach { entry ->
            val locked = !loggedIn && entry != PlScreen.ONBOARDING && entry != PlScreen.LOGIN
            val label = entry.title + if (entry == PlScreen.MAILBOX && unread > 0) " ($unread)" else ""
            Text(
                label,
                color = when {
                    locked -> PlColors.Rule
                    entry == screen -> Color.White
                    else -> PlColors.Ink
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlMono,
                modifier = Modifier
                    .background(
                        if (entry == screen) PlColors.Key else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable(enabled = !locked) { onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        PlGhost(text = "Exit") { onBack() }
    }
}
