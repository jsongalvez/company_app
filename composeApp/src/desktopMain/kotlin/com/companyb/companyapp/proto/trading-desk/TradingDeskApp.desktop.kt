package com.companyb.companyapp.proto.tradingdesk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

private enum class TdPhase { LOGIN, ONBOARDING_LOCKED, BRANCH_SELECT, APP }

enum class TdTab { DESK, BOARD, CLIENTS, BLOTTER, CREW, WIRE, LEDGER, TERMINAL }

@Composable
fun ProtoTradingDeskApp(onBack: () -> Unit = {}) {
    TradingDeskTheme {
        var phase by remember { mutableStateOf(TdPhase.LOGIN) }
        Box(modifier = Modifier.fillMaxSize().background(PitBlack)) {
            when (phase) {
                TdPhase.LOGIN -> TdLogin(
                    onLogin = { phase = TdPhase.BRANCH_SELECT },
                    onOnboardingDemo = { phase = TdPhase.ONBOARDING_LOCKED },
                )
                TdPhase.ONBOARDING_LOCKED -> TdOnboardingLocked(
                    onGrant = { phase = TdPhase.BRANCH_SELECT },
                    onBack = { phase = TdPhase.LOGIN },
                )
                TdPhase.BRANCH_SELECT -> TdBranchSelect(
                    onPick = { branchId ->
                        TradingDeskFakeRepo.clockedBranchId.value = branchId
                        phase = TdPhase.APP
                    },
                    onBack = { phase = TdPhase.LOGIN },
                )
                TdPhase.APP -> TdShell(onLogout = { phase = TdPhase.LOGIN }, onExit = onBack)
            }
        }
    }
}

@Composable
internal fun TdShell(onLogout: () -> Unit, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(TdTab.DESK) }
    Column(modifier = Modifier.fillMaxSize()) {
        TdTickerTape()
        TdDayBanner()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TdRail(current = tab, onPick = { tab = it })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(PitBlack)) {
                when (tab) {
                    TdTab.DESK -> TdDeskTab()
                    TdTab.BOARD -> TdBoardTab()
                    TdTab.CLIENTS -> TdClientsTab()
                    TdTab.BLOTTER -> TdBlotterTab()
                    TdTab.CREW -> TdCrewTab()
                    TdTab.WIRE -> TdWireTab()
                    TdTab.LEDGER -> TdLedgerTab()
                    TdTab.TERMINAL -> TdTerminalTab(onLogout = onLogout, onExit = onExit)
                }
            }
        }
    }
}

@Composable
internal fun TdTickerTape() {
    val tape = TradingDeskFakeRepo.sessions
    Row(
        modifier = Modifier.fillMaxWidth().background(PitPanel).horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            " LIVE ",
            style = MaterialTheme.typography.labelSmall,
            color = PitBlack,
            modifier = Modifier.background(TapeGreen).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        (tape + tape).forEach { s ->
            val up = s.status == TdSessionStatus.COMPLETED
            val down = s.status == TdSessionStatus.CANCELLED || s.status == TdSessionStatus.NO_SHOW
            val color = when {
                s.voided -> TapeFaint
                up -> TapeGreen
                down -> TapeRed
                else -> TapeAmber
            }
            val arrow = if (up) "▲" else if (down) "▼" else "■"
            Text(
                "${s.symbol} $arrow ${s.status.name} ${if (s.voided) "VOID" else "+" + s.price}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Text("   ///   ", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
        }
    }
    HorizontalDivider(color = PitLine)
}

@Composable
internal fun TdDayBanner() {
    val day by TradingDeskFakeRepo.dayState
    val branchId by TradingDeskFakeRepo.clockedBranchId
    val bandColor = when (day) {
        TdDayState.OPEN -> TapeGreen
        TdDayState.PAST -> TapeAmber
        TdDayState.REMITTED -> TapeCyan
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(PitPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TRADING-DESK", style = MaterialTheme.typography.titleSmall, color = TapeGreen)
        Spacer(Modifier.width(12.dp))
        Text(
            "${TradingDeskFakeRepo.branchCode(branchId)} // ${TradingDeskFakeRepo.branchName(branchId)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TapePaper,
        )
        Spacer(Modifier.width(12.dp))
        TdDayState.entries.forEach { state ->
            val selected = state == day
            Box(
                modifier = Modifier.padding(end = 6.dp).clip(MaterialTheme.shapes.small)
                    .background(if (selected) bandColor else FlatGrey)
                    .clickable {
                        TradingDeskFakeRepo.dayState.value = state
                        TradingDeskFakeRepo.stamp(
                            TradingDeskFakeRepo.currentUser.value.name,
                            "DAY_STATE",
                            "Branch Day -> ${state.name}",
                        )
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    state.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) PitBlack else TapeFaint,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("DAY BOUNDARY 04:00 ASIA/MANILA", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
    }
    HorizontalDivider(color = PitLine)
}

@Composable
internal fun TdRail(current: TdTab, onPick: (TdTab) -> Unit) {
    val unread = TradingDeskFakeRepo.mailbox.count { !it.read }
    val pending = TradingDeskFakeRepo.sessions.count { it.status == TdSessionStatus.PENDING && !it.voided }
    Column(
        modifier = Modifier.width(196.dp).fillMaxHeight().background(PitPanel).padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TdRailHeader("FLOOR")
        TdRailItem("Desk (home)", current == TdTab.DESK, null) { onPick(TdTab.DESK) }
        TdRailHeader("FLOW")
        TdRailItem("Board (sessions)", current == TdTab.BOARD, if (pending > 0) "$pending" else null) {
            onPick(TdTab.BOARD)
        }
        TdRailItem("Clients", current == TdTab.CLIENTS, null) { onPick(TdTab.CLIENTS) }
        TdRailItem("Blotter (finance)", current == TdTab.BLOTTER, null) { onPick(TdTab.BLOTTER) }
        TdRailHeader("FIRM")
        TdRailItem("Crew (team)", current == TdTab.CREW, null) { onPick(TdTab.CREW) }
        TdRailItem("Wire (mail)", current == TdTab.WIRE, if (unread > 0) "$unread" else null) { onPick(TdTab.WIRE) }
        TdRailItem("Ledger (audit)", current == TdTab.LEDGER, null) { onPick(TdTab.LEDGER) }
        TdRailItem("Terminal (me)", current == TdTab.TERMINAL, null) { onPick(TdTab.TERMINAL) }
        Spacer(Modifier.weight(1f))
        Text(
            "GROSS TODAY ₱${TradingDeskFakeRepo.dayGross(TradingDeskFakeRepo.clockedBranchId.value)}",
            style = MaterialTheme.typography.labelSmall,
            color = TapeGreen,
        )
    }
}

@Composable
internal fun TdRailHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = TapeFaint,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun TdRailItem(label: String, selected: Boolean, badge: String?, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
            .background(if (selected) BidGreen else PitBlack)
            .clickable(onClick = onPick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "▸ $label" else "  $label",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) TapeGreen else TapePaper,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = PitBlack,
                modifier = Modifier.background(TapeAmber).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun TdSparkline(values: List<Int>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(56.dp).fillMaxWidth()) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val min = (values.minOrNull() ?: 0).toFloat()
        val span = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - min) / span) * (size.height - 8.dp.toPx()) - 4.dp.toPx())
        }
        pts.zipWithNext { a, b -> drawLine(color, a, b, strokeWidth = 3f) }
        drawCircle(color, radius = 5f, center = pts.last())
    }
}

@Composable
internal fun TdPanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.background(PitPanel).padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TapeGreen)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
internal fun TdAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = TapeGreen, contentColor = PitBlack),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun TdGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TapePaper)
    }
}

@Composable
internal fun TdScroll(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        content()
    }
}

@Composable
internal fun TdEmptyRow(label: String) {
    Text("-- $label --", style = MaterialTheme.typography.bodyMedium, color = TapeFaint)
}

@Composable
internal fun TdDeskTab() {
    val user by TradingDeskFakeRepo.currentUser
    val clocked by TradingDeskFakeRepo.clockedIn
    val branchId by TradingDeskFakeRepo.clockedBranchId
    val relief by TradingDeskFakeRepo.reliefEdit
    TdScroll {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TdPanel("POSITION", modifier = Modifier.weight(1f)) {
                Text("TRADER  ${user.name}", style = MaterialTheme.typography.titleMedium, color = TapePaper)
                Text("ROLE ${user.role}  //  HOME ${TradingDeskFakeRepo.branchName(user.homeBranchId)}", color = TapeFaint)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (clocked) "● CLOCKED IN @ ${TradingDeskFakeRepo.branchName(branchId)}" else "○ FLAT (CLOCKED OUT)",
                    color = if (clocked) TapeGreen else TapeAmber,
                )
                if (branchId != user.homeBranchId && clocked) {
                    Text(
                        if (relief) "RELIEF EDIT GRANTED" else "RELIEF VIEW-ONLY — REQUEST GRANT TO EDIT",
                        color = if (relief) TapeCyan else TapeRed,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!clocked) {
                        TdAction("CLOCK IN") {
                            TradingDeskFakeRepo.clockedIn.value = true
                            TradingDeskFakeRepo.stamp(user.name, "CLOCK_IN", "branch $branchId")
                        }
                    } else {
                        TdGhost("CLOCK OUT") {
                            TradingDeskFakeRepo.clockedIn.value = false
                            TradingDeskFakeRepo.reliefEdit.value = false
                            TradingDeskFakeRepo.stamp(user.name, "CLOCK_OUT", "branch $branchId")
                        }
                    }
                }
            }
            TdPanel("GROSS // ${TradingDeskFakeRepo.branchCode(branchId)}", modifier = Modifier.weight(1f)) {
                val series = TradingDeskFakeRepo.grossSeries[branchId] ?: listOf(0, 0)
                val delta = series.last() - series[series.size - 2]
                Text(
                    "₱${TradingDeskFakeRepo.dayGross(branchId)} booked  //  ${if (delta >= 0) "▲ +$delta" else "▼ $delta"} 7d",
                    color = if (delta >= 0) TapeGreen else TapeRed,
                )
                TdSparkline(series, TapeGreen)
                Text("7-SESSION GROSS TAPE (FAKE)", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TdPanel("ALL VENUES GROSS", modifier = Modifier.weight(1f)) {
                TradingDeskFakeRepo.branches.forEach { b ->
                    val s = TradingDeskFakeRepo.grossSeries[b.id] ?: listOf(0, 0)
                    Text("${b.code}  ₱${TradingDeskFakeRepo.dayGross(b.id)}", color = TapePaper)
                    TdSparkline(s, if (b.id == branchId) TapeAmber else TapeGreenDim)
                    Spacer(Modifier.height(6.dp))
                }
            }
            TdPanel("RELIEF ORDER BOOK", modifier = Modifier.weight(1f)) {
                Text("INVITES (BRANCH → YOU)", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
                TradingDeskFakeRepo.invites.forEach { inv ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${inv.branchName} // ${inv.day} [${inv.state}]", modifier = Modifier.weight(1f))
                        if (inv.state == "OPEN") {
                            TdAction("FILL") {
                                inv.let {
                                    TradingDeskFakeRepo.invites[TradingDeskFakeRepo.invites.indexOf(it)] =
                                        it.copy(state = "ACCEPTED")
                                }
                                TradingDeskFakeRepo.reliefEdit.value = true
                                TradingDeskFakeRepo.stamp(user.name, "ACCEPT_INVITE", inv.id)
                            }
                            Spacer(Modifier.width(6.dp))
                            TdGhost("PASS") {
                                TradingDeskFakeRepo.invites.remove(inv)
                                TradingDeskFakeRepo.stamp(user.name, "DECLINE_INVITE", inv.id)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (TradingDeskFakeRepo.invites.isEmpty()) TdEmptyRow("NO OPEN INVITES")
                Spacer(Modifier.height(8.dp))
                Text("REQUESTS (YOU → FLOOR)", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
                TradingDeskFakeRepo.requests.forEach { req ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${req.branchName} // ${req.day} [${req.state}]", modifier = Modifier.weight(1f))
                        if (req.mine && req.state == "OPEN") {
                            TdGhost("PULL") {
                                TradingDeskFakeRepo.requests.remove(req)
                                TradingDeskFakeRepo.stamp(user.name, "WITHDRAW_REQUEST", req.id)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                TdGhost("SHOUT NEW REQUEST (TONDO 09-14)") {
                    TradingDeskFakeRepo.requests.add(TdReliefRequest("rr-${TradingDeskFakeRepo.requests.size + 1}", "Tondo Medical Mission", "Sun 09-14", true))
                    TradingDeskFakeRepo.stamp(user.name, "SHOUT_REQUEST", "b-tondo 09-14")
                }
            }
        }
    }
}
