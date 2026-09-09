package com.companyb.companyapp.proto.wallboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

// #759 — wallboard shell: branch+date hero, day band, nav rail, screen router. Fake data only.

enum class WbScreen(val title: String) {
    ONBOARDING("Onboarding"),
    LOGIN("Login"),
    BRANCHES("Branches"),
    HOME("Wallboard"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit"),
    PROFILE("Profile"),
}

@Composable
fun ProtoWallboardApp(onBack: () -> Unit) {
    val repo = remember { WallboardFakeRepo() }
    var screen by remember { mutableStateOf(WbScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("WallboardProto", "wallboard prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(WbColors.Bg)) {
        WbHero(repo = repo)
        WbDayBand(status = repo.dayStatus, onPick = { repo.dayStatus = it })
        Row(Modifier.fillMaxSize()) {
            WbRail(
                screen = screen,
                loggedIn = loggedIn,
                unread = repo.notifications.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                when (screen) {
                    WbScreen.ONBOARDING -> WbOnboarding(repo = repo, onNext = { screen = WbScreen.LOGIN })
                    WbScreen.LOGIN -> WbLogin(repo = repo, onNext = { screen = WbScreen.BRANCHES })
                    WbScreen.BRANCHES -> WbBranchSelect(repo = repo, onNext = { screen = WbScreen.HOME })
                    WbScreen.HOME -> WbHome(repo = repo, go = { screen = it })
                    WbScreen.SESSIONS -> WbSessions(repo = repo)
                    WbScreen.CLIENTS -> WbClients(repo = repo)
                    WbScreen.FINANCE -> WbFinance(repo = repo)
                    WbScreen.TEAM -> WbTeam(repo = repo)
                    WbScreen.MAILBOX -> WbMailbox(repo = repo)
                    WbScreen.AUDIT -> WbAudit(repo = repo)
                    WbScreen.PROFILE -> WbProfile(repo = repo, onLogout = { screen = WbScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun WbHero(repo: WallboardFakeRepo) {
    Row(
        Modifier.fillMaxWidth().background(WbColors.Bg).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(repo.currentBranch.name.uppercase(), color = WbColors.Ink, fontSize = 34.sp,
                fontWeight = FontWeight.Black)
            Text("${repo.operationalDate}  ·  ${repo.currentBranch.kind.name}  ·  WALLBOARD",
                color = WbColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        WbNumeral(value = repo.dayPendingCount().toString(), label = "pending")
        Spacer(Modifier.width(28.dp))
        WbNumeral(value = "₱${repo.dayCompletedTotal()}", label = "completed")
        Spacer(Modifier.width(28.dp))
        val user = repo.currentUser
        Column(horizontalAlignment = Alignment.End) {
            Text((user?.name ?: "signed out").uppercase(), color = WbColors.Amber, fontSize = 16.sp,
                fontWeight = FontWeight.Black)
            Text((user?.role?.name ?: "—"), color = WbColors.Muted, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
            Text(if (repo.clockedIn) "● CLOCKED IN" else "○ OFF DUTY",
                color = if (repo.clockedIn) WbColors.Green else WbColors.Muted, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WbRail(
    screen: WbScreen,
    loggedIn: Boolean,
    unread: Int,
    onPick: (WbScreen) -> Unit,
    onBack: () -> Unit,
) {
    val items = if (loggedIn) {
        listOf(WbScreen.HOME, WbScreen.SESSIONS, WbScreen.CLIENTS, WbScreen.FINANCE, WbScreen.TEAM,
            WbScreen.MAILBOX, WbScreen.AUDIT, WbScreen.BRANCHES, WbScreen.PROFILE)
    } else {
        listOf(WbScreen.ONBOARDING, WbScreen.LOGIN)
    }
    Column(Modifier.width(190.dp).fillMaxHeight().background(WbColors.Panel).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("COMMAND", color = WbColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        items.forEach { item ->
            val active = item == screen
            val label = if (item == WbScreen.MAILBOX && unread > 0) "Mailbox ($unread)" else item.title
            Box(
                Modifier.fillMaxWidth()
                    .background(if (active) WbColors.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(onClick = { onPick(item) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(label, color = if (active) Color.Black else WbColors.Ink,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        WbLink(text = "Exit prototype", onClick = onBack)
    }
}
