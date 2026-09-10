package com.companyb.companyapp.proto.papercraft

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #842 — paper-craft prototype fake domain: local only, no network, no shared contracts.

enum class PcDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class PcSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class PcRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class PcBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class PcRemitKind {
    SESSION,
    PRODUCT,
}

enum class PcRemitState {
    DRAFT,
    SUBMITTED,
}

data class PcBranch(
    val id: String,
    val name: String,
    val kind: PcBranchKind,
)

data class PcUser(
    val id: String,
    val name: String,
    val role: PcRole,
    val homeBranchId: String,
)

data class PcDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: PcDayStatus,
    val isToday: Boolean = false,
)

data class PcSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: PcSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class PcClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
)

data class PcRemittance(
    val kind: PcRemitKind,
    val state: PcRemitState = PcRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class PcNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class PcAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class PcReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class PcReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val decided: String? = null,
)

class PaperCraftFakeRepo {
    val branches = mutableStateListOf(
        PcBranch("b-qc", "QC Central", PcBranchKind.CLINIC),
        PcBranch("b-tour", "Laguna Tour Stop 3", PcBranchKind.PROVINCIAL_TOUR),
        PcBranch("b-tondo", "Tondo Medical Mission", PcBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        PcUser("u-new", "J. Ramos", PcRole.ONBOARDING, "b-qc"),
        PcUser("u-prac", "M. Santos", PcRole.PRACTITIONER, "b-qc"),
        PcUser("u-coord", "R. Aquino", PcRole.COORDINATOR, "b-qc"),
        PcUser("u-mgr", "D. Lim", PcRole.MANAGER, "b-tour"),
        PcUser("u-acct", "P. Reyes", PcRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        PcDay("d-mon", "MON", "Sep 7", PcDayStatus.PAST),
        PcDay("d-tue", "TUE", "Sep 8", PcDayStatus.REMITTED),
        PcDay("d-wed", "WED", "Sep 9", PcDayStatus.OPEN, isToday = true),
        PcDay("d-thu", "THU", "Sep 10", PcDayStatus.OPEN),
        PcDay("d-fri", "FRI", "Sep 11", PcDayStatus.OPEN),
        PcDay("d-sat", "SAT", "Sep 12", PcDayStatus.OPEN),
        PcDay("d-sun", "SUN", "Sep 13", PcDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        PcSession("s-01", "d-mon", "b-qc", "c-01", "A. Villanueva", "09:00", "Back rehab",
            PcSessionStatus.COMPLETED, 1200, false),
        PcSession("s-02", "d-mon", "b-qc", "c-02", "B. Ocampo", "11:00", "Sports recovery",
            PcSessionStatus.NO_SHOW, 1500, false),
        PcSession("s-03", "d-tue", "b-qc", "c-03", "C. Navarro", "10:00", "Post-op knee",
            PcSessionStatus.COMPLETED, 1800, false),
        PcSession("s-04", "d-tue", "b-qc", "c-04", "D. Salazar", "14:00", "Neck therapy",
            PcSessionStatus.CANCELLED, 900, false),
        PcSession("s-05", "d-wed", "b-qc", "c-01", "A. Villanueva", "09:30", "Back rehab",
            PcSessionStatus.PENDING, 1200, false),
        PcSession("s-06", "d-wed", "b-qc", "c-05", "E. Torres", "10:15", "Walk-in consult",
            PcSessionStatus.PENDING, 800, true),
        PcSession("s-07", "d-wed", "b-qc", "c-06", "F. Mercado", "13:00", "Shoulder program",
            PcSessionStatus.PENDING, 1400, false),
        PcSession("s-08", "d-thu", "b-qc", "c-02", "B. Ocampo", "09:00", "Sports recovery",
            PcSessionStatus.PENDING, 1500, false),
        PcSession("s-09", "d-fri", "b-qc", "c-03", "C. Navarro", "15:00", "Post-op knee",
            PcSessionStatus.PENDING, 1800, false),
        PcSession("s-10", "d-sat", "b-qc", "c-04", "D. Salazar", "08:30", "Walk-in consult",
            PcSessionStatus.PENDING, 800, true),
    )

    val clients = mutableStateListOf(
        PcClient("c-01", "A. Villanueva", "F", 41),
        PcClient("c-02", "B. Ocampo", "M", 35),
        PcClient("c-03", "C. Navarro", "F", 58),
        PcClient("c-04", "D. Salazar", "M", 47),
        PcClient("c-05", "E. Torres", "F", 29),
        PcClient("c-06", "F. Mercado", "M", 52),
    )

    val notifications = mutableStateListOf(
        PcNotification("n-1", "Relief invite", "R. Aquino invited you to cover Laguna Tour Stop 3 on Sep 12."),
        PcNotification("n-2", "Cut-out sealed", "SESSION cut-out PC-S0041 submitted and taped down.", read = true),
        PcNotification("n-3", "Day pasted shut", "Tue Sep 8 turned REMITTED at the 04:00 boundary."),
    )

    val audits = mutableStateListOf(
        PcAudit("a-1", "Sep 9 08:02", "M. Santos", "CLOCK_IN", "QC Central / Sep 9"),
        PcAudit("a-2", "Sep 8 04:00", "system", "TRANSITION", "Sep 8 OPEN → PAST",
            "04:00 Asia/Manila boundary"),
    )

    val invites = mutableStateListOf(
        PcReliefInvite("i-1", "Laguna Tour Stop 3", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        PcReliefRequest("r-1", "Tondo Medical Mission", "Sep 13", mine = true),
        PcReliefRequest("r-2", "QC Central", "Sep 10", mine = false),
    )

    var remitSession by mutableStateOf(PcRemittance(PcRemitKind.SESSION, draftTotal = 12400))
    var remitProduct by mutableStateOf(PcRemittance(PcRemitKind.PRODUCT, draftTotal = 3600))
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d-wed")
    var clockedIn by mutableStateOf(false)
    var anonymizedIds by mutableStateOf(setOf<String>())

    private var auditSeq = 3
    private var sessionSeq = 11
    private var folioSeq = 42

    val currentUser: PcUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: PcBranch get() = branches.first { it.id == currentBranchId }
    val selectedDay: PcDay get() = days.first { it.id == selectedDayId }

    fun nextCutId(kind: PcRemitKind): String = "PC-${kind.name.take(1)}${folioSeq++}"

    fun login(userId: String) {
        currentUserId = userId
    }

    fun logout() {
        clockedIn = false
        currentUserId = null
    }

    fun audit(who: String, action: String, target: String, reason: String? = null) {
        audits.add(0, PcAudit("a-${auditSeq++}", "Sep 9 now", who, action, target, reason))
    }

    fun sessionsFor(dayId: String): List<PcSession> =
        sessions.filter { it.dayId == dayId && it.branchId == currentBranchId }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == PcSessionStatus.PENDING && !it.voided }

    fun dayTotal(dayId: String): Int =
        sessionsFor(dayId).filter { it.status == PcSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun setStatus(sessionId: String, status: PcSessionStatus, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Cut-out not found on this sheet."
        val current = sessions[index]
        if (current.walkIn && (status == PcSessionStatus.NO_SHOW || status == PcSessionStatus.CANCELLED)) {
            return "Walk-in cut-outs cannot be marked NO_SHOW or CANCELLED."
        }
        sessions[index] = current.copy(status = status)
        audit(who, "STATUS", "${current.id} → ${status.name}")
        return null
    }

    fun setVoid(sessionId: String, voided: Boolean, reason: String, who: String): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "Cut-out not found on this sheet."
        if (voided && reason.isBlank()) return "A reason must be written on the tape to snip."
        val current = sessions[index]
        sessions[index] = current.copy(voided = voided, voidReason = if (voided) reason else null)
        audit(who, if (voided) "VOID" else "UNVOID", current.id, reason.ifBlank { null })
        return null
    }

    fun addWalkIn(clientName: String, time: String, who: String) {
        val id = "s-${sessionSeq++}"
        sessions.add(
            PcSession(
                id, selectedDayId, currentBranchId, "c-walk-$id", clientName, time,
                "Walk-in consult", PcSessionStatus.PENDING, 800, true,
            ),
        )
        audit(who, "CREATE", "$id walk-in $clientName")
    }

    fun updateRemit(kind: PcRemitKind, next: PcRemittance, who: String, action: String) {
        if (kind == PcRemitKind.SESSION) remitSession = next else remitProduct = next
        audit(who, action, "${kind.name} remittance", next.undoReason ?: next.snapshotId)
    }
}
