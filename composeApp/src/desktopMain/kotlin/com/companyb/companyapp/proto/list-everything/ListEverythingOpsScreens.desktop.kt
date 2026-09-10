package com.companyb.companyapp.proto.listeverything

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #854 — people + operations outline: onboarding, login, branches,
// home (clock-in + relief), sessions, clients, team.

@Composable
fun LeOpsSections(repo: ListEverythingFakeRepo, tree: LeTreeState) {
    LeNode(tree, "onboarding", 0, "Onboarding", badge = "ONBOARDING locked") {
        val locked = repo.users.firstOrNull { it.role == LeRole.ONBOARDING }
        LeNote(1, "ONBOARDING role holds zero capabilities until a Manager grants Practitioner")
        if (locked != null && tree.matches(locked.name)) {
            LeLeaf(1, label = locked.name, meta = locked.branch, badge = "locked", badgeTone = LeTone.AMBER) {
                OutlinedButton(onClick = { repo.grantPractitioner(locked.id) }) {
                    Text("Grant Practitioner", fontSize = 12.sp)
                }
            }
        }
        val granted = repo.users.filter { it.role != LeRole.ONBOARDING }
        if (granted.isNotEmpty()) {
            LeNote(1, "granted this session: ${granted.joinToString { it.name }}")
        }
    }

    LeNode(tree, "login", 0, "Login", meta = repo.users.size.toString() + " on roster") {
        LeNote(1, "tap a name to sign in · fake roster, no password, no network")
        repo.users.forEach { u ->
            if (!tree.matches(u.name, u.role.label)) return@forEach
            val active = repo.currentUserId == u.id
            LeLeaf(
                1,
                bullet = if (active) "●" else "○",
                label = u.name,
                meta = "${u.role.label} · ${u.branch}",
                badge = if (active) "signed in" else null,
                badgeTone = LeTone.GREEN,
            ) {
                if (!active) {
                    OutlinedButton(onClick = { repo.login(u.id) }) { Text("Sign in", fontSize = 12.sp) }
                }
            }
        }
    }

    LeNode(tree, "branches", 0, "Branches", meta = repo.currentBranch.name) {
        LeNote(1, "selecting a branch re-scopes home, sessions and the day strip")
        repo.branches.forEach { b ->
            if (!tree.matches(b.name, b.kind)) return@forEach
            val active = repo.currentBranchId == b.id
            LeLeaf(
                1,
                bullet = if (active) "●" else "○",
                label = b.name,
                meta = "${b.kind} · ${b.dayDate}",
                badge = if (active) "current" else null,
                badgeTone = LeTone.GREEN,
            ) {
                if (!active) {
                    OutlinedButton(onClick = { repo.selectBranch(b.id) }) { Text("Switch", fontSize = 12.sp) }
                }
            }
        }
    }

    LeNode(
        tree, "home", 0, "Home · clock-in + relief",
        badge = "${repo.clockedIn.size} clocked in",
    ) {
        LeNode(tree, "home.clock", 1, "Clock in / out", meta = "who is on shift") {
            repo.users.forEach { u ->
                if (!tree.matches(u.name)) return@forEach
                val on = repo.clockedIn[u.id] == true
                LeLeaf(
                    2,
                    bullet = if (on) "●" else "○",
                    label = u.name,
                    badge = if (on) "IN" else "OUT",
                    badgeTone = if (on) LeTone.GREEN else LeTone.SLATE,
                ) {
                    OutlinedButton(onClick = { repo.clockToggle(u.id) }) {
                        Text(if (on) "Clock out" else "Clock in", fontSize = 12.sp)
                    }
                }
            }
        }
        LeNode(tree, "home.invites", 1, "Relief invites", badge = repo.invites.count { it.state == "PENDING" }.toString() + " pending") {
            repo.invites.forEach { inv ->
                if (!tree.matches(inv.fromBranch, inv.shift)) return@forEach
                LeLeaf(2, label = inv.fromBranch, meta = inv.shift, badge = inv.state.lowercase(), badgeTone = LeTone.AMBER) {
                    if (inv.state == "PENDING") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { repo.setInvite(inv.id, "ACCEPTED") }) { Text("Accept", fontSize = 12.sp) }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { repo.setInvite(inv.id, "DECLINED") }) { Text("Decline", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        LeNode(tree, "home.asks", 1, "Relief requests", meta = "cover asks from staff") {
            repo.reliefAsks.forEach { ask ->
                if (!tree.matches(ask.by, ask.shift)) return@forEach
                LeLeaf(2, label = "${ask.by} asks cover", meta = ask.shift, badge = ask.state.lowercase()) {
                    if (ask.state == "PENDING") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { repo.setAsk(ask.id, "GRANTED") }) { Text("Grant", fontSize = 12.sp) }
                            Spacer(Modifier.width(6.dp))
                            if (ask.mine) {
                                OutlinedButton(onClick = { repo.setAsk(ask.id, "WITHDRAWN") }) { Text("Withdraw", fontSize = 12.sp) }
                            } else {
                                OutlinedButton(onClick = { repo.setAsk(ask.id, "DENIED") }) { Text("Deny", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }

    LeNode(tree, "sessions", 0, "Sessions", badge = repo.sessions.count { it.status == LeSessionStatus.PENDING }.toString() + " pending") {
        LeNote(1, "PENDING → COMPLETED / NO_SHOW / CANCELLED · walk-ins refuse NO_SHOW + CANCELLED (house rule)")
        LeLeaf(1, bullet = "+", label = "Book walk-in at ${repo.currentBranch.name}", meta = lePeso(500)) {
            OutlinedButton(onClick = { repo.addWalkIn() }) { Text("Book", fontSize = 12.sp) }
        }
        LeSessionStatus.entries.forEach { status ->
            val group = repo.sessions.filter { it.status == status }
            LeNode(tree, "sessions.${status.name}", 1, status.label, badge = group.size.toString()) {
                if (group.isEmpty()) LeNote(2, "empty — nothing ${status.label.lowercase()} right now")
                group.forEach { s ->
                    if (!tree.matches(s.id, s.service, s.client, s.practitioner, s.branch)) return@forEach
                    LeSessionLeaf(repo = repo, tree = tree, s = s)
                }
            }
        }
    }

    LeNode(tree, "clients", 0, "Clients", meta = "${repo.clients.size} global") {
        LeNote(1, "global book · at most one PENDING session per client · anonymized view keeps gender + age")
        repo.clients.forEach { c ->
            if (!tree.matches(c.name, c.id, c.homeBranch)) return@forEach
            LeLeaf(
                1,
                label = repo.displayName(c),
                meta = "${c.gender} · ${c.age} · ${c.homeBranch} · ${c.pendingCount} pending",
                badge = if (c.anonymized) "anonymized" else null,
                badgeTone = LeTone.SLATE,
            ) {
                OutlinedButton(onClick = { repo.toggleAnonymize(c.id) }) {
                    Text(if (c.anonymized) "Reveal" else "Anonymize", fontSize = 12.sp)
                }
            }
        }
    }

    LeNode(tree, "team", 0, "Team · users + roles", meta = repo.users.size.toString() + " users") {
        LeNote(1, "capability bundles ride on role · Manager grants, Accountant reads money")
        LeRole.entries.forEach { role ->
            val members = repo.users.filter { it.role == role }
            if (members.isEmpty()) return@forEach
            LeNode(tree, "team.${role.name}", 1, role.label, badge = members.size.toString()) {
                members.forEach { u ->
                    if (!tree.matches(u.name)) return@forEach
                    val on = repo.clockedIn[u.id] == true
                    LeLeaf(2, label = u.name, meta = "${u.branch} · ${if (on) "clocked in" else "off shift"}")
                }
            }
        }
    }
}

@Composable
private fun LeSessionLeaf(repo: ListEverythingFakeRepo, tree: LeTreeState, s: LeSession) {
    var showVoid by remember(s.id) { mutableStateOf(false) }
    var reason by remember(s.id) { mutableStateOf("") }
    var denied by remember(s.id) { mutableStateOf(false) }
    val tone = when (s.status) {
        LeSessionStatus.PENDING -> LeTone.AMBER
        LeSessionStatus.COMPLETED -> LeTone.GREEN
        LeSessionStatus.NO_SHOW -> LeTone.RED
        LeSessionStatus.CANCELLED -> LeTone.SLATE
    }
    LeNode(
        tree, "session.${s.id}", 2,
        "${s.id} · ${s.service}",
        meta = "${s.client} · ${s.practitioner} · ${lePeso(s.amount)}${if (s.walkIn) " · walk-in" else ""}",
        badge = if (s.voided) "voided" else s.status.label.lowercase(),
        badgeTone = if (s.voided) LeTone.RED else tone,
    ) {
        if (denied) LeNote(3, "house rule: walk-in sessions cannot be NO_SHOW or CANCELLED")
        if (s.voided) LeNote(3, "void reason: ${s.voidReason ?: "—"}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.status == LeSessionStatus.PENDING && !s.voided) {
                OutlinedButton(onClick = { repo.setSessionStatus(s.id, LeSessionStatus.COMPLETED) }) { Text("Complete", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    denied = !repo.setSessionStatus(s.id, LeSessionStatus.NO_SHOW)
                }) { Text("No-show", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    denied = !repo.setSessionStatus(s.id, LeSessionStatus.CANCELLED)
                }) { Text("Cancel", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
            }
            if (!s.voided) {
                OutlinedButton(onClick = { showVoid = !showVoid }) { Text("Void…", fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = { repo.unvoidSession(s.id) }) { Text("Unvoid", fontSize = 12.sp) }
            }
        }
        if (showVoid && !s.voided) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = true,
                    placeholder = { Text("void reason (required)", fontSize = 12.sp) },
                )
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = {
                    repo.voidSession(s.id, reason)
                    if (reason.isNotBlank()) {
                        reason = ""
                        showVoid = false
                    }
                }) { Text("Confirm void", fontSize = 12.sp) }
            }
        }
    }
}
