package com.companyb.companyapp.proto.terminalgreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// #768 — terminal-green screens part 1: home/clock/relief, sessions, clients.

@Composable
fun TgHome(repo: TerminalGreenRepo, user: TgUser, branch: TgBranch, clockedIn: Boolean, onClock: () -> Unit) {
    TgTitle("HOME // " + branch.name)
    TgRule()
    TgPanel {
        TgSection("CLOCK")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TgTag(if (clockedIn) "CLOCKED-IN" else "CLOCKED-OUT", if (clockedIn) TgColors.Phosphor else TgColors.Muted)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                TgText(user.name + " :: " + user.role, size = 13)
                TgText(
                    "drawer: " + branch.name + " (relief pay from relief drawer)",
                    size = 11,
                    color = TgColors.Muted,
                )
            }
            if (clockedIn) TgGhost("CLOCK-OUT") { onClock() } else TgButton("CLOCK-IN") { onClock() }
        }
    }
    TgPanel {
        TgSection("RELIEF DUTY")
        TgNote("relief = clocked into non-home branch. view-only until a grant; expires 04:00 Manila next day.")
        repo.reliefDuties.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TgText("▸ " + d.text, size = 12, modifier = Modifier.weight(1f))
                TgTag(d.state, TgColors.PhosphorDim)
            }
        }
    }
    TgPanel {
        TgSection("RELIEF REQUESTS")
        TgNote("outsider-initiated broadcast: one live request per requester per branch per date.")
        repo.reliefRequests.forEach { q ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TgText("▸ " + q.text, size = 12, modifier = Modifier.weight(1f))
                TgGhost("WITHDRAW") {
                    q.state = "withdrawn"
                    repo.log(user.login, "withdraw relief request " + q.id)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TgGhost("+ BROADCAST REQUEST") {
            repo.reliefRequests.add(
                TgReliefItem("q-new", "REQUEST", "broadcast: relief @TONDO-MISSION 2026-09-13", "live"),
            )
            repo.log(user.login, "broadcast relief request @TONDO-MISSION")
        }
    }
    TgPanel {
        TgSection("RELIEF INVITES")
        TgNote("branch-initiated. accept writes the day grant; branch may revoke until you clock in.")
        repo.reliefInvites.forEach { i ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TgText("▸ " + i.text + " [" + i.state + "]", size = 12, modifier = Modifier.weight(1f))
                if (i.state == "pending") {
                    TgButton("ACCEPT") {
                        i.state = "accepted"
                        repo.log(user.login, "accept relief invite " + i.id)
                    }
                    Spacer(Modifier.width(6.dp))
                    TgGhost("DECLINE") {
                        i.state = "declined"
                        repo.log(user.login, "decline relief invite " + i.id)
                    }
                }
            }
        }
    }
}

@Composable
fun TgSessions(repo: TerminalGreenRepo, user: TgUser, branch: TgBranch) {
    var showCreate by remember { mutableStateOf(false) }
    var voidTarget by remember { mutableStateOf<TgSession?>(null) }
    TgTitle("SESSIONS // " + branch.name)
    TgNote("PENDING -> COMPLETED / NO_SHOW / CANCELLED. walk-in: COMPLETED only.")
    TgRule()
    TgButton("+ LOG SESSION") { showCreate = true }
    Spacer(Modifier.height(8.dp))
    if (showCreate) {
        TgCreateSession(repo, user, branch, onDone = { showCreate = false })
    }
    if (voidTarget != null) {
        TgVoidDialog(repo, user, voidTarget!!, onDone = { voidTarget = null })
    }
    val list = repo.sessions.filter { it.branchId == branch.id }
    if (list.isEmpty()) {
        TgPanel { TgNote("no sessions on this branch day. log one with + LOG SESSION.") }
    }
    list.forEach { s ->
        TgPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TgText(s.time + " " + s.id, size = 13, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TgTag(s.status.name, TgStatusColor(s.status))
                if (s.walkIn) {
                    Spacer(Modifier.width(6.dp))
                    TgTag("WALK-IN", TgColors.Cyan)
                }
                if (s.voided) {
                    Spacer(Modifier.width(6.dp))
                    TgTag("VOID", TgColors.Red)
                }
            }
            TgText("client: " + s.clientName + " :: " + s.kind + " :: P" + s.price, size = 12)
            TgText("by: " + s.practitioners, size = 12, color = TgColors.Muted)
            if (s.voided) {
                TgText("void reason: " + s.voidReason, size = 12, color = TgColors.Red)
                TgGhost("UNVOID") {
                    s.voided = false
                    repo.log(user.login, "unvoid " + s.id)
                }
            } else {
                if (s.status == TgSessionStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TgButton("COMPLETE") {
                            s.status = TgSessionStatus.COMPLETED
                            repo.log(user.login, "complete " + s.id)
                        }
                        if (!s.walkIn) {
                            TgGhost("NO-SHOW") {
                                s.status = TgSessionStatus.NO_SHOW
                                repo.log(user.login, "no-show " + s.id)
                            }
                            TgGhost("CANCEL") {
                                s.status = TgSessionStatus.CANCELLED
                                repo.log(user.login, "cancel " + s.id)
                            }
                        } else {
                            TgNote("walk-in rule: NO_SHOW / CANCELLED unavailable")
                        }
                    }
                }
                TgDanger("VOID…") { voidTarget = s }
            }
        }
    }
}

@Composable
private fun TgCreateSession(repo: TerminalGreenRepo, user: TgUser, branch: TgBranch, onDone: () -> Unit) {
    var client by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("16:00") }
    var walkIn by remember { mutableStateOf(false) }
    TgPanel {
        TgSection("NEW SESSION")
        TgField("client name", client) { client = it }
        TgField("time hh:mm", time) { time = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TgGhost(if (walkIn) "[x] WALK-IN" else "[ ] WALK-IN") { walkIn = !walkIn }
            Spacer(Modifier.width(8.dp))
            TgNote("type auto-assigned from client history (fake: Initial).")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TgButton("WRITE PENDING") {
                val id = "s-" + repo.sessionCounter.toString().padStart(2, '0')
                repo.sessionCounter += 1
                repo.sessions.add(
                    TgSession(
                        id, time.ifBlank { "16:00" }, client.ifBlank { "UNNAMED" }.uppercase(),
                        branch.id, "Initial", walkIn, TgSessionStatus.PENDING, 1200, user.name.uppercase(),
                    ),
                )
                repo.log(user.login, "log session $id PENDING")
                onDone()
            }
            TgGhost("ABORT") { onDone() }
        }
    }
}

@Composable
private fun TgVoidDialog(repo: TerminalGreenRepo, user: TgUser, s: TgSession, onDone: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    TgPanel {
        TgSection("VOID " + s.id)
        TgNote("void excludes the session from finance but preserves the record. reason required.")
        TgField("reason", reason) { reason = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TgDanger("CONFIRM VOID", enabled = reason.isNotBlank()) {
                s.voided = true
                s.voidReason = reason
                repo.log(user.login, "void " + s.id + " reason=" + reason)
                onDone()
            }
            TgGhost("ABORT") { onDone() }
        }
    }
}

@Composable
fun TgClients(repo: TerminalGreenRepo, user: TgUser) {
    var showAnon by remember { mutableStateOf(false) }
    TgTitle("CLIENTS // GLOBAL")
    TgNote("global person record. at most one PENDING session each. anonymized keeps gender+age.")
    TgRule()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TgGhost(if (showAnon) "[x] ANONYMIZED VIEW" else "[ ] ANONYMIZED VIEW") { showAnon = !showAnon }
        Spacer(Modifier.width(8.dp))
        TgNote("toggle redacts PII like a soft-delete preview.")
    }
    Spacer(Modifier.height(8.dp))
    repo.clients.forEach { c ->
        TgPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TgText("▸ " + c.name, size = 13, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (c.anonymized) TgTag("ANON", TgColors.Cyan)
                if (c.pendingCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    TgTag("PENDING:" + c.pendingCount, TgColors.Amber)
                }
            }
            if (showAnon || c.anonymized) {
                TgText(
                    "contact: --redacted-- :: gender=" + c.gender + " age=" + c.age,
                    size = 12,
                    color = TgColors.Muted,
                )
            } else {
                TgText("contact: " + c.contact, size = 12, color = TgColors.Muted)
            }
            if (c.pendingCount >= 1) {
                TgNote("rule: at most one PENDING — new session blocked until it clears.")
            } else {
                TgLink("+ log session") {
                    repo.log(user.login, "open session create for " + c.id)
                }
            }
        }
    }
}

@Composable
fun TgField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { TgText(label, size = 12, color = TgColors.Muted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedTextColor = TgColors.Phosphor,
            unfocusedTextColor = TgColors.Phosphor,
            focusedContainerColor = TgColors.Tube,
            unfocusedContainerColor = TgColors.Tube,
            focusedIndicatorColor = TgColors.Phosphor,
            unfocusedIndicatorColor = TgColors.PanelEdge,
        ),
    )
    Spacer(Modifier.height(4.dp))
}
