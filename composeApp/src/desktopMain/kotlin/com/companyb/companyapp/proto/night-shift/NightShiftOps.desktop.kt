package com.companyb.companyapp.proto.nightshift

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

// #780 — night-shift home, sessions, clients. Clock numerals obey the dimmer;
// relief covers duty, invites and one-live broadcast requests.

@Composable
fun NsHome(repo: NightShiftRepo, user: NsUser, branch: NsBranch) {
    NsTitle("tonight")
    NsNote(branch.name + " · graveyard board for " + user.name)
    NsRule()
    NsPanel {
        NsText("CLOCK", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NsNumeral(if (repo.clockedIn) repo.clockInAt + " →" else "off duty", repo.dim, 40)
            Spacer(Modifier.width(12.dp))
            if (repo.clockedIn) {
                NsGhost("CLOCK OUT") {
                    repo.clockedIn = false
                    repo.log(user.login, "clock out at " + branch.name)
                }
            } else {
                NsButton("CLOCK IN") {
                    repo.clockedIn = true
                    repo.clockInAt = "22:00"
                    repo.log(user.login, "clock in at " + branch.name)
                }
            }
        }
        NsNote("clock state is per crew member and survives screen switches.")
    }
    NsPanel {
        NsText("RELIEF", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
        repo.relief.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NsText(item.kind + " · " + item.text, size = 13)
                    NsNote("state " + item.state)
                }
                when (item.kind) {
                    "INVITE" -> if (item.state == "OPEN") {
                        NsGhost("ACCEPT") {
                            item.state = "ACCEPTED"
                            repo.log(user.login, "accept relief invite " + item.id)
                        }
                        Spacer(Modifier.width(6.dp))
                        NsGhost("DECLINE") {
                            item.state = "DECLINED"
                            repo.log(user.login, "decline relief invite " + item.id)
                        }
                    } else NsTag(item.state, nsStatusColor("COMPLETED"))
                    "REQUEST" -> if (item.state == "LIVE") {
                        NsGhost("WITHDRAW") {
                            item.state = "WITHDRAWN"
                            repo.log(user.login, "withdraw relief request " + item.id)
                        }
                    } else NsTag(item.state, NsColors.Taupe)
                    else -> if (item.state == "OPEN") {
                        NsGhost("TAKE") {
                            item.state = "TAKEN"
                            repo.log(user.login, "take relief duty " + item.id)
                        }
                    } else NsTag(item.state, nsStatusColor("COMPLETED"))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        NsNote("broadcast requests: one live per date. withdraw before posting another.")
        Spacer(Modifier.height(6.dp))
        NsGhost("BROADCAST COVER REQUEST") {
            val live = repo.relief.any { it.kind == "REQUEST" && it.state == "LIVE" }
            if (!live) {
                repo.relief.add(NsReliefItem("q-live", "REQUEST", "Cover ask from " + branch.name, "LIVE"))
                repo.log(user.login, "broadcast relief request at " + branch.name)
            }
        }
    }
}

@Composable
fun NsSessions(repo: NightShiftRepo, user: NsUser, branch: NsBranch) {
    var showCreate by remember { mutableStateOf(false) }
    NsTitle("sessions")
    NsNote(branch.name + " · PENDING → COMPLETED / NO_SHOW / CANCELLED")
    NsRule()
    val list = repo.sessions.filter { it.branchId == branch.id }
    if (list.isEmpty()) NsNote("no sessions on this branch tonight.")
    list.forEach { s ->
        NsPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NsText(s.time + "  " + s.clientName, size = 14, weight = FontWeight.Bold)
                    NsText(
                        s.kind + (if (s.walkIn) " · walk-in" else "") + " · ₱" + s.price + " · " + s.practitioners,
                        size = 12,
                        color = NsColors.Taupe,
                    )
                    if (s.voided) NsText("voided: " + s.voidReason, size = 12, color = NsColors.Rose)
                }
                NsTag(
                    if (s.voided) "VOID" else s.status.name,
                    if (s.voided) NsColors.Rose else nsStatusColor(s.status.name),
                )
            }
            if (!s.voided && s.status == NsSessionStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                NsRowButtons {
                    NsGhost("COMPLETE") {
                        s.status = NsSessionStatus.COMPLETED
                        repo.log(user.login, "complete session " + s.id)
                    }
                    if (!s.walkIn) {
                        NsGhost("NO-SHOW") {
                            s.status = NsSessionStatus.NO_SHOW
                            repo.log(user.login, "mark no-show " + s.id)
                        }
                        NsGhost("CANCEL") {
                            s.status = NsSessionStatus.CANCELLED
                            repo.log(user.login, "cancel session " + s.id)
                        }
                    }
                }
                if (s.walkIn) {
                    NsNote("walk-ins only COMPLETE here: nobody can no-show or cancel a seat that was never booked.")
                }
            }
            Spacer(Modifier.height(8.dp))
            NsVoidRow(repo, user, s)
        }
    }
    Spacer(Modifier.height(10.dp))
    if (showCreate) NsSessionCreate(repo, user, branch) { showCreate = false }
    else NsButton("+ LOG SESSION") { showCreate = true }
}

@Composable
private fun NsVoidRow(repo: NightShiftRepo, user: NsUser, s: NsSession) {
    var reason by remember { mutableStateOf("") }
    if (s.voided) {
        NsGhost("UNVOID") {
            s.voided = false
            s.voidReason = ""
            repo.log(user.login, "unvoid session " + s.id)
        }
    } else {
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { NsText("void reason (required)", size = 12, color = NsColors.Taupe) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = NsColors.Glow,
                unfocusedTextColor = NsColors.Glow,
                focusedContainerColor = NsColors.Panel,
                unfocusedContainerColor = NsColors.Panel,
                focusedIndicatorColor = NsColors.Rose,
                unfocusedIndicatorColor = NsColors.Edge,
            ),
        )
        Spacer(Modifier.height(6.dp))
        NsGhost("VOID WITH REASON") {
            if (reason.isNotBlank()) {
                s.voided = true
                s.voidReason = reason.trim()
                repo.log(user.login, "void session " + s.id + ": " + s.voidReason)
            }
        }
    }
}

@Composable
private fun NsSessionCreate(
    repo: NightShiftRepo,
    user: NsUser,
    branch: NsBranch,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var walkIn by remember { mutableStateOf(false) }
    NsPanel {
        NsText("LOG SESSION → PENDING", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { NsText("client name", size = 12, color = NsColors.Taupe) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = NsColors.Glow,
                unfocusedTextColor = NsColors.Glow,
                focusedContainerColor = NsColors.Panel,
                unfocusedContainerColor = NsColors.Panel,
                focusedIndicatorColor = NsColors.Ember,
                unfocusedIndicatorColor = NsColors.Edge,
            ),
        )
        Spacer(Modifier.height(6.dp))
        NsRowButtons {
            NsGhost(if (walkIn) "WALK-IN ✓" else "WALK-IN") { walkIn = !walkIn }
            NsGhost("BOOKED" + if (!walkIn) " ✓" else "") { walkIn = false }
        }
        NsNote("type auto-note: " + if (walkIn) "Walk-in, COMPLETE-only rule applies." else "Booked visit.")
        Spacer(Modifier.height(8.dp))
        NsRowButtons {
            NsButton("SAVE PENDING") {
                if (name.isNotBlank()) {
                    val n = repo.sessions.size + 1
                    repo.sessions.add(
                        NsSession(
                            "s-n$n", "03:00", name.trim().uppercase(), branch.id,
                            if (walkIn) "Walk-in" else "Follow-up", walkIn,
                            NsSessionStatus.PENDING, if (walkIn) 800 else 1200, user.name.uppercase(),
                        ),
                    )
                    repo.log(user.login, "log session s-n$n at " + branch.name)
                    onDone()
                }
            }
            NsGhost("DISCARD") { onDone() }
        }
    }
}

@Composable
fun NsClients(repo: NightShiftRepo, user: NsUser) {
    NsTitle("clients")
    NsNote("global book · at most one PENDING session per client · quiet anonymized view keeps gender + age")
    NsRule()
    NsPanel {
        NsRowButtons {
            NsText("anonymized view", size = 13, modifier = Modifier.weight(1f))
            NsGhost(if (repo.anonymized) "ON" else "OFF") {
                repo.anonymized = !repo.anonymized
                repo.log(user.login, "toggle anonymized client view to " + repo.anonymized)
            }
        }
    }
    repo.clients.forEach { c ->
        NsPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NsText(repo.displayName(c), size = 14, weight = FontWeight.Bold)
                    NsText(c.gender + " · " + c.age + " · " + c.contact, size = 12, color = NsColors.Taupe)
                    NsNote("pending sessions: " + c.pendingCount + " (cap is one — book, don't stack)")
                }
                Spacer(Modifier.width(8.dp))
                NsGhost("+ PENDING") {
                    if (c.pendingCount < 1) {
                        c.pendingCount = 1
                        repo.log(user.login, "book pending session for " + c.id)
                    }
                }
            }
        }
    }
}
