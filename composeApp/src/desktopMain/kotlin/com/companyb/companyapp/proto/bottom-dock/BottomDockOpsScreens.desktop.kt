package com.companyb.companyapp.proto.bottomdock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #810 — bottom-dock ops: Crew, Bell mailbox, Ledger, Me profile.

@Composable
fun DockCrew(repo: DockFakeRepo) {
    DockHeading("Who is on this Mac?", 34)
    DockSub("Branch-slot order. ONBOARDING rows stay locked; YOU is marked.")
    Spacer(Modifier.height(12.dp))
    repo.users.sortedBy { it.slot }.forEach { user ->
        val you = user.id == repo.currentUserId
        val locked = user.role == DockRole.ONBOARDING
        DockPickRow(
            title = "${user.name}${if (you) " · YOU" else ""}${if (locked) " · LOCKED" else ""}",
            subtitle = "${user.role} · home ${repo.branches.firstOrNull {
                it.id == user.homeBranchId
            }?.name} · slot ${user.slot}",
            picked = you,
            onPick = {},
            trailing = user.role.name.take(4),
        )
        Spacer(Modifier.height(8.dp))
    }
    DockNoteCard(
        "MANAGER is a superset of Coordinator: finance edits plus user management and delegate assignment. ACCOUNTANT reads everything, edits nothing.",
    )
}

@Composable
fun DockBell(repo: DockFakeRepo) {
    val unread = repo.notifications.count { !it.read }
    DockHeading("Bell — $unread unread.", 34)
    DockSub("Relief answers, sealed snapshots, day closures. Hush one or hush all.")
    Spacer(Modifier.height(12.dp))
    if (unread > 0) {
        DockGhostButton("Hush all") { repo.markAllRead() }
        Spacer(Modifier.height(12.dp))
    }
    if (repo.notifications.isEmpty()) {
        DockNoteCard("Silent. Suspiciously silent — open a session and the dock will chirp.")
    }
    repo.notifications.forEach { note ->
        DockWindow(title = if (note.read) note.title else "● ${note.title}") {
            Text(note.body, color = DockColors.InkSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            if (!note.read) {
                DockGhostButton("Hush this one") { repo.markRead(note.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun DockLedger(repo: DockFakeRepo) {
    DockHeading("Receipts for everything.", 34)
    DockSub("Every prototype mutation lands here: who, what, which, why.")
    Spacer(Modifier.height(12.dp))
    repo.audits.forEach { entry ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DockColors.Ink)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(entry.action, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "${entry.target} · ${entry.who} · ${entry.whenLabel}${entry.reason?.let { " · $it" } ?: ""}",
                color = DockColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DockMe(
    repo: DockFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    DockHeading("This seat.", 34)
    DockSub(
        user?.let {
            "${it.name} · ${it.role} · home ${repo.branches.firstOrNull { b ->
                b.id == it.homeBranchId
            }?.name}"
        }
            ?: "Nobody yet.",
    )
    Spacer(Modifier.height(12.dp))
    val clockBody =
        if (repo.clockedIn) {
            "Clock out to freeze your line at ${repo.currentBranch.name}."
        } else {
            "Clock in to start taking the line at ${repo.currentBranch.name}."
        }
    DockSticky(
        title = if (repo.clockedIn) "On the clock" else "Off the clock",
        body = clockBody,
        tint = DockColors.Mint,
    )
    Spacer(Modifier.height(12.dp))
    DockBigButton(if (repo.clockedIn) "Clock out" else "Clock in") {
        repo.clockedIn = !repo.clockedIn
        repo.audit(user?.name ?: "dock", if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranch.name)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DockColors.Coral)
                    .clickable {
                        repo.logout()
                        onLogout()
                    }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Log out", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    DockNoteCard("Logging out unpins the dock and returns to sign-in. The wallpaper misses you already.")
}
