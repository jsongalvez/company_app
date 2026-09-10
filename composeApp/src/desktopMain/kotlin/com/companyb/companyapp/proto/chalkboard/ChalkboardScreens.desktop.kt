package com.companyb.companyapp.proto.chalkboard

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
internal fun ChalkSmudgeDivider() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ChalkLine))
        Spacer(Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth(0.72f).height(1.dp).background(ChalkSmudge.copy(alpha = 0.55f)))
    }
}

@Composable
internal fun ChalkCard(
    modifier: Modifier = Modifier,
    tint: Color = ChalkBoard,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = ChalkShapes.medium,
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun ChalkTag(text: String, bg: Color, fg: Color = ChalkSlateDeep) {
    Box(
        modifier = Modifier.clip(ChalkShapes.small).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
internal fun ChalkTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = ChalkWhite)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = ChalkFaint)
        }
    }
}

@Composable
internal fun ChalkButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ChalkShapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = ChalkYellow, contentColor = ChalkSlateDeep),
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ChalkGhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = ChalkShapes.small) {
        Text(text = label, color = ChalkFaint)
    }
}

@Composable
internal fun ChalkField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = ChalkDim) },
        modifier = Modifier.fillMaxWidth(),
        shape = ChalkShapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ChalkWhite,
            unfocusedTextColor = ChalkWhite,
            focusedBorderColor = ChalkYellow,
            unfocusedBorderColor = ChalkLine,
        ),
    )
}

@Composable
internal fun ChalkEmpty(face: String, title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ChalkShapes.large,
        colors = CardDefaults.cardColors(containerColor = ChalkTray),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = face, fontSize = 40.sp, color = ChalkYellow)
            Spacer(Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = ChalkWhite)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = ChalkFaint)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                ChalkButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun ChalkLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Ms. Reyes") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "⌛", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text(text = "chalkboard", style = MaterialTheme.typography.headlineMedium, color = ChalkYellow)
        Spacer(Modifier.height(4.dp))
        Text(text = "The class-session wall. Chalk dust, warm lamps, fake registers only.", color = ChalkDim)
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(16.dp))
        ChalkCard(modifier = Modifier.width(480.dp), tint = ChalkBoard) {
            ChalkTitle("Take your seat", "Any email works — this register is make-believe.")
            Spacer(Modifier.height(14.dp))
            ChalkField(name, { name = it }, "Display name")
            Spacer(Modifier.height(10.dp))
            ChalkField(email, { email = it }, "Email")
            Spacer(Modifier.height(16.dp))
            ChalkButton("Chalk me in") { onLogin() }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOnboarding) { Text("Peek at the ONBOARDING slate", color = ChalkBlue) }
        }
    }
}

@Composable
internal fun ChalkOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp)) {
        ChalkTitle("ONBOARDING stays after class", "Locked slate: the role bundle is empty, so nothing derives yet.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(16.dp))
        ChalkEmpty(
            face = "◯",
            title = "No chalk for ONBOARDING yet",
            body = "A fresh register entry with zero capabilities — functionally locked out until MANAGE_USERS grants a real role.",
            actionLabel = "Back to the door",
            onAction = onBack,
        )
    }
}

@Composable
internal fun ChalkBranchSelect(repo: ChalkboardFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(40.dp)) {
        ChalkTitle("Choose your classroom", "Three branch rooms on the wall — pick where today is chalked.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { b ->
            ChalkCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    repo.branchId.value = b.id
                    onPick()
                },
                tint = ChalkBoard,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = b.name, style = MaterialTheme.typography.titleLarge, color = ChalkWhite)
                        Text(text = "${b.kind} · ${b.roomNote}", color = ChalkDim, fontSize = 13.sp)
                    }
                    ChalkTag(if (repo.branchId.value == b.id) "seated ✓" else "sit here >", ChalkYellow)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back", color = ChalkDim) }
    }
}

@Composable
internal fun ChalkDayBanner(repo: ChalkboardFakeRepo) {
    val status = repo.dayStatus.value
    val (bg, copy) = when (status) {
        ChalkDayStatus.OPEN -> ChalkMintSoft to "Branch Day OPEN — ${repo.branchName(repo.branchId.value)} is writing."
        ChalkDayStatus.PAST -> ChalkOrangeSoft to "Branch Day PAST — chalk settled, books sleepy."
        ChalkDayStatus.REMITTED -> ChalkBlueSoft to "Branch Day REMITTED — sealed under glass."
    }
    val fg = when (status) {
        ChalkDayStatus.OPEN -> ChalkMint
        ChalkDayStatus.PAST -> ChalkOrange
        ChalkDayStatus.REMITTED -> ChalkBlue
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ChalkShapes.medium,
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = copy, fontWeight = FontWeight.Bold, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Day boundary 04:00 Asia/Manila — the wall stays editable until 04:00 the next morning.",
                fontSize = 13.sp,
                color = ChalkFaint,
            )
        }
    }
}

@Composable
internal fun ChalkHome(repo: ChalkboardFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Homeroom", "Clock the time book, then mind the relief roster.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(12.dp))
        ChalkDayBanner(repo)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ChalkCard(modifier = Modifier.weight(1f), tint = ChalkBoard) {
                Text(text = "Time book", fontWeight = FontWeight.Bold, color = ChalkYellow)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (repo.clockedIn.value) "Chalked in at ${repo.branchName(repo.branchId.value)}" else "Chalked out — the slate misses you",
                    color = ChalkFaint,
                )
                Spacer(Modifier.height(10.dp))
                if (repo.clockedIn.value) {
                    ChalkGhostButton("Chalk out") { repo.clockToggle() }
                } else {
                    ChalkButton("Chalk in") { repo.clockToggle() }
                }
            }
            ChalkCard(modifier = Modifier.weight(1f), tint = ChalkBoard) {
                Text(text = "Today on the wall", fontWeight = FontWeight.Bold, color = ChalkYellow)
                Spacer(Modifier.height(6.dp))
                val mine = repo.sessions.filter { it.branchId == repo.branchId.value }
                Text(text = "${mine.count { it.status == ChalkSessionStatus.PENDING }} pending sittings", color = ChalkWhite)
                Text(text = "${mine.count { it.status == ChalkSessionStatus.COMPLETED }} finished lessons", color = ChalkFaint)
                Text(text = "${repo.relief.size} relief notes pinned", color = ChalkFaint)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChalkCard(tint = ChalkBoard) {
            Text(text = "Relief roster — duty · invite · request", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Clocking into a non-home branch starts view-only relief; an approved request or accepted invite writes the day grant.",
                fontSize = 13.sp,
                color = ChalkDim,
            )
            Spacer(Modifier.height(10.dp))
            ChalkField(reliefNote, { reliefNote = it }, "Pin a relief note (e.g. cover Period 1 Sat)")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChalkButton("Ask (request)") {
                    repo.addRelief("Request", reliefNote.ifBlank { "Needs cover" })
                    reliefNote = ""
                }
                ChalkGhostButton("Offer (invite)") {
                    repo.addRelief("Invite", reliefNote.ifBlank { "Extra hand offered" })
                    reliefNote = ""
                }
            }
            Spacer(Modifier.height(10.dp))
            ChalkSmudgeDivider()
            Spacer(Modifier.height(6.dp))
            if (repo.relief.isEmpty()) {
                Text(text = "Roster wiped clean — no relief notes.", color = ChalkDim)
            } else {
                repo.relief.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChalkTag(r.kind, ChalkBlueSoft, ChalkBlue)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${r.who} · ${r.branch} · ${r.day}", color = ChalkWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = r.note, color = ChalkFaint, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun chalkStatusTag(status: ChalkSessionStatus): Pair<String, Color> = when (status) {
    ChalkSessionStatus.PENDING -> "PENDING" to ChalkYellow
    ChalkSessionStatus.COMPLETED -> "COMPLETED" to ChalkMint
    ChalkSessionStatus.NO_SHOW -> "NO_SHOW" to ChalkOrange
    ChalkSessionStatus.CANCELLED -> "CANCELLED" to ChalkPink
}

@Composable
internal fun ChalkSessions(repo: ChalkboardFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidTarget by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Session register", "PENDING → COMPLETED / NO_SHOW / CANCELLED, chalked per pupil.")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Wall rule: walk-in sittings cannot be marked NO_SHOW or CANCELLED.",
            fontSize = 13.sp,
            color = ChalkOrange,
        )
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, ChalkSessionStatus.PENDING, ChalkSessionStatus.COMPLETED, ChalkSessionStatus.NO_SHOW, ChalkSessionStatus.CANCELLED).forEach { s ->
                val label = s?.name ?: "ALL"
                val on = filter == s?.name
                Box(
                    modifier = Modifier.clip(ChalkShapes.small)
                        .background(if (on) ChalkYellow else ChalkTray)
                        .clickable { filter = s?.name }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) ChalkSlateDeep else ChalkFaint)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = repo.sessions.filter { filter == null || it.status.name == filter }
        if (list.isEmpty()) {
            ChalkEmpty("—", "Slate wiped clean", "No sittings under this chalk mark.")
        } else {
            list.forEach { s ->
                val (label, color) = chalkStatusTag(s.status)
                ChalkCard(tint = ChalkBoard) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { expanded = if (expanded == s.id) null else s.id }) {
                            Text(text = "${s.clientName} · ${s.time}", fontWeight = FontWeight.Bold, color = ChalkWhite)
                            Text(text = "${s.type} · ₱${s.price}${if (s.walkIn) " · walk-in" else ""}", color = ChalkDim, fontSize = 13.sp)
                        }
                        ChalkTag(label, color.copy(alpha = 0.22f), color)
                    }
                    if (s.voided) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = "VOIDED — ${s.voidReason}", color = ChalkPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (expanded == s.id) {
                        Spacer(Modifier.height(8.dp))
                        ChalkSmudgeDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(text = "Sitting ${s.id} at ${repo.branchName(s.branchId)} — ${s.type}, ₱${s.price}.", color = ChalkFaint, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.status == ChalkSessionStatus.PENDING) {
                                ChalkButton("Complete") { repo.completeSession(s.id) }
                            }
                            if (s.voided) {
                                ChalkGhostButton("Unvoid") { repo.unvoidSession(s.id) }
                            } else {
                                ChalkGhostButton("Void…") { voidTarget = s.id; voidReason = "" }
                            }
                        }
                        if (voidTarget == s.id) {
                            Spacer(Modifier.height(8.dp))
                            ChalkField(voidReason, { voidReason = it }, "Void reason (chalk it down)")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChalkButton("Confirm void") {
                                    repo.voidSession(s.id, voidReason.ifBlank { "No reason chalked" })
                                    voidTarget = null
                                }
                                ChalkGhostButton("Cancel") { voidTarget = null }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        ChalkCard(tint = ChalkTray) {
            Text(text = "New walk-in at the door", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(8.dp))
            ChalkField(walkName, { walkName = it }, "Pupil name")
            Spacer(Modifier.height(8.dp))
            ChalkButton("Seat the walk-in") {
                repo.addWalkIn(walkName.ifBlank { "Hall wanderer" }, "Drop-in check", 500)
                walkName = ""
            }
        }
    }
}

@Composable
internal fun ChalkClients(repo: ChalkboardFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Pupil cards", "Client records are global — shared across every classroom.")
        Spacer(Modifier.height(4.dp))
        Text(text = "Ledger rule: a pupil holds at most one PENDING sitting at a time.", fontSize = 13.sp, color = ChalkBlue)
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        repo.clients.forEach { c ->
            ChalkCard(tint = ChalkBoard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "Pupil ${c.id.uppercase()} (anonymized)" else c.name,
                            fontWeight = FontWeight.Bold,
                            color = ChalkWhite,
                        )
                        Text(
                            text = if (c.anonymized) "Age + gender kept for the wall chart; PII wiped." else c.detail,
                            color = ChalkDim,
                            fontSize = 13.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (c.hasPending) ChalkTag("1 PENDING", ChalkYellowSoft, ChalkYellow)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { repo.toggleAnonymized(c.id) }) {
                            Text(if (c.anonymized) "Reveal" else "Anonymize", color = ChalkBlue)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun ChalkFinance(repo: ChalkboardFakeRepo) {
    var undoTarget by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Ledger & remittance", "Two chalk trays: SESSION takings and PRODUCT shelf.")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Submitting seals an immutable snapshot; Undo erases it within 48h with a reason. Product commissions pool per branch day and split equally among practitioners and coordinators chalked in at sold_at.",
            fontSize = 13.sp,
            color = ChalkDim,
        )
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        ChalkRemitKind.entries.forEach { kind ->
            Text(text = "$kind tray", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(8.dp))
            val items = repo.remittances.filter { it.kind == kind }
            if (items.isEmpty()) {
                ChalkEmpty("□", "Empty $kind tray", "No drafts chalked here yet.")
            } else {
                items.forEach { r ->
                    ChalkCard(tint = ChalkBoard) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Remittance ${r.id} · ₱${r.amount} · ${r.dayLabel}", fontWeight = FontWeight.Bold, color = ChalkWhite)
                                if (r.status == ChalkRemitStatus.SUBMITTED) {
                                    Text(text = "Sealed ${r.snapshot} · ${r.submittedAt}", color = ChalkMint, fontSize = 13.sp)
                                } else {
                                    Text(text = "Draft — chalk it, then seal it", color = ChalkDim, fontSize = 13.sp)
                                }
                            }
                            ChalkTag(r.status.name, if (r.status == ChalkRemitStatus.SUBMITTED) ChalkMint else ChalkYellow)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.status == ChalkRemitStatus.DRAFT) {
                                ChalkButton("Submit") { repo.submitRemittance(r.id) }
                            } else {
                                ChalkGhostButton("Undo (48h)…") { undoTarget = r.id; undoReason = "" }
                            }
                        }
                        if (undoTarget == r.id) {
                            Spacer(Modifier.height(8.dp))
                            ChalkField(undoReason, { undoReason = it }, "Undo reason (required)")
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChalkButton("Confirm undo") {
                                    repo.undoRemittance(r.id, undoReason.ifBlank { "Chalked in error" })
                                    undoTarget = null
                                }
                                ChalkGhostButton("Cancel") { undoTarget = null }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ChalkTeam(repo: ChalkboardFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Faculty wall", "Practitioners, coordinators, managers, accountants — and the locked enrollee.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        repo.users.forEach { u ->
            ChalkCard(tint = if (u.onboarding) ChalkTray else ChalkBoard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = ChalkWhite)
                        Text(text = "${u.role} · home ${repo.branchName(u.homeBranchId)}", color = ChalkDim, fontSize = 13.sp)
                    }
                    if (u.onboarding) {
                        ChalkTag("ONBOARDING · locked", ChalkPinkSoft, ChalkPink)
                    } else {
                        ChalkTag(u.role, ChalkBlueSoft, ChalkBlue)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        ChalkCard(tint = ChalkTray) {
            Text(text = "Role glance", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(4.dp))
            Text(text = "Practitioner logs sittings · Coordinator seals PAST ledgers · MANAGER adds faculty · Accountant reads every wall · ONBOARDING reads nothing.", color = ChalkFaint, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun ChalkMailbox(repo: ChalkboardFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChalkTitle("Pigeonholes", "Relief notes name their branch and day; tap to mark read.")
            Spacer(Modifier.weight(1f))
            ChalkGhostButton("Read all") { repo.markAllRead() }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = "${repo.notes.count { !it.read }} unread notes pinned", color = ChalkYellow, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        if (repo.notes.none { !it.read }) {
            ChalkEmpty("✉", "Pigeonholes empty", "Every note read — ring the bell, class dismissed.")
        }
        repo.notes.forEach { n ->
            ChalkCard(tint = if (n.read) ChalkBoard else ChalkTray) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, color = ChalkWhite)
                        Text(text = "${n.body} (${n.day})", color = ChalkFaint, fontSize = 13.sp)
                    }
                    if (!n.read) {
                        ChalkButton("Read") { repo.markRead(n.id) }
                    } else {
                        ChalkTag("read", ChalkTray, ChalkDim)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun ChalkAuditLog(repo: ChalkboardFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Chalk dust trail", "Every void, seal, undo, walk-in and clock-in leaves dust.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        repo.audits.forEach { a ->
            ChalkCard(tint = ChalkBoard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChalkTag(a.action, ChalkYellowSoft, ChalkYellow)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${a.record} · ${a.actor}", fontWeight = FontWeight.Bold, color = ChalkWhite, fontSize = 13.sp)
                        Text(text = "${a.whenText}${if (a.reason.isNotEmpty()) " — ${a.reason}" else ""}", color = ChalkDim, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ChalkProfile(repo: ChalkboardFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChalkTitle("Teacher profile", "Branch-day remote, time book, and the door.")
        Spacer(Modifier.height(8.dp))
        ChalkSmudgeDivider()
        Spacer(Modifier.height(10.dp))
        ChalkCard(tint = ChalkBoard) {
            Text(text = repo.me.value.name, fontWeight = FontWeight.Bold, color = ChalkWhite, fontSize = 18.sp)
            Text(text = "${repo.me.value.role} · home ${repo.branchName(repo.me.value.homeBranchId)}", color = ChalkDim)
            Spacer(Modifier.height(10.dp))
            Text(text = "Branch Day remote", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChalkDayStatus.entries.forEach { s ->
                    val on = repo.dayStatus.value == s
                    Box(
                        modifier = Modifier.clip(ChalkShapes.small)
                            .background(if (on) ChalkYellow else ChalkTray)
                            .clickable { repo.dayStatus.value = s }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = s.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) ChalkSlateDeep else ChalkFaint)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ChalkCard(tint = ChalkBoard) {
            Text(text = "Time book", fontWeight = FontWeight.Bold, color = ChalkYellow)
            Spacer(Modifier.height(6.dp))
            Text(text = if (repo.clockedIn.value) "Currently chalked in" else "Currently chalked out", color = ChalkFaint)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChalkGhostButton(if (repo.clockedIn.value) "Chalk out" else "Chalk in") { repo.clockToggle() }
                ChalkGhostButton("Reset demo") { repo.reset() }
            }
        }
        Spacer(Modifier.height(10.dp))
        ChalkButton("Leave the classroom (logout)") { onLogout() }
    }
}
