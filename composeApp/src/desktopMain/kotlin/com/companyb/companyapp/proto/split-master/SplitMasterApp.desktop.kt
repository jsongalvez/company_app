package com.companyb.companyapp.proto.splitmaster

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// #765 — split-master prototype shell: gates then a two-pane shell for every dest.
// Master scroll states are hoisted here so leaving a destination and coming back
// restores both the retained selection and the list scroll position.

@Composable
fun SplitMasterProtoApp(onExit: () -> Unit = {}, repo: SplitMasterRepo = remember { SplitMasterRepo() }) {
    val user = repo.currentUser
    when {
        user == null -> SmLoginGate(repo)
        user.locked -> SmLockedGate(repo)
        repo.currentBranchId.isBlank() -> SmBranchGate(repo)
        else -> SmShell(repo, onExit)
    }
}

@Composable
private fun SmGateFrame(kicker: String, title: String, note: String, content: @Composable () -> Unit) {
    SmMasterTheme {
        Box(Modifier.fillMaxSize().background(SmColors.Ink).padding(40.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().width(980.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(SM_MASTER_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kicker, color = SmColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(title, color = SmColors.InkText, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(note, color = SmColors.InkFaded, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SmGateRow(title: String, sub: String, action: String, onClick: () -> Unit) {
    SmMasterTheme {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SmColors.InkSoft)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = SmColors.InkText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(sub, color = SmColors.InkFaded, fontSize = 12.sp)
            }
            Text(action, color = SmColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SmLoginGate(repo: SplitMasterRepo) {
    SmGateFrame(
        kicker = "SPLIT-MASTER · SIGN IN",
        title = "Two panes, everywhere",
        note = "Fake sign-in — no network, no backend. Master list left (480dp), detail right. Selection sticks; back restores scroll.",
    ) {
        repo.users.forEach { u ->
            SmGateRow(
                title = u.name,
                sub = u.role + " · home " + repo.branch(u.homeBranchId).name + if (u.locked) " · ONBOARDING locked" else "",
                action = "Sign in",
            ) {
                repo.currentUser = u
                repo.currentBranchId = ""
                repo.log("${u.name} signed in (fake)")
            }
        }
    }
}

@Composable
private fun SmLockedGate(repo: SplitMasterRepo) {
    SmGateFrame(
        kicker = "SPLIT-MASTER · LOCKED",
        title = "ONBOARDING holds nothing",
        note = "Eli Santos carries the ONBOARDING role: the capability bundle is empty, so nothing derives — even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS.",
    ) {
        SmMasterTheme {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SmColors.InkSoft).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Locked out — fake-data gate, no dashboard content.", color = SmColors.InkText, fontSize = 14.sp)
                Text("Switch user", color = SmColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { repo.currentUser = null })
            }
        }
    }
}

@Composable
private fun SmBranchGate(repo: SplitMasterRepo) {
    SmGateFrame(
        kicker = "SIGNED IN AS ${(repo.currentUser?.name ?: "?").uppercase()}",
        title = "Choose today's branch",
        note = "The banner follows this choice: OPEN edits, PAST reads, REMITTED seals. Boundary 04:00 Asia/Manila.",
    ) {
        repo.branches.forEach { b ->
            SmGateRow(
                title = b.name,
                sub = b.kind + " · day " + b.dayStatus.name,
                action = "Open",
            ) {
                repo.currentBranchId = b.id
                repo.log("${repo.currentUser?.name} opened branch ${b.name} (fake)")
            }
        }
    }
}

@Composable
private fun SmShell(repo: SplitMasterRepo, onExit: () -> Unit) {
    // Hoisted master scroll per destination: navigating away and back restores scroll.
    val scrolls = remember { SmDest.entries.associateWith { LazyListState() } }
    Row(Modifier.fillMaxSize()) {
        SmMasterTheme {
            Column(
                Modifier.width(212.dp).fillMaxHeight().background(SmColors.InkSoft).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("SPLIT-MASTER", color = SmColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Text(repo.currentUser?.name ?: "", color = SmColors.InkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    repo.currentBranch().name + " · " + repo.currentBranch().dayStatus.name,
                    color = SmColors.InkFaded, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                SmDest.entries.forEach { d ->
                    val active = d == repo.dest
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) SmColors.Ink else SmColors.InkSoft)
                            .clickable { repo.dest = d }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (active) {
                            Box(Modifier.width(4.dp).height(20.dp).background(SmColors.Amber, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(d.label, color = if (active) SmColors.Amber else SmColors.InkText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            if (d == SmDest.MAILBOX) {
                                val unread = repo.notices.count { !it.read }
                                if (unread > 0) Text("$unread unread", color = SmColors.InkFaded, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Switch branch", color = SmColors.InkFaded, fontSize = 12.sp, modifier = Modifier.clickable { repo.currentBranchId = "" })
                Text("Exit prototype", color = SmColors.InkFaded, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onExit))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (repo.dest) {
                SmDest.HOME -> SmHomePane(repo, scrolls.getValue(SmDest.HOME))
                SmDest.SESSIONS -> SmSessionsPane(repo, scrolls.getValue(SmDest.SESSIONS))
                SmDest.CLIENTS -> SmClientsPane(repo, scrolls.getValue(SmDest.CLIENTS))
                SmDest.FINANCE -> SmFinancePane(repo, scrolls.getValue(SmDest.FINANCE))
                SmDest.TEAM -> SmTeamPane(repo, scrolls.getValue(SmDest.TEAM))
                SmDest.MAILBOX -> SmMailboxPane(repo, scrolls.getValue(SmDest.MAILBOX))
                SmDest.AUDIT -> SmAuditPane(repo, scrolls.getValue(SmDest.AUDIT))
                SmDest.PROFILE -> SmProfilePane(repo, onExit)
            }
        }
    }
}
