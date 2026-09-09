package com.companyb.companyapp.proto.wallboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// #759 — wallboard prototype fake domain: local only, no network, no shared contracts.

enum class WbDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class WbSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class WbRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class WbBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class WbRemitKind {
    SESSION,
    PRODUCT,
}

enum class WbRemitState {
    DRAFT,
    SUBMITTED,
}

data class WbBranch(
    val id: String,
    val name: String,
    val kind: WbBranchKind,
)

data class WbUser(
    val id: String,
    val name: String,
    val role: WbRole,
    val homeBranchId: String,
    val slot: Int,
)

data class WbSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: WbSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class WbClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class WbRemittance(
    val kind: WbRemitKind,
    val state: WbRemitState = WbRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class WbNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class WbAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class WbReliefInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class WbReliefRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class WallboardFakeRepo {
    val branches = mutableStateListOf(
        WbBranch("qc", "QC Central", WbBranchKind.CLINIC),
        WbBranch("tour", "Laguna Tour Stop 3", WbBranchKind.PROVINCIAL_TOUR),
        WbBranch("mission", "Tondo Medical Mission", WbBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        WbUser("u-new", "J. Ramos (new hire)", WbRole.ONBOARDING, "qc", 9),
        WbUser("u-me", "M. Santos", WbRole.PRACTITIONER, "qc", 2),
        WbUser("u-coord", "L. Villanueva", WbRole.COORDINATOR, "qc", 1),
        WbUser("u-mgr", "R. Aquino", WbRole.MANAGER, "tour", 1),
        WbUser("u-acct", "D. Lim", WbRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        WbSession("s1", "c1", "A. Cruz", "qc", "09:00", "Standard", WbSessionStatus.COMPLETED, 1200, false),
        WbSession("s2", "c2", "B. Reyes", "qc", "10:30", "Deep Tissue", WbSessionStatus.PENDING, 1500, false),
        WbSession("s3", "c3", "Walk-in #41", "qc", "11:15", "Standard", WbSessionStatus.PENDING, 1200, true),
        WbSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", WbSessionStatus.NO_SHOW, 1800, false),
        WbSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", WbSessionStatus.CANCELLED, 1200, false),
        WbSession("s6", "c6", "F. Garcia", "qc", "16:00", "Sports", WbSessionStatus.PENDING, 1600, false),
    )

    val clients = mutableStateListOf(
        WbClient("c1", "A. Cruz", "F", 34),
        WbClient("c2", "B. Reyes", "M", 41),
        WbClient("c3", "Walk-in #41", "M", 29),
        WbClient("c4", "D. Ocampo", "F", 52),
        WbClient("c5", "E. Navarro", "F", 38),
        WbClient("c6", "F. Garcia", "M", 45),
    )

    val remittances = mutableStateListOf(
        WbRemittance(WbRemitKind.SESSION, draftTotal = 4350),
        WbRemittance(WbRemitKind.PRODUCT, draftTotal = 2800),
    )

    val notifications = mutableStateListOf(
        WbNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
        WbNotification("n2", "Remittance snapshot sealed", "SESSION snapshot WB-2401 is now immutable.", false),
        WbNotification("n3", "Branch day closed", "Sep 09 transitioned to PAST at 04:00 Asia/Manila.", true),
    )

    val audits = mutableStateListOf(
        WbAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance WB-2401", null),
        WbAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "client requested upgrade"),
        WbAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
    )

    val invites = mutableStateListOf(
        WbReliefInvite("i1", "tour", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        WbReliefRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
        WbReliefRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(WbDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100

    val currentUser: WbUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: WbBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            WbAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, WbNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "desktop wallboard")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "desktop wallboard") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == WbSessionStatus.PENDING && !it.voided }

    fun setSessionStatus(
        id: String,
        next: WbSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == WbSessionStatus.NO_SHOW || next == WbSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != WbSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "wallboard", "STATUS", "Session $id -> $next")
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
        audit(currentUser?.name ?: "wallboard", if (voided) "VOID" else "UNVOID", "Session $id", reason)
        return null
    }

    fun anonymize(id: String) {
        val index = clients.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = clients[index]
        if (current.anonymized) return
        clients[index] = current.copy(name = "Anonymized ${current.id}", anonymized = true)
        audit(currentUser?.name ?: "wallboard", "ANONYMIZE", "Client $id")
    }

    fun submitRemittance(kind: WbRemitKind) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == WbRemitState.SUBMITTED) return
        seq += 1
        remittances[index] = current.copy(
            state = WbRemitState.SUBMITTED,
            snapshotId = "WB-$seq",
            snapshotTotal = current.draftTotal,
        )
        audit(currentUser?.name ?: "wallboard", "SUBMIT", "$kind remittance WB-$seq")
        notify("Remittance submitted", "$kind snapshot WB-$seq sealed and immutable.")
    }

    fun undoRemittance(
        kind: WbRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != WbRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] = current.copy(state = WbRemitState.DRAFT, snapshotId = null, snapshotTotal = null)
        audit(currentUser?.name ?: "wallboard", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun decideInvite(
        id: String,
        accept: Boolean,
    ) {
        val index = invites.indexOfFirst { it.id == id }
        if (index < 0) return
        invites[index] = invites[index].copy(accepted = accept)
        audit(currentUser?.name ?: "wallboard", if (accept) "ACCEPT" else "DECLINE", "Relief invite $id")
    }

    fun decideRequest(
        id: String,
        decision: String,
    ) {
        val index = requests.indexOfFirst { it.id == id }
        if (index < 0) return
        requests[index] = requests[index].copy(decided = decision)
        audit(currentUser?.name ?: "wallboard", decision.uppercase(), "Relief request $id")
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
        sessions.filter { it.branchId == currentBranchId && it.status == WbSessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun dayPendingCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == WbSessionStatus.PENDING && !it.voided }
}
