package com.companyb.companyapp.proto.executivebrief

import androidx.compose.foundation.layout.Arrangement
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

// #770 — executive-brief money + people screens: remittance drafts/snapshots/Undo-48h,
// commission split note, team roles, mailbox, audit log, profile.

@Composable
fun EbFinanceScreen(repo: EbRepo) {
    EbHeadline("Finance — remittance desk")
    EbNote("Two independent flows: SESSION (net income) and PRODUCT (price × qty). Submitting seals an immutable snapshot; Undo reopens to Draft within 48h with a reason. Drafts may overlap.")
    repo.remittances.forEach { r ->
        EbCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${r.flow} · ${r.id} · ₱${r.amount}",
                            color = EbColors.Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${r.branchName} · ${r.stage}" +
                                if (r.submittedHoursAgo != null) " · submitted ${r.submittedHoursAgo}h ago" else " · unsealed draft",
                            color = EbColors.Faded,
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.stage == "Draft") {
                        EbPrimary("Submit → seal snapshot") { repo.submitRemittance(r.id) }
                    } else {
                        val locked = (r.submittedHoursAgo ?: 0) > 48
                        var reason by remember(r.id) { mutableStateOf("") }
                        if (locked) {
                            EbNote("Snapshot permanent — the 48h Undo window has closed.")
                        } else {
                            TextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = { Text("Undo reason (required)") },
                                modifier = Modifier.weight(1f),
                            )
                            EbGhost("Undo 48h", enabled = reason.isNotBlank()) { repo.undoRemittance(r.id, reason) }
                        }
                    }
                }
            }
        }
    }
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EbSubhead("Commission split")
            EbNote("Product commissions pool per branch day and split equally among all practitioners and coordinators clocked in at sold_at. Manual inclusions/exclusions may override. Separate from compensation — never part of remittance.")
        }
    }
}

@Composable
fun EbTeamScreen(repo: EbRepo) {
    EbHeadline("Team — roles & capabilities")
    EbNote("Runtime checks use capabilities, never role names. MANAGER is a superset of Coordinator plus user management and delegate assignment.")
    repo.users.forEach { u ->
        EbCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, color = EbColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                if (u.locked) " · ONBOARDING locked" else "",
                            color = EbColors.Faded,
                            fontSize = 12.sp,
                        )
                    }
                    if (u.locked) {
                        EbBrass("Grant Practitioner") {
                            repo.log("Eli Santos granted Practitioner via MANAGE_USERS (fake)")
                        }
                    } else {
                        EbGhost("Deactivate") { repo.log("${u.name} deactivated — login blocked, records kept (fake)") }
                    }
                }
                Text(
                    if (u.capabilities.isEmpty()) "Capabilities: none — locked out" else "Capabilities: ${u.capabilities.joinToString()}",
                    color = EbColors.Faded,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun EbMailboxScreen(repo: EbRepo) {
    EbHeadline("Mailbox — ${repo.unreadCount()} unread")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EbGhost("Mark all read") { repo.markAllRead() }
    }
    repo.notices.forEach { n ->
        EbCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        (if (n.read) "" else "● ") + n.title,
                        color = EbColors.Ink,
                        fontSize = 14.sp,
                        fontWeight = if (n.read) FontWeight.Medium else FontWeight.Black,
                    )
                    Text(n.body, color = EbColors.Faded, fontSize = 12.sp)
                }
                EbLink(if (n.read) "Mark unread" else "Mark read") { repo.toggleNotice(n.id) }
            }
        }
    }
}

@Composable
fun EbAuditScreen(repo: EbRepo) {
    EbHeadline("Audit log — newest first")
    EbNote("Every tap in this prototype appends an entry: who changed what, when, and why.")
    repo.audit.forEach { entry ->
        EbCard {
            Text(entry, color = EbColors.Ink, fontSize = 13.sp)
        }
    }
}

@Composable
fun EbProfileScreen(repo: EbRepo, onBack: () -> Unit) {
    val u = repo.currentUser
    EbHeadline("Profile")
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(u?.name ?: "?", color = EbColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${u?.role} · home ${u?.let { repo.branch(it.homeBranchId).name } ?: "?"}", color = EbColors.Faded, fontSize = 13.sp)
            Text(
                "Role bundle: ${(u?.capabilities ?: emptyList()).ifEmpty { listOf("none") }.joinToString()}",
                color = EbColors.Faded,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    EbGhost("Clock out") {
                        repo.clockedIn = false
                        repo.log("${u?.name} clocked out")
                    }
                }
                EbGhost("Log out") {
                    repo.log("${u?.name} logged out (fake)")
                    repo.currentUser = null
                }
                EbGhost("Exit prototype") { onBack() }
            }
        }
    }
    EbCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EbSubhead("Switch branch")
            repo.branches.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, color = EbColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${b.kind} · ${b.dayStatus.name}", color = EbColors.Faded, fontSize = 12.sp)
                    }
                    if (b.id == repo.currentBranchId) EbNote("● current")
                    else EbLink("Open") {
                        repo.currentBranchId = b.id
                        repo.log("${u?.name} opened ${b.name} (fake)")
                    }
                }
            }
        }
    }
}
