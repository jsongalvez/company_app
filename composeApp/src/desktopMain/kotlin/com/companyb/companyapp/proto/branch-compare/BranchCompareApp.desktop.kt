package com.companyb.companyapp.proto.branchcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private enum class CmpAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class CmpTab { COMPARE, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun BranchCompareProtoApp() {
    BranchCompareTheme {
        var phase by remember { mutableStateOf(CmpAuthPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                CmpAuthPhase.LOGIN -> CmpLogin(
                    onLogin = { phase = CmpAuthPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = CmpAuthPhase.ONBOARDING_LOCKED },
                )
                CmpAuthPhase.ONBOARDING_LOCKED -> CmpOnboardingLocked(
                    onBack = { phase = CmpAuthPhase.LOGIN },
                )
                CmpAuthPhase.BRANCH_SELECT -> CmpBranchSelect(
                    onPick = { branchId ->
                        BranchCompareFakeRepo.clockedBranchId.value = branchId
                        BranchCompareFakeRepo.focusedBranchId.value = branchId
                        phase = CmpAuthPhase.APP
                    },
                    onBack = { phase = CmpAuthPhase.LOGIN },
                )
                CmpAuthPhase.APP -> CmpShell(onLogout = { phase = CmpAuthPhase.LOGIN })
            }
        }
    }
}

@Composable
internal fun CompareChip(text: String, accent: Color = CompareSoft) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
internal fun DayRibbon(status: CmpDayStatus) {
    val (label, accent) = when (status) {
        CmpDayStatus.OPEN -> "OPEN" to DayOpen
        CmpDayStatus.PAST -> "PAST" to DayPast
        CmpDayStatus.REMITTED -> "REMITTED" to DayRemitted
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String, count: String = "") {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = CompareOnInk)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CompareMuted)
            }
        }
        if (count.isNotEmpty()) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ComparePaper)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = count, fontWeight = FontWeight.Black, fontSize = 13.sp, color = CompareBody)
            }
        }
    }
}

@Composable
internal fun ComparePanel(
    modifier: Modifier = Modifier,
    accent: Color = ComparePaperEdge,
    title: String,
    meta: String = "",
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.border(1.dp, accent, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ComparePaper),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(accent),
            ) {}
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = CompareBody)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = meta, style = MaterialTheme.typography.bodySmall, color = CompareSoft)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun CmpPrimaryButton(label: String, onClick: () -> Unit, accent: Color = SunriseAmber) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CmpGhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(text = label)
    }
}

@Composable
internal fun CmpBranchDayBanner(branchId: String) {
    val branch = BranchCompareFakeRepo.branch(branchId) ?: return
    val bg = when (branch.dayStatus) {
        CmpDayStatus.OPEN -> Color(0xFFDFF2E6)
        CmpDayStatus.PAST -> Color(0xFFE3E6EC)
        CmpDayStatus.REMITTED -> Color(0xFFFFEDCB)
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayRibbon(branch.dayStatus)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${branch.name} branch day",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CompareBody,
                )
                Text(
                    text = "Branch days roll over at 04:00 Asia/Manila. OPEN takes bookings, PAST is read-only, REMITTED is sealed.",
                    fontSize = 12.sp,
                    color = CompareSoft,
                )
            }
        }
    }
}

@Composable
internal fun CmpShell(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(CmpTab.COMPARE) }
    val unread = BranchCompareFakeRepo.mailbox.count { !it.read }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(CompareInkDeep).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SunriseAmber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text = "COMPARE", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(text = "Owner multi-branch desk", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CompareOnInk)
            Spacer(Modifier.weight(1f))
            val me = BranchCompareFakeRepo.currentUserName.value
            val clocked = BranchCompareFakeRepo.clockedIn.value
            Text(
                text = "$me - ${if (clocked) "clocked in" else "off shift"}",
                fontSize = 12.sp,
                color = CompareMuted,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(CompareInkDeep).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (entry in CmpTab.entries) {
                val label = if (entry == CmpTab.MAIL && unread > 0) "MAIL ($unread)" else entry.name
                val selected = tab == entry
                TextButton(onClick = { tab = entry }) {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                        fontSize = 12.sp,
                        color = if (selected) SunriseAmber else CompareMuted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLogout) {
                Text(text = "LOG OUT", fontSize = 12.sp, color = CompareMuted)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(CompareInk)) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    CmpTab.COMPARE -> CmpCompareHome()
                    CmpTab.SESSIONS -> CmpSessions(onAudit = { a, r, reason -> BranchCompareFakeRepo.log(a, r, reason) })
                    CmpTab.CLIENTS -> CmpClients()
                    CmpTab.FINANCE -> CmpFinance()
                    CmpTab.TEAM -> CmpTeam()
                    CmpTab.MAIL -> CmpMailbox()
                    CmpTab.AUDIT -> CmpAuditLog()
                    CmpTab.PROFILE -> CmpProfile(onLogout = onLogout)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
