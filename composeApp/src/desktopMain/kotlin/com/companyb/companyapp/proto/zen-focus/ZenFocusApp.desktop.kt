package com.companyb.companyapp.proto.zenfocus

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

private enum class ZenPhase { LOGIN, ONBOARDING, BRANCHES, APP }

private enum class ZenPlace { STILLNESS, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

private fun ZenPlace.title(): String =
    when (this) {
        ZenPlace.STILLNESS -> "Stillness"
        ZenPlace.SESSIONS -> "Sessions"
        ZenPlace.CLIENTS -> "Clients"
        ZenPlace.FINANCE -> "Finance"
        ZenPlace.TEAM -> "Team"
        ZenPlace.MAIL -> "Mailbox"
        ZenPlace.AUDIT -> "Audit log"
        ZenPlace.PROFILE -> "Profile"
    }

@Composable
fun ZenFocusApp() {
    logInfo("ZenFocus", "single-task prototype opened")
    ZenFocusTheme {
        var phase by remember { mutableStateOf(ZenPhase.LOGIN) }
        val repo = remember { ZenFakeRepo() }
        Box(modifier = Modifier.fillMaxSize().background(ZenPaper)) {
            when (phase) {
                ZenPhase.LOGIN ->
                    ZenLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = ZenPhase.BRANCHES
                        },
                        onPreviewOnboarding = { phase = ZenPhase.ONBOARDING },
                    )
                ZenPhase.ONBOARDING ->
                    ZenOnboardingLocked(onBack = { phase = ZenPhase.LOGIN })
                ZenPhase.BRANCHES ->
                    ZenBranchSelect(
                        repo = repo,
                        onPick = { phase = ZenPhase.APP },
                        onBack = { phase = ZenPhase.LOGIN },
                    )
                ZenPhase.APP ->
                    ZenShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = ZenPhase.LOGIN
                        },
                    )
            }
        }
    }
}

@Composable
private fun ZenShell(
    repo: ZenFakeRepo,
    onLogout: () -> Unit,
) {
    var place by remember { mutableStateOf(ZenPlace.STILLNESS) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val branch = repo.currentBranch()
    val focus = focusId?.let { id -> repo.sessions.firstOrNull { it.id == id } }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxWidth().background(ZenSurface)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "zen · focus",
                    fontFamily = ZenSerif,
                    fontSize = 18.sp,
                    color = ZenInk,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (repo.clockedIn) "● clocked in" else "○ not clocked in",
                        fontSize = 13.sp,
                        color = if (repo.clockedIn) ZenMoss else ZenSoftInk,
                    )
                    Text(
                        text = if (menuOpen) "close elsewhere ✕" else "elsewhere …",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ZenMoss,
                        modifier = Modifier.clickable { menuOpen = !menuOpen },
                    )
                }
            }
            if (focus == null) {
                Text(
                    text = "Now — ${place.title()} · ${branch.name}",
                    fontSize = 13.sp,
                    color = ZenSoftInk,
                )
            } else {
                Text(
                    text = "Now — one session · everything else is hidden",
                    fontSize = 13.sp,
                    color = ZenClay,
                )
            }
            if (menuOpen && focus == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ZenPlace.entries.forEach { entry ->
                        val selected = entry == place
                        Text(
                            text = entry.title(),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) ZenDeepMoss else ZenSoftInk,
                            modifier =
                                Modifier.clickable {
                                    place = entry
                                    menuOpen = false
                                }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ZenHairline))
        Box(modifier = Modifier.fillMaxSize()) {
            if (focus != null) {
                ZenSessionFocus(
                    repo = repo,
                    session = focus,
                    onLayDown = { focusId = null },
                )
            } else {
                when (place) {
                    ZenPlace.STILLNESS ->
                        ZenStillness(
                            repo = repo,
                            onAttend = { id ->
                                focusId = id
                            },
                            onOpenSessions = { place = ZenPlace.SESSIONS },
                        )
                    ZenPlace.SESSIONS ->
                        ZenSessions(
                            repo = repo,
                            onAttend = { id ->
                                focusId = id
                            },
                        )
                    ZenPlace.CLIENTS -> ZenClients(repo = repo)
                    ZenPlace.FINANCE -> ZenFinance(repo = repo)
                    ZenPlace.TEAM -> ZenTeam(repo = repo)
                    ZenPlace.MAIL -> ZenMail(repo = repo)
                    ZenPlace.AUDIT -> ZenAudit(repo = repo)
                    ZenPlace.PROFILE ->
                        ZenProfile(
                            repo = repo,
                            onLogout = onLogout,
                        )
                }
            }
        }
    }
}
