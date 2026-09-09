package com.companyb.companyapp.proto.wallboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

// #759 — wallboard ops screens: onboarding lock, login, branch select, home/relief, sessions, clients.

@Composable
fun WbOnboarding(
    repo: WallboardFakeRepo,
    onNext: () -> Unit,
) {
    var lockedNote by remember { mutableStateOf<String?>(null) }
    Column {
        Text("ONBOARDING", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Freshly registered users hold zero capabilities until a role is granted.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        WbPanel {
            Column {
                WbSectionTitle("LOCKED ACCOUNT — J. RAMOS (NEW HIRE)")
                Text("Role bundle: ONBOARDING → 0 capabilities", color = WbColors.Red, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
                WbNote("Branch assignment alone changes nothing: the role bundle is empty, so every " +
                    "capability check fails. Only MANAGE_USERS can grant a real role.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WbGhost(text = "Try opening Sessions", onClick = {
                        lockedNote = "Blocked: ONBOARDING role has no VIEW_BRANCH_DATA capability."
                    })
                    WbPrimary(text = "Grant Practitioner role", onClick = {
                        val index = repo.users.indexOfFirst { it.id == "u-new" }
                        repo.users[index] = repo.users[index].copy(role = WbRole.PRACTITIONER)
                        repo.audit("R. Aquino", "GRANT", "PRACTITIONER to J. Ramos", "onboarding complete")
                        lockedNote = null
                        onNext()
                    })
                }
                WbFieldError(lockedNote)
            }
        }
    }
}

@Composable
fun WbLogin(
    repo: WallboardFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("SIGN IN", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Fake directory — tap a user to sign in. No password, no network.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.users.forEach { user ->
            WbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = WbColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("${user.role.name} · home ${repo.branches.first { it.id == user.homeBranchId }.name}",
                            color = WbColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    WbPrimary(text = "Sign in", onClick = {
                        repo.login(user.id)
                        logInfo("WallboardProto", "signed in as ${user.id}")
                        onNext()
                    })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun WbBranchSelect(
    repo: WallboardFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("PICK A BRANCH", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("CLINIC, PROVINCIAL_TOUR, or MEDICAL_MISSION — each holds its own day.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { branch ->
            WbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name.uppercase(), color = WbColors.Ink, fontSize = 22.sp,
                            fontWeight = FontWeight.Black)
                        Text(branch.kind.name, color = WbColors.Amber, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    if (branch.id == repo.currentBranchId) {
                        Text("● ACTIVE", color = WbColors.Green, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    } else {
                        WbPrimary(text = "Switch", onClick = {
                            repo.currentBranchId = branch.id
                            repo.audit(repo.currentUser?.name ?: "wallboard", "SWITCH", "branch ${branch.name}")
                            onNext()
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun WbHome(
    repo: WallboardFakeRepo,
    go: (WbScreen) -> Unit,
) {
    Column {
        Text("TONIGHT ON THE BOARDS", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        WbPanel {
            Column {
                WbSectionTitle("CLOCK-IN — ${repo.currentBranch.name.uppercase()}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (repo.clockedIn) "● ON DUTY" else "○ OFF DUTY",
                        color = if (repo.clockedIn) WbColors.Green else WbColors.Muted,
                        fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    if (repo.clockedIn) {
                        WbGhost(text = "Clock out", onClick = {
                            repo.clockedIn = false
                            repo.audit(repo.currentUser?.name ?: "wallboard", "CLOCK_OUT", repo.currentBranch.name)
                        })
                    } else {
                        WbPrimary(text = "Clock in at home branch", onClick = {
                            repo.clockedIn = true
                            repo.audit(repo.currentUser?.name ?: "wallboard", "CLOCK_IN", repo.currentBranch.name)
                        })
                    }
                }
                WbNote("Clocking into a non-home branch starts Relief Duty: view-only until a relief grant lands.")
            }
        }
        Spacer(Modifier.height(12.dp))
        WbPanel {
            Column {
                WbSectionTitle("RELIEF INVITES (BRANCH-INITIATED)")
                if (repo.invites.isEmpty()) WbNote("No invites on the wire.")
                repo.invites.forEach { invite ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == invite.branchId }?.name ?: invite.branchId
                            Text("$branch / ${invite.day}", color = WbColors.Ink, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                            Text("from ${invite.fromUser} · " +
                                (invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "AWAITING YOU"),
                                color = WbColors.Muted, fontSize = 13.sp)
                        }
                        if (invite.accepted == null) {
                            WbPrimary(text = "Accept", onClick = { repo.decideInvite(invite.id, true) })
                            WbSpacerRow()
                            WbGhost(text = "Decline", onClick = { repo.decideInvite(invite.id, false) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        WbPanel {
            Column {
                WbSectionTitle("RELIEF REQUESTS (USER-INITIATED BROADCAST)")
                if (repo.requests.isEmpty()) WbNote("No live requests.")
                repo.requests.forEach { request ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == request.branchId }?.name
                                ?: request.branchId
                            Text("$branch / ${request.day} — ${request.requester}",
                                color = WbColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(request.decided ?: (if (request.mine) "live — withdraw any time" else "live — any member decides"),
                                color = WbColors.Muted, fontSize = 13.sp)
                        }
                        if (request.decided == null) {
                            if (request.mine) {
                                WbGhost(text = "Withdraw", onClick = { repo.decideRequest(request.id, "withdrawn") })
                            } else {
                                WbPrimary(text = "Grant", onClick = { repo.decideRequest(request.id, "granted") })
                                WbSpacerRow()
                                WbGhost(text = "Deny", onClick = { repo.decideRequest(request.id, "denied") })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                WbNote("One live request per requester per branch per date. All retraction locks once the " +
                    "requester clocks in as relief. Compensation is paid from the relief branch drawer.")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WbPrimary(text = "Open sessions", onClick = { go(WbScreen.SESSIONS) })
            WbGhost(text = "Open finance", onClick = { go(WbScreen.FINANCE) })
        }
    }
}

@Composable
fun WbSessions(repo: WallboardFakeRepo) {
    var filter by remember { mutableStateOf<WbSessionStatus?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("SESSIONS", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WbChip(text = "ALL", selected = filter == null, onClick = { filter = null })
            WbSessionStatus.entries.forEach { status ->
                WbChip(text = status.name, selected = filter == status, onClick = { filter = status })
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        list.forEach { session ->
            WbPanel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = session.status.dot(), fontSize = 20.sp)
                        WbSpacerRow()
                        Column(Modifier.weight(1f)) {
                            Text("${session.bookedTime}  ${session.clientName}", color = WbColors.Ink,
                                fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("${session.type} · ₱${session.price} · ${session.practitioner}" +
                                (if (session.walkIn) " · WALK-IN" else ""),
                                color = WbColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(session.status.name, color = session.status.dot(), fontWeight = FontWeight.Black)
                            if (session.voided) Text("VOIDED", color = WbColors.Red, fontWeight = FontWeight.Black,
                                fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val expanded = selectedId == session.id
                        WbGhost(text = if (expanded) "Close" else "Manage",
                            onClick = { selectedId = if (expanded) null else session.id })
                    }
                    if (selectedId == session.id) {
                        Spacer(Modifier.height(8.dp))
                        Text("STATUS (from PENDING)", color = WbColors.Muted, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WbSessionStatus.entries.forEach { next ->
                                WbChip(text = next.name, selected = session.status == next, onClick = {
                                    error = repo.setSessionStatus(session.id, next)
                                })
                            }
                        }
                        WbNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = reason,
                            onValueChange = { reason = it; error = null },
                            label = { Text(if (session.voided) "Unvoid reason" else "Void reason") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.voided) {
                                WbPrimary(text = "Unvoid", onClick = {
                                    error = repo.setVoid(session.id, false, reason)
                                    if (error == null) reason = ""
                                })
                            } else {
                                WbPrimary(text = "Void session", onClick = {
                                    error = repo.setVoid(session.id, true, reason)
                                    if (error == null) reason = ""
                                })
                            }
                        }
                        if (session.voidReason != null) WbNote("Void reason on record: ${session.voidReason}")
                        WbFieldError(error)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun WbClients(repo: WallboardFakeRepo) {
    Column {
        Text("CLIENTS — GLOBAL", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("One shared record across all branches. At most one PENDING session per client.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.clients.forEach { client ->
            val pending = repo.pendingCountFor(client.id)
            WbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(client.name, color = WbColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("${client.gender} · age ${client.age} · $pending PENDING" +
                            (if (client.anonymized) " · ANONYMIZED" else ""),
                            color = WbColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!client.anonymized) {
                        WbGhost(text = "Anonymize", onClick = { repo.anonymize(client.id) })
                    }
                }
                WbNote("Anonymize nullifies PII but retains gender + age for reporting.")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
