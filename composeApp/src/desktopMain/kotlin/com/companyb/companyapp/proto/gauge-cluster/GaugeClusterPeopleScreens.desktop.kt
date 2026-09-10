package com.companyb.companyapp.proto.gaugecluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

// #834 — gauge-cluster people screens: registry, fuel & ledger, pit crew, signals, logbook, driver.

@Composable
fun GaugeClients(repo: GaugeFakeRepo) {
    GaugePanel("Registry — global Client records (all branches)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (repo.clientsVeiled) "Names veiled (privacy lamp on)" else "Names revealed",
                Modifier.weight(1f),
                fontSize = 13.sp,
                color = GaugeColors.CreamDim,
            )
            GaugeButton(if (repo.clientsVeiled) "Reveal" else "Veil", { repo.clientsVeiled = !repo.clientsVeiled }, primary = false)
        }
        Spacer(Modifier.height(8.dp))
        repo.clients.forEach { c ->
            val shown = when {
                c.anonymized -> "Anonymized (${c.gender}, ${c.age})"
                repo.clientsVeiled -> "•••••• (${c.gender}, ${c.age})"
                else -> c.name
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shown, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GaugeColors.Cream)
                    Text(
                        "PENDING sessions: ${repo.pendingFor(c.id)} (at most one PENDING Session per Client)",
                        fontSize = 12.sp,
                        color = GaugeColors.CreamDim,
                    )
                }
                if (!c.anonymized) {
                    GaugeLink("Anonymize") {
                        repo.clients[repo.clients.indexOf(c)] = c.copy(name = "", anonymized = true)
                        repo.audit(repo.me()?.name ?: "proto", "ANONYMIZE_CLIENT", c.id, "privacy request")
                    }
                } else {
                    GaugeLamp("SEALED", GaugeColors.LampBlue)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        GaugeNote("Anonymize is a soft-delete + PII nullification: gender and age stay for reporting.")
    }
}

@Composable
fun GaugeFinance(repo: GaugeFakeRepo) {
    var undoFor by remember { mutableStateOf<GaugeRemitKind?>(null) }
    var undoReason by remember { mutableStateOf("") }
    GaugePanel("Fuel & ledger — remittance (SESSION + PRODUCT, independent flows)") {
        Text(
            "Drafts are unconstrained and can overlap. Submit seals an immutable Snapshot (RS-*); " +
                "Undo returns to Draft and deletes the snapshot within 48h, with a reason in the audit trail.",
            fontSize = 13.sp,
            color = GaugeColors.CreamDim,
        )
        Spacer(Modifier.height(10.dp))
        repo.remittances.forEach { r ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${r.kind.name} flow",
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GaugeColors.Cream,
                    )
                    GaugeLamp(r.state.name, if (r.state == GaugeRemitState.SUBMITTED) GaugeColors.LampGreen else GaugeColors.LampAmber)
                }
                Spacer(Modifier.height(4.dp))
                GaugeRow("Draft total", "₱${r.draftTotal}")
                GaugeRow("Snapshot", r.snapshotId?.let { "$it · ₱${r.snapshotTotal}" } ?: "—")
                if (r.submittedAt != null) GaugeRow("Submitted", r.submittedAt)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GaugeButton("+₱500", {
                        repo.remittances[repo.remittances.indexOf(r)] = r.copy(draftTotal = r.draftTotal + 500)
                    }, primary = false)
                    GaugeButton("−₱500", {
                        repo.remittances[repo.remittances.indexOf(r)] =
                            r.copy(draftTotal = (r.draftTotal - 500).coerceAtLeast(0))
                    }, primary = false)
                    if (r.state == GaugeRemitState.DRAFT) {
                        GaugeButton("Submit → snapshot", {
                            val id = "RS-${8800 + repo.remittances.indexOf(r) * 7 + r.draftTotal % 90}"
                            repo.remittances[repo.remittances.indexOf(r)] = r.copy(
                                state = GaugeRemitState.SUBMITTED,
                                snapshotId = id,
                                snapshotTotal = r.draftTotal,
                                submittedAt = "today 17:40",
                            )
                            repo.audit(repo.me()?.name ?: "proto", "SUBMIT_REMITTANCE", "${r.kind.name} $id")
                        })
                    } else {
                        GaugeButton("Undo (48h)", { undoFor = r.kind }, primary = false)
                    }
                }
                if (undoFor == r.kind && r.state == GaugeRemitState.SUBMITTED) {
                    Spacer(Modifier.height(6.dp))
                    TextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (required)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GaugeButton("Confirm undo", {
                            if (undoReason.isNotBlank()) {
                                repo.remittances[repo.remittances.indexOf(r)] = r.copy(
                                    state = GaugeRemitState.DRAFT,
                                    snapshotId = null,
                                    snapshotTotal = null,
                                    submittedAt = null,
                                    undoReason = undoReason,
                                )
                                repo.audit(repo.me()?.name ?: "proto", "UNDO_REMITTANCE", r.kind.name, undoReason)
                                undoFor = null
                                undoReason = ""
                            }
                        })
                        GaugeButton("Cancel", { undoFor = null }, primary = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        GaugeNote(
            "Commission split: pooled per Branch Day, even split over Practitioners clocked in at sold_at; " +
                "relief compensation is paid from the relief branch drawer.",
        )
    }
}

@Composable
fun GaugeTeam(repo: GaugeFakeRepo) {
    GaugePanel("Pit crew — roster in branch-slot order") {
        repo.users.sortedBy { it.slot }.forEach { u ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        u.name + if (u.id == repo.currentUserId) "  ● YOU" else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GaugeColors.Cream,
                    )
                    Text(
                        "${u.role.name} · home ${repo.branches.first { it.id == u.homeBranchId }.name} · slot ${u.slot}" +
                            if (u.role == GaugeRole.ONBOARDING && !repo.onboardGranted) " · LOCKED (zero capabilities)" else "",
                        fontSize = 12.sp,
                        color = GaugeColors.CreamDim,
                    )
                }
                GaugeLamp(u.role.name, if (u.role == GaugeRole.ONBOARDING) GaugeColors.LampRed else GaugeColors.LampGreen)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        GaugeNote("MANAGER is a superset of Coordinator (finance + user management + delegate assignment). Accountant is read-only across all branches.")
    }
}

@Composable
fun GaugeMail(repo: GaugeFakeRepo) {
    GaugePanel("Signals — notification mailbox") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeButton("Hush all", {
                repo.notifications.replaceAll { it.copy(read = true) }
                repo.audit(repo.me()?.name ?: "proto", "READ_ALL_NOTIFICATIONS", "mailbox")
            }, primary = false)
        }
        Spacer(Modifier.height(8.dp))
        repo.notifications.forEach { n ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (n.read) "○ " else "● ") + n.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (n.read) GaugeColors.CreamDim else GaugeColors.Cream,
                    )
                    Text(n.body, fontSize = 13.sp, color = GaugeColors.CreamDim)
                    Text(n.at, fontSize = 11.sp, color = GaugeColors.CreamDim)
                }
                Spacer(Modifier.width(8.dp))
                if (!n.read) {
                    GaugeButton("Hush", {
                        repo.notifications[repo.notifications.indexOf(n)] = n.copy(read = true)
                    }, primary = false)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun GaugeLedger(repo: GaugeFakeRepo) {
    GaugePanel("Logbook — audit trail (newest first)") {
        repo.audits.forEach { a ->
            Column(Modifier.fillMaxWidth()) {
                Text("${a.who} → ${a.action}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GaugeColors.Cream)
                Text(
                    "target ${a.target}" + (a.reason?.let { " · reason: $it" } ?: "") + " · ${a.at}",
                    fontSize = 12.sp,
                    color = GaugeColors.CreamDim,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun GaugeProfile(repo: GaugeFakeRepo, onSignOut: () -> Unit) {
    val me = repo.me()
    GaugePanel("Driver — profile") {
        GaugeRow("Name", me?.name ?: "—")
        GaugeRow("Role", me?.role?.name ?: "—")
        GaugeRow("Home branch", me?.let { repo.branches.first { b -> b.id == it.homeBranchId }.name } ?: "—")
        GaugeRow("Duty", if (repo.clockedIn) "Clocked in" else "Off duty")
        GaugeRow("Relief post", repo.reliefBranchId?.let { id -> repo.branches.first { it.id == id }.name } ?: "—")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeButton(if (repo.clockedIn) "Clock out" else "Clock in", {
                repo.clockedIn = !repo.clockedIn
                if (!repo.clockedIn) repo.reliefBranchId = null
                repo.audit(me?.name ?: "proto", if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranchId)
            }, primary = !repo.clockedIn)
            GaugeButton("Log out", {
                repo.audit(me?.name ?: "proto", "SIGN_OUT", repo.currentBranchId)
                onSignOut()
            }, primary = false)
        }
        Spacer(Modifier.height(8.dp))
        GaugeNote("Log out returns to Ignition; the cluster keeps its needles where you left them (fake state resets on relaunch).")
    }
}
