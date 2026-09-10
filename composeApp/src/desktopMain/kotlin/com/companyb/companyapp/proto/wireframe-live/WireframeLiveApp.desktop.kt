package com.companyb.companyapp.proto.wireframelive

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

private enum class WfPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class WfTab(val label: String) {
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
fun WireframeLiveProtoApp() {
    WireframeLiveTheme {
        val repo = remember { WireframeLiveFakeRepo() }
        var phase by remember { mutableStateOf(WfPhase.LOGIN) }
        var tab by remember { mutableStateOf(WfTab.HOME) }
        Box(modifier = Modifier.fillMaxSize()) {
            WfGridBackdrop(modifier = Modifier.matchParentSize())
            when (phase) {
                WfPhase.LOGIN -> WfLoginBox(
                    onLogin = { phase = WfPhase.BRANCH_SELECT },
                    onOnboarding = { phase = WfPhase.ONBOARDING_LOCKED },
                )
                WfPhase.ONBOARDING_LOCKED -> WfOnboardingLockBox(onBack = { phase = WfPhase.LOGIN })
                WfPhase.BRANCH_SELECT -> WfBranchPickBox(
                    repo = repo,
                    onEnter = { tab = WfTab.HOME; phase = WfPhase.APP },
                )
                WfPhase.APP -> WfShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = WfPhase.LOGIN },
                    onPickBranch = { phase = WfPhase.BRANCH_SELECT },
                )
            }
        }
    }
}

@Composable
fun WfPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WfInk,
            contentColor = WfPaper,
            disabledContainerColor = WfFaint,
            disabledContentColor = WfPaper,
        ),
    ) {
        Text(label, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun WfGhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = WfInk,
            disabledContentColor = WfFaint,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) WfInk else WfFaint),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun WfField(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WfDim("┌ $label")
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(placeholder, color = WfFaint, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().background(WfPaper),
            shape = RoundedCornerShape(2.dp),
            textStyle = TextStyle(color = WfInk, fontSize = 14.sp, fontFamily = FontFamily.SansSerif),
        )
    }
}

@Composable
fun WfFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) WfInk else WfPaper)
            .border(1.5.dp, WfInk, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) WfPaper else WfInk,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun WfStatusTag(text: String, filled: Boolean) {
    Box(
        modifier = Modifier
            .background(if (filled) WfInk else WfPaper, RoundedCornerShape(2.dp))
            .border(1.5.dp, WfInk, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = if (filled) WfPaper else WfInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun WfLoginBox(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var name by remember { mutableStateOf("mara.villanueva") }
    var pass by remember { mutableStateOf("wire-1104") }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(580.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WfKicker("CompanyApp · living wireframe · WF-846")
            Text("WIREFRAME-LIVE", color = WfInk, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            WfDim("GRAYSCALE BOXES — EVERY GRAY BOX NOW FILLED WITH REAL FAKE DATA — ANNOTATED")
            WfRuler("1280 MIN · FULL-SCREEN CAPABLE")
            WfBoxCard(boxNo = "BOX-T00 TITLE", flag = 1, strip = "LOGIN · FAKE ONLY") {
                WfKicker("Title box · sign in")
                WfFillBlock()
                WfField("User id", name, { name = it }, "e.g. mara.villanueva")
                WfField("Pass line", pass, { pass = it }, "fake only — never sent")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WfPrimaryButton("▸ FILL + ENTER", onClick = onLogin, modifier = Modifier.weight(1f))
                    WfGhostButton("ONBOARDING BOX", onClick = onOnboarding, modifier = Modifier.weight(1f))
                }
                WfDashedDivider()
                WfFlagNote(1, "LOGIN BOX: pick any id. Fake sign-in only — no network calls, data stays on this box.")
                WfFlagNote(2, "ONBOARDING ROLE: locked box, empty capability bundle, no clock-in until flagged Practitioner.")
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WfLiveStamp()
            }
            WfDim("GRID 04:00 ASIA/MANILA — BRANCH DAY ROLLS AT THE DOTTED LINE · DWG WF-LIVE-846")
        }
    }
}

@Composable
private fun WfOnboardingLockBox(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(580.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WfKicker("Box T-0B · onboarding")
            Text("LOCKED BOX", color = WfInk, fontSize = 36.sp, fontWeight = FontWeight.Black)
            WfBoxCard(boxNo = "BOX-T0B", flag = 2, strip = "ONBOARDING · EMPTY") {
                WfKicker("ONBOARDING · capability: none")
                Text(
                    "This user holds the ONBOARDING stamp. Capability bundle is empty: no clock-in, no branch select, no session fill. A MANAGER flag-off on the TEAM box promotes to Practitioner.",
                    color = WfInk,
                    fontSize = 14.sp,
                )
                WfFillBlock()
                WfDashedDivider()
                WfFlagNote(2, "EMPTY BUNDLE HOLDS THIS BOX AT THE TITLE STRIP — NOTHING ELSE IS CLICKABLE HERE.")
                WfGhostButton("◂ BACK TO TITLE BOX", onClick = onBack)
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WfLiveStamp("ONBOARDING · HOLD — BOX STAYS GRAY")
            }
        }
    }
}

@Composable
private fun WfBranchPickBox(repo: WireframeLiveFakeRepo, onEnter: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.width(780.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WfKicker("Box B-001 · branch boxes")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PICK A BOX", color = WfInk, fontSize = 30.sp, fontWeight = FontWeight.Black)
                WfLiveStamp("4 BOXES · LIVE")
            }
            WfRuler("4 BRANCHES · 1280 GRID")
            repo.branches.forEachIndexed { index, b ->
                WfBoxCard(boxNo = b.boxNo + " · " + b.name.uppercase(), flag = 3, strip = "TGT ₱${b.target}") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.name, color = WfInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            WfDim("${b.district} · GROSS ₱${b.gross} · ON SHIFT ${b.onShift}")
                        }
                        WfStatusTag(b.dayStatus.name, b.dayStatus == WfDayStatus.REMITTED)
                    }
                    WfFillBlock()
                    WfDashedDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        WfPrimaryButton(
                            if (repo.branchFilter.value == b.id) "■ IN THIS BOX" else "▸ FILL HERE",
                            onClick = { repo.selectBranch(b.id); onEnter() },
                            modifier = Modifier.weight(1f),
                        )
                        WfDim("◄ ${b.boxNo} · ${index + 1}/4 ►")
                    }
                }
            }
            WfFlagNote(3, "BRANCH BOX: day status rides the strip — OPEN / PAST / REMITTED. 04:00 Asia/Manila rolls the box.")
        }
    }
}

@Composable
private fun WfShell(
    repo: WireframeLiveFakeRepo,
    tab: WfTab,
    onTab: (WfTab) -> Unit,
    onLogout: () -> Unit,
    onPickBranch: () -> Unit,
) {
    val branch = repo.currentBranch()
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(232.dp).fillMaxHeight().background(WfPaper)
                .border(2.dp, WfInk).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WfKicker("Index")
            Text("WF-LIVE", color = WfInk, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
            WfDim("DWG 846 · GRAYSCALE")
            WfDashedDivider()
            WfTab.entries.forEach { t ->
                val selected = t == tab
                val label = if (t == WfTab.MAIL) "MAIL (${repo.unreadCount()})" else t.label
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) WfInk else WfPaper)
                        .border(1.5.dp, WfInk, RoundedCornerShape(2.dp))
                        .clickable { onTab(t) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(
                        "▦ $label",
                        color = if (selected) WfPaper else WfInk,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            WfDashedDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WfDim("FLAGS")
                WfFilterPill(if (repo.showFlags.value) "ON" else "OFF", repo.showFlags.value) {
                    repo.showFlags.value = !repo.showFlags.value
                }
            }
            WfDim("ME ■ ${if (repo.meClockedIn.value) "CLOCKED IN" else "OUT"}")
            WfGhostButton("SWAP BOX", onClick = onPickBranch)
            WfGhostButton("LOG OUT", onClick = onLogout)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WfDayBanner(repo = repo)
            when (tab) {
                WfTab.HOME -> WfHomeBox(repo)
                WfTab.SESSIONS -> WfSessionsBox(repo)
                WfTab.CLIENTS -> WfClientsBox(repo)
                WfTab.FINANCE -> WfFinanceBox(repo)
                WfTab.TEAM -> WfTeamBox(repo)
                WfTab.MAIL -> WfMailboxBox(repo)
                WfTab.AUDIT -> WfAuditBox(repo)
                WfTab.PROFILE -> WfProfileBox(repo, onLogout = onLogout, onPickBranch = onPickBranch)
            }
            WfDim("FOOT — WF-LIVE-846 · GRAYSCALE BOXES · 1280 MIN · FULL-SCREEN CAPABLE · FAKE DATA ONLY")
        }
    }
}

@Composable
private fun WfDayBanner(repo: WireframeLiveFakeRepo) {
    val branch = repo.currentBranch()
    val fill = WfDayFill(branch.dayStatus)
    val text = WfDayText(branch.dayStatus)
    Box(
        modifier = Modifier.fillMaxWidth()
            .border(2.dp, WfInk, RoundedCornerShape(2.dp))
            .background(fill, RoundedCornerShape(2.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    WfKicker("Branch-day box")
                    Text(branch.name, color = text, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(
                        "${branch.boxNo} · ${branch.district} · GROSS ₱${branch.gross} / TGT ₱${branch.target} · ON SHIFT ${branch.onShift}",
                        color = text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                WfStatusTag(branch.dayStatus.name, branch.dayStatus == WfDayStatus.REMITTED)
            }
            Text(
                "BOUNDARY — BRANCH DAY ROLLS AT 04:00 ASIA/MANILA. OPEN = FILLABLE · PAST = READ-ONLY GRAY · REMITTED = SEALED BLACK.",
                color = text,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            WfRuler("${branch.boxNo} · ${branch.dayStatus}")
        }
    }
}
