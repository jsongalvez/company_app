package com.companyb.companyapp.proto.projectormode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #777 — projector-mode prototype fake domain: local only, no network, no shared contracts.

enum class PmDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class PmSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class PmRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class PmBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class PmRemitKind {
    SESSION,
    PRODUCT,
}

enum class PmRemitState {
    DRAFT,
    SUBMITTED,
}

data class PmBranch(
    val id: String,
    val name: String,
    val kind: PmBranchKind,
)

data class PmUser(
    val id: String,
    val name: String,
    val role: PmRole,
    val homeBranchId: String,
    val slot: Int,
)

data class PmSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: PmSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class PmClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class PmRemittance(
    val kind: PmRemitKind,
    val state: PmRemitState = PmRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class PmNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class PmAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class PmReliefInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class PmReliefRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class ProjectorFakeRepo {
    val branches = mutableStateListOf(
        PmBranch("qc", "QC Central", PmBranchKind.CLINIC),
        PmBranch("tour", "Laguna Tour Stop 3", PmBranchKind.PROVINCIAL_TOUR),
        PmBranch("mission", "Tondo Medical Mission", PmBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        PmUser("u-new", "J. Ramos (new hire)", PmRole.ONBOARDING, "qc", 9),
        PmUser("u-me", "M. Santos", PmRole.PRACTITIONER, "qc", 2),
        PmUser("u-coord", "L. Villanueva", PmRole.COORDINATOR, "qc", 1),
        PmUser("u-mgr", "R. Aquino", PmRole.MANAGER, "tour", 1),
        PmUser("u-acct", "D. Lim", PmRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        PmSession("s1", "c1", "A. Cruz", "qc", "09:00", "Standard", PmSessionStatus.COMPLETED, 1200, false),
        PmSession("s2", "c2", "B. Reyes", "qc", "10:30", "Deep Tissue", PmSessionStatus.PENDING, 1500, false),
        PmSession("s3", "c3", "Walk-in #41", "qc", "11:15", "Standard", PmSessionStatus.PENDING, 1200, true),
        PmSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", PmSessionStatus.NO_SHOW, 1800, false),
        PmSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", PmSessionStatus.CANCELLED, 1200, false),
        PmSession("s6", "c6", "F. Garcia", "qc", "16:00", "Sports", PmSessionStatus.PENDING, 1600, false),
    )

    val clients = mutableStateListOf(
        PmClient("c1", "A. Cruz", "F", 34),
        PmClient("c2", "B. Reyes", "M", 41),
        PmClient("c3", "Walk-in #41", "M", 29),
        PmClient("c4", "D. Ocampo", "F", 52),
        PmClient("c5", "E. Navarro", "F", 38),
        PmClient("c6", "F. Garcia", "M", 45),
    )

    val remittances = mutableStateListOf(
        PmRemittance(PmRemitKind.SESSION, draftTotal = 4350),
        PmRemittance(PmRemitKind.PRODUCT, draftTotal = 2800),
    )

    val notifications = mutableStateListOf(
        PmNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
        PmNotification("n2", "Remittance snapshot sealed", "SESSION snapshot PM-2401 is now immutable.", false),
        PmNotification("n3", "Branch day closed", "Sep 09 transitioned to PAST at 04:00 Asia/Manila.", true),
    )

    val audits = mutableStateListOf(
        PmAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance PM-2401", null),
        PmAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "client requested upgrade"),
        PmAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
    )

    val invites = mutableStateListOf(
        PmReliefInvite("i1", "tour", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        PmReliefRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
        PmReliefRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(PmDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100

    val currentUser: PmUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: PmBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            PmAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, PmNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "desktop projector-mode")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "desktop projector-mode") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == PmSessionStatus.PENDING && !it.voided }

    fun setSessionStatus(
        id: String,
        next: PmSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == PmSessionStatus.NO_SHOW || next == PmSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != PmSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "projector", "STATUS", "Session $id -> $next")
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
        audit(currentUser?.name ?: "projector", if (voided) "VOID" else "UNVOID", "Session $id", reason)
        return null
    }

    fun anonymize(id: String) {
        val index = clients.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = clients[index]
        if (current.anonymized) return
        clients[index] = current.copy(name = "Anonymized ${current.id}", anonymized = true)
        audit(currentUser?.name ?: "projector", "ANONYMIZE", "Client $id")
    }

    fun submitRemittance(kind: PmRemitKind) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == PmRemitState.SUBMITTED) return
        seq += 1
        remittances[index] = current.copy(
            state = PmRemitState.SUBMITTED,
            snapshotId = "PM-$seq",
            snapshotTotal = current.draftTotal,
        )
        audit(currentUser?.name ?: "projector", "SUBMIT", "$kind remittance PM-$seq")
        notify("Remittance submitted", "$kind snapshot PM-$seq sealed and immutable.")
    }

    fun undoRemittance(
        kind: PmRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != PmRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] = current.copy(state = PmRemitState.DRAFT, snapshotId = null, snapshotTotal = null)
        audit(currentUser?.name ?: "projector", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun decideInvite(
        id: String,
        accept: Boolean,
    ) {
        val index = invites.indexOfFirst { it.id == id }
        if (index < 0) return
        invites[index] = invites[index].copy(accepted = accept)
        audit(currentUser?.name ?: "projector", if (accept) "ACCEPT" else "DECLINE", "Relief invite $id")
    }

    fun decideRequest(
        id: String,
        decision: String,
    ) {
        val index = requests.indexOfFirst { it.id == id }
        if (index < 0) return
        requests[index] = requests[index].copy(decided = decision)
        audit(currentUser?.name ?: "projector", decision.uppercase(), "Relief request $id")
    }

    fun markRead(id: String) {
        val index = notifications.indexOfFirst { it.id == id }
        if (index < 0) return
        notifications[index] = notifications[index].copy(read = true)
    }

    fun markAllRead() {
        for (i in notifications.indices) notifications[i] = notifications[i].copy(read = true)
    }

    fun dayCompletedTotal(): Int =
        sessions.filter { it.branchId == currentBranchId && it.status == PmSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun dayPendingCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == PmSessionStatus.PENDING && !it.voided }
}
