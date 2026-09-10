package com.companyb.companyapp.proto.passcounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

// #832 — pass-counter back of house: cash-up drawer, crew line, mailbox, waste
// ledger, expo profile.

@Composable
fun PcCashUp(repo: PassCounterFakeRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTicketCard {
            PcSectionTitle("Cash-up drawer · ${repo.currentBranch.name}")
            Text(
                "Count the drawer like a cash-up: SESSION course money and PRODUCT " +
                    "retail money stay in separate tills. Submitting seals a snapshot; " +
                    "undo reopens within 48h with a reason. Commission splits 60/40 " +
                    "house-to-line on SESSION, 80/20 on PRODUCT.",
                fontSize = 12.sp, color = PcColors.Muted,
            )
        }
        repo.remits.filter { it.branchId == repo.currentBranchId }.forEach { remit ->
            PcRemitCard(repo = repo, remit = remit)
        }
    }
}

@Composable
private fun PcRemitCard(
    repo: PassCounterFakeRepo,
    remit: PcRemit,
) {
    var undoReason by remember(remit.id, remit.status) { mutableStateOf("") }
    PcTicketCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${remit.kind} till", fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = PcSlip)
            Spacer(Modifier.width(8.dp))
            PcTag(remit.status.name, if (remit.status == PcRemitStatus.DRAFT) PcColors.Brass else PcColors.Serve)
            Spacer(Modifier.weight(1f))
            Text("₱${remit.amount}", fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = PcSlip)
        }
        Spacer(Modifier.height(6.dp))
        if (remit.snapshot.isNotEmpty()) {
            Text("Snapshot: ${remit.snapshot}", fontSize = 12.sp, fontFamily = PcSlip, color = PcColors.Serve)
        }
        if (remit.undoReason.isNotEmpty()) {
            Text("Reopened: ${remit.undoReason}", fontSize = 12.sp, color = PcColors.Fire)
        }
        Spacer(Modifier.height(8.dp))
        if (remit.status == PcRemitStatus.DRAFT) {
            PcFireButton("Seal snapshot (submit)", onClick = { repo.submitRemit(remit) })
        } else if (!remit.undoUsed) {
            TextField(
                value = undoReason, onValueChange = { undoReason = it },
                label = { Text("Undo reason (within 48h)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            PcSteelButton(
                "Undo (48h)", enabled = undoReason.isNotBlank(),
                onClick = { repo.undoRemit(remit, undoReason) },
            )
        } else {
            Text("Undo spent — snapshot stands.", fontSize = 12.sp, color = PcColors.Muted)
        }
    }
}

@Composable
fun PcCrew(repo: PassCounterFakeRepo) {
    var inviteNote by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTicketCard {
            PcSectionTitle("Crew line · roles")
            Text("Expo reads the whole line at a glance.", fontSize = 12.sp, color = PcColors.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.users.forEach { user ->
                val locked = user.onboarding && !repo.stageGranted
                PcTicketCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                user.role, fontSize = 12.sp, fontFamily = PcSlip,
                                color = if (locked) PcColors.Waste else PcColors.Muted,
                            )
                        }
                        PcTag(if (locked) "LOCKED" else user.role, if (locked) PcColors.Waste else PcColors.Serve)
                    }
                    if (locked) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "ONBOARDING: empty capability bundle — no firing, no bell, no cash-up.",
                            fontSize = 12.sp, color = PcColors.Waste,
                        )
                        Spacer(Modifier.height(8.dp))
                        PcFireButton(
                            "Grant Practitioner",
                            onClick = {
                                repo.stageGranted = true
                                repo.audit("GRANT", "${user.name} -> Practitioner", "stage complete")
                            },
                        )
                    }
                }
            }
        }
        PcTicketCard {
            PcSectionTitle("Relief cover board")
            repo.covers.forEach { cover ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${cover.asker} → ${cover.branchId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(cover.note, fontSize = 12.sp, color = PcColors.Muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    PcTag(cover.state.name, if (cover.state == PcCoverState.OPEN) PcColors.Brass else PcColors.Serve)
                }
                Spacer(Modifier.height(6.dp))
                if (cover.state == PcCoverState.OPEN) {
                    PcRowButtons(
                        {
                            PcFireButton(
                                "Grant",
                                onClick = {
                                    val i = repo.covers.indexOfFirst { it.id == cover.id }
                                    if (i >= 0) repo.covers[i] = cover.copy(state = PcCoverState.GRANTED)
                                    repo.audit("GRANT", "Cover ${cover.asker} / ${cover.branchId}", null)
                                },
                            )
                        },
                        {
                            PcGhost("Fold") {
                                val i = repo.covers.indexOfFirst { it.id == cover.id }
                                if (i >= 0) repo.covers[i] = cover.copy(state = PcCoverState.FOLDED)
                                repo.audit("FOLD", "Cover ${cover.asker} / ${cover.branchId}", "no line cover")
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = inviteNote, onValueChange = { inviteNote = it },
                label = { Text("Invite note (who covers which station)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            PcRowButtons(
                {
                    PcFireButton(
                        "Broadcast request",
                        enabled = inviteNote.isNotBlank(),
                        onClick = {
                            repo.covers.add(
                                PcCover(
                                    "v-live-${repo.covers.size}", repo.currentBranchId,
                                    repo.actorName(), inviteNote,
                                ),
                            )
                            repo.audit("REQUEST", "Cover requested at ${repo.currentBranch.name}", inviteNote)
                            inviteNote = ""
                        },
                    )
                },
                {
                    PcGhost("Send invite") {
                        repo.invites.add(
                            PcCover(
                                "i-live-${repo.invites.size}", repo.currentBranchId,
                                repo.actorName(), inviteNote.ifBlank { "Cover the rush" },
                            ),
                        )
                        repo.audit("INVITE", "Cover invite sent", inviteNote.ifBlank { "Cover the rush" })
                        inviteNote = ""
                    }
                },
            )
        }
    }
}

@Composable
fun PcMailbox(repo: PassCounterFakeRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PcTicketCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mailbox", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        "${repo.notices.count { !it.read }} unread slips on the spike.",
                        fontSize = 12.sp, color = PcColors.Muted, fontFamily = PcSlip,
                    )
                }
                PcGhost("Mark all read") {
                    repo.notices.forEachIndexed { i, n -> repo.notices[i] = n.copy(read = true) }
                    repo.audit("MAIL", "Spike cleared", null)
                }
            }
        }
        repo.notices.forEach { notice ->
            PcTicketCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (notice.read) "" else "● ") + notice.title,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (notice.read) PcColors.Muted else PcColors.Ink,
                        )
                        Text(notice.body, fontSize = 12.sp, color = PcColors.Muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (notice.read) {
                        PcGhost("Unread") {
                            val i = repo.notices.indexOfFirst { it.id == notice.id }
                            if (i >= 0) repo.notices[i] = notice.copy(read = false)
                        }
                    } else {
                        PcFireButton("Read") {
                            val i = repo.notices.indexOfFirst { it.id == notice.id }
                            if (i >= 0) repo.notices[i] = notice.copy(read = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PcLedger(repo: PassCounterFakeRepo) {
    PcTicketCard {
        PcSectionTitle("Waste + pass ledger · audit log")
        Text(
            "Every fire, bell, void, grant, submit and undo lands here with its reason.",
            fontSize = 12.sp, color = PcColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.audits.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        entry.stamp, fontFamily = PcSlip, fontSize = 11.sp,
                        color = PcColors.Muted, modifier = Modifier.width(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${entry.action} — ${entry.target}",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${entry.actor}${if (entry.reason != null) " · ${entry.reason}" else ""}",
                            fontSize = 11.sp, color = PcColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PcProfile(
    repo: PassCounterFakeRepo,
    onLogout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PcTicketCard {
            PcSectionTitle("Expo card")
            Text(repo.currentUser?.name ?: "Signed out", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                "${repo.currentUser?.role ?: "-"} · ${repo.currentBranch.name} · ${repo.serviceDate}",
                fontSize = 12.sp, color = PcColors.Muted, fontFamily = PcSlip,
            )
            Spacer(Modifier.height(10.dp))
            PcRowButtons(
                {
                    if (repo.clockedIn) {
                        PcSteelButton(
                            "Clock out",
                            onClick = {
                                repo.clockedIn = false
                                repo.audit("CLOCK-OUT", repo.actorName(), repo.currentBranch.name)
                            },
                        )
                    } else {
                        PcFireButton(
                            "Clock in",
                            onClick = {
                                repo.clockedIn = true
                                repo.audit("CLOCK-IN", repo.actorName(), repo.currentBranch.name)
                            },
                        )
                    }
                },
                { PcGhost("Log out") {
                    repo.audit("LOGOUT", repo.actorName(), null)
                    repo.currentUserId = null
                    repo.clockedIn = false
                    onLogout()
                } },
            )
        }
        PcTicketCard {
            PcSectionTitle("Service day flip")
            Text(
                "OPEN fires, PAST reads only, REMITTED seals the drawer. " +
                    "Boundary at 04:00 Asia/Manila.",
                fontSize = 12.sp, color = PcColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PcDayStatus.entries.forEach { option ->
                    PcChip(
                        option.name, repo.dayStatus == option,
                        onClick = { repo.setDayStatus(repo.currentBranchId, option) },
                    )
                }
            }
        }
        PcTicketCard {
            PcSectionTitle("Demo")
            PcRowButtons(
                { PcGhost("Reset demo") { repo.resetDemo() } },
                { PcGhost("Blur guest book") { repo.anonymizeAll = !repo.anonymizeAll } },
            )
        }
    }
}
