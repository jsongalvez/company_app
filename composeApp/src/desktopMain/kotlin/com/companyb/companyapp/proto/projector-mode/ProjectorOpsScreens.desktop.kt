package com.companyb.companyapp.proto.projectormode

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

// #777 — projector ops screens: onboarding lock, login, branch select, stage/relief,
// sessions with void flow, global clients. Giant type, one idea per card.

@Composable
fun PmOnboarding(
    repo: ProjectorFakeRepo,
    onNext: () -> Unit,
) {
    var lockedNote by remember { mutableStateOf<String?>(null) }
    Column {
        Text("NEW HERE?", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("ONBOARDING", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text(
            "Fresh accounts hold zero capabilities until a role is granted.",
            color = PmColors.Muted,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        PmPanel {
            Column {
                PmSectionTitle("Locked account — J. Ramos (new hire)")
                Text(
                    "ONBOARDING → 0 CAPABILITIES",
                    color = PmColors.Red,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                PmNote(
                    "Branch assignment alone changes nothing: the role bundle is empty, " +
                        "so every capability check fails. Only MANAGE_USERS can grant a real role.",
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PmGhost(text = "Try sessions", onClick = {
                        lockedNote = "Blocked: ONBOARDING has no VIEW_BRANCH_DATA capability."
                    })
                    PmPrimary(text = "Grant practitioner", onClick = {
                        val index = repo.users.indexOfFirst { it.id == "u-new" }
                        repo.users[index] = repo.users[index].copy(role = PmRole.PRACTITIONER)
                        repo.audit("R. Aquino", "GRANT", "PRACTITIONER to J. Ramos", "onboarding complete")
                        lockedNote = null
                        onNext()
                    })
                }
                PmFieldError(lockedNote)
            }
        }
    }
}

@Composable
fun PmLogin(
    repo: ProjectorFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("WHO IS ON STAGE?", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("SIGN IN", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text("Fake directory — tap to sign in. No password, no network.", color = PmColors.Muted, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))
        repo.users.forEach { user ->
            PmPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = PmColors.Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${user.role.name} · ${repo.branches.first { it.id == user.homeBranchId }.name}",
                            color = PmColors.Muted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    PmPrimary(text = "Sign in", onClick = {
                        repo.login(user.id)
                        logInfo("ProjectorProto", "signed in as ${user.id}")
                        onNext()
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PmBranchSelect(
    repo: ProjectorFakeRepo,
    onNext: () -> Unit,
) {
    Column {
        Text("WHERE TODAY?", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("PICK A BRANCH", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text("Each branch holds its own day.", color = PmColors.Muted, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))
        repo.branches.forEach { branch ->
            PmPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            branch.name.uppercase(),
                            color = PmColors.Ink,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(branch.kind.name, color = PmColors.Focus, fontSize = 22.sp,
                            fontWeight = FontWeight.Black)
                    }
                    if (branch.id == repo.currentBranchId) {
                        Text("● ACTIVE", color = PmColors.Green, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    } else {
                        PmPrimary(text = "Switch", onClick = {
                            repo.currentBranchId = branch.id
                            repo.audit(repo.currentUser?.name ?: "projector", "SWITCH", "branch ${branch.name}")
                            onNext()
                        })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PmHome(
    repo: ProjectorFakeRepo,
    go: (PmScreen) -> Unit,
) {
    Column {
        Text("TONIGHT", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("ON STAGE", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        PmPanel {
            Column {
                PmSectionTitle("Clock-in — ${repo.currentBranch.name}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (repo.clockedIn) "● ON DUTY" else "○ OFF DUTY",
                        color = if (repo.clockedIn) PmColors.Green else PmColors.Muted,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (repo.clockedIn) {
                        PmGhost(text = "Clock out", onClick = {
                            repo.clockedIn = false
                            repo.audit(repo.currentUser?.name ?: "projector", "CLOCK_OUT", repo.currentBranch.name)
                        })
                    } else {
                        PmPrimary(text = "Clock in", onClick = {
                            repo.clockedIn = true
                            repo.audit(repo.currentUser?.name ?: "projector", "CLOCK_IN", repo.currentBranch.name)
                        })
                    }
                }
                PmNote("Clocking into a non-home branch starts Relief Duty: view-only until a relief grant lands.")
            }
        }
        Spacer(Modifier.height(16.dp))
        PmPanel {
            Column {
                PmSectionTitle("Relief invites (branch-initiated)")
                if (repo.invites.isEmpty()) PmNote("No invites on the wire.")
                repo.invites.forEach { invite ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == invite.branchId }?.name ?: invite.branchId
                            Text(
                                "$branch / ${invite.day}",
                                color = PmColors.Ink,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "from ${invite.fromUser} · " +
                                    (invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "AWAITING YOU"),
                                color = PmColors.Muted,
                                fontSize = 22.sp,
                            )
                        }
                        if (invite.accepted == null) {
                            PmPrimary(text = "Accept", onClick = { repo.decideInvite(invite.id, true) })
                            PmSpacerRow()
                            PmGhost(text = "Decline", onClick = { repo.decideInvite(invite.id, false) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PmPanel {
            Column {
                PmSectionTitle("Relief requests (user broadcast)")
                if (repo.requests.isEmpty()) PmNote("No live requests.")
                repo.requests.forEach { request ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            val branch = repo.branches.firstOrNull { it.id == request.branchId }?.name
                                ?: request.branchId
                            Text(
                                "$branch / ${request.day}",
                                color = PmColors.Ink,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "${request.requester} · " +
                                    (request.decided ?: if (request.mine) "LIVE — WITHDRAW ANY TIME"
                                    else "LIVE — ANY MEMBER DECIDES"),
                                color = PmColors.Muted,
                                fontSize = 22.sp,
                            )
                        }
                        if (request.decided == null) {
                            if (request.mine) {
                                PmGhost(text = "Withdraw", onClick = { repo.decideRequest(request.id, "withdrawn") })
                            } else {
                                PmPrimary(text = "Grant", onClick = { repo.decideRequest(request.id, "granted") })
                                PmSpacerRow()
                                PmGhost(text = "Deny", onClick = { repo.decideRequest(request.id, "denied") })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                PmNote(
                    "One live request per requester per branch per date. Compensation is paid " +
                        "from the relief branch drawer.",
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PmPrimary(text = "Sessions", onClick = { go(PmScreen.SESSIONS) })
            PmGhost(text = "Finance", onClick = { go(PmScreen.FINANCE) })
        }
    }
}

@Composable
fun PmSessions(repo: ProjectorFakeRepo) {
    var filter by remember { mutableStateOf<PmSessionStatus?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("NOW / NEXT", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("SESSIONS", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PmChip(text = "All", selected = filter == null, onClick = { filter = null })
            PmSessionStatus.entries.forEach { status ->
                PmChip(text = status.name, selected = filter == status, onClick = { filter = status })
            }
        }
        Spacer(Modifier.height(16.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        list.forEach { session ->
            PmPanel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = session.status.dot(), fontSize = 40.sp)
                        PmSpacerRow()
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${session.bookedTime}  ${session.clientName}",
                                color = PmColors.Ink,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "${session.type} · ₱${session.price} · ${session.practitioner}" +
                                    (if (session.walkIn) " · WALK-IN" else ""),
                                color = PmColors.Muted,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(session.status.name, color = session.status.dot(),
                                fontWeight = FontWeight.Black, fontSize = 28.sp)
                            if (session.voided) Text("VOIDED", color = PmColors.Red,
                                fontWeight = FontWeight.Black, fontSize = 22.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val expanded = selectedId == session.id
                        PmGhost(
                            text = if (expanded) "Close" else "Manage",
                            onClick = { selectedId = if (expanded) null else session.id },
                        )
                    }
                    if (selectedId == session.id) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "STATUS (FROM PENDING)",
                            color = PmColors.Muted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PmSessionStatus.entries.forEach { next ->
                                PmChip(text = next.name, selected = session.status == next, onClick = {
                                    error = repo.setSessionStatus(session.id, next)
                                })
                            }
                        }
                        PmNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = reason,
                            onValueChange = { reason = it; error = null },
                            label = { Text(if (session.voided) "Unvoid reason" else "Void reason") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (session.voided) {
                                PmPrimary(text = "Unvoid", onClick = {
                                    error = repo.setVoid(session.id, false, reason)
                                    if (error == null) reason = ""
                                })
                            } else {
                                PmPrimary(text = "Void session", onClick = {
                                    error = repo.setVoid(session.id, true, reason)
                                    if (error == null) reason = ""
                                })
                            }
                        }
                        if (session.voidReason != null) PmNote("Void reason on record: ${session.voidReason}")
                        PmFieldError(error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PmClients(repo: ProjectorFakeRepo) {
    Column {
        Text("EVERYONE", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("CLIENTS", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text(
            "One shared record across all branches. At most one PENDING session per client.",
            color = PmColors.Muted,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        repo.clients.forEach { client ->
            val pending = repo.pendingCountFor(client.id)
            PmPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(client.name, color = PmColors.Ink, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${client.gender} · age ${client.age} · $pending PENDING" +
                                (if (client.anonymized) " · ANONYMIZED" else ""),
                            color = PmColors.Muted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!client.anonymized) {
                        PmGhost(text = "Anonymize", onClick = { repo.anonymize(client.id) })
                    }
                }
                PmNote("Anonymize nullifies PII but retains gender + age for reporting.")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
