package com.companyb.companyapp.proto.printledger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #776 — print-ledger prototype fake domain: local only, no network, no shared contracts.

enum class PlDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class PlSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class PlRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class PlBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class PlRemitKind {
    SESSION,
    PRODUCT,
}

enum class PlRemitState {
    DRAFT,
    SUBMITTED,
}

data class PlBranch(
    val id: String,
    val name: String,
    val kind: PlBranchKind,
)

data class PlUser(
    val id: String,
    val name: String,
    val role: PlRole,
    val homeBranchId: String,
)

data class PlDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: PlDayStatus,
    val isToday: Boolean = false,
)

data class PlSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: PlSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class PlClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
)

data class PlRemittance(
    val kind: PlRemitKind,
    val state: PlRemitState = PlRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class PlNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class PlAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class PlReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class PlReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val decided: String? = null,
)

class PrintLedgerFakeRepo {
    val branches = mutableStateListOf(
        PlBranch("b-qc", "QC Central", PlBranchKind.CLINIC),
        PlBranch("b-tour", "Laguna Tour Stop 3", PlBranchKind.PROVINCIAL_TOUR),
        PlBranch("b-tondo", "Tondo Medical Mission", PlBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        PlUser("u-new", "J. Ramos", PlRole.ONBOARDING, "b-qc"),
        PlUser("u-prac", "M. Santos", PlRole.PRACTITIONER, "b-qc"),
        PlUser("u-coord", "R. Aquino", PlRole.COORDINATOR, "b-qc"),
        PlUser("u-mgr", "D. Lim", PlRole.MANAGER, "b-tour"),
        PlUser("u-acct", "P. Reyes", PlRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        PlDay("d-mon", "MON", "Sep 7", PlDayStatus.PAST),
        PlDay("d-tue", "TUE", "Sep 8", PlDayStatus.REMITTED),
        PlDay("d-wed", "WED", "Sep 9", PlDayStatus.OPEN, isToday = true),
        PlDay("d-thu", "THU", "Sep 10", PlDayStatus.OPEN),
        PlDay("d-fri", "FRI", "Sep 11", PlDayStatus.OPEN),
        PlDay("d-sat", "SAT", "Sep 12", PlDayStatus.OPEN),
        PlDay("d-sun", "SUN", "Sep 13", PlDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        PlSession("s-01", "d-mon", "b-qc", "c-01", "A. Villanueva", "09:00", "Back rehab",
            PlSessionStatus.COMPLETED, 1200, false),
        PlSession("s-02", "d-mon", "b-qc", "c-02", "B. Ocampo", "11:00", "Sports recovery",
            PlSessionStatus.NO_SHOW, 1500, false),
        PlSession("s-03", "d-tue", "b-qc", "c-03", "C. Navarro", "10:00", "Post-op knee",
            PlSessionStatus.COMPLETED, 1800, false),
        PlSession("s-04", "d-tue", "b-qc", "c-04", "D. Salazar", "14:00", "Neck therapy",
            PlSessionStatus.CANCELLED, 900, false),
        PlSession("s-05", "d-wed", "b-qc", "c-01", "A. Villanueva", "09:30", "Back rehab",
            PlSessionStatus.PENDING, 1200, false),
        PlSession("s-06", "d-wed", "b-qc", "c-05", "E. Torres", "10:15", "Walk-in consult",
            PlSessionStatus.PENDING, 800, true),
        PlSession("s-07", "d-wed", "b-qc", "c-06", "F. Mercado", "13:00", "Shoulder program",
            PlSessionStatus.PENDING, 1400, false),
        PlSession("s-08", "d-thu", "b-qc", "c-02", "B. Ocampo", "09:00", "Sports recovery",
            PlSessionStatus.PENDING, 1500, false),
        PlSession("s-09", "d-fri", "b-qc", "c-03", "C. Navarro", "15:00", "Post-op knee",
            PlSessionStatus.PENDING, 1800, false),
        PlSession("s-10", "d-sat", "b-qc", "c-04", "D. Salazar", "08:30", "Walk-in consult",
            PlSessionStatus.PENDING, 800, true),
    )

    val clients = mutableStateListOf(
        PlClient("c-01", "A. Villanueva", "F", 41),
        PlClient("c-02", "B. Ocampo", "M", 35),
        PlClient("c-03", "C. Navarro", "F", 58),
        PlClient("c-04", "D. Salazar", "M", 47),
        PlClient("c-05", "E. Torres", "F", 29),
        PlClient("c-06", "F. Mercado", "M", 52),
    )

    val notifications = mutableStateListOf(
        PlNotification("n-1", "Relief invite", "R. Aquino invited you to cover Laguna Tour Stop 3 on Sep 12."),
        PlNotification("n-2", "Receipt sealed", "SESSION receipt RC-0041 submitted.", read = true),
        PlNotification("n-3", "Day closed", "Tue Sep 8 transitioned to REMITTED at the 04:00 boundary."),
    )

    val audits = mutableStateListOf(
        PlAudit("a-1", "Sep 9 08:02", "M. Santos", "CLOCK_IN", "QC Central / Sep 9"),
        PlAudit("a-2", "Sep 8 04:00", "system", "TRANSITION", "Sep 8 OPEN → PAST",
            "04:00 Asia/Manila boundary"),
    )

    val invites = mutableStateListOf(
        PlReliefInvite("i-1", "Laguna Tour Stop 3", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        PlReliefRequest("r-1", "Tondo Medical Mission", "Sep 13", mine = true),
        PlReliefRequest("r-2", "QC Central", "Sep 10", mine = false),
    )

    var remitSession by mutableStateOf(PlRemittance(PlRemitKind.SESSION, draftTotal = 12400))
    var remitProduct by mutableStateOf(PlRemittance(PlRemitKind.PRODUCT, draftTotal = 3600))
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d-wed")
    var clockedIn by mutableStateOf(false)
    var anonymizedIds by mutableStateOf(setOf<String>())

    private var auditSeq = 3
    private var sessionSeq = 11
    private var receiptSeq = 42

    val currentUser: PlUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: PlBranch get() = branches.first { it.id == currentBranchId }
    val selectedDay: PlDay get() = days.first { it.id == selectedDayId }

    fun nextReceiptId(kind: PlRemitKind): String = "RC-${kind.name.take(1)}${receiptSeq++}"

    fun login(userId: String) {
        currentUserId = userId
    }

    fun logout() {
        clockedIn = false
        currentUserId = null
    }

    fun audit(who: String, action: String, target: String, reason: String? = null) {
        audits.add(0, PlAudit("a-${auditSeq++}", "Sep 9 now", who, action, target, reason))
    }

    fun sessionsFor(dayId: String): List<PlSession> =
        sessions.filter { it.dayId == dayId && it.branchId == currentBranchId }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == PlSessionStatus.PENDING && !it.voided }

    fun dayTotal(dayId: String): Int =
        sessionsFor(dayId).filter { it.status == PlSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun setStatus(sessionId: String, status: PlSessionStatus, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (status == PlSessionStatus.NO_SHOW || status == PlSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be marked NO_SHOW or CANCELLED."
        }
        sessions[index] = current.copy(status = status)
        audit(who, "STATUS", "${current.id} → ${status.name}")
        return null
    }

    fun setVoid(sessionId: String, voided: Boolean, reason: String, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Session not found."
        if (voided && reason.isBlank()) return "A reason is required to void."
        val current = sessions[index]
        sessions[index] = current.copy(voided = voided, voidReason = if (voided) reason else null)
        audit(who, if (voided) "VOID" else "UNVOID", current.id, reason.ifBlank { null })
        return null
    }

    fun addWalkIn(clientName: String, time: String, who: String) {
        val id = "s-${sessionSeq++}"
        sessions.add(PlSession(id, selectedDayId, currentBranchId, "c-walk-$id", clientName, time,
            "Walk-in consult", PlSessionStatus.PENDING, 800, true))
        audit(who, "CREATE", "$id walk-in $clientName")
    }

    fun updateRemit(kind: PlRemitKind, next: PlRemittance, who: String, action: String) {
        if (kind == PlRemitKind.SESSION) remitSession = next else remitProduct = next
        audit(who, action, "${kind.name} remittance", next.undoReason ?: next.snapshotId)
    }
}
