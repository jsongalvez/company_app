package com.companyb.companyapp.proto.captainchair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #825 — finance (SESSION/PRODUCT draft → submit → snapshot → Undo-48h,
// commission split), team (roles + relief duty/invite/request), mailbox,
// audit log, profile. All fake-data flows clickable.

@Composable
fun CcFinanceScreen(repo: CaptainRepo) {
    var undoReason by remember { mutableStateOf("") }
    val includes = remember { mutableStateMapOf("Ana Reyes" to true, "Ben Cruz" to true, "Cara Lim" to false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Finance · remittance")
        CcNote("Two flows: SESSION and PRODUCT. Submission freezes an immutable snapshot; Undo returns to Draft within 48h with a reason.")
        repo.remittances.forEach { r ->
            CcRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.flow} · ${r.branchName} · ₱${r.amount}",
                            color = CcColors.Paper,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        CcChip(r.stage, if (r.stage == "DRAFT") CcColors.Brass else CcColors.Lantern)
                    }
                    if (r.stage == "DRAFT") {
                        CcPrimary("Submit + freeze snapshot") {
                            r.stage = "SUBMITTED"
                            repo.log("SUBMIT ${r.flow} remittance ${r.id} · ${r.branchName} · snapshot frozen")
                            repo.notify("Remittance snapshot frozen", "${r.flow} remittance for ${r.branchName} submitted. Undo window: 48h.")
                        }
                    } else {
                        CcNote("Snapshot frozen · submitted ${r.submittedHoursAgo?.let { "${it}h ago" } ?: "just now (fake)"}. Undo window: 48h from submission.")
                        if ((r.submittedHoursAgo ?: 0) <= 48) {
                            TextField(
                                value = undoReason,
                                onValueChange = { undoReason = it },
                                label = { Text("Undo reason (required)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            CcGhost("Undo to Draft") {
                                if (undoReason.isNotBlank()) {
                                    r.stage = "DRAFT"
                                    repo.log("UNDO remittance ${r.id} reason: $undoReason · snapshot deleted")
                                    undoReason = ""
                                }
                            }
                        } else {
                            CcNote("Undo window closed — snapshot is permanent.")
                        }
                    }
                }
            }
        }
        CcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CcHelmPlate("COMMISSION SPLIT")
                Text(
                    "Product commissions pool per branch day and split equally among practitioners + coordinators " +
                        "clocked in at sold_at. Manual include/exclude overrides. Separate from compensation, not subject to remittance.",
                    color = CcColors.Paper,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                val pool = 3600
                val crew = includes.filterValues { it }.keys.toList()
                val share = if (crew.isEmpty()) 0 else pool / crew.size
                includes.keys.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includes[name] == true,
                            onCheckedChange = { includes[name] = it },
                            colors = CheckboxDefaults.colors(checkedColor = CcColors.Brass),
                        )
                        Text(name, color = CcColors.Paper, fontSize = 13.sp)
                    }
                }
                Text(
                    "Pool ₱$pool → ${crew.size} in split → ₱$share each",
                    color = CcColors.Brass,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun CcTeamScreen(repo: CaptainRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Crew · roles + relief")
        CcStation("RELIEF POSTURE")
        if (repo.onRelief) {
            CcCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You took the chair at ${repo.currentBranch()?.name} — not your home branch. View-only until a grant lands. Expires 04:00 Manila; pay from this branch drawer.",
                        color = CcColors.Paper,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    CcNote("Access now: ${repo.reliefAccess.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (repo.reliefAccess == CcReliefState.DUTY_VIEW_ONLY) {
                            CcPrimary("Broadcast relief request") {
                                repo.relief.add(CcReliefLine("f-%02d".format(repo.relief.size + 1), "REQUEST", "You → ${repo.currentBranch()?.name}", repo.currentBranch()?.name ?: "—", "today", "PENDING — broadcast to branch"))
                                repo.reliefAccess = CcReliefState.REQUESTED
                                repo.log("REQUEST relief · ${repo.currentBranch()?.name} · today (one live per branch+date)")
                            }
                        }
                        if (repo.reliefAccess == CcReliefState.REQUESTED) {
                            CcPrimary("Simulate branch grant") {
                                repo.reliefAccess = CcReliefState.GRANTED
                                repo.notify("Relief granted · ${repo.currentBranch()?.name} · today", "A branch member granted your request. Helm edit access until 04:00 Manila.")
                                repo.log("GRANT relief request · ${repo.currentBranch()?.name} · today")
                            }
                        }
                    }
                }
            }
        } else {
            CcNote("Home branch — full helm authority. Take another branch from Profile → Switch branch to feel relief duty.")
        }
        CcStation("ROSTER + CAPABILITIES")
        repo.users.forEach { u ->
            CcRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.name, color = CcColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        CcChip(u.role, if (u.locked) CcColors.Faded else CcColors.Brass)
                    }
                    Text(
                        "Home ${repo.branch(u.homeBranchId).name} · station ${u.station} · " +
                            if (u.capabilities.isEmpty()) "no capabilities (locked)" else u.capabilities.joinToString(", "),
                        color = CcColors.Faded,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        CcStation("DUTY / INVITES / REQUESTS")
        repo.relief.forEach { r ->
            CcRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${r.kind} · ${r.who}", color = CcColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${r.branchName} · ${r.day}", color = CcColors.Faded, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcChip(r.state, CcColors.Lantern)
                    }
                    if (r.kind == "INVITE" && r.state.startsWith("PENDING").not() && r.state.contains("INVITED")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CcPrimary("Accept") {
                                r.state = "ACCEPTED — grant written on accept"
                                repo.log("ACCEPT relief invite ${r.id}")
                            }
                            CcGhost("Decline") {
                                r.state = "DECLINED"
                                repo.log("DECLINE relief invite ${r.id}")
                            }
                        }
                    }
                    if (r.state.startsWith("PENDING") && r.kind == "INVITE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CcPrimary("Accept") {
                                r.state = "ACCEPTED — grant written on accept"
                                repo.log("ACCEPT relief invite ${r.id}")
                            }
                            CcGhost("Decline") {
                                r.state = "DECLINED"
                                repo.log("DECLINE relief invite ${r.id}")
                            }
                        }
                    }
                    if (r.kind == "REQUEST" && r.state.startsWith("PENDING")) {
                        CcGhost("Revoke") {
                            r.state = "REVOKED"
                            repo.log("REVOKE relief request ${r.id}")
                        }
                    }
                }
            }
        }
        CcPrimary("Send relief invite (fake)") {
            repo.relief.add(CcReliefLine("f-%02d".format(repo.relief.size + 1), "INVITE", "You → Ana Reyes", repo.currentBranch()?.name ?: "—", "Saturday", "PENDING — invite sent"))
            repo.log("INVITE relief · ${repo.currentBranch()?.name} · Saturday")
        }
    }
}

@Composable
fun CcMailboxScreen(repo: CaptainRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Mailbox")
        CcNote("Relief events name branch + day. Handover arrivals land here too.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcChip("${repo.notices.count { !it.read }} unread", CcColors.Port)
            CcLink("Mark all read") {
                repo.notices.forEach { it.read = true }
                repo.log("READ all mailbox notices")
            }
        }
        repo.notices.forEach { n ->
            CcRowCard(onClick = {
                if (!n.read) {
                    n.read = true
                    repo.log("READ notice ${n.id}")
                }
            }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CcLamp(if (n.read) CcColors.Faded else CcColors.Port)
                    Spacer(Modifier.weight(1f))
                    CcChip(if (n.read) "READ" else "UNREAD", if (n.read) CcColors.Faded else CcColors.Port)
                }
                Text(n.title, color = CcColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(n.body, color = CcColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
fun CcAuditScreen(repo: CaptainRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Helm log · audit")
        CcNote("Every helm action appends here, newest first. Fake-data only.")
        repo.audit.forEach { a ->
            CcRowCard {
                Column {
                    Text("#${a.seq} · ${a.actor}", color = CcColors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(a.action, color = CcColors.Paper, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CcProfileScreen(repo: CaptainRepo, onBack: () -> Unit) {
    var switchOpen by remember { mutableStateOf(false) }
    val user = repo.currentUser
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcHeadline("Captain · profile")
        if (user != null) {
            CcCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.name, color = CcColors.Paper, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${user.role} · home ${repo.branch(user.homeBranchId).name} · station ${user.station}",
                        color = CcColors.Faded,
                        fontSize = 12.sp,
                    )
                    CcSpoke()
                    CcField("Capabilities", if (user.capabilities.isEmpty()) "none (locked)" else user.capabilities.joinToString(", "))
                    CcField("Watch", if (repo.clockedIn) "ON WATCH at ${repo.currentBranch()?.name}" else "OFF WATCH")
                }
            }
        }
        CcGhost(if (switchOpen) "Close branch switcher" else "Switch branch") { switchOpen = !switchOpen }
        if (switchOpen) {
            repo.branches.forEach { b ->
                CcRowCard(onClick = {
                    repo.currentBranchId = b.id
                    val home = user?.homeBranchId == b.id
                    repo.onRelief = !home
                    repo.reliefAccess = if (home) CcReliefState.NONE else CcReliefState.DUTY_VIEW_ONLY
                    repo.clockedIn = true
                    repo.log("SWITCH branch → ${b.name}" + if (home) " (home)" else " (relief duty, view-only)")
                    switchOpen = false
                }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CcLamp(b.dayStatus.lamp())
                        Text("  ${b.name} · ${b.dayStatus.name}", color = CcColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repo.clockedIn) {
                CcPrimary("Clock out") {
                    repo.clockOut("Clock-out from profile — chair held steady.", carryRelief = true)
                }
            } else {
                CcPrimary("Clock back in") {
                    repo.clockedIn = true
                    repo.log("CLOCK_IN ${repo.currentBranch()?.name} (return to watch)")
                }
            }
            CcGhost("Log out") {
                repo.currentUser = null
                repo.currentBranchId = ""
                repo.clockedIn = false
                repo.onRelief = false
                repo.reliefAccess = CcReliefState.NONE
            }
        }
        CcLink("Leave helm (exit)") { onBack() }
    }
}
