package com.companyb.companyapp.proto.mochapop

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class MochaPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class MochaTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun MochaPopProtoApp() {
    MochaPopTheme {
        var phase by remember { mutableStateOf(MochaPhase.LOGIN) }
        val repo = remember { MochaFakeRepo() }
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MochaDripStrip()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    MochaPhase.LOGIN -> MochaLogin(
                        users = repo.users,
                        onLogin = { u ->
                            repo.me.value = u
                            logInfo("MochaPop", "login swirl-in")
                            phase = MochaPhase.BRANCH_SELECT
                        },
                        onOnboarding = { phase = MochaPhase.ONBOARDING_LOCKED },
                    )
                    MochaPhase.ONBOARDING_LOCKED -> MochaOnboardingLocked(
                        onBack = { phase = MochaPhase.LOGIN },
                    )
                    MochaPhase.BRANCH_SELECT -> MochaBranchSelect(
                        repo = repo,
                        onPick = { phase = MochaPhase.APP },
                        onBack = { phase = MochaPhase.LOGIN },
                    )
                    MochaPhase.APP -> MochaShell(
                        repo = repo,
                        onLogout = { phase = MochaPhase.LOGIN },
                    )
                }
            }
            MochaFooter(repo)
        }
    }
}

@Composable
private fun MochaFooter(repo: MochaFakeRepo) {
    val pending = repo.sessions.count { it.status == MochaSessionStatus.PENDING && !it.voided }
    val unread = repo.notes.count { !it.read }
    Box(
        modifier = Modifier.fillMaxWidth().background(MochaPanel)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = "🍬 ${repo.dayLabel.value} ${repo.dayStatus.value.name} · $pending pending · $unread unread · " +
                "${repo.branchName(repo.branchId.value)} · fake candy only",
            fontSize = 12.sp,
            color = MochaMuted,
        )
    }
}

@Composable
private fun MochaShell(repo: MochaFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(MochaTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(250.dp).background(MochaPanel).padding(16.dp),
        ) {
            Text(text = "🍬", fontSize = 34.sp)
            Text(text = "mocha-pop", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MochaCandy)
            Text(text = "CANDY EVENINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MochaCaramel)
            Spacer(Modifier.height(4.dp))
            Text(text = repo.branchName(repo.branchId.value), fontSize = 13.sp, color = MochaMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (repo.clockedIn.value) "● CLOCKED IN" else "○ CLOCKED OUT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (repo.clockedIn.value) MochaMint else MochaCandy,
            )
            Spacer(Modifier.height(14.dp))
            MochaTab.entries.forEach { t ->
                val on = tab == t
                val unread = t == MochaTab.MAIL && repo.notes.any { !it.read }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (on) MochaCandy else Color.Transparent, MochaShapes.small)
                        .clickable { tab = t }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "◦ ${t.name}" + if (unread) " (${repo.notes.count { !it.read }})" else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (on) MochaNight else MochaCream,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "DAY ${repo.dayLabel.value} · ${repo.dayStatus.value.name}",
                fontSize = 12.sp,
                color = MochaMuted,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = repo.me.value.name, fontSize = 12.sp, color = MochaCaramel)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(26.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (tab) {
                    MochaTab.HOME -> MochaHome(repo)
                    MochaTab.SESSIONS -> MochaSessions(repo)
                    MochaTab.CLIENTS -> MochaClients(repo)
                    MochaTab.FINANCE -> MochaFinance(repo)
                    MochaTab.TEAM -> MochaTeam(repo)
                    MochaTab.MAIL -> MochaMailbox(repo)
                    MochaTab.AUDIT -> MochaAuditLog(repo)
                    MochaTab.PROFILE -> MochaProfile(repo, onLogout)
                }
            }
        }
    }
}
