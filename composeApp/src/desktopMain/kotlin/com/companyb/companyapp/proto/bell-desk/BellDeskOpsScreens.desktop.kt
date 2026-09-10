package com.companyb.companyapp.proto.belldesk

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
import androidx.compose.ui.unit.dp

@Composable
internal fun BdTillTab() {
    val repo = BellDeskFakeRepo
    val me = repo.currentUser.value
    var adjustKind by remember { mutableStateOf(BdRemitKind.SESSION) }
    var freeAmount by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("TILL · SESSION + PRODUCT DRAWERS", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("Count the drawer, seal the snapshot.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Text(
            "Submit seals an immutable snapshot (SN-*). Undo runs 48h and needs a reason. " +
                "Commission split note: lanes share per completed session — the till only records it.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftLobby,
        )
        Spacer(Modifier.height(10.dp))
        repo.remittances.forEach { r ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.kind} · ${r.dayLabel}", style = MaterialTheme.typography.titleMedium, color = InkLobby)
                        Text(
                            "₱${r.amount} · ${r.status}" +
                                (if (r.snapshotNo.isNotEmpty()) " · ${r.snapshotNo}" else "") +
                                (if (r.note.isNotEmpty()) " · ${r.note}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftLobby,
                        )
                    }
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (r.status == BdRemitStatus.DRAFT) Brass else BellGreen)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            r.status.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (r.status == BdRemitStatus.DRAFT) InkLobby else androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
                if (r.status == BdRemitStatus.DRAFT) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BdSmallButton("− ₱500") {
                            val i = repo.remittances.indexOfFirst { it.id == r.id }
                            if (i >= 0) repo.remittances[i] = r.copy(amount = (r.amount - 500).coerceAtLeast(0))
                        }
                        BdSmallButton("+ ₱500") {
                            val i = repo.remittances.indexOfFirst { it.id == r.id }
                            if (i >= 0) repo.remittances[i] = r.copy(amount = r.amount + 500)
                        }
                        BdSmallButton("Seal submit") {
                            val i = repo.remittances.indexOfFirst { it.id == r.id }
                            if (i >= 0) {
                                repo.remittances[i] = r.copy(status = BdRemitStatus.SUBMITTED, snapshotNo = repo.nextSnapshot())
                            }
                            repo.stamp(me.name, "SUBMIT_REMITTANCE", "${r.kind} ${r.dayLabel}")
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BdSmallButton("Undo (48h, reason kept in daybook)") {
                            val i = repo.remittances.indexOfFirst { it.id == r.id }
                            if (i >= 0) {
                                repo.remittances[i] = r.copy(
                                    status = BdRemitStatus.DRAFT,
                                    note = "undone <48h with reason",
                                )
                            }
                            repo.stamp(me.name, "UNDO_REMITTANCE", "${r.snapshotNo} ${r.kind}", "count correction")
                        }
                        Text("Undo window: 48h from seal.", style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BdCard(title = "FREE COUNT · ADD TO A DRAWER") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(BdRemitKind.SESSION, BdRemitKind.PRODUCT).forEach { k ->
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (k == adjustKind) BrassDeep else BrassWash)
                            .clickable { adjustKind = k }.padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            k.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (k == adjustKind) androidx.compose.ui.graphics.Color.White else InkLobby,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = freeAmount,
                    onValueChange = { freeAmount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Amount ₱") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val amt = freeAmount.toIntOrNull() ?: 0
                        val target = repo.remittances.firstOrNull { it.kind == adjustKind && it.status == BdRemitStatus.DRAFT }
                        if (target != null) {
                            val i = repo.remittances.indexOfFirst { it.id == target.id }
                            repo.remittances[i] = target.copy(amount = target.amount + amt)
                            repo.stamp(me.name, "TILL_ADJUST", "$adjustKind +₱$amt")
                            freeAmount = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                ) { Text("Drop in") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Commission split ledger: completed bells pay the lane 60 / house 40; relief cover lanes split evenly. Read-only note — the till never edits splits.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoftLobby,
            )
        }
    }
}

@Composable
internal fun BdTeamTab() {
    val repo = BellDeskFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("STAFF · WHO RINGS, WHO COVERS", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("Roles seat the bell; ONBOARDING watches silently.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Spacer(Modifier.height(10.dp))
        repo.team.forEach { (role, count, note) ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("$role · $count", style = MaterialTheme.typography.titleMedium, color = InkLobby)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
                }
                if (role == "ONBOARDING") {
                    Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = BellRed)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        BdCard(title = "DIRECTORY") {
            repo.directory.forEach { u ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.bodyLarge, color = InkLobby)
                        Text(
                            "${u.role} · home ${repo.branchName(u.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftLobby,
                        )
                    }
                    if (u.onboarding) Text("0 caps", style = MaterialTheme.typography.labelMedium, color = BellRed)
                }
            }
        }
    }
}

@Composable
internal fun BdMailTab() {
    val repo = BellDeskFakeRepo
    val me = repo.currentUser.value
    val unread = repo.mailbox.count { !it.read }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BELLBOX · $unread UNREAD", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
                Text("Every ring leaves a note.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
            }
            OutlinedButton(onClick = {
                repo.mailbox.indices.forEach { i -> repo.mailbox[i] = repo.mailbox[i].copy(read = true) }
                repo.stamp(me.name, "MAIL_READ_ALL", "Bellbox")
            }) { Text("File all") }
        }
        Spacer(Modifier.height(10.dp))
        repo.mailbox.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(if (m.read) MarbleCard else BrassWash)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium)
                    .clickable {
                        val i = repo.mailbox.indexOfFirst { it.id == m.id }
                        if (i >= 0) repo.mailbox[i] = m.copy(read = !m.read)
                        if (!m.read) repo.stamp(me.name, "MAIL_READ", m.title)
                    }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (m.read) "" else "● ") + m.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkLobby,
                    )
                    Text(m.body, style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
                    Text(m.day, style = MaterialTheme.typography.labelMedium, color = BrassDeep)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun BdAuditTab() {
    val repo = BellDeskFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("DAYBOOK · EVERY TAP, STAMPED", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text("The bell remembers who rang it.", style = MaterialTheme.typography.titleLarge, color = InkLobby)
        Spacer(Modifier.height(10.dp))
        repo.auditTrail.forEach { a ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MarbleCard)
                    .border(1.dp, MarbleEdge, MaterialTheme.shapes.small).padding(10.dp),
            ) {
                Text("${a.whenText} · ${a.actor} · ${a.action}", style = MaterialTheme.typography.labelLarge, color = InkLobby)
                Text(a.record, style = MaterialTheme.typography.bodyMedium, color = InkLobby)
                if (a.reason.isNotEmpty()) Text("Reason: ${a.reason}", style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (repo.auditTrail.isEmpty()) Text("No stamps yet.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun BdProfileTab(onLogout: () -> Unit, onExit: () -> Unit) {
    val repo = BellDeskFakeRepo
    val me = repo.currentUser.value
    val seated = repo.sessions.count { it.status == BdSessionStatus.PENDING && it.lane.isNotEmpty() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("PROFILE · BEHIND THE COUNTER", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text(me.name, style = MaterialTheme.typography.displaySmall, color = InkLobby)
        Text(
            "${me.role} · home ${repo.branchName(me.homeBranchId)} · " +
                (if (repo.clockedIn.value) "clocked in at ${repo.branchName(repo.clockedBranchId.value)}" else "clocked out") +
                " · $seated guests seated",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoftLobby,
        )
        Spacer(Modifier.height(10.dp))
        BdCard(title = "SHIFT") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn.value) {
                    OutlinedButton(onClick = {
                        repo.clockedIn.value = false
                        repo.stamp(me.name, "CLOCK_OUT", "Bell counter")
                    }) { Text("Clock out") }
                } else {
                    Button(
                        onClick = {
                            repo.clockedIn.value = true
                            repo.stamp(me.name, "CLOCK_IN", "Bell counter")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                    ) { Text("Clock in") }
                }
                OutlinedButton(onClick = {
                    repo.stamp(me.name, "LOGOUT", "Bell counter")
                    repo.clockedIn.value = false
                    onLogout()
                }) { Text("Log out") }
                OutlinedButton(onClick = onExit) { Text("Close") }
            }
        }
    }
}
