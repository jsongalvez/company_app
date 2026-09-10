package com.companyb.companyapp.proto.wireframelive

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
fun WfHomeBox(repo: WireframeLiveFakeRepo) {
    val branch = repo.currentBranch()
    var reqNote by remember { mutableStateOf("Evening box needs one practitioner, 18:00-22:00.") }
    var inviteName by remember { mutableStateOf("Jonas Reyes") }
    var inviteNote by remember { mutableStateOf("Evening box 18:00-22:00") }
    WfBoxCard(boxNo = "BOX-H100 HOME", flag = 4, strip = "CLOCK ${if (repo.meClockedIn.value) "IN" else "OUT"}", showFlags = repo.showFlags.value) {
        WfKicker("Home · clock-in + relief boxes")
        Text("SHIFT FILL — ${branch.name}", color = WfInk, fontWeight = FontWeight.Black, fontSize = 19.sp)
        WfDim("ME ■ MARA VILLANUEVA (MANAGER) · ${if (repo.meClockedIn.value) "CLOCKED IN" else "CLOCKED OUT"} · ${branch.boxNo}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WfPrimaryButton(
                if (repo.meClockedIn.value) "■ CLOCK OUT" else "▸ CLOCK IN",
                onClick = { repo.setClockedIn(branch.id, !repo.meClockedIn.value) },
                modifier = Modifier.weight(1f),
            )
            WfGhostButton("↗ RELIEF: CLAIM RF-1", onClick = { repo.claimRelief("RF-1") }, modifier = Modifier.weight(1f))
        }
        WfDashedDivider()
        WfKicker("Relief duty · request · invite")
        repo.reliefs.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${r.id} · ${r.asker}", color = WfInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    WfDim("${repo.branchName(r.branchId)} · ${r.note}")
                }
                WfStatusTag(r.state.name, r.state == WfReliefState.CLAIMED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WfGhostButton("CLAIM", onClick = { repo.claimRelief(r.id) })
                WfGhostButton("RELEASE", onClick = { repo.releaseRelief(r.id) })
            }
            WfDashedDivider()
        }
        WfField("Draft a relief request", reqNote, { reqNote = it }, "what the box needs")
        WfPrimaryButton("▸ FILE REQUEST @ ${branch.name}", onClick = { repo.addReliefRequest(branch.id, reqNote) })
        WfField("Invite name", inviteName, { inviteName = it }, "e.g. Jonas Reyes")
        WfField("Invite note", inviteNote, { inviteNote = it }, "e.g. Evening box 18:00-22:00")
        WfPrimaryButton("▸ SEND INVITE", onClick = { repo.sendInvite(inviteName, branch.id, inviteNote) })
        if (repo.invites.isNotEmpty()) {
            WfKicker("Flag-off tray")
            repo.invites.take(3).forEach { line -> WfDim("▸ $line") }
        }
        WfDashedDivider()
        if (repo.showFlags.value) {
            WfFlagNote(4, "HOME BOX: clock-in flips the ME strip. Relief CLAIM seats the box; RELEASE frees it; invites wait for flag-off.")
        }
    }
}

@Composable
fun WfSessionsBox(repo: WireframeLiveFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var newType by remember { mutableStateOf("Fill Visit 30") }
    var voidReason by remember { mutableStateOf("Double-boxed line — client reschedules.") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val branch = repo.currentBranch()
    val list = repo.sessions.filter { it.branchId == branch.id && (filter == null || it.status.name == filter) }
    WfBoxCard(boxNo = "BOX-S200 SESSIONS", flag = 5, strip = "PENDING → FILLED", showFlags = repo.showFlags.value) {
        WfKicker("Sessions · PENDING / COMPLETED / NO_SHOW / CANCELLED")
        WfDim("WALK-IN RULE — WALK-INS NEVER TAKE NO_SHOW OR CANCELLED. THOSE FILL TARGETS ARE NOT DRAWN.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WfFilterPill("ALL", filter == null) { filter = null }
            WfFilterPill("PENDING", filter == "PENDING") { filter = "PENDING" }
            WfFilterPill("COMPLETED", filter == "COMPLETED") { filter = "COMPLETED" }
            WfFilterPill("NO_SHOW", filter == "NO_SHOW") { filter = "NO_SHOW" }
            WfFilterPill("CANCELLED", filter == "CANCELLED") { filter = "CANCELLED" }
        }
        list.forEach { s ->
            Column(
                modifier = Modifier.fillMaxWidth().border(1.5.dp, WfInk, RoundedCornerShape(2.dp)).background(WfWash).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${s.id} · ${s.clientName}${if (s.walkIn) " ◇ WALK-IN" else ""}", color = WfInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        WfDim("${s.type} · ₱${s.price}${if (s.voided) " · ▨ VOID: ${s.voidReason}" else ""}")
                    }
                    WfStatusTag(if (s.voided) "VOIDED" else s.status.name, s.status == WfSessionStatus.COMPLETED || s.voided)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!s.walkIn) {
                        WfGhostButton("→PENDING", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.PENDING) })
                        WfGhostButton("→DONE", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.COMPLETED) })
                        WfGhostButton("→NO_SHOW", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.NO_SHOW) })
                        WfGhostButton("→CXL", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.CANCELLED) })
                    } else {
                        WfGhostButton("→PENDING", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.PENDING) })
                        WfGhostButton("→DONE", onClick = { repo.setSessionStatus(s.id, WfSessionStatus.COMPLETED) })
                    }
                    WfGhostButton(if (expanded == s.id) "HIDE VOID" else "VOID…", onClick = { expanded = if (expanded == s.id) null else s.id })
                }
                if (expanded == s.id) {
                    WfField("Void reason (required)", voidReason, { voidReason = it }, "why this box grays out")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WfPrimaryButton("▨ VOID", onClick = { repo.voidSession(s.id, voidReason) })
                        WfGhostButton("UNVOID", onClick = { repo.unvoidSession(s.id) })
                    }
                }
            }
        }
        if (list.isEmpty()) WfDim("— NO FILLED BOXES ON THIS SHELF FOR FILTER ${filter ?: "ALL"} —")
        WfDashedDivider()
        WfField("Draft a PENDING walk-in (type)", newType, { newType = it }, "e.g. Fill Visit 30")
        WfPrimaryButton("▸ FILL WALK-IN @ ${branch.name}", onClick = { repo.bookSession(branch.id, newType) })
        if (repo.showFlags.value) {
            WfFlagNote(5, "SESSION BOX: status fills the tag. VOID grays the box with a reason; UNVOID restores the fill.")
        }
    }
}

@Composable
fun WfClientsBox(repo: WireframeLiveFakeRepo) {
    WfBoxCard(boxNo = "BOX-C300 CLIENTS", flag = 6, strip = "GLOBAL FILE", showFlags = repo.showFlags.value) {
        WfKicker("Clients · global file")
        WfDim("RULE — CLIENTS ARE GLOBAL ACROSS BOXES. AT MOST ONE PENDING SESSION PER CLIENT.")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            WfDim("ANONYMIZED: ${if (repo.anonymized.value) "ON — CODES ONLY" else "OFF — FULL NAMES"}")
            WfGhostButton(if (repo.anonymized.value) "SHOW NAMES" else "ANONYMIZE", onClick = { repo.anonymized.value = !repo.anonymized.value })
        }
        repo.clients.forEach { c ->
            val pendings = repo.sessions.count { it.clientName == c.name && it.status == WfSessionStatus.PENDING }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (repo.anonymized.value) "Client ${c.code} · ■■■■" else "${c.name} · ${c.code}",
                        color = WfInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    WfDim(if (repo.anonymized.value) "Anonymized — ${c.detail.substringAfter("-", "on file")}" else c.detail)
                }
                WfStatusTag("PEND $pendings/1", pendings > 1)
            }
            WfDashedDivider()
        }
        if (repo.showFlags.value) {
            WfFlagNote(6, "CLIENT BOX: PEND n/1 tags are live off the session shelf. Anonymized view keeps codes + gender/age fragment.")
        }
    }
}

@Composable
fun WfFinanceBox(repo: WireframeLiveFakeRepo) {
    val branch = repo.currentBranch()
    var undoReason by remember { mutableStateOf("Miscounted product fill — recheck before seal.") }
    val items = repo.remits.filter { it.branchId == branch.id }
    WfBoxCard(boxNo = "BOX-F400 FINANCE", flag = 7, strip = "SESS + PROD", showFlags = repo.showFlags.value) {
        WfKicker("Finance · remittance SESSION + PRODUCT")
        WfDim("SEAL = DRAFT → SUBMITTED + SNAPSHOT. UNDO WITHIN 48H WITH REASON RETURNS TO DRAFT.")
        WfDim("SPLIT NOTE — COMMISSION 60/40 PRACTITIONER/HOUSE, SETTLED AT SNAPSHOT. FIGURES BELOW ARE PRE-SPLIT.")
        items.forEach { r ->
            Column(modifier = Modifier.fillMaxWidth().border(1.5.dp, WfInk, RoundedCornerShape(2.dp)).background(WfPaper).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.id} · ${r.kind} · ₱${r.amount}", color = WfInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    WfStatusTag(r.status.name, r.status == WfRemitStatus.SUBMITTED)
                }
                if (r.snapshot.isNotBlank()) WfDim("SNAPSHOT ■ ${r.snapshot}")
                if (r.undoReason.isNotBlank()) WfDim("UNDO ⌫ ${r.undoReason}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WfPrimaryButton("▸ SUBMIT + SNAP", onClick = { repo.submitRemit(r.id) }, enabled = r.status == WfRemitStatus.DRAFT)
                    WfGhostButton("⌫ UNDO 48H", onClick = { repo.undoRemit(r.id, undoReason) }, enabled = r.status == WfRemitStatus.SUBMITTED)
                }
            }
        }
        if (items.isEmpty()) WfDim("— NO REMIT BOXES ON THIS SHELF —")
        WfField("Undo reason (required, ≤48h)", undoReason, { undoReason = it }, "why the seal lifts")
        WfDashedDivider()
        WfDim("SHELF MATH — ${branch.name}: GROSS ₱${branch.gross} / TGT ₱${branch.target}.")
        if (repo.showFlags.value) {
            WfFlagNote(7, "FINANCE BOX: SUBMIT fills the snapshot strip; UNDO inside 48h with reason reopens the gray draft box.")
        }
    }
}

@Composable
fun WfTeamBox(repo: WireframeLiveFakeRepo) {
    WfBoxCard(boxNo = "BOX-T500 TEAM", flag = 8, strip = "ROSTER 1:1", showFlags = repo.showFlags.value) {
        WfKicker("Team · users + roles")
        WfDim("ROLES — Practitioner / Coordinator / MANAGER / Accountant / ONBOARDING. ONBOARDING = EMPTY BOX.")
        repo.users.forEach { u ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(u.name, color = WfInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    WfDim("${u.role} · HOME ${repo.branchName(u.homeBranchId)}${if (u.onboarding) " · EMPTY BUNDLE" else ""} · ${if (u.clockedIn) "IN" else "OUT"}")
                }
                WfStatusTag(u.role, u.role == "MANAGER")
            }
            WfDashedDivider()
        }
        WfPrimaryButton("▸ FLAG RINA → PRACTITIONER", onClick = { repo.grantPractitioner() })
        if (repo.showFlags.value) {
            WfFlagNote(8, "TEAM BOX: MANAGER flags ONBOARDING to Practitioner. One tap fills the role + audit line.")
        }
    }
}

@Composable
fun WfMailboxBox(repo: WireframeLiveFakeRepo) {
    WfBoxCard(boxNo = "BOX-M600 MAIL", flag = 9, strip = "INBOX", showFlags = repo.showFlags.value) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            WfKicker("Notifications · mailbox (${repo.unreadCount()} unread)")
            WfGhostButton("✓ ALL READ", onClick = { repo.markAllRead() })
        }
        repo.notices.forEach { n ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (n.read) WfPaper else WfBox)
                    .border(1.5.dp, WfInk, RoundedCornerShape(2.dp))
                    .clickable { repo.toggleNotice(n.id) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${if (n.read) "○" else "●"} ${n.title}", color = WfInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    WfDim(n.body)
                }
                WfStatusTag(if (n.read) "READ" else "UNREAD", !n.read)
            }
        }
        if (repo.showFlags.value) {
            WfFlagNote(9, "MAIL BOX: tap a card to flip read/unread. Unread count rides the index rail.")
        }
    }
}

@Composable
fun WfAuditBox(repo: WireframeLiveFakeRepo) {
    WfBoxCard(boxNo = "BOX-L700 AUDIT", flag = 10, strip = "LOG", showFlags = repo.showFlags.value) {
        WfKicker("Audit log · fill history")
        WfDim("EVERY FILL ABOVE — CLOCK, RELIEF, STATUS, VOID, SEAL, UNDO — LANDS HERE WITH REASON.")
        repo.audit.forEachIndexed { i, line ->
            Text("L-${700 + i} · $line", color = WfInk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            if (i < repo.audit.size - 1) WfDashedDivider()
        }
        if (repo.showFlags.value) {
            WfFlagNote(10, "AUDIT BOX: append-only list. Newest fill sits on top with its reason attached.")
        }
    }
}

@Composable
fun WfProfileBox(repo: WireframeLiveFakeRepo, onLogout: () -> Unit, onPickBranch: () -> Unit) {
    val branch = repo.currentBranch()
    var dayPick by remember { mutableStateOf(branch.dayStatus) }
    WfBoxCard(boxNo = "BOX-P800 PROFILE", flag = 11, strip = "SELF", showFlags = repo.showFlags.value) {
        WfKicker("Profile · Mara Villanueva — MANAGER")
        Text("USER BOX", color = WfInk, fontWeight = FontWeight.Black, fontSize = 19.sp)
        WfDim("HOME ${branch.name} · ${if (repo.meClockedIn.value) "CLOCKED IN" else "CLOCKED OUT"} · DAY ${branch.dayStatus}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WfPrimaryButton(
                if (repo.meClockedIn.value) "■ CLOCK OUT" else "▸ CLOCK IN",
                onClick = { repo.setClockedIn(branch.id, !repo.meClockedIn.value) },
                modifier = Modifier.weight(1f),
            )
            WfGhostButton("LOG OUT", onClick = onLogout, modifier = Modifier.weight(1f))
        }
        WfDashedDivider()
        WfKicker("Flip branch-day box")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WfFilterPill("OPEN", dayPick == WfDayStatus.OPEN) { dayPick = WfDayStatus.OPEN }
            WfFilterPill("PAST", dayPick == WfDayStatus.PAST) { dayPick = WfDayStatus.PAST }
            WfFilterPill("REMITTED", dayPick == WfDayStatus.REMITTED) { dayPick = WfDayStatus.REMITTED }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WfPrimaryButton("▸ FILL ${dayPick} @ ${branch.name}", onClick = { repo.flipDayStatus(branch.id, dayPick) }, modifier = Modifier.weight(1f))
            WfGhostButton("SWAP BOX", onClick = onPickBranch, modifier = Modifier.weight(1f))
        }
        WfGhostButton("↺ RESET BOXES", onClick = { repo.resetDemo() })
        WfDashedDivider()
        WfDim("04:00 ASIA/MANILA HOLDS: OPEN FILLS, PAST GRAYS, REMITTED SEALS BLACK.")
        if (repo.showFlags.value) {
            WfFlagNote(11, "PROFILE BOX: clock, logout, day-box flip, swap, reset — every control fills the audit box too.")
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            WfLiveStamp()
        }
    }
}
