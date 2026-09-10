package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LunchRushProfile(repo: LunchRushRepo) {
    RushSectionHeader("My apron", repo.currentUser.name)
    RushTicketCard(hot = true) {
        Text(repo.currentUser.name, style = LunchRushType.displaySmall)
        Text("${repo.currentUser.role} — ${repo.currentBranch.name}", style = LunchRushType.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("CAPABILITIES", style = LunchRushType.labelMedium)
        if (repo.currentUser.capabilities.isEmpty()) {
            Text("None — ONBOARDING lock. Ask a MANAGER to clear you.", style = LunchRushType.bodyMedium)
        } else {
            repo.currentUser.capabilities.forEach { cap ->
                Text("+ $cap", style = LunchRushType.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Clock: ${if (repo.clockedIn) "IN" else "OUT"}", style = LunchRushType.bodyMedium)
    }
    Spacer(Modifier.height(RushPadMd))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { repo.toggleClock() }) {
            Text(if (repo.clockedIn) "Clock out" else "Clock in")
        }
        Button(
            onClick = { repo.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = LunchRushPalette.Char),
        ) { Text("Hang apron + logout") }
    }
    Spacer(Modifier.height(RushPadMd))
    RushNote("Logout returns to fake sign-in. Clock-out stamps the audit log with branch + time.")
}

@Composable
internal fun LunchRushShortcutsDialog(repo: LunchRushRepo) {
    RushTicketCard(hot = true) {
        Column {
            Text("SHORTCUTS", style = LunchRushType.labelMedium)
            Text("1 rush board — 2 tickets — 3 regulars — 4 till — 5 crew — 6 pass — 7 apron", style = LunchRushType.bodyMedium)
            Text("? toggles this card — esc closes it", style = LunchRushType.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { repo.shortcutsOpen = false }) { Text("Back to the rush") }
            }
        }
    }
}
