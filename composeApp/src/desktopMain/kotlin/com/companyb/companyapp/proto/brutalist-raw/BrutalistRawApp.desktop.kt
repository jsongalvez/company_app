package com.companyb.companyapp.proto.brutalistraw

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class RawAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class RawTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun BrutalistRawProtoApp() {
    BrutalistRawTheme {
        var phase by remember { mutableStateOf(RawAuthPhase.LOGIN) }
        val repo = remember { BrutalistRawFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                RawAuthPhase.LOGIN -> RawLogin(
                    onLogin = {
                        logInfo("BrutalistRaw", "login stamped")
                        phase = RawAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = RawAuthPhase.ONBOARDING_LOCKED },
                )
                RawAuthPhase.ONBOARDING_LOCKED -> RawOnboardingLocked(
                    onBack = { phase = RawAuthPhase.LOGIN },
                )
                RawAuthPhase.BRANCH_SELECT -> RawBranchSelect(
                    repo = repo,
                    onPick = { phase = RawAuthPhase.APP },
                    onBack = { phase = RawAuthPhase.LOGIN },
                )
                RawAuthPhase.APP -> RawShell(
                    repo = repo,
                    onLogout = { phase = RawAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun RawShell(repo: BrutalistRawFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(RawTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(250.dp).background(RawInk).padding(0.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().background(RawHazard).padding(16.dp)) {
                Column {
                    Text(text = "BRUTALIST", fontSize = 30.sp, fontWeight = FontWeight.Black, color = RawWhite)
                    Text(text = "RAW // 001", fontSize = 30.sp, fontWeight = FontWeight.Black, color = RawInk)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().background(RawTar).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column {
                    Text(text = repo.branchName(repo.branchId.value), fontWeight = FontWeight.Bold, color = RawWhite)
                    Text(
                        text = if (repo.clockedIn.value) "[ CLOCKED IN ]" else "[ CLOCKED OUT ]",
                        fontWeight = FontWeight.Black,
                        color = if (repo.clockedIn.value) RawHazard else RawGravel,
                    )
                }
            }
            RawTab.entries.forEachIndexed { idx, t ->
                val on = tab == t
                val unread = t == RawTab.MAIL && repo.notes.any { !it.read }
                val num = "0${idx + 1}"
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (on) RawWhite else RawInk)
                        .clickable { tab = t }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "$num ${t.name}${if (unread) " (!)" else ""}",
                        fontWeight = FontWeight.Black,
                        color = if (on) RawInk else RawWhite,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.fillMaxWidth().background(RawDark).padding(16.dp)) {
                Text(text = "DAY: ${repo.dayStatus.value.name}", fontWeight = FontWeight.Bold, color = RawWhite)
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                RawDayBanner(repo)
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    RawTab.HOME -> RawHome(repo)
                    RawTab.SESSIONS -> RawSessions(repo)
                    RawTab.CLIENTS -> RawClients(repo)
                    RawTab.FINANCE -> RawFinance(repo)
                    RawTab.TEAM -> RawTeam(repo)
                    RawTab.MAIL -> RawMailbox(repo)
                    RawTab.AUDIT -> RawAuditLog(repo)
                    RawTab.PROFILE -> RawProfile(repo, onLogout)
                }
            }
        }
    }
}
