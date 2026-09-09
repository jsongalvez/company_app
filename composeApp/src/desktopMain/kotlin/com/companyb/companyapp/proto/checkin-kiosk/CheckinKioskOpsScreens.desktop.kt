package com.companyb.companyapp.proto.checkinkiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #782 — checkin-kiosk ops: crew, bell, ledger, me. Fake directory, mailbox, audit trail, sign-out.

private fun CheckinRole.blurb(): String = when (this) {
    CheckinRole.ONBOARDING -> "Locked — empty bundle until MANAGE_USERS grants a role"
    CheckinRole.PRACTITIONER -> "Hands on guests · home branches + relief days"
    CheckinRole.COORDINATOR -> "Owns the till · sole editor of PAST + REMITTED days"
    CheckinRole.MANAGER -> "Coordinator + people + cover assignments"
    CheckinRole.ACCOUNTANT -> "Reads every branch · changes nothing"
}

@Composable
fun CheckinCrew(repo: CheckinFakeRepo) {
    CheckinHeading("The crew.", 44)
    CheckinSub("Who works here, in branch-slot order. Roles are bundles of what-you-may-do.")
    repo.users.sortedBy { it.slot }.forEach { user ->
        CheckinPanel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(user.name, color = CheckinColors.Cream, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${user.role} · slot ${user.slot} · home ${repo.branches.firstOrNull { it.id == user.homeBranchId }?.name}",
                        color = CheckinColors.Marigold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(user.role.blurb(), color = CheckinColors.FaintOnNight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                if (user.id == repo.currentUserId) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(CheckinColors.Mint)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("YOU", color = CheckinColors.NightDeep, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CheckinBell(repo: CheckinFakeRepo) {
    val unread = repo.notifications.count { !it.read }
    CheckinHeading("The bell. ($unread new)", 44)
    CheckinSub("Everything the lobby wants you to know. Tap to hush one, or hush them all.")
    if (unread > 0) {
        CheckinBigButton("Hush them all", onClick = { repo.markAllRead() })
    }
    if (repo.notifications.isEmpty()) {
        CheckinNoteCard("Quiet lobby. New arrivals, grants, and snapshots ring here.")
    }
    repo.notifications.forEach { note ->
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (note.read) CheckinColors.NightDeep else CheckinColors.Cream)
                .clickable { repo.markRead(note.id) }
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        note.title,
                        color = if (note.read) CheckinColors.FaintOnNight else CheckinColors.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (!note.read) {
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(CheckinColors.Marigold)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("NEW", color = CheckinColors.MarigoldInk, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text(
                    note.body,
                    color = if (note.read) CheckinColors.FaintOnNight else CheckinColors.InkSoft,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun CheckinLedger(repo: CheckinFakeRepo) {
    CheckinHeading("The ledger.", 44)
    CheckinSub("Every tap that mattered, newest first — who did what to which, and why.")
    if (repo.audits.isEmpty()) {
        CheckinNoteCard("Nothing yet. Check someone in and watch this fill.")
    }
    repo.audits.forEach { entry ->
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CheckinColors.Panel)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.whenLabel, color = CheckinColors.Marigold, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f)) {
                Text("${entry.who} · ${entry.action}", color = CheckinColors.Cream, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(entry.target, color = CheckinColors.FaintOnNight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (entry.reason != null) {
                    Text("Why: ${entry.reason}", color = CheckinColors.FaintOnNight, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun CheckinMe(
    repo: CheckinFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    CheckinHeading("That's you.", 44)
    if (user == null) {
        CheckinNoteCard("Nobody signed in — the desk is unattended.")
        return
    }
    CheckinPanel {
        Text(user.name, color = CheckinColors.Cream, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(
            "${user.role} · home ${repo.branches.firstOrNull { it.id == user.homeBranchId }?.name} · slot ${user.slot}",
            color = CheckinColors.Marigold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(user.role.blurb(), color = CheckinColors.FaintOnNight, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Duty: ${if (repo.clockedIn) "clocked in at ${repo.currentBranch.name}" else "clocked out"} · day ${repo.dayStatus} · ${repo.operationalDate}",
            color = CheckinColors.Cream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        CheckinBigButton(if (repo.clockedIn) "Clock out" else "Clock in", onClick = {
            repo.clockedIn = !repo.clockedIn
            repo.audit(user.name, if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranch.name)
        })
    }
    CheckinGhostButton("Sign out of the lobby") {
        repo.logout()
        onLogout()
    }
    CheckinNoteCard("Signing out clocks you out too. The line, the till, and the ledger stay — they're the branch's, not yours.")
}
