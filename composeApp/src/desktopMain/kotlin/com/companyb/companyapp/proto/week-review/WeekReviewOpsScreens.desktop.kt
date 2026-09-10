package com.companyb.companyapp.proto.weekreview

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #850 — sessions ledger, client file, team roster, mailbox, audit log. Fake data only.

private fun statusTint(status: WrSessionStatus) =
    when (status) {
        WrSessionStatus.PENDING -> WrColors.Amber
        WrSessionStatus.COMPLETED -> WrColors.Moss
        WrSessionStatus.NO_SHOW -> WrColors.StampRed
        WrSessionStatus.CANCELLED -> WrColors.Slate
    }

@Composable
fun WrSessions(repo: WeekReviewFakeRepo) {
    var filter by remember { mutableStateOf<WrSessionStatus?>(null) }
    var manage by remember { mutableStateOf<WrSession?>(null) }
    var voiding by remember { mutableStateOf<WrSession?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var voidError by remember { mutableStateOf<String?>(null) }
    var addingWalkIn by remember { mutableStateOf(false) }
    var walkService by remember { mutableStateOf("") }
    var walkError by remember { mutableStateOf<String?>(null) }
    var manageError by remember { mutableStateOf<String?>(null) }

    val locked = repo.currentUser?.role == WrRole.ONBOARDING || repo.currentUser == null
    val visible = repo.sessions.filter { filter == null || it.status == filter }

    Column {
        SectionFlag("Sessions ledger · Mon–Fri")
        Spacer(Modifier.height(8.dp))
        if (locked) {
            NoteCard("ONBOARDING accounts are stopped at the door: sign in with a granted role to work sessions.")
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip("All", filter == null) { filter = null }
            WrSessionStatus.entries.forEach { s ->
                FilterChip(s.label, filter == s) { filter = s }
            }
        }
        Spacer(Modifier.height(8.dp))
        visible.forEach { s ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(4.dp)).background(WrColors.Paper).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.id} · ${s.service}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = WrColors.Ink)
                        Text(
                            "${s.day} · ${s.client} · ${s.practitioner} · ${s.branch}",
                            fontSize = 12.sp,
                            color = WrColors.Muted,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    Text(peso(s.amount), fontWeight = FontWeight.Black, fontSize = 14.sp, color = WrColors.Ink)
                    Spacer(Modifier.padding(4.dp))
                    StatusStamp(s.status.label, statusTint(s.status))
                    if (s.walkIn) {
                        Spacer(Modifier.padding(2.dp))
                        WashPill("walk-in", WrColors.Slate, WrColors.SlateWash)
                    }
                    if (s.voided) {
                        Spacer(Modifier.padding(2.dp))
                        StatusStamp("void", WrColors.StampRed)
                    }
                }
                if (s.voided) {
                    Text("Void: ${s.voidReason ?: "—"}", fontSize = 12.sp, color = WrColors.StampRed, fontStyle = FontStyle.Italic)
                }
                if (!locked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Manage",
                            color = WrColors.Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { manage = s; manageError = null }.padding(vertical = 4.dp),
                        )
                        if (s.voided) {
                            Text(
                                "Unvoid",
                                color = WrColors.Moss,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { repo.setVoid(s.id, false, "") }.padding(vertical = 4.dp),
                            )
                        } else {
                            Text(
                                "Void with reason",
                                color = WrColors.StampRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { voiding = s; voidReason = ""; voidError = null }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        NoteCard("House rule: walk-in sessions cannot be NO_SHOW or CANCELLED — a guest already in the chair is served, never stood up.")
        if (!locked) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { addingWalkIn = true; walkService = ""; walkError = null },
                colors = ButtonDefaults.buttonColors(containerColor = WrColors.Ink),
            ) {
                Text("Book Friday walk-in")
            }
        }
    }

    if (manage != null && !locked) {
        val target = repo.sessions.firstOrNull { it.id == manage!!.id } ?: manage!!
        AlertDialog(
            onDismissRequest = { manage = null },
            title = { Text("${target.id} · ${target.service}") },
            text = {
                Column {
                    Text("PENDING sessions move to one terminal state. Completed entries feed the till and the wins column.", fontSize = 13.sp)
                    if (manageError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(manageError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WrSessionStatus.entries.filter { it != WrSessionStatus.PENDING }.forEach { next ->
                        TextButton(
                            onClick = {
                                val err = repo.setSessionStatus(target.id, next)
                                if (err == null) manage = null else manageError = err
                            },
                        ) {
                            Text(next.label)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { manage = null }) { Text("Close") }
            },
        )
    }

    if (voiding != null) {
        AlertDialog(
            onDismissRequest = { voiding = null },
            title = { Text("Void ${voiding!!.id}?") },
            text = {
                Column {
                    Text("Voiding keeps the row for the audit trail but strikes it from the till. A reason is required.", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    TextField(value = voidReason, onValueChange = { voidReason = it }, label = { Text("Void reason") }, singleLine = true)
                    if (voidError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(voidError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val err = repo.setVoid(voiding!!.id, true, voidReason)
                        if (err == null) voiding = null else voidError = err
                    },
                ) {
                    Text("Void", color = WrColors.StampRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { voiding = null }) { Text("Cancel") }
            },
        )
    }

    if (addingWalkIn) {
        AlertDialog(
            onDismissRequest = { addingWalkIn = false },
            title = { Text("Friday walk-in") },
            text = {
                Column {
                    Text("Filed under ${repo.branch.name} · Fri. Walk-ins start PENDING and can only complete.", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    TextField(value = walkService, onValueChange = { walkService = it }, label = { Text("Service") }, singleLine = true)
                    if (walkError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(walkError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val err = repo.addWalkIn(walkService, repo.currentUser?.name ?: "kiosk")
                        if (err == null) addingWalkIn = false else walkError = err
                    },
                ) {
                    Text("Book")
                }
            },
            dismissButton = {
                TextButton(onClick = { addingWalkIn = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    onPick: () -> Unit,
) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (active) WrColors.Ink else WrColors.Paper)
            .clickable { onPick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = if (active) androidx.compose.ui.graphics.Color.White else WrColors.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun WrClients(repo: WeekReviewFakeRepo) {
    Column {
        SectionFlag("Client file · global")
        Spacer(Modifier.height(8.dp))
        NoteCard(
            "Clients are global across branches: any desk sees every file. " +
                "House rule: a client holds at most one PENDING session — the pending count below is the guard.",
        )
        Spacer(Modifier.height(8.dp))
        repo.clients.forEach { c ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(4.dp)).background(WrColors.Paper).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.anonymized) "${c.id} · masked" else "${c.id} · ${c.name}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = WrColors.Ink,
                    )
                    Text(
                        if (c.anonymized) {
                            "Anonymized view: gender ${c.gender} · age ${c.age} · home ${c.homeBranch}"
                        } else {
                            "Gender ${c.gender} · age ${c.age} · home ${c.homeBranch}"
                        },
                        fontSize = 12.sp,
                        color = WrColors.Muted,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (c.pendingCount > 0) WashPill("${c.pendingCount} pending", WrColors.Amber, WrColors.AmberWash)
                Spacer(Modifier.padding(3.dp))
                Text(
                    if (c.anonymized) "Reveal" else "Anonymize",
                    color = WrColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { repo.anonymize(c.id) }.padding(4.dp),
                )
            }
        }
    }
}

@Composable
fun WrTeam(repo: WeekReviewFakeRepo) {
    var asking by remember { mutableStateOf(false) }
    var askShift by remember { mutableStateOf("") }
    var askError by remember { mutableStateOf<String?>(null) }
    val isManager = repo.currentUser?.role == WrRole.MANAGER

    Column {
        SectionFlag("Roster & relief")
        Spacer(Modifier.height(8.dp))
        WrRole.entries.forEach { role ->
            val members = repo.users.filter { it.role == role }
            if (members.isNotEmpty()) {
                Text(role.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = WrColors.Accent)
                Spacer(Modifier.height(4.dp))
                members.forEach { user ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(4.dp)).background(WrColors.Paper).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WrColors.Ink)
                            Text(repo.capabilitiesOf(user.role), fontSize = 12.sp, color = WrColors.Muted)
                        }
                        if (isManager && user.role == WrRole.ONBOARDING) {
                            Text(
                                "Grant Practitioner",
                                color = WrColors.Moss,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { repo.grantRole(user.id, WrRole.PRACTITIONER) }.padding(4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        if (!isManager) {
            NoteCard("Only a signed-in Manager sees the grant control. Sign in as Mia Santos to unlock it.")
            Spacer(Modifier.height(8.dp))
        }
        SectionFlag("Cover asks")
        Spacer(Modifier.height(8.dp))
        repo.reliefAsks.forEach { ask ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(3.dp)).background(WrColors.Paper).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${ask.by}${if (ask.mine) " (you)" else ""}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WrColors.Ink)
                    Text(ask.shift, fontSize = 12.sp, color = WrColors.Muted)
                }
                if (ask.state == "PENDING") {
                    Text("Grant", color = WrColors.Moss, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { repo.answerAsk(ask.id, true) }.padding(4.dp))
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        if (ask.mine) "Withdraw" else "Deny",
                        color = WrColors.StampRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.answerAsk(ask.id, false) }.padding(4.dp),
                    )
                } else {
                    StatusStamp(ask.state, if (ask.state == "GRANTED") WrColors.Moss else WrColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { asking = true; askShift = ""; askError = null },
            colors = ButtonDefaults.buttonColors(containerColor = WrColors.Ink),
        ) {
            Text("Raise a cover ask")
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Cover ask") },
            text = {
                Column {
                    Text("Posted to the relief wire under your name.", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    TextField(value = askShift, onValueChange = { askShift = it }, label = { Text("Shift, e.g. Sat Sep 12 · morning") }, singleLine = true)
                    if (askError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(askError!!, color = WrColors.StampRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val err = repo.raiseAsk(askShift)
                        if (err == null) asking = false else askError = err
                    },
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun WrMailbox(repo: WeekReviewFakeRepo) {
    val unread = repo.notices.count { !it.read }
    Column {
        SectionFlag("Mailbox · $unread unread")
        Spacer(Modifier.height(8.dp))
        if (unread > 0) {
            Row {
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { repo.markAllRead() }) {
                    Text("Mark all read", color = WrColors.Ink)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        repo.notices.forEach { n ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (n.read) WrColors.Paper else WrColors.GoldWash)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.padding(end = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (n.read) WrColors.Line else WrColors.Accent)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Text(if (n.read) "✓" else "●", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Black, fontSize = 14.sp, color = WrColors.Ink)
                    Text(n.body, fontSize = 12.sp, color = WrColors.Muted)
                }
                if (!n.read) {
                    Text(
                        "Mark read",
                        color = WrColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.markNotice(n.id, true) }.padding(4.dp),
                    )
                } else {
                    Text(
                        "Unread",
                        color = WrColors.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.markNotice(n.id, false) }.padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun WrAuditLog(repo: WeekReviewFakeRepo) {
    Column {
        SectionFlag("Audit log · every stroke recorded")
        Spacer(Modifier.height(8.dp))
        NoteCard("Every mutation in this prototype — status, void, submit, payout, seed, relief — prepends an entry with who, action, target and reason.")
        Spacer(Modifier.height(8.dp))
        repo.ledger.forEach { a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(3.dp)).background(WrColors.Paper).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${a.seq}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = WrColors.Accent, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${a.action} · ${a.target}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WrColors.Ink)
                    Text(
                        "${a.who} · ${a.stamp}${if (a.reason != null) " · reason: ${a.reason}" else ""}",
                        fontSize = 12.sp,
                        color = WrColors.Muted,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}
