package com.companyb.companyapp.proto.pixelretro

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

private enum class PxPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class PxTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun PixelRetroProtoApp() {
    PixelRetroTheme {
        var phase by remember { mutableStateOf(PxPhase.LOGIN) }
        val repo = remember { PixelRetroFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(PxVoid),
        ) {
            when (phase) {
                PxPhase.LOGIN -> {
                    PxLogin(
                        repo = repo,
                        onLogin = {
                            logInfo("PixelRetro", "player pressed start")
                            phase = PxPhase.BRANCH_SELECT
                        },
                        onOnboarding = { phase = PxPhase.ONBOARDING_LOCKED },
                    )
                }

                PxPhase.ONBOARDING_LOCKED -> {
                    PxOnboardingLocked(onBack = { phase = PxPhase.LOGIN })
                }

                PxPhase.BRANCH_SELECT -> {
                    PxBranchSelect(
                        repo = repo,
                        onPick = { phase = PxPhase.APP },
                        onBack = { phase = PxPhase.LOGIN },
                    )
                }

                PxPhase.APP -> {
                    PxShell(
                        repo = repo,
                        onLogout = { phase = PxPhase.LOGIN },
                    )
                }
            }
        }
    }
}

@Composable
private fun PxShell(
    repo: PixelRetroFakeRepo,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(PxTab.HOME) }
    val unread = repo.unreadCount()
    Column(modifier = Modifier.fillMaxSize()) {
        PxMarquee(
            title = "★ PIXEL CLINIC ★",
            subtitle = "${repo.branchName(repo.branchId.value)} · Day 041 · ${repo.me.value}",
            right = if (repo.clockedIn.value) "■ ON SHIFT" else "□ OFF SHIFT",
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(250.dp)
                        .background(PxVoid)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                PxPanelBox(border = if (repo.clockedIn.value) PxGreen else PxDim) {
                    if (repo.clockedIn.value) {
                        PxTag("■ CLOCKED IN", PxGreen)
                    } else {
                        PxTag("□ CLOCKED OUT", PxDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    PxDim("▓ DAY: ${repo.dayStatus.value.name}")
                    Spacer(Modifier.height(2.dp))
                    PxDim("▓ ${dayBlurb(repo.dayStatus.value)}")
                }
                Spacer(Modifier.height(10.dp))
                PxNavButton("Home base", tab == PxTab.HOME, onClick = { tab = PxTab.HOME })
                PxNavButton("Quests", tab == PxTab.SESSIONS, onClick = { tab = PxTab.SESSIONS })
                PxNavButton("Party list", tab == PxTab.CLIENTS, onClick = { tab = PxTab.CLIENTS })
                PxNavButton("Coffers", tab == PxTab.FINANCE, onClick = { tab = PxTab.FINANCE })
                PxNavButton("Guild", tab == PxTab.TEAM, onClick = { tab = PxTab.TEAM })
                PxNavButton(
                    "Mailbox",
                    tab == PxTab.MAIL,
                    badge = if (unread > 0) "$unread NEW" else "",
                    onClick = { tab = PxTab.MAIL },
                )
                PxNavButton("Quest log", tab == PxTab.AUDIT, onClick = { tab = PxTab.AUDIT })
                PxNavButton("Player", tab == PxTab.PROFILE, onClick = { tab = PxTab.PROFILE })
                Spacer(Modifier.height(6.dp))
                PxDim("HI-SCORE 999999")
                PxDim("© DAY 041 ARCADE")
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(PxVoid)
                        .padding(10.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    when (tab) {
                        PxTab.HOME -> PxHomeScreen(repo)
                        PxTab.SESSIONS -> PxSessionsScreen(repo)
                        PxTab.CLIENTS -> PxClientsScreen(repo)
                        PxTab.FINANCE -> PxFinanceScreen(repo)
                        PxTab.TEAM -> PxTeamScreen(repo)
                        PxTab.MAIL -> PxMailboxScreen(repo)
                        PxTab.AUDIT -> PxAuditScreen(repo)
                        PxTab.PROFILE -> PxProfileScreen(repo, onLogout)
                    }
                }
            }
        }
    }
}

fun dayBlurb(day: PxDayStatus): String =
    when (day) {
        PxDayStatus.OPEN -> "Stage open, coins flowing"
        PxDayStatus.PAST -> "Stage closed, tally soon"
        PxDayStatus.REMITTED -> "Loot banked, run sealed"
    }

fun statusColor(status: PxSessionStatus) =
    when (status) {
        PxSessionStatus.PENDING -> PxYellow
        PxSessionStatus.COMPLETED -> PxGreen
        PxSessionStatus.NO_SHOW -> PxOrange
        PxSessionStatus.CANCELLED -> PxRed
    }

fun PxSession.displayClient(): String = if (walkIn) "$clientName (WALK-IN)" else clientName

@Composable
fun PxSectionHead(
    title: String,
    hint: String,
) {
    PxTitle(title, size = 18)
    Spacer(Modifier.height(2.dp))
    PxDim("▓ $hint")
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PxBanner(repo: PixelRetroFakeRepo) {
    val edge =
        when (repo.dayStatus.value) {
            PxDayStatus.OPEN -> PxGreen
            PxDayStatus.PAST -> PxOrange
            PxDayStatus.REMITTED -> PxCyan
        }
    PxPanelBox(border = edge) {
        Row(modifier = Modifier.fillMaxWidth()) {
            PxTag("DAY 041 · ${repo.dayStatus.value.name}", edge)
            Spacer(Modifier.width(8.dp))
            PxTag(repo.branchName(repo.branchId.value), PxYellow)
        }
        Spacer(Modifier.height(6.dp))
        PxBody("Branch Day ${repo.dayStatus.value.name}: ${dayBlurb(repo.dayStatus.value)}. New day rolls at 04:00 Asia/Manila — after that, yesterday locks and coin counts freeze.")
    }
    Spacer(Modifier.height(10.dp))
}
