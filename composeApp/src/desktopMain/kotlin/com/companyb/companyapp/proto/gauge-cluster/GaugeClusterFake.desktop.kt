package com.companyb.companyapp.proto.gaugecluster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #834 — gauge-cluster fake domain: analog instrument-panel clinic ops, local only, no network.

enum class GaugeDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class GaugeSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class GaugeRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class GaugeBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class GaugeRemitKind {
    SESSION,
    PRODUCT,
}

enum class GaugeRemitState {
    DRAFT,
    SUBMITTED,
}

data class GaugeBranch(
    val id: String,
    val name: String,
    val kind: GaugeBranchKind,
    val capacity: Int,
    val grossTarget: Int,
    val reliefNeed: Int,
)

data class GaugeUser(
    val id: String,
    val name: String,
    val role: GaugeRole,
    val homeBranchId: String,
    val slot: Int,
)

data class GaugeSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val startsAt: String,
    val service: String,
    val status: GaugeSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class GaugeClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class GaugeRemittance(
    val kind: GaugeRemitKind,
    val state: GaugeRemitState = GaugeRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class GaugeNotification(
    val id: String,
    val title: String,
    val body: String,
    val at: String,
    val read: Boolean = false,
)

data class GaugeAudit(
    val id: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String?,
    val at: String,
)

data class GaugeInvite(
    val id: String,
    val branchName: String,
    val person: String,
    val day: String,
    val accepted: Boolean? = null,
)

data class GaugeRequest(
    val id: String,
    val branchName: String,
    val requester: String,
    val day: String,
    val state: String = "LIVE",
)

class GaugeFakeRepo {
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var clockedIn by mutableStateOf(false)
    var reliefBranchId by mutableStateOf<String?>(null)
    var reliefEditGranted by mutableStateOf(false)
    var dayStatus by mutableStateOf(GaugeDayStatus.OPEN)
    var operationalDate by mutableStateOf("2026-09-10")
    var onboardGranted by mutableStateOf(false)
    var sessionFilter by mutableStateOf<GaugeSessionStatus?>(null)
    var clientsVeiled by mutableStateOf(true)
    var walkinCounter by mutableStateOf(31)

    val branches = mutableStateListOf(
        GaugeBranch("qc", "QC Central", GaugeBranchKind.CLINIC, capacity = 24, grossTarget = 48_000, reliefNeed = 3),
        GaugeBranch("laguna", "Laguna Tour Stop 3", GaugeBranchKind.PROVINCIAL_TOUR, capacity = 12, grossTarget = 18_000, reliefNeed = 2),
        GaugeBranch("tondo", "Tondo Medical Mission", GaugeBranchKind.MEDICAL_MISSION, capacity = 30, grossTarget = 0, reliefNeed = 4),
    )

    val users = mutableStateListOf(
        GaugeUser("u-onb", "R. Nuevo", GaugeRole.ONBOARDING, "qc", 9),
        GaugeUser("u-prac", "M. Santos", GaugeRole.PRACTITIONER, "qc", 1),
        GaugeUser("u-coor", "J. Reyes", GaugeRole.COORDINATOR, "laguna", 2),
        GaugeUser("u-mgr", "A. Villanueva", GaugeRole.MANAGER, "qc", 3),
        GaugeUser("u-acct", "D. Lim", GaugeRole.ACCOUNTANT, "tondo", 4),
    )

    val sessions = mutableStateListOf(
        GaugeSession("s-101", "c-1", "E. Cruz", "qc", "09:00", "Therapeutic exercise", GaugeSessionStatus.PENDING, 1_200, walkIn = false),
        GaugeSession("s-102", "c-2", "F. Ramos", "qc", "09:40", "Manual therapy", GaugeSessionStatus.PENDING, 1_500, walkIn = true),
        GaugeSession("s-103", "c-3", "G. Aquino", "laguna", "10:20", "Gait training", GaugeSessionStatus.COMPLETED, 900, walkIn = false),
        GaugeSession("s-104", "c-4", "H. Torres", "tondo", "11:00", "Screening", GaugeSessionStatus.NO_SHOW, 0, walkIn = false),
        GaugeSession("s-105", "c-5", "I. Navarro", "qc", "11:30", "Dry needling", GaugeSessionStatus.CANCELLED, 1_800, walkIn = false),
        GaugeSession("s-106", "c-6", "J. Salazar", "laguna", "13:00", "Post-op rehab", GaugeSessionStatus.PENDING, 1_100, walkIn = false),
    )

    val clients = mutableStateListOf(
        GaugeClient("c-1", "E. Cruz", "F", 41),
        GaugeClient("c-2", "F. Ramos", "M", 55),
        GaugeClient("c-3", "G. Aquino", "M", 63),
        GaugeClient("c-4", "H. Torres", "F", 29),
        GaugeClient("c-5", "I. Navarro", "F", 37),
        GaugeClient("c-6", "J. Salazar", "M", 48),
    )

    val remittances = mutableStateListOf(
        GaugeRemittance(GaugeRemitKind.SESSION),
        GaugeRemittance(GaugeRemitKind.PRODUCT, draftTotal = 2_400),
    )

    val notifications = mutableStateListOf(
        GaugeNotification("n-1", "Relief invite", "Tondo Medical Mission asks M. Santos for 2026-09-11 cover.", "08:12", read = false),
        GaugeNotification("n-2", "Day state", "Laguna Tour Stop 3 turns PAST at 04:00 Asia/Manila.", "07:40", read = false),
        GaugeNotification("n-3", "Snapshot sealed", "SESSION snapshot RS-8812 covers QC Central 2026-09-09.", "Yesterday", read = true),
    )

    val audits = mutableStateListOf(
        GaugeAudit("a-1", "J. Reyes", "SUBMIT_REMITTANCE", "SESSION RS-8812", null, "Yesterday"),
        GaugeAudit("a-2", "M. Santos", "COMPLETE_SESSION", "s-103", null, "10:55"),
        GaugeAudit("a-3", "A. Villanueva", "GRANT_MANAGE_USERS", "R. Nuevo", "role review", "08:05"),
    )

    val invites = mutableStateListOf(
        GaugeInvite("i-1", "Tondo Medical Mission", "M. Santos", "2026-09-11"),
    )

    val requests = mutableStateListOf(
        GaugeRequest("r-1", "Laguna Tour Stop 3", "M. Santos", "2026-09-10"),
    )

    fun me(): GaugeUser? = users.firstOrNull { it.id == currentUserId }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == GaugeSessionStatus.PENDING && !it.voided }

    fun branchSessions(branchId: String): List<GaugeSession> = sessions.filter { it.branchId == branchId }

    fun occupancy(branchId: String): Pair<Int, Int> {
        val b = branches.first { it.id == branchId }
        val booked = branchSessions(branchId).count { !it.voided && it.status != GaugeSessionStatus.CANCELLED }
        return booked to b.capacity
    }

    fun gross(branchId: String): Pair<Int, Int> {
        val b = branches.first { it.id == branchId }
        val earned = branchSessions(branchId)
            .filter { it.status == GaugeSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }
        return earned to b.grossTarget
    }

    fun reliefCover(branchId: String): Pair<Int, Int> {
        val b = branches.first { it.id == branchId }
        val covered = (if (reliefBranchId == branchId && clockedIn) 1 else 0) +
            invites.count { it.accepted == true && it.branchName == b.name }
        return covered to b.reliefNeed
    }

    fun audit(who: String, action: String, target: String, reason: String? = null) {
        audits.add(
            0,
            GaugeAudit("a-${audits.size + 1}", who, action, target, reason, "now"),
        )
    }

    fun addWalkIn(branchId: String, name: String, service: String, price: Int) {
        walkinCounter += 1
        val id = "s-w$walkinCounter"
        val clientId = "c-w$walkinCounter"
        clients.add(GaugeClient(clientId, name.ifBlank { "Walk-in $walkinCounter" }, "M", 30))
        sessions.add(
            GaugeSession(
                id, clientId, name.ifBlank { "Walk-in $walkinCounter" }, branchId,
                "next slot", service.ifBlank { "General session" },
                GaugeSessionStatus.PENDING, price, walkIn = true,
            ),
        )
        audit(me()?.name ?: "kiosk", "CREATE_WALKIN_SESSION", id)
    }
}
