package com.companyb.companyapp.proto.stickerbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun SbPage(title: String, blurb: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SbKicker("STICKER BOOK")
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(blurb, style = MaterialTheme.typography.bodyMedium, color = SbInkSoft)
        content()
    }
}

@Composable
fun SbHome(repo: StickerBookFakeRepo) {
    val branch = repo.currentBranch()
    var reliefNote by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    var inviteNote by remember { mutableStateOf("") }
    SbPage("Good morning, Mara", "Clock in, grab relief duty, and finish missions to earn stickers.") {
        StickerCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SbKicker("BRANCH DAY", SbSky)
                    Text("${branch.name} - ${branch.dayStatus}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Day rolls at 04:00 Asia/Manila. OPEN books earn, PAST days seal, REMITTED pages lock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SbInkSoft,
                    )
                }
                SbDaySeal(branch.dayStatus)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SbDayStatus.entries.forEach { s ->
                    SbPill(s.name, branch.dayStatus == s, SbSky) { repo.flipDayStatus(branch.id, s) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StickerCard(modifier = Modifier.weight(1f)) {
                SbKicker(if (repo.meClockedIn.value) "ON SHIFT" else "OFF SHIFT", SbLeaf)
                Text(
                    if (repo.meClockedIn.value) "Clocked in at ${branch.name}" else "Clocked out - paste yourself back in",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repo.setClockedIn(branch.id, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = SbLeaf),
                    ) { Text("CLOCK IN") }
                    OutlinedButton(onClick = { repo.setClockedIn(branch.id, false) }) { Text("CLOCK OUT") }
                }
                if (repo.meClockedIn.value) {
                    TextButton(onClick = { repo.earnSticker("early-bird", "Early Bird", branch.name) }) {
                        Text("Earn the Early Bird sticker")
                    }
                }
            }
            StickerCard(modifier = Modifier.weight(1f)) {
                SbKicker("MISSIONS", SbCoral)
                SbMissionRow("First clock-in", repo.meClockedIn.value, "clock-in", "Clock Star", repo, branch)
                SbMissionRow(
                    "A session hit COMPLETED",
                    repo.sessions.any { it.status == SbSessionStatus.COMPLETED },
                    "finisher", "Finisher", repo, branch,
                )
                SbMissionRow(
                    "A remittance got submitted",
                    repo.remits.any { it.status == SbRemitStatus.SUBMITTED },
                    "sealed", "Sealed Page", repo, branch,
                )
                SbMissionRow(
                    "Mailbox reads zero",
                    repo.unreadCount() == 0,
                    "mailbox-zero", "Clear Desk", repo, branch,
                )
            }
        }
        StickerCard {
            SbKicker("RELIEF DUTY BOARD", SbGrape)
            repo.reliefs.filter { it.branchId == branch.id }.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${r.asker}: ${r.note}", style = MaterialTheme.typography.bodyMedium)
                        Text(r.state.name, fontSize = 11.sp, color = SbMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SbPill("TAKE", r.state == SbReliefState.TAKEN, SbLeaf) { repo.takeRelief(r.id) }
                        SbPill("DROP", r.state == SbReliefState.RELEASED, SbCoral) { repo.releaseRelief(r.id) }
                    }
                }
            }
            if (repo.reliefs.none { it.branchId == branch.id }) {
                Text("No open duty on this page. Other pages: ${repo.reliefs.size} duties.", fontSize = 12.sp)
            }
            TextField(value = reliefNote, onValueChange = { reliefNote = it }, label = { Text("Need note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.addReliefRequest(branch.id, reliefNote); reliefNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = SbGrape),
            ) { Text("PASTE RELIEF REQUEST") }
            TextField(value = inviteName, onValueChange = { inviteName = it }, label = { Text("Invitee name") },
                modifier = Modifier.fillMaxWidth())
            TextField(value = inviteNote, onValueChange = { inviteNote = it }, label = { Text("Invite note") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.sendInvite(inviteName, branch.id, inviteNote); inviteName = ""; inviteNote = "" },
                colors = ButtonDefaults.buttonColors(containerColor = SbSky),
            ) { Text("STICK AN INVITE") }
            repo.invites.take(3).forEach { Text(it, fontSize = 12.sp, color = SbInkSoft) }
        }
    }
}

@Composable
private fun SbMissionRow(
    label: String,
    done: Boolean,
    stickerId: String,
    stickerTitle: String,
    repo: StickerBookFakeRepo,
    branch: SbBranch,
) {
    val earned = repo.stickers.any { it.id == stickerId }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((if (done) "[DONE] " else "[OPEN] ") + label, fontSize = 12.sp)
        when {
            earned -> SbStatusSticker("PASTED", SbLeaf)
            done -> SbPill("STICK IT", false, SbSun) { repo.earnSticker(stickerId, stickerTitle, branch.name) }
            else -> SbStatusSticker("LOCKED", SbMuted)
        }
    }
}

@Composable
fun SbSessions(repo: StickerBookFakeRepo) {
    val branch = repo.currentBranch()
    var filter by remember { mutableStateOf<SbSessionStatus?>(null) }
    var picked by remember { mutableStateOf("s-101") }
    var newType by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val list = repo.sessions.filter { it.branchId == branch.id && (filter == null || it.status == filter) }
    SbPage("Session stickers", "PENDING earns COMPLETED. Walk-ins skip NO_SHOW and CANCELLED.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SbPill("ALL", filter == null, SbSky) { filter = null }
            SbSessionStatus.entries.forEach { s ->
                SbPill(s.name, filter == s, SbSky) { filter = if (filter == s) null else s }
            }
        }
        list.forEach { s ->
            StickerCard(modifier = Modifier.fillMaxWidth().clickable { picked = s.id }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${s.id} - ${s.clientName}", style = MaterialTheme.typography.titleMedium)
                        Text("${s.type} - P${s.price}${if (s.walkIn) " - walk-in" else ""}", fontSize = 12.sp)
                        if (s.voided) Text("Voided: ${s.voidReason}", fontSize = 12.sp, color = SbCoralDeep)
                    }
                    SbStatusSticker(
                        if (s.voided) "VOID" else s.status.name,
                        if (s.voided) SbCoral else when (s.status) {
                            SbSessionStatus.PENDING -> SbSun
                            SbSessionStatus.COMPLETED -> SbLeaf
                            SbSessionStatus.NO_SHOW -> SbGrape
                            SbSessionStatus.CANCELLED -> SbMuted
                        },
                    )
                }
                if (picked == s.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SbSessionStatus.entries.forEach { st ->
                            val blocked = s.walkIn &&
                                (st == SbSessionStatus.NO_SHOW || st == SbSessionStatus.CANCELLED)
                            if (!blocked) SbPill(st.name, s.status == st, SbLeaf) { repo.setSessionStatus(s.id, st) }
                        }
                    }
                    if (s.walkIn) {
                        Text(
                            "Rule note: walk-ins never take NO_SHOW or CANCELLED, they just end.",
                            fontSize = 11.sp,
                            color = SbMuted,
                        )
                    }
                    TextField(value = reason, onValueChange = { reason = it }, label = { Text("Void reason") },
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repo.voidSession(s.id, reason); reason = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = SbCoral),
                        ) { Text("PEEL OFF (VOID)") }
                        OutlinedButton(onClick = { repo.unvoidSession(s.id) }) { Text("STICK BACK (UNVOID)") }
                    }
                }
            }
        }
        StickerCard {
            SbKicker("WALK-IN", SbSky)
            TextField(value = newType, onValueChange = { newType = it }, label = { Text("Session type") },
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { repo.bookSession(branch.id, newType); newType = "" },
                colors = ButtonDefaults.buttonColors(containerColor = SbSky),
            ) { Text("PASTE WALK-IN AS PENDING") }
        }
    }
}

@Composable
fun SbClients(repo: StickerBookFakeRepo) {
    SbPage("Client cards", "Clients are global across pages. At most one PENDING each keeps the book honest.") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (repo.anonymized.value) "Anonymized view: codes only" else "Full view: names + notes",
                style = MaterialTheme.typography.titleMedium,
            )
            SbPill(if (repo.anonymized.value) "SHOW NAMES" else "ANONYMIZE", repo.anonymized.value, SbGrape) {
                repo.anonymized.value = !repo.anonymized.value
            }
        }
        Text(
            "Note: a Client holds at most one PENDING Session. Extra bookings wait a turn.",
            fontSize = 12.sp,
            color = SbInkSoft,
        )
        repo.clients.forEach { c ->
            StickerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (repo.anonymized.value) "Client ${c.code}" else "${c.name} (${c.code})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!repo.anonymized.value) {
                            Text(c.detail, fontSize = 12.sp, color = SbInkSoft)
                        }
                    }
                    SbStatusSticker("${c.pendingCount} PENDING", if (c.pendingCount > 0) SbSun else SbLeaf)
                }
            }
        }
    }
}

@Composable
fun SbFinance(repo: StickerBookFakeRepo) {
    val branch = repo.currentBranch()
    var undoReason by remember { mutableStateOf("") }
    SbPage("Remittance pages", "SESSION and PRODUCT paste separately. Submit seals a snapshot. Undo peels within 48h.") {
        Text(
            "Commission split note: sealed pages split Branch share vs crew pool before payout.",
            fontSize = 12.sp,
            color = SbInkSoft,
        )
        SbRemitKind.entries.forEach { kind ->
            val drafts = repo.remits.filter { it.branchId == branch.id && it.kind == kind }
            StickerCard(modifier = Modifier.fillMaxWidth()) {
                SbKicker("$kind PAGE", SbLeaf)
                if (drafts.isEmpty()) Text("Nothing pasted here yet.", fontSize = 12.sp)
                drafts.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${r.id} - P${r.amount}", style = MaterialTheme.typography.titleMedium)
                            SbStatusSticker(
                                r.status.name,
                                if (r.status == SbRemitStatus.DRAFT) SbSun else SbLeaf,
                            )
                        }
                        if (r.snapshot.isNotBlank()) Text("Snapshot: ${r.snapshot}", fontSize = 12.sp)
                        if (r.undoReason.isNotBlank()) {
                            Text("Peeled: ${r.undoReason}", fontSize = 12.sp, color = SbCoralDeep)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.status == SbRemitStatus.DRAFT) {
                                Button(
                                    onClick = { repo.submitRemit(r.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SbLeaf),
                                ) { Text("SEAL (SUBMIT)") }
                            } else {
                                TextField(
                                    value = undoReason,
                                    onValueChange = { undoReason = it },
                                    label = { Text("Undo reason") },
                                    modifier = Modifier.width(280.dp),
                                )
                                OutlinedButton(onClick = { repo.undoRemit(r.id, undoReason); undoReason = "" }) {
                                    Text("PEEL (UNDO 48H)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SbTeam(repo: StickerBookFakeRepo) {
    SbPage("Crew badges", "Roles are badges. ONBOARDING pages stay blank until a MANAGER pastes the first badge.") {
        repo.users.forEach { u ->
            StickerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleMedium)
                        Text("Home page: ${repo.branchName(u.homeBranchId)}", fontSize = 12.sp, color = SbInkSoft)
                    }
                    SbStatusSticker(u.role, if (u.onboarding) SbGrape else SbSky)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (u.clockedIn) "On shift" else "Off shift",
                        fontSize = 12.sp,
                        color = SbInkSoft,
                    )
                    if (u.onboarding) {
                        SbPill("GRANT PRACTITIONER", false, SbLeaf) { repo.grantPractitioner() }
                    } else {
                        SbStatusSticker("BADGE EARNED", SbLeaf)
                    }
                }
            }
        }
        Text(
            "Capabilities ride on roles: Practitioner books, Coordinator seals pages, " +
                "Accountant reads money, MANAGER grants badges.",
            fontSize = 12.sp,
            color = SbInkSoft,
        )
    }
}

@Composable
fun SbMail(repo: StickerBookFakeRepo) {
    SbPage("Mailbox", "Tap a letter to flip read/unread. Clear the box to earn the Clear Desk sticker.") {
        Button(
            onClick = { repo.markAllRead() },
            colors = ButtonDefaults.buttonColors(containerColor = SbBubble),
        ) { Text("READ THEM ALL (${repo.unreadCount()} UNREAD)") }
        repo.notices.forEach { n ->
            StickerCard(
                modifier = Modifier.fillMaxWidth().clickable { repo.toggleNotice(n.id) },
                edge = if (n.read) Color.White else SbUnread,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(n.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SbStatusSticker(if (n.read) "READ" else "NEW", if (n.read) SbMuted else SbCoral)
                }
                Text(n.body, fontSize = 12.sp, color = SbInkSoft)
            }
        }
    }
}

@Composable
fun SbAudit(repo: StickerBookFakeRepo) {
    SbPage("Audit ribbon", "Every paste and peel lands on the ribbon, newest first.") {
        Button(
            onClick = { repo.resetDemo() },
            colors = ButtonDefaults.buttonColors(containerColor = SbInk),
        ) { Text("RESET DEMO BOOK") }
        repo.audit.forEach { line ->
            StickerCard(modifier = Modifier.fillMaxWidth()) {
                Text(line, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SbAlbum(repo: StickerBookFakeRepo, onLogout: () -> Unit) {
    val branch = repo.currentBranch()
    SbPage("My album + profile", "Stickers you earned, pride pages you filled, and the way out.") {
        StickerCard {
            SbKicker("MY STICKERS", SbCoral)
            if (repo.stickers.isEmpty()) {
                Text("Blank page. Finish a mission on HOME to paste your first sticker.", fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repo.stickers.forEach { s ->
                    BadgeStar(
                        s.title.split(" ").first(),
                        s.page.trim().take(8).uppercase(),
                        listOf(SbCoral, SbSky, SbLeaf, SbGrape, SbSun).random(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            repo.stickers.forEach { Text("${it.title} - ${it.page}", fontSize = 12.sp) }
        }
        StickerCard {
            SbKicker("BRANCH PRIDE", SbSun)
            repo.branches.forEach { b ->
                val done = repo.sessions.count { it.branchId == b.id && it.status == SbSessionStatus.COMPLETED }
                val sealed = repo.remits.count { it.branchId == b.id && it.status == SbRemitStatus.SUBMITTED }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${b.name} - ${b.pride}", style = MaterialTheme.typography.titleMedium)
                        SbDaySeal(b.dayStatus)
                    }
                    SbMeter("Completed sessions", done, 5, SbLeaf)
                    Text("$sealed remittance pages sealed", fontSize = 11.sp, color = SbInkSoft)
                }
            }
        }
        StickerCard {
            SbKicker("PROFILE", SbSky)
            Text("Mara Villanueva - MANAGER", style = MaterialTheme.typography.titleMedium)
            Text("Home page: ${repo.branchName("sunbeam")} - now viewing ${branch.name}", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.setClockedIn(branch.id, false) }) { Text("CLOCK OUT") }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = SbCoral),
                ) { Text("LOGOUT + CLOSE BOOK") }
            }
        }
    }
}
