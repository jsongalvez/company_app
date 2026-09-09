package com.companyb.companyapp.proto.checkinkiosk

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #782 — checkin-kiosk shell: night-lobby chrome, top day band, bottom tab bar, 3-tap arrival hero.

enum class CheckinScreen(val title: String) {
    WELCOME("Welcome"),
    SIGNIN("Sign in"),
    BRANCH("Branch"),
    LOBBY("Lobby"),
    ARRIVAL("Arrive"),
    QUEUE("Queue"),
    CARE("Care"),
    GUESTS("Guests"),
    TILL("Till"),
    CREW("Crew"),
    BELL("Bell"),
    LEDGER("Ledger"),
    ME("Me"),
}

@Composable
fun CheckinKioskApp(onBack: () -> Unit) {
    val repo = remember { CheckinFakeRepo() }
    var screen by remember { mutableStateOf(CheckinScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("CheckinKiosk", "checkin-kiosk prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(CheckinColors.Night)) {
        CheckinTopStrip(repo = repo)
        CheckinDayBand(status = repo.dayStatus, date = repo.operationalDate, onPick = { repo.dayStatus = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (screen) {
                    CheckinScreen.WELCOME -> CheckinWelcome(repo = repo, onNext = { screen = CheckinScreen.SIGNIN })
                    CheckinScreen.SIGNIN -> CheckinSignin(repo = repo, onNext = { screen = CheckinScreen.BRANCH })
                    CheckinScreen.BRANCH -> CheckinBranches(repo = repo, onNext = { screen = CheckinScreen.LOBBY })
                    CheckinScreen.LOBBY -> CheckinLobby(repo = repo, go = { screen = it })
                    CheckinScreen.ARRIVAL -> CheckinArrival(repo = repo, onDone = { screen = CheckinScreen.QUEUE })
                    CheckinScreen.QUEUE -> CheckinQueue(repo = repo)
                    CheckinScreen.CARE -> CheckinCare(repo = repo)
                    CheckinScreen.GUESTS -> CheckinGuests(repo = repo)
                    CheckinScreen.TILL -> CheckinTill(repo = repo)
                    CheckinScreen.CREW -> CheckinCrew(repo = repo)
                    CheckinScreen.BELL -> CheckinBell(repo = repo)
                    CheckinScreen.LEDGER -> CheckinLedger(repo = repo)
                    CheckinScreen.ME -> CheckinMe(repo = repo, onLogout = { screen = CheckinScreen.SIGNIN })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (loggedIn) {
            CheckinTabBar(
                screen = screen,
                unread = repo.notifications.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CheckinTopStrip(repo: CheckinFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(CheckinColors.NightDeep)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "SELF CHECK-IN",
                color = CheckinColors.Marigold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.name,
                color = CheckinColors.Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                repo.currentBranch.lobbyLine,
                color = CheckinColors.FaintOnNight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (repo.clockedIn) "● FRONT DESK OPEN" else "○ FRONT DESK CLOSED",
                color = if (repo.clockedIn) CheckinColors.Mint else CheckinColors.FaintOnNight,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                (repo.currentUser?.name ?: "guest screen").uppercase(),
                color = CheckinColors.Cream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CheckinDayBand(
    status: CheckinDayStatus,
    date: String,
    onPick: (CheckinDayStatus) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(CheckinColors.Panel)
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(status.band())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(status.name, color = CheckinColors.NightDeep, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "Branch day $date",
                color = CheckinColors.Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            CheckinDayStatus.entries.forEach { option ->
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (option == status) CheckinColors.Cream else CheckinColors.NightDeep)
                        .clickable { onPick(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        option.name,
                        color = if (option == status) CheckinColors.NightDeep else CheckinColors.FaintOnNight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            "Day rolls over at 04:00 Asia/Manila — after that this day reads PAST and only a Coordinator edits it.",
            color = CheckinColors.FaintOnNight,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CheckinTabBar(
    screen: CheckinScreen,
    unread: Int,
    onPick: (CheckinScreen) -> Unit,
    onBack: () -> Unit,
) {
    val tabs = listOf(
        CheckinScreen.LOBBY,
        CheckinScreen.ARRIVAL,
        CheckinScreen.QUEUE,
        CheckinScreen.GUESTS,
        CheckinScreen.TILL,
        CheckinScreen.CREW,
    )
    Column(Modifier.fillMaxWidth().background(CheckinColors.NightDeep).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = screen == tab
                Box(
                    Modifier.weight(1f).height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) CheckinColors.Marigold else CheckinColors.Panel)
                        .clickable { onPick(tab) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (tab == CheckinScreen.BELL) "$unread" else tab.title,
                        color = if (selected) CheckinColors.MarigoldInk else CheckinColors.Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val more = listOf(CheckinScreen.BRANCH, CheckinScreen.CARE, CheckinScreen.BELL, CheckinScreen.LEDGER, CheckinScreen.ME)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                more.forEach { extra ->
                    val label = if (extra == CheckinScreen.BELL && unread > 0) "Bell ($unread)" else extra.title
                    Box(
                        Modifier.height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(extra) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (screen == extra) CheckinColors.Marigold else CheckinColors.FaintOnNight,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Box(
                Modifier.height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Exit", color = CheckinColors.FaintOnNight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CheckinWelcome(
    repo: CheckinFakeRepo,
    onNext: () -> Unit,
) {
    var granted by remember { mutableStateOf(false) }
    var blockedNote by remember { mutableStateOf<String?>(null) }
    CheckinHeading("Welcome in.", 52)
    CheckinSub("Three taps and you're in line. No forms, no counter talk.")
    CheckinTicketStub(
        ticketNo = "A-044",
        guest = "Your name here",
        care = "Your care, your call",
        branch = repo.currentBranch.name,
        ahead = repo.queueAhead(),
    )
    CheckinPanel {
        CheckinHeading("Staff? Start locked.", 24)
        CheckinSub("A fresh ONBOARDING account holds zero capabilities — the lobby stays shut until a MANAGE_USERS grant lands.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                CheckinGhostButton("Try the lobby") {
                    blockedNote = if (granted) null else "Locked: ONBOARDING holds no capabilities yet."
                    if (granted) onNext()
                }
            }
            Box(Modifier.weight(1f)) {
                CheckinBigButton(if (granted) "Granted — enter" else "Grant Practitioner") {
                    granted = true
                    blockedNote = null
                    repo.audit("R. Aquino", "GRANT", "J. Ramos ONBOARDING -> PRACTITIONER", "fake hiring grant")
                    onNext()
                }
            }
        }
        if (blockedNote != null) {
            Spacer(Modifier.height(10.dp))
            CheckinNoteCard(blockedNote!!)
        }
    }
}

@Composable
private fun CheckinSignin(
    repo: CheckinFakeRepo,
    onNext: () -> Unit,
) {
    var picked by remember { mutableStateOf("u-me") }
    CheckinHeading("Who's at the desk?", 44)
    CheckinSub("Pick a staffer to open the lobby. Fake directory — five people, every role.")
    repo.users.forEach { user ->
        Spacer(Modifier.height(2.dp))
        CheckinPickRow(
            title = user.name,
            subtitle = "${user.role} · home ${repo.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: user.homeBranchId} · slot ${user.slot}",
            picked = picked == user.id,
            onPick = { picked = user.id },
            trailing = user.role.name.take(4),
        )
        Spacer(Modifier.height(8.dp))
    }
    val chosen = repo.users.firstOrNull { it.id == picked }
    if (chosen?.role == CheckinRole.ONBOARDING) {
        CheckinNoteCard("ONBOARDING is functionally locked out — even with a branch assignment the role bundle is empty. Pick anyone else, or head back and take the grant.")
    }
    CheckinBigButton(
        label = "Open the lobby",
        enabled = chosen?.role != CheckinRole.ONBOARDING,
        onClick = {
            repo.login(picked)
            repo.clockedIn = true
            onNext()
        },
    )
}

@Composable
private fun CheckinBranches(
    repo: CheckinFakeRepo,
    onNext: () -> Unit,
) {
    CheckinHeading("Which lobby?", 44)
    CheckinSub("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION — each keeps its own line, sessions, and drawer.")
    repo.branches.forEach { branch ->
        CheckinPickRow(
            title = branch.name,
            subtitle = "${branch.kind} · ${branch.lobbyLine}",
            picked = repo.currentBranchId == branch.id,
            onPick = {
                repo.currentBranchId = branch.id
                repo.audit(repo.currentUser?.name ?: "lobby", "SWITCH", "Branch ${branch.name}")
            },
            trailing = branch.kind.name.take(5),
        )
        Spacer(Modifier.height(8.dp))
    }
    CheckinBigButton("Take me to the lobby", onClick = onNext)
}

@Composable
private fun CheckinLobby(
    repo: CheckinFakeRepo,
    go: (CheckinScreen) -> Unit,
) {
    val user = repo.currentUser
    CheckinHeading("Good to see you${if (user != null) ", ${user.name.split(" ").first()}" else ""}.", 44)
    CheckinSub("${repo.currentBranch.name} · ${repo.operationalDate} · ${repo.dayStatus}")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { CheckinStat("In line", repo.queueAhead().toString()) }
        Box(Modifier.weight(1f)) { CheckinStat("Done today", "₱${repo.dayCompletedTotal()}") }
        Box(Modifier.weight(1f)) { CheckinStat("Tickets", repo.tickets.size.toString()) }
    }
    CheckinPanel {
        CheckinHeading(if (repo.clockedIn) "Front desk is open." else "Front desk is closed.", 26)
        CheckinSub(
            if (repo.clockedIn) "Guests can arrive in 3 taps. Clock out to freeze the line." else "Clock in to start handing out queue tickets.",
        )
        Spacer(Modifier.height(12.dp))
        CheckinBigButton(
            if (repo.clockedIn) "Clock out" else "Clock in",
            onClick = {
                repo.clockedIn = !repo.clockedIn
                repo.audit(user?.name ?: "lobby", if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranch.name)
            },
        )
    }
    CheckinBigButton("＋  New arrival — 3 taps", onClick = { go(CheckinScreen.ARRIVAL) })
    CheckinPanel {
        CheckinHeading("Cover needed?", 24)
        CheckinSub("Relief duty starts view-only at a non-home branch. A grant unlocks edits; it ends 04:00 Manila next day.")
        Spacer(Modifier.height(10.dp))
        val myInvites = repo.invites.filter { it.accepted == null }
        val myRequests = repo.requests.filter { it.mine && it.decided == null }
        val branchRequests = repo.requests.filter { !it.mine && it.decided == null }
        Text("Invites for you: ${myInvites.size} · your asks: ${myRequests.size} · asks to judge: ${branchRequests.size}", color = CheckinColors.Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        myInvites.forEach { invite ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${invite.fromUser} invites you · ${repo.branches.firstOrNull { it.id == invite.branchId }?.name} · ${invite.day}",
                    color = CheckinColors.Cream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(CheckinColors.Mint)
                        .clickable { repo.decideInvite(invite.id, true) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Yes", color = CheckinColors.NightDeep, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(CheckinColors.NightDeep)
                        .clickable { repo.decideInvite(invite.id, false) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("No", color = CheckinColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        myRequests.forEach { request ->
            Text(
                "You asked: ${repo.branches.firstOrNull { it.id == request.branchId }?.name} · ${request.day} — waiting",
                color = CheckinColors.FaintOnNight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            CheckinTextLink("Pull it back") { repo.decideRequest(request.id, "withdrawn") }
        }
        branchRequests.forEach { request ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${request.requester} asks for ${request.day}",
                    color = CheckinColors.Cream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(CheckinColors.Mint)
                        .clickable { repo.decideRequest(request.id, "granted") }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Allow", color = CheckinColors.NightDeep, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(CheckinColors.NightDeep)
                        .clickable { repo.decideRequest(request.id, "denied") }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Deny", color = CheckinColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        CheckinGhostButton("Ask another branch for cover") {
            repo.requests.add(
                0,
                CheckinRequest(
                    "r-new-${repo.requests.size}",
                    if (repo.currentBranchId == "qc") "tour" else "qc",
                    "Sep 14",
                    repo.currentUser?.name ?: "Front desk",
                    mine = true,
                ),
            )
            repo.audit(repo.currentUser?.name ?: "lobby", "REQUEST", "Relief cover Sep 14")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { CheckinGhostButton("Today's line") { go(CheckinScreen.QUEUE) } }
        Box(Modifier.weight(1f)) { CheckinGhostButton("Count the drawer") { go(CheckinScreen.TILL) } }
    }
}
