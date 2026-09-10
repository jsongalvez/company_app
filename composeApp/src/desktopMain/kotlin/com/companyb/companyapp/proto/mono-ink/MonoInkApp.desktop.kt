package com.companyb.companyapp.proto.monoink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.companyb.companyapp.util.logInfo

private enum class MonoPhase { LOGIN, ONBOARDING, BRANCHES, APP }

private enum class MonoPlace { FRONT, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

private fun MonoPlace.kicker(): String =
    when (this) {
        MonoPlace.FRONT -> "Front"
        MonoPlace.SESSIONS -> "Ledger"
        MonoPlace.CLIENTS -> "Directory"
        MonoPlace.FINANCE -> "Counting"
        MonoPlace.TEAM -> "Staff"
        MonoPlace.MAIL -> "Letters"
        MonoPlace.AUDIT -> "Log"
        MonoPlace.PROFILE -> "Colophon"
    }

@Composable
fun MonoInkApp() {
    logInfo("MonoInk", "single-ink prototype opened")
    MonoInkTheme {
        var phase by remember { mutableStateOf(MonoPhase.LOGIN) }
        var repo by remember { mutableStateOf(MonoFakeRepo()) }
        Box(modifier = Modifier.fillMaxSize().background(Paper)) {
            when (phase) {
                MonoPhase.LOGIN ->
                    MonoLogin(
                        onEnter = { address ->
                            repo.email = address
                            phase = MonoPhase.BRANCHES
                        },
                        onPreviewOnboarding = { phase = MonoPhase.ONBOARDING },
                    )
                MonoPhase.ONBOARDING -> MonoOnboardingLocked(onBack = { phase = MonoPhase.LOGIN })
                MonoPhase.BRANCHES ->
                    MonoBranchSelect(
                        repo = repo,
                        onPick = { phase = MonoPhase.APP },
                        onBack = { phase = MonoPhase.LOGIN },
                    )
                MonoPhase.APP ->
                    MonoShell(
                        repo = repo,
                        onLogout = {
                            repo.clock(false)
                            phase = MonoPhase.LOGIN
                        },
                        onReset = { repo = MonoFakeRepo().also { it.email = repo.email; it.branchId = repo.branchId } },
                        onSwitchBranch = { phase = MonoPhase.BRANCHES },
                    )
            }
        }
    }
}

@Composable
private fun MonoShell(
    repo: MonoFakeRepo,
    onLogout: () -> Unit,
    onReset: () -> Unit,
    onSwitchBranch: () -> Unit,
) {
    var place by remember { mutableStateOf(MonoPlace.FRONT) }
    val unread = repo.notices.count { !it.read }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(212.dp).fillMaxHeight().background(Paper).border(width = 1.dp, color = Ink).padding(vertical = 14.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("THE DAILY", fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp, color = Ink)
            Text("LEDGER", fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 4.sp, color = Ink)
            Text(repo.currentBranch().name.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = InkSoft, modifier = Modifier.clickable { onSwitchBranch() })
            Text("CHANGE BUREAU", fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 2.sp, color = Ink, modifier = Modifier.border(1.dp, Ink).padding(horizontal = 8.dp, vertical = 4.dp).clickable { onSwitchBranch() })
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { RuleThin() }
            MonoPlace.entries.forEachIndexed { n, p ->
                val on = place == p
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { place = p }.padding(vertical = 5.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("0${n + 1}", fontFamily = PressMono, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = Ink)
                    Text(
                        p.kicker().uppercase() + if (p == MonoPlace.MAIL && unread > 0) " ($unread)" else "",
                        fontFamily = PressSerif,
                        fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = Ink,
                    )
                }
                if (on) RuleThick() else RuleDotted()
            }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { RuleThin() }
            Text(if (repo.clockedIn) "● ON SHIFT" else "○ OFF SHIFT", fontFamily = PressMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(repo.email.ifBlank { "Practitioner" }, fontFamily = PressMono, fontSize = 11.sp, color = InkSoft)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (place) {
                MonoPlace.FRONT -> MonoFront(repo, onOpenSessions = { place = MonoPlace.SESSIONS })
                MonoPlace.SESSIONS -> MonoSessions(repo)
                MonoPlace.CLIENTS -> MonoClients(repo)
                MonoPlace.FINANCE -> MonoFinance(repo)
                MonoPlace.TEAM -> MonoTeam(repo)
                MonoPlace.MAIL -> MonoMailbox(repo)
                MonoPlace.AUDIT -> MonoAudit(repo)
                MonoPlace.PROFILE -> MonoProfile(repo, onLogout = onLogout, onReset = onReset)
            }
        }
    }
}
