package com.companyb.companyapp.proto.clerkcounter

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #826 — counter ops: sessions roster + booking, clients, finance/remittance,
// team, mailbox, audit log, profile. Every screen reads the same FakeRepo.

// Sessions: roster for this branch + walk-in booking; tap through to detail.
@Composable
fun CcSessionsScreen(repo: ClerkCounterRepo) {
    var openId by remember { mutableStateOf<String?>(null) }
    var showBook by remember { mutableStateOf(false) }
    val open = openId?.let { id -> repo.sessions.firstOrNull { it.id == id } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        if (open == null) {
            CcHeadline("Sessions — ${repo.currentBranch().name}")
            CcNote("One visit, one client, one branch. PENDING moves to COMPLETED, NO_SHOW, or CANCELLED.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcPrimary(if (showBook) "Hide booking" else "Book session") { showBook = !showBook }
            }
            if (showBook) CcBookSession(repo) { showBook = false }
            repo.branchSessions(repo.currentBranchId).forEach { s ->
                CcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.time} · ${s.clientName}${if (s.walkIn) " (walk-in)" else ""}" +
                                    if (s.voided) " · VOID" else "",
                                color = CcColors.Ink,
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
                                color = CcColors.Muted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        CcFlag(s.status.name, s.status.dot(), CcColors.Paper)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcLink("Open") { openId = s.id }
                        CcLink("Complete") { repo.advance(s.id, CcSessionStatus.COMPLETED) }
                    }
                }
            }
        } else {
            CcSessionDetail(repo, open.id) { openId = null }
        }
    }
}

@Composable
private fun CcBookSession(repo: ClerkCounterRepo, onDone: () -> Unit) {
    var client by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Follow-up") }
    var priceRaw by remember { mutableStateOf("1200") }
    CcCard {
        CcKicker("Book session")
        TextField(value = client, onValueChange = { client = it }, label = { Text("Client name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        TextField(value = kind, onValueChange = { kind = it }, label = { Text("Session type") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        TextField(
            value = priceRaw,
            onValueChange = { priceRaw = it.filter { c -> c.isDigit() } },
            label = { Text("Price (defaults to base rate, overridable)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcPrimary("Book PENDING") {
                if (client.isNotBlank()) {
                    repo.addSession(client, kind, priceRaw.toIntOrNull() ?: 0)
                    onDone()
                }
            }
            CcGhost("Cancel") { onDone() }
        }
    }
}

// Clients: global record shared across branches, at-most-one-PENDING note,
// anonymized view retained for reporting.
@Composable
fun CcClientsScreen(repo: ClerkCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Clients — global record")
        CcNote(
            "One global record per person, shared across all branches. " +
                "At most one PENDING session at a time per client.",
        )
        repo.clients.forEach { c ->
            CcCard {
                Text(c.name, color = CcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (c.anonymized) {
                    CcNote("Anonymized view: PII nulled, gender ${c.gender} + age ${c.age} retained for reporting.")
                } else {
                    Text(c.contact, color = CcColors.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.pendingCount > 0) {
                        CcFlag("1 PENDING", CcColors.Low, CcColors.LowWash)
                    } else {
                        CcFlag("NO PENDING", CcColors.Match, CcColors.MatchWash)
                    }
                }
            }
        }
    }
}

// Finance: SESSION + PRODUCT drafts, submit seals a snapshot, Undo inside 48h
// with a reason; commission split note pooled per branch day.
@Composable
fun CcFinanceScreen(repo: ClerkCounterRepo) {
    var undoId by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Finance — remittance")
        CcCard {
            CcKicker("Product math — live from the lane")
            CcTape(
                "Linked line sales at ${repo.currentBranch().name}",
                "₱${repo.productDraftTotal(repo.currentBranchId)}",
                strong = true,
            )
            CcNote("PRODUCT flow = unit price x quantity, summed across linked line sales. Drafts overlap freely.")
        }
        repo.remittances.forEach { r ->
            CcCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.flow} · ${r.id} · ${r.branchName}",
                            color = CcColors.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text("₱${r.amount}", color = CcColors.Stencil, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    CcFlag(r.stage.uppercase(), CcColors.Steel, CcColors.SteelWash)
                }
                if (r.stage == "Snapshot") {
                    CcNote(
                        "Immutable snapshot sealed" +
                            (r.submittedHoursAgo?.let { " $it h ago" } ?: "") +
                            ". Undo open for 48h with a reason; afterwards it is permanent.",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.stage == "Draft") {
                        CcPrimary("Submit (seal snapshot)") { repo.submitRemit(r.id) }
                    } else {
                        CcLink(if (undoId == r.id) "Hide undo" else "Undo within 48h") {
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
                        CcDanger("Confirm undo") {
                            if (undoReason.isNotBlank()) {
                                repo.undoRemit(r.id, undoReason)
                                undoId = null
                                undoReason = ""
                            }
                        }
                    }
                    CcNote("Undo returns the remittance to Draft, unlocks covered days, deletes the snapshot.")
                }
            }
        }
        CcCard {
            CcKicker("Commission split")
            CcNote(
                "Product commissions pool per branch day and split equally among all practitioners " +
                    "and coordinators clocked in at the sold_at time. Manual inclusions/exclusions can " +
                    "override. Separate from compensation, never remitted.",
            )
        }
    }
}

// Team: users, roles, capability bundles, branch slot order note.
@Composable
fun CcTeamScreen(repo: ClerkCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Team — users & roles")
        CcNote(
            "Roles are bundles of capabilities; runtime checks use capabilities, not role names. " +
                "Branch slot orders display (1 = senior); relief practitioners sort after home slots.",
        )
        repo.users.forEachIndexed { slot, u ->
            CcCard {
                Text(
                    "#${slot + 1} ${u.name} · ${u.role}" + if (u.locked) " · ONBOARDING locked" else "",
                    color = CcColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (u.capabilities.isEmpty()) "capabilities: none (empty bundle)"
                    else "capabilities: ${u.capabilities.joinToString()}",
                    color = CcColors.Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CcLink("Grant role") {
                        repo.log("MANAGE_USERS: ${repo.currentUser?.name} granted a role to ${u.name} (fake)")
                    }
                }
            }
        }
    }
}

// Mailbox: notifications with read/unread, history kept.
@Composable
fun CcMailboxScreen(repo: ClerkCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Mailbox — notifications")
        CcNote("Every message stored per recipient; read rows kept forever as history.")
        repo.notices.forEach { n ->
            CcCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (n.read) "" else "● ") + n.title,
                            color = CcColors.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(n.body, color = CcColors.Muted, fontSize = 12.sp)
                    }
                    if (!n.read) CcLink("Mark read") { repo.markRead(n.id) }
                }
            }
        }
    }
}

// Audit log: immutable list of who changed what and why.
@Composable
fun CcAuditScreen(repo: ClerkCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Audit log")
        CcNote("Immutable record of every mutation — who changed what, when, and why.")
        repo.audit.forEach { entry ->
            CcCard {
                Text(entry, color = CcColors.Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// Profile: current user, capabilities, logout + clock-out.
@Composable
fun CcProfileScreen(repo: ClerkCounterRepo, onBack: () -> Unit) {
    val me = repo.currentUser
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Profile")
        CcCard {
            Text(me?.name ?: "—", color = CcColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Role: ${me?.role}", color = CcColors.Stencil, fontSize = 13.sp)
            Text(
                "Capabilities: ${me?.capabilities?.joinToString() ?: "—"}",
                color = CcColors.Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            CcTape("Home branch", repo.branch(me?.homeBranchId ?: "b-makati").name)
            CcTape("Clock state", if (repo.clockedIn) "TILL OPEN" else "TILL SHUT", strong = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    CcGhost("Clock out") {
                        repo.clockedIn = false
                        repo.log("${me?.name} clocked out (fake)")
                    }
                }
                CcGhost("Log out") {
                    repo.currentUser = null
                    repo.clockedIn = false
                }
                CcLink("Exit prototype") { onBack() }
            }
        }
    }
}
