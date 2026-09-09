package com.companyb.companyapp.proto.calendarops

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.companyb.companyapp.util.logInfo

// #775 — calendar-ops money and back-office: remittance, team, mailbox, audit, profile.

@Composable
fun CoFinance(repo: CalendarOpsFakeRepo) {
    Column {
        CoTitle("FINANCE")
        CoSubtitle("SESSION and PRODUCT flows run independently: draft → submit → sealed snapshot → undo in 48h.")
        CoGap()
        CoRemitCard(repo = repo, kind = CoRemitKind.SESSION)
        Spacer(Modifier.height(10.dp))
        CoRemitCard(repo = repo, kind = CoRemitKind.PRODUCT)
        Spacer(Modifier.height(10.dp))
        CoCard {
            CoSection("COMMISSION SPLIT")
            CoNote("Session income pools per branch day and splits equally over staff clocked in " +
                "at sold_at. Relief payouts come from the relief branch drawer.")
        }
    }
}

@Composable
private fun CoRemitCard(repo: CalendarOpsFakeRepo, kind: CoRemitKind) {
    val remit = if (kind == CoRemitKind.SESSION) repo.remitSession else repo.remitProduct
    var amount by remember(kind) { mutableStateOf(remit.draftTotal.toString()) }
    var reason by remember(kind) { mutableStateOf("") }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    val user = repo.currentUser?.name ?: "planner"
    CoCard {
        CoSection("${kind.name} REMITTANCE — ${remit.state.name}")
        Spacer(Modifier.height(6.dp))
        when (remit.state) {
            CoRemitState.DRAFT -> {
                TextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Draft total (PHP)") })
                Spacer(Modifier.height(8.dp))
                CoButtonRow {
                    CoPrimary(text = "Save draft") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Enter a numeric draft total."
                        } else {
                            repo.updateRemit(kind, remit.copy(draftTotal = total), user, "DRAFT_SAVE")
                            error = null
                        }
                    }
                    CoGhost(text = "Submit") {
                        val total = amount.toIntOrNull()
                        if (total == null) {
                            error = "Enter a numeric draft total."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = CoRemitState.SUBMITTED,
                                    draftTotal = total,
                                    snapshotId = "SN-${kind.name.take(1)}241",
                                    snapshotTotal = total,
                                ),
                                user,
                                "SUBMIT",
                            )
                            logInfo("CalendarOpsProto", "${kind.name} remittance submitted")
                            error = null
                        }
                    }
                }
                CoNote("Drafts are unconstrained and may overlap. Submit freezes an immutable snapshot.")
            }
            CoRemitState.SUBMITTED -> {
                Text("Snapshot ${remit.snapshotId} sealed at PHP ${remit.snapshotTotal}",
                    color = CoColors.Remitted, fontSize = 15.sp, fontWeight = FontWeight.Black)
                CoNote("Later edits to sessions never rewrite a sealed snapshot. Undo deletes the snapshot " +
                    "and returns the remittance to Draft — within 48 hours of submission, with a reason.")
                Spacer(Modifier.height(8.dp))
                TextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Undo reason (required)") })
                Spacer(Modifier.height(8.dp))
                CoButtonRow {
                    CoPrimary(text = "Undo (48h)") {
                        if (reason.isBlank()) {
                            error = "A reason is required to undo."
                        } else {
                            repo.updateRemit(
                                kind,
                                remit.copy(
                                    state = CoRemitState.DRAFT,
                                    snapshotId = null,
                                    snapshotTotal = null,
                                    undoReason = reason.trim(),
                                ),
                                user,
                                "UNDO",
                            )
                            reason = ""
                            error = null
                        }
                    }
                }
            }
        }
        CoError(error)
    }
}

@Composable
fun CoTeam(repo: CalendarOpsFakeRepo) {
    Column {
        CoTitle("TEAM")
        CoSubtitle("Users by home branch with role bundles. MANAGER and SUPERUSER paths grant relief cover.")
        CoGap()
        repo.branches.forEach { branch ->
            CoCard {
                CoSection(branch.name.uppercase() + " — " + branch.kind.name)
                Spacer(Modifier.height(6.dp))
                repo.users.filter { it.homeBranchId == branch.id }.forEach { user ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, color = CoColors.Ink, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold)
                            Text(user.role.name, color = CoColors.Muted, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        if (repo.currentUserId == user.id) {
                            Text("● YOU", color = CoColors.Open, fontSize = 12.sp,
                                fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        CoCard {
            CoSection("ROLE NOTES")
            CoNote("ONBOARDING holds zero capabilities. ACCOUNTANT reads every branch but edits none. " +
                "Coordinators solely edit PAST and REMITTED records; MANAGER adds user management.")
        }
    }
}

@Composable
fun CoMailbox(repo: CalendarOpsFakeRepo) {
    val user = repo.currentUser?.name ?: "planner"
    Column {
        CoTitle("MAILBOX")
        CoSubtitle("Notifications — mark read one by one or clear the whole tray.")
        CoGap()
        CoButtonRow {
            CoGhost(text = "Mark all read") {
                repo.notifications.forEachIndexed { i, note -> repo.notifications[i] = note.copy(read = true) }
                repo.audit(user, "MAIL_READ_ALL", "notifications")
            }
        }
        Spacer(Modifier.height(6.dp))
        repo.notifications.forEach { note ->
            CoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (note.read) "" else "● ") + note.title,
                            color = CoColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        CoNote(note.body)
                    }
                    if (!note.read) {
                        CoGhost(text = "Mark read") {
                            val i = repo.notifications.indexOfFirst { it.id == note.id }
                            repo.notifications[i] = note.copy(read = true)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun CoAuditList(repo: CalendarOpsFakeRepo) {
    Column {
        CoTitle("AUDIT LOG")
        CoSubtitle("Every prototype mutation prepends who / action / target / reason.")
        CoGap()
        repo.audits.forEach { entry ->
            CoCard {
                Text("${entry.whenLabel} · ${entry.who} · ${entry.action}",
                    color = CoColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                CoNote(entry.target + (entry.reason?.let { " — $it" } ?: ""))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun CoProfile(repo: CalendarOpsFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    Column {
        CoTitle("PROFILE")
        CoSubtitle("Current user, duty state, logout.")
        CoGap()
        CoCard {
            if (user == null) {
                CoNote("Nobody is signed in.")
            } else {
                Text(user.name, color = CoColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("${user.role.name} · home ${repo.branches.first { it.id == user.homeBranchId }.name}",
                    color = CoColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                CoNote(if (repo.clockedIn) "On duty at ${repo.currentBranch.name}." else "Off duty.")
                Spacer(Modifier.height(12.dp))
                CoButtonRow {
                    if (repo.clockedIn) {
                        CoGhost(text = "Clock out") {
                            repo.clockedIn = false
                            repo.audit(user.name, "CLOCK_OUT", repo.currentBranch.name)
                        }
                    }
                    CoPrimary(text = "Log out") {
                        repo.audit(user.name, "LOGOUT", user.name)
                        repo.logout()
                        logInfo("CalendarOpsProto", "signed out")
                        onLogout()
                    }
                }
            }
        }
    }
}
