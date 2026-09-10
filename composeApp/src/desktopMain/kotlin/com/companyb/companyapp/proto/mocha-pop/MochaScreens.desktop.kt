package com.companyb.companyapp.proto.mochapop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
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
internal fun MochaCard(
    modifier: Modifier = Modifier,
    tint: Color = MochaCardSolid,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MochaShapes.medium,
        colors = CardDefaults.cardColors(containerColor = tint),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B4A38)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
internal fun MochaTruffle(text: String, bg: Color, fg: Color = MochaNight) {
    Box(
        modifier = Modifier.background(bg, MochaShapes.small)
            .border(1.dp, Color(0xFF6B4A38), MochaShapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
internal fun MochaTitle(title: String, subtitle: String = "") {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MochaCream)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MochaMuted)
        }
    }
}

@Composable
internal fun MochaCandyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MochaShapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MochaCandy, contentColor = Color(0xFF2A0E1B)),
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun MochaGhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = MochaShapes.small) {
        Text(text = label, color = MochaCandy)
    }
}

@Composable
internal fun MochaRuleNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().background(MochaCaramelSoft, MochaShapes.small)
            .border(1.dp, MochaCaramel, MochaShapes.small)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = MochaCream)
    }
}

@Composable
internal fun MochaDripStrip() {
    Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val dots = listOf(MochaCandy, MochaCaramel, MochaPlum, MochaMint, MochaCandyDeep)
        repeat(40) { i ->
            Box(modifier = Modifier.weight(1f).height(10.dp).background(dots[i % dots.size]))
        }
    }
}

@Composable
internal fun MochaEmpty(face: String, title: String, body: String, actionLabel: String = "", onAction: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MochaShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MochaPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B4A38)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = face, fontSize = 42.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MochaCream)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MochaMuted)
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                MochaCandyButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
internal fun MochaStatusChip(status: MochaSessionStatus, voided: Boolean) {
    val label = if (voided) "VOIDED" else status.name.replace('_', '-')
    val bg = when {
        voided -> MochaBerrySoft
        status == MochaSessionStatus.PENDING -> MochaCandySoft
        status == MochaSessionStatus.COMPLETED -> MochaMintSoft
        status == MochaSessionStatus.NO_SHOW -> MochaPlumSoft
        else -> MochaCaramelSoft
    }
    val fg = when {
        voided -> Color(0xFFFFD2D2)
        status == MochaSessionStatus.PENDING -> MochaCandy
        status == MochaSessionStatus.COMPLETED -> MochaMint
        status == MochaSessionStatus.NO_SHOW -> MochaPlum
        else -> MochaCaramel
    }
    Box(modifier = Modifier.background(bg, MochaShapes.extraSmall).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
internal fun MochaLogin(onLogin: (MochaUser) -> Unit, onOnboarding: () -> Unit, users: List<MochaUser>) {
    var name by remember { mutableStateOf("Mika Santos") }
    var picked by remember { mutableStateOf(users.firstOrNull()) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MochaTruffle("Evening batch no. 9", MochaCaramel, MochaNight)
        Spacer(Modifier.height(12.dp))
        Text(text = "mocha-pop", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = MochaCandy)
        Text(text = "MOCHA CANDY EVENINGS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MochaCaramel)
        Spacer(Modifier.height(4.dp))
        Text(text = "Cozy night-shift ops, 100% fake truffles.", color = MochaMuted)
        Spacer(Modifier.height(20.dp))
        MochaCard(modifier = Modifier.width(520.dp)) {
            MochaTitle(" melt in!", "Pick a candy-maker — any pick signs in, no password.")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            users.filter { !it.onboarding }.forEach { u ->
                val on = picked?.id == u.id
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (on) MochaCandySoft else Color.Transparent, MochaShapes.small)
                        .clickable { picked = u; name = u.name }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = if (on) "●" else "○", color = MochaCandy)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = MochaCream)
                        Text(text = "${u.role} · ${u.id}", fontSize = 12.sp, color = MochaMuted)
                    }
                    MochaTruffle(u.role, MochaPanel, MochaCaramel)
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(12.dp))
            MochaCandyButton("Swirl me in!") {
                val u = users.firstOrNull { it.name == name } ?: picked ?: users.first()
                onLogin(u)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOnboarding) { Text("Taste the ONBOARDING lock", color = MochaPlum) }
        }
    }
}

@Composable
internal fun MochaOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(44.dp)) {
        MochaRuleNote("ONBOARDING is a locked candy shell: zero capabilities until graduation.")
        Spacer(Modifier.height(12.dp))
        MochaTitle("New bean, sealed jar", "ONBOARDING sees this room only — nothing else is tappable by design.")
        Spacer(Modifier.height(16.dp))
        MochaEmpty(
            face = "🍬",
            title = "Still wrapped",
            body = "Capability bundle is empty on purpose. Finish onboarding to unwrap branch powers.",
            actionLabel = "Back to the counter",
            onAction = onBack,
        )
    }
}

@Composable
internal fun MochaBranchSelect(repo: MochaFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(36.dp)) {
        MochaTitle("Pick tonight's counter", "Branch select is fake — switching re-seats sessions below.")
        Spacer(Modifier.height(14.dp))
        repo.branches.forEach { b ->
            val on = repo.branchId.value == b.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { repo.branchId.value = b.id },
                shape = MochaShapes.medium,
                colors = CardDefaults.cardColors(containerColor = if (on) MochaCandySoft else MochaCardSolid),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (on) MochaCandy else Color(0xFF6B4A38),
                ),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = b.name, fontWeight = FontWeight.Bold, color = MochaCream, fontSize = 16.sp)
                        Text(text = "${b.kind} · ${b.candyNote}", color = MochaMuted, fontSize = 13.sp)
                    }
                    if (on) MochaTruffle("tonight", MochaCandy, MochaNight)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row {
            MochaCandyButton("Open the counter") { onPick() }
            Spacer(Modifier.width(10.dp))
            MochaGhostButton("Back") { onBack() }
        }
    }
}

@Composable
internal fun MochaDayBanner(repo: MochaFakeRepo) {
    val days = listOf(
        Triple("2026-09-08", MochaDayStatus.REMITTED, "Yesterday, sealed"),
        Triple("2026-09-09", MochaDayStatus.OPEN, "Today, melty"),
        Triple("2026-09-10", MochaDayStatus.PAST, "Tomorrow-ish, set"),
    )
    MochaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = "BRANCH DAY · ${repo.dayLabel.value}", fontWeight = FontWeight.Bold, color = MochaCaramel)
                Text(
                    text = "${repo.dayStatus.value.name} — 04:00 Asia/Manila boundary splits candy days.",
                    fontSize = 12.sp,
                    color = MochaMuted,
                )
                if (repo.dayStatus.value != MochaDayStatus.OPEN) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Covered day: edits lock for non-coordinators (fake rule).",
                        fontSize = 12.sp,
                        color = MochaBerry,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            days.forEach { (label, status, hint) ->
                val on = repo.dayLabel.value == label
                Box(
                    modifier = Modifier.background(
                        if (on) MochaCandy else MochaPanel,
                        MochaShapes.small,
                    ).clickable { repo.setDay(status, label) }.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label.takeLast(5),
                            fontWeight = FontWeight.Bold,
                            color = if (on) MochaNight else MochaCream,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = status.name,
                            fontSize = 10.sp,
                            color = if (on) MochaNight else MochaMuted,
                        )
                        Text(text = hint, fontSize = 9.sp, color = if (on) MochaNight else MochaMuted)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
internal fun MochaHome(repo: MochaFakeRepo) {
    var reliefNote by remember { mutableStateOf("Cover Sun 18:00-20:00?") }
    var reliefKind by remember { mutableStateOf("Request") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaDayBanner(repo)
        Spacer(Modifier.height(14.dp))
        Row {
            MochaCard(modifier = Modifier.weight(1f)) {
                MochaTitle("Clock-in counter", "Warm up the night: ${repo.me.value.name}")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (repo.clockedIn.value) "● CLOCKED IN at ${repo.branchName(repo.branchId.value)}"
                    else "○ CLOCKED OUT — the tray waits",
                    fontWeight = FontWeight.Bold,
                    color = if (repo.clockedIn.value) MochaMint else MochaCandy,
                )
                Spacer(Modifier.height(10.dp))
                MochaCandyButton(if (repo.clockedIn.value) "Clock out" else "Clock in") { repo.clockToggle() }
            }
            Spacer(Modifier.width(12.dp))
            MochaCard(modifier = Modifier.weight(1f), tint = MochaPanel) {
                MochaTitle("Tonight by the truffles", "Live counts from the fake tray")
                Spacer(Modifier.height(8.dp))
                val open = repo.sessions.count { it.status == MochaSessionStatus.PENDING && !it.voided }
                val done = repo.sessions.count { it.status == MochaSessionStatus.COMPLETED }
                val unread = repo.notes.count { !it.read }
                listOf(
                    "Pending truffles: $open",
                    "Completed melts: $done",
                    "Unread counter notes: $unread",
                    "Candy crew on shift: ${repo.users.size}",
                ).forEach {
                    Text(text = "🍫 $it", color = MochaCream, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MochaCard {
            MochaTitle("Relief candy exchange", "Duty grants, sweet invites,Cover requests — all fake.")
            Spacer(Modifier.height(10.dp))
            Row {
                listOf("Duty", "Invite", "Request").forEach { k ->
                    val on = reliefKind == k
                    Box(
                        modifier = Modifier.background(if (on) MochaCaramel else MochaPanel, MochaShapes.small)
                            .clickable { reliefKind = k }.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = k, fontWeight = FontWeight.Bold, color = if (on) MochaNight else MochaCream)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = reliefNote,
                onValueChange = { reliefNote = it },
                label = { Text("Relief note") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            MochaCandyButton("Drop on the tray") { repo.addRelief(reliefKind, reliefNote) }
            Spacer(Modifier.height(12.dp))
            repo.relief.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochaTruffle(r.kind, MochaCandySoft, MochaCandy)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = "${r.who} → ${r.branch} · ${r.day}", color = MochaCream, fontSize = 13.sp)
                        Text(text = r.note, color = MochaMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MochaSessions(repo: MochaFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var pickedId by remember { mutableStateOf(repo.sessions.firstOrNull()?.id) }
    var voidId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("Client asked to rebook") }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("Cocoa Guest") }
    var newType by remember { mutableStateOf("Checkup") }
    var newPrice by remember { mutableStateOf("500") }
    var newWalkIn by remember { mutableStateOf(true) }
    var newError by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Candy tray sessions", "PENDING → COMPLETED / NO_SHOW / CANCELLED, void with a reason.")
        Spacer(Modifier.height(6.dp))
        MochaRuleNote("Walk-ins never take NO_SHOW or CANCELLED — they simply melt away. Booked visits may.")
        Spacer(Modifier.height(10.dp))
        Row {
            MochaTruffle("all", if (filter == null) MochaCandy else MochaPanel, if (filter == null) MochaNight else MochaCream)
            Spacer(Modifier.width(8.dp))
            MochaSessionStatus.entries.forEach { s ->
                val on = filter == s.name
                Box(
                    modifier = Modifier.background(if (on) MochaCandy else MochaPanel, MochaShapes.small)
                        .clickable { filter = if (on) null else s.name }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(text = s.name.replace('_', '-'), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (on) MochaNight else MochaCream)
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            MochaCandyButton("+ truffle") { showNew = true; newError = "" }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                repo.sessions.filter { filter == null || it.status.name == filter }.forEach { s ->
                    val on = pickedId == s.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { pickedId = s.id },
                        shape = MochaShapes.small,
                        colors = CardDefaults.cardColors(containerColor = if (on) MochaCandySoft else MochaCardSolid),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (on) MochaCandy else Color(0xFF6B4A38),
                        ),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(text = s.clientName, fontWeight = FontWeight.Bold, color = MochaCream)
                                Text(
                                    text = "${s.type} · ₱${s.price} · ${s.time}${if (s.walkIn) " · walk-in" else ""}",
                                    fontSize = 12.sp,
                                    color = MochaMuted,
                                )
                            }
                            MochaStatusChip(s.status, s.voided)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            MochaCard(modifier = Modifier.weight(1f)) {
                val s = repo.sessions.firstOrNull { it.id == pickedId }
                if (s == null) {
                    MochaEmpty("🍬", "Empty tray", "Pick a truffle on the left.")
                } else {
                    MochaTitle(s.clientName, "${s.type} · ${s.time} · ${repo.branchName(s.branchId)}")
                    Spacer(Modifier.height(8.dp))
                    MochaStatusChip(s.status, s.voided)
                    if (s.voided) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = "Void reason: ${s.voidReason}", color = MochaBerry, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(text = "Pending for this client: ${repo.pendingCountForClient(s.clientName)}", color = MochaMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        MochaCandyButton("Complete") {
                            repo.setSessionStatus(s.id, MochaSessionStatus.COMPLETED, "Sweet finish")
                        }
                        Spacer(Modifier.width(8.dp))
                        MochaGhostButton("No-show") {
                            repo.setSessionStatus(s.id, MochaSessionStatus.NO_SHOW, "Guest never arrived")
                        }
                        Spacer(Modifier.width(8.dp))
                        MochaGhostButton("Cancel") {
                            repo.setSessionStatus(s.id, MochaSessionStatus.CANCELLED, "Evening rebook")
                        }
                    }
                    if (s.walkIn) {
                        Spacer(Modifier.height(6.dp))
                        Text(text = "Walk-in: NO_SHOW / CANCELLED buttons intentionally do nothing.", color = MochaCaramel, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (!s.voided) {
                            MochaGhostButton("Void…") { voidId = s.id; voidReason = "Client asked to rebook" }
                        } else {
                            MochaGhostButton("Unvoid") { repo.unvoidSession(s.id) }
                        }
                    }
                }
            }
        }
    }
    if (voidId != null) {
        AlertDialog(
            onDismissRequest = { voidId = null },
            title = { Text("Void this truffle?") },
            text = {
                Column {
                    Text("A reason is required — it lands in the audit log.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = voidReason, onValueChange = { voidReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                TextButton(onClick = { repo.voidSession(voidId!!, voidReason.ifEmpty { "No reason given" }); voidId = null }) {
                    Text("Void it")
                }
            },
            dismissButton = { TextButton(onClick = { voidId = null }) { Text("Keep") } },
        )
    }
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New truffle") },
            text = {
                Column {
                    if (newError.isNotEmpty()) {
                        Text(text = newError, color = MochaBerry, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Client") })
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = newType, onValueChange = { newType = it }, label = { Text("Type") })
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = newPrice, onValueChange = { newPrice = it }, label = { Text("Price") })
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.background(if (newWalkIn) MochaCandy else MochaPanel, MochaShapes.small)
                                .clickable { newWalkIn = !newWalkIn }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (newWalkIn) "● walk-in" else "○ booked",
                                color = if (newWalkIn) MochaNight else MochaCream,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("At most one PENDING booking at a time.", fontSize = 11.sp, color = MochaMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val err = repo.addSession(newName, newType, newPrice.toIntOrNull() ?: 0, newWalkIn)
                    if (err.isEmpty()) showNew = false else newError = err
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Close") } },
        )
    }
}

@Composable
internal fun MochaClients(repo: MochaFakeRepo) {
    var pickedId by remember { mutableStateOf(repo.clients.firstOrNull()?.id) }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Sugar registry", "Global client shelf — shared across every counter.")
        Spacer(Modifier.height(6.dp))
        MochaRuleNote("At most one live PENDING truffle per client; the ★ PENDING tag marks it.")
        Spacer(Modifier.height(10.dp))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                repo.clients.forEach { c ->
                    val on = pickedId == c.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { pickedId = c.id },
                        shape = MochaShapes.small,
                        colors = CardDefaults.cardColors(containerColor = if (on) MochaCandySoft else MochaCardSolid),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (on) MochaCandy else Color(0xFF6B4A38),
                        ),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = (if (c.anonymized) "Guest " else c.name) + if (c.hasPending) "  ★ PENDING" else "",
                                    fontWeight = FontWeight.Bold,
                                    color = MochaCream,
                                )
                                Text(text = c.detail, fontSize = 12.sp, color = MochaMuted)
                            }
                            if (c.anonymized) MochaTruffle("masked", MochaPlumSoft, MochaPlum)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            MochaCard(modifier = Modifier.weight(1f)) {
                val c = repo.clients.firstOrNull { it.id == pickedId }
                if (c == null) {
                    MochaEmpty("🍬", "No guest", "Pick a name on the left.")
                } else {
                    MochaTitle(if (c.anonymized) "Guest ${c.id}" else c.name, "Sugar file ${c.id}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (c.anonymized) "PII hidden: name + contact masked; taste notes kept."
                        else c.detail,
                        color = MochaCream,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = "Session history: ${repo.pendingCountForClient(c.name)} live PENDING.", color = MochaMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    MochaGhostButton(if (c.anonymized) "Unmask (fake)" else "Anonymize") { repo.toggleAnonymized(c.id) }
                    Spacer(Modifier.height(6.dp))
                    Text("Anonymized view keeps flavor, drops identity — demo only.", fontSize = 11.sp, color = MochaMuted)
                }
            }
        }
    }
}

@Composable
internal fun MochaFinance(repo: MochaFakeRepo) {
    var undoId by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("Miscounted truffles") }
    var lineLabel by remember { mutableStateOf("Truffle box") }
    var lineAmount by remember { mutableStateOf("350") }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Caramel ledger", "SESSION + PRODUCT drafts → submit → snapshot → undo within 48h.")
        Spacer(Modifier.height(6.dp))
        MochaRuleNote("Undo window is 48h after submit (fake clock: Today 21:00). Commission splits outside remittance.")
        Spacer(Modifier.height(10.dp))
        Row {
            MochaCard(modifier = Modifier.weight(1f)) {
                MochaTitle("SESSION draft", "Net = completed sessions − comps − expenses (fake: ₱${repo.sessionNet()})")
                Spacer(Modifier.height(8.dp))
                repo.remittances.filter { it.kind == MochaRemitKind.SESSION }.forEach { r ->
                    MochaRemitRow(repo, r) { undoId = r.id; undoReason = "Miscounted truffles" }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            MochaCard(modifier = Modifier.weight(1f)) {
                MochaTitle("PRODUCT draft", "Counter shelf: ₱${repo.productNet()}")
                Spacer(Modifier.height(8.dp))
                repo.productLines.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = p.label, color = MochaCream, fontSize = 13.sp)
                            Text(text = "₱${p.amount}", color = MochaMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { repo.removeProductLine(p.id) }) { Text("Drop", color = MochaBerry) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = lineLabel, onValueChange = { lineLabel = it }, label = { Text("Line") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = lineAmount, onValueChange = { lineAmount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                MochaCandyButton("Add line") { repo.addProductLine(lineLabel, lineAmount.toIntOrNull() ?: 0) }
                Spacer(Modifier.height(10.dp))
                repo.remittances.filter { it.kind == MochaRemitKind.PRODUCT }.forEach { r ->
                    MochaRemitRow(repo, r) { undoId = r.id; undoReason = "Miscounted truffles" }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MochaCard(tint = MochaPanel) {
            MochaTitle("Commission split note", "5% candy pool split equally across the clocked-in crew.")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pool = 5% of ₱${repo.sessionNet()} ≈ ₱${repo.sessionNet() * 5 / 100}. Paid outside remittance, listed per crew member in the real ledger.",
                color = MochaCream,
                fontSize = 13.sp,
            )
        }
    }
    if (undoId != null) {
        AlertDialog(
            onDismissRequest = { undoId = null },
            title = { Text("Undo within 48h?") },
            text = {
                Column {
                    Text("Undo reopens the draft and stamps the audit log with your reason.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = undoReason, onValueChange = { undoReason = it }, label = { Text("Reason") })
                }
            },
            confirmButton = {
                TextButton(onClick = { repo.undoRemittance(undoId!!, undoReason); undoId = null }) { Text("Undo it") }
            },
            dismissButton = { TextButton(onClick = { undoId = null }) { Text("Keep sealed") } },
        )
    }
}

@Composable
private fun MochaRemitRow(repo: MochaFakeRepo, r: MochaRemittance, onUndo: () -> Unit) {
    Card(
        shape = MochaShapes.small,
        colors = CardDefaults.cardColors(containerColor = MochaPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B4A38)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MochaTruffle(r.kind.name, MochaCaramelSoft, MochaCaramel)
                Spacer(Modifier.width(8.dp))
                MochaTruffle(r.status.name, if (r.status == MochaRemitStatus.DRAFT) MochaPlumSoft else MochaMintSoft,
                    if (r.status == MochaRemitStatus.DRAFT) MochaPlum else MochaMint)
                Spacer(Modifier.weight(1f))
                Text(text = "₱${r.amount}", fontWeight = FontWeight.Bold, color = MochaCream)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (r.snapshot.isNotEmpty()) "Snapshot ${r.snapshot} · ${r.submittedAt} · ${r.dayLabel}"
                else "Draft · ${r.dayLabel} · not yet sealed",
                fontSize = 12.sp,
                color = MochaMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                if (r.status == MochaRemitStatus.DRAFT) {
                    MochaCandyButton("Submit") { repo.submitRemittance(r.id) }
                } else {
                    MochaGhostButton("Undo (48h)") { onUndo() }
                }
            }
        }
    }
}

@Composable
internal fun MochaTeam(repo: MochaFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Candy crew", "Roster, roles, and who is melting on shift.")
        Spacer(Modifier.height(10.dp))
        repo.users.forEach { u ->
            Card(
                shape = MochaShapes.small,
                colors = CardDefaults.cardColors(containerColor = MochaCardSolid),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B4A38)),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(MochaCandySoft, MochaShapes.extraSmall)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = u.name.take(1), fontWeight = FontWeight.ExtraBold, color = MochaCandy, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = u.name, fontWeight = FontWeight.Bold, color = MochaCream)
                        Text(text = "${u.role} · home ${repo.branchName(u.homeBranchId)}", fontSize = 12.sp, color = MochaMuted)
                        if (u.onboarding) Text(text = "Locked: no capabilities yet", fontSize = 11.sp, color = MochaBerry)
                    }
                    MochaTruffle(u.role, MochaPanel, MochaCaramel)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun MochaMailbox(repo: MochaFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MochaTitle("Counter notes", "Mailbox: tap a note to mark it read.")
            }
            MochaGhostButton("Read them all") { repo.markAllRead() }
        }
        Spacer(Modifier.height(10.dp))
        val unread = repo.notes.count { !it.read }
        MochaRuleNote("Unread counter notes: $unread. Relief invites live here too.")
        Spacer(Modifier.height(10.dp))
        repo.notes.forEach { n ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { repo.markRead(n.id) },
                shape = MochaShapes.small,
                colors = CardDefaults.cardColors(containerColor = if (n.read) MochaCardSolid else MochaCandySoft),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (n.read) Color(0xFF6B4A38) else MochaCandy),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = n.title, fontWeight = FontWeight.Bold, color = MochaCream)
                        Text(text = n.body, fontSize = 12.sp, color = MochaMuted)
                        Text(text = n.day, fontSize = 11.sp, color = MochaMuted)
                    }
                    if (!n.read) MochaTruffle("new", MochaCandy, MochaNight)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun MochaAuditLog(repo: MochaFakeRepo) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Wrapper trail", "Every tap above stamps this audit shelf, newest first.")
        Spacer(Modifier.height(10.dp))
        repo.audits.forEach { a ->
            Card(
                shape = MochaShapes.small,
                colors = CardDefaults.cardColors(containerColor = MochaCardSolid),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B4A38)),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MochaTruffle(a.action, MochaPanel, MochaCaramel)
                        Spacer(Modifier.width(8.dp))
                        Text(text = a.record, fontWeight = FontWeight.Bold, color = MochaCream, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(text = a.whenText, fontSize = 11.sp, color = MochaMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${a.actor}${if (a.reason.isNotEmpty()) " — ${a.reason}" else ""}", fontSize = 12.sp, color = MochaMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun MochaProfile(repo: MochaFakeRepo, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        MochaTitle("Candy-maker badge", "Profile, capabilities, clock-out, logout.")
        Spacer(Modifier.height(10.dp))
        MochaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(MochaCandy, MochaShapes.medium).padding(20.dp)) {
                    Text(text = repo.me.value.name.take(1), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = MochaNight)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = repo.me.value.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MochaCream)
                    Text(text = "${repo.me.value.role} · ${repo.branchName(repo.branchId.value)}", color = MochaMuted)
                    Spacer(Modifier.height(6.dp))
                    val caps = when (repo.me.value.role) {
                        "ONBOARDING" -> "Capabilities: (none — sealed jar)"
                        "Practitioner" -> "Capabilities: sessions, clock-in, relief"
                        "Coordinator" -> "Capabilities: sessions, covered-day edits, remittance"
                        "Manager" -> "Capabilities: everything + crew + snapshots"
                        else -> "Capabilities: ledger, snapshots, undo-48h"
                    }
                    Text(text = caps, fontSize = 12.sp, color = MochaCaramel)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MochaCard(tint = MochaPanel) {
            MochaTitle("End the candy shift", "Clock out first, then melt out.")
            Spacer(Modifier.height(10.dp))
            Row {
                MochaCandyButton(if (repo.clockedIn.value) "Clock out" else "Clock in") { repo.clockToggle() }
                Spacer(Modifier.width(10.dp))
                MochaGhostButton("Log out") { onLogout() }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Logout returns to the counter sign-in. Fake state resets next run.", fontSize = 11.sp, color = MochaMuted)
        }
    }
}
