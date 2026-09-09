package com.companyb.companyapp.proto.kiosktouch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #762 — kiosk-touch fake domain: walk-in kiosk, local only, no network, no shared contracts.

enum class KioskDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class KioskSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class KioskRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class KioskBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class KioskRemitKind {
    SESSION,
    PRODUCT,
}

enum class KioskRemitState {
    DRAFT,
    SUBMITTED,
}

data class KioskBranch(
    val id: String,
    val name: String,
    val kind: KioskBranchKind,
)

data class KioskUser(
    val id: String,
    val name: String,
    val role: KioskRole,
    val homeBranchId: String,
    val slot: Int,
)

data class KioskSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: KioskSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class KioskClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class KioskRemittance(
    val kind: KioskRemitKind,
    val state: KioskRemitState = KioskRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class KioskNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class KioskAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class KioskInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class KioskRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class KioskFakeRepo {
    val branches = mutableStateListOf(
        KioskBranch("qc", "QC Central", KioskBranchKind.CLINIC),
        KioskBranch("tour", "Laguna Tour Stop 3", KioskBranchKind.PROVINCIAL_TOUR),
        KioskBranch("mission", "Tondo Medical Mission", KioskBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        KioskUser("u-new", "J. Ramos (new hire)", KioskRole.ONBOARDING, "qc", 9),
        KioskUser("u-me", "M. Santos", KioskRole.PRACTITIONER, "qc", 2),
        KioskUser("u-coord", "L. Villanueva", KioskRole.COORDINATOR, "qc", 1),
        KioskUser("u-mgr", "R. Aquino", KioskRole.MANAGER, "tour", 1),
        KioskUser("u-acct", "D. Lim", KioskRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        KioskSession("s1", "c1", "A. Cruz", "qc", "09:00", "Standard", KioskSessionStatus.COMPLETED, 1200, false),
        KioskSession("s2", "c2", "B. Reyes", "qc", "10:30", "Deep Tissue", KioskSessionStatus.PENDING, 1500, false),
        KioskSession("s3", "c3", "Walk-in #41", "qc", "11:15", "Standard", KioskSessionStatus.PENDING, 1200, true),
        KioskSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", KioskSessionStatus.NO_SHOW, 1800, false),
        KioskSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", KioskSessionStatus.CANCELLED, 1200, false),
        KioskSession("s6", "c6", "F. Garcia", "qc", "16:00", "Sports", KioskSessionStatus.PENDING, 1600, false),
    )

    val clients = mutableStateListOf(
        KioskClient("c1", "A. Cruz", "F", 34),
        KioskClient("c2", "B. Reyes", "M", 41),
        KioskClient("c3", "Walk-in #41", "M", 29),
        KioskClient("c4", "D. Ocampo", "F", 52),
        KioskClient("c5", "E. Navarro", "F", 38),
        KioskClient("c6", "F. Garcia", "M", 45),
    )

    val remittances = mutableStateListOf(
        KioskRemittance(KioskRemitKind.SESSION, draftTotal = 4350),
        KioskRemittance(KioskRemitKind.PRODUCT, draftTotal = 2800),
    )

    val notifications = mutableStateListOf(
        KioskNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
        KioskNotification("n2", "Remittance snapshot sealed", "SESSION snapshot KS-1001 is now immutable.", false),
        KioskNotification("n3", "Branch day closed", "Sep 09 moved to PAST at 04:00 Asia/Manila.", true),
    )

    val audits = mutableStateListOf(
        KioskAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance KS-1001", null),
        KioskAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "client requested upgrade"),
        KioskAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
    )

    val invites = mutableStateListOf(
        KioskInvite("i1", "tour", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        KioskRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
        KioskRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(KioskDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100

    val currentUser: KioskUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: KioskBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            KioskAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, KioskNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "kiosk touch")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "kiosk touch") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == KioskSessionStatus.PENDING && !it.voided }

    fun dayPendingCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == KioskSessionStatus.PENDING && !it.voided }

    fun dayCompletedTotal(): Int =
        sessions.filter {
            it.branchId == currentBranchId && it.status == KioskSessionStatus.COMPLETED && !it.voided
        }.sumOf { it.price }

    fun setSessionStatus(
        id: String,
        next: KioskSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == KioskSessionStatus.NO_SHOW || next == KioskSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != KioskSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "kiosk", "STATUS", "Session $id -> $next")
        return null
    }

    fun setVoid(
        id: String,
        voided: Boolean,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "A reason is required to void or unvoid."
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        sessions[index] = current.copy(voided = voided, voidReason = if (voided) reason else null)
        audit(
            currentUser?.name ?: "kiosk",
            if (voided) "VOID" else "UNVOID",
            "Session $id",
            reason,
        )
        return null
    }

    fun createWalkIn(
        clientLabel: String,
        type: String,
        price: Int,
    ): String {
        seq += 1
        val id = "w$seq"
        val time = "now"
        val clientId = "c-w$seq"
        clients.add(0, KioskClient(clientId, clientLabel, "M", 30))
        sessions.add(
            0,
            KioskSession(
                id = id,
                clientId = clientId,
                clientName = clientLabel,
                branchId = currentBranchId,
                bookedTime = time,
                type = type,
                status = KioskSessionStatus.PENDING,
                price = price,
                walkIn = true,
                practitioner = currentUser?.name ?: "Front desk",
            ),
        )
        audit(currentUser?.name ?: "kiosk", "CREATE", "Walk-in $id ($type)")
        notify("Walk-in checked in", "$clientLabel checked in for $type at ${currentBranch.name}.")
        return id
    }

    fun toggleAnonymized(clientId: String) {
        val index = clients.indexOfFirst { it.id == clientId }
        if (index < 0) return
        val current = clients[index]
        clients[index] = current.copy(anonymized = !current.anonymized)
        audit(currentUser?.name ?: "kiosk", "ANONYMIZE", "Client $clientId -> ${!current.anonymized}")
    }

    fun submitRemittance(kind: KioskRemitKind): String? {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state == KioskRemitState.SUBMITTED) return "Already submitted."
        seq += 1
        remittances[index] = current.copy(
            state = KioskRemitState.SUBMITTED,
            snapshotId = "KS-$seq",
            snapshotTotal = current.draftTotal,
            submittedAt = "now",
        )
        audit(currentUser?.name ?: "kiosk", "SUBMIT", "$kind remittance KS-$seq")
        notify("Remittance snapshot sealed", "$kind snapshot KS-$seq is now immutable.")
        return null
    }

    fun undoRemittance(
        kind: KioskRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != KioskRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] = current.copy(
            state = KioskRemitState.DRAFT,
            snapshotId = null,
            snapshotTotal = null,
            undoReason = reason,
        )
        audit(currentUser?.name ?: "kiosk", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun adjustDraft(
        kind: KioskRemitKind,
        delta: Int,
    ) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == KioskRemitState.SUBMITTED) return
        remittances[index] = current.copy(draftTotal = (current.draftTotal + delta).coerceAtLeast(0))
    }

    fun decideInvite(
        id: String,
        accept: Boolean,
    ) {
        val index = invites.indexOfFirst { it.id == id }
        if (index < 0) return
        invites[index] = invites[index].copy(accepted = accept)
        audit(
            currentUser?.name ?: "kiosk",
            if (accept) "ACCEPT" else "DECLINE",
            "Invite $id",
        )
    }

    fun decideRequest(
        id: String,
        decision: String,
    ) {
        val index = requests.indexOfFirst { it.id == id }
        if (index < 0) return
        requests[index] = requests[index].copy(decided = decision)
        audit(currentUser?.name ?: "kiosk", decision.uppercase(), "Request $id")
    }

    fun markRead(id: String) {
        val index = notifications.indexOfFirst { it.id == id }
        if (index < 0) return
        notifications[index] = notifications[index].copy(read = true)
    }

    fun markAllRead() {
        for (i in notifications.indices) {
            notifications[i] = notifications[i].copy(read = true)
        }
    }
}
