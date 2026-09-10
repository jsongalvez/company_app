package com.companyb.companyapp.proto.skeuomorphdesk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WoodBackdrop(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DeskWalnutDark)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(8) { i ->
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .background(if (i % 2 == 0) DeskWalnut else DeskPlank),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .background(DeskWalnutDark.copy(alpha = 0.7f))
                            .align(Alignment.TopCenter),
                    ) {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun LeatherPad(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.background(DeskLeatherDark, MaterialTheme.shapes.large).padding(3.dp),
    ) {
        Box(
            modifier = Modifier.background(DeskLeather, MaterialTheme.shapes.large)
                .border(BorderStroke(1.dp, DeskBrass.copy(alpha = 0.5f)), MaterialTheme.shapes.large),
        ) {
            content()
        }
    }
}

@Composable
fun BrassPlate(title: String, sub: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(DeskBrassDark, MaterialTheme.shapes.small).padding(2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(DeskBrass, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = DeskInk)
            Text(text = sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeskInkSoft)
        }
    }
}

@Composable
fun LedgerPage(title: String, stamp: String = "", stampOk: Boolean = false, body: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp)
                .background(DeskPaperEdge, MaterialTheme.shapes.medium),
        ) {
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(DeskPaper, MaterialTheme.shapes.medium)
                .border(BorderStroke(1.dp, DeskBrassDark), MaterialTheme.shapes.medium)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DeskInk,
                    modifier = Modifier.weight(1f),
                )
                if (stamp.isNotEmpty()) RubberStamp(stamp, ok = stampOk)
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeskPaperEdge)) {
                Spacer(Modifier.height(1.dp))
            }
            Spacer(Modifier.height(10.dp))
            body()
        }
    }
}

@Composable
fun RubberStamp(text: String, ok: Boolean = false) {
    val ink = if (ok) DeskFelt else DeskStamp
    Box(
        modifier = Modifier.graphicsLayer { rotationZ = -4f }
            .border(BorderStroke(2.dp, ink), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Black, fontSize = 12.sp, color = ink)
    }
}

@Composable
fun BrassButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(
            if (enabled) DeskBrassDark else DeskPaperEdge,
            MaterialTheme.shapes.small,
        ).padding(bottom = 3.dp),
    ) {
        Box(
            modifier = Modifier.background(
                if (enabled) DeskBrass else DeskPaperDark,
                MaterialTheme.shapes.small,
            )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeskInk)
        }
    }
}

@Composable
fun InkButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.background(DeskInk, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeskPaper)
    }
}

@Composable
fun StampButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.graphicsLayer { rotationZ = -1.5f }
            .border(BorderStroke(2.dp, DeskStamp), MaterialTheme.shapes.extraSmall)
            .background(DeskStampSoft, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Black, fontSize = 14.sp, color = DeskStamp)
    }
}

@Composable
fun LedgerField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(text = label, color = DeskInkSoft) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DeskPaper,
            unfocusedContainerColor = DeskPaper,
            focusedTextColor = DeskInk,
            unfocusedTextColor = DeskInk,
            focusedBorderColor = DeskBrassDark,
            unfocusedBorderColor = DeskPaperEdge,
        ),
    )
}

@Composable
fun SectionHead(book: String, title: String, sub: String = "") {
    Column {
        Text(text = book, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeskBrass)
        Text(text = title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = DeskPaper)
        if (sub.isNotEmpty()) Text(text = sub, fontSize = 14.sp, color = DeskPaperDark)
    }
}

@Composable
fun DeskDayRibbon(repo: SkeuomorphDeskFakeRepo) {
    val label = when (repo.dayStatus.value) {
        DeskDayStatus.OPEN -> "Branch Day OPEN — the ledger takes ink. Closes 04:00 Asia/Manila."
        DeskDayStatus.PAST -> "Branch Day PAST — turned at 04:00 Asia/Manila. Coordinator ink only."
        DeskDayStatus.REMITTED -> "Branch Day REMITTED — sealed under a filed remittance. Coordinator ink only."
    }
    val ok = repo.dayStatus.value == DeskDayStatus.OPEN
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(DeskPaper, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, DeskBrassDark), MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DeskInk,
                modifier = Modifier.weight(1f),
            )
            RubberStamp(repo.dayStatus.value.name, ok = ok)
        }
    }
}

@Composable
fun DeskLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "EST. MMXXIV", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DeskBrass)
            Text(text = "The Old Desk", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = DeskPaper)
            Text(
                text = "A wooden-desk ledger for branches, sessions, and the till. Leather, brass, paper, ink.",
                fontSize = 16.sp,
                color = DeskPaperDark,
            )
            Spacer(Modifier.height(16.dp))
            RubberStamp("FAKE DATA — NOTHING LEAVES THE DESK")
        }
        Spacer(Modifier.width(48.dp))
        LeatherPad {
            Column(modifier = Modifier.width(360.dp).padding(24.dp)) {
                BrassPlate(title = "SIGN IN", sub = "dip the pen")
                Spacer(Modifier.height(16.dp))
                LedgerField("Name on the door", name) { name = it }
                Spacer(Modifier.height(10.dp))
                LedgerField("Pass word", word) { word = it }
                Spacer(Modifier.height(16.dp))
                BrassButton("Press the signet", onClick = onLogin)
                Spacer(Modifier.height(10.dp))
                InkButton("New hand? Read the house rules") { onOnboarding() }
                Spacer(Modifier.height(12.dp))
                Text(text = "Any name and word opens the drawer. This desk keeps no secrets.", fontSize = 12.sp, color = DeskPaperDark)
            }
        }
    }
}

@Composable
fun DeskOnboardingLocked(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(modifier = Modifier.width(560.dp)) {
            LedgerPage(title = "House rules for the new hand", stamp = "LOCKED") {
                Text(
                    text = "ONBOARDING carries an empty capability bundle: no sessions, no till, no post. " +
                        "A MANAGER must grant a real role before any drawer on this desk will open.",
                    fontSize = 15.sp,
                    color = DeskInk,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Locked out of: Desk, Ledger, Files, Till, Staff, Post, Log. " +
                        "Kept: this card, and the door behind you.",
                    fontSize = 14.sp,
                    color = DeskInkSoft,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    BrassButton("Back to the door", onClick = onBack)
                }
            }
        }
    }
}

@Composable
fun DeskBranchSelect(repo: SkeuomorphDeskFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState())) {
        SectionHead("FIRST DRAWER", "Choose your branch", "Each branch keeps its own drawer, ledger, and till.")
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { b ->
            val picked = repo.branchId.value == b.id
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(
                    title = "${b.name} — ${b.kind}",
                    stamp = if (picked) "SEATED" else "",
                    stampOk = picked,
                ) {
                    Text(text = b.drawer, fontSize = 14.sp, color = DeskInkSoft)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (picked) {
                            BrassButton("Open this ledger", onClick = onPick)
                        } else {
                            InkButton("Sit here") {
                                repo.branchId.value = b.id
                                repo.fileEntry("J. Reyes", "SEAT", b.name, "Branch selected")
                            }
                        }
                    }
                }
            }
        }
        InkButton("Back to the door") { onBack() }
    }
}

@Composable
fun DeskHome(repo: SkeuomorphDeskFakeRepo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE DESKTOP", "Good morning, J. Reyes", "Clock in at your home branch, or ride relief elsewhere.")
        Spacer(Modifier.height(14.dp))
        LedgerPage(
            title = "Time book — ${repo.branchName(repo.branchId.value)}",
            stamp = if (repo.clockedIn.value) "IN" else "OUT",
            stampOk = repo.clockedIn.value,
        ) {
            Text(
                text = "Home branch duty starts with a clock-in. Relief duty at another branch starts " +
                    "view-only; edit ink needs a granted request or an accepted invite. " +
                    "Every duty ends 04:00 Asia/Manila, and relief pay comes from that branch drawer.",
                fontSize = 14.sp,
                color = DeskInk,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (repo.clockedIn.value) {
                    StampButton("Clock out") { repo.clockOut() }
                } else {
                    BrassButton("Clock in") { repo.clockIn() }
                }
                Spacer(Modifier.width(10.dp))
                InkButton("Ask relief (broadcast)") {
                    repo.reliefBoard.add(
                        DeskReliefItem(
                            "r${repo.reliefBoard.size + 1}",
                            "Request",
                            "J. Reyes",
                            repo.branchName(repo.branchId.value),
                            "Today",
                            "Broadcast to the branch — any member may grant",
                        ),
                    )
                    repo.fileEntry("J. Reyes", "INSERT", "relief request", "Broadcast for edit access")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                InkButton("Invite help (Sat)") {
                    repo.reliefBoard.add(
                        DeskReliefItem(
                            "r${repo.reliefBoard.size + 1}",
                            "Invite",
                            "J. Reyes",
                            repo.branchName(repo.branchId.value),
                            "Sat",
                            "Invites M. Cruz 09:00-13:00",
                        ),
                    )
                    repo.fileEntry("J. Reyes", "INSERT", "relief invite", "Sat 09:00-13:00")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LedgerPage(title = "Relief slate — who rides where") {
            if (repo.reliefBoard.isEmpty()) {
                Text(text = "Slate wiped clean. No relief traffic today.", fontSize = 14.sp, color = DeskInkSoft)
            }
            repo.reliefBoard.forEach { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${r.kind} — ${r.who}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeskInk)
                        Text(text = "${r.branch} / ${r.day} — ${r.note}", fontSize = 13.sp, color = DeskInkSoft)
                    }
                    InkButton("Grant") { repo.settleRelief(r.id, "Granted edit ink") }
                    Spacer(Modifier.width(8.dp))
                    StampButton("Cut") { repo.settleRelief(r.id, "Denied / withdrawn") }
                }
            }
        }
    }
}

@Composable
fun DeskSessions(repo: SkeuomorphDeskFakeRepo) {
    var filter by remember { mutableStateOf("ALL") }
    var openId by remember { mutableStateOf<String?>(null) }
    var walkName by remember { mutableStateOf("") }
    var walkType by remember { mutableStateOf("Checkup") }
    var voidReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHead("THE LEDGER", "Sessions", "PENDING turns to COMPLETED, NO_SHOW, or CANCELLED. Walk-ins never take NO_SHOW or CANCELLED.")
        Spacer(Modifier.height(12.dp))
        Row {
            listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { f ->
                val on = filter == f
                Box(
                    modifier = Modifier.padding(end = 8.dp)
                        .background(if (on) DeskInk else DeskPaper, MaterialTheme.shapes.extraSmall)
                        .border(BorderStroke(1.dp, if (on) DeskInk else DeskPaperEdge), MaterialTheme.shapes.extraSmall)
                        .clickable { filter = f }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(text = f, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) DeskPaper else DeskInk)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            val rows = repo.sessions.filter {
                (filter == "ALL" || it.status.name == filter) && it.branchId == repo.branchId.value
            }
            items(rows, key = { it.id }) { s ->
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    LedgerPage(
                        title = "${s.time} — ${s.clientName}",
                        stamp = if (s.voided) "VOID" else s.status.name,
                    ) {
                        Text(
                            text = "${s.type} · ₱${s.price}${if (s.walkIn) " · walk-in" else ""}",
                            fontSize = 14.sp,
                            color = DeskInkSoft,
                        )
                        if (s.voided) {
                            Text(text = "Void reason: ${s.voidReason}", fontSize = 13.sp, color = DeskStamp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            InkButton(if (openId == s.id) "Shut file" else "Open file") {
                                openId = if (openId == s.id) null else s.id
                            }
                        }
                        if (openId == s.id) {
                            Spacer(Modifier.height(10.dp))
                            LedgerField("Void reason (for void / undo notes)", voidReason) { voidReason = it }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                BrassButton("Complete") {
                                    repo.updateSession(s.id, { it.copy(status = DeskSessionStatus.COMPLETED) }, "session ${s.id}", "Marked COMPLETED")
                                }
                                Spacer(Modifier.width(8.dp))
                                InkButton("No-show") {
                                    if (s.walkIn) {
                                        repo.fileEntry("J. Reyes", "REFUSE", "session ${s.id}", "Walk-ins cannot take NO_SHOW")
                                    } else {
                                        repo.updateSession(s.id, { it.copy(status = DeskSessionStatus.NO_SHOW) }, "session ${s.id}", "Marked NO_SHOW")
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                InkButton("Cancel") {
                                    if (s.walkIn) {
                                        repo.fileEntry("J. Reyes", "REFUSE", "session ${s.id}", "Walk-ins cannot take CANCELLED")
                                    } else {
                                        repo.updateSession(s.id, { it.copy(status = DeskSessionStatus.CANCELLED) }, "session ${s.id}", "Marked CANCELLED")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                if (s.voided) {
                                    BrassButton("Unvoid") {
                                        repo.updateSession(s.id, { it.copy(voided = false, voidReason = "") }, "session ${s.id}", "Unvoided / ${voidReason.ifBlank { "entered in error" }}")
                                    }
                                } else {
                                    StampButton("Void") {
                                        repo.updateSession(s.id, { it.copy(voided = true, voidReason = voidReason.ifBlank { "Entered in error" }) }, "session ${s.id}", "Voided / ${voidReason.ifBlank { "Entered in error" }}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LedgerPage(title = "Enter a walk-in") {
            LedgerField("Name", walkName) { walkName = it }
            Spacer(Modifier.height(8.dp))
            LedgerField("Type", walkType) { walkType = it }
            Spacer(Modifier.height(8.dp))
            BrassButton("Write it in") {
                repo.addWalkIn(walkName.ifBlank { "Walk-in ${repo.sessions.size + 1}" }, walkType.ifBlank { "Checkup" }, 500)
                walkName = ""
            }
        }
    }
}

@Composable
fun DeskClients(repo: SkeuomorphDeskFakeRepo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE CARD FILES", "Clients", "One global file per person. At most one PENDING session each.")
        Spacer(Modifier.height(12.dp))
        repo.clients.forEach { c ->
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(
                    title = if (c.anonymized) "File ${c.id.uppercase()} — sealed" else c.name,
                    stamp = if (c.anonymized) "SEALED" else if (c.hasPending) "PENDING" else "CLEAR",
                ) {
                    Text(
                        text = if (c.anonymized) "PII inked out. Gender and age kept for the reports." else c.detail,
                        fontSize = 14.sp,
                        color = DeskInkSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        InkButton(if (c.anonymized) "Break seal" else "Anonymize") { repo.toggleAnonymized(c.id) }
                    }
                }
            }
        }
        LedgerPage(title = "Filing rule") {
            Text(
                text = "A client with a PENDING session cannot open a second one — the desk refuses the duplicate " +
                    "until the first turns COMPLETED, NO_SHOW, or CANCELLED.",
                fontSize = 14.sp,
                color = DeskInk,
            )
        }
    }
}

@Composable
fun DeskFinance(repo: SkeuomorphDeskFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE TILL", "Finance & remittance", "SESSION and PRODUCT drafts file separately. Filing seals a snapshot folio.")
        Spacer(Modifier.height(12.dp))
        repo.remittances.forEach { r ->
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(
                    title = "${r.kind} ${r.id} — ₱${r.amount} (${r.dayLabel})",
                    stamp = r.status.name,
                    stampOk = r.status == DeskRemitStatus.SUBMITTED,
                ) {
                    if (r.snapshot.isNotEmpty()) {
                        Text(text = "Snapshot ${r.snapshot} sealed ${r.submittedAt}. Later ink never rewrites it.", fontSize = 14.sp, color = DeskInk)
                    } else {
                        Text(text = "Draft — loose leaf, overlaps allowed, nothing sealed yet.", fontSize = 14.sp, color = DeskInkSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (r.status == DeskRemitStatus.DRAFT) {
                        BrassButton("File & seal snapshot") { repo.submitRemittance(r.id) }
                    } else {
                        LedgerField("Undo reason (48h window)", undoReason) { undoReason = it }
                        Spacer(Modifier.height(8.dp))
                        StampButton("Undo filing") { repo.undoRemittance(r.id, undoReason.ifBlank { "Filed in error" }) }
                    }
                }
            }
        }
        LedgerPage(title = "Commission split") {
            Text(
                text = "Product commissions pool per branch day and split equally among every practitioner and " +
                    "coordinator clocked in at the sold_at hour. Hand-written inclusions or exclusions may override. " +
                    "Separate from compensation; never passes through remittance.",
                fontSize = 14.sp,
                color = DeskInk,
            )
        }
    }
}

@Composable
fun DeskTeam(repo: SkeuomorphDeskFakeRepo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE STAFF BOARD", "Team", "Roles bundle capabilities. ONBOARDING holds an empty bundle.")
        Spacer(Modifier.height(12.dp))
        repo.users.forEach { u ->
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(
                    title = u.name,
                    stamp = u.role,
                ) {
                    Text(
                        text = "Home: ${repo.branchName(u.homeBranchId)}" +
                            if (u.onboarding) " · locked: empty capability bundle, nothing derives" else "",
                        fontSize = 14.sp,
                        color = DeskInkSoft,
                    )
                }
            }
        }
        LedgerPage(title = "Role glance") {
            Text(
                text = "Practitioner works sessions · Coordinator keeps the till (sole editor of PAST and REMITTED) · " +
                    "MANAGER adds user-keeping and delegates · Accountant reads everything, edits nothing.",
                fontSize = 14.sp,
                color = DeskInk,
            )
        }
    }
}

@Composable
fun DeskMailbox(repo: SkeuomorphDeskFakeRepo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHead("THE POST TRAY", "Notifications", "Read letters stay filed forever.")
            }
            BrassButton("Mark all read") { repo.markAllRead() }
        }
        Spacer(Modifier.height(12.dp))
        repo.notes.forEach { n ->
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(
                    title = n.title,
                    stamp = if (n.read) "READ" else "NEW",
                    stampOk = n.read,
                ) {
                    Text(text = n.body, fontSize = 14.sp, color = DeskInk)
                    Text(text = "Day: ${n.day}", fontSize = 13.sp, color = DeskInkSoft)
                    if (!n.read) {
                        Spacer(Modifier.height(8.dp))
                        InkButton("Open & file") { repo.markRead(n.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun DeskAuditLog(repo: SkeuomorphDeskFakeRepo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE DAY BOOK", "Audit log", "Every mutation: who, what, when, and why. Ink never lifts.")
        Spacer(Modifier.height(12.dp))
        repo.audits.forEach { a ->
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                LedgerPage(title = "${a.action} — ${a.record}") {
                    Text(text = "Hand: ${a.actor} · ${a.whenText}", fontSize = 14.sp, color = DeskInk)
                    if (a.reason.isNotEmpty()) Text(text = "Why: ${a.reason}", fontSize = 13.sp, color = DeskInkSoft)
                }
            }
        }
    }
}

@Composable
fun DeskProfile(repo: SkeuomorphDeskFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHead("THE STUDY", "Profile", "Your card, the day lever, and the door.")
        Spacer(Modifier.height(12.dp))
        LedgerPage(title = "J. Reyes — Practitioner", stamp = if (repo.clockedIn.value) "IN" else "OUT", stampOk = repo.clockedIn.value) {
            Text(text = "Home: ${repo.branchName("b1")} · Seated: ${repo.branchName(repo.branchId.value)}", fontSize = 14.sp, color = DeskInkSoft)
            Spacer(Modifier.height(8.dp))
            Row {
                if (repo.clockedIn.value) {
                    StampButton("Clock out") { repo.clockOut() }
                } else {
                    BrassButton("Clock in") { repo.clockIn() }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LedgerPage(title = "Branch-day lever (demonstration)") {
            Text(
                text = "OPEN takes ink · PAST turns lazily at 04:00 Asia/Manila · REMITTED seals under a filing. " +
                    "PAST and REMITTED take Coordinator ink only.",
                fontSize = 14.sp,
                color = DeskInk,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                DeskDayStatus.entries.forEach { d ->
                    val on = repo.dayStatus.value == d
                    Box(
                        modifier = Modifier.padding(end = 8.dp)
                            .background(if (on) DeskInk else DeskPaper, MaterialTheme.shapes.extraSmall)
                            .border(BorderStroke(1.dp, if (on) DeskInk else DeskPaperEdge), MaterialTheme.shapes.extraSmall)
                            .clickable { repo.dayStatus.value = d }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = d.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) DeskPaper else DeskInk)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LedgerPage(title = "The door") {
            StampButton("Log out") { onLogout() }
        }
    }
}
