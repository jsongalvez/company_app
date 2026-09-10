package com.companyb.companyapp.proto.clerkcounter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background

@Composable
private fun CcSkuPick(sku: String, selected: Boolean, onPick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.background(
            if (selected) CcColors.Till else CcColors.SteelWash,
            RoundedCornerShape(20.dp),
        )
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            sku,
            color = if (selected) androidx.compose.ui.graphics.Color.White else CcColors.Steel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// #826 — scan lane + session detail. The lane is the home counter: scan-feel rows,
// stamped variance callouts, unit x quantity math on every slip, one-tap link into
// the PRODUCT remittance draft via session line sales.

// LANE destination: clock home on top, full scan feed below.
@Composable
fun CcLaneScreen(repo: ClerkCounterRepo, goSessions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CcClockHome(repo, goSessions)
        CcScanFeed(repo)
    }
}

// Full scan feed: every SKU at this branch as a scan slip.
@Composable
fun CcScanFeed(repo: ClerkCounterRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CcScreenTitle()
        CcHeadline("Scan feed — ${repo.currentBranch().name}")
        CcNote(
            "Each branch runs its own counter. Scan a slip to key the physical count; " +
                "the lane stamps system vs counted live. Slips at or below reorder raise LOW.",
        )
        var selectedId by remember { mutableStateOf<String?>(null) }
        repo.branchStock(repo.currentBranchId).forEachIndexed { index, item ->
            val counted = item.countedQty
            val low = (counted ?: item.systemQty) <= item.lowAt
            CcCard {
                Text(
                    "▸ SLIP %02d".format(index + 1),
                    color = CcColors.Scan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CcScanCode(item.sku)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = CcColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "₱${item.unitPrice} / ${item.unit} · reorder at ${item.lowAt}",
                            color = CcColors.Muted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (low) CcStamp("LOW", CcColors.Low, CcColors.LowWash)
                }
                CcPerforation()
                CcTape("SYSTEM", "${item.systemQty} ${item.unit}s")
                CcTape("COUNTED", if (counted == null) "— not scanned" else "$counted ${item.unit}s")
                if (counted != null && counted != item.systemQty) {
                    val delta = counted - item.systemQty
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("VARIANCE", color = CcColors.Stencil, fontSize = 13.sp,
                            modifier = Modifier.weight(1f))
                        if (delta > 0) {
                            CcStamp("OVER $delta", CcColors.Over, CcColors.OverWash)
                        } else {
                            CcStamp("SHORT ${-delta}", CcColors.Short, CcColors.ShortWash)
                        }
                    }
                }
                if (counted != null && counted == item.systemQty) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VARIANCE", color = CcColors.Stencil, fontSize = 13.sp,
                            modifier = Modifier.weight(1f))
                        CcStamp("MATCH", CcColors.Match, CcColors.MatchWash)
                    }
                }
                CcTape(
                    "LANE VALUE",
                    "₱${item.unitPrice} x ${counted ?: item.systemQty} = " +
                        "₱${item.unitPrice * (counted ?: item.systemQty)}",
                    strong = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CcLink(if (counted == null) "Scan count" else "Rescan") {
                        selectedId = if (selectedId == item.id) null else item.id
                    }
                }
                if (selectedId == item.id) {
                    CcCountEntry(repo, item.id) { selectedId = null }
                }
            }
        }
        CcSection("Product remittance link") {
            CcCard {
                Text(
                    "PRODUCT draft for ${repo.currentBranch().name}: " +
                        "₱${repo.productDraftTotal(repo.currentBranchId)}",
                    color = CcColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                CcNote(
                    "Every line sale scanned from a session (unit price x quantity) rolls into the " +
                        "PRODUCT remittance draft. Count slips never edit sales — rescan instead.",
                )
            }
        }
    }
}

@Composable
private fun CcCountEntry(repo: ClerkCounterRepo, stockId: String, onDone: () -> Unit) {
    val item = repo.stockItem(stockId)
    var raw by remember(stockId) { mutableStateOf(item.countedQty?.toString() ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = raw,
            onValueChange = { raw = it.filter { c -> c.isDigit() } },
            label = { Text("Scanned ${item.unit}s") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcPrimary("Stamp count") {
                raw.toIntOrNull()?.let { repo.recordCount(stockId, it) }
                onDone()
            }
            CcGhost("Cancel") { onDone() }
        }
    }
}

// Session detail: status moves, walk-in rule note, void/unvoid with reason,
// linked product lines with unit x quantity math + link-a-sale.
@Composable
fun CcSessionDetail(repo: ClerkCounterRepo, sessionId: String, onClose: () -> Unit) {
    val s = repo.sessions.first { it.id == sessionId }
    var showVoid by remember(sessionId) { mutableStateOf(false) }
    var voidReason by remember(sessionId) { mutableStateOf("") }
    var showLink by remember(sessionId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Session ${s.id} · ${s.time}",
                    color = CcColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "${s.clientName} · ${s.kind} · ${s.practitioners}",
                    color = CcColors.Muted,
                    fontSize = 12.sp,
                )
            }
            CcFlag(s.status.name, s.status.dot(), CcColors.Paper)
        }
        if (s.voided) {
            CcCard {
                Text(
                    "VOIDED — excluded from financials, record kept. Reason: ${s.voidReason}",
                    color = CcColors.Short,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        CcCard {
            CcKicker("Status moves")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcGhost("Complete") { repo.advance(s.id, CcSessionStatus.COMPLETED) }
                CcGhost("No-show") { repo.advance(s.id, CcSessionStatus.NO_SHOW) }
                CcGhost("Cancel") { repo.advance(s.id, CcSessionStatus.CANCELLED) }
            }
            if (s.walkIn) {
                CcNote("Walk-in rule: walk-in sessions cannot be marked NO_SHOW or CANCELLED — moves above are inert.")
            }
        }
        CcCard {
            CcKicker("Linked products — unit x quantity")
            val lines = repo.lineSales.filter { it.sessionId == s.id }
            if (lines.isEmpty()) {
                CcNote("No products linked yet. Link a sale to feed the PRODUCT draft.")
            } else {
                lines.forEach { line ->
                    val item = repo.stockItem(line.stockId)
                    CcTape(
                        "${item.sku} · ${item.name}",
                        "${line.qty} x ₱${item.unitPrice} = ₱${repo.lineMath(line)}",
                        strong = true,
                    )
                }
                CcTape("Session product total", "₱${repo.sessionProductTotal(s.id)}", strong = true)
            }
            CcTape("Session price", "₱${s.price}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcLink(if (showLink) "Hide link-a-sale" else "Link a sale") { showLink = !showLink }
            }
            if (showLink) CcLinkSale(repo, s.id)
        }
        CcCard {
            CcKicker("Void")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcLink(if (showVoid) "Hide void" else if (s.voided) "Unvoid" else "Void with reason") {
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
                    CcDanger("Confirm void") {
                        if (voidReason.isNotBlank()) {
                            repo.toggleVoid(s.id, voidReason)
                            showVoid = false
                            voidReason = ""
                        }
                    }
                }
                CcNote("Void excludes the session from financials; the record stays visible. Unvoid reverses it.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CcGhost("Back to list") { onClose() }
        }
    }
}

@Composable
private fun CcLinkSale(repo: ClerkCounterRepo, sessionId: String) {
    var skuId by remember(sessionId) { mutableStateOf(repo.branchStock(repo.currentBranchId).firstOrNull()?.id ?: "") }
    var qtyRaw by remember(sessionId) { mutableStateOf("1") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CcNote("Tap a scan chip to select what the client takes home:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repo.branchStock(repo.currentBranchId).forEach { item ->
                CcSkuPick(item.sku, selected = skuId == item.id) { skuId = item.id }
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
            CcPrimary("Link sale") {
                val qty = qtyRaw.toIntOrNull() ?: 0
                if (skuId.isNotBlank() && qty > 0) repo.linkSale(sessionId, skuId, qty)
            }
        }
    }
}
