package com.companyb.companyapp.proto.belldesk

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
import androidx.compose.ui.unit.dp

private enum class BdPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class BdTab { BELL, ARRIVALS, LEDGER, CLIENTS, TILL, TEAM, MAIL, DAYBOOK, PROFILE }

@Composable
fun ProtoBellDeskApp(onBack: () -> Unit = {}) {
    BellDeskTheme {
        var phase by remember { mutableStateOf(BdPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(WalnutDeep)) {
            when (phase) {
                BdPhase.LOGIN -> BdLogin(
                    onLogin = { phase = BdPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = BdPhase.ONBOARDING_LOCKED },
                )
                BdPhase.ONBOARDING_LOCKED -> BdOnboardingLocked(
                    onGrant = { phase = BdPhase.BRANCH_SELECT },
                    onBack = { phase = BdPhase.LOGIN },
                )
                BdPhase.BRANCH_SELECT -> BdBranchSelect(
                    onPick = { branchId ->
                        BellDeskFakeRepo.clockedBranchId.value = branchId
                        phase = BdPhase.APP
                    },
                    onBack = { phase = BdPhase.LOGIN },
                )
                BdPhase.APP -> BdShell(onLogout = { phase = BdPhase.LOGIN }, onExit = onBack)
            }
        }
    }
}

@Composable
internal fun BdShell(onLogout: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(BdTab.BELL) }
    Column(modifier = Modifier.fillMaxSize()) {
        BdDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BdRail(current = tab, onPick = { tab = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MarbleCream)) {
                when (tab) {
                    BdTab.BELL -> BdBellTab(onOpenArrivals = { tab = BdTab.ARRIVALS })
                    BdTab.ARRIVALS -> BdArrivalsTab()
                    BdTab.LEDGER -> BdLedgerTab()
                    BdTab.CLIENTS -> BdClientsTab()
                    BdTab.TILL -> BdTillTab()
                    BdTab.TEAM -> BdTeamTab()
                    BdTab.MAIL -> BdMailTab()
                    BdTab.DAYBOOK -> BdAuditTab()
                    BdTab.PROFILE -> BdProfileTab(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

@Composable
internal fun BdDayBanner() {
    val day by BellDeskFakeRepo.dayState
    Row(
        modifier = Modifier.fillMaxWidth().background(WalnutCounter).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BELL-DESK", style = MaterialTheme.typography.labelLarge, color = Brass)
        Spacer(Modifier.width(12.dp))
        Text(
            BellDeskFakeRepo.branchName(BellDeskFakeRepo.clockedBranchId.value),
            style = MaterialTheme.typography.titleSmall,
            color = MarbleCream,
        )
        Spacer(Modifier.width(12.dp))
        BdDayState.values().forEach { state ->
            val selected = state == day
            Box(
                modifier = Modifier.padding(end = 6.dp).clip(MaterialTheme.shapes.small)
                    .background(if (selected) Brass else WalnutPanel)
                    .clickable {
                        BellDeskFakeRepo.dayState.value = state
                        BellDeskFakeRepo.stamp(
                            BellDeskFakeRepo.currentUser.value.name,
                            "DAY_STATE",
                            "Branch Day -> $state",
                        )
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    state.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) InkLobby else MarbleCream,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Day boundary 04:00 Asia/Manila",
            style = MaterialTheme.typography.bodySmall,
            color = MarbleCream.copy(alpha = 0.75f),
        )
    }
}

@Composable
internal fun BdRail(current: BdTab, onPick: (BdTab) -> Unit) {
    val unread = BellDeskFakeRepo.mailbox.count { !it.read }
    val waiting = BellDeskFakeRepo.sessions.count {
        it.status == BdSessionStatus.PENDING && it.lane.isEmpty()
    }
    val ringing = BellDeskFakeRepo.sessions.count { it.status == BdSessionStatus.PENDING }
    Column(
        modifier = Modifier.width(212.dp).fillMaxHeight().background(WalnutCounter).padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BdRailHeader("FRONT DESK")
        BdRailItem("Bell (home)", current == BdTab.BELL, null) { onPick(BdTab.BELL) }
        BdRailHeader("WALK-IN FLOW")
        BdRailItem("Arrivals", current == BdTab.ARRIVALS, if (waiting > 0) "$waiting" else null) {
            onPick(BdTab.ARRIVALS)
        }
        BdRailItem("Ledger (sessions)", current == BdTab.LEDGER, if (ringing > 0) "$ringing" else null) {
            onPick(BdTab.LEDGER)
        }
        BdRailHeader("HOUSE")
        BdRailItem("Client book", current == BdTab.CLIENTS, null) { onPick(BdTab.CLIENTS) }
        BdRailItem("Till (finance)", current == BdTab.TILL, null) { onPick(BdTab.TILL) }
        BdRailItem("Staff (team)", current == BdTab.TEAM, null) { onPick(BdTab.TEAM) }
        BdRailItem("Bellbox", current == BdTab.MAIL, if (unread > 0) "$unread" else null) { onPick(BdTab.MAIL) }
        BdRailItem("Daybook (audit)", current == BdTab.DAYBOOK, null) { onPick(BdTab.DAYBOOK) }
        BdRailItem("Profile", current == BdTab.PROFILE, null) { onPick(BdTab.PROFILE) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Ring the bell, pick a lane, seat the guest. Three taps, no queue left standing.",
            style = MaterialTheme.typography.bodySmall,
            color = MarbleCream.copy(alpha = 0.6f),
        )
    }
}

@Composable
internal fun BdRailHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MarbleCream.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun BdRailItem(label: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
            .background(if (selected) BrassDeep else WalnutPanel.copy(alpha = 0.7f))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MarbleCream, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(
                modifier = Modifier.clip(MaterialTheme.shapes.small).background(Brass)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = InkLobby)
            }
        }
    }
}

@Composable
internal fun BdLogin(onLogin: () -> Unit, onOnboardingDemo: () -> Unit) {
    var picked by remember { mutableStateOf(BellDeskFakeRepo.directory[0]) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BELL-DESK · FRONT DESK BELL", style = MaterialTheme.typography.labelLarge, color = Brass)
        Text(
            "The bell never waits. Ring arrivals in, seat them in three taps.",
            style = MaterialTheme.typography.displaySmall,
            color = MarbleCream,
        )
        Text(
            "Fake sign-in. No network, no backend. ONBOARDING stays locked by design.",
            style = MaterialTheme.typography.bodyMedium,
            color = MarbleCream.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.width(540.dp).clip(MaterialTheme.shapes.medium).background(MarbleCard).padding(18.dp),
        ) {
            BellDeskFakeRepo.directory.forEach { user ->
                val selected = user.id == picked.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.small)
                        .background(if (selected) BrassWash else MarbleCard)
                        .border(1.dp, if (selected) BrassDeep else MarbleEdge, MaterialTheme.shapes.small)
                        .clickable { picked = user }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium, color = InkLobby)
                        Text(
                            "${user.role} · home ${BellDeskFakeRepo.branchName(user.homeBranchId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftLobby,
                        )
                    }
                    if (user.onboarding) {
                        Text("LOCKED", style = MaterialTheme.typography.labelMedium, color = BellRed)
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
                            BellDeskFakeRepo.currentUser.value = picked
                            BellDeskFakeRepo.stamp(picked.name, "LOGIN", "Fake sign-in as ${picked.role}")
                            onLogin()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (picked.onboarding) "Inspect locked account" else "Ring in as ${picked.name}")
                }
                OutlinedButton(onClick = onOnboardingDemo) { Text("ONBOARDING demo") }
            }
        }
    }
}

@Composable
internal fun BdOnboardingLocked(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ONBOARDING · BELL KEPT SILENT", style = MaterialTheme.typography.labelLarge, color = Brass)
        Text("No taps until a MANAGER grants a role.", style = MaterialTheme.typography.displaySmall, color = MarbleCream)
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.width(540.dp).clip(MaterialTheme.shapes.medium).background(MarbleCard).padding(18.dp),
        ) {
            Text("0 capabilities. Every drawer shut.", style = MaterialTheme.typography.titleMedium, color = InkLobby)
            Text(
                "The locked hire can watch the bell but cannot ring it, claim arrivals, " +
                    "touch the till, or read the client book. A MANAGER grant via MANAGE_USERS " +
                    "unlocks the counter.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoftLobby,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        BellDeskFakeRepo.currentUser.value = BellDeskFakeRepo.directory.first { !it.onboarding }
                        BellDeskFakeRepo.stamp("M. Sy", "GRANT_ROLE", "ONBOARDING -> Practitioner (demo)")
                        onGrant()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Simulate MANAGE_USERS grant")
                }
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

@Composable
internal fun BdBranchSelect(onPick: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CHOOSE YOUR COUNTER", style = MaterialTheme.typography.labelLarge, color = Brass)
        Text("Three doors, one bell.", style = MaterialTheme.typography.displaySmall, color = MarbleCream)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BellDeskFakeRepo.branches.forEach { branch ->
                val waiting = BellDeskFakeRepo.sessions.count {
                    it.branchId == branch.id && it.status == BdSessionStatus.PENDING
                }
                Column(
                    modifier = Modifier.width(240.dp).clip(MaterialTheme.shapes.medium).background(MarbleCard)
                        .clickable {
                            BellDeskFakeRepo.stamp(
                                BellDeskFakeRepo.currentUser.value.name,
                                "BRANCH_SELECT",
                                branch.name,
                            )
                            onPick(branch.id)
                        }.padding(16.dp),
                ) {
                    Text(branch.kind, style = MaterialTheme.typography.labelMedium, color = BrassDeep)
                    Text(branch.name, style = MaterialTheme.typography.titleLarge, color = InkLobby)
                    Text(
                        "$waiting bells waiting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoftLobby,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Open counter →", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onBack) { Text("Back to sign-in", color = MarbleCream) }
    }
}

@Composable
internal fun BdBellTab(onOpenArrivals: () -> Unit) {
    val repo = BellDeskFakeRepo
    val me = repo.currentUser.value
    val clocked = repo.clockedIn.value
    val branchId = repo.clockedBranchId.value
    var arrivalId by remember { mutableStateOf<String?>(null) }
    var lane by remember { mutableStateOf<String?>(null) }
    var ringNote by remember { mutableStateOf("") }
    val waiting = repo.sessions.filter { it.branchId == branchId && it.status == BdSessionStatus.PENDING && it.lane.isEmpty() }
    val seated = repo.sessions.filter { it.branchId == branchId && it.status == BdSessionStatus.PENDING && it.lane.isNotEmpty() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BELL COUNTER · ${repo.branchName(branchId)}", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
                Text(
                    if (clocked) "Clocked in as ${me.name} (${me.role}). The bell is live."
                    else "Clock in to wake the bell. Guests are already at the door.",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkLobby,
                )
            }
            if (clocked) {
                OutlinedButton(onClick = {
                    repo.clockedIn.value = false
                    repo.stamp(me.name, "CLOCK_OUT", "Bell counter ${repo.branchName(branchId)}")
                }) { Text("Clock out") }
            } else {
                Button(
                    onClick = {
                        repo.clockedIn.value = true
                        repo.stamp(me.name, "CLOCK_IN", "Bell counter ${repo.branchName(branchId)}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                ) { Text("Clock in") }
            }
        }
        Spacer(Modifier.height(12.dp))
        BdCard(title = "RING THE BELL · NEW WALK-IN") {
            Text(
                "One tap stamps a fresh PENDING walk-in. Walk-ins skip NO_SHOW and CANCELLED " +
                    "in this house rule note: they either get seated or voided with a reason.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoftLobby,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val no = repo.nextBell()
                        repo.sessions.add(
                            0,
                            BdSession(
                                id = "s-$no",
                                bellNo = no,
                                clientName = "Walk-in $no",
                                branchId = branchId,
                                status = BdSessionStatus.PENDING,
                                service = "Walk-in",
                                price = 900,
                                walkIn = true,
                                time = "now",
                            ),
                        )
                        repo.stamp(me.name, "BELL_RING", "Walk-in $no")
                        ringNote = "Bell $no is on the counter. Tap it below, pick a lane, ring to seat."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                ) { Text("Ring! New walk-in") }
                OutlinedButton(onClick = onOpenArrivals) { Text("Open arrivals ($ringNote)".trim()) }
            }
            if (ringNote.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(ringNote, style = MaterialTheme.typography.bodyMedium, color = InkLobby)
            }
        }
        Spacer(Modifier.height(12.dp))
        BdCard(title = "THREE TAPS · ARRIVAL → LANE → RING") {
            Text("Tap 1 · arrival", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (waiting.isEmpty()) Text("No unseated arrivals. Ring the bell.", style = MaterialTheme.typography.bodyMedium)
                waiting.take(6).forEach { s ->
                    val sel = arrivalId == s.id
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (sel) BrassDeep else BrassWash)
                            .border(1.dp, BrassDeep, MaterialTheme.shapes.small)
                            .clickable { arrivalId = s.id }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${s.bellNo} ${s.clientName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) Color.White else InkLobby,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap 2 · lane", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repo.lanes.forEach { l ->
                    val sel = lane == l
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small)
                            .background(if (sel) BrassDeep else MarbleCream)
                            .border(1.dp, BrassDeep, MaterialTheme.shapes.small)
                            .clickable { lane = l }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(l, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else InkLobby)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap 3 · ring to seat", style = MaterialTheme.typography.labelLarge, color = BrassDeep)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = arrivalId != null && lane != null,
                    onClick = {
                        val id = arrivalId ?: return@Button
                        val seat = lane ?: return@Button
                        repo.updateSession(id) { it.copy(lane = seat) }
                        val rung = repo.sessions.firstOrNull { it.id == id }
                        repo.stamp(me.name, "ASSIGN", "${rung?.bellNo ?: id} -> $seat")
                        arrivalId = null
                        lane = null
                        ringNote = "Seated ${rung?.bellNo ?: ""} at $seat."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrassDeep),
                ) { Text("Ring! Seat guest") }
                Text(
                    if (arrivalId == null || lane == null) "Pick an arrival and a lane first."
                    else "Ready: ${waiting.firstOrNull { it.id == arrivalId }?.bellNo ?: ""} → $lane",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoftLobby,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Seated now (${seated.size}): " + seated.take(4).joinToString { "${it.bellNo}@${it.lane}" },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoftLobby,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BdMiniCard("Relief invites", "${repo.invites.count { it.state == "OPEN" }} open", Modifier.weight(1f)) {
                repo.invites.filter { it.state == "OPEN" }.take(2).forEach { inv ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${inv.branchName} · ${inv.day}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BdSmallButton("Accept") {
                            val i = repo.invites.indexOfFirst { it.id == inv.id }
                            if (i >= 0) repo.invites[i] = inv.copy(state = "ACCEPTED")
                            repo.stamp(me.name, "RELIEF_ACCEPT", inv.branchName)
                        }
                        BdSmallButton("Decline") {
                            val i = repo.invites.indexOfFirst { it.id == inv.id }
                            if (i >= 0) repo.invites[i] = inv.copy(state = "DECLINED")
                            repo.stamp(me.name, "RELIEF_DECLINE", inv.branchName)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            BdMiniCard("Relief requests", "${repo.requests.count { it.state == "OPEN" }} open", Modifier.weight(1f)) {
                repo.requests.filter { it.state == "OPEN" }.take(3).forEach { req ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${req.branchName} · ${req.day}${if (req.mine) " (mine)" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (req.mine) {
                            BdSmallButton("Withdraw") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "WITHDRAWN")
                                repo.stamp(me.name, "RELIEF_WITHDRAW", req.branchName)
                            }
                        } else {
                            BdSmallButton("Grant") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "GRANTED")
                                repo.stamp(me.name, "RELIEF_GRANT", req.branchName)
                            }
                            BdSmallButton("Deny") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "DENIED")
                                repo.stamp(me.name, "RELIEF_DENY", req.branchName)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                BdSmallButton("Ask another branch") {
                    repo.requests.add(0, BdRequest("q-${repo.requests.size + 1}", "Tondo Medical Mission", "Sun", mine = true))
                    repo.stamp(me.name, "RELIEF_REQUEST", "Tondo Medical Mission Sun")
                }
            }
        }
    }
}

@Composable
internal fun BdCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MarbleCard)
            .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
internal fun BdMiniCard(title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).background(MarbleCard)
            .border(1.dp, MarbleEdge, MaterialTheme.shapes.medium).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = BrassDeep)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSoftLobby)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
internal fun BdSmallButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.small).background(BrassWash)
            .border(1.dp, BrassDeep, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkLobby)
    }
}
