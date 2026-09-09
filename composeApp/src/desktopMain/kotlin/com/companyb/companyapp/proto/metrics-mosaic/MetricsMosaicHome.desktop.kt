package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import com.companyb.companyapp.util.logInfo

// #779 — mosaic home: KPI sparkline tiles drill into dense flows;
// exception tiles surface no-shows, voids, and undo windows.

@Composable
fun MmMosaic(repo: MmRepo, user: MmUser, branch: MmBranch, onScreen: (MmScreen) -> Unit) {
    val list = repo.branchSessions(branch.id)
    val done = list.count { it.status == MmSessionStatus.COMPLETED }
    val pending = list.count { it.status == MmSessionStatus.PENDING }
    val noShows = list.count { it.status == MmSessionStatus.NO_SHOW }
    val revenue = list.filter { it.status == MmSessionStatus.COMPLETED }.sumOf { it.price }
    val exceptions = repo.exceptions(branch.id)
    val undoable = repo.remittances.count { it.stage == "SUBMITTED" && it.hoursOld < 48 }
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Today · ${branch.name}")
        Text(
            "Good morning, ${user.name.split(" ").first()}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MmColors.Ink,
        )
        MmNote("Tap any tile to drill into its flow. Calm by default, dense one tap down.")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MmCell(1f, onClick = { onScreen(MmScreen.SESSIONS) }) {
                MmKpi("Sessions", "${list.size}", "+${done} done", branch.weekSessions, MmColors.Teal)
            }
            MmCell(1f, onClick = { onScreen(MmScreen.FINANCE) }) {
                MmKpi("Revenue ₱", "$revenue", "7-day", branch.weekRevenue, MmColors.Moss)
            }
            MmCell(1f, alert = noShows > 0, onClick = { onScreen(MmScreen.SESSIONS) }) {
                MmKpi("No-shows", "$noShows", "needs review", branch.weekNoShow, MmColors.Clay)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MmCell(1f, onClick = { onScreen(MmScreen.SESSIONS) }) {
                MmText("PENDING", color = MmColors.Soft, size = 11, bold = true)
                MmText("$pending awaiting outcome", bold = true)
                MmNote("Stepper: Complete / No-show / Cancel")
            }
            MmCell(1f, alert = exceptions.isNotEmpty(), onClick = { onScreen(MmScreen.SESSIONS) }) {
                val excColor = if (exceptions.isNotEmpty()) MmColors.Clay else MmColors.Soft
                MmText("EXCEPTIONS", color = excColor, size = 11, bold = true)
                MmText("${exceptions.size} surface", bold = true)
                MmNote(if (exceptions.isEmpty()) "Nothing needs review" else "No-show, cancelled, voided")
            }
            MmCell(1f, alert = undoable > 0, onClick = { onScreen(MmScreen.FINANCE) }) {
                val undoColor = if (undoable > 0) MmColors.Clay else MmColors.Soft
                MmText("UNDO WINDOW", color = undoColor, size = 11, bold = true)
                MmText("$undoable reversible", bold = true)
                MmNote("Submitted < 48h can be undone")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MmCell(1f) {
                MmSection("Clock")
                MmText(if (repo.clockedIn) "Clocked in" else "Clocked out", bold = true)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!repo.clockedIn) {
                        MmButton("Clock in") {
                            repo.clockedIn = true
                            repo.log(user.login + " clocked in at " + branch.name)
                            logInfo("proto-metrics-mosaic", "clock in " + user.login)
                        }
                    } else {
                        MmButton("Clock out") {
                            repo.clockedIn = false
                            repo.log(user.login + " clocked out")
                        }
                    }
                }
            }
            MmCell(1f, onClick = { onScreen(MmScreen.MAIL) }) {
                MmSection("Mailbox")
                MmText("${repo.unreadCount()} unread", bold = true)
                MmNote("Relief events name branch + day")
            }
            MmCell(1f, onClick = { onScreen(MmScreen.TEAM) }) {
                MmSection("Team")
                MmText("${repo.users.size} people", bold = true)
                MmNote("Roles + capabilities")
            }
        }
        Spacer(Modifier.height(10.dp))
        MmTile {
            MmSection("Relief duty · invite · request")
            MmNote("Broadcast: one live request per date. Invites accept/decline below.")
            Spacer(Modifier.height(4.dp))
            repo.relief.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmText("${d.branchName} · ${d.day} · ${d.state}", size = 13)
                    Spacer(Modifier.weight(1f))
                    if (d.state == "OPEN") {
                        MmButton("Claim") {
                            d.state = "CLAIMED by " + user.login
                            repo.log(user.login + " claimed relief " + d.branchName + " " + d.day)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            MmNote(repo.inviteState)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmButton("Accept invite") {
                    repo.inviteState = "Accepted: cover BGC 2026-09-11"
                    repo.log(user.login + " accepted relief invite")
                }
                MmGhost("Decline") {
                    repo.inviteState = "Declined invite from cara"
                    repo.log(user.login + " declined relief invite")
                }
            }
            Spacer(Modifier.height(4.dp))
            MmNote(repo.broadcastState)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmButton("Broadcast request 2026-09-13") {
                    repo.broadcastState = "Live: need cover 2026-09-13 (one-live-per-date)"
                    repo.log(user.login + " broadcast relief request 2026-09-13")
                }
                MmGhost("Withdraw") {
                    repo.broadcastState = "No live broadcast"
                    repo.log(user.login + " withdrew relief request")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MmTile(onClick = { onScreen(MmScreen.AUDIT) }) {
            MmSection("Latest audit")
            repo.audit.take(3).forEach { MmNote("· $it") }
        }
    }
}
