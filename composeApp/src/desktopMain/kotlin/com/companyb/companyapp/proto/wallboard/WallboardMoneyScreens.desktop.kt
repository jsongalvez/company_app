package com.companyb.companyapp.proto.wallboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// #759 — wallboard money + org screens: remittance, commission note, team, mailbox, audit, profile.

@Composable
fun WbFinance(repo: WallboardFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("FINANCE", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("SESSION (net income) and PRODUCT (price × qty) flow independently.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.remittances.forEach { remit ->
            WbPanel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(remit.kind.name, color = WbColors.Ink, fontSize = 28.sp,
                            fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(remit.state.name,
                            color = if (remit.state == WbRemitState.SUBMITTED) WbColors.Green else WbColors.Amber,
                            fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Text("₱${remit.draftTotal}", color = WbColors.Amber, fontSize = 44.sp,
                        fontWeight = FontWeight.Black)
                    if (remit.state == WbRemitState.SUBMITTED) {
                        WbNote("Snapshot ${remit.snapshotId} sealed at ₱${remit.snapshotTotal}: immutable — " +
                            "later edits never rewrite it. Undo closes 48h after submission.")
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = undoReason,
                            onValueChange = { undoReason = it; error = null },
                            label = { Text("Undo reason (audit trail)") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                        )
                        Spacer(Modifier.height(8.dp))
                        WbPrimary(text = "Undo within 48h", onClick = {
                            error = repo.undoRemittance(remit.kind, undoReason)
                            if (error == null) undoReason = ""
                        })
                    } else {
                        WbNote("Drafts are unconstrained and may overlap. Submit freezes the P&L snapshot.")
                        Spacer(Modifier.height(8.dp))
                        WbPrimary(text = "Submit ${remit.kind.name} remittance",
                            onClick = { repo.submitRemittance(remit.kind) })
                    }
                    WbFieldError(error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        WbPanel {
            Column {
                WbSectionTitle("COMMISSION SPLIT")
                Text("Product commissions pool per branch day and split equally among every practitioner " +
                    "and coordinator clocked in at the sold_at time. Manual inclusions/exclusions can " +
                    "override the split. Separate from compensation — never part of remittance.",
                    color = WbColors.Ink, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun WbTeam(repo: WallboardFakeRepo) {
    Column {
        Text("TEAM", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Roles bundle capabilities. Slot 1 sorts first; relief sorts after home slots.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.users.sortedBy { it.slot }.forEach { user ->
            WbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${user.slot}", color = WbColors.Amber, fontSize = 28.sp,
                        fontWeight = FontWeight.Black)
                    WbSpacerRow()
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = WbColors.Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(user.role.name, color = roleColor(user.role), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(repo.branches.first { it.id == user.homeBranchId }.name,
                        color = WbColors.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        WbPanel {
            Column {
                WbSectionTitle("ROLE BUNDLES (FAKE)")
                Text("PRACTITIONER logs sessions · COORDINATOR owns finance + PAST/REMITTED edits · " +
                    "MANAGER adds users + delegates · ACCOUNTANT reads everything, edits nothing · " +
                    "ONBOARDING is locked out.",
                    color = WbColors.Ink, fontSize = 14.sp)
            }
        }
    }
}

private fun roleColor(role: WbRole) = when (role) {
    WbRole.ONBOARDING -> WbColors.Red
    WbRole.PRACTITIONER -> WbColors.Green
    WbRole.COORDINATOR -> WbColors.Amber
    WbRole.MANAGER -> WbColors.Cyan
    WbRole.ACCOUNTANT -> WbColors.Muted
}

@Composable
fun WbMailbox(repo: WallboardFakeRepo) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MAILBOX", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f))
            WbGhost(text = "Mark all read", onClick = { repo.markAllRead() })
        }
        Text("Relief events name the branch and the day; tap through to that branch day (fake).",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        if (repo.notifications.isEmpty()) WbNote("Mailbox clear.")
        repo.notifications.forEach { note ->
            WbPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (note.read) "○" else "●", color = if (note.read) WbColors.Muted else WbColors.Amber,
                        fontSize = 20.sp)
                    WbSpacerRow()
                    Column(Modifier.weight(1f)) {
                        Text(note.title, color = WbColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text(note.body, color = WbColors.Muted, fontSize = 14.sp)
                    }
                    if (!note.read) WbGhost(text = "Read", onClick = { repo.markRead(note.id) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun WbAudit(repo: WallboardFakeRepo) {
    Column {
        Text("AUDIT LOG", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("Immutable: who changed what, when, and why. REMITTED-day edits would flag here.",
            color = WbColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        repo.audits.forEach { entry ->
            WbPanel {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.whenLabel, color = WbColors.Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(entry.action, color = WbColors.Ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(entry.target, color = WbColors.Ink, fontSize = 14.sp)
                    }
                    Text("by ${entry.who}" + (entry.reason?.let { " · reason: $it" } ?: ""),
                        color = WbColors.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun WbProfile(
    repo: WallboardFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    Column {
        Text("PROFILE", color = WbColors.Ink, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        WbPanel {
            Column {
                WbSectionTitle("SIGNED IN")
                Text(user?.name?.uppercase() ?: "NOBODY", color = WbColors.Ink, fontSize = 30.sp,
                    fontWeight = FontWeight.Black)
                Text("Role ${user?.role?.name} · home " +
                    (repo.branches.firstOrNull { it.id == user?.homeBranchId }?.name ?: "—"),
                    color = WbColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (repo.clockedIn) {
                        WbGhost(text = "Clock out", onClick = {
                            repo.clockedIn = false
                            repo.audit(user?.name ?: "wallboard", "CLOCK_OUT", repo.currentBranch.name)
                        })
                    }
                    WbPrimary(text = "Log out", onClick = {
                        repo.logout()
                        onLogout()
                    })
                }
            }
        }
    }
}
