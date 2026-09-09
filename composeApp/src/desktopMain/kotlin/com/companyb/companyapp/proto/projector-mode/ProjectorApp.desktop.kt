package com.companyb.companyapp.proto.projectormode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// #777 — projector shell: giant stage header, full-width day banner, single-row focus
// tabs, one focus card at a time. Fake data only.

enum class PmScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    HOME("Stage"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun ProtoProjectorModeApp(onBack: () -> Unit) {
    val repo = remember { ProjectorFakeRepo() }
    var screen by remember { mutableStateOf(PmScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("ProjectorProto", "projector-mode prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(PmColors.Stage)) {
        PmStageHeader(repo = repo)
        PmDayBanner(status = repo.dayStatus, onPick = { repo.dayStatus = it })
        PmTabStrip(
            screen = screen,
            loggedIn = loggedIn,
            unread = repo.notifications.count { !it.read },
            onPick = { screen = it },
            onBack = onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp)) {
            when (screen) {
                PmScreen.ONBOARDING -> PmOnboarding(repo = repo, onNext = { screen = PmScreen.LOGIN })
                PmScreen.LOGIN -> PmLogin(repo = repo, onNext = { screen = PmScreen.BRANCHES })
                PmScreen.BRANCHES -> PmBranchSelect(repo = repo, onNext = { screen = PmScreen.HOME })
                PmScreen.HOME -> PmHome(repo = repo, go = { screen = it })
                PmScreen.SESSIONS -> PmSessions(repo = repo)
                PmScreen.CLIENTS -> PmClients(repo = repo)
                PmScreen.FINANCE -> PmFinance(repo = repo)
                PmScreen.TEAM -> PmTeam(repo = repo)
                PmScreen.MAILBOX -> PmMailbox(repo = repo)
                PmScreen.AUDIT -> PmAudit(repo = repo)
                PmScreen.PROFILE -> PmProfile(repo = repo, onLogout = { screen = PmScreen.LOGIN })
            }
        }
    }
}

@Composable
private fun PmStageHeader(repo: ProjectorFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(PmColors.Stage).padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("PROJECTOR MODE", color = PmColors.Focus, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(
                repo.currentBranch.name.uppercase(),
                color = PmColors.Ink,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "${repo.operationalDate}  ·  ${repo.currentBranch.kind.name}",
                color = PmColors.Muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        PmGiant(value = repo.dayPendingCount().toString(), label = "pending")
        Spacer(Modifier.width(48.dp))
        PmGiant(value = "₱${repo.dayCompletedTotal()}", label = "completed")
        Spacer(Modifier.width(48.dp))
        val user = repo.currentUser
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (user?.name ?: "SIGNED OUT").uppercase(),
                color = PmColors.Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                user?.role?.name ?: "—",
                color = PmColors.Focus,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (repo.clockedIn) "● ON DUTY" else "○ OFF DUTY",
                color = if (repo.clockedIn) PmColors.Green else PmColors.Muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun PmTabStrip(
    screen: PmScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (PmScreen) -> Unit,
    onBack: () -> Unit,
) {
    val items = if (loggedIn) {
        listOf(
            PmScreen.HOME, PmScreen.SESSIONS, PmScreen.CLIENTS, PmScreen.FINANCE,
            PmScreen.TEAM, PmScreen.MAILBOX, PmScreen.AUDIT, PmScreen.BRANCHES, PmScreen.PROFILE,
        )
    } else {
        listOf(PmScreen.ONBOARDING, PmScreen.LOGIN)
    }
    Row(
        Modifier.fillMaxWidth().background(PmColors.Card).padding(horizontal = 20.dp, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = item == screen
            val label = if (item == PmScreen.MAILBOX && unread > 0) "Mailbox ($unread)" else item.title
            Box(
                Modifier.background(
                    if (active) PmColors.Focus else androidx.compose.ui.graphics.Color.Transparent,
                    androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                )
                    .clickable(onClick = { onPick(item) })
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(
                    label.uppercase(),
                    color = if (active) PmColors.FocusInk else PmColors.Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PmLink(text = "Exit", onClick = onBack)
    }
}
