package com.companyb.companyapp.proto.reportpack

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

// #787 — report-pack workbook flows: cover/onboarding, sign-in, branches, duty
// home, sessions ledger, global client file.

@Composable
fun RpOnboarding(repo: ReportPackFakeRepo, onNext: () -> Unit) {
    RpSheet {
        RpSheetTitle("Owner pack cover")
        RpSheetSub("SHEET 00 · read-only until a role is granted")
        RpRule()
        RpSectionLabel("Locked account")
        Spacer(Modifier.height(6.dp))
        Text(
            "The ONBOARDING account holds zero capabilities: every sheet below opens, " +
                "but rows refuse edits until MANAGE_USERS grants a real role. " +
                "That is the lock being demonstrated, not a missing feature.",
            color = RpColors.Ink,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpPill("ONBOARDING", RpColors.Amber, RpColors.AmberSoft)
            Spacer(Modifier.width(8.dp))
            RpNote("capabilities: none · branch assignment alone changes nothing")
        }
        Spacer(Modifier.height(12.dp))
        Row {
            RpAction("Grant Practitioner role") { repo.grantPractitioner() }
            Spacer(Modifier.width(8.dp))
            RpGhost("Continue to sign-in") { onNext() }
        }
        if (repo.currentUser != null && !repo.locked) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${repo.currentUser!!.name} now holds ${repo.currentUser!!.role.name}: sheets accept edits.",
                color = RpColors.Export,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun RpLogin(repo: ReportPackFakeRepo, onNext: () -> Unit) {
    RpSheet {
        RpSheetTitle("Sign the pack in")
        RpSheetSub("SHEET 01 · fake directory, five users ONBOARDING to ACCOUNTANT")
        RpRule()
        RpGridHeader(listOf("USER" to 2f, "ROLE" to 1.4f, "HOME BRANCH" to 1.6f, "SIGN" to 1f))
        repo.users.forEach { u ->
            RpGridRow(onClick = { repo.login(u.id); onNext() }) {
                RpCell(u.name, 2f, bold = u.id == repo.currentUserId)
                RpCell(u.role.name, 1.4f, mono = true, color = RpColors.Navy)
                RpCell(repo.branchName(u.homeBranchId), 1.6f)
                RpCell(if (u.id == repo.currentUserId) "● current" else "sign in →", 1f, mono = true, color = RpColors.Export)
            }
        }
        Spacer(Modifier.height(10.dp))
        RpNote("Tap any row to sign the book. ONBOARDING stays locked until the cover grants a role.")
    }
}

@Composable
fun RpBranchSelect(repo: ReportPackFakeRepo, onNext: () -> Unit) {
    RpSheet {
        RpSheetTitle("Branches in this pack")
        RpSheetSub("SHEET 02 · pick the active branch, then pick its day")
        RpRule()
        RpGridHeader(listOf("BRANCH" to 2f, "KIND" to 1.6f, "COMPLETED" to 1f, "OPEN" to 1f))
        repo.branches.forEach { b ->
            val done = repo.sessionsInScope().count { it.branchId == b.id && it.status == RpSessionStatus.COMPLETED && !it.voided }
            val open = repo.sessionsInScope().count { it.branchId == b.id && it.status == RpSessionStatus.PENDING && !it.voided }
            RpGridRow(onClick = { repo.currentBranchId = b.id; onNext() }) {
                RpCell((if (b.id == repo.currentBranchId) "● " else "") + b.name, 2f, bold = true)
                RpCell(b.kind.name, 1.6f, mono = true, color = RpColors.Navy)
                RpCell("$done", 1f, mono = true)
                RpCell("$open", 1f, mono = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Branch days · ${repo.currentBranch.name}")
        Spacer(Modifier.height(6.dp))
        repo.days.forEach { d ->
            RpGridRow(onClick = { repo.selectedDayId = d.id }) {
                RpCell("${d.dow} ${d.dateLabel}${if (d.isToday) " ● today" else ""}", 2f, mono = true, bold = d.id == repo.selectedDayId)
                Row(Modifier.weight(2f)) {
                    RpPill(d.status.name, d.status.ink(), d.status.soft())
                }
                RpCell(if (d.id == repo.selectedDayId) "● selected" else "", 1f, mono = true, color = RpColors.Navy)
            }
        }
        Spacer(Modifier.height(8.dp))
        RpNote("Branch Day closes at 04:00 Asia/Manila: anything after 04:00 posts to the next day. PAST and REMITTED rows are Coordinator-editable only in production; here they are fake and tappable.")
    }
}

@Composable
fun RpDuty(repo: ReportPackFakeRepo, go: (RpScreen) -> Unit) {
    val user = repo.currentUser
    RpSheet {
        RpSheetTitle("Duty sheet")
        RpSheetSub("SHEET 03 · clock-in home + relief duty, invites, requests")
        RpRule()
        if (user == null) {
            RpNote("Nobody signed in. Open sheet 01 and tap a row first.")
            Spacer(Modifier.height(8.dp))
            RpGhost("Go to sign-in") { go(RpScreen.LOGIN) }
            return@RpSheet
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpPill(if (repo.clockedIn) "ON DUTY" else "OFF DUTY", if (repo.clockedIn) RpColors.Export else RpColors.Faint, RpColors.HeaderFill)
            Spacer(Modifier.width(8.dp))
            Text(
                "${user.name} · ${user.role.name} · duty branch ${repo.dutyBranchId?.let(repo::branchName) ?: "—"}",
                color = RpColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            if (repo.clockedIn) RpAction("Clock out") { repo.clockOut() } else RpAction("Clock in @ ${repo.currentBranch.name}", enabled = !repo.locked) { repo.clockIn() }
            Spacer(Modifier.width(8.dp))
            RpGhost("Open pack") { go(RpScreen.PACK) }
        }
        if (repo.locked) {
            Spacer(Modifier.height(6.dp))
            RpNote("ONBOARDING is locked: clock-in refuses until the cover grants a role.")
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Relief invites")
        Spacer(Modifier.height(6.dp))
        RpGridHeader(listOf("FROM" to 2f, "SHIFT" to 1.6f, "STATE" to 1.2f, "ACT" to 1.6f))
        repo.invites.forEach { v ->
            RpGridRow {
                RpCell(v.fromBranch, 2f)
                RpCell(v.shift, 1.6f, mono = true)
                RpCell(v.state, 1.2f, mono = true, color = RpColors.Navy)
                Row(Modifier.weight(1.6f)) {
                    if (v.state == "PENDING") {
                        RpGhost("Accept") { repo.inviteAction(v.id, true) }
                        Spacer(Modifier.width(6.dp))
                        RpGhost("Decline") { repo.inviteAction(v.id, false) }
                    } else {
                        Text("done", color = RpColors.Faint, fontSize = 12.sp, fontFamily = RpMono)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Relief requests")
        Spacer(Modifier.height(6.dp))
        RpGridHeader(listOf("BRANCH" to 2f, "SHIFT" to 1.6f, "STATE" to 1f, "ACT" to 1.8f))
        repo.requests.forEach { q ->
            RpGridRow {
                RpCell(q.branch + if (q.mine) " (mine)" else "", 2f)
                RpCell(q.shift, 1.6f, mono = true)
                RpCell(q.state, 1f, mono = true, color = RpColors.Navy)
                Row(Modifier.weight(1.8f)) {
                    when (q.state) {
                        "OPEN" -> if (q.mine) RpGhost("Withdraw") { repo.withdrawRequest(q.id) } else RpGhost("Cover") { repo.coverRequest(q.id) }
                        else -> RpGhost("Reopen") { repo.reopenRequest(q.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun RpSessions(repo: ReportPackFakeRepo) {
    var filter by remember { mutableStateOf<RpSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkType by remember { mutableStateOf("STR") }
    var walkPrice by remember { mutableStateOf("1200") }

    RpSheet {
        RpSheetTitle("Sessions ledger")
        RpSheetSub("SHEET 06 · ${repo.period.name} scope · walk-ins refuse NO_SHOW / CANCELLED")
        RpRule()
        Row {
            RpChip("ALL", filter == null) { filter = null }
            Spacer(Modifier.width(6.dp))
            RpSessionStatus.entries.forEach {
                RpChip(it.name, filter == it) { filter = it }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val rows = repo.sessionsInScope().filter { filter == null || it.status == filter }
        RpGridHeader(listOf("ID" to 0.7f, "CLIENT" to 1.6f, "WHEN" to 1.4f, "TYPE" to 0.7f, "STATUS" to 1.2f, "PRICE" to 0.9f))
        rows.forEach { s ->
            RpGridRow(onClick = { openId = if (openId == s.id) null else s.id }) {
                RpCell(s.id + if (s.walkIn) " ⁺" else "", 0.7f, mono = true)
                RpCell(s.clientName + if (s.voided) " (void)" else "", 1.6f, bold = true, color = if (s.voided) RpColors.Faint else RpColors.Ink)
                RpCell("${s.dayId} ${s.time}", 1.4f, mono = true)
                RpCell(s.type, 0.7f, mono = true)
                Row(Modifier.weight(1.2f)) {
                    RpPill(s.status.name, s.status.ink(), RpColors.HeaderFill)
                }
                RpCell(s.price.php(), 0.9f, mono = true)
            }
            if (openId == s.id) {
                RpSessionDrawer(repo, s, voidReason, { voidReason = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Walk-in entry (no NO_SHOW / CANCELLED rule)")
        Spacer(Modifier.height(6.dp))
        RpNote("Walk-in Sessions can only complete or stay pending: the NO_SHOW and CANCELLED buttons refuse them with the rule note.")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextField(value = walkName, onValueChange = { walkName = it }, label = { Text("Guest name") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(0.7f)) {
                TextField(value = walkType, onValueChange = { walkType = it }, label = { Text("Type") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(0.7f)) {
                TextField(value = walkPrice, onValueChange = { walkPrice = it }, label = { Text("Price") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            RpAction("Add walk-in", enabled = !repo.locked) {
                repo.addWalkIn(walkName, repo.currentBranchId, walkType.ifBlank { "STR" }, walkPrice.toIntOrNull() ?: 0)
                walkName = ""
            }
        }
    }
}

@Composable
private fun RpSessionDrawer(repo: ReportPackFakeRepo, s: RpSession, voidReason: String, onReason: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        Text(
            "${s.id} · ${s.clientName} · ${repo.branchName(s.branchId)} · ${s.practitioners.ifBlank { "unassigned" }}",
            color = RpColors.Navy,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (s.walkIn) {
            Spacer(Modifier.height(4.dp))
            RpNote("Rule: walk-in Sessions cannot be NO_SHOW or CANCELLED — complete them or keep them pending.")
        }
        Spacer(Modifier.height(6.dp))
        Row {
            RpGhost("Complete") { repo.setSessionStatus(s.id, RpSessionStatus.COMPLETED) }
            Spacer(Modifier.width(6.dp))
            RpGhost("No-show") {
                if (!s.walkIn) repo.setSessionStatus(s.id, RpSessionStatus.NO_SHOW)
            }
            Spacer(Modifier.width(6.dp))
            RpGhost("Cancel") {
                if (!s.walkIn) repo.setSessionStatus(s.id, RpSessionStatus.CANCELLED)
            }
            Spacer(Modifier.width(6.dp))
            RpGhost("Reopen") { repo.setSessionStatus(s.id, RpSessionStatus.PENDING) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextField(value = voidReason, onValueChange = onReason, label = { Text("Void reason (required)") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            if (s.voided) {
                RpGhost("Unvoid") { repo.unvoidSession(s.id) }
            } else {
                RpAction("Void", enabled = voidReason.isNotBlank()) { repo.voidSession(s.id, voidReason); onReason("") }
            }
        }
        if (s.voided) {
            Spacer(Modifier.height(4.dp))
            RpNote("Voided: ${s.voidReason ?: "—"}")
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun RpClients(repo: ReportPackFakeRepo) {
    var query by remember { mutableStateOf("") }
    RpSheet {
        RpSheetTitle("Global client file")
        RpSheetSub("SHEET 07 · shared across branches · at most one PENDING per client")
        RpRule()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TextField(value = query, onValueChange = { query = it }, label = { Text("Search name") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            RpChip(if (repo.anonymizedView) "ANONYMIZED VIEW ●" else "ANONYMIZED VIEW ○", repo.anonymizedView) {
                repo.anonymizedView = !repo.anonymizedView
            }
        }
        Spacer(Modifier.height(6.dp))
        RpNote("At-most-one-PENDING: a client with a PENDING Session cannot open another until it resolves. Anonymized view masks PII for sharing the pack; per-row anonymize is a soft-delete that keeps gender + age for reporting.")
        Spacer(Modifier.height(8.dp))
        RpGridHeader(listOf("CLIENT" to 1.6f, "G/A" to 0.8f, "PENDING" to 0.9f, "FILE NOTE" to 1.8f, "MASK" to 1f))
        repo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.forEach { c ->
            val pending = repo.pendingFor(c.id)
            RpGridRow(onClick = { repo.anonymizeClient(c.id) }) {
                RpCell(repo.displayName(c), 1.6f, bold = true, color = if (pending > 1) RpColors.Red else RpColors.Ink)
                RpCell("${c.gender}/${c.age}", 0.8f, mono = true)
                RpCell("$pending", 0.9f, mono = true, color = if (pending > 1) RpColors.Red else RpColors.Ink)
                RpCell(c.branchNote, 1.8f)
                RpCell(if (c.anonymized) "masked" else "tap to mask", 1f, mono = true, color = RpColors.Navy)
            }
            if (pending > 1) {
                RpNote("Over limit: ${c.name} holds $pending PENDING Sessions (rule allows one).")
            }
        }
    }
}
