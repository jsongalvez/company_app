package com.companyb.companyapp.proto.shifthandover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #771 — signature flow: changeover board (incoming handover inbox + open items) and
// clock-out writing the handover summary with relief context carried.

@Composable
fun ShBoardScreen(repo: ShiftRepo, go: (ShDest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShHeadline("Changeover board")
            Spacer(Modifier.width(12.dp))
            ShCrewTag(repo.currentUser?.name ?: "—", outgoing = true)
            Spacer(Modifier.width(8.dp))
            ShShiftArrow()
            Spacer(Modifier.width(8.dp))
            ShCrewTag("next crew", outgoing = false)
        }
        ShNote("Outgoing shift on the left, incoming on the right. Clock-out writes the handover; the next crew acks it here.")
        val latest = repo.handovers.firstOrNull()
        if (latest != null && !latest.acked) {
            ShCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShTape("INCOMING HANDOVER")
                        Spacer(Modifier.width(8.dp))
                        Text("${latest.fromCrew} → ${latest.toCrew}", color = ShColors.Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(latest.note, color = ShColors.Paper, fontSize = 14.sp, lineHeight = 20.sp)
                    ShNote("Carries ${latest.carriedCount} open items · ${latest.reliefContext}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShPrimary("Acknowledge — mine now") {
                            latest.acked = true
                            repo.log("ACK handover ${latest.id} by ${repo.currentUser?.name}")
                        }
                        ShGhost("Open items") { go(ShDest.BOARD) }
                    }
                }
            }
        }
        ShSection("OPEN ITEMS · CARRIED ACROSS SHIFTS")
        repo.openItems.forEach { item ->
            ShRowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.text, color = ShColors.Paper, fontSize = 14.sp)
                        Text("from ${item.fromShift}", color = ShColors.Faded, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    ShStatusChip(item.state.name, itemColor(item.state))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.state == ShOpenState.OPEN) ShGhost("Ack") {
                        item.state = ShOpenState.ACKED
                        repo.log("ACK open item ${item.id}")
                    }
                    if (item.state != ShOpenState.DONE) ShGhost("Done") {
                        item.state = ShOpenState.DONE
                        repo.log("DONE open item ${item.id}")
                    }
                    if (item.state == ShOpenState.DONE) ShGhost("Reopen") {
                        item.state = ShOpenState.OPEN
                        repo.log("REOPEN open item ${item.id}")
                    }
                }
            }
        }
        ShSection("SHIFT PULSE")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShPulseCard("Pending sessions", repo.pendingSessions(repo.currentBranchId).size.toString()) { go(ShDest.SESSIONS) }
            ShPulseCard("Unread mail", repo.notices.count { !it.read }.toString()) { go(ShDest.MAILBOX) }
            ShPulseCard("Draft remittances", repo.remittances.count { it.stage == "DRAFT" }.toString()) { go(ShDest.FINANCE) }
        }
    }
}

fun itemColor(state: ShOpenState): androidx.compose.ui.graphics.Color = when (state) {
    ShOpenState.OPEN -> ShColors.Outgoing
    ShOpenState.ACKED -> ShColors.Tape
    ShOpenState.DONE -> ShColors.Incoming
}

@Composable
private fun ShPulseCard(label: String, value: String, onClick: () -> Unit) {
    ShRowCard(onClick = onClick) {
        Column {
            Text(value, color = ShColors.Tape, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(label, color = ShColors.Faded, fontSize = 12.sp)
        }
    }
}

@Composable
fun ShHandoverScreen(repo: ShiftRepo) {
    var note by remember { mutableStateOf("") }
    var carryRelief by remember { mutableStateOf(true) }
    var justWrote by remember { mutableStateOf<ShHandover?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShHeadline("Handover")
        ShNote("Clock-out writes the handover summary: open items auto-attach, your note rides along, relief context carries to the next crew.")
        ShCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShTape(if (repo.clockedIn) "YOU ARE ON SHIFT" else "OFF SHIFT")
                Text(
                    "Write the clock-out summary",
                    color = ShColors.Paper,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                val carried = repo.openItems.count { it.state != ShOpenState.DONE }
                ShNote("Auto-carried: $carried open items · branch ${repo.currentBranch()?.name ?: "—"} · relief: ${repo.relief.joinToString { it.who }}")
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Handover note for the incoming crew") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = carryRelief,
                        onCheckedChange = { carryRelief = it },
                        colors = CheckboxDefaults.colors(checkedColor = ShColors.Tape),
                    )
                    Text("Carry relief context into the handover", color = ShColors.Paper, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShPrimary(if (repo.clockedIn) "Clock out + write handover" else "Write handover (off shift)") {
                        justWrote = repo.clockOut(note, carryRelief)
                        note = ""
                    }
                }
                justWrote?.let { h ->
                    ShNote("Wrote ${h.id}: ${h.carriedCount} items carried · ${h.reliefContext}")
                }
            }
        }
        ShSection("HANDOVER LOG · NEWEST FIRST")
        repo.handovers.forEach { h ->
            ShRowCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShCrewTag(h.fromCrew, outgoing = true)
                        Spacer(Modifier.width(8.dp))
                        ShShiftArrow()
                        Spacer(Modifier.width(8.dp))
                        ShCrewTag(h.toCrew, outgoing = false)
                        Spacer(Modifier.weight(1f))
                        ShStatusChip(if (h.acked) "ACKED" else "WAITING", if (h.acked) ShColors.Incoming else ShColors.Outgoing)
                    }
                    Text(h.note, color = ShColors.Paper, fontSize = 14.sp, lineHeight = 20.sp)
                    ShNote("${h.branchName} · ${h.dayLabel} · ${h.carriedCount} items · ${h.reliefContext}")
                    if (!h.acked) {
                        ShGhost("Acknowledge") {
                            h.acked = true
                            repo.log("ACK handover ${h.id} by ${repo.currentUser?.name}")
                        }
                    }
                }
            }
        }
    }
}
