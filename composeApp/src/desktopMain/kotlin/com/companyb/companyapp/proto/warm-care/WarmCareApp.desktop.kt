package com.companyb.companyapp.proto.warmcare

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class WarmAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class WarmTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun WarmCareProtoApp() {
    WarmCareTheme {
        var phase by remember { mutableStateOf(WarmAuthPhase.LOGIN) }
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            when (phase) {
                WarmAuthPhase.LOGIN -> WarmLogin(
                    onLogin = { phase = WarmAuthPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = WarmAuthPhase.ONBOARDING_LOCKED },
                )
                WarmAuthPhase.ONBOARDING_LOCKED -> WarmOnboardingLocked(
                    onBack = { phase = WarmAuthPhase.LOGIN },
                )
                WarmAuthPhase.BRANCH_SELECT -> WarmBranchSelect(
                    onPick = { phase = WarmAuthPhase.APP },
                    onBack = { phase = WarmAuthPhase.LOGIN },
                )
                WarmAuthPhase.APP -> WarmShell(
                    onLogout = { phase = WarmAuthPhase.LOGIN },
                )
            }
        }
    }
}

@Composable
internal fun WarmCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun WarmChip(text: String, soft: Boolean = false) {
    val bg = if (soft) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val fg = if (soft) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
internal fun WarmSectionTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WarmEmpty(title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "( ^_^ )", fontSize = 34.sp)
            Spacer(Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun WarmLogin(
    onLogin: () -> Unit,
    onOnboardingDemo: () -> Unit,
) {
    var email by remember { mutableStateOf("maya@sunrise.example") }
    var password by remember { mutableStateOf("kindness-first") }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().background(WarmTerracotta).padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Warm Care", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = WarmCream)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "A gentle home for busy healing hands. Every neighbor welcome, every visit remembered.",
                fontSize = 17.sp,
                color = WarmCream,
            )
            Spacer(Modifier.height(24.dp))
            WarmChip("Mission-first prototype - fake data only")
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(48.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            WarmSectionTitle("Welcome back, caregiver", "Clock in with a smile - your branch missed you.")
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Log in warmly", modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOnboardingDemo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Preview the ONBOARDING welcome")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Any email works here - this prototype never calls the network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarmOnboardingLocked(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(56.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "You're on the welcome mat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "ONBOARDING neighbors can look around but cannot touch anything yet. " +
                "Your coordinator grants a real role, then every door opens.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        WarmCard {
            Column {
                Text(text = "Why am I locked?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A fresh account holds an empty capability bundle - nothing derives until " +
                        "MANAGE_USERS grants Practitioner, Coordinator, MANAGER, or Accountant.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack, shape = RoundedCornerShape(16.dp)) { Text("Back to login") }
    }
}

@Composable
private fun WarmBranchSelect(
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState())) {
        WarmSectionTitle("Choose your branch for today", "One warm home base - you can still visit others as relief.")
        Spacer(Modifier.height(20.dp))
        WarmCareFakeRepo.branches.forEach { branch ->
            WarmCard(modifier = Modifier.fillMaxWidth().clickable { onPick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = branch.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = branch.kind.replace('_', ' ') +
                                " - opens 08:00, closes at the 04:00 Manila boundary",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    WarmChip(branch.kind)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back to login") }
    }
}

@Composable
private fun WarmShell(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(WarmTab.HOME) }
    Row(modifier = Modifier.fillMaxSize()) {
        WarmRail(
            current = tab,
            unread = WarmCareFakeRepo.notes.count { !it.read },
            onPick = { tab = it },
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            WarmDayBanner()
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                when (tab) {
                    WarmTab.HOME -> WarmHome()
                    WarmTab.SESSIONS -> WarmSessions()
                    WarmTab.CLIENTS -> WarmClients()
                    WarmTab.FINANCE -> WarmFinance()
                    WarmTab.TEAM -> WarmTeam()
                    WarmTab.MAIL -> WarmMailbox()
                    WarmTab.AUDIT -> WarmAuditList()
                    WarmTab.PROFILE -> WarmProfile(onLogout = onLogout, onClockOut = { tab = WarmTab.HOME })
                }
            }
        }
    }
}

@Composable
private fun WarmRail(
    current: WarmTab,
    unread: Int,
    onPick: (WarmTab) -> Unit,
) {
    val items = listOf(
        WarmTab.HOME to "Home",
        WarmTab.SESSIONS to "Sessions",
        WarmTab.CLIENTS to "Clients",
        WarmTab.FINANCE to "Finance",
        WarmTab.TEAM to "Team",
        WarmTab.MAIL to if (unread > 0) "Mailbox ($unread)" else "Mailbox",
        WarmTab.AUDIT to "Audit",
        WarmTab.PROFILE to "Profile",
    )
    Column(
        modifier = Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(16.dp),
    ) {
        Text(text = "Warm Care", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = "Fake-data prototype", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        items.forEach { (tab, label) ->
            val selected = tab == current
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .clickable { onPick(tab) }.padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text(
                    text = label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WarmDayBanner() {
    val status = WarmCareFakeRepo.dayStatus.value
    val branch = WarmCareFakeRepo.branchName(WarmCareFakeRepo.clockedBranchId.value)
    val hint = when (status) {
        WarmDayStatus.OPEN -> "OPEN - today is editable for everyone on duty."
        WarmDayStatus.PAST -> "PAST - yesterday kept editable for coordinators until remitted."
        WarmDayStatus.REMITTED -> "REMITTED - sealed by snapshot; coordinator edits are flagged."
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WarmChip(branch, soft = true)
            Spacer(Modifier.width(10.dp))
            WarmChip(status.name)
            Spacer(Modifier.width(10.dp))
            Text(text = hint, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Branch days roll at 04:00 Asia/Manila - Jun 11 officially becomes Jun 12 after 4am.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
