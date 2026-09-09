package com.companyb.companyapp.proto.financecockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CockpitFinance(store: CockpitStore, user: CockpitUser) {
    val branchId = store.currentBranchId.value ?: return
    val date = store.selectedDay.value
    val sessionDraft = store.remittances.firstOrNull { it.kind == CockpitRemitKind.SESSION && it.branchId == branchId && it.dayDate == date && it.state == CockpitSubmission.DRAFT }
    val productDraft = store.remittances.firstOrNull { it.kind == CockpitRemitKind.PRODUCT && it.branchId == branchId && it.dayDate == date && it.state == CockpitSubmission.DRAFT }
    val snapshots = store.remittances.filter { it.state == CockpitSubmission.SUBMITTED }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Remittance · SESSION + PRODUCT side-by-side", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        CockpitNote("Two independent flows. Submitting freezes an immutable snapshot of the P&L. Drafts are unconstrained and may overlap.")
        Row(horizontalArrangement = Arrangement.spacedBy(CockpitGap)) {
            Box(Modifier.weight(1f)) {
                CockpitLedgerCard(title = "SESSION ledger", subtitle = "net income after compensation and expenses · $date", accent = CockpitColors.Pine) {
                    val income = store.sessionIncome(branchId, date)
                    CockpitMoneyRow("Completed sessions", "${store.sessions.count { it.branchId == branchId && it.dayDate == date && !it.voided && it.status == CockpitSessionStatus.COMPLETED }} lines")
                    CockpitMoneyRow("Draft total", cockpitPeso(income), strong = true)
                    if (sessionDraft != null) {
                        CockpitRowButtons {
                            CockpitPrimary("Submit SESSION", onClick = {
                                val idx = store.remittances.indexOf(sessionDraft)
                                store.remittances[idx] = sessionDraft.copy(state = CockpitSubmission.SUBMITTED, frozenTotal = income, submittedAgo = "submitted just now", undoLeft = "48h left to undo")
                                store.audit(user.name, "SUBMIT", "remittance:${sessionDraft.id}", "SESSION snapshot frozen at ${cockpitPeso(income)} for $date.")
                            })
                        }
                    } else {
                        Text("No open SESSION draft for this day — already submitted or covered.", color = CockpitColors.Muted, fontSize = 13.sp)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                CockpitLedgerCard(title = "PRODUCT ledger", subtitle = "unit price × quantity · pooled per branch day", accent = CockpitColors.Brass) {
                    for (line in store.productLines) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${cockpitPeso(line.unitPrice)} each", color = CockpitColors.Muted, fontSize = 12.sp)
                            }
                            CockpitQuiet("−", onClick = {
                                val idx = store.productLines.indexOf(line)
                                if (line.qty > 0) store.productLines[idx] = line.copy(qty = line.qty - 1)
                            })
                            Text("${line.qty}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            CockpitQuiet("+", onClick = {
                                val idx = store.productLines.indexOf(line)
                                store.productLines[idx] = line.copy(qty = line.qty + 1)
                            })
                        }
                    }
                    CockpitMoneyRow("Pool total", cockpitPeso(store.productIncome()), strong = true)
                    if (productDraft != null) {
                        CockpitRowButtons {
                            CockpitPrimary("Submit PRODUCT", onClick = {
                                val idx = store.remittances.indexOf(productDraft)
                                val total = store.productIncome()
                                store.remittances[idx] = productDraft.copy(state = CockpitSubmission.SUBMITTED, frozenTotal = total, submittedAgo = "submitted just now", undoLeft = "48h left to undo")
                                store.audit(user.name, "SUBMIT", "remittance:${productDraft.id}", "PRODUCT snapshot frozen at ${cockpitPeso(total)} for $date.")
                            })
                        }
                    } else {
                        Text("No open PRODUCT draft for this day.", color = CockpitColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        CockpitLedgerCard(title = "Snapshot vault", subtitle = "immutable once frozen · Undo only within 48h with a reason", accent = CockpitColors.BrassDeep) {
            if (snapshots.isEmpty()) {
                Text("No snapshots yet. Submit a ledger to freeze one.", color = CockpitColors.Muted, fontSize = 13.sp)
            }
            for (snap in snapshots) {
                SnapshotRow(store, user, snap)
            }
        }
        CockpitLedgerCard(title = "Commission split", subtitle = "pooled per branch day · split equally · manual overrides audited", accent = CockpitColors.Emerald) {
            val pool = store.productIncome()
            val members = store.users.filter { store.includedInSplit.contains(it.id) }
            val share = if (members.isNotEmpty()) pool / members.size else 0
            Text("Pool ${cockpitPeso(pool)} across ${members.size} checked-in staff → ${cockpitPeso(share)} each. Separate from compensation, never remitted.", fontSize = 13.sp, color = CockpitColors.Muted)
            for (person in store.users) {
                val checked = store.includedInSplit.contains(person.id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = {
                        if (it) store.includedInSplit.add(person.id) else store.includedInSplit.remove(person.id)
                        store.audit(user.name, "SPLIT_OVERRIDE", "user:${person.id}", "Commission inclusion set to $it for ${person.name}.")
                    })
                    Text("${person.name} · ${person.role.label}", Modifier.weight(1f), fontSize = 13.sp)
                    if (checked) Text(cockpitPeso(share), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(store: CockpitStore, user: CockpitUser, snap: CockpitRemittance) {
    var showUndo by remember(snap.id) { mutableStateOf(false) }
    var reason by remember(snap.id) { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CockpitColors.PineDeep).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${snap.kind.label} · ${snap.dayDate}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(cockpitPeso(snap.frozenTotal ?: 0), color = CockpitColors.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Text("${snap.submittedAgo ?: "submitted"} · ${snap.undoLeft ?: "undo window unknown"}", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        CockpitRowButtons {
            CockpitSecondary("Undo within 48h", onClick = { showUndo = true })
        }
    }
    if (showUndo) {
        AlertDialog(
            onDismissRequest = { showUndo = false },
            title = { Text("Undo ${snap.kind.label} snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Undo returns the remittance to Draft, unlocks the covered days, and deletes the frozen snapshot. A reason is required and audited.")
                    TextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (required)") }, singleLine = false)
                }
            },
            confirmButton = {
                CockpitQuiet("Confirm undo", onClick = {
                    if (reason.isNotBlank()) {
                        val idx = store.remittances.indexOf(snap)
                        store.remittances[idx] = snap.copy(state = CockpitSubmission.DRAFT, frozenTotal = null, submittedAgo = null, undoLeft = null)
                        store.audit(user.name, "UNDO", "remittance:${snap.id}", "Undid ${snap.kind.label} snapshot: $reason")
                        showUndo = false
                    }
                })
            },
            dismissButton = { CockpitQuiet("Cancel", onClick = { showUndo = false }) },
        )
    }
}

@Composable
fun CockpitTeam(store: CockpitStore) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Team · users and roles", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        for (person in store.users.sortedBy { it.slot }) {
            val home = store.branches.firstOrNull { it.id == person.homeBranchId }?.name ?: person.homeBranchId
            CockpitLedgerCard(title = "${person.name} · slot ${person.slot}", subtitle = "${person.role.label} · home $home") {
                Text(person.role.summary, color = CockpitColors.Muted, fontSize = 13.sp)
                val clocked = store.clockedIn.contains(person.homeBranchId)
                Text(if (clocked) "On duty today." else "Off duty.", fontSize = 12.sp, color = if (clocked) CockpitColors.Emerald else CockpitColors.Muted)
            }
        }
        CockpitLedgerCard(title = "Role bundles", subtitle = "capabilities derive from the role, not the name") {
            for (role in CockpitRole.entries) {
                Text("• ${role.label} — ${role.summary}", fontSize = 13.sp, color = CockpitColors.Ink, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun CockpitMailbox(store: CockpitStore, user: CockpitUser) {
    val inbox = store.notifications.filter { it.toUserId == user.id }
    val unread = inbox.count { !it.read }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mailbox · $unread unread", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink, modifier = Modifier.weight(1f))
            CockpitQuiet("Mark all read", onClick = {
                store.markAllInboxRead(user.id)
                store.audit(user.name, "MAIL_READ", "mailbox:${user.id}", "Marked all messages read.")
            })
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(inbox, key = { it.id }) { note ->
                CockpitLedgerCard(
                    title = (if (note.read) "" else "● ") + note.title,
                    subtitle = note.body,
                    accent = if (note.read) CockpitColors.Hairline else CockpitColors.Brass,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.branchId != null && note.dayDate != null) {
                            CockpitQuiet("Open branch day", onClick = {
                                store.currentBranchId.value = note.branchId
                                store.selectedDay.value = note.dayDate
                                store.screen.value = CockpitScreen.HOME
                            })
                        }
                        Spacer(Modifier.weight(1f))
                        if (!note.read) {
                            CockpitQuiet("Mark read", onClick = {
                                val idx = store.notifications.indexOf(note)
                                store.notifications[idx] = note.copy(read = true)
                            })
                        } else {
                            Text("Read · kept forever", color = CockpitColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (inbox.isEmpty()) CockpitNote("Mailbox empty for this profile. Switch user on the login screen to see relief and reminder traffic.")
    }
}

@Composable
fun CockpitAuditList(store: CockpitStore) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Audit log · newest first", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        CockpitNote("Immutable record of every mutation: who changed what, when, and why. Entries prepend as you click through the prototype.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(store.audits, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CockpitColors.Cream).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${entry.action} · ${entry.record}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${entry.actor} — ${entry.detail}", color = CockpitColors.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CockpitProfile(store: CockpitStore, user: CockpitUser) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CockpitGap)) {
        Text("Profile", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CockpitColors.Ink)
        CockpitLedgerCard(title = user.name, subtitle = "${user.role.label} · slot ${user.slot}", accent = CockpitColors.Pine) {
            Text(user.role.summary, color = CockpitColors.Muted, fontSize = 13.sp)
            Text("Clocked in at ${store.clockedIn.size} branch(es). Relief edit grants at ${store.reliefEdit.size} branch(es).", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            CockpitRowButtons {
                CockpitSecondary("Clock out everywhere", onClick = {
                    store.clockedIn.clear()
                    store.audit(user.name, "CLOCK_OUT", "profile:${user.id}", "Clocked out of all branches.")
                })
                CockpitSecondary("Log out", onClick = {
                    store.audit(user.name, "LOGOUT", "auth:${user.id}", "Logged out from profile.")
                    store.currentUserId.value = null
                    store.currentBranchId.value = null
                    store.screen.value = CockpitScreen.HOME
                })
            }
        }
        CockpitNote("Logging out returns to the profile picker. Clock-out clears every branch check-in for this fake session.")
    }
}
