package com.companyb.companyapp.proto.bottomdock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #810 — bottom-dock shell: menu bar, branch-day pill row, one front window
// on a playful wallpaper stage, macOS-style magnifying dock as the only nav.

enum class DockScreen(
    val title: String,
    val glyph: String,
    val tint: Color,
) {
    WELCOME("Welcome", "Hi", Color(0xFF7C5CFF)),
    SIGNIN("Sign in", "ID", Color(0xFF2F7FD1)),
    BRANCH("Branch", "Br", Color(0xFF1F9D63)),
    HOME("Home", "Ho", Color(0xFFFF6FA5)),
    NEW("New", "+", Color(0xFFFF8A3D)),
    QUEUE("Queue", "Qu", Color(0xFF9A6B00)),
    GUESTS("Guests", "Gu", Color(0xFF0E7C86)),
    TILL("Till", "Ti", Color(0xFF5A8F00)),
    CREW("Crew", "Cr", Color(0xFF8A5A00)),
    BELL("Bell", "Be", Color(0xFFD64545)),
    LEDGER("Ledger", "Le", Color(0xFF55506A)),
    ME("Me", "Me", Color(0xFF33302E)),
}

@Composable
fun BottomDockApp(onBack: () -> Unit) {
    val repo = remember { DockFakeRepo() }
    var screen by remember { mutableStateOf(DockScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("BottomDock", "bottom-dock prototype launched") }

    val loggedIn = repo.currentUserId != null
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DockColors.WallpaperTop, DockColors.WallpaperBottom))),
    ) {
        DockBubbles()
        Column(Modifier.fillMaxSize()) {
            DockMenuBar(repo = repo)
            DockDayPillRow(repo = repo)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 12.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val windowTitle =
                        if (loggedIn) {
                            "${screen.title} — ${repo.currentBranch.name}"
                        } else {
                            screen.title
                        }
                    DockWindow(title = windowTitle) {
                        when (screen) {
                            DockScreen.WELCOME -> DockWelcome(repo = repo, onNext = { screen = DockScreen.SIGNIN })
                            DockScreen.SIGNIN -> DockSignin(repo = repo, onNext = { screen = DockScreen.BRANCH })
                            DockScreen.BRANCH -> DockBranches(repo = repo, onNext = { screen = DockScreen.HOME })
                            DockScreen.HOME -> DockHome(repo = repo, go = { screen = it })
                            DockScreen.NEW -> DockArrival(repo = repo, onDone = { screen = DockScreen.QUEUE })
                            DockScreen.QUEUE -> DockQueue(repo = repo)
                            DockScreen.GUESTS -> DockGuests(repo = repo)
                            DockScreen.TILL -> DockTill(repo = repo)
                            DockScreen.CREW -> DockCrew(repo = repo)
                            DockScreen.BELL -> DockBell(repo = repo)
                            DockScreen.LEDGER -> DockLedger(repo = repo)
                            DockScreen.ME -> DockMe(repo = repo, onLogout = { screen = DockScreen.SIGNIN })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (loggedIn) {
                DockBar(
                    screen = screen,
                    unread = repo.notifications.count { !it.read },
                    onPick = { screen = it },
                    onBack = onBack,
                )
            } else {
                Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Sign in to pin apps to the dock.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBubbles() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(start = 90.dp, top = 70.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            Modifier
                .padding(start = 950.dp, top = 120.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(DockColors.Pink.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .padding(start = 620.dp, top = 480.dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(DockColors.Sunny.copy(alpha = 0.10f)),
        )
        Box(
            Modifier
                .padding(start = 180.dp, top = 560.dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(DockColors.Grape.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun DockMenuBar(repo: DockFakeRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DockColors.MenuBar)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(12.dp).clip(CircleShape).background(DockColors.Grape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "DockDesk",
            color = DockColors.MenuBarText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(18.dp))
        Text(
            repo.currentBranch.name,
            color = DockColors.MenuBarText.copy(alpha = 0.75f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (repo.clockedIn) "● clocked in" else "○ clocked out",
            color = if (repo.clockedIn) DockColors.TrafficGreen else DockColors.MenuBarText.copy(alpha = 0.6f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            (repo.currentUser?.name ?: "no one signed in").uppercase(),
            color = DockColors.MenuBarText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DockDayPillRow(repo: DockFakeRepo) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(repo.dayStatus.band())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(repo.dayStatus.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Text(
            "Branch day ${repo.operationalDate}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Rolls over 04:00 Asia/Manila",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        DockDayStatus.entries.forEach { option ->
            val selected = option == repo.dayStatus
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.22f))
                    .clickable { repo.dayStatus = option }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    option.name,
                    color = if (selected) DockColors.Ink else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun DockBar(
    screen: DockScreen,
    unread: Int,
    onPick: (DockScreen) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (screen != DockScreen.HOME) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(screen.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(DockColors.DockBar)
                    .border(1.dp, DockColors.DockEdge, RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val apps =
                    listOf(
                        DockScreen.HOME,
                        DockScreen.NEW,
                        DockScreen.QUEUE,
                        DockScreen.GUESTS,
                        DockScreen.TILL,
                        DockScreen.CREW,
                        DockScreen.BELL,
                    )
                apps.forEach { app ->
                    DockTile(
                        app = app,
                        selected = screen == app,
                        badge = if (app == DockScreen.BELL) unread else 0,
                        onPick = onPick,
                    )
                }
                Box(
                    Modifier.width(1.dp).height(52.dp).background(DockColors.InkSoft.copy(alpha = 0.4f)),
                )
                val files = listOf(DockScreen.BRANCH, DockScreen.LEDGER, DockScreen.ME)
                files.forEach { app ->
                    DockTile(app = app, selected = screen == app, badge = 0, onPick = onPick)
                }
                DockQuitTile(onBack = onBack)
            }
        }
    }
}

@Composable
private fun DockTile(
    app: DockScreen,
    selected: Boolean,
    badge: Int,
    onPick: (DockScreen) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .size(if (selected) 64.dp else 52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(app.tint)
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                        RoundedCornerShape(15.dp),
                    ).clickable { onPick(app) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.glyph,
                    color = Color.White,
                    fontSize = if (selected) 24.sp else 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
            if (badge > 0) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(DockColors.Coral)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$badge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) DockColors.Ink else Color.Transparent),
        )
    }
}

@Composable
private fun DockQuitTile(onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(DockColors.Ink)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("Exit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DockWelcome(
    repo: DockFakeRepo,
    onNext: () -> Unit,
) {
    var granted by remember { mutableStateOf(false) }
    var blockedNote by remember { mutableStateOf<String?>(null) }
    DockHeading("Double-click into the day.", 40)
    DockSub("One front window, one candy dock. Everything else is wallpaper.")
    Spacer(Modifier.height(12.dp))
    DockSticky(
        title = "Today at ${repo.currentBranch.name}",
        body = "${repo.queueAhead()} in line · ₱${repo.dayCompletedTotal()} banked · ${repo.tickets.size} tickets out",
    )
    Spacer(Modifier.height(12.dp))
    DockWindow(title = "Onboarding — locked account") {
        DockSub(
            "A fresh ONBOARDING account holds zero capabilities — the dock stays empty until a MANAGE_USERS grant lands.",
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                DockGhostButton("Try the dock") {
                    blockedNote = if (granted) null else "Locked: ONBOARDING holds no capabilities yet."
                    if (granted) onNext()
                }
            }
            Box(Modifier.weight(1f)) {
                DockGhostButton(if (granted) "Granted — open" else "Grant Practitioner") {
                    granted = true
                    blockedNote = null
                    repo.audit("R. Aquino", "GRANT", "J. Ramos ONBOARDING -> PRACTITIONER", "fake hiring grant")
                    onNext()
                }
            }
        }
        if (blockedNote != null) {
            Spacer(Modifier.height(10.dp))
            DockNoteCard(blockedNote!!)
        }
    }
}

@Composable
private fun DockSignin(
    repo: DockFakeRepo,
    onNext: () -> Unit,
) {
    var picked by remember { mutableStateOf("u-me") }
    DockHeading("Who is sitting at this Mac?", 34)
    DockSub("Pick a staffer. Fake directory — five people, every role.")
    Spacer(Modifier.height(12.dp))
    repo.users.forEach { user ->
        DockPickRow(
            title = user.name,
            subtitle = "${user.role} · home ${repo.branches.firstOrNull {
                it.id == user.homeBranchId
            }?.name} · slot ${user.slot}",
            picked = picked == user.id,
            onPick = { picked = user.id },
            trailing = user.role.name.take(4),
        )
        Spacer(Modifier.height(8.dp))
    }
    val chosen = repo.users.firstOrNull { it.id == picked }
    if (chosen?.role == DockRole.ONBOARDING) {
        DockNoteCard(
            "ONBOARDING is functionally locked out — the role bundle is empty even with a branch assignment. Pick anyone else, or head back and take the grant.",
        )
        Spacer(Modifier.height(8.dp))
    }
    DockBigButton(
        label = "Log in and pin the dock",
        enabled = chosen?.role != DockRole.ONBOARDING,
        onClick = {
            repo.login(picked)
            repo.clockedIn = true
            onNext()
        },
    )
}

@Composable
private fun DockBranches(
    repo: DockFakeRepo,
    onNext: () -> Unit,
) {
    DockHeading("Which desk are you at?", 34)
    DockSub("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION — each keeps its own line, sessions, and drawer.")
    Spacer(Modifier.height(12.dp))
    repo.branches.forEach { branch ->
        DockPickRow(
            title = branch.name,
            subtitle = "${branch.kind} · ${branch.dockLine}",
            picked = repo.currentBranchId == branch.id,
            onPick = {
                repo.currentBranchId = branch.id
                repo.audit(repo.currentUser?.name ?: "dock", "SWITCH", "Branch ${branch.name}")
            },
            trailing = branch.kind.name.take(5),
        )
        Spacer(Modifier.height(8.dp))
    }
    DockBigButton("Open the desktop", onClick = onNext)
}

@Composable
private fun DockHome(
    repo: DockFakeRepo,
    go: (DockScreen) -> Unit,
) {
    val user = repo.currentUser
    DockHeading("Good to see you${if (user != null) ", ${user.name.split(" ").first()}" else ""}.", 34)
    DockSub("${repo.currentBranch.name} · ${repo.operationalDate} · ${repo.dayStatus}")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { DockStat("In line", repo.queueAhead().toString(), DockColors.Grape) }
        Box(Modifier.weight(1f)) { DockStat("Banked", "₱${repo.dayCompletedTotal()}", DockColors.Mint) }
        Box(Modifier.weight(1f)) { DockStat("Tickets", repo.tickets.size.toString(), DockColors.Sky) }
    }
    Spacer(Modifier.height(12.dp))
    DockWindow(title = if (repo.clockedIn) "Desk is open" else "Desk is closed") {
        DockSub(
            if (repo.clockedIn) {
                "Guests can open sessions from the + tile. Clock out to freeze the line."
            } else {
                "Clock in to start handing out dock tickets."
            },
        )
        Spacer(Modifier.height(12.dp))
        DockBigButton(
            if (repo.clockedIn) "Clock out" else "Clock in",
            onClick = {
                repo.clockedIn = !repo.clockedIn
                repo.audit(
                    user?.name ?: "dock",
                    if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT",
                    repo.currentBranch.name,
                )
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    DockWindow(title = "Relief — cover board") {
        DockSub(
            "Relief duty starts view-only at a non-home branch. A grant unlocks edits; it ends 04:00 Manila next day.",
        )
        Spacer(Modifier.height(10.dp))
        val myInvites = repo.invites.filter { it.accepted == null }
        val myRequests = repo.requests.filter { it.mine && it.decided == null }
        val branchRequests = repo.requests.filter { !it.mine && it.decided == null }
        Text(
            "Invites for you: ${myInvites.size} · your asks: ${myRequests.size} · asks to judge: ${branchRequests.size}",
            color = DockColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        myInvites.forEach { invite ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${invite.fromUser} invites you · ${repo.branches.firstOrNull {
                        it.id == invite.branchId
                    }?.name} · ${invite.day}",
                    color = DockColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DockColors.Mint)
                        .clickable { repo.decideInvite(invite.id, true) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Yes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DockColors.Ink)
                        .clickable { repo.decideInvite(invite.id, false) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("No", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        myRequests.forEach { request ->
            Text(
                "You asked: ${repo.branches.firstOrNull {
                    it.id == request.branchId
                }?.name} · ${request.day} — waiting",
                color = DockColors.InkSoft,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { repo.decideRequest(request.id, "withdrawn") }
                    .padding(vertical = 4.dp),
            ) {
                Text("Pull it back", color = DockColors.Grape, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        branchRequests.forEach { request ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${request.requester} asks in · ${request.day}",
                    color = DockColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DockColors.Mint)
                        .clickable {
                            repo.decideRequest(
                                request.id,
                                "allowed",
                            )
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Allow", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DockColors.Coral)
                        .clickable {
                            repo.decideRequest(
                                request.id,
                                "denied",
                            )
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Deny", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DockGhostButton("Ask another branch for cover") {
            repo.audit(user?.name ?: "dock", "REQUEST", "Relief cover at Laguna Tour Stop 3")
            repo.notify("Relief request sent", "Laguna Tour Stop 3 coordinators see your ask.")
        }
    }
    Spacer(Modifier.height(12.dp))
    DockBigButton("＋  Open a session — New tile", onClick = { go(DockScreen.NEW) })
}
