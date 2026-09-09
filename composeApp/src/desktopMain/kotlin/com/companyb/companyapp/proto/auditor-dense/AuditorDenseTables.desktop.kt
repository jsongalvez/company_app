package com.companyb.companyapp.proto.auditordense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ADSessions(store: ADFakeStore) {
    var statusFilter by remember { mutableStateOf<ADSessionStatus?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val branchId = store.currentBranchId.value
    val rows = store.sessions.filter {
        it.branchId == branchId &&
            (statusFilter == null || it.status == statusFilter) &&
            (query.isBlank() || it.clientName.contains(query, true) || it.id.contains(query, true))
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ADFilterBar {
            Text("FILTER", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            ADChip("ALL", ADTheme.slate, filled = statusFilter == null) { statusFilter = null }
            ADSessionStatus.entries.forEach { s ->
                ADChip(s.label, statusColor(s), filled = statusFilter == s) {
                    statusFilter = if (statusFilter == s) null else s
                }
            }
            Spacer(Modifier.weight(1f))
            ADNum("${rows.size} ROWS")
            ADAction("＋ New session") { showNew = true }
        }
        ADFilterBar {
            Text("SEARCH", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("client name or session id…", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.6f).fillMaxHeight()) {
                ADHeaderRow {
                    ADHead("TIME", Modifier.weight(0.6f))
                    ADHead("ID", Modifier.weight(0.7f))
                    ADHead("CLIENT", Modifier.weight(1.4f))
                    ADHead("STATUS", Modifier.weight(0.9f))
                    ADHead("NET", Modifier.weight(0.8f), alignEnd = true)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, s -> s.id }) { i, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    when {
                                        selected == s.id -> ADTheme.paperDeep
                                        s.voided -> ADTheme.voidWash
                                        i % 2 == 1 -> ADTheme.zebra
                                        else -> ADTheme.card
                                    },
                                )
                                .clickable { selected = s.id }
                                .border(1.dp, ADTheme.hairline)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ADCell(s.time, mono = true, modifier = Modifier.weight(0.6f))
                            ADCell(s.id, mono = true, modifier = Modifier.weight(0.7f))
                            ADCell((if (s.voided) "∅ " else "") + s.clientName,
                                modifier = Modifier.weight(1.4f))
                            Box(Modifier.weight(0.9f)) { ADChip(s.status.label, statusColor(s.status)) }
                            ADNum(if (s.voided) "∅ ${adPeso(s.price)}" else adPeso(s.price),
                                modifier = Modifier.weight(0.8f))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val live = rows.filter { it.status == ADSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ADNum("FILTERED COMPLETED Σ ${adPeso(live)}", weight = FontWeight.Bold)
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val detail = rows.firstOrNull { it.id == selected } ?: rows.firstOrNull()
                if (detail == null) {
                    ADNote("No rows match the filter.")
                } else {
                    ADSessionDetail(store, detail)
                }
            }
        }
    }
    if (showNew) {
        ADNewSession(store) { showNew = false }
    }
}

@Composable
private fun ADSessionDetail(store: ADFakeStore, s: ADSession) {
    var voidOpen by remember { mutableStateOf(false) }
    var voidReason by remember { mutableStateOf("") }
    val canEdit = s.status == ADSessionStatus.PENDING && !s.voided
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
            .background(ADTheme.card).border(1.dp, ADTheme.hairline, RoundedCornerShape(4.dp))
            .padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("FOLIO ${s.id}", color = ADTheme.slate, fontSize = 12.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        ADRuleRow("Client", s.clientName)
        ADRuleRow("When", "${s.dayDate} · ${s.time}")
        ADRuleRow("Type", "${s.type}${if (s.walkIn) " · WALK-IN" else ""}")
        ADRuleRow("Practitioners", s.practitioners)
        ADRuleRow("Price", adPeso(s.price))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Status", color = ADTheme.muted, fontSize = 11.sp, modifier = Modifier.width(150.dp))
            ADChip(s.status.label, statusColor(s.status), filled = true)
            if (s.voided) ADChip("VOID", ADTheme.debit, filled = true)
        }
        if (s.voided) ADNote("Void reason: ${s.voidReason ?: "—"}. Excluded from finance totals, record kept.")
        if (s.walkIn) {
            ADNote("Walk-in rule: NO_SHOW and CANCELLED are disabled — a walk-in is either served or never existed.")
        }
        Spacer(Modifier.height(4.dp))
        Text("POST", color = ADTheme.muted, fontSize = 10.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ADAction("Complete", enabled = canEdit) { store.transitionSession(s.id, ADSessionStatus.COMPLETED) }
            ADAction("No-show", enabled = canEdit && !s.walkIn) {
                store.transitionSession(s.id, ADSessionStatus.NO_SHOW)
            }
            ADAction("Cancel", enabled = canEdit && !s.walkIn) {
                store.transitionSession(s.id, ADSessionStatus.CANCELLED)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("VOID LEDGER", color = ADTheme.muted, fontSize = 10.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.voided) {
                ADAction("Unvoid") { store.unvoidSession(s.id) }
            } else {
                ADAction("Void with reason…", danger = true) { voidOpen = true }
            }
        }
        if (voidOpen) {
            Spacer(Modifier.height(4.dp))
            TextField(
                value = voidReason,
                onValueChange = { voidReason = it },
                placeholder = { Text("reason (required)…", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ADAction("Confirm void", enabled = voidReason.isNotBlank()) {
                    store.voidSession(s.id, voidReason.trim())
                    voidOpen = false
                    voidReason = ""
                }
                ADAction("Cancel") { voidOpen = false }
            }
        }
    }
}

@Composable
private fun ADNewSession(store: ADFakeStore, onClose: () -> Unit) {
    var clientId by remember { mutableStateOf(store.clients.first().id) }
    var type by remember { mutableStateOf("FOLLOW-UP") }
    var price by remember { mutableStateOf("900") }
    var walkIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.55f).clip(RoundedCornerShape(4.dp))
                .background(ADTheme.card).border(1.dp, ADTheme.hairline).padding(14.dp)
                .clickable(enabled = false, onClick = {}).verticalScroll(rememberScrollState()),
        ) {
            Text("BOOK SESSION — ${store.currentDayDate.value}", color = ADTheme.ink, fontSize = 15.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("CLIENT (global — at most one PENDING each)", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            store.clients.forEach { c ->
                val blocked = store.clientPending(c.id) != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !blocked) { clientId = c.id }
                        .padding(vertical = 3.dp),
                ) {
                    Text(if (c.id == clientId) "◉" else "○", color = ADTheme.info, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    ADCell("${c.name} · ${c.age}${c.gender}${if (c.anonymized) " · ANON" else ""}",
                        modifier = Modifier.weight(1f))
                    if (blocked) ADChip("PENDING HELD", ADTheme.amber)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("TYPE", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("INITIAL", "FOLLOW-UP", "REHAB").forEach { t ->
                    ADChip(t, ADTheme.slate, filled = type == t) { type = t }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PRICE ₱", color = ADTheme.muted, fontSize = 11.sp, fontFamily = ADTheme.mono)
                Box(Modifier.width(140.dp)) {
                    TextField(value = price, onValueChange = { price = it.filter(Char::isDigit).take(6) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                ADCheckRow("walk-in", walkIn) { walkIn = it }
            }
            if (error != null) {
                Spacer(Modifier.height(4.dp))
                Text(error!!, color = ADTheme.debit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADAction("Book") {
                    val err = store.createSession(clientId, type, price.toIntOrNull() ?: 0, walkIn)
                    if (err == null) onClose() else error = err
                }
                ADAction("Cancel", onClick = onClose)
            }
        }
    }
}

@Composable
fun ADClients(store: ADFakeStore) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val rows = store.clients.filter {
        query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ADFilterBar {
            Text("SEARCH", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("global client register…", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(4.dp))
            ADNum("${rows.size} ROWS")
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight()) {
                ADHeaderRow {
                    ADHead("ID", Modifier.weight(0.7f))
                    ADHead("CLIENT", Modifier.weight(1.6f))
                    ADHead("AGE", Modifier.weight(0.5f), alignEnd = true)
                    ADHead("PENDING", Modifier.weight(1f))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, c -> c.id }) { i, c ->
                        val pending = store.clientPending(c.id)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    when {
                                        selected == c.id -> ADTheme.paperDeep
                                        i % 2 == 1 -> ADTheme.zebra
                                        else -> ADTheme.card
                                    },
                                )
                                .clickable { selected = c.id }
                                .border(1.dp, ADTheme.hairline)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ADCell(c.id, mono = true, modifier = Modifier.weight(0.7f))
                            ADCell(c.name, modifier = Modifier.weight(1.6f))
                            ADNum("${c.age}${c.gender}", modifier = Modifier.weight(0.5f))
                            Box(Modifier.weight(1f)) {
                                if (pending != null) ADChip(pending.id, ADTheme.amber, filled = true)
                                else ADCell("—", color = ADTheme.faint)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                ADNote("Client is global across branches. At most one PENDING session at a time — " +
                    "booking is blocked while a PENDING row is held.")
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val c = rows.firstOrNull { it.id == selected } ?: rows.firstOrNull()
                if (c == null) {
                    ADNote("No rows match.")
                } else {
                    ADClientDetail(store, c)
                }
            }
        }
    }
}

@Composable
private fun ADClientDetail(store: ADFakeStore, c: ADClient) {
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
            .background(ADTheme.card).border(1.dp, ADTheme.hairline, RoundedCornerShape(4.dp))
            .padding(10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("DOSSIER ${c.id}", color = ADTheme.slate, fontSize = 12.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        ADRuleRow("Name", c.name)
        ADRuleRow("Age / gender", "${c.age} / ${c.gender}")
        ADRuleRow("Contact", c.phone)
        val pending = store.clientPending(c.id)
        ADRuleRow("PENDING held", pending?.id ?: "none")
        if (c.anonymized) {
            ADNote("Anonymized view: PII nulled. Gender + age retained for reporting.")
        } else {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ADAction("Anonymize", danger = true) { store.anonymizeClient(c.id) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("SESSION HISTORY", color = ADTheme.muted, fontSize = 10.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        store.sessions.filter { it.clientId == c.id }.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ADCell("${s.dayDate} ${s.time}", mono = true, modifier = Modifier.weight(1.4f))
                ADCell(s.id, mono = true, modifier = Modifier.weight(0.7f))
                Box(Modifier.weight(1f)) { ADChip(s.status.label, statusColor(s.status)) }
                ADNum(adPeso(s.price), modifier = Modifier.weight(0.7f))
            }
        }
    }
}

@Composable
fun ADFinance(store: ADFakeStore) {
    var kind by remember { mutableStateOf(ADRemitKind.SESSION) }
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    val branchId = store.currentBranchId.value
    val date = store.currentDayDate.value
    val pool = store.productLines.sumOf { it.total }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ADFilterBar {
            Text("FLOW", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            ADChip("SESSION", ADTheme.credit, filled = kind == ADRemitKind.SESSION) {
                kind = ADRemitKind.SESSION
            }
            ADChip("PRODUCT", ADTheme.info, filled = kind == ADRemitKind.PRODUCT) {
                kind = ADRemitKind.PRODUCT
            }
            Spacer(Modifier.weight(1f))
            ADNum("DAY $date")
        }
        if (kind == ADRemitKind.SESSION) {
            ADLedgerCard("SESSION REMITTANCE — ${store.branchName(branchId).uppercase()}") {
                ADHeaderRow {
                    ADHead("ID", Modifier.weight(0.7f))
                    ADHead("DAY", Modifier.weight(0.9f))
                    ADHead("GROSS", Modifier.weight(0.8f), alignEnd = true)
                    ADHead("DEDUCT", Modifier.weight(0.8f), alignEnd = true)
                    ADHead("NET", Modifier.weight(0.8f), alignEnd = true)
                    ADHead("STATE", Modifier.weight(0.9f))
                    ADHead("OPS", Modifier.weight(1.4f))
                }
                store.remittances.filter { it.kind == ADRemitKind.SESSION }.forEachIndexed { i, r ->
                    val live = store.sessionGross(r.branchId, r.dayDate)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                            .border(1.dp, ADTheme.hairline)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ADCell(r.id, mono = true, modifier = Modifier.weight(0.7f))
                        ADCell(r.dayDate, mono = true, modifier = Modifier.weight(0.9f))
                        ADNum(adPeso(live), modifier = Modifier.weight(0.8f))
                        ADNum(adPeso(r.deductions), modifier = Modifier.weight(0.8f))
                        ADNum(adPeso(live - r.deductions), weight = FontWeight.Bold,
                            modifier = Modifier.weight(0.8f))
                        Box(Modifier.weight(0.9f)) {
                            ADChip(r.state.label,
                                if (r.state == ADSubmission.SUBMITTED) ADTheme.info else ADTheme.amber,
                                filled = r.state == ADSubmission.SUBMITTED)
                        }
                        Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (r.state == ADSubmission.DRAFT) {
                                ADAction("Submit") { store.submitRemittance(r.id) }
                            } else {
                                ADAction("Undo 48h…") { undoFor = r.id }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                ADNote("Gross = COMPLETED, non-voided sessions on the covered day (live — voiding a " +
                    "session moves the draft gross). Submit freezes immutable snapshot " +
                    "${store.remittances.firstOrNull { it.state == ADSubmission.SUBMITTED }?.snapshotId ?: "—"}; " +
                    "Undo reopens to draft and deletes the snapshot within 48h, reason required.")
                if (undoFor != null) {
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        placeholder = { Text("undo reason (required)…", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADAction("Confirm undo", enabled = undoReason.isNotBlank()) {
                            store.undoRemittance(undoFor!!, undoReason.trim())
                            undoFor = null
                            undoReason = ""
                        }
                        ADAction("Cancel") { undoFor = null }
                    }
                }
            }
        } else {
            ADLedgerCard("PRODUCT REMITTANCE — POOL & SPLIT") {
                ADHeaderRow {
                    ADHead("LINE", Modifier.weight(1.8f))
                    ADHead("UNIT", Modifier.weight(0.8f), alignEnd = true)
                    ADHead("QTY", Modifier.weight(0.9f), alignEnd = true)
                    ADHead("TOTAL", Modifier.weight(0.9f), alignEnd = true)
                }
                store.productLines.forEachIndexed { i, line ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                            .border(1.dp, ADTheme.hairline)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ADCell(line.name, modifier = Modifier.weight(1.8f))
                        ADNum(adPeso(line.unitPrice), modifier = Modifier.weight(0.8f))
                        Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            ADAction("−") { store.bumpQty(line.id, -1) }
                            Spacer(Modifier.width(6.dp))
                            ADNum("${line.qty}", weight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            ADAction("+") { store.bumpQty(line.id, 1) }
                        }
                        ADNum(adPeso(line.total), weight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ADNum("POOL Σ ${adPeso(pool)}", weight = FontWeight.Bold)
                }
            }
            ADLedgerCard("COMMISSION SPLIT — POOLED PER BRANCH DAY") {
                val crew = store.clockRoster
                val head = if (crew.isEmpty()) 0 else pool / crew.size
                ADNote("Product commissions pool per branch day and split equally among all " +
                    "practitioners and coordinators clocked in at sold_at. Separate from compensation, " +
                    "not subject to remittance. Manual overrides below are audited.")
                Spacer(Modifier.height(4.dp))
                crew.forEach { name ->
                    var included by remember(name) { mutableStateOf(true) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADCheckRow(name, included) {
                            included = it
                            store.record("UPDATE", "commission_override", name,
                                if (it) "Manually included in split" else "Manually excluded from split")
                        }
                        Spacer(Modifier.weight(1f))
                        ADNum(if (included) adPeso(head) else "∅ ${adPeso(0)}")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ADNum("PER HEAD ${adPeso(head)} ÷ ${crew.size}", weight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val draft = store.remittances.firstOrNull {
                        it.kind == ADRemitKind.PRODUCT && it.state == ADSubmission.DRAFT
                    }
                    if (draft != null) ADAction("Submit product draft ${draft.id}") {
                        store.submitRemittance(draft.id)
                    }
                }
            }
        }
    }
}

@Composable
fun ADAudit(store: ADFakeStore) {
    var query by remember { mutableStateOf("") }
    val rows = store.audit.filter {
        query.isBlank() ||
            it.action.contains(query, true) || it.table.contains(query, true) ||
            it.actor.contains(query, true) || it.record.contains(query, true)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ADFilterBar {
            Text("FILTER", color = ADTheme.muted, fontSize = 10.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("action / table / actor / record…", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(4.dp))
            ADChip("${rows.size} ENTRIES", ADTheme.slate, filled = true)
        }
        ADNote("First-class ledger: every mutation in this prototype prepends a hash-chained row, " +
            "newest first. Immutable — who changed what, when, and why.")
        ADHeaderRow {
            ADHead("SEQ", Modifier.weight(0.5f))
            ADHead("HASH", Modifier.weight(0.8f))
            ADHead("TIME", Modifier.weight(0.8f))
            ADHead("ACTOR", Modifier.weight(1.1f))
            ADHead("ACTION", Modifier.weight(0.9f))
            ADHead("TABLE", Modifier.weight(1f))
            ADHead("RECORD", Modifier.weight(0.9f))
            ADHead("DETAIL", Modifier.weight(2f))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, e -> e.seq }) { i, e ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (i % 2 == 1) ADTheme.zebra else ADTheme.card)
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADNum("${e.seq}", modifier = Modifier.weight(0.5f))
                    ADCell(e.hash, mono = true, color = ADTheme.muted, modifier = Modifier.weight(0.8f))
                    ADCell(e.time, mono = true, modifier = Modifier.weight(0.8f))
                    ADCell(e.actor, modifier = Modifier.weight(1.1f))
                    Box(Modifier.weight(0.9f)) {
                        ADChip(e.action, auditColor(e.action))
                    }
                    ADCell(e.table, mono = true, modifier = Modifier.weight(1f))
                    ADCell(e.record, mono = true, modifier = Modifier.weight(0.9f))
                    ADCell(e.detail, modifier = Modifier.weight(2f))
                }
            }
        }
    }
}

private fun auditColor(action: String): Color =
    when (action) {
        "SUBMIT" -> ADTheme.info
        "UNDO" -> ADTheme.debit
        "LOGIN", "LOGOUT" -> ADTheme.slate
        "READ", "OPEN" -> ADTheme.muted
        "SEED" -> ADTheme.faint
        else -> ADTheme.credit
    }
