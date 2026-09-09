package com.companyb.companyapp.proto.splitmaster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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

// #765 — split-master sessions: 480dp master list, flexible detail. Selection is
// retained in the repo; the master scroll state is hoisted so back restores scroll.

@Composable
fun SmSessionsPane(repo: SplitMasterRepo, scroll: LazyListState) {
    var showCreate by remember { mutableStateOf(false) }
    SmSplit(
        master = {
            SmMasterHead(
                kicker = "MASTER · SESSIONS",
                title = "Today's sessions",
                hint = "Selection sticks. Back restores this scroll.",
            )
            val branchSessions = repo.sessions.filter { it.branchId == repo.currentBranchId.ifBlank { "b-makati" } }
            SmMasterList(
                items = branchSessions,
                key = { it.id },
                selectedKey = repo.selectedSessionId,
                onSelect = { repo.selectedSessionId = it.id },
                scroll = scroll,
                row = { s, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmMono(s.time + "  " + s.id.uppercase(), selected)
                            Spacer(Modifier.width(8.dp))
                            if (s.voided) SmChip("VOIDED", dark = true)
                        }
                        SmMasterTheme {
                            Text(
                                s.clientName + if (s.walkIn) " · walk-in" else "",
                                color = SmColors.InkText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        SmMasterTheme {
                            Text(s.status.name, color = SmColors.InkFaded, fontSize = 12.sp)
                        }
                    }
                },
            )
            SmMasterAction("+ Log session") { showCreate = true }
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            val current = repo.sessions.firstOrNull { it.id == repo.selectedSessionId }
            if (current == null) {
                SmDetailCard { SmNote("Pick a session in the master list — the detail pane follows selection.") }
            } else {
                SmSessionDetail(repo, current)
            }
            if (showCreate) SmCreateSessionCard(repo) { showCreate = false }
        },
    )
}

@Composable
private fun SmSessionDetail(repo: SplitMasterRepo, s: SmSession) {
    var reason by remember(s.id) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmDetailCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmHeadline("${s.time} · ${s.clientName}")
                    SmNote("${s.id.uppercase()} · ${s.kind} · ${s.practitioners} · ₱${s.price} · ${repo.branch(s.branchId).name}")
                }
                SmStatusChip(s.status)
            }
            if (s.voided) {
                SmNote("VOIDED — reason: ${s.voidReason}. The record stays visible; unvoid restores it.")
            }
        }
        SmDetailCard {
            SmSubhead("Status transitions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmSessionStatus.entries.forEach { next ->
                    if (next == s.status) {
                        SmChip(next.name)
                    } else {
                        SmGhost(next.name) { repo.transitionSession(s.id, next) }
                    }
                }
            }
            if (s.walkIn) {
                SmNote("Walk-in rule: only COMPLETED is offered for walk-ins — NO_SHOW and CANCELLED never apply to a guest who simply showed up.")
            } else {
                SmNote("Standard session: PENDING → COMPLETED / NO_SHOW / CANCELLED. Each tap appends an audit entry.")
            }
        }
        SmDetailCard {
            SmSubhead("Void / unvoid")
            if (s.voided) {
                SmPrimary("Unvoid — restore record") { repo.unvoidSession(s.id) }
            } else {
                TextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Void reason (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SmPrimary("Void with reason", enabled = reason.isNotBlank()) {
                    repo.voidSession(s.id, reason.trim())
                    reason = ""
                }
            }
        }
    }
}

@Composable
private fun SmCreateSessionCard(repo: SplitMasterRepo, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("16:00") }
    var walkIn by remember { mutableStateOf(false) }
    SmDetailCard {
        SmSubhead("Log session — creates PENDING")
        TextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
            SmBody("Walk-in guest")
        }
        if (walkIn) SmNote("Walk-in rule note: the new entry can only COMPLETE — NO_SHOW/CANCELLED are hidden for it.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmPrimary("Create PENDING", enabled = name.isNotBlank()) {
                repo.addSession(name.trim(), walkIn, time.trim().ifBlank { "16:00" })
                onDone()
            }
            SmGhost("Cancel", onClick = onDone)
        }
    }
}
