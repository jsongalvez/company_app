package com.companyb.companyapp.proto.departuresboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #830 — departures-board people + logbook screens: passengers, crew, signals, logbook, pass.

@Composable
fun BoardPassengers(repo: BoardFakeRepo) {
    BoardPanel {
        BoardStationLabel("Passengers ● all platforms")
        BoardHeading("Passenger manifest.")
        BoardSub("Clients are global across platforms. At most one PENDING departure per passenger.")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardSub(if (repo.clientsPrivate) "Manifest shows private references." else "Manifest shows full names.")
            Spacer(Modifier.weight(1f))
            BoardLinkButton(
                text = if (repo.clientsPrivate) "Reveal names" else "Mask names",
                onClick = { repo.clientsPrivate = !repo.clientsPrivate },
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.clients.forEach { client ->
                val pending = repo.pendingCountFor(client.id)
                val label = if (repo.clientsPrivate && !client.anonymized) "${client.name.first()}••••" else repo.displayName(client)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(BoardColors.YardDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = BoardColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${client.gender} ● ${client.age}y ● $pending PENDING ${if (client.anonymized) "● ANONYMIZED (gender + age kept)" else ""}",
                            color = BoardColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (!client.anonymized) {
                        BoardLinkButton(text = "Anonymize", onClick = { repo.anonymize(client.id) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardNoteCard("Anonymized view keeps gender + age for reporting while nulling PII — the name above reads as a private reference only.")
    }
}

@Composable
fun BoardCrew(repo: BoardFakeRepo) {
    BoardPanel {
        BoardStationLabel("Crew ● duty roster")
        BoardHeading("Who works this station.")
        BoardSub("Branch slot orders the roster; relief riders sort after home slots. ONBOARDING row stays locked.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == BoardRole.ONBOARDING && !repo.onboardGranted
                val you = user.id == repo.currentUserId
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(BoardColors.YardDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardFlapCell("#${user.slot}")
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            user.name + if (you) " ● YOU" else "",
                            color = BoardColors.Cream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (locked) "ONBOARDING — no capabilities" else "${user.role.name} ● home ${user.homeBranchId}",
                            color = if (locked) BoardColors.Red else BoardColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    BoardFlagChip(
                        text = user.role.name.take(5),
                        color = when (user.role) {
                            BoardRole.MANAGER -> BoardColors.Amber
                            BoardRole.COORDINATOR -> BoardColors.Sky
                            BoardRole.ACCOUNTANT -> BoardColors.Violet
                            BoardRole.PRACTITIONER -> BoardColors.Green
                            BoardRole.ONBOARDING -> BoardColors.Red
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardNoteCard("Capability check is role-bundle based at runtime; relief edit grants are day-scoped and expire at the 04:00 Manila boundary.")
    }
}

@Composable
fun BoardSignals(repo: BoardFakeRepo) {
    BoardPanel {
        BoardStationLabel("Signals ● announcer")
        BoardHeading("Announcements.")
        BoardSub("Every relief event names its platform and day; taps would open that branch day.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardGhostButton(text = "Hush all", onClick = { repo.markAllRead() })
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.notifications.forEach { note ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (note.read) BoardColors.YardDeep else BoardColors.Flap)
                        .clickable { repo.markRead(note.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardFlagChip(text = if (note.read) "READ" else "NEW", color = if (note.read) BoardColors.Faint else BoardColors.Amber)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(note.title, color = BoardColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("${note.body} ● ${note.at}", color = BoardColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun BoardLedger(repo: BoardFakeRepo) {
    BoardPanel {
        BoardStationLabel("Logbook ● station record")
        BoardHeading("Every movement logged.")
        BoardSub("Who did what to which departure, with reasons for voids and undos. Read rows are kept forever.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.audits.forEach { audit ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(BoardColors.YardDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardFlapCell(audit.at)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("${audit.who} ● ${audit.action}", color = BoardColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${audit.target}${audit.reason?.let { " — reason: $it" } ?: ""}",
                            color = BoardColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoardMe(
    repo: BoardFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    BoardPanel {
        BoardStationLabel("My pass")
        BoardHeading(user?.name ?: "No pass holder")
        BoardSub("Role ${repo.effectiveRole.name} ● home ${user?.homeBranchId ?: "—"} ● slot ${user?.slot ?: "—"} ● ${if (repo.clockedIn) "clocked in" else "clocked out"}.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) {
                BoardGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            } else {
                BoardPrimaryButton(text = "Clock in", onClick = { repo.clockIn() })
            }
            BoardGhostButton(
                text = "Sign out",
                onClick = { repo.logout(); onLogout() },
            )
        }
        Spacer(Modifier.height(8.dp))
        BoardNoteCard("Signing out returns to the gates; the pass stays valid for the next boarding. Deactivate would block sign-in at once — not modelled with fake data beyond this note.")
    }
}

@Composable
fun BoardTicketOffice(repo: BoardFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var undoError by remember { mutableStateOf<String?>(null) }
    BoardPanel {
        BoardStationLabel("Ticket office ● fares + freight")
        BoardHeading("Remittance windows.")
        BoardSub("Two independent flows: SESSION fares and PRODUCT freight. Submitting freezes an immutable snapshot; Undo reopens within 48h with a reason.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.remittances.forEach { remit ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)).background(BoardColors.YardDeep)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(remit.kind.name, color = BoardColors.Cream, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        BoardFlagChip(
                            text = remit.state.name,
                            color = if (remit.state == BoardRemitState.SUBMITTED) BoardColors.Green else BoardColors.Amber,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoardFlapCell("₱${remit.draftTotal}")
                        Spacer(Modifier.weight(1f))
                        if (remit.state == BoardRemitState.DRAFT) {
                            BoardLinkButton(text = "−500", onClick = { repo.bumpDraft(remit.kind, -500) })
                            BoardLinkButton(text = "+500", onClick = { repo.bumpDraft(remit.kind, 500) })
                            BoardGhostButton(text = "Submit", onClick = { repo.submit(remit.kind) })
                        }
                    }
                    if (remit.state == BoardRemitState.SUBMITTED) {
                        Text(
                            "Snapshot ${remit.snapshotId} sealed ₱${remit.snapshotTotal} — later timetable edits never rewrite it.",
                            color = BoardColors.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                TextField(
                                    value = undoReason,
                                    onValueChange = { undoReason = it },
                                    label = { Text("Undo reason") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(),
                                )
                            }
                            BoardGhostButton(
                                text = "Undo 48h",
                                onClick = { undoError = repo.undo(remit.kind, undoReason); if (undoError == null) undoReason = "" },
                            )
                        }
                        if (undoError != null) {
                            Text(undoError ?: "", color = BoardColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        BoardSub("Draft — unconstrained, may overlap other drafts at this platform.")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardNoteCard("Commission split: product commissions pool per branch day and split evenly over riders clocked in at sold_at; relief paid from this drawer. Manual inclusions/exclusions can override — fake total ₱1,800 across 3 riders in this prototype.")
    }
}
