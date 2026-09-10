package com.companyb.companyapp.proto.weekreview

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #850 — Friday Review shell: nameplate masthead, branch-day band, section rail,
// front page (week rollup ritual). Fake data only.

enum class WrScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    REVIEW("Front Page"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    SEEDS("Seeds"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoWeekReviewApp(onBack: () -> Unit) {
    val repo = remember { WeekReviewFakeRepo() }
    var screen by remember { mutableStateOf(WrScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("WeekReviewProto", "week-review prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(WrColors.Bg)) {
        WrMasthead(repo = repo)
        WrDayBand(repo = repo)
        Row(Modifier.fillMaxSize()) {
            WrRail(
                screen = screen,
                loggedIn = loggedIn,
                unread = repo.notices.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(22.dp),
            ) {
                when (screen) {
                    WrScreen.ONBOARDING -> WrOnboarding(repo = repo, onNext = { screen = WrScreen.LOGIN })
                    WrScreen.LOGIN -> WrLogin(repo = repo, onNext = { screen = WrScreen.BRANCHES })
                    WrScreen.BRANCHES -> WrBranchSelect(repo = repo, onNext = { screen = WrScreen.REVIEW })
                    WrScreen.REVIEW -> WrFrontPage(repo = repo, go = { screen = it })
                    WrScreen.SESSIONS -> WrSessions(repo = repo)
                    WrScreen.CLIENTS -> WrClients(repo = repo)
                    WrScreen.FINANCE -> WrFinance(repo = repo)
                    WrScreen.SEEDS -> WrSeeds(repo = repo)
                    WrScreen.TEAM -> WrTeam(repo = repo)
                    WrScreen.MAILBOX -> WrMailbox(repo = repo)
                    WrScreen.AUDIT -> WrAuditLog(repo = repo)
                    WrScreen.PROFILE -> WrProfile(repo = repo, onLogout = { screen = WrScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun WrMasthead(repo: WeekReviewFakeRepo) {
    Column(
        Modifier.fillMaxWidth().background(WrColors.Paper)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "The Friday Review",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = WrColors.Ink,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "WEEK 37 · SEP 7–11, 2026",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = WrColors.Accent,
            )
            Spacer(Modifier.weight(1f))
            val user = repo.currentUser
            Text(
                if (user == null) "Not signed in" else "${user.name} · ${user.role.label}",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = WrColors.Muted,
            )
        }
        Text(
            "Week rollup ritual: wins, misses, commission payouts, next-week seeds · ${repo.branch.name}",
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            color = WrColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        MastRule()
    }
}

@Composable
private fun WrDayBand(repo: WeekReviewFakeRepo) {
    val tint =
        when (repo.dayStatus) {
            WrDayState.OPEN -> WrColors.Moss
            WrDayState.PAST -> WrColors.Amber
            WrDayState.REMITTED -> Color(0xFF1F6F8B)
        }
    Row(
        Modifier.fillMaxWidth().background(WrColors.Ink)
            .padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BRANCH DAY",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${repo.branch.name} · ${repo.branch.dayDate}",
            color = Color(0xFFF3EAD6),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        WrDayState.entries.forEach { state ->
            val active = repo.dayStatus == state
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) tint else Color(0xFF3A342A))
                    .clickable { repo.setDay(state) }
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    state.label.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Day boundary 04:00 Asia/Manila",
            color = Color(0xFFB9AE97),
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun WrRail(
    screen: WrScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (WrScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.width(168.dp).fillMaxHeight().background(WrColors.Paper)
            .padding(vertical = 14.dp, horizontal = 10.dp),
    ) {
        Text(
            "SECTIONS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = WrColors.Muted,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(6.dp))
        WrScreen.entries.forEach { s ->
            val locked = !loggedIn && s != WrScreen.ONBOARDING && s != WrScreen.LOGIN
            val active = screen == s
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) WrColors.Ink else Color.Transparent)
                    .clickable(enabled = !locked) { onPick(s) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.title,
                    color = if (active) Color.White else if (locked) WrColors.Line else WrColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (s == WrScreen.MAILBOX && unread > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(WrColors.Accent)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("$unread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ThinRule()
        Spacer(Modifier.height(8.dp))
        Text(
            "← Exit prototype",
            color = WrColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() }.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun WrOnboarding(
    repo: WeekReviewFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        SectionFlag("The ritual")
        Spacer(Modifier.height(10.dp))
        Text(
            "Every Friday, this desk closes the week.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = WrColors.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Read the front page top to bottom: Monday to Friday at a glance, the wins worth " +
                "repeating, the misses worth fixing, pay envelopes sealed, and seeds planted for " +
                "next week. Then sign in and run the desk like any other day.",
            fontSize = 14.sp,
            color = WrColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        NoteCard(
            "This kiosk signs in with an ONBOARDING account: locked, zero capabilities. " +
                "Try turning the page to Sessions — the desk will stop you until a role is granted.",
        )
        Spacer(Modifier.height(12.dp))
        val role = repo.users.first { it.id == "U-SAM" }.role
        Text("Kiosk account: Sam Rivera · ${role.label}", fontSize = 13.sp, color = WrColors.Muted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    repo.login("U-SAM")
                    onNext()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WrColors.Ink),
            ) {
                Text("Enter as ONBOARDING")
            }
            OutlinedButton(onClick = { repo.grantRole("U-SAM", WrRole.PRACTITIONER) }) {
                Text("Grant Practitioner role (demo)", color = WrColors.Ink)
            }
        }
        if (role != WrRole.ONBOARDING) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Role granted. The desk now treats Sam Rivera as ${role.label}: ${repo.capabilitiesOf(role)}",
                fontSize = 13.sp,
                color = WrColors.Moss,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WrLogin(
    repo: WeekReviewFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        SectionFlag("Sign the roster")
        Spacer(Modifier.height(10.dp))
        Text("Who is working the desk?", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WrColors.Ink)
        Spacer(Modifier.height(10.dp))
        repo.users.forEach { user ->
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WrColors.Paper)
                    .clickable {
                        repo.login(user.id)
                        onNext()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = WrColors.Ink)
                    Text(
                        "${user.role.label} · ${user.branch}",
                        fontSize = 12.sp,
                        color = WrColors.Muted,
                        fontStyle = FontStyle.Italic,
                    )
                }
                StatusStamp(user.role.label, if (user.role == WrRole.ONBOARDING) WrColors.Amber else WrColors.Slate)
            }
        }
        Spacer(Modifier.height(8.dp))
        NoteCard("Fake roster — tapping a name signs in with that role. No passwords, no network.")
    }
}

@Composable
private fun WrBranchSelect(
    repo: WeekReviewFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        SectionFlag("Pick the bureau")
        Spacer(Modifier.height(10.dp))
        Text("Which branch are you reviewing?", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WrColors.Ink)
        Spacer(Modifier.height(10.dp))
        repo.branches.forEach { branch ->
            val active = repo.branchId == branch.id
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) WrColors.GoldWash else WrColors.Paper)
                    .clickable { repo.pickBranch(branch.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(branch.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = WrColors.Ink)
                    Text(
                        "${branch.kind} · branch day ${branch.dayDate}",
                        fontSize = 12.sp,
                        color = WrColors.Muted,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (active) StatusStamp("Reviewing", WrColors.Gold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = WrColors.Ink),
        ) {
            Text("Open the Friday front page")
        }
    }
}

@Composable
private fun WrFrontPage(
    repo: WeekReviewFakeRepo,
    go: (WrScreen) -> Unit,
) {
    val done = repo.weekCompleted()
    val misses = repo.weekMisses()
    val till = done.sumOf { it.amount }
    val paidOut = repo.payouts.filter { it.paid }.sumOf { it.share }
    val pool = repo.payouts.sumOf { it.share }
    val user = repo.currentUser

    Column {
        SectionFlag("Front page · the week in five columns")
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.daySummary().forEach { (day, counts) ->
                val (d, total) = counts
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(WrColors.Paper)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = WrColors.Accent)
                    Text("$d/$total", fontSize = 24.sp, fontWeight = FontWeight.Black, color = WrColors.Ink)
                    Text("done", fontSize = 11.sp, color = WrColors.Muted, fontStyle = FontStyle.Italic)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(WrColors.Ink)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("WEEK'S TILL (completed, unvoided)", color = Color(0xFFB9AE97), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(peso(till), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("PAY ENVELOPES", color = Color(0xFFB9AE97), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(
                    "${peso(paidOut)} of ${peso(pool)} sealed",
                    color = Color(0xFFF3EAD6),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(3.dp)).background(WrColors.Gold)
                        .clickable { go(WrScreen.FINANCE) }.padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("OPEN PAYOUTS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WrColors.MossWash)
                    .padding(12.dp),
            ) {
                Text("WINS · ${done.size}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = WrColors.Moss)
                Spacer(Modifier.height(6.dp))
                done.take(6).forEach {
                    Text("✓ ${it.day} ${it.service} — ${it.client}", fontSize = 12.sp, color = WrColors.Ink)
                }
                if (done.size > 6) Text("+${done.size - 6} more on the Sessions page", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = WrColors.Moss)
            }
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WrColors.AmberWash)
                    .padding(12.dp),
            ) {
                Text("MISSES · ${misses.size}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = WrColors.Amber)
                Spacer(Modifier.height(6.dp))
                if (misses.isEmpty()) {
                    Text("A clean week. Frame this page.", fontSize = 12.sp, color = WrColors.Ink)
                } else {
                    misses.forEach {
                        val why = if (it.voided) "voided" else it.status.label
                        Text("× ${it.day} ${it.service} — ${it.client} ($why)", fontSize = 12.sp, color = WrColors.Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionFlag("The desk today")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(WrColors.Paper)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (repo.clockedIn) "Clocked in — ${user?.name ?: "kiosk"} is on duty" else "Off the clock",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = WrColors.Ink,
                )
                Text(
                    "Clock state feeds the commission split: only clocked-in staff share the pool.",
                    fontSize = 12.sp,
                    color = WrColors.Muted,
                    fontStyle = FontStyle.Italic,
                )
            }
            if (user?.role == WrRole.ONBOARDING || user == null) {
                Text("Locked for ONBOARDING", fontSize = 12.sp, color = WrColors.Amber, fontWeight = FontWeight.Bold)
            } else {
                Button(
                    onClick = { repo.clockToggle() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (repo.clockedIn) WrColors.Accent else WrColors.Moss),
                ) {
                    Text(if (repo.clockedIn) "Clock out" else "Clock in")
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        SectionFlag("Relief wire")
        Spacer(Modifier.height(8.dp))
        repo.invites.forEach { invite ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(3.dp)).background(WrColors.Paper).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Relief invite · ${invite.fromBranch}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WrColors.Ink)
                    Text(invite.shift, fontSize = 12.sp, color = WrColors.Muted)
                }
                when (invite.state) {
                    "PENDING" -> {
                        Text("Accept", color = WrColors.Moss, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { repo.answerInvite(invite.id, true) }.padding(4.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Decline", color = WrColors.StampRed, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { repo.answerInvite(invite.id, false) }.padding(4.dp))
                    }
                    else -> StatusStamp(invite.state, if (invite.state == "ACCEPTED") WrColors.Moss else WrColors.Muted)
                }
            }
        }
        repo.reliefAsks.forEach { ask ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(3.dp)).background(WrColors.Paper).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cover ask · ${ask.by}${if (ask.mine) " (you)" else ""}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WrColors.Ink)
                    Text(ask.shift, fontSize = 12.sp, color = WrColors.Muted)
                }
                when (ask.state) {
                    "PENDING" -> {
                        Text("Grant", color = WrColors.Moss, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { repo.answerAsk(ask.id, true) }.padding(4.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (ask.mine) "Withdraw" else "Deny",
                            color = WrColors.StampRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { repo.answerAsk(ask.id, false) }.padding(4.dp),
                        )
                    }
                    else -> StatusStamp(ask.state, if (ask.state == "GRANTED") WrColors.Moss else WrColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionFlag("Seeds for next week")
        Spacer(Modifier.height(8.dp))
        val open = repo.seeds.count { !it.planted }
        Text(
            "$open packets still to plant · ${repo.seeds.size - open} in the soil. Full seed table on the Seeds page.",
            fontSize = 13.sp,
            color = WrColors.Ink,
        )
        Spacer(Modifier.height(4.dp))
        repo.seeds.filter { !it.planted }.take(3).forEach {
            Text("▸ ${it.title} — ${it.owner} (${it.day})", fontSize = 12.sp, color = WrColors.Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Plant seeds →",
            color = WrColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clickable { go(WrScreen.SEEDS) },
        )
    }
}

@Composable
private fun WrProfile(
    repo: WeekReviewFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    Column {
        SectionFlag("Colophon")
        Spacer(Modifier.height(10.dp))
        if (user == null) {
            Text("Nobody is signed in.", fontSize = 15.sp, color = WrColors.Ink)
            return
        }
        Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.Black, color = WrColors.Ink)
        Text(
            "${user.role.label} · ${user.branch}",
            fontSize = 13.sp,
            color = WrColors.Muted,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(6.dp))
        Text(repo.capabilitiesOf(user.role), fontSize = 13.sp, color = WrColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Clock state: ${if (repo.clockedIn) "IN" else "OUT"} · Branch day: ${repo.branch.name} ${repo.dayStatus.label}",
            fontSize = 13.sp,
            color = WrColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (user.role != WrRole.ONBOARDING) {
                Button(
                    onClick = { repo.clockToggle() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (repo.clockedIn) WrColors.Accent else WrColors.Moss),
                ) {
                    Text(if (repo.clockedIn) "Clock out" else "Clock in")
                }
            }
            OutlinedButton(
                onClick = {
                    repo.logout()
                    onLogout()
                },
            ) {
                Text("Log out", color = WrColors.Ink)
            }
        }
    }
}
