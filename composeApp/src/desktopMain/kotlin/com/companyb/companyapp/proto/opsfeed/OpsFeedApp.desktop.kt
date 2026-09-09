package com.companyb.companyapp.proto.opsfeed

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

// #790 — ops-feed shell: masthead, branch-day band, channel rail, screen router.
// The feed screen is home: clock-in, relief traffic, and the living log share one column.

enum class OfScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    FEED("Ops Feed"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun ProtoOpsFeedApp(onBack: () -> Unit) {
    val repo = remember { OpsFeedFakeRepo() }
    var screen by remember { mutableStateOf(OfScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("OpsFeedProto", "ops-feed prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(OfColors.Paper)) {
        OfMasthead(repo = repo)
        OfDayBand(status = repo.dayStatus, onPick = { repo.dayStatus = it })
        Row(Modifier.fillMaxSize()) {
            OfRail(
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
                    OfScreen.ONBOARDING -> OfOnboarding(repo = repo, onNext = { screen = OfScreen.LOGIN })
                    OfScreen.LOGIN -> OfLogin(repo = repo, onNext = { screen = OfScreen.BRANCHES })
                    OfScreen.BRANCHES -> OfBranchSelect(repo = repo, onNext = { screen = OfScreen.FEED })
                    OfScreen.FEED -> OfFeed(repo = repo, go = { screen = it })
                    OfScreen.SESSIONS -> OfSessions(repo = repo)
                    OfScreen.CLIENTS -> OfClients(repo = repo)
                    OfScreen.FINANCE -> OfFinance(repo = repo)
                    OfScreen.TEAM -> OfTeam(repo = repo)
                    OfScreen.MAILBOX -> OfMailbox(repo = repo)
                    OfScreen.AUDIT -> OfAuditScreen(repo = repo)
                    OfScreen.PROFILE -> OfProfile(repo = repo, onLogout = { screen = OfScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun OfMasthead(repo: OpsFeedFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(OfColors.Ink).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("BRANCH OPS FEED", color = OfColors.Paper, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentBranch.name}  ·  ${repo.operationalDate}  ·  DISPATCH LOG",
                color = OfColors.Rail, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = OfMono,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (repo.clockedIn) "● ON SHIFT" else "○ OFF SHIFT",
                color = if (repo.clockedIn) androidx.compose.ui.graphics.Color(0xFF7FD6A2)
                else OfColors.Rail,
                fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = OfMono,
            )
            Text(
                (repo.currentUser?.name ?: "signed out").uppercase(),
                color = OfColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "${repo.dayPendingCount()} pending · ₱${repo.dayCompletedTotal()} banked",
                color = OfColors.Rail, fontSize = 12.sp, fontFamily = OfMono,
            )
        }
    }
}

@Composable
private fun OfDayBand(
    status: OfDayStatus,
    onPick: (OfDayStatus) -> Unit,
) {
    val band = when (status) {
        OfDayStatus.OPEN -> OfColors.Session
        OfDayStatus.PAST -> OfColors.Warn
        OfDayStatus.REMITTED -> OfColors.Note
    }
    Column(Modifier.fillMaxWidth().background(band).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BRANCH DAY: ${status.name}",
                color = OfColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = OfMono,
            )
            Spacer(Modifier.width(16.dp))
            OfDayStatus.entries.forEach { option ->
                val selected = option == status
                Box(
                    Modifier.background(
                        if (selected) OfColors.Paper else band,
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ).clickable { onPick(option) }.padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        option.name, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) band else OfColors.Paper,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Day boundary 04:00 Asia/Manila",
                color = OfColors.Paper, fontSize = 11.sp, fontFamily = OfMono,
            )
        }
    }
}

@Composable
private fun OfRail(
    screen: OfScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (OfScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.width(168.dp).fillMaxHeight().background(OfColors.Card)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Text(
            "CHANNELS", color = OfColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        OfScreen.entries.forEach { entry ->
            val locked = !loggedIn && entry != OfScreen.ONBOARDING && entry != OfScreen.LOGIN
            val selected = entry == screen
            val label = if (entry == OfScreen.MAILBOX && unread > 0) "${entry.title} ($unread)" else entry.title
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        if (selected) OfColors.Ink else androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ).clickable { if (!locked) onPick(entry) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    (if (locked) "◌ " else if (entry == OfScreen.FEED) "◉ " else "# ") + label,
                    color = when {
                        selected -> OfColors.Paper
                        locked -> OfColors.Rail
                        else -> OfColors.Ink
                    },
                    fontSize = 13.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        OfLink(label = "Exit prototype", onClick = onBack)
    }
}

@Composable
private fun OfOnboarding(
    repo: OpsFeedFakeRepo,
    onNext: () -> Unit,
) {
    var blockedNote by remember { mutableStateOf<String?>(null) }
    OfCard {
        OfSectionTitle("Shift radio check — onboarding")
        Text(
            "Signed in as J. Ramos (new hire) with the ONBOARDING role: zero capabilities. " +
                "The feed below is read-only until a coordinator grants a role.",
            color = OfColors.Ink, fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        OfRowButtons(
            {
                OfGhost("Try posting to the feed") {
                    blockedNote = "Blocked at 15:51 — ONBOARDING holds no post capability. Ask for a role grant."
                    repo.post(OfDomain.SYSTEM, "Blocked post attempt", "ONBOARDING user tried to post; denied.")
                }
            },
            {
                OfPrimary("Grant Practitioner role") {
                    repo.onboardingGranted = true
                    repo.audit("GRANT", "Practitioner role -> J. Ramos (new hire)", "onboarding walkthrough")
                    onNext()
                }
            },
        )
        if (blockedNote != null) {
            Spacer(Modifier.height(10.dp))
            Text(blockedNote!!, color = OfColors.Bad, fontSize = 13.sp, fontFamily = OfMono)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Rule under test: ONBOARDING is locked out of every mutation until MANAGE_USERS grants a role.",
            color = OfColors.Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun OfLogin(
    repo: OpsFeedFakeRepo,
    onNext: () -> Unit,
) {
    OfCard {
        OfSectionTitle("Sign on — fake directory")
        Text("Tap a name to take the shift. No passwords in the prototype.", color = OfColors.Ink, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        repo.users.forEach { user ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    repo.currentUserId = user.id
                    repo.post(OfDomain.SYSTEM, "${user.name} signed on", "Role ${user.role.name}, home ${user.homeBranchId}.")
                    onNext()
                }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(user.name, color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OfTag(user.role.name, if (user.role == OfRole.ONBOARDING) OfColors.Warn else OfColors.Note)
            }
        }
    }
}

@Composable
private fun OfBranchSelect(
    repo: OpsFeedFakeRepo,
    onNext: () -> Unit,
) {
    OfCard {
        OfSectionTitle("Pick the branch under dispatch")
        repo.branches.forEach { branch ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    repo.currentBranchId = branch.id
                    repo.post(OfDomain.SYSTEM, "Dispatch focus -> ${branch.name}", "Kind ${branch.kind.name}.")
                    onNext()
                }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(branch.name, color = OfColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(branch.kind.name, color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono)
                }
                Spacer(Modifier.weight(1f))
                if (branch.id == repo.currentBranchId) OfTag("TUNED IN", OfColors.Session)
            }
        }
    }
}

@Composable
private fun OfFeed(
    repo: OpsFeedFakeRepo,
    go: (OfScreen) -> Unit,
) {
    var filter by remember { mutableStateOf<OfDomain?>(null) }
    var note by remember { mutableStateOf("") }

    OfCard {
        OfSectionTitle("Now — ${repo.currentBranch.name}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (repo.clockedIn) "● CLOCKED IN" else "○ OFF DUTY",
                color = if (repo.clockedIn) OfColors.Session else OfColors.Muted,
                fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = OfMono,
            )
            Spacer(Modifier.weight(1f))
            if (repo.clockedIn) {
                OfGhost("Clock out") {
                    repo.clockedIn = false
                    repo.post(OfDomain.SYSTEM, "${repo.actorName()} clocked out", "Shift ended at ${repo.currentBranch.name}.")
                }
            } else {
                OfPrimary("Clock in") {
                    repo.clockedIn = true
                    repo.post(OfDomain.SYSTEM, "${repo.actorName()} clocked in", "Shift started at ${repo.currentBranch.name}.")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Relief traffic", color = OfColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        repo.invites.forEach { invite ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Invite: ${invite.fromUser} needs cover ${invite.branchId} / ${invite.day}",
                    color = OfColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (invite.accepted == null) {
                    OfLink("Accept") {
                        repo.invites[repo.invites.indexOf(invite)] = invite.copy(accepted = true)
                        repo.post(OfDomain.RELIEF, "Relief invite accepted", "${invite.branchId} / ${invite.day} covered.")
                    }
                    OfLink("Decline") {
                        repo.invites[repo.invites.indexOf(invite)] = invite.copy(accepted = false)
                        repo.post(OfDomain.RELIEF, "Relief invite declined", "${invite.branchId} / ${invite.day} stays open.")
                    }
                } else {
                    OfTag(if (invite.accepted == true) "COVERING" else "DECLINED", OfColors.Relief)
                }
            }
        }
        repo.requests.forEach { request ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Request: ${request.requester} -> ${request.branchId} / ${request.day}",
                    color = OfColors.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (request.decided == null) {
                    if (request.mine) {
                        OfLink("Withdraw") {
                            repo.requests[repo.requests.indexOf(request)] = request.copy(decided = "withdrawn")
                            repo.post(OfDomain.RELIEF, "Relief request withdrawn", "${request.branchId} / ${request.day}.")
                        }
                    } else {
                        OfLink("Grant") {
                            repo.requests[repo.requests.indexOf(request)] = request.copy(decided = "granted")
                            repo.audit("GRANT", "Relief access ${request.branchId} / ${request.day}")
                        }
                        OfLink("Deny") {
                            repo.requests[repo.requests.indexOf(request)] = request.copy(decided = "denied")
                            repo.post(OfDomain.RELIEF, "Relief request denied", "${request.requester}, ${request.day}.")
                        }
                    }
                } else {
                    OfTag(request.decided!!.uppercase(), OfColors.Relief)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OfLink("Open sessions queue", { go(OfScreen.SESSIONS) })
    }
    Spacer(Modifier.height(14.dp))
    OfCard {
        OfSectionTitle("Log a shift note")
        TextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("e.g. autoclave serviced, queue clear…") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = OfColors.Paper,
                unfocusedContainerColor = OfColors.Paper,
            ),
        )
        Spacer(Modifier.height(8.dp))
        OfPrimary("Post to the log", enabled = note.isNotBlank(), onClick = {
            repo.post(OfDomain.NOTE, "Shift note", note.trim())
            note = ""
        })
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OfChip("ALL", filter == null) { filter = null }
        OfDomain.entries.forEach { domain ->
            OfChip(domain.name, filter == domain) { filter = domain }
        }
    }
    Spacer(Modifier.height(10.dp))
    val visible = repo.feed.filter { filter == null || it.domain == filter }
    if (visible.isEmpty()) {
        OfCard {
            Text("Static on this channel — nothing logged yet.", color = OfColors.Muted, fontSize = 13.sp)
        }
    }
    visible.forEach { event ->
        OfEventRow(event = event)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OfEventRow(event: OfEvent) {
    val pip = OfDomainColor(event.domain)
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(event.timeLabel, color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono,
                fontWeight = FontWeight.Bold)
            Box(
                Modifier.width(3.dp).height(44.dp)
                    .background(pip, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).background(OfColors.Card, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfTag(event.domain.name, pip)
                    Spacer(Modifier.width(8.dp))
                    Text(event.headline, color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Text(event.detail, color = OfColors.Ink, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("— ${event.actor}", color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono)
            }
        }
    }
}
