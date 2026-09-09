package com.companyb.companyapp.proto.sessionscribe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun ScribeFinanceScreen(repo: SessionScribeFakeRepo) {
    val branchId = repo.currentBranchId.value
    val billable = repo.sessions.filter { it.branchId == branchId && !it.voided && it.status == SSessionStatus.COMPLETED }
    val sessionTotal = billable.sumOf { it.price }
    val productTotal = repo.remitLines.sumOf { it.qty * it.price }
    val onDuty = repo.users.filter { it.clockedBranchId == branchId }

    SectionHeader(index = "F-1", title = "Tonight's take", aside = "voided stays out")
    Row(modifier = Modifier.fillMaxWidth()) {
        StatCell(label = "Session net", value = "P$sessionTotal")
        Spacer(Modifier.width(8.dp))
        StatCell(label = "Product gross", value = "P$productTotal")
        Spacer(Modifier.width(8.dp))
        StatCell(label = "On duty", value = "${onDuty.size}")
    }
    Spacer(Modifier.height(8.dp))
    FieldNote(
        "Product commissions pool per branch day and split equally among practitioners and coordinators " +
            "clocked in at sold_at — separate from compensation, never remitted. Tonight: ${onDuty.size} ways.",
    )
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "F-2", title = "Remittance drafts", aside = "SESSION + PRODUCT")
    repo.remittances.forEach { r ->
        var undoReason by remember(r.id, r.status) { mutableStateOf("") }
        var undoMsg by remember(r.id) { mutableStateOf("") }
        ScribeCard(accent = if (r.status == SRemitStatus.DRAFT) ScribeTeal else ScribeMuted) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${r.kind.name} · P${r.amount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    ScribeCode("${r.id.uppercase()} · ${r.dayLabel.uppercase()}")
                    if (r.snapshot.isNotEmpty()) {
                        Text(text = r.snapshot, fontSize = 12.sp, color = ScribeMuted)
                    }
                }
                StatusTag(r.status.name)
            }
            Spacer(Modifier.height(8.dp))
            if (r.status == SRemitStatus.DRAFT) {
                ScribeButton(text = "Submit + freeze snapshot", onClick = { repo.submitRemit(r.id) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    ScribeGhostButton(
                        text = "Undo 48h",
                        onClick = { undoMsg = repo.undoRemit(r.id, undoReason) ?: "Undone — back to draft." },
                    )
                }
                if (undoMsg.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = undoMsg, fontSize = 12.sp, color = ScribeGreen)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    FieldNote(
        "Submission freezes an immutable snapshot of the P&L. Undo returns to draft and deletes the snapshot " +
            "within 48 hours with a reason — afterwards the snapshot is permanent.",
    )
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "F-3", title = "Product lines", aside = "qty x price")
    repo.remitLines.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = line.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            ScribeCode("${line.qty} x P${line.price} = P${line.qty * line.price}")
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ScribeMailScreen(repo: SessionScribeFakeRepo) {
    SectionHeader(index = "M-1", title = "Mailbox", aside = "read rows stay forever")
    if (repo.notes.isEmpty()) {
        EmptyScribe("Mailbox empty.")
    } else {
        repo.notes.forEach { n ->
            ScribeCard(accent = if (!n.read) ScribeAmber else null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = n.body, fontSize = 12.sp, color = ScribeMuted)
                        ScribeCode("${repo.branchName(n.branchId).uppercase()} · ${n.day.uppercase()}")
                    }
                    ScribeTag(
                        text = if (n.read) "READ" else "UNREAD",
                        color = if (n.read) ScribeGreen else ScribeAmber,
                        soft = if (n.read) ScribeGreenSoft else ScribeAmberSoft,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    if (!n.read) {
                        ScribeButton(text = "Mark read", onClick = { repo.markRead(n.id, true) })
                    } else {
                        ScribeGhostButton(text = "Mark unread", onClick = { repo.markRead(n.id, false) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ScribeAuditScreen(repo: SessionScribeFakeRepo) {
    SectionHeader(index = "A-1", title = "Audit log", aside = "who changed what, when")
    if (repo.audits.isEmpty()) {
        EmptyScribe("No mutations yet — every tap above lands here.")
    } else {
        repo.audits.forEach { a ->
            ScribeCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${a.action} · ${a.record}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(text = "${a.actor} · ${a.whenText}", fontSize = 12.sp, color = ScribeMuted)
                        if (a.reason.isNotEmpty()) {
                            Text(text = "Why: ${a.reason}", fontSize = 12.sp, color = ScribeMuted)
                        }
                    }
                    ScribeCode(a.id.uppercase())
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun ScribeProfileScreen(repo: SessionScribeFakeRepo, onLogout: () -> Unit) {
    val me = repo.currentUser()
    SectionHeader(index = "P-1", title = "Profile", aside = "you, on paper")
    ScribeCard(accent = ScribeTeal) {
        Text(text = me.name, fontWeight = FontWeight.Black, fontSize = 20.sp)
        ScribeCode("${me.role.uppercase()} · HOME ${repo.branchName(me.homeBranchId).uppercase()}")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Clocked: ${me.clockedBranchId?.let { repo.branchName(it) } ?: "off duty"}",
            fontSize = 13.sp,
            color = ScribeMuted,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row {
        if (me.clockedBranchId == null) {
            ScribeButton(text = "Clock in here", onClick = { repo.clockIn(repo.currentBranchId.value) })
        } else {
            ScribeGhostButton(text = "Clock out", onClick = { repo.clockOut() })
        }
        Spacer(Modifier.width(8.dp))
        ScribeButton(text = "Logout", onClick = onLogout)
    }
    Spacer(Modifier.height(12.dp))
    FieldNote("Logout returns to the login desk; clock state stays with the fake day. ONBOARDING preview lives on the login screen.")
}
