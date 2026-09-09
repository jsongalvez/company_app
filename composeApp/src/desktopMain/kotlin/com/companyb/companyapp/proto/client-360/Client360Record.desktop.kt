package com.companyb.companyapp.proto.client360

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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

// #773 — client-360 record dossier: stamped header, cross-branch timeline spine,
// session history with transitions + void, anonymize flow, at-most-one-PENDING guard.

@Composable
fun C360RecordPane(repo: Client360Repo, onOpenSessions: () -> Unit) {
    val client = repo.selectedClient()
    val history = repo.clientSessions(client.id)
    val pending = repo.pendingFor(client.id)
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Client 360 record — global across branches")
        C360RecordHeader(repo, client)
        if (pending != null) {
            C360PendingGuard(repo, client, pending, onOpenSessions)
        } else {
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    C360Title("Clear to book")
                    C360Note("No live PENDING session on this record. The at-most-one-PENDING rule is satisfied, so booking is open.")
                    Row {
                        C360Primary("Book follow-up session") { repo.bookSession(client) }
                    }
                    if (repo.statusMessage.isNotBlank()) {
                        C360Note(repo.statusMessage)
                    }
                }
            }
        }
        C360TimelineCard(repo, client)
        C360HistoryCard(repo, client, history)
        C360AnonymizeCard(repo, client)
    }
}

@Composable
private fun C360RecordHeader(repo: Client360Repo, client: C360Client) {
    C360Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        repo.displayName(client).uppercase(),
                        color = C360Colors.Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(repo.displayContact(client), color = C360Colors.Faded, fontSize = 13.sp)
                }
                Box(
                    Modifier.background(C360Colors.Card, RoundedCornerShape(8.dp))
                        .border(2.dp, C360Colors.StampRed, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (client.anonymized || repo.maskAll) "ANONYMIZED" else "VERIFIED",
                        color = C360Colors.StampRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                client.tags.forEach { C360Chip(it) }
                C360Chip("Since ${client.since}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                C360Stat("${repo.clientSessions(client.id).size}", "visits")
                C360Stat("${repo.branchNamesFor(client.id).size}", "branches")
                C360Stat("₱${repo.spendFor(client.id)}", "completed spend")
                C360Stat(if (repo.pendingFor(client.id) != null) "1 PENDING" else "clear", "booking state")
            }
            C360Note("Seen at: ${repo.branchNamesFor(client.id).joinToString(" · ")}. Clients are global — every branch reads this same record.")
        }
    }
}

@Composable
private fun C360Stat(value: String, label: String) {
    Column {
        Text(value, color = C360Colors.BrassDeep, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = C360Colors.Faded, fontSize = 11.sp)
    }
}

@Composable
private fun C360PendingGuard(repo: Client360Repo, client: C360Client, pending: C360Session, onOpenSessions: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(C360Colors.GuardBg, RoundedCornerShape(10.dp))
            .border(1.dp, C360Colors.GuardEdge, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AT-MOST-ONE-PENDING GUARD — BOOKING HELD", color = C360Colors.BrassDeep, fontSize = 12.sp, fontWeight = FontWeight.Black)
            C360Body("${repo.displayName(client)} already holds PENDING ${pending.id} (${pending.kind}, ${repo.branch(pending.branchId).name} ${pending.time}). A second PENDING is refused until it resolves.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                C360Primary("Resolve ${pending.id} now") {
                    repo.transition(pending, C360SessionStatus.COMPLETED)
                    onOpenSessions()
                }
                C360Ghost("Review in Sessions") { onOpenSessions() }
            }
        }
    }
}

@Composable
private fun C360TimelineCard(repo: Client360Repo, client: C360Client) {
    val history = repo.clientSessions(client.id)
    C360Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            C360Eyebrow("Global record timeline")
            C360Title("One spine, every branch")
            history.forEach { s ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        C360SealDot(s.status.seal())
                        Box(Modifier.width(2.dp).height(26.dp).background(C360Colors.Spine))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.time, color = C360Colors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(s.kind, color = C360Colors.Ink, fontSize = 13.sp)
                            if (s.voided) {
                                Spacer(Modifier.width(8.dp))
                                Text("VOIDED", color = C360Colors.StampRed, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            "${repo.branch(s.branchId).name} · ${s.practitioner}" +
                                if (s.walkIn) " · walk-in" else "",
                            color = C360Colors.Faded,
                            fontSize = 12.sp,
                        )
                    }
                    C360Chip(s.status.name)
                }
            }
            C360Note("Newest first. Walk-in entries only ever land COMPLETED — NO_SHOW and CANCELLED are never offered for walk-ins.")
        }
    }
}

@Composable
private fun C360HistoryCard(repo: Client360Repo, client: C360Client, history: List<C360Session>) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    var voidTarget by remember { mutableStateOf<C360Session?>(null) }
    C360Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            C360Eyebrow("Session history")
            C360Title("Transitions live on the record")
            history.forEach { s ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(C360Colors.Paper, RoundedCornerShape(8.dp))
                        .border(1.dp, C360Colors.CardEdge, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            C360SealDot(s.status.seal())
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${s.id} · ${s.kind}", color = C360Colors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${s.time} · ${repo.branch(s.branchId).name} · ₱${s.price}", color = C360Colors.Faded, fontSize = 12.sp)
                            }
                            C360Link(if (expandedId == s.id) "Hide" else "Manage") {
                                expandedId = if (expandedId == s.id) null else s.id
                            }
                        }
                        if (expandedId == s.id) {
                            if (!s.voided && s.status == C360SessionStatus.PENDING) {
                                if (s.walkIn) {
                                    C360Note("Walk-in rule: only COMPLETED is offered. NO_SHOW and CANCELLED do not apply to walk-ins.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        C360Primary("Complete") { repo.transition(s, C360SessionStatus.COMPLETED) }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        C360Primary("Complete") { repo.transition(s, C360SessionStatus.COMPLETED) }
                                        C360Ghost("No-show") { repo.transition(s, C360SessionStatus.NO_SHOW) }
                                        C360Ghost("Cancel") { repo.transition(s, C360SessionStatus.CANCELLED) }
                                    }
                                }
                            }
                            if (!s.voided) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Ghost("Void with reason") { voidTarget = s }
                                }
                            } else {
                                C360Note("Voided — reason: ${s.voidReason}.")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Ghost("Unvoid — restore record") { repo.unvoidSession(s) }
                                }
                            }
                            if (voidTarget?.id == s.id) {
                                C360Note("Pick a reason to void ${s.id}:")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Chip("Duplicate entry", onClick = { repo.voidSession(s, "Duplicate entry"); voidTarget = null })
                                    C360Chip("Wrong client", onClick = { repo.voidSession(s, "Wrong client"); voidTarget = null })
                                    C360Chip("Rescheduled", onClick = { repo.voidSession(s, "Rescheduled"); voidTarget = null })
                                }
                            }
                        }
                    }
                }
            }
            C360Note("Client: ${repo.displayName(client)}. Void always needs a reason; unvoid restores the record in place.")
        }
    }
}

@Composable
private fun C360AnonymizeCard(repo: Client360Repo, client: C360Client) {
    var confirm by remember { mutableStateOf(false) }
    C360Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            C360Eyebrow("Privacy")
            C360Title("Anonymize flow")
            if (!client.anonymized) {
                C360Body("Anonymizing stamps this record ANONYMIZED: the name and contact mask everywhere — timeline, history, mailbox, audit references — while visits and spend stay countable.")
                if (!confirm) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C360Primary("Anonymize this record") { confirm = true }
                        C360Ghost(if (repo.maskAll) "Unmask all records" else "Mask all records") {
                            repo.maskAll = !repo.maskAll
                            repo.log("${repo.currentUser?.name} toggled global anonymized view ${if (repo.maskAll) "on" else "off"}")
                        }
                    }
                } else {
                    C360Note("Confirm: mask ${client.name} (${client.contact})? This writes an audit entry.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        C360Primary("Confirm anonymize") {
                            client.anonymized = true
                            repo.log("${repo.currentUser?.name} anonymized client record ${client.id}")
                            repo.statusMessage = "${client.id} anonymized."
                            confirm = false
                        }
                        C360Ghost("Keep visible") { confirm = false }
                    }
                }
            } else {
                C360Body("Record is ANONYMIZED. Visits, branches, and spend remain visible; identity stays masked.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C360Ghost("Reveal (MANAGER override)") {
                        client.anonymized = false
                        repo.log("${repo.currentUser?.name} revealed client record ${client.id} (MANAGER override)")
                    }
                }
            }
        }
    }
}
