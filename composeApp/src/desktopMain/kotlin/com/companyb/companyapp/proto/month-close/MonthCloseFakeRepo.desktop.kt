package com.companyb.companyapp.proto.monthclose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #851 — month-close prototype fake domain: local only, no network, no shared contracts.

enum class McDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class McSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class McRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class McBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class McRemitKind {
    SESSION,
    PRODUCT,
}

enum class McRemitState {
    DRAFT,
    SUBMITTED,
}

data class McBranch(
    val id: String,
    val name: String,
    val kind: McBranchKind,
)

data class McUser(
    val id: String,
    val name: String,
    val role: McRole,
    val homeBranchId: String,
)

data class McDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: McDayStatus,
    val sales: Int,
    val isToday: Boolean = false,
)

data class McSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: McSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class McClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
)

data class McRemittance(
    val dayId: String,
    val kind: McRemitKind,
    val state: McRemitState = McRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class McNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class McAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class McReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class McReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val decided: String? = null,
)

class MonthCloseFakeRepo {
    val branches = mutableStateListOf(
        McBranch("b-qc", "QC Central", McBranchKind.CLINIC),
        McBranch("b-tour", "Laguna Tour Stop 3", McBranchKind.PROVINCIAL_TOUR),
        McBranch("b-tondo", "Tondo Medical Mission", McBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        McUser("u-new", "J. Ramos", McRole.ONBOARDING, "b-qc"),
        McUser("u-prac", "M. Santos", McRole.PRACTITIONER, "b-qc"),
        McUser("u-coord", "R. Aquino", McRole.COORDINATOR, "b-qc"),
        McUser("u-mgr", "D. Lim", McRole.MANAGER, "b-tour"),
        McUser("u-acct", "P. Reyes", McRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        McDay("d-01", "MON", "Sep 1", McDayStatus.REMITTED, sales = 18400),
        McDay("d-02", "TUE", "Sep 2", McDayStatus.REMITTED, sales = 21150),
        McDay("d-03", "WED", "Sep 3", McDayStatus.REMITTED, sales = 19750),
        McDay("d-04", "THU", "Sep 4", McDayStatus.PAST, sales = 22300),
        McDay("d-05", "FRI", "Sep 5", McDayStatus.PAST, sales = 24800),
        McDay("d-06", "SAT", "Sep 6", McDayStatus.PAST, sales = 26100),
        McDay("d-07", "SUN", "Sep 7", McDayStatus.PAST, sales = 15900),
        McDay("d-08", "MON", "Sep 8", McDayStatus.PAST, sales = 17600),
        McDay("d-09", "TUE", "Sep 9", McDayStatus.OPEN, sales = 8200, isToday = true),
    )

    val sessions = mutableStateListOf(
        McSession("s-01", "d-09", "b-qc", "c-01", "A. Villanueva", "09:00", "Deep Tissue 60", McSessionStatus.COMPLETED, 1200, walkIn = false),
        McSession("s-02", "d-09", "b-qc", "c-02", "B. Cruz", "10:30", "Swedish 90", McSessionStatus.PENDING, 1500, walkIn = false),
        McSession("s-03", "d-09", "b-qc", "c-03", "C. Dela Cruz", "11:00", "Walk-in Chair 20", McSessionStatus.PENDING, 500, walkIn = true),
        McSession("s-04", "d-08", "b-qc", "c-04", "D. Ocampo", "14:00", "Hot Stone 75", McSessionStatus.NO_SHOW, 1400, walkIn = false),
        McSession("s-05", "d-08", "b-qc", "c-05", "E. Navarro", "15:30", "Foot Spa 45", McSessionStatus.CANCELLED, 800, walkIn = false),
        McSession("s-06", "d-06", "b-qc", "c-06", "F. Gonzales", "13:00", "Combo 120", McSessionStatus.COMPLETED, 2200, walkIn = false, voided = true, voidReason = "Duplicate entry"),
        McSession("s-07", "d-05", "b-qc", "c-01", "A. Villanueva", "16:00", "Swedish 60", McSessionStatus.COMPLETED, 1100, walkIn = false),
        McSession("s-08", "d-04", "b-qc", "c-02", "B. Cruz", "09:30", "Deep Tissue 90", McSessionStatus.COMPLETED, 1800, walkIn = false, voided = true, voidReason = ""),
        McSession("s-09", "d-07", "b-tour", "c-03", "C. Dela Cruz", "10:00", "Tour Set 45", McSessionStatus.COMPLETED, 900, walkIn = true),
        McSession("s-10", "d-09", "b-qc", "c-04", "D. Ocampo", "17:00", "Aroma 60", McSessionStatus.PENDING, 1300, walkIn = false),
    )

    val clients = mutableStateListOf(
        McClient("c-01", "A. Villanueva", "F", 34),
        McClient("c-02", "B. Cruz", "M", 41),
        McClient("c-03", "C. Dela Cruz", "F", 28),
        McClient("c-04", "D. Ocampo", "M", 52),
        McClient("c-05", "E. Navarro", "F", 37),
        McClient("c-06", "F. Gonzales", "M", 45),
    )

    val remittances = mutableStateListOf(
        McRemittance("d-01", McRemitKind.SESSION, McRemitState.SUBMITTED, snapshotId = "MC-0901-S", snapshotTotal = 14200),
        McRemittance("d-01", McRemitKind.PRODUCT, McRemitState.SUBMITTED, snapshotId = "MC-0901-P", snapshotTotal = 4200),
        McRemittance("d-02", McRemitKind.SESSION, McRemitState.SUBMITTED, snapshotId = "MC-0902-S", snapshotTotal = 16800),
        McRemittance("d-02", McRemitKind.PRODUCT, McRemitState.SUBMITTED, snapshotId = "MC-0902-P", snapshotTotal = 4350),
        McRemittance("d-03", McRemitKind.SESSION, McRemitState.SUBMITTED, snapshotId = "MC-0903-S", snapshotTotal = 15400),
        McRemittance("d-03", McRemitKind.PRODUCT, McRemitState.SUBMITTED, snapshotId = "MC-0903-P", snapshotTotal = 4350),
        McRemittance("d-09", McRemitKind.SESSION, McRemitState.DRAFT, draftTotal = 2700),
        McRemittance("d-09", McRemitKind.PRODUCT, McRemitState.DRAFT, draftTotal = 900),
    )

    val notifications = mutableStateListOf(
        McNotification("n-1", "Close pack: 5 days unremitted", "Sep 4–8 still need remittance before month seal.", read = false),
        McNotification("n-2", "Void needs a reason", "Session s-08 voided without a reason; close is blocked.", read = false),
        McNotification("n-3", "Relief invite: Laguna Tour", "D. Lim invited you to cover Sat Sep 13.", read = false),
        McNotification("n-4", "Snapshot archived", "MC-0903-S sealed and filed in the archive.", read = true),
        McNotification("n-5", "Product folio drafted", "Sep 9 product draft totals P900, awaiting submit.", read = true),
    )

    val audits = mutableStateListOf(
        McAudit("a-1", "Sep 9 08:02", "M. Santos", "CLOCK_IN", "QC Central / Sep 9"),
        McAudit("a-2", "Sep 8 18:40", "R. Aquino", "SESSION_COMPLETED", "s-07 · A. Villanueva"),
        McAudit("a-3", "Sep 8 17:15", "R. Aquino", "VOID", "s-08 · duplicate?", reason = ""),
        McAudit("a-4", "Sep 6 19:05", "P. Reyes", "SNAPSHOT_SEALED", "MC-0903-S · P15400"),
    )

    val invites = mutableStateListOf(
        McReliefInvite("i-1", "Laguna Tour Stop 3", "Sat Sep 13", "D. Lim"),
        McReliefInvite("i-2", "Tondo Medical Mission", "Sun Sep 14", "R. Aquino", accepted = true),
    )

    val requests = mutableStateListOf(
        McReliefRequest("r-1", "QC Central", "Fri Sep 12", mine = true),
        McReliefRequest("r-2", "QC Central", "Sat Sep 13", mine = false),
    )

    var currentUser by mutableStateOf(users[1])
    var currentBranch by mutableStateOf(branches[0])
    var selectedDayId by mutableStateOf("d-09")
    var clockedIn by mutableStateOf(false)
    var onboarded by mutableStateOf(false)
    var anonymized by mutableStateOf(false)
    var auditSeq by mutableStateOf(5)

    fun selectedDay(): McDay = days.firstOrNull { it.id == selectedDayId } ?: days.last()

    fun log(who: String, action: String, target: String, reason: String? = null) {
        val id = "a-${auditSeq++}"
        audits.add(0, McAudit(id, "Sep 9 now", who, action, target, reason))
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == McSessionStatus.PENDING && !it.voided }

    fun unremittedDays(): List<McDay> = days.filter { it.status != McDayStatus.REMITTED }

    fun voidsMissingReason(): List<McSession> = sessions.filter { it.voided && it.voidReason.isNullOrBlank() }

    fun snapshots(): List<McRemittance> = remittances.filter { it.state == McRemitState.SUBMITTED }

    fun closeProgress(): Pair<Int, Int> {
        var done = 0
        val total = 8
        if (days.count { it.status == McDayStatus.REMITTED } >= 3) done += 3
        if (voidsMissingReason().isEmpty()) done += 2 else done += 1
        if (snapshots().size >= 6) done += 2 else done += 1
        if (clockedIn) done += 1
        return done to total
    }

    fun markDayRemitted(dayId: String) {
        val idx = days.indexOfFirst { it.id == dayId }
        if (idx >= 0) {
            val d = days[idx]
            days[idx] = d.copy(status = McDayStatus.REMITTED)
        }
    }
}
