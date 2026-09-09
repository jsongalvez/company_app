package com.companyb.companyapp.proto.neurosoft

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class SoftAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class SoftTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun NeuroSoftProtoApp() {
    NeuroSoftTheme {
        var phase by remember { mutableStateOf(SoftAuthPhase.LOGIN) }
        val repo = remember { NeuroSoftFakeRepo() }
        Box(
            modifier = Modifier.fillMaxSize().background(SoftBg),
        ) {
            when (phase) {
                SoftAuthPhase.LOGIN -> SoftLogin(
                    onLogin = {
                        logInfo("NeuroSoft", "login soft press")
                        phase = SoftAuthPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = SoftAuthPhase.ONBOARDING_LOCKED },
                )
                SoftAuthPhase.ONBOARDING_LOCKED -> SoftOnboardingLocked(
                    onBack = { phase = SoftAuthPhase.LOGIN },
                )
                SoftAuthPhase.BRANCH_SELECT -> SoftBranchSelect(
                    repo = repo,
                    onPick = { phase = SoftAuthPhase.APP },
                    onBack = { phase = SoftAuthPhase.LOGIN },
                )
                SoftAuthPhase.APP -> SoftShell(
                    repo = repo,
                    onLogout = { phase = SoftAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
private fun SoftShell(repo: NeuroSoftFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(SoftTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(248.dp).background(SoftBg).padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SoftRaised)
                    .padding(16.dp),
            ) {
                Column {
                    Text(text = "◍", fontSize = 30.sp, color = SoftPrimary)
                    Text(text = "neuro-soft", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = SoftPrimaryDeep)
                    Text(text = repo.branchName(repo.branchId.value), fontSize = 12.sp, color = SoftMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (repo.clockedIn.value) "pressed in" else "flat (out)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (repo.clockedIn.value) SoftMintDeep else SoftRose,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SoftTab.entries.forEach { t ->
                val on = tab == t
                val unread = t == SoftTab.MAIL && repo.notes.any { !it.read }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .shadow(if (on) 6.dp else 0.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) SoftPrimary else SoftRaised)
                        .clickable { tab = t }
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = t.name + if (unread) " (•)" else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (on) Color.White else SoftInk,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.weight(1f))
            SoftInsetLabel("Day: ${repo.dayStatus.value.name}")
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(SoftBg).padding(26.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (tab) {
                    SoftTab.HOME -> SoftHome(repo)
                    SoftTab.SESSIONS -> SoftSessions(repo)
                    SoftTab.CLIENTS -> SoftClients(repo)
                    SoftTab.FINANCE -> SoftFinance(repo)
                    SoftTab.TEAM -> SoftTeam(repo)
                    SoftTab.MAIL -> SoftMailbox(repo)
                    SoftTab.AUDIT -> SoftAuditLog(repo)
                    SoftTab.PROFILE -> SoftProfile(repo, onLogout)
                }
            }
        }
    }
}

@Composable
private fun SoftInsetLabel(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftPressed)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = SoftMuted, fontWeight = FontWeight.SemiBold)
    }
}
