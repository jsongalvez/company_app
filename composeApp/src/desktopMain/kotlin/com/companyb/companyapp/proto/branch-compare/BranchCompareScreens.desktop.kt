package com.companyb.companyapp.proto.branchcompare

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun CmpLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ComparePanel(
            modifier = Modifier.width(460.dp),
            accent = SunriseAmber,
            title = "Owner sign in",
            meta = "Branch-compare desk - fake sign-in, any email works",
        ) {
            var email by remember { mutableStateOf("owner@companyb.ph") }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Work email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            CmpPrimaryButton(label = "Open compare desk", onClick = onLogin)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOnboardingDemo) {
                Text(text = "Preview the ONBOARDING welcome", fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun CmpOnboardingLocked(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ComparePanel(
            modifier = Modifier.width(460.dp),
            accent = CompareSoft,
            title = "Welcome - account pending",
            meta = "ONBOARDING role is locked until a MANAGER grants a capability bundle",
        ) {
            Text(
                text = "Lena Cruz sits in ONBOARDING with an empty capability bundle: no clock-in, no sessions, no remittance. A MANAGER grants Practitioner from the TEAM tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = CompareBody,
            )
            Spacer(Modifier.height(12.dp))
            CmpGhostButton(label = "Back to sign in", onClick = onBack)
        }
    }
}

@Composable
internal fun CmpBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = "Pick today's home branch",
            subtitle = "Clock-in starts here. The compare desk opens after you pick.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            for (branch in BranchCompareFakeRepo.branches) {
                val accent = branchAccent(branch.id)
                ComparePanel(
                    modifier = Modifier.weight(1f).clickable { onPick(branch.id) },
                    accent = accent,
                    title = branch.name,
                    meta = branch.kind.replace('_', ' '),
                ) {
                    DayRibbon(branch.dayStatus)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Gross P${BranchCompareFakeRepo.grossFor(branch.id)} of P${branch.target}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = CompareBody,
                    )
                    Text(
                        text = "${BranchCompareFakeRepo.staffOn(branch.id)} on shift - ${BranchCompareFakeRepo.pendingCount(branch.id)} pending",
                        fontSize = 12.sp,
                        color = CompareSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = "Tap to clock in here >", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
                }
            }
        }
        CmpGhostButton(label = "Back", onClick = onBack)
    }
}

@Composable
internal fun CmpCompareHome() {
    val focused = BranchCompareFakeRepo.focusedBranchId.value
    SectionHeader(
        title = "Side-by-side today",
        subtitle = "Owner view: day state, gross vs target, staffing. Tap a column to dive in.",
        count = "3 branches",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        for (branch in BranchCompareFakeRepo.branches) {
            val accent = branchAccent(branch.id)
            val selected = branch.id == focused
            val gross = BranchCompareFakeRepo.grossFor(branch.id)
            Card(
                modifier = Modifier.weight(1f)
                    .clickable { BranchCompareFakeRepo.focusedBranchId.value = branch.id }
                    .border(if (selected) 3.dp else 1.dp, accent, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) Color.White else ComparePaper,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(6.dp)).background(accent),
                    ) {}
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = branch.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = CompareBody)
                            Text(text = branch.kind.replace('_', ' '), fontSize = 12.sp, color = CompareSoft)
                        }
                        if (selected) CompareChip("DIVING IN", accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    DayRibbon(branch.dayStatus)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "P$gross",
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        color = CompareBody,
                    )
                    Text(text = "of P${branch.target} target", fontSize = 12.sp, color = CompareSoft)
                    Spacer(Modifier.height(4.dp))
                    val pct = (gross * 100 / branch.target.coerceAtLeast(1)).coerceAtMost(100)
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(ComparePaperEdge)) {
                        Box(modifier = Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(8.dp)).background(accent)) {}
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompareChip("${BranchCompareFakeRepo.staffOn(branch.id)} on shift", HarborTeal)
                        CompareChip("${BranchCompareFakeRepo.pendingCount(branch.id)} pending", StatusPending)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(text = "Tap to dive >", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
                }
            }
        }
    }
    val dive = BranchCompareFakeRepo.branch(focused)
    if (dive != null) {
        SectionHeader(
            title = "Dive: ${dive.name}",
            subtitle = "Single-branch detail for the tapped column. Other tabs follow this branch.",
        )
        CmpBranchDayBanner(focused)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ComparePanel(
                modifier = Modifier.weight(1f),
                accent = branchAccent(focused),
                title = "Clock-in",
                meta = "Home shift plus relief coverage",
            ) {
                val clocked = BranchCompareFakeRepo.clockedIn.value
                Text(
                    text = if (clocked) "Clocked in at ${BranchCompareFakeRepo.branchName(BranchCompareFakeRepo.clockedBranchId.value)}" else "Off shift - clock in to start",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CompareBody,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!clocked) {
                        CmpPrimaryButton(
                            label = "Clock in here",
                            onClick = {
                                BranchCompareFakeRepo.clockedIn.value = true
                                BranchCompareFakeRepo.clockedBranchId.value = focused
                                BranchCompareFakeRepo.log("Clock in", dive.name)
                            },
                        )
                    } else {
                        CmpGhostButton(
                            label = "Clock out",
                            onClick = {
                                BranchCompareFakeRepo.clockedIn.value = false
                                BranchCompareFakeRepo.log("Clock out", dive.name)
                            },
                        )
                    }
                }
            }
            ComparePanel(
                modifier = Modifier.weight(1f),
                accent = HarborTeal,
                title = "Relief duty, invites, requests",
                meta = "Broadcast a request or send an invite",
            ) {
                var inviteNote by remember { mutableStateOf("") }
                for (item in BranchCompareFakeRepo.relief) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${item.kind}: ${item.branch}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CompareBody)
                            Text(text = "${item.who} - ${item.date} - ${item.state}", fontSize = 12.sp, color = CompareSoft)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.state.contains("waiting", ignoreCase = true)) {
                            Button(
                                onClick = {
                                    val idx = BranchCompareFakeRepo.relief.indexOfFirst { it.id == item.id }
                                    if (idx >= 0) BranchCompareFakeRepo.relief[idx] = item.copy(state = "Granted")
                                    BranchCompareFakeRepo.log("Relief granted", "${item.kind} ${item.branch}")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DayOpen, contentColor = Color.White),
                            ) { Text("Grant", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = {
                                    val idx = BranchCompareFakeRepo.relief.indexOfFirst { it.id == item.id }
                                    if (idx >= 0) BranchCompareFakeRepo.relief[idx] = item.copy(state = "Folded")
                                    BranchCompareFakeRepo.log("Relief folded", "${item.kind} ${item.branch}")
                                },
                            ) { Text("Fold", fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedTextField(
                    value = inviteNote,
                    onValueChange = { inviteNote = it },
                    label = { Text("Invite note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CmpPrimaryButton(
                        label = "Broadcast request",
                        accent = HarborTeal,
                        onClick = {
                            val n = BranchCompareFakeRepo.relief.size + 1
                            BranchCompareFakeRepo.relief.add(
                                CmpRelief("q$n", "Relief request", BranchCompareFakeRepo.currentUserName.value, dive.name, "Today", "Broadcast - waiting for grant"),
                            )
                            BranchCompareFakeRepo.log("Relief requested", dive.name)
                        },
                    )
                    CmpGhostButton(
                        label = "Send invite",
                        onClick = {
                            val n = BranchCompareFakeRepo.relief.size + 1
                            val note = inviteNote.ifBlank { "Come cover Saturday" }
                            BranchCompareFakeRepo.relief.add(
                                CmpRelief("q$n", "Relief invite", BranchCompareFakeRepo.currentUserName.value, dive.name, "Sat", "Invite waiting for reply - $note"),
                            )
                            inviteNote = ""
                            BranchCompareFakeRepo.log("Relief invited", dive.name)
                        },
                    )
                }
            }
        }
        ComparePanel(
            accent = branchAccent(focused),
            title = "Today at ${dive.name}",
            meta = "Session mix for the dive branch - full list lives in SESSIONS",
        ) {
            val list = BranchCompareFakeRepo.sessionsFor(focused)
            if (list.isEmpty()) {
                Text(text = "No sessions yet for this branch.", fontSize = 13.sp, color = CompareSoft)
            } else {
                for (s in list) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${s.time}  ${s.clientName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CompareBody,
                            modifier = Modifier.weight(1f),
                        )
                        CompareChip(s.status.name, statusAccent(s.status))
                    }
                }
            }
        }
    }
}

internal fun statusAccent(status: CmpSessionStatus): Color = when (status) {
    CmpSessionStatus.PENDING -> StatusPending
    CmpSessionStatus.COMPLETED -> StatusCompleted
    CmpSessionStatus.NO_SHOW -> StatusNoShow
    CmpSessionStatus.CANCELLED -> StatusCancelled
}

@Composable
internal fun CmpSessions(onAudit: (String, String, String) -> Unit) {
    val focused = BranchCompareFakeRepo.focusedBranchId.value
    var filter by remember { mutableStateOf<CmpSessionStatus?>(null) }
    var voidingId by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    SectionHeader(
        title = "Sessions",
        subtitle = "Walk-ins can never be NO_SHOW or CANCELLED. Void needs a reason.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (branch in BranchCompareFakeRepo.branches) {
            val selected = branch.id == focused
            if (selected) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = branchAccent(branch.id), contentColor = Color.White),
                ) { Text(branch.name, fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = { BranchCompareFakeRepo.focusedBranchId.value = branch.id }) {
                    Text(branch.name, fontSize = 12.sp)
                }
            }
        }
    }
    CmpBranchDayBanner(focused)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val allSelected = filter == null
        if (allSelected) {
            Button(onClick = {}) { Text("All", fontSize = 12.sp) }
        } else {
            OutlinedButton(onClick = { filter = null }) { Text("All", fontSize = 12.sp) }
        }
        for (status in CmpSessionStatus.entries) {
            if (filter == status) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = statusAccent(status), contentColor = Color.White),
                ) { Text(status.name, fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = { filter = status }) { Text(status.name, fontSize = 12.sp) }
            }
        }
    }
    val list = BranchCompareFakeRepo.sessionsFor(focused).filter { filter == null || it.status == filter }
    if (list.isEmpty()) {
        ComparePanel(accent = ComparePaperEdge, title = "Nothing here", meta = "No sessions match this filter.") {}
    }
    for (session in list) {
        ComparePanel(
            accent = statusAccent(session.status),
            title = "${session.time} - ${session.clientName}",
            meta = "${session.type} - P${session.price}${if (session.walkIn) " - walk-in" else ""}${if (session.voided) " - VOIDED (${session.voidReason})" else ""}",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompareChip(session.status.name, statusAccent(session.status))
                if (session.walkIn) CompareChip("WALK-IN", HarborTeal)
                if (session.voided) CompareChip("VOID", StatusCancelled)
            }
            Spacer(Modifier.height(8.dp))
            if (session.walkIn) {
                Text(
                    text = "Walk-in rule: NO_SHOW and CANCELLED are disabled for walk-ins.",
                    fontSize = 12.sp,
                    color = CompareSoft,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (next in CmpSessionStatus.entries.filter { it != session.status }) {
                    if (session.walkIn && (next == CmpSessionStatus.NO_SHOW || next == CmpSessionStatus.CANCELLED)) continue
                    OutlinedButton(
                        onClick = {
                            val idx = BranchCompareFakeRepo.sessions.indexOfFirst { it.id == session.id }
                            if (idx >= 0) BranchCompareFakeRepo.sessions[idx] = session.copy(status = next)
                            onAudit("Session ${next.name.lowercase()}", "${session.clientName} ${session.id}", "")
                        },
                    ) { Text(next.name, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!session.voided) {
                if (voidingId == session.id) {
                    OutlinedTextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Void reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (voidReason.isNotBlank()) {
                                    val idx = BranchCompareFakeRepo.sessions.indexOfFirst { it.id == session.id }
                                    if (idx >= 0) BranchCompareFakeRepo.sessions[idx] = session.copy(voided = true, voidReason = voidReason.trim())
                                    onAudit("Session voided", "${session.clientName} ${session.id}", voidReason.trim())
                                    voidingId = null
                                    voidReason = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled, contentColor = Color.White),
                        ) { Text("Confirm void", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                voidingId = null
                                voidReason = ""
                            },
                        ) { Text("Cancel", fontSize = 12.sp) }
                    }
                } else {
                    OutlinedButton(onClick = { voidingId = session.id }) { Text("Void with reason", fontSize = 12.sp) }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val idx = BranchCompareFakeRepo.sessions.indexOfFirst { it.id == session.id }
                        if (idx >= 0) BranchCompareFakeRepo.sessions[idx] = session.copy(voided = false, voidReason = "")
                        onAudit("Session unvoided", "${session.clientName} ${session.id}", "")
                    },
                ) { Text("Unvoid", fontSize = 12.sp) }
            }
        }
    }
    ComparePanel(accent = HarborTeal, title = "Book a session", meta = "Adds a PENDING session to the dive branch.") {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Client name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        CmpPrimaryButton(
            label = "Add PENDING session",
            onClick = {
                if (newName.isNotBlank()) {
                    val n = BranchCompareFakeRepo.sessions.size + 1
                    BranchCompareFakeRepo.sessions.add(
                        CmpSession("n$n", newName.trim(), focused, CmpSessionStatus.PENDING, "Follow-up", 800, false, time = "16:00"),
                    )
                    onAudit("Session booked", "${newName.trim()} $focused", "")
                    newName = ""
                }
            },
        )
    }
}

@Composable
internal fun CmpClients() {
    var anonymizedView by remember { mutableStateOf(false) }
    SectionHeader(
        title = "Clients (global)",
        subtitle = "One shared book across branches. At most one PENDING session per client.",
        count = "${BranchCompareFakeRepo.clients.size} clients",
    )
    ComparePanel(accent = HarborTeal, title = "Privacy", meta = "Anonymized view keeps gender and age only.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (anonymizedView) "Anonymized view is ON" else "Anonymized view is OFF",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CompareBody,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { anonymizedView = !anonymizedView }) {
                Text(if (anonymizedView) "Show names" else "Anonymize", fontSize = 12.sp)
            }
        }
    }
    for (client in BranchCompareFakeRepo.clients) {
        val hidden = anonymizedView || client.anonymized
        ComparePanel(
            accent = if (client.hasPending) StatusPending else ComparePaperEdge,
            title = if (hidden) "Client ${client.id.uppercase()} (anonymized)" else client.name,
            meta = client.detail,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (client.hasPending) CompareChip("1 PENDING", StatusPending) else CompareChip("NO PENDING", StatusCompleted)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val idx = BranchCompareFakeRepo.clients.indexOfFirst { it.id == client.id }
                        if (idx >= 0) BranchCompareFakeRepo.clients[idx] = client.copy(anonymized = !client.anonymized)
                    },
                ) { Text(if (client.anonymized) "Reveal" else "Anonymize", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
internal fun CmpFinance() {
    val focused = BranchCompareFakeRepo.focusedBranchId.value
    var undoingId by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    SectionHeader(
        title = "Finance - ${BranchCompareFakeRepo.branchName(focused)}",
        subtitle = "SESSION and PRODUCT drafts. Submit seals a snapshot. Undo works within 48h with a reason.",
    )
    CmpBranchDayBanner(focused)
    ComparePanel(accent = SunriseAmber, title = "Commission split", meta = "Net income splits Practitioner / branch after each sealed remittance.") {
        Text(
            text = "Practitioner keeps the service share, the branch keeps the room share, missions pool into Lingap outreach. Split posts when a remittance is submitted.",
            fontSize = 13.sp,
            color = CompareBody,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (kind in CmpRemitKind.entries) {
            CmpPrimaryButton(
                label = "New ${kind.name} draft",
                onClick = {
                    BranchCompareFakeRepo.remittances.add(
                        CmpRemittance(
                            BranchCompareFakeRepo.nextRemitId(),
                            focused,
                            kind,
                            CmpRemitStatus.DRAFT,
                            if (kind == CmpRemitKind.SESSION) BranchCompareFakeRepo.grossFor(focused) else 1200,
                            "Today at ${BranchCompareFakeRepo.branchName(focused)}",
                        ),
                    )
                    BranchCompareFakeRepo.log("${kind.name} draft created", BranchCompareFakeRepo.branchName(focused))
                },
            )
        }
    }
    val list = BranchCompareFakeRepo.remittances.filter { it.branchId == focused }
    if (list.isEmpty()) {
        ComparePanel(accent = ComparePaperEdge, title = "No drafts yet", meta = "Create a SESSION or PRODUCT draft above.") {}
    }
    for (remit in list) {
        ComparePanel(
            accent = if (remit.status == CmpRemitStatus.SUBMITTED) DayRemitted else StatusPending,
            title = "${remit.kind.name} - P${remit.amount}",
            meta = "${remit.dayLabel}${if (remit.snapshot.isNotEmpty()) " - ${remit.snapshot}" else ""}",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompareChip(remit.status.name, if (remit.status == CmpRemitStatus.SUBMITTED) DayRemitted else StatusPending)
                if (remit.submittedAt.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(text = remit.submittedAt, fontSize = 12.sp, color = CompareSoft)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (remit.status == CmpRemitStatus.DRAFT) {
                CmpPrimaryButton(
                    label = "Submit and seal snapshot",
                    onClick = {
                        val idx = BranchCompareFakeRepo.remittances.indexOfFirst { it.id == remit.id }
                        if (idx >= 0) BranchCompareFakeRepo.remittances[idx] = remit.copy(
                            status = CmpRemitStatus.SUBMITTED,
                            snapshot = "SNAP-${(1000 + idx * 7)} sealed",
                            submittedAt = "Today 18:02",
                        )
                        BranchCompareFakeRepo.log("${remit.kind.name} remittance submitted", "${remit.branchId} P${remit.amount}")
                    },
                )
            } else {
                if (undoingId == remit.id) {
                    OutlinedTextField(
                        value = undoReason,
                        onValueChange = { undoReason = it },
                        label = { Text("Undo reason (required, within 48h)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (undoReason.isNotBlank()) {
                                    val idx = BranchCompareFakeRepo.remittances.indexOfFirst { it.id == remit.id }
                                    if (idx >= 0) BranchCompareFakeRepo.remittances[idx] = remit.copy(
                                        status = CmpRemitStatus.DRAFT,
                                        snapshot = "",
                                        submittedAt = "",
                                    )
                                    BranchCompareFakeRepo.log("${remit.kind.name} remittance undone", "${remit.branchId} P${remit.amount}", undoReason.trim())
                                    undoingId = null
                                    undoReason = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled, contentColor = Color.White),
                        ) { Text("Confirm undo", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = {
                                undoingId = null
                                undoReason = ""
                            },
                        ) { Text("Cancel", fontSize = 12.sp) }
                    }
                } else {
                    OutlinedButton(onClick = { undoingId = remit.id }) { Text("Undo within 48h", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
internal fun CmpTeam() {
    SectionHeader(
        title = "Team",
        subtitle = "Users and roles across branches. ONBOARDING is locked until granted.",
        count = "${BranchCompareFakeRepo.users.size} users",
    )
    for (user in BranchCompareFakeRepo.users) {
        val accent = if (user.onboarding) CompareSoft else branchAccent(user.homeBranchId)
        ComparePanel(
            accent = accent,
            title = user.name,
            meta = "${user.role} - home: ${BranchCompareFakeRepo.branchName(user.homeBranchId)}${if (user.clockedIn) " - on shift" else ""}",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompareChip(user.role, accent)
                if (user.onboarding) CompareChip("LOCKED", StatusCancelled)
                if (user.clockedIn) CompareChip("ON SHIFT", DayOpen)
                Spacer(Modifier.weight(1f))
                if (user.onboarding) {
                    CmpPrimaryButton(
                        label = "Grant Practitioner",
                        accent = DayOpen,
                        onClick = {
                            val idx = BranchCompareFakeRepo.users.indexOfFirst { it.id == user.id }
                            if (idx >= 0) BranchCompareFakeRepo.users[idx] = user.copy(role = "Practitioner", onboarding = false)
                            BranchCompareFakeRepo.log("Capability granted", "${user.name} Practitioner")
                        },
                    )
                }
            }
            if (user.onboarding) {
                Spacer(Modifier.height(6.dp))
                Text(text = "Empty capability bundle: cannot clock in or take sessions.", fontSize = 12.sp, color = CompareSoft)
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (user.role) {
                        "Practitioner" -> "Takes sessions, clocks in, requests relief."
                        "Coordinator" -> "Runs the day board, grants relief, takes walk-ins."
                        "MANAGER" -> "Owns branch days, grants capabilities, submits remittance."
                        "Accountant" -> "Reviews snapshots, posts commission splits."
                        else -> "Supports the branch."
                    },
                    fontSize = 12.sp,
                    color = CompareSoft,
                )
            }
        }
    }
}

@Composable
internal fun CmpMailbox() {
    val unread = BranchCompareFakeRepo.mailbox.count { !it.read }
    SectionHeader(
        title = "Notifications",
        subtitle = "Mailbox with read / unread states. Relief items name the branch and day.",
        count = "$unread unread",
    )
    CmpGhostButton(
        label = "Mark all read",
        onClick = {
            BranchCompareFakeRepo.mailbox.forEachIndexed { idx, mail ->
                BranchCompareFakeRepo.mailbox[idx] = mail.copy(read = true)
            }
        },
    )
    for (mail in BranchCompareFakeRepo.mailbox) {
        ComparePanel(
            accent = if (mail.read) ComparePaperEdge else SunriseAmber,
            title = mail.title,
            meta = "${mail.day}${if (mail.read) " - read" else " - unread"}",
        ) {
            Text(text = mail.body, fontSize = 13.sp, color = CompareBody)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val idx = BranchCompareFakeRepo.mailbox.indexOfFirst { it.id == mail.id }
                    if (idx >= 0) BranchCompareFakeRepo.mailbox[idx] = mail.copy(read = !mail.read)
                },
            ) { Text(if (mail.read) "Mark unread" else "Mark read", fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun CmpAuditLog() {
    SectionHeader(
        title = "Audit log",
        subtitle = "Clock, relief, void / unvoid, submit / undo append here with reasons.",
        count = "${BranchCompareFakeRepo.audits.size} entries",
    )
    if (BranchCompareFakeRepo.audits.isEmpty()) {
        ComparePanel(accent = ComparePaperEdge, title = "Quiet so far", meta = "Do something above - it lands here.") {}
    }
    for (entry in BranchCompareFakeRepo.audits) {
        ComparePanel(
            accent = CompareLine,
            title = entry.action,
            meta = "${entry.actor} - ${entry.whenText} - ${entry.record}",
        ) {
            if (entry.reason.isNotEmpty()) {
                Text(text = "Reason: ${entry.reason}", fontSize = 12.sp, color = CompareSoft)
            }
        }
    }
}

@Composable
internal fun CmpProfile(onLogout: () -> Unit) {
    val focused = BranchCompareFakeRepo.focusedBranchId.value
    val branch = BranchCompareFakeRepo.branch(focused)
    SectionHeader(title = "Profile", subtitle = "Shift, branch day, demo controls.")
    ComparePanel(
        accent = SunriseAmber,
        title = BranchCompareFakeRepo.currentUserName.value,
        meta = "Practitioner - ${BranchCompareFakeRepo.branchName(BranchCompareFakeRepo.clockedBranchId.value)}",
    ) {
        val clocked = BranchCompareFakeRepo.clockedIn.value
        Text(
            text = if (clocked) "Clocked in - tap out to end the shift." else "Off shift.",
            fontSize = 13.sp,
            color = CompareBody,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (clocked) {
                CmpGhostButton(
                    label = "Clock out",
                    onClick = {
                        BranchCompareFakeRepo.clockedIn.value = false
                        BranchCompareFakeRepo.log("Clock out", BranchCompareFakeRepo.branchName(focused))
                    },
                )
            } else {
                CmpPrimaryButton(
                    label = "Clock in",
                    onClick = {
                        BranchCompareFakeRepo.clockedIn.value = true
                        BranchCompareFakeRepo.log("Clock in", BranchCompareFakeRepo.branchName(focused))
                    },
                )
            }
            CmpGhostButton(
                label = "Log out",
                onClick = {
                    BranchCompareFakeRepo.clockedIn.value = false
                    onLogout()
                },
            )
        }
    }
    if (branch != null) {
        ComparePanel(
            accent = branchAccent(focused),
            title = "Branch day: ${branch.name}",
            meta = "Flip the day state for the dive branch. Days roll at 04:00 Asia/Manila.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (status in CmpDayStatus.entries) {
                    if (status == branch.dayStatus) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = branchAccent(focused), contentColor = Color.White),
                        ) { Text(status.name, fontSize = 12.sp) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                BranchCompareFakeRepo.setDayStatus(focused, status)
                                BranchCompareFakeRepo.log("Branch day ${status.name}", branch.name)
                            },
                        ) { Text(status.name, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
    ComparePanel(accent = ComparePaperEdge, title = "Demo controls", meta = "Reset restores all fake data.") {
        CmpGhostButton(
            label = "Reset demo data",
            onClick = { BranchCompareFakeRepo.reset() },
        )
    }
}
