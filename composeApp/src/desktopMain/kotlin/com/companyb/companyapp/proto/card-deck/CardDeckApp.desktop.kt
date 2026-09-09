package com.companyb.companyapp.proto.carddeck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DeckAuthPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class DeckTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun CardDeckProtoApp() {
    CardDeckTheme {
        var phase by remember { mutableStateOf(DeckAuthPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                DeckAuthPhase.LOGIN -> DeckLogin(
                    onLogin = { phase = DeckAuthPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = DeckAuthPhase.ONBOARDING_LOCKED },
                )
                DeckAuthPhase.ONBOARDING_LOCKED -> DeckOnboardingLocked(
                    onBack = { phase = DeckAuthPhase.LOGIN },
                )
                DeckAuthPhase.BRANCH_SELECT -> DeckBranchSelect(
                    onPick = { branchId ->
                        CardDeckFakeRepo.clockedBranchId.value = branchId
                        phase = DeckAuthPhase.APP
                    },
                    onBack = { phase = DeckAuthPhase.LOGIN },
                )
                DeckAuthPhase.APP -> DeckShell(onLogout = { phase = DeckAuthPhase.LOGIN })
            }
        }
    }
}

@Composable
internal fun PlayingCard(
    modifier: Modifier = Modifier,
    accent: Color,
    rank: String,
    title: String,
    meta: String = "",
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.border(2.dp, InkLine, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PaperCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = rank, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = InkText)
                    if (meta.isNotEmpty()) {
                        Text(text = meta, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun DeckChip(text: String, accent: Color = SuitSlate) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
internal fun DeckHeader(title: String, subtitle: String, count: String = "") {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.displaySmall, color = FeltOnDark)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = FeltMuted)
            }
        }
        if (count.isNotEmpty()) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TableRail)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = count, fontWeight = FontWeight.Black, fontSize = 13.sp, color = InkText)
            }
        }
    }
}

@Composable
internal fun <T> DeckRow(
    cards: List<T>,
    emptyTitle: String,
    emptyBody: String,
    keyOf: (T) -> String,
    cardWidth: Int = 320,
    render: @Composable (T) -> Unit,
) {
    if (cards.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().border(2.dp, FeltMuted, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = FeltDeep),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = emptyTitle, style = MaterialTheme.typography.titleMedium, color = FeltOnDark)
                Spacer(Modifier.height(4.dp))
                Text(text = emptyBody, style = MaterialTheme.typography.bodyMedium, color = FeltMuted)
            }
        }
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(cards, key = { keyOf(it) }) { item ->
            Box(modifier = Modifier.width(cardWidth.dp)) { render(item) }
        }
    }
}

@Composable
internal fun DeckNoteCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().border(2.dp, TableRail, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3D6)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = InkText,
        )
    }
}

@Composable
private fun DeckLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.width(460.dp)) {
            PlayingCard(accent = SuitCoral, rank = "A", title = "Card-deck table", meta = "Every entity a card - pick up a hand") {
                Text(
                    text = "Fake-data prototype. Any email deals you in; nothing leaves this table.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuitCoral),
                ) {
                    Text("Deal me in")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOnboardingDemo, modifier = Modifier.fillMaxWidth()) {
                    Text("Preview the ONBOARDING welcome", color = SuitSky)
                }
            }
        }
    }
}

@Composable
private fun DeckOnboardingLocked(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.width(460.dp)) {
            PlayingCard(accent = SuitSlate, rank = "?", title = "ONBOARDING is locked out", meta = "Fresh account - zero capabilities") {
                Text(
                    text = "A freshly registered user holds an empty role bundle: nothing derives, " +
                        "even with a branch assignment. A MANAGER must grant a real role first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkText,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to login") }
            }
        }
    }
}

@Composable
private fun DeckBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckHeader("Choose your table", "One branch per hand - deals the whole day", "${CardDeckFakeRepo.branches.size} tables")
        DeckRow(
            cards = CardDeckFakeRepo.branches.toList(),
            emptyTitle = "No tables",
            emptyBody = "Fresh deck has no branches.",
            keyOf = { it.id },
        ) { branch ->
            PlayingCard(accent = SuitSky, rank = branch.name.take(1), title = branch.name, meta = branch.kind) {
                Text(text = "Own inventory, sessions and drawer.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onPick(branch.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SuitSky),
                ) {
                    Text("Sit at this table")
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back", color = FeltOnDark) }
    }
}

@Composable
private fun DeckShell(onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(DeckTab.HOME) }
    val unread = CardDeckFakeRepo.mailbox.count { !it.read }
    Column(modifier = Modifier.fillMaxSize()) {
        DeckDayBanner()
        Row(modifier = Modifier.fillMaxWidth().background(FeltDeep).padding(horizontal = 16.dp, vertical = 8.dp)) {
            DeckTab.entries.forEach { entry ->
                val label = if (entry == DeckTab.MAIL && unread > 0) "MAIL ($unread)" else entry.name
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (tab == entry) TableRail else Color.Transparent)
                        .clickable { tab = entry }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (tab == entry) InkText else FeltMuted,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                DeckTab.HOME -> DeckHomeScreen()
                DeckTab.SESSIONS -> DeckSessionsScreen()
                DeckTab.CLIENTS -> DeckClientsScreen()
                DeckTab.FINANCE -> DeckFinanceScreen()
                DeckTab.TEAM -> DeckTeamScreen()
                DeckTab.MAIL -> DeckMailScreen()
                DeckTab.AUDIT -> DeckAuditScreen()
                DeckTab.PROFILE -> DeckProfileScreen(onLogout = onLogout, onReset = { tab = DeckTab.HOME })
            }
        }
    }
}

@Composable
private fun DeckDayBanner() {
    val status = CardDeckFakeRepo.dayStatus.value
    val branch = CardDeckFakeRepo.branchName(CardDeckFakeRepo.clockedBranchId.value)
    val (label, bg) = when (status) {
        DeckDayStatus.OPEN -> "OPEN - editable by all on-duty users" to SuitMint
        DeckDayStatus.PAST -> "PAST - Coordinator-only edits" to SuitAmber
        DeckDayStatus.REMITTED -> "REMITTED - covered by a submitted remittance" to SuitLilac
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Branch day:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text = "$branch - $label", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        Text(text = "04:00 Asia/Manila boundary", fontSize = 12.sp, color = Color.White)
    }
}
