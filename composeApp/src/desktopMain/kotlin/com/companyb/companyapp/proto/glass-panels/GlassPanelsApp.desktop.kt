package com.companyb.companyapp.proto.glasspanels

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

private enum class GpPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class GpTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun GlassPanelsProtoApp() {
    GlassTheme {
        var phase by remember { mutableStateOf(GpPhase.LOGIN) }
        val repo = remember { GlassPanelsFakeRepo() }
        GlassBackground {
            when (phase) {
                GpPhase.LOGIN -> GpLogin(
                    onLogin = {
                        logInfo("GlassPanels", "login drift in")
                        phase = GpPhase.BRANCH_SELECT
                    },
                    onOnboarding = { phase = GpPhase.ONBOARDING_LOCKED },
                )
                GpPhase.ONBOARDING_LOCKED -> GpOnboardingLocked(onBack = { phase = GpPhase.LOGIN })
                GpPhase.BRANCH_SELECT -> GpBranchSelect(
                    repo = repo,
                    onPick = { phase = GpPhase.APP },
                    onBack = { phase = GpPhase.LOGIN },
                )
                GpPhase.APP -> GpShell(repo = repo, onLogout = { phase = GpPhase.LOGIN })
            }
        }
    }
}

@Composable
private fun GpShell(repo: GlassPanelsFakeRepo, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(GpTab.HOME) }
    val unread = repo.notes.count { !it.read }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        GpDayBanner(repo)
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassSheet(modifier = Modifier.width(218.dp).fillMaxHeight(), radius = 24.dp) {
                Column {
                    Text(text = "◍ GLASS", fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, color = FrostText)
                    Text(text = "PANELS", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AquaGlow)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = repo.branchName(repo.branchId.value), fontSize = 12.sp, color = FrostDim)
                    Spacer(modifier = Modifier.height(14.dp))
                    TabPill("Home", "", tab == GpTab.HOME) { tab = GpTab.HOME }
                    TabPill("Sessions", "${repo.sessions.size}", tab == GpTab.SESSIONS) { tab = GpTab.SESSIONS }
                    TabPill("Clients", "${repo.clients.size}", tab == GpTab.CLIENTS) { tab = GpTab.CLIENTS }
                    TabPill("Finance", "${repo.remittances.size}", tab == GpTab.FINANCE) { tab = GpTab.FINANCE }
                    TabPill("Team", "${repo.users.size}", tab == GpTab.TEAM) { tab = GpTab.TEAM }
                    TabPill("Mailbox", if (unread == 0) "" else "$unread", tab == GpTab.MAIL) { tab = GpTab.MAIL }
                    TabPill("Audit", "${repo.audits.size}", tab == GpTab.AUDIT) { tab = GpTab.AUDIT }
                    TabPill("Profile", "", tab == GpTab.PROFILE) { tab = GpTab.PROFILE }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (repo.clockedIn.value) "● in shift" else "○ off shift",
                        fontSize = 12.sp,
                        color = if (repo.clockedIn.value) MintGlow else FrostDim,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    GpTab.HOME -> GpHome(repo)
                    GpTab.SESSIONS -> GpSessions(repo)
                    GpTab.CLIENTS -> GpClients(repo)
                    GpTab.FINANCE -> GpFinance(repo)
                    GpTab.TEAM -> GpTeam(repo)
                    GpTab.MAIL -> GpMail(repo)
                    GpTab.AUDIT -> GpAudit(repo)
                    GpTab.PROFILE -> GpProfile(repo, onLogout)
                }
            }
        }
    }
}

@Composable
private fun GpDayBanner(repo: GlassPanelsFakeRepo) {
    val status = repo.dayStatus.value
    val tint = when (status) {
        GpDayStatus.OPEN -> MintGlow
        GpDayStatus.PAST -> PeachGlow
        GpDayStatus.REMITTED -> AquaGlow
    }
    val copy = when (status) {
        GpDayStatus.OPEN -> "Branch day is OPEN — sessions flow, drafts breathe."
        GpDayStatus.PAST -> "Branch day is PAST — read the water, seal what remains."
        GpDayStatus.REMITTED -> "Branch day is REMITTED — snapshots frozen, splits settled."
    }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
    ) {
        GlassSheet(radius = 18.dp, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status.name, tint)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = copy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FrostText)
                    Text(
                        text = "Days flip at the 04:00 Asia/Manila boundary.",
                        fontSize = 12.sp,
                        color = FrostDim,
                    )
                }
                Text(text = repo.branchName(repo.branchId.value), fontSize = 12.sp, color = FrostDim)
            }
        }
    }
}
