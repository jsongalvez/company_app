package com.companyb.companyapp.proto.splitmaster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// #765 — split-master ops panes. Every destination is a split: 480dp master list
// plus flexible detail, selection retained, scroll hoisted to the shell.

// ---- Home: clock + relief ----

@Composable
fun SmHomePane(repo: SplitMasterRepo, scroll: LazyListState) {
    val lanes = listOf("All", "Duty", "Requests", "Invites")
    var lane by remember { mutableStateOf("All") }
    SmSplit(
        master = {
            SmMasterHead("MASTER · RELIEF", "Relief board", "Pick an item — detail explains the rule.")
            SmMasterTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lanes.forEach { l ->
                        if (l == lane) SmMasterAction(l) { lane = l } else SmMasterGhost(l) { lane = l }
                    }
                }
            }
            val items = repo.relief.filter { lane == "All" || it.lane == lane }
            SmMasterList(
                items = items, key = { it.id }, selectedKey = repo.selectedReliefId,
                onSelect = { repo.selectedReliefId = it.id }, scroll = scroll,
                row = { r, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmMono(r.lane.uppercase() + " · " + r.state, selected)
                        SmMasterTheme {
                            Text(r.title, color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            SmDetailCard {
                SmSubhead(if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Off the clock")
                SmNote("Home branch: ${repo.branch(repo.currentUser?.homeBranchId ?: repo.currentBranchId).name}. Relief starts view-only; edit needs a grant. Duty expires 04:00 Manila next day.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        SmGhost("Clock out") { repo.clockedIn = false; repo.log("${repo.currentUser?.name} clocked out") }
                    } else {
                        SmPrimary("Clock in") { repo.clockedIn = true; repo.log("${repo.currentUser?.name} clocked in at ${repo.currentBranch().name}") }
                    }
                }
            }
            val sel = repo.relief.firstOrNull { it.id == repo.selectedReliefId }
            if (sel == null) {
                SmDetailCard { SmNote("Pick a relief item in the master list.") }
            } else {
                SmDetailCard {
                    SmSubhead(sel.title)
                    SmBody(sel.body)
                    SmNote(
                        when (sel.lane) {
                            "Requests" -> "Outsider-initiated broadcast: one live request per requester per branch per date. Any active branch member grants, denies, or cancels; the grant locks once you clock in as relief."
                            "Invites" -> "Branch-initiated single future day: accepting writes the day grant. The branch may revoke an accepted future duty until you clock in; revocation frees re-invite."
                            else -> "Relief pay comes from the relief branch drawer, never the home drawer."
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmPrimary("Accept / approve") { sel.state = "ACCEPTED"; repo.log("Relief ${sel.id} accepted (fake)") }
                        SmGhost("Decline / withdraw") { sel.state = "WITHDRAWN"; repo.log("Relief ${sel.id} declined (fake)") }
                    }
                }
            }
        },
    )
}

// ---- Clients ----

@Composable
fun SmClientsPane(repo: SplitMasterRepo, scroll: LazyListState) {
    var showAnon by remember { mutableStateOf(false) }
    SmSplit(
        master = {
            SmMasterHead("MASTER · CLIENTS", "Clients — global", "One record across branches.")
            SmMasterTheme {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAnon, onCheckedChange = { showAnon = it })
                    Text("Anonymized view", color = SmColors.InkText, fontSize = 13.sp)
                }
            }
            SmMasterList(
                items = repo.clients, key = { it.id }, selectedKey = repo.selectedClientId,
                onSelect = { repo.selectedClientId = it.id }, scroll = scroll,
                row = { c, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmMono(c.id.uppercase(), selected)
                        SmMasterTheme {
                            Text(
                                if (showAnon || c.anonymized) "Client ${c.id.uppercase()}" else c.name,
                                color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        SmMasterTheme {
                            Text(
                                if (c.pendingCount > 0) "1 PENDING — booking blocked" else "no PENDING",
                                color = if (c.pendingCount > 0) SmColors.Amber else SmColors.InkFaded,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            val c = repo.clients.firstOrNull { it.id == repo.selectedClientId }
            if (c == null) {
                SmDetailCard { SmNote("Pick a client in the master list.") }
            } else {
                SmDetailCard {
                    SmHeadline(if (showAnon || c.anonymized) "Client ${c.id.uppercase()}" else c.name)
                    SmNote("Global record · ${c.contact}")
                    SmBody("At most one PENDING session per client at a time — a second booking is blocked until the open one resolves.")
                    if (c.anonymized) SmNote("Anonymized: PII nullified, gender ${c.gender} + age ${c.age} retained for reporting.")
                    if (showAnon && !c.anonymized) SmNote("Preview of anonymized view: name/contact masked, gender + age retained.")
                }
                SmDetailCard {
                    SmSubhead("Open sessions")
                    val open = repo.sessions.filter { it.clientName == c.name && it.status == SmSessionStatus.PENDING }
                    if (open.isEmpty()) SmNote("No PENDING sessions — booking open.") else open.forEach {
                        SmBody("${it.id.uppercase()} · ${it.time} · ${repo.branch(it.branchId).name}")
                    }
                }
            }
        },
    )
}

// ---- Finance ----

@Composable
fun SmFinancePane(repo: SplitMasterRepo, scroll: LazyListState) {
    var reason by remember(repo.selectedRemitId) { mutableStateOf("") }
    SmSplit(
        master = {
            SmMasterHead("MASTER · FINANCE", "Remittances", "SESSION + PRODUCT drafts.")
            SmMasterTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmMasterAction("+ SESSION") {
                        val id = "r-%02d".format(repo.remitSeq++)
                        repo.remittances.add(SmRemittance(id, "SESSION", "Draft", 9600, repo.currentBranch().name))
                        repo.selectedRemitId = id
                        repo.log("SESSION draft $id opened")
                    }
                    SmMasterGhost("+ PRODUCT") {
                        val id = "r-%02d".format(repo.remitSeq++)
                        repo.remittances.add(SmRemittance(id, "PRODUCT", "Draft", 1500, repo.currentBranch().name))
                        repo.selectedRemitId = id
                        repo.log("PRODUCT draft $id opened")
                    }
                }
            }
            SmMasterList(
                items = repo.remittances.toList(), key = { it.id }, selectedKey = repo.selectedRemitId,
                onSelect = { repo.selectedRemitId = it.id }, scroll = scroll,
                row = { r, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmMono(r.flow + " · " + r.id.uppercase(), selected)
                        SmMasterTheme {
                            Text("₱${r.amount} · ${r.stage.uppercase()}", color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            SmDetailCard {
                SmSubhead("Commission split")
                SmBody("Each COMPLETED session splits net income between practitioner compensation and the branch drawer; the SESSION flow remits the drawer share. PRODUCT remits unit price × quantity.")
            }
            val r = repo.remittances.firstOrNull { it.id == repo.selectedRemitId }
            if (r == null) {
                SmDetailCard { SmNote("Pick a remittance in the master list.") }
            } else {
                SmDetailCard {
                    SmHeadline("${r.flow} · ${r.id.uppercase()}")
                    SmNote("₱${r.amount} · ${r.branchName} · stage ${r.stage.uppercase()}")
                    when {
                        r.stage == "Draft" -> SmPrimary("Submit — seal snapshot") { repo.submitRemittance(r.id) }
                        r.submittedHoursAgo != null && r.submittedHoursAgo <= 48 -> {
                            SmBody("Submitted ${r.submittedHoursAgo}h ago — Undo window open (48h).")
                            TextField(value = reason, onValueChange = { reason = it }, label = { Text("Undo reason (required)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            SmPrimary("Undo — back to Draft", enabled = reason.isNotBlank()) {
                                repo.undoRemittance(r.id, reason.trim())
                                reason = ""
                            }
                        }
                        else -> SmNote("Submitted ${r.submittedHoursAgo}h ago — snapshot permanent. Later edits never rewrite it.")
                    }
                }
            }
        },
    )
}

// ---- Team ----

@Composable
fun SmTeamPane(repo: SplitMasterRepo, scroll: LazyListState) {
    SmSplit(
        master = {
            SmMasterHead("MASTER · TEAM", "Users & roles", "MANAGER superset note in detail.")
            SmMasterList(
                items = repo.users, key = { it.id }, selectedKey = repo.selectedMemberId,
                onSelect = { repo.selectedMemberId = it.id }, scroll = scroll,
                row = { u, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmMono(u.role.uppercase(), selected)
                        SmMasterTheme {
                            Text(u.name + if (u.locked) " · ONBOARDING" else "", color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            SmDetailCard {
                SmSubhead("Role model")
                SmBody("MANAGER is a superset of Coordinator: user management + delegate assignment on top of finance duties. Accountant is read-only everywhere. ONBOARDING holds an empty capability bundle — nothing derives until a MANAGER grants a real role via MANAGE_USERS.")
            }
            val u = repo.users.firstOrNull { it.id == repo.selectedMemberId }
            if (u == null) {
                SmDetailCard { SmNote("Pick a member in the master list.") }
            } else {
                SmDetailCard {
                    SmHeadline("${u.name} · ${u.role}")
                    SmNote("Home ${repo.branch(u.homeBranchId).name}")
                    if (u.capabilities.isEmpty()) {
                        SmBody("Empty capability bundle — locked out of every flow.")
                    } else {
                        u.capabilities.forEach { SmBody("• $it") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmPrimary("Grant Practitioner") { repo.log("${u.name} granted Practitioner (fake)") }
                        SmGhost("Deactivate") { repo.log("${u.name} deactivated (fake)") }
                    }
                }
            }
        },
    )
}

// ---- Mailbox ----

@Composable
fun SmMailboxPane(repo: SplitMasterRepo, scroll: LazyListState) {
    SmSplit(
        master = {
            SmMasterHead("MASTER · MAIL", "Notifications", "${repo.notices.count { !it.read }} unread.")
            SmMasterTheme {
                SmMasterAction("Mark all read") { repo.markAllNoticesRead() }
            }
            SmMasterList(
                items = repo.notices.toList(), key = { it.id }, selectedKey = repo.selectedNoticeId,
                onSelect = {
                    repo.selectedNoticeId = it.id
                    it.read = true
                },
                scroll = scroll,
                row = { n, selected ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SmMono(if (n.read) "READ" else "UNREAD", selected)
                        SmMasterTheme {
                            Text(n.title, color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            val n = repo.notices.firstOrNull { it.id == repo.selectedNoticeId }
            if (n == null) {
                SmDetailCard { SmNote("Mailbox empty — pick a notice in the master list.") }
            } else {
                SmDetailCard {
                    SmHeadline(n.title)
                    SmBody(n.body)
                    SmNote(if (n.read) "Marked read on open." else "Unread — opening marks it read.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmGhost(if (n.read) "Mark unread" else "Mark read") {
                            n.read = !n.read
                            repo.log("Notice ${n.id} marked ${if (n.read) "read" else "unread"}")
                        }
                    }
                }
            }
        },
    )
}

// ---- Audit ----

@Composable
fun SmAuditPane(repo: SplitMasterRepo, scroll: LazyListState) {
    SmSplit(
        master = {
            SmMasterHead("MASTER · AUDIT", "Audit log", "Newest first, ${repo.audit.size} entries.")
            SmMasterList(
                items = repo.audit.toList(), key = { it }, selectedKey = repo.audit.firstOrNull() ?: "",
                onSelect = {}, scroll = scroll,
                row = { entry, _ ->
                    SmMasterTheme {
                        Text(entry, color = SmColors.InkText, fontSize = 13.sp)
                    }
                },
            )
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            SmDetailCard {
                SmHeadline("What gets logged")
                SmBody("Sign-in/out, clock in/out, session transitions, void/unvoid, remittance draft/submit/undo, relief decisions, mailbox reads — every action in this prototype appends here, newest first.")
            }
            SmDetailCard {
                SmSubhead("Latest")
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repo.audit.take(8).forEach { SmBody("• $it") }
                }
            }
        },
    )
}

// ---- Profile ----

@Composable
fun SmProfilePane(repo: SplitMasterRepo, onExit: () -> Unit) {
    val user = repo.currentUser
    SmSplit(
        master = {
            SmMasterHead("MASTER · PROFILE", user?.name ?: "Profile", user?.role ?: "")
            SmMasterTheme {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Signed in as", color = SmColors.InkFaded, fontSize = 12.sp)
                    Text(user?.name ?: "—", color = SmColors.InkText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(1.dp))
        },
        detail = {
            SmDayBanner(repo.currentBranch())
            SmDetailCard {
                SmHeadline("Capability bundle")
                if (user == null || user.capabilities.isEmpty()) {
                    SmBody("Empty — ONBOARDING locked.")
                } else {
                    user.capabilities.forEach { SmBody("• $it") }
                }
                SmNote("Branch ${repo.currentBranch().name} · ${if (repo.clockedIn) "clocked in" else "off the clock"}.")
            }
            SmDetailCard {
                SmSubhead("Session controls")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        SmGhost("Clock out") { repo.clockedIn = false; repo.log("${user?.name} clocked out") }
                    }
                    SmGhost("Logout") { repo.signOut() }
                    SmTextLink("Exit prototype", onExit)
                }
            }
        },
    )
}
