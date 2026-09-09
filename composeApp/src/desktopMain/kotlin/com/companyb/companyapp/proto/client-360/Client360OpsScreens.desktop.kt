package com.companyb.companyapp.proto.client360

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #773 — client-360 operations screens: home + relief, client index, sessions,
// finance, team, mailbox, audit, profile. All fake, all clickable.

@Composable
fun C360HomePane(repo: Client360Repo) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Home & clock")
        C360Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                C360Title("Today at ${repo.currentBranch().name}")
                C360Body(if (repo.clockedIn) "Clocked in — the floor sees you on duty." else "Clocked out — clock in to start the day.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!repo.clockedIn) {
                        C360Primary("Clock in") {
                            repo.clockedIn = true
                            repo.log("${repo.currentUser?.name} clocked in at ${repo.currentBranch().name}")
                        }
                    } else {
                        C360Ghost("Clock out") {
                            repo.clockedIn = false
                            repo.log("${repo.currentUser?.name} clocked out at ${repo.currentBranch().name}")
                        }
                    }
                }
            }
        }
        C360Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                C360Eyebrow("Relief")
                val tab = repo.opsTab
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C360Chip("Duty", active = tab == 0, onClick = { repo.opsTab = 0 })
                    C360Chip("Requests", active = tab == 1, onClick = { repo.opsTab = 1 })
                    C360Chip("Invites", active = tab == 2, onClick = { repo.opsTab = 2 })
                }
                when (tab) {
                    0 -> repo.reliefDuty.forEach { C360Body("• $it") }
                    1 -> {
                        C360Note("Broadcast requests — one live request per branch per date.")
                        repo.reliefRequests.forEach { q ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${q.branch} · ${q.date} · ${q.state}", color = C360Colors.Ink, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Ghost("Cover") {
                                        q.state = "COVERED"
                                        repo.log("${repo.currentUser?.name} covered relief request ${q.id} (${q.branch} ${q.date})")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        C360Note("Invites name you directly. Accepting writes an audit entry; declining frees the slot.")
                        repo.reliefInvites.forEach { v ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${v.fromBranch} · ${v.date} · ${v.state}", color = C360Colors.Ink, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Primary("Accept") {
                                        v.state = "ACCEPTED"
                                        repo.log("${repo.currentUser?.name} accepted relief invite ${v.id} (${v.fromBranch} ${v.date})")
                                    }
                                    C360Ghost("Decline") {
                                        v.state = "DECLINED"
                                        repo.log("${repo.currentUser?.name} declined relief invite ${v.id}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun C360ClientsPane(repo: Client360Repo, onOpenRecord: () -> Unit) {
    var filter by remember { mutableStateOf("ALL") }
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Clients — global index")
        C360Note("One record per client across all branches. Opening a card loads its 360 dossier.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C360Chip("All", active = filter == "ALL", onClick = { filter = "ALL" })
            C360Chip("Has PENDING", active = filter == "PENDING", onClick = { filter = "PENDING" })
            C360Chip("Anonymized", active = filter == "MASKED", onClick = { filter = "MASKED" })
        }
        repo.clients.filter {
            when (filter) {
                "PENDING" -> repo.pendingFor(it.id) != null
                "MASKED" -> it.anonymized || repo.maskAll
                else -> true
            }
        }.forEach { c ->
            val pending = repo.pendingFor(c.id)
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(repo.displayName(c), color = C360Colors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${repo.displayContact(c)} · ${repo.branchNamesFor(c.id).joinToString(" · ")}",
                                color = C360Colors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        C360Chip(if (pending != null) "1 PENDING" else "clear", active = pending != null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.tags.forEach { C360Chip(it) }
                    }
                    Row {
                        C360Primary("Open 360 record") {
                            repo.selectedClientId = c.id
                            repo.recordTab = 0
                            repo.log("${repo.currentUser?.name} opened 360 record ${c.id}")
                            onOpenRecord()
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            C360Ghost(if (repo.maskAll) "Unmask all records" else "Mask all records") {
                repo.maskAll = !repo.maskAll
                repo.log("${repo.currentUser?.name} toggled global anonymized view ${if (repo.maskAll) "on" else "off"}")
            }
        }
    }
}

@Composable
fun C360SessionsPane(repo: Client360Repo) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Sessions — branch board")
        C360Note("Every session belongs to a client record. Manage here, or resolve the PENDING from the 360 dossier.")
        repo.sessions.sortedBy { it.time }.forEach { s ->
            val client = repo.clients.first { it.id == s.clientId }
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        C360SealDot(s.status.seal())
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.id} · ${repo.displayName(client)} — ${s.kind}",
                                color = C360Colors.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${s.time} · ${repo.branch(s.branchId).name} · ${s.practitioner}" +
                                    if (s.walkIn) " · walk-in" else "" +
                                    if (s.voided) " · VOIDED (${s.voidReason})" else "",
                                color = C360Colors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        C360Link(if (expandedId == s.id) "Hide" else "Manage") {
                            expandedId = if (expandedId == s.id) null else s.id
                        }
                    }
                    if (expandedId == s.id) {
                        Row {
                            C360Ghost("Open client record") { repo.selectedClientId = s.clientId }
                        }
                        if (!s.voided && s.status == C360SessionStatus.PENDING) {
                            if (s.walkIn) {
                                C360Note("Walk-in rule: only COMPLETED is offered.")
                                Row { C360Primary("Complete") { repo.transition(s, C360SessionStatus.COMPLETED) } }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    C360Primary("Complete") { repo.transition(s, C360SessionStatus.COMPLETED) }
                                    C360Ghost("No-show") { repo.transition(s, C360SessionStatus.NO_SHOW) }
                                    C360Ghost("Cancel") { repo.transition(s, C360SessionStatus.CANCELLED) }
                                }
                            }
                        }
                        if (!s.voided) {
                            Row { C360Ghost("Void — duplicate entry") { repo.voidSession(s, "Duplicate entry") } }
                        } else {
                            Row { C360Ghost("Unvoid — restore") { repo.unvoidSession(s) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun C360FinancePane(repo: Client360Repo) {
    var undoNote by remember { mutableStateOf("") }
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Finance & remittance")
        C360Note("SESSION and PRODUCT drafts submit into sealed snapshots. Undo runs inside 48h with a reason; past 48h the snapshot is permanent.")
        repo.remittances.forEach { r ->
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("${r.id} · ${r.flow} · ₱${r.amount}", color = C360Colors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${r.branchName} · ${r.stage}" +
                                    (r.submittedHoursAgo?.let { " · submitted ${it}h ago" } ?: " · not submitted"),
                                color = C360Colors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        C360Chip(r.stage, active = r.stage == "SNAPSHOT")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.stage == "DRAFT") {
                            C360Primary("Submit → snapshot") {
                                r.stage = "SNAPSHOT"
                                repo.log("${repo.currentUser?.name} submitted remittance ${r.id} (${r.flow}, ₱${r.amount})")
                            }
                        }
                        if (r.stage == "SNAPSHOT" || r.stage == "SUBMITTED") {
                            val hours = r.submittedHoursAgo ?: 0
                            if (hours <= 48) {
                                C360Ghost("Undo (within 48h)") {
                                    r.stage = "DRAFT"
                                    repo.log("${repo.currentUser?.name} undid remittance ${r.id} (${hours}h old, reason recorded)")
                                    undoNote = "${r.id} returned to DRAFT."
                                }
                            } else {
                                C360Note("Undo locked — ${hours}h past submit exceeds the 48h window. Snapshot is permanent.")
                            }
                        }
                    }
                }
            }
        }
        if (undoNote.isNotBlank()) C360Note(undoNote)
        C360Card {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                C360Eyebrow("Commission")
                C360Body("Completed sessions split commission to the practitioner of record plus the branch pool. Voided and non-completed sessions never split.")
                C360Row("Practitioner share", "70%")
                C360Row("Branch pool", "30%")
            }
        }
    }
}

@Composable
fun C360TeamPane(repo: Client360Repo) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Team, users & roles")
        C360Note("MANAGER holds the capability superset. ONBOARDING carries an empty bundle until a MANAGER grants a real role.")
        repo.users.forEach { u ->
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = C360Colors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${u.role} · home ${repo.branch(u.homeBranchId).name}", color = C360Colors.Faded, fontSize = 12.sp)
                        }
                        C360Chip(if (u.locked) "ONBOARDING" else "ACTIVE", active = !u.locked)
                    }
                    if (u.capabilities.isEmpty()) {
                        C360Note("Empty capability bundle — nothing derives, even with a branch assignment.")
                        Row {
                            C360Ghost("Grant Practitioner (MANAGER)") {
                                repo.log("${repo.currentUser?.name} recorded a role grant for ${u.name} (MANAGER approval)")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            u.capabilities.forEach { C360Chip(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun C360MailboxPane(repo: Client360Repo) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Notifications mailbox")
        Row {
            C360Ghost("Mark all read") {
                repo.notices.forEach { it.read = true }
                repo.log("${repo.currentUser?.name} marked all notifications read")
            }
        }
        val unread = repo.notices.count { !it.read }
        C360Note(if (unread == 0) "All caught up." else "$unread unread.")
        repo.notices.forEach { n ->
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        C360SealDot(if (n.read) C360Colors.Slate else C360Colors.Brass)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, color = C360Colors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(n.body, color = C360Colors.Faded, fontSize = 12.sp)
                        }
                    }
                    if (!n.read) {
                        Row { C360Ghost("Mark read") { n.read = true } }
                    }
                }
            }
        }
    }
}

@Composable
fun C360AuditPane(repo: Client360Repo) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Audit log")
        C360Note("Every action in this prototype appends here, newest first — bookings, transitions, voids, anonymize, clock, relief, remittance.")
        repo.audit.forEach { entry ->
            C360Card { C360Body("• $entry") }
        }
    }
}

@Composable
fun C360ProfilePane(repo: Client360Repo, onLogout: () -> Unit) {
    val user = repo.currentUser
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        C360Eyebrow("Profile")
        if (user != null) {
            C360Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    C360Headline(user.name)
                    C360Body("${user.role} · home ${repo.branch(user.homeBranchId).name}")
                    C360Note(if (user.capabilities.isEmpty()) "Capability bundle: empty (ONBOARDING)." else "Capability bundle: ${user.capabilities.joinToString(", ")}.")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (repo.clockedIn) {
                            C360Ghost("Clock out") {
                                repo.clockedIn = false
                                repo.log("${user.name} clocked out at ${repo.currentBranch().name}")
                            }
                        }
                        C360Primary("Log out") { onLogout() }
                    }
                }
            }
        }
        C360Card {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                C360Title("Prototype")
                C360Note("Client-360 fake-data prototype (ref #773). No network calls; exit returns to the launcher via the rail button below.")
            }
        }
    }
}
