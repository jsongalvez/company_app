package com.companyb.companyapp.proto.stickerbook

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class SbPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class SbTab(val label: String, val color: Color) {
    HOME("HOME", SbCoral),
    SESSIONS("SESSIONS", SbSky),
    CLIENTS("CLIENTS", SbGrape),
    FINANCE("FINANCE", SbLeaf),
    TEAM("TEAM", SbSun),
    MAIL("MAIL", SbBubble),
    AUDIT("AUDIT", SbInkSoft),
    ALBUM("ALBUM", SbCoral),
}

@Composable
fun StickerBookProtoApp() {
    StickerBookTheme {
        val repo = remember { StickerBookFakeRepo() }
        var phase by remember { mutableStateOf(SbPhase.LOGIN) }
        var tab by remember { mutableStateOf(SbTab.HOME) }
        Box(modifier = Modifier.fillMaxSize().background(SbPaper)) {
            when (phase) {
                SbPhase.LOGIN -> SbLogin(
                    onLogin = { phase = SbPhase.BRANCH_SELECT },
                    onOnboarding = { phase = SbPhase.ONBOARDING_LOCKED },
                )
                SbPhase.ONBOARDING_LOCKED -> SbOnboardingLock(onBack = { phase = SbPhase.LOGIN })
                SbPhase.BRANCH_SELECT -> SbBranchPick(
                    repo = repo,
                    onEnter = { tab = SbTab.HOME; phase = SbPhase.APP },
                )
                SbPhase.APP -> SbShell(
                    repo = repo,
                    tab = tab,
                    onTab = { tab = it },
                    onLogout = { phase = SbPhase.LOGIN },
                    onPickBranch = { phase = SbPhase.BRANCH_SELECT },
                )
            }
        }
    }
}

@Composable
private fun SbLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(SbCoral).padding(40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SbKicker("STICKER REWARD BOOK", Color.White)
                Text(
                    "Every shift earns\na sticker.",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BadgeStar("7", "TODAY", SbCoralDeep)
                    BadgeStar("12", "CREW", SbSky)
                    BadgeStar("4", "PAGES", SbGrape)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Crews, pride pages, mission stickers. Fake album data only, no network calls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            StickerCard(modifier = Modifier.width(430.dp)) {
                SbKicker("STICK TO START")
                Text("Open your reward book", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Clock in, finish missions, and paste badges onto your branch pride pages.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SbCoral),
                ) { Text("OPEN MY STICKER BOOK") }
                OutlinedButton(onClick = onOnboarding, modifier = Modifier.fillMaxWidth()) {
                    Text("I am new here (ONBOARDING)")
                }
                Text(
                    "Demo crew: Mara Villanueva, MANAGER at Sunbeam Strip. Any tap signs in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SbMuted,
                )
            }
        }
    }
}

@Composable
private fun SbOnboardingLock(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StickerCard(modifier = Modifier.width(460.dp)) {
            SbKicker("ONBOARDING", SbGrape)
            Text("Your page is still blank", style = MaterialTheme.typography.headlineSmall)
            Text(
                "ONBOARDING crew cannot clock in yet. A MANAGER pastes your first badge, " +
                    "then your reward book unlocks.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SbStatusSticker("LOCKED", SbGrape)
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("BACK TO SIGN IN") }
        }
    }
}

@Composable
private fun SbBranchPick(repo: StickerBookFakeRepo, onEnter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SbKicker("BRANCH PRIDE PAGES")
        Text("Pick a page to open", style = MaterialTheme.typography.displaySmall)
        Text(
            "Each Branch keeps its own pride page: crew on shift, gross against target, and the Branch Day seal.",
            style = MaterialTheme.typography.bodyMedium,
            color = SbInkSoft,
        )
        repo.branches.forEach { b ->
            StickerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.name, style = MaterialTheme.typography.titleLarge)
                        Text(b.pride, style = MaterialTheme.typography.bodySmall, color = SbInkSoft)
                        SbMeter("Gross", b.gross, b.target, SbLeaf)
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SbDaySeal(b.dayStatus)
                        Text("${b.onShift} on shift", fontSize = 12.sp, color = SbInkSoft)
                        val picked = repo.branchFilter.value == b.id
                        SbPill(
                            if (picked) "OPEN" else "OPEN PAGE",
                            picked,
                            SbCoral,
                        ) { repo.selectBranch(b.id); onEnter() }
                    }
                }
            }
        }
    }
}

@Composable
fun SbDaySeal(status: SbDayStatus) {
    val color = when (status) {
        SbDayStatus.OPEN -> SbOpen
        SbDayStatus.PAST -> SbPast
        SbDayStatus.REMITTED -> SbRemitted
    }
    SbStatusSticker(status.name, color)
}

@Composable
private fun SbShell(
    repo: StickerBookFakeRepo,
    tab: SbTab,
    onTab: (SbTab) -> Unit,
    onLogout: () -> Unit,
    onPickBranch: () -> Unit,
) {
    val branch = repo.currentBranch()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(SbInk).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("STICKER BOOK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            SbStatusSticker(branch.name.uppercase(), SbSun)
            SbDaySeal(branch.dayStatus)
            Spacer(Modifier.weight(1f))
            Text("04:00 Asia/Manila boundary", color = Color.White, fontSize = 11.sp)
            if (!repo.meClockedIn.value) SbStatusSticker("OFF SHIFT", SbMuted)
            SbPill("PAGES", false, SbSun) { onPickBranch() }
            SbPill("LOGOUT", false, SbCoral) { onLogout() }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.width(190.dp).fillMaxHeight().background(SbPaperDeep).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SbTab.entries.forEach { t ->
                    val selected = t == tab
                    SbPill(
                        (if (t == SbTab.MAIL && repo.unreadCount() > 0) "(${repo.unreadCount()}) " else "") + t.label,
                        selected,
                        t.color,
                    ) { onTab(t) }
                }
                Spacer(Modifier.weight(1f))
                StickerCard {
                    Text("Album", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${repo.stickers.size} stickers pasted",
                        style = MaterialTheme.typography.bodySmall,
                        color = SbInkSoft,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    SbTab.HOME -> SbHome(repo)
                    SbTab.SESSIONS -> SbSessions(repo)
                    SbTab.CLIENTS -> SbClients(repo)
                    SbTab.FINANCE -> SbFinance(repo)
                    SbTab.TEAM -> SbTeam(repo)
                    SbTab.MAIL -> SbMail(repo)
                    SbTab.AUDIT -> SbAudit(repo)
                    SbTab.ALBUM -> SbAlbum(repo, onLogout)
                }
            }
        }
    }
}
