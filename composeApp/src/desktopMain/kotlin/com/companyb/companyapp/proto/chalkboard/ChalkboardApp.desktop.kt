package com.companyb.companyapp.proto.chalkboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class ChalkAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class ChalkTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun ChalkboardProtoApp() {
    ChalkboardTheme {
        var phase by remember { mutableStateOf(ChalkAuthPhase.LOGIN) }
        val repo = remember { ChalkboardFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                ChalkAuthPhase.LOGIN -> ChalkLogin(
                    onLogin = {
                        logInfo("Chalkboard", "login chalk-in")
                        phase = ChalkAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = ChalkAuthPhase.ONBOARDING_LOCKED },
                )
                ChalkAuthPhase.ONBOARDING_LOCKED -> ChalkOnboardingLocked(
                    onBack = { phase = ChalkAuthPhase.LOGIN },
                )
                ChalkAuthPhase.BRANCH_SELECT -> ChalkBranchSelect(
                    repo = repo,
                    onPick = { phase = ChalkAuthPhase.APP },
                    onBack = { phase = ChalkAuthPhase.LOGIN },
                )
                ChalkAuthPhase.APP -> ChalkShell(
                    repo = repo,
                    onLogout = { phase = ChalkAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun ChalkShell(repo: ChalkboardFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(ChalkTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(248.dp).background(ChalkSlate).padding(16.dp),
        ) {
            Text(text = "⌛ chalkboard", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ChalkYellow)
            Text(text = "Class-session wall", fontSize = 13.sp, color = ChalkDim)
            Spacer(Modifier.height(4.dp))
            Text(text = repo.branchName(repo.branchId.value), fontSize = 13.sp, color = ChalkFaint)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (repo.clockedIn.value) "● chalked in" else "○ chalked out",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (repo.clockedIn.value) ChalkMint else ChalkPink,
            )
            Spacer(Modifier.height(6.dp))
            ChalkSmudgeDivider()
            Spacer(Modifier.height(10.dp))
            ChalkTab.entries.forEach { t ->
                val on = tab == t
                val unread = t == ChalkTab.MAIL && repo.notes.any { !it.read }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(ChalkShapes.small)
                        .background(if (on) ChalkTray else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tab = t }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (on) {
                            Box(modifier = Modifier.width(3.dp).height(18.dp).background(ChalkYellow))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = t.name + if (unread) " (•)" else "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (on) ChalkWhite else ChalkFaint,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.weight(1f))
            ChalkSmudgeDivider()
            Spacer(Modifier.height(8.dp))
            Text(text = "Day: ${repo.dayStatus.value.name}", fontSize = 13.sp, color = ChalkDim)
            Text(text = "chalk tray · fake data only", fontSize = 12.sp, color = ChalkSmudge)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (tab) {
                    ChalkTab.HOME -> ChalkHome(repo)
                    ChalkTab.SESSIONS -> ChalkSessions(repo)
                    ChalkTab.CLIENTS -> ChalkClients(repo)
                    ChalkTab.FINANCE -> ChalkFinance(repo)
                    ChalkTab.TEAM -> ChalkTeam(repo)
                    ChalkTab.MAIL -> ChalkMailbox(repo)
                    ChalkTab.AUDIT -> ChalkAuditLog(repo)
                    ChalkTab.PROFILE -> ChalkProfile(repo, onLogout)
                }
            }
        }
    }
}
