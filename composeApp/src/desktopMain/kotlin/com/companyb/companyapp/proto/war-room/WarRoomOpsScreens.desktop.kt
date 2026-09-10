package com.companyb.companyapp.proto.warroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

// #836 — war-room ops screens: brief, access, sectors, floor, triage, vault.

@Composable
fun WarBrief(repo: WarFakeRepo, onNext: () -> Unit, go: (WarScreen) -> Unit) {
    WarPanel(accent = WarColors.Siren) {
        WarExceptionHeader("brief", "Only what bleeds.")
        WarSub("This room hides healthy work. Voids, variances, relief gaps and undo windows are the whole board. Everything else stays behind the rail.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarStatCell("Voids", "${repo.voidQueue.size}", WarColors.Siren)
            WarStatCell("Variances", "${repo.varianceQueue.size}", WarColors.Amber)
            WarStatCell("Relief gaps", "${repo.reliefGapCount}", WarColors.Violet)
            WarStatCell("Undo open", "${repo.undoWindows.size}", WarColors.Sky)
        }
        Spacer(Modifier.height(10.dp))
        WarNoteCard("ONBOARDING tags are locked at the door: R. Nuevo carries zero capabilities until a MANAGE_USERS grant clears the hold. Nothing triages before that.", WarColors.Siren)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WarPrimaryButton(text = "Take a tag", onClick = onNext)
            WarGhostButton(text = "Clear onboarding hold", onClick = { repo.grantOnboarding() })
        }
        if (repo.onboardGranted) {
            Spacer(Modifier.height(8.dp))
            WarSub("Hold lifted: R. Nuevo now triages as PRACTITIONER in this prototype.")
        }
    }
    WarPanel {
        WarKicker("triage map")
        WarSub("Pick the bleed first. The rail counts stay live while you work.")
        Spacer(Modifier.height(8.dp))
        WarTriageRow("Voids + variances", "${repo.voidQueue.size + repo.varianceQueue.size}", WarColors.Siren) { go(WarScreen.TRIAGE) }
        Spacer(Modifier.height(8.dp))
        WarTriageRow("Relief gaps", "${repo.reliefGapCount}", WarColors.Violet) { go(WarScreen.ROSTER) }
        Spacer(Modifier.height(8.dp))
        WarTriageRow("Undo windows", "${repo.undoWindows.size}", WarColors.Sky) { go(WarScreen.VAULT) }
    }
}

@Composable
fun WarSignin(repo: WarFakeRepo, onNext: () -> Unit) {
    WarPanel {
        WarKicker("access ● tags")
        WarHeading("Take a tag.")
        WarSub("Five fake tags. ONBOARDING stays behind the barrier until the hold is cleared in the Brief.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.users.sortedBy { it.slot }.forEach { user ->
                val locked = user.role == WarRole.ONBOARDING && !repo.onboardGranted
                val active = repo.currentUserId == user.id
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) WarColors.SirenDim else WarColors.BunkerDeep)
                        .border(1.dp, if (active) WarColors.Siren else WarColors.PanelLine, RoundedCornerShape(10.dp))
                        .then(if (!locked) Modifier.clickable { repo.login(user.id); onNext() } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WarSirenCell(user.role.name.take(4))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(user.name, color = WarColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (locked) "ONBOARDING — held at door" else "${user.role.name} ● home ${user.homeBranchId}",
                            color = if (locked) WarColors.Siren else WarColors.Faint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (locked) "HELD" else "TAG IN",
                        color = if (locked) WarColors.Siren else WarColors.Ok,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
fun WarSectors(repo: WarFakeRepo, onNext: () -> Unit) {
    WarPanel {
        WarKicker("sectors ● branch select")
        WarHeading("Pick a sector.")
        WarSub("Each Branch is a sector. Tour and Mission lines run shorter timetables with thinner cover.")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repo.branches.forEach { branch ->
                val active = repo.currentBranchId == branch.id
                val live = repo.sessions.count { it.branchId == branch.id && it.status == WarSessionStatus.PENDING && !it.voided }
                val voids = repo.sessions.count { it.branchId == branch.id && it.voided }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) WarColors.SirenDim else WarColors.BunkerDeep)
                        .border(1.dp, if (active) WarColors.Siren else WarColors.PanelLine, RoundedCornerShape(10.dp))
                        .clickable { repo.currentBranchId = branch.id; onNext() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name.uppercase(), color = WarColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("${branch.sector} ● ${branch.kind.name}", color = WarColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(branch.watchNote, color = WarColors.InkDim, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$live LIVE", color = WarColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text("$voids VOID", color = WarColors.Siren, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun WarFloor(repo: WarFakeRepo, go: (WarScreen) -> Unit) {
    WarPanel(accent = WarColors.Ok) {
        WarKicker("floor ● clock-in home")
        WarHeading("Hold the floor, ${(repo.currentUser?.name ?: "no tag").uppercase()}.")
        WarSub("Home sector ${repo.currentBranch.name}. Clock state drives every exception stamp below.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WarChip(if (repo.clockedIn) "ON THE FLOOR" else "OFF THE FLOOR", if (repo.clockedIn) WarColors.Ok else WarColors.Faint)
            WarChip(repo.effectiveRole.name, WarColors.Violet, filled = false)
            Spacer(Modifier.weight(1f))
            if (repo.clockedIn) {
                WarGhostButton("Clock out", onClick = { repo.clockOut() })
            } else {
                WarPrimaryButton("Clock in", onClick = { repo.clockIn() })
            }
        }
    }
    WarPanel(accent = WarColors.Violet) {
        WarKicker("relief ● gaps")
        WarHeading("Relief gaps bleed here.")
        WarSub("Start relief at another sector view-only until edit is granted. Relief ends at the 04:00 Manila boundary.")
        Spacer(Modifier.height(8.dp))
        if (repo.reliefBranchId == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repo.branches.filter { it.id != repo.currentBranchId }.forEach { branch ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep)
                            .clickable { repo.startRelief(branch.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cover ${branch.name}", color = WarColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            Text(branch.watchNote, color = WarColors.Faint, fontSize = 13.sp)
                        }
                        Text("COVER >", color = WarColors.Violet, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        } else {
            val name = repo.branches.firstOrNull { it.id == repo.reliefBranchId }?.name ?: "?"
            WarNoteCard("Covering $name ${if (repo.reliefEditGranted) "with edit rights." else "view-only until edit is granted."}", WarColors.Violet)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!repo.reliefEditGranted) {
                    WarGhostButton("Request edit", onClick = { repo.grantReliefEdit() })
                }
                WarGhostButton("End relief", onClick = { repo.endRelief() })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Invites".uppercase(), color = WarColors.Faint, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.invites.forEach { invite ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${invite.branchName} ● ${invite.person}", color = WarColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${invite.day} ● ${invite.accepted?.let { if (it) "ACCEPTED" else "DECLINED" } ?: "GAP OPEN"}",
                            color = if (invite.accepted == null) WarColors.Violet else WarColors.Faint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (invite.accepted == null) {
                        WarLinkButton("Yes") { repo.decideInvite(invite.id, true) }
                        WarLinkButton("No") { repo.decideInvite(invite.id, false) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Requests".uppercase(), color = WarColors.Faint, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.requests.forEach { request ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(WarColors.BunkerDeep).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${request.branchName} ● ${request.requester}", color = WarColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${request.day} ● ${request.state}", color = WarColors.Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (request.state == "LIVE") {
                        WarLinkButton("Grant") { repo.decideRequest(request.id, true) }
                        WarLinkButton("Deny") { repo.decideRequest(request.id, false) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WarLinkButton("Open triage") { go(WarScreen.TRIAGE) }
            WarLinkButton("Open vault") { go(WarScreen.VAULT) }
        }
    }
}

@Composable
fun WarTriage(repo: WarFakeRepo) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("PT-Back") }
    var error by remember { mutableStateOf<String?>(null) }

    WarPanel(accent = WarColors.Siren) {
        WarExceptionHeader("triage", "Kill list first.")
        WarSub("Sessions PENDING, NO_SHOW, CANCELLED and COMPLETED. Walk-in rows refuse NO_SHOW and CANCELLED — rebook or void instead.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarFilterChip("ALL", repo.sessionFilter == null) { repo.sessionFilter = null }
            WarSessionStatus.entries.forEach { status ->
                WarFilterChip(status.name, repo.sessionFilter == status) { repo.sessionFilter = status }
            }
        }
        Spacer(Modifier.height(10.dp))
        val rows = repo.sessions.filter { row ->
            (repo.sessionFilter == null || row.status == repo.sessionFilter) && row.branchId == repo.currentBranchId
        }
        if (rows.isEmpty()) {
            WarNoteCard("No rows in ${repo.currentBranch.name} under this filter. The room is quiet here — check another sector.", WarColors.Ok)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                WarSessionCard(repo = repo, row = row, onError = { error = it })
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            WarNoteCard(error ?: "", WarColors.Siren)
        }
    }
    WarPanel {
        WarKicker("walk-in intake")
        WarSub("New walk-ins board as PENDING with the variance watch on.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WarField("Client label", name, { name = it }, Modifier.weight(1f))
            WarField("Service", service, { service = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        WarPrimaryButton("Board walk-in") { repo.addWalkIn(name, service); name = "" }
    }
}

@Composable
private fun WarFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) WarColors.Siren else WarColors.Quiet)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (active) Color.White else WarColors.InkDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun WarSessionCard(repo: WarFakeRepo, row: WarSession, onError: (String?) -> Unit) {
    var reason by remember(row.id, row.voidReason) { mutableStateOf(row.voidReason ?: "") }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WarColors.BunkerDeep)
            .border(1.dp, if (row.voided) WarColors.Siren else WarColors.PanelLine, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${row.id} ● ${row.clientName}", color = WarColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    "${row.startsAt} ● ${row.service} ● ${row.practitioner} ● ₱${row.price}${if (row.walkIn) " ● WALK-IN" else ""}",
                    color = WarColors.Faint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            WarChip(row.status.tag(), row.status.siren())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (row.variance) WarChip("VARIANCE", WarColors.Amber)
            if (row.voided) WarChip("VOID", WarColors.Siren)
            if (row.walkIn) WarChip("WALK-IN", WarColors.Violet, filled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WarSessionStatus.entries.forEach { next ->
                Text(
                    next.name.take(4),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(WarColors.Quiet)
                        .clickable {
                            val err = repo.setStatus(row.id, next)
                            onError(err)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = WarColors.InkDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.weight(1f))
            WarLinkButton(if (row.variance) "Clear flag" else "Flag") { repo.toggleVariance(row.id) }
        }
        if (!row.voided) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WarField("Void reason", reason, { reason = it }, Modifier.weight(1f))
                WarLinkButton("Void") {
                    val err = repo.voidSession(row.id, reason)
                    onError(err)
                }
            }
        } else {
            WarNoteCard("VOIDED: ${row.voidReason ?: "no reason"} — unvoid returns it to the kill list.", WarColors.Siren)
            WarLinkButton("Unvoid") { repo.unvoidSession(row.id); onError(null) }
        }
    }
}

@Composable
fun WarVault(repo: WarFakeRepo) {
    WarPanel(accent = WarColors.Sky) {
        WarExceptionHeader("vault", "Draft, seal, undo.")
        WarSub("SESSION and PRODUCT drafts submit into immutable snapshots. Undo reopens a 48h window with a reason.")
        Spacer(Modifier.height(6.dp))
        WarNoteCard("Commission split: pooled per Branch Day, even split over Practitioners clocked in at sold_at. Relief cover is paid from this vault.", WarColors.Sky)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.remittances.forEach { remit ->
                WarRemitCard(repo = repo, remit = remit)
            }
        }
    }
}

@Composable
private fun WarRemitCard(repo: WarFakeRepo, remit: WarRemittance) {
    var undoReason by remember(remit.kind) { mutableStateOf("") }
    var error by remember(remit.kind) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(WarColors.BunkerDeep)
            .border(1.dp, if (remit.state == WarRemitState.SUBMITTED) WarColors.Sky else WarColors.PanelLine, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${remit.kind.name} DRAWER", color = WarColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            WarChip(remit.state.name, if (remit.state == WarRemitState.SUBMITTED) WarColors.Sky else WarColors.Amber)
            Spacer(Modifier.weight(1f))
            Text("₱${remit.draftTotal}", color = WarColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        if (remit.state == WarRemitState.SUBMITTED) {
            WarNoteCard("Sealed ${remit.snapshotId} @ ₱${remit.snapshotTotal} — immutable until an undo with reason inside 48h.", WarColors.Sky)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WarField("Undo reason", undoReason, { undoReason = it }, Modifier.weight(1f))
                WarLinkButton("Undo 48h") {
                    val err = repo.undo(remit.kind, undoReason)
                    error = err
                    if (err == null) undoReason = ""
                }
            }
            if (error != null) WarNoteCard(error ?: "", WarColors.Siren)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarGhostButton("- 500") { repo.bumpDraft(remit.kind, -500) }
                WarGhostButton("+ 500") { repo.bumpDraft(remit.kind, 500) }
                Spacer(Modifier.weight(1f))
                WarPrimaryButton("Seal snapshot") { repo.submit(remit.kind) }
            }
            if (remit.undoReason != null) {
                WarNoteCard("Last undo: ${remit.undoReason}", WarColors.Violet)
            }
        }
    }
}

@Composable
private fun WarField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), color = WarColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = WarColors.Quiet,
                unfocusedContainerColor = WarColors.Quiet,
                focusedTextColor = WarColors.Ink,
                unfocusedTextColor = WarColors.Ink,
            ),
        )
    }
}
