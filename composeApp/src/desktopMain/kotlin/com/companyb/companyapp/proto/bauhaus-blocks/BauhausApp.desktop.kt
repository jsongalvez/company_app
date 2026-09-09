package com.companyb.companyapp.proto.bauhausblocks

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class BhPhase { LOGIN, ONBOARDING_LOCKED, BLOCK_SELECT, APP }

enum class BhTab(val label: String, val shape: BhShape, val color: Color) {
    HOME("HOME", BhShape.CIRCLE, BhRed),
    SESSIONS("SESSIONS", BhShape.SQUARE, BhBlue),
    CLIENTS("CLIENTS", BhShape.TRIANGLE, BhRed),
    FINANCE("FINANCE", BhShape.HALF, BhBlue),
    TEAM("TEAM", BhShape.SQUARE, BhRed),
    MAIL("MAIL", BhShape.CIRCLE, BhBlue),
    AUDIT("AUDIT", BhShape.BARS, BhInk),
    PROFILE("PROFILE", BhShape.ARCH, BhRed),
}

@Composable
fun BauhausProtoApp() {
    BauhausTheme {
        val repo = remember { BauhausFakeRepo() }
        var phase by remember { mutableStateOf(BhPhase.LOGIN) }
        var tab by remember { mutableStateOf(BhTab.HOME) }
        Box(modifier = Modifier.fillMaxSize().background(BhPaper)) {
            when (phase) {
                BhPhase.LOGIN -> BhLogin(
                    onLogin = { phase = BhPhase.BLOCK_SELECT },
                    onOnboarding = { phase = BhPhase.ONBOARDING_LOCKED },
                )
                BhPhase.ONBOARDING_LOCKED -> BhOnboardingLock(onBack = { phase = BhPhase.LOGIN })
                BhPhase.BLOCK_SELECT -> BhBlockPick(
                    repo = repo,
                    onEnter = { tab = BhTab.HOME; phase = BhPhase.APP },
                )
                BhPhase.APP -> BhShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = BhPhase.LOGIN },
                    onPickBlock = { phase = BhPhase.BLOCK_SELECT },
                )
            }
        }
    }
}

@Composable
fun BhCard(
    modifier: Modifier = Modifier,
    accent: Color = BhInk,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(3.dp, BhLine)
            .background(BhCard)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(accent))
            content()
        }
    }
}

@Composable
fun BhKicker(text: String, color: Color = BhRed) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 3.sp)
}

@Composable
fun BhPill(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .border(2.dp, BhLine)
            .background(if (selected) color else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                if (color == BhYellow) BhInk else Color.White
            } else {
                BhInk
            },
        )
    }
}

@Composable
private fun BhCompositionArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(color = BhYellow, topLeft = Offset(w * 0.52f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.48f, h))
        drawCircle(color = BhRed, radius = w * 0.30f, center = Offset(w * 0.32f, h * 0.34f))
        drawRect(color = BhBlue, topLeft = Offset(w * 0.08f, h * 0.60f),
            size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.32f))
        drawArc(
            color = BhInk,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.58f, h * 0.55f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.37f),
        )
        drawLine(color = BhInk, start = Offset(0f, h * 0.55f), end = Offset(w, h * 0.55f), strokeWidth = 8f)
    }
}

@Composable
private fun BhLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(BhInk).padding(40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BhKicker("BAUHAUS BLOCKS", BhYellow)
                Text("Primary forms,\nserious play.",
                    style = MaterialTheme.typography.displaySmall, color = BhPaper)
                BhCompositionArt(modifier = Modifier.fillMaxWidth().weight(1f))
                Text("Circle triangle square. Every block a branch day. Fake data only, no network calls.",
                    style = MaterialTheme.typography.bodyMedium, color = BhPaper)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            BhCard(modifier = Modifier.width(420.dp), accent = BhRed) {
                BhKicker("SIGN IN")
                Text("Enter the composition", style = MaterialTheme.typography.headlineSmall)
                Text("Pick a block, clock in, run the whole branch day from one canvas.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BhRed),
                ) { Text("ENTER THE COMPOSITION") }
                OutlinedButton(onClick = onOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text("Preview the ONBOARDING welcome")
                }
                Text("Fake roster data only.", style = MaterialTheme.typography.bodySmall, color = BhMuted)
            }
        }
    }
}

@Composable
private fun BhOnboardingLock(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BhInk), contentAlignment = Alignment.Center) {
        BhCard(modifier = Modifier.width(460.dp), accent = BhYellow) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BhShapeIcon(BhShape.TRIANGLE, BhYellow)
                BhKicker("ONBOARDING - LOCKED", BhBlue)
            }
            Text("Welcome, new block", style = MaterialTheme.typography.headlineSmall)
            Text("ONBOARDING accounts hold an empty capability bundle: no sessions, no remittance, " +
                "no relief duty until a MANAGER grants a Practitioner role from the TEAM board.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK TO SIGN IN") }
        }
    }
}

@Composable
private fun BhBlockPick(repo: BauhausFakeRepo, onEnter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BhKicker("BRANCH SELECT")
        Text("Choose your block", style = MaterialTheme.typography.displaySmall)
        Text("Four quarters, four colors. Each block opens the same full branch day.",
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            for (branch in repo.branches) {
                val selected = repo.branchFilter.value == branch.id
                Box(
                    modifier = Modifier.weight(1f)
                        .border(if (selected) 5.dp else 3.dp, BhLine)
                        .background(if (selected) BhYellow else BhCard)
                        .clickable { repo.selectBranch(branch.id) }
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BhShapeIcon(branch.shape, if (selected) BhInk else BhRed, 36.dp)
                        Text(branch.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                        Text(branch.district, style = MaterialTheme.typography.bodySmall, color = BhMuted)
                        Text("DAY ${branch.dayStatus} - ${branch.onShift} ON SHIFT",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("TARGET ${branch.target} - GROSS ${branch.gross}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onEnter,
                colors = ButtonDefaults.buttonColors(containerColor = BhBlue),
            ) { Text("OPEN ${repo.currentBranch().name.uppercase()}") }
        }
    }
}

@Composable
private fun BhShell(
    repo: BauhausFakeRepo,
    tab: BhTab,
    onTab: (BhTab) -> Unit,
    onLogout: () -> Unit,
    onPickBlock: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(190.dp).fillMaxHeight().background(BhInk).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BH/BLOCKS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BhYellow,
                letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            for (t in BhTab.entries) {
                val selected = t == tab
                val unread = t == BhTab.MAIL && repo.unreadCount() > 0
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (selected) BhPaper else Color.Transparent)
                        .clickable { onTab(t) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BhShapeIcon(t.shape, if (selected) t.color else BhPaper, 20.dp)
                    Text(
                        t.label + if (unread) " (${repo.unreadCount()})" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) BhInk else BhPaper,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("FAKE DATA ONLY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BhMuted)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BhDayBanner(repo = repo, onPickBlock = onPickBlock)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    BhTab.HOME -> BhHomeScreen(repo)
                    BhTab.SESSIONS -> BhSessionsScreen(repo)
                    BhTab.CLIENTS -> BhClientsScreen(repo)
                    BhTab.FINANCE -> BhFinanceScreen(repo)
                    BhTab.TEAM -> BhTeamScreen(repo)
                    BhTab.MAIL -> BhMailScreen(repo)
                    BhTab.AUDIT -> BhAuditScreen(repo)
                    BhTab.PROFILE -> BhProfileScreen(repo, onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
private fun BhDayBanner(repo: BauhausFakeRepo, onPickBlock: () -> Unit) {
    val branch = repo.currentBranch()
    val block = branch.dayStatus.blockColor()
    Row(
        modifier = Modifier.fillMaxWidth().background(BhInk).padding(start = 16.dp, end = 16.dp, top = 12.dp,
            bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.border(2.dp, BhPaper).background(block).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("DAY ${branch.dayStatus}", fontWeight = FontWeight.Black, fontSize = 14.sp,
                color = if (branch.dayStatus == BhDayStatus.REMITTED) BhInk else Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${branch.name} - ${branch.district}",
                color = BhPaper, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Branch day flips at the 04:00 Asia/Manila boundary. " +
                if (repo.meClockedIn.value) "Clocked in." else "Clocked out.",
                color = BhPaper, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onPickBlock) { Text("SWITCH BLOCK") }
    }
}
