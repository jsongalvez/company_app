package com.companyb.companyapp.proto.gaugecluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

// #834 — gauge-cluster ops screens: showroom, ignition, garage, cluster home, sessions bay.

@Composable
fun GaugeWelcome(repo: GaugeFakeRepo, onNext: () -> Unit) {
    GaugePanel("Showroom ● step 1 of 3") {
        Text(
            "Every target is a dial. Drive the day by needle.",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = GaugeColors.Cream,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Occupancy, gross and relief coverage read as analog gauges with redlines — " +
                "no tables, no Linear cards. The needle is the status; the lamp strip is the branch day.",
            fontSize = 14.sp,
            color = GaugeColors.CreamDim,
        )
        Spacer(Modifier.height(12.dp))
        val (booked, cap) = repo.occupancy(repo.currentBranchId)
        val (earned, target) = repo.gross(repo.currentBranchId)
        val (covered, need) = repo.reliefCover(repo.currentBranchId)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GaugeDial("Occupancy", booked, cap, "seats")
            GaugeDial("Gross", earned, target.coerceAtLeast(1), "PHP", redlineFrom = 0.7f)
            GaugeDial("Relief cover", covered, need, "posts")
        }
        Spacer(Modifier.height(12.dp))
        GaugeNote(
            "ONBOARDING drivers are ignition-locked: R. Nuevo carries zero capabilities until a " +
                "MANAGE_USERS grant turns the key. Nothing derives before that.",
        )
        Spacer(Modifier.height(12.dp))
        GaugeButton("Turn the key →", onNext)
    }
}

@Composable
fun GaugeSignin(repo: GaugeFakeRepo, onNext: () -> Unit) {
    var blocked by remember { mutableStateOf<String?>(null) }
    GaugePanel("Ignition ● step 2 of 3 — pick a driver") {
        repo.users.forEach { u ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GaugeColors.Cream)
                    Text(
                        "${u.role.name}  ·  home ${repo.branches.first { it.id == u.homeBranchId }.name}  ·  slot ${u.slot}",
                        fontSize = 12.sp,
                        color = GaugeColors.CreamDim,
                    )
                }
                GaugeButton(
                    "Drive",
                    {
                        if (u.role == GaugeRole.ONBOARDING && !repo.onboardGranted) {
                            blocked = "${u.name} is ONBOARDING-locked (zero capabilities). Clear the hold first."
                        } else {
                            repo.currentUserId = u.id
                            repo.clockedIn = true
                            repo.audit(u.name, "SIGN_IN", u.homeBranchId)
                            onNext()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (blocked != null) {
            GaugeNote(blocked!!)
            Spacer(Modifier.height(8.dp))
        }
        GaugeNote(
            "ONBOARDING hold override (simulates the MANAGE_USERS grant): " +
                "flip the switch, then R. Nuevo can drive.",
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GaugeButton(if (repo.onboardGranted) "Hold cleared ✓" else "Clear onboarding hold", {
                repo.onboardGranted = !repo.onboardGranted
                repo.audit(repo.me()?.name ?: "proto", "GRANT_MANAGE_USERS", "R. Nuevo", "role review")
            })
        }
    }
}

@Composable
fun GaugeBranches(repo: GaugeFakeRepo, onNext: () -> Unit) {
    GaugePanel("Garage ● step 3 of 3 — pick a branch") {
        repo.branches.forEach { b ->
            val (booked, cap) = repo.occupancy(b.id)
            val (earned, target) = repo.gross(b.id)
            val pending = repo.branchSessions(b.id).count { it.status == GaugeSessionStatus.PENDING && !it.voided }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GaugeColors.Cream)
                        Text(
                            "${b.kind.name}  ·  occupancy $booked/$cap  ·  gross ₱$earned / ₱$target  ·  $pending PENDING",
                            fontSize = 12.sp,
                            color = GaugeColors.CreamDim,
                        )
                    }
                    if (repo.currentBranchId == b.id) {
                        GaugeLamp("SELECTED", GaugeColors.LampGreen)
                    } else {
                        GaugeButton("Select", {
                            repo.currentBranchId = b.id
                            repo.audit(repo.me()?.name ?: "proto", "SELECT_BRANCH", b.id)
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        GaugeButton("Open the cluster →", onNext)
    }
}

@Composable
fun GaugeHome(repo: GaugeFakeRepo, go: (GaugeScreen) -> Unit) {
    val me = repo.me()
    val (booked, cap) = repo.occupancy(repo.currentBranchId)
    val (earned, target) = repo.gross(repo.currentBranchId)
    val (covered, need) = repo.reliefCover(repo.currentBranchId)
    GaugePanel("Cluster — ${me?.name ?: "?"} · ${repo.branches.first { it.id == repo.currentBranchId }.name}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GaugeDial("Occupancy", booked, cap, "seats")
            GaugeDial("Gross", earned, target.coerceAtLeast(1), "PHP", redlineFrom = 0.7f)
            GaugeDial("Relief cover", covered, need, "posts")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GaugeCell("PENDING today", repo.branchSessions(repo.currentBranchId).count { it.status == GaugeSessionStatus.PENDING && !it.voided }.toString())
            GaugeCell("Completed", repo.branchSessions(repo.currentBranchId).count { it.status == GaugeSessionStatus.COMPLETED }.toString())
            GaugeCell("Unread mail", repo.notifications.count { !it.read }.toString())
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (repo.clockedIn) "●  Clocked in" else "○  Off duty",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (repo.clockedIn) GaugeColors.LampGreen else GaugeColors.CreamDim,
            )
            Spacer(Modifier.width(12.dp))
            GaugeButton(if (repo.clockedIn) "Clock out" else "Clock in", {
                repo.clockedIn = !repo.clockedIn
                if (!repo.clockedIn) repo.reliefBranchId = null
                repo.audit(me?.name ?: "proto", if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranchId)
            }, primary = !repo.clockedIn)
        }
        Spacer(Modifier.height(10.dp))
        GaugeNote(
            "Relief Duty: clock into a non-home branch for view-only access; edit needs a relief grant " +
                "(broadcast Relief Request approved by any branch member, or a branch Relief Invite you accept). " +
                "Expires 04:00 Manila next day; compensation is paid from the relief branch drawer.",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeButton("Start relief at…", {}, primary = false)
        }
        repo.branches.filter { it.id != me?.homeBranchId }.forEach { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, Modifier.weight(1f), fontSize = 14.sp, color = GaugeColors.Cream)
                if (repo.reliefBranchId == b.id && repo.clockedIn) {
                    GaugeLamp(if (repo.reliefEditGranted) "RELIEF ● EDIT" else "RELIEF ● VIEW", GaugeColors.LampAmber)
                    Spacer(Modifier.width(8.dp))
                    if (!repo.reliefEditGranted) {
                        GaugeButton("Grant edit (simulate)", {
                            repo.reliefEditGranted = true
                            repo.audit("branch member", "GRANT_RELIEF", b.id)
                        }, primary = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    GaugeButton("End", {
                        repo.reliefBranchId = null
                        repo.reliefEditGranted = false
                    }, primary = false)
                } else {
                    GaugeButton("Clock relief here", {
                        repo.reliefBranchId = b.id
                        repo.reliefEditGranted = false
                        repo.clockedIn = true
                        repo.audit(me?.name ?: "proto", "CLOCK_IN_RELIEF", b.id)
                    }, primary = false)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Relief Invites (branch-initiated, one future day each)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GaugeColors.Cream)
        repo.invites.forEach { inv ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${inv.branchName} → ${inv.person} · ${inv.day} · " +
                        when (inv.accepted) {
                            true -> "ACCEPTED"
                            false -> "DECLINED"
                            null -> "AWAITING"
                        },
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = GaugeColors.CreamDim,
                )
                if (inv.accepted == null) {
                    GaugeButton("Accept", {
                        repo.invites[repo.invites.indexOf(inv)] = inv.copy(accepted = true)
                        repo.audit(me?.name ?: "proto", "ACCEPT_INVITE", inv.id)
                    })
                    Spacer(Modifier.width(6.dp))
                    GaugeButton("Decline", {
                        repo.invites[repo.invites.indexOf(inv)] = inv.copy(accepted = false)
                    }, primary = false)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Relief Requests (outsider-initiated broadcast, one live per requester per branch per date)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GaugeColors.Cream)
        repo.requests.forEach { req ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${req.requester} → ${req.branchName} · ${req.day} · ${req.state}",
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = GaugeColors.CreamDim,
                )
                if (req.state == "LIVE") {
                    GaugeButton("Allow", {
                        repo.requests[repo.requests.indexOf(req)] = req.copy(state = "GRANTED")
                        repo.reliefEditGranted = true
                        repo.audit(me?.name ?: "proto", "GRANT_RELIEF_REQUEST", req.id)
                    })
                    Spacer(Modifier.width(6.dp))
                    GaugeButton("Deny", {
                        repo.requests[repo.requests.indexOf(req)] = req.copy(state = "DENIED")
                    }, primary = false)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaugeButton("Sessions bay", { go(GaugeScreen.SESSIONS) }, primary = false)
            GaugeButton("Fuel & ledger", { go(GaugeScreen.FINANCE) }, primary = false)
        }
    }
}

@Composable
fun GaugeSessions(repo: GaugeFakeRepo) {
    var name by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    val filters = listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED")
    val selIdx = when (repo.sessionFilter) {
        null -> 0
        GaugeSessionStatus.PENDING -> 1
        GaugeSessionStatus.COMPLETED -> 2
        GaugeSessionStatus.NO_SHOW -> 3
        GaugeSessionStatus.CANCELLED -> 4
    }
    GaugePanel("Sessions bay — ${repo.branches.first { it.id == repo.currentBranchId }.name}") {
        GaugeChipRow(filters, selIdx) { i ->
            repo.sessionFilter = when (i) {
                1 -> GaugeSessionStatus.PENDING
                2 -> GaugeSessionStatus.COMPLETED
                3 -> GaugeSessionStatus.NO_SHOW
                4 -> GaugeSessionStatus.CANCELLED
                else -> null
            }
        }
        Spacer(Modifier.height(10.dp))
        repo.branchSessions(repo.currentBranchId)
            .filter { repo.sessionFilter == null || it.status == repo.sessionFilter }
            .forEach { s ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${s.startsAt}  ${s.clientName} — ${s.service}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GaugeColors.Cream,
                            )
                            Text(
                                "${s.status.name}${if (s.walkIn) " · WALK-IN" else ""}${if (s.voided) " · VOID (${s.voidReason})" else ""} · ₱${s.price} · ${s.practitioner}",
                                fontSize = 12.sp,
                                color = GaugeColors.CreamDim,
                            )
                        }
                        GaugeLamp(s.status.name, s.status.lamp())
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GaugeSessionStatus.entries.forEach { st ->
                            GaugeLink(st.name) {
                                if ((s.walkIn) && (st == GaugeSessionStatus.NO_SHOW || st == GaugeSessionStatus.CANCELLED)) {
                                    repo.audit(repo.me()?.name ?: "proto", "BLOCK_WALKIN_STATUS", "${s.id}→${st.name}", "walk-in rule")
                                } else {
                                    repo.sessions[repo.sessions.indexOf(s)] = s.copy(status = st)
                                    repo.audit(repo.me()?.name ?: "proto", "SET_SESSION_STATUS", "${s.id}→${st.name}")
                                }
                            }
                        }
                        if (!s.voided) {
                            GaugeLink("Void") { voidTarget = s.id }
                        } else {
                            GaugeLink("Unvoid") {
                                repo.sessions[repo.sessions.indexOf(s)] = s.copy(voided = false, voidReason = null)
                                repo.audit(repo.me()?.name ?: "proto", "UNVOID_SESSION", s.id, "correction")
                            }
                        }
                    }
                    if (voidTarget == s.id) {
                        Spacer(Modifier.height(6.dp))
                        TextField(
                            value = voidReason,
                            onValueChange = { voidReason = it },
                            label = { Text("Void reason (required)") },
                            colors = TextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GaugeButton("Confirm void", {
                                if (voidReason.isNotBlank()) {
                                    repo.sessions[repo.sessions.indexOf(s)] = s.copy(voided = true, voidReason = voidReason)
                                    repo.audit(repo.me()?.name ?: "proto", "VOID_SESSION", s.id, voidReason)
                                    voidTarget = null
                                    voidReason = ""
                                }
                            })
                            GaugeButton("Cancel", { voidTarget = null }, primary = false)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        GaugeNote("Walk-in Sessions cannot be marked NO_SHOW or CANCELLED — the dial refuses and logs the attempt.")
        Spacer(Modifier.height(10.dp))
        Text("Book a walk-in (creates a PENDING Session + Client record)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GaugeColors.Cream)
        TextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        TextField(value = service, onValueChange = { service = it }, label = { Text("Service") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        GaugeButton("Add walk-in ₱950", {
            repo.addWalkIn(repo.currentBranchId, name, service, 950)
            name = ""
            service = ""
        })
    }
}
