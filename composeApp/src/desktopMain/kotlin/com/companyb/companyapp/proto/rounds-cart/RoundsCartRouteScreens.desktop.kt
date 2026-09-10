package com.companyb.companyapp.proto.roundscart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
internal fun RcRouteTab(onOpenChart: () -> Unit) {
    val repo = RoundsCartFakeRepo
    val me = repo.currentUser.value
    val clockedIn by repo.clockedIn
    val next = repo.nextStop()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("ROUTE · NEXT CLIENT FIRST", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text(
            "Stop order is the schedule. Finish the stop in front of you.",
            style = MaterialTheme.typography.titleLarge,
            color = InkChart,
        )
        Spacer(Modifier.height(10.dp))
        if (next != null) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(CartSteel)
                    .padding(16.dp),
            ) {
                Text("NEXT UP · STOP ${next.stopNo} · ${next.time}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FD8D2))
                Text(next.clientName, style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "${next.service} · ₱${next.price}" + if (next.walkIn) " · walk-in" else " · booked",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB9C6C4),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RcSmallButton("Clip chart") {
                        repo.chartSessionId.value = next.id
                        onOpenChart()
                    }
                    RcSmallButton("Complete stop") {
                        repo.markSession(next.id, RcSessionStatus.COMPLETED)
                        repo.stamp(me.name, "COMPLETE_SESSION", "${next.id} ${next.clientName}")
                    }
                }
            }
        } else {
            RcCard("ROUTE CLEAR") {
                Text("No PENDING stops at this Branch. The cart is parked.", color = InkChart)
            }
        }
        Spacer(Modifier.height(10.dp))
        RcCard("CLOCK + RELIEF DUTY") {
            val branch = repo.branchName(repo.clockedBranchId.value)
            Text(
                if (clockedIn) "Clocked in at $branch as ${me.name}." else "Off the clock — clock in to push the cart at $branch.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkChart,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clockedIn) {
                    RcSmallButton("Clock out") {
                        repo.clockedIn.value = false
                        repo.stamp(me.name, "CLOCK_OUT", branch)
                    }
                } else {
                    RcSmallButton("Clock in here") {
                        repo.clockedIn.value = true
                        repo.stamp(me.name, "CLOCK_IN", branch)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Relief invites (branch-initiated, accept to write the day grant):", style = MaterialTheme.typography.titleSmall, color = InkChart)
            repo.invites.forEach { invite ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${invite.branchName} · ${invite.day} · ${invite.state}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkChart,
                    )
                    if (invite.state == "OPEN") {
                        RcGhostButton("Accept") {
                            val i = repo.invites.indexOfFirst { it.id == invite.id }
                            if (i >= 0) repo.invites[i] = invite.copy(state = "ACCEPTED")
                            repo.stamp(me.name, "ACCEPT_INVITE", "${invite.branchName} ${invite.day}")
                        }
                        Spacer(Modifier.padding(3.dp))
                        RcGhostButton("Decline") {
                            val i = repo.invites.indexOfFirst { it.id == invite.id }
                            if (i >= 0) repo.invites[i] = invite.copy(state = "DECLINED")
                            repo.stamp(me.name, "DECLINE_INVITE", "${invite.branchName} ${invite.day}")
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Relief requests (broadcast to the whole Branch — any member grants):", style = MaterialTheme.typography.titleSmall, color = InkChart)
            repo.requests.forEach { req ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${req.branchName} · ${req.day} · ${req.state}" + if (req.mine) " · mine" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkChart,
                    )
                    if (req.state == "OPEN") {
                        if (req.mine) {
                            RcGhostButton("Withdraw") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "WITHDRAWN")
                                repo.stamp(me.name, "WITHDRAW_REQUEST", "${req.branchName} ${req.day}")
                            }
                        } else {
                            RcGhostButton("Grant") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "GRANTED")
                                repo.stamp(me.name, "GRANT_RELIEF", "${req.branchName} ${req.day}")
                            }
                            Spacer(Modifier.padding(3.dp))
                            RcGhostButton("Deny") {
                                val i = repo.requests.indexOfFirst { it.id == req.id }
                                if (i >= 0) repo.requests[i] = req.copy(state = "DENIED")
                                repo.stamp(me.name, "DENY_REQUEST", "${req.branchName} ${req.day}")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            RcGhostButton("Ask another Branch for relief") {
                repo.requests.add(RcRequest("q-${repo.requests.size + 1}", "Tondo Medical Mission", "tomorrow", mine = true))
                repo.stamp(me.name, "REQUEST_RELIEF", "Tondo Medical Mission tomorrow")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Relief Duty starts view-only; a grant (invite accept or request grant) unlocks edits. " +
                    "Expires 04:00 Asia/Manila next day.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoftChart,
            )
        }
        Spacer(Modifier.height(10.dp))
        RcCard("STOPS AT ${repo.branchName(repo.clockedBranchId.value).uppercase()}") {
            repo.routeStops().forEach { stop ->
                val charted = stop.id == repo.chartSessionId.value
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (charted) CartTealWash else Color.Transparent)
                        .border(1.dp, if (charted) CartTeal else ChartEdge, MaterialTheme.shapes.small)
                        .clickable { repo.chartSessionId.value = stop.id }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.clip(MaterialTheme.shapes.small).background(CartSteel)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(stop.stopNo, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Spacer(Modifier.padding(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stop.clientName, style = MaterialTheme.typography.bodyLarge, color = InkChart)
                        Text(
                            "${stop.service} · ${stop.status}" + if (stop.voided) " · VOID" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoftChart,
                        )
                    }
                    if (stop.status == RcSessionStatus.PENDING) {
                        RcGhostButton("Done") {
                            repo.markSession(stop.id, RcSessionStatus.COMPLETED)
                            repo.stamp(me.name, "COMPLETE_SESSION", "${stop.id} ${stop.clientName}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RcChartTab() {
    val repo = RoundsCartFakeRepo
    val me = repo.currentUser.value
    var filter by remember { mutableStateOf<RcSessionStatus?>(null) }
    var newName by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("") }
    val charted = repo.sessions.firstOrNull { it.id == repo.chartSessionId.value }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("CHART · CLIPPED TO THE CART", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("One chart at hand, the full census below.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Text(
            "House rule: walk-in Sessions cannot be marked NO_SHOW or CANCELLED — they were never booked. " +
                "Void keeps the record visible with a reason; unvoid restores it.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftChart,
        )
        Spacer(Modifier.height(8.dp))
        if (charted != null) {
            RcCard("CHART AT HAND · STOP ${charted.stopNo} ${charted.time}") {
                Text(charted.clientName, style = MaterialTheme.typography.titleLarge, color = InkChart)
                Text(
                    "${charted.service} · ₱${charted.price} · ${charted.status}" +
                        (if (charted.walkIn) " · walk-in" else " · booked") +
                        (if (charted.voided) " · VOID (${charted.voidReason})" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkChart,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (charted.status == RcSessionStatus.PENDING) {
                        RcSmallButton("Complete") {
                            repo.markSession(charted.id, RcSessionStatus.COMPLETED)
                            repo.stamp(me.name, "COMPLETE_SESSION", "${charted.id} ${charted.clientName}")
                        }
                        if (!charted.walkIn) {
                            RcGhostButton("No-show") {
                                repo.markSession(charted.id, RcSessionStatus.NO_SHOW)
                                repo.stamp(me.name, "NO_SHOW_SESSION", charted.id)
                            }
                            RcGhostButton("Cancel") {
                                repo.markSession(charted.id, RcSessionStatus.CANCELLED)
                                repo.stamp(me.name, "CANCEL_SESSION", charted.id)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (!charted.voided) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = voidReason,
                            onValueChange = { voidReason = it },
                            label = { Text("Void reason") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        RcSmallButton("Void") {
                            if (voidReason.isNotBlank()) {
                                val i = repo.sessions.indexOfFirst { it.id == charted.id }
                                if (i >= 0) repo.sessions[i] = charted.copy(voided = true, voidReason = voidReason)
                                repo.stamp(me.name, "VOID_SESSION", charted.id, voidReason)
                                voidReason = ""
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Voided: ${charted.voidReason}", modifier = Modifier.weight(1f), color = InkChart)
                        RcGhostButton("Unvoid") {
                            val i = repo.sessions.indexOfFirst { it.id == charted.id }
                            if (i >= 0) repo.sessions[i] = charted.copy(voided = false, voidReason = "")
                            repo.stamp(me.name, "UNVOID_SESSION", charted.id)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RcGhostButton(if (filter == null) "[ALL]" else "ALL") { filter = null }
            RcSessionStatus.entries.forEach { status ->
                RcGhostButton(if (filter == status) "[$status]" else status.name) { filter = status }
            }
        }
        Spacer(Modifier.height(8.dp))
        repo.sessions.filter { filter == null || it.status == filter }.forEach { session ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(MaterialTheme.shapes.small).background(ChartCard)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.small)
                    .clickable { repo.chartSessionId.value = session.id }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${session.stopNo} · ${session.clientName}", style = MaterialTheme.typography.bodyLarge, color = InkChart)
                    Text(
                        "${session.service} · ${session.status}" + if (session.voided) " · VOID" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoftChart,
                    )
                }
                Text(session.time, style = MaterialTheme.typography.labelMedium, color = CartTealDeep)
            }
        }
        Spacer(Modifier.height(8.dp))
        RcCard("NEW WALK-IN STOP") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Client name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                RcSmallButton("Add stop") {
                    if (newName.isNotBlank()) {
                        val no = (repo.sessions.size + 1).toString().padStart(2, '0')
                        val added = RcSession(
                            "s-${110 + repo.sessions.size}", no, newName.trim(),
                            repo.clockedBranchId.value, RcSessionStatus.PENDING,
                            "Triage + eval", 800, walkIn = true, time = "12:00",
                        )
                        repo.sessions.add(added)
                        repo.stamp(me.name, "CREATE_SESSION", "${added.id} ${added.clientName} walk-in")
                        newName = ""
                    }
                }
            }
        }
    }
}

@Composable
internal fun RcClientsTab() {
    val repo = RoundsCartFakeRepo
    var query by remember { mutableStateOf("") }
    var showMasked by remember { mutableStateOf(true) }
    val shown = repo.clients.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("CLIENTS · GLOBAL BOOK", style = MaterialTheme.typography.labelLarge, color = CartTealDeep)
        Text("One person, every Branch. The cart borrows the book.", style = MaterialTheme.typography.titleLarge, color = InkChart)
        Text(
            "Rule: a Client holds at most one PENDING Session at a time — a second booking waits. " +
                "Anonymized records keep gender + age for reporting, PII nullified.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoftChart,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search the book") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anonymized view", modifier = Modifier.weight(1f), color = InkChart)
            RcGhostButton(if (showMasked) "Masked: ON" else "Masked: OFF") { showMasked = !showMasked }
        }
        Spacer(Modifier.height(6.dp))
        shown.forEach { client ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(MaterialTheme.shapes.small).background(ChartCard)
                    .border(1.dp, ChartEdge, MaterialTheme.shapes.small).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (client.anonymized && showMasked) "File ${client.id} (masked)" else client.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkChart,
                    )
                    if (client.hasPending) {
                        Box(modifier = Modifier.clip(MaterialTheme.shapes.small).background(CartAmberWash).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text("PENDING", style = MaterialTheme.typography.labelMedium, color = CartAmber)
                        }
                    }
                }
                Text(
                    if (client.anonymized && showMasked) "PII nullified · ${client.genderAge}" else "${client.detail} · ${client.genderAge}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoftChart,
                )
            }
        }
    }
}
