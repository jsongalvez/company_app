package com.companyb.companyapp.proto.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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

// #844 — whiteboard day screens: standup board, branch pins, relief duty, session cards.

@Composable
fun WhiteboardBoard(repo: WhiteboardFakeRepo, go: (WhiteboardScreen) -> Unit) {
    BoardPanel {
        MarkerTitle("Today's standup", BoardColors.MarkerBlue)
        Spacer(Modifier.height(6.dp))
        val here = repo.sessions.filter { it.branchId == repo.currentBranchId }
        val todo = here.count { it.status == BoardSessionStatus.PENDING && !it.voided }
        val done = here.count { it.status == BoardSessionStatus.COMPLETED }
        val missed = here.count {
            (it.status == BoardSessionStatus.NO_SHOW || it.status == BoardSessionStatus.CANCELLED) && !it.voided
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardChip("$todo to-do", BoardColors.MarkerOrange)
            BoardChip("$done done", BoardColors.MarkerGreen)
            BoardChip("$missed missed", BoardColors.MarkerRed)
        }
        Spacer(Modifier.height(8.dp))
        BoardNote("${repo.currentBranch.name} · ${repo.operationalDate} · boundary 04:00 Asia/Manila")
    }

    BoardPanel(tape = BoardColors.MagnetTeal) {
        BoardSectionRow("Clock + relief") {
            BoardChip(if (repo.clockedIn) "ON DUTY" else "OFF DUTY", BoardColors.MarkerGreen)
        }
        if (!repo.clockedIn) {
            BoardNote("Pin yourself to a branch to start. Non-home branches start view-only.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardCta("Clock in here") {
                    repo.clockIn(repo.currentBranchId, relief = repo.currentBranchId != repo.currentUser?.homeBranchId)
                }
            }
        } else {
            Text(
                "On duty at ${repo.branchName(repo.clockBranchId)}.",
                color = BoardColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (repo.clockBranchId != repo.currentUser?.homeBranchId && !repo.reliefEdit) {
                Spacer(Modifier.height(6.dp))
                BoardNote("Relief duty: view-only until a branch member grants edit access.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardCta("Ask branch (request)") {
                        repo.requests.add(
                            0,
                            BoardRequest(
                                "r-${repo.requests.size + 100}",
                                repo.clockBranchId,
                                repo.operationalDate,
                                repo.currentUser?.name ?: "Guest",
                            ),
                        )
                        repo.audit("RELIEF_REQUEST", repo.branchName(repo.clockBranchId))
                    }
                    BoardGhost("Simulate grant") {
                        repo.reliefEdit = true
                        repo.audit("RELIEF_GRANT", repo.branchName(repo.clockBranchId))
                    }
                }
            }
            if (repo.reliefEdit) {
                Spacer(Modifier.height(6.dp))
                BoardChip("relief edit granted", BoardColors.MarkerGreen)
            }
            Spacer(Modifier.height(8.dp))
            BoardGhost("Clock out") { repo.clockOut() }
        }
    }

    BoardPanel(tape = BoardColors.MagnetPink) {
        BoardSectionRow("Branches") {
            BoardLink("open sessions") { go(WhiteboardScreen.SESSIONS) }
        }
        repo.branches.forEach { branch ->
            val active = branch.id == repo.currentBranchId
            MagnetCard(
                magnet = if (active) BoardColors.MarkerBlue else BoardColors.CardLine,
                onClick = { repo.currentBranchId = branch.id },
            ) {
                Text(
                    "${if (active) "★ " else ""}${branch.name}",
                    color = BoardColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("${branch.kind} · ${branch.standupLine}", color = BoardColors.InkSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    BoardPanel {
        BoardSectionRow("Relief inbox") {
            BoardChip("${repo.invites.count { it.accepted == null }} invites", BoardColors.MarkerPurple)
        }
        if (repo.invites.isEmpty() && repo.requests.isEmpty()) {
            BoardNote("No invites or requests pinned. Quiet board.")
        }
        repo.invites.forEach { invite ->
            MagnetCard(magnet = BoardColors.MagnetYellow) {
                Text(
                    "Invite: ${repo.branchName(invite.branchId)} · ${invite.day}",
                    color = BoardColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                BoardNote("from ${invite.fromUser} · status ${invite.accepted ?: "waiting"}")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardCta("Accept") {
                        invite.accepted = true
                        repo.audit("INVITE_ACCEPT", repo.branchName(invite.branchId))
                    }
                    BoardGhost("Decline") {
                        invite.accepted = false
                        repo.audit("INVITE_DECLINE", repo.branchName(invite.branchId))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        repo.requests.forEach { request ->
            MagnetCard(magnet = BoardColors.MagnetSky) {
                Text(
                    "Request: ${request.requester} → ${repo.branchName(request.branchId)} · ${request.day}",
                    color = BoardColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                BoardNote("decision: ${request.decided ?: "waiting — one live request per requester per branch per date"}")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardCta("Grant") {
                        request.decided = "granted"
                        repo.audit("RELIEF_GRANT", "${request.requester} @ ${repo.branchName(request.branchId)}")
                    }
                    BoardGhost("Deny") {
                        request.decided = "denied"
                        repo.audit("RELIEF_DENY", "${request.requester} @ ${repo.branchName(request.branchId)}")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun WhiteboardSessions(repo: WhiteboardFakeRepo) {
    var filter by remember { mutableStateOf(0) }
    val tabs = listOf("To-do", "Done", "Missed", "All")
    BoardPanel {
        MarkerTitle("Session cards", BoardColors.MarkerOrange)
        Spacer(Modifier.height(6.dp))
        BoardNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — the rule is printed on the card.")
        Spacer(Modifier.height(8.dp))
        BoardTabPills(tabs, filter) { filter = it }
    }

    var showCreate by remember { mutableStateOf(false) }
    BoardPanel(tape = BoardColors.MagnetYellow) {
        BoardSectionRow("Pin a new card") {
            BoardLink(if (showCreate) "fold up" else "new card") { showCreate = !showCreate }
        }
        if (showCreate) {
            WhiteboardSessionCreate(repo = repo, onDone = { showCreate = false })
        } else {
            BoardNote("Booked or walk-in — walk-ins skip the missed pile by rule.")
        }
    }

    val here = repo.sessions.filter { it.branchId == repo.currentBranchId }
    val shown = when (filter) {
        0 -> here.filter { it.status == BoardSessionStatus.PENDING && !it.voided }
        1 -> here.filter { it.status == BoardSessionStatus.COMPLETED }
        2 -> here.filter {
            (it.status == BoardSessionStatus.NO_SHOW || it.status == BoardSessionStatus.CANCELLED) && !it.voided
        }
        else -> here
    }
    if (shown.isEmpty()) {
        BoardPanel {
            BoardNote("No cards in this pile for ${repo.currentBranch.name}. Enjoy the clean board.")
        }
    }
    shown.forEach { session ->
        WhiteboardSessionCard(repo = repo, session = session)
    }
}

@Composable
private fun WhiteboardSessionCard(repo: WhiteboardFakeRepo, session: BoardSession) {
    var showVoid by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    MagnetCard(magnet = session.status.accent()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.slot} · ${session.clientName}",
                    color = BoardColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${session.type} · ₱${session.price} · ${session.practitioner}${if (session.walkIn) " · walk-in" else ""}",
                    color = BoardColors.InkSoft,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            BoardChip(
                if (session.voided) "VOIDED" else session.status.label(),
                if (session.voided) BoardColors.MarkerRed else session.status.accent(),
            )
        }
        if (session.walkIn) {
            Spacer(Modifier.height(4.dp))
            BoardNote("Walk-in: NO_SHOW / CANCELLED pens stay capped by rule.")
        }
        if (session.voided) {
            Spacer(Modifier.height(4.dp))
            BoardNote("Void reason: ${session.voidReason ?: "—"}")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!session.voided) {
                if (session.status == BoardSessionStatus.PENDING) {
                    BoardCta("Complete") { repo.setStatus(session, BoardSessionStatus.COMPLETED) }
                    BoardGhost("No-show") {
                        if (!session.walkIn) {
                            repo.setStatus(session, BoardSessionStatus.NO_SHOW)
                        }
                    }
                    BoardGhost("Cancel") {
                        if (!session.walkIn) {
                            repo.setStatus(session, BoardSessionStatus.CANCELLED)
                        }
                    }
                } else {
                    BoardGhost("Reopen") { repo.setStatus(session, BoardSessionStatus.PENDING) }
                }
                BoardLink(if (showVoid) "keep" else "void") { showVoid = !showVoid }
            } else {
                BoardCta("Unvoid") { repo.unvoidSession(session) }
            }
        }
        if (showVoid && !session.voided) {
            Spacer(Modifier.height(8.dp))
            TextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Void reason") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            BoardCta("Void this card") {
                repo.voidSession(session, reason)
                showVoid = false
                reason = ""
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun WhiteboardSessionCreate(repo: WhiteboardFakeRepo, onDone: () -> Unit) {
    var name by remember { mutableStateOf("New Client") }
    var slot by remember { mutableStateOf("15:00") }
    var type by remember { mutableStateOf("Follow-up") }
    var price by remember { mutableStateOf("1200") }
    var walkIn by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = slot, onValueChange = { slot = it }, label = { Text("Slot") }, modifier = Modifier.weight(1f), singleLine = true)
            TextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(value = price, onValueChange = { price = it }, label = { Text("Price ₱") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(10.dp))
            Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
            Text("Walk-in", color = BoardColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        BoardCta("Pin it") {
            repo.addSession(name, slot, type, price.toIntOrNull() ?: 0, walkIn, repo.currentBranchId)
            onDone()
        }
    }
}
