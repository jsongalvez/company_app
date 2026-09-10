package com.companyb.companyapp.proto.ribbontabs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
private fun RibbonDoc(title: String, kicker: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RibbonKicker(kicker)
        RibbonTitle(title)
        content()
    }
}

@Composable
fun RibbonLogin(
    onEnter: (String) -> Unit,
    onPreviewOnboarding: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(RibbonBlue), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.background(RibbonPage).border(1.dp, RibbonBlueDark).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("◫", fontSize = 30.sp, color = RibbonBlue)
            Text("CompanyApp", fontSize = 24.sp, fontWeight = FontWeight.Black, color = RibbonInk)
            RibbonNote("Ribbon edition — every tool filed under its tab")
            RibbonField(email, { email = it }, "Work email", modifier = Modifier.fillMaxWidth())
            RibbonButton("Sign in ▸", onClick = { onEnter(email) })
            RibbonLink("Preview the ONBOARDING gate", onClick = onPreviewOnboarding)
            RibbonNote("Any email works — fake directory, no network")
        }
    }
}

@Composable
fun RibbonOnboardingLocked(onBack: () -> Unit) {
    RibbonDoc("ONBOARDING is locked", "File ▸ New ▸ People") {
        RibbonSectionCard("Observe only", "Capability bundle: empty") {
            RibbonBody("Newcomers hold no capabilities until a Coordinator clears them. The ribbon shows every tab greyed except this notice.")
            RibbonStamp("LOCKED", RibbonAmber)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RibbonOutline("Back to sign in", onClick = onBack)
            }
        }
    }
}

@Composable
fun RibbonBranchSelect(
    repo: RibbonFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    RibbonDoc("Pick a branch bureau", "File ▸ Open ▸ Branch") {
        repo.branches.forEach { b ->
            val color =
                when (b.day) {
                    RibbonDay.OPEN -> RibbonGreen
                    RibbonDay.PAST -> RibbonAmber
                    RibbonDay.REMITTED -> RibbonBlueDark
                }
            Row(
                modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge)
                    .clickable { repo.branchId = b.id; onPick() }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
                    RibbonNote("${b.place} · ${repo.sessions.count { it.branchId == b.id }} sessions on the sheet")
                }
                RibbonStamp(b.day.name, color)
            }
        }
        RibbonNote("Branch day rolls at 04:00 Asia/Manila — OPEN takes work, PAST is read-only, REMITTED is sealed.")
        RibbonLink("‹ Back to sign in", onBack)
    }
}

@Composable
fun RibbonHome(repo: RibbonFakeRepo) {
    var ask by remember { mutableStateOf("") }
    val mine = repo.branchSessions()
    val done = mine.count { it.status == RibbonStatus.COMPLETED }
    val pend = mine.count { it.status == RibbonStatus.PENDING }
    RibbonDoc("Good morning — ${repo.currentBranch().name}", "Home tab") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RibbonSectionCard("Shift", "Clipboard group", modifier = Modifier.weight(1f)) {
                RibbonBody(if (repo.clockedIn) "Clocked in — hands on the sheet." else "Clocked out — press Clock in to start.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        RibbonOutline("Clock out", onClick = { repo.clock(false) })
                    } else {
                        RibbonButton("Clock in", onClick = { repo.clock(true) })
                    }
                }
            }
            RibbonSectionCard("Day in figures", "Status bar group", modifier = Modifier.weight(1f)) {
                RibbonBody("$pend pending · $done completed · ${mine.size} on sheet")
                RibbonNote("Walk-ins today: ${mine.count { it.kind == RibbonKind.WALK_IN }}")
            }
        }
        RibbonSectionCard("Relief duty", "Home ▸ Cover group") {
            if (repo.relief.isEmpty()) RibbonNote("No relief traffic — quiet week.")
            repo.relief.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, RibbonBarEdge).padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (if (r.kind == RibbonReliefKind.INVITE) "Invite · " else "Request · ") + "${r.branch} · ${r.day}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = RibbonInk,
                        )
                        RibbonNote(r.note)
                        if (r.answered.isNotBlank()) RibbonNote("Answer: ${r.answered}")
                    }
                    if (r.answered.isBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RibbonButton("Accept", onClick = { repo.answerRelief(r.id, "ACCEPTED") })
                            RibbonOutline("Decline", onClick = { repo.answerRelief(r.id, "DECLINED") })
                        }
                    } else {
                        RibbonStamp(r.answered, RibbonGreen)
                    }
                }
            }
            RibbonEmptyLine()
            RibbonKicker("Ask for relief")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RibbonField(ask, { ask = it }, "Reason (e.g. dental Wed 10:00)", modifier = Modifier.weight(1f))
                RibbonButton("Send", onClick = { repo.askRelief(ask); ask = "" })
            }
        }
    }
}

private fun RibbonSession.matchFilter(filter: String): Boolean =
    when (filter) {
        "PEND" -> status == RibbonStatus.PENDING
        "DONE" -> status == RibbonStatus.COMPLETED
        "N-SHOW" -> status == RibbonStatus.NO_SHOW
        "CXLD" -> status == RibbonStatus.CANCELLED
        else -> true
    }

private fun RibbonStatus.stampColor() =
    when (this) {
        RibbonStatus.PENDING -> RibbonAmber
        RibbonStatus.COMPLETED -> RibbonGreen
        RibbonStatus.NO_SHOW -> RibbonAccent
        RibbonStatus.CANCELLED -> RibbonRed
    }

@Composable
fun RibbonSessions(repo: RibbonFakeRepo, filter: String) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    val rows = repo.branchSessions().filter { it.matchFilter(filter) }
    RibbonDoc("Sessions — ${repo.currentBranch().name}", "Sessions tab ▸ Views group filters above") {
        RibbonSectionCard("Walk-in intake", "Sessions ▸ New group") {
            RibbonNote("Walk-ins never take NO_SHOW or CANCELLED — they are either PENDING or COMPLETED.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RibbonField(name, { name = it }, "Name (blank = anonymous)", modifier = Modifier.weight(1f))
                RibbonField(service, { service = it }, "Service", modifier = Modifier.weight(1f))
                RibbonButton("Add", onClick = { repo.addWalkIn(name, service); name = ""; service = "" })
            }
        }
        if (rows.isEmpty()) {
            RibbonSectionCard("Nothing filed", "Empty view") {
                RibbonNote("No sessions match $filter on this sheet.")
            }
        }
        rows.forEach { s ->
            Column(modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f).clickable { openId = if (openId == s.id) null else s.id }) {
                        Text("${s.client} · ${s.service}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
                        RibbonNote("${s.time} · ${s.kind.name} · ${s.practitioner} · ₱${"%.2f".format(s.amount)}")
                    }
                    RibbonStamp(s.status.name, s.status.stampColor())
                }
                if (openId == s.id) {
                    RibbonEmptyLine()
                    if (s.voidReason.isNotBlank()) RibbonNote("Void reason on file: ${s.voidReason}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RibbonOutline("Complete", onClick = { repo.setStatus(s.id, RibbonStatus.COMPLETED) })
                        val walkIn = s.kind == RibbonKind.WALK_IN
                        RibbonOutline("No-show", onClick = { if (!walkIn) repo.setStatus(s.id, RibbonStatus.NO_SHOW) }, enabled = !walkIn)
                        RibbonOutline("Cancel", onClick = { if (!walkIn) repo.setStatus(s.id, RibbonStatus.CANCELLED) }, enabled = !walkIn)
                    }
                    RibbonEmptyLine()
                    RibbonField(reason, { reason = it }, "Reason (void / unvoid)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RibbonOutline("Void ▸", onClick = { repo.voidSession(s.id, reason.ifBlank { "No reason given" }); reason = "" })
                        RibbonOutline("Unvoid ▸", onClick = { repo.unvoidSession(s.id, reason.ifBlank { "Reinstated" }); reason = "" })
                    }
                }
            }
        }
    }
}

@Composable
fun RibbonClients(repo: RibbonFakeRepo, veilLifted: Boolean) {
    RibbonDoc("Client directory — global", "Clients tab") {
        RibbonSectionCard("House policy", "Clients ▸ Policy group") {
            RibbonBody("Clients are global across branches. At most one PENDING session per client — the book refuses double-booking.")
            RibbonNote(if (veilLifted) "Veil lifted — full names visible." else "Anonymized view — codes only until the veil is lifted in the ribbon above.")
        }
        repo.clients.forEach { c ->
            Row(
                modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (veilLifted) c.name else c.code, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
                    RibbonNote("${c.code} · ${c.visits} visits · ${c.pending} pending")
                    RibbonNote(c.note)
                }
                if (c.pending > 0) RibbonStamp("PENDING ×${c.pending}", RibbonAmber) else RibbonStamp("CLEAR", RibbonGreen)
            }
        }
    }
}

@Composable
fun RibbonFinance(repo: RibbonFakeRepo) {
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    val sessionNet = repo.drafts.filter { it.kind == RibbonDraftKind.SESSION && !it.undone }.sumOf { it.amount }
    val productNet = repo.drafts.filter { it.kind == RibbonDraftKind.PRODUCT && !it.undone }.sumOf { it.amount * it.qty }
    RibbonDoc("Finance — remittance bench", "Finance tab") {
        RibbonSectionCard("Commission split", "Finance ▸ Split group") {
            RibbonBody("SESSION net splits House 60 / Practitioner 40. PRODUCT margin stays with the branch.")
            RibbonNote("Session net ₱${"%.2f".format(sessionNet)} · Product net ₱${"%.2f".format(productNet)}")
        }
        repo.drafts.forEach { d ->
            Column(modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
                        RibbonNote(
                            "${d.kind.name} · " + if (d.kind == RibbonDraftKind.PRODUCT) {
                                "₱${"%.2f".format(d.amount)} × ${d.qty} = ₱${"%.2f".format(d.amount * d.qty)}"
                            } else {
                                "₱${"%.2f".format(d.amount)}"
                            },
                        )
                        if (d.submitted) RibbonNote("Snapshot ${d.snapshot}")
                        if (d.undone) RibbonNote("Undone: ${d.undoReason}")
                    }
                    when {
                        d.undone -> RibbonStamp("UNDONE", RibbonRed)
                        d.submitted -> RibbonStamp("SEALED", RibbonBlueDark)
                        else -> RibbonStamp("DRAFT", RibbonAmber)
                    }
                }
                RibbonEmptyLine()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!d.submitted) {
                        RibbonButton("Submit ▸", onClick = { repo.submitDraft(d.id) })
                    } else if (!d.undone) {
                        RibbonOutline("Undo (48h)", onClick = { undoFor = if (undoFor == d.id) null else d.id })
                    }
                }
                if (undoFor == d.id && d.submitted && !d.undone) {
                    RibbonEmptyLine()
                    RibbonField(undoReason, { undoReason = it }, "Undo reason", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RibbonButton("Confirm undo", onClick = { repo.undoDraft(d.id, undoReason.ifBlank { "Counted twice" }); undoReason = ""; undoFor = null })
                        RibbonLink("Keep sealed", onClick = { undoFor = null })
                    }
                }
            }
        }
    }
}

@Composable
fun RibbonTeam(repo: RibbonFakeRepo) {
    RibbonDoc("Team — people & capabilities", "Team tab") {
        repo.mates.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
                    RibbonNote("${m.role} · ${m.note}")
                }
                if (m.locked) RibbonStamp("ONBOARDING", RibbonAmber) else RibbonStamp(m.role, RibbonBlueDark)
            }
        }
        RibbonNote("Capability glance: only Coordinators approve relief, only Managers sign remittance, ONBOARDING holds none.")
    }
}

@Composable
fun RibbonMailbox(repo: RibbonFakeRepo) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            RibbonKicker("Mail tab ▸ Inbox group")
            RibbonEmptyLine()
            Text("Mailbox", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
            RibbonEmptyLine()
        }
        items(repo.notices, key = { it.id }) { n ->
            Row(
                modifier = Modifier.fillMaxWidth().background(if (n.read) RibbonPage else RibbonBlue.copy(alpha = 0.08f))
                    .border(1.dp, RibbonBarEdge).clickable { repo.toggleNotice(n.id) }.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (if (n.read) "" else "● ") + n.title,
                        fontSize = 14.sp,
                        fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold,
                        color = RibbonInk,
                    )
                    RibbonNote("${n.body} — ${n.branch} · ${n.day}")
                }
                RibbonStamp(if (n.read) "READ" else "NEW", if (n.read) RibbonInkFaint else RibbonBlue)
            }
        }
        item {
            RibbonEmptyLine()
            RibbonNote("Tap a letter to flip read / unread.")
        }
    }
}

@Composable
fun RibbonAudit(repo: RibbonFakeRepo) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            RibbonKicker("Audit tab ▸ Ledger group")
            RibbonEmptyLine()
            Text("Audit log", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RibbonInk)
            RibbonEmptyLine()
        }
        items(repo.audits, key = { it.time + it.action + it.detail }) { a ->
            Row(
                modifier = Modifier.fillMaxWidth().background(RibbonPage).border(1.dp, RibbonBarEdge).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.time, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RibbonInkSoft, modifier = Modifier.weight(0.9f))
                Column(modifier = Modifier.weight(2f)) {
                    Text(a.action, fontSize = 12.sp, fontWeight = FontWeight.Black, color = RibbonBlueDark)
                    RibbonNote("${a.actor} · ${a.detail}")
                }
            }
        }
    }
}

@Composable
fun RibbonProfile(
    repo: RibbonFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    RibbonDoc("Profile & sign-out", "Profile tab") {
        RibbonSectionCard("Account", "Profile ▸ Me group") {
            RibbonBody(repo.email.ifBlank { "Unnamed practitioner" })
            RibbonNote("Role: Practitioner · ${repo.currentBranch().name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    RibbonOutline("Clock out", onClick = { repo.clock(false) })
                } else {
                    RibbonButton("Clock in", onClick = { repo.clock(true) })
                }
            }
        }
        RibbonSectionCard("Branch day", "Profile ▸ Day group") {
            RibbonNote("Day rolls at 04:00 Asia/Manila. Move this bureau:")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RibbonTab2("OPEN", repo.currentBranch().day == RibbonDay.OPEN) { repo.moveDay(RibbonDay.OPEN) }
                RibbonTab2("PAST", repo.currentBranch().day == RibbonDay.PAST) { repo.moveDay(RibbonDay.PAST) }
                RibbonTab2("REMITTED", repo.currentBranch().day == RibbonDay.REMITTED) { repo.moveDay(RibbonDay.REMITTED) }
            }
        }
        RibbonSectionCard("Danger drawer", "Profile ▸ Session group") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RibbonOutline("Switch branch", onClick = onSwitchBranch)
                RibbonOutline("Reset demo", onClick = onReset)
                RibbonOutline("Log out", onClick = onLogout)
            }
        }
    }
}

@Composable
private fun RibbonTab2(label: String, on: Boolean, onPick: () -> Unit) {
    if (on) RibbonButton(label, onClick = onPick) else RibbonOutline(label, onClick = onPick)
}
