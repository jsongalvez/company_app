package com.companyb.companyapp.proto.clerkcounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #826 — clerk-counter fake data. Local only: no ApiClient, no Ktor, no backend.
// The scan lane is the hero: every branch holds its own counter stock, each row
// carries unit x quantity math, variances feed stamped callouts and the PRODUCT draft.

enum class CcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class CcDayStatus { OPEN, PAST, REMITTED }

data class CcUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class CcBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: CcDayStatus,
)

data class CcStockItem(
    val id: String,
    val branchId: String,
    val sku: String,
    val name: String,
    val unit: String,
    val unitPrice: Int,
    val systemQty: Int,
    var countedQty: Int?,
    val lowAt: Int,
)

data class CcLineSale(
    val id: String,
    val sessionId: String,
    val stockId: String,
    val qty: Int,
)

data class CcSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: CcSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class CcClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class CcRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    var submittedHoursAgo: Int? = null,
    var undoReason: String = "",
)

data class CcNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

class ClerkCounterRepo {
    val users = listOf(
        CcUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        CcUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        CcUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        CcUser("u-dora", "Dora Sy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        CcUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        CcBranch("b-makati", "Makati", "CLINIC", CcDayStatus.OPEN),
        CcBranch("b-bgc", "BGC", "CLINIC", CcDayStatus.PAST),
        CcBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", CcDayStatus.REMITTED),
        CcBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", CcDayStatus.OPEN),
    )

    val stock = mutableStateListOf(
        CcStockItem("k-01", "b-makati", "LIN-500", "Liniment 500ml", "bottle", 450, 16, 16, 6),
        CcStockItem("k-02", "b-makati", "BALM-30", "Herbal balm 30g", "jar", 280, 4, 3, 6),
        CcStockItem("k-03", "b-makati", "BAND-10", "Elastic bandage 10cm", "roll", 120, 20, null, 8),
        CcStockItem("k-04", "b-makati", "OIL-100", "Massage oil 100ml", "bottle", 350, 7, 9, 6),
        CcStockItem("k-05", "b-makati", "HOT-20", "Hot pack large", "piece", 520, 5, null, 4),
        CcStockItem("k-06", "b-bgc", "LIN-500", "Liniment 500ml", "bottle", 450, 6, 6, 6),
        CcStockItem("k-07", "b-bgc", "BALM-30", "Herbal balm 30g", "jar", 280, 9, null, 6),
        CcStockItem("k-08", "b-bgc", "BAND-10", "Elastic bandage 10cm", "roll", 120, 3, 1, 8),
    )

    val lineSales = mutableStateListOf(
        CcLineSale("l-01", "s-01", "k-01", 2),
        CcLineSale("l-02", "s-01", "k-02", 1),
        CcLineSale("l-03", "s-02", "k-02", 1),
        CcLineSale("l-04", "s-06", "k-06", 1),
    )

    val sessions = mutableStateListOf(
        CcSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, CcSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        CcSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, CcSessionStatus.PENDING, 1500, "Ana Reyes"),
        CcSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, CcSessionStatus.PENDING, 1000, "Ana Reyes"),
        CcSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, CcSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
        CcSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, CcSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        CcSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, CcSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        CcClient("c-01", "Maria Clara", "0917-111-0001", 0),
        CcClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        CcClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        CcClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
        CcClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        CcRemittance("r-01", "SESSION", "Snapshot", 19200, "Makati", submittedHoursAgo = 5),
        CcRemittance("r-02", "PRODUCT", "Draft", 1630, "Makati"),
        CcRemittance("r-03", "SESSION", "Snapshot", 22100, "Cebu Tour", submittedHoursAgo = 72),
    )

    val notices = mutableStateListOf(
        CcNotice("n-01", "SCAN SHORT: Herbal balm 30g", "Makati scanned 3 jars against system 4 — variance callout raised.", false),
        CcNotice("n-02", "PRODUCT draft ready", "Makati lane lines sum to unit x quantity math below — submit when the count settles.", false),
        CcNotice("n-03", "Branch day turned PAST", "BGC passed the 04:00 Asia/Manila boundary.", true),
    )

    val audit = mutableStateListOf(
        "08:40 Ana scanned LIN-500: system 16, counted 16 — MATCH",
        "08:55 Ana clocked in at Makati",
        "09:40 Session s-01 COMPLETED by Ana Reyes (2 x LIN-500 linked)",
        "13:05 Session s-04 marked NO_SHOW by Ana Reyes",
        "16:20 Ben submitted SESSION remittance r-01 (snapshot sealed)",
    )

    val reliefBoard = mutableStateListOf(
        "Invite: Makati invites you (Practitioner) for Saturday — pending your accept",
        "Request: You asked BGC for edit access today — APPROVED",
        "Duty: Relief at Cebu Tour last Friday — view-only, expired 04:00 Manila",
    )

    var currentUser by mutableStateOf<CcUser?>(null)
    var currentBranchId by mutableStateOf("b-makati")
    var clockedIn by mutableStateOf(false)
    var sessionSeq by mutableStateOf(7)
    var remitSeq by mutableStateOf(4)
    var lineSeq by mutableStateOf(5)

    fun branch(id: String): CcBranch = branches.first { it.id == id }
    fun currentBranch(): CcBranch = branch(currentBranchId)

    fun branchStock(branchId: String): List<CcStockItem> = stock.filter { it.branchId == branchId }
    fun stockItem(id: String): CcStockItem = stock.first { it.id == id }
    fun lowStock(branchId: String): List<CcStockItem> =
        branchStock(branchId).filter { (it.countedQty ?: it.systemQty) <= it.lowAt }

    // Unit x quantity math, visible everywhere: the PRODUCT draft sums live here.
    fun lineMath(line: CcLineSale): Int = line.qty * stockItem(line.stockId).unitPrice
    fun sessionProductTotal(sessionId: String): Int =
        lineSales.filter { it.sessionId == sessionId }.sumOf { lineMath(it) }
    fun productDraftTotal(branchId: String): Int =
        lineSales.filter { line -> sessions.any { it.id == line.sessionId && it.branchId == branchId } }
            .sumOf { lineMath(it) }

    fun branchSessions(branchId: String): List<CcSession> =
        sessions.filter { it.branchId == branchId }.sortedBy { it.time }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun advance(id: String, next: CcSessionStatus) {
        val s = sessions.first { it.id == id }
        if (s.walkIn && (next == CcSessionStatus.NO_SHOW || next == CcSessionStatus.CANCELLED)) return
        s.status = next
        sessions.remove(s)
        sessions.add(s)
        log("Session $id moved to ${next.name} by ${currentUser?.name ?: "?"}")
    }

    fun toggleVoid(id: String, reason: String) {
        val s = sessions.first { it.id == id }
        s.voided = !s.voided
        s.voidReason = if (s.voided) reason else ""
        log("Session $id ${if (s.voided) "VOIDED ($reason)" else "UNVOIDED"} by ${currentUser?.name ?: "?"}")
    }

    fun recordCount(stockId: String, qty: Int) {
        val item = stockItem(stockId)
        item.countedQty = qty
        val delta = qty - item.systemQty
        val verdict = when {
            delta == 0 -> "MATCH"
            delta > 0 -> "OVER by $delta"
            else -> "SHORT by ${-delta}"
        }
        log("${currentUser?.name ?: "?"} scanned ${item.sku}: system ${item.systemQty}, counted $qty — $verdict")
    }

    fun linkSale(sessionId: String, stockId: String, qty: Int) {
        lineSales.add(CcLineSale("l-%02d".format(lineSeq++), sessionId, stockId, qty))
        val item = stockItem(stockId)
        log("${item.sku} x $qty linked to session $sessionId (₱${item.unitPrice} x $qty = ₱${item.unitPrice * qty})")
    }

    fun submitRemit(id: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Snapshot"
        r.submittedHoursAgo = 0
        log("${r.flow} remittance $id submitted by ${currentUser?.name ?: "?"} (snapshot sealed)")
    }

    fun undoRemit(id: String, reason: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Draft"
        r.undoReason = reason
        r.submittedHoursAgo = null
        log("${r.flow} remittance $id UNDONE within 48h ($reason) — snapshot deleted, days unlocked")
    }

    fun markRead(id: String) {
        notices.first { it.id == id }.read = true
    }

    fun addSession(client: String, kind: String, price: Int) {
        val id = "s-%02d".format(sessionSeq++)
        sessions.add(CcSession(id, "16:30", client, currentBranchId, kind, false, CcSessionStatus.PENDING, price, currentUser?.name ?: "?"))
        log("Session $id booked for $client by ${currentUser?.name ?: "?"}")
    }
}
