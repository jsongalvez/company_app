package com.companyb.companyapp.proto.opsfeed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #790 — ops-feed fake domain: everything local, no network, no shared contracts.
// Every mutation also posts an OpsEvent so the feed stays the single living log.

enum class OfDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class OfSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class OfRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class OfBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class OfRemitKind {
    SESSION,
    PRODUCT,
}

enum class OfRemitState {
    DRAFT,
    SUBMITTED,
}

enum class OfDomain {
    SESSION,
    RELIEF,
    REMIT,
    AUDIT,
    NOTE,
    SYSTEM,
}

data class OfBranch(
    val id: String,
    val name: String,
    val kind: OfBranchKind,
)

data class OfUser(
    val id: String,
    val name: String,
    val role: OfRole,
    val homeBranchId: String,
    val slot: Int,
)

data class OfSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: OfSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class OfClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class OfRemittance(
    val kind: OfRemitKind,
    val state: OfRemitState = OfRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class OfNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class OfAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class OfReliefInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class OfReliefRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

data class OfEvent(
    val id: String,
    val timeLabel: String,
    val domain: OfDomain,
    val headline: String,
    val detail: String,
    val actor: String,
)

class OpsFeedFakeRepo {
    val branches = mutableStateListOf(
        OfBranch("qc", "QC Central", OfBranchKind.CLINIC),
        OfBranch("tour", "Laguna Tour Stop 3", OfBranchKind.PROVINCIAL_TOUR),
        OfBranch("mission", "Tondo Medical Mission", OfBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        OfUser("u-new", "J. Ramos (new hire)", OfRole.ONBOARDING, "qc", 9),
        OfUser("u-me", "M. Santos", OfRole.PRACTITIONER, "qc", 2),
        OfUser("u-coord", "L. Villanueva", OfRole.COORDINATOR, "qc", 1),
        OfUser("u-mgr", "R. Aquino", OfRole.MANAGER, "tour", 1),
        OfUser("u-acct", "D. Lim", OfRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        OfSession("s1", "c1", "A. Cruz", "qc", "09:00", "Standard", OfSessionStatus.COMPLETED, 1200, false),
        OfSession("s2", "c2", "B. Reyes", "qc", "10:30", "Deep Tissue", OfSessionStatus.PENDING, 1500, false),
        OfSession("s3", "c3", "Walk-in #41", "qc", "11:15", "Standard", OfSessionStatus.PENDING, 1200, true),
        OfSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", OfSessionStatus.NO_SHOW, 1800, false),
        OfSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", OfSessionStatus.CANCELLED, 1200, false),
        OfSession("s6", "c6", "F. Garcia", "qc", "16:00", "Sports", OfSessionStatus.PENDING, 1600, false),
    )

    val clients = mutableStateListOf(
        OfClient("c1", "A. Cruz", "F", 34),
        OfClient("c2", "B. Reyes", "M", 41),
        OfClient("c3", "Walk-in #41", "M", 29),
        OfClient("c4", "D. Ocampo", "F", 52),
        OfClient("c5", "E. Navarro", "F", 38),
        OfClient("c6", "F. Garcia", "M", 45),
    )

    val remittances = mutableStateListOf(
        OfRemittance(OfRemitKind.SESSION, draftTotal = 4350),
        OfRemittance(OfRemitKind.PRODUCT, draftTotal = 2800),
    )

    val notifications = mutableStateListOf(
        OfNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
        OfNotification("n2", "Remittance snapshot sealed", "SESSION snapshot OF-1007 is now immutable.", false),
        OfNotification("n3", "Branch day closed", "Sep 09 transitioned to PAST at 04:00 Asia/Manila.", true),
    )

    val audits = mutableStateListOf(
        OfAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance OF-1007", null),
        OfAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "client requested upgrade"),
        OfAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
    )

    val invites = mutableStateListOf(
        OfReliefInvite("i1", "tour", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        OfReliefRequest("r1", "qc", "Sep 11", "M. Santos", mine = true),
        OfReliefRequest("r2", "mission", "Sep 13", "J. Ramos (new hire)", mine = false),
    )

    val feed = mutableStateListOf(
        OfEvent("e1", "08:02", OfDomain.REMIT, "SESSION remittance submitted", "Snapshot OF-1007 sealed at ₱4,350.", "L. Villanueva"),
        OfEvent("e2", "08:15", OfDomain.SYSTEM, "Branch day opened", "QC Central opened for Sep 10 under the 04:00 Asia/Manila boundary.", "System"),
        OfEvent("e3", "09:00", OfDomain.SESSION, "Session s1 completed", "A. Cruz · Standard · ₱1,200.", "M. Santos"),
        OfEvent("e4", "09:41", OfDomain.SESSION, "Session s2 upgraded", "Price 1200 -> 1500 at client request.", "M. Santos"),
        OfEvent("e5", "10:05", OfDomain.RELIEF, "Relief access granted", "R. Aquino granted M. Santos QC Central / Sep 11.", "R. Aquino"),
        OfEvent("e6", "10:20", OfDomain.NOTE, "Shift note", "Hot-stone supplies running low; reorder before the weekend tour.", "L. Villanueva"),
        OfEvent("e7", "11:15", OfDomain.SESSION, "Walk-in seated", "Walk-in #41 took 11:15 Standard slot.", "M. Santos"),
        OfEvent("e8", "13:00", OfDomain.SESSION, "Session s4 no-show", "D. Ocampo marked NO_SHOW after the grace window.", "L. Villanueva"),
        OfEvent("e9", "14:30", OfDomain.SESSION, "Session s5 cancelled", "E. Navarro cancelled ahead of cut-off.", "L. Villanueva"),
        OfEvent("e10", "15:44", OfDomain.AUDIT, "Audit checkpoint", "Coordinator reviewed the morning log; no voids outstanding.", "L. Villanueva"),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var operationalDate by mutableStateOf("Sep 10")
    var dayStatus by mutableStateOf(OfDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var onboardingGranted by mutableStateOf(false)

    private var tick = 10
    private var clockHour = 15
    private var clockMinute = 50

    val currentUser: OfUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: OfBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun actorName(): String = currentUser?.name ?: "Signed out"

    fun post(
        domain: OfDomain,
        headline: String,
        detail: String,
    ) {
        tick += 1
        clockMinute += 3
        if (clockMinute >= 60) {
            clockMinute -= 60
            clockHour += 1
        }
        val stamp = "%02d:%02d".format(clockHour, clockMinute)
        feed.add(
            0,
            OfEvent("e-live-$tick", stamp, domain, headline, detail, actorName()),
        )
    }

    fun audit(
        action: String,
        target: String,
        reason: String? = null,
    ) {
        tick += 1
        audits.add(
            0,
            OfAudit("a-live-$tick", "live-$tick", actorName(), action, target, reason),
        )
        post(OfDomain.AUDIT, "$action — $target", reason ?: "Logged to the audit trail.")
    }

    fun dayPendingCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == OfSessionStatus.PENDING && !it.voided }

    fun dayCompletedTotal(): Int =
        sessions.filter { it.branchId == currentBranchId && it.status == OfSessionStatus.COMPLETED }
            .sumOf { it.price }

    fun pendingForClient(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == OfSessionStatus.PENDING && !it.voided }
}
