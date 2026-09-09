package com.companyb.companyapp.proto.projectormode

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

// #777 — projector money + org screens: remittance, commission note, team, mailbox,
// audit, profile. Same giant-type stage language throughout.

@Composable
fun PmFinance(repo: ProjectorFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text("THE MONEY", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("FINANCE", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text("SESSION and PRODUCT flow independently.", color = PmColors.Muted, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))
        repo.remittances.forEach { remit ->
            PmPanel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            remit.kind.name,
                            color = PmColors.Ink,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            remit.state.name,
                            color = if (remit.state == PmRemitState.SUBMITTED) PmColors.Green else PmColors.Focus,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                        )
                    }
                    Text("₱${remit.draftTotal}", color = PmColors.Focus, fontSize = 72.sp,
                        fontWeight = FontWeight.Black)
                    if (remit.state == PmRemitState.SUBMITTED) {
                        PmNote(
                            "Snapshot ${remit.snapshotId} sealed at ₱${remit.snapshotTotal}: immutable — " +
                                "later edits never rewrite it. Undo closes 48h after submission.",
                        )
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = undoReason,
                            onValueChange = { undoReason = it; error = null },
                            label = { Text("Undo reason (audit trail)") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PmPrimary(text = "Undo within 48h", onClick = {
                            error = repo.undoRemittance(remit.kind, undoReason)
                            if (error == null) undoReason = ""
                        })
                    } else {
                        PmNote("Drafts are unconstrained and may overlap. Submit freezes the P&L snapshot.")
                        Spacer(Modifier.height(12.dp))
                        PmPrimary(
                            text = "Submit ${remit.kind.name}",
                            onClick = { repo.submitRemittance(remit.kind) },
                        )
                    }
                    PmFieldError(error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PmPanel {
            Column {
                PmSectionTitle("Commission split")
                Text(
                    "Product commissions pool per branch day and split equally among every practitioner " +
                        "and coordinator clocked in at the sold_at time. Manual inclusions/exclusions can " +
                        "override the split. Separate from compensation — never part of remittance.",
                    color = PmColors.Ink,
                    fontSize = 24.sp,
                )
            }
        }
    }
}

@Composable
fun PmTeam(repo: ProjectorFakeRepo) {
    Column {
        Text("THE CREW", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("TEAM", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text("Roles bundle capabilities. Slot 1 sorts first.", color = PmColors.Muted, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))
        repo.users.sortedBy { it.slot }.forEach { user ->
            PmPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${user.slot}", color = PmColors.Focus, fontSize = 44.sp, fontWeight = FontWeight.Black)
                    PmSpacerRow()
                    Column(Modifier.weight(1f)) {
                        Text(user.name, color = PmColors.Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            user.role.name,
                            color = pmRoleColor(user.role),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        repo.branches.first { it.id == user.homeBranchId }.name,
                        color = PmColors.Muted,
                        fontSize = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        PmPanel {
            Column {
                PmSectionTitle("Role bundles (fake)")
                Text(
                    "PRACTITIONER logs sessions · COORDINATOR owns finance + PAST/REMITTED edits · " +
                        "MANAGER adds users + delegates · ACCOUNTANT reads everything, edits nothing · " +
                        "ONBOARDING is locked out.",
                    color = PmColors.Ink,
                    fontSize = 24.sp,
                )
            }
        }
    }
}

private fun pmRoleColor(role: PmRole) = when (role) {
    PmRole.ONBOARDING -> PmColors.Red
    PmRole.PRACTITIONER -> PmColors.Green
    PmRole.COORDINATOR -> PmColors.Focus
    PmRole.MANAGER -> PmColors.Cyan
    PmRole.ACCOUNTANT -> PmColors.Muted
}

@Composable
fun PmMailbox(repo: ProjectorFakeRepo) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INBOX", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("MAILBOX", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
            }
            PmGhost(text = "Mark all read", onClick = { repo.markAllRead() })
        }
        Text(
            "Relief events name the branch and the day.",
            color = PmColors.Muted,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        if (repo.notifications.isEmpty()) PmNote("Mailbox clear.")
        repo.notifications.forEach { note ->
            PmPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (note.read) "○" else "●",
                        color = if (note.read) PmColors.Muted else PmColors.Focus,
                        fontSize = 36.sp,
                    )
                    PmSpacerRow()
                    Column(Modifier.weight(1f)) {
                        Text(note.title, color = PmColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text(note.body, color = PmColors.Muted, fontSize = 22.sp)
                    }
                    if (!note.read) PmGhost(text = "Read", onClick = { repo.markRead(note.id) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PmAudit(repo: ProjectorFakeRepo) {
    Column {
        Text("RECEIPTS", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("AUDIT LOG", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Text(
            "Immutable: who changed what, when, and why.",
            color = PmColors.Muted,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        repo.audits.forEach { entry ->
            PmPanel {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(entry.whenLabel, color = PmColors.Focus,
                            fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text(entry.action, color = PmColors.Ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    Text(entry.target, color = PmColors.Ink, fontSize = 24.sp)
                    Text(
                        "by ${entry.who}" + (entry.reason?.let { " · reason: $it" } ?: ""),
                        color = PmColors.Muted,
                        fontSize = 22.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PmProfile(
    repo: ProjectorFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    Column {
        Text("YOU", color = PmColors.Focus, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("PROFILE", color = PmColors.Ink, fontSize = 72.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        PmPanel {
            Column {
                PmSectionTitle("Signed in")
                Text(
                    user?.name?.uppercase() ?: "NOBODY",
                    color = PmColors.Ink,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Role ${user?.role?.name} · home " +
                        (repo.branches.firstOrNull { it.id == user?.homeBranchId }?.name ?: "—"),
                    color = PmColors.Muted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (repo.clockedIn) {
                        PmGhost(text = "Clock out", onClick = {
                            repo.clockedIn = false
                            repo.audit(user?.name ?: "projector", "CLOCK_OUT", repo.currentBranch.name)
                        })
                    }
                    PmPrimary(text = "Log out", onClick = {
                        repo.logout()
                        onLogout()
                    })
                }
            }
        }
    }
}
