package com.companyb.companyapp.proto.blueprintdraft

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class BpPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class BpTab(val label: String) {
    HOME("HOME"),
    SESSIONS("SESSIONS"),
    CLIENTS("CLIENTS"),
    FINANCE("FINANCE"),
    TEAM("TEAM"),
    MAIL("MAIL"),
    AUDIT("AUDIT"),
    PROFILE("PROFILE"),
}

@Composable
fun BlueprintDraftProtoApp() {
    BlueprintDraftTheme {
        val repo = remember { BlueprintDraftFakeRepo() }
        var phase by remember { mutableStateOf(BpPhase.LOGIN) }
        var tab by remember { mutableStateOf(BpTab.HOME) }
        Box(modifier = Modifier.fillMaxSize()) {
            BpGridBackdrop(modifier = Modifier.matchParentSize())
            when (phase) {
                BpPhase.LOGIN -> BpLoginSheet(
                    onLogin = { phase = BpPhase.BRANCH_SELECT },
                    onOnboarding = { phase = BpPhase.ONBOARDING_LOCKED },
                )
                BpPhase.ONBOARDING_LOCKED -> BpOnboardingLockSheet(onBack = { phase = BpPhase.LOGIN })
                BpPhase.BRANCH_SELECT -> BpBranchPickSheet(
                    repo = repo,
                    onEnter = { tab = BpTab.HOME; phase = BpPhase.APP },
                )
                BpPhase.APP -> BpShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = BpPhase.LOGIN },
                    onPickBranch = { phase = BpPhase.BRANCH_SELECT },
                )
            }
        }
    }
}

@Composable
fun BpWhiteButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BpLine, contentColor = BpBlue, disabledContainerColor = BpLineFaint),
    ) {
        Text(label, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun BpGhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BpLine, disabledContentColor = BpLineFaint),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) BpLine else BpLineFaint),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun BpField(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BpDim("⌞ $label")
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(placeholder, color = BpLineFaint, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(2.dp),
            textStyle = TextStyle(color = BpLine, fontSize = 14.sp, fontFamily = FontFamily.SansSerif),
        )
    }
}

@Composable
fun BpFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) BpLine else Color.Transparent)
            .border(1.5.dp, BpLine, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) BpBlue else BpLine,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun BpStatusTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.5.dp, color, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BpLoginSheet(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var name by remember { mutableStateOf("mara.villanueva") }
    var pass by remember { mutableStateOf("draft-1104") }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BpKicker("CompanyApp · Architect blueprint · Rev P1")
            Text("BLUEPRINT-DRAFT", color = BpLine, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            BpDim("WHITE-ON-BLUE LINEWORK — MEASURE TWICE, INK ONCE — SHEET 0 OF 8")
            BpDimensionBar("◄ 1280 MIN ►")
            BpSheetCard(sheetNo = "T-000 TITLE", scale = "SCALE —") {
                BpKicker("Title block · sign on")
                BpField("Drafter id", name, { name = it }, "e.g. mara.villanueva")
                BpField("Pass line", pass, { pass = it }, "fake only — never sent")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BpWhiteButton("▸ INK LOGIN", onClick = onLogin, modifier = Modifier.weight(1f))
                    BpGhostButton("ONBOARDING SHEET", onClick = onOnboarding, modifier = Modifier.weight(1f))
                }
                BpDashedDivider()
                BpDim("NOTE 1 — FAKE DATA ONLY. NO NETWORK. NO BACKEND. THIS SHEET NEVER LEAVES THE TABLE.")
                BpDim("NOTE 2 — ONBOARDING ROLE IS REDLINED LOCKED: EMPTY CAPABILITY BUNDLE, NO CLOCK-IN.")
            }
            BpDraftStamp()
            BpDim("ELEV 04:00 ASIA/MANILA — BRANCH DAY ROLLS AT THE DOTTED LINE · DWG BP-DRAFT-845")
        }
    }
}

@Composable
private fun BpOnboardingLockSheet(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BpKicker("Sheet T-0B · onboarding")
            Text("LOCKED SHEET", color = BpLine, fontSize = 36.sp, fontWeight = FontWeight.Black)
            BpSheetCard(sheetNo = "T-0B", scale = "SCALE —") {
                BpKicker("ONBOARDING · capability: none")
                Text(
                    "This drafter holds the ONBOARDING stamp. Capability bundle is empty: no clock-in, no branch select, no session ink. A MANAGER countersign on the TEAM sheet promotes to Practitioner.",
                    color = BpLine,
                    fontSize = 14.sp,
                )
                BpDashedDivider()
                BpDim("CLOUD 1 — EMPTY CAPABILITY BUNDLE HOLDS THE SHEET AT THE TITLE BLOCK.")
                BpGhostButton("◂ BACK TO TITLE BLOCK", onClick = onBack)
            }
            BpDraftStamp("ONBOARDING · HOLD — NOT FOR BUILD")
        }
    }
}

@Composable
private fun BpBranchPickSheet(repo: BlueprintDraftFakeRepo, onEnter: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BpKicker("Sheet B-001 · drafting tables")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PICK A TABLE", color = BpLine, fontSize = 30.sp, fontWeight = FontWeight.Black)
                BpDraftStamp("DRAFT")
            }
            BpDimensionBar("4 TABLES · 1280 GRID")
            repo.branches.forEach { b ->
                BpSheetCard(sheetNo = b.sheetNo, scale = "TGT ₱${b.target}") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.name, color = BpLine, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            BpDim("${b.district} · GROSS ₱${b.gross} · ON SHIFT ${b.onShift}")
                        }
                        BpStatusTag(b.dayStatus.name, BpDayStatusColor(b.dayStatus))
                    }
                    BpDashedDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BpWhiteButton(
                            if (repo.branchFilter.value == b.id) "■ ON THIS TABLE" else "▸ DRAFT HERE",
                            onClick = { repo.selectBranch(b.id); onEnter() },
                            modifier = Modifier.weight(1f),
                        )
                        BpDim("◄ ${b.sheetNo} ►")
                    }
                }
            }
            BpDim("NOTE — DAY STATUS RIDES THE TITLE BLOCK: OPEN / PAST / REMITTED. 04:00 ASIA/MANILA ROLLS THE SHEET.")
        }
    }
}

@Composable
private fun BpShell(
    repo: BlueprintDraftFakeRepo,
    tab: BpTab,
    onTab: (BpTab) -> Unit,
    onLogout: () -> Unit,
    onPickBranch: () -> Unit,
) {
    val branch = repo.currentBranch()
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(228.dp).fillMaxHeight().background(BpBlueDeep.copy(alpha = 0.85f))
                .border(2.dp, BpLine).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BpKicker("Index")
            Text("BP-DRAFT", color = BpLine, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
            BpDim("DWG 845 · REV P1")
            BpDashedDivider()
            BpTab.entries.forEach { t ->
                val selected = t == tab
                val label = if (t == BpTab.MAIL) "MAIL (${repo.unreadCount()})" else t.label
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) BpLine else Color.Transparent)
                        .border(1.5.dp, BpLine, RoundedCornerShape(2.dp))
                        .clickable { onTab(t) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(
                        "▤ $label",
                        color = if (selected) BpBlue else BpLine,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            BpDashedDivider()
            BpDim("ME ■ ${if (repo.meClockedIn.value) "CLOCKED IN" else "OUT"}")
            BpGhostButton("SWAP TABLE", onClick = onPickBranch)
            BpGhostButton("LOG OUT", onClick = onLogout)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BpTitleBanner(repo = repo)
            when (tab) {
                BpTab.HOME -> BpHomeSheet(repo)
                BpTab.SESSIONS -> BpSessionsSheet(repo)
                BpTab.CLIENTS -> BpClientsSheet(repo)
                BpTab.FINANCE -> BpFinanceSheet(repo)
                BpTab.TEAM -> BpTeamSheet(repo)
                BpTab.MAIL -> BpMailboxSheet(repo)
                BpTab.AUDIT -> BpAuditSheet(repo)
                BpTab.PROFILE -> BpProfileSheet(repo, onLogout = onLogout, onPickBranch = onPickBranch)
            }
            BpDim("FOOT — BP-DRAFT-845 · WHITE-ON-BLUE · MEASURED AT 1280 MIN · FULL-SCREEN CAPABLE · FAKE DATA ONLY")
        }
    }
}

@Composable
private fun BpTitleBanner(repo: BlueprintDraftFakeRepo) {
    val branch = repo.currentBranch()
    BpSheetCard(sheetNo = branch.sheetNo, scale = "04:00 ASIA/MANILA") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BpKicker("Branch-day title block")
                Text(branch.name, color = BpLine, fontWeight = FontWeight.Black, fontSize = 22.sp)
                BpDim("${branch.district} · GROSS ₱${branch.gross} / TGT ₱${branch.target} · ON SHIFT ${branch.onShift}")
            }
            BpStatusTag(branch.dayStatus.name, BpDayStatusColor(branch.dayStatus))
        }
        BpDashedDivider()
        BpDim("DATUM — BRANCH DAY ROLLS AT 04:00 ASIA/MANILA. OPEN = INKABLE · PAST = READ-ONLY MEASURE · REMITTED = SEALED PRINT.")
        BpDimensionBar("◄ ${branch.sheetNo} · ${branch.dayStatus} ►")
    }
}
