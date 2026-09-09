package com.companyb.companyapp.proto.reportpack

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

// #787 — report-pack shell: owner cover masthead, branch-day ribbon, workbook tab
// rail, sheet content. Window opens 1280x800 minimum, full-screen capable.

enum class RpScreen(val tab: String) {
    ONBOARDING("00 · Cover"),
    LOGIN("01 · Sign in"),
    BRANCHES("02 · Branches"),
    HOME("03 · Duty"),
    PACK("04 · Pack"),
    ROLLUP("05 · Rollup"),
    SESSIONS("06 · Sessions"),
    CLIENTS("07 · Clients"),
    FINANCE("08 · Finance"),
    TEAM("09 · Team"),
    MAILBOX("10 · Mailbox"),
    AUDIT("11 · Audit"),
    PROFILE("12 · Profile"),
}

@Composable
fun ReportPackProtoApp(onBack: () -> Unit) {
    val repo = remember { ReportPackFakeRepo() }
    var screen by remember { mutableStateOf(RpScreen.ONBOARDING) }

    LaunchedEffect(Unit) { logInfo("ReportPackProto", "report-pack prototype launched") }

    val unread = repo.notifications.count { !it.read }
    val packName = "${repo.currentBranch.name} ${repo.period.name.lowercase()} pack"

    Column(Modifier.fillMaxSize().background(RpColors.Page)) {
        RpCoverMasthead(repo = repo, packName = packName)
        RpRibbon(
            dayLabel = "${repo.selectedDay.dow} ${repo.selectedDay.dateLabel}",
            branchName = repo.currentBranch.name,
            status = repo.selectedDay.status,
            onPickDay = { screen = RpScreen.BRANCHES },
        )
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(190.dp).fillMaxHeight().padding(10.dp)) {
                RpTabRail(
                    tabs = RpScreen.entries.map { it.name to it.tab },
                    selected = screen.name,
                    unread = unread,
                    onPick = { screen = RpScreen.valueOf(it) },
                )
                Spacer(Modifier.height(10.dp))
                RpGhost("Exit prototype", onClick = onBack)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(10.dp),
            ) {
                Column {
                    if (screen == RpScreen.PACK || screen == RpScreen.ROLLUP || screen == RpScreen.FINANCE) {
                        RpExportBar(packName = packName, onShared = { repo.sharePack(it) })
                        Spacer(Modifier.height(10.dp))
                    }
                    when (screen) {
                        RpScreen.ONBOARDING -> RpOnboarding(repo = repo, onNext = { screen = RpScreen.LOGIN })
                        RpScreen.LOGIN -> RpLogin(repo = repo, onNext = { screen = RpScreen.BRANCHES })
                        RpScreen.BRANCHES -> RpBranchSelect(repo = repo, onNext = { screen = RpScreen.HOME })
                        RpScreen.HOME -> RpDuty(repo = repo, go = { screen = it })
                        RpScreen.PACK -> RpPackSheet(repo = repo, go = { screen = it })
                        RpScreen.ROLLUP -> RpRollupSheet(repo = repo)
                        RpScreen.SESSIONS -> RpSessions(repo = repo)
                        RpScreen.CLIENTS -> RpClients(repo = repo)
                        RpScreen.FINANCE -> RpFinance(repo = repo)
                        RpScreen.TEAM -> RpTeam(repo = repo)
                        RpScreen.MAILBOX -> RpMailbox(repo = repo)
                        RpScreen.AUDIT -> RpAuditList(repo = repo)
                        RpScreen.PROFILE -> RpProfile(repo = repo, onLogout = { screen = RpScreen.LOGIN })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RpCoverMasthead(repo: ReportPackFakeRepo, packName: String) {
    Column(
        Modifier.fillMaxWidth().background(RpColors.Navy).padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "COMPANYB · OWNER REPORT PACK",
                    color = Color(0xFF9DB4D4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    packName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    (repo.currentUser?.let { "${it.name} · ${it.role.name}" } ?: "SIGNED OUT") +
                        "  ·  " + if (repo.clockedIn) "ON DUTY ${repo.dutyBranchId?.let(repo::branchName) ?: ""}" else "OFF DUTY",
                    color = Color(0xFFC9D6E8),
                    fontSize = 12.sp,
                    fontFamily = RpMono,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PERIOD  ", color = Color(0xFF9DB4D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    RpPeriodChip("WEEK", repo.period == RpPeriod.WEEK, dark = true) { repo.period = RpPeriod.WEEK }
                    Spacer(Modifier.width(6.dp))
                    RpPeriodChip("MONTH", repo.period == RpPeriod.MONTH, dark = true) { repo.period = RpPeriod.MONTH }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "fake data · no network · 04:00 Asia/Manila close",
                    color = Color(0xFF9DB4D4),
                    fontSize = 11.sp,
                    fontFamily = RpMono,
                )
            }
        }
    }
}

@Composable
private fun RpPeriodChip(text: String, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val bg = when {
        selected && dark -> Color.White
        selected -> RpColors.NavySoft
        else -> Color.Transparent
    }
    val fg = when {
        selected -> RpColors.Navy
        dark -> Color.White
        else -> RpColors.Faint
    }
    Box(
        Modifier.background(bg, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = RpMono)
    }
}
