package com.companyb.companyapp.proto.sessionscribe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun ScribeTodayScreen(repo: SessionScribeFakeRepo, onOpenSessions: () -> Unit) {
    val me = repo.currentUser()
    val branchId = repo.currentBranchId.value
    val pending = repo.pendingFor(branchId)
    val doneToday = repo.sessions.count { it.branchId == branchId && it.status == SSessionStatus.COMPLETED }

    SectionHeader(index = "T-1", title = "Your queue today", aside = "tap once to finish")
    Row(modifier = Modifier.fillMaxWidth()) {
        StatCell(label = "Pending now", value = "$pending")
        Spacer(Modifier.width(8.dp))
        StatCell(label = "Completed", value = "$doneToday")
        Spacer(Modifier.width(8.dp))
        StatCell(label = "Clocked in", value = "${repo.users.count { it.clockedBranchId == branchId }}")
    }
    Spacer(Modifier.height(12.dp))

    if (pending.isEmpty()) {
        EmptyScribe("Queue clear. Log the next walk-in in one tap.", "Open sessions", onOpenSessions)
    } else {
        pending.take(4).forEach { s ->
            var note by remember(s.id) { mutableStateOf("") }
            ScribeCard(accent = ScribeAmber) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${s.time} · ${s.clientName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        ScribeCode("${s.id.uppercase()} · ${s.type.uppercase()} · P${s.price}${if (s.walkIn) " · WALK-IN" else ""}")
                    }
                    StatusTag(s.status.name)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    ScribeButton(text = "Done", onClick = { note = repo.transition(s.id, SSessionStatus.COMPLETED) ?: "" })
                    Spacer(Modifier.width(8.dp))
                    ScribeGhostButton(text = "No-show", onClick = { note = repo.transition(s.id, SSessionStatus.NO_SHOW) ?: "" })
                }
                if (note.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = note, fontSize = 12.sp, color = ScribeRed)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    FieldNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — the scribe blocks it and says why.")
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "T-2", title = "Clock + relief", aside = "home vs relief duty")
    ScribeCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                val clockedId = me.clockedBranchId
                Text(
                    text = if (clockedId == null) "You are off duty" else "On duty at ${repo.branchName(clockedId)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                ScribeCode(
                    if (me.homeBranchId == branchId) {
                        "HOME BRANCH · FULL ACCESS"
                    } else {
                        "RELIEF DUTY · VIEW-ONLY UNTIL GRANT"
                    },
                )
            }
            if (me.clockedBranchId == null) {
                ScribeButton(text = "Clock in here", onClick = { repo.clockIn(branchId) })
            } else {
                ScribeGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    var inviteName by remember { mutableStateOf("Iko T.") }
    ScribeCard {
        Text(text = "Need cover? Invite or ask in one tap.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inviteName,
                onValueChange = { inviteName = it },
                label = { Text("Practitioner name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            ScribeButton(text = "Invite", onClick = { repo.sendInvite(inviteName, branchId) })
        }
        Spacer(Modifier.height(8.dp))
        Row {
            ScribeGhostButton(text = "Request relief here", onClick = { repo.broadcastRequest(branchId) })
        }
    }
    Spacer(Modifier.height(6.dp))
    FieldNote(
        "Relief duty starts view-only; a broadcast request any branch member approves, or a branch invite " +
            "the invitee accepts, grants edit. Grants expire 04:00 Manila next day; pay comes from this drawer.",
    )
}

@Composable
fun ScribeSessionsScreen(repo: SessionScribeFakeRepo) {
    val branchId = repo.currentBranchId.value
    var filter by remember { mutableStateOf("ALL") }
    var quickClient by remember { mutableStateOf("c-02") }
    var quickError by remember { mutableStateOf("") }
    var detailId by remember { mutableStateOf<String?>(null) }

    SectionHeader(index = "S-1", title = "Quick log", aside = "smart defaults from history")
    ScribeCard(accent = ScribeTeal) {
        Text(text = "Pick a client — type and price fill themselves.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            repo.clients.take(4).forEach { c ->
                val picked = quickClient == c.id
                if (picked) {
                    ScribeButton(text = c.name, onClick = { quickClient = c.id })
                } else {
                    ScribeGhostButton(text = c.name, onClick = { quickClient = c.id })
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val picked = repo.clients.firstOrNull { it.id == quickClient }
        if (picked != null) {
            ScribeCode(
                "DEFAULTS · ${picked.lastType.uppercase()} · P${picked.lastPrice} · ${picked.visits} VISITS" +
                    if (picked.hasPending) " · HAS PENDING" else " · CLEAR TO LOG",
            )
            Spacer(Modifier.height(8.dp))
            Row {
                ScribeButton(
                    text = "Log session now",
                    onClick = { quickError = repo.quickLog(quickClient, branchId) ?: "" },
                )
            }
            if (quickError.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(text = quickError, fontSize = 12.sp, color = ScribeRed)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    FieldNote("Each client holds at most one PENDING session — the scribe refuses a second and points at the live one.")
    Spacer(Modifier.height(16.dp))

    SectionHeader(index = "S-2", title = "Session ledger", aside = "void keeps the record")
    Row {
        listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
            if (filter == f) {
                ScribeButton(text = f, onClick = { filter = f })
            } else {
                ScribeGhostButton(text = f, onClick = { filter = f })
            }
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    val rows = repo.sessions.filter { it.branchId == branchId && (filter == "ALL" || it.status.name == filter) }
    if (rows.isEmpty()) {
        EmptyScribe("Nothing under $filter at this branch today.")
    } else {
        rows.forEach { s ->
            ScribeCard(accent = if (s.voided) ScribeRed else null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${s.time} · ${s.clientName}${if (s.voided) " · VOIDED" else ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        ScribeCode(
                            "${s.id.uppercase()} · ${s.type.uppercase()} · P${s.price}" +
                                if (s.walkIn) " · WALK-IN" else "",
                        )
                        if (s.voided) {
                            Text(text = "Void: ${s.voidReason}", fontSize = 12.sp, color = ScribeRed)
                        }
                    }
                    StatusTag(s.status.name)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    ScribeLink(text = if (detailId == s.id) "Hide detail" else "Open detail", onClick = {
                        detailId = if (detailId == s.id) null else s.id
                    })
                }
                if (detailId == s.id) {
                    ScribeSessionDetail(repo = repo, sessionId = s.id)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScribeSessionDetail(repo: SessionScribeFakeRepo, sessionId: String) {
    val s = repo.sessions.firstOrNull { it.id == sessionId } ?: return
    var voidReason by remember(s.id, s.voided) { mutableStateOf("") }
    var message by remember(s.id) { mutableStateOf("") }
    Spacer(Modifier.height(8.dp))
    ScribeCode("DETAIL · ${s.id.uppercase()} · CLIENT ${s.clientId.uppercase()}")
    Spacer(Modifier.height(6.dp))
    Row {
        ScribeButton(text = "Complete", onClick = { message = repo.transition(s.id, SSessionStatus.COMPLETED) ?: "Marked COMPLETED." })
        Spacer(Modifier.width(6.dp))
        ScribeGhostButton(text = "No-show", onClick = { message = repo.transition(s.id, SSessionStatus.NO_SHOW) ?: "Marked NO_SHOW." })
        Spacer(Modifier.width(6.dp))
        ScribeGhostButton(text = "Cancel", onClick = { message = repo.transition(s.id, SSessionStatus.CANCELLED) ?: "Marked CANCELLED." })
    }
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(text = message, fontSize = 12.sp, color = if (message.startsWith("Walk")) ScribeRed else ScribeGreen)
    }
    Spacer(Modifier.height(8.dp))
    if (!s.voided) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = voidReason,
                onValueChange = { voidReason = it },
                label = { Text("Void reason") },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                singleLine = true,
            )
            ScribeButton(text = "Void", onClick = { message = repo.setVoid(s.id, voidReason) ?: "Voided — kept visible, out of totals." })
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Voided for: ${s.voidReason}", fontSize = 12.sp, color = ScribeRed, modifier = Modifier.weight(1f))
            ScribeGhostButton(text = "Unvoid", onClick = { repo.unvoid(s.id); message = "Unvoided — back in totals." })
        }
    }
    Spacer(Modifier.height(4.dp))
    FieldNote("Void excludes the session from finance but preserves the record; unvoid restores it. Reason is required.")
}
