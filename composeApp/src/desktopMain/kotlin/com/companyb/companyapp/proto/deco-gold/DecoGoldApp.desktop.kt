package com.companyb.companyapp.proto.decogold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class GoldAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class GoldTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun DecoGoldProtoApp() {
    DecoGoldTheme {
        var phase by remember { mutableStateOf(GoldAuthPhase.LOGIN) }
        val repo = remember { DecoGoldFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                GoldAuthPhase.LOGIN -> {
                    GoldLogin(
                        onLogin = {
                            logInfo("DecoGold", "login entered the grand hall")
                            phase = GoldAuthPhase.BRANCH_SELECT
                        },
                        onOnboarding = { phase = GoldAuthPhase.ONBOARDING_LOCKED },
                    )
                }

                GoldAuthPhase.ONBOARDING_LOCKED -> {
                    GoldOnboardingLocked(
                        onBack = { phase = GoldAuthPhase.LOGIN },
                    )
                }

                GoldAuthPhase.BRANCH_SELECT -> {
                    GoldBranchSelect(
                        repo = repo,
                        onPick = { phase = GoldAuthPhase.APP },
                        onBack = { phase = GoldAuthPhase.LOGIN },
                    )
                }

                GoldAuthPhase.APP -> {
                    GoldShell(
                        repo = repo,
                        onLogout = { phase = GoldAuthPhase.LOGIN },
                    )
                }
            }
        }
    }
}

@Composable
private fun GoldShell(
    repo: DecoGoldFakeRepo,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(GoldTab.HOME) }
    Column(modifier = Modifier.fillMaxSize()) {
        GoldMarquee(title = "GRAND HALL", subtitle = repo.branchName(repo.branchId.value))
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(240.dp)
                        .background(GoldPanel)
                        .border(width = 1.dp, color = GoldLine)
                        .padding(16.dp),
            ) {
                Text(
                    text = if (repo.clockedIn.value) "◆ CLOCKED IN ◆" else "◇ CLOCKED OUT ◇",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (repo.clockedIn.value) GoldEmerald else GoldMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Day: ${repo.dayStatus.value.name}",
                    fontSize = 12.sp,
                    color = GoldMuted,
                )
                Spacer(Modifier.height(12.dp))
                GoldRule()
                Spacer(Modifier.height(12.dp))
                GoldTab.entries.forEach { t ->
                    val on = tab == t
                    val unread = t == GoldTab.MAIL && repo.notes.any { !it.read }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(if (on) GoldSoft else GoldPanel)
                                .border(width = 1.dp, color = if (on) GoldPrimary else GoldLine)
                                .clickable { tab = t }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (on) "◆" else "◇",
                                fontSize = 12.sp,
                                color = if (on) GoldPrimary else GoldFaint,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = t.name + if (unread) " ●" else "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (on) GoldBright else GoldCream,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.weight(1f))
                GoldRule()
                Spacer(Modifier.height(8.dp))
                Text(text = "EST. MMXXVI", fontSize = 11.sp, color = GoldFaint)
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (tab) {
                        GoldTab.HOME -> GoldHome(repo)
                        GoldTab.SESSIONS -> GoldSessions(repo)
                        GoldTab.CLIENTS -> GoldClients(repo)
                        GoldTab.FINANCE -> GoldFinance(repo)
                        GoldTab.TEAM -> GoldTeam(repo)
                        GoldTab.MAIL -> GoldMailbox(repo)
                        GoldTab.AUDIT -> GoldAuditLog(repo)
                        GoldTab.PROFILE -> GoldProfile(repo, onLogout)
                    }
                }
            }
        }
    }
}
