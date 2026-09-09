package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// #779 — sessions, clients, finance drill-downs. Dense tables under the calm tiles.

@Composable
fun MmSessions(repo: MmRepo, user: MmUser, branch: MmBranch) {
    var filter by remember { mutableStateOf("") }
    var showExceptionsOnly by remember { mutableStateOf(false) }
    var voidTarget by remember { mutableStateOf<MmSession?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    val locked = branch.dayStatus == MmDayStatus.REMITTED ||
        (branch.dayStatus == MmDayStatus.PAST && user.role != "MANAGER")
    var list = repo.branchSessions(branch.id)
    if (showExceptionsOnly) list = repo.exceptions(branch.id)
    if (filter.isNotBlank()) list = list.filter { it.clientName.contains(filter, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Sessions · ${branch.name}")
        MmTitle("Session board")
        if (locked) {
            val why = if (branch.dayStatus == MmDayStatus.PAST) " (MANAGER override not held by ${user.role})" else ""
            MmNote("Day is ${branch.dayStatus}: edits locked$why.")
        }
        MmNote("Walk-in rule: walk-ins complete only — NO_SHOW / CANCELLED never apply.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmField(filter, { filter = it }, "Filter client")
            Spacer(Modifier.width(4.dp))
            if (showExceptionsOnly) MmButton("Show all") { showExceptionsOnly = false }
            else MmGhost("Exceptions only") { showExceptionsOnly = true }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmGhost(if (walkIn) "New: walk-in ON" else "New: booked") { walkIn = !walkIn }
            MmButton("Log session") {
                if (!locked) {
                    val label = "New ${if (walkIn) "walk-in guest" else "booking"}"
                    repo.sessions.add(
                        MmSession(
                            "s-${100 + repo.sessions.size + 1}", "16:00", label,
                            branch.id, "SESSION", walkIn, MmSessionStatus.PENDING, 900, user.login,
                        ),
                    )
                    repo.log(user.login + " logged session at " + branch.name)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        list.forEach { s ->
            MmTile(alert = s.voided || s.status == MmSessionStatus.NO_SHOW) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmText("${s.time} · ${s.clientName}", bold = true)
                    Spacer(Modifier.width(8.dp))
                    MmStatusBadge(s.status)
                    if (s.walkIn) {
                        Spacer(Modifier.width(6.dp))
                        MmBadge("WALK-IN", MmColors.MossWash, MmColors.Moss)
                    }
                    if (s.voided) {
                        Spacer(Modifier.width(6.dp))
                        MmBadge("VOID", MmColors.ClayWash, MmColors.Clay)
                    }
                }
                val voidNote = if (s.voided) " · reason: ${s.voidReason}" else ""
                MmNote("${s.kind} · ₱${s.price} · ${s.practitioner}$voidNote")
                if (!locked && !s.voided) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val targets = if (s.walkIn) {
                            listOf(MmSessionStatus.COMPLETED)
                        } else {
                            listOf(MmSessionStatus.COMPLETED, MmSessionStatus.NO_SHOW, MmSessionStatus.CANCELLED)
                        }
                        targets.forEach { t ->
                            MmGhost(t.name) {
                                s.status = t
                                repo.log(user.login + " marked ${s.id} " + t.name)
                            }
                        }
                        MmGhost("Void") { voidTarget = s }
                    }
                }
                if (s.voided && !locked) {
                    MmGhost("Unvoid") {
                        s.voided = false
                        repo.log(user.login + " unvoided ${s.id}")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (voidTarget != null) {
            MmTile(alert = true) {
                MmText("Void ${voidTarget!!.id} — reason required", bold = true, color = MmColors.Clay)
                MmField(voidReason, { voidReason = it }, "Reason")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MmButton("Confirm void", danger = true) {
                        if (voidReason.isNotBlank()) {
                            voidTarget!!.voided = true
                            voidTarget!!.voidReason = voidReason.trim()
                            repo.log(user.login + " voided ${voidTarget!!.id}: ${voidTarget!!.voidReason}")
                            voidTarget = null
                            voidReason = ""
                        }
                    }
                    MmGhost("Cancel") { voidTarget = null }
                }
            }
        }
    }
}

@Composable
fun MmClients(repo: MmRepo, user: MmUser) {
    var anonymized by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Clients · global")
        MmTitle("Client mosaic")
        MmNote("Global list across branches. Rule: at most one PENDING session per client.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmField(query, { query = it }, "Search name")
            Spacer(Modifier.width(4.dp))
            MmGhost(if (anonymized) "Anonymized ON" else "Anonymized OFF") { anonymized = !anonymized }
        }
        Spacer(Modifier.height(8.dp))
        repo.clients.filter { it.name.contains(query, ignoreCase = true) }.forEach { c ->
            val pendings = repo.sessions.count { it.clientName == c.name && it.status == MmSessionStatus.PENDING }
            MmTile(alert = pendings > 1) {
                MmText(if (anonymized) "Client ${c.id} · ${c.gender}/${c.age}" else c.name, bold = true)
                MmNote(if (anonymized) "Anonymized view: gender + age kept, name + contact hidden" else c.contact)
                MmNote("PENDING sessions: $pendings / 1 allowed${if (pendings > 1) " — OVER LIMIT" else ""}")
                if (pendings >= 1) {
                    MmGhost("Book anyway (blocked)") {
                        repo.log(user.login + " blocked double-PENDING for ${c.id}")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun MmFinance(repo: MmRepo, user: MmUser, branch: MmBranch) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Finance · ${branch.name}")
        MmTitle("Remittance desk")
        MmNote("SESSION + PRODUCT drafts → submit → sealed snapshot. Undo within 48h; older is permanent.")
        MmNote("Commission split note: practitioner share settles only from sealed snapshots.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmButton("New SESSION draft") {
                repo.remittances.add(MmRemittance("r-${repo.remittances.size + 1}", "SESSION", "DRAFT", 1200, 0))
                repo.log(user.login + " opened SESSION draft")
            }
            MmButton("New PRODUCT draft") {
                repo.remittances.add(MmRemittance("r-${repo.remittances.size + 1}", "PRODUCT", "DRAFT", 600, 0))
                repo.log(user.login + " opened PRODUCT draft")
            }
        }
        Spacer(Modifier.height(10.dp))
        repo.remittances.forEach { r ->
            MmTile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmText("${r.id} · ${r.flow} · ₱${r.amount}", bold = true)
                    Spacer(Modifier.width(8.dp))
                    val sealed = r.stage == "SNAPSHOT"
                    val wash = if (sealed) MmColors.Edge else MmColors.TealWash
                    val ink = if (sealed) MmColors.Soft else MmColors.Teal
                    MmBadge(r.stage, wash, ink)
                }
                MmNote("${r.hoursOld}h old · ${if (r.hoursOld < 48) "undo window open" else "permanent — undo closed"}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.stage == "DRAFT") {
                        MmButton("Submit") {
                            r.stage = "SUBMITTED"
                            repo.log(user.login + " submitted ${r.id}")
                        }
                    }
                    if (r.stage == "SUBMITTED") {
                        MmButton("Seal snapshot") {
                            r.stage = "SNAPSHOT"
                            repo.log(user.login + " sealed ${r.id}")
                        }
                        if (r.hoursOld < 48) {
                            MmGhost("Undo (<48h)") {
                                r.stage = "DRAFT"
                                repo.log(user.login + " undid ${r.id} within 48h")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
