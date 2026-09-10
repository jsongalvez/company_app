package com.companyb.companyapp.proto.newsroomdesk

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

// #833 — newsroom-desk ops screens: budget meeting, press passes, bureaus, desk, stories.

@Composable
fun DeskWelcome(
    repo: DeskFakeRepo,
    onNext: () -> Unit,
) {
    DeskPanel {
        DeskSectionLabel("Budget meeting ● step 1 of 3")
        DeskHeading("What leads today's edition?")
        DeskSub("The day budget lists every Session the way editors list stories: slug, beat, desk, budget flag. Pitch the desk, slug walk-in tips, spike with a reason.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeskSlugCell("QC CENTRAL")
            DeskSlugCell("LAGUNA 3")
            DeskSlugCell("TONDO DESK")
        }
        Spacer(Modifier.height(10.dp))
        DeskNoteCard("ONBOARDING cards are held at the door: R. Nuevo carries zero capabilities until a MANAGE_USERS grant clears the desk. Nothing derives before that.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeskPrimaryButton(text = "Join the budget", onClick = onNext)
            DeskGhostButton(text = "Clear onboarding hold", onClick = { repo.grantOnboarding() })
        }
        if (repo.onboardGranted) {
            Spacer(Modifier.height(8.dp))
            DeskSub("Hold lifted: R. Nuevo now files as PRACTITIONER in this prototype.")
        }
    }
    DeskPanel {
        DeskSectionLabel("How to judge this variant")
        DeskSub("Slug cells replace the Linear list. URGENT flags are first-class. Bureaus replace the branch picker. Judge whether a budget-meeting metaphor makes PENDING vs URGENT vs SPIKED legible at deadline.")
    }
}

@Composable
fun DeskSignin(
    repo: DeskFakeRepo,
    onNext: () -> Unit,
) {
    DeskPanel {
        DeskSectionLabel("Press passes ● step 2 of 3")
        DeskHeading("Show your press card.")
        DeskSub("Five fake cards. ONBOARDING stays at the door until the hold is cleared in the budget meeting.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == DeskRole.ONBOARDING && !repo.onboardGranted
                val active = repo.currentUserId == user.id
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) DeskColors.Highlighter else DeskColors.PaperDeep)
                        .border(1.dp, if (active) DeskColors.Ink else DeskColors.Rule, RoundedCornerShape(8.dp))
                        .then(if (!locked) Modifier.clickable { repo.login(user.id); onNext() } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskSlugCell(user.role.name.take(4))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(user.name, color = DeskColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (locked) "ONBOARDING — held at door" else "${user.role.name} ● home ${user.homeBranchId}",
                            color = if (locked) DeskColors.Pencil else DeskColors.InkSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (locked) "HELD" else "FILE",
                        color = if (locked) DeskColors.Pencil else DeskColors.Filed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
fun DeskBureaus(
    repo: DeskFakeRepo,
    onNext: () -> Unit,
) {
    DeskPanel {
        DeskSectionLabel("Bureaus ● step 3 of 3")
        DeskHeading("Pick your bureau.")
        DeskSub("Each Branch is a bureau desk. Tour and Mission desks run shorter budgets.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.branches.forEach { branch ->
                val active = repo.currentBranchId == branch.id
                val count = repo.sessions.count { it.branchId == branch.id && it.status == DeskSessionStatus.PENDING }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) DeskColors.Highlighter else DeskColors.PaperDeep)
                        .border(1.dp, if (active) DeskColors.Ink else DeskColors.Rule, RoundedCornerShape(8.dp))
                        .clickable { repo.currentBranchId = branch.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(DeskColors.Ink)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(branch.deskNo, color = DeskColors.Highlighter, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(branch.name.uppercase(), color = DeskColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("${branch.kind.name} — ${branch.bureauNote}", color = DeskColors.InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    DeskSlugCell("$count")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        DeskPrimaryButton(text = "Take the assignment desk", onClick = onNext)
    }
}

@Composable
fun DeskAssignment(
    repo: DeskFakeRepo,
    go: (DeskScreen) -> Unit,
) {
    val branch = repo.currentBranch
    val budget = repo.sessions.filter { it.branchId == repo.currentBranchId }
    val pitched = budget.count { it.status == DeskSessionStatus.PENDING }
    val filed = budget.count { it.status == DeskSessionStatus.COMPLETED }
    DeskPanel {
        DeskSectionLabel("Assignment desk ● ${branch.name}")
        DeskHeading("Morning, ${repo.currentUser?.name ?: "stringer"}.")
        DeskSub("Clocked ${if (repo.clockedIn) "in at desk ${branch.deskNo}" else "out"} ● role ${repo.effectiveRole.name} ● $pitched on budget / $filed filed today.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (repo.clockedIn) {
                DeskGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            } else {
                DeskPrimaryButton(text = "Clock in", onClick = { repo.clockIn() })
            }
            DeskGhostButton(text = "Open stories", onClick = { go(DeskScreen.STORIES) })
            DeskGhostButton(text = "Business office", onClick = { go(DeskScreen.BUSINESS) })
        }
    }
    DeskPanel {
        DeskSectionLabel("Stringer assignments")
        if (repo.reliefBranchId == null) {
            DeskSub("File a stringer shift at another desk: view-only until the bureau waves you through.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repo.branches.filter { it.id != repo.currentBranchId }.forEach { other ->
                    DeskGhostButton(text = "String @ ${other.name}", onClick = { repo.startRelief(other.id) })
                }
            }
        } else {
            val reliefName = repo.branches.firstOrNull { it.id == repo.reliefBranchId }?.name ?: "?"
            DeskSub("Stringer shift at $reliefName — ${if (repo.reliefEditGranted) "edit access granted" else "view-only until granted"}. Paid from that desk's drawer; ends 04:00 Manila.")
            Spacer(Modifier.height(8.dp))
            DeskRowActions(
                "Request edit grant" to { repo.grantReliefEdit() },
                "End stringer shift" to { repo.endRelief() },
            )
        }
        Spacer(Modifier.height(10.dp))
        DeskSectionLabel("Desk invites for you")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.invites.forEach { invite ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(invite.branchName, color = DeskColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${invite.person} ● ${invite.day} ● ${invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "AWAITING"}", color = DeskColors.InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (invite.accepted == null) {
                        DeskLinkButton(text = "Yes", onClick = { repo.decideInvite(invite.id, true) })
                        DeskLinkButton(text = "No", onClick = { repo.decideInvite(invite.id, false) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskSectionLabel("Broadcast requests at this desk")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.requests.forEach { request ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)).background(DeskColors.PaperDeep)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(request.requester, color = DeskColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${request.day} ● ${request.state} — one live request per stringer per desk per day", color = DeskColors.InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (request.state == "LIVE") {
                        DeskLinkButton(text = "Allow", onClick = { repo.decideRequest(request.id, true) })
                        DeskLinkButton(text = "Deny", onClick = { repo.decideRequest(request.id, false) })
                    }
                }
            }
        }
    }
    DeskPanel {
        DeskSectionLabel("Top of the budget — next three slugs")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            budget.filter { it.status == DeskSessionStatus.PENDING }.take(3).forEach { session ->
                DeskStoryRow(session = session, onToggleUrgent = { repo.toggleUrgent(session.id) }, compact = true)
            }
            if (budget.none { it.status == DeskSessionStatus.PENDING }) {
                DeskSub("The budget is clear at this desk today.")
            }
        }
    }
}

@Composable
fun DeskStoryRow(
    session: DeskSession,
    onToggleUrgent: () -> Unit,
    compact: Boolean = false,
) {
    val statusColor = if (session.voided) Color(0xFF8A8471) else session.status.flag()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeskColors.PaperDeep)
            .border(1.dp, DeskColors.Rule, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeskSlugCell(session.slugAt)
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.id} — ${session.clientName}".uppercase(),
                    color = DeskColors.Ink,
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${session.service} ● ${session.practitioner}${if (session.walkIn) " ● WALK-IN TIP" else ""}${if (session.voided) " ● SPIKED" else ""}",
                    color = DeskColors.InkSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            DeskFlagChip(text = if (session.voided) "SPIKED" else session.status.label(), color = statusColor)
        }
        if (session.urgent && !session.voided) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskFlagChip(text = "URGENT", color = DeskColors.Pencil)
                Text("Needs a rewrite before deadline.", color = DeskColors.Pencil, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                DeskLinkButton(text = "Clear", onClick = onToggleUrgent)
            }
        } else if (!session.voided) {
            Spacer(Modifier.height(4.dp))
            Row {
                Spacer(Modifier.weight(1f))
                DeskLinkButton(text = "Mark urgent", onClick = onToggleUrgent)
            }
        }
    }
}

@Composable
fun DeskStories(repo: DeskFakeRepo) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("PT-Back") }
    var notice by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var voidError by remember { mutableStateOf<String?>(null) }
    val list = repo.sessions.filter { it.branchId == repo.currentBranchId }
        .let { rows -> repo.sessionFilter?.let { f -> rows.filter { it.status == f } } ?: rows }

    DeskPanel {
        DeskSectionLabel("Stories ● desk ${repo.currentBranch.deskNo}")
        DeskHeading("The day budget.")
        DeskSub("Sessions are stories. Walk-in tips cannot take NO_SHOW or CANCELLED — re-slug or spike instead.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeskFlagChip(text = "ALL", color = DeskColors.Ink, selected = repo.sessionFilter == null, onClick = { repo.sessionFilter = null })
            DeskSessionStatus.entries.forEach { option ->
                DeskFlagChip(
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
                DeskStoryRow(session = session, onToggleUrgent = { repo.toggleUrgent(session.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeskSessionStatus.entries.forEach { next ->
                        DeskLinkButton(text = next.label(), onClick = { notice = repo.setStatus(session.id, next) })
                    }
                    DeskLinkButton(text = "Spike desk", onClick = { managing = if (managing == session.id) null else session.id; voidError = null })
                }
                if (managing == session.id) {
                    DeskNoteCard("Spiking (void) keeps the slug on the budget with a reason; unspike restores it. Price ₱${session.price} ● spiked=${session.voided} ● reason=${session.voidReason ?: "—"}.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            TextField(
                                value = voidReason,
                                onValueChange = { voidReason = it },
                                label = { Text("Spike reason") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(),
                            )
                        }
                        if (session.voided) {
                            DeskGhostButton(text = "Unspike", onClick = { repo.unvoidSession(session.id) })
                        } else {
                            DeskGhostButton(
                                text = "Spike",
                                onClick = { voidError = repo.voidSession(session.id, voidReason); if (voidError == null) voidReason = "" },
                            )
                        }
                    }
                    if (voidError != null) {
                        Text(voidError ?: "", color = DeskColors.Pencil, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (list.isEmpty()) DeskSub("No slugs under this flag at this desk.")
        }
        if (notice != null) {
            Spacer(Modifier.height(6.dp))
            Text(notice ?: "", color = DeskColors.Pencil, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            DeskLinkButton(text = "Dismiss", onClick = { notice = null })
        }
    }
    DeskPanel {
        DeskSectionLabel("New walk-in tip ● over the transom")
        DeskSub("Slugs a PENDING walk-in story with a tip number and creates its source record.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Tipster label") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors())
            }
            Box(Modifier.weight(1f)) {
                TextField(value = service, onValueChange = { service = it }, label = { Text("Beat") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors())
            }
        }
        Spacer(Modifier.height(8.dp))
        DeskPrimaryButton(
            text = "Slug walk-in TIP-${(repo.tipCounter + 1).toString().padStart(3, '0')}",
            onClick = { repo.addWalkIn(name, service); name = "" },
        )
    }
}
