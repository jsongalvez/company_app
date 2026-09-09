package com.companyb.companyapp.proto.calendarops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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

// #775 — calendar-ops planning flows: onboarding lock, login, branches, week home, day, sessions, clients.

@Composable
fun CoOnboarding(repo: CalendarOpsFakeRepo, onNext: () -> Unit) {
    var lockedNote by remember { mutableStateOf<String?>(null) }
    Column {
        CoTitle("ONBOARDING")
        CoSubtitle("Freshly registered users hold zero capabilities until a role is granted.")
        CoGap()
        CoCard {
            CoSection("LOCKED ACCOUNT — J. RAMOS (NEW HIRE)")
            Text(
                "Role bundle: ONBOARDING → 0 capabilities",
                color = CoColors.Today,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            CoNote("A branch assignment alone changes nothing: the role bundle is empty, so every " +
                "capability check fails. Only MANAGE_USERS can grant a real role.")
            Spacer(Modifier.height(12.dp))
            CoButtonRow {
                CoGhost(text = "Try opening Sessions") {
                    lockedNote = "Blocked: ONBOARDING role has no VIEW_BRANCH_DATA capability."
                }
                CoPrimary(text = "Grant Practitioner role") {
                    val index = repo.users.indexOfFirst { it.id == "u-new" }
                    repo.users[index] = repo.users[index].copy(role = CoRole.PRACTITIONER)
                    repo.audit("R. Aquino", "GRANT", "PRACTITIONER to J. Ramos", "onboarding complete")
                    lockedNote = null
                    onNext()
                }
            }
            CoError(lockedNote)
        }
    }
}

@Composable
fun CoLogin(repo: CalendarOpsFakeRepo, onNext: () -> Unit) {
    Column {
        CoTitle("SIGN IN")
        CoSubtitle("Fake directory — tap a user to sign in. No password, no network.")
        CoGap()
        repo.users.forEach { user ->
            CoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = CoColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${user.role.name} · home ${repo.branches.first { it.id == user.homeBranchId }.name}",
                            color = CoColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    CoPrimary(text = "Sign in") {
                        repo.login(user.id)
                        logInfo("CalendarOpsProto", "signed in as ${user.id}")
                        onNext()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun CoBranchSelect(repo: CalendarOpsFakeRepo, onNext: () -> Unit) {
    Column {
        CoTitle("PICK A BRANCH")
        CoSubtitle("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION — each holds its own week.")
        CoGap()
        repo.branches.forEach { branch ->
            CoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name.uppercase(), color = CoColors.Ink, fontSize = 20.sp,
                            fontWeight = FontWeight.Black)
                        Text(branch.kind.name, color = CoColors.Past, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    if (branch.id == repo.currentBranchId) {
                        Text("● ACTIVE", color = CoColors.Open, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    } else {
                        CoPrimary(text = "Switch") {
                            repo.currentBranchId = branch.id
                            repo.audit(repo.currentUser?.name ?: "planner", "SWITCH", "branch ${branch.name}")
                            onNext()
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun CoWeekHome(repo: CalendarOpsFakeRepo, go: (CoScreen) -> Unit) {
    val user = repo.currentUser
    Column {
        CoTitle("WEEK AT A GLANCE")
        CoSubtitle("Tap any day cell above to open it. Today: WED Sep 9, branch ${repo.currentBranch.name}.")
        CoGap()
        CoCard {
            CoSection("CLOCK-IN")
            if (user == null) {
                CoNote("Sign in first — the planner needs a user before anyone can clock in.")
            } else if (repo.clockedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${user.name} is on duty at ${repo.currentBranch.name}",
                            color = CoColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        CoNote("Relief duty at a non-home branch starts view-only; a relief grant unlocks edits.")
                    }
                    CoGhost(text = "Clock out") {
                        repo.clockedIn = false
                        repo.audit(user.name, "CLOCK_OUT", repo.currentBranch.name)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Off duty — clock in to start ${user.name}'s day",
                            color = CoColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        CoNote("Clock-in is per branch day and expires at 04:00 Manila next morning.")
                    }
                    CoPrimary(text = "Clock in") {
                        repo.clockedIn = true
                        repo.audit(user.name, "CLOCK_IN", "${repo.currentBranch.name} / Sep 9")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CoCard {
            CoSection("RELIEF DUTY")
            CoNote("Invites come from a branch; requests go out from you. Accepting writes the day grant. " +
                "Any branch member may revoke an accepted future duty until the invitee clocks in.")
            Spacer(Modifier.height(8.dp))
            repo.invites.forEach { invite ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Invite: ${invite.branchName} · ${invite.day}",
                            color = CoColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        CoNote("from ${invite.fromUser}" +
                            (invite.accepted?.let { if (it) " — accepted" else " — declined" } ?: ""))
                    }
                    if (invite.accepted == null) {
                        CoButtonRow {
                            CoPrimary(text = "Accept") {
                                val i = repo.invites.indexOfFirst { it.id == invite.id }
                                repo.invites[i] = invite.copy(accepted = true)
                                repo.audit(user?.name ?: "planner", "RELIEF_ACCEPT", invite.branchName)
                            }
                            CoGhost(text = "Decline") {
                                val i = repo.invites.indexOfFirst { it.id == invite.id }
                                repo.invites[i] = invite.copy(accepted = false)
                                repo.audit(user?.name ?: "planner", "RELIEF_DECLINE", invite.branchName)
                            }
                        }
                    }
                }
            }
            repo.requests.forEach { request ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${if (request.mine) "My request" else "Incoming request"}: " +
                            "${request.branchName} · ${request.day}",
                            color = CoColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        CoNote(request.decided?.let { "decided: $it" }
                            ?: "One live request per requester per branch per date.")
                    }
                    if (!request.mine && request.decided == null) {
                        CoButtonRow {
                            CoPrimary(text = "Grant") {
                                val i = repo.requests.indexOfFirst { it.id == request.id }
                                repo.requests[i] = request.copy(decided = "granted")
                                repo.audit(user?.name ?: "planner", "RELIEF_GRANT", request.branchName)
                            }
                            CoGhost(text = "Deny") {
                                val i = repo.requests.indexOfFirst { it.id == request.id }
                                repo.requests[i] = request.copy(decided = "denied")
                                repo.audit(user?.name ?: "planner", "RELIEF_DENY", request.branchName)
                            }
                        }
                    }
                    if (request.mine && request.decided == null) {
                        CoGhost(text = "Withdraw") {
                            val i = repo.requests.indexOfFirst { it.id == request.id }
                            repo.requests[i] = request.copy(decided = "withdrawn")
                            repo.audit(user?.name ?: "planner", "RELIEF_WITHDRAW", request.branchName)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CoCard {
            CoSection("JUMP TO")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoGhost(text = "Open today") { go(CoScreen.DAY) }
                CoGhost(text = "Sessions") { go(CoScreen.SESSIONS) }
                CoGhost(text = "Finance") { go(CoScreen.FINANCE) }
            }
        }
    }
}

@Composable
fun CoDayDetail(repo: CalendarOpsFakeRepo, go: (CoScreen) -> Unit) {
    val day = repo.selectedDay
    val list = repo.sessionsFor(day.id)
    Column {
        CoTitle("${day.dow} ${day.dateLabel.uppercase()}")
        CoSubtitle("Branch day is ${day.status.name} — tap a session to manage it, or jump to full lists.")
        CoGap()
        list.forEach { session ->
            CoSessionRow(repo = repo, session = session)
            Spacer(Modifier.height(10.dp))
        }
        if (list.isEmpty()) {
            CoCard { CoNote("No sessions booked at ${repo.currentBranch.name} on this day yet.") }
            Spacer(Modifier.height(10.dp))
        }
        CoButtonRow {
            CoGhost(text = "All sessions") { go(CoScreen.SESSIONS) }
            CoGhost(text = "Back to week") { go(CoScreen.WEEK) }
        }
    }
}

@Composable
fun CoSessions(repo: CalendarOpsFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("16:00") }
    val user = repo.currentUser?.name ?: "planner"
    Column {
        CoTitle("SESSIONS")
        CoSubtitle("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-ins refuse NO_SHOW and CANCELLED.")
        CoGap()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoChip("ALL", filter == null) { filter = null }
            CoSessionStatus.entries.forEach { status ->
                CoChip(status.name, filter == status.name) { filter = status.name }
            }
        }
        Spacer(Modifier.height(10.dp))
        repo.sessions.filter { filter == null || it.status.name == filter }.forEach { session ->
            CoSessionRow(repo = repo, session = session)
            Spacer(Modifier.height(10.dp))
        }
        CoCard {
            CoSection("BOOK WALK-IN ON ${repo.selectedDay.dow} ${repo.selectedDay.dateLabel}")
            Spacer(Modifier.height(8.dp))
            TextField(value = name, onValueChange = { name = it }, label = { Text("Client name") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = time, onValueChange = { time = it }, label = { Text("Time") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CoPrimary(text = "Add walk-in") {
                if (name.isNotBlank()) {
                    repo.addWalkIn(name.trim(), time.trim().ifBlank { "16:00" }, user)
                    name = ""
                }
            }
        }
    }
}

@Composable
fun CoSessionRow(repo: CalendarOpsFakeRepo, session: CoSession) {
    var expanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val user = repo.currentUser?.name ?: "planner"
    CoCard {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${session.time} · ${session.clientName}",
                        color = CoColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("${session.type} · ${session.practitioner}${if (session.walkIn) " · WALK-IN" else ""}",
                        color = CoColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(session.status.name,
                    color = when (session.status) {
                        CoSessionStatus.PENDING -> CoColors.Past
                        CoSessionStatus.COMPLETED -> CoColors.Open
                        else -> CoColors.Today
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 8.dp))
                CoGhost(if (expanded) "Close" else "Manage") { expanded = !expanded }
            }
            if (session.voided) {
                CoNote("VOIDED — reason: ${session.voidReason ?: "—"}")
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoSessionStatus.entries.forEach { status ->
                        CoChip(status.name, session.status == status) {
                            error = repo.setStatus(session.id, status, user)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Void reason (required)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                CoButtonRow {
                    if (!session.voided) {
                        CoPrimary(text = "Void") { error = repo.setVoid(session.id, true, reason.trim(), user) }
                    } else {
                        CoGhost(text = "Unvoid") { error = repo.setVoid(session.id, false, "unvoid", user) }
                    }
                }
                CoError(error)
            }
        }
    }
}

@Composable
fun CoClients(repo: CalendarOpsFakeRepo) {
    var anonymizedNote by remember { mutableStateOf(false) }
    Column {
        CoTitle("CLIENTS")
        CoSubtitle("Global person records — at most one PENDING session per client at a time.")
        CoGap()
        CoButtonRow {
            CoGhost(text = if (anonymizedNote) "Show PII" else "Anonymized view") { anonymizedNote = !anonymizedNote }
        }
        Spacer(Modifier.height(6.dp))
        repo.clients.forEach { client ->
            val pending = repo.pendingFor(client.id)
            CoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (anonymizedNote) "Client ${client.id.uppercase()}" else client.name,
                            color = CoColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("${client.gender} · age ${client.age}" +
                            (if (anonymizedNote) " · PII masked" else ""),
                            color = CoColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        CoNote(if (pending > 0) "$pending PENDING session${if (pending > 1) "s" else ""}" +
                            " — booking another is blocked by the at-most-one-PENDING rule."
                        else "No PENDING session — eligible to book.")
                    }
                    if (repo.anonymizedIds.contains(client.id)) {
                        Text("ANONYMIZED", color = CoColors.Remitted, fontSize = 12.sp,
                            fontWeight = FontWeight.Black)
                    } else {
                        CoGhost(text = "Anonymize") {
                            repo.anonymizedIds = repo.anonymizedIds + client.id
                            repo.audit(repo.currentUser?.name ?: "planner", "ANONYMIZE",
                                client.name, "soft-delete + PII nullification")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
