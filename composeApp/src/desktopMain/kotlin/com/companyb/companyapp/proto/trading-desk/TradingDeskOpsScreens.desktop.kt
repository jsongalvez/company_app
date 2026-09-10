package com.companyb.companyapp.proto.tradingdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
internal fun TdClientsTab() {
    var reveal by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    TdScroll {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CLIENT BOOK // GLOBAL", color = TapeGreen, modifier = Modifier.weight(1f))
            Text(
                if (reveal) "[REVEALED — TAP TO MASK]" else "[MASKED — TAP TO REVEAL]",
                color = TapeAmber,
                modifier = Modifier.clickable { reveal = !reveal },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "AT MOST ONE PENDING SESSION PER CLIENT. ANONYMIZED ROWS KEEP GENDER + AGE ONLY.",
            style = MaterialTheme.typography.labelSmall,
            color = TapeFaint,
        )
        Spacer(Modifier.height(8.dp))
        TradingDeskFakeRepo.clients.forEach { c ->
            val open = TradingDeskFakeRepo.pendingFor(if (c.anonymized) "X. Anon" else c.name)
            val label = if (c.anonymized && !reveal) "ANON-${c.id.uppercase()} // ${c.gender}${c.age}" else "${c.name} // ${c.gender}${c.age}"
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
                    .background(if (picked == c.id) BidGreen else PitPanel)
                    .clickable { picked = c.id }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = TapePaper, modifier = Modifier.weight(1f))
                Text(
                    if (open > 0) "● $open PENDING" else "○ FLAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (open > 0) TapeAmber else TapeFaint,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val c = TradingDeskFakeRepo.clients.firstOrNull { it.id == picked }
        if (c != null) {
            TdPanel("POSITION ${c.id.uppercase()}") {
                val displayName = if (c.anonymized && !reveal) "ANON-${c.id.uppercase()}" else c.name
                Text(displayName, style = MaterialTheme.typography.titleMedium, color = TapePaper)
                Text("GENDER ${c.gender} // AGE ${c.age}${if (c.note.isNotEmpty()) " // ${c.note}" else ""}", color = TapeFaint)
                val mine = TradingDeskFakeRepo.sessions.filter {
                    it.clientName == (if (c.anonymized) "X. Anon" else c.name)
                }
                if (mine.isEmpty()) {
                    TdEmptyRow("NO TICKETS ON THIS BOOK")
                } else {
                    mine.forEach { s ->
                        Text("${s.symbol} ${s.status.name} ₱${s.price}${if (s.voided) " VOID" else ""}", color = TapeFaint)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TdCrewTab() {
    val me by TradingDeskFakeRepo.currentUser
    TdScroll {
        Text("CREW // BRANCH-SLOT ORDER", color = TapeGreen)
        Spacer(Modifier.height(8.dp))
        TradingDeskFakeRepo.directory.sortedBy { it.slot }.forEach { u ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
                    .background(if (u.id == me.id) BidGreen else PitPanel)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${u.slot}", color = TapeFaint, modifier = Modifier.width(40.dp))
                Text(u.name, color = TapePaper, modifier = Modifier.weight(1f))
                if (u.id == me.id) {
                    Text("YOU ", style = MaterialTheme.typography.labelSmall, color = TapeGreen)
                }
                Text(
                    u.role,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (u.onboarding) TapeRed else TapeAmber,
                )
            }
            if (u.onboarding) {
                Text("  LOCKED: ZERO CAPABILITIES UNTIL MANAGE_USERS GRANTS A ROLE.", color = TapeRed)
                Spacer(Modifier.height(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("RELIEF HANDS SORT AFTER HOME SLOTS. PAY COMES FROM THE RELIEF DRAWER.", color = TapeFaint)
    }
}

@Composable
internal fun TdWireTab() {
    val user by TradingDeskFakeRepo.currentUser
    var filterUnread by remember { mutableStateOf(false) }
    val rows = TradingDeskFakeRepo.mailbox.filter { !filterUnread || !it.read }
    TdScroll {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WIRE // MAILBOX", color = TapeGreen, modifier = Modifier.weight(1f))
            TdGhost(if (filterUnread) "SHOW ALL" else "UNREAD ONLY") { filterUnread = !filterUnread }
            Spacer(Modifier.width(6.dp))
            TdGhost("HUSH ALL") {
                TradingDeskFakeRepo.mailbox.forEachIndexed { i, m -> TradingDeskFakeRepo.mailbox[i] = m.copy(read = true) }
                TradingDeskFakeRepo.stamp(user.name, "READ_ALL_MAIL", "mailbox")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) TdEmptyRow("WIRE QUIET")
        rows.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.small)
                    .background(if (m.read) PitPanel else BidGreen).clickable {
                        val i = TradingDeskFakeRepo.mailbox.indexOfFirst { it.id == m.id }
                        if (i >= 0) TradingDeskFakeRepo.mailbox[i] = m.copy(read = !m.read)
                    }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (m.read) "○" else "●", color = if (m.read) TapeFaint else TapeGreen, modifier = Modifier.width(28.dp))
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text("[${m.wire}] ${m.title}", color = TapePaper)
                    Text("${m.body} // ${m.day}", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("READ ROWS ARE KEPT FOREVER AS HISTORY.", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
    }
}

@Composable
internal fun TdLedgerTab() {
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("LEDGER // AUDIT LOG (IMMUTABLE)", color = TapeGreen) }
        item { Spacer(Modifier.height(4.dp)) }
        items(TradingDeskFakeRepo.audits, key = { it.id }) { a ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(PitPanel)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a.whenText, style = MaterialTheme.typography.labelSmall, color = TapeFaint, modifier = Modifier.width(96.dp))
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text("${a.actor} :: ${a.action} :: ${a.record}", color = TapePaper)
                    if (a.reason.isNotEmpty()) Text("WHY: ${a.reason}", style = MaterialTheme.typography.labelSmall, color = TapeAmber)
                }
            }
        }
    }
}

@Composable
internal fun TdTerminalTab(onLogout: () -> Unit, onExit: () -> Unit) {
    val user by TradingDeskFakeRepo.currentUser
    val clocked by TradingDeskFakeRepo.clockedIn
    val branchId by TradingDeskFakeRepo.clockedBranchId
    TdScroll {
        TdPanel("TERMINAL // ${user.name.uppercase()}") {
            Text("ROLE ${user.role}", color = TapePaper)
            Text("HOME ${TradingDeskFakeRepo.branchName(user.homeBranchId)}", color = TapeFaint)
            Text(
                if (clocked) "● ON SHIFT @ ${TradingDeskFakeRepo.branchName(branchId)}" else "○ OFF SHIFT",
                color = if (clocked) TapeGreen else TapeAmber,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (clocked) {
                    TdGhost("CLOCK OUT") {
                        TradingDeskFakeRepo.clockedIn.value = false
                        TradingDeskFakeRepo.reliefEdit.value = false
                        TradingDeskFakeRepo.stamp(user.name, "CLOCK_OUT", "branch $branchId")
                    }
                } else {
                    TdAction("CLOCK IN") {
                        TradingDeskFakeRepo.clockedIn.value = true
                        TradingDeskFakeRepo.stamp(user.name, "CLOCK_IN", "branch $branchId")
                    }
                }
                TdGhost("SIGN OUT") {
                    TradingDeskFakeRepo.stamp(user.name, "LOGOUT", "desk terminal")
                    TradingDeskFakeRepo.clockedIn.value = false
                    onLogout()
                }
                TdGhost("KILL TERMINAL") { onExit() }
            }
        }
        Spacer(Modifier.height(12.dp))
        TdPanel("SESSION") {
            Text("FEED FAKE // NO NETWORK // NO BACKEND", color = TapeFaint)
            Text("TAPE: ${TradingDeskFakeRepo.sessions.size} TICKETS // MAIL: ${TradingDeskFakeRepo.mailbox.count { !it.read }} UNREAD", color = TapeFaint)
            Text("SNAPSHOTS: ${TradingDeskFakeRepo.remittances.count { it.status == TdRemitStatus.SUBMITTED }} SEALED", color = TapeFaint)
        }
    }
}
