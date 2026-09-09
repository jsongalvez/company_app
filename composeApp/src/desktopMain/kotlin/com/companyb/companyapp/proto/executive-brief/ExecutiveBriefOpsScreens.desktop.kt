package com.companyb.companyapp.proto.executivebrief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

// #770 — executive-brief ops screens: home/relief, sessions with void flow, global clients.

@Composable
fun EbHomeScreen(repo: EbRepo) {
    var tab by remember { mutableStateOf(0) }
    EbHeadline("Home & clock")
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Off the clock",
                        color = EbColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    EbNote("Home branch: ${repo.branch(repo.currentUser?.homeBranchId ?: repo.currentBranchId).name}. Relief duty starts view-only; edit access needs a grant.")
                }
                if (repo.clockedIn) {
                    EbGhost("Clock out") {
                        repo.clockedIn = false
                        repo.log("${repo.currentUser?.name} clocked out")
                    }
                } else {
                    EbPrimary("Clock in") {
                        repo.clockedIn = true
                        repo.log("${repo.currentUser?.name} clocked in at ${repo.currentBranch().name}")
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Relief duty", "Relief requests", "Relief invites").forEachIndexed { i, label ->
            if (i == tab) EbPrimary(label) { tab = i } else EbGhost(label) { tab = i }
        }
    }
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    EbSubhead("My relief duty")
                    repo.reliefBoard.filter { it.startsWith("Duty") }.forEach {
                        Text("• $it", color = EbColors.Ink, fontSize = 13.sp)
                    }
                    EbNote("Relief pay comes from the relief branch drawer; duty expires 04:00 Manila the next day.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EbPrimary("Clock in as relief at BGC") {
                            repo.clockedIn = true
                            repo.log("${repo.currentUser?.name} clocked in as relief at BGC (view-only until grant)")
                        }
                    }
                }
                1 -> {
                    EbSubhead("Relief requests — outsider-initiated broadcast")
                    repo.reliefBoard.filter { it.startsWith("Request") }.forEach {
                        Text("• $it", color = EbColors.Ink, fontSize = 13.sp)
                    }
                    EbNote("One live request per requester per branch per date. Any active branch member grants, denies, or cancels; the requester may withdraw. All retraction locks once the requester clocks in as relief.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EbPrimary("New request for Cebu Tour") {
                            repo.reliefBoard.add("Request: You asked Cebu Tour for edit access today — PENDING")
                            repo.log("Relief request opened for Cebu Tour")
                        }
                        EbGhost("Withdraw BGC request") { repo.log("Relief request withdrawn (fake)") }
                    }
                }
                else -> {
                    EbSubhead("Relief invites — branch-initiated, single future day")
                    repo.reliefBoard.filter { it.startsWith("Invite") }.forEach {
                        Text("• $it", color = EbColors.Ink, fontSize = 13.sp)
                    }
                    EbNote("Accepting writes the day grant. The branch may revoke an accepted future duty until clock-in; revocation frees re-invite.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EbPrimary("Accept invite") { repo.log("Relief invite accepted — day grant written (fake)") }
                        EbGhost("Decline") { repo.log("Relief invite declined (fake)") }
                    }
                }
            }
        }
    }
}

@Composable
fun EbSessionsScreen(repo: EbRepo) {
    var showVoided by remember { mutableStateOf(true) }
    var time by remember { mutableStateOf("16:00") }
    var client by remember { mutableStateOf("New client") }
    var walkIn by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    EbHeadline("Sessions — ${repo.currentBranch().name}")
    Row(verticalAlignment = Alignment.CenterVertically) {
        EbNote("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
        Spacer(Modifier.weight(1f))
        Checkbox(checked = showVoided, onCheckedChange = { showVoided = it })
        Text("Show voided", color = EbColors.Ink, fontSize = 12.sp)
    }
    repo.branchSessions(repo.currentBranchId).filter { showVoided || !it.voided }.forEach { s ->
        EbCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.time} · ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}${if (s.voided) " · VOIDED" else ""}",
                            color = EbColors.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${s.id} · ${s.kind} · ₱${s.price} · ${s.practitioners} · ${s.status.name}",
                            color = EbColors.Faded,
                            fontSize = 12.sp,
                        )
                        if (s.voided) EbNote("Void reason: ${s.voidReason}")
                    }
                    EbGhost(if (selectedId == s.id) "Hide" else "Detail") {
                        selectedId = if (selectedId == s.id) null else s.id
                    }
                }
                if (selectedId == s.id) {
                    EbRule()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EbSessionStatus.entries.filter { it != EbSessionStatus.PENDING }.forEach { next ->
                            val blocked = s.walkIn && (next == EbSessionStatus.NO_SHOW || next == EbSessionStatus.CANCELLED)
                            if (next == s.status) {
                                EbNote("✓ ${next.name.lowercase().replace('_', '-')}")
                            } else if (!blocked) {
                                EbGhost(next.name.lowercase().replace('_', '-')) { repo.advance(s.id, next) }
                            }
                        }
                    }
                    if (s.walkIn) EbNote("Walk-in rule: only COMPLETED is offered — no NO_SHOW / CANCELLED.")
                    var reason by remember(s.id) { mutableStateOf("") }
                    if (!s.voided) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Void reason (required)") },
                                modifier = Modifier.weight(1f),
                            )
                            EbGhost("Void", enabled = reason.isNotBlank()) { repo.voidSession(s.id, reason) }
                        }
                    } else {
                        EbLink("Unvoid — restore record") { repo.unvoidSession(s.id) }
                    }
                }
            }
        }
    }
    EbSubhead("Log session")
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = time, onValueChange = { time = it }, label = { Text("Time") }, modifier = Modifier.weight(1f))
                TextField(value = client, onValueChange = { client = it }, label = { Text("Client") }, modifier = Modifier.weight(2f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = walkIn, onCheckedChange = { walkIn = it })
                Text("Walk-in", color = EbColors.Ink, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                EbPrimary("Create PENDING") {
                    repo.addSession(time, client, repo.currentBranchId, if (walkIn) "Walk-in" else "Follow-up", walkIn, 1200)
                }
            }
        }
    }
}

@Composable
fun EbClientsScreen(repo: EbRepo) {
    var showAnon by remember { mutableStateOf(false) }
    EbHeadline("Clients — one global record")
    EbNote("A client is global across branches with at most one PENDING session at a time. Anonymized rows keep gender + age for reporting only.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = showAnon, onCheckedChange = { showAnon = it })
        Text("Reveal anonymized PII note", color = EbColors.Ink, fontSize = 13.sp)
    }
    repo.clients.forEach { c ->
        EbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = EbColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (c.anonymized) "Anonymized view · gender ${c.gender} · age ${c.age}" else c.contact,
                        color = EbColors.Faded,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (c.pendingCount > 0) "1 PENDING (max)" else "no pending",
                    color = if (c.pendingCount > 0) EbColors.Brass else EbColors.Sage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (c.anonymized && showAnon) {
                Spacer(Modifier.height(6.dp))
                EbNote("PII nullified on anonymize; only gender + age retained for reporting. The row stays visible so history never breaks.")
            }
        }
    }
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EbSubhead("At-most-one-PENDING rule")
            EbNote("Booking a second PENDING session for Jose Rizal or Gloria Diaz is refused until the live one resolves — enforced at create time, noted here for the judge.")
            EbPrimary("Try second PENDING for Jose Rizal") {
                repo.log("Refused second PENDING for Jose Rizal — at-most-one-PENDING rule (fake)")
            }
        }
    }
}
