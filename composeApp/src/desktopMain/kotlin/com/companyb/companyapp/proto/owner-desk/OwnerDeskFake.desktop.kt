package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #818 — owner-desk fake domain: single-owner private desk, local only, no network, no shared contracts.

enum class OwnerDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class OwnerSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class OwnerRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class OwnerBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class OwnerRemitKind {
    SESSION,
    PRODUCT,
}

enum class OwnerRemitState {
    DRAFT,
    SUBMITTED,
}

data class OwnerBranch(
    val id: String,
    val name: String,
    val kind: OwnerBranchKind,
    val deskLine: String,
)

data class OwnerUser(
    val id: String,
    val name: String,
    val role: OwnerRole,
    val homeBranchId: String,
    val slot: Int,
)

data class OwnerSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: OwnerSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val ticketNo: String? = null,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class OwnerClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = true,
)

data class OwnerTicket(
    val no: String,
    val guest: String,
    val care: String,
    val at: String,
)

data class OwnerRemittance(
    val kind: OwnerRemitKind,
    val state: OwnerRemitState = OwnerRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class OwnerNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class OwnerAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class OwnerInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class OwnerRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class OwnerFakeRepo {
    val branches =
        mutableStateListOf(
            OwnerBranch("qc", "QC Central", OwnerBranchKind.CLINIC, "Flagship · steady book"),
            OwnerBranch("tour", "Laguna Tour Stop 3", OwnerBranchKind.PROVINCIAL_TOUR, "Tent B · thin margin"),
            OwnerBranch("mission", "Tondo Medical Mission", OwnerBranchKind.MEDICAL_MISSION, "Hall · high volume"),
        )

    val users =
        mutableStateListOf(
            OwnerUser("u-new", "J. Ramos (new hire)", OwnerRole.ONBOARDING, "qc", 9),
            OwnerUser("u-me", "M. Santos", OwnerRole.PRACTITIONER, "qc", 2),
            OwnerUser("u-coord", "L. Villanueva", OwnerRole.COORDINATOR, "qc", 1),
            OwnerUser("u-mgr", "R. Aquino", OwnerRole.MANAGER, "tour", 1),
            OwnerUser("u-acct", "D. Lim", OwnerRole.ACCOUNTANT, "qc", 5),
        )

    val sessions =
        mutableStateListOf(
            OwnerSession(
                "s1",
                "c1",
                "A. Cruz",
                "qc",
                "09:00",
                "Standard",
                OwnerSessionStatus.COMPLETED,
                1200,
                false,
                ticketNo = "O-038",
            ),
            OwnerSession(
                "s2",
                "c2",
                "B. Reyes",
                "qc",
                "10:30",
                "Deep Tissue",
                OwnerSessionStatus.PENDING,
                1500,
                false,
                ticketNo = "O-040",
            ),
            OwnerSession(
                "s3",
                "c3",
                "Walk-in guest",
                "qc",
                "11:15",
                "Standard",
                OwnerSessionStatus.PENDING,
                1200,
                true,
                ticketNo = "O-041",
            ),
            OwnerSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", OwnerSessionStatus.NO_SHOW, 1800, false),
            OwnerSession(
                "s5",
                "c5",
                "E. Navarro",
                "qc",
                "14:30",
                "Standard",
                OwnerSessionStatus.CANCELLED,
                1200,
                false,
            ),
            OwnerSession(
                "s6",
                "c6",
                "F. Garcia",
                "qc",
                "16:00",
                "Sports",
                OwnerSessionStatus.PENDING,
                1600,
                false,
                ticketNo = "O-043",
            ),
        )

    val clients =
        mutableStateListOf(
            OwnerClient("c1", "A. Cruz", "F", 34),
            OwnerClient("c2", "B. Reyes", "M", 41),
            OwnerClient("c3", "Walk-in guest", "M", 29),
            OwnerClient("c4", "D. Ocampo", "F", 52),
            OwnerClient("c5", "E. Navarro", "F", 38),
            OwnerClient("c6", "F. Garcia", "M", 45),
        )

    val remittances =
        mutableStateListOf(
            OwnerRemittance(OwnerRemitKind.SESSION, draftTotal = 4350),
            OwnerRemittance(OwnerRemitKind.PRODUCT, draftTotal = 2800),
        )

    val notifications =
        mutableStateListOf(
            OwnerNotification("n1", "Relief approved", "QC Central granted your Sep 11 access (relief).", false),
            OwnerNotification("n2", "Remittance snapshot sealed", "SESSION snapshot BD-1001 is now immutable.", false),
            OwnerNotification("n3", "Branch day closed", "Sep 09 moved to PAST at 04:00 Asia/Manila.", true),
        )

    val audits =
        mutableStateListOf(
            OwnerAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance BD-1001", null),
            OwnerAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 1200 -> 1500", "guest asked for upgrade"),
            OwnerAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
        )

    val invites =
        mutableStateListOf(
            OwnerInvite("i1", "tour", "Sep 12", "R. Aquino"),
        )

    val requests =
        mutableStateListOf(
            OwnerRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
            OwnerRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
        )

    val tickets =
        mutableStateListOf(
            OwnerTicket("O-038", "A. Cruz", "Standard", "09:00"),
            OwnerTicket("O-040", "B. Reyes", "Deep Tissue", "10:30"),
            OwnerTicket("O-041", "Walk-in guest", "Standard", "11:15"),
            OwnerTicket("O-043", "F. Garcia", "Sports", "16:00"),
        )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(OwnerDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100
    private var ticketSeq = 44

    val currentUser: OwnerUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: OwnerBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            OwnerAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, OwnerNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "owner desk")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "owner desk") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == OwnerSessionStatus.PENDING && !it.voided }

    fun queueAhead(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == OwnerSessionStatus.PENDING && !it.voided }

    fun dayCompletedTotal(): Int =
        sessions
            .filter {
                it.branchId == currentBranchId && it.status == OwnerSessionStatus.COMPLETED && !it.voided
            }.sumOf { it.price }

    fun noShowCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == OwnerSessionStatus.NO_SHOW }

    fun voidCount(): Int =
        sessions.count { it.branchId == currentBranchId && it.voided }

    fun unremittedTotal(): Int =
        remittances.filter { it.state == OwnerRemitState.DRAFT }.sumOf { it.draftTotal }

    fun nextTicketNo(): String {
        val no = "O-%03d".format(ticketSeq)
        ticketSeq += 1
        return no
    }

    fun setSessionStatus(
        id: String,
        next: OwnerSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == OwnerSessionStatus.NO_SHOW || next == OwnerSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != OwnerSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "owner", "STATUS", "Session $id -> $next")
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
            currentUser?.name ?: "owner",
            if (voided) "VOID" else "UNVOID",
            "Session $id",
            reason,
        )
        return null
    }

    fun openSession(
        guestLabel: String,
        type: String,
        price: Int,
        returningClientId: String?,
        walkIn: Boolean,
        at: String,
    ): OwnerTicket {
        seq += 1
        val ticketNo = nextTicketNo()
        val id = "w$seq"
        val clientId =
            returningClientId ?: "c-w$seq".also {
                clients.add(0, OwnerClient(it, guestLabel, "M", 30))
            }
        sessions.add(
            0,
            OwnerSession(
                id = id,
                clientId = clientId,
                clientName = guestLabel,
                branchId = currentBranchId,
                bookedTime = at,
                type = type,
                status = OwnerSessionStatus.PENDING,
                price = price,
                walkIn = walkIn,
                ticketNo = ticketNo,
                practitioner = currentUser?.name ?: "Owner desk",
            ),
        )
        val ticket = OwnerTicket(ticketNo, guestLabel, type, at)
        tickets.add(0, ticket)
        audit(currentUser?.name ?: "owner", "CREATE", "Session $id ($type, $ticketNo)")
        notify("Session opened", "$guestLabel took $ticketNo for $type at ${currentBranch.name}.")
        return ticket
    }

    fun toggleAnonymized(clientId: String) {
        val index = clients.indexOfFirst { it.id == clientId }
        if (index < 0) return
        val current = clients[index]
        clients[index] = current.copy(anonymized = !current.anonymized)
        audit(currentUser?.name ?: "owner", "ANONYMIZE", "Client $clientId -> ${!current.anonymized}")
    }

    fun submitRemittance(kind: OwnerRemitKind): String? {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state == OwnerRemitState.SUBMITTED) return "Already submitted."
        seq += 1
        remittances[index] =
            current.copy(
                state = OwnerRemitState.SUBMITTED,
                snapshotId = "BD-$seq",
                snapshotTotal = current.draftTotal,
                submittedAt = "now",
            )
        audit(currentUser?.name ?: "owner", "SUBMIT", "$kind remittance BD-$seq")
        notify("Remittance snapshot sealed", "$kind snapshot BD-$seq is now immutable.")
        return null
    }

    fun undoRemittance(
        kind: OwnerRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != OwnerRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] =
            current.copy(
                state = OwnerRemitState.DRAFT,
                snapshotId = null,
                snapshotTotal = null,
                undoReason = reason,
            )
        audit(currentUser?.name ?: "owner", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun adjustDraft(
        kind: OwnerRemitKind,
        delta: Int,
    ) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == OwnerRemitState.SUBMITTED) return
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
            currentUser?.name ?: "owner",
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
        audit(currentUser?.name ?: "owner", decision.uppercase(), "Request $id")
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
