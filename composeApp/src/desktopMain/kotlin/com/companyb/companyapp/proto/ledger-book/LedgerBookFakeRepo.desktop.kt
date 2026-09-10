package com.companyb.companyapp.proto.ledgerbook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #835 — ledger-book prototype fake domain: local only, no network, no shared contracts.

enum class LbDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class LbSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class LbRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class LbBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class LbRemitKind {
    SESSION,
    PRODUCT,
}

enum class LbRemitState {
    DRAFT,
    SUBMITTED,
}

data class LbBranch(
    val id: String,
    val name: String,
    val kind: LbBranchKind,
)

data class LbUser(
    val id: String,
    val name: String,
    val role: LbRole,
    val homeBranchId: String,
)

data class LbDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: LbDayStatus,
    val isToday: Boolean = false,
)

data class LbSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: LbSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class LbClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
)

data class LbRemittance(
    val kind: LbRemitKind,
    val state: LbRemitState = LbRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class LbNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class LbAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class LbReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class LbReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val decided: String? = null,
)

class LedgerBookFakeRepo {
    val branches = mutableStateListOf(
        LbBranch("b-qc", "QC Central", LbBranchKind.CLINIC),
        LbBranch("b-tour", "Laguna Tour Stop 3", LbBranchKind.PROVINCIAL_TOUR),
        LbBranch("b-tondo", "Tondo Medical Mission", LbBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        LbUser("u-new", "J. Ramos", LbRole.ONBOARDING, "b-qc"),
        LbUser("u-prac", "M. Santos", LbRole.PRACTITIONER, "b-qc"),
        LbUser("u-coord", "R. Aquino", LbRole.COORDINATOR, "b-qc"),
        LbUser("u-mgr", "D. Lim", LbRole.MANAGER, "b-tour"),
        LbUser("u-acct", "P. Reyes", LbRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        LbDay("d-mon", "MON", "Sep 7", LbDayStatus.PAST),
        LbDay("d-tue", "TUE", "Sep 8", LbDayStatus.REMITTED),
        LbDay("d-wed", "WED", "Sep 9", LbDayStatus.OPEN, isToday = true),
        LbDay("d-thu", "THU", "Sep 10", LbDayStatus.OPEN),
        LbDay("d-fri", "FRI", "Sep 11", LbDayStatus.OPEN),
        LbDay("d-sat", "SAT", "Sep 12", LbDayStatus.OPEN),
        LbDay("d-sun", "SUN", "Sep 13", LbDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        LbSession("s-01", "d-mon", "b-qc", "c-01", "A. Villanueva", "09:00", "Back rehab",
            LbSessionStatus.COMPLETED, 1200, false),
        LbSession("s-02", "d-mon", "b-qc", "c-02", "B. Ocampo", "11:00", "Sports recovery",
            LbSessionStatus.NO_SHOW, 1500, false),
        LbSession("s-03", "d-tue", "b-qc", "c-03", "C. Navarro", "10:00", "Post-op knee",
            LbSessionStatus.COMPLETED, 1800, false),
        LbSession("s-04", "d-tue", "b-qc", "c-04", "D. Salazar", "14:00", "Neck therapy",
            LbSessionStatus.CANCELLED, 900, false),
        LbSession("s-05", "d-wed", "b-qc", "c-01", "A. Villanueva", "09:30", "Back rehab",
            LbSessionStatus.PENDING, 1200, false),
        LbSession("s-06", "d-wed", "b-qc", "c-05", "E. Torres", "10:15", "Walk-in consult",
            LbSessionStatus.PENDING, 800, true),
        LbSession("s-07", "d-wed", "b-qc", "c-06", "F. Mercado", "13:00", "Shoulder program",
            LbSessionStatus.PENDING, 1400, false),
        LbSession("s-08", "d-thu", "b-qc", "c-02", "B. Ocampo", "09:00", "Sports recovery",
            LbSessionStatus.PENDING, 1500, false),
        LbSession("s-09", "d-fri", "b-qc", "c-03", "C. Navarro", "15:00", "Post-op knee",
            LbSessionStatus.PENDING, 1800, false),
        LbSession("s-10", "d-sat", "b-qc", "c-04", "D. Salazar", "08:30", "Walk-in consult",
            LbSessionStatus.PENDING, 800, true),
    )

    val clients = mutableStateListOf(
        LbClient("c-01", "A. Villanueva", "F", 41),
        LbClient("c-02", "B. Ocampo", "M", 35),
        LbClient("c-03", "C. Navarro", "F", 58),
        LbClient("c-04", "D. Salazar", "M", 47),
        LbClient("c-05", "E. Torres", "F", 29),
        LbClient("c-06", "F. Mercado", "M", 52),
    )

    val notifications = mutableStateListOf(
        LbNotification("n-1", "Relief invite", "R. Aquino invited you to cover Laguna Tour Stop 3 on Sep 12."),
        LbNotification("n-2", "Page sealed", "SESSION folio LB-S0041 submitted and stamped.", read = true),
        LbNotification("n-3", "Day closed", "Tue Sep 8 turned REMITTED at the 04:00 boundary."),
    )

    val audits = mutableStateListOf(
        LbAudit("a-1", "Sep 9 08:02", "M. Santos", "CLOCK_IN", "QC Central / Sep 9"),
        LbAudit("a-2", "Sep 8 04:00", "system", "TRANSITION", "Sep 8 OPEN → PAST",
            "04:00 Asia/Manila boundary"),
    )

    val invites = mutableStateListOf(
        LbReliefInvite("i-1", "Laguna Tour Stop 3", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        LbReliefRequest("r-1", "Tondo Medical Mission", "Sep 13", mine = true),
        LbReliefRequest("r-2", "QC Central", "Sep 10", mine = false),
    )

    var remitSession by mutableStateOf(LbRemittance(LbRemitKind.SESSION, draftTotal = 12400))
    var remitProduct by mutableStateOf(LbRemittance(LbRemitKind.PRODUCT, draftTotal = 3600))
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d-wed")
    var clockedIn by mutableStateOf(false)
    var anonymizedIds by mutableStateOf(setOf<String>())

    private var auditSeq = 3
    private var sessionSeq = 11
    private var folioSeq = 42

    val currentUser: LbUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: LbBranch get() = branches.first { it.id == currentBranchId }
    val selectedDay: LbDay get() = days.first { it.id == selectedDayId }

    fun nextFolioId(kind: LbRemitKind): String = "LB-${kind.name.take(1)}${folioSeq++}"

    fun login(userId: String) {
        currentUserId = userId
    }

    fun logout() {
        clockedIn = false
        currentUserId = null
    }

    fun audit(who: String, action: String, target: String, reason: String? = null) {
        audits.add(0, LbAudit("a-${auditSeq++}", "Sep 9 now", who, action, target, reason))
    }

    fun sessionsFor(dayId: String): List<LbSession> =
        sessions.filter { it.dayId == dayId && it.branchId == currentBranchId }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == LbSessionStatus.PENDING && !it.voided }

    fun dayTotal(dayId: String): Int =
        sessionsFor(dayId).filter { it.status == LbSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun setStatus(sessionId: String, status: LbSessionStatus, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Entry not found in this folio."
        val current = sessions[index]
        if (current.walkIn && (status == LbSessionStatus.NO_SHOW || status == LbSessionStatus.CANCELLED)) {
            return "Walk-in entries cannot be marked NO_SHOW or CANCELLED."
        }
        sessions[index] = current.copy(status = status)
        audit(who, "STATUS", "${current.id} → ${status.name}")
        return null
    }

    fun setVoid(sessionId: String, voided: Boolean, reason: String, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Entry not found in this folio."
        if (voided && reason.isBlank()) return "A reason must be inked in to void."
        val current = sessions[index]
        sessions[index] = current.copy(voided = voided, voidReason = if (voided) reason else null)
        audit(who, if (voided) "VOID" else "UNVOID", current.id, reason.ifBlank { null })
        return null
    }

    fun addWalkIn(clientName: String, time: String, who: String) {
        val id = "s-${sessionSeq++}"
        sessions.add(
            LbSession(
                id, selectedDayId, currentBranchId, "c-walk-$id", clientName, time,
                "Walk-in consult", LbSessionStatus.PENDING, 800, true,
            ),
        )
        audit(who, "CREATE", "$id walk-in $clientName")
    }

    fun updateRemit(kind: LbRemitKind, next: LbRemittance, who: String, action: String) {
        if (kind == LbRemitKind.SESSION) remitSession = next else remitProduct = next
        audit(who, action, "${kind.name} remittance", next.undoReason ?: next.snapshotId)
    }
}
