package com.companyb.companyapp.proto.kanday

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class KbAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class KbTab { HOME, BOARD, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun KanbanDayProtoApp() {
    KanbanDayTheme {
        var phase by remember { mutableStateOf(KbAuthPhase.LOGIN) }
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(CorkBoard, CorkDeep))),
        ) {
            when (phase) {
                KbAuthPhase.LOGIN -> KbLogin(
                    onLogin = { phase = KbAuthPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = KbAuthPhase.ONBOARDING_LOCKED },
                )
                KbAuthPhase.ONBOARDING_LOCKED -> KbOnboardingLocked(
                    onBack = { phase = KbAuthPhase.LOGIN },
                )
                KbAuthPhase.BRANCH_SELECT -> KbBranchSelect(
                    onPick = { branchId ->
                        KanbanDayFakeRepo.clockedBranchId.value = branchId
                        phase = KbAuthPhase.APP
                    },
                    onBack = { phase = KbAuthPhase.LOGIN },
                )
                KbAuthPhase.APP -> KbShell(onLogout = { phase = KbAuthPhase.LOGIN })
            }
        }
    }
}

@Composable
internal fun KbShell(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(KbTab.HOME) }
    Column(modifier = Modifier.fillMaxSize()) {
        KbTopRail(
            current = tab,
            onPick = { tab = it },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                KbTab.HOME -> KbHomeTab(onOpenBoard = { tab = KbTab.BOARD })
                KbTab.BOARD -> KbBoardTab()
                KbTab.CLIENTS -> KbClientsTab()
                KbTab.FINANCE -> KbFinanceTab()
                KbTab.TEAM -> KbTeamTab()
                KbTab.MAIL -> KbMailTab()
                KbTab.AUDIT -> KbAuditTab()
                KbTab.PROFILE -> KbProfileTab(onLogout = onLogout)
            }
        }
    }
}

@Composable
internal fun KbTopRail(current: KbTab, onPick: (KbTab) -> Unit) {
    val unread = KanbanDayFakeRepo.mailbox.count { !it.read }
    Column(modifier = Modifier.fillMaxWidth().background(RailDark).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clip(MaterialTheme.shapes.small).background(NotePending)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("DAY BOARD", color = InkBoard, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Branch day kanban - ${KanbanDayFakeRepo.branchName(KanbanDayFakeRepo.clockedBranchId.value)}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KbTab.entries.forEach { tab ->
                val label = if (tab == KbTab.MAIL && unread > 0) "MAIL ($unread)" else tab.name
                KbRailPill(label = label, selected = tab == current, onClick = { onPick(tab) })
            }
        }
    }
}

@Composable
internal fun KbRailPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small)
            .background(if (selected) NotePending else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) InkBoard else Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun KbDayBanner() {
    val status = KanbanDayFakeRepo.dayStatus.value
    val (copy, tint) = when (status) {
        KbDayStatus.OPEN -> "Branch Day OPEN - pull notes across the lanes" to NoteCompleted
        KbDayStatus.PAST -> "Branch Day PAST - lanes frozen for review" to NoteNoShow
        KbDayStatus.REMITTED -> "Branch Day REMITTED - snapshot sealed" to TapeStrip
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(BoardCream)
            .border(2.dp, InkBoard, MaterialTheme.shapes.medium).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(tint).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(status.name, color = InkBoard, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(copy, style = MaterialTheme.typography.bodyMedium, color = InkBoard)
            Text(
                "Day boundary 04:00 Asia/Manila - visits before 04:00 belong to yesterday.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
internal fun KbLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.clip(MaterialTheme.shapes.large).background(BoardCream)
                .border(2.dp, InkBoard, MaterialTheme.shapes.large).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TapeTag("PIN THIS DAY")
            Spacer(Modifier.height(10.dp))
            Text("Day Board", style = MaterialTheme.typography.displaySmall, color = InkBoard)
            Text(
                "Branch day kanban - sessions ride the lanes, not a list.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Work email (any address pulls a lane)") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = RailDark),
            ) {
                Text("Pull my board")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOnboardingDemo) {
                Text("Preview the ONBOARDING welcome")
            }
        }
    }
}

@Composable
internal fun KbOnboardingLocked(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.clip(MaterialTheme.shapes.large).background(NotePending)
                .border(2.dp, InkBoard, MaterialTheme.shapes.large).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TapeTag("LOCKED LANE")
            Spacer(Modifier.height(10.dp))
            Text("ONBOARDING holds no Capability yet", style = MaterialTheme.typography.titleLarge, color = InkBoard)
            Text(
                "Empty Capability bundle - a Coordinator or MANAGER pins Practitioner on you first.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkBoard,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = RailDark)) {
                Text("Back to login")
            }
        }
    }
}

@Composable
internal fun KbBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.clip(MaterialTheme.shapes.large).background(BoardCream)
                .border(2.dp, InkBoard, MaterialTheme.shapes.large).padding(24.dp)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TapeTag("PICK A WALL")
            Spacer(Modifier.height(8.dp))
            Text("Which Branch wall opens today?", style = MaterialTheme.typography.titleLarge, color = InkBoard)
            Spacer(Modifier.height(12.dp))
            KanbanDayFakeRepo.branches.forEach { branch ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(LanePaper)
                        .border(1.dp, InkBoard, MaterialTheme.shapes.medium).clickable { onPick(branch.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(branch.name, style = MaterialTheme.typography.titleMedium, color = InkBoard)
                        Text(branch.kind, style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    }
                    Text("OPEN >", color = InkBoard, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
internal fun TapeTag(text: String) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small)
            .background(TapeStrip.copy(alpha = 0.85f)).border(1.dp, InkSoft, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = InkBoard)
    }
}

@Composable
internal fun KbLaneCard(
    modifier: Modifier = Modifier,
    title: String,
    meta: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).background(BoardCream)
            .border(1.dp, InkBoard, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = InkBoard)
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
internal fun KbChip(text: String, tint: Color) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(tint.copy(alpha = 0.28f))
            .border(1.dp, tint, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkBoard)
    }
}
