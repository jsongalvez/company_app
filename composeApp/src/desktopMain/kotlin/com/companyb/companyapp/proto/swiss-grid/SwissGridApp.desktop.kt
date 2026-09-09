package com.companyb.companyapp.proto.swissgrid

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
import androidx.compose.runtime.LaunchedEffect
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

// #793 — swiss-grid shell: black masthead rule, 12-col ruler, red index, screen router.

enum class SgScreen(val index: String, val title: String) {
    ONBOARDING("00", "Onboarding"),
    LOGIN("01", "Login"),
    BRANCHES("02", "Branches"),
    HOME("03", "Home"),
    SESSIONS("04", "Sessions"),
    CLIENTS("05", "Clients"),
    FINANCE("06", "Finance"),
    TEAM("07", "Team"),
    MAILBOX("08", "Mailbox"),
    AUDIT("09", "Audit"),
    PROFILE("10", "Profile"),
}

@Composable
fun ProtoSwissGridApp(onBack: () -> Unit) {
    val repo = remember { SgFakeRepo() }
    var screen by remember { mutableStateOf(SgScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("SwissGridProto", "swiss-grid prototype launched") }

    Column(Modifier.fillMaxSize().background(SgPaper)) {
        SgMasthead(repo = repo, onBack = onBack)
        SgDayBand(branch = repo.currentBranch())
        if (repo.loggedIn && screen != SgScreen.ONBOARDING && screen != SgScreen.LOGIN && screen != SgScreen.BRANCHES) {
            SgNavRail(current = screen, onPick = { screen = it })
        }
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                SgScreen.ONBOARDING -> SgOnboarding(
                    onContinue = { screen = SgScreen.LOGIN },
                    onPreviewLogin = { screen = SgScreen.LOGIN },
                )
                SgScreen.LOGIN -> SgLogin(
                    repo = repo,
                    onEnter = { screen = SgScreen.BRANCHES },
                    onBackOnboarding = { screen = SgScreen.ONBOARDING },
                )
                SgScreen.BRANCHES -> SgBranches(
                    repo = repo,
                    onPick = { screen = SgScreen.HOME },
                    onBack = { screen = SgScreen.LOGIN },
                )
                SgScreen.HOME -> SgHome(repo = repo, onOpenSessions = { screen = SgScreen.SESSIONS })
                SgScreen.SESSIONS -> SgSessions(repo = repo)
                SgScreen.CLIENTS -> SgClients(repo = repo)
                SgScreen.FINANCE -> SgFinance(repo = repo)
                SgScreen.TEAM -> SgTeam(repo = repo)
                SgScreen.MAILBOX -> SgMailbox(repo = repo)
                SgScreen.AUDIT -> SgAuditList(repo = repo)
                SgScreen.PROFILE -> SgProfile(repo = repo, onLogout = { screen = SgScreen.LOGIN }, onExit = onBack)
            }
        }
    }
}

@Composable
private fun SgMasthead(repo: SgFakeRepo, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(SgPaper).padding(horizontal = 28.dp, vertical = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "01",
                    fontFamily = SgSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = SgRed,
                )
                Text(
                    text = "COMPANY — GRID",
                    fontFamily = SgSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 2.sp,
                    color = SgInk,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SgLabel(if (repo.loggedIn) repo.email.ifBlank { "DEMO USER" } else "NOT LOGGED IN")
                Text(
                    text = "EXIT ×",
                    fontFamily = SgSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = SgInk,
                    modifier = Modifier.clickable(onClick = onBack).padding(6.dp),
                )
            }
        }
        Box(Modifier.height(10.dp))
        SgGridRuler()
        Box(Modifier.height(10.dp))
        SgThickRule()
    }
}

@Composable
private fun SgDayBand(branch: SgBranch) {
    val copy = when (branch.day) {
        SgDay.OPEN -> "OPEN — editable by all on-duty users."
        SgDay.PAST -> "PAST — coordinator-only edits, audit flagged."
        SgDay.REMITTED -> "REMITTED — covered by submitted remittance."
    }
    Row(
        Modifier.fillMaxWidth().background(SgBlack).padding(horizontal = 28.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${branch.name.uppercase()} · ${branch.day.name}",
            fontFamily = SgSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            color = SgPaper,
        )
        Text(
            text = (copy + " 04:00 Asia/Manila boundary.").uppercase(),
            fontFamily = SgSans,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = SgPaper,
        )
    }
}

@Composable
private fun SgNavRail(current: SgScreen, onPick: (SgScreen) -> Unit) {
    val tabs = listOf(SgScreen.HOME, SgScreen.SESSIONS, SgScreen.CLIENTS, SgScreen.FINANCE, SgScreen.TEAM, SgScreen.MAILBOX, SgScreen.AUDIT, SgScreen.PROFILE)
    Column(Modifier.fillMaxWidth().background(SgPaper).padding(horizontal = 28.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEach { t ->
                Column(
                    Modifier.weight(1f).clickable { onPick(t) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${t.index} ${t.title}".uppercase(),
                        fontFamily = SgSans,
                        fontWeight = if (t == current) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = if (t == current) SgInk else SgGrey,
                    )
                    Box(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(if (t == current) 3.dp else 1.dp)
                            .background(if (t == current) SgRed else SgHairline),
                    )
                }
            }
        }
        SgHairRule()
    }
}

@Composable
fun SgSplit(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.weight(7f)) { left() }
        Box(Modifier.weight(5f)) { right() }
    }
}
