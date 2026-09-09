package com.companyb.companyapp.proto.shifthandover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

// #771 — finance (SESSION/PRODUCT draft → submit → snapshot → Undo-48h, commission split),
// team (roles + relief duty/invite/request), mailbox, audit log, profile.

@Composable
fun ShFinanceScreen(repo: ShiftRepo) {
    var undoReason by remember { mutableStateOf("") }
    val includes = remember { mutableStateMapOf("Ana Reyes" to true, "Ben Cruz" to true, "Cara Lim" to false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Finance · remittance")
        ShNote("Two flows: SESSION (net income after compensation and expenses) and PRODUCT (unit price × quantity). Submission freezes an immutable snapshot; Undo returns to Draft within 48h with a reason.")
        repo.remittances.forEach { r ->
            ShRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.flow} · ${r.branchName} · ₱${r.amount}", color = ShColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        ShStatusChip(r.stage, if (r.stage == "DRAFT") ShColors.Tape else ShColors.Teal)
                    }
                    if (r.stage == "DRAFT") {
                        ShPrimary("Submit + freeze snapshot") {
                            r.stage = "SUBMITTED"
                            repo.log("SUBMIT ${r.flow} remittance ${r.id} · ${r.branchName} · snapshot frozen")
                        }
                    } else {
                        val hours = undoWindowText(r)
                        ShNote("Snapshot frozen · submitted $hours. Undo window: 48h from submission.")
                        if ((r.submittedHoursAgo ?: 99) <= 48) {
                            TextField(
                                value = undoReason,
                                onValueChange = { undoReason = it },
                                label = { Text("Undo reason (required)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            ShGhost("Undo to Draft") {
                                if (undoReason.isNotBlank()) {
                                    r.stage = "DRAFT"
                                    repo.log("UNDO remittance ${r.id} reason: $undoReason · days unlocked, snapshot deleted")
                                    undoReason = ""
                                }
                            }
                        } else {
                            ShNote("Undo window closed — snapshot is permanent.")
                        }
                    }
                }
            }
        }
        ShCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShTape("COMMISSION SPLIT")
                Text(
                    "Product commissions pool per branch day and split equally among practitioners + coordinators clocked in at sold_at. Manual include/exclude overrides. Separate from compensation, not subject to remittance.",
                    color = ShColors.Paper,
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
                            colors = CheckboxDefaults.colors(checkedColor = ShColors.Tape),
                        )
                        Text(name, color = ShColors.Paper, fontSize = 13.sp)
                    }
                }
                Text("Pool ₱$pool → ${crew.size} in split → ₱$share each", color = ShColors.Tape, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun undoWindowText(r: ShRemittance): String = when (r.submittedHoursAgo) {
    null -> "just now (fake)"
    else -> "${r.submittedHoursAgo}h ago"
}

@Composable
fun ShTeamScreen(repo: ShiftRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Team · roles + relief")
        ShSection("RELIEF DUTY")
        if (repo.onRelief) {
            ShCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You clocked into ${repo.currentBranch()?.name} — not your home branch. View-only until a grant lands. Expires 04:00 Manila; pay from this branch drawer.",
                        color = ShColors.Paper,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    ShNote("Access now: ${repo.reliefAccess.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (repo.reliefAccess == ShReliefState.DUTY_VIEW_ONLY) {
                            ShPrimary("Broadcast relief request") {
                                repo.relief.add(ShReliefLine("f-%02d".format(repo.relief.size + 1), "REQUEST", "You → ${repo.currentBranch()?.name}", repo.currentBranch()?.name ?: "—", "today", "PENDING — broadcast to branch"))
                                repo.reliefAccess = ShReliefState.REQUESTED
                                repo.log("REQUEST relief · ${repo.currentBranch()?.name} · today (broadcast, one live per branch+date)")
                            }
                        }
                        if (repo.reliefAccess == ShReliefState.REQUESTED) {
                            ShPrimary("Simulate branch grant") {
                                repo.reliefAccess = ShReliefState.GRANTED
                                repo.notices.add(0, ShNotice("n-g1", "Relief granted · ${repo.currentBranch()?.name} · today", "A branch member granted your request. Edit access active until 04:00 Manila."))
                                repo.log("GRANT relief request · ${repo.currentBranch()?.name} · today")
                            }
                        }
                    }
                }
            }
        } else {
            ShNote("On your home branch — full access. Clock into another branch to start relief duty.")
        }
        ShSection("RELIEF LINES")
        repo.relief.forEach { line ->
            ShRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ShTape(line.kind)
                        Spacer(Modifier.width(8.dp))
                        Text("${line.who} · ${line.branchName} · ${line.day}", color = ShColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    ShNote(line.state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (line.kind == "REQUEST" && line.state.startsWith("PENDING")) ShGhost("Withdraw") {
                            line.state = "WITHDRAWN by requester"
                            repo.log("WITHDRAW relief request ${line.id}")
                        }
                        if (line.kind == "INVITE" && line.state.startsWith("ACCEPTED")) ShGhost("Revoke") {
                            line.state = "REVOKED — grant removed, branch notified"
                            repo.log("REVOKE relief invite ${line.id} (before clock-in, re-invitable)")
                        }
                        if (line.kind == "INVITE" && line.state.startsWith("PENDING")) {
                            ShPrimary("Accept") {
                                line.state = "ACCEPTED — grant written on accept"
                                repo.log("ACCEPT relief invite ${line.id}")
                            }
                            ShGhost("Decline") {
                                line.state = "DECLINED"
                                repo.log("DECLINE relief invite ${line.id}")
                            }
                        }
                    }
                }
            }
        }
        ShSection("ROSTER · ROLES ARE CAPABILITY BUNDLES")
        repo.users.forEach { u ->
            ShRowCard {
                Column {
                    Text("${u.name} · ${u.role}", color = ShColors.Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (u.capabilities.isEmpty()) "ONBOARDING — empty bundle, locked out" else u.capabilities.joinToString(" · "),
                        color = ShColors.Faded,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ShMailboxScreen(repo: ShiftRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Mailbox")
        ShNote("Per-recipient read/unread; read rows kept forever. Relief events name the branch and day, written in the same transaction as the change.")
        repo.notices.forEach { n ->
            ShRowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ShDot(if (n.read) ShColors.Faded else ShColors.Outgoing)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, color = ShColors.Paper, fontSize = 14.sp, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                        Text(n.body, color = ShColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    if (!n.read) ShGhost("Read") {
                        n.read = true
                        repo.log("READ notice ${n.id}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShAuditScreen(repo: ShiftRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Audit log")
        ShNote("Immutable record of every mutation — who changed what, when, and why. Newest first.")
        repo.audit.forEach { a ->
            ShRowCard {
                Row(Modifier.fillMaxWidth()) {
                    Text("#${a.seq}", color = ShColors.Tape, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(a.actor, color = ShColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(a.action, color = ShColors.Faded, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ShProfileScreen(repo: ShiftRepo, onBack: () -> Unit, go: (ShDest) -> Unit) {
    val user = repo.currentUser ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Profile")
        ShCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShField("Name", user.name)
                ShField("Role", user.role)
                ShField("Home branch", repo.branch(user.homeBranchId).name)
                ShField("Capabilities", if (user.capabilities.isEmpty()) "none (ONBOARDING)" else user.capabilities.joinToString(", "))
                ShField("Shift", if (repo.clockedIn) "Clocked in · ${repo.currentBranch()?.name}" + if (repo.onRelief) " (relief)" else "" else "Off shift")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (repo.clockedIn) ShPrimary("Clock out → write handover") { go(ShDest.HANDOVER) }
            ShGhost("Switch branch") { repo.currentBranchId = "" }
            ShGhost("Log out") {
                repo.currentUser = null
                repo.currentBranchId = ""
                repo.clockedIn = false
            }
        }
        ShLink("Exit prototype") { onBack() }
    }
}
