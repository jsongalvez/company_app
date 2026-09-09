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

// #787 — report-pack shareable sheets: period pack cover, branch rollup, finance
// with remittance snapshots, team, mailbox, audit, profile.

@Composable
fun RpPackSheet(repo: ReportPackFakeRepo, go: (RpScreen) -> Unit) {
    val rows = repo.sessionsInScope().filter { !it.voided }
    val done = rows.filter { it.status == RpSessionStatus.COMPLETED }
    val revenue = done.sumOf { it.price }
    val pending = rows.count { it.status == RpSessionStatus.PENDING }
    val noshow = rows.count { it.status == RpSessionStatus.NO_SHOW }
    val cancelled = rows.count { it.status == RpSessionStatus.CANCELLED }
    val scope = if (repo.period == RpPeriod.WEEK) "Thu Sep 04 – Sun Sep 07" else "Mon Sep 01 – Sun Sep 07"

    RpSheet {
        RpSheetTitle("${repo.period.name.lowercase().replaceFirstChar { it.uppercase() }} pack · ${repo.currentBranch.name}")
        RpSheetSub("SHEET 04 · $scope · export-ready owner summary")
        RpRule()
        Row {
            Column(Modifier.weight(1f)) { RpKpi("Revenue", revenue.php(), "${done.size} completed") }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { RpKpi("Sessions", "${rows.size}", "$pending pending · $noshow no-show · $cancelled cancelled") }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { RpKpi("Branches", "${repo.branches.size}", "rollup on sheet 05") }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("For the owner, in one glance")
        Spacer(Modifier.height(6.dp))
        Text(
            "This ${repo.period.name.lowercase()} the practice completed ${done.size} Sessions for ${revenue.php()} " +
                "with $pending still pending. " +
                (if (noshow + cancelled == 0) "No lost Sessions." else "$noshow no-show, $cancelled cancelled.") +
                " Full branch split lives on the rollup sheet; money proof on finance.",
            color = RpColors.Ink,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            RpGhost("Branch rollup →") { go(RpScreen.ROLLUP) }
            Spacer(Modifier.width(8.dp))
            RpGhost("Finance proof →") { go(RpScreen.FINANCE) }
            Spacer(Modifier.width(8.dp))
            RpGhost("Sessions ledger →") { go(RpScreen.SESSIONS) }
        }
        if (repo.shareLog.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(repo.shareLog, color = RpColors.Export, fontSize = 12.sp, fontFamily = RpMono)
        }
    }
}

@Composable
fun RpRollupSheet(repo: ReportPackFakeRepo) {
    val rows = repo.sessionsInScope().filter { !it.voided }
    val max = repo.branches.maxOfOrNull { b -> rows.filter { it.branchId == b.id && it.status == RpSessionStatus.COMPLETED }.sumOf { it.price } } ?: 1
    RpSheet {
        RpSheetTitle("Branch rollup")
        RpSheetSub("SHEET 05 · ${repo.period.name} scope · completed revenue per branch")
        RpRule()
        RpGridHeader(listOf("BRANCH" to 2f, "DONE" to 0.7f, "PEND" to 0.7f, "REVENUE" to 1f, "SHARE" to 1.6f))
        repo.branches.forEach { b ->
            val mine = rows.filter { it.branchId == b.id }
            val rev = mine.filter { it.status == RpSessionStatus.COMPLETED }.sumOf { it.price }
            val pend = mine.count { it.status == RpSessionStatus.PENDING }
            RpGridRow {
                RpCell(b.name, 2f, bold = true)
                RpCell("${mine.count { it.status == RpSessionStatus.COMPLETED }}", 0.7f, mono = true)
                RpCell("$pend", 0.7f, mono = true)
                RpCell(rev.php(), 1f, mono = true, bold = true, color = RpColors.Navy)
                Column(Modifier.weight(1.6f)) {
                    RpBar(if (max == 0) 0f else rev.toFloat() / max, RpColors.Navy)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RpSectionLabel("Day states in scope")
        Spacer(Modifier.height(6.dp))
        repo.days.filter { it.id in repo.dayIdsInScope() }.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${d.dow} ${d.dateLabel}", color = RpColors.Ink, fontSize = 13.sp, fontFamily = RpMono, modifier = Modifier.weight(1f))
                RpPill(d.status.name, d.status.ink(), d.status.soft())
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        RpNote("REMITTED days are sealed; PAST days await the Coordinator close; OPEN days are still posting (04:00 Asia/Manila boundary).")
    }
}

@Composable
fun RpFinance(repo: ReportPackFakeRepo) {
    var kind by remember { mutableStateOf(RpRemitKind.SESSION) }
    var amount by remember { mutableStateOf("1500") }
    var lines by remember { mutableStateOf("2") }
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }

    val mine = repo.remittances.filter { it.branchId == repo.currentBranchId }
    val sealed = mine.filter { it.state == RpRemitState.SUBMITTED }.sumOf { it.amount }
    val draft = mine.filter { it.state == RpRemitState.DRAFT }.sumOf { it.amount }

    RpSheet {
        RpSheetTitle("Finance proof")
        RpSheetSub("SHEET 08 · SESSION + PRODUCT drafts → submit seals a snapshot → undo within 48h")
        RpRule()
        Row {
            Column(Modifier.weight(1f)) { RpKpi("Sealed", sealed.php(), "${mine.count { it.state == RpRemitState.SUBMITTED }} receipts") }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { RpKpi("In draft", draft.php(), "${mine.count { it.state == RpRemitState.DRAFT }} drafts") }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Remittances · ${repo.currentBranch.name}")
        Spacer(Modifier.height(6.dp))
        RpGridHeader(listOf("RECEIPT" to 1f, "KIND" to 1f, "STATE" to 1f, "AMOUNT" to 1f, "SNAPSHOT" to 2f))
        mine.forEach { r ->
            RpGridRow(onClick = { undoFor = if (undoFor == r.id) null else r.id }) {
                RpCell(r.id, 1f, mono = true, bold = true)
                RpCell(r.kind.name, 1f, mono = true, color = RpColors.Navy)
                RpCell(r.state.name, 1f, mono = true, color = if (r.state == RpRemitState.SUBMITTED) RpColors.Export else RpColors.Amber)
                RpCell(r.amount.php(), 1f, mono = true)
                RpCell(r.snapshot ?: "${r.lines} lines, unsealed", 2f)
            }
            if (undoFor == r.id) {
                Spacer(Modifier.height(6.dp))
                when (r.state) {
                    RpRemitState.DRAFT -> {
                        Row {
                            RpAction("Submit (seal snapshot)") { repo.submitRemittance(r.id); undoFor = null }
                        }
                        Spacer(Modifier.height(4.dp))
                        RpNote("Submit freezes an immutable snapshot with an RC number.")
                    }
                    RpRemitState.SUBMITTED -> {
                        if (r.undoable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    TextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Undo reason (required)") }, singleLine = true)
                                }
                                Spacer(Modifier.width(8.dp))
                                RpAction("Undo within 48h", enabled = undoReason.isNotBlank()) {
                                    repo.undoRemittance(r.id, undoReason); undoReason = ""; undoFor = null
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            RpNote("Undo reopens the draft inside the 48h window and writes the reason to audit. Older receipts (undo exhausted) stay sealed.")
                        } else {
                            RpNote("Sealed past the 48h undo window: immutable.")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("New draft")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpChip("SESSION", kind == RpRemitKind.SESSION) { kind = RpRemitKind.SESSION }
            Spacer(Modifier.width(6.dp))
            RpChip("PRODUCT", kind == RpRemitKind.PRODUCT) { kind = RpRemitKind.PRODUCT }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                TextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount PHP") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(0.7f)) {
                TextField(value = lines, onValueChange = { lines = it }, label = { Text("Lines") }, singleLine = true)
            }
            Spacer(Modifier.width(8.dp))
            RpAction("Add draft", enabled = !repo.locked) {
                repo.addDraft(kind, amount.toIntOrNull() ?: 0, lines.toIntOrNull() ?: 0)
            }
        }
        Spacer(Modifier.height(12.dp))
        RpSectionLabel("Commission split rule")
        Spacer(Modifier.height(6.dp))
        RpNote("Commissions pool per branch day and split equally over staff clocked in at sold_at. The pack shows pool totals; per-person math is illustrative fake data.")
        Spacer(Modifier.height(6.dp))
        RpGridHeader(listOf("DAY" to 1f, "POOL" to 1f, "ON-DUTY SPLIT" to 2f))
        repo.days.filter { it.id in repo.dayIdsInScope() }.take(4).forEach { d ->
            val pool = repo.sessions.count { it.dayId == d.id && it.status == RpSessionStatus.COMPLETED && !it.voided } * 120
            RpGridRow {
                RpCell("${d.dow} ${d.dateLabel}", 1f, mono = true)
                RpCell(pool.php(), 1f, mono = true)
                RpCell("÷ 2 on duty → ${(pool / 2).php()} each (illustrative)", 2f, mono = true)
            }
        }
    }
}

@Composable
fun RpTeam(repo: ReportPackFakeRepo) {
    RpSheet {
        RpSheetTitle("Team sheet")
        RpSheetSub("SHEET 09 · roster by home branch with role notes")
        RpRule()
        RpGridHeader(listOf("NAME" to 1.6f, "ROLE" to 1.4f, "HOME" to 1.8f, "NOTE" to 2f))
        repo.users.forEach { u ->
            val note = when (u.role) {
                RpRole.ONBOARDING -> "locked: zero capabilities"
                RpRole.PRACTITIONER -> "logs sessions, views clients"
                RpRole.COORDINATOR -> "owns finance + PAST/REMITTED edits"
                RpRole.MANAGER -> "coordinator + users + delegates"
                RpRole.ACCOUNTANT -> "read-only, all branches"
            }
            RpGridRow {
                RpCell(u.name + if (u.id == repo.currentUserId) " ●" else "", 1.6f, bold = true)
                RpCell(u.role.name, 1.4f, mono = true, color = RpColors.Navy)
                RpCell(repo.branchName(u.homeBranchId), 1.8f)
                RpCell(note, 2f)
            }
        }
    }
}

@Composable
fun RpMailbox(repo: ReportPackFakeRepo) {
    RpSheet {
        RpSheetTitle("Owner mailbox")
        RpSheetSub("SHEET 10 · ${repo.notifications.count { !it.read }} unread")
        RpRule()
        Row {
            RpGhost("Mark all read") { repo.markAllRead() }
        }
        Spacer(Modifier.height(8.dp))
        RpGridHeader(listOf("STATE" to 0.8f, "TITLE" to 2f, "BODY" to 3f))
        repo.notifications.forEach { n ->
            RpGridRow(onClick = { repo.markRead(n.id) }) {
                RpCell(if (n.read) "read" else "● new", 0.8f, mono = true, bold = !n.read, color = if (n.read) RpColors.Faint else RpColors.Red)
                RpCell(n.title, 2f, bold = !n.read)
                RpCell(n.body, 3f)
            }
        }
    }
}

@Composable
fun RpAuditList(repo: ReportPackFakeRepo) {
    RpSheet {
        RpSheetTitle("Audit sheet")
        RpSheetSub("SHEET 11 · every pack mutation prepends a row: who / action / target / reason")
        RpRule()
        RpGridHeader(listOf("STAMP" to 1.1f, "ACTOR" to 1.3f, "ACTION" to 1.6f, "TARGET + REASON" to 2.6f))
        repo.audits.forEach { a ->
            RpGridRow {
                RpCell(a.stamp, 1.1f, mono = true)
                RpCell(a.actor, 1.3f)
                RpCell(a.action, 1.6f, mono = true, color = RpColors.Navy)
                RpCell(if (a.reason.isBlank()) a.target else "${a.target} · “${a.reason}”", 2.6f)
            }
        }
    }
}

@Composable
fun RpProfile(repo: ReportPackFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    RpSheet {
        RpSheetTitle("Profile")
        RpSheetSub("SHEET 12 · sign out + clock out")
        RpRule()
        if (user == null) {
            RpNote("Nobody signed in.")
            return@RpSheet
        }
        RpGridHeader(listOf("FIELD" to 1f, "VALUE" to 2f))
        listOf(
            "Name" to user.name,
            "Role" to user.role.name,
            "Home branch" to repo.branchName(user.homeBranchId),
            "Duty" to if (repo.clockedIn) "ON DUTY @ ${repo.dutyBranchId?.let(repo::branchName) ?: "—"}" else "OFF DUTY",
            "Pack scope" to "${repo.period.name} · ${repo.currentBranch.name}",
        ).forEach { (k, v) ->
            RpGridRow {
                RpCell(k, 1f, color = RpColors.Faint)
                RpCell(v, 2f, bold = true, mono = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            if (repo.clockedIn) {
                RpAction("Clock out") { repo.clockOut() }
                Spacer(Modifier.width(8.dp))
            }
            RpAction("Logout") { repo.logout(); onLogout() }
        }
    }
}
