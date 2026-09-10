package com.companyb.companyapp.proto.commanddeck

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

// #828 — command-deck starship bridge. Fake CdStore only; every station is clickable.

@Composable
fun CommandDeckApp() {
    val store = remember { seedCdStore() }
    logInfo("CommandDeck", "command-deck prototype started")
    MaterialTheme(colorScheme = CdDeckScheme) {
        Box(Modifier.fillMaxSize().background(CdVoid)) {
            when (store.phase) {
                "LOGIN" -> CdLogin(store)
                "LOCKED" -> CdLocked(store)
                "VESSEL" -> CdVesselPick(store)
                else -> CdDeck(store)
            }
        }
    }
}

// ---------- gangway: sign in ----------

@Composable
fun CdLogin(store: CdStore) {
    val v = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("◆ COMPANYAPP — COMMAND DECK", fontFamily = CdMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CdGlow)
        Text("gangway · state your cipher · any secret works · fake deck, no network", fontFamily = CdMono, fontSize = 12.sp, color = CdFog)
        Spacer(Modifier.height(14.dp))
        CdConsoleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FLEET STATUS", fontFamily = CdMono, fontSize = 10.sp, color = CdFog)
                    Text("3 VESSELS · 1 OPEN DAY", fontFamily = CdMono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CdGlow)
                    Text("Makati live · Cebu past · Tondo remitted", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CREW", fontFamily = CdMono, fontSize = 10.sp, color = CdFog)
                    Text("${store.users.size}", fontFamily = CdMono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                    Text("1 awaiting grant", fontFamily = CdMono, fontSize = 11.sp, color = CdAmber)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        CdSectionHead("DECK-01", "Who is boarding?", "${store.users.size} crew on the manifest")
        store.users.forEachIndexed { i, u ->
            val on = store.loginPick == i
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) CdDeckSoft else CdConsole)
                    .border(1.dp, if (on) CdGlow else CdLine, RoundedCornerShape(8.dp))
                    .clickable { store.loginPick = i; store.loginError = "" }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.background(CdVoid, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("№ ${i + 1}", fontFamily = CdMono, fontSize = 10.sp, color = CdGlow)
                        Text(u.slot.toString().padStart(2, '0'), fontFamily = CdMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, fontFamily = CdSans, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                        Text("${u.role.name} · home ${store.branchName(u.homeBranchId)}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                    }
                    if (u.role == CdRole.ONBOARDING) CdGlowTag("HOLD", CdAmber) else CdGlowTag(u.role.name, CdGlowDim)
                }
            }
        }
        OutlinedTextField(
            value = store.loginSecret,
            onValueChange = { store.loginSecret = it; store.loginError = "" },
            label = { Text("cipher (any value)", fontFamily = CdMono, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (store.loginError.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            CdNote(store.loginError)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val picked = store.users.getOrNull(store.loginPick) ?: store.users.first()
            CdPrimary("BOARD → ${picked.name}") { store.login(picked) }
            Spacer(Modifier.width(10.dp))
            Text("ONBOARDING is held on the observation deck.", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
}

// ---------- observation deck: ONBOARDING hold, designed not denied ----------

@Composable
fun CdLocked(store: CdStore) {
    val v = rememberScrollState()
    val me = store.currentUser
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("◆ THE OBSERVATION DECK", fontFamily = CdMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CdGlow)
        Text("a held boarding with a viewport, a signal, and a next step", fontFamily = CdMono, fontSize = 12.sp, color = CdFog)
        Spacer(Modifier.height(14.dp))
        CdSignalTicket("H-001", me?.name ?: "guest", "ONBOARDING · zero capabilities · nothing derives until a real role is granted")
        Spacer(Modifier.height(14.dp))
        CdSectionHead("STATUS", "You are aboard — at the viewport, not the helm", "role bundle empty by policy")
        CdPanel {
            CdStatusLine("Name", me?.name ?: "—")
            CdStatusLine("Role", "ONBOARDING (freshly registered, zero capabilities)")
            CdStatusLine("Home vessel", me?.let { store.branchName(it.homeBranchId) } ?: "—")
            CdStatusLine("Capabilities", "none — every station below stays dark")
            CdStatusLine("Sessions / finance / team", "held — the deck keeps them, you keep the signal")
        }
        Spacer(Modifier.height(14.dp))
        CdSectionHead("NEXT STEP", "Three burns to leave the viewport", "in order")
        CdPanel {
            CdBurn("1", "A MANAGER grants you a real role", "needs team.grant · usually A. Villanueva · hail from the Cebu channel", done = false)
            CdBurn("2", "A coordinator confirms your vessel", "J. Reyes seats Makati crew most mornings", done = false)
            CdBurn("3", "Clock in and take a station", "any console lights up once the grant lands — relief needs its own burn", done = false)
        }
        Spacer(Modifier.height(14.dp))
        CdSectionHead("HAIL", " Officers, not helpdesks", "tap a name to rehearse the hail")
        var hail by remember { mutableStateOf<String?>(null) }
        CdHailRow("A. Villanueva", "MANAGER · Cebu Provincial Tour · slot 1 · grants roles", "“Command — K. Dela Pena, ONBOARDING at Makati. Requesting a practitioner grant when you have a minute.”") { hail = it }
        CdHailRow("J. Reyes", "COORDINATOR · Makati Clinic · slot 1 · runs the deck", "“Command — just registered. Which vessel should I stand by on until my role lands?”") { hail = it }
        CdHailRow("R. Ocampo", "ACCOUNTANT · read-only · sees every ledger", "“Command — no edits needed. Confirm my record exists so I am not queuing twice.”") { hail = it }
        if (hail != null) {
            Spacer(Modifier.height(8.dp))
            CdNote(hail!!)
        }
        Spacer(Modifier.height(14.dp))
        CdDeckDivider()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CdPrimary("DISEMBARK (SIGN OUT)") { store.logout() }
            Spacer(Modifier.width(10.dp))
            Text("signal H-001 is kept — the deck remembers", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
}

@Composable
fun CdStatusLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, fontFamily = CdMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CdPaper, modifier = Modifier.width(170.dp))
        Text(v, fontFamily = CdMono, fontSize = 12.sp, color = CdFog)
    }
}

@Composable
fun CdBurn(no: String, title: String, sub: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(if (done) CdPhos else CdConsole, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(if (done) "✓" else no, fontFamily = CdMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (done) CdVoid else CdGlow)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
            Text(sub, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
}

@Composable
fun CdHailRow(name: String, meta: String, script: String, onHail: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CdDeck)
            .border(1.dp, CdLine, RoundedCornerShape(8.dp))
            .clickable { onHail(script) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                Text(meta, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
            }
            CdGlowTag("HAIL", CdGlow)
        }
    }
}

// ---------- vessel select ----------

@Composable
fun CdVesselPick(store: CdStore) {
    val v = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text("◆ CHOOSE YOUR VESSEL", fontFamily = CdMono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CdGlow)
        Text("officer ${store.currentUser?.name} · ${store.currentUser?.role?.name} · three vessels, one fleet", fontFamily = CdMono, fontSize = 12.sp, color = CdFog)
        Spacer(Modifier.height(14.dp))
        store.branches.forEach { b ->
            val on = store.currentBranchId == b.id
            val tone = when (b.dayStatus) {
                CdDayStatus.OPEN -> CdPhos
                CdDayStatus.PAST -> CdAmber
                CdDayStatus.REMITTED -> CdGlow
            }
            Box(
                Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) CdDeckSoft else CdConsole)
                    .border(1.dp, if (on) CdGlow else CdLine, RoundedCornerShape(8.dp))
                    .clickable { store.enterBranch(b.id) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontFamily = CdSans, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                        Text("${b.kind.name} · ${b.line}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                        Spacer(Modifier.height(4.dp))
                        Text("${b.dayLabel} · ${b.dayStatus.name}", fontFamily = CdMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone)
                    }
                    CdGlowTag(b.dayStatus.name, tone)
                }
            }
        }
        Row {
            CdLink("← back to gangway") { store.logout() }
        }
    }
}

// ---------- deck shell ----------

@Composable
fun CdDeck(store: CdStore) {
    Row(Modifier.fillMaxSize()) {
        CdStationRail(store)
        val v = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(v).padding(20.dp)) {
            CdCommandStrip(store)
            Spacer(Modifier.height(10.dp))
            CdDayBanner(store)
            Spacer(Modifier.height(10.dp))
            CdAlertBoard(store)
            Spacer(Modifier.height(10.dp))
            CdDeckDivider()
            Spacer(Modifier.height(10.dp))
            when (store.station) {
                CdStation.HELM -> CdHelmStation(store)
                CdStation.SESSIONS -> CdSessionsStation(store)
                CdStation.CLIENTS -> CdClientsStation(store)
                CdStation.FINANCE -> CdFinanceStation(store)
                CdStation.TEAM -> CdTeamStation(store)
                CdStation.MAIL -> CdMailStation(store)
                CdStation.AUDIT -> CdAuditStation(store)
                CdStation.PROFILE -> CdProfileStation(store)
            }
        }
    }
}

// ---------- helm: clock + relief + status clusters ----------

@Composable
fun CdHelmStation(store: CdStore) {
    val me = store.currentUser
    CdSectionHead("HELM", "Clock in, then fly the deck", "shift + relief")
    val branchSessions = store.sessions.filter { it.branchId == store.currentBranchId }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CdCluster("${branchSessions.count { it.status == CdSessionStatus.PENDING }}", "PENDING", CdGlow)
        CdCluster("${branchSessions.count { it.status == CdSessionStatus.COMPLETED }}", "COMPLETED", CdPhos)
        CdCluster("${store.notifications.count { !it.read }}", "UNREAD", CdAmber)
        CdCluster("${store.sessions.count { it.voided }}", "VOID", CdFog)
    }
    Spacer(Modifier.height(10.dp))
    CdPanel {
        Text("SHIFT", fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, fontWeight = FontWeight.Bold)
        Text(
            if (store.clockedIn) "on shift at ${store.branchName(store.clockBranchId ?: "")} — the deck tracks you" else "off shift — ${me?.name} is standing by at the viewport",
            fontFamily = CdMono, fontSize = 12.sp, color = CdPaper,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CdPrimary(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
            CdGhost(if (store.powerDampened) "FULL POWER" else "DAMPEN CONSOLES") { store.powerDampened = !store.powerDampened }
        }
        if (store.powerDampened) {
            Spacer(Modifier.height(6.dp))
            Text("consoles dampened — glow clusters hold at half light (display only, nothing locks)", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
    Spacer(Modifier.height(10.dp))
    CdSectionHead("RELIEF", "Duty · request · invite", "relief pays from the relief drawer, ends 04:00 Manila")
    store.reliefs.forEach { r ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp)).background(CdDeck).border(1.dp, CdLine, RoundedCornerShape(8.dp)).padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CdGlowTag(r.kind.name, CdGlow)
                    Spacer(Modifier.width(8.dp))
                    Text(r.who, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper, modifier = Modifier.weight(1f))
                    CdGlowTag(r.state.name, if (r.state == CdReliefState.OPEN) CdAmber else CdPhos)
                }
                Text("${store.branchName(r.branchId)} · ${r.day}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                if (r.state == CdReliefState.OPEN) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (r.kind) {
                            CdReliefKind.REQUEST -> {
                                CdPrimary("GRANT") { r.state = CdReliefState.GRANTED; cdAudit(store, "granted relief request ${r.id}") }
                                CdGhost("DENY") { r.state = CdReliefState.DENIED }
                            }
                            CdReliefKind.INVITE -> {
                                CdPrimary("ACCEPT") { r.state = CdReliefState.ACCEPTED }
                                CdGhost("DECLINE") { r.state = CdReliefState.DECLINED }
                            }
                            CdReliefKind.DUTY -> {
                                CdPrimary("TAKE DUTY") { r.state = CdReliefState.GRANTED }
                            }
                        }
                    }
                }
            }
        }
    }
    CdNote("Relief starts view-only; edit needs a grant (broadcast request approved by any branch member, or a branch invite). Multiple relief workers may hold edit access on one day. All retraction locks once the requester clocks in as relief.")
}

// ---------- sessions ----------

@Composable
fun CdSessionsStation(store: CdStore) {
    CdSectionHead("SESSIONS", "One visit, one client, one branch", "PENDING → COMPLETED / NO_SHOW / CANCELLED")
    CdChipRow(listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"), store.sessionFilter) { store.sessionFilter = it }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = store.showVoided, onCheckedChange = { store.showVoided = it })
        Text("show voided", fontFamily = CdMono, fontSize = 12.sp, color = CdPaper)
        Spacer(Modifier.width(12.dp))
        CdGhost("+ LOG SESSION") { store.bookOpen = true; store.bookName = ""; store.bookWalkIn = false }
    }
    Spacer(Modifier.height(8.dp))
    CdNote("Walk-in sessions cannot be marked NO_SHOW or CANCELLED — a walk-in either shows or never existed. Void excludes a session from money math but keeps the record; unvoid needs no reason, void needs one.")
    Spacer(Modifier.height(8.dp))
    val list = store.sessions.filter {
        (it.branchId == store.currentBranchId) &&
            (store.sessionFilter == "ALL" || it.status.name == store.sessionFilter) &&
            (store.showVoided || !it.voided)
    }
    if (list.isEmpty()) {
        CdNote("No contacts on this scope for the current filter. Log one above.")
    }
    list.forEach { s ->
        val sel = store.selectedSessionId == s.id
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (sel) CdDeckSoft else CdConsole)
                .border(1.dp, if (sel) CdGlow else CdLine, RoundedCornerShape(8.dp))
                .clickable { store.selectedSessionId = if (sel) null else s.id }
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.ticketNo ?: s.id, fontFamily = CdMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CdGlow, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.clientName + if (s.walkIn) " · walk-in" else "", fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                        Text("${s.bookedTime} · ${s.type} · ${cdPeso(s.price)} · ${s.practitioner}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                    }
                    CdGlowTag(s.status.name, cdStatusTone(s.status))
                }
                if (s.voided) {
                    Text("VOIDED — ${s.voidReason ?: "no reason"}", fontFamily = CdMono, fontSize = 11.sp, color = CdKlaxon)
                }
                if (sel) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CdPrimary("COMPLETE") { s.status = CdSessionStatus.COMPLETED; cdAudit(store, "completed ${s.ticketNo}") }
                        CdGhost("NO-SHOW") {
                            if (s.walkIn) {
                                cdAudit(store, "refused NO_SHOW for walk-in ${s.ticketNo} (rule)")
                            } else {
                                s.status = CdSessionStatus.NO_SHOW; cdAudit(store, "marked ${s.ticketNo} NO_SHOW")
                            }
                        }
                        CdGhost("CANCEL") {
                            if (s.walkIn) {
                                cdAudit(store, "refused CANCEL for walk-in ${s.ticketNo} (rule)")
                            } else {
                                s.status = CdSessionStatus.CANCELLED; cdAudit(store, "cancelled ${s.ticketNo}")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!s.voided) {
                            CdGhost("VOID…") { store.voidDialogFor = s.id; store.voidReasonText = "" }
                        } else {
                            CdGhost("UNVOID") { s.voided = false; s.voidReason = null; cdAudit(store, "unvoided ${s.ticketNo}") }
                        }
                    }
                }
            }
        }
    }
    if (store.voidDialogFor != null) {
        AlertDialog(
            onDismissRequest = { store.voidDialogFor = null },
            title = { Text("Void session", fontFamily = CdMono) },
            text = {
                Column {
                    Text("A reason is required — the record stays on scope with a void marker.", fontFamily = CdMono, fontSize = 12.sp)
                    OutlinedTextField(value = store.voidReasonText, onValueChange = { store.voidReasonText = it }, label = { Text("reason") }, singleLine = true)
                }
            },
            confirmButton = {
                CdPrimary("VOID") {
                    val target = store.sessions.firstOrNull { it.id == store.voidDialogFor }
                    if (target != null && store.voidReasonText.isNotBlank()) {
                        target.voided = true
                        target.voidReason = store.voidReasonText
                        cdAudit(store, "voided ${target.ticketNo} (${store.voidReasonText})")
                    }
                    store.voidDialogFor = null
                }
            },
            dismissButton = { CdGhost("BACK") { store.voidDialogFor = null } },
        )
    }
    if (store.bookOpen) {
        AlertDialog(
            onDismissRequest = { store.bookOpen = false },
            title = { Text("Log session", fontFamily = CdMono) },
            text = {
                Column {
                    OutlinedTextField(value = store.bookName, onValueChange = { store.bookName = it }, label = { Text("client name") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = store.bookWalkIn, onCheckedChange = { store.bookWalkIn = it })
                        Text("walk-in (gets a W-signal; no NO_SHOW/CANCELLED)", fontFamily = CdMono, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                CdPrimary("LOG") {
                    if (store.bookName.isNotBlank()) {
                        val n = store.sessions.size + 101
                        val ticket = if (store.bookWalkIn) "W-${n - 94}" else "A-$n"
                        store.sessions.add(
                            CdSession("s-$n", "c-new", store.bookName, store.currentBranchId, "03:00", if (store.bookWalkIn) "WALK-IN" else "OTC-1", CdSessionStatus.PENDING, 650, store.bookWalkIn, ticketNo = ticket),
                        )
                        cdAudit(store, "logged $ticket for ${store.bookName}")
                    }
                    store.bookOpen = false
                }
            },
            dismissButton = { CdGhost("BACK") { store.bookOpen = false } },
        )
    }
}

private fun cdStatusTone(st: CdSessionStatus): androidx.compose.ui.graphics.Color =
    when (st) {
        CdSessionStatus.PENDING -> CdGlow
        CdSessionStatus.COMPLETED -> CdPhos
        CdSessionStatus.NO_SHOW -> CdAmber
        CdSessionStatus.CANCELLED -> CdFog
    }

private fun cdAudit(store: CdStore, action: String) {
    store.audits.add(0, CdAudit("a${store.audits.size + 1}", "now", store.currentUser?.name ?: "?", action))
}

// ---------- clients ----------

@Composable
fun CdClientsStation(store: CdStore) {
    CdSectionHead("CLIENTS", "Global registry — every vessel sees the same faces", "at most one PENDING each")
    val pending = store.pendingClientIds()
    CdNote("A client holds at most one PENDING session at a time — rows marked * PENDING are mid-visit. Anonymized rows keep gender + age for reporting, PII nulled.")
    Spacer(Modifier.height(8.dp))
    store.clients.forEach { c ->
        val live = pending.contains(c.id)
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp)).background(CdConsole).border(1.dp, CdLine, RoundedCornerShape(8.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.anonymized) "anon-${c.id} (anonymized)" else c.name + if (live) "  * PENDING" else "",
                        fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper,
                    )
                    Text(
                        if (c.anonymized) "gender ${c.gender} · age ${c.age} · PII nulled" else "${c.gender} · age ${c.age} · ${c.id}",
                        fontFamily = CdMono, fontSize = 11.sp, color = CdFog,
                    )
                }
                if (!c.anonymized) {
                    CdGhost("ANONYMIZE") { c.anonymized = true; cdAudit(store, "anonymized client ${c.id}") }
                } else {
                    CdGlowTag("SEALED", CdFog)
                }
            }
        }
    }
}

// ---------- finance ----------

@Composable
fun CdFinanceStation(store: CdStore) {
    CdSectionHead("FINANCE", "Two reactors: sessions and products", "snapshot seals, undo has 48h")
    CdRemitReactor(store, store.sessionRemit, "SESSION", "net income after compensation and expenses")
    Spacer(Modifier.height(10.dp))
    CdRemitReactor(store, store.productRemit, "PRODUCT", "unit price × quantity")
    Spacer(Modifier.height(10.dp))
    CdSectionHead("CARGO", "Product lines", "${store.productLines.size} lines")
    store.productLines.forEach { p ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp)).background(CdConsole).border(1.dp, CdLine, RoundedCornerShape(8.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                Text("${p.qty} × ${cdPeso(p.price)} = ${cdPeso(p.qty * p.price)}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
            }
            CdGhost("+") { p.qty += 1 }
            Spacer(Modifier.width(6.dp))
            CdGhost("−") { if (p.qty > 0) p.qty -= 1 }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = store.newProductName, onValueChange = { store.newProductName = it }, label = { Text("new product", fontFamily = CdMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        CdPrimary("STOW") {
            if (store.newProductName.isNotBlank()) {
                store.productLines.add(CdProductLine("p-${store.productLines.size + 1}", store.newProductName, 1, 300))
                store.newProductName = ""
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    CdNote("Commission split: product commissions pool per branch day and split equally among all practitioners and coordinators clocked in at the sold_at time. Manual inclusions/exclusions may override. Commissions ride beside remittance — never inside it.")
    if (store.undoDialogFor != null) {
        val kind = store.undoDialogFor!!
        AlertDialog(
            onDismissRequest = { store.undoDialogFor = null },
            title = { Text("Undo $kind remittance", fontFamily = CdMono) },
            text = {
                Column {
                    Text("Within 48h the snapshot is deleted and the days unlock. A reason is recorded in the audit trail.", fontFamily = CdMono, fontSize = 12.sp)
                    OutlinedTextField(value = store.undoReasonText, onValueChange = { store.undoReasonText = it }, label = { Text("reason") }, singleLine = true)
                }
            },
            confirmButton = {
                CdPrimary("UNDO") {
                    val holder = if (kind == CdRemitKind.SESSION) store.sessionRemit else store.productRemit
                    if (store.undoReasonText.isNotBlank()) {
                        holder.state = CdRemitState.DRAFT
                        holder.undoReason = store.undoReasonText
                        holder.snapshotId = null
                        holder.snapshotTotal = null
                        holder.submittedAt = null
                        cdAudit(store, "undid $kind remittance (${store.undoReasonText})")
                    }
                    store.undoDialogFor = null
                }
            },
            dismissButton = { CdGhost("BACK") { store.undoDialogFor = null } },
        )
    }
}

@Composable
fun CdRemitReactor(store: CdStore, holder: CdRemitStateHolder, title: String, sub: String) {
    CdConsoleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$title REACTOR", fontFamily = CdMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdGlow, modifier = Modifier.weight(1f))
            CdGlowTag(holder.state.name, if (holder.state == CdRemitState.DRAFT) CdAmber else CdPhos)
        }
        Text(sub, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        Spacer(Modifier.height(8.dp))
        if (holder.state == CdRemitState.DRAFT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = holder.draftGross.toString(), onValueChange = { holder.draftGross = it.toIntOrNull() ?: holder.draftGross },
                    label = { Text("gross", fontFamily = CdMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = holder.draftDeductions.toString(), onValueChange = { holder.draftDeductions = it.toIntOrNull() ?: holder.draftDeductions },
                    label = { Text("deductions", fontFamily = CdMono, fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("net ${cdPeso(holder.draftGross - holder.draftDeductions)} · drafts overlap freely", fontFamily = CdMono, fontSize = 12.sp, color = CdPaper)
            Spacer(Modifier.height(8.dp))
            Row {
                CdPrimary("SUBMIT → SEAL") {
                    holder.state = CdRemitState.SUBMITTED
                    holder.snapshotId = "snap-${holder.kind.name.lowercase()}-${store.dayFilter}"
                    holder.snapshotTotal = holder.draftGross - holder.draftDeductions
                    holder.submittedAt = "${store.dayFilter} 18:02 Manila"
                    cdAudit(store, "submitted ${holder.kind.name} remittance ${holder.snapshotId} (${cdPeso(holder.snapshotTotal ?: 0)})")
                }
            }
        } else {
            Text("sealed ${holder.snapshotId} · ${cdPeso(holder.snapshotTotal ?: 0)} · ${holder.submittedAt}", fontFamily = CdMono, fontSize = 12.sp, color = CdPhos)
            Text("immutable — later edits never rewrite the seal; only Undo within 48h reopens it", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
            Spacer(Modifier.height(8.dp))
            Row {
                CdGhost("UNDO ≤48H…") { store.undoDialogFor = holder.kind; store.undoReasonText = "" }
            }
            if (holder.undoReason != null) {
                Text("last undo: ${holder.undoReason}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
            }
        }
    }
}

// ---------- team ----------

@Composable
fun CdTeamStation(store: CdStore) {
    CdSectionHead("TEAM", "Slot order runs the watch bill", "1 = senior · relief sorts after home")
    val me = store.currentUser
    val roster = store.users.sortedBy { it.slot }
    roster.forEach { u ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp)).background(CdConsole).border(1.dp, CdLine, RoundedCornerShape(8.dp)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.slot.toString().padStart(2, '0'), fontFamily = CdMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CdGlow, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                    Text("${u.role.name} · home ${store.branchName(u.homeBranchId)}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
                }
                if (u.role == CdRole.ONBOARDING) {
                    if (me?.role == CdRole.MANAGER) {
                        CdPrimary("GRANT PRACTITIONER") {
                            val idx = store.users.indexOfFirst { it.id == u.id }
                            if (idx >= 0) store.users[idx] = u.copy(role = CdRole.PRACTITIONER)
                            cdAudit(store, "granted PRACTITIONER to ${u.name}")
                        }
                    } else {
                        CdGlowTag("HOLD", CdAmber)
                    }
                } else {
                    CdGlowTag(u.role.name, CdGlowDim)
                }
            }
        }
    }
    CdNote("Only a MANAGER grant lifts ONBOARDING off the observation deck — the role bundle is empty until team.grant writes a real one. Sign in as A. Villanueva to authorize the grant.")
}

// ---------- mail + audit ----------

@Composable
fun CdMailStation(store: CdStore) {
    CdSectionHead("MAIL", "Every signal keeps its read stamp", "tap-through names branch + day")
    CdChipRow(listOf("ALL", "UNREAD"), store.mailFilter) { store.mailFilter = it }
    Spacer(Modifier.height(8.dp))
    Row {
        CdGhost("MARK ALL READ") { store.notifications.forEach { it.read = true } }
    }
    Spacer(Modifier.height(8.dp))
    val list = store.notifications.filter { store.mailFilter == "ALL" || !it.read }
    if (list.isEmpty()) {
        CdNote("Comms silent for this filter — the deck is quiet.")
    }
    list.forEach { n ->
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (n.read) CdConsole else CdDeckSoft)
                .border(1.dp, if (n.read) CdLine else CdGlow, RoundedCornerShape(8.dp))
                .clickable { n.read = true }
                .padding(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.title, fontFamily = CdSans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdPaper, modifier = Modifier.weight(1f))
                    if (!n.read) CdGlowTag("NEW", CdAmber) else CdGlowTag("READ", CdFog)
                }
                Text(n.body, fontFamily = CdMono, fontSize = 12.sp, color = CdFog)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${store.branchName(n.branchId)} · ${n.day}", fontFamily = CdMono, fontSize = 11.sp, color = CdFog, modifier = Modifier.weight(1f))
                    CdLink("JUMP → DAY") { store.dayFilter = n.day; store.station = CdStation.HELM }
                }
            }
        }
    }
}

@Composable
fun CdAuditStation(store: CdStore) {
    CdSectionHead("AUDIT", "Newest first, kept in the black box", "${store.audits.size} rows")
    CdNote("Reads never die here — even comms reads stay on the record. REMITTED-day edits would flag coordinator-only; this deck only rehearses them.")
    Spacer(Modifier.height(8.dp))
    store.audits.forEach { a ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp)).background(CdConsole).border(1.dp, CdLine, RoundedCornerShape(8.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(a.at, fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, modifier = Modifier.width(110.dp))
            Column {
                Text(a.actor, fontFamily = CdSans, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdPaper)
                Text(a.action, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
            }
        }
    }
}

// ---------- profile ----------

@Composable
fun CdProfileStation(store: CdStore) {
    val me = store.currentUser
    CdSectionHead("PROFILE", "Your signal, your stamps", "logout + clock-out live here")
    if (me == null) {
        CdNote("Nobody is holding a signal. Head back to the gangway.")
        return
    }
    CdSignalTicket("H-002", me.name, "${me.role.name} · home ${store.branchName(me.homeBranchId)} · slot ${me.slot}")
    Spacer(Modifier.height(10.dp))
    CdPanel {
        Text("CAPABILITIES — ${me.role.name}", fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, fontWeight = FontWeight.Bold)
        val caps = cdCaps(me.role)
        if (caps.isEmpty()) {
            Text("empty bundle — the observation deck is honest about it", fontFamily = CdMono, fontSize = 12.sp, color = CdPaper)
        } else {
            caps.forEach { Text("· $it", fontFamily = CdMono, fontSize = 12.sp, color = CdPaper) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CdPrimary(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
        CdGhost("SWITCH VESSEL") { store.phase = "VESSEL" }
        CdGhost("LOGOUT") { store.logout() }
    }
    Spacer(Modifier.height(8.dp))
    CdNote("Deactivation would block login at once and vanish every capability; reactivation hands them back on a fresh sign-in. This deck only posts the notice — the gangway enforces it.")
}
