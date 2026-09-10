package com.companyb.companyapp.proto.readonlyaudit

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun statusTint(status: RaSessionStatus): Color = when (status) {
    RaSessionStatus.PENDING -> AuditGlass.Amber
    RaSessionStatus.COMPLETED -> AuditGlass.Ledger
    RaSessionStatus.NO_SHOW -> AuditGlass.Sky
    RaSessionStatus.CANCELLED -> AuditGlass.Rose
}

@Composable
fun RaHome(repo: ReadonlyAuditRepo) {
    AuditHeader("Home — clocked-out by design",
        repo.currentBranch.name + " · " + repo.currentDay.date + " (" + repo.currentDay.state.label + "). " +
            "The audit seat watches the day; it never joins it.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            Text("CLOCK", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Not clocked in", color = AuditGlass.Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Audit seats are never rostered, so there is no shift to start.",
                color = AuditGlass.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            LockedAction("Clock in", "Clock-in requires an ACTIVE branch assignment — audit seats hold none")
        }
        GlassPanel(Modifier.weight(1f), alpha = 0.06f) {
            Text("TODAY AT A GLANCE", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val daySessions = repo.sessionsForDay(repo.currentDayId)
            StatStrip(
                listOf(
                    Triple("Sessions", daySessions.size.toString(), "on this day"),
                    Triple("Pending", daySessions.count { it.status == RaSessionStatus.PENDING }.toString(),
                        "awaiting outcome"),
                    Triple("Voided", daySessions.count { it.voided }.toString(), "excluded, kept visible"),
                ),
            )
            Spacer(Modifier.height(8.dp))
            NoteCard("Figures are read live from the same day the roster sees — the glass shows " +
                "the number, never the lever.")
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("RELIEF BOARD", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        repo.reliefBoard.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                StatusPill(item.kind.label, when (item.kind) {
                    RaReliefKind.DUTY -> AuditGlass.Sky
                    RaReliefKind.REQUEST -> AuditGlass.Amber
                    RaReliefKind.INVITE -> AuditGlass.Violet
                })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.who + " — " + item.branchDay, color = AuditGlass.Paper, fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(item.detail, color = AuditGlass.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    LockReason(
                        when (item.kind) {
                            RaReliefKind.DUTY -> "Relief grants are decided by branch members — audit seats observe"
                            RaReliefKind.REQUEST -> "Grant/Deny needs branch membership — audit seats observe"
                            RaReliefKind.INVITE -> "Accept/Decline belongs to the invitee — audit seats observe"
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NoteCard("Relief duty starts view-only; edit access needs a relief grant (broadcast request " +
            "approved by any branch member, or a branch-initiated invite). Grants expire at 04:00 Manila.")
    }
}

@Composable
fun RaSessions(repo: ReadonlyAuditRepo) {
    var filter by remember { mutableStateOf<RaSessionStatus?>(null) }
    var selectedId by remember { mutableStateOf(repo.sessionsForDay(repo.currentDayId).firstOrNull()?.id) }
    var voidTarget by remember { mutableStateOf<RaSession?>(null) }
    var unvoidTarget by remember { mutableStateOf<RaSession?>(null) }
    val list = repo.sessionsForDay(repo.currentDayId).filter { filter == null || it.status == filter }
    val selected = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()

    AuditHeader("Sessions — every lever, frozen",
        repo.sessionsForDay(repo.currentDayId).size.toString() + " sessions on " + repo.currentDay.date +
            ". Pick one: the detail shows what the role would press, and why this seat cannot.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AuditChip("All", filter == null) { filter = null }
        RaSessionStatus.entries.forEach { status ->
            AuditChip(status.label, filter == status) { filter = if (filter == status) null else status }
        }
    }
    Spacer(Modifier.height(8.dp))
    NoteCard("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — they were never booked, " +
        "so there is nothing to miss.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f)) {
            list.forEach { session ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (session.id == selected?.id) Color.White.copy(alpha = 0.09f)
                        else Color.Transparent)
                        .clickable { selectedId = session.id }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(session.id + " · " + session.clientName + if (session.walkIn) " · walk-in" else "",
                            color = AuditGlass.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(session.practitioners + " · " + session.price, color = AuditGlass.Muted,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    StatusPill(session.status.label, statusTint(session.status))
                    if (session.voided) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("VOIDED", AuditGlass.Rose)
                    }
                }
            }
            if (list.isEmpty()) {
                Text("No sessions carry this status on " + repo.currentDay.date + ".",
                    color = AuditGlass.Muted, fontSize = 13.sp)
            }
        }
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            if (selected == null) {
                Text("Nothing selected.", color = AuditGlass.Muted, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.id + " · " + selected.clientName, color = AuditGlass.Paper,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(selected.status.label, statusTint(selected.status))
                }
                Spacer(Modifier.height(6.dp))
                Text("Practitioners: " + selected.practitioners + " · Final price " + selected.price +
                    if (selected.walkIn) " · walk-in" else " · booked",
                    color = AuditGlass.Muted, fontSize = 12.5.sp)
                if (selected.voided) {
                    Spacer(Modifier.height(8.dp))
                    NoteCard("VOIDED — excluded from financial calculations, record preserved. " +
                        "Reason: \"" + (selected.voidReason ?: "—") + "\"", tint = AuditGlass.Rose)
                }
                Spacer(Modifier.height(12.dp))
                LockedAction("Complete", "Status changes need EDIT_BRANCH_DATA on an OPEN day — seat holds VIEW")
                Spacer(Modifier.height(6.dp))
                if (selected.walkIn) {
                    LockedAction("No-show", "Walk-in sessions cannot be NO_SHOW — nothing was booked to miss")
                    Spacer(Modifier.height(6.dp))
                    LockedAction("Cancel", "Walk-in sessions cannot be CANCELLED — nothing was booked to cancel")
                } else {
                    LockedAction("No-show", "Status changes need EDIT_BRANCH_DATA on an OPEN day — seat holds VIEW")
                    Spacer(Modifier.height(6.dp))
                    LockedAction("Cancel", "Status changes need EDIT_BRANCH_DATA on an OPEN day — seat holds VIEW")
                }
                Spacer(Modifier.height(6.dp))
                if (selected.voided) {
                    Text("UNVOID — reverses a void done in error", color = AuditGlass.Paper,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditChip("Preview unvoid", false) { unvoidTarget = selected }
                    }
                    Spacer(Modifier.height(6.dp))
                    LockReason("Unvoid needs EDIT_BRANCH_DATA — the preview shows the filed reason")
                } else {
                    Text("VOID — excludes from financials, keeps the record", color = AuditGlass.Paper,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditChip("Preview void", false) { voidTarget = selected }
                    }
                    Spacer(Modifier.height(6.dp))
                    LockReason("Void needs EDIT_BRANCH_DATA plus a filed reason — the preview is read-only")
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    LockedAction("Book session", "Booking needs EDIT_BRANCH_DATA on an OPEN day — seat holds VIEW",
        Modifier.fillMaxWidth())

    if (voidTarget != null) {
        var reason by remember(voidTarget) { mutableStateOf(voidTarget?.voidReason ?: "Duplicate entry — rebook") }
        AlertDialog(
            onDismissRequest = { voidTarget = null },
            title = { Text("Void " + (voidTarget?.id ?: "") + " (preview)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Void excludes the session from financial calculations while preserving its record. " +
                        "A reason is required — you may draft it below, but this seat cannot file it.",
                        fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = reason, onValueChange = { reason = it },
                        label = { Text("Reason (draft only)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    LockReason("Filing a void needs EDIT_BRANCH_DATA on an OPEN day — seat holds VIEW")
                }
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) { Text("File void") }
            },
            dismissButton = {
                TextButton(onClick = { voidTarget = null }) { Text("Close") }
            },
        )
    }
    if (unvoidTarget != null) {
        AlertDialog(
            onDismissRequest = { unvoidTarget = null },
            title = { Text("Unvoid " + (unvoidTarget?.id ?: "") + " (preview)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Filed reason on record: \"" + (unvoidTarget?.voidReason ?: "—") + "\". " +
                        "Unvoiding restores the session into financial calculations.",
                        fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LockReason("Unvoid needs EDIT_BRANCH_DATA — the filed reason stays on record either way")
                }
            },
            confirmButton = {
                TextButton(onClick = {}, enabled = false) { Text("Restore session") }
            },
            dismissButton = {
                TextButton(onClick = { unvoidTarget = null }) { Text("Close") }
            },
        )
    }
}

@Composable
fun RaClients(repo: ReadonlyAuditRepo) {
    var selectedId by remember { mutableStateOf("c-01") }
    val selected = repo.clients.firstOrNull { it.id == selectedId } ?: repo.clients.first()

    AuditHeader("Clients — global registry, glassed",
        "One person record shared across all branches. At most one PENDING session at a time — " +
            "the registry refuses a second.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f)) {
            repo.clients.forEach { client ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (client.id == selected.id) Color.White.copy(alpha = 0.09f)
                        else Color.Transparent)
                        .clickable { selectedId = client.id }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(client.name ?: client.anonCode ?: "—", color = AuditGlass.Paper,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(client.gender + " · " + client.age + " · " + client.branchSeen,
                            color = AuditGlass.Muted, fontSize = 12.sp)
                    }
                    if (client.hasPending) StatusPill("* PENDING", AuditGlass.Amber)
                    if (client.name == null) StatusPill("ANONYMIZED", AuditGlass.Violet)
                }
            }
        }
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            Text(selected.name ?: selected.anonCode ?: "—", color = AuditGlass.Paper,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Gender " + selected.gender + " · Age " + selected.age + " · " + selected.branchSeen,
                color = AuditGlass.Muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
            if (selected.name == null) {
                NoteCard("Anonymized view: soft-deleted record with PII nullified — gender and age " +
                    "are retained for reporting.", tint = AuditGlass.Violet)
            } else if (selected.hasPending) {
                NoteCard("* PENDING marks the one live session — booking a second is refused " +
                    "until it resolves.", tint = AuditGlass.Amber)
            } else {
                NoteCard("No live PENDING session — this client could be booked today.",
                    tint = AuditGlass.Ledger)
            }
            Spacer(Modifier.height(12.dp))
            LockedAction("Anonymize", "Anonymize needs MANAGE_CLIENTS (Coordinator) — seat holds VIEW")
        }
    }
}

@Composable
fun RaFinance(repo: ReadonlyAuditRepo) {
    AuditHeader("Finance — drafts you can read, snapshots you cannot touch",
        "SESSION nets income after compensation and expenses; PRODUCT multiplies unit price by quantity. " +
            "Submission freezes an immutable snapshot — Undo has 48 hours, then permanence.")
    Spacer(Modifier.height(12.dp))
    repo.remittancesForDay(repo.currentDayId).forEach { remit ->
        GlassPanel(Modifier.fillMaxWidth().padding(vertical = 6.dp), alpha = 0.08f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(remit.kind.label, AuditGlass.Ledger)
                Spacer(Modifier.width(8.dp))
                StatusPill(if (remit.submitted) "SUBMITTED" else "DRAFT",
                    if (remit.submitted) AuditGlass.Violet else AuditGlass.Amber)
                Spacer(Modifier.width(8.dp))
                Text(remit.id, color = AuditGlass.Faint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                if (remit.snapshotId != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("snapshot " + remit.snapshotId, color = AuditGlass.Violet, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(10.dp))
            StatStrip(
                listOf(
                    Triple("Gross", remit.gross, null),
                    Triple("Deductions", remit.deductions, null),
                    Triple("Net", remit.net, "gross − deductions"),
                ),
            )
            if (remit.lines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                remit.lines.forEach { line ->
                    Text("· " + line, color = AuditGlass.Muted, fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!remit.submitted) {
                LockedAction("Submit " + remit.kind.label.lowercase() + " remittance",
                    "Submit needs SUBMIT_REMITTANCE (Coordinator) — drafts stay drafts for this seat")
            } else {
                val hours = remit.submittedAgoHours ?: 99
                if (hours <= 48) {
                    NoteCard("Submitted " + hours + "h ago — inside the 48h Undo window. Undo returns the " +
                        "remittance to Draft, unlocks the covered days and deletes the snapshot, " +
                        "with a reason in the audit trail.", tint = AuditGlass.Amber)
                    Spacer(Modifier.height(8.dp))
                    LockedAction("Undo with reason",
                        "Undo needs SUBMIT_REMITTANCE plus a filed reason — seat holds VIEW")
                } else {
                    NoteCard("Submitted " + hours + "h ago — the 48h window has closed. The snapshot " +
                        "is permanent; later edits never rewrite it.", tint = AuditGlass.Rose)
                    Spacer(Modifier.height(8.dp))
                    LockedAction("Undo with reason",
                        "Window closed after 48h — snapshot " + (remit.snapshotId ?: "") + " is permanent")
                }
            }
        }
    }
    if (repo.remittancesForDay(repo.currentDayId).isEmpty()) {
        NoteCard("No remittance rows cover " + repo.currentDay.date + " yet — switch the branch-day " +
            "strip to 2026-09-08 for submitted snapshots.")
        Spacer(Modifier.height(12.dp))
    }
    GlassPanel(Modifier.fillMaxWidth(), alpha = 0.06f) {
        Text("COMMISSION SPLIT — OUTSIDE REMITTANCE", color = AuditGlass.Faint, fontSize = 11.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Pool " + repo.commissionPool + " · split equally across the clocked-in crew",
            color = AuditGlass.Paper, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        repo.commissionCrew.forEach { member ->
            Text("· " + member, color = AuditGlass.Muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(repo.commissionNote, color = AuditGlass.Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
fun RaTeam(repo: ReadonlyAuditRepo) {
    var selectedId by remember { mutableStateOf("u-onb") }
    val ordered = repo.team.sortedWith(compareBy({ it.relief }, { it.slot }))
    val selected = ordered.firstOrNull { it.id == selectedId } ?: ordered.first()

    AuditHeader("Team — slots, bundles, and one empty chair",
        "Branch Slot orders the display (1 = senior); relief sorts last. Tap the ONBOARDING row " +
            "to see what zero capabilities looks like.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f)) {
            ordered.forEach { member ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (member.id == selected.id) Color.White.copy(alpha = 0.09f)
                        else Color.Transparent)
                        .clickable { selectedId = member.id }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(member.name + if (member.relief) " · relief" else "",
                            color = AuditGlass.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(member.role.label + " · slot " + member.slot, color = AuditGlass.Muted,
                            fontSize = 12.sp)
                    }
                    StatusPill(
                        if (member.role.locked) "LOCKED" else member.role.label.uppercase(),
                        if (member.role.locked) AuditGlass.Rose else AuditGlass.Ledger,
                    )
                }
            }
        }
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            Text(selected.name, color = AuditGlass.Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(selected.role.label + " · home " +
                repo.branches.first { it.id == selected.homeBranchId }.name +
                " · slot " + selected.slot + if (selected.relief) " · relief (sorts last)" else "",
                color = AuditGlass.Muted, fontSize = 12.5.sp)
            Spacer(Modifier.height(10.dp))
            Text("CAPABILITY BUNDLE", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (selected.capabilities.isEmpty()) {
                NoteCard("Empty bundle — ONBOARDING derives nothing, anywhere, until MANAGE_USERS " +
                    "grants a real role.", tint = AuditGlass.Rose)
            } else {
                selected.capabilities.forEach { cap ->
                    Text("· " + cap, color = AuditGlass.Paper, fontSize = 12.5.sp)
                    Spacer(Modifier.height(3.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (selected.role.locked) {
                LockedAction("Grant Practitioner", "Role grants need MANAGE_USERS (MANAGER) — seat holds VIEW")
            } else {
                LockedAction("Change role", "Role grants need MANAGE_USERS (MANAGER) — seat holds VIEW")
            }
        }
    }
}

@Composable
fun RaMail(repo: ReadonlyAuditRepo, onOpenDay: (String) -> Unit) {
    var selectedId by remember { mutableStateOf(repo.mailbox.firstOrNull()?.id) }
    val selected = repo.mailbox.firstOrNull { it.id == selectedId } ?: repo.mailbox.firstOrNull()

    Row {
        AuditHeader("Mailbox — the one drawer that opens",
            "Reading is the single action this seat owns. Tap a row to mark it read; " +
                "relief events tap through to their Branch day.")
        Spacer(Modifier.weight(1f))
        AuditChip("Mark all read (" + repo.unreadCount + ")", false) { repo.markAllRead() }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f)) {
            repo.mailbox.forEach { note ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (note.id == selected?.id) Color.White.copy(alpha = 0.09f)
                        else Color.Transparent)
                        .clickable {
                            repo.markRead(note.id)
                            selectedId = note.id
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(8.dp).height(8.dp).clip(CircleShape)
                            .background(if (note.read) Color.Transparent else AuditGlass.Ledger),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(note.title, color = if (note.read) AuditGlass.Muted else AuditGlass.Paper,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(if (note.read) "READ · kept forever" else "UNREAD",
                            color = if (note.read) AuditGlass.Faint else AuditGlass.Ledger,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            if (selected == null) {
                Text("Mailbox empty.", color = AuditGlass.Muted, fontSize = 13.sp)
            } else {
                Text(selected.title, color = AuditGlass.Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(selected.body, color = AuditGlass.Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                if (selected.dayId != null) {
                    AuditChip("Jump to branch day", false) { onOpenDay(selected.dayId) }
                    Spacer(Modifier.height(8.dp))
                    Text("Read rows are kept forever — this mailbox is history, not a queue.",
                        color = AuditGlass.Faint, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
fun RaAudit(repo: ReadonlyAuditRepo) {
    AuditHeader("Audit log — the ledger of levers",
        "Every mutation: who changed what, when, and why. Immutable and append-only — " +
            "the one screen nobody can lock, because nobody can edit it.")
    Spacer(Modifier.height(12.dp))
    GlassPanel(Modifier.fillMaxWidth()) {
        repo.auditTrail.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                StatusPill(entry.action, when (entry.action) {
                    "INSERT" -> AuditGlass.Ledger
                    "UPDATE" -> AuditGlass.Amber
                    else -> AuditGlass.Rose
                })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.table + " · " + entry.record, color = AuditGlass.Paper, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(2.dp))
                    Text("by " + entry.caller + " — " + entry.why, color = AuditGlass.Muted,
                        fontSize = 12.sp)
                }
                Text("#" + entry.id, color = AuditGlass.Faint, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    NoteCard("Each entry captures table, record id, action, caller and before-and-after snapshots — " +
        "this prototype shows the human-readable why-line for each.")
}

@Composable
fun RaProfile(repo: ReadonlyAuditRepo, onLogout: () -> Unit, onSwitchBranch: () -> Unit) {
    val user = repo.currentUser
    AuditHeader("Profile — " + user.name,
        "Accountant: read-only across all branches. No edit capabilities — the glass is the job.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.weight(1f), alpha = 0.09f) {
            Text("ROLE BUNDLE", color = AuditGlass.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            StatusPill(user.role.label.uppercase(), AuditGlass.Ledger)
            Spacer(Modifier.height(8.dp))
            user.capabilities.forEach { cap ->
                Text("· " + cap, color = AuditGlass.Paper, fontSize = 12.5.sp)
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Global VIEW_BRANCH_DATA derives from the role; branch edit codes never do.",
                color = AuditGlass.Muted, fontSize = 12.sp)
        }
        GlassPanel(Modifier.weight(1f)) {
            LockedAction("Clock out", "Audit seats never clock in — there is no shift to end")
            Spacer(Modifier.height(6.dp))
            LockedAction("Deactivate user", "Deactivation needs MANAGE_USERS (MANAGER) — seat holds VIEW")
            Spacer(Modifier.height(12.dp))
            Text("Glass-box navigation — always allowed", color = AuditGlass.Paper, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditChip("Switch Branch", false, onClick = onSwitchBranch)
                AuditChip("Logout", true, onClick = onLogout)
            }
        }
    }
}
