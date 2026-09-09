package com.companyb.companyapp.proto.compactlaptop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

// #778 — compact-laptop shell: graphite bar, day banner, nav rail, tri-pane workbench.
// Fake data only; window floors at 1280x800.

enum class ClScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun CompactLaptopProtoApp(onBack: () -> Unit) {
    val repo = remember { CompactLaptopFakeRepo() }
    var screen by remember { mutableStateOf(ClScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("CompactLaptopProto", "compact-laptop prototype launched") }

    Column(Modifier.fillMaxSize().background(ClColors.Canvas)) {
        ClTopBar(repo = repo, onBack = onBack)
        ClDayBanner(repo = repo)
        if (repo.currentUserId == null) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                when (screen) {
                    ClScreen.LOGIN -> ClLogin(repo = repo, onNext = { screen = ClScreen.BRANCHES })
                    else -> ClOnboarding(repo = repo, onNext = { screen = ClScreen.LOGIN })
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                ClRail(screen = screen, unread = repo.notifs.count { !it.read }, onPick = { screen = it })
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(8.dp),
                ) {
                    when (screen) {
                        ClScreen.ONBOARDING -> ClOnboarding(repo = repo, onNext = { screen = ClScreen.HOME })
                        ClScreen.LOGIN -> ClLogin(repo = repo, onNext = { screen = ClScreen.BRANCHES })
                        ClScreen.BRANCHES -> ClBranchSelect(repo = repo, onNext = { screen = ClScreen.HOME })
                        ClScreen.HOME -> ClHome(repo = repo)
                        ClScreen.SESSIONS -> ClSessions(repo = repo)
                        ClScreen.CLIENTS -> ClClients(repo = repo)
                        ClScreen.FINANCE -> ClFinance(repo = repo)
                        ClScreen.TEAM -> ClTeam(repo = repo)
                        ClScreen.MAILBOX -> ClMailbox(repo = repo)
                        ClScreen.AUDIT -> ClAuditList(repo = repo)
                        ClScreen.PROFILE -> ClProfile(repo = repo, onLogout = { screen = ClScreen.LOGIN })
                    }
                }
                ClContext(repo = repo, go = { screen = it })
            }
        }
    }
}

@Composable
private fun ClTopBar(repo: CompactLaptopFakeRepo, onBack: () -> Unit) {
    val user = repo.currentUser
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(ClColors.Bar).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▦ COMPACT", color = ClColors.BarInk, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("  1280 workbench", color = ClColors.BarMuted, fontSize = 10.sp)
        Box(Modifier.weight(1f))
        if (user != null) {
            Text("${user.name} · ${repo.effectiveRole} · ${repo.selectedBranch.name}", color = ClColors.BarInk, fontSize = 11.sp)
            if (repo.clockedIn) {
                Text("  ● IN", color = Color(0xFF7BE3A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("signed out", color = ClColors.BarMuted, fontSize = 11.sp)
        }
        Text(
            "  Exit",
            color = ClColors.BarMuted,
            fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
        )
    }
}

@Composable
private fun ClDayBanner(repo: CompactLaptopFakeRepo) {
    val day = repo.selectedDay
    Row(
        Modifier.fillMaxWidth().background(day.status.band()).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day.status.name, color = day.status.ink(), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(
            "  ${repo.selectedBranch.name} · ${day.label}  |  day boundary 04:00 Asia/Manila",
            color = ClColors.Ink,
            fontSize = 11.sp,
        )
        Box(Modifier.weight(1f))
        repo.days.forEach { d ->
            Text(
                " ${d.label.substringBefore(" (")} ",
                color = if (d.id == day.id) Color.White else ClColors.Ink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(
                    if (d.id == day.id) ClColors.Bar else Color.Transparent,
                    RoundedCornerShape(3.dp),
                ).clickable { repo.selectedDayId = d.id }.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ClRail(screen: ClScreen, unread: Int, onPick: (ClScreen) -> Unit) {
    val items = listOf(
        ClScreen.HOME, ClScreen.SESSIONS, ClScreen.CLIENTS, ClScreen.FINANCE,
        ClScreen.TEAM, ClScreen.MAILBOX, ClScreen.AUDIT, ClScreen.PROFILE,
    )
    Column(
        Modifier.width(148.dp).fillMaxHeight().background(ClColors.Card)
            .border(0.dp, ClColors.Line).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ClMicro("Workbench")
        items.forEach { item ->
            val label = if (item == ClScreen.MAILBOX && unread > 0) "${item.title} ($unread)" else item.title
            Text(
                label,
                color = if (item == screen) Color.White else ClColors.Ink,
                fontSize = 11.sp,
                fontWeight = if (item == screen) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
                    .background(if (item == screen) ClColors.Accent else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { onPick(item) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Box(Modifier.weight(1f))
        ClMicro("1280x800 min · full-screen OK")
    }
}

@Composable
private fun ClContext(repo: CompactLaptopFakeRepo, go: (ClScreen) -> Unit) {
    val open = repo.daySessions.count { it.status == ClSessionStatus.PENDING && !it.voided }
    val done = repo.daySessions.count { it.status == ClSessionStatus.COMPLETED }
    val drafts = repo.remits.count { it.state == ClRemitState.DRAFT }
    Column(
        Modifier.width(252.dp).fillMaxHeight().background(ClColors.Card)
            .padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClMicro("Today at a glance")
        ClCard {
            ClSection("Wed load · ${repo.selectedBranch.name}")
            ClGap(4)
            Row {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    ClMicro("Open")
                    Text("$open", color = ClColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    ClMicro("Done")
                    Text("$done", color = ClColors.Open, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    ClMicro("Drafts")
                    Text("$drafts", color = ClColors.Past, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        ClCard {
            ClSection("Duty")
            ClGap(2)
            ClBody(if (repo.clockedIn) "Clocked in · ${repo.selectedBranch.name}" else "Not clocked in")
            ClBody("Relief edit: ${if (repo.reliefEdit) "granted" else "view-only"}")
            ClGap(4)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!repo.clockedIn) {
                    ClChip("In", true) { repo.clockIn(false) }
                } else {
                    ClChip("Out", false) { repo.clockOut() }
                }
                ClGhostBtn("Sessions") { go(ClScreen.SESSIONS) }
            }
        }
        ClCard {
            ClSection("Needs you")
            ClGap(2)
            val invites = repo.invites.count { it.state == "PENDING" }
            val reqs = repo.reliefRequests.count { it.state == "PENDING" }
            val unread = repo.notifs.count { !it.read }
            ClKeyRow("Invites", "$invites pending")
            ClKeyRow("Requests", "$reqs pending")
            ClKeyRow("Unread", "$unread")
            ClGap(4)
            ClGhostBtn("Open mailbox") { go(ClScreen.MAILBOX) }
        }
    }
}

@Composable
private fun ClOnboarding(repo: CompactLaptopFakeRepo, onNext: () -> Unit) {
    ClCard {
        ClMicro("Prototype · fake data")
        ClTitle("Compact workbench for small-laptop teams")
        ClGap(4)
        ClBody("One dense screen: rail left, work center, context right. No scroll marathons at 1280x800.")
        ClGap(4)
        ClSection("Try the locked account")
        ClBody("ONBOARDING carries an empty role bundle: even assigned a branch, nothing derives.")
        ClGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClBtn("Continue to login", onClick = onNext)
            if (repo.currentUserId == null) {
                ClGhostBtn("Preview locked") {
                    repo.login("u-ob")
                    onNext()
                }
            }
        }
        if (repo.locked && repo.currentUserId != null) {
            ClGap(4)
            ClNote("Locked: R. Nuevo (ONBOARDING) cannot open work screens until MANAGE_USERS grants a role.")
            ClGap(4)
            ClBtn("Grant Practitioner role") { repo.grantPractitioner() }
        }
    }
}

@Composable
private fun ClLogin(repo: CompactLaptopFakeRepo, onNext: () -> Unit) {
    ClCard {
        ClMicro("Login · fake directory")
        ClTitle("Who is on shift?")
        ClGap(4)
        repo.users.forEach { u ->
            Row(
                Modifier.fillMaxWidth().clickable { repo.login(u.id); onNext() }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("○ ", color = ClColors.Accent, fontSize = 11.sp)
                Column(Modifier.weight(1f)) {
                    Text(u.name, color = ClColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${u.role} · home ${repo.branches.first { it.id == u.homeBranchId }.name}", color = ClColors.Muted, fontSize = 10.sp)
                }
                if (u.role == ClRole.ONBOARDING) {
                    Text("LOCKED", color = ClColors.Danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        ClGap(4)
        ClNote("ONBOARDING signs in fine but stays locked: zero capabilities until a role grant.")
    }
}

@Composable
private fun ClBranchSelect(repo: CompactLaptopFakeRepo, onNext: () -> Unit) {
    ClCard {
        ClMicro("Branch select")
        ClTitle("Pick today's branch")
        ClGap(4)
        repo.branches.forEach { b ->
            Text(
                "${if (b.id == repo.selectedBranchId) "● " else "○ "}${b.name} · ${b.kind}",
                color = if (b.id == repo.selectedBranchId) ClColors.Accent else ClColors.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().clickable { repo.selectedBranchId = b.id }.padding(vertical = 4.dp),
            )
        }
        ClGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ClBtn("Open workbench", onClick = onNext)
            ClGhostBtn("Clock in here") { repo.clockIn(false); onNext() }
        }
        ClGap(4)
        ClNote("Branch kinds: CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION — inventory, sessions and money stay per branch.")
    }
}

@Composable
private fun ClHome(repo: CompactLaptopFakeRepo) {
    if (repo.locked) {
        ClCard {
            ClTitle("Locked")
            ClBody("ONBOARDING holds no capabilities. Ask a MANAGER for a role grant.")
            ClGap(4)
            ClBtn("Grant Practitioner role") { repo.grantPractitioner() }
        }
        return
    }
    ClCard {
        ClMicro("Clock-in · ${repo.selectedBranch.name}")
        ClTitle(if (repo.clockedIn) "On duty" else "Off duty")
        ClGap(2)
        ClBody("Home: ${repo.branches.first { it.id == (repo.currentUser?.homeBranchId ?: "") }.name}")
        ClGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!repo.clockedIn) {
                ClBtn("Clock in (home)") { repo.clockIn(false) }
                ClGhostBtn("Clock in as relief") { repo.clockIn(true) }
            } else {
                ClGhostBtn("Clock out") { repo.clockOut() }
            }
        }
        ClGap(4)
        ClNote("Relief duty starts view-only; edit needs a relief grant. Relief expires 04:00 Manila next day; pay comes from the relief branch drawer.")
    }
    ClGap(6)
    ClCard {
        ClMicro("Relief invites · branch-initiated, one future day each")
        ClTitle("Invites for you")
        ClGap(2)
        if (repo.invites.none { it.state == "PENDING" }) ClMuted("No pending invites.")
        repo.invites.forEach { inv ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${repo.branches.first { it.id == inv.branchId }.name} · ${inv.day}", color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(inv.state, color = ClColors.Muted, fontSize = 10.sp)
                }
                if (inv.state == "PENDING") {
                    ClChip("Accept", true) { repo.inviteAnswer(inv.id, true) }
                    Box(Modifier.width(4.dp))
                    ClChip("Decline", false) { repo.inviteAnswer(inv.id, false) }
                }
            }
        }
    }
    ClGap(6)
    ClCard {
        ClMicro("Relief requests · outsider-initiated broadcast, one live per requester/branch/date")
        ClTitle("Branch requests")
        ClGap(2)
        repo.reliefRequests.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${q.requester} → ${repo.branches.first { it.id == q.branchId }.name} · ${q.day}", color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(q.state, color = ClColors.Muted, fontSize = 10.sp)
                }
                if (q.state == "PENDING") {
                    if (q.requester == repo.currentUser?.name) {
                        ClGhostBtn("Withdraw") { repo.requestWithdraw(q.id) }
                    } else {
                        ClChip("Grant", true) { repo.requestDecide(q.id, true) }
                        Box(Modifier.width(4.dp))
                        ClChip("Deny", false) { repo.requestDecide(q.id, false) }
                    }
                }
            }
        }
        ClGap(2)
        ClNote("Granting writes the day relief grant and flips this device to edit. All retraction locks once the requester clocks in as relief.")
    }
}

@Composable
private fun ClTeam(repo: CompactLaptopFakeRepo) {
    ClCard {
        ClMicro("Team · users by home branch")
        ClTitle("Who works where")
        ClGap(2)
        repo.branches.forEach { b ->
            ClSection("${b.name} · ${b.kind}")
            repo.users.filter { it.homeBranchId == b.id }.forEach { u ->
                ClKeyRow(u.name, u.role.name)
            }
            ClGap(2)
        }
        ClNote("Roles: PRACTITIONER < COORDINATOR < MANAGER (superset: finance edit + user management + delegates); ACCOUNTANT reads everything, edits nothing; ONBOARDING is locked.")
    }
}

@Composable
private fun ClMailbox(repo: CompactLaptopFakeRepo) {
    ClCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ClMicro("Notifications mailbox")
                ClTitle("Inbox (${repo.notifs.count { !it.read }} unread)")
            }
            ClGhostBtn("Mark all read") { repo.markAllRead() }
        }
        ClGap(2)
        repo.notifs.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (n.read) "○ " else "● ", color = if (n.read) ClColors.Muted else ClColors.Accent, fontSize = 11.sp)
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(n.body, color = ClColors.Muted, fontSize = 10.sp)
                }
                if (!n.read) ClGhostBtn("Read") { repo.markRead(n.id) }
            }
        }
    }
}

@Composable
private fun ClAuditList(repo: CompactLaptopFakeRepo) {
    ClCard {
        ClMicro("Audit log · every prototype mutation prepends here")
        ClTitle("Audit (${repo.audits.size})")
        ClGap(2)
        repo.audits.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${a.who} · ${a.action} · ${a.target}", color = ClColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (a.reason.isNotBlank()) Text("reason: ${a.reason}", color = ClColors.Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ClProfile(repo: CompactLaptopFakeRepo, onLogout: () -> Unit) {
    val u = repo.currentUser ?: return
    ClCard {
        ClMicro("Profile")
        ClTitle(u.name)
        ClGap(2)
        ClKeyRow("Role", repo.effectiveRole.name)
        ClKeyRow("Bundle", repo.effectiveRole.bundle)
        ClKeyRow("Home", repo.branches.first { it.id == u.homeBranchId }.name)
        ClKeyRow("Duty", if (repo.clockedIn) "clocked in" else "off duty")
        ClGap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (repo.clockedIn) ClGhostBtn("Clock out") { repo.clockOut() }
            ClBtn("Logout") { repo.logout(); onLogout() }
        }
    }
}
