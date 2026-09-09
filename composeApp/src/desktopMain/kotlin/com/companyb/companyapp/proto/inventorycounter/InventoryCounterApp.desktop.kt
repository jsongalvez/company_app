package com.companyb.companyapp.proto.inventorycounter

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #774 — stock-counter shell: login rail, ONBOARDING lock, branch select,
// shelf nav with the count ledger as home.

enum class IcDest(val label: String) {
    COUNT("Count Ledger"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

@Composable
fun ProtoInventoryCounterApp(onBack: () -> Unit, repo: InventoryCounterRepo = remember { InventoryCounterRepo() }) {
    IcThemeWrap {
        val user = repo.currentUser
        when {
            user == null -> IcLoginGate(repo)
            user.locked -> IcLockedGate(repo)
            repo.currentBranchId.isBlank() -> IcBranchGate(repo)
            else -> IcShell(repo, onBack)
        }
    }
}

@Composable
private fun IcLoginGate(repo: InventoryCounterRepo) {
    Box(Modifier.fillMaxSize().background(IcColors.Kraft).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IcScreenTitle()
            IcHeadline("Count first, sessions second")
            IcNote("Pick a practitioner to open the stockroom. Fake sign-in — no network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                IcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = IcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = IcColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        IcPrimary("Sign in") {
                            repo.currentUser = u
                            repo.currentBranchId = ""
                            repo.log("${u.name} signed in (fake)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IcLockedGate(repo: InventoryCounterRepo) {
    Box(Modifier.fillMaxSize().background(IcColors.Kraft).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(540.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IcHeadline("Locked out — ONBOARDING")
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                            "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS.",
                        color = IcColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    IcNote("Fake-data flow: the gate renders instead of any stockroom content.")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IcGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun IcBranchGate(repo: InventoryCounterRepo) {
    Box(Modifier.fillMaxSize().background(IcColors.Kraft).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "SIGNED IN AS ${repo.currentUser?.name?.uppercase()}",
                color = IcColors.Stencil,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
            IcHeadline("Which stockroom today?")
            repo.branches.forEach { b ->
                IcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, color = IcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                IcFlag(b.dayStatus.name, IcColors.Stencil, b.dayStatus.wash())
                            }
                            Text(
                                "${b.kind} · ${repo.branchStock(b.id).size} SKUs · " +
                                    "${repo.lowStock(b.id).size} low-stock flags",
                                color = IcColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        IcPrimary("Open") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} opened ${b.name} stockroom (fake)")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IcGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun IcShell(repo: InventoryCounterRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(IcDest.COUNT) }
    Row(Modifier.fillMaxSize().background(IcColors.Kraft)) {
        Column(
            Modifier.width(228.dp).fillMaxHeight()
                .background(IcColors.Ink)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("▮▮▮ STOCKROOM", color = IcColors.Kraft, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(
                "${repo.currentUser?.name} · ${repo.currentUser?.role}",
                color = IcColors.Faded,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            IcDest.entries.forEach { d ->
                val selected = d == dest
                val unread = d == IcDest.MAILBOX && repo.notices.any { !it.read }
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            if (selected) IcColors.Safety else androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                        )
                        .clickable { dest = d }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        d.label + if (unread) " ●" else "",
                        color = if (selected) androidx.compose.ui.graphics.Color.White else IcColors.Kraft,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "◀ Exit prototype",
                color = IcColors.Faded,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(28.dp)) {
            IcDayBanner(repo)
            Spacer(Modifier.height(12.dp))
            when (dest) {
                IcDest.COUNT -> IcCountScreen(repo) { dest = IcDest.SESSIONS }
                IcDest.SESSIONS -> IcSessionsScreen(repo)
                IcDest.CLIENTS -> IcClientsScreen(repo)
                IcDest.FINANCE -> IcFinanceScreen(repo)
                IcDest.TEAM -> IcTeamScreen(repo)
                IcDest.MAILBOX -> IcMailboxScreen(repo)
                IcDest.AUDIT -> IcAuditScreen(repo)
                IcDest.PROFILE -> IcProfileScreen(repo, onBack)
            }
        }
    }
}

// Branch-day banner: OPEN/PAST/REMITTED + the 04:00 Asia/Manila boundary note.
@Composable
fun IcDayBanner(repo: InventoryCounterRepo) {
    val b = repo.currentBranch()
    IcCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, color = IcColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    IcFlag(b.dayStatus.name, IcColors.Stencil, b.dayStatus.wash())
                }
                IcNote(
                    "Branch day boundary 04:00 Asia/Manila — ${b.name} stays editable until 04:00 the next " +
                        "morning, then turns PAST lazily. REMITTED days need Coordinator-only edits.",
                )
            }
            IcLink("Switch branch") { repo.currentBranchId = "" }
        }
    }
}

// Home: clock-in, relief duty/invite/request board, low-stock ticker.
@Composable
fun IcCountHome(repo: InventoryCounterRepo, goSessions: () -> Unit) {
    val me = repo.currentUser
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Morning count — ${repo.currentBranch().name}")
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (repo.clockedIn) "● CLOCKED IN as ${me?.name}" else "○ NOT CLOCKED IN",
                    color = if (repo.clockedIn) IcColors.Moss else IcColors.Safety,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
                IcNote(
                    "Clock-in starts your branch day at ${repo.currentBranch().name}. " +
                        "Relief duty at a non-home branch starts view-only — edit access needs a relief grant.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!repo.clockedIn) {
                        IcPrimary("Clock in") {
                            repo.clockedIn = true
                            repo.log("${me?.name} clocked in at ${repo.currentBranch().name}")
                        }
                    } else {
                        IcGhost("Clock out") {
                            repo.clockedIn = false
                            repo.log("${me?.name} clocked out at ${repo.currentBranch().name}")
                        }
                    }
                    IcGhost("Book walk-in session") { goSessions() }
                }
            }
        }
        IcSection("Low-stock ticker") {
            val lows = repo.lowStock(repo.currentBranchId)
            if (lows.isEmpty()) {
                IcCard { IcNote("Shelves healthy — nothing at or below its reorder flag.") }
            } else {
                lows.forEach { item ->
                    IcCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IcSku(item.sku)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = IcColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "counted ${item.countedQty ?: "—"} ${item.unit}s · reorder at ${item.lowAt}",
                                    color = IcColors.Faded,
                                    fontSize = 12.sp,
                                )
                            }
                            IcFlag("LOW", IcColors.Safety, IcColors.SafetyWash)
                        }
                    }
                }
            }
        }
        IcSection("Relief duty · invites · requests") {
            repo.reliefBoard.forEach { line ->
                IcCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(line, color = IcColors.Ink, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IcLink("Accept") { repo.log("Relief line accepted by ${me?.name} (fake): $line") }
                            IcLink("Decline") { repo.log("Relief line declined by ${me?.name} (fake): $line") }
                        }
                    }
                }
            }
            IcNote(
                "Relief requests are outsider-initiated (one live per requester per branch per date); " +
                    "relief invites are branch-initiated. Both expire at 04:00 Manila.",
            )
        }
    }
}
