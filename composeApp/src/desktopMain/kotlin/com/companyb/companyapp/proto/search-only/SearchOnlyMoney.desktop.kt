package com.companyb.companyapp.proto.searchonly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

// #855 — search-only money + people + mailbox + trail + identity details.

@Composable
fun SoFinanceDetail(repo: SearchOnlyFakeRepo, id: String?, query: String, onOpen: (SoSel) -> Unit) {
    var reason by remember { mutableStateOf("") }
    SoCard {
        SoH1("Finance & remittance")
        SoMono("${repo.currentBranch.name} · two independent flows: SESSION (net income) and PRODUCT (price × qty)")
        SoActionRow {
            SoPrimary("+ SESSION draft") { repo.newDraft(SoRemitKind.SESSION) }
            Spacer(Modifier.width(8.dp))
            SoGhost("+ PRODUCT draft") { repo.newDraft(SoRemitKind.PRODUCT) }
        }
    }
    SoSection("REMITTANCES", "draft → submit seals snapshot → Undo needs reason, 48h")
    val list = if (query.isBlank()) repo.remittances else repo.remittances.filter {
        (it.id + it.kind.label + it.state.label).lowercase().contains(query.trim().lowercase())
    }
    list.forEach { r ->
        SoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.id} · ${r.kind.label} · ${soPeso(r.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SoColors.Ink)
                    SoMono("${r.branchDay} · ${r.state.label}${r.snapshotId?.let { " · snapshot $it" } ?: ""}${r.undoReason?.let { " · undo: $it" } ?: ""}")
                }
                SoPill(r.state.label, when (r.state) {
                    SoRemitState.DRAFT -> SoTone.AMBER
                    SoRemitState.SUBMITTED -> SoTone.GREEN
                    SoRemitState.UNDONE -> SoTone.GREY
                })
            }
            if (r.id == id || r.state == SoRemitState.DRAFT || r.state == SoRemitState.SUBMITTED) {
                SoActionRow {
                    if (r.state == SoRemitState.DRAFT) SoPrimary("Submit ${r.id}") { repo.submitRemit(r) }
                    if (r.state == SoRemitState.SUBMITTED) SoGhost("Undo ${r.id} (48h)") { repo.undoRemit(r, reason); reason = "" }
                }
                if (r.state == SoRemitState.SUBMITTED) {
                    Text("Undo reason", fontSize = 12.sp, color = SoColors.Dim)
                    TextField(value = reason, onValueChange = { reason = it }, singleLine = true, placeholder = { Text("reason…", fontSize = 12.sp) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    SoNote("Submission freezes an immutable financial snapshot; later edits never rewrite it. Undo (reason, 48h) returns the remittance to Draft and deletes the snapshot.")
    Spacer(Modifier.height(8.dp))
    SoSection("COMMISSION SPLIT", "pooled per branch day · equal shares")
    SoCard {
        SoBody("Product commissions pool per branch day and split equally among every practitioner and coordinator clocked in at sold_at. Manual include / exclude overrides; separate from compensation, never remitted.")
        Spacer(Modifier.height(6.dp))
        repo.payouts.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.staff, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SoColors.Ink)
                    SoMono(if (p.paid) "paid" else "unpaid share")
                }
                SoGhost(if (p.paid) "Reopen" else "Mark paid") { repo.togglePaid(p) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun SoTeamDetail(repo: SearchOnlyFakeRepo, id: String?, onOpen: (SoSel) -> Unit) {
    SoCard {
        SoH1("Team & roles")
        SoBody("Roles bundle capabilities; runtime checks capabilities, not names. Practitioner logs sessions, Coordinator owns finance, MANAGER adds user management, Accountant reads everything, ONBOARDING is locked.")
    }
    SoSection("PEOPLE", "${repo.users.size} users")
    repo.users.forEach { u ->
        val focus = u.id == id
        SoHitRow(
            "⛉", u.name, "${u.role.label} · home ${u.homeBranch}${if (u.clockedIn) " · clocked in" else ""}",
            if (u.role == SoRole.ONBOARDING) SoTone.RED else SoTone.VIOLET, hot = focus,
            onClick = { repo.touch(SoSel("team", u.id)); onOpen(SoSel("team", u.id)) },
        )
        if (focus && u.role == SoRole.ONBOARDING) {
            SoCard {
                SoNote("ONBOARDING is functionally locked out — empty role bundle, nothing derives.")
                SoActionRow {
                    SoPrimary("Grant Practitioner") { repo.grantPractitioner(u.id) }
                }
            }
        }
    }
}

@Composable
fun SoNoticesDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    SoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SoH1("Notifications")
                SoMono("${repo.notices.count { !it.read }} unread · per-recipient mailbox · reads kept forever")
            }
            SoGhost("Mark all read") { repo.markAllRead() }
        }
    }
    Spacer(Modifier.height(8.dp))
    repo.notices.forEach { n ->
        SoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = SoColors.Ink)
                    SoBody(n.body)
                }
                SoPill(if (n.read) "read" else "unread", if (n.read) SoTone.GREY else SoTone.AMBER)
            }
            SoActionRow {
                SoGhost(if (n.read) "Mark unread" else "Mark read") { n.read = !n.read; repo.audit("TOGGLE_READ", n.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SoAuditDetail(repo: SearchOnlyFakeRepo, query: String) {
    SoCard {
        SoH1("Audit log")
        SoMono("${repo.audits.size} entries · newest first · REMITTED-day edits would flag here")
    }
    val q = query.trim().lowercase()
    val list = if (q.isBlank()) repo.audits else repo.audits.filter {
        (it.action + it.target + it.who + (it.reason ?: "")).lowercase().contains(q)
    }
    Spacer(Modifier.height(8.dp))
    list.forEach { a ->
        SoHitRow(
            "#${a.seq}", a.action, "${a.stamp} · ${a.who} · ${a.target}${a.reason?.let { " · $it" } ?: ""}",
            SoTone.GREY, hot = false, onClick = {},
        )
    }
    if (list.isEmpty()) SoCard { SoBody("No audit entries match. Clear the box.") }
}

@Composable
fun SoProfileDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    val u = repo.actor
    if (u == null) {
        SoCard { SoBody("Signed out. Type login to sign in.") }
        return
    }
    SoCard {
        SoH1(u.name)
        SoMono("${u.id} · ${u.role.label} · home ${u.homeBranch} · ${if (repo.clockedIn) "clocked in" else "clocked out"}")
        SoActionRow {
            if (!repo.clockedIn) SoPrimary("Clock in") { repo.setClock(true) }
            else SoGhost("Clock out") { repo.setClock(false) }
            Spacer(Modifier.width(8.dp))
            SoGhost("Logout") {
                repo.logout()
                onOpen(SoSel("login", "U-ANN"))
            }
        }
    }
}

@Composable
fun SoOnboardingDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    val locked = repo.users.firstOrNull { it.role == SoRole.ONBOARDING }
    SoCard {
        SoH1("Onboarding lock")
        SoBody("A fresh registration lands here: zero capabilities, functionally locked out even after a branch assignment, until MANAGE_USERS grants a real role.")
        Spacer(Modifier.height(8.dp))
        if (locked == null) {
            SoNote("Nobody is locked right now — every user holds a role. The factory line is clear.")
        } else {
            SoMono("${locked.name} · ${locked.id} · home ${locked.homeBranch}")
            SoActionRow {
                SoPrimary("Grant Practitioner to ${locked.name}") {
                    repo.grantPractitioner(locked.id)
                    onOpen(SoSel("team", locked.id))
                }
            }
        }
    }
}
