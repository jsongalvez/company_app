package com.companyb.companyapp.proto.decogold

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GoldRule() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoldPrimary))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoldLineBright))
    }
}

@Composable
internal fun GoldMarquee(
    title: String,
    subtitle: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GoldPanel)
                .border(width = 1.dp, color = GoldLine)
                .padding(vertical = 14.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "◆ ◆ ◆", fontSize = 11.sp, color = GoldPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = GoldBright,
        )
        Text(text = subtitle.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldMuted)
        Spacer(Modifier.height(8.dp))
        GoldRule()
    }
}

@Composable
internal fun GoldPanel(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GoldPanel)
                .border(width = 1.dp, color = GoldLine)
                .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
internal fun GoldTitle(
    title: String,
    subtitle: String = "",
) {
    Column {
        Text(text = "◆ $title ◆", style = MaterialTheme.typography.titleLarge, color = GoldBright)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = GoldMuted)
        }
    }
}

@Composable
internal fun GoldNote(text: String) {
    Text(text = "❖ $text", fontSize = 13.sp, color = GoldMuted)
}

@Composable
internal fun GoldPlaque(
    text: String,
    accent: Color = GoldPrimary,
) {
    Box(
        modifier =
            Modifier
                .background(GoldSoft)
                .border(width = 1.dp, color = accent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldBright)
    }
}

@Composable
internal fun GoldButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = GoldShapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = GoldObsidian),
    ) {
        Text(text = label.uppercase(), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun GoldGhost(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = GoldShapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldLineBright),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldBright),
    ) {
        Text(text = label.uppercase(), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun GoldField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = GoldMuted) },
        modifier = Modifier.fillMaxWidth(),
        shape = GoldShapes.small,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = GoldCream,
                unfocusedTextColor = GoldCream,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = GoldLine,
                cursorColor = GoldPrimary,
            ),
    )
}

@Composable
internal fun GoldEmpty(
    title: String,
    body: String,
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GoldPanel)
                .border(width = 1.dp, color = GoldLine)
                .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "◇ ◆ ◇", fontSize = 28.sp, color = GoldPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = GoldBright)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = GoldMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                GoldButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun GoldLogin(
    onLogin: () -> Unit,
    onOnboarding: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Aurelia Santos") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "◆ ◆ ◆", fontSize = 14.sp, color = GoldPrimary)
        Spacer(Modifier.height(12.dp))
        Text(text = "THE GRAND HALL", style = MaterialTheme.typography.headlineMedium, color = GoldBright)
        Spacer(Modifier.height(4.dp))
        Text(text = "A black-gold house of appointments. Fiction only, no ledger leaves the hall.", color = GoldMuted)
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.width(480.dp)) {
            GoldPanel {
                GoldTitle("Present your card", "Any name and email opens the brass doors.")
                Spacer(Modifier.height(6.dp))
                GoldField(name, { name = it }, "Display name")
                Spacer(Modifier.height(4.dp))
                GoldField(email, { email = it }, "Email")
                Spacer(Modifier.height(8.dp))
                GoldButton("Enter the hall") { onLogin() }
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onOnboarding) { Text("View the ONBOARDING chamber", color = GoldBright) }
            }
        }
    }
}

@Composable
internal fun GoldOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(48.dp)) {
        GoldTitle("The ONBOARDING chamber is sealed", "Locked role: an empty capability bundle, nothing to touch yet.")
        Spacer(Modifier.height(16.dp))
        GoldEmpty(
            title = "No gilding for ONBOARDING yet",
            body = "This role carries zero capabilities by design. Complete onboarding to take a seat in the hall.",
            actionLabel = "Back to the doors",
            onAction = onBack,
        )
    }
}

@Composable
internal fun GoldBranchSelect(
    repo: DecoGoldFakeRepo,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(40.dp)) {
        GoldTitle("Choose your hall", "Three houses, one evening program.")
        Spacer(Modifier.height(16.dp))
        repo.branches.forEach { b ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(GoldPanel)
                        .border(width = 1.dp, color = if (repo.branchId.value == b.id) GoldPrimary else GoldLine)
                        .clickable {
                            repo.branchId.value = b.id
                            onPick()
                        }.padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = b.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = GoldBright)
                        Text(text = "${b.kind} — ${b.hallNote}", color = GoldMuted)
                    }
                    GoldPlaque(if (repo.branchId.value == b.id) "Seated" else "Enter")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onBack) { Text("Back", color = GoldMuted) }
    }
}

@Composable
internal fun GoldDayBanner(repo: DecoGoldFakeRepo) {
    val status = repo.dayStatus.value
    val copy =
        when (status) {
            GoldDayStatus.OPEN -> "The Branch Day is OPEN — ${repo.branchName(repo.branchId.value)} receives patrons."
            GoldDayStatus.PAST -> "The Branch Day is PAST — the books rest, the chandeliers dim."
            GoldDayStatus.REMITTED -> "The Branch Day is REMITTED — sealed under the house stamp."
        }
    GoldPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoldPlaque(status.name)
            Spacer(Modifier.width(10.dp))
            Text(text = copy, fontWeight = FontWeight.Bold, color = GoldCream, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
        GoldNote("Branch Days turn at 04:00 Asia/Manila — late-night entries still belong to yesterday.")
    }
}

@Composable
internal fun GoldHome(repo: DecoGoldFakeRepo) {
    var reliefNote by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldDayBanner(repo)
        Spacer(Modifier.height(14.dp))
        GoldPanel {
            GoldTitle(
                if (repo.clockedIn.value) "You hold the floor" else "Take your post",
                "${repo.me.value.name} · ${repo.branchName(repo.branchId.value)}",
            )
            Spacer(Modifier.height(4.dp))
            Row {
                GoldButton(if (repo.clockedIn.value) "Clock out" else "Clock in") { repo.clockToggle() }
                Spacer(Modifier.width(10.dp))
                GoldGhost("Request relief") {
                    repo.addRelief("Request", reliefNote.ifEmpty { "Cover the floor, please" })
                }
                Spacer(Modifier.width(10.dp))
                GoldGhost("Invite relief") {
                    repo.addRelief("Invite", reliefNote.ifEmpty { "The hall needs one more chair" })
                }
            }
            Spacer(Modifier.height(4.dp))
            GoldField(reliefNote, { reliefNote = it }, "Relief note (optional)")
        }
        Spacer(Modifier.height(14.dp))
        GoldTitle("Relief board", "Requests, invites and duty — who stands where, and when.")
        Spacer(Modifier.height(8.dp))
        if (repo.relief.isEmpty()) {
            GoldEmpty(title = "A quiet board", body = "No relief posted. The hall stands fully crewed.")
        } else {
            repo.relief.forEach { r ->
                GoldPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoldPlaque(r.kind)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${r.who} — ${r.branch}", fontWeight = FontWeight.Bold, color = GoldCream)
                            Text(text = "${r.day} · ${r.note}", color = GoldMuted, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun GoldSessions(repo: DecoGoldFakeRepo) {
    var filter by remember { mutableStateOf<GoldSessionStatus?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var walkName by remember { mutableStateOf("") }
    var walkType by remember { mutableStateOf("Checkup") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("Evening program", "Sessions across PENDING, COMPLETED, NO_SHOW and CANCELLED.")
        Spacer(Modifier.height(4.dp))
        GoldNote("House rule: walk-ins stay PENDING until served — a walk-in is never marked NO_SHOW or CANCELLED.")
        Spacer(Modifier.height(10.dp))
        Row {
            val tabs = listOf(null to "ALL") + GoldSessionStatus.entries.map { it to it.name }
            tabs.forEach { (v, label) ->
                Box(
                    modifier =
                        Modifier
                            .background(if (filter == v) GoldSoft else Color.Transparent)
                            .border(1.dp, if (filter == v) GoldPrimary else GoldLine)
                            .clickable { filter = v }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (filter == v) GoldBright else GoldMuted,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        val shown = repo.sessions.filter { filter == null || it.status == filter }
        if (shown.isEmpty()) {
            GoldEmpty(
                title = "No entries on this page",
                body = "The program lists nothing under this seal. Try another filter.",
            )
        } else {
            shown.forEach { s ->
                GoldPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${s.time} · ${s.clientName}", fontWeight = FontWeight.Bold, color = GoldCream)
                            Text(
                                text =
                                    "${s.type} · ₱${s.price}" +
                                        (if (s.walkIn) " · WALK-IN" else "") +
                                        (if (s.voided) " · VOIDED" else ""),
                                color = GoldMuted,
                                fontSize = 13.sp,
                            )
                        }
                        GoldPlaque(s.status.name)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                            Text(if (expanded == s.id) "FOLD" else "OPEN", color = GoldBright)
                        }
                    }
                    if (expanded == s.id) {
                        Spacer(Modifier.height(8.dp))
                        GoldRule()
                        Spacer(Modifier.height(8.dp))
                        if (s.voided) GoldNote("Void reason on record: ${s.voidReason}")
                        Row {
                            if (!s.voided && s.status == GoldSessionStatus.PENDING) {
                                GoldButton("Complete") { repo.completeSession(s.id) }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (!s.voided) {
                                GoldGhost("Void") {
                                    repo.voidSession(s.id, voidReason.ifEmpty { "Sealed off the program" })
                                    voidReason = ""
                                }
                            } else {
                                GoldGhost("Unvoid") { repo.unvoidSession(s.id) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        GoldField(voidReason, { voidReason = it }, "Void reason (written to the audit log)")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        GoldPanel {
            GoldTitle("Admit a walk-in", "Walk-ins join as PENDING at the current branch.")
            Spacer(Modifier.height(4.dp))
            GoldField(walkName, { walkName = it }, "Patron name")
            Spacer(Modifier.height(4.dp))
            GoldField(walkType, { walkType = it }, "Service type")
            Spacer(Modifier.height(4.dp))
            GoldButton("Seat walk-in") {
                if (walkName.isNotBlank()) {
                    repo.addWalkIn(walkName.trim(), walkType.trim().ifEmpty { "Checkup" }, 500)
                    walkName = ""
                }
            }
        }
    }
}

@Composable
internal fun GoldClients(repo: DecoGoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("Registry of patrons", "Clients are global across halls — one book, many doors.")
        Spacer(Modifier.height(4.dp))
        GoldNote("House rule: a patron holds at most one PENDING session — the desk refuses a second booking.")
        Spacer(Modifier.height(10.dp))
        repo.clients.forEach { c ->
            GoldPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (c.anonymized) "◆◆◆ Anonymized ◆◆◆" else c.name,
                            fontWeight = FontWeight.Bold,
                            color = GoldCream,
                        )
                        Text(text = c.detail, color = GoldMuted, fontSize = 13.sp)
                        if (c.hasPending) {
                            Spacer(Modifier.height(2.dp))
                            Text(text = "❖ Holds one PENDING session", fontSize = 12.sp, color = GoldBright)
                        }
                    }
                    GoldGhost(if (c.anonymized) "Reveal" else "Anonymize") { repo.toggleAnonymized(c.id) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun GoldFinance(repo: DecoGoldFakeRepo) {
    var undoReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("House coffers", "SESSION and PRODUCT remittances, sealed by snapshot.")
        Spacer(Modifier.height(4.dp))
        GoldNote("Undo stands 48 hours after submit and demands a written reason for the audit log.")
        GoldNote("Commission split: practitioner and house shares are computed at submit and frozen in the snapshot.")
        Spacer(Modifier.height(10.dp))
        GoldRemitKind.entries.forEach { kind ->
            Text(text = "◆ ${kind.name} LEDGER ◆", fontWeight = FontWeight.Bold, color = GoldPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            val rows = repo.remittances.filter { it.kind == kind }
            if (rows.isEmpty()) {
                GoldEmpty(title = "An empty ledger", body = "No $kind drafts on the desk.")
            } else {
                rows.forEach { r ->
                    GoldPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${r.id.uppercase()} · ₱${r.amount} · ${r.dayLabel}",
                                    fontWeight = FontWeight.Bold,
                                    color = GoldCream,
                                )
                                Text(
                                    text =
                                        if (r.status == GoldRemitStatus.SUBMITTED) {
                                            "Sealed ${r.snapshot} · ${r.submittedAt}"
                                        } else {
                                            "Draft — awaiting the house stamp"
                                        },
                                    color = GoldMuted,
                                    fontSize = 13.sp,
                                )
                            }
                            GoldPlaque(r.status.name)
                            Spacer(Modifier.width(8.dp))
                            if (r.status == GoldRemitStatus.DRAFT) {
                                GoldButton("Submit") { repo.submitRemittance(r.id) }
                            } else {
                                GoldGhost("Undo") {
                                    repo.undoRemittance(r.id, undoReason.ifEmpty { "Recount ordered by the house" })
                                    undoReason = ""
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        GoldPanel {
            GoldTitle("Undo with cause", "One reason line, written beside every reopened seal.")
            Spacer(Modifier.height(4.dp))
            GoldField(undoReason, { undoReason = it }, "Undo reason")
        }
    }
}

@Composable
internal fun GoldTeam(repo: DecoGoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("The company", "Practitioners, coordinators, managers, accountants — and the sealed ONBOARDING row.")
        Spacer(Modifier.height(10.dp))
        repo.users.forEach { u ->
            GoldPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = GoldCream)
                        Text(
                            text = "${u.role} · ${repo.branchName(u.homeBranchId)}",
                            color = GoldMuted,
                            fontSize = 13.sp,
                        )
                    }
                    if (u.onboarding) {
                        GoldPlaque("Locked", GoldLineBright)
                    } else {
                        GoldPlaque(u.role)
                    }
                }
                if (u.onboarding) {
                    Spacer(Modifier.height(4.dp))
                    GoldNote("ONBOARDING carries an empty capability bundle until the rites complete.")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        GoldPanel {
            GoldTitle("Glance at the ranks", "Who may seal, who may count, who may only watch.")
            Spacer(Modifier.height(4.dp))
            GoldNote("MANAGER seals remittances · Accountant counts the coffers.")
            GoldNote("Coordinator keeps the program · Practitioner serves the chairs.")
        }
    }
}

@Composable
internal fun GoldMailbox(repo: DecoGoldFakeRepo) {
    val unread = repo.notes.count { !it.read }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoldTitle(
                "Letter box",
                if (unread == 0) "Every seal broken, nothing unread." else "$unread sealed letter(s) await.",
            )
            Spacer(Modifier.weight(1f))
            if (unread > 0) GoldGhost("Open all") { repo.markAllRead() }
        }
        Spacer(Modifier.height(10.dp))
        if (repo.notes.isEmpty()) {
            GoldEmpty(title = "An empty box", body = "No letters rest in the box tonight.")
        } else {
            repo.notes.forEach { n ->
                GoldPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (n.read) "◇ " else "◆ ") + n.title,
                                fontWeight = FontWeight.Bold,
                                color = if (n.read) GoldMuted else GoldCream,
                            )
                            Text(text = n.body, color = GoldMuted, fontSize = 13.sp)
                            if (n.day.isNotEmpty()) GoldNote("Delivered ${n.day}")
                        }
                        if (!n.read) GoldButton("Open") { repo.markRead(n.id) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun GoldAuditLog(repo: DecoGoldFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("Chronicle", "Every void, seal, undo and footstep — with its reason.")
        Spacer(Modifier.height(10.dp))
        if (repo.audits.isEmpty()) {
            GoldEmpty(title = "Blank pages", body = "No entries yet. The night is young.")
        } else {
            repo.audits.forEach { a ->
                GoldPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoldPlaque(a.action)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${a.actor} — ${a.record}", fontWeight = FontWeight.Bold, color = GoldCream)
                            Text(text = a.whenText, color = GoldMuted, fontSize = 13.sp)
                            if (a.reason.isNotEmpty()) GoldNote("Reason: ${a.reason}")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun GoldProfile(
    repo: DecoGoldFakeRepo,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        GoldTitle("Your gilded card", "${repo.me.value.name} · ${repo.me.value.role}")
        Spacer(Modifier.height(10.dp))
        GoldPanel {
            GoldTitle("Branch Day lever", "Turn the day OPEN, PAST or REMITTED and watch the banner answer.")
            Spacer(Modifier.height(4.dp))
            Row {
                GoldDayStatus.entries.forEach { s ->
                    Box(
                        modifier =
                            Modifier
                                .background(if (repo.dayStatus.value == s) GoldSoft else Color.Transparent)
                                .border(1.dp, if (repo.dayStatus.value == s) GoldPrimary else GoldLine)
                                .clickable { repo.dayStatus.value = s }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = s.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (repo.dayStatus.value == s) GoldBright else GoldMuted,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GoldPanel {
            GoldTitle("Shift & hall", "Clock, reset the fiction, or step out through the brass doors.")
            Spacer(Modifier.height(4.dp))
            Row {
                GoldButton(if (repo.clockedIn.value) "Clock out" else "Clock in") { repo.clockToggle() }
                Spacer(Modifier.width(8.dp))
                GoldGhost("Reset demo") { repo.reset() }
                Spacer(Modifier.width(8.dp))
                GoldGhost("Log out") { onLogout() }
            }
        }
    }
}
