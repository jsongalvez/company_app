package com.companyb.companyapp.proto.commandpalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CpSessionsView(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch() ?: return
    val readOnly = user.role == CpRole.ACCOUNTANT
    val filter = store.sessionFilter.value
    val list =
        store.sessions
            .filter { it.branchId == branch.id && it.dayDate == store.dayFilter.value }
            .filter { filter == null || it.status == filter }
    CpPageTitle("Sessions", "${branch.name} · ${store.dayFilter.value} — pick one for detail")
    if (readOnly) {
        Text(
            "Accountant view is read-only — status moves and voids are disabled.",
            fontSize = 13.sp,
            color = CpNight.Blue,
        )
        CpVSpace(10)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CpFilterChip("All", filter == null) { store.sessionFilter.value = null }
        CpSessionStatus.entries.forEach { status ->
            CpFilterChip(status.label, filter == status) { store.sessionFilter.value = status }
        }
        Spacer(Modifier.weight(1f))
        CpSecondaryButton("New session", enabled = !readOnly) {
            store.createOpen.value = true
            store.createClientId.value = null
        }
    }
    CpVSpace()
    CpTwoCol(
        left = {
            if (list.isEmpty()) {
                CpEmptyNote("No sessions match this filter on this branch day.")
            } else {
                list.forEach { session ->
                    val selected = store.selectedSessionId.value == session.id
                    CpCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .border(
                                    if (selected) 1.dp else 0.dp,
                                    if (selected) CpNight.Accent else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(14.dp),
                                ),
                        onClick = {
                            store.selectedSessionId.value = session.id
                            store.touchRecent(
                                CpRecent(
                                    "Session",
                                    "${session.id} · ${session.clientName}",
                                    session.status.label,
                                    CpScreen.SESSIONS,
                                    session.id,
                                ),
                            )
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${session.time} · ${session.clientName}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CpNight.Ink,
                                )
                                Text(
                                    "${session.id} · ${session.type}${if (session.walkIn) " · walk-in" else ""}",
                                    fontSize = 12.sp,
                                    color = CpNight.Muted,
                                )
                            }
                            CpChip(session.status.label, session.status.tint())
                        }
                        if (session.voided) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Voided — kept for record, excluded from totals.",
                                fontSize = 12.sp,
                                color = CpNight.Red,
                            )
                        }
                    }
                }
            }
        },
        right = {
            val detail = store.sessions.firstOrNull { it.id == store.selectedSessionId.value }
            if (detail == null) {
                CpEmptyNote("Select a session on the left — or hit Ctrl/⌘K and fuzz its id.")
            } else {
                CpSessionDetail(store, user, detail, readOnly)
            }
        },
    )
    if (store.createOpen.value) CpCreateSessionDialog(store, user)
    if (store.voidTargetId.value != null) CpVoidDialog(store, user)
}

@Composable
private fun CpFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) CpNight.Accent.copy(alpha = 0.18f) else CpNight.Panel)
            .border(1.dp, if (selected) CpNight.Accent else CpNight.Hairline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CpNight.Accent else CpNight.Muted,
        )
    }
}

@Composable
private fun CpSessionDetail(
    store: CpStore,
    user: CpUser,
    session: CpSession,
    readOnly: Boolean,
) {
    CpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.id} · ${session.clientName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CpNight.Ink,
                )
                Text("${session.type} · ${session.time} · ${session.dayDate}", fontSize = 13.sp, color = CpNight.Muted)
            }
            CpChip(session.status.label, session.status.tint())
        }
        CpVSpace(10)
        CpStatRow("Practitioners", session.practitioners)
        CpStatRow("Price", cpMoney(session.price))
        CpStatRow("Source", if (session.walkIn) "Walk-in" else "Booked")
        CpStatRow("Void", if (session.voided) "Yes — ${session.voidReason ?: "no reason"}" else "No")
        if (session.status == CpSessionStatus.PENDING && !session.voided) {
            CpVSpace(10)
            Text("Move PENDING forward:", fontSize = 13.sp, color = CpNight.Muted)
            CpVSpace(6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CpSecondaryButton("Complete", enabled = !readOnly) {
                    moveCpSession(store, user, session, CpSessionStatus.COMPLETED)
                }
                CpSecondaryButton("No-show", enabled = !readOnly && !session.walkIn) {
                    moveCpSession(store, user, session, CpSessionStatus.NO_SHOW)
                }
                CpSecondaryButton("Cancel", enabled = !readOnly && !session.walkIn) {
                    moveCpSession(store, user, session, CpSessionStatus.CANCELLED)
                }
            }
            if (session.walkIn) {
                CpVSpace(6)
                Text(
                    "Rule note: walk-in sessions cannot be marked NO_SHOW or CANCELLED — there was no booking to miss.",
                    fontSize = 12.sp,
                    color = CpNight.Amber,
                )
            }
        }
        CpVSpace(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!session.voided) {
                CpSecondaryButton("Void with reason…", enabled = !readOnly) {
                    store.voidTargetId.value = session.id
                    store.voidReason.value = ""
                }
            } else {
                CpSecondaryButton("Unvoid (restore)") {
                    val i = store.sessions.indexOfFirst { it.id == session.id }
                    if (i >= 0) store.sessions[i] = session.copy(voided = false, voidReason = null)
                    store.audit(user.name, "UPDATE", "sessions:${session.id}", "Unvoided — restored to totals.")
                }
            }
        }
    }
}

private fun moveCpSession(
    store: CpStore,
    user: CpUser,
    session: CpSession,
    next: CpSessionStatus,
) {
    val i = store.sessions.indexOfFirst { it.id == session.id }
    if (i >= 0) store.sessions[i] = session.copy(status = next)
    if (next != CpSessionStatus.PENDING) {
        val ci = store.clients.indexOfFirst { it.id == session.clientId }
        if (ci >= 0 && store.clients[ci].pendingSessionId == session.id) {
            store.clients[ci] = store.clients[ci].copy(pendingSessionId = null)
        }
    }
    store.audit(user.name, "UPDATE", "sessions:${session.id}", "${session.status} -> $next.")
    store.notify(
        "Session ${next.label.lowercase()}",
        "${session.id} with ${session.clientName} is now ${next.label}.",
        session.branchId,
        session.dayDate,
    )
}

@Composable
private fun CpVoidDialog(
    store: CpStore,
    user: CpUser,
) {
    val target = store.sessions.firstOrNull { it.id == store.voidTargetId.value }
    AlertDialog(
        onDismissRequest = { store.voidTargetId.value = null },
        title = { Text("Void ${target?.id ?: ""}", color = CpNight.Ink) },
        text = {
            Column {
                Text(
                    "Voiding excludes the session from financial totals while keeping its record visible.",
                    color = CpNight.Muted,
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = store.voidReason.value,
                    onValueChange = { store.voidReason.value = it },
                    placeholder = { Text("Reason (required)", color = CpNight.Faint) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = store.voidReason.value.isNotBlank() && target != null,
                onClick = {
                    val i = store.sessions.indexOfFirst { it.id == target?.id }
                    if (i >= 0 && target != null) {
                        store.sessions[i] = target.copy(voided = true, voidReason = store.voidReason.value.trim())
                    }
                    store.audit(user.name, "VOID", "sessions:${target?.id}", "Voided: ${store.voidReason.value.trim()}")
                    store.voidTargetId.value = null
                },
            ) {
                Text("Void", color = CpNight.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = { store.voidTargetId.value = null }) {
                Text("Keep", color = CpNight.Muted)
            }
        },
    )
}

@Composable
private fun CpCreateSessionDialog(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch()
    val chosen = store.clients.firstOrNull { it.id == store.createClientId.value }
    val blocked = chosen?.pendingSessionId != null
    AlertDialog(
        onDismissRequest = { store.createOpen.value = false },
        title = { Text("New session", color = CpNight.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Client (global record):", color = CpNight.Muted)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(store.clients, key = { it.id }) { client ->
                        val selected = client.id == store.createClientId.value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) {
                                        CpNight.Accent.copy(
                                            alpha = 0.14f,
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                ).clickable { store.createClientId.value = client.id }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(client.name, color = CpNight.Ink)
                                Text(
                                    if (client.pendingSessionId !=
                                        null
                                    ) {
                                        "Holds PENDING ${client.pendingSessionId}"
                                    } else {
                                        "No pending session"
                                    },
                                    fontSize = 12.sp,
                                    color = if (client.pendingSessionId != null) CpNight.Amber else CpNight.Faint,
                                )
                            }
                            if (selected) CpChip("picked", CpNight.Accent)
                        }
                    }
                }
                if (chosen != null && blocked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Note: a client holds at most one PENDING session — ${chosen.name} must finish ${chosen.pendingSessionId} first.",
                        fontSize = 12.sp,
                        color = CpNight.Amber,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Time (24h):", color = CpNight.Muted)
                TextField(
                    value = store.createTime.value,
                    onValueChange = { store.createTime.value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null && !blocked && branch != null,
                onClick = {
                    if (chosen != null && branch != null) {
                        val id = store.nextSessionId()
                        store.sessions.add(
                            CpSession(
                                id = id,
                                time = store.createTime.value.ifBlank { "15:00" },
                                clientId = chosen.id,
                                clientName = chosen.name,
                                branchId = branch.id,
                                dayDate = store.dayFilter.value,
                                type = "Follow-up",
                                status = CpSessionStatus.PENDING,
                                price = 1200,
                                practitioners = user.name,
                                walkIn = false,
                            ),
                        )
                        val ci = store.clients.indexOfFirst { it.id == chosen.id }
                        if (ci >= 0) store.clients[ci] = chosen.copy(pendingSessionId = id)
                        store.audit(user.name, "INSERT", "sessions:$id", "Session created for ${chosen.name}.")
                        store.notify(
                            "Session created",
                            "$id with ${chosen.name} at ${store.createTime.value}.",
                            branch.id,
                            store.dayFilter.value,
                        )
                        store.selectedSessionId.value = id
                    }
                    store.createOpen.value = false
                },
            ) {
                Text("Create PENDING", color = CpNight.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { store.createOpen.value = false }) {
                Text("Discard", color = CpNight.Muted)
            }
        },
    )
}

@Composable
internal fun CpClientsView(
    store: CpStore,
    user: CpUser,
) {
    CpPageTitle("Clients", "Global records shared across every branch")
    TextField(
        value = store.clientQuery.value,
        onValueChange = { store.clientQuery.value = it },
        placeholder = { Text("Search name… (or Ctrl/⌘K to fuzz)", color = CpNight.Faint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CpVSpace()
    Text(
        "Rule note: a client holds at most one PENDING session at a time — the new-session dialog enforces it.",
        fontSize = 12.sp,
        color = CpNight.Faint,
    )
    CpVSpace(10)
    val list =
        store.clients.filter {
            store.clientQuery.value.isBlank() || it.name.contains(store.clientQuery.value, ignoreCase = true)
        }
    CpTwoCol(
        left = {
            if (list.isEmpty()) {
                CpEmptyNote("No clients match that search.")
            } else {
                list.forEach { client ->
                    CpCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        onClick = {
                            store.selectedClientId.value = client.id
                            store.touchRecent(
                                CpRecent(
                                    "Client",
                                    client.name,
                                    "Age ${client.age} · ${client.gender}",
                                    CpScreen.CLIENTS,
                                    client.id,
                                ),
                            )
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    client.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CpNight.Ink,
                                )
                                Text("Age ${client.age} · ${client.gender}", fontSize = 12.sp, color = CpNight.Muted)
                            }
                            if (client.anonymized) {
                                CpChip("Anonymized", CpNight.Violet)
                            } else if (client.pendingSessionId != null) {
                                CpChip("PENDING", CpNight.Gold)
                            }
                        }
                    }
                }
            }
        },
        right = {
            val detail = store.clients.firstOrNull { it.id == store.selectedClientId.value }
            if (detail == null) {
                CpEmptyNote("Select a client to see the record.")
            } else if (detail.anonymized) {
                CpCard(Modifier.fillMaxWidth()) {
                    CpChip("Anonymized view", CpNight.Violet)
                    CpVSpace(8)
                    Text("Record ${detail.id}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CpNight.Ink)
                    CpVSpace(6)
                    Text(
                        "PII was nullified on anonymize — only gender and age survive for reporting.",
                        fontSize = 13.sp,
                        color = CpNight.Muted,
                    )
                    CpVSpace(8)
                    CpStatRow("Gender", detail.gender)
                    CpStatRow("Age", detail.age.toString())
                    CpStatRow("Phone", "withheld")
                }
            } else {
                CpCard(Modifier.fillMaxWidth()) {
                    Text(detail.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CpNight.Ink)
                    CpVSpace(8)
                    CpStatRow("Age", detail.age.toString())
                    CpStatRow("Gender", detail.gender)
                    CpStatRow("Phone", detail.phone)
                    CpStatRow("Pending session", detail.pendingSessionId ?: "none")
                    val history = store.sessions.filter { it.clientId == detail.id }
                    CpVSpace(8)
                    CpSectionTitle("Session history", trailing = "${history.size}")
                    history.forEach { s ->
                        Text(
                            "${s.id} · ${s.dayDate} ${s.time} · ${s.status.label}${if (s.voided) " · voided" else ""}",
                            fontSize = 12.sp,
                            color = CpNight.Muted,
                        )
                    }
                    CpVSpace(10)
                    CpSecondaryButton("Anonymize (soft-delete + null PII)…", enabled = user.role != CpRole.ACCOUNTANT) {
                        val i = store.clients.indexOfFirst { it.id == detail.id }
                        if (i >= 0) {
                            store.clients[i] =
                                detail.copy(
                                    name = "Anonymized record ${detail.id}",
                                    phone = "withheld",
                                    anonymized = true,
                                    pendingSessionId = null,
                                )
                        }
                        store.audit(
                            user.name,
                            "ANONYMIZE",
                            "clients:${detail.id}",
                            "Client anonymized — gender + age retained.",
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun CpFinanceView(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch() ?: return
    val readOnly = user.role == CpRole.ACCOUNTANT
    CpPageTitle("Finance", "${branch.name} · remittance drafts, snapshots, and the 48h undo")
    if (readOnly) {
        Text("Accountant view is read-only — submit and undo are disabled.", fontSize = 13.sp, color = CpNight.Blue)
        CpVSpace(10)
    }
    val collectible =
        store.sessions
            .filter { it.branchId == branch.id && it.dayDate == store.dayFilter.value }
            .filter { it.status == CpSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }
    CpCard(Modifier.fillMaxWidth()) {
        Text(
            "Collectible for ${store.dayFilter.value}: ${cpMoney(collectible)} " +
                "(COMPLETED, non-voided). Voided sessions stay visible but never enter totals.",
            fontSize = 13.sp,
            color = CpNight.Muted,
        )
    }
    CpVSpace()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CpFilterChip("SESSION flow", store.financeKind.value == CpRemitKind.SESSION) {
            store.financeKind.value = CpRemitKind.SESSION
        }
        CpFilterChip("PRODUCT flow", store.financeKind.value == CpRemitKind.PRODUCT) {
            store.financeKind.value = CpRemitKind.PRODUCT
        }
    }
    CpVSpace()
    store.remittances
        .filter { it.branchId == branch.id && it.kind == store.financeKind.value }
        .forEach { remit ->
            CpCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${remit.id} · ${remit.dayDate}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CpNight.Ink,
                        )
                        Text(remit.note, fontSize = 13.sp, color = CpNight.Muted)
                    }
                    CpChip(
                        remit.state.label,
                        if (remit.state == CpSubmission.DRAFT) CpNight.Gold else CpNight.Green,
                    )
                }
                CpVSpace(8)
                CpStatRow("Gross", cpMoney(remit.gross))
                CpStatRow("Deductions", cpMoney(remit.deductions))
                CpStatRow("Net", cpMoney(remit.net))
                if (remit.snapshotId != null) {
                    CpStatRow("Snapshot", "${remit.snapshotId} (immutable)")
                    CpStatRow("Submitted", remit.submittedAt ?: "—")
                }
                if (remit.undoReason != null) CpStatRow("Undo reason", remit.undoReason)
                CpVSpace(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remit.state == CpSubmission.DRAFT) {
                        CpPrimaryButton("Submit (freeze snapshot)", enabled = !readOnly) {
                            val i = store.remittances.indexOfFirst { it.id == remit.id }
                            if (i >= 0) {
                                store.remittances[i] =
                                    remit.copy(
                                        state = CpSubmission.SUBMITTED,
                                        snapshotId = "SNAP-${(8800..8899).random()}",
                                        submittedAt = "${remit.dayDate} 22:10 Asia/Manila",
                                    )
                            }
                            store.audit(
                                user.name,
                                "SUBMIT",
                                "remittances:${remit.id}",
                                "Remittance submitted — snapshot frozen.",
                            )
                            store.notify(
                                "Remittance submitted",
                                "${remit.id} ${remit.kind.label.lowercase()} remittance frozen.",
                                remit.branchId,
                                remit.dayDate,
                            )
                        }
                    } else {
                        CpSecondaryButton("Undo within 48h…", enabled = !readOnly) {
                            store.undoTargetId.value = remit.id
                            store.undoReason.value = ""
                        }
                    }
                }
            }
        }
    if (store.financeKind.value == CpRemitKind.PRODUCT) {
        CpCard(Modifier.fillMaxWidth()) {
            CpSectionTitle("Product pool", trailing = cpMoney(store.productLines.sumOf { it.total }))
            store.productLines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, fontSize = 14.sp, color = CpNight.Ink)
                        Text("${cpMoney(line.unitPrice)} each", fontSize = 12.sp, color = CpNight.Muted)
                    }
                    CpSmallButton("−") {
                        val i = store.productLines.indexOfFirst { it.id == line.id }
                        if (i >= 0 && line.qty > 0) store.productLines[i] = line.copy(qty = line.qty - 1)
                    }
                    Text(
                        "${line.qty}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CpNight.Ink,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    CpSmallButton("+") {
                        val i = store.productLines.indexOfFirst { it.id == line.id }
                        if (i >= 0) store.productLines[i] = line.copy(qty = line.qty + 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(cpMoney(line.total), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                }
            }
            CpVSpace(8)
            Text(
                "Commission split note: product commissions pool per branch day and split equally among " +
                    "practitioners and coordinators clocked in at sold_at time. Overrides below are audited.",
                fontSize = 12.sp,
                color = CpNight.Faint,
            )
            CpVSpace(6)
            val pool = store.productLines.sumOf { it.total }
            val included = store.commissionIncluded.toList()
            val share = if (included.isEmpty()) 0 else pool / included.size
            listOf("Dra. Amara Santos", "Maria Cruz", "Liam Tan").forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = included.contains(name),
                        onCheckedChange = { on ->
                            if (on) store.commissionIncluded.add(name) else store.commissionIncluded.remove(name)
                            store.audit(
                                user.name,
                                "UPDATE",
                                "commission:$name",
                                "Manual commission ${if (on) "inclusion" else "exclusion"}.",
                            )
                        },
                    )
                    Text(name, fontSize = 13.sp, color = CpNight.Ink, modifier = Modifier.weight(1f))
                    Text(cpMoney(if (included.contains(name)) share else 0), fontSize = 13.sp, color = CpNight.Muted)
                }
            }
        }
    }
    if (store.undoTargetId.value != null) {
        val target = store.remittances.firstOrNull { it.id == store.undoTargetId.value }
        AlertDialog(
            onDismissRequest = { store.undoTargetId.value = null },
            title = { Text("Undo ${target?.id ?: ""}", color = CpNight.Ink) },
            text = {
                Column {
                    Text(
                        "Within 48h of submission the remittance returns to Draft, covered days unlock, " +
                            "and the frozen snapshot is deleted. A reason is required.",
                        color = CpNight.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = store.undoReason.value,
                        onValueChange = { store.undoReason.value = it },
                        placeholder = { Text("Reason (required)", color = CpNight.Faint) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = store.undoReason.value.isNotBlank() && target != null,
                    onClick = {
                        val i = store.remittances.indexOfFirst { it.id == target?.id }
                        if (i >= 0 && target != null) {
                            store.remittances[i] =
                                target.copy(
                                    state = CpSubmission.DRAFT,
                                    snapshotId = null,
                                    submittedAt = null,
                                    undoReason = store.undoReason.value.trim(),
                                )
                        }
                        store.audit(
                            user.name,
                            "UNDO",
                            "remittances:${target?.id}",
                            "Undone: ${store.undoReason.value.trim()}",
                        )
                        store.undoTargetId.value = null
                    },
                ) {
                    Text("Undo to Draft", color = CpNight.Amber)
                }
            },
            dismissButton = {
                TextButton(onClick = { store.undoTargetId.value = null }) {
                    Text("Keep submitted", color = CpNight.Muted)
                }
            },
        )
    }
}

@Composable
internal fun CpTeamView(
    store: CpStore,
    user: CpUser,
) {
    CpPageTitle("Team", "Users, branch slots, and role bundles")
    CpTwoCol(
        left = {
            CpSectionTitle("People", trailing = "${store.users.size}")
            store.users.forEach { member ->
                CpCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                            val home =
                                store.branches.firstOrNull { it.id == member.homeBranchId }?.name ?: member.homeBranchId
                            Text("$home · slot ${member.slot}", fontSize = 12.sp, color = CpNight.Muted)
                        }
                        CpChip(
                            member.role.label,
                            if (member.role ==
                                CpRole.ONBOARDING
                            ) {
                                CpNight.Amber
                            } else {
                                CpNight.Accent
                            },
                        )
                    }
                }
            }
            Text(
                "Relief practitioners sort after home slots in reports.",
                fontSize = 12.sp,
                color = CpNight.Faint,
            )
        },
        right = {
            CpSectionTitle("Role bundles", trailing = "capabilities")
            CpRole.entries.forEach { role ->
                CpCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(role.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(role.summary, fontSize = 13.sp, color = CpNight.Muted)
                }
            }
            Text("Signed in as ${user.name} (${user.role.label}).", fontSize = 12.sp, color = CpNight.Faint)
        },
    )
}

@Composable
internal fun CpMailView(
    store: CpStore,
    user: CpUser,
) {
    CpPageTitle("Mailbox", "${store.unreadCount()} unread — relief messages tap through to their branch day")
    if (store.notifications.isEmpty()) {
        CpEmptyNote("Mailbox is quiet — no notifications.")
    } else {
        store.notifications.forEach { note ->
            CpCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = {
                    markCpRead(store, note.id)
                    store.audit(user.name, "READ", "notifications:${note.id}", "Marked notification read.")
                    if (note.branchId != null) {
                        store.currentBranchId.value = note.branchId
                        if (note.dayDate != null) store.dayFilter.value = note.dayDate
                        store.screen.value = CpScreen.HOME
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(note.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(note.body, fontSize = 13.sp, color = CpNight.Muted)
                        if (note.branchId != null) {
                            Spacer(Modifier.height(4.dp))
                            val bName = store.branches.firstOrNull { it.id == note.branchId }?.name ?: note.branchId
                            Text("Opens $bName · ${note.dayDate ?: ""}", fontSize = 12.sp, color = CpNight.Accent)
                        }
                    }
                    if (!note.read) CpChip("Unread", CpNight.Gold) else CpChip("Read", CpNight.Faint)
                }
            }
        }
    }
    Text("Read rows are kept forever as history.", fontSize = 12.sp, color = CpNight.Faint)
}

@Composable
internal fun CpAuditView(store: CpStore) {
    CpPageTitle("Audit log", "Newest first — every mutation above prepends an entry")
    TextField(
        value = store.auditQuery.value,
        onValueChange = { store.auditQuery.value = it },
        placeholder = { Text("Filter actor, action, target…", color = CpNight.Faint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CpVSpace()
    val list =
        store.audit.filter {
            store.auditQuery.value.isBlank() ||
                "${it.actor} ${it.action} ${it.target} ${it.detail}".contains(store.auditQuery.value, ignoreCase = true)
        }
    if (list.isEmpty()) {
        CpEmptyNote("No audit entries match.")
    } else {
        list.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(entry.time, fontSize = 12.sp, color = CpNight.Faint, modifier = Modifier.width(120.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entry.actor} · ${entry.action} · ${entry.target}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CpNight.Ink,
                    )
                    Text(entry.detail, fontSize = 12.sp, color = CpNight.Muted)
                }
            }
            androidx.compose.material3.HorizontalDivider(color = CpNight.Hairline, thickness = 1.dp)
        }
    }
}

@Composable
internal fun CpProfileView(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch()
    CpPageTitle("Profile", "Identity, capabilities, and sign-out")
    CpTwoCol(
        left = {
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Who am I")
                CpStatRow("Name", user.name)
                CpStatRow("Role", user.role.label)
                CpStatRow(
                    "Home branch",
                    store.branches.firstOrNull { it.id == user.homeBranchId }?.name ?: user.homeBranchId,
                )
                CpStatRow("Working at", branch?.name ?: "—")
                CpVSpace(6)
                Text(user.role.summary, fontSize = 13.sp, color = CpNight.Muted)
            }
        },
        right = {
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Session")
                CpStatRow("Clock", if (store.clockedIn.value) "Clocked in" else "Clocked out")
                CpVSpace(8)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (store.clockedIn.value) {
                        CpSecondaryButton("Clock out") {
                            store.clockedIn.value = false
                            store.audit(user.name, "CLOCK_OUT", "attendance:${branch?.id}", "Clocked out from profile.")
                        }
                    } else {
                        CpSecondaryButton("Clock in") {
                            store.clockedIn.value = true
                            store.audit(user.name, "CLOCK_IN", "attendance:${branch?.id}", "Clocked in from profile.")
                        }
                    }
                    CpPrimaryButton("Sign out") {
                        store.audit(user.name, "SIGN_OUT", "users:${user.id}", "Signed out.")
                        store.currentUserId.value = null
                        store.currentBranchId.value = null
                        store.clockedIn.value = false
                        store.reliefEdit.value = false
                        store.screen.value = CpScreen.HOME
                    }
                }
            }
        },
    )
}
