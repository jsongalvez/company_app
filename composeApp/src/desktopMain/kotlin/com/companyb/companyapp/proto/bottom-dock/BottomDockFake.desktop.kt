package com.companyb.companyapp.proto.bottomdock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #810 — bottom-dock fake domain: macOS-dock desktop, local only, no network, no shared contracts.

enum class DockDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class DockSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class DockRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class DockBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class DockRemitKind {
    SESSION,
    PRODUCT,
}

enum class DockRemitState {
    DRAFT,
    SUBMITTED,
}

data class DockBranch(
    val id: String,
    val name: String,
    val kind: DockBranchKind,
    val dockLine: String,
)

data class DockUser(
    val id: String,
    val name: String,
    val role: DockRole,
    val homeBranchId: String,
    val slot: Int,
)

data class DockSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: DockSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val ticketNo: String? = null,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class DockClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class DockTicket(
    val no: String,
    val guest: String,
    val care: String,
    val at: String,
)

data class DockRemittance(
    val kind: DockRemitKind,
    val state: DockRemitState = DockRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class DockNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class DockAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class DockInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class DockRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class DockFakeRepo {
    val branches =
        mutableStateListOf(
            DockBranch("qc", "QC Central", DockBranchKind.CLINIC, "Main floor · 2 chairs free"),
            DockBranch("tour", "Laguna Tour Stop 3", DockBranchKind.PROVINCIAL_TOUR, "Tent B · short line"),
            DockBranch("mission", "Tondo Medical Mission", DockBranchKind.MEDICAL_MISSION, "Hall · busy morning"),
        )

    val users =
        mutableStateListOf(
            DockUser("u-new", "J. Ramos (new hire)", DockRole.ONBOARDING, "qc", 9),
            DockUser("u-me", "M. Santos", DockRole.PRACTITIONER, "qc", 2),
            DockUser("u-coord", "L. Villanueva", DockRole.COORDINATOR, "qc", 1),
            DockUser("u-mgr", "R. Aquino", DockRole.MANAGER, "tour", 1),
            DockUser("u-acct", "D. Lim", DockRole.ACCOUNTANT, "qc", 5),
        )

    val sessions =
        mutableStateListOf(
            DockSession(
                "s1",
                "c1",
                "A. Cruz",
                "qc",
                "09:00",
                "Standard",
                DockSessionStatus.COMPLETED,
                1200,
                false,
                ticketNo = "D-038",
            ),
            DockSession(
                "s2",
                "c2",
                "B. Reyes",
                "qc",
                "10:30",
                "Deep Tissue",
                DockSessionStatus.PENDING,
                1500,
                false,
                ticketNo = "D-040",
            ),
            DockSession(
                "s3",
                "c3",
                "Walk-in guest",
                "qc",
                "11:15",
                "Standard",
                DockSessionStatus.PENDING,
                1200,
                true,
                ticketNo = "D-041",
            ),
            DockSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", DockSessionStatus.NO_SHOW, 1800, false),
            DockSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", DockSessionStatus.CANCELLED, 1200, false),
            DockSession(
                "s6",
                "c6",
                "F. Garcia",
                "qc",
                "16:00",
                "Sports",
                DockSessionStatus.PENDING,
                1600,
                false,
                ticketNo = "D-043",
            ),
        )

    val clients =
        mutableStateListOf(
            DockClient("c1", "A. Cruz", "F", 34),
            DockClient("c2", "B. Reyes", "M", 41),
            DockClient("c3", "Walk-in guest", "M", 29),
            DockClient("c4", "D. Ocampo", "F", 52),
            DockClient("c5", "E. Navarro", "F", 38),
            DockClient("c6", "F. Garcia", "M", 45),
        )

    val remittances =
        mutableStateListOf(
            DockRemittance(DockRemitKind.SESSION, draftTotal = 4350),
            DockRemittance(DockRemitKind.PRODUCT, draftTotal = 2800),
        )

    val notifications =
        mutableStateListOf(
            DockNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
            DockNotification("n2", "Remittance snapshot sealed", "SESSION snapshot BD-1001 is now immutable.", false),
            DockNotification("n3", "Branch day closed", "Sep 09 moved to PAST at 04:00 Asia/Manila.", true),
        )

    val audits =
        mutableStateListOf(
            DockAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance BD-1001", null),
            DockAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "guest asked for upgrade"),
            DockAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
        )

    val invites =
        mutableStateListOf(
            DockInvite("i1", "tour", "Sep 12", "R. Aquino"),
        )

    val requests =
        mutableStateListOf(
            DockRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
            DockRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
        )

    val tickets =
        mutableStateListOf(
            DockTicket("D-038", "A. Cruz", "Standard", "09:00"),
            DockTicket("D-040", "B. Reyes", "Deep Tissue", "10:30"),
            DockTicket("D-041", "Walk-in guest", "Standard", "11:15"),
            DockTicket("D-043", "F. Garcia", "Sports", "16:00"),
        )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(DockDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100
    private var ticketSeq = 44

    val currentUser: DockUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: DockBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            DockAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, DockNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "dock desktop")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "dock desktop") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == DockSessionStatus.PENDING && !it.voided }

    fun queueAhead(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == DockSessionStatus.PENDING && !it.voided }

    fun dayCompletedTotal(): Int =
        sessions
            .filter {
                it.branchId == currentBranchId && it.status == DockSessionStatus.COMPLETED && !it.voided
            }.sumOf { it.price }

    fun nextTicketNo(): String {
        val no = "D-%03d".format(ticketSeq)
        ticketSeq += 1
        return no
    }

    fun setSessionStatus(
        id: String,
        next: DockSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == DockSessionStatus.NO_SHOW || next == DockSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != DockSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "dock", "STATUS", "Session $id -> $next")
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
            currentUser?.name ?: "dock",
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
    ): DockTicket {
        seq += 1
        val ticketNo = nextTicketNo()
        val id = "w$seq"
        val clientId =
            returningClientId ?: "c-w$seq".also {
                clients.add(0, DockClient(it, guestLabel, "M", 30))
            }
        sessions.add(
            0,
            DockSession(
                id = id,
                clientId = clientId,
                clientName = guestLabel,
                branchId = currentBranchId,
                bookedTime = at,
                type = type,
                status = DockSessionStatus.PENDING,
                price = price,
                walkIn = walkIn,
                ticketNo = ticketNo,
                practitioner = currentUser?.name ?: "Dock desk",
            ),
        )
        val ticket = DockTicket(ticketNo, guestLabel, type, at)
        tickets.add(0, ticket)
        audit(currentUser?.name ?: "dock", "CREATE", "Session $id ($type, $ticketNo)")
        notify("Session opened", "$guestLabel took $ticketNo for $type at ${currentBranch.name}.")
        return ticket
    }

    fun toggleAnonymized(clientId: String) {
        val index = clients.indexOfFirst { it.id == clientId }
        if (index < 0) return
        val current = clients[index]
        clients[index] = current.copy(anonymized = !current.anonymized)
        audit(currentUser?.name ?: "dock", "ANONYMIZE", "Client $clientId -> ${!current.anonymized}")
    }

    fun submitRemittance(kind: DockRemitKind): String? {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state == DockRemitState.SUBMITTED) return "Already submitted."
        seq += 1
        remittances[index] =
            current.copy(
                state = DockRemitState.SUBMITTED,
                snapshotId = "BD-$seq",
                snapshotTotal = current.draftTotal,
                submittedAt = "now",
            )
        audit(currentUser?.name ?: "dock", "SUBMIT", "$kind remittance BD-$seq")
        notify("Remittance snapshot sealed", "$kind snapshot BD-$seq is now immutable.")
        return null
    }

    fun undoRemittance(
        kind: DockRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != DockRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] =
            current.copy(
                state = DockRemitState.DRAFT,
                snapshotId = null,
                snapshotTotal = null,
                undoReason = reason,
            )
        audit(currentUser?.name ?: "dock", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun adjustDraft(
        kind: DockRemitKind,
        delta: Int,
    ) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == DockRemitState.SUBMITTED) return
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
            currentUser?.name ?: "dock",
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
        audit(currentUser?.name ?: "dock", decision.uppercase(), "Request $id")
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
