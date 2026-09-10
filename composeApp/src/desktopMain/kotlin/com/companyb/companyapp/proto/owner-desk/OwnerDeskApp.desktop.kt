package com.companyb.companyapp.proto.ownerdesk

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #818 — owner-desk shell: brass nameplate, branch-day ribbon, three-lane
// portfolio header (money / people / risk), left drawer nav over a walnut desk.

enum class OwnerScreen(
    val title: String,
    val drawer: String,
) {
    WELCOME("Welcome", "Foyer"),
    SIGNIN("Sign in", "Foyer"),
    BRANCH("Branch", "Foyer"),
    HOME("Desk", "Desk"),
    NEW("New folio", "Desk"),
    QUEUE("Folio book", "Desk"),
    GUESTS("Guest index", "People"),
    TILL("Vault", "Money"),
    CREW("Hands", "People"),
    BELL("Pigeonhole", "Risk"),
    LEDGER("Daybook", "Risk"),
    ME("Study", "Desk"),
}

@Composable
fun ProtoOwnerDeskApp(onBack: () -> Unit) {
    val repo = remember { OwnerFakeRepo() }
    var screen by remember { mutableStateOf(OwnerScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("OwnerDesk", "owner-desk prototype launched") }

    val loggedIn = repo.currentUserId != null
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(OwnerColors.DeskTop, OwnerColors.DeskBottom))),
    ) {
        Column(Modifier.fillMaxSize()) {
            OwnerNameplate(repo = repo)
            OwnerRibbon(repo = repo)
            if (loggedIn) {
                OwnerLanes(repo = repo)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (loggedIn) {
                    OwnerDrawer(
                        screen = screen,
                        unread = repo.notifications.count { !it.read },
                        onPick = { screen = it },
                        onBack = onBack,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (screen) {
                        OwnerScreen.WELCOME -> OwnerWelcome(repo = repo, onNext = { screen = OwnerScreen.SIGNIN })
                        OwnerScreen.SIGNIN -> OwnerSignin(repo = repo, onNext = { screen = OwnerScreen.BRANCH })
                        OwnerScreen.BRANCH -> OwnerBranches(repo = repo, onNext = { screen = OwnerScreen.HOME })
                        OwnerScreen.HOME -> OwnerHome(repo = repo, go = { screen = it })
                        OwnerScreen.NEW -> OwnerIntake(repo = repo, onDone = { screen = OwnerScreen.QUEUE })
                        OwnerScreen.QUEUE -> OwnerQueue(repo = repo)
                        OwnerScreen.GUESTS -> OwnerGuests(repo = repo)
                        OwnerScreen.TILL -> OwnerVault(repo = repo)
                        OwnerScreen.CREW -> OwnerHands(repo = repo)
                        OwnerScreen.BELL -> OwnerPigeonhole(repo = repo)
                        OwnerScreen.LEDGER -> OwnerDaybook(repo = repo)
                        OwnerScreen.ME -> OwnerStudy(repo = repo, onLogout = { screen = OwnerScreen.SIGNIN })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun OwnerNameplate(repo: OwnerFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(OwnerColors.Rail).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "THE OWNER'S DESK",
            color = OwnerColors.Nameplate,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OwnerSerif,
            modifier = Modifier
                .border(1.dp, OwnerColors.Brass, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                repo.currentBranch.name,
                color = OwnerColors.Paper,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (repo.clockedIn) "The shop is open — you are on the floor" else "The shop is closed — desk only",
                color = OwnerColors.Paper.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            (repo.currentUser?.name ?: "PRIVATE").uppercase(),
            color = OwnerColors.Paper.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OwnerRibbon(repo: OwnerFakeRepo) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            repo.dayStatus.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(repo.dayStatus.ribbon())
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
        Text(
            "Branch day ${repo.operationalDate}",
            color = OwnerColors.Paper,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OwnerSerif,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Rolls over 04:00 Asia/Manila",
            color = OwnerColors.Paper.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        OwnerDayStatus.entries.forEach { option ->
            val selected = option == repo.dayStatus
            Text(
                option.name,
                color = if (selected) OwnerColors.Nameplate else OwnerColors.Paper.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, if (selected) OwnerColors.Brass else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { repo.dayStatus = option }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun OwnerLanes(repo: OwnerFakeRepo) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            OwnerLaneMini(
                "Money",
                "₱${repo.dayCompletedTotal()} banked · ₱${repo.unremittedTotal()} unsealed",
                OwnerColors.Money,
                OwnerColors.MoneyBg,
            )
        }
        Box(Modifier.weight(1f)) {
            OwnerLaneMini(
                "People",
                "${repo.queueAhead()} waiting · ${repo.clients.size} guests on file",
                OwnerColors.People,
                OwnerColors.PeopleBg,
            )
        }
        Box(Modifier.weight(1f)) {
            OwnerLaneMini(
                "Risk",
                "${repo.noShowCount()} no-show · ${repo.voidCount()} void · " +
                    "${repo.remittances.count { it.state == OwnerRemitState.DRAFT }} drawers open",
                OwnerColors.Risk,
                OwnerColors.RiskBg,
            )
        }
    }
}

@Composable
private fun OwnerLaneMini(
    label: String,
    body: String,
    tint: Color,
    bg: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = OwnerSerif,
        )
        Spacer(Modifier.width(10.dp))
        Text(body, color = OwnerColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OwnerDrawer(
    screen: OwnerScreen,
    unread: Int,
    onPick: (OwnerScreen) -> Unit,
    onBack: () -> Unit,
) {
    val groups =
        listOf(
            "Desk" to listOf(OwnerScreen.HOME, OwnerScreen.NEW, OwnerScreen.QUEUE, OwnerScreen.ME),
            "Money" to listOf(OwnerScreen.TILL),
            "People" to listOf(OwnerScreen.GUESTS, OwnerScreen.CREW),
            "Risk" to listOf(OwnerScreen.BELL, OwnerScreen.LEDGER),
            "House" to listOf(OwnerScreen.BRANCH),
        )
    Column(
        Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(OwnerColors.Rail)
            .border(width = 1.dp, color = OwnerColors.RailEdge)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { (group, items) ->
            Text(
                group.uppercase(),
                color = OwnerColors.Brass,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
            )
            items.forEach { item ->
                val selected = screen == item
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) OwnerColors.Brass else Color.Transparent)
                        .clickable { onPick(item) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.title,
                        color = if (selected) OwnerColors.NameplateInk else OwnerColors.Paper.copy(alpha = 0.82f),
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (item == OwnerScreen.BELL && unread > 0) {
                        Text(
                            "$unread",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(OwnerColors.Seal)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "↩ Leave the desk",
            color = OwnerColors.Paper.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
    }
}

@Composable
private fun OwnerWelcome(
    repo: OwnerFakeRepo,
    onNext: () -> Unit,
) {
    var granted by remember { mutableStateOf(false) }
    var blockedNote by remember { mutableStateOf<String?>(null) }
    OwnerHeading("The desk is yours. The floor can wait.", 40)
    OwnerSub("Money, people, risk — one private view. Staff names stay in the drawer.")
    Spacer(Modifier.height(12.dp))
    OwnerLedger(title = "Portfolio at ${repo.currentBranch.name}", seal = repo.dayStatus.name) {
        OwnerInkSub(
            "${repo.queueAhead()} waiting · ₱${repo.dayCompletedTotal()} banked · " +
                "₱${repo.unremittedTotal()} unsealed · ${repo.noShowCount()} no-show",
        )
    }
    Spacer(Modifier.height(12.dp))
    OwnerLedger(title = "Onboarding — locked account") {
        OwnerInkSub(
            "A fresh ONBOARDING account holds zero capabilities — the drawers stay shut until a grant lands.",
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                OwnerGhostButton("Try a drawer") {
                    blockedNote = if (granted) null else "Locked: ONBOARDING holds no capabilities yet."
                    if (granted) onNext()
                }
            }
            Box(Modifier.weight(1f)) {
                OwnerGhostButton(if (granted) "Granted — enter" else "Grant Practitioner") {
                    granted = true
                    blockedNote = null
                    repo.audit("R. Aquino", "GRANT", "J. Ramos ONBOARDING -> PRACTITIONER", "fake hiring grant")
                    onNext()
                }
            }
        }
        if (blockedNote != null) {
            Spacer(Modifier.height(10.dp))
            OwnerPaperNote(blockedNote!!)
        }
    }
}

@Composable
private fun OwnerSignin(
    repo: OwnerFakeRepo,
    onNext: () -> Unit,
) {
    var picked by remember { mutableStateOf("u-me") }
    OwnerHeading("Who sits at the desk?", 34)
    OwnerSub("The owner signs the book. Fake directory — five people, every role.")
    Spacer(Modifier.height(12.dp))
    repo.users.forEach { user ->
        OwnerPickRow(
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
    if (chosen?.role == OwnerRole.ONBOARDING) {
        OwnerNoteCard(
            "ONBOARDING is functionally locked out — the role bundle is empty even with a branch assignment. " +
                "Pick anyone else, or head back and take the grant.",
        )
        Spacer(Modifier.height(8.dp))
    }
    OwnerBigButton(
        label = "Sign the book",
        enabled = chosen?.role != OwnerRole.ONBOARDING,
        onClick = {
            repo.login(picked)
            repo.clockedIn = true
            onNext()
        },
    )
}

@Composable
private fun OwnerBranches(
    repo: OwnerFakeRepo,
    onNext: () -> Unit,
) {
    OwnerHeading("Which house?", 34)
    OwnerSub("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION — each keeps its own book, sessions, and vault.")
    Spacer(Modifier.height(12.dp))
    repo.branches.forEach { branch ->
        OwnerPickRow(
            title = branch.name,
            subtitle = "${branch.kind} · ${branch.deskLine}",
            picked = repo.currentBranchId == branch.id,
            onPick = {
                repo.currentBranchId = branch.id
                repo.audit(repo.currentUser?.name ?: "owner", "SWITCH", "Branch ${branch.name}")
            },
            trailing = branch.kind.name.take(5),
        )
        Spacer(Modifier.height(8.dp))
    }
    OwnerBigButton("Take the seat", onClick = onNext)
}

@Composable
private fun OwnerHome(
    repo: OwnerFakeRepo,
    go: (OwnerScreen) -> Unit,
) {
    val user = repo.currentUser
    OwnerHeading("Good evening${if (user != null) ", owner" else ""}.", 34)
    OwnerSub("${repo.currentBranch.name} · ${repo.operationalDate} · ${repo.dayStatus}")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            OwnerLaneStat("Money banked", "₱${repo.dayCompletedTotal()}", OwnerColors.Money, OwnerColors.MoneyBg)
        }
        Box(Modifier.weight(1f)) {
            OwnerLaneStat("People waiting", repo.queueAhead().toString(), OwnerColors.People, OwnerColors.PeopleBg)
        }
        Box(Modifier.weight(1f)) {
            val riskCount = repo.noShowCount() + repo.voidCount()
            OwnerLaneStat("Open risks", riskCount.toString(), OwnerColors.Risk, OwnerColors.RiskBg)
        }
    }
    Spacer(Modifier.height(12.dp))
    OwnerLedger(title = if (repo.clockedIn) "The shop is open" else "The shop is closed") {
        OwnerInkSub(
            if (repo.clockedIn) {
                "New folios open from the Desk drawer. Clock out to freeze the line."
            } else {
                "Clock in to start the line at ${repo.currentBranch.name}."
            },
        )
        Spacer(Modifier.height(12.dp))
        OwnerBigButton(
            if (repo.clockedIn) "Clock out" else "Clock in",
            onClick = {
                repo.clockedIn = !repo.clockedIn
                repo.audit(
                    user?.name ?: "owner",
                    if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT",
                    repo.currentBranch.name,
                )
            },
        )
    }
    Spacer(Modifier.height(12.dp))
    OwnerLedger(title = "Relief — cover board") {
        OwnerInkSub(
            "Relief duty starts view-only at a non-home branch. A grant unlocks edits; it ends 04:00 Manila next day.",
        )
        Spacer(Modifier.height(10.dp))
        val myInvites = repo.invites.filter { it.accepted == null }
        val myRequests = repo.requests.filter { it.mine && it.decided == null }
        val branchRequests = repo.requests.filter { !it.mine && it.decided == null }
        Text(
            "Invites: ${myInvites.size} · your asks: ${myRequests.size} · to judge: ${branchRequests.size}",
            color = OwnerColors.Ink,
            fontSize = 16.sp,
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
                    color = OwnerColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                OwnerSmallSeal("Yes", OwnerColors.Money) { repo.decideInvite(invite.id, true) }
                OwnerSmallSeal("No", OwnerColors.Ink) { repo.decideInvite(invite.id, false) }
            }
        }
        myRequests.forEach { request ->
            Text(
                "You asked: ${repo.branches.firstOrNull {
                    it.id == request.branchId
                }?.name} · ${request.day} — waiting",
                color = OwnerColors.InkSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Pull it back",
                color = OwnerColors.BrassDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { repo.decideRequest(request.id, "withdrawn") }.padding(vertical = 4.dp),
            )
        }
        branchRequests.forEach { request ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${request.requester} asks in · ${request.day}",
                    color = OwnerColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                OwnerSmallSeal("Allow", OwnerColors.Money) { repo.decideRequest(request.id, "allowed") }
                OwnerSmallSeal("Deny", OwnerColors.Risk) { repo.decideRequest(request.id, "denied") }
            }
        }
        Spacer(Modifier.height(8.dp))
        OwnerGhostButton("Ask another branch for cover") {
            repo.audit(user?.name ?: "owner", "REQUEST", "Relief cover at Laguna Tour Stop 3")
            repo.notify("Relief request sent", "Laguna Tour Stop 3 coordinators see your ask.")
        }
    }
    Spacer(Modifier.height(12.dp))
    OwnerBigButton("Open a folio — new", onClick = { go(OwnerScreen.NEW) })
}

@Composable
fun OwnerSmallSeal(
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
