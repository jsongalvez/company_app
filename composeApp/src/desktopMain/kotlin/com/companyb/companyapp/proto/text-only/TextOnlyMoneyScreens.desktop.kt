package com.companyb.companyapp.proto.textonly

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// #853 — finance/remittance, team, mailbox, audit log, profile. Fake data only.

private fun txRemitColor(state: TxRemitState) =
    when (state) {
        TxRemitState.DRAFT -> TxTerm.Amber
        TxRemitState.SUBMITTED -> TxTerm.Green
        TxRemitState.UNDONE -> TxTerm.Dim
    }

@Composable
fun TxFinance(repo: TextOnlyFakeRepo) {
    var undoing by remember { mutableStateOf<TxRemittance?>(null) }
    var undoReason by remember { mutableStateOf("") }
    var undoError by remember { mutableStateOf<String?>(null) }

    Head("06", "finance", "SESSION + PRODUCT drafts seal into snapshots.")
    repo.remittances.forEach { r ->
        TxPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${r.id} ", size = 12, bold = true, color = TxTerm.Bright)
                Tag(r.kind.label, TxTerm.Dim)
                Spacer(Modifier.width(6.dp))
                Tag(r.state.label, txRemitColor(r.state))
            }
            KV("branch-day", r.branchDay)
            KV("amount", txPeso(r.amount), TxTerm.Green)
            if (r.snapshotId != null) KV("snapshot", r.snapshotId)
            if (r.undoReason != null) KV("undo reason", r.undoReason, TxTerm.Amber)
            Spacer(Modifier.height(4.dp))
            Row {
                Cmd(
                    "submit+seal",
                    onClick = { repo.submitRemittance(r.id) },
                    enabled = r.state == TxRemitState.DRAFT,
                )
                Spacer(Modifier.width(8.dp))
                Cmd(
                    "undo-48h",
                    onClick = {
                        undoing = r
                        undoReason = ""
                        undoError = null
                    },
                    enabled = r.state == TxRemitState.SUBMITTED,
                    danger = true,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Note("submit seals an immutable snapshot; undo needs a reason inside the 48h window.")
    Note("commission split: pooled per branch-day, split over clocked-in staff at sold_at.")
    Spacer(Modifier.height(6.dp))
    TxPanel {
        T("pay envelopes", size = 12, bold = true, color = TxTerm.Amber)
        repo.payouts.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${p.staff} (${p.role}) :: done=${p.completed} share=${txPeso(p.share)} ", size = 12)
                Tag(if (p.paid) "PAID" else "OPEN", if (p.paid) TxTerm.Green else TxTerm.Amber)
            }
            Row {
                Spacer(Modifier.width(4.dp))
                Cmd(if (p.paid) "reopen" else "mark-paid", onClick = { repo.markPaid(p.id) })
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    undoing?.let { r ->
        AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text("undo ${r.id} (${r.snapshotId})", fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    TxDialogField(
                        value = undoReason,
                        onValueChange = {
                            undoReason = it
                            undoError = null
                        },
                        placeholder = "undo reason (required, 48h window)",
                    )
                    undoError?.let { Text("error: $it", fontFamily = FontFamily.Monospace) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    undoError = repo.undoRemittance(r.id, undoReason)
                    if (undoError == null) undoing = null
                }) { Text("undo snapshot", fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { undoing = null }) { Text("cancel", fontFamily = FontFamily.Monospace) }
            },
        )
    }
}

@Composable
fun TxTeam(repo: TextOnlyFakeRepo) {
    val me = repo.currentUser
    val iAmManager = me?.role == TxRole.MANAGER
    Head("07", "team", "users, roles, capability bundles.")
    TxPanel {
        T("id        name                 role", size = 12, bold = true, color = TxTerm.Bright)
        repo.users.forEach { u ->
            T(
                (u.id + "  " + u.name).padEnd(28, ' ') + u.role.label +
                    if (u.id == repo.currentUserId) "  <-- you" else "",
                size = 12,
                bold = u.id == repo.currentUserId,
                color = if (u.id == repo.currentUserId) TxTerm.Green else TxTerm.Ink,
            )
            T("  can: ${repo.capabilitiesOf(u.role)}", size = 12, dim = true)
            if (iAmManager && u.id != repo.currentUserId) {
                Row {
                    Spacer(Modifier.width(4.dp))
                    TxRole.entries.forEach { r ->
                        if (r != u.role) {
                            T(
                                text = "[grant:${r.label.lowercase()}]",
                                size = 12,
                                color = TxTerm.Green,
                                modifier =
                                    Modifier
                                        .clickable { repo.grantRole(u.id, r) }
                                        .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    if (iAmManager) {
        Note("you are MANAGER: tap a grant link to change a role.")
    } else {
        Note("role grants need a MANAGER identity (sign in as Mia Santos).")
    }
}

@Composable
fun TxMailbox(repo: TextOnlyFakeRepo) {
    Head("08", "mailbox", "notices: read / unread.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        val unread = repo.notices.count { !it.read }
        T("unread=$unread total=${repo.notices.size}   ", size = 12, dim = true)
        Cmd("mark all read", onClick = { repo.markAllRead() }, enabled = unread > 0)
    }
    Spacer(Modifier.height(6.dp))
    repo.notices.forEach { n ->
        TxPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(if (n.read) "   " else "(*) ", color = TxTerm.Amber, bold = true)
                T(n.title, size = 13, bold = !n.read)
            }
            T(n.body, size = 12, dim = true)
            Spacer(Modifier.height(4.dp))
            Cmd(
                if (n.read) "mark unread" else "mark read",
                onClick = { repo.markNotice(n.id, !n.read) },
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun TxAuditLog(repo: TextOnlyFakeRepo) {
    Head("09", "audit-log", "every mutation prepends a line here.")
    T("seq  stamp      who           action           target", size = 12, bold = true, color = TxTerm.Bright)
    repo.ledger.forEach { a ->
        TxPanel {
            T("#${a.seq} ${a.stamp} ${a.who}", size = 12, color = TxTerm.Amber)
            T("${a.action} :: ${a.target}", size = 12)
            if (a.reason != null) T("reason: ${a.reason}", size = 12, color = TxTerm.Red)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun TxProfile(
    repo: TextOnlyFakeRepo,
    onLogout: () -> Unit,
) {
    Head("10", "profile", "you, your shift, your exit.")
    val u = repo.currentUser
    if (u == null) {
        TxPanel {
            T("signed out.", color = TxTerm.Amber, bold = true)
            Spacer(Modifier.height(4.dp))
            Cmd("go to login", onClick = onLogout)
        }
        return
    }
    TxPanel {
        KV("name", u.name, TxTerm.Bright)
        KV("role", u.role.label)
        KV("branch", u.branch)
        KV("clock", if (repo.clockedIn) "IN" else "OUT")
        Spacer(Modifier.height(4.dp))
        T("capabilities: ${repo.capabilitiesOf(u.role)}", size = 12, dim = true)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cmd(if (repo.clockedIn) "clock-out" else "clock-in", onClick = { repo.clockToggle() })
        Spacer(Modifier.width(8.dp))
        Cmd("logout", onClick = {
            repo.logout()
            onLogout()
        }, danger = true)
    }
    Spacer(Modifier.height(8.dp))
    Note("logout returns to login; clock-out is recorded first if you are IN.")
}
