package com.companyb.companyapp.proto.sheetgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #817 — sheet-grid workbook: every flow is a spreadsheet tab. Fake SgStore only.

fun sgCaps(role: SgRole): List<String> =
    when (role) {
        SgRole.MANAGER -> {
            listOf(
                "sessions.write",
                "void.approve",
                "remit.submit",
                "remit.undo",
                "team.grant",
                "branch.close",
            )
        }

        SgRole.COORDINATOR -> {
            listOf("sessions.write", "relief.grant", "remit.draft", "clients.write")
        }

        SgRole.PRACTITIONER -> {
            listOf("sessions.write", "clock.self")
        }

        SgRole.ACCOUNTANT -> {
            listOf("remit.submit", "remit.undo", "audit.read")
        }

        SgRole.ONBOARDING -> {
            emptyList()
        }
    }

@Composable
fun SheetGridApp() {
    val store = remember { seedSgStore() }
    logInfo("SheetGrid", "sheet-grid prototype started")
    MaterialTheme(colorScheme = SgScheme) {
        Box(modifier = Modifier.fillMaxSize().background(SgPaper)) {
            when (store.phase) {
                "LOGIN" -> SgLoginSheet(store)
                "LOCKED" -> SgLockedSheet(store)
                "BRANCH" -> SgBranchSheet(store)
                else -> SgBook(store)
            }
        }
    }
}

// ---------- login / lock / branch ----------

@Composable
fun SgLoginSheet(store: SgStore) {
    val v = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text(
            "▦ COMPANYAPP — SHEET-GRID",
            fontFamily = SgMono,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SgGreenDark,
        )
        Text(
            "sign-in workbook · range A1:D6 · fake auth, any secret works",
            fontFamily = SgMono,
            fontSize = 12.sp,
            color = SgMuted,
        )
        Spacer(Modifier.height(14.dp))
        SgColumnLetters(listOf(1.4f, 1f, 1.2f, 0.8f))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(52.dp))
            SgHeaderCell("OPERATOR", 1.4f)
            SgHeaderCell("ROLE", 1f)
            SgHeaderCell("HOME BRANCH", 1.2f)
            SgHeaderCell("SLOT", 0.8f)
        }
        store.users.forEachIndexed { i, u ->
            val active = store.activeRow == i + 1
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable {
                        store.activeRow = i + 1
                        store.loginSecret = ""
                        store.loginError =
                            ""
                    },
            ) {
                SgRowNum(i + 1, active)
                SgCell(u.name, 1.4f, active, bold = true)
                SgCell(u.role.name, 1f, active)
                SgCell(store.branchName(u.homeBranchId), 1.2f, active)
                SgCell("${u.slot}", 0.8f, active, align = TextAlign.Right)
            }
        }
        Spacer(Modifier.height(12.dp))
        val picked = store.users.getOrNull((store.activeRow - 1).coerceIn(0, store.users.size - 1))
        SgFormulaBar("B${store.activeRow}", picked?.let { "${it.name} · ${it.role} — enter secret, press LOGIN" } ?: "")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = store.loginSecret,
            onValueChange = {
                store.loginSecret = it
                store.loginError = ""
            },
            label = { Text("secret (any value)", fontFamily = SgMono, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (store.loginError.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SgNote(store.loginError)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            picked?.let { u -> SgPrimary("LOGIN → ${u.name}") { store.login(u) } }
            Spacer(Modifier.width(10.dp))
            Text("ONBOARDING row routes to the lock screen.", fontFamily = SgMono, fontSize = 11.sp, color = SgMuted)
        }
    }
}

@Composable
fun SgLockedSheet(store: SgStore) {
    Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        Text(
            "▦ SHEET-GRID — LOCKED",
            fontFamily = SgMono,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SgToneRedFg,
        )
        Spacer(Modifier.height(10.dp))
        SgNote(
            "K. Dela Pena holds role ONBOARDING: zero capabilities. No branch, no sessions, no finance. Sign out to continue judging.",
        )
        Spacer(Modifier.height(12.dp))
        SgSectionTitle("A1", "CAPABILITY RANGE — EMPTY", "0 rows")
        SgEmptyRow("no capabilities for ONBOARDING — range is blank by policy")
        Spacer(Modifier.height(14.dp))
        Row {
            SgPrimary("SIGN OUT") { store.logout() }
        }
    }
}

@Composable
fun SgBranchSheet(store: SgStore) {
    val v = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(v).padding(28.dp)) {
        Text(
            "▦ SHEET-GRID — BRANCH SELECT",
            fontFamily = SgMono,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SgGreenDark,
        )
        Text(
            "operator ${store.currentUser?.name} · pick the active branch tab",
            fontFamily = SgMono,
            fontSize = 12.sp,
            color = SgMuted,
        )
        Spacer(Modifier.height(14.dp))
        SgColumnLetters(listOf(1.6f, 1f, 1.4f))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(52.dp))
            SgHeaderCell("BRANCH", 1.6f)
            SgHeaderCell("KIND", 1f)
            SgHeaderCell("LINE", 1.4f)
        }
        store.branches.forEachIndexed { i, b ->
            val active = store.currentBranchId == b.id
            Row(modifier = Modifier.fillMaxWidth().clickable { store.enterBranch(b.id) }) {
                SgRowNum(i + 1, active)
                SgCell(b.name, 1.6f, active, bold = true)
                SgCell(b.kind.name, 1f, active)
                SgCell(b.line, 1.4f, active)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            SgLink("← back to sign-in") { store.logout() }
        }
    }
}

// ---------- workbook ----------

@Composable
fun SgBook(store: SgStore) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (store.sheet) {
            "HOME" -> SgHomeSheet(store)
            "SESSIONS" -> SgSessionsSheet(store)
            "CLIENTS" -> SgClientsSheet(store)
            "FINANCE" -> SgFinanceSheet(store)
            "TEAM" -> SgTeamSheet(store)
            "MAIL" -> SgMailSheet(store)
            "AUDIT" -> SgAuditSheet(store)
            "PROFILE" -> SgProfileSheet(store)
        }
    }
}

fun sgClamp(
    store: SgStore,
    size: Int,
): Int {
    store.activeRow = store.activeRow.coerceIn(1, maxOf(1, size))
    return store.activeRow
}

// ---------- HOME ----------

@Composable
fun SgHomeSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet, store.currentBranchId, store.dayIndex) { focus.requestFocus() }
    val branchSessions = store.sessions.filter { it.branchId == store.currentBranchId && !it.voided }
    val pend = branchSessions.count { it.status == SgSessionStatus.PENDING }
    val done = branchSessions.count { it.status == SgSessionStatus.COMPLETED }
    val reliefOpen = store.reliefs.count { it.state == SgReliefState.OPEN }
    val unread = store.notifs.count { !it.read }
    SgWorkbookShell(
        store,
        "HOME!A1",
        "clock ${if (store.clockedIn) "IN" else "OUT"} · $pend pending · $done completed · day ${store.currentDay().status}",
        "HOME · ${store.branchName(store.currentBranchId)} · ${store.currentDay().label} ${store.currentDay().status}",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle("HOME!A", "TODAY — " + store.branchName(store.currentBranchId), store.currentDay().label)
            SgStatStrip(
                listOf(
                    "PENDING" to "$pend",
                    "COMPLETED" to "$done",
                    "RELIEF OPEN" to "$reliefOpen",
                    "UNREAD" to "$unread",
                ),
            )
            SgActionStrip("CLOCK") {
                SgMiniAction(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
            }
            SgSectionTitle("HOME!B", "RELIEF GRID — DUTY / REQUEST / INVITE", "${store.reliefs.size} rows")
            SgColumnLetters(listOf(0.7f, 1.3f, 1.8f, 0.9f, 1.4f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("KIND", 0.7f)
                SgHeaderCell("WHO", 1.3f)
                SgHeaderCell("DETAIL", 1.8f)
                SgHeaderCell("STATE", 0.9f)
                SgHeaderCell("ACT", 1.4f)
            }
            if (store.reliefs.isEmpty()) SgEmptyRow("relief grid empty — nobody needs cover")
            store.reliefs.forEachIndexed { i, r ->
                val active = sgClamp(store, store.reliefs.size) == i + 1
                Row(modifier = Modifier.fillMaxWidth().clickable { store.activeRow = i + 1 }) {
                    SgRowNum(i + 1, active)
                    SgCell(r.kind.name, 0.7f, active, bold = true)
                    SgCell(r.who, 1.3f, active)
                    SgCell(r.detail, 1.8f, active)
                    SgCell(r.state.name, 0.9f, active)
                    Row(
                        modifier =
                            Modifier
                                .weight(1.4f)
                                .background(SgPaper)
                                .border(0.5.dp, SgGridLine)
                                .padding(4.dp),
                    ) {
                        if (r.state == SgReliefState.OPEN) {
                            when (r.kind) {
                                SgReliefKind.REQUEST -> {
                                    SgMiniAction("GRANT") { store.reliefAct(r.id, true) }
                                    Spacer(Modifier.width(6.dp))
                                    SgMiniAction("DENY") { store.reliefAct(r.id, false) }
                                }

                                else -> {
                                    SgMiniAction("ACCEPT") { store.reliefAct(r.id, true) }
                                    Spacer(Modifier.width(6.dp))
                                    SgMiniAction("DECLINE") { store.reliefAct(r.id, false) }
                                }
                            }
                        } else {
                            Text(
                                "— ${r.state}",
                                fontFamily = SgMono,
                                fontSize = 11.sp,
                                color = SgMuted,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            SgNote(
                "Duty = cover a shift · Request = needs grant/deny · Invite = needs accept/decline. Every action lands in AUDIT.",
            )
        }
    }
}

// ---------- SESSIONS ----------

@Composable
fun SgSessionsSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet, store.currentBranchId, store.sessionFilter) { focus.requestFocus() }
    var ruleMsg by remember { mutableStateOf("") }
    val rows =
        store.sessions.filter { s ->
            (s.branchId == store.currentBranchId) &&
                (
                    store.sessionFilter == "ALL" || (store.sessionFilter == "VOIDED" && s.voided) ||
                        (!s.voided && s.status.name == store.sessionFilter)
                )
        }
    val active = sgClamp(store, rows.size)
    val sel = rows.getOrNull(active - 1)
    SgWorkbookShell(
        store,
        "SESSIONS!A$active",
        sel?.let { "${it.id} · ${it.clientName} · ${it.status}${if (it.voided) " · VOIDED(${it.voidReason})" else ""}" }
            ?: "no row",
        "SESSIONS · filter ${store.sessionFilter} · ${rows.size} rows",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle(
                "SESSIONS!A",
                "SESSION GRID — " + store.branchName(store.currentBranchId),
                "${rows.size} rows",
            )
            SgActionStrip("FILTER") {
                listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED", "VOIDED").forEach { f ->
                    SgMiniAction(
                        (
                            if (store.sessionFilter ==
                                f
                            ) {
                                "◉ "
                            } else {
                                "○ "
                            }
                        ) + f,
                    ) {
                        store.sessionFilter = f
                        store.activeRow = 1
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                SgMiniAction("+ BOOK") { store.showBook = true }
            }
            SgColumnLetters(listOf(0.7f, 1.3f, 0.7f, 1f, 0.9f, 0.9f, 1.1f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("CLIENT", 1.3f)
                SgHeaderCell("TIME", 0.7f)
                SgHeaderCell("TYPE", 1f)
                SgHeaderCell("STATUS", 0.9f)
                SgHeaderCell("PRICE", 0.9f)
                SgHeaderCell("FLAGS", 1.1f)
            }
            if (rows.isEmpty()) SgEmptyRow("filter ${store.sessionFilter} returns zero rows")
            rows.forEachIndexed { i, s ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            store.activeRow = i + 1
                            ruleMsg = ""
                        },
                ) {
                    SgRowNum(i + 1, active == i + 1)
                    SgCell(s.clientName, 1.3f, active == i + 1, bold = true)
                    SgCell(s.bookedTime, 0.7f, active == i + 1)
                    SgCell(s.type, 1f, active == i + 1)
                    SgCell(if (s.voided) "VOIDED" else s.status.name, 0.9f, active == i + 1)
                    SgCell("₱${s.price}", 0.9f, active == i + 1, align = TextAlign.Right)
                    SgCell(
                        buildString {
                            if (s.walkIn) append("WALK-IN ${s.ticketNo ?: ""} ")
                            if (s.voided) append("void:${s.voidReason ?: ""}")
                        }.ifBlank { "—" },
                        1.1f,
                        active == i + 1,
                    )
                }
            }
            if (sel != null) {
                SgActionStrip("ROW $active — ${sel.id}") {
                    SgMiniAction("COMPLETE") { ruleMsg = store.setSessionStatus(sel.id, SgSessionStatus.COMPLETED) }
                    Spacer(Modifier.width(6.dp))
                    SgMiniAction("NO-SHOW") { ruleMsg = store.setSessionStatus(sel.id, SgSessionStatus.NO_SHOW) }
                    Spacer(Modifier.width(6.dp))
                    SgMiniAction("CANCEL") { ruleMsg = store.setSessionStatus(sel.id, SgSessionStatus.CANCELLED) }
                    Spacer(Modifier.width(6.dp))
                    if (sel.voided) {
                        SgMiniAction("UNVOID") {
                            store.unvoidSession(sel.id)
                            ruleMsg = ""
                        }
                    } else {
                        SgMiniAction("VOID…") {
                            store.voidTarget = sel
                            store.showVoid = true
                        }
                    }
                }
            }
            if (ruleMsg.isNotEmpty()) SgNote(ruleMsg)
            SgNote(
                "Rule: walk-in rows refuse NO_SHOW/CANCELLED (served or voided only). Void/unvoid always needs a reason and writes AUDIT.",
            )
        }
    }
    if (store.showVoid && store.voidTarget != null) {
        AlertDialog(
            onDismissRequest = { store.showVoid = false },
            title = {
                Text(
                    "Void ${store.voidTarget?.id} — reason required",
                    fontFamily = SgMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value = store.voidReason,
                    onValueChange = { store.voidReason = it },
                    label = { Text("void reason", fontFamily = SgMono, fontSize = 12.sp) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { SgMiniAction("VOID ROW") { store.voidSession(store.voidReason) } },
            dismissButton = { SgMiniAction("KEEP") { store.showVoid = false } },
        )
    }
    if (store.showBook) {
        AlertDialog(
            onDismissRequest = { store.showBook = false },
            title = {
                Text(
                    "Book session — new row",
                    fontFamily = SgMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = store.bookName,
                        onValueChange = { store.bookName = it },
                        label = { Text("client name", fontFamily = SgMono, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = store.bookType,
                        onValueChange = { store.bookType = it },
                        label = { Text("service type", fontFamily = SgMono, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = store.bookWalkIn, onCheckedChange = { store.bookWalkIn = it })
                        Text("walk-in (gets W-ticket, no NO_SHOW/CANCELLED)", fontFamily = SgMono, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { SgMiniAction("INSERT ROW") { store.bookSession() } },
            dismissButton = { SgMiniAction("CLOSE") { store.showBook = false } },
        )
    }
}

// ---------- CLIENTS ----------

@Composable
fun SgClientsSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet) { focus.requestFocus() }
    val pending = store.pendingClientIds()
    val active = sgClamp(store, store.clients.size)
    val sel = store.clients.getOrNull(active - 1)
    SgWorkbookShell(
        store,
        "CLIENTS!A$active",
        sel?.let {
            "${it.id} · ${it.name} · ${if (pending.contains(
                    it.id,
                )
            ) {
                "HAS PENDING"
            } else {
                "no pending"
            }}"
        } ?: "",
        "CLIENTS · global registry · ${store.clients.size} rows",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle("CLIENTS!A", "CLIENT REGISTRY — GLOBAL (ALL BRANCHES)", "${store.clients.size} rows")
            SgColumnLetters(listOf(1.4f, 0.7f, 0.5f, 1f, 1.2f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("NAME", 1.4f)
                SgHeaderCell("GENDER", 0.7f)
                SgHeaderCell("AGE", 0.5f)
                SgHeaderCell("PENDING", 1f)
                SgHeaderCell("ACT", 1.2f)
            }
            store.clients.forEachIndexed { i, c ->
                Row(modifier = Modifier.fillMaxWidth().clickable { store.activeRow = i + 1 }) {
                    SgRowNum(i + 1, active == i + 1)
                    SgCell(c.name + if (c.anonymized) " [anon]" else "", 1.4f, active == i + 1, bold = true)
                    SgCell(if (c.anonymized) "—" else c.gender, 0.7f, active == i + 1)
                    SgCell(if (c.anonymized) "—" else "${c.age}", 0.5f, active == i + 1, align = TextAlign.Right)
                    SgCell(if (pending.contains(c.id)) "* PENDING" else "—", 1f, active == i + 1)
                    Row(
                        modifier =
                            Modifier
                                .weight(1.2f)
                                .background(SgPaper)
                                .border(0.5.dp, SgGridLine)
                                .padding(4.dp),
                    ) {
                        if (!c.anonymized) {
                            SgMiniAction("ANONYMIZE") { store.anonymize(c.id) }
                        } else {
                            Text(
                                "PII nulled · gender/age kept",
                                fontFamily = SgMono,
                                fontSize = 10.5.sp,
                                color = SgMuted,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            SgNote(
                "At most one live PENDING session per client — the * PENDING flag marks it. Anonymize nulls PII but keeps gender/age for reports.",
            )
        }
    }
}

// ---------- FINANCE ----------

@Composable
fun SgFinanceSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet) { focus.requestFocus() }
    val sr = store.sessionRemit
    val pr = store.productRemit
    val pTotal = store.productLines.sumOf { it.qty * it.price }
    SgWorkbookShell(
        store,
        "FINANCE!A1",
        "SESSION ${sr.state} net=${sr.draftGross - sr.draftDeductions} · PRODUCT ${pr.state} total=$pTotal",
        "FINANCE · SESSION+PRODUCT · 48h undo window",
    ) {
        val v = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(v).sgFocusableGrid(focus, {}, {})) {
            SgSectionTitle("FINANCE!A", "SESSION REMITTANCE — DRAFT MATH", sr.state.name)
            if (sr.state == SgRemitState.SUBMITTED) {
                SgNote(
                    "Sealed ${sr.snapshotId} · total ₱${sr.snapshotTotal} · ${sr.submittedAt}. Undo reopens to DRAFT (48h rule).",
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("FIELD", 1f)
                SgHeaderCell("VALUE", 1f)
                SgHeaderCell("ADJUST", 1.4f)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                SgRowNum(1)
                SgCell("gross", 1f, bold = true)
                SgCell("₱${sr.draftGross}", 1f, align = TextAlign.Right)
                Row(
                    modifier =
                        Modifier
                            .weight(1.4f)
                            .background(SgPaper)
                            .border(0.5.dp, SgGridLine)
                            .padding(4.dp),
                ) {
                    SgMiniAction("+500") {
                        store.sessionRemit =
                            sr.copy(state = SgRemitState.DRAFT, draftGross = sr.draftGross + 500)
                    }
                    Spacer(Modifier.width(6.dp))
                    SgMiniAction("−500") {
                        store.sessionRemit =
                            sr.copy(state = SgRemitState.DRAFT, draftGross = maxOf(0, sr.draftGross - 500))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                SgRowNum(2)
                SgCell("deductions", 1f, bold = true)
                SgCell("₱${sr.draftDeductions}", 1f, align = TextAlign.Right)
                Row(
                    modifier =
                        Modifier
                            .weight(1.4f)
                            .background(SgPaper)
                            .border(0.5.dp, SgGridLine)
                            .padding(4.dp),
                ) {
                    SgMiniAction("+100") {
                        store.sessionRemit =
                            sr.copy(state = SgRemitState.DRAFT, draftDeductions = sr.draftDeductions + 100)
                    }
                    Spacer(Modifier.width(6.dp))
                    SgMiniAction("−100") {
                        store.sessionRemit =
                            sr.copy(state = SgRemitState.DRAFT, draftDeductions = maxOf(0, sr.draftDeductions - 100))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                SgRowNum(3)
                SgCell("= gross − deductions", 1f, bold = true, bg = SgGreenTint)
                SgCell(
                    "₱${sr.draftGross - sr.draftDeductions}",
                    1f,
                    bg = SgGreenTint,
                    bold = true,
                    align = TextAlign.Right,
                )
                Row(
                    modifier =
                        Modifier
                            .weight(1.4f)
                            .background(SgPaper)
                            .border(0.5.dp, SgGridLine)
                            .padding(4.dp),
                ) {
                    if (sr.state ==
                        SgRemitState.DRAFT
                    ) {
                        SgMiniAction("SUBMIT → SNAPSHOT") { store.submitRemit(SgRemitKind.SESSION) }
                    } else {
                        SgMiniAction("UNDO ≤48h…") {
                            store.undoTarget = SgRemitKind.SESSION
                            store.showUndo = true
                        }
                    }
                }
            }
            SgSectionTitle("FINANCE!B", "PRODUCT REMITTANCE — LINE ITEMS", pr.state.name)
            if (pr.state == SgRemitState.SUBMITTED) {
                SgNote(
                    "Sealed ${pr.snapshotId} · total ₱${pr.snapshotTotal} · ${pr.submittedAt}. Undo reopens to DRAFT (48h rule).",
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("ITEM", 1.4f)
                SgHeaderCell("QTY", 0.6f)
                SgHeaderCell("PRICE", 0.8f)
                SgHeaderCell("LINE TOTAL", 0.8f)
            }
            if (store.productLines.isEmpty()) SgEmptyRow("no product lines — add one below")
            store.productLines.forEachIndexed { i, l ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    SgRowNum(i + 1)
                    SgCell(l.name, 1.4f, bold = true)
                    SgCell("${l.qty}", 0.6f, align = TextAlign.Right)
                    SgCell("₱${l.price}", 0.8f, align = TextAlign.Right)
                    SgCell("₱${l.qty * l.price}", 0.8f, bold = true, align = TextAlign.Right)
                }
            }
            SgActionStrip("PRODUCT LINES Σ=₱$pTotal") {
                SgMiniAction("+ BALM") {
                    store.productLines.add(SgProductLine("Ginger balm", 1, 220))
                    store.productRemit =
                        pr.copy(state = SgRemitState.DRAFT)
                }
                Spacer(Modifier.width(6.dp))
                SgMiniAction("− LAST") {
                    if (store.productLines.isNotEmpty()) {
                        store.productLines.removeAt(store.productLines.size - 1)
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (pr.state ==
                    SgRemitState.DRAFT
                ) {
                    SgMiniAction("SUBMIT → SNAPSHOT") { store.submitRemit(SgRemitKind.PRODUCT) }
                } else {
                    SgMiniAction("UNDO ≤48h…") {
                        store.undoTarget = SgRemitKind.PRODUCT
                        store.showUndo = true
                    }
                }
            }
            if (sr.undoReason !=
                null
            ) {
                SgNote(
                    "SESSION undo reason on file: ${sr.undoReason} (within 48h of ${sr.submittedAt ?: "submit"}).",
                )
            }
            if (pr.undoReason !=
                null
            ) {
                SgNote(
                    "PRODUCT undo reason on file: ${pr.undoReason} (within 48h of ${pr.submittedAt ?: "submit"}).",
                )
            }
            SgNote(
                "Commission split: pooled per Branch day, divided equally across clocked-in crew — tracked outside remittance, inside AUDIT.",
            )
        }
    }
    if (store.showUndo && store.undoTarget != null) {
        AlertDialog(
            onDismissRequest = { store.showUndo = false },
            title = {
                Text(
                    "Undo ${store.undoTarget} snapshot — reason required",
                    fontFamily = SgMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        "48h rule: undo only within 48h of submit. Snapshot stays in history.",
                        fontFamily = SgMono,
                        fontSize = 12.sp,
                        color = SgMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = store.undoReason,
                        onValueChange = { store.undoReason = it },
                        label = { Text("undo reason", fontFamily = SgMono, fontSize = 12.sp) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { SgMiniAction("UNDO → DRAFT") { store.undoRemit(store.undoTarget!!, store.undoReason) } },
            dismissButton = { SgMiniAction("KEEP SEALED") { store.showUndo = false } },
        )
    }
}

// ---------- TEAM ----------

@Composable
fun SgTeamSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet) { focus.requestFocus() }
    val active = sgClamp(store, store.users.size)
    SgWorkbookShell(
        store,
        "TEAM!A$active",
        "slot order · relief sorts last · ONBOARDING locked",
        "TEAM · ${store.users.size} rows · roles",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle("TEAM!A", "ROSTER — SLOT ORDER", "relief sorts last")
            SgColumnLetters(listOf(0.6f, 1.3f, 1f, 1.2f, 1.2f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("SLOT", 0.6f)
                SgHeaderCell("NAME", 1.3f)
                SgHeaderCell("ROLE", 1f)
                SgHeaderCell("HOME", 1.2f)
                SgHeaderCell("ACT", 1.2f)
            }
            store.users.sortedBy { it.slot }.forEachIndexed { i, u ->
                Row(modifier = Modifier.fillMaxWidth().clickable { store.activeRow = i + 1 }) {
                    SgRowNum(i + 1, active == i + 1)
                    SgCell("${u.slot}", 0.6f, active == i + 1, align = TextAlign.Right)
                    SgCell(u.name, 1.3f, active == i + 1, bold = true)
                    SgCell(u.role.name, 1f, active == i + 1)
                    SgCell(store.branchName(u.homeBranchId), 1.2f, active == i + 1)
                    Row(
                        modifier =
                            Modifier
                                .weight(1.2f)
                                .background(SgPaper)
                                .border(0.5.dp, SgGridLine)
                                .padding(4.dp),
                    ) {
                        if (u.role == SgRole.ONBOARDING && store.isManager()) {
                            SgMiniAction("GRANT PRACT.") {
                                val k = store.users.indexOfFirst { it.id == u.id }
                                if (k >= 0) store.users[k] = u.copy(role = SgRole.PRACTITIONER)
                                store.audit("TEAM_GRANT", "${u.name} → PRACTITIONER")
                            }
                        } else if (u.role == SgRole.ONBOARDING) {
                            Text(
                                "locked · MANAGER grants",
                                fontFamily = SgMono,
                                fontSize = 10.5.sp,
                                color = SgMuted,
                                modifier = Modifier.padding(4.dp),
                            )
                        } else {
                            Text(
                                sgCaps(u.role).joinToString(" · "),
                                fontFamily = SgMono,
                                fontSize = 10.5.sp,
                                color = SgMuted,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            SgNote(
                "ONBOARDING row is locked (zero capabilities) until a MANAGER grants Practitioner. You are ${store.currentUser?.role ?: "—"}.",
            )
        }
    }
}

// ---------- MAIL + AUDIT ----------

@Composable
fun SgMailSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet, store.mailFilter) { focus.requestFocus() }
    val rows = store.notifs.filter { store.mailFilter == "ALL" || (store.mailFilter == "UNREAD" && !it.read) }
    val active = sgClamp(store, rows.size)
    SgWorkbookShell(
        store,
        "MAIL!A$active",
        if (store.mailJump.isNotEmpty()) store.mailJump else "${rows.count { !it.read }} unread",
        "MAIL · mailbox ${store.mailFilter} · tap row = read",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle("MAIL!A", "NOTIFICATION MAILBOX", "${rows.count { !it.read }} unread")
            SgActionStrip("FILTER") {
                SgMiniAction(
                    (
                        if (store.mailFilter ==
                            "ALL"
                        ) {
                            "◉ ALL"
                        } else {
                            "○ ALL"
                        }
                    ),
                ) {
                    store.mailFilter = "ALL"
                    store.activeRow = 1
                }
                Spacer(Modifier.width(6.dp))
                SgMiniAction(
                    (
                        if (store.mailFilter ==
                            "UNREAD"
                        ) {
                            "◉ UNREAD"
                        } else {
                            "○ UNREAD"
                        }
                    ),
                ) {
                    store.mailFilter = "UNREAD"
                    store.activeRow = 1
                }
                Spacer(Modifier.width(6.dp))
                SgMiniAction("MARK ALL READ") { store.markAllRead() }
            }
            SgColumnLetters(listOf(1.4f, 2f, 0.8f, 0.9f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("TITLE", 1.4f)
                SgHeaderCell("BODY", 2f)
                SgHeaderCell("READ", 0.8f)
                SgHeaderCell("JUMP", 0.9f)
            }
            if (rows.isEmpty()) SgEmptyRow("mailbox empty for filter ${store.mailFilter}")
            rows.forEachIndexed { i, n ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            store.activeRow = i + 1
                            store.markRead(n.id)
                        },
                ) {
                    SgRowNum(i + 1, active == i + 1)
                    SgCell(n.title, 1.4f, active == i + 1, bold = !n.read)
                    SgCell(n.body, 2f, active == i + 1)
                    SgCell(if (n.read) "READ" else "UNREAD", 0.8f, active == i + 1)
                    Row(
                        modifier =
                            Modifier
                                .weight(0.9f)
                                .background(SgPaper)
                                .border(0.5.dp, SgGridLine)
                                .padding(4.dp),
                    ) {
                        SgMiniAction("JUMP→DAY") {
                            val k = store.days.indexOfFirst { it.label == n.day }
                            if (k >= 0) store.dayIndex = k
                            store.sheet = "HOME"
                            store.mailJump = "jumped MAIL→HOME @ ${n.day}"
                            store.audit("MAIL_JUMP", "${n.id} → ${n.day}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SgAuditSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet) { focus.requestFocus() }
    val active = sgClamp(store, store.audits.size)
    SgWorkbookShell(
        store,
        "AUDIT!A$active",
        "${store.audits.size} rows · newest first · immutable",
        "AUDIT · append-only · ${store.audits.size} rows",
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().sgFocusableGrid(focus, { store.activeRow -= 1 }, {
                    store.activeRow +=
                        1
                }),
        ) {
            SgSectionTitle("AUDIT!A", "AUDIT LOG — NEWEST FIRST", "${store.audits.size} rows")
            SgColumnLetters(listOf(0.9f, 0.9f, 1.1f, 1.3f, 1.2f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("WHEN", 0.9f)
                SgHeaderCell("WHO", 0.9f)
                SgHeaderCell("ACTION", 1.1f)
                SgHeaderCell("TARGET", 1.3f)
                SgHeaderCell("REASON", 1.2f)
            }
            store.audits.forEachIndexed { i, a ->
                Row(modifier = Modifier.fillMaxWidth().clickable { store.activeRow = i + 1 }) {
                    SgRowNum(i + 1, active == i + 1)
                    SgCell(a.whenLabel, 0.9f, active == i + 1)
                    SgCell(a.who, 0.9f, active == i + 1)
                    SgCell(a.action, 1.1f, active == i + 1, bold = true)
                    SgCell(a.target, 1.3f, active == i + 1)
                    SgCell(a.reason ?: "—", 1.2f, active == i + 1)
                }
            }
        }
    }
}

// ---------- PROFILE ----------

@Composable
fun SgProfileSheet(store: SgStore) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(store.sheet) { focus.requestFocus() }
    val u = store.currentUser
    SgWorkbookShell(
        store,
        "PROFILE!A1",
        u?.let { "${it.name} · ${it.role} · caps=${sgCaps(it.role).size}" } ?: "",
        "PROFILE · logout · clock-out",
    ) {
        Column(modifier = Modifier.fillMaxWidth().sgFocusableGrid(focus, {}, {})) {
            SgSectionTitle("PROFILE!A", "OPERATOR — ${u?.name ?: "—"}", u?.role?.name ?: "")
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(52.dp))
                SgHeaderCell("FIELD", 1f)
                SgHeaderCell("VALUE", 1.6f)
            }
            listOf(
                "name" to (u?.name ?: "—"),
                "role" to (u?.role?.name ?: "—"),
                "home" to (u?.let { store.branchName(it.homeBranchId) } ?: "—"),
                "branch" to store.branchName(store.currentBranchId),
                "clock" to if (store.clockedIn) "CLOCKED IN" else "CLOCKED OUT",
            ).forEachIndexed { i, (k, v) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    SgRowNum(i + 1)
                    SgCell(k, 1f, bold = true)
                    SgCell(v, 1.6f)
                }
            }
            SgSectionTitle("PROFILE!B", "CAPABILITY RANGE", "${u?.let { sgCaps(it.role).size } ?: 0} rows")
            val caps = u?.let { sgCaps(it.role) } ?: emptyList()
            if (caps.isEmpty()) SgEmptyRow("ONBOARDING: blank range — nothing granted")
            caps.forEachIndexed { i, c ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    SgRowNum(i + 1)
                    SgCell(c, 2.6f)
                }
            }
            SgActionStrip("SESSION") {
                SgMiniAction(if (store.clockedIn) "CLOCK OUT" else "CLOCK IN") { store.toggleClock() }
                Spacer(Modifier.width(6.dp))
                SgMiniAction("SWITCH BRANCH") { store.phase = "BRANCH" }
                Spacer(Modifier.width(6.dp))
                SgMiniAction("LOGOUT") { store.logout() }
            }
        }
    }
}
