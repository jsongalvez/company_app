package com.companyb.companyapp.proto.monthclose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #851 — month-close close pack: unremitted days, pending voids, snapshot archive in one checklist.

enum class McScreen {
    ONBOARDING,
    LOGIN,
    BRANCHES,
    HOME,
    SESSIONS,
    CLIENTS,
    FINANCE,
    TEAM,
    MAILBOX,
    AUDIT,
    PROFILE,
}

@Composable
fun MonthCloseProtoApp(onBack: () -> Unit) {
    val repo = remember { MonthCloseFakeRepo() }
    var screen by remember { mutableStateOf(McScreen.ONBOARDING) }
    logInfo("mc-proto", "month-close opened")
    Column(Modifier.fillMaxSize().background(McTheme.Paper)) {
        McTopBar(repo, screen, onBack) { screen = it }
        val day = repo.selectedDay()
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            McBanner(day, repo.currentBranch.name)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            when (screen) {
                McScreen.ONBOARDING -> McOnboarding(repo) { screen = McScreen.LOGIN }
                McScreen.LOGIN -> McLogin(repo) { screen = McScreen.BRANCHES }
                McScreen.BRANCHES -> McBranchSelect(repo) { screen = McScreen.HOME }
                McScreen.HOME -> McHome(repo) { screen = it }
                McScreen.SESSIONS -> McSessions(repo)
                McScreen.CLIENTS -> McClients(repo)
                McScreen.FINANCE -> McFinance(repo)
                McScreen.TEAM -> McTeam(repo)
                McScreen.MAILBOX -> McMailbox(repo)
                McScreen.AUDIT -> McAuditList(repo)
                McScreen.PROFILE -> McProfile(repo) { screen = it }
            }
        }
    }
}

@Composable
private fun McTopBar(repo: MonthCloseFakeRepo, screen: McScreen, onBack: () -> Unit, go: (McScreen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(McTheme.Ink).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MONTH-CLOSE PACK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text("September · fake data · no network", color = Color(0xFFB9C1D6), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("EXIT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            McNavButton("Pack", screen == McScreen.HOME) { go(McScreen.HOME) }
            McNavButton("Sessions", screen == McScreen.SESSIONS) { go(McScreen.SESSIONS) }
            McNavButton("Clients", screen == McScreen.CLIENTS) { go(McScreen.CLIENTS) }
            McNavButton("Finance", screen == McScreen.FINANCE) { go(McScreen.FINANCE) }
            McNavButton("Team", screen == McScreen.TEAM) { go(McScreen.TEAM) }
            McNavButton("Mailbox", screen == McScreen.MAILBOX) { go(McScreen.MAILBOX) }
            McNavButton("Audit", screen == McScreen.AUDIT) { go(McScreen.AUDIT) }
            McNavButton("Profile", screen == McScreen.PROFILE) { go(McScreen.PROFILE) }
        }
    }
}

@Composable
private fun McOnboarding(repo: MonthCloseFakeRepo, next: () -> Unit) {
    var denied by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Onboarding · locked hand", "ONBOARDING has zero capabilities")
        McCard {
            Text("J. Ramos is ONBOARDING: read the close pack, touch nothing sealed.", fontSize = 13.sp, color = McTheme.Ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { denied = "Refused: ONBOARDING cannot touch Sessions — needs Practitioner grant." }) {
                    Text("Attempt Sessions chapter")
                }
                Button(
                    onClick = {
                        repo.onboarded = true
                        repo.log("D. Lim", "ONBOARDING_GRANTED", "J. Ramos → Practitioner")
                        next()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = McTheme.SealRed),
                ) {
                    Text("Inscribe Practitioner")
                }
            }
            if (denied != null) {
                Spacer(Modifier.height(8.dp))
                Text(denied!!, color = McTheme.SealRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (repo.onboarded) {
                Spacer(Modifier.height(6.dp))
                Text("Grant recorded in audit. Continue to login.", color = McTheme.SealedGreen, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun McLogin(repo: MonthCloseFakeRepo, next: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Login · false directory", "pick a hand to continue")
        repo.users.forEach { u ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(McTheme.Card).clickable {
                        repo.currentUser = u
                        repo.log(u.name, "LOGIN", u.role.name)
                        next()
                    }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = McTheme.Ink)
                    Text("${u.role} · home ${repo.branches.firstOrNull { it.id == u.homeBranchId }?.name}",
                        fontSize = 12.sp, color = McTheme.Muted)
                }
                McStatusChip(u.role.name, if (u.role == McRole.ONBOARDING) McTheme.PendingAmber else McTheme.SealedGreen)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun McBranchSelect(repo: MonthCloseFakeRepo, next: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McSectionTitle("Branch select · one house kept", repo.currentUser.name)
        repo.branches.forEach { b ->
            val kept = b.id == repo.currentBranch.id
            McCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = McTheme.Ink)
                        Text(b.kind.name, fontSize = 12.sp, color = McTheme.Muted)
                    }
                    if (kept) McStatusChip("KEPT", McTheme.SealRed)
                    else OutlinedButton(onClick = {
                        repo.currentBranch = b
                        repo.log(repo.currentUser.name, "BRANCH_KEPT", b.name)
                        next()
                    }) { Text("Keep") }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(onClick = next) { Text("Continue with ${repo.currentBranch.name}") }
    }
}

@Composable
private fun McHome(repo: MonthCloseFakeRepo, go: (McScreen) -> Unit) {
    val (done, total) = repo.closeProgress()
    val unremitted = repo.unremittedDays()
    val voids = repo.sessions.filter { it.voided }
    val missing = repo.voidsMissingReason()
    val snaps = repo.snapshots()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        McCloseProgress(done, total)
        Spacer(Modifier.height(8.dp))
        McCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (repo.clockedIn) "Clocked in · ${repo.currentBranch.name}" else "Off duty",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = McTheme.Ink)
                    Text("Home ${repo.currentBranch.name} · ${repo.currentUser.name} (${repo.currentUser.role})",
                        fontSize = 12.sp, color = McTheme.Muted)
                }
                if (repo.clockedIn) {
                    OutlinedButton(onClick = {
                        repo.clockedIn = false
                        repo.log(repo.currentUser.name, "CLOCK_OUT", repo.currentBranch.name)
                    }) { Text("Clock out") }
                } else {
                    Button(
                        onClick = {
                            repo.clockedIn = true
                            repo.log(repo.currentUser.name, "CLOCK_IN", repo.currentBranch.name)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = McTheme.SealedGreen),
                    ) { Text("Clock in") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        McSectionTitle("Close checklist", "seal every row before month seal")
        McCard {
            McCheckRow("Remit all branch days", "${unremitted.size} unremitted · oldest ${unremitted.firstOrNull()?.dateLabel ?: "none"}",
                unremitted.isEmpty()) { go(McScreen.FINANCE) }
            McCheckRow("Voids carry reasons", "${missing.size} void(s) missing a reason · ${voids.size} voided total",
                missing.isEmpty()) { go(McScreen.SESSIONS) }
            McCheckRow("Snapshots archived", "${snaps.size} sealed folios filed · need Sep 4–9",
                snaps.size >= 8) { go(McScreen.FINANCE) }
            McCheckRow("No PENDING stragglers on past days", "past-day PENDING blocks the pack",
                repo.sessions.none { it.status == McSessionStatus.PENDING && repo.days.firstOrNull { d -> d.id == it.dayId }?.status == McDayStatus.PAST }) {
                go(McScreen.SESSIONS)
            }
        }
        Spacer(Modifier.height(8.dp))
        McSectionTitle("Unremitted days", "${unremitted.size} open")
        unremitted.forEach { d ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(McTheme.Card)
                    .clickable { repo.selectedDayId = d.id }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${d.dow} ${d.dateLabel}${if (d.isToday) " · today" else ""}",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
                    Text("Sales P${d.sales}", fontSize = 12.sp, color = McTheme.Muted)
                }
                McDayStamp(d.status)
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        McSectionTitle("Relief duty · invites & requests", "cover moves")
        McCard {
            Text("Invites", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
            repo.invites.forEach { inv ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${inv.branchName} · ${inv.day}", fontSize = 13.sp, color = McTheme.Ink)
                        Text("from ${inv.fromUser} · ${inv.accepted?.let { if (it) "accepted" else "declined" } ?: "awaiting"}",
                            fontSize = 12.sp, color = McTheme.Muted)
                    }
                    if (inv.accepted == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                repo.invites[repo.invites.indexOf(inv)] = inv.copy(accepted = true)
                                repo.log(repo.currentUser.name, "RELIEF_ACCEPT", inv.branchName)
                            }) { Text("Take") }
                            OutlinedButton(onClick = {
                                repo.invites[repo.invites.indexOf(inv)] = inv.copy(accepted = false)
                                repo.log(repo.currentUser.name, "RELIEF_DECLINE", inv.branchName)
                            }) { Text("Pass") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Requests", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = McTheme.Ink)
            repo.requests.forEach { req ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${req.branchName} · ${req.day}", fontSize = 13.sp, color = McTheme.Ink)
                        Text(if (req.mine) "mine · ${req.decided ?: "open"}" else "teammate · ${req.decided ?: "open"}",
                            fontSize = 12.sp, color = McTheme.Muted)
                    }
                    if (!req.mine && req.decided == null) {
                        OutlinedButton(onClick = {
                            repo.requests[repo.requests.indexOf(req)] = req.copy(decided = "covered")
                            repo.log(repo.currentUser.name, "RELIEF_COVER", req.branchName)
                        }) { Text("Cover") }
                    }
                    if (req.mine && req.decided == null) {
                        OutlinedButton(onClick = {
                            repo.requests[repo.requests.indexOf(req)] = req.copy(decided = "withdrawn")
                            repo.log(repo.currentUser.name, "RELIEF_WITHDRAW", req.branchName)
                        }) { Text("Strike") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            var coverFor by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextField(value = coverFor, onValueChange = { coverFor = it },
                        label = { Text("New request, e.g. Fri Sep 12 day shift") }, singleLine = true)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (coverFor.isBlank()) return@Button
                        repo.requests.add(McReliefRequest("r-${repo.requests.size + 1}", repo.currentBranch.name, coverFor, mine = true))
                        repo.log(repo.currentUser.name, "RELIEF_REQUEST", coverFor)
                        coverFor = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = McTheme.Ink),
                ) { Text("Ask") }
            }
        }
    }
}
