package com.companyb.companyapp.proto.ledgerbook

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

// #835 — ledger-book front folios: preface, login, branches, counter, day, sessions.

@Composable
fun LbOnboarding(repo: LedgerBookFakeRepo, onNext: () -> Unit) {
    var blocked by remember { mutableStateOf<String?>(null) }
    Column {
        LbChapter("Preface")
        LbEpigraph("A locked ONBOARDING hand — no capabilities until a role is inked in.")
        LbGap()
        LbPage {
            LbHead("NEW HAND — J. RAMOS")
            LbRuled()
            LbEntry("ROLE", LbRole.ONBOARDING.name)
            LbEntry("CAPABILITIES", "nil")
            LbRuled()
            LbButtonRow {
                LbPencil(text = "Attempt the Sessions chapter") {
                    blocked = "The ONBOARDING hand holds no capabilities — ask a MANAGER to inscribe a role."
                }
            }
            Spacer(Modifier.height(8.dp))
            LbButtonRow {
                LbQuill(text = "Inscribe Practitioner") {
                    repo.login("u-new")
                    val index = repo.users.indexOfFirst { it.id == "u-new" }
                    repo.users[index] = repo.users[index].copy(role = LbRole.PRACTITIONER)
                    repo.audit("D. Lim", "ROLE_GRANT", "J. Ramos → PRACTITIONER")
                    logInfo("LedgerBookProto", "preface inscribed practitioner")
                    blocked = null
                    onNext()
                }
            }
            LbErratum(blocked)
        }
    }
}

@Composable
fun LbLogin(repo: LedgerBookFakeRepo, onNext: () -> Unit) {
    Column {
        LbChapter("Chapter I — Signatures")
        LbEpigraph("A false directory of five hands — touch a name to sign the book.")
        LbGap()
        repo.users.forEach { user ->
            LbPage {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = LbColors.Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                        Text("${user.role.name} · ${user.id}", color = LbColors.Faint,
                            fontSize = 12.sp, fontFamily = LbSerif)
                    }
                    LbPencil(text = "Sign") {
                        repo.login(user.id)
                        repo.audit(user.name, "LOGIN", user.name)
                        logInfo("LedgerBookProto", "signed ${user.id}")
                        onNext()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun LbBranchSelect(repo: LedgerBookFakeRepo, onNext: () -> Unit) {
    Column {
        LbChapter("Chapter II — Houses")
        LbEpigraph("One house counter open at a time — turning houses re-heads the ledger.")
        LbGap()
        repo.branches.forEach { branch ->
            val active = branch.id == repo.currentBranchId
            LbPage {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.name, color = LbColors.Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                        Text(branch.kind.name, color = LbColors.Faint, fontSize = 12.sp,
                            fontFamily = LbSerif)
                    }
                    if (active) {
                        LbStamp(text = "KEPT HERE", color = LbColors.StampOpen)
                    } else {
                        LbPencil(text = "Keep here") {
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
fun LbCounter(repo: LedgerBookFakeRepo, go: (LbScreen) -> Unit) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        LbChapter("Chapter III — The Counter")
        LbEpigraph("Clock the duty slate — relief duty, invites, and requests.")
        LbGap()
        LbPage {
            LbHead("DUTY SLATE — ${repo.currentBranch.name.uppercase()}")
            LbRuled()
            LbEntry("HAND", user)
            LbEntry(
                "STATE",
                if (repo.clockedIn) "PEN DOWN" else "PEN UP",
                if (repo.clockedIn) LbColors.StampOpen else LbColors.Faint,
            )
            LbRuled()
            LbButtonRow {
                if (repo.clockedIn) {
                    LbPencil(text = "Pen up") {
                        repo.clockedIn = false
                        repo.audit(user, "CLOCK_OUT", repo.currentBranch.name)
                    }
                } else {
                    LbQuill(text = "Pen down") {
                        repo.clockedIn = true
                        repo.audit(user, "CLOCK_IN",
                            "${repo.currentBranch.name} / ${repo.selectedDay.dateLabel}")
                    }
                }
                LbPencil(text = "Day folio") { go(LbScreen.DAY) }
                LbPencil(text = "Remittance") { go(LbScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        LbPage {
            LbHead("RELIEF INVITES")
            LbRuled()
            if (repo.invites.isEmpty()) {
                LbMarginalia("No open invites.")
            }
            repo.invites.forEach { invite ->
                Text("${invite.fromUser} begs cover of ${invite.branchName} on ${invite.day}",
                    color = LbColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    fontFamily = LbSerif)
                val verdict = invite.accepted
                if (verdict == null) {
                    Spacer(Modifier.height(6.dp))
                    LbButtonRow {
                        LbQuill(text = "Accept") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = true)
                            repo.audit(user, "RELIEF_ACCEPT", invite.branchName)
                        }
                        LbPencil(text = "Decline") {
                            repo.invites[repo.invites.indexOfFirst { it.id == invite.id }] =
                                invite.copy(accepted = false)
                            repo.audit(user, "RELIEF_DECLINE", invite.branchName)
                        }
                    }
                } else {
                    LbMarginalia(if (verdict) "Accepted — ruled into the duty roster." else "Declined.")
                }
                LbRuled()
            }
        }
        Spacer(Modifier.height(10.dp))
        LbPage {
            LbHead("RELIEF REQUESTS")
            LbRuled()
            repo.requests.forEach { request ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(request.branchName, color = LbColors.Ink, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, fontFamily = LbSerif)
                        LbMarginalia("${request.day} · " + if (request.mine) "mine" else "cover wanted" +
                            (request.decided?.let { " · $it" } ?: ""))
                    }
                    if (request.mine && request.decided == null) {
                        LbPencil(text = "Strike") {
                            val i = repo.requests.indexOfFirst { it.id == request.id }
                            repo.requests[i] = request.copy(decided = "WITHDRAWN")
                            repo.audit(user, "RELIEF_WITHDRAW", request.branchName)
                        }
                    } else if (!request.mine && request.decided == null) {
                        LbPencil(text = "Cover") {
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
fun LbDayFolio(repo: LedgerBookFakeRepo, go: (LbScreen) -> Unit) {
    val day = repo.selectedDay
    val lines = repo.sessionsFor(day.id)
    Column {
        LbChapter("Chapter IV — Day Folio")
        LbEpigraph("${day.dow} ${day.dateLabel} · ${repo.currentBranch.name} · ${day.status.name}.")
        LbGap()
        LbPage {
            Row {
                LbTally("ENTRIES", lines.size.toString())
                LbTally("DONE", lines.count { it.status == LbSessionStatus.COMPLETED }.toString(),
                    LbColors.StampOpen)
                LbTally("AWAIT", lines.count { it.status == LbSessionStatus.PENDING }.toString())
                LbTally("SUM", "₱${repo.dayTotal(day.id)}", LbColors.StampOpen)
            }
            LbRuled()
            LbButtonRow {
                LbPencil(text = "Rule sessions") { go(LbScreen.SESSIONS) }
                LbPencil(text = "Stamp remittance") { go(LbScreen.FINANCE) }
            }
        }
        Spacer(Modifier.height(10.dp))
        lines.forEach { session ->
            LbPage {
                LbSessionScript(session = session)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (lines.isEmpty()) {
            LbPage { LbMarginalia("No entries ruled yet — pen a walk-in from Sessions.") }
        }
    }
}

@Composable
fun LbSessionScript(session: LbSession) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${session.time}  ${session.clientName}", color = LbColors.Ink,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = LbSerif)
            Text("${session.id} · ${session.type} · ${session.practitioner}" +
                if (session.walkIn) " · WALK-IN" else "",
                color = LbColors.Faint, fontSize = 12.sp, fontFamily = LbSerif)
        }
        Column(horizontalAlignment = Alignment.End) {
            LbSum("₱${session.price}")
            Text(
                (if (session.voided) "VOID · " else "") + session.status.name,
                color = if (session.voided) LbColors.StampVoid else LbColors.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = LbSerif,
            )
        }
    }
}

@Composable
fun LbSessions(repo: LedgerBookFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    var filter by remember { mutableStateOf<LbSessionStatus?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkTime by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lines = repo.sessionsFor(repo.selectedDayId).filter { filter == null || it.status == filter }
    Column {
        LbChapter("Chapter V — Sessions")
        LbEpigraph("${repo.selectedDay.dow} ${repo.selectedDay.dateLabel} ruled lines. " +
            "Walk-in entries refuse NO_SHOW and CANCELLED.")
        LbGap()
        LbButtonRow {
            LbTab(text = "WHOLE", selected = filter == null) { filter = null }
            LbSessionStatus.entries.forEach { status ->
                LbTab(text = status.name, selected = filter == status) { filter = status }
            }
        }
        Spacer(Modifier.height(6.dp))
        lines.forEach { session ->
            LbPage {
                LbSessionScript(session = session)
                if (session.voidReason != null) {
                    LbMarginalia("Void writ: ${session.voidReason}")
                }
                Spacer(Modifier.height(6.dp))
                LbButtonRow {
                    LbPencil(text = if (openId == session.id) "Shut" else "Amend") {
                        openId = if (openId == session.id) null else session.id
                        error = null
                    }
                }
                if (openId == session.id) {
                    LbRuled()
                    LbHead("AMEND STANDING")
                    Spacer(Modifier.height(6.dp))
                    LbButtonRow {
                        LbSessionStatus.entries.forEach { status ->
                            LbTab(text = status.name, selected = session.status == status) {
                                error = repo.setStatus(session.id, status, user)
                            }
                        }
                    }
                    LbRuled()
                    LbHead(if (session.voided) "UNVOID ENTRY" else "VOID ENTRY")
                    Spacer(Modifier.height(6.dp))
                    if (session.voided) {
                        LbButtonRow {
                            LbPencil(text = "Unvoid") {
                                error = repo.setVoid(session.id, false, "", user)
                            }
                        }
                    } else {
                        TextField(value = voidReason, onValueChange = { voidReason = it },
                            label = { Text("Writ (required)") },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        LbButtonRow {
                            LbQuill(text = "Void with writ") {
                                error = repo.setVoid(session.id, true, voidReason.trim(), user)
                                if (error == null) voidReason = ""
                            }
                        }
                    }
                    LbErratum(error)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        LbPage {
            LbHead("WALK-IN MARGIN")
            LbRuled()
            TextField(value = walkName, onValueChange = { walkName = it },
                label = { Text("Hand of client") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = walkTime, onValueChange = { walkTime = it },
                label = { Text("Hour (HH:MM)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LbButtonRow {
                LbQuill(text = "Rule walk-in ₱800") {
                    if (walkName.isBlank() || walkTime.isBlank()) {
                        error = "Both a hand and an hour are required for a walk-in entry."
                    } else {
                        repo.addWalkIn(walkName.trim(), walkTime.trim(), user)
                        walkName = ""
                        walkTime = ""
                        error = null
                    }
                }
            }
            LbErratum(error)
        }
    }
}

@Composable
fun LbClients(repo: LedgerBookFakeRepo) {
    val user = repo.currentUser?.name ?: "guest"
    Column {
        LbChapter("Chapter VI — Clientele")
        LbEpigraph("A global register — at most one PENDING entry per hand. The veiled view masks faces.")
        LbGap()
        repo.clients.forEach { client ->
            val anon = repo.anonymizedIds.contains(client.id)
            val pending = repo.pendingFor(client.id)
            LbPage {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (anon) "HAND ${client.id.uppercase()}" else client.name,
                            color = LbColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            fontFamily = LbSerif)
                        Text("${client.gender} · ${client.age}" + if (anon) " · VEILED" else "",
                            color = LbColors.Faint, fontSize = 12.sp, fontFamily = LbSerif)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        LbSum("OWE $pending",
                            if (pending > 1) LbColors.StampVoid else LbColors.Ink)
                        if (pending > 1) {
                            Text("OVER RULE", color = LbColors.StampVoid, fontSize = 11.sp,
                                fontWeight = FontWeight.Black, fontFamily = LbSerif)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                LbButtonRow {
                    if (anon) {
                        LbPencil(text = "Unveil") {
                            repo.anonymizedIds = repo.anonymizedIds - client.id
                            repo.audit(user, "DEANONYMIZE", client.id)
                        }
                    } else {
                        LbPencil(text = "Veil") {
                            repo.anonymizedIds = repo.anonymizedIds + client.id
                            repo.audit(user, "ANONYMIZE", client.id)
                            logInfo("LedgerBookProto", "veiled ${client.id}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
