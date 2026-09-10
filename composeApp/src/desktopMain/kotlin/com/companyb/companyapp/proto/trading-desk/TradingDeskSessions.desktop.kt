package com.companyb.companyapp.proto.tradingdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp

@Composable
internal fun TdBoardTab() {
    val branchId by TradingDeskFakeRepo.clockedBranchId
    var filter by remember { mutableStateOf<TdSessionStatus?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val rows = TradingDeskFakeRepo.sessions.filter { it.branchId == branchId && (filter == null || it.status == filter) }
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ORDER BOARD // ${TradingDeskFakeRepo.branchCode(branchId)}", color = TapeGreen)
                Spacer(Modifier.weight(1f))
                TdGhost(if (showCreate) "CLOSE TICKET" else "+ NEW TICKET") { showCreate = !showCreate }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TdFilterChip("ALL", filter == null) { filter = null }
                TdSessionStatus.entries.forEach { st ->
                    TdFilterChip(st.name, filter == st) { filter = st }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (showCreate) TdCreateTicket { showCreate = false }
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.id }) { s ->
                    val bg = when {
                        s.voided -> FlatGrey
                        s.status == TdSessionStatus.PENDING -> HoldAmber
                        s.status == TdSessionStatus.COMPLETED -> BidGreen
                        else -> AskRed
                    }
                    val fg = if (s.voided) TapeFaint else TapePaper
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(bg)
                            .clickable { selectedId = s.id }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.symbol, style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.width(88.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${s.clientName} — ${s.type} ${if (s.walkIn) "[WALK-IN]" else ""}", color = fg)
                            Text(
                                "${s.time} // ${s.status.name}${if (s.voided) " // VOID: ${s.voidReason}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TapeFaint,
                            )
                        }
                        Text("₱${s.price}", style = MaterialTheme.typography.titleSmall, color = if (s.voided) TapeFaint else TapeGreen)
                    }
                }
                if (rows.isEmpty()) item { TdEmptyRow("BOOK EMPTY FOR THIS FILTER") }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            val s = TradingDeskFakeRepo.sessions.firstOrNull { it.id == selectedId }
            if (s == null) {
                TdPanel("TICKET DETAIL") { TdEmptyRow("CLICK A ROW TO INSPECT") }
            } else {
                TdTicketDetail(s)
            }
        }
    }
}

@Composable
internal fun TdFilterChip(label: String, selected: Boolean, onPick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) PitBlack else TapePaper,
        modifier = Modifier.clip(MaterialTheme.shapes.small)
            .background(if (selected) TapeGreen else FlatGrey).clickable(onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
internal fun TdTicketDetail(s: TdSession) {
    val user by TradingDeskFakeRepo.currentUser
    var reason by remember(s.id) { mutableStateOf("") }
    var ruleNote by remember(s.id) { mutableStateOf("") }
    TdPanel("TICKET ${s.symbol}") {
        Text("${s.clientName} // ${s.type} // ${s.time}", color = TapePaper)
        Text("PRACTITIONER ${s.practitioner.ifEmpty { "-- UNASSIGNED --" }}", color = TapeFaint)
        Text("PRICE ₱${s.price}  //  ${if (s.walkIn) "WALK-IN" else "BOOKED"}", color = TapeFaint)
        Text("STATUS ${s.status.name}${if (s.voided) " // VOIDED (${s.voidReason})" else ""}", color = TapeAmber)
        Spacer(Modifier.height(8.dp))
        Text("FILL ACTIONS", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TdSessionStatus.entries.filter { it != s.status }.forEach { target ->
                TdGhost(target.name) {
                    if (s.walkIn && (target == TdSessionStatus.NO_SHOW || target == TdSessionStatus.CANCELLED)) {
                        ruleNote = "RULE: WALK-IN SESSIONS CANNOT BE MARKED ${target.name}."
                    } else {
                        ruleNote = ""
                        replaceSession(s.id, s.copy(status = target))
                        TradingDeskFakeRepo.stamp(user.name, "UPDATE_SESSION", "${s.id} -> ${target.name}")
                    }
                }
            }
        }
        if (ruleNote.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(ruleNote, color = TapeRed)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PitLine)
        Spacer(Modifier.height(8.dp))
        Text("VOID DESK (REASON REQUIRED)", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!s.voided) {
                TdAction("VOID", enabled = reason.isNotBlank()) {
                    replaceSession(s.id, s.copy(voided = true, voidReason = reason.trim()))
                    TradingDeskFakeRepo.stamp(user.name, "VOID_SESSION", "${s.id} voided", reason.trim())
                    reason = ""
                }
            } else {
                TdAction("UNVOID", enabled = reason.isNotBlank()) {
                    replaceSession(s.id, s.copy(voided = false, voidReason = ""))
                    TradingDeskFakeRepo.stamp(user.name, "UNVOID_SESSION", "${s.id} unvoided", reason.trim())
                    reason = ""
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "VOIDED TICKETS STAY VISIBLE AND LEAVE FINANCIAL CALCS.",
            style = MaterialTheme.typography.labelSmall,
            color = TapeFaint,
        )
    }
}

@Composable
internal fun TdCreateTicket(onDone: () -> Unit) {
    val branchId by TradingDeskFakeRepo.clockedBranchId
    val user by TradingDeskFakeRepo.currentUser
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("850") }
    var walkIn by remember { mutableStateOf(false) }
    TdPanel("OPEN NEW TICKET") {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Client name") }, singleLine = true)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price ₱") }, singleLine = true)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (walkIn) "[X] WALK-IN" else "[ ] WALK-IN", modifier = Modifier.clickable { walkIn = !walkIn }, color = TapeAmber)
            Spacer(Modifier.width(12.dp))
            TdAction("QUOTE PENDING", enabled = name.isNotBlank()) {
                val n = TradingDeskFakeRepo.sessions.size + 101
                TradingDeskFakeRepo.sessions.add(
                    TdSession(
                        "s-$n",
                        "${TradingDeskFakeRepo.branchCode(branchId)}.S$n",
                        name.trim(),
                        branchId,
                        TdSessionStatus.PENDING,
                        "Follow-up",
                        price.toIntOrNull() ?: 0,
                        walkIn,
                        user.name,
                        false,
                        "",
                        "now",
                    ),
                )
                TradingDeskFakeRepo.stamp(user.name, "CREATE_SESSION", "s-$n PENDING")
                onDone()
            }
        }
    }
}

private fun replaceSession(id: String, next: TdSession) {
    val i = TradingDeskFakeRepo.sessions.indexOfFirst { it.id == id }
    if (i >= 0) TradingDeskFakeRepo.sessions[i] = next
}
