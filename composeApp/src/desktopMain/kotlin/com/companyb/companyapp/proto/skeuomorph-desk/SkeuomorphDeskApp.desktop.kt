package com.companyb.companyapp.proto.skeuomorphdesk

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
import androidx.compose.material3.MaterialTheme
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

private enum class DeskAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class DeskTab { DESK, LEDGER, FILES, TILL, STAFF, POST, LOG, STUDY }

@Composable
fun SkeuomorphDeskProtoApp() {
    SkeuomorphDeskTheme {
        var phase by remember { mutableStateOf(DeskAuthPhase.LOGIN) }
        val repo = remember { SkeuomorphDeskFakeRepo() }
        WoodBackdrop {
            when (phase) {
                DeskAuthPhase.LOGIN -> DeskLogin(
                    onLogin = {
                        logInfo("SkeuomorphDesk", "signet pressed, opening branch drawer")
                        phase = DeskAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = DeskAuthPhase.ONBOARDING_LOCKED },
                )
                DeskAuthPhase.ONBOARDING_LOCKED -> DeskOnboardingLocked(
                    onBack = { phase = DeskAuthPhase.LOGIN },
                )
                DeskAuthPhase.BRANCH_SELECT -> DeskBranchSelect(
                    repo = repo,
                    onPick = { phase = DeskAuthPhase.APP },
                    onBack = { phase = DeskAuthPhase.LOGIN },
                )
                DeskAuthPhase.APP -> DeskShell(
                    repo = repo,
                    onLogout = { phase = DeskAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun DeskShell(repo: SkeuomorphDeskFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(DeskTab.DESK) }
    Row(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        Column(
            modifier = Modifier.fillMaxHeight().width(264.dp),
        ) {
            BrassPlate(
                title = "THE OLD DESK",
                sub = "Branch ledger no. 7",
            )
            Spacer(Modifier.height(10.dp))
            LeatherPad {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = repo.branchName(repo.branchId.value),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = DeskPaper,
                    )
                    Text(
                        text = if (repo.clockedIn.value) "● Clocked in" else "○ Clocked out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (repo.clockedIn.value) DeskBrass else DeskPaperDark,
                    )
                    Text(
                        text = "Day: ${repo.dayStatus.value.name}",
                        fontSize = 13.sp,
                        color = DeskPaperDark,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val tabs = DeskTab.entries
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                tabs.forEach { t ->
                    val on = tab == t
                    val unread = t == DeskTab.POST && repo.notes.any { !it.read }
                    FolderTab(
                        label = t.name,
                        badge = unread,
                        selected = on,
                        onClick = { tab = t },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "04:00 Asia/Manila closes the day",
                fontSize = 11.sp,
                color = DeskPaperDark,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            DeskDayRibbon(repo)
            Spacer(Modifier.height(12.dp))
            LeatherPad {
                Box(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    when (tab) {
                        DeskTab.DESK -> DeskHome(repo)
                        DeskTab.LEDGER -> DeskSessions(repo)
                        DeskTab.FILES -> DeskClients(repo)
                        DeskTab.TILL -> DeskFinance(repo)
                        DeskTab.STAFF -> DeskTeam(repo)
                        DeskTab.POST -> DeskMailbox(repo)
                        DeskTab.LOG -> DeskAuditLog(repo)
                        DeskTab.STUDY -> DeskProfile(repo, onLogout)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTab(label: String, badge: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = if (selected) 0.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier.weight(1f)
                .background(
                    if (selected) DeskPaper else DeskPaperDark,
                    MaterialTheme.shapes.small,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DeskInk,
                    modifier = Modifier.weight(1f),
                )
                if (badge) {
                    Box(
                        modifier = Modifier.background(DeskStamp, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(text = "POST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeskPaper)
                    }
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier.width(6.dp).fillMaxHeight().background(DeskBrass),
            ) {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
