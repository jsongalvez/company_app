package com.companyb.companyapp.proto.blockbuilder

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun BbScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun BbScreenHead(kicker: String, title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BbKicker(kicker)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = BbMuted)
    }
}

@Composable
fun BbHomeScreen(repo: BlockBuilderFakeRepo) {
    var requestNote by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    var inviteNote by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    BbScreenColumn {
        BbScreenHead("SNAP-IN HOME", "Good stacking at ${branch.name}",
            "Clock bricks, relief stacks, invites, and requests click into one baseplate. Fake toy-box data only.")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            BbBrickCard(modifier = Modifier.weight(1f), brick = BbBrickRed, studs = 3) {
                BbKicker("MY CLICKER BRICK")
                Text(if (repo.meClockedIn.value) "SNAPPED IN" else "POPPED OFF",
                    style = MaterialTheme.typography.titleLarge)
                Text("Home plate: ${branch.name}. Clock bricks echo on the plate banner.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repo.setClockedIn(branch.id, true) },
                        enabled = !repo.meClockedIn.value,
                        colors = ButtonDefaults.buttonColors(containerColor = BbBrickBlue),
                    ) { Text("SNAP IN") }
                    OutlinedButton(
                        onClick = { repo.setClockedIn(branch.id, false) },
                        enabled = repo.meClockedIn.value,
                    ) { Text("POP OFF") }
                }
            }
            BbBrickCard(modifier = Modifier.weight(1f), brick = BbBrickBlue, studs = 3) {
                BbKicker("DAY STACK", BbBrickBlue)
                Text("${repo.sessions.count { it.branchId == branch.id && it.status == BbSessionStatus.PENDING }} " +
                    "loose - ${repo.sessions.count { it.branchId == branch.id &&
                        it.status == BbSessionStatus.COMPLETED }} clicked in",
                    style = MaterialTheme.typography.titleMedium)
                Text("Plate stacked ${branch.gross} of target ${branch.target}. ${branch.onShift} bricks on shift.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        BbBrickCard(brick = BbBrickYellow, studs = 5) {
            BbKicker("RELIEF STACK BOARD", BbBrickBlue)
            for (relief in repo.reliefs) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, BbInk, RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BbBrickSwatch(color = BbBrickYellow, deep = BbBrickYellowDeep, studs = 2)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${relief.asker} - ${repo.branchName(relief.branchId)}",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${relief.note} [${relief.state}]", style = MaterialTheme.typography.bodySmall)
                    }
                    if (relief.state == BbReliefState.OPEN) {
                        Button(
                            onClick = { repo.snapRelief(relief.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = BbBrickRed),
                        ) { Text("SNAP ON") }
                        OutlinedButton(onClick = { repo.unsnapRelief(relief.id) }) { Text("POP OFF") }
                    } else {
                        Text(relief.state.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BbMuted)
                    }
                }
            }
            Text("Stack a relief request", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextField(value = requestNote, onValueChange = { requestNote = it }, label = { Text("Need note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.addReliefRequest(branch.id, requestNote); requestNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = BbInk),
            ) { Text("STACK REQUEST") }
            Text("Snap a relief invite", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextField(value = inviteName, onValueChange = { inviteName = it }, label = { Text("Invitee name") },
                modifier = Modifier.fillMaxWidth())
            TextField(value = inviteNote, onValueChange = { inviteNote = it }, label = { Text("Invite note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.sendInvite(inviteName, branch.id, inviteNote); inviteName = ""; inviteNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = BbBrickBlue),
            ) { Text("SNAP INVITE") }
            for (line in repo.invites) {
                Text("- $line", style = MaterialTheme.typography.bodySmall, color = BbMuted)
            }
        }
    }
}

@Composable
fun BbSessionsScreen(repo: BlockBuilderFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var newType by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    val shown = repo.sessions.filter { it.branchId == branch.id && (filter == null || it.status.name == filter) }
    BbScreenColumn {
        BbScreenHead("SESSIONS", "Session bricks at ${branch.name}",
            "PENDING bricks click into COMPLETED, NO_SHOW, or CANCELLED. Walk-in bricks never take NO_SHOW or CANCELLED.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BbPill("ALL", filter == null, BbInk) { filter = null }
            for (s in BbSessionStatus.entries) {
                BbPill(s.name, filter == s.name, s.chip()) { filter = s.name }
            }
        }
        BbBrickCard(brick = BbBrickBlue, studs = 3) {
            BbKicker("STACK A WALK-IN", BbBrickBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextField(value = newType, onValueChange = { newType = it },
                        label = { Text("Brick type, e.g. Snap Visit 30") }, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = { repo.bookSession(branch.id, newType); newType = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = BbBrickRed),
                ) { Text("STACK PENDING") }
            }
        }
        for (s in shown) {
            BbSessionRow(repo = repo, session = s)
        }
        if (shown.isEmpty()) Text("No bricks under this filter.", color = BbMuted)
    }
}

@Composable
private fun BbSessionRow(repo: BlockBuilderFakeRepo, session: BbSession) {
    var reason by remember(session.id) { mutableStateOf("") }
    BbBrickCard(brick = session.status.chip(), studs = 2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbBrickSwatch(color = session.status.chip(), deep = BbInk, studs = if (session.walkIn) 1 else 2)
            Column(modifier = Modifier.weight(1f)) {
                Text("${session.id} - ${session.clientName}${if (session.walkIn) " (WALK-IN)" else ""}",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${session.type} - ${session.price}. Stacked ${session.status}" +
                    if (session.voided) " - POPPED OFF: ${session.voidReason}" else "",
                    style = MaterialTheme.typography.bodySmall, color = BbMuted)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(session.status.chip())
                .border(2.dp, BbInk, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(session.status.name, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = if (session.status == BbSessionStatus.CANCELLED) BbInk else Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (target in BbSessionStatus.entries) {
                if (target == session.status) continue
                if (session.walkIn && (target == BbSessionStatus.NO_SHOW || target == BbSessionStatus.CANCELLED)) {
                    continue
                }
                TextButton(onClick = { repo.setSessionStatus(session.id, target) }) { Text("CLICK TO ${target.name}") }
            }
        }
        if (session.walkIn) {
            Text("Walk-in rule: NO_SHOW and CANCELLED studs are missing on walk-in bricks.",
                style = MaterialTheme.typography.bodySmall, color = BbMuted)
        }
        if (!session.voided) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    TextField(value = reason, onValueChange = { reason = it },
                        label = { Text("Pop-off reason (required)") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = { repo.voidSession(session.id, reason) }) { Text("POP OFF") }
            }
        } else {
            OutlinedButton(onClick = { repo.unvoidSession(session.id) }) { Text("SNAP BACK ON") }
        }
    }
}

@Composable
fun BbClientsScreen(repo: BlockBuilderFakeRepo) {
    BbScreenColumn {
        BbScreenHead("CLIENTS", "Global brick pit",
            "Clients are global across plates. At most one PENDING brick per client. " +
                "Anonymized view keeps gender and age only.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbPill(if (repo.anonymized.value) "ANONYMIZED ON" else "ANONYMIZED OFF",
                repo.anonymized.value, BbBrickBlue) {
                repo.anonymized.value = !repo.anonymized.value
            }
        }
        for (client in repo.clients) {
            BbClientRow(repo = repo, client = client)
        }
    }
}

@Composable
private fun BbClientRow(repo: BlockBuilderFakeRepo, client: BbClient) {
    var hidden by remember(client.id) { mutableStateOf(false) }
    val anon = repo.anonymized.value || hidden
    BbBrickCard(brick = BbBrickYellow, studs = 2) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BbBrickSwatch(color = BbBrickRed, deep = BbBrickRedDeep, studs = 2)
            Column(modifier = Modifier.weight(1f)) {
                Text(if (anon) "BRICK ${client.code} (ANONYMIZED)" else client.name,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(client.detail, style = MaterialTheme.typography.bodySmall, color = BbMuted)
                Text("${client.pendingCount} PENDING brick${if (client.pendingCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { hidden = !hidden }) {
                Text(if (hidden) "REVEAL" else "ANONYMIZE")
            }
        }
    }
}

@Composable
fun BbFinanceScreen(repo: BlockBuilderFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    val branch = repo.currentBranch()
    val drafts = repo.remits.filter { it.branchId == branch.id }
    BbScreenColumn {
        BbScreenHead("FINANCE", "Cash bricks at ${branch.name}",
            "SESSION and PRODUCT bricks stack into sealed snapshots. Pop a stack off within 48h with a reason. " +
                "Commission splits 60/40 practitioner/house, settled when the snapshot clicks shut.")
        for (r in drafts) {
            BbBrickCard(brick = if (r.status == BbRemitStatus.SUBMITTED) BbBaseGreen else BbBrickYellow, studs = 3) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BbBrickSwatch(
                        color = if (r.kind == BbRemitKind.SESSION) BbBrickBlue else BbBrickRed,
                        deep = BbInk,
                        studs = 2,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${r.kind} - ${r.amount}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (r.status == BbRemitStatus.SUBMITTED) "SEALED - ${r.snapshot}"
                        else "LOOSE - not yet stacked",
                            style = MaterialTheme.typography.bodySmall, color = BbMuted)
                    }
                }
                if (r.status == BbRemitStatus.DRAFT) {
                    Button(
                        onClick = { repo.submitRemit(r.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = BbBrickBlue),
                    ) { Text("STACK + CLICK SNAPSHOT SHUT") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            TextField(value = undoReason, onValueChange = { undoReason = it },
                                label = { Text("Pop-off reason (required, within 48h)") },
                                modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedButton(onClick = { repo.undoRemit(r.id, undoReason); undoReason = "" }) {
                            Text("POP OFF (UNDO)")
                        }
                    }
                }
            }
        }
        if (drafts.isEmpty()) Text("No cash bricks on this plate.", color = BbMuted)
        BbBrickCard(brick = BbInk, studs = 2) {
            BbKicker("CLICK-SPLIT NOTE")
            Text("60 practitioner / 40 house on SESSION bricks, settled when the snapshot clicks shut. " +
                "PRODUCT bricks stack in full to the house.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BbTeamScreen(repo: BlockBuilderFakeRepo) {
    BbScreenColumn {
        BbScreenHead("TEAM", "Minifigs and roles",
            "Practitioner, Coordinator, MANAGER, Accountant, plus the loose ONBOARDING brick.")
        for (user in repo.users) {
            if (user.onboarding) {
                BbBrickCard(brick = BbBrickYellow, studs = 1) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BbBrickSwatch(color = BbBrickYellow, deep = BbBrickYellowDeep, studs = 1)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${user.name} - ONBOARDING (LOOSE BRICK)",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Empty capability bundle. Snap on a Practitioner role to join the build.",
                                style = MaterialTheme.typography.bodySmall, color = BbMuted)
                        }
                        Button(
                            onClick = { repo.grantPractitioner() },
                            colors = ButtonDefaults.buttonColors(containerColor = BbBrickRed),
                        ) { Text("SNAP ON PRACTITIONER") }
                    }
                }
            } else {
                BbBrickCard(brick = BbBrickBlue, studs = 2) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BbBrickSwatch(color = BbBrickBlue, deep = BbBrickBlueDeep, studs = 2)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${user.name} - ${user.role}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Home ${repo.branchName(user.homeBranchId)} - " +
                                if (user.clockedIn) "snapped in" else "popped off",
                                style = MaterialTheme.typography.bodySmall, color = BbMuted)
                        }
                    }
                }
            }
        }
        BbBrickCard(brick = BbBrickRed, studs = 3) {
            BbKicker("TRAY GUIDE", BbBrickBlue)
            Text("MANAGERs run the baseplate. Practitioners stack sessions. Coordinators place relief bricks. " +
                "Accountants click snapshots shut.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BbMailScreen(repo: BlockBuilderFakeRepo) {
    BbScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                BbScreenHead("BRICK MAIL", "Pigeonhole (${repo.unreadCount()} unclicked)",
                    "Click a brick note to flip read/unread. Relief and snapshot clicks land here.")
            }
            OutlinedButton(onClick = { repo.markAllRead() }) { Text("CLICK ALL READ") }
        }
        for (n in repo.notices) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(3.dp, BbInk, RoundedCornerShape(12.dp))
                    .background(if (n.read) Color.White else BbUnread)
                    .clickable { repo.toggleNotice(n.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BbBrickSwatch(
                    color = if (n.read) BbMuted else BbBrickRed,
                    deep = BbInk,
                    studs = if (n.read) 1 else 2,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(n.title + if (n.read) "" else " - UNCLICKED", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(n.body, style = MaterialTheme.typography.bodySmall, color = BbMuted)
                }
            }
        }
    }
}

@Composable
fun BbAuditScreen(repo: BlockBuilderFakeRepo) {
    BbScreenColumn {
        BbScreenHead("CLICK LOG", "Build record",
            "Snap-in, relief, pop-off/snap-back, and stack/undo clicks land here with reasons.")
        BbBrickCard(brick = BbInk, studs = 4) {
            for (entry in repo.audit) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(BbBrickYellow)
                        .padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("CLICK", color = BbInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Text(entry, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun BbProfileScreen(repo: BlockBuilderFakeRepo, onLogout: () -> Unit) {
    val branch = repo.currentBranch()
    BbScreenColumn {
        BbScreenHead("MY MINIFIG", "Mara Villanueva - MANAGER",
            "Snap in, swap the plate color, reset the toy box, or hop out.")
        BbBrickCard(brick = BbBrickRed, studs = 3) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BbBrickSwatch(color = BbBrickRed, deep = BbBrickRedDeep, studs = 2)
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (repo.meClockedIn.value) "Snapped in at ${branch.name}" else "Popped off",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Home plate Redbrick Row.", style = MaterialTheme.typography.bodySmall, color = BbMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { repo.setClockedIn(branch.id, true) },
                    enabled = !repo.meClockedIn.value,
                    colors = ButtonDefaults.buttonColors(containerColor = BbBrickBlue),
                ) { Text("SNAP IN") }
                OutlinedButton(
                    onClick = { repo.setClockedIn(branch.id, false) },
                    enabled = repo.meClockedIn.value,
                ) { Text("POP OFF (CLOCK OUT)") }
            }
        }
        BbBrickCard(brick = BbBrickBlue, studs = 3) {
            BbKicker("BUILD PLATE DAY", BbBrickBlue)
            Text("Current: ${branch.dayStatus}. Plates reset at the 04:00 Asia/Manila boundary.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in BbDayStatus.entries) {
                    BbPill(s.name, branch.dayStatus == s, s.brick()) {
                        repo.flipDayStatus(branch.id, s)
                    }
                }
            }
        }
        BbBrickCard(brick = BbBaseGreen, studs = 2) {
            BbKicker("TOY BOX LID", BbBaseGreen)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.resetDemo() }) { Text("RESET TOY BOX") }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = BbBrickRed),
                ) { Text("HOP OUT (LOG OUT)") }
            }
        }
    }
}
