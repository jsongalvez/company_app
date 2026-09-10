package com.companyb.companyapp.proto.queuedesk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

private enum class QdPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class QdTab { COUNTER, TRIAGE, TRAY, DONE, CLIENTS, VAULT, HANDS, PIGEONHOLE, DAYBOOK, PROFILE }

@Composable
fun ProtoQueueDeskApp(onBack: () -> Unit = {}) {
    QueueDeskTheme {
        var phase by remember { mutableStateOf(QdPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(BeltDeep)) {
            when (phase) {
                QdPhase.LOGIN -> QdLogin(
                    onLogin = { phase = QdPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = QdPhase.ONBOARDING_LOCKED },
                )
                QdPhase.ONBOARDING_LOCKED -> QdOnboardingLocked(
                    onGrant = { phase = QdPhase.BRANCH_SELECT },
                    onBack = { phase = QdPhase.LOGIN },
                )
                QdPhase.BRANCH_SELECT -> QdBranchSelect(
                    onPick = { branchId ->
                        QueueDeskFakeRepo.clockedBranchId.value = branchId
                        phase = QdPhase.APP
                    },
                    onBack = { phase = QdPhase.LOGIN },
                )
                QdPhase.APP -> QdShell(onLogout = { phase = QdPhase.LOGIN }, onExit = onBack)
            }
        }
    }
}

@Composable
internal fun QdShell(onLogout: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(QdTab.COUNTER) }
    Column(modifier = Modifier.fillMaxSize()) {
        QdDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            QdRail(current = tab, onPick = { tab = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(BoardCream)) {
                when (tab) {
                    QdTab.COUNTER -> QdCounterTab(onOpenTriage = { tab = QdTab.TRIAGE })
                    QdTab.TRIAGE -> QdTriageTab()
                    QdTab.TRAY -> QdTrayTab()
                    QdTab.DONE -> QdDoneTab()
                    QdTab.CLIENTS -> QdClientsTab()
                    QdTab.VAULT -> QdVaultTab()
                    QdTab.HANDS -> QdTeamTab()
                    QdTab.PIGEONHOLE -> QdMailboxTab()
                    QdTab.DAYBOOK -> QdAuditTab()
                    QdTab.PROFILE -> QdProfileTab(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

@Composable
internal fun QdDayBanner() {
    val day by QueueDeskFakeRepo.dayState
    Row(
        modifier = Modifier.fillMaxWidth().background(BeltDark).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("QUEUE-DESK", style = MaterialTheme.typography.labelLarge, color = SlipPending)
        Spacer(Modifier.width(12.dp))
        Text(
            QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value),
            style = MaterialTheme.typography.titleSmall,
            color = TicketPaper,
        )
        Spacer(Modifier.width(12.dp))
        QdDayState.values().forEach { state ->
            val selected = state == day
            Box(
                modifier = Modifier.padding(end = 6.dp).clip(MaterialTheme.shapes.small)
                    .background(if (selected) SlipPending else BeltSteel)
                    .clickable {
                        QueueDeskFakeRepo.dayState.value = state
                        QueueDeskFakeRepo.stamp(
                            QueueDeskFakeRepo.currentUser.value.name,
                            "DAY_STATE",
                            "Branch Day -> $state",
                        )
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(state.name, style = MaterialTheme.typography.labelMedium, color = if (selected) InkQueue else TicketPaper)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Day boundary 04:00 Asia/Manila",
            style = MaterialTheme.typography.bodySmall,
            color = TicketPaper.copy(alpha = 0.75f),
        )
    }
}

@Composable
internal fun QdRail(current: QdTab, onPick: (QdTab) -> Unit) {
    val unread = QueueDeskFakeRepo.mailbox.count { !it.read }
    val myCount = QueueDeskFakeRepo.sessions.count {
        it.status == QdSessionStatus.PENDING && it.claimedBy == QueueDeskFakeRepo.currentUser.value.name
    }
    val inboxCount = QueueDeskFakeRepo.sessions.count {
        it.status == QdSessionStatus.PENDING && it.claimedBy.isEmpty()
    }
    Column(
        modifier = Modifier.width(208.dp).fillMaxHeight().background(BeltDark).padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        QdRailHeader("SORTING")
        QdRailItem("Counter (home)", current == QdTab.COUNTER, null) { onPick(QdTab.COUNTER) }
        QdRailHeader("WORK QUEUE")
        QdRailItem("Triage inbox", current == QdTab.TRIAGE, if (inboxCount > 0) "$inboxCount" else null) { onPick(QdTab.TRIAGE) }
        QdRailItem("My tray", current == QdTab.TRAY, if (myCount > 0) "$myCount" else null) { onPick(QdTab.TRAY) }
        QdRailItem("Done pile", current == QdTab.DONE, null) { onPick(QdTab.DONE) }
        QdRailHeader("HOUSE")
        QdRailItem("Client index", current == QdTab.CLIENTS, null) { onPick(QdTab.CLIENTS) }
        QdRailItem("Vault (finance)", current == QdTab.VAULT, null) { onPick(QdTab.VAULT) }
        QdRailItem("Hands (team)", current == QdTab.HANDS, null) { onPick(QdTab.HANDS) }
        QdRailItem("Pigeonhole", current == QdTab.PIGEONHOLE, if (unread > 0) "$unread" else null) { onPick(QdTab.PIGEONHOLE) }
        QdRailItem("Daybook (audit)", current == QdTab.DAYBOOK, null) { onPick(QdTab.DAYBOOK) }
        QdRailItem("Profile", current == QdTab.PROFILE, null) { onPick(QdTab.PROFILE) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Triage, claim-next, done pile. Nothing leaves the belt unclaimed.",
            style = MaterialTheme.typography.bodySmall,
            color = TicketPaper.copy(alpha = 0.6f),
        )
    }
}

@Composable
internal fun QdRailHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = TicketPaper.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun QdRailItem(label: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
            .background(if (selected) ClaimBlue else BeltSteel.copy(alpha = 0.55f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TicketPaper, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(SlipPending).padding(horizontal = 7.dp, vertical = 2.dp)) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = InkQueue)
            }
        }
    }
}

@Composable
internal fun QdLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var picked by remember { mutableStateOf(QueueDeskFakeRepo.directory[0]) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("QUEUE-DESK", style = MaterialTheme.typography.labelLarge, color = SlipPending)
        Text("Morning sort: sign the roster, take the belt.", style = MaterialTheme.typography.displaySmall, color = TicketPaper)
        Text(
            "Fake sign-in. No network, no backend. ONBOARDING stays locked by design.",
            style = MaterialTheme.typography.bodyMedium,
            color = TicketPaper.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(18.dp))
        Column(modifier = Modifier.width(520.dp).clip(MaterialTheme.shapes.medium).background(TicketPaper).padding(18.dp)) {
            QueueDeskFakeRepo.directory.forEach { user ->
                val selected = user.id == picked.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.small)
                        .background(if (selected) Color(0xFFD8E6FF) else TicketPaper)
                        .border(1.dp, if (selected) ClaimBlue else TicketEdge, MaterialTheme.shapes.small)
                        .clickable { picked = user }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium, color = InkQueue)
                        Text("${user.role} · home ${QueueDeskFakeRepo.branchName(user.homeBranchId)}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    if (user.onboarding) {
                        Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = StampRed)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (picked.onboarding) {
                            onOnboardingDemo()
                        } else {
                            QueueDeskFakeRepo.currentUser.value = picked
                            QueueDeskFakeRepo.stamp(picked.name, "LOGIN", "Fake sign-in as ${picked.role}")
                            onLogin()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClaimBlue),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (picked.onboarding) "Inspect locked account" else "Sign in as ${picked.name}")
                }
                OutlinedButton(onClick = onOnboardingDemo) { Text("ONBOARDING demo") }
            }
        }
    }
}

@Composable
internal fun QdOnboardingLocked(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ONBOARDING · 0 CAPABILITIES", style = MaterialTheme.typography.labelLarge, color = StampRed)
        Text("This badge opens no doors.", style = MaterialTheme.typography.displaySmall, color = TicketPaper)
        Text(
            "A fresh account holds an empty role bundle: branch assignment alone grants nothing. " +
                "The belt, the vault, and the daybook stay shut until MANAGE_USERS grants a real role.",
            style = MaterialTheme.typography.bodyMedium,
            color = TicketPaper.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.width(460.dp).clip(MaterialTheme.shapes.medium).background(TicketPaper).padding(18.dp)) {
            Text("Locked drawers: Triage inbox · My tray · Vault · Hands · Pigeonhole", style = MaterialTheme.typography.bodyMedium, color = InkQueue)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    QueueDeskFakeRepo.stamp("M. Sy", "GRANT_ROLE", "New Hire -> Practitioner")
                    QueueDeskFakeRepo.currentUser.value = QueueDeskFakeRepo.directory[1]
                    onGrant()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StampGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Simulate MANAGE_USERS grant (Practitioner)")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign-in") }
        }
    }
}

@Composable
internal fun QdBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PICK YOUR COUNTER", style = MaterialTheme.typography.labelLarge, color = SlipPending)
        Text("One branch, one belt.", style = MaterialTheme.typography.displaySmall, color = TicketPaper)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QueueDeskFakeRepo.branches.forEach { branch ->
                Column(
                    modifier = Modifier.width(280.dp).clip(MaterialTheme.shapes.medium).background(TicketPaper)
                        .clickable { onPick(branch.id) }.padding(16.dp),
                ) {
                    Text(branch.kind, style = MaterialTheme.typography.labelMedium, color = ClaimBlue)
                    Text(branch.name, style = MaterialTheme.typography.titleLarge, color = InkQueue)
                    val open = QueueDeskFakeRepo.sessions.count { it.branchId == branch.id && it.status == QdSessionStatus.PENDING }
                    Text("$open tickets waiting on the belt", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    Spacer(Modifier.height(8.dp))
                    Text("TAKE THIS COUNTER", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text("Back", color = TicketPaper) }
    }
}

@Composable
internal fun QdCounterTab(onOpenTriage: () -> Unit) {
    val me = QueueDeskFakeRepo.currentUser.value
    val clocked by QueueDeskFakeRepo.clockedIn
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("COUNTER · ${me.name} (${me.role})", style = MaterialTheme.typography.labelLarge, color = ClaimBlue)
                Text("Clock in, work the belt, hand off clean.", style = MaterialTheme.typography.titleLarge, color = InkQueue)
            }
            if (clocked) {
                Button(onClick = {
                    QueueDeskFakeRepo.clockedIn.value = false
                    QueueDeskFakeRepo.stamp(me.name, "CLOCK_OUT", QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value))
                }, colors = ButtonDefaults.buttonColors(containerColor = StampRed)) { Text("Clock out") }
            } else {
                Button(onClick = {
                    QueueDeskFakeRepo.clockedIn.value = true
                    QueueDeskFakeRepo.stamp(me.name, "CLOCK_IN", QueueDeskFakeRepo.branchName(QueueDeskFakeRepo.clockedBranchId.value))
                }, colors = ButtonDefaults.buttonColors(containerColor = StampGreen)) { Text("Clock in") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QdStatCard("Triage inbox", "${QueueDeskFakeRepo.sessions.count { it.status == QdSessionStatus.PENDING && it.claimedBy.isEmpty() }}", "Unclaimed tickets")
            QdStatCard("My tray", "${QueueDeskFakeRepo.sessions.count { it.status == QdSessionStatus.PENDING && it.claimedBy == me.name }}", "Claimed by you")
            QdStatCard("Done pile", "${QueueDeskFakeRepo.sessions.count { it.status != QdSessionStatus.PENDING }}", "Finished today")
            QdStatCard("Unread mail", "${QueueDeskFakeRepo.mailbox.count { !it.read }}", "Pigeonhole")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenTriage, colors = ButtonDefaults.buttonColors(containerColor = ClaimBlue)) { Text("Open triage inbox") }
        Spacer(Modifier.height(16.dp))
        Text("RELIEF COUNTER", style = MaterialTheme.typography.labelLarge, color = InkQueue)
        Text(
            "Relief duty starts view-only at a non-home branch; edit needs a grant. " +
                "Requests are outsider-initiated broadcasts (any branch member grants); invites are branch-initiated offers. " +
                "Duty and pay expire at 04:00 Asia/Manila; relief pay comes from this branch drawer.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
        Spacer(Modifier.height(8.dp))
        Text("Invites for you", style = MaterialTheme.typography.titleSmall, color = InkQueue)
        QueueDeskFakeRepo.invites.forEach { invite ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.small)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.small).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${invite.branchName} · ${invite.day}", style = MaterialTheme.typography.bodyMedium, color = InkQueue)
                    Text("State: ${invite.state}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                if (invite.state == "OPEN") {
                    OutlinedButton(onClick = {
                        inviteShift("i", invite.id, "ACCEPTED", me.name)
                    }) { Text("Accept") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = {
                        inviteShift("i", invite.id, "DECLINED", me.name)
                    }) { Text("Decline") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Relief requests on this belt", style = MaterialTheme.typography.titleSmall, color = InkQueue)
        QueueDeskFakeRepo.requests.forEach { request ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.small)
                    .background(Color.White).border(1.dp, TicketEdge, MaterialTheme.shapes.small).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${request.branchName} · ${request.day}${if (request.mine) " · yours" else ""}", style = MaterialTheme.typography.bodyMedium, color = InkQueue)
                    Text("State: ${request.state}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                if (request.state == "OPEN") {
                    if (request.mine) {
                        OutlinedButton(onClick = { inviteShift("q", request.id, "WITHDRAWN", me.name) }) { Text("Withdraw") }
                    } else {
                        OutlinedButton(onClick = { inviteShift("q", request.id, "GRANTED", me.name) }) { Text("Grant") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { inviteShift("q", request.id, "DENIED", me.name) }) { Text("Deny") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            QueueDeskFakeRepo.requests.add(0, QdReliefRequest("q-${QueueDeskFakeRepo.requests.size + 10}", "Tondo Medical Mission", "Sun", mine = true))
            QueueDeskFakeRepo.stamp(me.name, "RELIEF_REQUEST", "Tondo Medical Mission / Sun")
        }) { Text("Ask another branch (new relief request)") }
    }
}

private fun inviteShift(list: String, id: String, state: String, actor: String) {
    if (list == "i") {
        val index = QueueDeskFakeRepo.invites.indexOfFirst { it.id == id }
        if (index >= 0) QueueDeskFakeRepo.invites[index] = QueueDeskFakeRepo.invites[index].copy(state = state)
        QueueDeskFakeRepo.stamp(actor, "RELIEF_INVITE_$state", "$id / ${QueueDeskFakeRepo.invites.getOrNull(index)?.branchName}")
    } else {
        val index = QueueDeskFakeRepo.requests.indexOfFirst { it.id == id }
        if (index >= 0) QueueDeskFakeRepo.requests[index] = QueueDeskFakeRepo.requests[index].copy(state = state)
        QueueDeskFakeRepo.stamp(actor, "RELIEF_REQUEST_$state", "$id / ${QueueDeskFakeRepo.requests.getOrNull(index)?.branchName}")
    }
}

@Composable
internal fun QdStatCard(title: String, value: String, sub: String) {
    Column(
        modifier = Modifier.width(200.dp).clip(MaterialTheme.shapes.medium).background(Color.White)
            .border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(14.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = InkSoft)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = InkQueue)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

@Composable
internal fun QdSectionCard(title: String, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White)
            .border(1.dp, TicketEdge, MaterialTheme.shapes.medium).padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = InkQueue)
        Spacer(Modifier.height(8.dp))
        body()
    }
}
