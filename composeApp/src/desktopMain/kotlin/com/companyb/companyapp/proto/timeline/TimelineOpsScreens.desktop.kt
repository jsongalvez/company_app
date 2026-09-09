package com.companyb.companyapp.proto.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
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

// #760 — timeline prototype ops screens: home/relief, clients, finance, team, mailbox, audit, profile.

@Composable
fun TlHomeScreen(repo: TimelineRepo) {
    var tab by remember { mutableStateOf(0) }
    TlHeadline("Clock & relief")
    TlCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Off the clock",
                        color = TlColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TlNote("Home branch: ${repo.branch(repo.currentUser?.homeBranchId ?: repo.currentBranchId).name}. Relief starts view-only; edit needs a grant.")
                }
                if (repo.clockedIn) {
                    TlGhost("Clock out") {
                        repo.clockedIn = false
                        repo.log("${repo.currentUser?.name} clocked out")
                    }
                } else {
                    TlPrimary("Clock in") {
                        repo.clockedIn = true
                        repo.log("${repo.currentUser?.name} clocked in at ${repo.currentBranch().name}")
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Relief duty", "Relief requests", "Relief invites").forEachIndexed { i, label ->
            if (i == tab) TlPrimary(label) { tab = i } else TlGhost(label) { tab = i }
        }
    }
    TlCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    TlSubhead("My relief duty")
                    repo.reliefBoard.filter { it.startsWith("Duty") || it.startsWith("Request: You asked") }.forEach {
                        Text("• $it", color = TlColors.Ink, fontSize = 13.sp)
                    }
                    TlNote("Relief pay comes from the relief branch drawer; duty expires 04:00 Manila next day.")
                }
                1 -> {
                    TlSubhead("Relief requests (outsider-initiated, broadcast)")
                    Text("• You asked BGC for edit access today — APPROVED", color = TlColors.Ink, fontSize = 13.sp)
                    TlNote("One live request per requester per branch per date. Any active branch member grants, denies, or cancels; locks once you clock in as relief.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TlPrimary("New request for Cebu Tour") {
                            repo.reliefBoard.add("Request: You asked Cebu Tour for edit access today — PENDING")
                            repo.log("Relief request opened for Cebu Tour")
                        }
                        TlGhost("Withdraw BGC request") { repo.log("Relief request withdrawn (fake)") }
                    }
                }
                else -> {
                    TlSubhead("Relief invites (branch-initiated, single future day)")
                    Text("• Makati invites you (Practitioner) for Saturday — pending your accept", color = TlColors.Ink, fontSize = 13.sp)
                    TlNote("Accepting writes the day grant. The branch may revoke an accepted future duty until you clock in; revocation frees re-invite.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TlPrimary("Accept invite") { repo.log("Relief invite accepted — day grant written (fake)") }
                        TlGhost("Decline") { repo.log("Relief invite declined (fake)") }
                    }
                }
            }
        }
    }
}

@Composable
fun TlClientsScreen(repo: TimelineRepo) {
    var showAnon by remember { mutableStateOf(false) }
    TlHeadline("Clients — global record")
    TlNote("Clients are global across branches. At most one PENDING session per client at a time.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = showAnon, onCheckedChange = { showAnon = it })
        Text("Show anonymized view", color = TlColors.Ink, fontSize = 13.sp)
    }
    repo.clients.forEach { c ->
        TlCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showAnon || c.anonymized) "Client ${c.id.uppercase()}" else c.name,
                        color = TlColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (c.pendingCount > 0) "1 PENDING — booking blocked" else "no PENDING",
                        color = if (c.pendingCount > 0) TlColors.Oxblood else TlColors.Teal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (c.anonymized) {
                    TlNote("Anonymized: PII nullified, gender ${c.gender} + age ${c.age} retained for reporting.")
                } else if (showAnon) {
                    TlNote("Preview of anonymized view: name/contact masked, gender + age retained.")
                } else {
                    Text(c.contact, color = TlColors.Faded, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TlFinanceScreen(repo: TimelineRepo) {
    var reason by remember { mutableStateOf("") }
    TlHeadline("Finance & remittance")
    TlNote("Two independent flows: SESSION (net income after compensation + expenses) and PRODUCT (unit price × quantity). Submission seals an immutable snapshot.")
    TlCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TlSubhead("Commission split")
            Text(
                "Each COMPLETED session splits net income between practitioner compensation and branch drawer; the SESSION flow remits the drawer share.",
                color = TlColors.Ink,
                fontSize = 13.sp,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TlPrimary("+ SESSION draft") {
            val id = "r-%02d".format(repo.remitSeq++)
            repo.remittances.add(TlRemittance(id, "SESSION", "Draft", 9600, repo.currentBranch().name))
            repo.log("SESSION draft $id opened")
        }
        TlGhost("+ PRODUCT draft") {
            val id = "r-%02d".format(repo.remitSeq++)
            repo.remittances.add(TlRemittance(id, "PRODUCT", "Draft", 1500, repo.currentBranch().name))
            repo.log("PRODUCT draft $id opened")
        }
    }
    repo.remittances.toList().forEach { r ->
        TlCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.flow} · ${r.id}", color = TlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(r.stage.uppercase(), color = if (r.stage == "Snapshot") TlColors.Teal else TlColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Text("₱${r.amount} · ${r.branchName}", color = TlColors.Faded, fontSize = 12.sp)
                when {
                    r.stage == "Draft" -> TlPrimary("Submit — seal snapshot") { repo.submitRemittance(r.id) }
                    r.submittedHoursAgo != null && r.submittedHoursAgo <= 48 -> {
                        Text("Submitted ${r.submittedHoursAgo}h ago — Undo window open (48h).", color = TlColors.Ink, fontSize = 13.sp)
                        TextField(value = reason, onValueChange = { reason = it }, label = { Text("Undo reason (required)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        TlGhost("Undo — back to Draft", enabled = reason.isNotBlank()) {
                            repo.undoRemittance(r.id, reason.trim())
                            reason = ""
                        }
                    }
                    else -> TlNote("Submitted ${r.submittedHoursAgo}h ago — snapshot permanent. Later edits never rewrite it.")
                }
            }
        }
    }
}

@Composable
fun TlTeamScreen(repo: TimelineRepo) {
    TlHeadline("Team & roles")
    TlNote("MANAGER is a superset of Coordinator: user management + delegate assignment on top of finance duties. Accountant is read-only everywhere.")
    repo.users.forEach { u ->
        TlCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = TlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(u.role, color = TlColors.Oxblood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Capabilities: ${if (u.capabilities.isEmpty()) "none — locked" else u.capabilities.joinToString()}", color = TlColors.Faded, fontSize = 12.sp)
                if (u.locked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TlPrimary("Grant Practitioner") { repo.log("MANAGE_USERS: ${u.name} granted Practitioner (fake)") }
                        TlGhost("Deactivate") { repo.log("${u.name} deactivated — login blocked, JWT killed (fake)") }
                    }
                }
            }
        }
    }
}

@Composable
fun TlMailboxScreen(repo: TimelineRepo) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TlHeadline("Mailbox")
        Spacer(Modifier.weight(1f))
        TlGhost("Mark all read") { repo.markAllRead() }
    }
    if (repo.notices.isEmpty()) {
        TlEmptyLine("All caught up", "No notifications in the mailbox.")
    }
    repo.notices.toList().forEach { n ->
        TlCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TlRailDot(if (n.read) TlColors.CardEdge else TlColors.Rail)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = TlColors.Ink, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold)
                    Text(n.body, color = TlColors.Faded, fontSize = 12.sp)
                }
                if (!n.read) {
                    TlLink("Mark read") {
                        n.read = true
                        val copy = repo.notices.toList()
                        repo.notices.clear()
                        repo.notices.addAll(copy)
                    }
                }
            }
        }
    }
}

@Composable
fun TlAuditScreen(repo: TimelineRepo) {
    TlHeadline("Audit log")
    TlNote("Every rail action lands here, newest first. REMITTED-day edits would flag Coordinator-only entries.")
    TlCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.audit.forEach { entry ->
                Text("• $entry", color = TlColors.Ink, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun TlProfileScreen(repo: TimelineRepo, onBack: () -> Unit) {
    val u = repo.currentUser ?: return
    TlHeadline("Profile")
    TlCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(u.name, color = TlColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Role: ${u.role} · Home: ${repo.branch(u.homeBranchId).name}", color = TlColors.Faded, fontSize = 13.sp)
            Text("Capabilities: ${if (u.capabilities.isEmpty()) "none" else u.capabilities.joinToString()}", color = TlColors.Faded, fontSize = 13.sp)
            Text("Clock: ${if (repo.clockedIn) "IN at ${repo.currentBranch().name}" else "OUT"}", color = TlColors.Ink, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (repo.clockedIn) {
            TlGhost("Clock out") {
                repo.clockedIn = false
                repo.log("${u.name} clocked out")
            }
        }
        TlGhost("Log out") {
            repo.log("${u.name} signed out")
            repo.currentUser = null
        }
        TlGhost("Exit prototype") { onBack() }
    }
}
