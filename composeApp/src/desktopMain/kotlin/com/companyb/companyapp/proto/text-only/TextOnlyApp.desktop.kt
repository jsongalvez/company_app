package com.companyb.companyapp.proto.textonly

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

// #853 — text-only terminal shell: banner, branch-day line, numbered menu,
// scrollable reading pane. Fake data only.

enum class TxScreen(
    val index: String,
    val title: String,
) {
    ONBOARDING("00", "onboarding"),
    LOGIN("01", "login"),
    BRANCHES("02", "branches"),
    HOME("03", "home"),
    SESSIONS("04", "sessions"),
    CLIENTS("05", "clients"),
    FINANCE("06", "finance"),
    TEAM("07", "team"),
    MAILBOX("08", "mailbox"),
    AUDIT("09", "audit-log"),
    PROFILE("10", "profile"),
}

@Composable
fun ProtoTextOnlyApp(onBack: () -> Unit) {
    val repo = remember { TextOnlyFakeRepo() }
    var screen by remember { mutableStateOf(TxScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("TextOnlyProto", "text-only prototype launched") }

    Column(Modifier.fillMaxSize().background(TxTerm.Bg)) {
        TxBanner(repo = repo)
        TxDayLine(repo = repo)
        Row(Modifier.fillMaxSize()) {
            TxMenu(
                screen = screen,
                unread = repo.notices.count { !it.read },
                onPick = { screen = it },
                onBack = onBack,
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (screen) {
                    TxScreen.ONBOARDING -> TxOnboarding(repo = repo, onNext = { screen = TxScreen.LOGIN })
                    TxScreen.LOGIN -> TxLogin(repo = repo, onNext = { screen = TxScreen.BRANCHES })
                    TxScreen.BRANCHES -> TxBranchSelect(repo = repo, onNext = { screen = TxScreen.HOME })
                    TxScreen.HOME -> TxHome(repo = repo, go = { screen = it })
                    TxScreen.SESSIONS -> TxSessions(repo = repo)
                    TxScreen.CLIENTS -> TxClients(repo = repo)
                    TxScreen.FINANCE -> TxFinance(repo = repo)
                    TxScreen.TEAM -> TxTeam(repo = repo)
                    TxScreen.MAILBOX -> TxMailbox(repo = repo)
                    TxScreen.AUDIT -> TxAuditLog(repo = repo)
                    TxScreen.PROFILE -> TxProfile(repo = repo, onLogout = { screen = TxScreen.LOGIN })
                }
            }
        }
    }
}

@Composable
private fun TxBanner(repo: TextOnlyFakeRepo) {
    val user = repo.currentUser
    Column(
        Modifier
            .fillMaxWidth()
            .background(TxTerm.Panel)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("companyapp", color = TxTerm.Bright, size = 18, bold = true)
            Spacer(Modifier.width(8.dp))
            T("// text-only terminal", color = TxTerm.Amber, size = 13, bold = true)
            Spacer(Modifier.width(16.dp))
            T("80x25 spirit / 1280x800 minimum", size = 12, dim = true)
        }
        T(
            "user=${user?.name ?: "(signed out)"} " +
                "role=${user?.role?.label ?: "-"} " +
                "branch=${repo.branch.name} " +
                "clock=${if (repo.clockedIn) "IN" else "OUT"}",
            size = 12,
            color = TxTerm.Dim,
        )
        Rule()
    }
}

@Composable
private fun TxDayLine(repo: TextOnlyFakeRepo) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TxTerm.Bg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("branch-day: ", size = 12, dim = true)
        T(repo.branch.dayDate, size = 12, bold = true)
        Spacer(Modifier.width(12.dp))
        val color =
            when (repo.dayStatus) {
                TxDayState.OPEN -> TxTerm.Green
                TxDayState.PAST -> TxTerm.Amber
                TxDayState.REMITTED -> TxTerm.Dim
            }
        Tag(repo.dayStatus.label, color)
        Spacer(Modifier.width(12.dp))
        T("day boundary 04:00 Asia/Manila", size = 12, dim = true)
        Spacer(Modifier.width(12.dp))
        TxDayState.entries.forEach { st ->
            T(
                text = "[${st.label.lowercase()}]",
                size = 12,
                color = if (st == repo.dayStatus) TxTerm.Bright else TxTerm.Faint,
                bold = st == repo.dayStatus,
                modifier = Modifier.clickable { repo.setDay(st) }.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun TxMenu(
    screen: TxScreen,
    unread: Int,
    onPick: (TxScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .width(212.dp)
            .fillMaxHeight()
            .background(TxTerm.Panel)
            .padding(10.dp),
    ) {
        T("menu", size = 12, bold = true, color = TxTerm.Amber)
        T("------", size = 12, color = TxTerm.Faint)
        TxScreen.entries.forEach { s ->
            val active = s == screen
            val badge = if (s == TxScreen.MAILBOX && unread > 0) " ($unread)" else ""
            T(
                text = (if (active) "> " else "  ") + s.index + " " + s.title + badge,
                size = 13,
                bold = active,
                color = if (active) TxTerm.Bright else TxTerm.Ink,
                modifier = Modifier.clickable { onPick(s) }.padding(vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        T("------", size = 12, color = TxTerm.Faint)
        T(
            text = "  .. quit",
            size = 13,
            color = TxTerm.Red,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 3.dp),
        )
        Spacer(Modifier.height(12.dp))
        T("type-free zone:", size = 12, dim = true)
        T("click [ cmds ]", size = 12, dim = true)
        T("to run them.", size = 12, dim = true)
    }
}
