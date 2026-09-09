package com.companyb.companyapp.proto.pastelplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class PlayAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class PlayTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun PastelPlayProtoApp() {
    PastelPlayTheme {
        var phase by remember { mutableStateOf(PlayAuthPhase.LOGIN) }
        val repo = remember { PastelPlayFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                PlayAuthPhase.LOGIN -> PlayLogin(
                    onLogin = {
                        logInfo("PastelPlay", "login bounce-in")
                        phase = PlayAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = PlayAuthPhase.ONBOARDING_LOCKED },
                )
                PlayAuthPhase.ONBOARDING_LOCKED -> PlayOnboardingLocked(
                    onBack = { phase = PlayAuthPhase.LOGIN },
                )
                PlayAuthPhase.BRANCH_SELECT -> PlayBranchSelect(
                    repo = repo,
                    onPick = { phase = PlayAuthPhase.APP },
                    onBack = { phase = PlayAuthPhase.LOGIN },
                )
                PlayAuthPhase.APP -> PlayShell(
                    repo = repo,
                    onLogout = { phase = PlayAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun PlayShell(repo: PastelPlayFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(PlayTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(230.dp).background(PlayCotton).padding(16.dp),
        ) {
            Text(text = "(˶ᵔᵕᵔ˶)", fontSize = 36.sp)
            Text(text = "pastel-play", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PlayCandyDeep)
            Text(text = repo.branchName(repo.branchId.value), fontSize = 13.sp, color = PlayMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (repo.clockedIn.value) "clocked in" else "clocked out",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (repo.clockedIn.value) PlayMint else PlayCandyDeep,
            )
            Spacer(Modifier.height(16.dp))
            PlayTab.entries.forEach { t ->
                val on = tab == t
                val unread = t == PlayTab.MAIL && repo.notes.any { !it.read }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(if (on) PlayCandy else Color.Transparent)
                        .clickable { tab = t }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = t.name + if (unread) " (!)" else "",
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color.White else PlayInk,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(text = "Day: ${repo.dayStatus.value.name}", fontSize = 13.sp, color = PlayMuted)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (tab) {
                    PlayTab.HOME -> PlayHome(repo)
                    PlayTab.SESSIONS -> PlaySessions(repo)
                    PlayTab.CLIENTS -> PlayClients(repo)
                    PlayTab.FINANCE -> PlayFinance(repo)
                    PlayTab.TEAM -> PlayTeam(repo)
                    PlayTab.MAIL -> PlayMailbox(repo)
                    PlayTab.AUDIT -> PlayAuditLog(repo)
                    PlayTab.PROFILE -> PlayProfile(repo, onLogout)
                }
            }
        }
    }
}
