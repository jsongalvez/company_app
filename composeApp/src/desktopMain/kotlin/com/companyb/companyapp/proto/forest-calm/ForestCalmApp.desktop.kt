package com.companyb.companyapp.proto.forestcalm

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #807 — forest-calm shell: login, branch select, grove rail + day banner.

@Composable
fun ForestCalmApp(onBack: () -> Unit) {
    val repo = remember { ForestCalmRepo() }
    var user by remember { mutableStateOf<FcUser?>(null) }
    var branch by remember { mutableStateOf<FcBranch?>(null) }
    var screen by remember { mutableStateOf(FcScreen.HOME) }
    FcRoot {
        val u = user
        val b = branch
        when {
            u == null -> FcLogin(repo.users, onPick = {
                user = it
                repo.log(it.login, "login forest floor")
                logInfo("ForestCalm", "login " + it.login)
            })
            u.locked -> FcLockedOut(u, onBack = { user = null })
            b == null -> FcBranchSelect(repo, u, onPick = { branch = it }, onBack = { user = null })
            else -> FcShell(
                repo, u, b, screen,
                onScreen = { screen = it },
                onBranchChange = { branch = null },
                onLogout = {
                    repo.log(u.login, "logout")
                    user = null
                    branch = null
                    screen = FcScreen.HOME
                },
                onExit = onBack,
            )
        }
    }
}

@Composable
private fun FcShell(
    repo: ForestCalmRepo,
    user: FcUser,
    branch: FcBranch,
    screen: FcScreen,
    onScreen: (FcScreen) -> Unit,
    onBranchChange: () -> Unit,
    onLogout: () -> Unit,
    onExit: () -> Unit,
) {
    val unread = repo.notices.count { !it.read }
    Row(modifier = Modifier.fillMaxSize().background(FcPaper)) {
        Column(
            modifier = Modifier.width(240.dp).fillMaxHeight()
                .background(FcPaperDeep)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "❧ forest-calm", fontFamily = FcSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FcBark)
            Text(
                "${user.name} · ${user.role}",
                fontFamily = FcSans, fontSize = 12.sp, color = FcInkSoft,
            )
            Spacer(Modifier.height(6.dp))
            FcNavItem("Clearing", "☀", screen == FcScreen.HOME) { onScreen(FcScreen.HOME) }
            FcNavItem("Sessions", "❧", screen == FcScreen.SESSIONS) { onScreen(FcScreen.SESSIONS) }
            FcNavItem("Clients", "✿", screen == FcScreen.CLIENTS) { onScreen(FcScreen.CLIENTS) }
            FcNavItem("Finance", "⬢", screen == FcScreen.FINANCE) { onScreen(FcScreen.FINANCE) }
            FcNavItem("Keepers", "♣", screen == FcScreen.TEAM) { onScreen(FcScreen.TEAM) }
            FcNavItem("Nest", "✉", screen == FcScreen.MAIL, unread) { onScreen(FcScreen.MAIL) }
            FcNavItem("Rings", "◎", screen == FcScreen.AUDIT) { onScreen(FcScreen.AUDIT) }
            FcNavItem("Keeper", "●", screen == FcScreen.PROFILE) { onScreen(FcScreen.PROFILE) }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FcMossDeep)
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(branch.name, fontFamily = FcSans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Branch Day ${branch.dayStatus}",
                        fontFamily = FcSans, fontSize = 11.sp, color = FcFernPale,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    " Change grove",
                    fontFamily = FcSans, fontSize = 12.sp, color = FcMossDeep,
                    modifier = Modifier.clickableNoRipple(onBranchChange).padding(vertical = 4.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FcDayBanner(branch)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    FcScreen.HOME -> FcHome(repo, user, branch)
                    FcScreen.SESSIONS -> FcSessions(repo, user, branch)
                    FcScreen.CLIENTS -> FcClients(repo, user)
                    FcScreen.FINANCE -> FcFinance(repo, user)
                    FcScreen.TEAM -> FcTeam(repo, user)
                    FcScreen.MAIL -> FcMail(repo)
                    FcScreen.AUDIT -> FcAudit(repo)
                    FcScreen.PROFILE -> FcProfile(repo, user, branch, onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick,
        ),
    )
