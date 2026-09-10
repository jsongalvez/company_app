package com.companyb.companyapp.proto.floatingpanels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun FloatCenterStage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(FloatCanvasDeep), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxWidth(0.55f).shadow(24.dp, RoundedCornerShape(20.dp)).background(FloatPanel, RoundedCornerShape(20.dp)).padding(26.dp),
        ) {
            content()
        }
    }
}

@Composable
fun FloatLogin(
    onEnter: (String) -> Unit,
    onPreviewOnboarding: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    FloatCenterStage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { FloatGrip() }
            Text("❖", fontSize = 30.sp, color = FloatAccent)
            Text("CompanyApp", fontSize = 24.sp, fontWeight = FontWeight.Black, color = FloatInk)
            FloatNote("Floating-panels edition — every tool drifts on its own panel")
            FloatField(email, { email = it }, "Work email", modifier = Modifier.fillMaxWidth())
            FloatButton("Sign in ▸", onClick = { onEnter(email) })
            FloatLink("Preview the ONBOARDING gate", onClick = onPreviewOnboarding)
            FloatNote("Any email works — fake directory, no network")
        }
    }
}

@Composable
fun FloatOnboardingLocked(onBack: () -> Unit) {
    FloatCenterStage {
        FloatPanelCard(title = "Observe only", kicker = "Capability bundle: empty", focused = true) {
            FloatBody("Newcomers hold no capabilities until a Coordinator clears them. Panels stay dimmed except this notice.")
            FloatStamp("LOCKED", FloatAmber)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatOutline("Back to sign in", onClick = onBack)
            }
        }
    }
}

@Composable
fun FloatBranchSelect(
    repo: FloatFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(FloatCanvasDeep).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.7f).shadow(24.dp, RoundedCornerShape(20.dp)).background(FloatPanel, RoundedCornerShape(20.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { FloatGrip() }
            FloatKicker("Drift ▸ Pick a branch")
            FloatTitle("Choose your canvas")
            repo.branches.forEachIndexed { idx, b ->
                val color =
                    when (b.day) {
                        FloatDay.OPEN -> FloatGreen
                        FloatDay.PAST -> FloatAmber
                        FloatDay.REMITTED -> FloatAccentDeep
                    }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = (idx * 8).dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp))
                        .background(FloatPanelDim, RoundedCornerShape(14.dp))
                        .border(1.dp, FloatPanelEdge, RoundedCornerShape(14.dp))
                        .clickable { repo.branchId = b.id; onPick() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FloatInk)
                        FloatNote("${b.place} · ${repo.sessions.count { it.branchId == b.id }} sessions on the sheet")
                    }
                    FloatStamp(b.day.name, color)
                }
            }
            FloatNote("Branch day rolls at 04:00 Asia/Manila — OPEN takes work, PAST is read-only, REMITTED is sealed.")
            FloatLink("‹ Back to sign in", onBack)
        }
    }
}

@Composable
private fun FloatZoomCard(
    title: String,
    kicker: String,
    offset: Int = 0,
    content: @Composable () -> Unit,
) {
    var zoomed by remember { mutableStateOf(false) }
    FloatPanelCard(
        title = title,
        kicker = kicker,
        focused = zoomed,
        onFocus = { zoomed = !zoomed },
        modifier = Modifier.fillMaxWidth().padding(start = offset.dp),
    ) {
        content()
    }
}

@Composable
fun FloatHome(repo: FloatFakeRepo) {
    var ask by remember { mutableStateOf("") }
    val mine = repo.branchSessions()
    val done = mine.count { it.status == FloatStatus.COMPLETED }
    val pend = mine.count { it.status == FloatStatus.PENDING }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FloatZoomCard("Shift", "Tray panel", offset = 0) {
                FloatBody(if (repo.clockedIn) "Clocked in — hands on the sheet." else "Clocked out — press Clock in to start.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        FloatOutline("Clock out", onClick = { repo.clock(false) })
                    } else {
                        FloatButton("Clock in", onClick = { repo.clock(true) })
                    }
                }
            }
            FloatZoomCard("Day in figures", "Ledger panel", offset = 10) {
                FloatBody("$pend pending · $done completed · ${mine.size} on sheet")
                FloatNote("Walk-ins today: ${mine.count { it.kind == FloatKind.WALK_IN }}")
            }
        }
        FloatZoomCard("Relief duty", "Cover panel") {
            if (repo.relief.isEmpty()) FloatNote("No relief traffic — quiet week.")
            repo.relief.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, FloatPanelEdge, RoundedCornerShape(12.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (if (r.kind == FloatReliefKind.INVITE) "Invite · " else "Request · ") + "${r.branch} · ${r.day}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = FloatInk,
                        )
                        FloatNote(r.note)
                        if (r.answered.isNotBlank()) FloatNote("Answer: ${r.answered}")
                    }
                    if (r.answered.isBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FloatButton("Accept", onClick = { repo.answerRelief(r.id, "ACCEPTED") })
                            FloatOutline("Decline", onClick = { repo.answerRelief(r.id, "DECLINED") })
                        }
                    } else {
                        FloatStamp(r.answered, FloatGreen)
                    }
                }
            }
            FloatGap()
            FloatKicker("Ask for relief")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatField(ask, { ask = it }, "Reason (e.g. dental Wed 10:00)", modifier = Modifier.weight(1f))
                FloatButton("Send", onClick = { repo.askRelief(ask); ask = "" })
            }
        }
    }
}

private fun FloatSession.matchFilter(filter: String): Boolean =
    when (filter) {
        "PEND" -> status == FloatStatus.PENDING
        "DONE" -> status == FloatStatus.COMPLETED
        "N-SHOW" -> status == FloatStatus.NO_SHOW
        "CXLD" -> status == FloatStatus.CANCELLED
        else -> true
    }

private fun FloatStatus.stampColor() =
    when (this) {
        FloatStatus.PENDING -> FloatAmber
        FloatStatus.COMPLETED -> FloatGreen
        FloatStatus.NO_SHOW -> FloatGold
        FloatStatus.CANCELLED -> FloatRed
    }

@Composable
fun FloatSessions(repo: FloatFakeRepo, filter: String) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    val rows = repo.branchSessions().filter { it.matchFilter(filter) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatZoomCard("Walk-in intake", "Intake panel") {
            FloatNote("Walk-ins never take NO_SHOW or CANCELLED — they are either PENDING or COMPLETED.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatField(name, { name = it }, "Name (blank = anonymous)", modifier = Modifier.weight(1f))
                FloatField(service, { service = it }, "Service", modifier = Modifier.weight(1f))
                FloatButton("Add", onClick = { repo.addWalkIn(name, service); name = ""; service = "" })
            }
        }
        if (rows.isEmpty()) {
            FloatZoomCard("Nothing floating", "Empty panel") {
                FloatNote("No sessions match $filter on this sheet.")
            }
        }
        rows.forEachIndexed { idx, s ->
            var zoomed by remember(s.id) { mutableStateOf(false) }
            FloatPanelCard(
                title = "${s.client} · ${s.service}",
                kicker = "Session panel",
                focused = zoomed,
                onFocus = { zoomed = !zoomed },
                modifier = Modifier.fillMaxWidth().padding(start = ((idx % 3) * 8).dp),
            ) {
                FloatNote("${s.time} · ${s.kind.name} · ${s.practitioner} · ₱${"%.2f".format(s.amount)}")
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FloatNote("Tap the card to open its tools.")
                    FloatStamp(s.status.name, s.status.stampColor())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { openId = if (openId == s.id) null else s.id }) {
                    FloatGhostButton(if (openId == s.id) "Fold tools ▴" else "Unfold tools ▾", onClick = { openId = if (openId == s.id) null else s.id })
                }
                if (openId == s.id) {
                    FloatGap()
                    if (s.voidReason.isNotBlank()) FloatNote("Void reason on file: ${s.voidReason}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatOutline("Complete", onClick = { repo.setStatus(s.id, FloatStatus.COMPLETED) })
                        val walkIn = s.kind == FloatKind.WALK_IN
                        FloatOutline("No-show", onClick = { if (!walkIn) repo.setStatus(s.id, FloatStatus.NO_SHOW) }, enabled = !walkIn)
                        FloatOutline("Cancel", onClick = { if (!walkIn) repo.setStatus(s.id, FloatStatus.CANCELLED) }, enabled = !walkIn)
                    }
                    FloatGap()
                    FloatField(reason, { reason = it }, "Reason (void / unvoid)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatOutline("Void ▸", onClick = { repo.voidSession(s.id, reason.ifBlank { "No reason given" }); reason = "" })
                        FloatOutline("Unvoid ▸", onClick = { repo.unvoidSession(s.id, reason.ifBlank { "Reinstated" }); reason = "" })
                    }
                }
            }
        }
    }
}

@Composable
fun FloatClients(repo: FloatFakeRepo, veilLifted: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatZoomCard("House policy", "Policy panel") {
            FloatBody("Clients are global across branches. At most one PENDING session per client — the book refuses double-booking.")
            FloatNote(if (veilLifted) "Veil lifted — full names visible." else "Anonymized view — codes only until the veil is lifted in the dock above.")
        }
        repo.clients.forEachIndexed { idx, c ->
            FloatPanelCard(
                title = if (veilLifted) c.name else c.code,
                kicker = "Client panel",
                modifier = Modifier.fillMaxWidth().padding(start = ((idx % 3) * 8).dp),
            ) {
                FloatNote("${c.code} · ${c.visits} visits · ${c.pending} pending")
                FloatNote(c.note)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    FloatNote(if (veilLifted) "Full name shown" else "Name veiled")
                    if (c.pending > 0) FloatStamp("PENDING ×${c.pending}", FloatAmber) else FloatStamp("CLEAR", FloatGreen)
                }
            }
        }
    }
}

@Composable
fun FloatFinance(repo: FloatFakeRepo) {
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    val sessionNet = repo.drafts.filter { it.kind == FloatDraftKind.SESSION && !it.undone }.sumOf { it.amount }
    val productNet = repo.drafts.filter { it.kind == FloatDraftKind.PRODUCT && !it.undone }.sumOf { it.amount * it.qty }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatZoomCard("Commission split", "Split panel") {
            FloatBody("SESSION net splits House 60 / Practitioner 40. PRODUCT margin stays with the branch.")
            FloatNote("Session net ₱${"%.2f".format(sessionNet)} · Product net ₱${"%.2f".format(productNet)}")
        }
        repo.drafts.forEach { d ->
            FloatZoomCard(
                title = d.label,
                kicker = "Remittance panel · ${d.kind.name}",
            ) {
                FloatNote(
                    if (d.kind == FloatDraftKind.PRODUCT) {
                        "₱${"%.2f".format(d.amount)} × ${d.qty} = ₱${"%.2f".format(d.amount * d.qty)}"
                    } else {
                        "₱${"%.2f".format(d.amount)}"
                    },
                )
                if (d.submitted) FloatNote("Snapshot ${d.snapshot}")
                if (d.undone) FloatNote("Undone: ${d.undoReason}")
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    FloatNote(if (d.kind == FloatDraftKind.SESSION) "SESSION takings" else "PRODUCT retail")
                    when {
                        d.undone -> FloatStamp("UNDONE", FloatRed)
                        d.submitted -> FloatStamp("SEALED", FloatAccentDeep)
                        else -> FloatStamp("DRAFT", FloatAmber)
                    }
                }
                FloatGap()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!d.submitted) {
                        FloatButton("Submit ▸", onClick = { repo.submitDraft(d.id) })
                    } else if (!d.undone) {
                        FloatOutline("Undo (48h)", onClick = { undoFor = if (undoFor == d.id) null else d.id })
                    }
                }
                if (undoFor == d.id && d.submitted && !d.undone) {
                    FloatGap()
                    FloatField(undoReason, { undoReason = it }, "Undo reason", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FloatButton("Confirm undo", onClick = { repo.undoDraft(d.id, undoReason.ifBlank { "Counted twice" }); undoReason = ""; undoFor = null })
                        FloatLink("Keep sealed", onClick = { undoFor = null })
                    }
                }
            }
        }
    }
}

@Composable
fun FloatTeam(repo: FloatFakeRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repo.mates.forEachIndexed { idx, m ->
            FloatPanelCard(
                title = m.name,
                kicker = "Teammate panel",
                modifier = Modifier.fillMaxWidth().padding(start = ((idx % 3) * 8).dp),
            ) {
                FloatNote("${m.role} · ${m.note}")
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    FloatNote("Capability: ${if (m.locked) "none" else m.role}")
                    if (m.locked) FloatStamp("ONBOARDING", FloatAmber) else FloatStamp(m.role, FloatAccentDeep)
                }
            }
        }
        FloatNote("Capability glance: only Coordinators approve relief, only Managers sign remittance, ONBOARDING holds none.")
    }
}

@Composable
fun FloatMailbox(repo: FloatFakeRepo) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(repo.notices, key = { it.id }) { n ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .background(if (n.read) FloatPanelDim else FloatPanel, RoundedCornerShape(14.dp))
                    .border(1.dp, if (n.read) FloatPanelEdge else FloatAccent, RoundedCornerShape(14.dp))
                    .clickable { repo.toggleNotice(n.id) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (if (n.read) "" else "● ") + n.title,
                        fontSize = 14.sp,
                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                        color = FloatInk,
                    )
                    FloatNote("${n.body} — ${n.branch} · ${n.day}")
                }
                FloatStamp(if (n.read) "READ" else "NEW", if (n.read) FloatInkFaint else FloatAccent)
            }
        }
        item {
            FloatGap()
            FloatNote("Tap a letter to flip read / unread.")
        }
    }
}

@Composable
fun FloatAudit(repo: FloatFakeRepo) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(repo.audits, key = { it.time + it.action + it.detail }) { a ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(FloatPanelDim, RoundedCornerShape(12.dp))
                    .border(1.dp, FloatPanelEdge, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.time, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FloatInkSoft, modifier = Modifier.weight(0.9f))
                Column(modifier = Modifier.weight(2f)) {
                    Text(a.action, fontSize = 12.sp, fontWeight = FontWeight.Black, color = FloatAccentDeep)
                    FloatNote("${a.actor} · ${a.detail}")
                }
            }
        }
    }
}

@Composable
fun FloatProfile(
    repo: FloatFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatZoomCard("Account", "Me panel") {
            FloatBody(repo.email.ifBlank { "Unnamed practitioner" })
            FloatNote("Role: Practitioner · ${repo.currentBranch().name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    FloatOutline("Clock out", onClick = { repo.clock(false) })
                } else {
                    FloatButton("Clock in", onClick = { repo.clock(true) })
                }
            }
        }
        FloatZoomCard("Branch day", "Day panel") {
            FloatNote("Day rolls at 04:00 Asia/Manila. Move this bureau:")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FloatDayPick("OPEN", repo.currentBranch().day == FloatDay.OPEN) { repo.moveDay(FloatDay.OPEN) }
                FloatDayPick("PAST", repo.currentBranch().day == FloatDay.PAST) { repo.moveDay(FloatDay.PAST) }
                FloatDayPick("REMITTED", repo.currentBranch().day == FloatDay.REMITTED) { repo.moveDay(FloatDay.REMITTED) }
            }
        }
        FloatZoomCard("Off the canvas", "Session panel") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatOutline("Switch branch", onClick = onSwitchBranch)
                FloatOutline("Reset demo", onClick = onReset)
                FloatOutline("Log out", onClick = onLogout)
            }
        }
    }
}

@Composable
private fun FloatDayPick(label: String, on: Boolean, onPick: () -> Unit) {
    if (on) FloatButton(label, onClick = onPick) else FloatOutline(label, onClick = onPick)
}
