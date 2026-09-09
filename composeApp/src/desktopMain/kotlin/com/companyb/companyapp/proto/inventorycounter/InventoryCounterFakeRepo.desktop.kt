package com.companyb.companyapp.proto.inventorycounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #774 — stock-counter fake data. Local only: no ApiClient, no Ktor, no backend.
// Inventory is the hero: every branch holds its own stock, each row carries
// unit x quantity math, low-stock flags feed the PRODUCT remittance draft.

enum class IcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class IcDayStatus { OPEN, PAST, REMITTED }

data class IcUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class IcBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: IcDayStatus,
)

data class IcStockItem(
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

data class IcLineSale(
    val id: String,
    val sessionId: String,
    val stockId: String,
    val qty: Int,
)

data class IcSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: IcSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class IcClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class IcRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    var submittedHoursAgo: Int? = null,
    var undoReason: String = "",
)

data class IcNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

class InventoryCounterRepo {
    val users = listOf(
        IcUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        IcUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        IcUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        IcUser("u-dora", "Dora Sy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        IcUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        IcBranch("b-makati", "Makati", "CLINIC", IcDayStatus.OPEN),
        IcBranch("b-bgc", "BGC", "CLINIC", IcDayStatus.PAST),
        IcBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", IcDayStatus.REMITTED),
        IcBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", IcDayStatus.OPEN),
    )

    val stock = mutableStateListOf(
        IcStockItem("k-01", "b-makati", "LIN-500", "Liniment 500ml", "bottle", 450, 14, 14, 6),
        IcStockItem("k-02", "b-makati", "BALM-30", "Herbal balm 30g", "jar", 280, 5, 5, 6),
        IcStockItem("k-03", "b-makati", "BAND-10", "Elastic bandage 10cm", "roll", 120, 22, null, 8),
        IcStockItem("k-04", "b-makati", "OIL-100", "Massage oil 100ml", "bottle", 350, 3, 2, 6),
        IcStockItem("k-05", "b-makati", "HOT-20", "Hot pack large", "piece", 520, 9, null, 4),
        IcStockItem("k-06", "b-bgc", "LIN-500", "Liniment 500ml", "bottle", 450, 4, 4, 6),
        IcStockItem("k-07", "b-bgc", "BALM-30", "Herbal balm 30g", "jar", 280, 11, null, 6),
        IcStockItem("k-08", "b-bgc", "BAND-10", "Elastic bandage 10cm", "roll", 120, 2, 2, 8),
    )

    val lineSales = mutableStateListOf(
        IcLineSale("l-01", "s-01", "k-01", 2),
        IcLineSale("l-02", "s-01", "k-02", 1),
        IcLineSale("l-03", "s-02", "k-02", 1),
        IcLineSale("l-04", "s-06", "k-06", 1),
    )

    val sessions = mutableStateListOf(
        IcSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, IcSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        IcSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, IcSessionStatus.PENDING, 1500, "Ana Reyes"),
        IcSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, IcSessionStatus.PENDING, 1000, "Ana Reyes"),
        IcSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, IcSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
        IcSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, IcSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        IcSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, IcSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        IcClient("c-01", "Maria Clara", "0917-111-0001", 0),
        IcClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        IcClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        IcClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
        IcClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        IcRemittance("r-01", "SESSION", "Snapshot", 18400, "Makati", submittedHoursAgo = 5),
        IcRemittance("r-02", "PRODUCT", "Draft", 1180, "Makati"),
        IcRemittance("r-03", "SESSION", "Snapshot", 22100, "Cebu Tour", submittedHoursAgo = 72),
    )

    val notices = mutableStateListOf(
        IcNotice("n-01", "LOW STOCK: Massage oil 100ml", "Makati counted 2 bottles against a reorder flag of 6.", false),
        IcNotice("n-02", "PRODUCT draft ready", "Makati product lines sum to unit x quantity math below — submit when counted.", false),
        IcNotice("n-03", "Branch day turned PAST", "BGC passed the 04:00 Asia/Manila boundary.", true),
    )

    val audit = mutableStateListOf(
        "08:40 Ana counted LIN-500: system 14, counted 14 — match",
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

    var currentUser by mutableStateOf<IcUser?>(null)
    var currentBranchId by mutableStateOf("b-makati")
    var clockedIn by mutableStateOf(false)
    var sessionSeq by mutableStateOf(7)
    var remitSeq by mutableStateOf(4)
    var lineSeq by mutableStateOf(5)
    var noticeSeq by mutableStateOf(4)

    fun branch(id: String): IcBranch = branches.first { it.id == id }
    fun currentBranch(): IcBranch = branch(currentBranchId)

    fun branchStock(branchId: String): List<IcStockItem> = stock.filter { it.branchId == branchId }
    fun stockItem(id: String): IcStockItem = stock.first { it.id == id }
    fun lowStock(branchId: String): List<IcStockItem> =
        branchStock(branchId).filter { (it.countedQty ?: it.systemQty) <= it.lowAt }

    // Unit x quantity math, visible everywhere: the PRODUCT draft sums live here.
    fun lineMath(line: IcLineSale): Int = line.qty * stockItem(line.stockId).unitPrice
    fun sessionProductTotal(sessionId: String): Int =
        lineSales.filter { it.sessionId == sessionId }.sumOf { lineMath(it) }
    fun productDraftTotal(branchId: String): Int =
        lineSales.filter { line -> sessions.any { it.id == line.sessionId && it.branchId == branchId } }
            .sumOf { lineMath(it) }

    fun branchSessions(branchId: String): List<IcSession> =
        sessions.filter { it.branchId == branchId }.sortedBy { it.time }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun advance(id: String, next: IcSessionStatus) {
        val s = sessions.first { it.id == id }
        if (s.walkIn && (next == IcSessionStatus.NO_SHOW || next == IcSessionStatus.CANCELLED)) return
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
            delta == 0 -> "match"
            delta > 0 -> "over by $delta"
            else -> "short by ${-delta}"
        }
        log("${currentUser?.name ?: "?"} counted ${item.sku}: system ${item.systemQty}, counted $qty — $verdict")
    }

    fun linkSale(sessionId: String, stockId: String, qty: Int) {
        lineSales.add(IcLineSale("l-%02d".format(lineSeq++), sessionId, stockId, qty))
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
        sessions.add(IcSession(id, "16:30", client, currentBranchId, kind, false, IcSessionStatus.PENDING, price, currentUser?.name ?: "?"))
        log("Session $id booked for $client by ${currentUser?.name ?: "?"}")
    }
}
