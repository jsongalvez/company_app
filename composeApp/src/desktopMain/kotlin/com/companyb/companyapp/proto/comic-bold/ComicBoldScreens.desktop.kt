package com.companyb.companyapp.proto.comicbold

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
internal fun BoomCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = BoomShapes.medium,
        colors = CardDefaults.cardColors(containerColor = tint),
        border = BorderStroke(3.dp, BoomInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun BoomSticker(text: String, bg: Color, fg: Color = BoomInk) {
    Box(
        modifier = Modifier.border(3.dp, BoomInk).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = fg)
    }
}

@Composable
internal fun BoomTitle(title: String, subtitle: String = "") {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = "💬 $subtitle", style = MaterialTheme.typography.bodyMedium, color = BoomMuted)
        }
    }
}

@Composable
internal fun BoomPowButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = BoomShapes.small,
        border = BorderStroke(3.dp, BoomInk),
        colors = ButtonDefaults.buttonColors(containerColor = BoomPow, contentColor = Color.White),
    ) {
        Text(text = label.uppercase() + "!", fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun BoomActionStrip(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().border(3.dp, BoomInk).background(BoomPowSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = "/// $text", fontWeight = FontWeight.Black, color = BoomPowDeep)
    }
}

@Composable
internal fun BoomEmpty(face: String, title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BoomShapes.medium,
        colors = CardDefaults.cardColors(containerColor = BoomBurstSoft),
        border = BorderStroke(3.dp, BoomInk),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = face, fontSize = 44.sp)
            Spacer(Modifier.height(10.dp))
            Text(text = title.uppercase(), style = MaterialTheme.typography.titleLarge, color = BoomInk)
            Spacer(Modifier.height(6.dp))
            Text(text = "💬 $body", style = MaterialTheme.typography.bodyMedium, color = BoomMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BoomPowButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun BoomLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Dyna Reyes") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoomSticker("ISSUE #1", BoomBurst)
        Spacer(Modifier.height(10.dp))
        Text(text = "POW!", fontSize = 72.sp, fontWeight = FontWeight.Black, color = BoomPow)
        Text(text = "COMIC-BOLD DASHBOARD", style = MaterialTheme.typography.headlineSmall, color = BoomInk)
        Spacer(Modifier.height(4.dp))
        Text(text = "💬 The bold-comic clinic! Halftone heroes only, 100% fake!", color = BoomMuted)
        Spacer(Modifier.height(20.dp))
        BoomCard(modifier = Modifier.width(480.dp), tint = BoomBurstSoft) {
            BoomTitle("SPLASH IN!", "Any email works, this is make-believe.")
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Hero name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            BoomPowButton("Pow me in!") { onLogin() }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOnboarding) { Text("Peek at the ONBOARDING origin story", color = BoomZap) }
        }
    }
}

@Composable
internal fun BoomOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(44.dp)) {
        BoomActionStrip("KRAKOOM! ONBOARDING heroes start powerless!")
        Spacer(Modifier.height(12.dp))
        BoomTitle("ONBOARDING IS BENCHED!", "Locked role: empty capability bundle, nothing to tap yet.")
        Spacer(Modifier.height(16.dp))
        BoomEmpty(
            face = "💥",
            title = "No powers for ONBOARDING yet",
            body = "This role ships with zero capabilities on purpose. Finish onboarding to join the league!",
            actionLabel = "Back to splash",
            onAction = onBack,
        )
    }
}

@Composable
internal fun BoomBranchSelect(repo: ComicBoldFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(36.dp)) {
        BoomTitle("PICK YOUR ARENA!", "Three loud branches, one heroic shift.")
        Spacer(Modifier.height(16.dp))
        val tints = listOf(BoomBurstSoft, BoomZapSoft, BoomKapowSoft)
        repo.branches.forEachIndexed { i, b ->
            BoomCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    repo.branchId.value = b.id
                    onPick()
                },
                tint = tints[i % tints.size],
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "💬 ${b.name.uppercase()}", style = MaterialTheme.typography.titleLarge, color = BoomInk)
                        Text(text = "${b.kind} — ${b.powNote}", color = BoomMuted)
                    }
                    BoomSticker(if (repo.branchId.value == b.id) "HERE!" else "GO >", BoomBurst)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back", color = BoomMuted) }
    }
}

@Composable
internal fun BoomDayBanner(repo: ComicBoldFakeRepo) {
    val status = repo.dayStatus.value
    val (bg, copy) = when (status) {
        BoomDayStatus.OPEN -> BoomKapowSoft to "BRANCH DAY IS OPEN — ${repo.branchName(repo.branchId.value)} is kicking!"
        BoomDayStatus.PAST -> BoomOrangeSoft to "BRANCH DAY IS PAST — ink is dry, books are sleepy."
        BoomDayStatus.REMITTED -> BoomZapSoft to "BRANCH DAY IS REMITTED — sealed with a KRAKOOM!"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BoomShapes.medium,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(3.dp, BoomInk),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            BoomActionStrip("ACTION LINES! ${status.name}!")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoomSticker(status.name, BoomCard)
                Spacer(Modifier.width(10.dp))
                Text(text = copy, fontWeight = FontWeight.Black, color = BoomInk, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "💬 Branch Days flip at 04:00 Asia/Manila — night-owl entries still count for yesterday.",
                fontSize = 13.sp,
                color = BoomMuted,
            )
        }
    }
}

@Composable
internal fun BoomHome(repo: ComicBoldFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomDayBanner(repo)
        Spacer(Modifier.height(14.dp))
        BoomCard(modifier = Modifier.fillMaxWidth(), tint = BoomPowSoft) {
            BoomTitle(
                if (repo.clockedIn.value) "YOU ARE CLOCKED IN — ZAP!" else "READY TO CLOCK IN?",
                "${repo.me.value.name} @ ${repo.branchName(repo.branchId.value)}",
            )
            Spacer(Modifier.height(12.dp))
            Row {
                BoomPowButton(if (repo.clockedIn.value) "Clock out" else "Clock in!") { repo.clockToggle() }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Request", reliefNote.ifEmpty { "Cover my panel, please!" }) },
                ) { Text("Ask for relief") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Invite", reliefNote.ifEmpty { "Join my splash page!" }) },
                ) { Text("Invite help") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = reliefNote,
                onValueChange = { reliefNote = it },
                label = { Text("Relief note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        BoomTitle("RELIEF BOARD", "Requests, invites and duty — all ink, no meetings.")
        Spacer(Modifier.height(8.dp))
        if (repo.relief.isEmpty()) {
            BoomEmpty("💥", "Board is blank!", "No relief chatter. Shout for help and watch it pop!")
        } else {
            repo.relief.forEach { item ->
                val tint = when (item.kind) {
                    "Request" -> BoomOrangeSoft
                    "Invite" -> BoomZapSoft
                    else -> BoomKapowSoft
                }
                BoomCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${item.kind.uppercase()}: ${item.who}", fontWeight = FontWeight.Black, color = BoomInk)
                            Text(text = "💬 ${item.branch} — ${item.day}: ${item.note}", color = BoomMuted)
                        }
                        BoomSticker(item.day, BoomCard)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun BoomSessions(repo: ComicBoldFakeRepo) {
    var filter by remember { mutableStateOf<BoomSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("SESSIONS SMACKDOWN!", "PENDING panels punch first. Walk-ins never sulk as NO_SHOW or CANCELLED.")
        Spacer(Modifier.height(6.dp))
        BoomActionStrip("HOUSE RULE: a walk-in stays PENDING or COMPLETED — never NO_SHOW or CANCELLED!")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val all: List<BoomSessionStatus?> = listOf(null) + BoomSessionStatus.entries
            all.forEach { f ->
                val label = f?.name ?: "ALL"
                val on = filter == f
                Box(
                    modifier = Modifier.border(3.dp, BoomInk)
                        .background(if (on) BoomPow else BoomCard)
                        .clickable { filter = f }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, fontWeight = FontWeight.Black, color = if (on) Color.White else BoomPowDeep)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoomCard(modifier = Modifier.fillMaxWidth(), tint = BoomKapowSoft) {
            BoomTitle("FRESH WALK-IN — BAM!", "Strangers at the door become PENDING panels.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = walkName,
                onValueChange = { walkName = it },
                label = { Text("Walk-in hero name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BoomPowButton("Add walk-in (PENDING)") {
                repo.addWalkIn(walkName.ifEmpty { "Sunny Walk-in" }, "Checkup", 500)
                walkName = ""
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        if (list.isEmpty()) {
            BoomEmpty("💥", "No panels in this pile!", "Flip the filter bursts to find more heroes.")
        } else {
            list.forEach { s ->
                BoomCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${s.clientName} — ${s.type}".uppercase(), fontWeight = FontWeight.Black, color = BoomInk)
                            Text(
                                text = "${repo.branchName(s.branchId)} — ${s.time} — P${s.price}" +
                                    (if (s.walkIn) " — WALK-IN" else "") +
                                    (if (s.voided) " — VOID: ${s.voidReason}" else ""),
                                color = BoomMuted,
                            )
                        }
                        BoomSticker(s.status.name, if (s.voided) BoomOrangeSoft else BoomBurstSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                            Text(if (expanded == s.id) "Hide" else "Details", color = BoomZap)
                        }
                        if (s.status == BoomSessionStatus.PENDING && !s.voided) {
                            TextButton(onClick = { repo.completeSession(s.id) }) {
                                Text("Complete!", color = BoomKapow)
                            }
                        }
                        if (!s.voided) {
                            TextButton(onClick = { expanded = s.id }) { Text("Void...", color = BoomPowDeep) }
                        } else {
                            TextButton(onClick = { repo.unvoidSession(s.id) }) {
                                Text("Unvoid!", color = BoomPurple)
                            }
                        }
                    }
                    if (expanded == s.id && !s.voided) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Void reason (required!)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        BoomPowButton("Void with reason") {
                            repo.voidSession(s.id, reason.ifEmpty { "Plot twist!" })
                            reason = ""
                            expanded = null
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun BoomClients(repo: ComicBoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("CLIENT ROGUES' GALLERY!", "One global wall of faces for every branch.")
        Spacer(Modifier.height(6.dp))
        BoomActionStrip("GENTLE RULE: a client holds at most one PENDING session — finish it before booking again!")
        Spacer(Modifier.height(12.dp))
        repo.clients.forEach { c ->
            BoomCard(modifier = Modifier.fillMaxWidth(), tint = if (c.anonymized) BoomPurpleSoft else BoomCard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "??? MASKED MARVEL" else "💬 ${c.name.uppercase()}",
                            fontWeight = FontWeight.Black,
                            color = BoomInk,
                        )
                        Text(text = if (c.anonymized) "Hidden for the wallboard" else c.detail, color = BoomMuted)
                    }
                    if (c.hasPending) BoomSticker("1 PENDING", BoomBurst)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { repo.toggleAnonymized(c.id) }) {
                    Text(if (c.anonymized) "Unmask!" else "Mask!", color = BoomZap)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun BoomFinance(repo: ComicBoldFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("FINANCE FACE-OFF!", "SESSION coins + PRODUCT loot, sealed with snapshots.")
        Spacer(Modifier.height(6.dp))
        BoomActionStrip("COMMISSION SPLIT: every COMPLETED session splits Practitioner / Branch / Mission!")
        Spacer(Modifier.height(12.dp))
        BoomRemitKind.entries.forEach { kind ->
            val drafts = repo.remittances.filter { it.kind == kind && it.status == BoomRemitStatus.DRAFT }
            val sealed = repo.remittances.filter { it.kind == kind && it.status == BoomRemitStatus.SUBMITTED }
            BoomCard(
                modifier = Modifier.fillMaxWidth(),
                tint = if (kind == BoomRemitKind.SESSION) BoomPowSoft else BoomZapSoft,
            ) {
                Text(
                    text = if (kind == BoomRemitKind.SESSION) "SESSION VAULT (net income)" else "PRODUCT CRATE (price x qty)",
                    style = MaterialTheme.typography.titleMedium,
                    color = BoomInk,
                )
                Spacer(Modifier.height(8.dp))
                if (drafts.isEmpty()) {
                    Text("No drafts — tidy panels!", color = BoomMuted)
                } else {
                    drafts.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${r.id}: P${r.amount} — ${r.dayLabel}",
                                modifier = Modifier.weight(1f),
                                color = BoomInk,
                                fontWeight = FontWeight.Black,
                            )
                            TextButton(onClick = { repo.submitRemittance(r.id) }) {
                                Text("Submit!", color = BoomKapow)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                sealed.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${r.id}: P${r.amount} ${r.snapshot} @ ${r.submittedAt}",
                            modifier = Modifier.weight(1f),
                            color = BoomMuted,
                        )
                        BoomSticker("SEALED", BoomKapowSoft, BoomKapow)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        BoomCard(modifier = Modifier.fillMaxWidth(), tint = BoomBurstSoft) {
            BoomTitle("UNDO WITHIN 48H — ZOOM!", "Sealed vaults pop back open with a reason, up to 48 hours.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = undoReason,
                onValueChange = { undoReason = it },
                label = { Text("Undo reason") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val sealed = repo.remittances.filter { it.status == BoomRemitStatus.SUBMITTED }
            if (sealed.isEmpty()) {
                Text("Nothing sealed right now.", color = BoomMuted)
            } else {
                sealed.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${r.id} ${r.snapshot}", modifier = Modifier.weight(1f), color = BoomInk)
                        TextButton(onClick = {
                            repo.undoRemittance(r.id, undoReason.ifEmpty { "Oops — miscounted panels!" })
                            undoReason = ""
                        }) { Text("Undo!", color = BoomPowDeep) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoomTeam(repo: ComicBoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("HERO ROSTER!", "Every role gets a burst: Practitioner, Coordinator, MANAGER, Accountant.")
        Spacer(Modifier.height(6.dp))
        BoomActionStrip("ROLE GUIDE: Practitioners punch sessions, Coordinators run the board, MANAGERs seal days, Accountants count loot!")
        Spacer(Modifier.height(12.dp))
        repo.users.forEach { u ->
            val tint = if (u.onboarding) BoomPanel else BoomKapowSoft
            BoomCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name.uppercase(), fontWeight = FontWeight.Black, color = BoomInk)
                        Text(text = "${u.role} @ ${repo.branchName(u.homeBranchId)}", color = BoomMuted)
                        if (u.onboarding) {
                            Text(
                                text = "/// BENCHED: empty capability bundle until onboarding completes!",
                                fontSize = 13.sp,
                                color = BoomPowDeep,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    BoomSticker(u.role, BoomCard)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun BoomMailbox(repo: ComicBoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("SHOUT-O-GRAMS!", "Loud letters from HQ.")
        Spacer(Modifier.height(4.dp))
        Row {
            val unread = repo.notes.count { !it.read }
            BoomSticker(if (unread == 0) "ALL READ!" else "$unread UNREAD!", if (unread == 0) BoomKapowSoft else BoomPowSoft)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { repo.markAllRead() }) { Text("Read 'em all!", color = BoomZap) }
        }
        Spacer(Modifier.height(12.dp))
        if (repo.notes.all { it.read }) {
            BoomEmpty("💥", "Mailbox zero — WHAM!", "Every shout has been heard. New mail bursts in here.")
        }
        repo.notes.forEach { n ->
            BoomCard(modifier = Modifier.fillMaxWidth(), tint = if (n.read) BoomCard else BoomBurstSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title.uppercase(), fontWeight = FontWeight.Black, color = BoomInk)
                        Text(text = "💬 ${n.body}", color = BoomMuted)
                        if (n.day.isNotEmpty()) Text(text = n.day, fontSize = 13.sp, color = BoomPurple)
                    }
                    if (!n.read) {
                        TextButton(onClick = { repo.markRead(n.id) }) { Text("READ!", color = BoomKapow) }
                    } else {
                        BoomSticker("READ", BoomKapowSoft, BoomKapow)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun BoomAuditLog(repo: ComicBoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("KAPOW TRAIL!", "Every void, unvoid, submit and undo leaves ink splats.")
        Spacer(Modifier.height(12.dp))
        if (repo.audits.isEmpty()) {
            BoomEmpty("💥", "No ink yet!", "Do something heroic and it lands here.")
        } else {
            repo.audits.forEach { a ->
                BoomCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${a.action} — ${a.record}".uppercase(), fontWeight = FontWeight.Black, color = BoomInk)
                            Text(text = "${a.actor} — ${a.whenText}", color = BoomMuted)
                            if (a.reason.isNotEmpty()) {
                                Text(text = "💬 Why: ${a.reason}", fontSize = 13.sp, color = BoomPurple)
                            }
                        }
                        BoomSticker(a.action, BoomPanel)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun BoomProfile(repo: ComicBoldFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        BoomTitle("SECRET IDENTITY: ${repo.me.value.name.uppercase()}", "Role ${repo.me.value.role} — caped crusader.")
        Spacer(Modifier.height(12.dp))
        BoomCard(modifier = Modifier.fillMaxWidth(), tint = BoomPurpleSoft) {
            Text(text = "BRANCH DAY MASTER SWITCH", style = MaterialTheme.typography.titleMedium, color = BoomInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoomDayStatus.entries.forEach { s ->
                    val on = repo.dayStatus.value == s
                    Box(
                        modifier = Modifier.border(3.dp, BoomInk)
                            .background(if (on) BoomPurple else BoomCard)
                            .clickable { repo.dayStatus.value = s }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(s.name, fontWeight = FontWeight.Black, color = if (on) Color.White else BoomPurple)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoomCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "HERO CONTROLS", style = MaterialTheme.typography.titleMedium, color = BoomInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn.value) {
                    BoomPowButton("Clock out") { repo.clockToggle() }
                } else {
                    Text(text = "Already clocked out — see you next issue!", color = BoomMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.reset() }) {
                    Text("Reset demo")
                }
                OutlinedButton(onClick = onLogout) { Text("Log out") }
            }
        }
    }
}
