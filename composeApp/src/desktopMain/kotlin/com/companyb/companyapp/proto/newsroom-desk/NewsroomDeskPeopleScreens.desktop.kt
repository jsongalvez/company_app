package com.companyb.companyapp.proto.newsroomdesk

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

// #833 — newsroom-desk people + ledger screens: sources, masthead, wire, ledger, card.

@Composable
fun DeskSources(repo: DeskFakeRepo) {
    DeskPanel {
        DeskSectionLabel("Sources ● all desks")
        DeskHeading("Source book.")
        DeskSub("Clients are global across desks. At most one PENDING story per source.")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeskSub(if (repo.clientsPrivate) "Book shows desk references." else "Book shows full names.")
            Spacer(Modifier.weight(1f))
            DeskLinkButton(
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
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = DeskColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${client.gender} ● ${client.age}y ● $pending ON BUDGET ${if (client.anonymized) "● ANONYMIZED (gender + age kept)" else ""}",
                            color = DeskColors.InkSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (!client.anonymized) {
                        DeskLinkButton(text = "Anonymize", onClick = { repo.anonymize(client.id) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskNoteCard("Anonymized view keeps gender + age for reporting while nulling PII — the name above reads as a desk reference only.")
    }
}

@Composable
fun DeskRoster(repo: DeskFakeRepo) {
    DeskPanel {
        DeskSectionLabel("Masthead ● desk roster")
        DeskHeading("Who files from this desk.")
        DeskSub("Branch slot orders the roster; stringers sort after home slots. ONBOARDING row stays locked.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == DeskRole.ONBOARDING && !repo.onboardGranted
                val you = user.id == repo.currentUserId
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskSlugCell("#${user.slot}")
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            user.name + if (you) " ● YOU" else "",
                            color = DeskColors.Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (locked) "ONBOARDING — no capabilities" else "${user.role.name} ● home ${user.homeBranchId}",
                            color = if (locked) DeskColors.Pencil else DeskColors.InkSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DeskFlagChip(
                        text = user.role.name.take(5),
                        color = when (user.role) {
                            DeskRole.MANAGER -> DeskColors.Ink
                            DeskRole.COORDINATOR -> DeskColors.CopyBlue
                            DeskRole.ACCOUNTANT -> DeskColors.Violet
                            DeskRole.PRACTITIONER -> DeskColors.Filed
                            DeskRole.ONBOARDING -> DeskColors.Pencil
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskNoteCard("Capability check is role-bundle based at runtime; stringer edit grants are day-scoped and expire at the 04:00 Manila boundary.")
    }
}

@Composable
fun DeskWire(repo: DeskFakeRepo) {
    DeskPanel {
        DeskSectionLabel("Wire ● desk announcements")
        DeskHeading("The wire.")
        DeskSub("Every stringer event names its desk and day; taps would open that branch day.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeskGhostButton(text = "Spike all", onClick = { repo.markAllRead() })
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.notifications.forEach { note ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (note.read) DeskColors.PaperDeep else DeskColors.Highlighter)
                        .clickable { repo.markRead(note.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskFlagChip(text = if (note.read) "READ" else "FLASH", color = if (note.read) DeskColors.Faint else DeskColors.Pencil)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(note.title, color = DeskColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("${note.body} ● ${note.at}", color = DeskColors.InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun DeskLedger(repo: DeskFakeRepo) {
    DeskPanel {
        DeskSectionLabel("Ledger ● corrections log")
        DeskHeading("Every change logged.")
        DeskSub("Who did what to which story, with reasons for spikes and undos. Read rows are kept forever.")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.audits.forEach { audit ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskSlugCell(audit.at)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("${audit.who} ● ${audit.action}", color = DeskColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${audit.target}${audit.reason?.let { " — reason: $it" } ?: ""}",
                            color = DeskColors.InkSoft,
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
fun DeskMyPass(
    repo: DeskFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    DeskPanel {
        DeskSectionLabel("My press card")
        DeskHeading(user?.name ?: "No press card")
        DeskSub("Role ${repo.effectiveRole.name} ● home ${user?.homeBranchId ?: "—"} ● slot ${user?.slot ?: "—"} ● ${if (repo.clockedIn) "clocked in" else "clocked out"}.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) {
                DeskGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            } else {
                DeskPrimaryButton(text = "Clock in", onClick = { repo.clockIn() })
            }
            DeskGhostButton(
                text = "Sign out",
                onClick = { repo.logout(); onLogout() },
            )
        }
        Spacer(Modifier.height(8.dp))
        DeskNoteCard("Signing out returns to press passes; the card stays valid for the next budget. Deactivate would block sign-in at once — not modelled with fake data beyond this note.")
    }
}

@Composable
fun DeskBusinessOffice(repo: DeskFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var undoError by remember { mutableStateOf<String?>(null) }
    DeskPanel {
        DeskSectionLabel("Business office ● circ + ads")
        DeskHeading("Remittance editions.")
        DeskSub("Two independent flows: SESSION circ and PRODUCT ads. Submitting freezes an immutable snapshot; Undo reopens within 48h with a reason.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.remittances.forEach { remit ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(remit.kind.name, color = DeskColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        DeskFlagChip(
                            text = remit.state.name,
                            color = if (remit.state == DeskRemitState.SUBMITTED) DeskColors.Filed else DeskColors.CopyBlue,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeskSlugCell("₱${remit.draftTotal}")
                        Spacer(Modifier.weight(1f))
                        if (remit.state == DeskRemitState.DRAFT) {
                            DeskLinkButton(text = "−500", onClick = { repo.bumpDraft(remit.kind, -500) })
                            DeskLinkButton(text = "+500", onClick = { repo.bumpDraft(remit.kind, 500) })
                            DeskGhostButton(text = "Lock edition", onClick = { repo.submit(remit.kind) })
                        }
                    }
                    if (remit.state == DeskRemitState.SUBMITTED) {
                        Text(
                            "Snapshot ${remit.snapshotId} locked ₱${remit.snapshotTotal} — later budget edits never rewrite it.",
                            color = DeskColors.Filed,
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
                            DeskGhostButton(
                                text = "Undo 48h",
                                onClick = { undoError = repo.undo(remit.kind, undoReason); if (undoError == null) undoReason = "" },
                            )
                        }
                        if (undoError != null) {
                            Text(undoError ?: "", color = DeskColors.Pencil, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        DeskSub("Draft — unconstrained, may overlap other drafts at this desk.")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskNoteCard("Commission split: product commissions pool per branch day and split evenly over staff clocked in at sold_at; stringers paid from this drawer. Manual inclusions/exclusions can override — fake total ₱1,800 across 3 filers in this prototype.")
    }
}
