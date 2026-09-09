package com.companyb.companyapp.proto.bauhausblocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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

@Composable
private fun BhScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun BhScreenHead(kicker: String, title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BhKicker(kicker)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = BhMuted)
    }
}

@Composable
fun BhHomeScreen(repo: BauhausFakeRepo) {
    var requestNote by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    var inviteNote by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    BhScreenColumn {
        BhScreenHead("CLOCK-IN HOME", "Good shift, ${branch.name}",
            "Relief duty, invites, and requests live on one board. Fake roster data only.")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            BhCard(modifier = Modifier.weight(1f), accent = BhRed) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BhShapeIcon(BhShape.CIRCLE, BhRed, 26.dp)
                    BhKicker("MY SHIFT")
                }
                Text(if (repo.meClockedIn.value) "CLOCKED IN" else "CLOCKED OUT",
                    style = MaterialTheme.typography.titleLarge)
                Text("Home block: ${branch.name}. Clock state rides the day banner.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repo.setClockedIn(branch.id, true) },
                        enabled = !repo.meClockedIn.value,
                        colors = ButtonDefaults.buttonColors(containerColor = BhBlue),
                    ) { Text("CLOCK IN") }
                    OutlinedButton(
                        onClick = { repo.setClockedIn(branch.id, false) },
                        enabled = repo.meClockedIn.value,
                    ) { Text("CLOCK OUT") }
                }
            }
            BhCard(modifier = Modifier.weight(1f), accent = BhBlue) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BhShapeIcon(BhShape.SQUARE, BhBlue, 26.dp)
                    BhKicker("COMPOSITION", BhBlue)
                }
                Text("${repo.sessions.count { it.branchId == branch.id && it.status == BhSessionStatus.PENDING }} " +
                    "pending - ${repo.sessions.count { it.branchId == branch.id &&
                        it.status == BhSessionStatus.COMPLETED }} completed",
                    style = MaterialTheme.typography.titleMedium)
                Text("Day gross ${branch.gross} of target ${branch.target}. ${branch.onShift} on shift.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        BhCard(accent = BhYellow) {
            BhKicker("RELIEF DUTY BOARD", BhBlue)
            for (relief in repo.reliefs) {
                Row(
                    modifier = Modifier.fillMaxWidth().border(2.dp, BhLine).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BhShapeIcon(BhShape.TRIANGLE, BhYellow, 20.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${relief.asker} - ${repo.branchName(relief.branchId)}",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${relief.note} [${relief.state}]", style = MaterialTheme.typography.bodySmall)
                    }
                    if (relief.state == BhReliefState.OPEN) {
                        Button(
                            onClick = { repo.grantRelief(relief.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = BhRed),
                        ) { Text("GRANT") }
                        OutlinedButton(onClick = { repo.foldRelief(relief.id) }) { Text("FOLD") }
                    } else {
                        Text(relief.state.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BhMuted)
                    }
                }
            }
            Text("Broadcast a relief request", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextField(value = requestNote, onValueChange = { requestNote = it }, label = { Text("Need note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.addReliefRequest(branch.id, requestNote); requestNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = BhInk),
            ) { Text("BROADCAST REQUEST") }
            Text("Send a relief invite", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextField(value = inviteName, onValueChange = { inviteName = it }, label = { Text("Invitee name") },
                modifier = Modifier.fillMaxWidth())
            TextField(value = inviteNote, onValueChange = { inviteNote = it }, label = { Text("Invite note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.sendInvite(inviteName, branch.id, inviteNote); inviteName = ""; inviteNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = BhBlue),
            ) { Text("SEND INVITE") }
            for (line in repo.invites) {
                Text("- $line", style = MaterialTheme.typography.bodySmall, color = BhMuted)
            }
        }
    }
}

@Composable
fun BhSessionsScreen(repo: BauhausFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var newType by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    val shown = repo.sessions.filter { it.branchId == branch.id && (filter == null || it.status.name == filter) }
    BhScreenColumn {
        BhScreenHead("SESSIONS", "Session blocks at ${branch.name}",
            "PENDING flows to COMPLETED, NO_SHOW, or CANCELLED. Walk-ins never take NO_SHOW or CANCELLED.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BhPill("ALL", filter == null, BhInk) { filter = null }
            for (s in BhSessionStatus.entries) {
                BhPill(s.name, filter == s.name, s.chipColor()) { filter = s.name }
            }
        }
        BhCard(accent = BhBlue) {
            BhKicker("BOOK WALK-IN", BhBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextField(value = newType, onValueChange = { newType = it },
                        label = { Text("Session type, e.g. Drop-in 30") }, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = { repo.bookSession(branch.id, newType); newType = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = BhRed),
                ) { Text("BOOK PENDING") }
            }
        }
        for (s in shown) {
            BhSessionRow(repo = repo, session = s)
        }
        if (shown.isEmpty()) Text("No session blocks under this filter.", color = BhMuted)
    }
}

@Composable
private fun BhSessionRow(repo: BauhausFakeRepo, session: BhSession) {
    var reason by remember(session.id) { mutableStateOf("") }
    BhCard(accent = session.status.chipColor()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BhShapeIcon(if (session.walkIn) BhShape.CIRCLE else BhShape.SQUARE, session.status.chipColor(), 22.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("${session.id} - ${session.clientName}${if (session.walkIn) " (WALK-IN)" else ""}",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${session.type} - ${session.price}. Status ${session.status}" +
                    if (session.voided) " - VOIDED: ${session.voidReason}" else "",
                    style = MaterialTheme.typography.bodySmall, color = BhMuted)
            }
            Box(modifier = Modifier.border(2.dp, BhLine).background(session.status.chipColor())
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(session.status.name, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = if (session.status == BhSessionStatus.PENDING) Color.White else Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (target in BhSessionStatus.entries) {
                if (target == session.status) continue
                if (session.walkIn && (target == BhSessionStatus.NO_SHOW || target == BhSessionStatus.CANCELLED)) {
                    continue
                }
                TextButton(onClick = { repo.setSessionStatus(session.id, target) }) { Text("MARK ${target.name}") }
            }
        }
        if (session.walkIn) {
            Text("Walk-in rule: NO_SHOW and CANCELLED are not offered for walk-ins.",
                style = MaterialTheme.typography.bodySmall, color = BhMuted)
        }
        if (!session.voided) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextField(value = reason, onValueChange = { reason = it },
                        label = { Text("Void reason (required)") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { repo.voidSession(session.id, reason) }) { Text("VOID") }
            }
        } else {
            OutlinedButton(onClick = { repo.unvoidSession(session.id) }) { Text("UNVOID") }
        }
    }
}

@Composable
fun BhClientsScreen(repo: BauhausFakeRepo) {
    BhScreenColumn {
        BhScreenHead("CLIENTS", "Global client wall",
            "Clients are global across blocks. At most one PENDING session per client. " +
                "Anonymized view keeps gender and age only.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BhPill(if (repo.anonymized.value) "ANONYMIZED ON" else "ANONYMIZED OFF", repo.anonymized.value, BhBlue) {
                repo.anonymized.value = !repo.anonymized.value
            }
        }
        for (client in repo.clients) {
            BhClientRow(repo = repo, client = client)
        }
    }
}

@Composable
private fun BhClientRow(repo: BauhausFakeRepo, client: BhClient) {
    var hidden by remember(client.id) { mutableStateOf(false) }
    val anon = repo.anonymized.value || hidden
    BhCard(accent = BhRed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BhShapeIcon(BhShape.TRIANGLE, BhYellow, 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(if (anon) "CLIENT ${client.code} (ANONYMIZED)" else client.name,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(client.detail, style = MaterialTheme.typography.bodySmall, color = BhMuted)
                Text("${client.pendingCount} PENDING block${if (client.pendingCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { hidden = !hidden }) {
                Text(if (hidden) "REVEAL" else "ANONYMIZE")
            }
        }
    }
}

@Composable
fun BhFinanceScreen(repo: BauhausFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    val drafts = repo.remits.filter { it.branchId == branch.id }
    BhScreenColumn {
        BhScreenHead("FINANCE", "Remittance at ${branch.name}",
            "SESSION and PRODUCT drafts seal into snapshots. Undo lands within 48h with a reason. " +
                "Commission splits 60/40 practitioner/house, settled at snapshot.")
        for (r in drafts) {
            BhCard(accent = if (r.status == BhRemitStatus.SUBMITTED) BhBlue else BhYellow) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BhShapeIcon(BhShape.HALF, BhRed, 24.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${r.kind} - ${r.amount}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (r.status == BhRemitStatus.SUBMITTED) "SUBMITTED - ${r.snapshot}"
                        else "DRAFT - unsealed",
                            style = MaterialTheme.typography.bodySmall, color = BhMuted)
                    }
                }
                if (r.status == BhRemitStatus.DRAFT) {
                    Button(
                        onClick = { repo.submitRemit(r.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = BhBlue),
                    ) { Text("SUBMIT + SEAL SNAPSHOT") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            TextField(value = undoReason, onValueChange = { undoReason = it },
                                label = { Text("Undo reason (required, within 48h)") },
                                modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedButton(onClick = { repo.undoRemit(r.id, undoReason); undoReason = "" }) {
                            Text("UNDO")
                        }
                    }
                }
            }
        }
        if (drafts.isEmpty()) Text("No drafts at this block.", color = BhMuted)
        BhCard(accent = BhInk) {
            BhKicker("COMMISSION SPLIT")
            Text("60 practitioner / 40 house on SESSION amounts, settled when the snapshot seals. " +
                "PRODUCT amounts remit in full to the house.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BhTeamScreen(repo: BauhausFakeRepo) {
    BhScreenColumn {
        BhScreenHead("TEAM", "Users and roles",
            "Practitioner, Coordinator, MANAGER, Accountant, plus the locked ONBOARDING square.")
        for (user in repo.users) {
            if (user.onboarding) {
                BhCard(accent = BhYellow) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BhShapeIcon(BhShape.SQUARE, BhYellow, 24.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${user.name} - ONBOARDING (LOCKED)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Empty capability bundle. Grant a Practitioner role to unlock the grid.",
                                style = MaterialTheme.typography.bodySmall, color = BhMuted)
                        }
                        Button(
                            onClick = { repo.grantPractitioner() },
                            colors = ButtonDefaults.buttonColors(containerColor = BhRed),
                        ) { Text("GRANT PRACTITIONER") }
                    }
                }
            } else {
                BhCard {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BhShapeIcon(BhShape.CIRCLE, BhBlue, 22.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${user.name} - ${user.role}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Home ${repo.branchName(user.homeBranchId)} - " +
                                if (user.clockable()) "on shift" else "off shift",
                                style = MaterialTheme.typography.bodySmall, color = BhMuted)
                        }
                    }
                }
            }
        }
        BhCard(accent = BhBlue) {
            BhKicker("ROLE GLANCE", BhBlue)
            Text("MANAGER runs the board. Practitioners hold sessions. Coordinators place relief. " +
                "Accountants seal remittance.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun BhUser.clockable(): Boolean = clockedIn

@Composable
fun BhMailScreen(repo: BauhausFakeRepo) {
    BhScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                BhScreenHead("MAILBOX", "Notifications (${repo.unreadCount()} unread)",
                    "Tap a notice to flip read/unread. New relief and snapshot events land here.")
            }
            OutlinedButton(onClick = { repo.markAllRead() }) { Text("MARK ALL READ") }
        }
        for (n in repo.notices) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .border(3.dp, BhLine)
                    .background(if (n.read) BhCard else BhRemittedSoft)
                    .clickable { repo.toggleNotice(n.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BhShapeIcon(BhShape.CIRCLE, if (n.read) BhMuted else BhRed, 20.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(n.title + if (n.read) "" else " - UNREAD", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(n.body, style = MaterialTheme.typography.bodySmall, color = BhMuted)
                }
            }
        }
    }
}

@Composable
fun BhAuditScreen(repo: BauhausFakeRepo) {
    BhScreenColumn {
        BhScreenHead("AUDIT LOG", "Composition record",
            "Clock, relief, void/unvoid, and submit/undo events append with reasons.")
        BhCard(accent = BhInk) {
            for (entry in repo.audit) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(BhInk).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("-", color = BhYellow, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Text(entry, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun BhProfileScreen(repo: BauhausFakeRepo, onLogout: () -> Unit) {
    val branch = repo.currentBranch()
    BhScreenColumn {
        BhScreenHead("PROFILE", "Mara Villanueva - MANAGER",
            "Clock, flip the branch day, reset the demo, or step out of the composition.")
        BhCard(accent = BhRed) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BhShapeIcon(BhShape.ARCH, BhRed, 26.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (repo.meClockedIn.value) "Clocked in at ${branch.name}" else "Clocked out",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Home block Atelier Central.", style = MaterialTheme.typography.bodySmall, color = BhMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { repo.setClockedIn(branch.id, true) },
                    enabled = !repo.meClockedIn.value,
                    colors = ButtonDefaults.buttonColors(containerColor = BhBlue),
                ) { Text("CLOCK IN") }
                OutlinedButton(
                    onClick = { repo.setClockedIn(branch.id, false) },
                    enabled = repo.meClockedIn.value,
                ) { Text("CLOCK OUT") }
            }
        }
        BhCard(accent = BhBlue) {
            BhKicker("BRANCH DAY", BhBlue)
            Text("Current: ${branch.dayStatus}. Boundary 04:00 Asia/Manila.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in BhDayStatus.entries) {
                    BhPill(s.name, branch.dayStatus == s, s.blockColor()) {
                        repo.flipDayStatus(branch.id, s)
                    }
                }
            }
        }
        BhCard(accent = BhInk) {
            BhKicker("SESSION")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.resetDemo() }) { Text("RESET DEMO DATA") }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = BhRed),
                ) { Text("LOG OUT") }
            }
        }
    }
}
