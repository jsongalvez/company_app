package com.companyb.companyapp.proto.searchonly

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

// #855 — search-only detail stage. Every hit opens here; every mutation
// writes the fake repo + audit trail and lands in recents.

@Composable
fun SoStage(repo: SearchOnlyFakeRepo, sel: SoSel, query: String, onOpen: (SoSel) -> Unit) {
    when (sel.kind) {
        "login" -> SoLoginDetail(repo, sel.id, onOpen)
        "logout" -> SoLogoutDetail(repo, onOpen)
        "clockin", "clockout" -> SoClockDetail(repo, sel.kind == "clockin", onOpen)
        "home" -> SoHomeDetail(repo, onOpen)
        "session" -> SoSessionDetail(repo, sel.id, onOpen)
        "walkin" -> SoWalkinDetail(repo, onOpen)
        "client" -> SoClientDetail(repo, sel.id, onOpen)
        "branches" -> SoBranchesDetail(repo, sel.id, onOpen)
        "finance" -> SoFinanceDetail(repo, sel.id, query, onOpen)
        "team" -> SoTeamDetail(repo, sel.id, onOpen)
        "notices" -> SoNoticesDetail(repo, onOpen)
        "audit" -> SoAuditDetail(repo, query)
        "profile" -> SoProfileDetail(repo, onOpen)
        "onboarding" -> SoOnboardingDetail(repo, onOpen)
        else -> SoCard { SoBody("Unknown target ${sel.kind}. Clear the box and search again.") }
    }
}

private fun touch(repo: SearchOnlyFakeRepo, sel: SoSel, onOpen: (SoSel) -> Unit) {
    repo.touch(sel)
    onOpen(sel)
}

@Composable
private fun SoLoginDetail(repo: SearchOnlyFakeRepo, id: String?, onOpen: (SoSel) -> Unit) {
    val u = repo.users.firstOrNull { it.id == id } ?: repo.users.first()
    SoCard {
        SoH1("Login as ${u.name}")
        SoMono("${u.id} · ${u.role.label} · home ${u.homeBranch}")
        Spacer(Modifier.height(6.dp))
        if (u.role == SoRole.ONBOARDING) {
            SoNote("ONBOARDING holds zero capabilities — even with a branch assignment the role bundle is empty. You will land locked until a MANAGE_USERS grant.")
        }
        SoActionRow {
            SoPrimary("Sign in") {
                repo.login(u.id)
                val next = if (u.role == SoRole.ONBOARDING) SoSel("onboarding") else SoSel("home")
                touch(repo, next, onOpen)
            }
        }
    }
}

@Composable
private fun SoLogoutDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    SoCard {
        SoH1("Logout")
        SoBody("Sign out ${repo.actorName()} and return to the login box. The audit trail keeps the session.")
        SoActionRow {
            SoPrimary("Logout now") {
                repo.logout()
                onOpen(SoSel("login", "U-ANN"))
            }
            Spacer(Modifier.width(8.dp))
            SoGhost("Stay") { touch(repo, SoSel("home"), onOpen) }
        }
    }
}

@Composable
private fun SoClockDetail(repo: SearchOnlyFakeRepo, inNow: Boolean, onOpen: (SoSel) -> Unit) {
    SoCard {
        SoH1(if (inNow) "Clock in" else "Clock out")
        SoMono("${repo.actorName()} · ${repo.currentBranch.name} · ${repo.dayState.label} day")
        SoActionRow {
            SoPrimary(if (inNow) "Clock in here" else "Clock out") {
                repo.setClock(inNow)
                touch(repo, SoSel("home"), onOpen)
            }
        }
    }
}

@Composable
private fun SoHomeDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    SoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoH1("Clock & relief")
            Spacer(Modifier.width(10.dp))
            SoPill(if (repo.clockedIn) "● clocked in" else "○ clocked out", if (repo.clockedIn) SoTone.GREEN else SoTone.GREY)
        }
        SoMono("${repo.actorName()} · ${repo.currentBranch.name}")
        Spacer(Modifier.height(8.dp))
        SoActionRow {
            if (!repo.clockedIn) SoPrimary("Clock in") { repo.setClock(true) }
            else SoGhost("Clock out") { repo.setClock(false) }
            Spacer(Modifier.width(8.dp))
            SoGhost("Ask ${repo.currentBranch.name} for relief") { repo.newAsk(repo.currentBranch.name) }
        }
    }
    SoSection("RELIEF INVITES", "branch-initiated · one future day")
    repo.invites.forEach { i ->
        SoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(i.branch, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SoColors.Ink)
                    SoMono("${i.day} · ${i.state}")
                }
                SoPill(i.state, when (i.state) {
                    "ACCEPTED" -> SoTone.GREEN
                    "DECLINED" -> SoTone.RED
                    else -> SoTone.AMBER
                })
            }
            if (i.state == "PENDING") {
                SoActionRow {
                    SoPrimary("Accept") { repo.acceptInvite(i) }
                    Spacer(Modifier.width(8.dp))
                    SoGhost("Decline") { repo.declineInvite(i) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    SoSection("COVER ASKS", "outsider-initiated · broadcast to the branch")
    repo.asks.forEach { q ->
        SoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${q.branch} · ${q.day}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SoColors.Ink)
                    SoMono(if (q.mine) "mine · ${q.state}" else "from branch · ${q.state}")
                }
                SoPill(q.state, when (q.state) {
                    "GRANTED" -> SoTone.GREEN
                    "DENIED", "WITHDRAWN" -> SoTone.RED
                    else -> SoTone.AMBER
                })
            }
            if (q.state == "PENDING") {
                SoActionRow {
                    if (!q.mine) {
                        SoPrimary("Grant") { repo.grantAsk(q) }
                        Spacer(Modifier.width(8.dp))
                        SoGhost("Deny") { repo.denyAsk(q) }
                    } else {
                        SoGhost("Withdraw mine") { repo.withdrawAsk(q) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    SoNote("Relief duty starts view-only; a grant writes edit access for the day. Grants expire at the 04:00 Asia/Manila boundary. Paid from the relief branch drawer.")
    Spacer(Modifier.height(8.dp))
    SoChipRow(listOf("s-101" to SoSel("session", "S-101"), "finance" to SoSel("finance"))) { touch(repo, it, onOpen) }
}

@Composable
private fun SoSessionDetail(repo: SearchOnlyFakeRepo, id: String?, onOpen: (SoSel) -> Unit) {
    val s = repo.sessions.firstOrNull { it.id == id }
    if (s == null) {
        SoCard { SoBody("Session $id is gone. Search again.") }
        return
    }
    var reason by remember(s.id) { mutableStateOf("") }
    var denied by remember(s.id) { mutableStateOf<String?>(null) }
    SoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SoH1("${s.id} · ${s.client}")
                SoMono("${s.service} · ${s.branch} · ${s.practitioner} · ${soPeso(s.amount)}${if (s.walkIn) " · ⚑ walk-in" else ""}")
            }
            SoPill(s.status.label, when (s.status) {
                SoSessionStatus.PENDING -> SoTone.LIME
                SoSessionStatus.COMPLETED -> SoTone.GREEN
                SoSessionStatus.NO_SHOW -> SoTone.AMBER
                SoSessionStatus.CANCELLED -> SoTone.GREY
            })
            if (s.voided) {
                Spacer(Modifier.width(6.dp))
                SoPill("VOID", SoTone.RED)
            }
        }
        if (s.walkIn) {
            Spacer(Modifier.height(8.dp))
            SoNote("House rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED.")
        }
        if (s.voided) {
            Spacer(Modifier.height(8.dp))
            SoNote("Voided — excluded from financials, record kept. Reason: ${s.voidReason ?: "—"}.")
        }
        denied?.let {
            Spacer(Modifier.height(8.dp))
            SoNote(it)
        }
        if (s.status == SoSessionStatus.PENDING && !s.voided) {
            SoActionRow {
                SoPrimary("Complete") { repo.transitionSession(s, SoSessionStatus.COMPLETED) }
                Spacer(Modifier.width(8.dp))
                SoGhost("No-show") {
                    if (!repo.transitionSession(s, SoSessionStatus.NO_SHOW)) denied = "Blocked: walk-in sessions cannot be NO_SHOW — house rule."
                }
                Spacer(Modifier.width(8.dp))
                SoGhost("Cancel") {
                    if (!repo.transitionSession(s, SoSessionStatus.CANCELLED)) denied = "Blocked: walk-in sessions cannot be CANCELLED — house rule."
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Void reason", fontSize = 12.sp, color = SoColors.Dim)
        TextField(value = reason, onValueChange = { reason = it }, singleLine = true, placeholder = { Text("reason…", fontSize = 12.sp) })
        SoActionRow {
            if (!s.voided) SoGhost("Void with reason") { repo.voidSession(s, reason); reason = "" }
            else SoGhost("Unvoid") { repo.unvoidSession(s, reason); reason = "" }
            Spacer(Modifier.width(8.dp))
            SoGhost("Open client") { touch(repo, SoSel("client", s.clientId), onOpen) }
        }
    }
}

@Composable
private fun SoWalkinDetail(repo: SearchOnlyFakeRepo, onOpen: (SoSel) -> Unit) {
    var name by remember { mutableStateOf("") }
    var blocked by remember { mutableStateOf<String?>(null) }
    SoCard {
        SoH1("Book walk-in session")
        SoBody("Cash client at ${repo.currentBranch.name}. Walk-ins skip the at-most-one-PENDING check and the NO_SHOW / CANCELLED rule is noted on the session.")
        Spacer(Modifier.height(8.dp))
        TextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("client name…", fontSize = 13.sp) })
        blocked?.let {
            Spacer(Modifier.height(8.dp))
            SoNote(it)
        }
        SoActionRow {
            SoPrimary("Book as walk-in") {
                val clean = name.trim().ifBlank { "Walk-in Guest" }
                val existing = repo.clients.firstOrNull { it.name.equals(clean, ignoreCase = true) }
                val c = existing ?: SoClient("C-%02d".format(repo.clients.size + 1), clean, "F", 30).also { repo.clients.add(it) }
                val s = repo.bookSession(c.id, "Walk-in Rehab 30m", walkIn = true)
                if (s == null) {
                    blocked = "Blocked: ${c.name} already holds a PENDING session — at most one per client."
                } else {
                    touch(repo, SoSel("session", s.id), onOpen)
                }
            }
        }
    }
}

@Composable
private fun SoClientDetail(repo: SearchOnlyFakeRepo, id: String?, onOpen: (SoSel) -> Unit) {
    val c = repo.clients.firstOrNull { it.id == id }
    if (c == null) {
        SoCard { SoBody("Client $id is gone. Search again.") }
        return
    }
    val pend = repo.pendingCount(c.id)
    SoCard {
        SoH1(if (c.anonymized) "${c.id} · anonymized" else c.name)
        SoMono("${c.id} · ${c.gender} · ${c.age} · $pend PENDING · global record — shared across all branches")
        Spacer(Modifier.height(8.dp))
        SoNote("At most one PENDING session per client. Anonymize keeps gender + age for reporting, nulls the rest.")
        SoActionRow {
            if (!c.anonymized) SoGhost("Anonymize") { repo.anonymize(c) }
            else SoGhost("Reveal (fake undo)") { repo.reveal(c) }
            Spacer(Modifier.width(8.dp))
            SoPrimary("Book session") {
                val s = repo.bookSession(c.id, "PT Rehab 45m", walkIn = false)
                if (s != null) touch(repo, SoSel("session", s.id), onOpen)
            }
        }
        if (pend >= 1) {
            Spacer(Modifier.height(8.dp))
            SoNote("Booking is blocked while a PENDING session exists — book a walk-in instead, or finish the pending one first.")
        }
    }
    SoSection("SESSIONS FOR THIS CLIENT")
    repo.sessions.filter { it.clientId == c.id }.forEach { s ->
        SoHitRow(
            if (s.walkIn) "⚑" else "◉", "${s.id} · ${s.service}", "${s.status.label}${if (s.voided) " · VOID" else ""} · ${soPeso(s.amount)}",
            SoTone.CYAN, hot = false, onClick = { touch(repo, SoSel("session", s.id), onOpen) },
        )
    }
}

@Composable
private fun SoBranchesDetail(repo: SearchOnlyFakeRepo, id: String?, onOpen: (SoSel) -> Unit) {
    SoCard {
        SoH1("Branches")
        SoMono("Day boundary 04:00 Asia/Manila — a branch day stays editable until 04:00 the next morning, then turns PAST lazily.")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoPill(repo.dayState.label, when (repo.dayState) {
                SoDayState.OPEN -> SoTone.GREEN
                SoDayState.PAST -> SoTone.AMBER
                SoDayState.REMITTED -> SoTone.VIOLET
            })
            Spacer(Modifier.width(8.dp))
            SoGhost("Advance day") { repo.cycleDay() }
        }
    }
    Spacer(Modifier.height(8.dp))
    repo.branches.forEach { b ->
        val current = b.id == repo.branchId
        SoHitRow(
            "◈", b.name, "${b.kind} · ${b.dayDate}${if (current) " · current" else ""}",
            SoTone.CYAN, hot = current,
            onClick = { repo.switchBranch(b.id); touch(repo, SoSel("home"), onOpen) },
        )
    }
}

@Composable
fun SoChipRow(chips: List<Pair<String, SoSel>>, onPick: (SoSel) -> Unit) {
    Row {
        chips.forEach {
            SoChip(it.first) { onPick(it.second) }
            Spacer(Modifier.width(8.dp))
        }
    }
}
