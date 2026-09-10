package com.companyb.companyapp.proto.ownerdesk

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

// #818 — owner-desk housekeeping: masked Hands, Pigeonhole mailbox, Daybook, Study.

@Composable
fun OwnerHands(repo: OwnerFakeRepo) {
    OwnerHeading("Hands, not names.", 34)
    OwnerSub("The owner's staffing view: roles and headcount per house. Personal detail stays in the drawer.")
    Spacer(Modifier.height(12.dp))
    OwnerRole.entries.filter { it != OwnerRole.ONBOARDING }.forEach { role ->
        val hands = repo.users.filter { it.role == role }
        val houses = hands.mapNotNull { hand -> repo.branches.firstOrNull { it.id == hand.homeBranchId }?.name }
        OwnerLedger(title = "${role.name} — ${hands.size} hand${if (hands.size == 1) "" else "s"}") {
            OwnerInkSub(
                if (houses.isEmpty()) {
                    "No hands carry this standing today."
                } else {
                    "Posted at ${houses.distinct().joinToString(", ")}."
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                roleBrief(role),
                color = OwnerColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    val locked = repo.users.filter { it.role == OwnerRole.ONBOARDING }
    OwnerLedger(title = "Onboarding — ${locked.size} awaiting grant") {
        OwnerInkSub("Zero capabilities until a MANAGE_USERS grant lands. Names withheld by house rule.")
    }
}

private fun roleBrief(role: OwnerRole): String =
    when (role) {
        OwnerRole.ONBOARDING -> "No capabilities."
        OwnerRole.PRACTITIONER -> "Holds sessions, banks folios, asks for relief cover."
        OwnerRole.COORDINATOR -> "Runs the line, judges relief asks, drafts remittance."
        OwnerRole.MANAGER -> "Superset of Coordinator: vault edits plus user management and delegate assignment."
        OwnerRole.ACCOUNTANT -> "Reads everything, edits nothing."
    }

@Composable
fun OwnerPigeonhole(repo: OwnerFakeRepo) {
    val unread = repo.notifications.count { !it.read }
    OwnerHeading("Pigeonhole — $unread unread.", 34)
    OwnerSub("Relief answers, pressed seals, day closures. File one away or clear the hole.")
    Spacer(Modifier.height(12.dp))
    if (unread > 0) {
        OwnerGhostButton("File them all") { repo.markAllRead() }
        Spacer(Modifier.height(12.dp))
    }
    if (repo.notifications.isEmpty()) {
        OwnerNoteCard("Empty hole. Open a folio and the desk will rustle.")
    }
    repo.notifications.forEach { note ->
        OwnerLedger(title = if (note.read) note.title else "● ${note.title}") {
            OwnerInkSub(note.body)
            if (!note.read) {
                Spacer(Modifier.height(10.dp))
                OwnerGhostButton("File this one") { repo.markRead(note.id) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun OwnerDaybook(repo: OwnerFakeRepo) {
    OwnerHeading("The daybook.", 34)
    OwnerSub("Every ruling entered here: who, what, which, why.")
    Spacer(Modifier.height(12.dp))
    repo.audits.forEach { entry ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(OwnerColors.Paper)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                entry.action,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(OwnerColors.Seal)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "${entry.target} · ${entry.who} · ${entry.whenLabel}${entry.reason?.let { " · $it" } ?: ""}",
                color = OwnerColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun OwnerStudy(
    repo: OwnerFakeRepo,
    onLogout: () -> Unit,
) {
    val user = repo.currentUser
    OwnerHeading("The study.", 34)
    OwnerSub(
        user?.let {
            "${it.name} · ${it.role} · home ${repo.branches.firstOrNull { b ->
                b.id == it.homeBranchId
            }?.name}"
        }
            ?: "Nobody yet.",
    )
    Spacer(Modifier.height(12.dp))
    OwnerLedger(title = if (repo.clockedIn) "On the floor" else "At the desk") {
        OwnerInkSub(
            if (repo.clockedIn) {
                "Clock out to freeze your line at ${repo.currentBranch.name}."
            } else {
                "Clock in to take the line at ${repo.currentBranch.name}."
            },
        )
        Spacer(Modifier.height(12.dp))
        OwnerBigButton(if (repo.clockedIn) "Clock out" else "Clock in") {
            repo.clockedIn = !repo.clockedIn
            repo.audit(user?.name ?: "owner", if (repo.clockedIn) "CLOCK_IN" else "CLOCK_OUT", repo.currentBranch.name)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Text(
                "Log out",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(OwnerColors.Seal)
                    .clickable {
                        repo.logout()
                        onLogout()
                    }.padding(vertical = 15.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OwnerNoteCard("Logging out locks the drawers and returns to the sign-in book.")
}
