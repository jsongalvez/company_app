package com.companyb.companyapp.proto.blockbuilder

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class BbPhase { LOGIN, ONBOARDING_LOCKED, PLATE_SELECT, APP }

enum class BbTab(val label: String, val color: Color, val deep: Color) {
    HOME("HOME", BbBrickRed, BbBrickRedDeep),
    SESSIONS("SESSIONS", BbBrickBlue, BbBrickBlueDeep),
    CLIENTS("CLIENTS", BbBrickYellow, BbBrickYellowDeep),
    FINANCE("FINANCE", BbBaseGreen, Color(0xFF237A36)),
    TEAM("TEAM", BbBrickRed, BbBrickRedDeep),
    MAIL("MAIL", BbBrickBlue, BbBrickBlueDeep),
    AUDIT("AUDIT", BbInkSoft, BbInk),
    PROFILE("PROFILE", BbBrickYellow, BbBrickYellowDeep),
}

@Composable
fun BlockBuilderProtoApp() {
    BlockBuilderTheme {
        val repo = remember { BlockBuilderFakeRepo() }
        var phase by remember { mutableStateOf(BbPhase.LOGIN) }
        var tab by remember { mutableStateOf(BbTab.HOME) }
        Box(modifier = Modifier.fillMaxSize().background(BbCream)) {
            when (phase) {
                BbPhase.LOGIN -> BbLogin(
                    onLogin = { phase = BbPhase.PLATE_SELECT },
                    onOnboarding = { phase = BbPhase.ONBOARDING_LOCKED },
                )
                BbPhase.ONBOARDING_LOCKED -> BbOnboardingLock(onBack = { phase = BbPhase.LOGIN })
                BbPhase.PLATE_SELECT -> BbPlatePick(
                    repo = repo,
                    onEnter = { tab = BbTab.HOME; phase = BbPhase.APP },
                )
                BbPhase.APP -> BbShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = BbPhase.LOGIN },
                    onPickPlate = { phase = BbPhase.PLATE_SELECT },
                )
            }
        }
    }
}

@Composable
fun BbBrickCard(
    modifier: Modifier = Modifier,
    brick: Color = BbBrickRed,
    studs: Int = 4,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(brick)
                .padding(top = 6.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            BbStuds(color = Color.White.copy(alpha = 0.85f), count = studs, modifier = Modifier.size(120.dp, 16.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(BbCard)
                .border(3.dp, BbInk, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
fun BbKicker(text: String, color: Color = BbBrickRed) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = color, letterSpacing = 2.sp)
}

@Composable
fun BbPill(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else Color.Transparent)
            .border(2.dp, BbInk, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = if (selected) {
                if (color == BbBrickYellow) BbInk else Color.White
            } else {
                BbInk
            },
        )
    }
}

@Composable
private fun BbToyArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = BbBaseGreen,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.72f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
        )
        fun brick(left: Float, top: Float, bw: Float, bh: Float, color: Color) {
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            )
            val d = bw / 7f
            var x = left + bw * 0.18f
            repeat(3) {
                drawCircle(color = color, radius = d / 2f, center = androidx.compose.ui.geometry.Offset(x, top - 4f))
                x += bw * 0.30f
            }
        }
        brick(w * 0.08f, h * 0.52f, w * 0.38f, h * 0.20f, BbBrickRed)
        brick(w * 0.50f, h * 0.52f, w * 0.38f, h * 0.20f, BbBrickBlue)
        brick(w * 0.18f, h * 0.30f, w * 0.34f, h * 0.20f, BbBrickYellow)
        brick(w * 0.56f, h * 0.30f, w * 0.26f, h * 0.20f, BbBrickRed)
        brick(w * 0.32f, h * 0.08f, w * 0.30f, h * 0.20f, BbBrickBlue)
    }
}

@Composable
private fun BbLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(BbInk).padding(40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BbKicker("TOY BLOCK BUILDER", BbBrickYellow)
                Text("Snap the day\ntogether brick by brick.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                BbToyArt(modifier = Modifier.fillMaxWidth().weight(1f))
                Text("Chunky bricks, click-feel stacks. Fake toy-box data only, no network calls.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            BbBrickCard(modifier = Modifier.width(430.dp), brick = BbBrickRed, studs = 4) {
                BbKicker("SNAP IN")
                Text("Click into the toy box", style = MaterialTheme.typography.headlineSmall)
                Text("Pick a build plate, clock in, and stack sessions, clients, and cash into one day layout.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BbBrickRed),
                ) { Text("SNAP INTO THE TOY BOX") }
                OutlinedButton(onClick = onOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text("Peek at the ONBOARDING brick")
                }
                Text("Fake roster bricks only.", style = MaterialTheme.typography.bodySmall, color = BbMuted)
            }
        }
    }
}

@Composable
private fun BbOnboardingLock(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BbCreamDeep), contentAlignment = Alignment.Center) {
        BbBrickCard(modifier = Modifier.width(470.dp), brick = BbBrickYellow, studs = 3) {
            BbKicker("ONBOARDING - LOOSE BRICK", BbBrickBlue)
            Text("Hello, new brick", style = MaterialTheme.typography.headlineSmall)
            Text("ONBOARDING bricks sit loose in the lid: empty capability bundle with no sessions, " +
                "no remittance, and no relief duty until a MANAGER snaps on a Practitioner role from the TEAM tray.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK TO SNAP IN") }
        }
    }
}

@Composable
private fun BbPlatePick(repo: BlockBuilderFakeRepo, onEnter: () -> Unit) {
    val plateBricks = listOf(BbBrickRed to BbBrickRedDeep, BbBrickBlue to BbBrickBlueDeep,
        BbBrickYellow to BbBrickYellowDeep, BbBaseGreen to Color(0xFF237A36))
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BbKicker("BRANCH SELECT")
        Text("Pick your build plate", style = MaterialTheme.typography.displaySmall)
        Text("Four plates, one toy box. Every plate snaps into the same full branch-day layout.",
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            repo.branches.forEachIndexed { index, branch ->
                val selected = repo.branchFilter.value == branch.id
                val (brick, _) = plateBricks[index % plateBricks.size]
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(if (selected) brick else brick.copy(alpha = 0.55f))
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BbStuds(color = Color.White.copy(alpha = 0.9f), count = 3,
                            modifier = Modifier.size(90.dp, 14.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(if (selected) BbCard else BbCard.copy(alpha = 0.85f))
                            .border(if (selected) 4.dp else 3.dp, BbInk,
                                RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .clickable { repo.selectBranch(branch.id) }
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BbBrickSwatch(color = brick, deep = BbInk)
                            Text(branch.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                            Text(branch.district, style = MaterialTheme.typography.bodySmall, color = BbMuted)
                            Text("PLATE ${branch.dayStatus} - ${branch.onShift} STACKED IN",
                                fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text("TARGET ${branch.target} - STACKED ${branch.gross}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onEnter,
                colors = ButtonDefaults.buttonColors(containerColor = BbBrickBlue),
            ) { Text("CLICK ${repo.currentBranch().name.uppercase()} INTO PLACE") }
        }
    }
}

@Composable
private fun BbShell(
    repo: BlockBuilderFakeRepo,
    tab: BbTab,
    onTab: (BbTab) -> Unit,
    onLogout: () -> Unit,
    onPickPlate: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(210.dp).fillMaxHeight().background(BbInk).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SNAP/TOYBOX", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BbBrickYellow,
                letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            for (t in BbTab.entries) {
                val selected = t == tab
                val unread = t == BbTab.MAIL && repo.unreadCount() > 0
                val shape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(if (selected) t.color else t.color.copy(alpha = 0.35f))
                        .padding(top = 5.dp, bottom = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BbStuds(color = Color.White.copy(alpha = 0.9f), count = 3,
                        modifier = Modifier.size(80.dp, 12.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                        .background(if (selected) Color.White else Color(0xFF33353F))
                        .border(if (selected) 3.dp else 2.dp, if (selected) BbInk else Color(0xFF4B4E5A), shape)
                        .clickable { onTab(t) }
                        .padding(10.dp),
                ) {
                    Text(
                        t.label + if (unread) " (${repo.unreadCount()})" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selected) BbInk else Color.White,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("FAKE BRICKS ONLY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BbMuted)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BbDayBanner(repo = repo, onPickPlate = onPickPlate)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    BbTab.HOME -> BbHomeScreen(repo)
                    BbTab.SESSIONS -> BbSessionsScreen(repo)
                    BbTab.CLIENTS -> BbClientsScreen(repo)
                    BbTab.FINANCE -> BbFinanceScreen(repo)
                    BbTab.TEAM -> BbTeamScreen(repo)
                    BbTab.MAIL -> BbMailScreen(repo)
                    BbTab.AUDIT -> BbAuditScreen(repo)
                    BbTab.PROFILE -> BbProfileScreen(repo, onLogout = onLogout)
                }
            }
        }
    }
}

@Composable
private fun BbDayBanner(repo: BlockBuilderFakeRepo, onPickPlate: () -> Unit) {
    val branch = repo.currentBranch()
    Row(
        modifier = Modifier.fillMaxWidth().background(BbCreamDeep).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(branch.dayStatus.brick())
                    .padding(top = 4.dp, bottom = 1.dp)
                    .width(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                BbStuds(color = Color.White.copy(alpha = 0.9f), count = 3, modifier = Modifier.size(70.dp, 10.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(branch.dayStatus.brick())
                    .border(2.dp, BbInk, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("PLATE ${branch.dayStatus}", fontWeight = FontWeight.Black, fontSize = 14.sp,
                    color = if (branch.dayStatus == BbDayStatus.REMITTED) BbInk else Color.White)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${branch.name} - ${branch.district}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = BbInk)
            Text("Build plates reset at the 04:00 Asia/Manila boundary. " +
                if (repo.meClockedIn.value) "Snapped in." else "Popped off.",
                fontSize = 12.sp, color = BbInkSoft)
        }
        OutlinedButton(onClick = onPickPlate) { Text("SWAP PLATE") }
    }
}
