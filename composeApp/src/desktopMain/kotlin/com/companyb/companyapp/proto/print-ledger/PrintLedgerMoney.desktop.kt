package com.companyb.companyapp.proto.printledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.companyb.companyapp.util.logInfo

// #776 — print-ledger back office: remittance receipts, team, mailbox, audit, profile.

@Composable
fun PlFinance(repo: PrintLedgerFakeRepo) {
    Column {
        PlTitle("RECEIPTS")
        PlSubtitle("SESSION and PRODUCT remittances print as first-class receipts: " +
            "draft → submit → sealed slip → undo in 48h.")
        PlGap()
        PlReceiptCard(repo = repo, kind = PlRemitKind.SESSION)
        Spacer(Modifier.height(10.dp))
        PlReceiptCard(repo = repo, kind = PlRemitKind.PRODUCT)
        Spacer(Modifier.height(10.dp))
        PlSlip {
            PlSection("COMMISSION SPLIT")
            PlRule()
            PlNote("Session income pools per branch day and splits equally over staff clocked in " +
                "at sold_at. Relief payouts come from the relief branch drawer.")
        }
    }
}

@Composable
private fun PlReceiptCard(repo: PrintLedgerFakeRepo, kind: PlRemitKind) {
    val remit = if (kind == PlRemitKind.SESSION) repo.remitSession else repo.remitProduct
    var amount by remember(kind) { mutableStateOf(remit.draftTotal.toString()) }
    var reason by remember(kind) { mutableStateOf("") }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    val user = repo.currentUser?.name ?: "clerk"
    PlSlip {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()) {
            Text("* * *  R E M I T T A N C E  * * *", color = PlColors.Faint, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, fontFamily = PlMono)
            Text("${kind.name} RECEIPT", color = PlColors.Ink, fontSize = 20.sp,
                fontWeight = FontWeight.Black)
            PlStamp(text = remit.state.name,
                color = if (remit.state == PlRemitState.DRAFT) PlColors.StampPast
                else PlColors.StampRemitted)
        }
        PlRule()
        when (remit.state) {
            PlRemitState.DRAFT -> {
                TextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Draft total (PHP)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PlButtonRow {
                    PlPrimary(text = "Save draft") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Enter a numeric draft total."
                        } else {
                            repo.updateRemit(kind, remit.copy(draftTotal = total), user, "DRAFT_SAVE")
                            error = null
                        }
                    }
                    PlGhost(text = "Submit + print") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Enter a numeric draft total."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = PlRemitState.SUBMITTED,
                                    draftTotal = total,
                                    snapshotId = repo.nextReceiptId(kind),
                                    snapshotTotal = total,
                                ),
                                user,
                                "SUBMIT",
                            )
                            logInfo("PrintLedgerProto", "${kind.name} receipt submitted")
                            error = null
                        }
                    }
                }
                PlNote("Drafts are unconstrained and may overlap. Submit freezes an immutable slip.")
            }
            PlRemitState.SUBMITTED -> {
                PlLine("RECEIPT NO", remit.snapshotId ?: "—")
                PlLine("SEALED TOTAL", "PHP ${remit.snapshotTotal}")
                PlLine("CASHIER", user)
                PlRule()
                PlNote("Later edits to sessions never rewrite a sealed slip. Undo deletes the slip " +
                    "and returns the remittance to Draft — within 48 hours, with a reason.")
                Spacer(Modifier.height(8.dp))
                TextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Undo reason (required)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PlButtonRow {
                    PlPrimary(text = "Undo (48h)") {
                        if (reason.isBlank()) {
                            error = "A reason is required to undo."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = PlRemitState.DRAFT,
                                    snapshotId = null,
                                    snapshotTotal = null,
                                    undoReason = reason.trim(),
                                ),
                                user,
                                "UNDO",
                            )
                            reason = ""
                            error = null
                        }
                    }
                }
            }
        }
        PlError(error)
    }
}

@Composable
fun PlTeam(repo: PrintLedgerFakeRepo) {
    Column {
        PlTitle("TEAM")
        PlSubtitle("Roster by home branch with role print-outs.")
        PlGap()
        repo.branches.forEach { branch ->
            PlSlip {
                PlSection(branch.name.uppercase() + " — " + branch.kind.name)
                PlRule()
                repo.users.filter { it.homeBranchId == branch.id }.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, color = PlColors.Ink, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold)
                            Text(member.role.name, color = PlColors.Faint, fontSize = 12.sp,
                                fontFamily = PlMono)
                        }
                        if (repo.currentUserId == member.id) {
                            PlStamp(text = "YOU", color = PlColors.StampOpen)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PlSlip {
            PlSection("ROLE NOTES")
            PlRule()
            PlNote("ONBOARDING holds zero capabilities. ACCOUNTANT reads every branch but edits none. " +
                "Coordinators solely edit PAST and REMITTED records; MANAGER adds user management.")
        }
    }
}

@Composable
fun PlMailbox(repo: PrintLedgerFakeRepo) {
    val user = repo.currentUser?.name ?: "clerk"
    Column {
        PlTitle("MAILBOX")
        PlSubtitle("Pigeonholes — mark read one by one or clear the whole tray.")
        PlGap()
        PlButtonRow {
            PlGhost(text = "Mark all read") {
                repo.notifications.forEachIndexed { i, note -> repo.notifications[i] = note.copy(read = true) }
                repo.audit(user, "MAIL_READ_ALL", "notifications")
            }
        }
        Spacer(Modifier.height(6.dp))
        repo.notifications.forEach { note ->
            PlSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (note.read) "" else "● ") + note.title,
                            color = PlColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        PlNote(note.body)
                    }
                    if (!note.read) {
                        PlGhost(text = "Mark read") {
                            val i = repo.notifications.indexOfFirst { it.id == note.id }
                            repo.notifications[i] = note.copy(read = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PlAuditList(repo: PrintLedgerFakeRepo) {
    Column {
        PlTitle("AUDIT LOG")
        PlSubtitle("Bound book — every prototype mutation prepends who / action / target / reason.")
        PlGap()
        repo.audits.forEach { entry ->
            PlSlip {
                Text("${entry.whenLabel} · ${entry.who} · ${entry.action}",
                    color = PlColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black,
                    fontFamily = PlMono)
                PlNote(entry.target + (entry.reason?.let { " — $it" } ?: ""))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PlProfile(repo: PrintLedgerFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    Column {
        PlTitle("PROFILE")
        PlSubtitle("Sign-off slip — duty state, clock-out, logout.")
        PlGap()
        PlSlip {
            if (user == null) {
                PlNote("Nobody is signed in.")
            } else {
                Text(user.name, color = PlColors.Ink, fontSize = 22.sp,
                    fontWeight = FontWeight.Black)
                Text("${user.role.name} · home ${repo.branches.first { it.id == user.homeBranchId }.name}",
                    color = PlColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PlMono)
                PlNote(if (repo.clockedIn) "On duty at ${repo.currentBranch.name}." else "Off duty.")
                PlRule()
                PlButtonRow {
                    if (repo.clockedIn) {
                        PlGhost(text = "Clock out") {
                            repo.clockedIn = false
                            repo.audit(user.name, "CLOCK_OUT", repo.currentBranch.name)
                        }
                    }
                    PlPrimary(text = "Log out") {
                        repo.audit(user.name, "LOGOUT", user.name)
                        repo.logout()
                        logInfo("PrintLedgerProto", "signed out")
                        onLogout()
                    }
                }
            }
        }
    }
}
