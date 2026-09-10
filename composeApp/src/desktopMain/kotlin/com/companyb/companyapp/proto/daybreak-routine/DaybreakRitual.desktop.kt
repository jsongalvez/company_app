package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakRitual(repo: DaybreakRepo) {
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Morning opener", "${repo.ritualStepsDone}/4 steps · ${repo.currentBranch.name}") {
            if (repo.ritualDone) DawnBadge("DOORS OPEN", DaybreakPalette.Sage)
        }
        LinearProgressIndicator(
            progress = { repo.ritualStepsDone / 4f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = DaybreakPalette.Sunrise,
            trackColor = DaybreakPalette.Line,
        )
        Spacer(Modifier.height(10.dp))
        RitualUnlockCard(repo)
        Spacer(Modifier.height(10.dp))
        RitualDrawerCard(repo)
        Spacer(Modifier.height(10.dp))
        RitualReviewCard(repo)
        Spacer(Modifier.height(10.dp))
        RitualFirstClientCard(repo)
        Spacer(Modifier.height(10.dp))
        if (repo.ritualStepsDone == 4 && !repo.ritualDone) {
            Button(
                onClick = { repo.finishRitual() },
                colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Sunrise),
            ) {
                Text("OPEN THE DOORS FOR ${repo.currentBranch.name.uppercase()}")
            }
        }
        if (repo.ritualDone) {
            DawnNote(
                "Opener complete: drawer counted, day reviewed, first client welcomed. " +
                    "The rest of the tabs stay live all day.",
            )
        }
        Spacer(Modifier.height(10.dp))
        ReliefStrip(repo)
    }
}

@Composable
private fun RitualUnlockCard(repo: DaybreakRepo) {
    DawnPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (repo.unlockDone) "[1] UNLOCKED" else "[1] UNLOCK", style = DaybreakType.titleMedium)
            Spacer(Modifier.width(8.dp))
            DawnBadge(
                if (repo.unlockDone) "DONE" else "STEP 1",
                if (repo.unlockDone) DaybreakPalette.Sage else DaybreakPalette.Sunrise,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { repo.toggleClock() }) {
                Text(if (repo.clockedIn) "Lock back up" else "Clock in + unlock")
            }
        }
        Text(
            "Turn the key as ${repo.currentUser.name} (${repo.currentUser.role}) at ${repo.currentBranch.name}.",
            style = DaybreakType.bodySmall,
        )
        DawnRow("Clock state", if (repo.clockedIn) "CLOCKED IN" else "OUTSIDE")
        DawnRow("Home relief split", "home crew first, relief sorts last")
    }
}

@Composable
private fun RitualDrawerCard(repo: DaybreakRepo) {
    DawnPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[2] COUNT THE DRAWER", style = DaybreakType.titleMedium)
            Spacer(Modifier.width(8.dp))
            DawnBadge(
                if (repo.drawerDone) "DONE" else "STEP 2",
                if (repo.drawerDone) DaybreakPalette.Sage else DaybreakPalette.Sunrise,
            )
        }
        Text(
            "Expected float ${dawnPeso(repo.drawerExpected)}. Count before the first client walks in.",
            style = DaybreakType.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = repo.drawerAmount,
                onValueChange = { repo.drawerAmount = it },
                label = { Text("Counted cash") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { repo.confirmDrawer() },
                enabled = repo.drawerAmount.isNotBlank() && !repo.drawerDone,
                colors = ButtonDefaults.buttonColors(containerColor = DaybreakPalette.Ink),
            ) {
                Text(if (repo.drawerDone) "Counted" else "Confirm")
            }
        }
        if (repo.drawerDone) DawnRow("Drawer", "counted at ${repo.drawerAmount} vs expected ${repo.drawerExpected}")
    }
}

@Composable
private fun RitualReviewCard(repo: DaybreakRepo) {
    DawnPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[3] REVIEW THE DAY", style = DaybreakType.titleMedium)
            Spacer(Modifier.width(8.dp))
            DawnBadge(
                if (repo.reviewDone) "DONE" else "STEP 3",
                if (repo.reviewDone) DaybreakPalette.Sage else DaybreakPalette.Sunrise,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { repo.markDayReviewed() }, enabled = !repo.reviewDone) { Text("Mark reviewed") }
        }
        Text(
            repo.pendingCount.toString() + " PENDING · day " + repo.currentDay.date + " " +
                repo.currentDay.state.label + " · " + repo.currentDay.note,
            style = DaybreakType.bodySmall,
        )
        DawnRow("Branch day", repo.currentDay.date + " " + repo.currentDay.state.label)
        DawnRow("Boundary", "04:00 Asia/Manila, not midnight")
        if (repo.dayLocked) {
            DawnNote(
                "This day is locked for ${repo.currentUser.role}: PAST/REMITTED needs Coordinator or MANAGER.",
            )
        }
    }
}

@Composable
private fun RitualFirstClientCard(repo: DaybreakRepo) {
    val first = repo.sessions.firstOrNull { it.status == DawnSessionStatus.PENDING && !it.voided }
    DawnPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[4] FIRST CLIENT", style = DaybreakType.titleMedium)
            Spacer(Modifier.width(8.dp))
            DawnBadge(
                if (repo.firstDone) "DONE" else "STEP 4",
                if (repo.firstDone) DaybreakPalette.Sage else DaybreakPalette.Sunrise,
            )
        }
        if (first == null) {
            Text("No PENDING session left: the opener has nothing to welcome.", style = DaybreakType.bodySmall)
        } else {
            Text(
                "Opener slot: ${first.time} ${first.clientName} (${first.service}) at ${dawnPeso(first.price)}.",
                style = DaybreakType.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { repo.startFirstClient(first.id) }, enabled = !repo.firstDone) {
                    Text(if (repo.firstDone) "Welcomed" else "Welcome ${first.clientName}")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { repo.screen = DawnScreen.SESSIONS }) { Text("Open roster") }
            }
        }
        DawnNote("Client is global: at most one PENDING Session per Client, starred in the registry.")
    }
}

@Composable
private fun ReliefStrip(repo: DaybreakRepo) {
    DawnPanel(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Relief for today", "duty + invite + request")
        repo.staff.filter { it.relief }.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(member.name + " · from " + member.home, style = DaybreakType.bodyMedium)
                    Text(
                        "Relief duty expires 04:00 Manila next day; pay from this drawer.",
                        style = DaybreakType.bodySmall,
                    )
                }
                OutlinedButton(onClick = { repo.grantRelief(member.id) }) {
                    Text(if (member.clockedIn) "Edit granted" else "Grant edit")
                }
            }
        }
        DawnNote(
            "Invite (branch asks a named Practitioner for a future day) vs Request " +
                "(outsider broadcasts to the Branch). Any member grants; locks at clock-in.",
        )
    }
}
