package com.companyb.companyapp.proto.limboroom

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #824 — limbo-room waiting hall. Fake LrStore only; every room is clickable.

@Composable
fun LimboRoomApp() {
    val store = remember { seedLrStore() }
    logInfo("LimboRoom", "limbo-room prototype started")
    MaterialTheme(colorScheme = LrHallScheme) {
        Box(Modifier.fillMaxSize().background(LrNight)) {
            when (store.phase) {
                "LOGIN" -> LrLogin(store)
                "LOCKED" -> LrLocked(store)
                "BRANCH" -> LrBranchPick(store)
                else -> LrHall(store)
            }
        }
    }
}

// ---------- front desk: sign in ----------

@Composable
fun LrLogin(store: LrStore) {
    val v = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("✦ COMPANYAPP — LIMBO ROOM", fontFamily = LrMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LrAmber)
        Text("front desk · take a number · any secret works · fake hall, no network", fontFamily = LrMono, fontSize = 12.sp, color = LrMuted)
        Spacer(Modifier.height(14.dp))
        LrNowServingStub()
        Spacer(Modifier.height(14.dp))
        LrSectionTitle("DESK-01", "Who is waiting?", "${store.users.size} regulars on file")
        store.users.forEachIndexed { i, u ->
            val on = store.loginPick == i
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) LrCream else LrNightSoft)
                    .border(1.dp, if (on) LrAmber else LrNightLine, RoundedCornerShape(12.dp))
                    .clickable { store.loginPick = i; store.loginError = "" }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.background(if (on) LrNight else ColorNightLine(), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("№ ${i + 1}", fontFamily = LrMono, fontSize = 10.sp, color = LrAmber)
                        Text(u.slot.toString().padStart(2, '0'), fontFamily = LrMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontFamily = LrSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) LrInk else LrCream)
                        Text("${u.role.name} · home ${store.branchName(u.homeBranchId)}", fontFamily = LrMono, fontSize = 11.sp, color = if (on) ColorInkSoft() else LrMuted)
                    }
                    if (u.role == LrRole.ONBOARDING) LrStamp("LIMBO", LrRope) else LrStamp(u.role.name, LrAmberDim)
                }
            }
        }
        OutlinedTextField(
            value = store.loginSecret,
            onValueChange = { store.loginSecret = it; store.loginError = "" },
            label = { Text("secret (any value)", fontFamily = LrMono, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (store.loginError.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LrNote(store.loginError)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val picked = store.users.getOrNull(store.loginPick) ?: store.users.first()
            LrPrimary("TAKE A NUMBER → ${picked.name}") { store.login(picked) }
            Spacer(Modifier.width(10.dp))
            Text("ONBOARDING waits in the limbo room.", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
        }
    }
}

private fun ColorNightLine() = androidx.compose.ui.graphics.Color(0xFF3A2F63)
private fun ColorInkSoft() = androidx.compose.ui.graphics.Color(0xFF6B5F45)

@Composable
fun LrNowServingStub() {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF0B0A1A))
            .border(1.dp, LrAmberDim, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NOW SERVING", fontFamily = LrMono, fontSize = 10.sp, color = LrMuted)
                Text("A-103", fontFamily = LrMono, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LrAmber)
                Text("window 3 · J. Reyes · remittance counter", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("WAITING", fontFamily = LrMono, fontSize = 10.sp, color = LrMuted)
                Text("14", fontFamily = LrMono, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LrCream)
                Text("incl. 1 in limbo", fontFamily = LrMono, fontSize = 11.sp, color = LrRope)
            }
        }
    }
}

// ---------- the limbo room: ONBOARDING lockout done well ----------

@Composable
fun LrLocked(store: LrStore) {
    val v = rememberScrollState()
    val me = store.currentUser
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("✦ THE LIMBO ROOM", fontFamily = LrMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LrAmber)
        Text("a locked-out experience with a chair, a number, and a next step", fontFamily = LrMono, fontSize = 12.sp, color = LrMuted)
        Spacer(Modifier.height(14.dp))
        LrTicketStub("L-001", me?.name ?: "guest", "ONBOARDING · zero capabilities · nothing derives until a real role is granted")
        Spacer(Modifier.height(14.dp))
        LrSectionTitle("STATUS", "You are checked in — not locked out blind", "role bundle empty by policy")
        LrPaperCard {
            LrStatusLine("Name", me?.name ?: "—")
            LrStatusLine("Role", "ONBOARDING (freshly registered, zero capabilities)")
            LrStatusLine("Home branch", me?.let { store.branchName(it.homeBranchId) } ?: "—")
            LrStatusLine("Capabilities", "none — every door below stays shut")
            LrStatusLine("Sessions / finance / team", "hidden — the hall keeps them, you keep the ticket")
        }
        Spacer(Modifier.height(14.dp))
        LrSectionTitle("NEXT STEP", "Three stamps to leave limbo", "in order")
        LrPaperCard {
            LrStep("1", "A MANAGER grants you a real role", "needs MANAGE_USERS · usually A. Villanueva · ask at the Cebu window", done = false)
            LrStep("2", "A coordinator confirms your branch", "J. Reyes seats Makati regulars most mornings", done = false)
            LrStep("3", "Clock in and take a door", "any arch opens once the grant lands — relief needs its own stamp", done = false)
        }
        Spacer(Modifier.height(14.dp))
        LrSectionTitle("WHO TO ASK", "Humans, not helpdesks", "tap a name to rehearse the ask")
        var ask by remember { mutableStateOf<String?>(null) }
        LrAskRow("A. Villanueva", "MANAGER · Cebu Provincial Tour · slot 1 · grants roles", "“Hi — I am K. Dela Pena, ONBOARDING at Makati. Could you grant me a practitioner role when you have a minute?”") { ask = it }
        LrAskRow("J. Reyes", "COORDINATOR · Makati Clinic · slot 1 · seats the hall", "“Hi — I just registered. Which branch should I sit in until my role lands?”") { ask = it }
        LrAskRow("R. Ocampo", "ACCOUNTANT · read-only · sees every drawer", "“Hi — no edits needed. Could you confirm my record exists so I am not queuing twice?”") { ask = it }
        if (ask != null) {
            Spacer(Modifier.height(8.dp))
            LrNote(ask!!)
        }
        Spacer(Modifier.height(14.dp))
        LrRopeDivider()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LrPrimary("LEAVE THE HALL (SIGN OUT)") { store.logout() }
            Spacer(Modifier.width(10.dp))
            Text("your ticket L-001 is kept — the queue remembers", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
        }
    }
}

@Composable
fun LrStatusLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, fontFamily = LrMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LrInk, modifier = Modifier.width(170.dp))
        Text(v, fontFamily = LrMono, fontSize = 12.sp, color = ColorInkSoft())
    }
}

@Composable
fun LrStep(no: String, title: String, sub: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(if (done) LrMint else LrNight, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(if (done) "✓" else no, fontFamily = LrMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (done) LrNight else LrAmber)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrInk)
            Text(sub, fontFamily = LrMono, fontSize = 11.sp, color = ColorInkSoft())
        }
    }
}

@Composable
fun LrAskRow(name: String, meta: String, script: String, onAsk: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LrNightSoft)
            .border(1.dp, LrNightLine, RoundedCornerShape(12.dp))
            .clickable { onAsk(script) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream)
                Text(meta, fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            }
            LrStamp("ASK", LrSky)
        }
    }
}

// ---------- branch select ----------

@Composable
fun LrBranchPick(store: LrStore) {
    val v = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("✦ PICK A WING", fontFamily = LrMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LrAmber)
        Text("operator ${store.currentUser?.name} · ${store.currentUser?.role?.name} · three wings, one hall", fontFamily = LrMono, fontSize = 12.sp, color = LrMuted)
        Spacer(Modifier.height(14.dp))
        store.branches.forEach { b ->
            val on = store.currentBranchId == b.id
            Box(
                Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(if (on) LrCream else LrNightSoft)
                    .border(1.dp, if (on) LrAmber else LrNightLine, RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                    .clickable { store.enterBranch(b.id) }
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(b.name, fontFamily = LrSans, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (on) LrInk else LrCream)
                    Text("${b.kind.name} · ${b.line}", fontFamily = LrMono, fontSize = 11.sp, color = if (on) ColorInkSoft() else LrMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("${b.dayLabel} · ${b.dayStatus.name}", fontFamily = LrMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) LrInk else LrSky)
                }
            }
        }
        Row {
            LrLink("← back to front desk") { store.logout() }
        }
    }
}

// ---------- hall shell ----------

@Composable
fun LrHall(store: LrStore) {
    Row(Modifier.fillMaxSize()) {
        LrDoorRail(store)
        val v = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(v).padding(20.dp)) {
            LrNowServing(store)
            Spacer(Modifier.height(10.dp))
            LrDayBanner(store)
            Spacer(Modifier.height(10.dp))
            LrRopeDivider()
            Spacer(Modifier.height(10.dp))
            when (store.room) {
                LrRoom.HOME -> LrHomeRoom(store)
                LrRoom.SESSIONS -> LrSessionsRoom(store)
                LrRoom.CLIENTS -> LrClientsRoom(store)
                LrRoom.FINANCE -> LrFinanceRoom(store)
                LrRoom.TEAM -> LrTeamRoom(store)
                LrRoom.MAIL -> LrMailRoom(store)
                LrRoom.AUDIT -> LrAuditRoom(store)
                LrRoom.PROFILE -> LrProfileRoom(store)
            }
        }
    }
}

// ---------- home: clock + relief ----------

@Composable
fun LrHomeRoom(store: LrStore) {
    val me = store.currentUser
    LrSectionTitle("LOBBY", "Clock in, then work the hall", "home + relief")
    val branchSessions = store.sessions.filter { it.branchId == store.currentBranchId }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LrStat("${branchSessions.count { it.status == LrSessionStatus.PENDING }}", "PENDING")
        LrStat("${branchSessions.count { it.status == LrSessionStatus.COMPLETED }}", "DONE")
        LrStat("${store.notifications.count { !it.read }}", "UNREAD")
    }
    Spacer(Modifier.height(10.dp))
    LrHallCard {
        Text("CLOCK", fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, fontWeight = FontWeight.Bold)
        Text(
            if (store.clockedIn) "clocked in at ${store.branchName(store.clockBranchId ?: "")} — the hall sees you" else "not clocked in — ${me?.name} is still in the waiting chairs",
            fontFamily = LrMono, fontSize = 12.sp, color = LrCream,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            LrPrimary(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
        }
    }
    Spacer(Modifier.height(10.dp))
    LrSectionTitle("RELIEF", "Duty · request · invite", "relief pays from the relief drawer, ends 04:00 Manila")
    store.reliefs.forEach { r ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp)).background(LrNightSoft).border(1.dp, LrNightLine, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LrStamp(r.kind.name, LrSky)
                    Spacer(Modifier.width(8.dp))
                    Text(r.who, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream, modifier = Modifier.weight(1f))
                    LrStamp(r.state.name, if (r.state == LrReliefState.OPEN) LrAmber else LrMint)
                }
                Text("${store.branchName(r.branchId)} · ${r.day}", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
                if (r.state == LrReliefState.OPEN) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (r.kind) {
                            LrReliefKind.REQUEST -> {
                                LrPrimary("GRANT") { r.state = LrReliefState.GRANTED; store.audits.add(0, LrAudit("a-x", "now", me?.name ?: "?", "granted relief request ${r.id}")) }
                                LrGhost("DENY") { r.state = LrReliefState.DENIED }
                            }
                            LrReliefKind.INVITE -> {
                                LrPrimary("ACCEPT") { r.state = LrReliefState.ACCEPTED }
                                LrGhost("DECLINE") { r.state = LrReliefState.DECLINED }
                            }
                            LrReliefKind.DUTY -> {
                                LrPrimary("TAKE DUTY") { r.state = LrReliefState.GRANTED }
                            }
                        }
                    }
                }
            }
        }
    }
    LrNote("Relief starts view-only; edit needs a grant (broadcast request approved by any branch member, or a branch invite). Multiple relief workers may hold edit access on one day. All retraction locks once the requester clocks in as relief.")
}

private fun notificationsUnread(store: LrStore): Int = store.notifications.count { !it.read }

// ---------- sessions ----------

@Composable
fun LrSessionsRoom(store: LrStore) {
    LrSectionTitle("SESSIONS", "One visit, one client, one branch", "PENDING → COMPLETED / NO_SHOW / CANCELLED")
    LrChipRow(listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"), store.sessionFilter) { store.sessionFilter = it }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = store.showVoided, onCheckedChange = { store.showVoided = it })
        Text("show voided", fontFamily = LrMono, fontSize = 12.sp, color = LrCream)
        Spacer(Modifier.width(12.dp))
        LrGhost("+ BOOK SESSION") { store.bookOpen = true; store.bookName = ""; store.bookWalkIn = false }
    }
    Spacer(Modifier.height(8.dp))
    LrNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — a walk-in either shows or never existed. Void excludes a session from money math but keeps the record; unvoid needs no reason, void needs one.")
    Spacer(Modifier.height(8.dp))
    val list = store.sessions.filter {
        (it.branchId == store.currentBranchId) &&
            (store.sessionFilter == "ALL" || it.status.name == store.sessionFilter) &&
            (store.showVoided || !it.voided)
    }
    if (list.isEmpty()) {
        LrNote("No sessions behind this door for the current filter. Book one above.")
    }
    list.forEach { s ->
        val sel = store.selectedSessionId == s.id
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (sel) LrCream else LrNightSoft)
                .border(1.dp, if (sel) LrAmber else LrNightLine, RoundedCornerShape(12.dp))
                .clickable { store.selectedSessionId = if (sel) null else s.id }
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.ticketNo ?: s.id, fontFamily = LrMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) LrInk else LrAmber, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.clientName + if (s.walkIn) " · walk-in" else "", fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (sel) LrInk else LrCream)
                        Text("${s.bookedTime} · ${s.type} · ${peso(s.price)} · ${s.practitioner}", fontFamily = LrMono, fontSize = 11.sp, color = if (sel) ColorInkSoft() else LrMuted)
                    }
                    LrStamp(s.status.name, if (sel) LrAmberDim else statusTone(s.status))
                }
                if (s.voided) {
                    Text("VOIDED — ${s.voidReason ?: "no reason"}", fontFamily = LrMono, fontSize = 11.sp, color = LrRope)
                }
                if (sel) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LrPrimary("COMPLETE") { s.status = LrSessionStatus.COMPLETED; audit(store, "completed ${s.ticketNo}") }
                        LrGhost("NO-SHOW") {
                            if (s.walkIn) {
                                audit(store, "refused NO_SHOW for walk-in ${s.ticketNo} (rule)")
                            } else {
                                s.status = LrSessionStatus.NO_SHOW; audit(store, "marked ${s.ticketNo} NO_SHOW")
                            }
                        }
                        LrGhost("CANCEL") {
                            if (s.walkIn) {
                                audit(store, "refused CANCEL for walk-in ${s.ticketNo} (rule)")
                            } else {
                                s.status = LrSessionStatus.CANCELLED; audit(store, "cancelled ${s.ticketNo}")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!s.voided) {
                            LrGhost("VOID…") { store.voidDialogFor = s.id; store.voidReasonText = "" }
                        } else {
                            LrGhost("UNVOID") { s.voided = false; s.voidReason = null; audit(store, "unvoided ${s.ticketNo}") }
                        }
                    }
                }
            }
        }
    }
    if (store.voidDialogFor != null) {
        AlertDialog(
            onDismissRequest = { store.voidDialogFor = null },
            title = { Text("Void session", fontFamily = LrMono) },
            text = {
                Column {
                    Text("A reason is required — the record stays visible with a void marker.", fontFamily = LrMono, fontSize = 12.sp)
                    OutlinedTextField(value = store.voidReasonText, onValueChange = { store.voidReasonText = it }, label = { Text("reason") }, singleLine = true)
                }
            },
            confirmButton = {
                LrPrimary("VOID") {
                    val target = store.sessions.firstOrNull { it.id == store.voidDialogFor }
                    if (target != null && store.voidReasonText.isNotBlank()) {
                        target.voided = true
                        target.voidReason = store.voidReasonText
                        audit(store, "voided ${target.ticketNo} (${store.voidReasonText})")
                    }
                    store.voidDialogFor = null
                }
            },
            dismissButton = { LrGhost("BACK") { store.voidDialogFor = null } },
        )
    }
    if (store.bookOpen) {
        AlertDialog(
            onDismissRequest = { store.bookOpen = false },
            title = { Text("Book session", fontFamily = LrMono) },
            text = {
                Column {
                    OutlinedTextField(value = store.bookName, onValueChange = { store.bookName = it }, label = { Text("client name") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = store.bookWalkIn, onCheckedChange = { store.bookWalkIn = it })
                        Text("walk-in (gets a W-ticket; no NO_SHOW/CANCELLED)", fontFamily = LrMono, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                LrPrimary("BOOK") {
                    if (store.bookName.isNotBlank()) {
                        val n = store.sessions.size + 101
                        val ticket = if (store.bookWalkIn) "W-${n - 94}" else "A-$n"
                        store.sessions.add(
                            LrSession("s-$n", "c-new", store.bookName, store.currentBranchId, "03:00", if (store.bookWalkIn) "WALK-IN" else "OTC-1", LrSessionStatus.PENDING, 650, store.bookWalkIn, ticketNo = ticket),
                        )
                        audit(store, "booked $ticket for ${store.bookName}")
                    }
                    store.bookOpen = false
                }
            },
            dismissButton = { LrGhost("BACK") { store.bookOpen = false } },
        )
    }
}

private fun statusTone(st: LrSessionStatus): androidx.compose.ui.graphics.Color =
    when (st) {
        LrSessionStatus.PENDING -> LrAmber
        LrSessionStatus.COMPLETED -> LrMint
        LrSessionStatus.NO_SHOW -> LrSky
        LrSessionStatus.CANCELLED -> LrRope
    }

private fun audit(store: LrStore, action: String) {
    store.audits.add(0, LrAudit("a${store.audits.size + 1}", "now", store.currentUser?.name ?: "?", action))
}

// ---------- clients ----------

@Composable
fun LrClientsRoom(store: LrStore) {
    LrSectionTitle("CLIENTS", "Global registry — every wing sees the same faces", "at most one PENDING each")
    val pending = store.pendingClientIds()
    LrNote("A client holds at most one PENDING session at a time — rows marked * PENDING are mid-visit. Anonymized rows keep gender + age for reporting, PII nulled.")
    Spacer(Modifier.height(8.dp))
    store.clients.forEach { c ->
        val live = pending.contains(c.id)
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp)).background(LrNightSoft).border(1.dp, LrNightLine, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.anonymized) "anon-${c.id} (anonymized)" else c.name + if (live) "  * PENDING" else "",
                        fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream,
                    )
                    Text(
                        if (c.anonymized) "gender ${c.gender} · age ${c.age} · PII nulled" else "${c.gender} · age ${c.age} · ${c.id}",
                        fontFamily = LrMono, fontSize = 11.sp, color = LrMuted,
                    )
                }
                if (!c.anonymized) {
                    LrGhost("ANONYMIZE") { c.anonymized = true; audit(store, "anonymized client ${c.id}") }
                } else {
                    LrStamp("SEALED", LrMuted)
                }
            }
        }
    }
}

// ---------- finance ----------

@Composable
fun LrFinanceRoom(store: LrStore) {
    LrSectionTitle("FINANCE", "Two counters: sessions and products", "snapshot seals, undo has 48h")
    LrRemitCounter(store, store.sessionRemit, "SESSION", "net income after compensation and expenses")
    Spacer(Modifier.height(10.dp))
    LrRemitCounter(store, store.productRemit, "PRODUCT", "unit price × quantity")
    Spacer(Modifier.height(10.dp))
    LrSectionTitle("SHELF", "Product lines", "${store.productLines.size} lines")
    store.productLines.forEach { p ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp)).background(LrNightSoft).border(1.dp, LrNightLine, RoundedCornerShape(10.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream)
                Text("${p.qty} × ${peso(p.price)} = ${peso(p.qty * p.price)}", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            }
            LrGhost("+") { p.qty += 1 }
            Spacer(Modifier.width(6.dp))
            LrGhost("−") { if (p.qty > 0) p.qty -= 1 }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = store.newProductName, onValueChange = { store.newProductName = it }, label = { Text("new product", fontFamily = LrMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        LrPrimary("SHELVE") {
            if (store.newProductName.isNotBlank()) {
                store.productLines.add(LrProductLine("p-${store.productLines.size + 1}", store.newProductName, 1, 300))
                store.newProductName = ""
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    LrNote("Commission split: product commissions pool per branch day and split equally among all practitioners and coordinators clocked in at the sold_at time. Manual inclusions/exclusions may override. Commissions ride beside remittance — never inside it.")
    if (store.undoDialogFor != null) {
        val kind = store.undoDialogFor!!
        AlertDialog(
            onDismissRequest = { store.undoDialogFor = null },
            title = { Text("Undo $kind remittance", fontFamily = LrMono) },
            text = {
                Column {
                    Text("Within 48h the snapshot is deleted and the days unlock. A reason is recorded in the audit trail.", fontFamily = LrMono, fontSize = 12.sp)
                    OutlinedTextField(value = store.undoReasonText, onValueChange = { store.undoReasonText = it }, label = { Text("reason") }, singleLine = true)
                }
            },
            confirmButton = {
                LrPrimary("UNDO") {
                    val holder = if (kind == LrRemitKind.SESSION) store.sessionRemit else store.productRemit
                    if (store.undoReasonText.isNotBlank()) {
                        holder.state = LrRemitState.DRAFT
                        holder.undoReason = store.undoReasonText
                        holder.snapshotId = null
                        holder.snapshotTotal = null
                        holder.submittedAt = null
                        audit(store, "undid $kind remittance (${store.undoReasonText})")
                    }
                    store.undoDialogFor = null
                }
            },
            dismissButton = { LrGhost("BACK") { store.undoDialogFor = null } },
        )
    }
}

@Composable
fun LrRemitCounter(store: LrStore, holder: LrRemitStateHolder, title: String, sub: String) {
    LrHallCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$title COUNTER", fontFamily = LrMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LrAmber, modifier = Modifier.weight(1f))
            LrStamp(holder.state.name, if (holder.state == LrRemitState.DRAFT) LrSky else LrMint)
        }
        Text(sub, fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
        Spacer(Modifier.height(8.dp))
        if (holder.state == LrRemitState.DRAFT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = holder.draftGross.toString(), onValueChange = { holder.draftGross = it.toIntOrNull() ?: holder.draftGross },
                    label = { Text("gross", fontFamily = LrMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = holder.draftDeductions.toString(), onValueChange = { holder.draftDeductions = it.toIntOrNull() ?: holder.draftDeductions },
                    label = { Text("deductions", fontFamily = LrMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("net ${peso(holder.draftGross - holder.draftDeductions)} · drafts overlap freely", fontFamily = LrMono, fontSize = 12.sp, color = LrCream)
            Spacer(Modifier.height(8.dp))
            Row {
                LrPrimary("SUBMIT → SEAL") {
                    holder.state = LrRemitState.SUBMITTED
                    holder.snapshotId = "snap-${holder.kind.name.lowercase()}-${store.dayFilter}"
                    holder.snapshotTotal = holder.draftGross - holder.draftDeductions
                    holder.submittedAt = "${store.dayFilter} 18:02 Manila"
                    audit(store, "submitted ${holder.kind.name} remittance ${holder.snapshotId} (${peso(holder.snapshotTotal ?: 0)})")
                }
            }
        } else {
            Text("sealed ${holder.snapshotId} · ${peso(holder.snapshotTotal ?: 0)} · ${holder.submittedAt}", fontFamily = LrMono, fontSize = 12.sp, color = LrMint)
            Text("immutable — later edits never rewrite the seal; only Undo within 48h reopens it", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            Spacer(Modifier.height(8.dp))
            Row {
                LrGhost("UNDO ≤48H…") { store.undoDialogFor = holder.kind; store.undoReasonText = "" }
            }
            if (holder.undoReason != null) {
                Text("last undo: ${holder.undoReason}", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            }
        }
    }
}

// ---------- team ----------

@Composable
fun LrTeamRoom(store: LrStore) {
    LrSectionTitle("TEAM", "Slot order runs the report queue", "1 = senior · relief sorts after home")
    val me = store.currentUser
    val roster = store.users.sortedBy { it.slot }
    roster.forEach { u ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp)).background(LrNightSoft).border(1.dp, LrNightLine, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.slot.toString().padStart(2, '0'), fontFamily = LrMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LrAmber, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LrCream)
                    Text("${u.role.name} · home ${store.branchName(u.homeBranchId)}", fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
                }
                if (u.role == LrRole.ONBOARDING) {
                    if (me?.role == LrRole.MANAGER) {
                        LrPrimary("GRANT PRACTITIONER") {
                            val idx = store.users.indexOfFirst { it.id == u.id }
                            if (idx >= 0) store.users[idx] = u.copy(role = LrRole.PRACTITIONER)
                            audit(store, "granted PRACTITIONER to ${u.name}")
                        }
                    } else {
                        LrStamp("LIMBO", LrRope)
                    }
                } else {
                    LrStamp(u.role.name, LrAmberDim)
                }
            }
        }
    }
    LrNote("Only a MANAGER grant lifts ONBOARDING out of limbo — the role bundle is empty until MANAGE_USERS writes a real one. Sign in as A. Villanueva to stamp the grant.")
}

// ---------- mail + audit ----------

@Composable
fun LrMailRoom(store: LrStore) {
    LrSectionTitle("MAIL", "Every message keeps its read stamp", "tap-through names branch + day")
    LrChipRow(listOf("ALL", "UNREAD"), store.mailFilter) { store.mailFilter = it }
    Spacer(Modifier.height(8.dp))
    Row {
        LrGhost("MARK ALL READ") { store.notifications.forEach { it.read = true } }
    }
    Spacer(Modifier.height(8.dp))
    val list = store.notifications.filter { store.mailFilter == "ALL" || !it.read }
    if (list.isEmpty()) {
        LrNote("Mailbox empty for this filter — the hall is quiet.")
    }
    list.forEach { n ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (n.read) LrNightSoft else LrCream)
                .border(1.dp, if (n.read) LrNightLine else LrAmber, RoundedCornerShape(12.dp))
                .clickable { n.read = true }
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.title, fontFamily = LrSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (n.read) LrCream else LrInk, modifier = Modifier.weight(1f))
                    if (!n.read) LrStamp("NEW", LrRope) else LrStamp("READ", LrMuted)
                }
                Text(n.body, fontFamily = LrMono, fontSize = 12.sp, color = if (n.read) LrMuted else ColorInkSoft())
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${store.branchName(n.branchId)} · ${n.day}", fontFamily = LrMono, fontSize = 11.sp, color = if (n.read) LrMuted else ColorInkSoft(), modifier = Modifier.weight(1f))
                    LrLink("JUMP → DAY") { store.dayFilter = n.day; store.room = LrRoom.HOME }
                }
            }
        }
    }
}

@Composable
fun LrAuditRoom(store: LrStore) {
    LrSectionTitle("AUDIT", "Newest first, forever kept", "${store.audits.size} rows")
    LrNote("Reads never die here — even mailbox reads stay on the shelf. REMITTED-day edits would flag coordinator-only; this hall only rehearses them.")
    Spacer(Modifier.height(8.dp))
    store.audits.forEach { a ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp)).background(LrNightSoft).border(1.dp, LrNightLine, RoundedCornerShape(10.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(a.at, fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, modifier = Modifier.width(110.dp))
            Column {
                Text(a.actor, fontFamily = LrSans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LrCream)
                Text(a.action, fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
            }
        }
    }
}

// ---------- profile ----------

@Composable
fun LrProfileRoom(store: LrStore) {
    val me = store.currentUser
    LrSectionTitle("PROFILE", "Your ticket, your stamps", "logout + clock-out live here")
    if (me == null) {
        LrNote("Nobody is holding a ticket. Head back to the front desk.")
        return
    }
    LrTicketStub("L-002", me.name, "${me.role.name} · home ${store.branchName(me.homeBranchId)} · slot ${me.slot}")
    Spacer(Modifier.height(10.dp))
    LrHallCard {
        Text("CAPABILITIES — ${me.role.name}", fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, fontWeight = FontWeight.Bold)
        val caps = lrCaps(me.role)
        if (caps.isEmpty()) {
            Text("empty bundle — the limbo room is honest about it", fontFamily = LrMono, fontSize = 12.sp, color = LrCream)
        } else {
            caps.forEach { Text("· $it", fontFamily = LrMono, fontSize = 12.sp, color = LrCream) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LrPrimary(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
        LrGhost("SWITCH WING") { store.phase = "BRANCH" }
        LrGhost("LOGOUT") { store.logout() }
    }
    Spacer(Modifier.height(8.dp))
    LrNote("Deactivation would block login at once and vanish every capability; reactivation hands them back on a fresh sign-in. This hall only posts the notice — the desk enforces it.")
}
