package com.companyb.companyapp.proto.blueprintdraft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BpHomeSheet(repo: BlueprintDraftFakeRepo) {
    val branch = repo.currentBranch()
    var reqNote by remember { mutableStateOf("Evening sheet needs one practitioner, 18:00-22:00.") }
    var inviteName by remember { mutableStateOf("Jonas Reyes") }
    var inviteNote by remember { mutableStateOf("Evening sheet 18:00-22:00") }
    BpSheetCard(sheetNo = "H-100 HOME", scale = "CLOCK ${if (repo.meClockedIn.value) "IN" else "OUT"}") {
        BpKicker("Home · clock-in + relief board")
        Text("SHIFT ELEVATION — ${branch.name}", color = BpLine, fontWeight = FontWeight.Black, fontSize = 19.sp)
        BpDim("ME ■ MARA VILLANUEVA (MANAGER) · ${if (repo.meClockedIn.value) "CLOCKED IN" else "CLOCKED OUT"} · TABLE ${branch.sheetNo}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BpWhiteButton(
                if (repo.meClockedIn.value) "■ CLOCK OUT" else "▸ CLOCK IN",
                onClick = { repo.setClockedIn(branch.id, !repo.meClockedIn.value) },
                modifier = Modifier.weight(1f),
            )
            BpGhostButton("↗ RELIEF DUTY: CLAIM RF-1", onClick = { repo.claimRelief("RF-1") }, modifier = Modifier.weight(1f))
        }
        BpDashedDivider()
        BpKicker("Relief duty · request · invite")
        repo.reliefs.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${r.id} · ${r.asker}", color = BpLine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    BpDim("${repo.branchName(r.branchId)} · ${r.note}")
                }
                BpStatusTag(r.state.name, BpLineDim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BpGhostButton("CLAIM", onClick = { repo.claimRelief(r.id) })
                BpGhostButton("RELEASE", onClick = { repo.releaseRelief(r.id) })
            }
            BpDashedDivider()
        }
        BpField("Draft a relief request", reqNote, { reqNote = it }, "what the sheet needs")
        BpWhiteButton("▸ FILE REQUEST @ ${branch.name}", onClick = { repo.addReliefRequest(branch.id, reqNote) })
        BpField("Invite name", inviteName, { inviteName = it }, "e.g. Jonas Reyes")
        BpField("Invite note", inviteNote, { inviteNote = it }, "e.g. Evening sheet 18:00-22:00")
        BpWhiteButton("▸ SEND INVITE", onClick = { repo.sendInvite(inviteName, branch.id, inviteNote) })
        if (repo.invites.isNotEmpty()) {
            BpKicker("Countersign tray")
            repo.invites.take(3).forEach { line -> BpDim("▸ $line") }
        }
        BpDashedDivider()
        BpDim("SECTION H-H · CLOCK LINE 1:1 · INVITES AWAIT COUNTERSIGN, NEVER AUTO-SEAT.")
    }
}

@Composable
fun BpSessionsSheet(repo: BlueprintDraftFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var newType by remember { mutableStateOf("Measure Visit 30") }
    var voidReason by remember { mutableStateOf("Double-inked line — client reschedules.") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val branch = repo.currentBranch()
    val list = repo.sessions.filter { it.branchId == branch.id && (filter == null || it.status.name == filter) }
    BpSheetCard(sheetNo = "S-200 SESSIONS", scale = "PENDING → DONE") {
        BpKicker("Sessions · PENDING / COMPLETED / NO_SHOW / CANCELLED")
        BpDim("WALK-IN RULE — WALK-INS NEVER TAKE NO_SHOW OR CANCELLED. NO CLICK TARGETS ARE DRAWN FOR THEM.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpFilterPill("ALL", filter == null) { filter = null }
            BpFilterPill("PENDING", filter == "PENDING") { filter = "PENDING" }
            BpFilterPill("COMPLETED", filter == "COMPLETED") { filter = "COMPLETED" }
            BpFilterPill("NO_SHOW", filter == "NO_SHOW") { filter = "NO_SHOW" }
            BpFilterPill("CANCELLED", filter == "CANCELLED") { filter = "CANCELLED" }
        }
        list.forEach { s ->
            Column(
                modifier = Modifier.fillMaxWidth().border(1.5.dp, BpLine, RoundedCornerShape(2.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${s.id} · ${s.clientName}${if (s.walkIn) " ◇ WALK-IN" else ""}", color = BpLine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        BpDim("${s.type} · ₱${s.price}${if (s.voided) " · ☁ VOID: ${s.voidReason}" else ""}")
                    }
                    BpStatusTag(if (s.voided) "VOIDED" else s.status.name, if (s.voided) BpBad else BpSessionStatusColor(s.status))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!s.walkIn) {
                        BpGhostButton("→PENDING", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.PENDING) })
                        BpGhostButton("→DONE", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.COMPLETED) })
                        BpGhostButton("→NO_SHOW", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.NO_SHOW) })
                        BpGhostButton("→CXL", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.CANCELLED) })
                    } else {
                        BpGhostButton("→PENDING", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.PENDING) })
                        BpGhostButton("→DONE", onClick = { repo.setSessionStatus(s.id, BpSessionStatus.COMPLETED) })
                    }
                    BpGhostButton(if (expanded == s.id) "HIDE CLOUD" else "CLOUD…", onClick = { expanded = if (expanded == s.id) null else s.id })
                }
                if (expanded == s.id) {
                    BpField("Void reason (required)", voidReason, { voidReason = it }, "why this line clouds out")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BpWhiteButton("☁ VOID", onClick = { repo.voidSession(s.id, voidReason) })
                        BpGhostButton("ERASE CLOUD", onClick = { repo.unvoidSession(s.id) })
                    }
                }
            }
        }
        if (list.isEmpty()) BpDim("— NO LINES ON THIS SHEET FOR FILTER ${filter ?: "ALL"} —")
        BpDashedDivider()
        BpField("Draft a PENDING walk-in (type)", newType, { newType = it }, "e.g. Measure Visit 30")
        BpWhiteButton("▸ INK WALK-IN @ ${branch.name}", onClick = { repo.bookSession(branch.id, newType) })
        BpDim("VOID = REVISION CLOUD WITH REASON. UNVOID ERASES THE CLOUD, STATUS RETURNS.")
    }
}

@Composable
fun BpClientsSheet(repo: BlueprintDraftFakeRepo) {
    BpSheetCard(sheetNo = "C-300 CLIENTS", scale = "GLOBAL FILE") {
        BpKicker("Clients · global file")
        BpDim("RULE — CLIENTS ARE GLOBAL ACROSS TABLES. AT MOST ONE PENDING SESSION PER CLIENT; EXTRA PENDING LINES ARE REFUSED AT THE TITLE BLOCK.")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BpDim("ANONYMIZED VIEW: ${if (repo.anonymized.value) "ON — CODES ONLY, KEEPS GENDER+AGE" else "OFF — FULL DRAFT NAMES"}")
            BpGhostButton(if (repo.anonymized.value) "SHOW NAMES" else "ANONYMIZE", onClick = { repo.anonymized.value = !repo.anonymized.value })
        }
        repo.clients.forEach { c ->
            val pendings = repo.sessions.count { it.clientName == c.name && it.status == BpSessionStatus.PENDING }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (repo.anonymized.value) "Client ${c.code} · ■■■■" else "${c.name} · ${c.code}",
                        color = BpLine,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    BpDim(if (repo.anonymized.value) "Anonymized — ${c.detail.substringAfter("-", "on file")}" else c.detail)
                }
                BpStatusTag("PEND $pendings/1", if (pendings > 1) BpBad else BpLineDim)
            }
            BpDashedDivider()
        }
        BpDim("MEASURE — FIVE CARDS ON FILE. PENDING COUNTS ARE LIVE OFF THE SESSION SHEET.")
    }
}

@Composable
fun BpFinanceSheet(repo: BlueprintDraftFakeRepo) {
    val branch = repo.currentBranch()
    var undoReason by remember { mutableStateOf("Mismeasured product count — recheck before seal.") }
    val items = repo.remits.filter { it.branchId == branch.id }
    BpSheetCard(sheetNo = "F-400 FINANCE", scale = "SESS + PROD") {
        BpKicker("Finance · remittance SESSION + PRODUCT")
        BpDim("SEAL = DRAFT → SUBMITTED + SNAPSHOT. UNDO WITHIN 48H WITH REASON RETURNS TO DRAFT.")
        BpDim("SPLIT NOTE — COMMISSION 60/40 PRACTITIONER/HOUSE, SETTLED AT SNAPSHOT. FIGURES BELOW ARE PRE-SPLIT INK.")
        items.forEach { r ->
            Column(modifier = Modifier.fillMaxWidth().border(1.5.dp, BpLine, RoundedCornerShape(2.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.id} · ${r.kind} · ₱${r.amount}", color = BpLine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    BpStatusTag(r.status.name, if (r.status == BpRemitStatus.DRAFT) BpStamp else BpGood)
                }
                if (r.snapshot.isNotBlank()) BpDim("SNAPSHOT ■ ${r.snapshot}")
                if (r.undoReason.isNotBlank()) BpDim("UNDO ⌫ ${r.undoReason}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BpWhiteButton("▸ SUBMIT + SNAP", onClick = { repo.submitRemit(r.id) }, enabled = r.status == BpRemitStatus.DRAFT)
                    BpGhostButton("⌫ UNDO 48H", onClick = { repo.undoRemit(r.id, undoReason) }, enabled = r.status == BpRemitStatus.SUBMITTED)
                }
            }
        }
        if (items.isEmpty()) BpDim("— NO REMIT LINES ON THIS TABLE —")
        BpField("Undo reason (required, ≤48h)", undoReason, { undoReason = it }, "why the seal lifts")
        BpDashedDivider()
        BpDim("TITLE MATH — ${branch.name}: GROSS ₱${branch.gross} / TGT ₱${branch.target} · SEALED PRINTS HOLD.")
    }
}

@Composable
fun BpTeamSheet(repo: BlueprintDraftFakeRepo) {
    BpSheetCard(sheetNo = "T-500 TEAM", scale = "ROSTER 1:1") {
        BpKicker("Team · users + roles")
        BpDim("ROLES — Practitioner / Coordinator / MANAGER / Accountant / ONBOARDING. ONBOARDING CARRIES AN EMPTY BUNDLE.")
        repo.users.forEach { u ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(u.name, color = BpLine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    BpDim("${u.role} · HOME ${repo.branchName(u.homeBranchId)}${if (u.onboarding) " · EMPTY BUNDLE" else ""} · ${if (u.clockedIn) "IN" else "OUT"}")
                }
                BpStatusTag(u.role, if (u.onboarding) BpStamp else BpLineDim)
            }
            BpDashedDivider()
        }
        BpWhiteButton("▸ COUNTERSIGN RINA → PRACTITIONER", onClick = { repo.grantPractitioner() })
        BpDim("TRAY GUIDE — MANAGER COUNTERSIGNS ONBOARDING. ONE TAP INKS THE PROMOTION + AUDIT LINE.")
    }
}

@Composable
fun BpMailboxSheet(repo: BlueprintDraftFakeRepo) {
    BpSheetCard(sheetNo = "M-600 MAIL", scale = "INBOX") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BpKicker("Notifications · mailbox (${repo.unreadCount()} unread)")
            BpGhostButton("✓ ALL READ", onClick = { repo.markAllRead() })
        }
        repo.notices.forEach { n ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (n.read) Color.Transparent else BpLine.copy(alpha = 0.14f))
                    .border(1.5.dp, BpLine, RoundedCornerShape(2.dp))
                    .clickable { repo.toggleNotice(n.id) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${if (n.read) "○" else "●"} ${n.title}", color = BpLine, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    BpDim(n.body)
                }
                BpStatusTag(if (n.read) "READ" else "UNREAD", if (n.read) BpLineFaint else BpStamp)
            }
        }
        BpDim("TAP A CARD TO FLIP READ/UNREAD. UNREAD COUNT RIDES THE INDEX RAIL.")
    }
}

@Composable
fun BpAuditSheet(repo: BlueprintDraftFakeRepo) {
    BpSheetCard(sheetNo = "L-700 AUDIT", scale = "LOG") {
        BpKicker("Audit log · revision history")
        BpDim("EVERY INK STROKE ABOVE — CLOCK, RELIEF, STATUS, CLOUD, SEAL, UNDO — LANDS HERE WITH REASON.")
        repo.audit.forEachIndexed { i, line ->
            Text("L-${700 + i} · $line", color = BpLine, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            if (i < repo.audit.size - 1) BpDashedDivider()
        }
    }
}

@Composable
fun BpProfileSheet(repo: BlueprintDraftFakeRepo, onLogout: () -> Unit, onPickBranch: () -> Unit) {
    val branch = repo.currentBranch()
    var dayPick by remember { mutableStateOf(branch.dayStatus) }
    BpSheetCard(sheetNo = "P-800 PROFILE", scale = "SELF") {
        BpKicker("Profile · Mara Villanueva — MANAGER")
        Text("DRAFTER CARD", color = BpLine, fontWeight = FontWeight.Black, fontSize = 19.sp)
        BpDim("HOME TABLE ${branch.name} · ${if (repo.meClockedIn.value) "CLOCKED IN" else "CLOCKED OUT"} · DAY ${branch.dayStatus}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BpWhiteButton(
                if (repo.meClockedIn.value) "■ CLOCK OUT" else "▸ CLOCK IN",
                onClick = { repo.setClockedIn(branch.id, !repo.meClockedIn.value) },
                modifier = Modifier.weight(1f),
            )
            BpGhostButton("LOG OUT", onClick = onLogout, modifier = Modifier.weight(1f))
        }
        BpDashedDivider()
        BpKicker("Flip branch-day (title block)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpFilterPill("OPEN", dayPick == BpDayStatus.OPEN) { dayPick = BpDayStatus.OPEN }
            BpFilterPill("PAST", dayPick == BpDayStatus.PAST) { dayPick = BpDayStatus.PAST }
            BpFilterPill("REMITTED", dayPick == BpDayStatus.REMITTED) { dayPick = BpDayStatus.REMITTED }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BpWhiteButton("▸ INK ${dayPick} @ ${branch.name}", onClick = { repo.flipDayStatus(branch.id, dayPick) }, modifier = Modifier.weight(1f))
            BpGhostButton("SWAP TABLE", onClick = onPickBranch, modifier = Modifier.weight(1f))
        }
        BpGhostButton("↺ RESET DRAFTING TABLE", onClick = { repo.resetDemo() })
        BpDashedDivider()
        BpDim("04:00 ASIA/MANILA DATUM HOLDS: OPEN INKS, PAST MEASURES, REMITTED SEALS.")
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            BpDraftStamp()
        }
    }
}
