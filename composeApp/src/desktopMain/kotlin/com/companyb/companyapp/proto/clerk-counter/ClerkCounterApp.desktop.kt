package com.companyb.companyapp.proto.clerkcounter

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #826 — clerk-counter shell: till header, login rail, ONBOARDING lock, branch
// select, scan-lane tabs with the counter lane as home.

enum class CcDest(val label: String, val sub: String) {
    LANE("LANE", "scan feed"),
    SESSIONS("SESSIONS", "roster"),
    CLIENTS("CLIENTS", "global"),
    FINANCE("FINANCE", "remit"),
    TEAM("TEAM", "roles"),
    MAILBOX("MAILBOX", "alerts"),
    AUDIT("AUDIT", "trail"),
    PROFILE("PROFILE", "me"),
}

@Composable
fun ClerkCounterProtoApp(onBack: () -> Unit, repo: ClerkCounterRepo = remember { ClerkCounterRepo() }) {
    CcThemeWrap {
        val user = repo.currentUser
        when {
            user == null -> CcLoginGate(repo)
            user.locked -> CcLockedGate(repo)
            repo.currentBranchId.isBlank() -> CcBranchGate(repo)
            else -> CcShell(repo, onBack)
        }
    }
}

@Composable
private fun CcLoginGate(repo: ClerkCounterRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CcScreenTitle()
            CcHeadline("Open the counter lane")
            CcNote("Pick a clerk to lift the till shutter. Fake sign-in — no network, no backend.")
            Spacer(Modifier.height(6.dp))
            repo.users.forEach { u ->
                CcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = CcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${u.role} · home ${repo.branch(u.homeBranchId).name}" +
                                    if (u.locked) " · ONBOARDING locked" else "",
                                color = CcColors.Muted,
                                fontSize = 12.sp,
                            )
                        }
                        CcTill("Sign in") {
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
private fun CcLockedGate(repo: ClerkCounterRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(540.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CcHeadline("Locked out — ONBOARDING")
            CcCard {
                Text(
                    "Eli Santos holds the ONBOARDING role: the capability bundle is empty, so nothing derives — " +
                        "even with a branch assignment. A MANAGER must grant a real role via MANAGE_USERS.",
                    color = CcColors.Ink,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                CcNote("Fake-data flow: the shutter stays down instead of any counter content.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun CcBranchGate(repo: ClerkCounterRepo) {
    Box(Modifier.fillMaxSize().background(CcColors.Paper).padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "SIGNED IN AS ${repo.currentUser?.name?.uppercase()}",
                color = CcColors.Stencil,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            CcHeadline("Which counter today?")
            repo.branches.forEach { b ->
                CcCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.name, color = CcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                CcFlag(b.dayStatus.name, CcColors.Stencil, b.dayStatus.wash())
                            }
                            Text(
                                "${b.kind} · ${repo.branchStock(b.id).size} SKUs · " +
                                    "${repo.lowStock(b.id).size} variance flags",
                                color = CcColors.Muted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        CcPrimary("Open lane") {
                            repo.currentBranchId = b.id
                            repo.log("${repo.currentUser?.name} opened ${b.name} counter (fake)")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcGhost("Switch user") { repo.currentUser = null }
            }
        }
    }
}

@Composable
private fun CcShell(repo: ClerkCounterRepo, onBack: () -> Unit) {
    var dest by remember { mutableStateOf(CcDest.LANE) }
    Column(Modifier.fillMaxSize().background(CcColors.Paper)) {
        Row(
            Modifier.fillMaxWidth().background(CcColors.Till).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "▮▮▮ CLERK COUNTER",
                color = CcColors.TillInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(3.dp).height(18.dp).background(CcColors.Scan)) { }
            Spacer(Modifier.width(12.dp))
            Text(
                "${repo.currentUser?.name} · ${repo.currentUser?.role} · ${repo.currentBranch().name}",
                color = CcColors.ScanWash,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            val unread = repo.notices.count { !it.read }
            Text(
                if (unread > 0) "● $unread ALERTS" else "○ LANE CLEAR",
                color = if (unread > 0) CcColors.OverWash else CcColors.ScanWash,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { dest = CcDest.MAILBOX }.padding(4.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "◀ EXIT",
                color = CcColors.TillInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(CcColors.Paper).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CcDest.entries.forEach { d ->
                val selected = d == dest
                val dot = d == CcDest.MAILBOX && repo.notices.any { !it.read }
                CcLaneTab(
                    title = d.label + if (dot) " ●" else "",
                    sub = d.sub,
                    selected = selected,
                    onClick = { dest = d },
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            CcDayBanner(repo)
            Spacer(Modifier.height(12.dp))
            when (dest) {
                CcDest.LANE -> CcLaneScreen(repo) { dest = CcDest.SESSIONS }
                CcDest.SESSIONS -> CcSessionsScreen(repo)
                CcDest.CLIENTS -> CcClientsScreen(repo)
                CcDest.FINANCE -> CcFinanceScreen(repo)
                CcDest.TEAM -> CcTeamScreen(repo)
                CcDest.MAILBOX -> CcMailboxScreen(repo)
                CcDest.AUDIT -> CcAuditScreen(repo)
                CcDest.PROFILE -> CcProfileScreen(repo, onBack)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Branch-day banner: OPEN/PAST/REMITTED + the 04:00 Asia/Manila boundary note.
@Composable
fun CcDayBanner(repo: ClerkCounterRepo) {
    val b = repo.currentBranch()
    CcCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, color = CcColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    CcFlag(b.dayStatus.name, CcColors.Stencil, b.dayStatus.wash())
                }
                CcNote(
                    "Branch day boundary 04:00 Asia/Manila — ${b.name} stays editable until 04:00 the next " +
                        "morning, then turns PAST lazily. REMITTED days need Coordinator-only edits.",
                )
            }
            CcLink("Switch branch") { repo.currentBranchId = "" }
        }
    }
}

// Home top: clock-in, relief duty/invite/request board, variance ticker.
@Composable
fun CcClockHome(repo: ClerkCounterRepo, goSessions: () -> Unit) {
    val me = repo.currentUser
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Counter lane — ${repo.currentBranch().name}")
        CcCard {
            Text(
                if (repo.clockedIn) "● TILL OPEN — ${me?.name}" else "○ TILL SHUT",
                color = if (repo.clockedIn) CcColors.Match else CcColors.Short,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            CcNote(
                "Clock-in lifts the shutter at ${repo.currentBranch().name}. " +
                    "Relief duty at a non-home branch starts view-only — edit access needs a relief grant.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!repo.clockedIn) {
                    CcPrimary("Clock in") {
                        repo.clockedIn = true
                        repo.log("${me?.name} clocked in at ${repo.currentBranch().name}")
                    }
                } else {
                    CcGhost("Clock out") {
                        repo.clockedIn = false
                        repo.log("${me?.name} clocked out at ${repo.currentBranch().name}")
                    }
                }
                CcGhost("Book walk-in session") { goSessions() }
            }
        }
        CcSection("Variance ticker") {
            val lows = repo.lowStock(repo.currentBranchId)
            if (lows.isEmpty()) {
                CcCard { CcNote("Lane clear — nothing at or below its reorder flag.") }
            } else {
                lows.forEach { item ->
                    CcCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CcScanCode(item.sku)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = CcColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "counted ${item.countedQty ?: "—"} ${item.unit}s · reorder at ${item.lowAt}",
                                    color = CcColors.Muted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            CcStamp("LOW", CcColors.Low, CcColors.LowWash)
                        }
                    }
                }
            }
        }
        CcSection("Relief duty · invites · requests") {
            repo.reliefBoard.forEach { line ->
                CcCard {
                    Text(line, color = CcColors.Ink, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CcLink("Accept") { repo.log("Relief line accepted by ${me?.name} (fake): $line") }
                        CcLink("Decline") { repo.log("Relief line declined by ${me?.name} (fake): $line") }
                    }
                }
            }
            CcNote(
                "Relief requests are outsider-initiated (one live per requester per branch per date); " +
                    "relief invites are branch-initiated. Both expire at 04:00 Manila.",
            )
        }
    }
}
