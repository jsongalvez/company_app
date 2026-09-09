package com.companyb.companyapp.proto.pastelplay

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PlayCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(22.dp)) { content() }
    }
}

@Composable
internal fun PlaySticker(text: String, bg: Color, fg: Color = PlayInk) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
internal fun PlayTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = PlayMuted)
        }
    }
}

@Composable
internal fun PlayCandyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PlayCandy, contentColor = Color.White),
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PlayEmpty(face: String, title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = PlayLilacSoft),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = face, fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = PlayInk)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PlayMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                PlayCandyButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun PlayLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Poppy Reyes") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "(˶ᵔᵕᵔ˶)", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(text = "pastel-play", style = MaterialTheme.typography.headlineMedium, color = PlayCandyDeep)
        Spacer(Modifier.height(4.dp))
        Text(text = "The candy-coated clinic playground. Fake smiles only!", color = PlayMuted)
        Spacer(Modifier.height(24.dp))
        PlayCard(modifier = Modifier.width(460.dp)) {
            PlayTitle("Hop in!", "Any email works, this is make-believe.")
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(16.dp))
            PlayCandyButton("Bounce me in!") { onLogin() }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOnboarding) { Text("Peek at the ONBOARDING welcome", color = PlayLilac) }
        }
    }
}

@Composable
internal fun PlayOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp)) {
        PlayTitle("ONBOARDING is still in the toy box", "Locked role: empty capability bundle, nothing to tap yet.")
        Spacer(Modifier.height(16.dp))
        PlayEmpty(
            face = "(っ- ‸ - ς)",
            title = "No toys for ONBOARDING yet",
            body = "This role ships with zero capabilities on purpose. Finish onboarding to unlock the playroom.",
            actionLabel = "Back to login",
            onAction = onBack,
        )
    }
}

@Composable
internal fun PlayBranchSelect(repo: PastelPlayFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(40.dp)) {
        PlayTitle("Pick your playroom", "Three pastel branches, one happy shift.")
        Spacer(Modifier.height(16.dp))
        val tints = listOf(PlayCandySoft, PlayMintSoft, PlayLilacSoft)
        repo.branches.forEachIndexed { i, b ->
            PlayCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    repo.branchId.value = b.id
                    onPick()
                },
                tint = tints[i % tints.size],
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = b.name, style = MaterialTheme.typography.titleLarge, color = PlayInk)
                        Text(text = "${b.kind} - ${b.colorNote}", color = PlayMuted)
                    }
                    PlaySticker(if (repo.branchId.value == b.id) "here!" else "play >", PlayCard)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back", color = PlayMuted) }
    }
}

@Composable
internal fun PlayDayBanner(repo: PastelPlayFakeRepo) {
    val status = repo.dayStatus.value
    val (bg, copy) = when (status) {
        PlayDayStatus.OPEN -> PlayMintSoft to "Branch Day is OPEN - ${repo.branchName(repo.branchId.value)} is bouncing!"
        PlayDayStatus.PAST -> PlayLemonSoft to "Branch Day is PAST - confetti settled, books are sleepy."
        PlayDayStatus.REMITTED -> PlaySkySoft to "Branch Day is REMITTED - sealed with a sticker."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaySticker(status.name, PlayCard)
                Spacer(Modifier.width(10.dp))
                Text(text = copy, fontWeight = FontWeight.Bold, color = PlayInk, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Branch Days flip at 04:00 Asia/Manila - night-owl entries still count for yesterday.",
                fontSize = 13.sp,
                color = PlayMuted,
            )
        }
    }
}

@Composable
internal fun PlayHome(repo: PastelPlayFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayDayBanner(repo)
        Spacer(Modifier.height(14.dp))
        PlayCard(modifier = Modifier.fillMaxWidth(), tint = PlayCandySoft) {
            PlayTitle(
                if (repo.clockedIn.value) "You are clocked in - yay!" else "Ready to clock in?",
                "${repo.me.value.name} @ ${repo.branchName(repo.branchId.value)}",
            )
            Spacer(Modifier.height(12.dp))
            Row {
                PlayCandyButton(if (repo.clockedIn.value) "Clock out" else "Clock in!") { repo.clockToggle() }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Request", reliefNote.ifEmpty { "Cover my giggles please" }) },
                    shape = RoundedCornerShape(24.dp),
                ) { Text("Ask for relief") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Invite", reliefNote.ifEmpty { "Come play with us!" }) },
                    shape = RoundedCornerShape(24.dp),
                ) { Text("Invite help") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = reliefNote,
                onValueChange = { reliefNote = it },
                label = { Text("Relief note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        PlayTitle("Relief board", "Requests, invites and duty - all candy, no meetings.")
        Spacer(Modifier.height(8.dp))
        if (repo.relief.isEmpty()) {
            PlayEmpty("( ´･･)ﾉ", "Board is squeaky clean", "No relief chatter. Ask for help and watch it pop!")
        } else {
            repo.relief.forEach { item ->
                val tint = when (item.kind) {
                    "Request" -> PlayLemonSoft
                    "Invite" -> PlaySkySoft
                    else -> PlayMintSoft
                }
                PlayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${item.kind}: ${item.who}", fontWeight = FontWeight.Bold, color = PlayInk)
                            Text(text = "${item.branch} - ${item.day}: ${item.note}", color = PlayMuted)
                        }
                        PlaySticker(item.day, PlayCard)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun PlaySessions(repo: PastelPlayFakeRepo) {
    var filter by remember { mutableStateOf<PlaySessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Sessions", "PENDING sparkles first. Walk-ins never sulk as NO_SHOW or CANCELLED.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "House rule: a walk-in stays PENDING or COMPLETED - walk-ins are never NO_SHOW or CANCELLED.",
            fontSize = 13.sp,
            color = PlayLilac,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val all: List<PlaySessionStatus?> = listOf(null) + PlaySessionStatus.entries
            all.forEach { f ->
                val label = f?.name ?: "ALL"
                val on = filter == f
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (on) PlayCandy else PlayCotton)
                        .clickable { filter = f }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label, fontWeight = FontWeight.Bold, color = if (on) Color.White else PlayCandyDeep)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PlayCard(modifier = Modifier.fillMaxWidth(), tint = PlayMintSoft) {
            PlayTitle("Fresh walk-in", "Smiles at the door become PENDING sessions.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = walkName,
                onValueChange = { walkName = it },
                label = { Text("Walk-in nickname") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            PlayCandyButton("Add walk-in (PENDING)") {
                repo.addWalkIn(walkName.ifEmpty { "Sunny Walk-in" }, "Checkup", 500)
                walkName = ""
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        if (list.isEmpty()) {
            PlayEmpty("(o˘◡˘o)", "No sessions in this pile", "Flip the filter stickers to find more playmates.")
        } else {
            list.forEach { s ->
                PlayCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${s.clientName} - ${s.type}", fontWeight = FontWeight.Bold, color = PlayInk)
                            Text(
                                text = "${repo.branchName(s.branchId)} - ${s.time} - P${s.price}" +
                                    (if (s.walkIn) " - walk-in" else "") +
                                    (if (s.voided) " - VOID: ${s.voidReason}" else ""),
                                color = PlayMuted,
                            )
                        }
                        PlaySticker(s.status.name, if (s.voided) PlayPeachSoft else PlayCotton)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                            Text(if (expanded == s.id) "Hide" else "Details", color = PlaySky)
                        }
                        if (s.status == PlaySessionStatus.PENDING && !s.voided) {
                            TextButton(onClick = { repo.completeSession(s.id) }) {
                                Text("Complete", color = PlayMint)
                            }
                        }
                        if (!s.voided) {
                            TextButton(onClick = { expanded = s.id }) { Text("Void...", color = PlayCandyDeep) }
                        } else {
                            TextButton(onClick = { repo.unvoidSession(s.id) }) {
                                Text("Unvoid", color = PlayLilac)
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
                            shape = RoundedCornerShape(20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        PlayCandyButton("Void with reason") {
                            repo.voidSession(s.id, reason.ifEmpty { "No reason, just vibes" })
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
internal fun PlayClients(repo: PastelPlayFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Clients", "One global sticker book for every branch.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Gentle rule: a client holds at most one PENDING session - finish or clear it before booking again.",
            fontSize = 13.sp,
            color = PlayLilac,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        repo.clients.forEach { c ->
            PlayCard(modifier = Modifier.fillMaxWidth(), tint = if (c.anonymized) PlayLilacSoft else PlayCard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "Anonymous cutie" else c.name,
                            fontWeight = FontWeight.Bold,
                            color = PlayInk,
                        )
                        Text(text = if (c.anonymized) "Hidden for the wallboard" else c.detail, color = PlayMuted)
                    }
                    if (c.hasPending) PlaySticker("1 PENDING", PlayLemonSoft)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { repo.toggleAnonymized(c.id) }) {
                    Text(if (c.anonymized) "Reveal" else "Anonymize", color = PlaySky)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun PlayFinance(repo: PastelPlayFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Finance sprinkles", "SESSION coins + PRODUCT goodies, sealed with snapshots.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Commission split note: every COMPLETED session splits Practitioner / Branch / Mission per the playbook.",
            fontSize = 13.sp,
            color = PlayLilac,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        PlayRemitKind.entries.forEach { kind ->
            val drafts = repo.remittances.filter { it.kind == kind && it.status == PlayRemitStatus.DRAFT }
            val sealed = repo.remittances.filter { it.kind == kind && it.status == PlayRemitStatus.SUBMITTED }
            PlayCard(
                modifier = Modifier.fillMaxWidth(),
                tint = if (kind == PlayRemitKind.SESSION) PlayCandySoft else PlaySkySoft,
            ) {
                Text(
                    text = if (kind == PlayRemitKind.SESSION) "SESSION jar (net income)" else "PRODUCT shelf (price x qty)",
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayInk,
                )
                Spacer(Modifier.height(8.dp))
                if (drafts.isEmpty()) {
                    Text("No drafts - tidy!", color = PlayMuted)
                } else {
                    drafts.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${r.id}: P${r.amount} - ${r.dayLabel}",
                                modifier = Modifier.weight(1f),
                                color = PlayInk,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(onClick = { repo.submitRemittance(r.id) }) {
                                Text("Submit", color = PlayMint)
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
                            color = PlayMuted,
                        )
                        PlaySticker("SEALED", PlayMintSoft, PlayMint)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PlayCard(modifier = Modifier.fillMaxWidth(), tint = PlayLemonSoft) {
            PlayTitle("Undo within 48h", "Sealed jars can pop back open with a reason, up to 48 hours.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = undoReason,
                onValueChange = { undoReason = it },
                label = { Text("Undo reason") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            val sealed = repo.remittances.filter { it.status == PlayRemitStatus.SUBMITTED }
            if (sealed.isEmpty()) {
                Text("Nothing sealed right now.", color = PlayMuted)
            } else {
                sealed.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${r.id} ${r.snapshot}", modifier = Modifier.weight(1f), color = PlayInk)
                        TextButton(onClick = {
                            repo.undoRemittance(r.id, undoReason.ifEmpty { "Oopsie, miscounted" })
                            undoReason = ""
                        }) { Text("Undo", color = PlayCandyDeep) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayTeam(repo: PastelPlayFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Playmates", "Every role gets a sticker: Practitioner, Coordinator, MANAGER, Accountant.")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Role glance: Practitioners play sessions, Coordinators run the board, MANAGERs seal days, Accountants count sprinkles.",
            fontSize = 13.sp,
            color = PlayLilac,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        repo.users.forEach { u ->
            val tint = if (u.onboarding) PlayLine else PlayMintSoft
            PlayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = PlayInk)
                        Text(text = "${u.role} @ ${repo.branchName(u.homeBranchId)}", color = PlayMuted)
                        if (u.onboarding) {
                            Text(
                                text = "Locked: empty capability bundle until onboarding completes.",
                                fontSize = 13.sp,
                                color = PlayCandyDeep,
                            )
                        }
                    }
                    PlaySticker(u.role, PlayCard)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun PlayMailbox(repo: PastelPlayFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Mailbox", "Love letters from the clinic.")
        Spacer(Modifier.height(4.dp))
        Row {
            val unread = repo.notes.count { !it.read }
            PlaySticker(if (unread == 0) "all read!" else "$unread unread", if (unread == 0) PlayMintSoft else PlayCandySoft)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { repo.markAllRead() }) { Text("Mark all read", color = PlaySky) }
        }
        Spacer(Modifier.height(12.dp))
        if (repo.notes.all { it.read }) {
            PlayEmpty("(つ◕‿◕)つ", "Mailbox zero, high five!", "Every letter has been hugged. New mail pops here.")
        }
        repo.notes.forEach { n ->
            PlayCard(modifier = Modifier.fillMaxWidth(), tint = if (n.read) PlayCard else PlayLemonSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, color = PlayInk)
                        Text(text = n.body, color = PlayMuted)
                        if (n.day.isNotEmpty()) Text(text = n.day, fontSize = 13.sp, color = PlayLilac)
                    }
                    if (!n.read) {
                        TextButton(onClick = { repo.markRead(n.id) }) { Text("Hug (read)", color = PlayMint) }
                    } else {
                        PlaySticker("read", PlayMintSoft, PlayMint)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun PlayAuditLog(repo: PastelPlayFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Audit confetti trail", "Every void, unvoid, submit and undo leaves glitter.")
        Spacer(Modifier.height(12.dp))
        if (repo.audits.isEmpty()) {
            PlayEmpty("(¬‿¬)", "No glitter yet", "Do something playful and it lands here.")
        } else {
            repo.audits.forEach { a ->
                PlayCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${a.action} - ${a.record}", fontWeight = FontWeight.Bold, color = PlayInk)
                            Text(text = "${a.actor} - ${a.whenText}", color = PlayMuted)
                            if (a.reason.isNotEmpty()) {
                                Text(text = "Why: ${a.reason}", fontSize = 13.sp, color = PlayLilac)
                            }
                        }
                        PlaySticker(a.action, PlayCotton)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun PlayProfile(repo: PastelPlayFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PlayTitle("Me: ${repo.me.value.name}", "Role ${repo.me.value.role} - professional playmate.")
        Spacer(Modifier.height(12.dp))
        PlayCard(modifier = Modifier.fillMaxWidth(), tint = PlayLilacSoft) {
            Text(text = "Branch Day remote control", style = MaterialTheme.typography.titleMedium, color = PlayInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayDayStatus.entries.forEach { s ->
                    val on = repo.dayStatus.value == s
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (on) PlayLilac else PlayCard)
                            .clickable { repo.dayStatus.value = s }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(s.name, fontWeight = FontWeight.Bold, color = if (on) Color.White else PlayLilac)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PlayCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Shift buttons", style = MaterialTheme.typography.titleMedium, color = PlayInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn.value) {
                    PlayCandyButton("Clock out") { repo.clockToggle() }
                } else {
                    Text(text = "Already clocked out - see you at playtime!", color = PlayMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.reset() }, shape = RoundedCornerShape(24.dp)) {
                    Text("Reset demo")
                }
                OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(24.dp)) { Text("Log out") }
            }
        }
    }
}
