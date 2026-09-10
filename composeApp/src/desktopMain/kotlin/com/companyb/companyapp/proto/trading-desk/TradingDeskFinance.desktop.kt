package com.companyb.companyapp.proto.tradingdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
internal fun TdBlotterTab() {
    val branchId by TradingDeskFakeRepo.clockedBranchId
    TdScroll {
        Text("SETTLEMENT BLOTTER // ${TradingDeskFakeRepo.branchCode(branchId)}", color = TapeGreen)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TdSessionRemitCard()
                TdProductRemitCard()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TdSettledRows()
                TdCommissionCard()
            }
        }
    }
}

@Composable
internal fun TdSessionRemitCard() {
    val user by TradingDeskFakeRepo.currentUser
    val branchId by TradingDeskFakeRepo.clockedBranchId
    var draft by TradingDeskFakeRepo.sessionDraft
    TdPanel("SESSION LEG // NET INCOME DRAFT") {
        Text("BOOKED COMPLETED (NON-VOID): ₱${TradingDeskFakeRepo.dayGross(branchId)}", color = TapeFaint)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TdGhost("-500") { draft = (draft - 500).coerceAtLeast(0) }
            Spacer(Modifier.width(8.dp))
            Text("₱$draft", style = MaterialTheme.typography.titleLarge, color = TapePaper)
            Spacer(Modifier.width(8.dp))
            TdGhost("+500") { draft += 500 }
        }
        Spacer(Modifier.height(8.dp))
        TdAction("SUBMIT SESSION REMITTANCE") {
            TradingDeskFakeRepo.remittances.add(
                TdRemittance(
                    "r-${TradingDeskFakeRepo.remittances.size + 1}",
                    TdRemitKind.SESSION,
                    TdRemitStatus.SUBMITTED,
                    draft,
                    "${TradingDeskFakeRepo.branchCode(branchId)} 09-10",
                    "TD-%04d".format(43 + TradingDeskFakeRepo.remittances.size),
                    "Desk submit",
                    "09-10 now",
                ),
            )
            TradingDeskFakeRepo.stamp(user.name, "SUBMIT_REMITTANCE", "SESSION ₱$draft", "Desk submit")
        }
        Spacer(Modifier.height(4.dp))
        Text("SUBMIT FREEZES AN IMMUTABLE SNAPSHOT (TD-*).", style = MaterialTheme.typography.labelSmall, color = TapeFaint)
    }
}

@Composable
internal fun TdProductRemitCard() {
    val user by TradingDeskFakeRepo.currentUser
    val branchId by TradingDeskFakeRepo.clockedBranchId
    val total = TradingDeskFakeRepo.productLines.sumOf { it.qty * it.unitPrice }
    TdPanel("PRODUCT LEG // UNIT PRICE x QTY") {
        TradingDeskFakeRepo.productLines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${line.name} @₱${line.unitPrice}", modifier = Modifier.weight(1f), color = TapePaper)
                TdGhost("-") {
                    val i = TradingDeskFakeRepo.productLines.indexOf(line)
                    if (line.qty > 0) TradingDeskFakeRepo.productLines[i] = line.copy(qty = line.qty - 1)
                }
                Text(" ${line.qty} ", color = TapeAmber)
                TdGhost("+") {
                    val i = TradingDeskFakeRepo.productLines.indexOf(line)
                    TradingDeskFakeRepo.productLines[i] = line.copy(qty = line.qty + 1)
                }
                Spacer(Modifier.width(8.dp))
                Text("₱${line.qty * line.unitPrice}", color = TapeGreen, modifier = Modifier.width(72.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(color = PitLine)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("PRODUCT TOTAL ₱$total", style = MaterialTheme.typography.titleSmall, color = TapePaper, modifier = Modifier.weight(1f))
            TdAction("SUBMIT PRODUCT") {
                TradingDeskFakeRepo.remittances.add(
                    TdRemittance(
                        "r-${TradingDeskFakeRepo.remittances.size + 1}",
                        TdRemitKind.PRODUCT,
                        TdRemitStatus.SUBMITTED,
                        total,
                        "${TradingDeskFakeRepo.branchCode(branchId)} 09-10",
                        "TD-%04d".format(43 + TradingDeskFakeRepo.remittances.size),
                        "Counter drop",
                        "09-10 now",
                    ),
                )
                TradingDeskFakeRepo.stamp(user.name, "SUBMIT_REMITTANCE", "PRODUCT ₱$total", "Counter drop")
            }
        }
    }
}

@Composable
internal fun TdSettledRows() {
    val user by TradingDeskFakeRepo.currentUser
    var undoFor by remember { mutableStateOf<String?>(null) }
    var undoReason by remember { mutableStateOf("") }
    TdPanel("SETTLED // SNAPSHOTS") {
        if (TradingDeskFakeRepo.remittances.isEmpty()) TdEmptyRow("BLOTTER EMPTY")
        TradingDeskFakeRepo.remittances.forEach { r ->
            val chip = when {
                r.status == TdRemitStatus.DRAFT -> TapeFaint to "DRAFT"
                else -> TapeGreen to "SEALED"
            }
            Column(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(FlatGrey)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${r.kind.name} ${r.id}", style = MaterialTheme.typography.titleSmall, color = TapePaper, modifier = Modifier.weight(1f))
                    Text(
                        chip.second,
                        style = MaterialTheme.typography.labelSmall,
                        color = PitBlack,
                        modifier = Modifier.background(chip.first).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text("AMT ₱${r.amount} // DAY ${r.dayLabel}", color = TapeFaint)
                if (r.snapshotNo.isNotEmpty()) Text("SNAPSHOT ${r.snapshotNo} // ${r.submittedAt}", color = TapeCyan)
                if (r.note.isNotEmpty()) Text("NOTE ${r.note}", color = TapeFaint)
                if (r.status == TdRemitStatus.SUBMITTED) {
                    Spacer(Modifier.height(4.dp))
                    if (undoFor == r.id) {
                        OutlinedTextField(
                            value = undoReason,
                            onValueChange = { undoReason = it },
                            label = { Text("Undo reason (required)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TdAction("CONFIRM UNDO", enabled = undoReason.isNotBlank()) {
                                val i = TradingDeskFakeRepo.remittances.indexOfFirst { it.id == r.id }
                                TradingDeskFakeRepo.remittances[i] = r.copy(status = TdRemitStatus.DRAFT, snapshotNo = "")
                                TradingDeskFakeRepo.stamp(user.name, "UNDO_REMITTANCE", "${r.id} -> Draft", undoReason.trim())
                                undoFor = null
                                undoReason = ""
                            }
                            TdGhost("CANCEL") { undoFor = null }
                        }
                    } else {
                        TdGhost("UNDO (48H WINDOW)") { undoFor = r.id }
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    TdGhost("SUBMIT DRAFT") {
                        val i = TradingDeskFakeRepo.remittances.indexOfFirst { it.id == r.id }
                        val snap = "TD-%04d".format(43 + TradingDeskFakeRepo.remittances.size)
                        TradingDeskFakeRepo.remittances[i] =
                            r.copy(status = TdRemitStatus.SUBMITTED, snapshotNo = snap, submittedAt = "09-10 now")
                        TradingDeskFakeRepo.stamp(user.name, "SUBMIT_REMITTANCE", "${r.id} $snap")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "UNDO RETURNS THE REMITTANCE TO DRAFT, UNLOCKS THE DAYS AND DELETES THE SNAPSHOT — WITHIN 48H ONLY.",
            style = MaterialTheme.typography.labelSmall,
            color = TapeFaint,
        )
    }
}

@Composable
internal fun TdCommissionCard() {
    TdPanel("COMMISSION SPLIT // HOUSE RULES") {
        Text("POOLED PER BRANCH DAY, SPLIT EVENLY OVER CLOCKED-IN HANDS.", color = TapePaper)
        Text("RELIEF PAID FROM THIS DRAWER. SEPARATE FROM COMPENSATION.", color = TapeFaint)
        Text("NOT SUBJECT TO REMITTANCE.", color = TapeFaint)
        Spacer(Modifier.height(6.dp))
        val hands = TradingDeskFakeRepo.directory.filter { !it.onboarding }.take(4)
        val pool = 2400
        Text("TODAY POOL ₱$pool ÷ ${hands.size} = ₱${pool / hands.size} EACH", color = TapeGreen)
        hands.forEach { h ->
            Text("· ${h.name} (${h.role}) +₱${pool / hands.size}", color = TapeFaint)
        }
    }
}
