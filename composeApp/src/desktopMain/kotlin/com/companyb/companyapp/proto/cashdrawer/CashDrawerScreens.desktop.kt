package com.companyb.companyapp.proto.cashdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #785 — cash-drawer supporting screens: home, sessions, clients, finance,
// team, mailbox, audit, profile. Fake data only.

@Composable
private fun cdFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = CdColors.Cream,
    unfocusedTextColor = CdColors.Cream,
    focusedContainerColor = CdColors.Rail,
    unfocusedContainerColor = CdColors.Rail,
    cursorColor = CdColors.Brass,
)

@Composable
private fun CdDarkCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CdColors.Rail, RoundedCornerShape(4.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = { content() },
    )
}

@Composable
fun CdHomeScreen(repo: CashDrawerRepo, user: CdUser) {
    var broadcastDate by remember { mutableStateOf("") }
    CdTitle("Counter home")
    CdNote("Clock in to open your shift. Relief covers other drawers without leaving home branch.")
    Spacer(Modifier.height(8.dp))
    CdDarkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (repo.clockedIn) "CLOCKED IN — shift running" else "CLOCKED OUT",
                fontWeight = FontWeight.Black, fontSize = 15.sp, color = CdColors.Cream,
                modifier = Modifier.weight(1f),
            )
            CdBrassButton(if (repo.clockedIn) "CLOCK OUT" else "CLOCK IN", onClick = {
                repo.clockedIn = !repo.clockedIn
                repo.log(user.login, if (repo.clockedIn) "clock in" else "clock out")
                logInfo("CashDrawer", "clock toggled in=${repo.clockedIn}")
            })
        }
    }
    Spacer(Modifier.height(8.dp))
    CdSection("relief duties")
    repo.reliefDuties.forEach { d -> CdDarkCard { Text(d.text, fontSize = 13.sp, color = CdColors.Cream) } }
    Spacer(Modifier.height(8.dp))
    CdSection("broadcast a request")
    CdNote("One live request per date — the counter refuses a second broadcast for the same day.")
    CdDarkCard {
        OutlinedTextField(
            value = broadcastDate, onValueChange = { broadcastDate = it },
            label = { Text("date 2026-09-12", fontSize = 12.sp, color = CdColors.CreamDim) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = cdFieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CdBrassButton("BROADCAST", onClick = {
                val live = repo.reliefRequests.any { it.state == "live" }
                if (live) {
                    repo.log(user.login, "broadcast refused: one live per date")
                } else {
                    repo.reliefRequests.add(CdReliefItem("q-new", "REQUEST", "broadcast: relief @HOME $broadcastDate", "live"))
                    repo.log(user.login, "broadcast relief $broadcastDate")
                }
            })
            CdGhostButton("WITHDRAW", onClick = {
                repo.reliefRequests.forEach { if (it.state == "live") it.state = "withdrawn" }
                repo.log(user.login, "withdraw relief broadcast")
            })
        }
        repo.reliefRequests.forEach { q -> Text("• ${q.text} [${q.state}]", fontSize = 12.sp, color = CdColors.CreamDim) }
    }
    Spacer(Modifier.height(8.dp))
    CdSection("invites")
    repo.reliefInvites.forEach { inv ->
        CdDarkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(inv.text + " [${inv.state}]", fontSize = 13.sp, color = CdColors.Cream, modifier = Modifier.weight(1f))
                if (inv.state == "pending") {
                    CdBrassButton("TAKE", onClick = { inv.state = "accepted"; repo.log(user.login, "accept invite ${inv.id}") })
                    Spacer(Modifier.width(6.dp))
                    CdGhostButton("PASS", onClick = { inv.state = "declined"; repo.log(user.login, "decline invite ${inv.id}") })
                }
            }
        }
    }
}

@Composable
fun CdSessionsScreen(repo: CashDrawerRepo, user: CdUser, branch: CdBranch) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var newClient by remember { mutableStateOf("") }
    var newWalkIn by remember { mutableStateOf(false) }
    CdTitle("Sessions — ${branch.name}")
    CdNote("PENDING → COMPLETED / NO_SHOW / CANCELLED. Walk-ins finish COMPLETED only — the counter offers no NO_SHOW/CANCELLED for them.")
    Spacer(Modifier.height(8.dp))
    repo.sessions.filter { it.branchId == branch.id }.forEach { s ->
        CdDarkCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = if (expanded == s.id) null else s.id }) {
                Column(Modifier.weight(1f)) {
                    Text("${s.time} · ${s.clientName}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Cream)
                    Text(
                        "${s.kind}${if (s.walkIn) " · WALK-IN" else ""} · ${php(s.priceCentavos)} · ${s.practitioners}",
                        fontSize = 12.sp, color = CdColors.CreamDim,
                    )
                }
                CdStatusTag(s.status)
                if (s.voided) {
                    Spacer(Modifier.width(6.dp))
                    Text("VOID", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CdColors.Short)
                }
            }
            if (expanded == s.id) {
                CdNote("Status stepper:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = if (s.walkIn) {
                        listOf(CdSessionStatus.COMPLETED)
                    } else {
                        listOf(CdSessionStatus.COMPLETED, CdSessionStatus.NO_SHOW, CdSessionStatus.CANCELLED)
                    }
                    options.forEach { st ->
                        CdGhostButton(st.name) {
                            s.status = st
                            repo.log(user.login, "session ${s.id} -> $st")
                        }
                    }
                }
                if (s.walkIn) CdNote("Walk-in rule: NO_SHOW / CANCELLED never offered — a guest at the counter either completes or was never logged.")
                OutlinedTextField(
                    value = voidReason, onValueChange = { voidReason = it },
                    label = { Text("void reason (required)", fontSize = 12.sp, color = CdColors.CreamDim) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = cdFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CdBrassButton("VOID", onClick = {
                        if (voidReason.isNotBlank()) {
                            s.voided = true
                            s.voidReason = voidReason
                            repo.log(user.login, "void ${s.id}: $voidReason")
                            voidReason = ""
                        } else {
                            repo.log(user.login, "void ${s.id} refused: reason required")
                        }
                    })
                    if (s.voided) CdGhostButton("UNVOID", onClick = {
                        s.voided = false
                        repo.log(user.login, "unvoid ${s.id} (was: ${s.voidReason})")
                    })
                }
                if (s.voided) CdNote("Void: ${s.voidReason}")
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(8.dp))
    CdSection("log a session")
    CdDarkCard {
        OutlinedTextField(
            value = newClient, onValueChange = { newClient = it },
            label = { Text("client name", fontSize = 12.sp, color = CdColors.CreamDim) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = cdFieldColors(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = newWalkIn, onCheckedChange = { newWalkIn = it },
                colors = CheckboxDefaults.colors(checkedColor = CdColors.Brass),
            )
            Text("walk-in (COMPLETED-only rule applies)", fontSize = 12.sp, color = CdColors.CreamDim)
        }
        CdBrassButton("LOG PENDING", onClick = {
            if (newClient.isNotBlank()) {
                val id = "s-" + "%02d".format(repo.sessionCounter++)
                repo.sessions.add(
                    CdSession(id, "17:00", newClient.uppercase(), branch.id, if (newWalkIn) "Walk-in" else "Follow-up", newWalkIn, CdSessionStatus.PENDING, 120000, user.name.uppercase()),
                )
                repo.log(user.login, "log session $id for $newClient")
                newClient = ""
                newWalkIn = false
            }
        })
    }
}

@Composable
private fun CdStatusTag(status: CdSessionStatus) {
    val bg = when (status) {
        CdSessionStatus.PENDING -> CdColors.Over
        CdSessionStatus.COMPLETED -> CdColors.OkDeep
        CdSessionStatus.NO_SHOW -> CdColors.Copper
        CdSessionStatus.CANCELLED -> CdColors.Muted
    }
    val fg = if (status == CdSessionStatus.PENDING) CdColors.CounterDeep else CdColors.Cream
    Text(
        status.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun CdClientsScreen(repo: CashDrawerRepo, user: CdUser) {
    var anonymizedView by remember { mutableStateOf(false) }
    CdTitle("Clients — global")
    CdNote("One shared book across branches. At most one PENDING session per client — the counter blocks double-booking.")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = anonymizedView, onCheckedChange = { anonymizedView = it },
            colors = CheckboxDefaults.colors(checkedColor = CdColors.Brass),
        )
        Text("anonymized view (keeps gender + age)", fontSize = 12.sp, color = CdColors.CreamDim)
    }
    Spacer(Modifier.height(6.dp))
    repo.clients.forEach { c ->
        CdDarkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (anonymizedView || c.anonymized) "SEALED ${c.id.uppercase()}" else c.name,
                        fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Cream,
                    )
                    Text(
                        if (anonymizedView || c.anonymized) {
                            "contact hidden" + if (c.gender.isNotEmpty()) " · ${c.gender}${c.age}" else ""
                        } else {
                            c.contact
                        },
                        fontSize = 12.sp, color = CdColors.CreamDim,
                    )
                }
                Text(
                    if (c.pendingCount > 0) "1 PENDING" else "clear",
                    fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = if (c.pendingCount > 0) CdColors.Over else CdColors.Ok,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    CdNote("Tried to log ${user.login}: double-PENDING refused at the book — finish or void the open session first.")
}

@Composable
fun CdFinanceScreen(repo: CashDrawerRepo, user: CdUser) {
    CdTitle("Finance & remittance")
    CdNote("SESSION + PRODUCT drafts submit into frozen snapshots. Undo lives 48h; past 72h the snapshot is permanent.")
    Spacer(Modifier.height(8.dp))
    listOf("SESSION", "PRODUCT").forEach { flow ->
        CdSection(flow.lowercase() + " spool")
        repo.remittances.filter { it.flow == flow }.forEach { r ->
            CdDarkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.id.uppercase()} · ${php(r.amountCentavos)}",
                            fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Cream,
                        )
                        Text(
                            "${r.branchName} · ${r.stage}" +
                                (r.ageHours?.let { " · ${it}h old" } ?: "") +
                                if (r.fromCount) " · from drawer count" else "",
                            fontSize = 12.sp, color = CdColors.CreamDim,
                        )
                    }
                    when (r.stage) {
                        "DRAFT" -> CdBrassButton("SUBMIT") {
                            r.stage = "SUBMITTED"
                            repo.log(user.login, "submit ${r.id} snapshot ${php(r.amountCentavos)}")
                        }
                        "SUBMITTED" -> {
                            val undoable = (r.ageHours ?: 0) <= 48
                            if (undoable) {
                                CdGhostButton("UNDO") {
                                    r.stage = "DRAFT"
                                    repo.log(user.login, "undo ${r.id} inside 48h")
                                }
                            } else {
                                Text("SEALED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CdColors.Short)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(6.dp))
    CdNote("r-04 BGC at 80h is permanent — past the 72h line no undo exists. Commission split: 60 practitioner / 40 house on COMPLETED sessions; NO_SHOW and CANCELLED pay nothing.")
}

@Composable
fun CdTeamScreen(repo: CashDrawerRepo, user: CdUser) {
    CdTitle("Team & roles")
    CdNote("MANAGER holds the superset: every capability below plus user management. ONBOARDING holds none until granted.")
    Spacer(Modifier.height(8.dp))
    repo.users.forEach { u ->
        CdDarkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Cream)
                    Text(u.role, fontSize = 12.sp, color = CdColors.Brass)
                    Text(
                        if (u.capabilities.isEmpty()) "empty bundle — locked" else u.capabilities.joinToString(" · "),
                        fontSize = 11.sp, color = CdColors.CreamDim,
                    )
                }
                if (u.role == "ONBOARDING" && user.role == "MANAGER") {
                    CdBrassButton("GRANT", onClick = { repo.log(user.login, "grant ONBOARDING ${u.login} (fake: stays locked)") })
                    Spacer(Modifier.width(6.dp))
                    CdGhostButton("OFF", onClick = { repo.log(user.login, "deactivate ${u.login}") })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun CdMailScreen(repo: CashDrawerRepo, user: CdUser) {
    CdTitle("Mailbox")
    CdNote("Relief events always name branch + day so a cashier never guesses which drawer called.")
    Spacer(Modifier.height(8.dp))
    CdGhostButton("MARK ALL READ", onClick = {
        repo.notices.forEach { it.read = true }
        repo.log(user.login, "mark all mail read")
    })
    Spacer(Modifier.height(8.dp))
    repo.notices.forEach { n ->
        CdDarkCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                n.read = !n.read
                repo.log(user.login, "mail ${n.id} read=${n.read}")
            }) {
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Cream)
                    Text(n.body, fontSize = 12.sp, color = CdColors.CreamDim)
                }
                Text(
                    if (n.read) "READ" else "NEW",
                    fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = if (n.read) CdColors.Muted else CdColors.Brass,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun CdAuditScreen(repo: CashDrawerRepo) {
    CdTitle("Audit log")
    CdNote("Every count, seal, void and grant lands here — newest first, no edits, no deletes.")
    Spacer(Modifier.height(8.dp))
    repo.audit.forEach { e ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CdFiguresText("#" + "%03d".format(e.seq), size = 12, color = CdColors.Brass)
            Spacer(Modifier.width(10.dp))
            Text(e.actor, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CdColors.Cream)
            Spacer(Modifier.width(10.dp))
            Text(e.action, fontSize = 12.sp, color = CdColors.CreamDim, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CdProfileScreen(repo: CashDrawerRepo, user: CdUser, onLogout: () -> Unit) {
    CdTitle("Profile")
    Spacer(Modifier.height(8.dp))
    CdDarkCard {
        Text(user.name, fontWeight = FontWeight.Black, fontSize = 18.sp, color = CdColors.Cream)
        Text("${user.login} · ${user.role} · home ${user.homeBranchId}", fontSize = 12.sp, color = CdColors.CreamDim)
        Text(
            if (user.capabilities.isEmpty()) "capabilities: none (ONBOARDING)" else "capabilities: " + user.capabilities.joinToString(", "),
            fontSize = 12.sp, color = CdColors.Brass,
        )
        Text("shift: " + if (repo.clockedIn) "CLOCKED IN" else "CLOCKED OUT", fontSize = 12.sp, color = CdColors.CreamDim)
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CdBrassButton("LOGOUT", onClick = {
            repo.log(user.login, "logout")
            onLogout()
        })
        CdGhostButton("CLOCK-OUT + LOGOUT", onClick = {
            repo.clockedIn = false
            repo.log(user.login, "clock-out + logout")
            onLogout()
        })
    }
}
