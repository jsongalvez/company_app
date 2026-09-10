package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// #819 — owner-desk sessions ledger and the sealed client file.

@Composable
fun OdSessionsScreen(repo: OwnerDeskRepo) {
    val branch = repo.currentBranch()
    var showCreate by remember { mutableStateOf(false) }
    var voidTarget by remember { mutableStateOf<OdSession?>(null) }
    var detail by remember { mutableStateOf<OdSession?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("Session ledger")
        OdNote("${branch.name} book · walk-ins may only COMPLETE — NO_SHOW/CANCELLED never applies to a guest who walked in.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdGhostButton(if (showCreate) "Close the page" else "+ Enter session") { showCreate = !showCreate }
        }
        if (showCreate) OdCreateSession(repo) { showCreate = false }
        repo.branchSessions(branch.id).forEach { s ->
            OdLedgerCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OdLedgerTitle("${s.time} · ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}")
                        OdLedgerFaint("${s.kind} · ₱${s.price} · ${s.status.name}${if (s.voided) " · VOID" else ""}")
                        if (s.voided) OdLedgerFaint("Void reason: ${s.voidReason}")
                    }
                    Spacer(Modifier.width(8.dp))
                    OdLedgerGhost("Open") { detail = s }
                }
            }
        }
    }
    detail?.let { s ->
        OdSessionDetail(repo, s, onClose = { detail = null }, onVoid = { voidTarget = s; detail = null })
    }
    voidTarget?.let { s ->
        OdVoidSheet(repo, s) { voidTarget = null }
    }
}

@Composable
private fun OdCreateSession(repo: OwnerDeskRepo, onDone: () -> Unit) {
    var time by remember { mutableStateOf("16:30") }
    var client by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    OdLedgerCard {
        OdLedgerTitle("New page in the ledger")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Time", color = OdColors.Faded, fontSize = 12.sp)
                OutlinedTextField(time, { time = it }, singleLine = true)
            }
            Column(Modifier.weight(2f)) {
                Text("Client", color = OdColors.Faded, fontSize = 12.sp)
                OutlinedTextField(client, { client = it }, singleLine = true)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OdLedgerGhost(if (walkIn) "Walk-in: YES" else "Walk-in: no") { walkIn = !walkIn }
            Spacer(Modifier.width(8.dp))
            OdLedgerFaint("Walk-ins skip NO_SHOW/CANCELLED by rule.")
        }
        OdLedgerButton("Enter as PENDING") {
            repo.addSession(time, client.ifBlank { "Unnamed guest" }, repo.currentBranchId, "Follow-up", walkIn, 1200)
            onDone()
        }
    }
}

@Composable
private fun OdSessionDetail(repo: OwnerDeskRepo, s: OdSession, onClose: () -> Unit, onVoid: () -> Unit) {
    OdLedgerCard {
        OdLedgerTitle("Session ${s.id} — ${s.clientName}")
        OdLedgerText("Practitioner on record: sealed (owner sees counts, not hands).")
        OdLedgerFaint("${s.time} · ${s.kind} · ₱${s.price} · ${repo.branch(s.branchId).name}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val targets = if (s.walkIn) {
                listOf(OdSessionStatus.COMPLETED)
            } else {
                listOf(OdSessionStatus.PENDING, OdSessionStatus.COMPLETED, OdSessionStatus.NO_SHOW, OdSessionStatus.CANCELLED)
            }
            targets.forEach { t ->
                val active = s.status == t
                if (active) {
                    OdLedgerButton(t.name) { }
                } else {
                    OdLedgerGhost(t.name) { repo.advance(s.id, t) }
                }
            }
        }
        if (s.walkIn) OdLedgerFaint("Rule note: walk-in sessions never take NO_SHOW or CANCELLED.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.voided) {
                OdLedgerButton("Restore (unvoid)") { repo.unvoidSession(s.id); onClose() }
            } else {
                OdLedgerGhost("Void with reason") { onVoid() }
            }
            OdLedgerGhost("Shut") { onClose() }
        }
    }
}

@Composable
private fun OdVoidSheet(repo: OwnerDeskRepo, s: OdSession, onDone: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    OdLedgerCard {
        OdLedgerTitle("Void session ${s.id}?")
        OdLedgerFaint("A reason is required — the ledger never forgets silently.")
        OutlinedTextField(reason, { reason = it }, singleLine = true, label = { Text("Reason") })
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdLedgerButton("Void the entry") {
                if (reason.isNotBlank()) {
                    repo.voidSession(s.id, reason.trim())
                    onDone()
                }
            }
            OdLedgerGhost("Keep it") { onDone() }
        }
    }
}

@Composable
fun OdClientsScreen(repo: OwnerDeskRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("The sealed file")
        OdNote("Clients are global across branches. At most one PENDING session each — the desk refuses a second.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OdGhostButton(if (repo.anonymizeClients) "Anonymized view: ON" else "Anonymized view: off") {
                repo.anonymizeClients = !repo.anonymizeClients
            }
        }
        repo.clients.forEach { c ->
            val shown = repo.anonymizeClients || c.anonymized
            OdLedgerCard {
                OdLedgerTitle(if (shown) "File ${c.id} (sealed)" else c.name)
                if (shown) {
                    OdLedgerFaint("Name and contact withheld in anonymized view.")
                } else {
                    OdLedgerFaint(c.contact)
                }
                OdLedgerFaint("PENDING sessions: ${c.pendingCount} (at most one allowed)")
            }
        }
    }
}
