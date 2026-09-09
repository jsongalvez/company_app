package com.companyb.companyapp.proto.guided_onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// #766 — guided-onboarding prototype shell: first-run wizard journey. Sign in,
// ONBOARDING locked gate, branch gate, then an 8-lantern step rail where every
// step coaches one full fake-data flow.

@Composable
fun ProtoGuidedOnboardingApp(onBack: () -> Unit, repo: GuidedRepo = remember { GuidedRepo() }) {
    GdTheme {
        val user = repo.currentUser
        when {
            user == null -> GdLoginGate(repo)
            user.locked -> GdLockedGate(repo)
            repo.currentBranchId.isBlank() -> GdBranchGate(repo)
            else -> GdJourneyShell(repo, onBack)
        }
    }
}

@Composable
private fun GdLoginGate(repo: GuidedRepo) {
    Box(Modifier.fillMaxSize().background(GdColors.Night).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FIRST DAY AT THE PRACTICE", color = GdColors.Lantern, fontSize = 13.sp, fontWeight = FontWeight.Black)
            GdNightHeadline("Your guided first day")
            GdNightNote("A first-run wizard that walks you through every station with fake data — no network, no backend. Pick who to be today.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                GdCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(u.name, color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · starts locked" else "",
                                color = GdColors.Faded,
                                fontSize = 12.sp,
                            )
                            Text(u.explainer, color = GdColors.Ink, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        GdPrimary("Begin") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.visit(GdStep.WELCOME)
                            repo.log("${u.name} began the guided first day (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GdLockedGate(repo: GuidedRepo) {
    Box(Modifier.fillMaxSize().background(GdColors.Night).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LANTERN 2 PREVIEW — LOCKED", color = GdColors.Lantern, fontSize = 12.sp, fontWeight = FontWeight.Black)
            GdNightHeadline("Locked out — ONBOARDING")
            GdCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so " +
                            "nothing derives — even with a branch assignment. A MANAGER must grant a " +
                            "real role via MANAGE_USERS before the journey unlocks.",
                        color = GdColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    GdNote("Coaching note: this locked screen IS the lesson. Every new hire lands here first.")
                }
            }
            GdCoachCard(
                "Why am I seeing this?",
                "ONBOARDING is not a broken account — it is the starting role. Your guide lights " +
                    "again the moment a MANAGER grants Practitioner, Coordinator, or any real bundle.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GdPrimary("Try another teammate") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun GdBranchGate(repo: GuidedRepo) {
    Box(Modifier.fillMaxSize().background(GdColors.Night).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "SIGNED IN AS ${repo.currentUser?.name?.uppercase()} · ${repo.currentUser?.role?.uppercase()}",
                color = GdColors.Lantern,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            GdNightHeadline("Where is today happening?")
            GdNightNote("A Branch is a CLINIC, a PROVINCIAL_TOUR, or a MEDICAL_MISSION. Each holds its own sessions and books.")
            repo.branches.forEach { b ->
                GdCard {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} selected branch ${b.name}")
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(b.name, color = GdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("${b.kind} · day ${b.dayStatus.name}", color = GdColors.Faded, fontSize = 12.sp)
                            Text(b.tour, color = GdColors.Ink, fontSize = 12.sp)
                        }
                        GdLink("Enter") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} selected branch ${b.name}")
                        }
                    }
                }
            }
            GdNightLink("‹ Back to sign-in") { repo.currentUser = null }
        }
    }
}

@Composable
private fun GdJourneyShell(repo: GuidedRepo, onBack: () -> Unit) {
    var step by remember { mutableStateOf(GdStep.WELCOME) }
    val branch = repo.currentBranch()
    val unread = repo.notices.count { !it.read }
    val done = repo.doneCount()
    val total = GdStep.entries.size
    Row(Modifier.fillMaxSize().background(GdColors.Night)) {
        Column(
            Modifier.width(280.dp).fillMaxHeight().background(GdColors.NightSoft).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("GUIDED FIRST DAY", color = GdColors.Lantern, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentUser?.name} · ${repo.currentUser?.role}",
                color = GdColors.FadedNight,
                fontSize = 12.sp,
            )
            Text(
                "${branch.name} · ${branch.dayStatus.name}" +
                    if (unread > 0) " · $unread unread" else "",
                color = GdColors.InkOnNight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            GdJourneyProgress(done, total)
            Spacer(Modifier.height(8.dp))
            GdStep.entries.forEach { s ->
                val state = when {
                    repo.stepDone(s) -> GdStepState.DONE
                    s == step -> GdStepState.CURRENT
                    else -> GdStepState.NEXT
                }
                GdStepRow(GdStep.entries.indexOf(s) + 1, s.label, state) {
                    step = s
                    repo.visit(s)
                }
            }
            Spacer(Modifier.height(12.dp))
            GdNightLink("‹ Switch branch") { repo.currentBranchId = "" }
            GdNightLink("‹ Sign out") {
                repo.log("${repo.currentUser?.name} signed out")
                repo.currentUser = null
            }
            GdNightLink("‹ Exit prototype") { onBack() }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GdDayBanner(branch.dayStatus, branch.name)
                when (step) {
                    GdStep.WELCOME -> GdWelcomeStep(repo) { step = GdStep.ROLES; repo.visit(GdStep.ROLES) }
                    GdStep.ROLES -> GdRolesStep(repo) { step = GdStep.BRANCH_DAY; repo.visit(GdStep.BRANCH_DAY) }
                    GdStep.BRANCH_DAY -> GdBranchDayStep(repo) { step = GdStep.SESSIONS; repo.visit(GdStep.SESSIONS) }
                    GdStep.SESSIONS -> GdStepFrame(repo, step, "Continue to Clients") {
                        step = GdStep.CLIENTS; repo.visit(GdStep.CLIENTS)
                    }
                    GdStep.CLIENTS -> GdStepFrame(repo, step, "Continue to Finance") {
                        step = GdStep.FINANCE; repo.visit(GdStep.FINANCE)
                    }
                    GdStep.FINANCE -> GdStepFrame(repo, step, "Continue to Team") {
                        step = GdStep.TEAM; repo.visit(GdStep.TEAM)
                    }
                    GdStep.TEAM -> GdStepFrame(repo, step, "Continue to Graduate") {
                        step = GdStep.GRADUATE; repo.visit(GdStep.GRADUATE)
                    }
                    GdStep.GRADUATE -> GdGraduateStep(repo, onBack)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GdStepFrame(repo: GuidedRepo, step: GdStep, nextLabel: String, onNext: () -> Unit) {
    GdCoachCard("Lantern ${GdStep.entries.indexOf(step) + 1} — ${step.label}", step.coach)
    when (step) {
        GdStep.SESSIONS -> GdSessionsFlow(repo)
        GdStep.CLIENTS -> GdClientsFlow(repo)
        GdStep.FINANCE -> GdFinanceFlow(repo)
        GdStep.TEAM -> GdTeamFlow(repo)
        else -> Unit
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GdPrimary(nextLabel, onClick = onNext)
    }
}
