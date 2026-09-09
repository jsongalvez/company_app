package com.companyb.companyapp.proto.opsfeed

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

// #790 — ops-feed back-office screens: remittance desk, team roster, mailbox, audit, profile.

@Composable
fun OfFinance(repo: OpsFeedFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    OfCard {
        OfSectionTitle("Remittance desk")
        Text(
            "SESSION and PRODUCT drafts submit into immutable snapshots. Undo needs a reason and " +
                "only lands inside the 48h window after submit.",
            color = OfColors.Ink, fontSize = 13.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    repo.remittances.forEach { remit ->
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(remit.kind.name, color = OfColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                OfTag(remit.state.name, if (remit.state == OfRemitState.SUBMITTED) OfColors.Session else OfColors.Warn)
            }
            Spacer(Modifier.height(4.dp))
            Text("Draft total ₱${remit.draftTotal}", color = OfColors.Ink, fontSize = 13.sp, fontFamily = OfMono)
            if (remit.snapshotId != null) {
                Text(
                    "Snapshot ${remit.snapshotId} sealed at ₱${remit.snapshotTotal}",
                    color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono,
                )
            }
            if (remit.undoReason != null) {
                Text("Undone: ${remit.undoReason}", color = OfColors.Bad, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (remit.state == OfRemitState.DRAFT) {
                OfRowButtons(
                    {
                        OfPrimary("Submit + seal snapshot") {
                            val snap = "OF-${1000 + repo.remittances.indexOf(remit) * 7 + repo.feed.size}"
                            repo.remittances[repo.remittances.indexOf(remit)] = remit.copy(
                                state = OfRemitState.SUBMITTED,
                                snapshotId = snap,
                                snapshotTotal = remit.draftTotal,
                            )
                            repo.audit("SUBMIT", "${remit.kind.name} remittance $snap", "sealed at ₱${remit.draftTotal}")
                        }
                    },
                    {
                        OfGhost("+₱500 to draft") {
                            repo.remittances[repo.remittances.indexOf(remit)] =
                                remit.copy(draftTotal = remit.draftTotal + 500)
                            repo.post(OfDomain.REMIT, "${remit.kind.name} draft adjusted",
                                "Draft now ₱${remit.draftTotal + 500}.")
                        }
                    },
                )
            } else {
                TextField(
                    value = undoReason,
                    onValueChange = { undoReason = it },
                    placeholder = { Text("Undo reason (required, 48h window)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = OfColors.Paper,
                        unfocusedContainerColor = OfColors.Paper,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                OfGhost("Undo submission") {
                    if (undoReason.isNotBlank()) {
                        repo.remittances[repo.remittances.indexOf(remit)] = remit.copy(
                            state = OfRemitState.DRAFT,
                            snapshotId = null,
                            snapshotTotal = null,
                            undoReason = undoReason.trim(),
                        )
                        repo.audit("UNDO", "${remit.kind.name} remittance", undoReason.trim())
                        undoReason = ""
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    OfCard {
        OfSectionTitle("Commission split")
        Text(
            "Session revenue pools per branch day, then splits equally across staff clocked in " +
                "at sold_at. Walk the feed: every completion above lands here at close.",
            color = OfColors.Ink, fontSize = 13.sp,
        )
    }
}

@Composable
fun OfTeam(repo: OpsFeedFakeRepo) {
    OfCard {
        OfSectionTitle("Team — slots and roles")
        Text("Capability check: only MANAGER / COORDINATOR / ACCOUNTANT act on money and grants.",
            color = OfColors.Muted, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
    repo.users.sortedBy { it.slot }.forEach { user ->
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("S${user.slot}", color = OfColors.Muted, fontSize = 13.sp, fontFamily = OfMono,
                    fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(0.04f))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.name, color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("home ${user.homeBranchId}", color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono)
                }
                OfTag(user.role.name, if (user.role == OfRole.ONBOARDING) OfColors.Warn else OfColors.Relief)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OfMailbox(repo: OpsFeedFakeRepo) {
    var showUnreadOnly by remember { mutableStateOf(false) }
    OfCard {
        OfSectionTitle("Mailbox")
        OfRowButtons(
            { OfChip(if (showUnreadOnly) "UNREAD ONLY" else "ALL MAIL", true) { showUnreadOnly = !showUnreadOnly } },
            {
                OfLink("Mark all read") {
                    repo.notifications.forEachIndexed { index, note ->
                        repo.notifications[index] = note.copy(read = true)
                    }
                    repo.post(OfDomain.NOTE, "Mailbox cleared", "All notifications marked read.")
                }
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    val visible = repo.notifications.filter { !showUnreadOnly || !it.read }
    if (visible.isEmpty()) {
        OfCard { Text("Inbox zero. The branch is quiet.", color = OfColors.Muted, fontSize = 13.sp) }
    }
    visible.forEach { note ->
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                OfTag(if (note.read) "READ" else "UNREAD", if (note.read) OfColors.Muted else OfColors.Note)
            }
            Spacer(Modifier.height(4.dp))
            Text(note.body, color = OfColors.Ink, fontSize = 13.sp)
            if (!note.read) {
                Spacer(Modifier.height(6.dp))
                OfLink("Mark read") {
                    repo.notifications[repo.notifications.indexOf(note)] = note.copy(read = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OfAuditScreen(repo: OpsFeedFakeRepo) {
    OfCard {
        OfSectionTitle("Audit log")
        Text("Newest first. Voids, grants, submits, and undos all land here with a reason.",
            color = OfColors.Muted, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
    repo.audits.forEach { entry ->
        OfCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.whenLabel, color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OfTag(entry.action, OfColors.Audit)
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.target, color = OfColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("by ${entry.who}", color = OfColors.Muted, fontSize = 12.sp, fontFamily = OfMono)
            if (entry.reason != null) {
                Text("Reason: ${entry.reason}", color = OfColors.Ink, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OfProfile(
    repo: OpsFeedFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    OfCard {
        OfSectionTitle("Profile")
        Text(user?.name ?: "Signed out", color = OfColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Role ${user?.role?.name ?: "—"} · home ${user?.homeBranchId ?: "—"} · " +
                if (repo.clockedIn) "on shift" else "off shift",
            color = OfColors.Muted, fontSize = 13.sp, fontFamily = OfMono,
        )
        Spacer(Modifier.height(10.dp))
        OfRowButtons(
            {
                if (repo.clockedIn) {
                    OfGhost("Clock out") {
                        repo.clockedIn = false
                        repo.post(OfDomain.SYSTEM, "${repo.actorName()} clocked out", "Shift ended from profile.")
                    }
                } else {
                    OfGhost("Clock in") {
                        repo.clockedIn = true
                        repo.post(OfDomain.SYSTEM, "${repo.actorName()} clocked in", "Shift started from profile.")
                    }
                }
            },
            {
                OfPrimary("Log out") {
                    repo.post(OfDomain.SYSTEM, "${repo.actorName()} signed off", "Session ended.")
                    repo.currentUserId = null
                    repo.clockedIn = false
                    onLogout()
                }
            },
        )
    }
}
