package com.companyb.companyapp.proto.listeverything

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #854 — money + records outline: branch day, finance/remittance, commission,
// mailbox, audit log, profile.

@Composable
fun LeMoneySections(repo: ListEverythingFakeRepo, tree: LeTreeState) {
    LeNode(tree, "day", 0, "Branch day", badge = repo.dayState.label) {
        LeNote(1, "OPEN → PAST → REMITTED · day boundary 04:00 Asia/Manila · ${repo.currentBranch.name} · ${repo.currentBranch.dayDate}")
        LeLeaf(1, label = "Cycle: ${repo.dayState.label} → next", meta = "records the turn in audit") {
            OutlinedButton(onClick = { repo.cycleDay() }) { Text("Advance", fontSize = 12.sp) }
        }
    }

    LeNode(tree, "finance", 0, "Finance · remittance", badge = repo.remittances.count { it.state == LeRemitState.DRAFT }.toString() + " drafts") {
        LeNote(1, "SESSION + PRODUCT flows · submit seals an immutable snapshot · undo needs a reason inside 48h")
        LeRemitKind.entries.forEach { kind ->
            val group = repo.remittances.filter { it.kind == kind }
            LeNode(tree, "finance.${kind.name}", 1, "${kind.label} remittance", badge = group.size.toString()) {
                group.forEach { r ->
                    if (!tree.matches(r.id, r.branchDay)) return@forEach
                    LeRemitLeaf(repo = repo, tree = tree, r = r)
                }
            }
        }
        LeNode(tree, "finance.split", 1, "Commission split", meta = "pooled per branch day") {
            LeNote(2, "pool splits over staff clocked in at sold_at · onboarding shares vest on grant")
            repo.payouts.forEach { p ->
                if (!tree.matches(p.staff, p.role)) return@forEach
                LeLeaf(
                    2,
                    label = p.staff,
                    meta = "${p.role} · ${p.completed} completed · share ${lePeso(p.share)}",
                    badge = if (p.paid) "paid" else "unpaid",
                    badgeTone = if (p.paid) LeTone.GREEN else LeTone.AMBER,
                ) {
                    OutlinedButton(onClick = { repo.markPaid(p.id, !p.paid) }) {
                        Text(if (p.paid) "Reopen" else "Mark paid", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    LeNode(
        tree, "mailbox", 0, "Notifications",
        badge = "${repo.notices.count { !it.read }} unread",
    ) {
        LeLeaf(1, bullet = "✉", label = "Mark all read", meta = "${repo.notices.size} notices") {
            OutlinedButton(onClick = { repo.markAllRead() }) { Text("Read all", fontSize = 12.sp) }
        }
        repo.notices.forEach { n ->
            if (!tree.matches(n.title, n.body)) return@forEach
            LeLeaf(
                1,
                bullet = if (n.read) "○" else "●",
                label = n.title,
                meta = n.body,
                badge = if (n.read) "read" else "unread",
                badgeTone = if (n.read) LeTone.SLATE else LeTone.ACCENT,
            ) {
                OutlinedButton(onClick = { repo.setNoticeRead(n.id, !n.read) }) {
                    Text(if (n.read) "Unread" else "Read", fontSize = 12.sp)
                }
            }
        }
    }

    LeNode(tree, "audit", 0, "Audit log", badge = repo.audits.size.toString() + " entries") {
        LeNote(1, "every mutation in this outline prepends who · action · target · reason")
        if (repo.audits.isEmpty()) LeNote(1, "empty — do anything above and it lands here")
        repo.audits.forEach { a ->
            if (!tree.matches(a.who, a.action, a.target, a.reason ?: "")) return@forEach
            LeLeaf(
                1,
                bullet = "#",
                label = "${a.action} → ${a.target}",
                meta = "${a.stamp} · ${a.who}${if (a.reason != null) " · “${a.reason}”" else ""}",
            )
        }
    }

    LeNode(tree, "profile", 0, "Profile", meta = repo.actorName()) {
        val me = repo.currentUser
        if (me == null) {
            LeNote(1, "signed out — pick a name under Login ↑")
        } else {
            if (tree.matches(me.name, me.role.label)) {
                LeLeaf(1, label = me.name, meta = "${me.role.label} · ${me.branch}", badge = "me", badgeTone = LeTone.GREEN)
            }
            LeNode(tree, "profile.session", 1, "My session", meta = "clock + sign-out") {
                val on = repo.clockedIn[me.id] == true
                LeLeaf(2, label = "Clock", badge = if (on) "IN" else "OUT", badgeTone = if (on) LeTone.GREEN else LeTone.SLATE) {
                    OutlinedButton(onClick = { repo.clockToggle(me.id) }) {
                        Text(if (on) "Clock out" else "Clock in", fontSize = 12.sp)
                    }
                }
                LeLeaf(2, bullet = "↩", label = "Log out", meta = "returns to the Login outline") {
                    OutlinedButton(onClick = { repo.logout() }) { Text("Log out", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun LeRemitLeaf(repo: ListEverythingFakeRepo, tree: LeTreeState, r: LeRemittance) {
    var showUndo by remember(r.id) { mutableStateOf(false) }
    var reason by remember(r.id) { mutableStateOf("") }
    val tone = when (r.state) {
        LeRemitState.DRAFT -> LeTone.AMBER
        LeRemitState.SUBMITTED -> LeTone.GREEN
        LeRemitState.UNDONE -> LeTone.RED
    }
    LeNode(
        tree, "remit.${r.id}", 2,
        "${r.id} · ${lePeso(r.amount)}",
        meta = buildString {
            append(r.branchDay)
            if (r.snapshotId != null) append(" · ${r.snapshotId}")
            if (r.undoReason != null) append(" · undo: ${r.undoReason}")
        },
        badge = r.state.label.lowercase(),
        badgeTone = tone,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.state == LeRemitState.DRAFT) {
                OutlinedButton(onClick = { repo.submitRemittance(r.id) }) { Text("Submit + seal snapshot", fontSize = 12.sp) }
            }
            if (r.state == LeRemitState.SUBMITTED) {
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { showUndo = !showUndo }) { Text("Undo…", fontSize = 12.sp) }
            }
        }
        if (r.state == LeRemitState.SUBMITTED) {
            LeNote(3, "snapshot ${r.snapshotId} is immutable · undo allowed within 48h with reason")
        }
        if (showUndo && r.state == LeRemitState.SUBMITTED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = true,
                    placeholder = { Text("undo reason (required, 48h)", fontSize = 12.sp) },
                )
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    repo.undoRemittance(r.id, reason)
                    if (reason.isNotBlank()) {
                        reason = ""
                        showUndo = false
                    }
                }) { Text("Confirm undo", fontSize = 12.sp) }
            }
        }
    }
}
