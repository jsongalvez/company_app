package com.companyb.companyapp.proto.neurosoft

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SoftPanel(
    modifier: Modifier = Modifier,
    tint: Color = SoftRaised,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.shadow(10.dp, RoundedCornerShape(24.dp), clip = false),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftShadowLight.copy(alpha = 0.7f)),
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun SoftInset(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SoftPressed)
            .border(1.dp, SoftLine, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) { content() }
}

@Composable
internal fun SoftChip(text: String, bg: Color, fg: Color = SoftInk) {
    Box(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
internal fun SoftTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = SoftInk)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = SoftMuted)
        }
    }
}

@Composable
internal fun SoftRaisedButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SoftPrimary, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SoftToggle(checked: Boolean, onChecked: (Boolean) -> Unit, label: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (checked) SoftMintSoft else SoftPressed)
                .border(1.dp, SoftLine, RoundedCornerShape(20.dp))
                .clickable { onChecked(!checked) }
                .padding(4.dp)
                .width(52.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(4.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(if (checked) SoftMint else SoftRaised)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
            )
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, color = SoftMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SoftEmpty(face: String, title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    SoftPanel(modifier = Modifier.fillMaxWidth(), tint = SoftLilacSoft) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = face, fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = SoftInk)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = SoftMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SoftRaisedButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun SoftLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Maya Reyes") }
    var rememberMe by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SoftPanel(modifier = Modifier.size(84.dp), tint = SoftRaised) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "◍", fontSize = 40.sp, color = SoftPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = "neuro-soft", style = MaterialTheme.typography.headlineMedium, color = SoftPrimaryDeep)
        Spacer(Modifier.height(4.dp))
        Text(text = "Pastel extruded calm. Press softly, everything yields.", color = SoftMuted)
        Spacer(Modifier.height(22.dp))
        SoftPanel(modifier = Modifier.width(480.dp)) {
            SoftTitle("Press in", "Any email works, this is soft make-believe.")
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(12.dp))
            SoftInset(modifier = Modifier.fillMaxWidth()) {
                SoftToggle(checked = rememberMe, onChecked = { rememberMe = it }, label = "Stay softly signed in")
            }
            Spacer(Modifier.height(12.dp))
            SoftRaisedButton("Press to enter") { onLogin() }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOnboarding) { Text("Peek at the ONBOARDING cushion", color = SoftLilacDeep) }
        }
    }
}

@Composable
internal fun SoftOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp)) {
        SoftTitle("ONBOARDING rests on the cushion", "Locked role: empty capability bundle, nothing presses yet.")
        Spacer(Modifier.height(16.dp))
        SoftEmpty(
            face = "○",
            title = "No depth for ONBOARDING yet",
            body = "This role ships with zero capabilities on purpose. Finish onboarding to feel the panels rise.",
            actionLabel = "Back to login",
            onAction = onBack,
        )
    }
}

@Composable
internal fun SoftBranchSelect(repo: NeuroSoftFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(40.dp)) {
        SoftTitle("Choose your cushion", "Three pastel branches, one soft shift.")
        Spacer(Modifier.height(16.dp))
        val tints = listOf(SoftPrimarySoft, SoftMintSoft, SoftLilacSoft)
        repo.branches.forEachIndexed { i, b ->
            SoftPanel(
                modifier = Modifier.fillMaxWidth().clickable {
                    repo.branchId.value = b.id
                    onPick()
                },
                tint = tints[i % tints.size],
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = b.name, style = MaterialTheme.typography.titleLarge, color = SoftInk)
                        Text(text = "${b.kind} · ${b.softNote}", color = SoftMuted)
                    }
                    SoftChip(if (repo.branchId.value == b.id) "pressed" else "press >", SoftRaised)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back", color = SoftMuted) }
    }
}

@Composable
internal fun SoftDayBanner(repo: NeuroSoftFakeRepo) {
    val status = repo.dayStatus.value
    val (bg, copy) = when (status) {
        SoftDayStatus.OPEN -> SoftMintSoft to "Branch Day is OPEN — Cloud cushion is raised and editable."
        SoftDayStatus.PAST -> SoftButterSoft to "Branch Day is PAST — panels settled, edits rest."
        SoftDayStatus.REMITTED -> SoftSkySoft to "Branch Day is REMITTED — pressed flat under a snapshot."
    }
    SoftPanel(modifier = Modifier.fillMaxWidth(), tint = bg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoftChip(status.name, SoftRaised)
            Spacer(Modifier.width(10.dp))
            Text(text = copy, fontWeight = FontWeight.Bold, color = SoftInk, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Branch Days turn at 04:00 Asia/Manila — late-night presses still belong to yesterday.",
            fontSize = 12.sp,
            color = SoftMuted,
        )
    }
}

@Composable
internal fun SoftHome(repo: NeuroSoftFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    var softHum by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftDayBanner(repo)
        Spacer(Modifier.height(14.dp))
        SoftPanel(modifier = Modifier.fillMaxWidth(), tint = SoftPrimarySoft) {
            SoftTitle(
                if (repo.clockedIn.value) "You are pressed in — soft glow on" else "Ready to press in?",
                "${repo.me.value.name} @ ${repo.branchName(repo.branchId.value)}",
            )
            Spacer(Modifier.height(10.dp))
            SoftInset(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Tactile clock-in", fontWeight = FontWeight.Bold, color = SoftInk)
                        Text(text = "A soft press starts the shift hum.", fontSize = 12.sp, color = SoftMuted)
                    }
                    SoftToggle(checked = repo.clockedIn.value, onChecked = { repo.clockToggle() })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                SoftRaisedButton(if (repo.clockedIn.value) "Clock out" else "Clock in softly") { repo.clockToggle() }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Request", reliefNote.ifEmpty { "Cover my cushion please" }) },
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Ask for relief") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { repo.addRelief("Invite", reliefNote.ifEmpty { "Come press with us" }) },
                    shape = RoundedCornerShape(20.dp),
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
            Spacer(Modifier.height(8.dp))
            SoftToggle(checked = softHum, onChecked = { softHum = it }, label = "Calm depth hum (tactile feedback)")
        }
        Spacer(Modifier.height(14.dp))
        SoftTitle("Relief cushions", "Requests, invites and duty — all soft, no sharp edges.")
        Spacer(Modifier.height(8.dp))
        if (repo.relief.isEmpty()) {
            SoftEmpty("○", "Cushions are flat", "No relief chatter. Ask for help and watch one rise.")
        } else {
            repo.relief.forEach { item ->
                val tint = when (item.kind) {
                    "Request" -> SoftButterSoft
                    "Invite" -> SoftSkySoft
                    else -> SoftMintSoft
                }
                SoftPanel(modifier = Modifier.fillMaxWidth(), tint = tint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${item.kind}: ${item.who}", fontWeight = FontWeight.Bold, color = SoftInk)
                            Text(text = "${item.branch} · ${item.day}: ${item.note}", color = SoftMuted)
                        }
                        SoftChip(item.day, SoftRaised)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun SoftSessions(repo: NeuroSoftFakeRepo) {
    var filter by remember { mutableStateOf<SoftSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Sessions", "PENDING rises first. Walk-ins never dent as NO_SHOW or CANCELLED.")
        Spacer(Modifier.height(6.dp))
        SoftInset(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "House rule: a walk-in stays PENDING or COMPLETED — walk-ins are never NO_SHOW or CANCELLED.",
                fontSize = 12.sp,
                color = SoftLilacDeep,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val all: List<SoftSessionStatus?> = listOf(null) + SoftSessionStatus.entries
            all.forEach { f ->
                val label = f?.name ?: "ALL"
                val on = filter == f
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) SoftPrimary else SoftPressed)
                        .border(1.dp, SoftLine, RoundedCornerShape(16.dp))
                        .clickable { filter = f }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(label, fontWeight = FontWeight.Bold, color = if (on) Color.White else SoftPrimaryDeep)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftPanel(modifier = Modifier.fillMaxWidth(), tint = SoftMintSoft) {
            SoftTitle("Fresh walk-in", "Soft arrivals become PENDING cushions.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = walkName,
                onValueChange = { walkName = it },
                label = { Text("Walk-in nickname") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            SoftRaisedButton("Add walk-in (PENDING)") {
                repo.addWalkIn(walkName.ifEmpty { "Sunny Walk-in" }, "Checkup", 500)
                walkName = ""
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = repo.sessions.filter { filter == null || it.status == filter }
        if (list.isEmpty()) {
            SoftEmpty("○", "No cushions in this pile", "Flip the soft filters to find more sessions.")
        } else {
            list.forEach { s ->
                SoftPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${s.clientName} · ${s.type}", fontWeight = FontWeight.Bold, color = SoftInk)
                            Text(
                                text = "${repo.branchName(s.branchId)} · ${s.time} · P${s.price}" +
                                    (if (s.walkIn) " · walk-in" else "") +
                                    (if (s.voided) " · VOID: ${s.voidReason}" else ""),
                                color = SoftMuted,
                            )
                        }
                        SoftChip(s.status.name, if (s.voided) SoftPeachSoft else SoftPressed)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                            Text(if (expanded == s.id) "Hide" else "Details", color = SoftPrimaryDeep)
                        }
                        if (s.status == SoftSessionStatus.PENDING && !s.voided) {
                            TextButton(onClick = { repo.completeSession(s.id) }) {
                                Text("Complete", color = SoftMintDeep)
                            }
                        }
                        if (!s.voided) {
                            TextButton(onClick = { expanded = s.id }) { Text("Void…", color = SoftRose) }
                        } else {
                            TextButton(onClick = { repo.unvoidSession(s.id) }) {
                                Text("Unvoid", color = SoftLilacDeep)
                            }
                        }
                    }
                    if (expanded == s.id && !s.voided) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Void reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        SoftRaisedButton("Void with reason") {
                            repo.voidSession(s.id, reason.ifEmpty { "Pressed out gently" })
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
internal fun SoftClients(repo: NeuroSoftFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Clients", "One global cushion book for every branch.")
        Spacer(Modifier.height(6.dp))
        SoftInset(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Gentle rule: a client holds at most one PENDING session — settle it before pressing another.",
                fontSize = 12.sp,
                color = SoftLilacDeep,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        repo.clients.forEach { c ->
            SoftPanel(modifier = Modifier.fillMaxWidth(), tint = if (c.anonymized) SoftLilacSoft else SoftRaised) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "Anonymous cushion" else c.name,
                            fontWeight = FontWeight.Bold,
                            color = SoftInk,
                        )
                        Text(text = if (c.anonymized) "Hidden for the board" else c.detail, color = SoftMuted)
                    }
                    if (c.hasPending) SoftChip("1 PENDING", SoftButterSoft)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { repo.toggleAnonymized(c.id) }) {
                    Text(if (c.anonymized) "Reveal" else "Anonymize", color = SoftPrimaryDeep)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun SoftFinance(repo: NeuroSoftFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Finance cushions", "SESSION press + PRODUCT layers, sealed with snapshots.")
        Spacer(Modifier.height(6.dp))
        SoftInset(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Commission split note: product commissions pool per Branch Day and split equally among clocked-in practitioners and coordinators at sold_at time.",
                fontSize = 12.sp,
                color = SoftLilacDeep,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        SoftRemitKind.entries.forEach { kind ->
            val drafts = repo.remittances.filter { it.kind == kind && it.status == SoftRemitStatus.DRAFT }
            val sealed = repo.remittances.filter { it.kind == kind && it.status == SoftRemitStatus.SUBMITTED }
            SoftPanel(
                modifier = Modifier.fillMaxWidth(),
                tint = if (kind == SoftRemitKind.SESSION) SoftPrimarySoft else SoftSkySoft,
            ) {
                Text(
                    text = if (kind == SoftRemitKind.SESSION) "SESSION press (net income)" else "PRODUCT layer (price × qty)",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftInk,
                )
                Spacer(Modifier.height(8.dp))
                if (drafts.isEmpty()) {
                    Text("No drafts — flat and tidy.", color = SoftMuted)
                } else {
                    drafts.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${r.id}: P${r.amount} · ${r.dayLabel}",
                                modifier = Modifier.weight(1f),
                                color = SoftInk,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(onClick = { repo.submitRemittance(r.id) }) {
                                Text("Submit", color = SoftMintDeep)
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
                            color = SoftMuted,
                        )
                        SoftChip("SEALED", SoftMintSoft, SoftMintDeep)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        SoftPanel(modifier = Modifier.fillMaxWidth(), tint = SoftButterSoft) {
            SoftTitle("Undo within 48h", "Pressed seals can rise again with a reason, up to 48 hours.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = undoReason,
                onValueChange = { undoReason = it },
                label = { Text("Undo reason") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(8.dp))
            val sealed = repo.remittances.filter { it.status == SoftRemitStatus.SUBMITTED }
            if (sealed.isEmpty()) {
                Text("Nothing sealed right now.", color = SoftMuted)
            } else {
                sealed.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "${r.id} ${r.snapshot}", modifier = Modifier.weight(1f), color = SoftInk)
                        TextButton(onClick = {
                            repo.undoRemittance(r.id, undoReason.ifEmpty { "Miscounted softly" })
                            undoReason = ""
                        }) { Text("Undo", color = SoftRose) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SoftTeam(repo: NeuroSoftFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Soft crew", "Every role gets a cushion: Practitioner, Coordinator, MANAGER, Accountant.")
        Spacer(Modifier.height(6.dp))
        SoftInset(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Role glance: Practitioners press sessions, Coordinators smooth the day, MANAGERs seal cushions, Accountants count the layers.",
                fontSize = 12.sp,
                color = SoftLilacDeep,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        repo.users.forEach { u ->
            val tint = if (u.onboarding) SoftPressed else SoftMintSoft
            SoftPanel(modifier = Modifier.fillMaxWidth(), tint = tint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = SoftInk)
                        Text(text = "${u.role} @ ${repo.branchName(u.homeBranchId)}", color = SoftMuted)
                        if (u.onboarding) {
                            Text(
                                text = "Locked: empty capability bundle until onboarding completes.",
                                fontSize = 12.sp,
                                color = SoftRose,
                            )
                        }
                    }
                    SoftChip(u.role, SoftRaised)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun SoftMailbox(repo: NeuroSoftFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Mailbox", "Soft letters from the clinic.")
        Spacer(Modifier.height(4.dp))
        Row {
            val unread = repo.notes.count { !it.read }
            SoftChip(if (unread == 0) "all read" else "$unread unread", if (unread == 0) SoftMintSoft else SoftPrimarySoft)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { repo.markAllRead() }) { Text("Mark all read", color = SoftPrimaryDeep) }
        }
        Spacer(Modifier.height(12.dp))
        if (repo.notes.all { it.read }) {
            SoftEmpty("○", "Mailbox flat, soft high-five", "Every letter has been pressed. New mail rises here.")
        }
        repo.notes.forEach { n ->
            SoftPanel(modifier = Modifier.fillMaxWidth(), tint = if (n.read) SoftRaised else SoftButterSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, color = SoftInk)
                        Text(text = n.body, color = SoftMuted)
                        if (n.day.isNotEmpty()) Text(text = n.day, fontSize = 12.sp, color = SoftLilacDeep)
                    }
                    if (!n.read) {
                        TextButton(onClick = { repo.markRead(n.id) }) { Text("Press (read)", color = SoftMintDeep) }
                    } else {
                        SoftChip("read", SoftMintSoft, SoftMintDeep)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun SoftAuditLog(repo: NeuroSoftFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Audit soft trail", "Every press leaves a gentle dent.")
        Spacer(Modifier.height(12.dp))
        if (repo.audits.isEmpty()) {
            SoftEmpty("○", "No dents yet", "Press something softly and it lands here.")
        } else {
            repo.audits.forEach { a ->
                SoftPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${a.action} · ${a.record}", fontWeight = FontWeight.Bold, color = SoftInk)
                            Text(text = "${a.actor} · ${a.whenText}", color = SoftMuted)
                            if (a.reason.isNotEmpty()) {
                                Text(text = "Why: ${a.reason}", fontSize = 12.sp, color = SoftLilacDeep)
                            }
                        }
                        SoftChip(a.action, SoftPressed)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun SoftProfile(repo: NeuroSoftFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SoftTitle("Me: ${repo.me.value.name}", "Role ${repo.me.value.role} — soft practitioner.")
        Spacer(Modifier.height(12.dp))
        SoftPanel(modifier = Modifier.fillMaxWidth(), tint = SoftLilacSoft) {
            Text(text = "Branch Day press control", style = MaterialTheme.typography.titleMedium, color = SoftInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftDayStatus.entries.forEach { s ->
                    val on = repo.dayStatus.value == s
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) SoftLilac else SoftPressed)
                            .border(1.dp, SoftLine, RoundedCornerShape(16.dp))
                            .clickable { repo.dayStatus.value = s }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    ) {
                        Text(s.name, fontWeight = FontWeight.Bold, color = if (on) Color.White else SoftLilacDeep)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftPanel(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Shift cushions", style = MaterialTheme.typography.titleMedium, color = SoftInk)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn.value) {
                    SoftRaisedButton("Clock out") { repo.clockToggle() }
                } else {
                    Text(text = "Already flat — see you at soft hour.", color = SoftMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { repo.reset() }, shape = RoundedCornerShape(20.dp)) {
                    Text("Reset demo")
                }
                OutlinedButton(onClick = onLogout, shape = RoundedCornerShape(20.dp)) { Text("Log out") }
            }
        }
    }
}
