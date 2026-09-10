package com.companyb.companyapp.proto.onebutton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #852 — one-button screens: quiet content above, one giant action below.
// Every flow runs on fake data; nothing here touches the network.

// ---------- shared bits ----------

@Composable
private fun ObScreenTitle(title: String, sub: String) {
    Text(text = title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = ObInk)
    Spacer(Modifier.height(4.dp))
    Text(text = sub, fontSize = 14.sp, color = ObDim)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ObCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(ObShapes.medium).background(ObCard).padding(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun ObTag(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.clip(ObShapes.extraSmall).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun ObStatusTag(status: ObSessionStatus) {
    val (fg, bg) = when (status) {
        ObSessionStatus.PENDING -> ObAmber to ObAmberDeep
        ObSessionStatus.COMPLETED -> ObGreen to ObGreenDeep
        ObSessionStatus.NO_SHOW -> ObRed to ObRedDeep
        ObSessionStatus.CANCELLED -> ObGray to ObRaised
    }
    ObTag(status.name, fg, bg)
}

@Composable
private fun ObBigButton(label: String, sub: String, onPress: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = sub, fontSize = 13.sp, color = ObDim, modifier = Modifier.padding(bottom = 8.dp))
        Button(
            onClick = onPress,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            shape = ObShapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = ObAmber, contentColor = ObAmberInk),
        ) {
            Text(text = label, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ---------- login / onboarding / branch select ----------

@Composable
fun ObLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 200.dp, vertical = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "ONE BUTTON", fontSize = 44.sp, fontWeight = FontWeight.Black, color = ObAmber)
        Spacer(Modifier.height(8.dp))
        Text(text = "One screen. One job. One press.", fontSize = 15.sp, color = ObDim)
        Spacer(Modifier.height(32.dp))
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Work email (anything works — fake door)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ObCard,
                unfocusedContainerColor = ObCard,
                focusedTextColor = ObInk,
                unfocusedTextColor = ObInk,
            ),
        )
        Spacer(Modifier.height(20.dp))
        ObBigButton(
            label = "ENTER",
            sub = if (email.isBlank()) "Type anything above, then press once" else "Press once as $email",
            onPress = {
                logInfo("OneButton", "login pressed email=$email")
                onLogin()
            },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "New here? Peek at the ONBOARDING door",
            fontSize = 13.sp,
            color = ObDim,
            modifier = Modifier.clickable { onOnboarding() }.padding(8.dp),
        )
    }
}

@Composable
fun ObOnboardingLocked(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 200.dp, vertical = 90.dp)) {
        Text(text = "LOCKED", fontSize = 44.sp, fontWeight = FontWeight.Black, color = ObInk)
        Spacer(Modifier.height(8.dp))
        Text(text = "The ONBOARDING door opens only one way.", fontSize = 15.sp, color = ObDim)
        Spacer(Modifier.height(24.dp))
        ObCard {
            Text(text = "Role: ONBOARDING", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ObAmber)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Capability bundle is empty until a Coordinator lets you in. " +
                    "No screens, no queue, no button — this waiting room is the whole flow.",
                fontSize = 14.sp,
                color = ObGray,
            )
        }
        Spacer(Modifier.height(24.dp))
        ObBigButton(label = "BACK", sub = "One press returns to the door", onPress = onBack)
    }
}

@Composable
fun ObBranchSelect(repo: OneButtonFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 160.dp, vertical = 60.dp)) {
        Text(text = "PICK ONE ROOM", fontSize = 36.sp, fontWeight = FontWeight.Black, color = ObInk)
        Spacer(Modifier.height(8.dp))
        Text(text = "You work one room at a time. The rest wait.", fontSize = 15.sp, color = ObDim)
        Spacer(Modifier.height(24.dp))
        repo.branches.forEach { b ->
            val picked = repo.branchId.value == b.id
            Box(
                modifier = Modifier.fillMaxWidth().clip(ObShapes.medium)
                    .background(if (picked) ObAmberDeep else ObCard)
                    .clickable {
                        repo.branchId.value = b.id
                        logInfo("OneButton", "branch picked id=${b.id}")
                        onPick()
                    }
                    .padding(22.dp),
            ) {
                Column {
                    Text(
                        text = b.name.uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (picked) ObAmber else ObInk,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${b.kind} · ${b.focusNote}", fontSize = 13.sp, color = ObDim)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = "Not you? Back to the door",
            fontSize = 13.sp,
            color = ObDim,
            modifier = Modifier.clickable { onBack() }.padding(8.dp),
        )
    }
}

// ---------- home ----------

@Composable
fun ObHomeScreen(repo: OneButtonFakeRepo) {
    val next = repo.nextPending()
    ObScreenTitle(
        title = "Good shift, ${repo.me.value.name}.",
        sub = if (repo.clockedIn.value) "You are in. One thing matters now."
        else "You are out. The button below is your first step.",
    )
    ObCard {
        Text(
            text = if (repo.clockedIn.value) "CLOCKED IN" else "CLOCKED OUT",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = if (repo.clockedIn.value) ObGreen else ObDim,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (next != null) "Next in line: ${next.clientName} · ${next.type} · ${next.time}."
            else "Queue is clear at ${repo.branchName(repo.branchId.value)}.",
            fontSize = 14.sp,
            color = ObGray,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(text = "RELIEF BOARD — behind the button", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObGhost)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObQuietButton("Log duty") { repo.addRelief("Duty", "Single-press duty at the front desk") }
        ObQuietButton("Ask cover") { repo.addRelief("Request", "Single-press call for Saturday cover") }
        ObQuietButton("Offer help") { repo.addRelief("Invite", "Single-press hand for the tour") }
    }
    Spacer(Modifier.height(12.dp))
    repo.relief.forEach { r ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObTag(r.kind.uppercase(), ObBlue, ObBlueDeep)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${r.who} · ${r.branch} · ${r.day}", fontSize = 14.sp, color = ObInk)
                    Text(text = r.note, fontSize = 13.sp, color = ObDim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- sessions ----------

@Composable
fun ObSessionsScreen(repo: OneButtonFakeRepo, onVoid: (ObSession) -> Unit) {
    var filter by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var walkName by remember { mutableStateOf("") }
    ObScreenTitle(title = "The queue.", sub = "One guest at a time. The button finishes whoever is next.")
    ObCard {
        Text(
            text = "House rule: walk-ins never file NO_SHOW or CANCELLED — they were here in person.",
            fontSize = 13.sp,
            color = ObAmber,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObQuietButton("ALL") { filter = null }
        ObSessionStatus.entries.forEach { s -> ObQuietButton(s.name) { filter = s.name } }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (filter == null) "Showing everything" else "Showing $filter only",
        fontSize = 12.sp,
        color = ObGhost,
    )
    Spacer(Modifier.height(12.dp))
    repo.sessions.filter { filter == null || it.status.name == filter }.forEach { s ->
        val open = openId == s.id
        ObCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { openId = if (open) null else s.id },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${s.clientName}${if (s.walkIn) " · walk-in" else ""}${if (s.voided) " · VOID" else ""}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (s.voided) ObGhost else ObInk,
                    )
                    Text(
                        text = "${s.id} · ${repo.branchName(s.branchId)} · ${s.type} · ₱${s.price} · ${s.time}",
                        fontSize = 13.sp,
                        color = ObDim,
                    )
                }
                Spacer(Modifier.width(10.dp))
                ObStatusTag(s.status)
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                if (s.voided) {
                    Text(text = "Void reason: ${s.voidReason}", fontSize = 13.sp, color = ObRed)
                    Spacer(Modifier.height(8.dp))
                    ObQuietButton("Press back in (unvoid)") { repo.unvoidSession(s.id) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.status == ObSessionStatus.PENDING) {
                            ObQuietButton("Complete") { repo.completeSession(s.id) }
                        }
                        ObQuietButton("Void…") { onVoid(s) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
    ObCard {
        Text(text = "Seat someone new", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ObInk)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = walkName,
            onValueChange = { walkName = it },
            label = { Text("Guest name (blank = Walk-in guest)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ObRaised,
                unfocusedContainerColor = ObRaised,
                focusedTextColor = ObInk,
                unfocusedTextColor = ObInk,
            ),
        )
        Spacer(Modifier.height(8.dp))
        ObQuietButton("Add walk-in") {
            repo.addWalkIn(walkName.ifBlank { "Walk-in guest" }, "Drop-in check", 500)
            walkName = ""
        }
    }
}

// ---------- clients ----------

@Composable
fun ObClientsScreen(repo: OneButtonFakeRepo) {
    ObScreenTitle(title = "Faces.", sub = "Global list. One client, one card, no branches on faces.")
    ObCard {
        Text(
            text = "Rule of the house: at most one PENDING session per client.",
            fontSize = 13.sp,
            color = ObAmber,
        )
    }
    Spacer(Modifier.height(12.dp))
    repo.clients.forEach { c ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (c.anonymized) "Guest ••••" else c.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObInk,
                    )
                    Text(
                        text = if (c.anonymized) "Hidden for the lobby screen." else c.detail,
                        fontSize = 13.sp,
                        color = ObDim,
                    )
                    if (c.hasPending) {
                        Spacer(Modifier.height(4.dp))
                        ObTag("ONE PENDING", ObAmber, ObAmberDeep)
                    }
                }
                Spacer(Modifier.width(10.dp))
                ObQuietButton(if (c.anonymized) "Reveal" else "Anonymize") { repo.toggleAnonymized(c.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- finance ----------

@Composable
fun ObFinanceScreen(repo: OneButtonFakeRepo, onUndo: (ObRemittance) -> Unit) {
    ObScreenTitle(title = "The envelope.", sub = "SESSION and PRODUCT drafts. One seal per press.")
    ObRemitGroup(repo, ObRemitKind.SESSION, onUndo)
    Spacer(Modifier.height(16.dp))
    ObRemitGroup(repo, ObRemitKind.PRODUCT, onUndo)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObQuietButton("New SESSION draft") { repo.addDraft(ObRemitKind.SESSION) }
        ObQuietButton("New PRODUCT draft") { repo.addDraft(ObRemitKind.PRODUCT) }
    }
    Spacer(Modifier.height(12.dp))
    ObCard {
        Text(
            text = "Commission split 70/30 practitioner/branch on COMPLETED sessions. " +
                "Undo window is 48h with a reason; older snapshots stay sealed.",
            fontSize = 13.sp,
            color = ObDim,
        )
    }
}

@Composable
private fun ObRemitGroup(repo: OneButtonFakeRepo, kind: ObRemitKind, onUndo: (ObRemittance) -> Unit) {
    Text(text = "${kind.name} TRAY", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObGhost)
    Spacer(Modifier.height(8.dp))
    val items = repo.remittances.filter { it.kind == kind }
    if (items.isEmpty()) {
        Text(text = "Empty tray.", fontSize = 13.sp, color = ObGhost)
    }
    items.forEach { r ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${r.id} · ₱${r.amount} · ${r.dayLabel}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObInk,
                    )
                    val stateLine = when {
                        r.state == ObRemitState.DRAFT -> "DRAFT — waiting for the one press"
                        r.undoable -> "SUBMITTED ${r.snapshot} · ${r.submittedAt} — inside 48h"
                        else -> "SUBMITTED ${r.snapshot} · ${r.submittedAt} — sealed past 48h"
                    }
                    Text(text = stateLine, fontSize = 13.sp, color = ObDim)
                }
                Spacer(Modifier.width(10.dp))
                when {
                    r.state == ObRemitState.DRAFT -> ObQuietButton("Submit") { repo.submitRemittance(r.id) }
                    r.undoable -> ObQuietButton("Undo…") { onUndo(r) }
                    else -> ObTag("SEALED", ObGray, ObRaised)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- team ----------

@Composable
fun ObTeamScreen(repo: OneButtonFakeRepo) {
    ObScreenTitle(title = "The crew.", sub = "Users and roles. The button invites one more pair of hands.")
    val counts = repo.users.groupBy { it.role }.mapValues { it.value.size }
    ObCard {
        Text(text = "ROLE GLANCE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObGhost)
        Spacer(Modifier.height(8.dp))
        Text(
            text = counts.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
            fontSize = 14.sp,
            color = ObInk,
        )
    }
    Spacer(Modifier.height(12.dp))
    repo.users.forEach { u ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = u.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ObInk)
                    Text(
                        text = "${u.role} · home ${repo.branchName(u.homeBranchId)}",
                        fontSize = 13.sp,
                        color = ObDim,
                    )
                    if (u.onboarding) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "ONBOARDING is locked: empty capability bundle, no screens yet.",
                            fontSize = 13.sp,
                            color = ObAmber,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                ObTag(u.role, if (u.onboarding) ObAmber else ObBlue, if (u.onboarding) ObAmberDeep else ObBlueDeep)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- mailbox ----------

@Composable
fun ObMailboxScreen(repo: OneButtonFakeRepo) {
    val unread = repo.notes.count { !it.read }
    ObScreenTitle(
        title = "The pile.",
        sub = if (unread > 0) "$unread unread. The button clears them all at once." else "Pile is clear.",
    )
    repo.notes.forEach { n ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (n.read) n.title else "● ${n.title}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (n.read) ObGray else ObInk,
                    )
                    Text(text = n.body, fontSize = 13.sp, color = ObDim)
                    if (n.day.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = n.day, fontSize = 12.sp, color = ObGhost)
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (!n.read) ObQuietButton("Read") { repo.markRead(n.id) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Relief notes name their branch and day; the board itself lives on Home.",
        fontSize = 12.sp,
        color = ObGhost,
    )
}

// ---------- audit ----------

@Composable
fun ObAuditScreen(repo: OneButtonFakeRepo) {
    ObScreenTitle(
        title = "The receipt roll.",
        sub = "Every press leaves a line. The button stamps a summary.",
    )
    repo.audits.forEach { a ->
        ObCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObTag(a.action, ObAmber, ObAmberDeep)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(text = "${a.record} — ${a.actor}", fontSize = 14.sp, color = ObInk)
                    Text(
                        text = "${a.whenText}${if (a.reason.isNotEmpty()) " · ${a.reason}" else ""}",
                        fontSize = 13.sp,
                        color = ObDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- profile ----------

@Composable
fun ObProfileScreen(repo: OneButtonFakeRepo, onLogout: () -> Unit) {
    ObScreenTitle(title = "You.", sub = "One profile, one room control, one way out.")
    ObCard {
        Text(text = repo.me.value.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = ObInk)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${repo.me.value.role} · ${repo.branchName(repo.branchId.value)} · " +
                if (repo.clockedIn.value) "clocked in" else "clocked out",
            fontSize = 14.sp,
            color = ObDim,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(text = "BRANCH DAY REMOTE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObGhost)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObDayStatus.entries.forEach { s -> ObQuietButton(s.name) { repo.setDayStatus(s) } }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Now showing ${repo.dayStatus.value.name}. Day boundary 04:00 Asia/Manila.",
        fontSize = 12.sp,
        color = ObGhost,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ObQuietButton(if (repo.clockedIn.value) "Clock out" else "Clock in") { repo.clockToggle() }
        ObQuietButton("Reset demo") { repo.reset() }
        ObQuietButton("Log out") {
            logInfo("OneButton", "logout from profile")
            onLogout()
        }
    }
}

// ---------- reason dialog ----------

@Composable
fun ObReasonDialog(title: String, hint: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = ObInk) },
        text = {
            TextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ObRaised,
                    unfocusedContainerColor = ObRaised,
                    focusedTextColor = ObInk,
                    unfocusedTextColor = ObInk,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(text = confirm, fontWeight = FontWeight.Bold, color = ObAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "NEVER MIND", color = ObDim)
            }
        },
        containerColor = ObCard,
    )
}
