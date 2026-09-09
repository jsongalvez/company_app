package com.companyb.companyapp.proto.inventorycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

// #774 — ops screens: sessions roster + booking, clients, finance/remittance,
// team, mailbox, audit log, profile. Every screen reads the same FakeRepo.

// Sessions: roster for this branch + walk-in booking; tap through to detail.
@Composable
fun IcSessionsScreen(repo: InventoryCounterRepo) {
    var openId by remember { mutableStateOf<String?>(null) }
    var showBook by remember { mutableStateOf(false) }
    val open = openId?.let { id -> repo.sessions.firstOrNull { it.id == id } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        if (open == null) {
            IcHeadline("Sessions — ${repo.currentBranch().name}")
            IcNote("One visit, one client, one branch. PENDING moves to COMPLETED, NO_SHOW, or CANCELLED.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IcPrimary(if (showBook) "Hide booking" else "Book session") { showBook = !showBook }
            }
            if (showBook) IcBookSession(repo) { showBook = false }
            repo.branchSessions(repo.currentBranchId).forEach { s ->
                IcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.time} · ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}" +
                                    if (s.voided) " · VOID" else "",
                                color = IcColors.Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${s.kind} · ₱${s.price} · ${s.practitioners}" +
                                    if (repo.sessionProductTotal(s.id) > 0) {
                                        " · +₱${repo.sessionProductTotal(s.id)} products"
                                    } else {
                                        ""
                                    },
                                color = IcColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        IcFlag(s.status.name, s.status.dot(), IcColors.Shelf)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IcLink("Open") { openId = s.id }
                        IcLink("Complete") { repo.advance(s.id, IcSessionStatus.COMPLETED) }
                    }
                }
            }
        } else {
            IcSessionDetail(repo, open.id) { openId = null }
        }
    }
}

@Composable
private fun IcBookSession(repo: InventoryCounterRepo, onDone: () -> Unit) {
    var client by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Follow-up") }
    var priceRaw by remember { mutableStateOf("1200") }
    IcCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IcKicker("Book session")
            TextField(value = client, onValueChange = { client = it }, label = { Text("Client name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextField(value = kind, onValueChange = { kind = it }, label = { Text("Session type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextField(
                value = priceRaw,
                onValueChange = { priceRaw = it.filter { c -> c.isDigit() } },
                label = { Text("Price (defaults to base rate, overridable)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IcPrimary("Book PENDING") {
                    if (client.isNotBlank()) {
                        repo.addSession(client, kind, priceRaw.toIntOrNull() ?: 0)
                        onDone()
                    }
                }
                IcGhost("Cancel") { onDone() }
            }
        }
    }
}

// Clients: global record shared across branches, at-most-one-PENDING note,
// anonymized view retained for reporting.
@Composable
fun IcClientsScreen(repo: InventoryCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Clients — global record")
        IcNote(
            "One global record per person, shared across all branches. " +
                "At most one PENDING session at a time per client.",
        )
        repo.clients.forEach { c ->
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.name, color = IcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (c.anonymized) {
                        IcNote("Anonymized view: PII nulled, gender ${c.gender} + age ${c.age} retained for reporting.")
                    } else {
                        Text(c.contact, color = IcColors.Faded, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.pendingCount > 0) {
                            IcFlag("1 PENDING", IcColors.Safety, IcColors.SafetyWash)
                        } else {
                            IcFlag("NO PENDING", IcColors.Moss, IcColors.MossWash)
                        }
                    }
                }
            }
        }
    }
}

// Finance: SESSION + PRODUCT drafts, submit seals a snapshot, Undo inside 48h
// with a reason; commission split note pooled per branch day.
@Composable
fun IcFinanceScreen(repo: InventoryCounterRepo) {
    var undoId by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Finance — remittance")
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IcKicker("Product math — live from the ledger")
                IcLedger(
                    "Linked line sales at ${repo.currentBranch().name}",
                    "₱${repo.productDraftTotal(repo.currentBranchId)}",
                    strong = true,
                )
                IcNote("PRODUCT flow = unit price x quantity, summed across linked line sales. Drafts overlap freely.")
            }
        }
        repo.remittances.forEach { r ->
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${r.flow} · ${r.id} · ${r.branchName}",
                                color = IcColors.Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("₱${r.amount}", color = IcColors.Stencil, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IcFlag(r.stage.uppercase(), IcColors.Steel, IcColors.SteelWash)
                    }
                    if (r.stage == "Snapshot") {
                        IcNote(
                            "Immutable snapshot sealed" +
                                (r.submittedHoursAgo?.let { " $it h ago" } ?: "") +
                                ". Undo open for 48h with a reason; afterwards it is permanent.",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.stage == "Draft") {
                            IcPrimary("Submit (seal snapshot)") { repo.submitRemit(r.id) }
                        } else {
                            IcLink(if (undoId == r.id) "Hide undo" else "Undo within 48h") {
                                undoId = if (undoId == r.id) null else r.id
                            }
                        }
                    }
                    if (undoId == r.id && r.stage == "Snapshot") {
                        TextField(
                            value = undoReason,
                            onValueChange = { undoReason = it },
                            label = { Text("Undo reason (required)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IcDanger("Confirm undo") {
                                if (undoReason.isNotBlank()) {
                                    repo.undoRemit(r.id, undoReason)
                                    undoId = null
                                    undoReason = ""
                                }
                            }
                        }
                        IcNote("Undo returns the remittance to Draft, unlocks covered days, deletes the snapshot.")
                    }
                }
            }
        }
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IcKicker("Commission split")
                IcNote(
                    "Product commissions pool per branch day and split equally among all practitioners " +
                        "and coordinators clocked in at the sold_at time. Manual inclusions/exclusions can " +
                        "override. Separate from compensation, never remitted.",
                )
            }
        }
    }
}

// Team: users, roles, capability bundles, branch slot order note.
@Composable
fun IcTeamScreen(repo: InventoryCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Team — users & roles")
        IcNote(
            "Roles are bundles of capabilities; runtime checks use capabilities, not role names. " +
                "Branch slot orders display (1 = senior); relief practitioners sort after home slots.",
        )
        repo.users.forEachIndexed { slot, u ->
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "#${slot + 1} ${u.name} · ${u.role}" + if (u.locked) " · ONBOARDING locked" else "",
                        color = IcColors.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (u.capabilities.isEmpty()) "capabilities: none (empty bundle)" else "capabilities: ${u.capabilities.joinToString()}",
                        color = IcColors.Faded,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IcLink("Grant role") { repo.log("MANAGE_USERS: ${repo.currentUser?.name} granted a role to ${u.name} (fake)") }
                    }
                }
            }
        }
    }
}

// Mailbox: notifications with read/unread, history kept.
@Composable
fun IcMailboxScreen(repo: InventoryCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Mailbox — notifications")
        IcNote("Every message stored per recipient; read rows kept forever as history.")
        repo.notices.forEach { n ->
            IcCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (n.read) "" else "● ") + n.title,
                            color = IcColors.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(n.body, color = IcColors.Faded, fontSize = 12.sp)
                    }
                    if (!n.read) IcLink("Mark read") { repo.markRead(n.id) }
                }
            }
        }
    }
}

// Audit log: immutable list of who changed what and why.
@Composable
fun IcAuditScreen(repo: InventoryCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Audit log")
        IcNote("Immutable record of every mutation — who changed what, when, and why.")
        repo.audit.forEach { entry ->
            IcCard { Text(entry, color = IcColors.Ink, fontSize = 13.sp) }
        }
    }
}

// Profile: current user, capabilities, logout + clock-out.
@Composable
fun IcProfileScreen(repo: InventoryCounterRepo, onBack: () -> Unit) {
    val me = repo.currentUser
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Profile")
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(me?.name ?: "—", color = IcColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Role: ${me?.role}", color = IcColors.Stencil, fontSize = 13.sp)
                Text(
                    "Capabilities: ${me?.capabilities?.joinToString() ?: "—"}",
                    color = IcColors.Faded,
                    fontSize = 12.sp,
                )
                IcLedger("Home branch", repo.branch(me?.homeBranchId ?: "b-makati").name)
                IcLedger("Clock state", if (repo.clockedIn) "clocked in" else "clocked out", strong = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        IcGhost("Clock out") {
                            repo.clockedIn = false
                            repo.log("${me?.name} clocked out (fake)")
                        }
                    }
                    IcGhost("Log out") {
                        repo.currentUser = null
                        repo.clockedIn = false
                    }
                    IcLink("Exit prototype") { onBack() }
                }
            }
        }
    }
}
