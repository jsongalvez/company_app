package com.companyb.companyapp.proto.queuedesk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun QdVaultTab() {
    val me = QueueDeskFakeRepo.currentUser.value
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("VAULT · REMITTANCE", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text("Two drawers, one seal each.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(10.dp))
        QueueDeskFakeRepo.remittances.filter { it.status == QdRemitStatus.DRAFT }.forEach { remit ->
            QdRemitDrawer(remit = remit, actor = me.name)
            Spacer(Modifier.height(10.dp))
        }
        Text("SEALED SNAPSHOTS", style = MaterialTheme.typography.titleSmall, color = InkQueue)
        Spacer(Modifier.height(6.dp))
        QueueDeskFakeRepo.remittances.filter { it.status == QdRemitStatus.SUBMITTED }.forEach { remit ->
            var reason by remember(remit.id) { mutableStateOf("") }
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${remit.kind} · ${remit.dayLabel}", style = MaterialTheme.typography.titleMedium, color = InkQueue)
                        Text("Snapshot ${remit.snapshotNo} · ₱${remit.amount} · immutable", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(StampGreen).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("SEALED", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Undo reason (required, 48h window)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val index = QueueDeskFakeRepo.remittances.indexOfFirst { it.id == remit.id }
                        if (index >= 0) {
                            QueueDeskFakeRepo.remittances[index] = remit.copy(status = QdRemitStatus.DRAFT, snapshotNo = "", note = "")
                        }
                        QueueDeskFakeRepo.stamp(me.name, "UNDO_REMITTANCE", "${remit.kind} ${remit.snapshotNo}", reason)
                    },
                    enabled = reason.isNotBlank(),
                ) { Text("Undo within 48h (reopens draft, deletes snapshot)") }
            }
        }
        Spacer(Modifier.height(10.dp))
        QdSectionCard("Commission split rule") {
            Text(
                "Product commissions pool per branch day and split equally across every practitioner " +
                    "and coordinator clocked in at sold_at time. Manual inclusions or exclusions override. " +
                    "Relief hands are paid from this branch drawer. Separate from compensation, outside remittance.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
internal fun QdRemitDrawer(remit: QdRemittance, actor: String) {
    var amount by remember(remit.id) { mutableStateOf("${remit.amount}") }
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White)
            .border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${remit.kind} DRAFT · ${remit.dayLabel}", style = MaterialTheme.typography.titleMedium, color = InkQueue)
                Text("Drafts overlap freely; submission freezes an immutable snapshot.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(SlipPending).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("DRAFT", style = MaterialTheme.typography.labelMedium, color = InkQueue)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                val next = (amount.toIntOrNull() ?: 0) - 500
                amount = "$next"
            }) { Text("-500") }
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("Amount ₱") }, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val next = (amount.toIntOrNull() ?: 0) + 500
                amount = "$next"
            }) { Text("+500") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val sealed = QueueDeskFakeRepo.nextSnapshot()
                val index = QueueDeskFakeRepo.remittances.indexOfFirst { it.id == remit.id }
                if (index >= 0) {
                    QueueDeskFakeRepo.remittances[index] = remit.copy(
                        status = QdRemitStatus.SUBMITTED,
                        amount = amount.toIntOrNull() ?: remit.amount,
                        snapshotNo = sealed,
                    )
                }
                QueueDeskFakeRepo.stamp(actor, "SUBMIT_REMITTANCE", "${remit.kind} ${remit.dayLabel} / $sealed")
            },
            colors = ButtonDefaults.buttonColors(containerColor = StampGreen),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit ${remit.kind} (seal snapshot)") }
    }
}

@Composable
internal fun QdTeamTab() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("HANDS · WHO WORKS THE BELT", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text("Roles, headcount, posting. No personal detail on this board.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(10.dp))
        QueueDeskFakeRepo.team.forEach { (role, count, posting) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(BeltDark).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("$count", style = MaterialTheme.typography.labelLarge, color = SlipPending)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(role, style = MaterialTheme.typography.titleMedium, color = InkQueue)
                    Text(posting, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "ONBOARDING row stays a locked grant note: assignment alone grants nothing until MANAGE_USERS acts.",
            style = MaterialTheme.typography.bodySmall,
            color = StampRed,
        )
    }
}

@Composable
internal fun QdMailboxTab() {
    val me = QueueDeskFakeRepo.currentUser.value
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PIGEONHOLE · ${QueueDeskFakeRepo.mailbox.count { !it.read }} UNREAD", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
                Text("Every slip kept. Read rows stay as history.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
            }
            OutlinedButton(onClick = {
                QueueDeskFakeRepo.mailbox.forEachIndexed { index, mail ->
                    QueueDeskFakeRepo.mailbox[index] = mail.copy(read = true)
                }
                QueueDeskFakeRepo.stamp(me.name, "MAIL_READ_ALL", "Pigeonhole")
            }) { Text("File all") }
        }
        Spacer(Modifier.height(10.dp))
        if (QueueDeskFakeRepo.mailbox.isEmpty()) {
            QdEmptyNote("Pigeonhole empty.")
        } else {
            QueueDeskFakeRepo.mailbox.forEach { mail ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (mail.read) VoidWash else Color.White)
                        .border(1.dp, TicketEdge, MaterialTheme.shapes.medium)
                        .clickable {
                            val index = QueueDeskFakeRepo.mailbox.indexOfFirst { it.id == mail.id }
                            if (index >= 0) QueueDeskFakeRepo.mailbox[index] = mail.copy(read = true)
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (mail.read) TicketEdge else ClaimBlue)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(if (mail.read) "FILED" else "NEW", style = MaterialTheme.typography.labelMedium, color = if (mail.read) InkQueue else Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mail.title, style = MaterialTheme.typography.titleMedium, color = InkQueue)
                        Text("${mail.body} · ${mail.day}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
            }
        }
    }
}

@Composable
internal fun QdAuditTab() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("DAYBOOK · AUDIT LOG", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text("Every prototype mutation lands here.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
        Spacer(Modifier.height(10.dp))
        QueueDeskFakeRepo.auditTrail.forEach { entry ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.action, style = MaterialTheme.typography.labelLarge, color = InkQueue, modifier = Modifier.weight(1f))
                    Text(entry.whenText, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                Text("${entry.actor} · ${entry.record}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                if (entry.reason.isNotBlank()) {
                    Text("Reason: ${entry.reason}", style = MaterialTheme.typography.bodySmall, color = StampRed)
                }
            }
        }
    }
}

@Composable
internal fun QdProfileTab(onLogout: () -> Unit, onExit: () -> Unit) {
    val me = QueueDeskFakeRepo.currentUser.value
    val clocked by QueueDeskFakeRepo.clockedIn
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("PROFILE", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
        Text(me.name, style = MaterialTheme.typography.displaySmall, color = InkQueue)
        Text("${me.role} · home ${QueueDeskFakeRepo.branchName(me.homeBranchId)}", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Text(
            if (clocked) "Clocked in at ${QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value)}" else "Clocked out",
            style = MaterialTheme.typography.bodyMedium,
            color = if (clocked) StampGreen else StampRed,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (clocked) {
                Button(onClick = {
                    QueueDeskFakeRepo.clockedIn.value = false
                    QueueDeskFakeRepo.stamp(me.name, "CLOCK_OUT", QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value))
                }, colors = ButtonDefaults.buttonColors(containerColor = StampRed)) { Text("Clock out") }
            } else {
                Button(onClick = {
                    QueueDeskFakeRepo.clockedIn.value = true
                    QueueDeskFakeRepo.stamp(me.name, "CLOCK_IN", QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value))
                }, colors = ButtonDefaults.buttonColors(containerColor = StampGreen)) { Text("Clock in") }
            }
            OutlinedButton(onClick = {
                QueueDeskFakeRepo.clockedIn.value = false
                QueueDeskFakeRepo.stamp(me.name, "LOGOUT", "Signed out")
                onLogout()
            }) { Text("Log out") }
            OutlinedButton(onClick = onExit) { Text("Close") }
        }
        Spacer(Modifier.height(12.dp))
        QdSectionCard("Shift note") {
            Text(
                "Logging out returns to sign-in and locks the belt. Clock state is fake and resets on restart.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}
