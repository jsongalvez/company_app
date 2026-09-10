package com.companyb.companyapp.proto.nordfrost

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private enum class NfPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class NfTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun NordFrostProtoApp() {
    NordFrostTheme {
        var phase by remember { mutableStateOf(NfPhase.LOGIN) }
        val repo = remember { NordFrostFakeRepo() }
        Box(modifier = Modifier.fillMaxSize().background(NfSnow)) {
            when (phase) {
                NfPhase.LOGIN -> {
                    NfLogin(
                        repo = repo,
                        onLogin = {
                            logInfo("NordFrost", "keeper stepped through the frost door")
                            phase = NfPhase.BRANCH_SELECT
                        },
                        onOnboarding = { phase = NfPhase.ONBOARDING_LOCKED },
                    )
                }

                NfPhase.ONBOARDING_LOCKED -> {
                    NfOnboardingLocked(onBack = { phase = NfPhase.LOGIN })
                }

                NfPhase.BRANCH_SELECT -> {
                    NfBranchSelect(
                        repo = repo,
                        onPick = { phase = NfPhase.APP },
                        onBack = { phase = NfPhase.LOGIN },
                    )
                }

                NfPhase.APP -> {
                    NfShell(repo = repo, onLogout = { phase = NfPhase.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun NfShell(
    repo: NordFrostFakeRepo,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(NfTab.HOME) }
    val unread = repo.unreadCount()
    Column(modifier = Modifier.fillMaxSize()) {
        NfMarquee(
            title = "❄ Frost Desk ❄",
            subtitle = "${repo.branchName(repo.branchId.value)} · Day 041 · ${repo.me.value}",
            right = if (repo.clockedIn.value) "● On watch" else "○ Off watch",
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(264.dp)
                        .background(NfSnowSoft)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                NfPanelBox(border = if (repo.clockedIn.value) NfTeal else NfDrift) {
                    if (repo.clockedIn.value) {
                        NfTag("● Clocked in", dot = NfTealInk, wash = NfTealWash)
                    } else {
                        NfTag("○ Clocked out", dot = NfMist, wash = NfSnowSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                    NfMicroLabel("Branch day · ${repo.dayStatus.value.name}")
                    Spacer(Modifier.height(2.dp))
                    NfDim(dayBlurb(repo.dayStatus.value))
                }
                Spacer(Modifier.height(12.dp))
                NfNavButton("Home drift", tab == NfTab.HOME, onClick = { tab = NfTab.HOME })
                NfNavButton("Sessions", tab == NfTab.SESSIONS, onClick = { tab = NfTab.SESSIONS })
                NfNavButton("Clients", tab == NfTab.CLIENTS, onClick = { tab = NfTab.CLIENTS })
                NfNavButton("Finance", tab == NfTab.FINANCE, onClick = { tab = NfTab.FINANCE })
                NfNavButton("Team", tab == NfTab.TEAM, onClick = { tab = NfTab.TEAM })
                NfNavButton(
                    "Mailbox",
                    tab == NfTab.MAIL,
                    badge = if (unread > 0) "$unread NEW" else "",
                    onClick = { tab = NfTab.MAIL },
                )
                NfNavButton("Audit trail", tab == NfTab.AUDIT, onClick = { tab = NfTab.AUDIT })
                NfNavButton("Profile", tab == NfTab.PROFILE, onClick = { tab = NfTab.PROFILE })
                Spacer(Modifier.height(12.dp))
                NfFrostRule(NfDrift)
                Spacer(Modifier.height(8.dp))
                NfDim("❄ 04:00 Asia/Manila rolls the day")
                NfDim("◇ Snow surfaces, clinical chill")
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(NfSnow)
                        .padding(14.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    when (tab) {
                        NfTab.HOME -> NfHomeScreen(repo)
                        NfTab.SESSIONS -> NfSessionsScreen(repo)
                        NfTab.CLIENTS -> NfClientsScreen(repo)
                        NfTab.FINANCE -> NfFinanceScreen(repo)
                        NfTab.TEAM -> NfTeamScreen(repo)
                        NfTab.MAIL -> NfMailboxScreen(repo)
                        NfTab.AUDIT -> NfAuditScreen(repo)
                        NfTab.PROFILE -> NfProfileScreen(repo, onLogout)
                    }
                }
            }
        }
    }
}

fun dayBlurb(day: NfDayStatus): String =
    when (day) {
        NfDayStatus.OPEN -> "Drift open, chairs warm beneath the frost"
        NfDayStatus.PAST -> "Drift sealed at dusk, tally before the thaw"
        NfDayStatus.REMITTED -> "Vault frozen shut, day archived in ice"
    }

fun statusWash(status: NfSessionStatus) =
    when (status) {
        NfSessionStatus.PENDING -> NfGlacierWash
        NfSessionStatus.COMPLETED -> NfTealWash
        NfSessionStatus.NO_SHOW -> NfAmberWash
        NfSessionStatus.CANCELLED -> NfRoseWash
    }

fun statusDot(status: NfSessionStatus) =
    when (status) {
        NfSessionStatus.PENDING -> NfGlacier
        NfSessionStatus.COMPLETED -> NfTealInk
        NfSessionStatus.NO_SHOW -> NfAmber
        NfSessionStatus.CANCELLED -> NfRose
    }

fun NfSession.displayClient(): String = if (walkIn) "$clientName (WALK-IN)" else clientName

@Composable
fun NfSectionHead(
    title: String,
    hint: String,
) {
    NfTitle(title, size = 18)
    Spacer(Modifier.height(2.dp))
    NfDim("❄ $hint")
    Spacer(Modifier.height(10.dp))
}

@Composable
fun NfBanner(repo: NordFrostFakeRepo) {
    val edge =
        when (repo.dayStatus.value) {
            NfDayStatus.OPEN -> NfTeal
            NfDayStatus.PAST -> NfAmber
            NfDayStatus.REMITTED -> NfGlacier
        }
    NfPanelBox(border = edge) {
        Row(modifier = Modifier.fillMaxWidth()) {
            NfTag("Day 041 · ${repo.dayStatus.value.name}", dot = edge)
            Spacer(Modifier.width(8.dp))
            NfTag(repo.branchName(repo.branchId.value), dot = NfFrostDeep)
        }
        Spacer(Modifier.height(8.dp))
        NfBody(
            "Branch Day ${repo.dayStatus.value.name}: ${dayBlurb(repo.dayStatus.value)}. " +
                "The new day breaks at 04:00 Asia/Manila — after that, yesterday freezes over and counts lock in ice.",
        )
    }
    Spacer(Modifier.height(12.dp))
}
