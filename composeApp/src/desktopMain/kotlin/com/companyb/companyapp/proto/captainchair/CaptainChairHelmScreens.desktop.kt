package com.companyb.companyapp.proto.captainchair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

// #825 — helm home (crew / relief / exceptions / handover at a glance),
// handover desk, sessions, clients. All fake-data flows clickable.

@Composable
fun CcHelmScreen(repo: CaptainRepo, go: (CcDest) -> Unit) {
    var note by remember { mutableStateOf("") }
    var carryRelief by remember { mutableStateOf(true) }
    val openExceptions = repo.exceptions.count { !it.cleared }
    val pendingCount = repo.sessions.count { it.branchId == repo.currentBranchId && it.status == CcSessionStatus.PENDING }
    val unread = repo.notices.count { !it.read }
    val drafts = repo.remittances.count { it.stage == "DRAFT" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Helm — ${repo.currentBranch()?.name ?: "—"} at a glance")
        CcNote("Four spokes from the chair: crew on watch, relief posture, exceptions needing the lead, and the standing handover.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcStationHead("STARBOARD", "SPOKE 1 · CREW", "${repo.users.size - 1} hands")
                        repo.users.filter { !it.locked }.forEach { u ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CcLamp(if (u.homeBranchId == repo.currentBranchId) CcColors.Starboard else CcColors.Faded)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(u.name, color = CcColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${u.role} · station ${u.station}", color = CcColors.Faded, fontSize = 11.sp)
                                }
                            }
                        }
                        CcSpoke()
                        CcNote(if (repo.clockedIn) "You are ON WATCH at ${repo.currentBranch()?.name}." else "You are OFF WATCH — handover desk keeps your note.")
                    }
                }
                CcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcStationHead("PORT", "SPOKE 2 · RELIEF", repo.reliefAccess.name)
                        repo.relief.take(3).forEach { r ->
                            Text("${r.kind} · ${r.who} · ${r.branchName} · ${r.day} — ${r.state}", color = CcColors.Paper, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CcGhost("Relief desk") { go(CcDest.TEAM) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcStationHead("PORT", "SPOKE 3 · EXCEPTIONS", "$openExceptions open")
                        if (openExceptions == 0) {
                            CcNote("Clear water — every exception acknowledged.")
                        } else {
                            repo.exceptions.filter { !it.cleared }.forEach { x ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    CcLamp(CcColors.Port)
                                    Spacer(Modifier.width(8.dp))
                                    Text(x.text, color = CcColors.Paper, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                }
                                Row {
                                    Spacer(Modifier.weight(1f))
                                    CcLink("Acknowledge") {
                                        x.cleared = true
                                        repo.log("ACK exception ${x.id} from helm")
                                    }
                                }
                            }
                        }
                        CcNote("$pendingCount PENDING sessions · $drafts remittance drafts · $unread unread mail.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CcGhost("Sessions") { go(CcDest.SESSIONS) }
                            CcGhost("Finance") { go(CcDest.FINANCE) }
                        }
                    }
                }
                CcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcStationHead("STARBOARD", "SPOKE 4 · HANDOVER", "${repo.handovers.size} logged")
                        val latest = repo.handovers.firstOrNull()
                        if (latest != null) {
                            Text(
                                "${latest.fromWatch} → ${latest.toWatch}",
                                color = CcColors.Brass,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(latest.note, color = CcColors.Paper, fontSize = 13.sp, lineHeight = 19.sp)
                            CcNote("Carried ${latest.carriedCount} · ${latest.reliefContext}")
                        }
                        if (repo.clockedIn) {
                            TextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Quick handover note from the chair") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = carryRelief,
                                    onCheckedChange = { carryRelief = it },
                                    colors = CheckboxDefaults.colors(checkedColor = CcColors.Brass),
                                )
                                Text("Carry relief context", color = CcColors.Paper, fontSize = 13.sp)
                            }
                            CcPrimary("Clock out + log handover") {
                                repo.clockOut(note, carryRelief)
                                note = ""
                            }
                        } else {
                            CcNote("Off watch — clock back in from Profile to resume the helm.")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CcGhost("Handover log") { go(CcDest.HANDOVER) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CcHandoverScreen(repo: CaptainRepo) {
    var note by remember { mutableStateOf("") }
    var carryRelief by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Handover log")
        CcNote("Clock-out writes a handover: the note, carried open-item count, and relief context sail to the next watch and the mailbox.")
        if (repo.clockedIn) {
            CcCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CcStation("WRITE HANDOVER + CLOCK OUT")
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note for the next watch") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = carryRelief,
                            onCheckedChange = { carryRelief = it },
                            colors = CheckboxDefaults.colors(checkedColor = CcColors.Brass),
                        )
                        Text("Carry relief context into the handover", color = CcColors.Paper, fontSize = 13.sp)
                    }
                    CcPrimary("Clock out + log handover") {
                        repo.clockOut(note, carryRelief)
                        note = ""
                    }
                }
            }
        } else {
            CcNote("Off watch. Profile → Clock back in to write the next handover.")
        }
        repo.handovers.forEach { h ->
            CcRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${h.id} · ${h.branchName} · ${h.dayLabel}", color = CcColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        CcChip(if (h.acked) "ACKED" else "OPEN", if (h.acked) CcColors.Starboard else CcColors.Brass)
                    }
                    Text("${h.fromWatch} → ${h.toWatch}", color = CcColors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(h.note, color = CcColors.Paper, fontSize = 13.sp, lineHeight = 19.sp)
                    CcNote("Carried ${h.carriedCount} items · ${h.reliefContext}")
                    if (!h.acked) {
                        CcGhost("Acknowledge") {
                            h.acked = true
                            repo.log("ACK handover ${h.id}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CcSessionsScreen(repo: CaptainRepo) {
    var clientFilter by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("") }
    var bookName by remember { mutableStateOf("") }
    val list = repo.sessions.filter { it.branchId == repo.currentBranchId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Sessions")
        CcNote("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-ins never take NO_SHOW or CANCELLED — complete or keep pending. Void needs a reason; unvoid restores.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcChip("PENDING ${list.count { it.status == CcSessionStatus.PENDING }}", CcColors.Brass)
            CcChip("COMPLETED ${list.count { it.status == CcSessionStatus.COMPLETED }}", CcColors.Starboard)
            CcChip("VOIDED ${list.count { it.voided }}", CcColors.Port)
        }
        TextField(
            value = clientFilter,
            onValueChange = { clientFilter = it },
            label = { Text("Filter by client (fake)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        list.filter { clientFilter.isBlank() || it.clientName.contains(clientFilter, ignoreCase = true) }.forEach { s ->
            CcRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.time} · ${s.clientName} · ${s.kind} · ₱${s.price}", color = CcColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        CcChip(s.status.name + if (s.voided) " · VOID" else "", s.status.seal())
                    }
                    CcNote("${s.practitioners}" + if (s.walkIn) " · walk-in (NO_SHOW/CANCELLED barred)" else "")
                    if (s.voided) {
                        CcNote("Voided: ${s.voidReason}")
                        CcGhost("Unvoid") {
                            s.voided = false
                            s.voidReason = ""
                            repo.log("UNVOID session ${s.id}")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.status == CcSessionStatus.PENDING) {
                                CcPrimary("Complete") {
                                    s.status = CcSessionStatus.COMPLETED
                                    repo.log("COMPLETE session ${s.id}")
                                }
                                if (!s.walkIn) {
                                    CcGhost("No-show") {
                                        s.status = CcSessionStatus.NO_SHOW
                                        repo.log("NO_SHOW session ${s.id}")
                                    }
                                    CcGhost("Cancel") {
                                        s.status = CcSessionStatus.CANCELLED
                                        repo.log("CANCEL session ${s.id}")
                                    }
                                }
                            } else {
                                CcGhost("Reopen to PENDING") {
                                    s.status = CcSessionStatus.PENDING
                                    repo.log("REOPEN session ${s.id} to PENDING")
                                }
                            }
                        }
                        TextField(
                            value = voidReason,
                            onValueChange = { voidReason = it },
                            label = { Text("Void reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        CcGhost("Void with reason") {
                            if (voidReason.isNotBlank()) {
                                s.voided = true
                                s.voidReason = voidReason
                                repo.log("VOID session ${s.id} reason: $voidReason")
                                voidReason = ""
                            }
                        }
                    }
                }
            }
        }
        CcCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CcStation("BOOK NEW PENDING SESSION")
                TextField(
                    value = bookName,
                    onValueChange = { bookName = it },
                    label = { Text("Client name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CcPrimary("Book PENDING") {
                    if (bookName.isNotBlank()) {
                        val id = "s-%02d".format(repo.sessions.size + 1)
                        repo.sessions.add(CcSession(id, "16:30", bookName, repo.currentBranchId, "Follow-up", false, CcSessionStatus.PENDING, 1200, repo.currentUser?.name ?: "—"))
                        repo.log("BOOK session $id for $bookName (PENDING)")
                        bookName = ""
                    }
                }
            }
        }
    }
}

@Composable
fun CcClientsScreen(repo: CaptainRepo) {
    var query by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Clients — global registry")
        CcNote("Clients are global across branches. At most one PENDING session per client. Anonymized rows keep gender + age only.")
        TextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search clients (fake)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        repo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.forEach { c ->
            CcRowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CcLamp(if (c.anonymized) CcColors.Lantern else CcColors.Brass)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = CcColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (c.anonymized) "Anonymized · ${c.gender} · age ${c.age}" else c.contact,
                            color = CcColors.Faded,
                            fontSize = 12.sp,
                        )
                    }
                    CcChip(
                        if (c.pendingCount > 0) "1 PENDING" else "NO PENDING",
                        if (c.pendingCount > 0) CcColors.Brass else CcColors.Starboard,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        CcCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CcStation("REGISTER NEW CLIENT")
                TextField(
                    value = registerName,
                    onValueChange = { registerName = it },
                    label = { Text("Full name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                CcPrimary("Register") {
                    if (registerName.isNotBlank()) {
                        val id = "c-%02d".format(repo.clients.size + 1)
                        repo.clients.add(CcClient(id, registerName, "09XX-XXX-XXXX", 0))
                        repo.log("REGISTER client $id ($registerName)")
                        registerName = ""
                    }
                }
            }
        }
    }
}
