package com.companyb.companyapp.proto.monthclose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

// #851 — month-close operational flows: sessions, clients, finance, team, mailbox, audit, profile.

@Composable
fun McSessions(repo: MonthCloseFakeRepo) {
    var filter by remember { mutableStateOf("ALL") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidDraft by remember { mutableStateOf("") }
    val day = repo.selectedDay()
    val list = repo.sessions.filter { it.dayId == day.id && (filter == "ALL" || it.status.name == filter) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Sessions · ${day.dateLabel}", "walk-ins never take NO_SHOW / CANCELLED")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
                McNavButton(f, filter == f) { filter = f }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Walk-in rule: a walk-in entry refuses NO_SHOW and CANCELLED — convert to PENDING or void with reason instead.",
            fontSize = 12.sp, color = McTheme.Muted)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            McCard { Text("No entries under this tab for ${day.dateLabel}.", fontSize = 13.sp, color = McTheme.Muted) }
        }
        list.forEach { s ->
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.time} · ${s.clientName}${if (s.walkIn) " · walk-in" else ""}",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
                        Text("${s.type} · P${s.price} · ${s.practitioner}", fontSize = 12.sp, color = McTheme.Muted)
                        if (s.voided) Text("VOIDED · ${s.voidReason?.ifBlank { "(reason missing — blocks close)" } ?: s.voidReason}",
                            fontSize = 12.sp, color = McTheme.SealRed, fontWeight = FontWeight.Bold)
                    }
                    McStatusChip(s.status.name, when (s.status) {
                        McSessionStatus.COMPLETED -> McTheme.SealedGreen
                        McSessionStatus.PENDING -> McTheme.PendingAmber
                        else -> McTheme.SealRed
                    })
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                        Text(if (expanded == s.id) "Close" else "Amend")
                    }
                }
                if (expanded == s.id) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(McSessionStatus.COMPLETED, McSessionStatus.NO_SHOW, McSessionStatus.CANCELLED, McSessionStatus.PENDING).forEach { target ->
                            OutlinedButton(onClick = {
                                if (s.walkIn && (target == McSessionStatus.NO_SHOW || target == McSessionStatus.CANCELLED)) {
                                    repo.log(repo.currentUser.name, "WALKIN_RULE_REFUSED", "${s.id} → $target")
                                    return@OutlinedButton
                                }
                                val idx = repo.sessions.indexOfFirst { it.id == s.id }
                                repo.sessions[idx] = s.copy(status = target)
                                repo.log(repo.currentUser.name, "SESSION_$target", "${s.id} · ${s.clientName}")
                            }) { Text(target.name) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (!s.voided) {
                        TextField(value = voidDraft, onValueChange = { voidDraft = it },
                            label = { Text("Void reason (required)") }, singleLine = true)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val idx = repo.sessions.indexOfFirst { it.id == s.id }
                                repo.sessions[idx] = s.copy(voided = true, voidReason = voidDraft)
                                repo.log(repo.currentUser.name, "VOID", s.id, voidDraft.ifBlank { null })
                                voidDraft = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = McTheme.SealRed),
                        ) { Text("Void with reason") }
                    } else {
                        OutlinedButton(onClick = {
                            val idx = repo.sessions.indexOfFirst { it.id == s.id }
                            repo.sessions[idx] = s.copy(voided = false, voidReason = null)
                            repo.log(repo.currentUser.name, "UNVOID", s.id)
                        }) { Text("Unvoid") }
                    }
                    if (s.walkIn) Text("Note: walk-in entries refuse NO_SHOW / CANCELLED per house rule.",
                        fontSize = 12.sp, color = McTheme.PendingAmber)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun McClients(repo: MonthCloseFakeRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Clients · global register", "at most one PENDING per client")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anonymized view", fontSize = 13.sp, color = McTheme.Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                repo.anonymized = !repo.anonymized
                repo.log(repo.currentUser.name, if (repo.anonymized) "CLIENTS_VEILED" else "CLIENTS_UNVEILED", "register")
            }) { Text(if (repo.anonymized) "Unveil" else "Veil") }
        }
        Spacer(Modifier.height(6.dp))
        Text("House rule: a client holds at most one PENDING session at a time. Over-rule flags are highlighted.",
            fontSize = 12.sp, color = McTheme.Muted)
        Spacer(Modifier.height(6.dp))
        repo.clients.forEach { c ->
            val pending = repo.pendingCountFor(c.id)
            val over = pending > 1
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (repo.anonymized) "Client ${c.id.uppercase()} · ${c.gender}${c.age}" else "${c.name} · ${c.gender}${c.age}",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
                        Text("$pending PENDING · ${repo.sessions.count { it.clientId == c.id && !it.voided }} kept entries",
                            fontSize = 12.sp, color = if (over) McTheme.SealRed else McTheme.Muted)
                        if (over) Text("OVER-RULE: more than one PENDING — resolve before close.",
                            fontSize = 12.sp, color = McTheme.SealRed, fontWeight = FontWeight.Bold)
                    }
                    McStatusChip(if (over) "OVER" else "OK", if (over) McTheme.SealRed else McTheme.SealedGreen)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun McFinance(repo: MonthCloseFakeRepo) {
    val day = repo.selectedDay()
    var undoDraft by remember { mutableStateOf("") }
    var undoFor by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Finance · ${day.dateLabel}", "draft → submit → snapshot → undo 48h")
        Text("Commission split note: session revenue pools per branch day and splits evenly over clocked-in hands at sold_at.",
            fontSize = 12.sp, color = McTheme.Muted)
        Spacer(Modifier.height(6.dp))
        McRemitKind.entries.forEach { kind ->
            val r = repo.remittances.firstOrNull { it.dayId == day.id && it.kind == kind }
                ?: McRemittance(day.id, kind, McRemitState.DRAFT, draftTotal = if (kind == McRemitKind.SESSION) 2700 else 900)
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${kind.name} folio", fontWeight = FontWeight.Black, fontSize = 14.sp, color = McTheme.Ink)
                        if (r.state == McRemitState.SUBMITTED) {
                            Text("Sealed ${r.snapshotId} · P${r.snapshotTotal}", fontSize = 13.sp, color = McTheme.SealedGreen, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Draft P${r.draftTotal} · unsealed", fontSize = 13.sp, color = McTheme.PendingAmber, fontWeight = FontWeight.Bold)
                        }
                    }
                    McStatusChip(r.state.name, if (r.state == McRemitState.SUBMITTED) McTheme.SealedGreen else McTheme.PendingAmber)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.state == McRemitState.DRAFT) {
                        Button(
                            onClick = {
                                val idx = repo.remittances.indexOfFirst { it.dayId == day.id && it.kind == kind }
                                val snap = "MC-${day.dateLabel.filter { it.isDigit() }.takeLast(4).padStart(4, '0')}-${kind.name.first()}"
                                val sealed = r.copy(state = McRemitState.SUBMITTED, snapshotId = snap, snapshotTotal = r.draftTotal)
                                if (idx >= 0) repo.remittances[idx] = sealed else repo.remittances.add(sealed)
                                repo.log(repo.currentUser.name, "SNAPSHOT_SEALED", "$snap · P${r.draftTotal}")
                                if (repo.remittances.count { it.dayId == day.id && it.state == McRemitState.SUBMITTED } == 2) {
                                    repo.markDayRemitted(day.id)
                                    repo.log(repo.currentUser.name, "DAY_REMITTED", "${day.dateLabel} · ${repo.currentBranch.name}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = McTheme.SealRed),
                        ) { Text("Submit & seal snapshot") }
                    } else {
                        OutlinedButton(onClick = { undoFor = if (undoFor == kind.name) null else kind.name }) {
                            Text("Undo (48h)")
                        }
                    }
                }
                if (undoFor == kind.name && r.state == McRemitState.SUBMITTED) {
                    Spacer(Modifier.height(6.dp))
                    Text("Undo window: 48h from seal, reason required, writes audit.", fontSize = 12.sp, color = McTheme.Muted)
                    TextField(value = undoDraft, onValueChange = { undoDraft = it },
                        label = { Text("Undo reason (required)") }, singleLine = true)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (undoDraft.isBlank()) return@Button
                            val idx = repo.remittances.indexOfFirst { it.dayId == day.id && it.kind == kind }
                            if (idx >= 0) repo.remittances[idx] = r.copy(state = McRemitState.DRAFT, undoReason = undoDraft)
                            repo.log(repo.currentUser.name, "SNAPSHOT_UNDO", r.snapshotId ?: kind.name, undoDraft)
                            undoDraft = ""
                            undoFor = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = McTheme.PendingAmber),
                    ) { Text("Confirm undo") }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        McSectionTitle("Snapshot archive", "${repo.snapshots().size} sealed folios")
        repo.snapshots().forEach { s ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(McTheme.Kraft)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.snapshotId ?: s.kind.name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = McTheme.Ink)
                    Text("${s.dayId} · ${s.kind} · P${s.snapshotTotal}", fontSize = 12.sp, color = McTheme.Muted)
                }
                McStatusChip("FILED", McTheme.Ink)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun McTeam(repo: MonthCloseFakeRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Team · roster by home house", "roles shape what seals")
        repo.branches.forEach { b ->
            Text(b.name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = McTheme.Ink)
            Spacer(Modifier.height(4.dp))
            repo.users.filter { it.homeBranchId == b.id }.forEach { u ->
                McCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
                            Text(u.role.name + when (u.role) {
                                McRole.ONBOARDING -> " · locked, zero capabilities"
                                McRole.PRACTITIONER -> " · runs sessions, clock-in"
                                McRole.COORDINATOR -> " · keeps the day folio, covers relief"
                                McRole.MANAGER -> " · seals days, grants roles"
                                McRole.ACCOUNTANT -> " · seals snapshots, reads archive"
                            }, fontSize = 12.sp, color = McTheme.Muted)
                        }
                        McStatusChip(u.role.name, McTheme.Ink)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun McMailbox(repo: MonthCloseFakeRepo) {
    val unread = repo.notifications.count { !it.read }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Mailbox", "$unread unread")
        Row {
            OutlinedButton(onClick = {
                repo.notifications.replaceAll { it.copy(read = true) }
                repo.log(repo.currentUser.name, "MAILBOX_READ_ALL", "$unread notes")
            }) { Text("Read all") }
        }
        Spacer(Modifier.height(6.dp))
        repo.notifications.forEach { n ->
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = if (n.read) McTheme.Muted else McTheme.Ink)
                        Text(n.body, fontSize = 12.sp, color = McTheme.Muted)
                    }
                    if (!n.read) {
                        OutlinedButton(onClick = {
                            val idx = repo.notifications.indexOfFirst { it.id == n.id }
                            repo.notifications[idx] = n.copy(read = true)
                            repo.log(repo.currentUser.name, "MAILBOX_READ", n.id)
                        }) { Text("Read") }
                    } else {
                        McStatusChip("READ", McTheme.Muted)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun McAuditList(repo: MonthCloseFakeRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Audit log", "${repo.audits.size} entries · newest first")
        repo.audits.forEach { a ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(McTheme.Card).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${a.who} · ${a.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
                    Text("${a.target}${a.reason?.let { " · reason: $it" } ?: ""}", fontSize = 12.sp, color = McTheme.Muted)
                }
                Text(a.whenLabel, fontSize = 11.sp, color = McTheme.Muted,
                    modifier = Modifier.clickable {}.padding(4.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun McProfile(repo: MonthCloseFakeRepo, go: (McScreen) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Profile", "hand + duty")
        McCard {
            Text(repo.currentUser.name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = McTheme.Ink)
            Text("${repo.currentUser.role} · home ${repo.branches.firstOrNull { it.id == repo.currentUser.homeBranchId }?.name}",
                fontSize = 13.sp, color = McTheme.Muted)
            Text(if (repo.clockedIn) "On duty · ${repo.currentBranch.name}" else "Off duty",
                fontSize = 13.sp, color = if (repo.clockedIn) McTheme.SealedGreen else McTheme.PendingAmber,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    OutlinedButton(onClick = {
                        repo.clockedIn = false
                        repo.log(repo.currentUser.name, "CLOCK_OUT", repo.currentBranch.name)
                    }) { Text("Clock out") }
                }
                Button(
                    onClick = {
                        repo.log(repo.currentUser.name, "LOGOUT", repo.currentBranch.name)
                        go(McScreen.LOGIN)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = McTheme.Ink),
                ) { Text("Logout") }
            }
        }
    }
}
