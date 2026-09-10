package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// #819 — owner-desk money, people-counts, mailbox, audit, and profile screens.

@Composable
fun OdFinanceScreen(repo: OwnerDeskRepo) {
    var flow by remember { mutableStateOf("SESSION") }
    var amount by remember { mutableStateOf("4500") }
    var undoTarget by remember { mutableStateOf<OdRemittance?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("Money in, money sealed")
        OdNote("SESSION and PRODUCT drafts live apart. Submit seals a snapshot; Undo reopens it within 48h with a reason. Past 48h, the seal is permanent.")
        OdNote("Commission split note: the house and the practitioner split each COMPLETED session; the split posts only when its snapshot seals.")
        OdLedgerCard {
            OdLedgerTitle("Draft a remittance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OdLedgerGhost("Flow: $flow") { flow = if (flow == "SESSION") "PRODUCT" else "SESSION" }
                Column(Modifier.width(160.dp)) {
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Amount ₱") })
                }
                OdLedgerButton("Draft it") {
                    repo.addRemittance(flow, amount.toIntOrNull() ?: 0, repo.currentBranch().name)
                }
            }
        }
        repo.remittances.forEach { r ->
            OdLedgerCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OdLedgerTitle("${r.id} · ${r.flow} · ₱${r.amount}")
                        OdLedgerFaint("${r.branchName} · ${r.stage}" + (r.submittedHoursAgo?.let { " · sealed ${it}h ago" } ?: ""))
                    }
                    Spacer(Modifier.width(8.dp))
                    when (r.stage) {
                        "Draft" -> OdLedgerButton("Submit") { repo.submitRemittance(r.id) }
                        else -> {
                            val hours = r.submittedHoursAgo ?: 0
                            if (hours < 48) {
                                OdLedgerGhost("Undo (48h)") { undoTarget = r }
                            } else {
                                OdLedgerFaint("Seal permanent")
                            }
                        }
                    }
                }
            }
        }
    }
    undoTarget?.let { r ->
        var reason by remember { mutableStateOf("") }
        OdLedgerCard {
            OdLedgerTitle("Undo snapshot ${r.id}?")
            OdLedgerFaint("Within 48h only — days unlock, the snapshot is deleted, reason recorded.")
            OutlinedTextField(reason, { reason = it }, singleLine = true, label = { Text("Reason") })
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OdLedgerButton("Undo it") {
                    if (reason.isNotBlank()) {
                        repo.undoRemittance(r.id, reason.trim())
                        undoTarget = null
                    }
                }
                OdLedgerGhost("Keep sealed") { undoTarget = null }
            }
        }
    }
}

@Composable
fun OdPeopleScreen(repo: OwnerDeskRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("People, counted not opened")
        OdNote("The owner sees headcount and capability bundles. No staff detail pages — particulars stay sealed.")
        val grouped = repo.users.groupBy { it.role }
        grouped.forEach { (role, members) ->
            OdLedgerCard {
                Row(Modifier.fillMaxWidth()) {
                    OdStat("Role", role)
                    OdStat("Headcount", "${members.size}")
                    OdStat("Locked", "${members.count { it.locked }}")
                }
                OdLedgerFaint("Capabilities: ${(members.flatMap { it.capabilities }.distinct().ifEmpty { listOf("none — key withheld") }).joinToString(", ")}")
                OdLedgerFaint("MANAGER bundle is the staff superset; Owner sees all branches and all money.")
            }
        }
        OdLedgerCard {
            OdLedgerTitle("ONBOARDING seats")
            repo.users.filter { it.role == "ONBOARDING" }.forEach { u ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OdLedgerText("Seat ${u.id}")
                        OdLedgerFaint(if (u.locked) "Key withheld — grant to activate" else "Active")
                    }
                    OdLedgerGhost(if (u.locked) "Grant" else "Deactivate") {
                        repo.log("ONBOARDING seat ${u.id} ${if (u.locked) "granted" else "deactivated"} (fake)")
                    }
                }
            }
        }
    }
}

@Composable
fun OdMailboxScreen(repo: OwnerDeskRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("Dispatch box")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdGhostButton("Mark all read") {
                repo.markAllRead()
                repo.log("Mailbox marked all-read")
            }
        }
        repo.notices.forEach { n ->
            OdLedgerCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OdLedgerTitle((if (n.read) "" else "● ") + n.title)
                        OdLedgerText(n.body)
                    }
                    if (!n.read) {
                        Spacer(Modifier.width(8.dp))
                        OdLedgerGhost("Read") {
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
}

@Composable
fun OdAuditScreen(repo: OwnerDeskRepo) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("The day book")
        OdNote("Every hand on the desk leaves ink. Newest first.")
        repo.audit.forEach { entry ->
            OdLedgerCard {
                OdLedgerText(entry)
            }
        }
    }
}

@Composable
fun OdProfileScreen(repo: OwnerDeskRepo, onBack: () -> Unit) {
    val user = repo.currentUser!!
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OdHeadline("The nameplate")
        OdLedgerCard {
            OdLedgerTitle(user.name)
            OdLedgerText("${user.role} · home ${repo.branch(user.homeBranchId).name}")
            OdLedgerFaint("Capabilities: ${if (user.capabilities.isEmpty()) "none — key withheld" else user.capabilities.joinToString(", ")}")
            OdLedgerFaint(if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}." else "Not clocked in.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repo.clockedIn) {
                OdGhostButton("Clock out") {
                    repo.clockedIn = false
                    repo.log("${user.name} clocked out")
                }
            }
            OdGhostButton("Log out") {
                repo.currentUser = null
                repo.currentBranchId = ""
                repo.clockedIn = false
            }
            OdGhostButton("Leave the desk") { onBack() }
        }
    }
}
