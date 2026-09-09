package com.companyb.companyapp.proto.kiosktouch

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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

// #762 — kiosk-touch shell: minimal chrome, giant touch targets, 3-tap walk-in hero.

enum class KioskScreen(val title: String) {
    ONBOARDING("Start"),
    LOGIN("Sign in"),
    BRANCHES("Branch"),
    HOME("Home"),
    WALKIN("Walk-in"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Pay"),
    TEAM("Team"),
    MAILBOX("Inbox"),
    AUDIT("Log"),
    PROFILE("Me"),
}

@Composable
fun KioskTouchApp(onBack: () -> Unit) {
    val repo = remember { KioskFakeRepo() }
    var screen by remember { mutableStateOf(KioskScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("KioskTouch", "kiosk-touch prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(KioskColors.Bg)) {
        KioskTopStrip(repo = repo)
        KioskDayBand(status = repo.dayStatus, onPick = { repo.dayStatus = it })
        Row(Modifier.fillMaxSize()) {
            KioskNav(
                screen = screen,
                loggedIn = loggedIn,
                unread = repo.notifications.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
            ) {
                when (screen) {
                    KioskScreen.ONBOARDING -> KioskOnboarding(repo = repo, onNext = { screen = KioskScreen.LOGIN })
                    KioskScreen.LOGIN -> KioskLogin(repo = repo, onNext = { screen = KioskScreen.BRANCHES })
                    KioskScreen.BRANCHES -> KioskBranches(repo = repo, onNext = { screen = KioskScreen.HOME })
                    KioskScreen.HOME -> KioskHome(repo = repo, go = { screen = it })
                    KioskScreen.WALKIN -> KioskWalkIn(repo = repo, onDone = { screen = KioskScreen.SESSIONS })
                    KioskScreen.SESSIONS -> KioskSessions(repo = repo)
                    KioskScreen.CLIENTS -> KioskClients(repo = repo)
                    KioskScreen.FINANCE -> KioskFinance(repo = repo)
                    KioskScreen.TEAM -> KioskTeam(repo = repo)
                    KioskScreen.MAILBOX -> KioskMailbox(repo = repo)
                    KioskScreen.AUDIT -> KioskAudit(repo = repo)
                    KioskScreen.PROFILE -> KioskProfile(repo = repo, onLogout = { screen = KioskScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun KioskTopStrip(repo: KioskFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(KioskColors.Ink).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "WALK-IN KIOSK",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "${repo.currentBranch.name} · ${repo.operationalDate}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (repo.clockedIn) "● ON DUTY" else "○ OFF DUTY",
                color = if (repo.clockedIn) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            val user = repo.currentUser
            Text(
                (user?.name ?: "not signed in").uppercase(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun KioskNav(
    screen: KioskScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (KioskScreen) -> Unit,
    onBack: () -> Unit,
) {
    val items = if (loggedIn) {
        listOf(
            KioskScreen.HOME,
            KioskScreen.WALKIN,
            KioskScreen.SESSIONS,
            KioskScreen.CLIENTS,
            KioskScreen.FINANCE,
            KioskScreen.TEAM,
            KioskScreen.MAILBOX,
            KioskScreen.AUDIT,
            KioskScreen.BRANCHES,
            KioskScreen.PROFILE,
        )
    } else {
        listOf(KioskScreen.ONBOARDING, KioskScreen.LOGIN)
    }
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(KioskColors.Paper)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val active = item == screen
            val label = if (item == KioskScreen.MAILBOX && unread > 0) "Inbox ($unread)" else item.title
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(
                        if (active) KioskColors.Ink else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(onClick = { onPick(item) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else KioskColors.Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        KioskLink(text = "Exit prototype", onClick = onBack)
    }
}

@Composable
private fun KioskOnboarding(
    repo: KioskFakeRepo,
    onNext: () -> Unit,
) {
    var blocked by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Touch to start", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text(
            "Walk-ins in 3 taps. Huge buttons, no hunting.",
            color = KioskColors.Muted,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(20.dp))
        KioskCard {
            Column {
                KioskSection("Locked account — ONBOARDING")
                Text(
                    "J. Ramos (new hire): 0 capabilities.",
                    color = KioskColors.Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
                KioskNote(
                    "A branch assignment alone unlocks nothing — the ONBOARDING bundle is empty. " +
                        "Only a MANAGE_USERS grant opens the kiosk.",
                )
                KioskGap()
                KioskSecondary(text = "1 · Try opening Sessions", onClick = {
                    blocked = "Blocked: ONBOARDING has no VIEW_BRANCH_DATA. Ask a manager to grant a role."
                })
                KioskGap()
                KioskBigButton(text = "2 · Grant Practitioner + enter", onClick = {
                    val index = repo.users.indexOfFirst { it.id == "u-new" }
                    repo.users[index] = repo.users[index].copy(role = KioskRole.PRACTITIONER)
                    repo.audit("R. Aquino", "GRANT", "PRACTITIONER to J. Ramos", "onboarding complete")
                    blocked = null
                    onNext()
                })
                KioskError(blocked)
            }
        }
    }
}

@Composable
private fun KioskLogin(
    repo: KioskFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("Who is working?", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("Tap your name. No password on this demo kiosk.", color = KioskColors.Muted, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        repo.users.forEach { user ->
            KioskCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${user.role.name} · home ${repo.branches.first { it.id == user.homeBranchId }.name}",
                            color = KioskColors.Muted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    KioskRowGap()
                    KioskRowButton(text = "Start", onClick = {
                        repo.login(user.id)
                        logInfo("KioskTouch", "signed in as ${user.id}")
                        onNext()
                    })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun KioskBranches(
    repo: KioskFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("Pick a branch", color = KioskColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Black)
        Text("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION.", color = KioskColors.Muted, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        repo.branches.forEach { branch ->
            KioskCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            branch.name.uppercase(),
                            color = KioskColors.Ink,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            branch.kind.name,
                            color = KioskColors.Accent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    if (branch.id == repo.currentBranchId) {
                        Text("● HERE", color = KioskColors.Green, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    } else {
                        KioskRowButton(text = "Use", onClick = {
                            repo.currentBranchId = branch.id
                            repo.audit(repo.currentUser?.name ?: "kiosk", "SWITCH", "branch ${branch.name}")
                            onNext()
                        })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        KioskBigButton(text = "Continue at ${repo.currentBranch.name}", onClick = onNext)
    }
}

@Composable
private fun KioskHome(
    repo: KioskFakeRepo,
    go: (KioskScreen) -> Unit,
) {
    Column {
        Text(
            "Today at ${repo.currentBranch.name}",
            color = KioskColors.Ink,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KioskStatCard(label = "pending", value = repo.dayPendingCount().toString(), modifier = Modifier.weight(1f))
            KioskStatCard(label = "completed ₱", value = "${repo.dayCompletedTotal()}", modifier = Modifier.weight(1f))
        }
        KioskGap()
        KioskCard {
            Column {
                KioskSection("Clock-in — ${repo.currentBranch.name}")
                Text(
                    if (repo.clockedIn) "You are ON DUTY." else "You are OFF DUTY.",
                    color = KioskColors.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                KioskGap()
                if (repo.clockedIn) {
                    KioskSecondary(text = "Clock out", onClick = {
                        repo.clockedIn = false
                        repo.audit(repo.currentUser?.name ?: "kiosk", "CLOCK_OUT", repo.currentBranch.name)
                    })
                } else {
                    KioskBigButton(text = "Tap to clock in", onClick = {
                        repo.clockedIn = true
                        repo.audit(repo.currentUser?.name ?: "kiosk", "CLOCK_IN", repo.currentBranch.name)
                    })
                }
                KioskNote("Clocking into a non-home branch starts Relief Duty: view-only until a relief grant lands.")
            }
        }
        KioskGap()
        KioskBigButton(text = "New walk-in — 3 taps", onClick = { go(KioskScreen.WALKIN) })
        KioskGap()
        KioskCard {
            Column {
                KioskSection("Relief invites — branch offers you a day")
                if (repo.invites.isEmpty()) KioskNote("No invites.")
                repo.invites.forEach { invite ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == invite.branchId }?.name ?: invite.branchId
                            Text(
                                "$branch / ${invite.day}",
                                color = KioskColors.Ink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "from ${invite.fromUser} · " +
                                    (invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "tap to answer"),
                                color = KioskColors.Muted,
                                fontSize = 18.sp,
                            )
                        }
                        if (invite.accepted == null) {
                            KioskRowButton(text = "Yes", onClick = { repo.decideInvite(invite.id, true) })
                            KioskRowGap()
                            KioskGhostButton(text = "No", onClick = { repo.decideInvite(invite.id, false) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        KioskGap()
        KioskCard {
            Column {
                KioskSection("Relief requests — you ask, the branch answers")
                repo.requests.forEach { request ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == request.branchId }?.name
                                ?: request.branchId
                            Text(
                                "$branch / ${request.day}",
                                color = KioskColors.Ink,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "${request.requester} · ${request.decided ?: "live"}",
                                color = KioskColors.Muted,
                                fontSize = 18.sp,
                            )
                        }
                        if (request.decided == null) {
                            if (request.mine) {
                                KioskGhostButton(
                                        text = "Pull back",
                                        onClick = { repo.decideRequest(request.id, "withdrawn") },
                                    )
                            } else {
                                KioskRowButton(text = "Allow", onClick = { repo.decideRequest(request.id, "granted") })
                                KioskRowGap()
                                KioskGhostButton(text = "Deny", onClick = { repo.decideRequest(request.id, "denied") })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                KioskNote(
                    "One live request per person per branch per date. " +
                        "Locks once the requester clocks in as relief. Paid from the relief drawer.",
                )
            }
        }
        KioskGap()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                KioskSecondary(text = "Sessions", onClick = { go(KioskScreen.SESSIONS) })
            }
            Box(Modifier.weight(1f)) {
                KioskSecondary(text = "Collect pay", onClick = { go(KioskScreen.FINANCE) })
            }
        }
    }
}

@Composable
private fun KioskStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(KioskColors.Ink, RoundedCornerShape(20.dp)).padding(20.dp),
    ) {
        Column {
            Text(value, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black)
            Text(
                label.uppercase(),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun KioskWalkIn(
    repo: KioskFakeRepo,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    var pickedType by remember { mutableStateOf("Standard") }
    var pickedPrice by remember { mutableStateOf(1200) }
    var pickedName by remember { mutableStateOf("Walk-in guest") }
    var doneId by remember { mutableStateOf<String?>(null) }
    Column {
        Text("Walk-in — tap $step of 3", color = KioskColors.Ink, fontSize = 44.sp, fontWeight = FontWeight.Black)
        KioskGap()
        when (step) {
            1 -> {
                KioskSection("Tap 1 — what service?")
                val options = listOf("Standard" to 1200, "Deep Tissue" to 1500, "Hot Stone" to 1800, "Sports" to 1600)
                options.forEach { (name, price) ->
                    val active = pickedType == name
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .background(
                                if (active) KioskColors.Ink else KioskColors.Paper,
                                RoundedCornerShape(20.dp),
                            )
                            .border(2.dp, KioskColors.Line, RoundedCornerShape(20.dp))
                            .clickable(onClick = {
                                pickedType = name
                                pickedPrice = price
                                step = 2
                            })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = if (active) Color.White else KioskColors.Ink,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "₱$price",
                                color = if (active) Color.White else KioskColors.Accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            2 -> {
                KioskSection("Tap 2 — who walks in?")
                val names = listOf("Walk-in guest", "Walk-in #42", "Walk-in family", "Returning guest")
                names.forEach { name ->
                    val active = pickedName == name
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .background(
                                if (active) KioskColors.Wash else KioskColors.Paper,
                                RoundedCornerShape(20.dp),
                            )
                            .border(2.dp, KioskColors.Line, RoundedCornerShape(20.dp))
                            .clickable(onClick = {
                                pickedName = name
                                step = 3
                            })
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(name, color = KioskColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                KioskSecondary(text = "Back", onClick = { step = 1 })
            }
            3 -> {
                KioskSection("Tap 3 — confirm")
                KioskCard {
                    Column {
                        Text(pickedName, color = KioskColors.Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            "$pickedType · ₱$pickedPrice · ${repo.currentBranch.name}",
                            color = KioskColors.Muted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        KioskNote("Creates a PENDING walk-in. Walk-ins can never be NO_SHOW or CANCELLED.")
                    }
                }
                KioskGap()
                if (doneId == null) {
                    KioskBigButton(text = "Check in now", onClick = {
                        doneId = repo.createWalkIn(pickedName, pickedType, pickedPrice)
                        logInfo("KioskTouch", "walk-in created $doneId")
                    })
                    KioskGap()
                    KioskSecondary(text = "Back", onClick = { step = 2 })
                } else {
                    KioskCard {
                        Column {
                            Text(
                                "Checked in: $doneId",
                                color = KioskColors.Green,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                            )
                            KioskNote("Find it under Sessions → PENDING. Tap Manage to complete it.")
                        }
                    }
                    KioskGap()
                    KioskBigButton(text = "See sessions", onClick = onDone)
                }
            }
        }
    }
}
