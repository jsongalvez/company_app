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
fun ScribeClientsScreen(repo: SessionScribeFakeRepo) {
    var query by remember { mutableStateOf("") }
    SectionHeader(index = "C-1", title = "Clients", aside = "global across branches")
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(10.dp))
    val rows = repo.clients.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    if (rows.isEmpty()) {
        EmptyScribe("No client matches \"$query\".")
    } else {
        rows.forEach { c ->
            ScribeCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "${c.gender}/${c.age} · anonymized" else c.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        if (!c.anonymized) {
                            ScribeCode("${c.id.uppercase()} · ${c.gender}/${c.age} · ${c.visits} VISITS")
                            Text(text = c.branchNote, fontSize = 12.sp, color = ScribeMuted)
                        } else {
                            ScribeCode("${c.id.uppercase()} · PII REMOVED · ${c.gender}/${c.age} KEPT")
                        }
                        ScribeCode("LAST · ${c.lastType.uppercase()} · P${c.lastPrice}")
                    }
                    if (c.hasPending) {
                        ScribeTag(text = "1 PENDING", color = ScribeAmber, soft = ScribeAmberSoft)
                    } else {
                        ScribeTag(text = "CLEAR", color = ScribeGreen, soft = ScribeGreenSoft)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    var msg by remember(c.id) { mutableStateOf("") }
                    ScribeButton(
                        text = "Log next visit",
                        onClick = { msg = repo.quickLog(c.id, repo.currentBranchId.value) ?: "Logged with smart defaults." },
                    )
                    if (msg.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(text = msg, fontSize = 12.sp, color = if (msg.startsWith("Logged")) ScribeGreen else ScribeRed)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    FieldNote(
        "Client records are global and shared across branches. At most one PENDING session each; " +
            "anonymized rows keep gender and age for reporting with PII removed.",
    )
}

@Composable
fun ScribeTeamScreen(repo: SessionScribeFakeRepo, onOpenMail: () -> Unit) {
    val branchId = repo.currentBranchId.value
    SectionHeader(index = "T-1", title = "On duty", aside = "home slot order")
    val roster = repo.users.filter { it.clockedBranchId == branchId }.sortedBy { it.slot }
    if (roster.isEmpty()) {
        EmptyScribe("Nobody clocked in here yet.")
    } else {
        roster.forEach { u ->
            ScribeCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ScribeCode("#${u.slot.toString().padStart(2, '0')}")
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "${u.role} · home: ${repo.branchName(u.homeBranchId)}",
                            fontSize = 12.sp,
                            color = ScribeMuted,
                        )
                    }
                    if (u.homeBranchId != branchId) {
                        ScribeTag(
                            text = if (u.reliefEdit) "Relief +edit" else "Relief view-only",
                            color = ScribeTeal,
                            soft = ScribeTealSoft,
                        )
                    } else {
                        ScribeTag(text = "Home", color = ScribeGreen, soft = ScribeGreenSoft)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "T-2", title = "Relief inbox", aside = "tap to decide")
    val inbox = repo.invites.filter { it.direction == "IN" && it.status == SInviteStatus.PENDING }
    if (inbox.isEmpty()) {
        EmptyScribe("Inbox zero. No relief waits on you.", "Open mailbox", onOpenMail)
    } else {
        inbox.forEach { item ->
            ScribeCard(accent = ScribeAmber) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (item.kind == SInviteKind.INVITE) "Relief invite" else "Relief request",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(text = "${item.who} · ${repo.branchName(item.branchId)}", fontSize = 13.sp)
                        ScribeCode(item.day.uppercase())
                    }
                    StatusTag(item.status.name)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    ScribeButton(
                        text = if (item.kind == SInviteKind.INVITE) "Accept duty" else "Grant access",
                        onClick = { repo.decideInvite(item.id, true) },
                    )
                    Spacer(Modifier.width(8.dp))
                    ScribeGhostButton(text = "Decline", onClick = { repo.decideInvite(item.id, false) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    SectionHeader(index = "T-3", title = "Everyone", aside = "roles at a glance")
    repo.users.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ScribeCode(u.role.uppercase().padEnd(12))
            Spacer(Modifier.width(8.dp))
            Text(text = u.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(text = repo.branchName(u.homeBranchId), fontSize = 12.sp, color = ScribeMuted)
        }
        Spacer(Modifier.height(4.dp))
    }
    Spacer(Modifier.height(8.dp))
    val decided = repo.invites.filter { it.status != SInviteStatus.PENDING }
    if (decided.isNotEmpty()) {
        Text(text = "Decided this week", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ScribeMuted)
        Spacer(Modifier.height(4.dp))
        decided.take(4).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ScribeCode("${item.kind} · ${item.who} · ${repo.branchName(item.branchId)}".uppercase())
                Spacer(Modifier.width(8.dp))
                StatusTag(item.status.name)
                if (item.status == SInviteStatus.ACCEPTED || item.status == SInviteStatus.GRANTED) {
                    Spacer(Modifier.width(8.dp))
                    ScribeLink(text = "Revoke", onClick = { repo.revokeInvite(item.id) })
                }
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}
