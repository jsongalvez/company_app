package com.companyb.companyapp.proto.nightshift

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// #780 — night-shift finance, team, mail, audit, profile. Remittance moves
// DRAFT → SUBMITTED → SEALED with a 48h undo window; mail stays quiet.

@Composable
fun NsFinance(repo: NightShiftRepo, user: NsUser, branch: NsBranch) {
    NsTitle("finance")
    NsNote("SESSION + PRODUCT drafts → submit → sealed snapshot · Undo within 48h")
    NsRule()
    repo.remittances.forEach { r ->
        NsPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NsText(r.id + " · " + r.flow + " · " + r.branchName, size = 14, weight = FontWeight.Bold)
                    NsText(
                        "₱" + r.amount + (r.ageHours?.let { " · submitted $it h ago" } ?: " · unsent draft"),
                        size = 12,
                        color = NsColors.Taupe,
                    )
                }
                NsTag(r.stage, nsStatusColor(r.stage))
            }
            Spacer(Modifier.height(8.dp))
            NsRowButtons {
                if (r.stage == "DRAFT") {
                    NsGhost("SUBMIT") {
                        r.stage = "SUBMITTED"
                        repo.log(user.login, "submit remittance " + r.id)
                    }
                }
                if (r.stage == "SUBMITTED") {
                    NsGhost("SEAL SNAPSHOT") {
                        r.stage = "SEALED"
                        repo.log(user.login, "seal remittance " + r.id)
                    }
                }
                if (r.stage == "SEALED" && (r.ageHours ?: 0) <= 48) {
                    NsGhost("UNDO ≤48H") {
                        r.stage = "DRAFT"
                        repo.log(user.login, "undo remittance " + r.id + " within 48h")
                    }
                }
            }
            if (r.stage == "SEALED" && (r.ageHours ?: 0) > 48) {
                NsNote("sealed 72h+ — permanent. corrections go through a new draft, never edits.")
            }
        }
    }
    NsPanel {
        NsText("COMMISSION SPLIT", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
        NsText("overnight sessions split practitioner / branch / relief-cover in one note.", size = 13)
        NsNote("example: s-02 ₱1500 → Ana 60 · Makati 30 · cover pool 10. split posts when the session COMPLETES.")
    }
    NsNote("branch day here: " + branch.name + " is " + branch.dayStatus.name + " — sealed days reject new drafts.")
}

@Composable
fun NsTeam(repo: NightShiftRepo, user: NsUser) {
    val isManager = user.role == "MANAGER"
    NsTitle("team")
    NsNote("roles carry capabilities · ONBOARDING carries none" + if (!isManager) " · MANAGER view required to grant" else "")
    NsRule()
    repo.users.forEach { u ->
        NsPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NsText(u.name + " · " + u.role, size = 14, weight = FontWeight.Bold)
                    NsText(
                        if (u.capabilities.isEmpty()) "caps: none" else "caps: " + u.capabilities.joinToString(", "),
                        size = 12,
                        color = NsColors.Taupe,
                    )
                }
                if (u.locked) NsTag("LOCKED", NsColors.Rose) else NsTag("ACTIVE", nsStatusColor("COMPLETED"))
            }
            if (isManager && u.locked) {
                Spacer(Modifier.height(8.dp))
                NsGhost("GRANT PRACTITIONER") {
                    u.locked = false
                    repo.log(user.login, "grant Practitioner to " + u.login)
                }
            }
            if (isManager && !u.locked && u.id != user.id) {
                Spacer(Modifier.height(8.dp))
                NsGhost("DEACTIVATE") {
                    u.locked = true
                    repo.log(user.login, "deactivate " + u.login)
                }
            }
        }
    }
    NsPanel {
        NsText("WHY MANAGER IS A SUPERSET", size = 11, weight = FontWeight.Bold, color = NsColors.Ember)
        NsNote("MANAGE_USERS + ASSIGN_DELEGATES + SUBMIT_REMITTANCE + EDIT_PAST. coordinators edit past days, never people.")
    }
}

@Composable
fun NsMail(repo: NightShiftRepo) {
    val unread = repo.notices.count { !it.read }
    NsTitle("mailbox")
    NsNote(if (repo.quietHours) "quiet hours ON — alerts arrive dim until 06:00." else "quiet hours OFF — alerts at full glow.")
    NsRule()
    NsPanel {
        NsRowButtons {
            NsText("quiet hours", size = 13, modifier = Modifier.weight(1f))
            NsGhost(if (repo.quietHours) "ON" else "OFF") {
                repo.quietHours = !repo.quietHours
                repo.touch()
            }
            Spacer(Modifier.width(8.dp))
            NsGhost("MARK ALL READ ($unread)") {
                repo.notices.forEach { it.read = true }
                repo.touch()
            }
        }
    }
    if (unread == 0) NsNote("all caught up. the night is yours.")
    repo.notices.forEach { n ->
        if (repo.quietHours) {
            NsQuietAlert((if (n.read) "· " else "◦ ") + n.title + " — " + n.body, n.read)
            Spacer(Modifier.height(6.dp))
            if (!n.read) NsLink("mark read") {
                n.read = true
                repo.touch()
            }
        } else {
            NsPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NsText(n.title, size = 14, weight = FontWeight.Bold)
                        NsText(n.body, size = 12, color = NsColors.Taupe)
                    }
                    if (!n.read) NsTag("NEW", NsColors.Ember) else NsTag("READ", NsColors.Taupe)
                }
                if (!n.read) {
                    Spacer(Modifier.height(6.dp))
                    NsGhost("MARK READ") {
                        n.read = true
                        repo.touch()
                    }
                }
            }
        }
    }
}

@Composable
fun NsAudit(repo: NightShiftRepo) {
    NsTitle("audit log")
    NsNote("every tap above lands here, newest first. local only.")
    NsRule()
    if (repo.audit.isEmpty()) NsNote("nothing yet.")
    repo.audit.forEach { e ->
        NsPanel {
            NsText("#" + e.seq + " · " + e.actor, size = 12, weight = FontWeight.Bold, color = NsColors.Ember)
            NsText(e.action, size = 13)
        }
    }
}

@Composable
fun NsProfile(
    repo: NightShiftRepo,
    user: NsUser,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    NsTitle("profile")
    NsNote("role bundle for " + user.login)
    NsRule()
    NsPanel {
        NsText(user.name, size = 16, weight = FontWeight.Bold)
        NsText(user.role + " · home " + repo.branchName(user.homeBranchId), size = 13)
        NsNote(if (user.capabilities.isEmpty()) "caps: none (ONBOARDING)" else "caps: " + user.capabilities.joinToString(", "))
        Spacer(Modifier.height(6.dp))
        NsText("clock: " + if (repo.clockedIn) "IN since " + repo.clockInAt else "OUT", size = 13)
    }
    Spacer(Modifier.height(10.dp))
    NsRowButtons {
        NsGhost("LOGOUT") { onLogout() }
        NsGhost("CLOCK-OUT + LOGOUT") {
            repo.clockedIn = false
            repo.log(user.login, "clock out + logout")
            onLogout()
        }
    }
    Spacer(Modifier.height(6.dp))
    NsLink("exit prototype", onExit)
}
