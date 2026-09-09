package com.companyb.companyapp.proto.guided_onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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

// #766 — wizard steps 1-3: welcome/sample-data, role explainer, branch-day tour
// with clock-in and relief duty/request/invite coaching.

@Composable
fun GdWelcomeStep(repo: GuidedRepo, onNext: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    GdCoachCard(
        "Lantern 1 — Welcome to your first day",
        "Eight lanterns, one per station. Do the small task on each step to light it. " +
            "Stuck or mid-experiment? Reset restores the exact seed below — replay as often as you like.",
    )
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GdHeadline("The journey ahead")
            GdStep.entries.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (repo.stepDone(s)) "✓" else "${i + 1}",
                        color = if (repo.stepDone(s)) GdColors.Leaf else GdColors.LanternDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(s.label, color = GdColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(s.coach, color = GdColors.Faded, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GdSubhead("Sample data reset")
            Text(
                "Seeds: 6 sessions across Makati + BGC, 5 clients (one anonymized), " +
                    "3 remittances (SESSION + PRODUCT), 3 mailbox notices, 4 audit entries, 3 relief lines.",
                color = GdColors.Ink,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            GdNote("Reset keeps you signed in at this branch; progress lanterns restart.")
            if (confirmReset) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GdPrimary("Yes, reset everything") {
                        repo.resetSampleData()
                        confirmReset = false
                    }
                    GdGhost("Keep my mess") { confirmReset = false }
                }
            } else {
                GdGhost("Reset sample data") { confirmReset = true }
            }
        }
    }
    GdPrimary("Meet the roles") { onNext() }
}

@Composable
fun GdRolesStep(repo: GuidedRepo, onNext: () -> Unit) {
    var showLocked by remember { mutableStateOf(false) }
    GdCoachCard(
        "Lantern 2 — Roles are bundles of capabilities",
        "Nobody is checked by name at runtime — checks read capabilities like " +
            "EDIT_BRANCH_DATA or SUBMIT_REMITTANCE. A Role is just the bundle. Signed in as " +
            "${repo.currentUser?.name} (${repo.currentUser?.role}).",
    )
    repo.users.forEach { u ->
        GdCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        u.role + if (u.locked) " · EMPTY BUNDLE" else "",
                        color = if (u.locked) GdColors.Alarm else GdColors.LanternDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (u.id == repo.currentUser?.id) {
                        Text("← THAT'S YOU", color = GdColors.Leaf, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(u.explainer, color = GdColors.Ink, fontSize = 13.sp, lineHeight = 19.sp)
                Text(
                    if (u.capabilities.isEmpty()) "Capabilities: none — locked out" else "Capabilities: ${u.capabilities.joinToString()}",
                    color = GdColors.Faded,
                    fontSize = 12.sp,
                )
            }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GdSubhead("Try the locked view")
            GdNote("Preview what Eli Santos (ONBOARDING) sees — the gate that replaces every dashboard.")
            if (showLocked) {
                Text(
                    "Locked out — ONBOARDING. The capability bundle is empty, so nothing derives, " +
                        "even with a branch assignment. A MANAGER grants a real role via MANAGE_USERS.",
                    color = GdColors.Alarm,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                GdGhost("Hide preview") { showLocked = false }
            } else {
                GdGhost("Preview ONBOARDING lock") { showLocked = true }
            }
            GdNote("MANAGER is a Coordinator superset: finance powers plus MANAGE_USERS and delegate assignment.")
        }
    }
    GdPrimary("Tour the branch day") { onNext() }
}

@Composable
fun GdBranchDayStep(repo: GuidedRepo, onNext: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    GdCoachCard(
        "Lantern 3 — One branch, one day, one clock",
        "The Branch Day is the unit of work: OPEN today, PAST after the 04:00 Asia/Manila " +
            "boundary, REMITTED once sealed. Clock in to light this lantern, then explore relief.",
    )
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GdSubhead("Clock")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (repo.clockedIn) "Clocked in at ${repo.currentBranch().name}" else "Off the clock — tap to start the day",
                        color = GdColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    GdNote("Home branch: ${repo.branch(repo.currentUser?.homeBranchId ?: repo.currentBranchId).name}.")
                }
                if (repo.clockedIn) {
                    GdGhost("Clock out") { repo.clockOut() }
                } else {
                    GdPrimary("Clock in") { repo.clockIn() }
                }
            }
            if (repo.everClockedIn) {
                Text("✓ Lantern lit — you clocked in.", color = GdColors.Leaf, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Relief duty", "Relief requests", "Relief invites").forEachIndexed { i, label ->
            if (i == tab) GdPrimary(label) { tab = i } else GdGhost(label) { tab = i }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    GdSubhead("Relief duty — covering another branch")
                    repo.reliefBoard.filter { it.startsWith("Duty") }.forEach {
                        Text("• $it", color = GdColors.Ink, fontSize = 13.sp)
                    }
                    GdNote("Relief starts view-only; edit needs a grant. Duty expires 04:00 Manila next day; pay comes from the relief branch drawer.")
                }
                1 -> {
                    GdSubhead("Relief requests — you ask the branch")
                    Text("• You asked BGC for edit access today — APPROVED", color = GdColors.Ink, fontSize = 13.sp)
                    GdNote("Broadcast to the whole branch — names nobody. One live request per requester per branch per date. Any active member grants, denies, or cancels; locks once you clock in as relief.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GdPrimary("Ask Cebu Tour") {
                            repo.reliefBoard.add("Request: You asked Cebu Tour for edit access today — PENDING")
                            repo.log("Relief request opened for Cebu Tour")
                        }
                        GdGhost("Withdraw") { repo.log("Relief request withdrawn (fake)") }
                    }
                }
                else -> {
                    GdSubhead("Relief invites — the branch asks you")
                    Text("• Makati invites you (Practitioner) for Saturday — pending your accept", color = GdColors.Ink, fontSize = 13.sp)
                    GdNote("Single future day. Accepting writes the day grant; the branch may revoke an accepted future duty until you clock in, freeing re-invite.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GdPrimary("Accept invite") { repo.log("Relief invite accepted — day grant written (fake)") }
                        GdGhost("Decline") { repo.log("Relief invite declined (fake)") }
                    }
                }
            }
        }
    }
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GdSubhead("Day states at a glance")
            Text("Makati OPEN · BGC PAST · Cebu Tour REMITTED · Tondo Mission OPEN", color = GdColors.Ink, fontSize = 13.sp)
            GdNote("Switch branches from the rail to feel each state: PAST is Coordinator-only, REMITTED is sealed.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GdGhost("‹ Switch branch") { repo.currentBranchId = "" }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    GdPrimary("Log your first session", enabled = repo.everClockedIn) { onNext() }
    if (!repo.everClockedIn) {
        GdNote("Clock in above to continue — the wizard insists on this one habit.")
    }
}

@Composable
fun GdGraduateStep(repo: GuidedRepo, onBack: () -> Unit) {
    GdCoachCard(
        "Lantern 8 — Inbox, trail, graduation",
        "Every action you took wrote to the audit trail and sometimes your mailbox. " +
            "Read them, then graduate — or reset and run the day again.",
    )
    GdMailboxFlow(repo)
    GdAuditFlow(repo)
    GdCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GdSubhead("Graduation")
            Text(
                "Lanterns lit: ${repo.doneCount()} of ${GdStep.entries.size}.",
                color = GdColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (repo.graduated) {
                Text(
                    "Graduated — first day complete. The rail stays open for free exploration, " +
                        "or reset the sample data and mentor the next hire.",
                    color = GdColors.Leaf,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GdGhost("Reset sample data") { repo.resetSampleData() }
                    GdGhost("Exit prototype") { onBack() }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GdPrimary("Light the last lantern") {
                        repo.graduated = true
                        repo.log("${repo.currentUser?.name} graduated the guided first day")
                    }
                    GdGhost("Clock out & sign out") {
                        repo.clockOut()
                        repo.log("${repo.currentUser?.name} signed out")
                        repo.currentUser = null
                    }
                }
            }
        }
    }
    GdProfileFlow(repo, onBack)
}
