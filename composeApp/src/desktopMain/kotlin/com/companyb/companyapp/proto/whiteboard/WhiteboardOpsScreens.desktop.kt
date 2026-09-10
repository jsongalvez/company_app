package com.companyb.companyapp.proto.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #844 — whiteboard ops screens: crew roles, notification mailbox, audit log, profile.

@Composable
fun WhiteboardCrew(repo: WhiteboardFakeRepo) {
    BoardPanel {
        MarkerTitle("Crew magnets", BoardColors.MarkerPurple)
        Spacer(Modifier.height(6.dp))
        BoardNote("Roles bundle capabilities. ONBOARDING holds an empty bundle — locked until MANAGE_USERS grants a real role.")
    }
    val iAmManager = repo.currentUser?.role == BoardRole.MANAGER
    repo.users.forEach { user ->
        val magnet = when (user.role) {
            BoardRole.ONBOARDING -> BoardColors.InkSoft
            BoardRole.PRACTITIONER -> BoardColors.MagnetTeal
            BoardRole.COORDINATOR -> BoardColors.MarkerBlue
            BoardRole.MANAGER -> BoardColors.MagnetYellow
            BoardRole.ACCOUNTANT -> BoardColors.MagnetPink
        }
        MagnetCard(magnet = magnet) {
            Text(user.name, color = BoardColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            BoardNote("${user.role} · home ${repo.branchName(user.homeBranchId)} · ${capabilityLine(user.role)}")
            if (user.role == BoardRole.ONBOARDING && iAmManager) {
                Spacer(Modifier.height(6.dp))
                BoardCta("Grant Practitioner") {
                    user.role = BoardRole.PRACTITIONER
                    repo.audit("ROLE_GRANT", "${user.name} → PRACTITIONER", "MANAGE_USERS")
                    repo.notify("Role granted", "${user.name} is now a Practitioner.")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (!iAmManager) {
        BoardPanel(tape = BoardColors.MagnetSky) {
            BoardNote("Sign in as A. Villanueva (MANAGER) to demo the ONBOARDING → Practitioner grant.")
        }
    }
}

private fun capabilityLine(role: BoardRole): String = when (role) {
    BoardRole.ONBOARDING -> "no capabilities — locked"
    BoardRole.PRACTITIONER -> "sessions, inventory, clients at duty branches"
    BoardRole.COORDINATOR -> "finance + remittance, sole editor of PAST/REMITTED"
    BoardRole.MANAGER -> "coordinator powers + users + delegate assignment"
    BoardRole.ACCOUNTANT -> "read-only sales and data, all branches"
}

@Composable
fun WhiteboardBell(repo: WhiteboardFakeRepo) {
    BoardPanel {
        BoardSectionRow("Mailbox") {
            BoardLink("mark all read") {
                repo.notices.forEach { it.read = true }
                repo.audit("NOTICE_READ_ALL", "${repo.notices.size} notices")
            }
        }
        BoardNote("Broadcasts, grants, and snapshot news land here. Unread magnets glow yellow.")
    }
    val unread = repo.notices.count { !it.read }
    if (unread == 0) {
        BoardPanel {
            BoardNote("All caught up — every magnet read.")
        }
    }
    repo.notices.forEach { notice ->
        MagnetCard(magnet = if (notice.read) BoardColors.CardLine else BoardColors.MagnetYellow) {
            Text(notice.title, color = BoardColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            BoardNote(notice.body)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardChip(if (notice.read) "read" else "unread", if (notice.read) BoardColors.InkSoft else BoardColors.MarkerOrange)
                if (!notice.read) {
                    BoardLink("mark read") { notice.read = true }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun WhiteboardLedger(repo: WhiteboardFakeRepo) {
    BoardPanel {
        MarkerTitle("Audit tape", BoardColors.MarkerRed)
        Spacer(Modifier.height(6.dp))
        BoardNote("Every pin, void, grant, and snapshot writes a line of tape. Newest first.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repo.audits.forEach { entry ->
            MagnetCard(magnet = BoardColors.MarkerRed) {
                Text(
                    "${entry.whenLabel} · ${entry.action}",
                    color = BoardColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                BoardNote("${entry.who} → ${entry.target}${if (entry.reason != null) " · ${entry.reason}" else ""}")
            }
        }
    }
}

@Composable
fun WhiteboardMe(repo: WhiteboardFakeRepo, onLogout: () -> Unit) {
    val user = repo.currentUser
    BoardPanel {
        MarkerTitle("My magnet", BoardColors.MarkerBlue)
        Spacer(Modifier.height(6.dp))
        if (user == null) {
            BoardNote("Not signed in.")
        } else {
            Text(user.name, color = BoardColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            BoardNote("${user.role} · home ${repo.branchName(user.homeBranchId)}")
            Spacer(Modifier.height(4.dp))
            BoardNote(if (repo.clockedIn) "On duty at ${repo.branchName(repo.clockBranchId)}." else "Off duty.")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    BoardCta("Clock out") { repo.clockOut() }
                } else {
                    BoardCta("Clock in at ${repo.currentBranch.name}") {
                        repo.clockIn(repo.currentBranchId, relief = repo.currentBranchId != user.homeBranchId)
                    }
                }
                BoardGhost("Log out") { onLogout() }
            }
        }
    }
    BoardPanel(tape = BoardColors.MagnetTeal) {
        MarkerTitle("Day cheat-sheet", BoardColors.MarkerGreen)
        Spacer(Modifier.height(6.dp))
        val here = repo.sessions.filter { it.branchId == repo.currentBranchId }
        Text(
            "${here.count { it.status == BoardSessionStatus.PENDING }} to-do · " +
                "${here.count { it.status == BoardSessionStatus.COMPLETED }} done · " +
                "${repo.notices.count { !it.read }} unread · day ${repo.dayStatus}",
            color = BoardColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        BoardNote("Boundary 04:00 Asia/Manila. Snapshot rule: undo needs a reason inside 48 hours.")
    }
}
