package com.companyb.companyapp.proto.solarizedcalm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.companyb.companyapp.util.logInfo

private enum class CalmPhase { LOGIN, ONBOARDING, BRANCHES, APP }

private enum class CalmPlace { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

private fun CalmPlace.title(): String =
    when (this) {
        CalmPlace.HOME -> "Home"
        CalmPlace.SESSIONS -> "Sessions"
        CalmPlace.CLIENTS -> "Clients"
        CalmPlace.FINANCE -> "Finance"
        CalmPlace.TEAM -> "Team"
        CalmPlace.MAIL -> "Mailbox"
        CalmPlace.AUDIT -> "Audit"
        CalmPlace.PROFILE -> "Profile"
    }

@Composable
fun SolarizedCalmApp() {
    logInfo("SolarizedCalm", "solarized-calm prototype opened")
    SolarizedCalmTheme {
        var phase by remember { mutableStateOf(CalmPhase.LOGIN) }
        val repo = remember { CalmFakeRepo() }
        Box(modifier = Modifier.fillMaxSize().background(SolBase3)) {
            when (phase) {
                CalmPhase.LOGIN ->
                    CalmLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = CalmPhase.BRANCHES
                        },
                        onPreviewOnboarding = { phase = CalmPhase.ONBOARDING },
                    )
                CalmPhase.ONBOARDING ->
                    CalmOnboardingLocked(onBack = { phase = CalmPhase.LOGIN })
                CalmPhase.BRANCHES ->
                    CalmBranchSelect(
                        repo = repo,
                        onPick = { phase = CalmPhase.APP },
                        onBack = { phase = CalmPhase.LOGIN },
                    )
                CalmPhase.APP ->
                    CalmShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = CalmPhase.LOGIN
                        },
                    )
            }
        }
    }
}

@Composable
private fun CalmShell(
    repo: CalmFakeRepo,
    onLogout: () -> Unit,
) {
    var place by remember { mutableStateOf(CalmPlace.HOME) }
    val branch = repo.currentBranch()
    val unread = repo.notices.count { !it.read }
    val dayTone =
        when (branch.day) {
            CalmDay.OPEN -> SolGreen
            CalmDay.PAST -> SolYellow
            CalmDay.REMITTED -> SolViolet
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxWidth().background(SolBase2)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.background(dayTone, CircleShape).padding(5.dp))
                    Column {
                        Text(
                            text = "solarized · calm",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SolBase02,
                            fontFamily = SolMono,
                        )
                        Text(
                            text = "${branch.name} · ${branch.day.name} · ${if (repo.clockedIn) "clocked in" else "not clocked in"}",
                            fontSize = 12.sp,
                            fontFamily = SolMono,
                            color = SolBase00,
                        )
                    }
                }
                Text(
                    text = "04:00 Manila boundary",
                    fontSize = 12.sp,
                    fontFamily = SolMono,
                    color = SolBase00,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CalmPlace.entries.forEach { entry ->
                    val selected = entry == place
                    val label = if (entry == CalmPlace.MAIL && unread > 0) "${entry.title()} ($unread)" else entry.title()
                    Box(
                        modifier =
                            Modifier.background(
                                if (selected) SolBase3 else dayTone.copy(alpha = 0.0f),
                                RoundedCornerShape(6.dp),
                            )
                                .clickable { place = entry }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) SolBase02 else SolBase00,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SolBase1.copy(alpha = 0.5f)))
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (place) {
                    CalmPlace.HOME -> CalmHome(repo = repo)
                    CalmPlace.SESSIONS -> CalmSessions(repo = repo)
                    CalmPlace.CLIENTS -> CalmClients(repo = repo)
                    CalmPlace.FINANCE -> CalmFinance(repo = repo)
                    CalmPlace.TEAM -> CalmTeam(repo = repo)
                    CalmPlace.MAIL -> CalmMail(repo = repo)
                    CalmPlace.AUDIT -> CalmAudit(repo = repo)
                    CalmPlace.PROFILE ->
                        CalmProfile(
                            repo = repo,
                            onLogout = onLogout,
                        )
                }
            }
        }
    }
}
