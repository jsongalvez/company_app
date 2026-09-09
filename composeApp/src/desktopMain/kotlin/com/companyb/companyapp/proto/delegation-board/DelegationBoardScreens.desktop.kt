package com.companyb.companyapp.proto.delegationboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun DBoardScreen(
    repo: DelegationBoardFakeRepo,
    onOpenMail: () -> Unit,
) {
    val me = repo.currentUser()
    val branchId = repo.currentBranchId.value
    var inviteName by remember { mutableStateOf("Iko T.") }

    SectionHeader(index = "B-1", title = "Coverage gaps", aside = "red tags first")
    val openGaps = repo.gaps.filter { it.have < it.need }
    if (openGaps.isEmpty()) {
        EmptyManifest("Every post is covered. The mission breathes.")
    } else {
        openGaps.forEach { gap ->
            ManifestCard(accent = TriageRed) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = repo.branchName(gap.branchId), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        ManifestCode("NEED ${gap.need} · HAVE ${gap.have} · SHORT ${gap.need - gap.have}")
                        Text(text = "Still needed: ${gap.roles}", fontSize = 12.sp, color = ManifestMuted)
                    }
                    TriageTag(text = "Gap ${gap.need - gap.have}", color = TriageRed, soft = TriageRedSoft)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    BoardButton(
                        text = "Invite help here",
                        onClick = { repo.sendInvite(inviteName, gap.branchId) },
                    )
                    Spacer(Modifier.width(8.dp))
                    BoardGhostButton(text = "Ask for relief", onClick = { repo.broadcastRequest(gap.branchId) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    ManifestCard {
        Text(text = "Invite a practitioner by name", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inviteName,
                onValueChange = { inviteName = it },
                label = { Text("Practitioner name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            BoardButton(text = "Send invite", onClick = { repo.sendInvite(inviteName, branchId) })
        }
    }
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "B-2", title = "Invite / accept inbox", aside = "tap to decide")
    val inbox = repo.invites.filter { it.direction == "IN" }
    if (inbox.none { it.status == DInviteStatus.PENDING }) {
        EmptyManifest("Inbox zero. No delegate waits on you.", "Open mailbox", onOpenMail)
    } else {
        inbox.forEach { item ->
            if (item.status == DInviteStatus.PENDING) {
                ManifestCard(accent = TriageAmber) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (item.kind == DInviteKind.INVITE) "Relief invite" else "Relief request",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(text = "${item.who} · ${repo.branchName(item.branchId)}", fontSize = 13.sp)
                            ManifestCode(item.day.uppercase())
                        }
                        StatusTag(item.status.name)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        BoardButton(
                            text = if (item.kind == DInviteKind.INVITE) "Accept duty" else "Grant access",
                            onClick = { repo.decideInvite(item.id, true) },
                        )
                        Spacer(Modifier.width(8.dp))
                        BoardGhostButton(text = "Decline", onClick = { repo.decideInvite(item.id, false) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    val decided = repo.invites.filter { it.status != DInviteStatus.PENDING }
    if (decided.isNotEmpty()) {
        Text(text = "Decided this week", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ManifestMuted)
        Spacer(Modifier.height(4.dp))
        decided.take(4).forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                ManifestCode("${item.kind} · ${item.who} · ${repo.branchName(item.branchId)}".uppercase())
                Spacer(Modifier.width(8.dp))
                StatusTag(item.status.name)
                if (item.status == DInviteStatus.ACCEPTED || item.status == DInviteStatus.GRANTED) {
                    Spacer(Modifier.width(8.dp))
                    BoardLink(text = "Revoke", onClick = { repo.revokeInvite(item.id) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    SectionHeader(index = "B-3", title = "Duty roster", aside = "branch slot order")
    val roster = repo.users.filter { it.clockedBranchId == branchId }.sortedBy { it.slot }
    if (roster.isEmpty()) {
        EmptyManifest("Nobody clocked in at this post yet.")
    } else {
        roster.forEach { u ->
            ManifestCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ManifestCode("#${u.slot.toString().padStart(2, '0')}")
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "${u.role} · home: ${repo.branchName(u.homeBranchId)}",
                            fontSize = 12.sp,
                            color = ManifestMuted,
                        )
                    }
                    if (u.homeBranchId != branchId) {
                        TriageTag(
                            text = if (u.reliefEdit) "Relief +edit" else "Relief view-only",
                            color = DispatchTeal,
                            soft = DispatchTealSoft,
                        )
                    } else {
                        TriageTag(text = "Home", color = TriageGreen, soft = TriageGreenSoft)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    FieldNote(
        "Relief duty starts view-only; edit access needs a relief grant — a broadcast request any branch member approves, or a branch invite the invitee accepts. Grants expire 04:00 Manila next day.",
    )
    Spacer(Modifier.height(8.dp))
    ManifestCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (me.clockedBranchId == null) "You are off duty" else "You are on duty",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                ManifestCode((me.clockedBranchId?.let { "POSTED AT ${repo.branchName(it).uppercase()}" } ?: "NO POST"))
            }
            if (me.clockedBranchId == null) {
                BoardButton(text = "Clock in here", onClick = { repo.clockIn(branchId) })
            } else {
                BoardGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            }
        }
    }
}

@Composable
fun DSessionsScreen(repo: DelegationBoardFakeRepo) {
    var filter by remember { mutableStateOf<DSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("Walk-in delegate") }
    var newType by remember { mutableStateOf("First visit") }
    var newPrice by remember { mutableStateOf("800") }

    SectionHeader(index = "S-1", title = "Sessions", aside = repo.branchName(repo.currentBranchId.value))
    FieldNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — the delegate is already here.")
    Spacer(Modifier.height(8.dp))
    Row {
        BoardGhostButton(text = "All") { filter = null }
        Spacer(Modifier.width(6.dp))
        DSessionStatus.entries.forEach { s ->
            Spacer(Modifier.width(6.dp))
            if (filter == s) {
                BoardButton(text = s.name) { filter = s }
            } else {
                BoardGhostButton(text = s.name) { filter = s }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val list =
        repo.sessions.filter {
            it.branchId == repo.currentBranchId.value &&
                (filter == null || it.status == filter)
        }
    if (list.isEmpty()) {
        EmptyManifest("No sessions under this tag at this post.")
    } else {
        list.forEach { s ->
            ManifestCard(accent = if (s.voided) ManifestLine else null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = s.clientName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        ManifestCode("${s.id.uppercase()} · ${s.time} · ${s.type.uppercase()} · P${s.price}")
                        if (s.walkIn) {
                            Text(text = "Walk-in", fontSize = 11.sp, color = DispatchTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                    StatusTag(s.status.name)
                    if (s.voided) {
                        Spacer(Modifier.width(6.dp))
                        TriageTag(text = "Voided", color = ManifestMuted, soft = DispatchTealSoft)
                    }
                    Spacer(Modifier.width(8.dp))
                    BoardLink(
                        text = if (expanded == s.id) "Hide" else "Open",
                        onClick = { expanded = if (expanded == s.id) null else s.id },
                    )
                }
                if (expanded == s.id) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        BoardGhostButton(text = "Complete") { repo.moveSession(s.id, DSessionStatus.COMPLETED) }
                        Spacer(Modifier.width(6.dp))
                        BoardGhostButton(text = "No-show") {
                            if (!s.walkIn) repo.moveSession(s.id, DSessionStatus.NO_SHOW)
                        }
                        Spacer(Modifier.width(6.dp))
                        BoardGhostButton(text = "Cancel") {
                            if (!s.walkIn) repo.moveSession(s.id, DSessionStatus.CANCELLED)
                        }
                        Spacer(Modifier.width(6.dp))
                        BoardGhostButton(text = "Reopen") { repo.moveSession(s.id, DSessionStatus.PENDING) }
                    }
                    if (s.walkIn) {
                        Text(
                            text = "No-show / Cancel disabled for walk-ins (rule above).",
                            fontSize = 11.sp,
                            color = ManifestMuted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (s.voided) {
                        Text(text = "Void reason: ${s.voidReason}", fontSize = 12.sp)
                        BoardLink(text = "Unvoid (voided in error)", onClick = { repo.unvoidSession(s.id) })
                    } else {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Void reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        BoardButton(
                            text = "Void session",
                            danger = true,
                            onClick = {
                                if (reason.isNotBlank()) {
                                    repo.voidSession(s.id, reason)
                                    reason = ""
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    SectionHeader(index = "S-2", title = "Log a walk-in")
    ManifestCard {
        OutlinedTextField(
            value = newClient,
            onValueChange = { newClient = it },
            label = { Text("Client name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            OutlinedTextField(
                value = newType,
                onValueChange = { newType = it },
                label = { Text("Session type") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = newPrice,
                onValueChange = { newPrice = it },
                label = { Text("Price") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        BoardButton(
            text = "Add walk-in session",
            onClick = { repo.addWalkIn(newClient, newType, newPrice.toIntOrNull() ?: 0) },
        )
    }
}
