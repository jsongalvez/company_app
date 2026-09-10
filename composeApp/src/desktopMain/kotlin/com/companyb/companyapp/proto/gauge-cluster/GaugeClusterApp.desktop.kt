package com.companyb.companyapp.proto.gaugecluster

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #834 — gauge-cluster shell: fascia header, branch-day lamp strip, dial rail + bay canvas.

enum class GaugeScreen(val bay: String) {
    WELCOME("Showroom"),
    SIGNIN("Ignition"),
    BRANCHES("Garage"),
    HOME("Cluster"),
    SESSIONS("Sessions"),
    CLIENTS("Registry"),
    FINANCE("Fuel & Ledger"),
    TEAM("Pit Crew"),
    MAIL("Signals"),
    LEDGER("Logbook"),
    PROFILE("Driver"),
}

@Composable
fun GaugeClusterApp(onBack: () -> Unit) {
    val repo = remember { GaugeFakeRepo() }
    var screen by remember { mutableStateOf(GaugeScreen.WELCOME) }

    LaunchedEffect(Unit) { logInfo("GaugeCluster", "gauge-cluster prototype launched") }

    val loggedIn = repo.currentUserId != null
    Column(Modifier.fillMaxSize().background(GaugeColors.Fascia)) {
        GaugeMasthead(repo = repo)
        GaugeDayStrip(repo = repo)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (loggedIn) {
                GaugeDialRail(
                    screen = screen,
                    unread = repo.notifications.count { !it.read },
                    onPick = { screen = it },
                    onBack = onBack,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (screen) {
                        GaugeScreen.WELCOME -> GaugeWelcome(repo = repo, onNext = { screen = GaugeScreen.SIGNIN })
                        GaugeScreen.SIGNIN -> GaugeSignin(repo = repo, onNext = { screen = GaugeScreen.BRANCHES })
                        GaugeScreen.BRANCHES -> GaugeBranches(repo = repo, onNext = { screen = GaugeScreen.HOME })
                        GaugeScreen.HOME -> GaugeHome(repo = repo, go = { screen = it })
                        GaugeScreen.SESSIONS -> GaugeSessions(repo = repo)
                        GaugeScreen.CLIENTS -> GaugeClients(repo = repo)
                        GaugeScreen.FINANCE -> GaugeFinance(repo = repo)
                        GaugeScreen.TEAM -> GaugeTeam(repo = repo)
                        GaugeScreen.MAIL -> GaugeMail(repo = repo)
                        GaugeScreen.LEDGER -> GaugeLedger(repo = repo)
                        GaugeScreen.PROFILE -> GaugeProfile(repo = repo, onSignOut = {
                            repo.currentUserId = null
                            repo.clockedIn = false
                            repo.reliefBranchId = null
                            screen = GaugeScreen.SIGNIN
                        })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun GaugeMasthead(repo: GaugeFakeRepo) {
    Row(
        Modifier.fillMaxWidth()
            .background(GaugeColors.FasciaDeep)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "COMPANYAPP  ●  GAUGE CLUSTER",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = GaugeColors.Cream,
                letterSpacing = 2.sp,
            )
            Text(
                (repo.me()?.name ?: "No driver") + "  ·  " +
                    (repo.branches.firstOrNull { it.id == repo.currentBranchId }?.name ?: "?") +
                    (if (repo.clockedIn) "  ·  CLOCKED IN" else "  ·  OFF DUTY"),
                fontSize = 12.sp,
                color = GaugeColors.CreamDim,
            )
        }
        GaugeLamp(
            when (repo.dayStatus) {
                GaugeDayStatus.OPEN -> "DAY OPEN"
                GaugeDayStatus.PAST -> "DAY PAST"
                GaugeDayStatus.REMITTED -> "REMITTED"
            },
            repo.dayStatus.lamp(),
        )
    }
}

@Composable
private fun GaugeDayStrip(repo: GaugeFakeRepo) {
    Row(
        Modifier.fillMaxWidth()
            .background(GaugeColors.Plate)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BRANCH DAY ${repo.operationalDate}  ·  boundary 04:00 Asia/Manila  ·  OPEN editable, PAST read-only for non-Coordinators, REMITTED sealed",
            fontSize = 12.sp,
            color = GaugeColors.CreamDim,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        GaugeDayStatus.entries.forEach { s ->
            val on = repo.dayStatus == s
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (on) s.lamp() else GaugeColors.FasciaDeep)
                    .clickable {
                        repo.dayStatus = s
                        repo.audit(repo.me()?.name ?: "proto", "SET_DAY_STATE", s.name)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    s.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) GaugeColors.FasciaDeep else GaugeColors.CreamDim,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun GaugeDialRail(
    screen: GaugeScreen,
    unread: Int,
    onPick: (GaugeScreen) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.width(190.dp).fillMaxHeight()
            .background(GaugeColors.FasciaDeep)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GaugeScreen.entries.filter { it != GaugeScreen.WELCOME && it != GaugeScreen.SIGNIN }.forEach { s ->
            val on = s == screen
            val label = if (s == GaugeScreen.MAIL && unread > 0) "${s.bay} ($unread)" else s.bay
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) GaugeColors.Bezel else GaugeColors.Plate)
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    "◉  $label",
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) GaugeColors.FasciaDeep else GaugeColors.Cream,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GaugeLink("← Exit prototype") { onBack() }
    }
}
