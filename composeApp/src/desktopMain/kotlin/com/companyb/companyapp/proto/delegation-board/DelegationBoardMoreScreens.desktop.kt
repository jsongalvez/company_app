package com.companyb.companyapp.proto.delegationboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun DClientsScreen(repo: DelegationBoardFakeRepo) {
    var query by remember { mutableStateOf("") }
    SectionHeader(index = "C-1", title = "Clients", aside = "global record")
    FieldNote("One client, every branch: a client holds at most one PENDING session at a time across the mission.")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search clients") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
    val list = repo.clients.filter { it.name.contains(query, ignoreCase = true) }
    if (list.isEmpty()) {
        EmptyManifest("No client matches that search.")
    } else {
        list.forEach { c ->
            ManifestCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "Anonymized · ${c.gender}/${c.age}" else c.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        ManifestCode("${c.id.uppercase()} · ${c.gender}/${c.age} · ${c.branchNote.uppercase()}")
                        if (c.hasPending) {
                            Text(
                                text = "Has the one live PENDING session",
                                fontSize = 11.sp,
                                color = TriageAmber,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    BoardLink(
                        text = if (c.anonymized) "Reveal (demo)" else "Anonymize",
                        onClick = { repo.toggleAnonymized(c.id) },
                    )
                }
                if (c.anonymized) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Soft-deleted + PII nullified; gender and age kept for reporting.",
                        fontSize = 11.sp,
                        color = ManifestMuted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DFinanceScreen(repo: DelegationBoardFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("Heat patch") }
    var newQty by remember { mutableStateOf("3") }
    var newPrice by remember { mutableStateOf("150") }

    SectionHeader(
        index = "F-1",
        title = "Remittance",
        aside = "Day 14 · ${repo.branchName(repo.currentBranchId.value)}",
    )
    val sessionDraft = repo.remittances.firstOrNull { it.kind == DRemitKind.SESSION && it.status == DRemitStatus.DRAFT }
    if (sessionDraft != null) {
        ManifestCard(accent = DispatchTeal) {
            Text(text = "SESSION draft · ${sessionDraft.dayLabel}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ManifestCode("NET = SESSIONS − COMPENSATION − EXPENSES")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = repo.sessionExpense.value,
                onValueChange = { repo.sessionExpense.value = it.filter { ch -> ch.isDigit() } },
                label = { Text("Compensation + expenses") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = "Draft net: P${repo.sessionDraftTotal()}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            BoardButton(text = "Submit SESSION (seal snapshot)", onClick = { repo.submitRemit(DRemitKind.SESSION) })
        }
        Spacer(Modifier.height(8.dp))
    }
    val productDraft = repo.remittances.firstOrNull { it.kind == DRemitKind.PRODUCT && it.status == DRemitStatus.DRAFT }
    if (productDraft != null) {
        ManifestCard(accent = TriageAmber) {
            Text(text = "PRODUCT draft · ${productDraft.dayLabel}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            ManifestCode("UNIT PRICE × QUANTITY PER LINE")
            Spacer(Modifier.height(6.dp))
            repo.remitLines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = line.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    ManifestCode("${line.qty} × P${line.price} = P${line.qty * line.price}")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Product") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = newQty,
                    onValueChange = { newQty = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = { newPrice = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Price") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                BoardGhostButton(
                    text = "Add line",
                    onClick = {
                        repo.remitLines.add(
                            DRemitLine(
                                "p-${repo.remitLines.size + 1}-n",
                                newLabel,
                                newQty.toIntOrNull() ?: 1,
                                newPrice.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
                BoardButton(
                    text = "Submit PRODUCT (P${repo.productDraftTotal()})",
                    onClick = { repo.submitRemit(DRemitKind.PRODUCT) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    SectionHeader(index = "F-2", title = "Snapshots", aside = "immutable · undo 48h")
    val sealed = repo.remittances.filter { it.status == DRemitStatus.SUBMITTED }
    if (sealed.isEmpty()) {
        EmptyManifest("No sealed snapshots yet — submit a draft above.")
    } else {
        sealed.forEach { r ->
            ManifestCard(accent = TriageGreen) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${r.kind} · ${r.dayLabel} · P${r.amount}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        ManifestCode("SNAPSHOT ${r.snapshot} · ${r.submittedAt.uppercase()}")
                    }
                    StatusTag(r.status.name)
                }
                Spacer(Modifier.height(6.dp))
                if (undoTarget == r.id) {
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (required, 48h window)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        BoardButton(
                            text = "Confirm undo",
                            danger = true,
                            onClick = {
                                if (undoReason.isNotBlank()) {
                                    repo.undoRemit(r.id, undoReason)
                                    undoReason = ""
                                    undoTarget = null
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        BoardGhostButton(text = "Keep sealed") { undoTarget = null }
                    }
                } else {
                    BoardLink(text = "Undo within 48h", onClick = { undoTarget = r.id })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(4.dp))
    FieldNote(
        "Commission split: product commissions pool per branch day and split equally among all practitioners and coordinators clocked in at sold_at time. Manual inclusions/exclusions may override. Separate from compensation, never remitted.",
    )
}

@Composable
fun DTeamScreen(repo: DelegationBoardFakeRepo) {
    SectionHeader(index = "T-1", title = "Team", aside = "roles + slots")
    repo.users.sortedBy { it.slot }.forEach { u ->
        ManifestCard(accent = if (u.onboarding) TriageAmber else null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ManifestCode("#${u.slot.toString().padStart(2, '0')}")
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text =
                            "${u.role} · home ${repo.branchName(u.homeBranchId)}" +
                                (u.clockedBranchId?.let { " · on duty at ${repo.branchName(it)}" } ?: " · off duty"),
                        fontSize = 12.sp,
                        color = ManifestMuted,
                    )
                    if (u.onboarding) {
                        Text(
                            text = "ONBOARDING — empty capability bundle, locked out",
                            fontSize = 11.sp,
                            color = TriageAmber,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (u.onboarding) {
                    BoardButton(text = "Grant Practitioner", onClick = { repo.grantRole(u.id, "Practitioner") })
                } else {
                    TriageTag(text = u.role, color = DispatchTeal, soft = DispatchTealSoft)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(4.dp))
    FieldNote(
        "Relief practitioners sort after home slots on reports. A MANAGER is a Coordinator superset with user-management and delegate-assignment powers.",
    )
}

@Composable
fun DMailScreen(repo: DelegationBoardFakeRepo) {
    SectionHeader(index = "M-1", title = "Mailbox", aside = "${repo.notes.count { !it.read }} unread")
    Row {
        BoardGhostButton(text = "Mark all read", onClick = { repo.markAllRead() })
    }
    Spacer(Modifier.height(8.dp))
    if (repo.notes.isEmpty()) {
        EmptyManifest("Mailbox bliss — nothing waiting.")
    } else {
        repo.notes.forEach { n ->
            ManifestCard(accent = if (n.read) null else TriageRed) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = n.body, fontSize = 12.sp)
                        ManifestCode("${repo.branchName(n.branchId).uppercase()} · ${n.day.uppercase()}")
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusTag(if (n.read) "READ" else "UNREAD")
                }
                if (!n.read) {
                    Spacer(Modifier.height(6.dp))
                    BoardLink(text = "Mark read", onClick = { n.read = true })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DAuditScreen(repo: DelegationBoardFakeRepo) {
    SectionHeader(index = "A-1", title = "Audit log", aside = "immutable · newest first")
    if (repo.audits.isEmpty()) {
        EmptyManifest("No mutations recorded yet.")
    } else {
        repo.audits.forEach { a ->
            ManifestCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${a.action} — ${a.record}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ManifestCode("${a.actor.uppercase()} · ${a.whenText.uppercase()}")
                        if (a.reason.isNotEmpty()) {
                            Text(text = "Reason: ${a.reason}", fontSize = 12.sp, color = ManifestMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun DProfileScreen(
    repo: DelegationBoardFakeRepo,
    onLogout: () -> Unit,
) {
    val me = repo.currentUser()
    var picked by remember { mutableStateOf(repo.currentUserId.value) }
    SectionHeader(index = "P-1", title = "Profile")
    ManifestCard(accent = DispatchTeal) {
        Text(text = me.name, fontWeight = FontWeight.Black, fontSize = 18.sp)
        ManifestCode(
            "${me.id.uppercase()} · ${me.role.uppercase()} · HOME ${repo.branchName(me.homeBranchId).uppercase()}",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = me.clockedBranchId?.let { "On duty at ${repo.branchName(it)}" } ?: "Off duty",
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            if (me.clockedBranchId == null) {
                BoardButton(text = "Clock in at post", onClick = { repo.clockIn(repo.currentBranchId.value) })
            } else {
                BoardGhostButton(text = "Clock out", onClick = { repo.clockOut() })
            }
            Spacer(Modifier.width(8.dp))
            BoardButton(text = "Log out", danger = true, onClick = onLogout)
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionHeader(index = "P-2", title = "Demo controls", aside = "fake data only")
    ManifestCard {
        Text(text = "Switch delegate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        repo.users.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "${u.name} · ${u.role}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (picked == u.id) {
                    TriageTag(text = "Active", color = TriageGreen, soft = TriageGreenSoft)
                } else {
                    BoardLink(
                        text = "Switch",
                        onClick = {
                            picked = u.id
                            repo.currentUserId.value = u.id
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "Branch-day state for ${repo.currentBranch().name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row {
            DDayStatus.entries.forEach { s ->
                if (repo.currentBranch().dayStatus == s) {
                    BoardButton(text = s.name) {}
                } else {
                    BoardGhostButton(text = s.name) { repo.flipDay(repo.currentBranchId.value, s) }
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        BoardGhostButton(text = "Reset demo data", onClick = { repo.reset() })
    }
}
