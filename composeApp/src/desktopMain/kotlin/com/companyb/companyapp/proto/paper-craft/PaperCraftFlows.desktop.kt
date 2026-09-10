package com.companyb.companyapp.proto.papercraft

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// #842 — paper-craft front sheets: cover, login, branches, desk, branch day, sessions.

@Composable
fun PcOnboarding(repo: PaperCraftFakeRepo, onNext: () -> Unit) {
    var blocked by remember { mutableStateOf<String?>(null) }
    Column {
        PcTitle("Cover Sheet")
        PcSubtitle("A fresh ONBOARDING cut-out — no strings attached until a role is pasted on.")
        PcGap()
        PcSheet {
            PcHead("FRESH CUT-OUT — J. RAMOS")
            PcWashi(color = PcColors.WashiYellow)
            PcLine("ROLE", PcRole.ONBOARDING.name)
            PcLine("STRINGS", "none")
            PcButtonRow {
                PcCutGhost(text = "Try the Sessions sheet") {
                    blocked = "The ONBOARDING cut-out has no strings — ask a MANAGER to paste a role."
                }
            }
            Spacer(Modifier.height(8.dp))
            PcButtonRow {
                PcCutPrimary(text = "Paste Practitioner") {
                    repo.login("u-new")
                    val index = repo.users.indexOfFirst { it.id == "u-new" }
                    repo.users[index] = repo.users[index].copy(role = PcRole.PRACTITIONER)
                    repo.audit("D. Lim", "ROLE_GRANT", "J. Ramos → PRACTITIONER")
                    logInfo("PaperCraftProto", "cover pasted practitioner")
                    blocked = null
                    onNext()
                }
            }
            PcError(blocked)
        }
    }
}

@Composable
fun PcLogin(repo: PaperCraftFakeRepo, onNext: () -> Unit) {
    Column {
        PcTitle("Sheet 1 — Faces")
        PcSubtitle("A paper-doll row of five faces — tap one to stick it on the desk.")
        PcGap()
        repo.users.forEachIndexed { index, user ->
            PcSheet {
                PcWashi(color = washiFor(index))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = PcColors.Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, fontFamily = PcHand)
                        Text("${user.role.name} · ${user.id}", color = PcColors.Faint,
                            fontSize = 12.sp, fontFamily = PcHand)
                    }
                    PcCutGhost(text = "Stick on") {
                        repo.login(user.id)
                        repo.audit(user.name, "LOGIN", user.name)
                        logInfo("PaperCraftProto", "stuck ${user.id}")
                        onNext()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PcBranchSelect(repo: PaperCraftFakeRepo, onNext: () -> Unit) {
    Column {
        PcTitle("Sheet 2 — Desks")
        PcSubtitle("One desk mat unrolled at a time — moving desks re-lays every sheet.")
        PcGap()
        repo.branches.forEachIndexed { index, branch ->
            val active = branch.id == repo.currentBranchId
            PcSheet {
                PcWashi(color = washiFor(index + 2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, color = PcColors.Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, fontFamily = PcHand)
                        Text(branch.kind.name, color = PcColors.Faint, fontSize = 12.sp,
                            fontFamily = PcHand)
                    }
                    if (active) {
                        PcSticker(text = "UNROLLED", color = PcColors.StampOpen)
                    } else {
                        PcCutGhost(text = "Unroll here") {
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
fun PcDesk(repo: PaperCraftFakeRepo, go: (PcScreen) -> Unit) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        PcTitle("Sheet 3 — The Desk")
        PcSubtitle("Clock the duty paper — relief duty, invites, and requests.")
        PcGap()
        PcSheet {
            PcHead("DUTY PAPER — ${repo.currentBranch.name.uppercase()}")
            PcWashi(color = PcColors.WashiTeal)
            PcLine("FACE", user)
            PcLine(
                "SCISSORS",
                if (repo.clockedIn) "DOWN" else "UP",
                if (repo.clockedIn) PcColors.StampOpen else PcColors.Faint,
            )
            PcButtonRow {
                if (repo.clockedIn) {
                    PcCutGhost(text = "Scissors up") {
                        repo.clockedIn = false
                        repo.audit(user, "CLOCK_OUT", repo.currentBranch.name)
                    }
                } else {
                    PcCutPrimary(text = "Scissors down") {
                        repo.clockedIn = true
                        repo.audit(user, "CLOCK_IN",
                            "${repo.currentBranch.name} / ${repo.selectedDay.dateLabel}")
                    }
                }
                PcCutGhost(text = "Branch day") { go(PcScreen.DAY) }
                PcCutGhost(text = "Remittance") { go(PcScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        PcSheet {
            PcHead("RELIEF INVITES")
            PcWashi(color = PcColors.WashiPink)
            if (repo.invites.isEmpty()) {
                PcNote("No taped-on invites.")
            }
            repo.invites.forEach { invite ->
                Text("${invite.fromUser} asks cover of ${invite.branchName} on ${invite.day}",
                    color = PcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PcHand)
                val verdict = invite.accepted
                if (verdict == null) {
                    Spacer(Modifier.height(6.dp))
                    PcButtonRow {
                        PcCutPrimary(text = "Paste in") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = true)
                            repo.audit(user, "RELIEF_ACCEPT", invite.branchName)
                        }
                        PcCutGhost(text = "Snip away") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = false)
                            repo.audit(user, "RELIEF_DECLINE", invite.branchName)
                        }
                    }
                } else {
                    PcNote(if (verdict) "Pasted into the duty collage." else "Snipped away.")
                }
                PcWashi(color = PcColors.WashiBlue)
            }
        }
        Spacer(Modifier.height(10.dp))
        PcSheet {
            PcHead("RELIEF REQUESTS")
            PcWashi(color = PcColors.WashiGreen)
            repo.requests.forEach { request ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(request.branchName, color = PcColors.Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, fontFamily = PcHand)
                        PcNote("${request.day} · " + if (request.mine) "mine" else "cover wanted" +
                            (request.decided?.let { " · $it" } ?: ""))
                    }
                    if (request.mine && request.decided == null) {
                        PcCutGhost(text = "Peel off") {
                            val i = repo.requests.indexOfFirst { it.id == request.id }
                            repo.requests[i] = request.copy(decided = "WITHDRAWN")
                            repo.audit(user, "RELIEF_WITHDRAW", request.branchName)
                        }
                    } else if (!request.mine && request.decided == null) {
                        PcCutGhost(text = "Cover") {
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
fun PcBranchDay(repo: PaperCraftFakeRepo, go: (PcScreen) -> Unit) {
    val day = repo.selectedDay
    val lines = repo.sessionsFor(day.id)
    Column {
        PcTitle("Sheet 4 — Branch Day")
        PcSubtitle("${day.dow} ${day.dateLabel} · ${repo.currentBranch.name} · ${day.status.name}.")
        PcGap()
        PcSheet {
            Row {
                PcTally("CUT-OUTS", lines.size.toString())
                PcTally("DONE", lines.count { it.status == PcSessionStatus.COMPLETED }.toString(),
                    PcColors.StampOpen)
                PcTally("QUEUED", lines.count { it.status == PcSessionStatus.PENDING }.toString())
                PcTally("SUM", "₱${repo.dayTotal(day.id)}", PcColors.StampOpen)
            }
            PcWashi(color = PcColors.WashiYellow)
            PcButtonRow {
                PcCutGhost(text = "Cut sessions") { go(PcScreen.SESSIONS) }
                PcCutGhost(text = "Tape remittance") { go(PcScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        lines.forEach { session ->
            PcSheet {
                PcSessionCut(session = session)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (lines.isEmpty()) {
            PcSheet { PcNote("No cut-outs glued yet — snip a walk-in from Sessions.") }
        }
    }
}

@Composable
fun PcSessionCut(session: PcSession) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${session.time}  ${session.clientName}", color = PcColors.Ink,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = PcHand)
            Text("${session.id} · ${session.type} · ${session.practitioner}" +
                if (session.walkIn) " · WALK-IN" else "",
                color = PcColors.Faint, fontSize = 12.sp, fontFamily = PcHand)
        }
        Column(horizontalAlignment = Alignment.End) {
            PcSum("₱${session.price}")
            Text(
                (if (session.voided) "SNIPPED · " else "") + session.status.name,
                color = if (session.voided) PcColors.StampVoid else PcColors.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = PcHand,
            )
        }
    }
}

@Composable
fun PcSessions(repo: PaperCraftFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    var filter by remember { mutableStateOf<PcSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkTime by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lines = repo.sessionsFor(repo.selectedDayId).filter { filter == null || it.status == filter }
    Column {
        PcTitle("Sheet 5 — Sessions")
        PcSubtitle("${repo.selectedDay.dow} ${repo.selectedDay.dateLabel} cut-outs. " +
            "Walk-in cut-outs refuse NO_SHOW and CANCELLED.")
        PcGap()
        PcButtonRow {
            PcTab(text = "WHOLE", selected = filter == null) { filter = null }
            PcSessionStatus.entries.forEach { status ->
                PcTab(text = status.name, selected = filter == status) { filter = status }
            }
        }
        Spacer(Modifier.height(6.dp))
        lines.forEach { session ->
            PcSheet {
                PcSessionCut(session = session)
                if (session.voidReason != null) {
                    PcNote("Snip tape: ${session.voidReason}")
                }
                Spacer(Modifier.height(6.dp))
                PcButtonRow {
                    PcCutGhost(text = if (openId == session.id) "Fold shut" else "Unfold") {
                        openId = if (openId == session.id) null else session.id
                        error = null
                    }
                }
                if (openId == session.id) {
                    PcWashi(color = PcColors.WashiPink)
                    PcHead("RE-STICK STATUS")
                    Spacer(Modifier.height(6.dp))
                    PcButtonRow {
                        PcSessionStatus.entries.forEach { status ->
                            PcTab(text = status.name, selected = session.status == status) {
                                error = repo.setStatus(session.id, status, user)
                            }
                        }
                    }
                    PcWashi(color = PcColors.WashiTeal)
                    PcHead(if (session.voided) "UN-SNIP CUT-OUT" else "SNIP CUT-OUT")
                    Spacer(Modifier.height(6.dp))
                    if (session.voided) {
                        PcButtonRow {
                            PcCutGhost(text = "Glue back") {
                                error = repo.setVoid(session.id, false, "", user)
                            }
                        }
                    } else {
                        TextField(value = voidReason, onValueChange = { voidReason = it },
                            label = { Text("Tape note (required)") },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        PcButtonRow {
                            PcCutPrimary(text = "Snip with tape") {
                                error = repo.setVoid(session.id, true, voidReason.trim(), user)
                                if (error == null) voidReason = ""
                            }
                        }
                    }
                    PcError(error)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PcSheet {
            PcHead("WALK-IN SCRAPS")
            PcWashi(color = PcColors.WashiGreen)
            TextField(value = walkName, onValueChange = { walkName = it },
                label = { Text("Name on scrap") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = walkTime, onValueChange = { walkTime = it },
                label = { Text("Hour (HH:MM)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PcButtonRow {
                PcCutPrimary(text = "Snip walk-in ₱800") {
                    if (walkName.isBlank() || walkTime.isBlank()) {
                        error = "Both a name scrap and an hour are needed for a walk-in."
                    } else {
                        repo.addWalkIn(walkName.trim(), walkTime.trim(), user)
                        walkName = ""
                        walkTime = ""
                        error = null
                    }
                }
            }
            PcError(error)
        }
    }
}

@Composable
fun PcClients(repo: PaperCraftFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        PcTitle("Sheet 6 — Faces")
        PcSubtitle("A global paper-doll register — at most one PENDING cut-out per face. " +
            "The folded view hides faces.")
        PcGap()
        repo.clients.forEach { client ->
            val anon = repo.anonymizedIds.contains(client.id)
            val pending = repo.pendingFor(client.id)
            PcSheet {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (anon) "FACE ${client.id.uppercase()}" else client.name,
                            color = PcColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            fontFamily = PcHand)
                        Text("${client.gender} · ${client.age}" + if (anon) " · FOLDED" else "",
                            color = PcColors.Faint, fontSize = 12.sp, fontFamily = PcHand)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PcSum("OWE $pending",
                            if (pending > 1) PcColors.StampVoid else PcColors.Ink)
                        if (pending > 1) {
                            Text("OVER RULE", color = PcColors.StampVoid, fontSize = 11.sp,
                                fontWeight = FontWeight.Black, fontFamily = PcHand)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PcButtonRow {
                    if (anon) {
                        PcCutGhost(text = "Unfold") {
                            repo.anonymizedIds = repo.anonymizedIds - client.id
                            repo.audit(user, "DEANONYMIZE", client.id)
                        }
                    } else {
                        PcCutGhost(text = "Fold") {
                            repo.anonymizedIds = repo.anonymizedIds + client.id
                            repo.audit(user, "ANONYMIZE", client.id)
                            logInfo("PaperCraftProto", "folded ${client.id}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
