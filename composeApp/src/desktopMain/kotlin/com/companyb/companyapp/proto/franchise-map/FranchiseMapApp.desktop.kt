package com.companyb.companyapp.proto.franchisemap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class FmAuthPhase { LOGIN, ONBOARDING_LOCKED, MAP_SELECT, APP }

enum class FmTab { MAP, DAY, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

@Composable
fun FranchiseMapProtoApp() {
    FranchiseMapTheme {
        val repo = remember { FranchiseMapFakeRepo() }
        var phase by remember { mutableStateOf(FmAuthPhase.LOGIN) }
        var tab by remember { mutableStateOf(FmTab.MAP) }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (phase) {
                FmAuthPhase.LOGIN -> FmLogin(
                    onLogin = { phase = FmAuthPhase.MAP_SELECT },
                    onOnboarding = { phase = FmAuthPhase.ONBOARDING_LOCKED },
                )
                FmAuthPhase.ONBOARDING_LOCKED -> FmOnboardingLock(onBack = { phase = FmAuthPhase.LOGIN })
                FmAuthPhase.MAP_SELECT -> FmTerritoryPick(
                    repo = repo,
                    onDrill = { tab = FmTab.DAY; phase = FmAuthPhase.APP },
                )
                FmAuthPhase.APP -> FmShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = FmAuthPhase.LOGIN },
                    onPickBranch = { tab = FmTab.MAP },
                )
            }
        }
    }
}

@Composable
private fun FmLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(FmRail), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(460.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FmCard),
        ) {
            Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("FRANCHISE ATLAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FmBrass,
                    letterSpacing = 3.sp)
                Text("Territory overview", style = MaterialTheme.typography.displaySmall, fontFamily = FmDisplay)
                Text("Branch pins on a schematic survey map. Pick a territory, drill to its branch day.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FmInk),
                ) { Text("Open the territory map") }
                OutlinedButton(onClick = onOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text("Preview the ONBOARDING welcome")
                }
                Text("Fake survey data only - no network calls.",
                    style = MaterialTheme.typography.bodySmall, color = FmPast)
            }
        }
    }
}

@Composable
private fun FmOnboardingLock(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(FmRail), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(460.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FmCard),
        ) {
            Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ONBOARDING - LOCKED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FmBrass,
                    letterSpacing = 3.sp)
                Text("Welcome, Rina", style = MaterialTheme.typography.headlineSmall, fontFamily = FmDisplay)
                Text("Capability bundle is empty: no branch duties, no session actions, no finance access. " +
                    "A MANAGER grants the Practitioner bundle from the TEAM chart before clock-in unlocks.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onBack) { Text("Back to login") }
            }
        }
    }
}

@Composable
private fun FmTerritoryPick(repo: FranchiseMapFakeRepo, onDrill: () -> Unit) {
    val current = repo.currentBranch()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FmRail).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("FRANCHISE ATLAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmGold,
                    letterSpacing = 3.sp)
                Text("Pick a branch pin to open its day",
                    style = MaterialTheme.typography.titleLarge, fontFamily = FmDisplay, color = FmParchment)
            }
            Text("${repo.branches.count { it.dayStatus == FmDayStatus.OPEN }} open today",
                color = FmParchment, fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.width(320.dp).fillMaxHeight().background(FmParchmentDeep)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("BRANCH ROSTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmBrass,
                    letterSpacing = 2.sp)
                repo.branches.forEach { b ->
                    val selected = b.id == current.id
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { repo.selectBranch(b.id) }
                            .then(if (selected) Modifier.border(2.dp, FmBrass, RoundedCornerShape(14.dp))
                            else Modifier),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = FmCard),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .background(b.dayStatus.dayColor()),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${b.territory} - ${b.dayStatus} - P${b.gross}/P${b.target}",
                                    fontSize = 12.sp, color = FmPast)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDrill,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FmInk),
                ) { Text("Drill to ${current.name}") }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                FmSurveyMap(
                    repo = repo,
                    modifier = Modifier.fillMaxSize(),
                    onPin = { repo.selectBranch(it) },
                )
            }
        }
    }
}

@Composable
fun FmSurveyMap(repo: FranchiseMapFakeRepo, modifier: Modifier = Modifier, onPin: (String) -> Unit) {
    val selected = repo.currentBranch().id
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FmParchment),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().border(1.dp, FmRoad, RoundedCornerShape(20.dp))) {
            val pins = repo.branches
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRoundRect(FmRidge, Offset(w * 0.06f, h * 0.06f), Size(w * 0.52f, h * 0.40f),
                    CornerRadius(48f, 48f))
                drawRoundRect(FmBay, Offset(w * 0.48f, h * 0.40f), Size(w * 0.46f, h * 0.52f),
                    CornerRadius(48f, 48f))
                drawRoundRect(FmSouth, Offset(w * 0.10f, h * 0.52f), Size(w * 0.44f, h * 0.40f),
                    CornerRadius(48f, 48f))
                val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                fun pt(b: FmBranch) = Offset(b.mapX * w, b.mapY * h)
                val order = listOf("sunrise", "uptown", "harbor", "lingap")
                val pts = order.mapNotNull { id -> pins.firstOrNull { it.id == id }?.let { pt(it) } }
                for (i in 0 until pts.size - 1) {
                    drawLine(FmRoad, pts[i], pts[i + 1], strokeWidth = 5f, pathEffect = dash)
                }
                pins.forEach { b ->
                    val c = Offset(b.mapX * w, b.mapY * h)
                    drawCircle(b.dayStatus.daySoft(), radius = 34f, center = c)
                }
            }
            Text("NORTH RIDGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmInk.copy(alpha = 0.55f),
                letterSpacing = 2.sp, modifier = Modifier.align(Alignment.TopStart).padding(20.dp, 14.dp))
            Text("HARBOR BAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmInk.copy(alpha = 0.55f),
                letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
            Text("METRO SOUTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmInk.copy(alpha = 0.55f),
                letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomStart).padding(20.dp, 16.dp))
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    .background(FmCard, RoundedCornerShape(10.dp)).border(1.dp, FmRoad, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("N ^  survey roads - - -", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmInk)
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    .background(FmCard, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FmLegendDot("OPEN", FmOpen)
                FmLegendDot("PAST", FmPast)
                FmLegendDot("REMITTED", FmRemitted)
            }
            pins.forEach { b ->
                val isSel = b.id == selected
                Column(
                    modifier = Modifier.align(Alignment.TopStart)
                        .padding(
                            start = (maxWidth * b.mapX) - 26.dp,
                            top = (maxHeight * b.mapY) - 26.dp,
                        )
                        .clickable { onPin(b.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(if (isSel) 56.dp else 48.dp).clip(CircleShape)
                            .background(b.dayStatus.dayColor())
                            .then(if (isSel) Modifier.border(3.dp, FmBrass, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(b.name.first().toString(), color = FmParchment, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, fontFamily = FmDisplay)
                    }
                    Box(
                        modifier = Modifier.background(FmRail, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(b.name, color = FmParchment, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FmLegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FmShell(
    repo: FranchiseMapFakeRepo,
    tab: FmTab,
    onTab: (FmTab) -> Unit,
    onLogout: () -> Unit,
    onPickBranch: () -> Unit,
) {
    val branch = repo.currentBranch()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FmRail).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ATLAS", fontWeight = FontWeight.Bold, color = FmGold, letterSpacing = 3.sp, fontSize = 13.sp)
            Spacer(Modifier.width(16.dp))
            FmTab.values().forEach { t ->
                val active = t == tab
                val label = if (t == FmTab.MAIL) "MAIL (${repo.unreadCount()})" else t.name
                TextButton(onClick = { onTab(t) }) {
                    Text(label, color = if (active) FmGold else FmParchment,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLogout) { Text("Logout", color = FmParchment, fontSize = 12.sp) }
        }
        FmDayBanner(repo = repo, onPickBranch = onPickBranch)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                FmTab.MAP -> Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.width(280.dp).fillMaxHeight().background(FmParchmentDeep)
                            .verticalScroll(rememberScrollState()).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("TERRITORY PINS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FmBrass,
                            letterSpacing = 2.sp)
                        repo.branches.forEach { b ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { repo.selectBranch(b.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (b.id == branch.id) FmCard else FmCard.copy(alpha = 0.6f)),
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape)
                                        .background(b.dayStatus.dayColor()))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${b.dayStatus} - P${b.gross}", fontSize = 11.sp, color = FmPast)
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { onTab(FmTab.DAY) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Drill to ${branch.name}")
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(14.dp)) {
                        FmSurveyMap(repo = repo, modifier = Modifier.fillMaxSize(),
                            onPin = { repo.selectBranch(it) })
                    }
                }
                FmTab.DAY -> FmDayScreen(repo)
                FmTab.SESSIONS -> FmSessionsScreen(repo)
                FmTab.CLIENTS -> FmClientsScreen(repo)
                FmTab.FINANCE -> FmFinanceScreen(repo)
                FmTab.TEAM -> FmTeamScreen(repo)
                FmTab.MAIL -> FmMailScreen(repo)
                FmTab.AUDIT -> FmAuditScreen(repo)
                FmTab.PROFILE -> FmProfileScreen(repo, onLogout = onLogout)
            }
        }
    }
}

@Composable
fun FmDayBanner(repo: FranchiseMapFakeRepo, onPickBranch: () -> Unit) {
    val branch = repo.currentBranch()
    Row(
        modifier = Modifier.fillMaxWidth().background(branch.dayStatus.daySoft())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(branch.dayStatus.dayColor()))
        Spacer(Modifier.width(10.dp))
        Text("${branch.name} - branch day ${branch.dayStatus}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text("Branch days roll at 04:00 Asia/Manila; OPEN takes sessions, PAST closes booking, " +
            "REMITTED seals the ledger.", fontSize = 12.sp, color = FmInk.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f))
        TextButton(onClick = onPickBranch) { Text("Change pin") }
    }
}

@Composable
fun FmChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
