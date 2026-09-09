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

// #776 — print-ledger front-counter flows: onboarding, login, branches, counter, day, sessions.

@Composable
fun PlOnboarding(repo: PrintLedgerFakeRepo, onNext: () -> Unit) {
    var blocked by remember { mutableStateOf<String?>(null) }
    Column {
        PlTitle("ONBOARDING")
        PlSubtitle("Locked ONBOARDING account — zero capabilities until a role is granted.")
        PlGap()
        PlSlip {
            PlSection("NEW HIRE SLIP — J. RAMOS")
            PlRule()
            PlLine("ROLE", PlRole.ONBOARDING.name)
            PlLine("CAPABILITIES", "0")
            PlRule()
            PlButtonRow {
                PlGhost(text = "Try opening Sessions") {
                    blocked = "ONBOARDING holds zero capabilities — ask a MANAGER to grant a role."
                }
            }
            Spacer(Modifier.height(8.dp))
            PlButtonRow {
                PlPrimary(text = "Grant Practitioner role") {
                    repo.login("u-new")
                    val index = repo.users.indexOfFirst { it.id == "u-new" }
                    repo.users[index] = repo.users[index].copy(role = PlRole.PRACTITIONER)
                    repo.audit("D. Lim", "ROLE_GRANT", "J. Ramos → PRACTITIONER")
                    logInfo("PrintLedgerProto", "onboarding granted practitioner")
                    blocked = null
                    onNext()
                }
            }
            PlError(blocked)
        }
    }
}

@Composable
fun PlLogin(repo: PrintLedgerFakeRepo, onNext: () -> Unit) {
    Column {
        PlTitle("LOGIN")
        PlSubtitle("Fake directory — tap a name to sign the day book.")
        PlGap()
        repo.users.forEach { user ->
            PlSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = PlColors.Ink, fontSize = 16.sp,
                            fontWeight = FontWeight.Black)
                        Text("${user.role.name} · ${user.id}", color = PlColors.Faint,
                            fontSize = 12.sp, fontFamily = PlMono)
                    }
                    PlGhost(text = "Sign in") {
                        repo.login(user.id)
                        repo.audit(user.name, "LOGIN", user.name)
                        logInfo("PrintLedgerProto", "signed in ${user.id}")
                        onNext()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PlBranchSelect(repo: PrintLedgerFakeRepo, onNext: () -> Unit) {
    Column {
        PlTitle("BRANCHES")
        PlSubtitle("One active branch counter at a time — switching re-prints the ledger head.")
        PlGap()
        repo.branches.forEach { branch ->
            val active = branch.id == repo.currentBranchId
            PlSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, color = PlColors.Ink, fontSize = 16.sp,
                            fontWeight = FontWeight.Black)
                        Text(branch.kind.name, color = PlColors.Faint, fontSize = 12.sp,
                            fontFamily = PlMono)
                    }
                    if (active) {
                        PlStamp(text = "ACTIVE", color = PlColors.StampOpen)
                    } else {
                        PlGhost(text = "Open counter") {
                            repo.currentBranchId = branch.id
                            repo.audit(repo.currentUser?.name ?: "guest", "BRANCH_SWITCH", branch.name)
                            onNext()
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PlCounter(repo: PrintLedgerFakeRepo, go: (PlScreen) -> Unit) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        PlTitle("COUNTER")
        PlSubtitle("Clock-in home — duty state, relief duty, invites, requests.")
        PlGap()
        PlSlip {
            PlSection("DUTY SLIP — ${repo.currentBranch.name.uppercase()}")
            PlRule()
            PlLine("STAFF", user)
            PlLine("STATUS", if (repo.clockedIn) "CLOCKED IN" else "OFF DUTY",
                if (repo.clockedIn) PlColors.StampOpen else PlColors.Faint)
            PlRule()
            PlButtonRow {
                if (repo.clockedIn) {
                    PlGhost(text = "Clock out") {
                        repo.clockedIn = false
                        repo.audit(user, "CLOCK_OUT", repo.currentBranch.name)
                    }
                } else {
                    PlPrimary(text = "Clock in") {
                        repo.clockedIn = true
                        repo.audit(user, "CLOCK_IN",
                            "${repo.currentBranch.name} / ${repo.selectedDay.dateLabel}")
                    }
                }
                PlGhost(text = "Open day sheet") { go(PlScreen.DAY) }
                PlGhost(text = "Print receipts") { go(PlScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        PlSlip {
            PlSection("RELIEF INVITES")
            PlRule()
            if (repo.invites.isEmpty()) {
                PlNote("No open invites.")
            }
            repo.invites.forEach { invite ->
                Text("${invite.fromUser} → cover ${invite.branchName} on ${invite.day}",
                    color = PlColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val verdict = invite.accepted
                if (verdict == null) {
                    Spacer(Modifier.height(6.dp))
                    PlButtonRow {
                        PlPrimary(text = "Accept") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = true)
                            repo.audit(user, "RELIEF_ACCEPT", invite.branchName)
                        }
                        PlGhost(text = "Decline") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = false)
                            repo.audit(user, "RELIEF_DECLINE", invite.branchName)
                        }
                    }
                } else {
                    PlNote(if (verdict) "Accepted — printed to the duty roster." else "Declined.")
                }
                PlRule()
            }
        }
        Spacer(Modifier.height(10.dp))
        PlSlip {
            PlSection("RELIEF REQUESTS")
            PlRule()
            repo.requests.forEach { request ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(request.branchName, color = PlColors.Ink, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold)
                        PlNote("${request.day} · " + if (request.mine) "mine" else "cover needed" +
                            (request.decided?.let { " · $it" } ?: ""))
                    }
                    if (request.mine && request.decided == null) {
                        PlGhost(text = "Withdraw") {
                            val i = repo.requests.indexOfFirst { it.id == request.id }
                            repo.requests[i] = request.copy(decided = "WITHDRAWN")
                            repo.audit(user, "RELIEF_WITHDRAW", request.branchName)
                        }
                    } else if (!request.mine && request.decided == null) {
                        PlGhost(text = "Cover") {
                            val i = repo.requests.indexOfFirst { it.id == request.id }
                            repo.requests[i] = request.copy(decided = "COVERED")
                            repo.audit(user, "RELIEF_COVER", request.branchName)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PlDaySheet(repo: PrintLedgerFakeRepo, go: (PlScreen) -> Unit) {
    val day = repo.selectedDay
    val lines = repo.sessionsFor(day.id)
    Column {
        PlTitle("DAY SHEET")
        PlSubtitle("${day.dow} ${day.dateLabel} · ${repo.currentBranch.name} · ${day.status.name}.")
        PlGap()
        PlSlip {
            Row {
                PlStat("LINES", lines.size.toString())
                PlStat("DONE", lines.count { it.status == PlSessionStatus.COMPLETED }.toString(),
                    PlColors.StampOpen)
                PlStat("OPEN", lines.count { it.status == PlSessionStatus.PENDING }.toString())
                PlStat("TOTAL", "PHP ${repo.dayTotal(day.id)}", PlColors.StampOpen)
            }
            PlRule()
            PlButtonRow {
                PlGhost(text = "Manage sessions") { go(PlScreen.SESSIONS) }
                PlGhost(text = "Print receipts") { go(PlScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        lines.forEach { session ->
            PlSlip {
                PlSessionLine(session = session)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (lines.isEmpty()) {
            PlSlip { PlNote("No lines on this sheet yet — book a walk-in from Sessions.") }
        }
    }
}

@Composable
fun PlSessionLine(session: PlSession) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${session.time}  ${session.clientName}", color = PlColors.Ink,
                fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("${session.id} · ${session.type} · ${session.practitioner}" +
                if (session.walkIn) " · WALK-IN" else "",
                color = PlColors.Faint, fontSize = 12.sp, fontFamily = PlMono)
        }
        Column(horizontalAlignment = Alignment.End) {
            PlFigure("PHP ${session.price}")
            Text(
                (if (session.voided) "VOID · " else "") + session.status.name,
                color = if (session.voided) PlColors.StampVoid else PlColors.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = PlMono,
            )
        }
    }
}

@Composable
fun PlSessions(repo: PrintLedgerFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    var filter by remember { mutableStateOf<PlSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkTime by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lines = repo.sessionsFor(repo.selectedDayId).filter { filter == null || it.status == filter }
    Column {
        PlTitle("SESSIONS")
        PlSubtitle("${repo.selectedDay.dow} ${repo.selectedDay.dateLabel} ledger lines. " +
            "Walk-in lines refuse NO_SHOW and CANCELLED.")
        PlGap()
        PlButtonRow {
            PlChip(text = "ALL", selected = filter == null) { filter = null }
            PlSessionStatus.entries.forEach { status ->
                PlChip(text = status.name, selected = filter == status) { filter = status }
            }
        }
        Spacer(Modifier.height(6.dp))
        lines.forEach { session ->
            PlSlip {
                PlSessionLine(session = session)
                if (session.voidReason != null) {
                    PlNote("Void reason: ${session.voidReason}")
                }
                Spacer(Modifier.height(6.dp))
                PlButtonRow {
                    PlGhost(text = if (openId == session.id) "Close" else "Manage") {
                        openId = if (openId == session.id) null else session.id
                        error = null
                    }
                }
                if (openId == session.id) {
                    PlRule()
                    PlSection("POST STATUS")
                    Spacer(Modifier.height(6.dp))
                    PlButtonRow {
                        PlSessionStatus.entries.forEach { status ->
                            PlChip(text = status.name, selected = session.status == status) {
                                error = repo.setStatus(session.id, status, user)
                            }
                        }
                    }
                    PlRule()
                    PlSection(if (session.voided) "UNVOID LINE" else "VOID LINE")
                    Spacer(Modifier.height(6.dp))
                    if (session.voided) {
                        PlButtonRow {
                            PlGhost(text = "Unvoid") {
                                error = repo.setVoid(session.id, false, "", user)
                            }
                        }
                    } else {
                        TextField(value = voidReason, onValueChange = { voidReason = it },
                            label = { Text("Reason (required)") },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        PlButtonRow {
                            PlPrimary(text = "Void with reason") {
                                error = repo.setVoid(session.id, true, voidReason.trim(), user)
                                if (error == null) voidReason = ""
                            }
                        }
                    }
                    PlError(error)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PlSlip {
            PlSection("WALK-IN ENTRY")
            PlRule()
            TextField(value = walkName, onValueChange = { walkName = it },
                label = { Text("Client name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = walkTime, onValueChange = { walkTime = it },
                label = { Text("Time (HH:MM)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PlButtonRow {
                PlPrimary(text = "Add walk-in PHP 800") {
                    if (walkName.isBlank() || walkTime.isBlank()) {
                        error = "Name and time are both required for a walk-in line."
                    } else {
                        repo.addWalkIn(walkName.trim(), walkTime.trim(), user)
                        walkName = ""
                        walkTime = ""
                        error = null
                    }
                }
            }
            PlError(error)
        }
    }
}

@Composable
fun PlClients(repo: PrintLedgerFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        PlTitle("CLIENTS")
        PlSubtitle("Global file — at most one PENDING line per client. Anonymized view masks PII.")
        PlGap()
        repo.clients.forEach { client ->
            val anon = repo.anonymizedIds.contains(client.id)
            val pending = repo.pendingFor(client.id)
            PlSlip {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (anon) "CLIENT ${client.id.uppercase()}" else client.name,
                            color = PlColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("${client.gender} · ${client.age}" + if (anon) " · ANONYMIZED" else "",
                            color = PlColors.Faint, fontSize = 12.sp, fontFamily = PlMono)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PlFigure("PEND $pending",
                            if (pending > 1) PlColors.StampVoid else PlColors.Ink)
                        if (pending > 1) {
                            Text("OVER LIMIT", color = PlColors.StampVoid, fontSize = 11.sp,
                                fontWeight = FontWeight.Black, fontFamily = PlMono)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PlButtonRow {
                    if (anon) {
                        PlGhost(text = "Reveal") {
                            repo.anonymizedIds = repo.anonymizedIds - client.id
                            repo.audit(user, "DEANONYMIZE", client.id)
                        }
                    } else {
                        PlGhost(text = "Anonymize") {
                            repo.anonymizedIds = repo.anonymizedIds + client.id
                            repo.audit(user, "ANONYMIZE", client.id)
                            logInfo("PrintLedgerProto", "anonymized ${client.id}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
