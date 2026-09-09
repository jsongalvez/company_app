package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsHome(repo: DarkOpsRepo) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(OpsPadMd)) {
        item { OpsSectionHeader("home", repo.currentBranch.name + " · " + repo.currentUser.name) }
        item { HomeClockPanel(repo) }
        item { HomeTodayPanel(repo) }
        item { HomeReliefPanel(repo) }
    }
}

@Composable
private fun HomeClockPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SHIFT", style = DarkOpsType.labelSmall)
                Text(
                    if (repo.clockedIn) "CLOCKED IN @ " + repo.currentBranch.name else "OFF DUTY",
                    style = DarkOpsType.titleLarge,
                    color = if (repo.clockedIn) DarkOpsPalette.Phosphor else DarkOpsPalette.Dim,
                )
                Text("relief at a non-home branch starts view-only", style = DarkOpsType.bodySmall)
            }
            Button(onClick = { repo.toggleClock() }, colors = greenButton()) {
                Text(
                    if (repo.clockedIn) "CLOCK OUT" else "CLOCK IN",
                    style = DarkOpsType.labelLarge,
                    color = DarkOpsPalette.Void,
                )
            }
        }
    }
}

@Composable
private fun HomeTodayPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("TODAY @ " + repo.currentDay.date, style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadLg)) {
            HomeStat("PENDING", repo.pendingCount.toString(), DarkOpsPalette.Amber)
            HomeStat(
                "DONE",
                repo.sessions.count { it.status == OpsSessionStatus.COMPLETED }.toString(),
                DarkOpsPalette.Phosphor,
            )
            HomeStat("NET", peso(repo.sessionNet()), DarkOpsPalette.Cyan)
            HomeStat("UNREAD", repo.unreadCount.toString(), DarkOpsPalette.Violet)
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("walk-in sessions skip NO_SHOW/CANCELLED — see sessions")
    }
}

@Composable
private fun HomeStat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text(label, style = DarkOpsType.labelSmall)
        Text(value, style = DarkOpsType.displaySmall, color = color)
    }
}

@Composable
private fun HomeReliefPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("RELIEF DUTY // INVITES // REQUESTS", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text("duty: P. Gomez holds edit at Makati today (grant kept)", style = DarkOpsType.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadSm), verticalAlignment = Alignment.CenterVertically) {
            OpsBadge("INVITE: Cebu Tour 2026-09-10", DarkOpsPalette.Cyan)
            Spacer(Modifier.width(4.dp))
            OpsMiniButton("accept", repo)
        }
        Spacer(Modifier.height(4.dp))
        Text("request: J. Cruz asked Makati 2026-09-11 (broadcast, no target)", style = DarkOpsType.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadSm)) {
            OpsBadge("1 live request / branch / date", DarkOpsPalette.Amber)
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("grants die at 04:00 Manila next day; pay comes from relief drawer")
    }
}

@Composable
private fun OpsMiniButton(
    label: String,
    repo: DarkOpsRepo,
) {
    Button(onClick = { repo.grantRelief("u-relief") }, colors = greenButton()) {
        Text(label, style = DarkOpsType.labelMedium, color = DarkOpsPalette.Void)
    }
}
