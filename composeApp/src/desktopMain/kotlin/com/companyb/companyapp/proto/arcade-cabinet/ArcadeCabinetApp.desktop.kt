package com.companyb.companyapp.proto.arcadecabinet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class CabPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class CabTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun ArcadeCabinetProtoApp() {
    ArcadeCabinetTheme {
        var phase by remember { mutableStateOf(CabPhase.LOGIN) }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF05050C), CabBlack, Color(0xFF0D0D1A))),
            ),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.width(26.dp).fillMaxHeight().background(
                        Brush.horizontalGradient(listOf(Color(0xFF3A3F4D), Color(0xFF12121E))),
                    ),
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (phase) {
                        CabPhase.LOGIN -> CabInsertCoin(
                            onLogin = { phase = CabPhase.BRANCH_SELECT },
                            onOnboardingDemo = { phase = CabPhase.ONBOARDING_LOCKED },
                        )
                        CabPhase.ONBOARDING_LOCKED -> CabOnboardingLocked(
                            onBack = { phase = CabPhase.LOGIN },
                        )
                        CabPhase.BRANCH_SELECT -> CabCabinetSelect(
                            onPick = { phase = CabPhase.APP },
                            onBack = { phase = CabPhase.LOGIN },
                        )
                        CabPhase.APP -> CabShell(
                            onLogout = { phase = CabPhase.LOGIN },
                        )
                    }
                }
                Box(
                    modifier = Modifier.width(26.dp).fillMaxHeight().background(
                        Brush.horizontalGradient(listOf(Color(0xFF12121E), Color(0xFF3A3F4D))),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun CabMarquee(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF2A0A24), Color(0xFF101A3A), Color(0xFF2A0A24))),
                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            )
            .border(2.dp, CabNeonPink, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(vertical = 14.dp, horizontal = 20.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = CabNeonYellow,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = CabNeonCyan,
            )
        }
    }
}

@Composable
internal fun CabPanelCard(
    modifier: Modifier = Modifier,
    accent: Color = CabNeonCyan,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CabPanel),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
internal fun CabChip(text: String, color: Color = CabNeonCyan) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
internal fun CabSectionTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = ">> $title", style = MaterialTheme.typography.titleLarge, color = CabNeonYellow)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = CabMuted)
        }
    }
}

@Composable
internal fun CabEmpty(title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, CabChrome.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "[ NO DATA ]", style = MaterialTheme.typography.headlineSmall, color = CabNeonPurple)
            Spacer(Modifier.height(6.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = CabInk)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = CabMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = CabNeonPink, contentColor = Color.Black),
                ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun CabField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CabNeonCyan,
            unfocusedBorderColor = Color(0xFF2E2E44),
            focusedLabelColor = CabNeonCyan,
            unfocusedLabelColor = CabMuted,
            cursorColor = CabNeonCyan,
        ),
    )
}

@Composable
private fun CabInsertCoin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var email by remember { mutableStateOf("maya@sunrise.ph") }
    var pin by remember { mutableStateOf("1234") }
    val repo = ArcadeCabinetFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CabMarquee("COMPANYAPP ARCADE", "INSERT COIN TO CLOCK IN - 1 CREDIT = 1 SHIFT")
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CabPanelCard(modifier = Modifier.fillMaxWidth(0.7f), accent = CabNeonPink) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CREDITS: ${repo.coins.value}", style = MaterialTheme.typography.headlineSmall, color = CabNeonYellow)
                    Spacer(Modifier.height(4.dp))
                    Text("PRESS START - STAFF LOGIN", style = MaterialTheme.typography.labelLarge, color = CabNeonPink)
                }
            }
            Spacer(Modifier.height(20.dp))
            CabPanelCard(modifier = Modifier.fillMaxWidth(0.7f), accent = CabNeonCyan) {
                Column {
                    CabField(email, { email = it }, "Email")
                    Spacer(Modifier.height(10.dp))
                    CabField(pin, { pin = it }, "PIN")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                repo.coins.value += 1
                                onLogin()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CabNeonGreen, contentColor = Color.Black),
                        ) { Text("INSERT COIN + START", fontWeight = FontWeight.Black) }
                        OutlinedButton(onClick = onOnboardingDemo, modifier = Modifier.weight(1f)) {
                            Text("ONBOARDING demo", color = CabNeonCyan)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Any email + PIN works. Fake cabinet - no network calls, credits reset on demo reset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CabMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CabOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CabMarquee("PLAYER 2 LOCKED", "ONBOARDING HAS ZERO CREDITS")
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CabPanelCard(modifier = Modifier.fillMaxWidth(0.7f), accent = CabDanger) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GAME OVER?", style = MaterialTheme.typography.headlineMedium, color = CabDanger)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Lena Cruz holds the ONBOARDING role: freshly registered, zero capabilities. " +
                            "The role bundle is empty, so nothing derives - locked out even with a branch " +
                            "assignment, until MANAGE_USERS grants a real role.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CabInk,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CabChip("0 CAPABILITIES", CabDanger)
                        CabChip("EMPTY BUNDLE", CabNeonYellow)
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onBack) { Text("< BACK TO INSERT COIN", color = CabNeonCyan) }
                }
            }
        }
    }
}

@Composable
private fun CabCabinetSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    val repo = ArcadeCabinetFakeRepo
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CabMarquee("SELECT YOUR CABINET", "3 BRANCHES - PICK A HIGH-SCORE TABLE")
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.branches.forEach { branch ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .border(2.dp, CabNeonPurple, RoundedCornerShape(8.dp))
                        .clickable {
                            repo.clockedBranchId.value = branch.id
                            onPick(branch.id)
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = CabPanelHi),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(branch.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = CabInk)
                            Spacer(Modifier.height(2.dp))
                            Text(branch.kind, style = MaterialTheme.typography.labelMedium, color = CabNeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("HI-SCORE", style = MaterialTheme.typography.labelSmall, color = CabMuted)
                            Text("${branch.highScore}", style = MaterialTheme.typography.headlineSmall, color = CabNeonYellow)
                        }
                    }
                }
            }
            TextButton(onClick = onBack) { Text("< BACK TO INSERT COIN", color = CabNeonCyan) }
        }
    }
}

@Composable
private fun CabShell(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(CabTab.HOME) }
    val repo = ArcadeCabinetFakeRepo
    val branchId = repo.clockedBranchId.value
    val branchLabel = repo.branchName(branchId)
    Column(modifier = Modifier.fillMaxSize()) {
        CabMarquee("HI-SCORE DASHBOARD", "$branchLabel - ${repo.currentUserName.value} - CREDITS ${repo.coins.value}")
        CabDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.width(190.dp).fillMaxHeight().background(Color.Black)
                    .border(1.dp, Color(0xFF2E2E44)).padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CabTab.values().forEach { t ->
                    val selected = t == tab
                    val unread = t == CabTab.MAIL && repo.notes.any { !it.read }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) CabNeonPink else Color(0xFF14141F))
                            .border(1.dp, if (selected) CabNeonPink else Color(0xFF2E2E44), RoundedCornerShape(6.dp))
                            .clickable { tab = t }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                    ) {
                        Text(
                            (if (selected) "> " else "") + t.name + (if (unread) " (!)" else ""),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selected) Color.Black else CabInk,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onLogout) { Text("EJECT (LOGOUT)", color = CabDanger) }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                when (tab) {
                    CabTab.HOME -> CabHomeScreen()
                    CabTab.SESSIONS -> CabSessionsScreen()
                    CabTab.CLIENTS -> CabClientsScreen()
                    CabTab.FINANCE -> CabFinanceScreen()
                    CabTab.TEAM -> CabTeamScreen()
                    CabTab.MAIL -> CabMailScreen()
                    CabTab.AUDIT -> CabAuditScreen()
                    CabTab.PROFILE -> CabProfileScreen(onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
internal fun CabDayBanner() {
    val repo = ArcadeCabinetFakeRepo
    val status = repo.dayStatus.value
    val (label, color) = when (status) {
        CabDayStatus.OPEN -> "DAY: OPEN - editable by all on-duty players" to CabNeonGreen
        CabDayStatus.PAST -> "DAY: PAST - Coordinator-only edits" to CabNeonYellow
        CabDayStatus.REMITTED -> "DAY: REMITTED - sealed by remittance, flagged audits" to CabNeonPink
    }
    Box(
        modifier = Modifier.fillMaxWidth().background(Color.Black)
            .border(1.dp, color).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            Text("04:00 Asia/Manila boundary", fontSize = 12.sp, color = CabMuted)
        }
    }
}
