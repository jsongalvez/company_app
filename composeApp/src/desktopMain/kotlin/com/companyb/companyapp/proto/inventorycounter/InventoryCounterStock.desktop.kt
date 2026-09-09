package com.companyb.companyapp.proto.inventorycounter

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

@Composable
private fun IcStockPick(sku: String, selected: Boolean, onPick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.background(
            if (selected) IcColors.Ink else IcColors.SteelWash,
            androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        )
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            sku,
            color = if (selected) androidx.compose.ui.graphics.Color.White else IcColors.Steel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

// #774 — count ledger + session detail. The ledger is the home shelf: system vs
// counted per SKU, unit x quantity math on every row, one-tap link into the
// PRODUCT remittance draft via session line sales.

// COUNT destination: home shelf on top, full count ledger below.
@Composable
fun IcCountScreen(repo: InventoryCounterRepo, goSessions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IcCountHome(repo, goSessions)
        IcCountLedger(repo)
    }
}

// Full count ledger: every SKU at this branch with system vs counted math.
@Composable
fun IcCountLedger(repo: InventoryCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IcScreenTitle()
        IcHeadline("Count ledger — ${repo.currentBranch().name}")
        IcNote(
            "Each branch holds its own inventory. Tap a row to key in the physical count; " +
                "the ledger scores system vs counted live. Flags at or below reorder light up LOW.",
        )
        var selectedId by remember { mutableStateOf<String?>(null) }
        repo.branchStock(repo.currentBranchId).forEach { item ->
            val counted = item.countedQty
            val low = (counted ?: item.systemQty) <= item.lowAt
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IcSku(item.sku)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = IcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "₱${item.unitPrice} / ${item.unit} · reorder at ${item.lowAt}",
                                color = IcColors.Faded,
                                fontSize = 12.sp,
                            )
                        }
                        if (low) IcFlag("LOW", IcColors.Safety, IcColors.SafetyWash)
                        if (counted != null && counted == item.systemQty) {
                            IcFlag("MATCH", IcColors.Moss, IcColors.MossWash)
                        }
                    }
                    IcLedger("System", "${item.systemQty} ${item.unit}s")
                    IcLedger("Counted", if (counted == null) "— not counted" else "$counted ${item.unit}s")
                    if (counted != null && counted != item.systemQty) {
                        val delta = counted - item.systemQty
                        IcLedger(
                            "Variance",
                            if (delta > 0) "over by $delta" else "short by ${-delta}",
                            strong = true,
                        )
                    }
                    IcLedger(
                        "Shelf value",
                        "₱${item.unitPrice} x ${counted ?: item.systemQty} = ₱${item.unitPrice * (counted ?: item.systemQty)}",
                        strong = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IcLink(if (counted == null) "Key in count" else "Recount") {
                            selectedId = if (selectedId == item.id) null else item.id
                        }
                    }
                    if (selectedId == item.id) {
                        IcCountEntry(repo, item.id) { selectedId = null }
                    }
                }
            }
        }
        IcSection("Product remittance link") {
            IcCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "PRODUCT draft for ${repo.currentBranch().name}: " +
                            "₱${repo.productDraftTotal(repo.currentBranchId)}",
                        color = IcColors.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IcNote(
                        "Every line sale keyed from a session (unit price x quantity) rolls into the " +
                            "PRODUCT remittance draft. Count discrepancies never edit sales — recount instead.",
                    )
                }
            }
        }
    }
}

@Composable
private fun IcCountEntry(repo: InventoryCounterRepo, stockId: String, onDone: () -> Unit) {
    val item = repo.stockItem(stockId)
    var raw by remember(stockId) { mutableStateOf(item.countedQty?.toString() ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = raw,
            onValueChange = { raw = it.filter { c -> c.isDigit() } },
            label = { Text("Counted ${item.unit}s") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IcPrimary("Record count") {
                raw.toIntOrNull()?.let { repo.recordCount(stockId, it) }
                onDone()
            }
            IcGhost("Cancel") { onDone() }
        }
    }
}

// Session detail: status moves, walk-in rule note, void/unvoid with reason,
// linked product lines with unit x quantity math + link-a-sale.
@Composable
fun IcSessionDetail(repo: InventoryCounterRepo, sessionId: String, onClose: () -> Unit) {
    val s = repo.sessions.first { it.id == sessionId }
    var showVoid by remember(sessionId) { mutableStateOf(false) }
    var voidReason by remember(sessionId) { mutableStateOf("") }
    var showLink by remember(sessionId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Session ${s.id} · ${s.time}",
                    color = IcColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "${s.clientName} · ${s.kind} · ${s.practitioners}",
                    color = IcColors.Faded,
                    fontSize = 12.sp,
                )
            }
            IcFlag(s.status.name, s.status.dot(), IcColors.Shelf)
        }
        if (s.voided) {
            IcCard {
                Text(
                    "VOIDED — excluded from financials, record kept. Reason: ${s.voidReason}",
                    color = IcColors.Safety,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IcKicker("Status moves")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IcGhost("Complete") { repo.advance(s.id, IcSessionStatus.COMPLETED) }
                    IcGhost("No-show") { repo.advance(s.id, IcSessionStatus.NO_SHOW) }
                    IcGhost("Cancel") { repo.advance(s.id, IcSessionStatus.CANCELLED) }
                }
                if (s.walkIn) {
                    IcNote("Walk-in rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED — moves above are inert.")
                }
            }
        }
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IcKicker("Linked products — unit x quantity")
                val lines = repo.lineSales.filter { it.sessionId == s.id }
                if (lines.isEmpty()) {
                    IcNote("No products linked yet. Link a sale to feed the PRODUCT draft.")
                } else {
                    lines.forEach { line ->
                        val item = repo.stockItem(line.stockId)
                        IcLedger(
                            "${item.sku} · ${item.name}",
                            "${line.qty} x ₱${item.unitPrice} = ₱${repo.lineMath(line)}",
                            strong = true,
                        )
                    }
                    IcLedger("Session product total", "₱${repo.sessionProductTotal(s.id)}", strong = true)
                }
                IcLedger("Session price", "₱${s.price}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IcLink(if (showLink) "Hide link-a-sale" else "Link a sale") { showLink = !showLink }
                }
                if (showLink) IcLinkSale(repo, s.id)
            }
        }
        IcCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IcKicker("Void")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IcLink(if (showVoid) "Hide void" else if (s.voided) "Unvoid" else "Void with reason") {
                        if (s.voided) {
                            repo.toggleVoid(s.id, "")
                        } else {
                            showVoid = !showVoid
                        }
                    }
                }
                if (showVoid && !s.voided) {
                    TextField(
                        value = voidReason,
                        onValueChange = { voidReason = it },
                        label = { Text("Void reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IcDanger("Confirm void") {
                            if (voidReason.isNotBlank()) {
                                repo.toggleVoid(s.id, voidReason)
                                showVoid = false
                                voidReason = ""
                            }
                        }
                    }
                    IcNote("Void excludes the session from financials; the record stays visible. Unvoid reverses it.")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IcGhost("Back to list") { onClose() }
        }
    }
}

@Composable
private fun IcLinkSale(repo: InventoryCounterRepo, sessionId: String) {
    var skuId by remember(sessionId) { mutableStateOf(repo.branchStock(repo.currentBranchId).firstOrNull()?.id ?: "") }
    var qtyRaw by remember(sessionId) { mutableStateOf("1") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IcNote("Tap a SKU to select what the client takes home:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.branchStock(repo.currentBranchId).forEach { item ->
                IcStockPick(item.sku, selected = skuId == item.id) { skuId = item.id }
            }
        }
        TextField(
            value = qtyRaw,
            onValueChange = { qtyRaw = it.filter { c -> c.isDigit() } },
            label = { Text("Quantity") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IcPrimary("Link sale") {
                val qty = qtyRaw.toIntOrNull() ?: 0
                if (skuId.isNotBlank() && qty > 0) repo.linkSale(sessionId, skuId, qty)
            }
        }
    }
}
