package com.companyb.companyapp.proto.calendarops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #775 — calendar-ops prototype fake domain: local only, no network, no shared contracts.

enum class CoDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class CoSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class CoRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class CoBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class CoRemitKind {
    SESSION,
    PRODUCT,
}

enum class CoRemitState {
    DRAFT,
    SUBMITTED,
}

data class CoBranch(
    val id: String,
    val name: String,
    val kind: CoBranchKind,
)

data class CoUser(
    val id: String,
    val name: String,
    val role: CoRole,
    val homeBranchId: String,
)

data class CoDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: CoDayStatus,
    val isToday: Boolean = false,
)

data class CoSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: CoSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class CoClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
)

data class CoRemittance(
    val kind: CoRemitKind,
    val state: CoRemitState = CoRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class CoNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class CoAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class CoReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class CoReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val decided: String? = null,
)

class CalendarOpsFakeRepo {
    val branches = mutableStateListOf(
        CoBranch("b-qc", "QC Central", CoBranchKind.CLINIC),
        CoBranch("b-tour", "Laguna Tour Stop 3", CoBranchKind.PROVINCIAL_TOUR),
        CoBranch("b-tondo", "Tondo Medical Mission", CoBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        CoUser("u-new", "J. Ramos", CoRole.ONBOARDING, "b-qc"),
        CoUser("u-prac", "M. Santos", CoRole.PRACTITIONER, "b-qc"),
        CoUser("u-coord", "R. Aquino", CoRole.COORDINATOR, "b-qc"),
        CoUser("u-mgr", "D. Lim", CoRole.MANAGER, "b-tour"),
        CoUser("u-acct", "P. Reyes", CoRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        CoDay("d-mon", "MON", "Sep 7", CoDayStatus.PAST),
        CoDay("d-tue", "TUE", "Sep 8", CoDayStatus.REMITTED),
        CoDay("d-wed", "WED", "Sep 9", CoDayStatus.OPEN, isToday = true),
        CoDay("d-thu", "THU", "Sep 10", CoDayStatus.OPEN),
        CoDay("d-fri", "FRI", "Sep 11", CoDayStatus.OPEN),
        CoDay("d-sat", "SAT", "Sep 12", CoDayStatus.OPEN),
        CoDay("d-sun", "SUN", "Sep 13", CoDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        CoSession("s-01", "d-mon", "b-qc", "c-01", "A. Villanueva", "09:00", "Back rehab",
            CoSessionStatus.COMPLETED, 1200, false),
        CoSession("s-02", "d-mon", "b-qc", "c-02", "B. Ocampo", "11:00", "Sports recovery",
            CoSessionStatus.NO_SHOW, 1500, false),
        CoSession("s-03", "d-tue", "b-qc", "c-03", "C. Navarro", "10:00", "Post-op knee",
            CoSessionStatus.COMPLETED, 1800, false),
        CoSession("s-04", "d-tue", "b-qc", "c-04", "D. Salazar", "14:00", "Neck therapy",
            CoSessionStatus.CANCELLED, 900, false),
        CoSession("s-05", "d-wed", "b-qc", "c-01", "A. Villanueva", "09:30", "Back rehab",
            CoSessionStatus.PENDING, 1200, false),
        CoSession("s-06", "d-wed", "b-qc", "c-05", "E. Torres", "10:15", "Walk-in consult",
            CoSessionStatus.PENDING, 800, true),
        CoSession("s-07", "d-wed", "b-qc", "c-06", "F. Mercado", "13:00", "Shoulder program",
            CoSessionStatus.PENDING, 1400, false),
        CoSession("s-08", "d-thu", "b-qc", "c-02", "B. Ocampo", "09:00", "Sports recovery",
            CoSessionStatus.PENDING, 1500, false),
        CoSession("s-09", "d-fri", "b-qc", "c-03", "C. Navarro", "15:00", "Post-op knee",
            CoSessionStatus.PENDING, 1800, false),
        CoSession("s-10", "d-sat", "b-qc", "c-04", "D. Salazar", "08:30", "Walk-in consult",
            CoSessionStatus.PENDING, 800, true),
    )

    val clients = mutableStateListOf(
        CoClient("c-01", "A. Villanueva", "F", 41),
        CoClient("c-02", "B. Ocampo", "M", 35),
        CoClient("c-03", "C. Navarro", "F", 58),
        CoClient("c-04", "D. Salazar", "M", 47),
        CoClient("c-05", "E. Torres", "F", 29),
        CoClient("c-06", "F. Mercado", "M", 52),
    )

    val notifications = mutableStateListOf(
        CoNotification("n-1", "Relief invite", "R. Aquino invited you to cover Laguna Tour Stop 3 on Sep 12."),
        CoNotification("n-2", "Snapshot sealed", "SESSION remittance snapshot SN-0041 submitted.", read = true),
        CoNotification("n-3", "Day closed", "Tue Sep 8 transitioned to REMITTED at the 04:00 boundary."),
    )

    val audits = mutableStateListOf(
        CoAudit("a-1", "Sep 9 08:02", "M. Santos", "CLOCK_IN", "QC Central / Sep 9"),
        CoAudit("a-2", "Sep 8 04:00", "system", "TRANSITION", "Sep 8 OPEN → PAST",
            "04:00 Asia/Manila boundary"),
    )

    val invites = mutableStateListOf(
        CoReliefInvite("i-1", "Laguna Tour Stop 3", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        CoReliefRequest("r-1", "Tondo Medical Mission", "Sep 13", mine = true),
        CoReliefRequest("r-2", "QC Central", "Sep 10", mine = false),
    )

    var remitSession by mutableStateOf(CoRemittance(CoRemitKind.SESSION, draftTotal = 12400))
    var remitProduct by mutableStateOf(CoRemittance(CoRemitKind.PRODUCT, draftTotal = 3600))
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d-wed")
    var clockedIn by mutableStateOf(false)
    var anonymizedIds by mutableStateOf(setOf<String>())

    private var auditSeq = 3
    private var sessionSeq = 11

    val currentUser: CoUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: CoBranch get() = branches.first { it.id == currentBranchId }
    val selectedDay: CoDay get() = days.first { it.id == selectedDayId }

    fun login(userId: String) {
        currentUserId = userId
    }

    fun logout() {
        clockedIn = false
        currentUserId = null
    }

    fun audit(who: String, action: String, target: String, reason: String? = null) {
        audits.add(0, CoAudit("a-${auditSeq++}", "Sep 9 now", who, action, target, reason))
    }

    fun sessionsFor(dayId: String): List<CoSession> =
        sessions.filter { it.dayId == dayId && it.branchId == currentBranchId }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == CoSessionStatus.PENDING && !it.voided }

    fun setStatus(sessionId: String, status: CoSessionStatus, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (status == CoSessionStatus.NO_SHOW || status == CoSessionStatus.CANCELLED)) {
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
        sessions.add(CoSession(id, selectedDayId, currentBranchId, "c-walk-$id", clientName, time,
            "Walk-in consult", CoSessionStatus.PENDING, 800, true))
        audit(who, "CREATE", "$id walk-in $clientName")
    }

    fun updateRemit(kind: CoRemitKind, next: CoRemittance, who: String, action: String) {
        if (kind == CoRemitKind.SESSION) remitSession = next else remitProduct = next
        audit(who, action, "${kind.name} remittance", next.undoReason ?: next.snapshotId)
    }
}
