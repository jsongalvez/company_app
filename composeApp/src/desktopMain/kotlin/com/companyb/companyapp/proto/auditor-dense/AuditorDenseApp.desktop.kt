package com.companyb.companyapp.proto.auditordense

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
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
import com.companyb.companyapp.util.logInfo

private const val TAG = "AuditorDenseApp"

@Composable
fun AuditorDenseApp() {
    val store = remember { ADFakeStore().also { it.seed() } }
    logInfo(TAG, "auditor-dense prototype opened")
    Box(
        modifier = Modifier.fillMaxSize().background(ADTheme.paper),
    ) {
        val user = store.currentUser.value
        if (user == null) {
            ADLogin(store)
        } else if (user.role == ADRole.ONBOARDING) {
            ADLocked(store, user)
        } else {
            ADShell(store, user)
        }
    }
}

@Composable
private fun ADLogin(store: ADFakeStore) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("AUDITOR // DENSE", color = ADTheme.faint, fontSize = 12.sp,
            fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        Text("Sign in — pick a ledger identity", color = ADTheme.ink, fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        ADNote("Fake users only. No passwords, no network. ONBOARDING identity opens the locked screen.")
        Spacer(Modifier.height(12.dp))
        ADLedgerCard("USER REGISTER") {
            ADHeaderRow {
                ADHead("USER", Modifier.weight(2f))
                ADHead("ROLE", Modifier.weight(1.4f))
                ADHead("HOME", Modifier.weight(1.4f))
                ADHead("SLOT", Modifier.weight(0.5f), alignEnd = true)
            }
            store.users.forEachIndexed { i, u ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                        .clickable { store.login(u) }
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADCell(u.name, FontWeight.SemiBold, modifier = Modifier.weight(2f))
                    ADChip(u.role.label, if (u.role == ADRole.ONBOARDING) ADTheme.debit else ADTheme.slate)
                    Spacer(Modifier.width(4.dp))
                    ADCell(store.branchName(u.homeBranchId), modifier = Modifier.weight(1.4f))
                    ADCell("slot ${u.slot}", mono = true, modifier = Modifier.weight(0.9f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ADNote("Row click = sign in. Every sign-in writes a LOGIN row to the audit log.")
    }
}

@Composable
private fun ADLocked(store: ADFakeStore, user: ADUser) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ADChip("ONBOARDING — LOCKED", ADTheme.debit, filled = true)
        Spacer(Modifier.height(12.dp))
        Text("No capabilities, no surfaces", color = ADTheme.ink, fontSize = 20.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "${user.name}: the role bundle is empty, so nothing derives — even with a branch " +
                "assignment. A MANAGER must grant a real role via MANAGE_USERS.",
            color = ADTheme.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADAction("Sign out") { store.logout() }
        }
    }
}

@Composable
private fun ADShell(store: ADFakeStore, user: ADUser) {
    var branchPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ADTheme.ink).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AUDITOR//DENSE", color = Color(0xFFE9E3CF), fontSize = 12.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { branchPicker = true }
                    .border(1.dp, Color(0xFF8A8471), RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("▤ ${store.branchName(store.currentBranchId.value)} ▾", color = Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            Text(user.name, color = Color(0xFFE9E3CF), fontSize = 12.sp)
            Text(" · ${user.role.label}", color = Color(0xFF8A8471), fontSize = 12.sp,
                fontFamily = ADTheme.mono)
            Spacer(Modifier.weight(1f))
            val unread = store.notifications.count { !it.read }
            Text("$unread UNREAD", color = Color(0xFFE9E3CF), fontSize = 11.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(if (store.clockedIn.value) "● ON DUTY" else "○ OFF DUTY",
                color = if (store.clockedIn.value) Color(0xFF7BD88F) else Color(0xFF8A8471),
                fontSize = 11.sp, fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
        }
        ADBranchDayBanner(store)
        Row(
            modifier = Modifier.fillMaxWidth().background(ADTheme.paperDeep)
                .border(1.dp, ADTheme.hairline).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADTab.entries.forEach { tab ->
                val selected = store.selectedTab.value == tab
                val badge = when (tab) {
                    ADTab.AUDIT -> " ${store.audit.size}"
                    ADTab.MAILBOX -> {
                        val n = store.notifications.count { !it.read }
                        if (n > 0) " $n" else ""
                    }
                    else -> ""
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) ADTheme.ink else Color.Transparent)
                        .clickable { store.selectedTab.value = tab }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (tab.label + badge).uppercase(),
                        color = if (selected) Color.White else ADTheme.slate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ADTheme.mono,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            when (store.selectedTab.value) {
                ADTab.REGISTER -> ADRegister(store, user) { branchPicker = true }
                ADTab.SESSIONS -> ADSessions(store)
                ADTab.CLIENTS -> ADClients(store)
                ADTab.FINANCE -> ADFinance(store)
                ADTab.AUDIT -> ADAudit(store)
                ADTab.TEAM -> ADTeam(store)
                ADTab.MAILBOX -> ADMailbox(store)
                ADTab.PROFILE -> ADProfile(store, user)
            }
        }
    }
    if (branchPicker) {
        ADBranchPicker(store) { branchPicker = false }
    }
}

@Composable
private fun ADBranchDayBanner(store: ADFakeStore) {
    val branchId = store.currentBranchId.value
    val date = store.currentDayDate.value
    val state = store.dayState(branchId, date)
    val dates = listOf("2026-09-08", "2026-09-09", "2026-09-10")
    Row(
        modifier = Modifier.fillMaxWidth().background(ADTheme.card)
            .border(1.dp, ADTheme.hairline).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ADChip("BRANCH DAY", dayColor(state), filled = true)
        dates.forEach { d ->
            val active = d == date
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) ADTheme.slate else Color.Transparent)
                    .clickable { store.setDay(d) }
                    .border(1.dp, ADTheme.slate, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                val label = "$d · ${store.dayState(branchId, d).label}"
                Text(label, color = if (active) Color.White else ADTheme.slate, fontSize = 11.sp,
                    fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Day boundary 04:00 Asia/Manila — OPEN stays editable past midnight, flips to PAST lazily.",
            color = ADTheme.muted, fontSize = 11.sp)
    }
}

@Composable
private fun ADBranchPicker(store: ADFakeStore, onClose: () -> Unit) {
    val user = store.currentUser.value
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp))
                .background(ADTheme.card).border(1.dp, ADTheme.hairline).padding(14.dp)
                .clickable(enabled = false, onClick = {}),
        ) {
            Text("SELECT BRANCH", color = ADTheme.slate, fontSize = 12.sp,
                fontFamily = ADTheme.mono, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ADHeaderRow {
                ADHead("BRANCH", Modifier.weight(2f))
                ADHead("KIND", Modifier.weight(1.6f))
                ADHead("ACCESS", Modifier.weight(1.6f))
            }
            store.branches.forEachIndexed { i, b ->
                val home = b.id == user?.homeBranchId
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                        .clickable {
                            store.currentBranchId.value = b.id
                            store.reliefEdit.value = home
                            store.record("OPEN", "branch", b.id,
                                if (home) "Home branch opened — full access"
                                else "Opened as relief duty — view-only until a relief grant")
                            onClose()
                        }
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADCell(b.name, FontWeight.SemiBold, modifier = Modifier.weight(2f))
                    ADCell(b.kind.label, mono = true, modifier = Modifier.weight(1.6f))
                    Box(Modifier.weight(1.6f)) {
                        ADChip(if (home) "HOME · FULL" else "RELIEF · VIEW-ONLY",
                            if (home) ADTheme.credit else ADTheme.amber)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ADNote("Non-home branches open as relief duty: view-only until a relief request is " +
                "granted or an invite accepted (Register tab).")
        }
    }
}

@Composable
private fun ADRegister(store: ADFakeStore, user: ADUser, onPickBranch: () -> Unit) {
    val branchId = store.currentBranchId.value
    val home = branchId == user.homeBranchId
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ADLedgerCard("CLOCK REGISTER — ${store.branchName(branchId).uppercase()}",
            right = {
                if (store.clockedIn.value) {
                    ADAction("Clock out") { store.clockOut() }
                } else {
                    ADAction("Clock in") { store.clockIn() }
                }
            }) {
            ADRuleRow("Status", if (store.clockedIn.value) "● ON DUTY" else "○ OFF DUTY")
            ADRuleRow("Access", if (home || store.reliefEdit.value) "FULL — home or granted relief"
            else "VIEW-ONLY — relief duty, request a grant below")
            ADRuleRow("Relief pay", "Compensation is paid from this branch's drawer")
            ADRuleRow("Expiry", "Relief access expires 04:00 Manila next day")
            if (!home && !store.reliefEdit.value) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADAction("Request relief for ${store.currentDayDate.value}") {
                        store.requestRelief(branchId, store.currentDayDate.value)
                    }
                    ADAction("Change branch", onClick = onPickBranch)
                }
            }
        }
        ADLedgerCard("RELIEF DUTY / REQUESTS / INVITES",
            right = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ADAction("Simulate branch grant") {
                        store.relief.firstOrNull { it.state == ADReliefState.OPEN }?.let { store.grantRelief(it.id) }
                    }
                }
            }) {
            ADHeaderRow {
                ADHead("REF", Modifier.weight(0.7f))
                ADHead("DIRECTION", Modifier.weight(1.4f))
                ADHead("BRANCH", Modifier.weight(1.4f))
                ADHead("DAY", Modifier.weight(1f))
                ADHead("STATE", Modifier.weight(1f))
                ADHead("OPS", Modifier.weight(2f))
            }
            if (store.relief.isEmpty()) {
                ADNote("No relief rows. Request relief at a non-home branch to open a broadcast request.")
            }
            store.relief.forEachIndexed { i, r ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADCell(r.id, mono = true, modifier = Modifier.weight(0.7f))
                    ADCell(r.direction, mono = true, modifier = Modifier.weight(1.4f))
                    ADCell(store.branchName(r.branchId), modifier = Modifier.weight(1.4f))
                    ADCell(r.dayDate, mono = true, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        ADChip(r.state.label,
                            if (r.state == ADReliefState.GRANTED || r.state == ADReliefState.ACCEPTED) {
                                ADTheme.credit
                            } else if (r.state == ADReliefState.OPEN) {
                                ADTheme.amber
                            } else {
                                ADTheme.faint
                            })
                    }
                    Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when {
                            r.direction == "OUTGOING REQUEST" && r.state == ADReliefState.OPEN -> {
                                ADAction("Withdraw") { store.withdrawRelief(r.id) }
                            }
                            r.direction == "INCOMING INVITE" && r.state == ADReliefState.OPEN -> {
                                ADAction("Accept") { store.acceptInvite(r.id) }
                                ADAction("Decline") { store.declineInvite(r.id) }
                            }
                            r.direction == "INCOMING INVITE" && r.state == ADReliefState.ACCEPTED -> {
                                ADAction("Revoke (branch)") { store.revokeInvite(r.id) }
                            }
                            else -> ADCell("—", color = ADTheme.faint)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            ADNote("One live request per requester per branch per date. Broadcast names no individual; " +
                "any active branch member grants. Retraction locks once the requester clocks in as relief.")
        }
        ADLedgerCard("TODAY'S REGISTER — ${store.currentDayDate.value}") {
            ADHeaderRow {
                ADHead("TIME", Modifier.weight(0.7f))
                ADHead("SESSION", Modifier.weight(0.8f))
                ADHead("CLIENT", Modifier.weight(1.4f))
                ADHead("STATUS", Modifier.weight(1f))
                ADHead("AMOUNT", Modifier.weight(0.8f), alignEnd = true)
            }
            val rows = store.sessions.filter { it.branchId == branchId && it.dayDate == store.currentDayDate.value }
            if (rows.isEmpty()) ADNote("No rows on this branch day.")
            rows.forEachIndexed { i, s ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            when {
                                s.voided -> ADTheme.voidWash
                                i % 2 == 1 -> ADTheme.zebra
                                else -> Color.Transparent
                            },
                        )
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADCell(s.time, mono = true, modifier = Modifier.weight(0.7f))
                    ADCell(s.id, mono = true, modifier = Modifier.weight(0.8f))
                    ADCell((if (s.voided) "∅ " else "") + s.clientName, modifier = Modifier.weight(1.4f))
                    Box(Modifier.weight(1f)) { ADChip(s.status.label, statusColor(s.status)) }
                    ADNum(adPeso(s.price), modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}

@Composable
private fun ADTeam(store: ADFakeStore) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ADLedgerCard("USERS / ROLES / SLOTS") {
            ADHeaderRow {
                ADHead("USER", Modifier.weight(1.6f))
                ADHead("ROLE", Modifier.weight(1f))
                ADHead("HOME", Modifier.weight(1.4f))
                ADHead("SLOT", Modifier.weight(0.5f), alignEnd = true)
            }
            store.users.sortedBy { it.slot }.forEachIndexed { i, u ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (i % 2 == 1) ADTheme.zebra else Color.Transparent)
                        .border(1.dp, ADTheme.hairline)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADCell(u.name, FontWeight.SemiBold, modifier = Modifier.weight(1.6f))
                    ADCell(u.role.label, mono = true, modifier = Modifier.weight(1f))
                    ADCell(store.branchName(u.homeBranchId), modifier = Modifier.weight(1.4f))
                    ADNum("${u.slot}", modifier = Modifier.weight(0.5f))
                }
            }
            Spacer(Modifier.height(4.dp))
            ADNote("Slot 1 = senior. Relief practitioners sort after home slots. " +
                "Capability checks use bundles, never role names.")
        }
        ADRole.entries.forEach { role ->
            ADLedgerCard("ROLE BUNDLE — ${role.label}") {
                ADNote(role.summary)
            }
        }
    }
}

@Composable
private fun ADMailbox(store: ADFakeStore) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ADFilterBar {
            ADChip("${store.notifications.count { !it.read }} UNREAD", ADTheme.info, filled = true)
            Spacer(Modifier.weight(1f))
            ADAction("Mark all read") { store.markAllRead() }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(store.notifications.toList(), key = { _, n -> n.id }) { i, n ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i % 2 == 1) ADTheme.zebra else ADTheme.card)
                        .border(1.dp, ADTheme.hairline, RoundedCornerShape(4.dp))
                        .clickable { store.openNotification(n) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (n.read) "○" else "●", color = if (n.read) ADTheme.faint else ADTheme.info,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(n.title, color = ADTheme.ink, fontSize = 13.sp,
                            fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                        Text(n.body, color = ADTheme.muted, fontSize = 11.sp)
                    }
                    if (n.branchId != null) ADChip("OPEN DAY →", ADTheme.info)
                }
            }
        }
        ADNote("Read rows are kept forever as history. Relief messages tap through to the branch day.")
    }
}

@Composable
private fun ADProfile(store: ADFakeStore, user: ADUser) {
    var confirmOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ADLedgerCard("OPERATOR CARD") {
            ADRuleRow("Name", user.name)
            ADRuleRow("Role", user.role.label)
            ADRuleRow("Home branch", store.branchName(user.homeBranchId))
            ADRuleRow("Branch slot", "${user.slot}")
            ADRuleRow("Capabilities", if (user.role == ADRole.ACCOUNTANT) "read-only, all branches"
            else "per capability view (role bundle + grants)")
        }
        ADLedgerCard("SHIFT") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (store.clockedIn.value) {
                    ADAction("Clock out") { confirmOut = true }
                } else {
                    ADAction("Clock in") { store.clockIn() }
                }
                ADAction("Log out", danger = true) { store.logout() }
            }
            Spacer(Modifier.height(6.dp))
            ADNote("Clock-out ends the attendance row; log out returns to the sign-in register.")
        }
    }
    if (confirmOut) {
        ADConfirm("Clock out now?", "Attendance row closes at the current branch.") {
            if (it) store.clockOut()
            confirmOut = false
        }
    }
}

@Composable
fun ADConfirm(title: String, body: String, onDone: (Boolean) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = { onDone(false) }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp))
                .background(ADTheme.card).border(1.dp, ADTheme.hairline).padding(14.dp)
                .clickable(enabled = false, onClick = {}),
        ) {
            Text(title, color = ADTheme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = ADTheme.muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADAction("Confirm") { onDone(true) }
                ADAction("Cancel") { onDone(false) }
            }
        }
    }
}

@Composable
fun ADCheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onToggle(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(label, color = ADTheme.ink, fontSize = 12.sp, fontFamily = ADTheme.mono)
    }
}
