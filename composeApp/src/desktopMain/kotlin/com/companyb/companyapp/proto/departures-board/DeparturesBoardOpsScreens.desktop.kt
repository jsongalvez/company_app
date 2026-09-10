package com.companyb.companyapp.proto.departuresboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #830 — departures-board ops screens: hall, gates, platforms, concourse, departures flip-board.

@Composable
fun BoardWelcome(
    repo: BoardFakeRepo,
    onNext: () -> Unit,
) {
    BoardPanel {
        BoardStationLabel("Ticket hall ● step 1 of 3")
        BoardHeading("Departures for every healing journey.")
        BoardSub("This station board lists today's Sessions the way a rail board lists trains: time, service, platform, flag. Pick a platform, board walk-ins, flag delays.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardFlapCell("QC CENTRAL")
            BoardFlapCell("LAGUNA 3")
            BoardFlapCell("TONDO MISSION")
        }
        Spacer(Modifier.height(10.dp))
        BoardNoteCard("ONBOARDING passes are held at the gate: R. Nuevo carries zero capabilities until a MANAGE_USERS grant clears the barrier. Nothing derives before that.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardPrimaryButton(text = "Enter the gates", onClick = onNext)
            BoardGhostButton(text = "Clear onboarding hold", onClick = { repo.grantOnboarding() })
        }
        if (repo.onboardGranted) {
            Spacer(Modifier.height(8.dp))
            BoardSub("Hold lifted: R. Nuevo now boards as PRACTITIONER in this prototype.")
        }
    }
    BoardPanel {
        BoardStationLabel("How to judge this variant")
        BoardSub("Flip-board times replace the Linear list. Delay flags are first-class. Platforms replace the branch picker. Judge whether a station metaphor makes PENDING vs DELAYED vs VOID legible at a glance.")
    }
}

@Composable
fun BoardSignin(
    repo: BoardFakeRepo,
    onNext: () -> Unit,
) {
    BoardPanel {
        BoardStationLabel("Gates ● step 2 of 3")
        BoardHeading("Show your pass.")
        BoardSub("Five fake passes. ONBOARDING stays behind the barrier until the hold is cleared in the Ticket Hall.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == BoardRole.ONBOARDING && !repo.onboardGranted
                val active = repo.currentUserId == user.id
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) BoardColors.Flap else BoardColors.YardDeep)
                        .border(1.dp, if (active) BoardColors.Amber else BoardColors.PanelLine, RoundedCornerShape(10.dp))
                        .then(if (!locked) Modifier.clickable { repo.login(user.id); onNext() } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardFlapCell(user.role.name.take(4))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(user.name, color = BoardColors.Cream, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (locked) "ONBOARDING — held at gate" else "${user.role.name} ● home ${user.homeBranchId}",
                            color = if (locked) BoardColors.Red else BoardColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (locked) "HELD" else "BOARD",
                        color = if (locked) BoardColors.Red else BoardColors.Green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
fun BoardPlatforms(
    repo: BoardFakeRepo,
    onNext: () -> Unit,
) {
    BoardPanel {
        BoardStationLabel("Platforms ● step 3 of 3")
        BoardHeading("Choose your platform.")
        BoardSub("Each Branch is a platform. Tour and Mission lines run shorter timetables.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.branches.forEach { branch ->
                val active = repo.currentBranchId == branch.id
                val count = repo.sessions.count { it.branchId == branch.id && it.status == BoardSessionStatus.PENDING }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) BoardColors.Flap else BoardColors.YardDeep)
                        .border(1.dp, if (active) BoardColors.Amber else BoardColors.PanelLine, RoundedCornerShape(10.dp))
                        .clickable { repo.currentBranchId = branch.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(BoardColors.Amber)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(branch.platformNo, color = BoardColors.AmberInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(branch.name.uppercase(), color = BoardColors.Cream, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("${branch.kind.name} — ${branch.lineNote}", color = BoardColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    BoardFlapCell("$count")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoardPrimaryButton(text = "Enter the concourse", onClick = onNext)
    }
}

@Composable
fun BoardConcourse(
    repo: BoardFakeRepo,
    go: (BoardScreen) -> Unit,
) {
    val branch = repo.currentBranch
    val board = repo.sessions.filter { it.branchId == repo.currentBranchId }
    val pending = board.count { it.status == BoardSessionStatus.PENDING }
    val departed = board.count { it.status == BoardSessionStatus.COMPLETED }
    BoardPanel {
        BoardStationLabel("Concourse ● ${branch.name}")
        BoardHeading("Good morning, ${repo.currentUser?.name ?: "traveller"}.")
        BoardSub("Clocked ${if (repo.clockedIn) "in at home platform ${branch.platformNo}" else "out"} ● role ${repo.effectiveRole.name} ● $pending boarding / $departed departed today.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) {
                BoardGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            } else {
                BoardPrimaryButton(text = "Clock in", onClick = { repo.clockIn() })
            }
            BoardGhostButton(text = "Open departures", onClick = { go(BoardScreen.DEPARTURES) })
            BoardGhostButton(text = "Ticket office", onClick = { go(BoardScreen.TICKETS) })
        }
    }
    BoardPanel {
        BoardStationLabel("Relief lines")
        if (repo.reliefBranchId == null) {
            BoardSub("Ride a relief duty at another platform: view-only until the branch waves you through.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repo.branches.filter { it.id != repo.currentBranchId }.forEach { other ->
                    BoardGhostButton(text = "Relief @ ${other.name}", onClick = { repo.startRelief(other.id) })
                }
            }
        } else {
            val reliefName = repo.branches.firstOrNull { it.id == repo.reliefBranchId }?.name ?: "?"
            BoardSub("Relief duty at $reliefName — ${if (repo.reliefEditGranted) "edit access granted" else "view-only until granted"}. Paid from that platform's drawer; ends 04:00 Manila.")
            Spacer(Modifier.height(8.dp))
            BoardRowActions(
                "Request edit grant" to { repo.grantReliefEdit() },
                "End relief" to { repo.endRelief() },
            )
        }
        Spacer(Modifier.height(10.dp))
        BoardStationLabel("Invites for you")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.invites.forEach { invite ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(BoardColors.YardDeep)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(invite.branchName, color = BoardColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${invite.person} ● ${invite.day} ● ${invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "AWAITING"}", color = BoardColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (invite.accepted == null) {
                        BoardLinkButton(text = "Yes", onClick = { repo.decideInvite(invite.id, true) })
                        BoardLinkButton(text = "No", onClick = { repo.decideInvite(invite.id, false) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardStationLabel("Broadcast requests at this platform")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.requests.forEach { request ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(BoardColors.YardDeep)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(request.requester, color = BoardColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${request.day} ● ${request.state} — one live request per rider per platform per day", color = BoardColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (request.state == "LIVE") {
                        BoardLinkButton(text = "Allow", onClick = { repo.decideRequest(request.id, true) })
                        BoardLinkButton(text = "Deny", onClick = { repo.decideRequest(request.id, false) })
                    }
                }
            }
        }
    }
    BoardPanel {
        BoardStationLabel("Next three departures")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            board.filter { it.status == BoardSessionStatus.PENDING }.take(3).forEach { session ->
                BoardDepartureRow(session = session, onToggleDelay = { repo.toggleDelay(session.id) }, compact = true)
            }
            if (board.none { it.status == BoardSessionStatus.PENDING }) {
                BoardSub("No further departures from this platform today.")
            }
        }
    }
}

@Composable
fun BoardDepartureRow(
    session: BoardSession,
    onToggleDelay: () -> Unit,
    compact: Boolean = false,
) {
    val statusColor = if (session.voided) Color(0xFF6B6350) else session.status.flag()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BoardColors.YardDeep)
            .border(1.dp, BoardColors.PanelLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardFlapCell(session.departsAt)
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.id} — ${session.clientName}".uppercase(),
                    color = BoardColors.Cream,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${session.service} ● ${session.practitioner}${if (session.walkIn) " ● WALK-IN" else ""}${if (session.voided) " ● VOID" else ""}",
                    color = BoardColors.Faint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            BoardFlagChip(text = if (session.voided) "VOID" else session.status.label(), color = statusColor)
        }
        if (session.delayed && !session.voided) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardFlagChip(text = "DELAYED", color = BoardColors.Amber)
                Text("Running behind — see the announcer.", color = BoardColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                BoardLinkButton(text = "Clear", onClick = onToggleDelay)
            }
        } else if (!session.voided) {
            Spacer(Modifier.height(4.dp))
            Row {
                Spacer(Modifier.weight(1f))
                BoardLinkButton(text = "Flag delay", onClick = onToggleDelay)
            }
        }
    }
}

@Composable
fun BoardDepartures(repo: BoardFakeRepo) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("PT-Back") }
    var notice by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var voidError by remember { mutableStateOf<String?>(null) }
    val list = repo.sessions.filter { it.branchId == repo.currentBranchId }
        .let { rows -> repo.sessionFilter?.let { f -> rows.filter { it.status == f } } ?: rows }

    BoardPanel {
        BoardStationLabel("Departures ● platform ${repo.currentBranch.platformNo}")
        BoardHeading("Timetable.")
        BoardSub("Sessions are departures. Walk-in departures cannot take NO_SHOW or CANCELLED — rebook or void instead.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoardFlagChip(text = "ALL", color = BoardColors.Cream, selected = repo.sessionFilter == null, onClick = { repo.sessionFilter = null })
            BoardSessionStatus.entries.forEach { option ->
                BoardFlagChip(
                    text = option.label(),
                    color = option.flag(),
                    selected = repo.sessionFilter == option,
                    onClick = { repo.sessionFilter = if (repo.sessionFilter == option) null else option },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { session ->
                BoardDepartureRow(session = session, onToggleDelay = { repo.toggleDelay(session.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardSessionStatus.entries.forEach { next ->
                        BoardLinkButton(text = next.label(), onClick = { notice = repo.setStatus(session.id, next) })
                    }
                    BoardLinkButton(text = "Manage", onClick = { managing = if (managing == session.id) null else session.id; voidError = null })
                }
                if (managing == session.id) {
                    BoardNoteCard("Void keeps the record on the board with a reason; unvoid restores it. Price ₱${session.price} ● voided=${session.voided} ● reason=${session.voidReason ?: "—"}.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            TextField(
                                value = voidReason,
                                onValueChange = { voidReason = it },
                                label = { Text("Void reason") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(),
                            )
                        }
                        if (session.voided) {
                            BoardGhostButton(text = "Unvoid", onClick = { repo.unvoidSession(session.id) })
                        } else {
                            BoardGhostButton(
                                text = "Void",
                                onClick = { voidError = repo.voidSession(session.id, voidReason); if (voidError == null) voidReason = "" },
                            )
                        }
                    }
                    if (voidError != null) {
                        Text(voidError ?: "", color = BoardColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (list.isEmpty()) BoardSub("No departures under this flag on this platform.")
        }
        if (notice != null) {
            Spacer(Modifier.height(6.dp))
            Text(notice ?: "", color = BoardColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            BoardLinkButton(text = "Dismiss", onClick = { notice = null })
        }
    }
    BoardPanel {
        BoardStationLabel("New walk-in ● unlisted train")
        BoardSub("Boards a PENDING walk-in departure with a queue ticket and creates its passenger record.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Guest label") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors())
            }
            Box(Modifier.weight(1f)) {
                TextField(value = service, onValueChange = { service = it }, label = { Text("Service") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors())
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardPrimaryButton(
            text = "Board walk-in A-${(repo.ticketCounter + 1).toString().padStart(3, '0')}",
            onClick = { repo.addWalkIn(name, service); name = "" },
        )
    }
}
