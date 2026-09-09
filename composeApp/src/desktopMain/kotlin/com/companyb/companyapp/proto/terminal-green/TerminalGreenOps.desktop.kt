package com.companyb.companyapp.proto.terminalgreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// #768 — terminal-green screens part 2: finance/remittance, team, mail, audit, profile.

@Composable
fun TgFinance(repo: TerminalGreenRepo, user: TgUser, branch: TgBranch) {
    var undoTarget by remember { mutableStateOf<TgRemittance?>(null) }
    TgTitle("FINANCE // " + branch.name)
    TgNote("SESSION + PRODUCT flows independent. submit freezes an immutable snapshot.")
    TgRule()
    TgPanel {
        TgSection("DRAFTS")
        val drafts = repo.remittances.filter { it.stage == "DRAFT" }
        if (drafts.isEmpty()) {
            TgNote("no open drafts. spool one below.")
        }
        drafts.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TgText(
                    r.flow + " " + r.id + " :: P" + r.amount + " :: " + r.branchName,
                    size = 12,
                    modifier = Modifier.weight(1f),
                )
                TgTag("DRAFT", TgColors.Amber)
                Spacer(Modifier.width(6.dp))
                TgButton("SUBMIT") {
                    r.stage = "SUBMITTED"
                    repo.log(user.login, "submit " + r.id + " snapshot frozen")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TgGhost("+ SPOOL SESSION") {
                repo.remittances.add(TgRemittance("r-n" + repo.remittances.size, "SESSION", "DRAFT", 9600, branch.name))
                repo.log(user.login, "spool SESSION draft")
            }
            TgGhost("+ SPOOL PRODUCT") {
                repo.remittances.add(TgRemittance("r-n" + repo.remittances.size, "PRODUCT", "DRAFT", 3100, branch.name))
                repo.log(user.login, "spool PRODUCT draft")
            }
        }
    }
    TgPanel {
        TgSection("SUBMITTED / SNAPSHOTS")
        TgNote("undo within 48h with reason; later the snapshot is permanent.")
        repo.remittances.filter { it.stage == "SUBMITTED" }.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TgText(
                    r.flow + " " + r.id + " :: P" + r.amount + " :: age " + (r.ageHours ?: 0) + "h",
                    size = 12,
                    modifier = Modifier.weight(1f),
                )
                TgTag("SEALED", TgColors.Cyan)
                Spacer(Modifier.width(6.dp))
                val hours = r.ageHours ?: 0
                if (undoTarget?.id == r.id) {
                    TgGhost("CONFIRM UNDO") {
                        r.stage = "DRAFT"
                        undoTarget = null
                        repo.log(user.login, "undo " + r.id + " reason=duplicate-batch")
                    }
                } else {
                    TgGhost("UNDO…", enabled = hours <= 48) { undoTarget = r }
                }
            }
            if ((r.ageHours ?: 0) > 48) {
                TgNote(r.id + ": window closed — snapshot permanent.")
            }
        }
    }
    if (undoTarget != null) {
        TgPanel {
            TgText(
                "undo " + undoTarget!!.id + "? returns to DRAFT, unlocks days, deletes snapshot.",
                size = 12,
                color = TgColors.Amber,
            )
            TgNote("reason recorded in audit (fake reason: duplicate-batch).")
        }
    }
    TgPanel {
        TgSection("COMMISSION SPLIT")
        TgNote("product commissions pool per branch day, split equally among all practitioners")
        TgNote("+ coordinators clocked in at sold_at. manual include/exclude overrides.")
        TgNote("separate from compensation, outside remittance.")
        TgText("pool P5200 / 4 on-duty = P1300 each :: ANA, BEN, +2", size = 12)
    }
}

@Composable
fun TgTeam(repo: TerminalGreenRepo, user: TgUser) {
    TgTitle("TEAM // USERS + ROLES")
    TgNote("roles are capability bundles. MANAGER superset of Coordinator. ONBOARDING = empty bundle.")
    TgRule()
    repo.users.forEach { u ->
        TgPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TgText(
                    "$ " + u.login + " :: " + u.name,
                    size = 13,
                    weight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TgTag(u.role, if (u.locked) TgColors.Red else TgColors.PhosphorDim)
            }
            TgText("home=" + u.homeBranchId, size = 12, color = TgColors.Muted)
            val caps = if (u.capabilities.isEmpty()) "(none — locked)" else u.capabilities.joinToString(", ")
            TgText("caps: " + caps, size = 12)
            if (u.locked && user.role == "MANAGER") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TgButton("GRANT PRACTITIONER") {
                        repo.log(user.login, "grant Practitioner to " + u.login + " (fake: bundle preview only)")
                    }
                    TgGhost("DEACTIVATE") {
                        repo.log(user.login, "deactivate " + u.login + " (fake: login would block, JWT killed)")
                    }
                }
            }
        }
    }
}

@Composable
fun TgMail(repo: TerminalGreenRepo, user: TgUser) {
    TgTitle("MAIL // NOTIFICATIONS")
    TgNote("per-recipient mailbox. read rows kept forever. relief events name branch + day.")
    TgRule()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TgText(
            "inbox: " + repo.notices.count { !it.read } + " unread / " + repo.notices.size + " total",
            size = 12,
            modifier = Modifier.weight(1f),
        )
        TgGhost("MARK-ALL-READ") {
            repo.notices.forEach { it.read = true }
            repo.log(user.login, "mark all mail read")
        }
    }
    Spacer(Modifier.height(8.dp))
    repo.notices.forEach { n ->
        TgPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TgText(
                    (if (n.read) "  " else "● ") + n.title,
                    size = 13,
                    weight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (!n.read) TgTag("UNREAD", TgColors.Amber)
            }
            TgText(n.body, size = 12, color = TgColors.Muted)
            if (!n.read) {
                TgLink("mark read") {
                    n.read = true
                    repo.log(user.login, "read mail " + n.id)
                }
            }
        }
    }
}

@Composable
fun TgAudit(repo: TerminalGreenRepo) {
    TgTitle("AUDIT LOG")
    TgNote("every action appends. newest first. REMITTED-day edits flagged.")
    TgRule()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repo.audit.forEach { e ->
            TgText("#" + e.seq.toString().padStart(4, '0') + "  " + e.actor.padEnd(8) + "  " + e.action, size = 12)
        }
    }
}

@Composable
fun TgProfile(repo: TerminalGreenRepo, user: TgUser, branch: TgBranch, clockedIn: Boolean, onLogout: () -> Unit) {
    TgTitle("PROFILE")
    TgRule()
    TgPanel {
        TgSection("OPERATOR")
        TgText(user.name + " (" + user.login + ")", size = 14, weight = FontWeight.Bold)
        TgText("role: " + user.role + " :: home: " + user.homeBranchId, size = 12)
        TgText("branch: " + branch.name + " [" + branch.dayStatus.name + "]", size = 12)
        TgText("clock: " + if (clockedIn) "IN" else "OUT", size = 12)
        TgRule()
        TgSection("CAPABILITY BUNDLE")
        if (user.capabilities.isEmpty()) {
            TgText("(empty — account locked)", size = 12, color = TgColors.Red)
        } else {
            user.capabilities.forEach { TgText("  [+] " + it, size = 12) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TgButton("LOGOUT") { onLogout() }
        TgGhost("CLOCK-OUT + LOGOUT") {
            repo.log(user.login, "clock-out + logout")
            onLogout()
        }
    }
}
