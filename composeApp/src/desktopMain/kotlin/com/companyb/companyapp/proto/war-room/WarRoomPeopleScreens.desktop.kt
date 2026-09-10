package com.companyb.companyapp.proto.warroom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #836 — war-room people + ledger screens: dossier, roster, signals, ledger, tag.

@Composable
fun WarDossier(repo: WarFakeRepo) {
    WarPanel {
        WarKicker("dossier ● clients global")
        WarHeading("Who bleeds.")
        WarSub("Clients are global across sectors. At most one PENDING row per client — the count below enforces it.")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WarSub(if (repo.clientsPrivate) "Dossier shows masked references." else "Dossier shows full names.")
            Spacer(Modifier.weight(1f))
            WarLinkButton(if (repo.clientsPrivate) "Reveal" else "Mask") { repo.clientsPrivate = !repo.clientsPrivate }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.clients.forEach { client ->
                val pending = repo.pendingCountFor(client.id)
                val label = if (repo.clientsPrivate && !client.anonymized) "${client.name.first()}####" else repo.displayName(client)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = WarColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${client.gender} ● ${client.age}y ● $pending PENDING${if (client.anonymized) " ● ANONYMIZED (gender + age kept)" else ""}",
                            color = WarColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (pending > 1) {
                        WarChip("OVERBOOK", WarColors.Siren)
                    } else if (!client.anonymized) {
                        WarLinkButton("Anonymize") { repo.anonymize(client.id) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        WarNoteCard("Anonymized view keeps gender + age for reporting while nulling PII — the dossier reads as a masked reference only.", WarColors.Sky)
    }
}

@Composable
fun WarRoster(repo: WarFakeRepo) {
    WarPanel(accent = WarColors.Violet) {
        WarKicker("roster ● team duty")
        WarHeading("Who holds which gap.")
        WarSub("Roster in branch-slot order. ONBOARDING stays locked until the Brief clears the hold.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == WarRole.ONBOARDING && !repo.onboardGranted
                val you = repo.currentUserId == user.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${user.name}${if (you) " ● YOU" else ""}",
                            color = WarColors.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (locked) "ONBOARDING — locked, zero capabilities" else "${user.role.name} ● home ${user.homeBranchId} ● slot ${user.slot}",
                            color = if (locked) WarColors.Siren else WarColors.Faint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    WarChip(if (locked) "LOCKED" else user.role.name.take(4), if (locked) WarColors.Siren else WarColors.Violet, filled = false)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        WarSub("Capability note: ONBOARDING grants nothing. PRACTITIONER holds sessions, COORDINATOR holds the floor, MANAGER holds sectors, ACCOUNTANT holds the vault.")
    }
}

@Composable
fun WarSignals(repo: WarFakeRepo) {
    WarPanel {
        WarKicker("signals ● notifications")
        WarHeading("Incoming fire.")
        WarSub("Mailbox with read/unread. Hush one or hush them all.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            WarLinkButton("Hush all") { repo.markAllRead() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.notifications.forEach { note ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (note.read) WarColors.BunkerDeep else WarColors.SirenDim)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(note.title, color = WarColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text(note.body, color = WarColors.InkDim, fontSize = 13.sp)
                        Text(note.at, color = WarColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!note.read) {
                        Text(
                            "HUSH",
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(WarColors.Siren)
                                .clickable { repo.markRead(note.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = WarColors.BunkerDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                        )
                    } else {
                        Text("QUIET", color = WarColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun WarLedger(repo: WarFakeRepo) {
    WarPanel {
        WarKicker("ledger ● audit log")
        WarHeading("Every shot logged.")
        WarSub("Each mutation in this room prepends who / action / target / reason.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.audits.forEach { audit ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep).padding(12.dp),
                ) {
                    Text("${audit.who} ● ${audit.action}", color = WarColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(audit.target, color = WarColors.InkDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${audit.at}${audit.reason?.let { " ● reason: $it" } ?: ""}",
                        color = WarColors.Faint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun WarMe(repo: WarFakeRepo, onLogout: () -> Unit) {
    WarPanel {
        WarKicker("my tag ● profile")
        WarHeading(repo.currentUser?.name ?: "No tag issued")
        WarSub("Role ${repo.effectiveRole.name} ● home ${repo.currentUser?.homeBranchId ?: "-"} ● sector ${repo.currentBranch.name}.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WarChip(if (repo.clockedIn) "ON THE FLOOR" else "OFF THE FLOOR", if (repo.clockedIn) WarColors.Ok else WarColors.Faint)
            Spacer(Modifier.weight(1f))
            if (repo.clockedIn) {
                WarGhostButton("Clock out") { repo.clockOut() }
            } else {
                WarPrimaryButton("Clock in") { repo.clockIn() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WarGhostButton("Drop tag (logout)") {
                repo.logout()
                onLogout()
            }
        }
        Spacer(Modifier.height(8.dp))
        WarNoteCard("Dropping the tag returns to Access. Clock state and relief cover reset with it.", WarColors.Violet)
    }
    WarPanel(accent = WarColors.Siren) {
        WarKicker("my exceptions")
        WarSub("Your slice of the bleed: ${repo.voidQueue.size} voids open, ${repo.varianceQueue.size} variances flagged, ${repo.undoWindows.size} undo windows ticking.")
        Spacer(Modifier.height(6.dp))
        WarNoteCard("Branch Day ${repo.operationalDate} ● ${repo.dayStatus.name} ● 04:00 Asia/Manila boundary decides OPEN vs PAST.", WarColors.Sky)
    }
}
