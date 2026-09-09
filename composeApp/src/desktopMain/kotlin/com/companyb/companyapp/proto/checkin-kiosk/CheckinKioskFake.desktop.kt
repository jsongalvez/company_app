package com.companyb.companyapp.proto.checkinkiosk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #782 — checkin-kiosk fake domain: self check-in lobby, local only, no network, no shared contracts.

enum class CheckinDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class CheckinSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class CheckinRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class CheckinBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class CheckinRemitKind {
    SESSION,
    PRODUCT,
}

enum class CheckinRemitState {
    DRAFT,
    SUBMITTED,
}

data class CheckinBranch(
    val id: String,
    val name: String,
    val kind: CheckinBranchKind,
    val lobbyLine: String,
)

data class CheckinUser(
    val id: String,
    val name: String,
    val role: CheckinRole,
    val homeBranchId: String,
    val slot: Int,
)

data class CheckinSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: CheckinSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val ticketNo: String? = null,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class CheckinClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class CheckinTicket(
    val no: String,
    val guest: String,
    val care: String,
    val at: String,
)

data class CheckinRemittance(
    val kind: CheckinRemitKind,
    val state: CheckinRemitState = CheckinRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class CheckinNotification(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class CheckinAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class CheckinInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    val accepted: Boolean? = null,
)

data class CheckinRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    val mine: Boolean,
    val decided: String? = null,
)

class CheckinFakeRepo {
    val branches = mutableStateListOf(
        CheckinBranch("qc", "QC Central", CheckinBranchKind.CLINIC, "Main lobby · 2 chairs free"),
        CheckinBranch("tour", "Laguna Tour Stop 3", CheckinBranchKind.PROVINCIAL_TOUR, "Tent B · short line"),
        CheckinBranch("mission", "Tondo Medical Mission", CheckinBranchKind.MEDICAL_MISSION, "Hall · busy morning"),
    )

    val users = mutableStateListOf(
        CheckinUser("u-new", "J. Ramos (new hire)", CheckinRole.ONBOARDING, "qc", 9),
        CheckinUser("u-me", "M. Santos", CheckinRole.PRACTITIONER, "qc", 2),
        CheckinUser("u-coord", "L. Villanueva", CheckinRole.COORDINATOR, "qc", 1),
        CheckinUser("u-mgr", "R. Aquino", CheckinRole.MANAGER, "tour", 1),
        CheckinUser("u-acct", "D. Lim", CheckinRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        CheckinSession("s1", "c1", "A. Cruz", "qc", "09:00", "Standard", CheckinSessionStatus.COMPLETED, 1200, false, ticketNo = "A-038"),
        CheckinSession("s2", "c2", "B. Reyes", "qc", "10:30", "Deep Tissue", CheckinSessionStatus.PENDING, 1500, false, ticketNo = "A-040"),
        CheckinSession("s3", "c3", "Walk-in guest", "qc", "11:15", "Standard", CheckinSessionStatus.PENDING, 1200, true, ticketNo = "A-041"),
        CheckinSession("s4", "c4", "D. Ocampo", "qc", "13:00", "Hot Stone", CheckinSessionStatus.NO_SHOW, 1800, false),
        CheckinSession("s5", "c5", "E. Navarro", "qc", "14:30", "Standard", CheckinSessionStatus.CANCELLED, 1200, false),
        CheckinSession("s6", "c6", "F. Garcia", "qc", "16:00", "Sports", CheckinSessionStatus.PENDING, 1600, false, ticketNo = "A-043"),
    )

    val clients = mutableStateListOf(
        CheckinClient("c1", "A. Cruz", "F", 34),
        CheckinClient("c2", "B. Reyes", "M", 41),
        CheckinClient("c3", "Walk-in guest", "M", 29),
        CheckinClient("c4", "D. Ocampo", "F", 52),
        CheckinClient("c5", "E. Navarro", "F", 38),
        CheckinClient("c6", "F. Garcia", "M", 45),
    )

    val remittances = mutableStateListOf(
        CheckinRemittance(CheckinRemitKind.SESSION, draftTotal = 4350),
        CheckinRemittance(CheckinRemitKind.PRODUCT, draftTotal = 2800),
    )

    val notifications = mutableStateListOf(
        CheckinNotification("n1", "Relief request approved", "QC Central granted your Sep 11 access (relief).", false),
        CheckinNotification("n2", "Remittance snapshot sealed", "SESSION snapshot CK-1001 is now immutable.", false),
        CheckinNotification("n3", "Branch day closed", "Sep 09 moved to PAST at 04:00 Asia/Manila.", true),
    )

    val audits = mutableStateListOf(
        CheckinAudit("a1", "08:02", "L. Villanueva", "SUBMIT", "SESSION remittance CK-1001", null),
        CheckinAudit("a2", "09:41", "M. Santos", "UPDATE", "Session s2 price 1200 -> 1500", "guest asked for upgrade"),
        CheckinAudit("a3", "10:05", "R. Aquino", "GRANT", "Relief access QC Central / Sep 11", null),
    )

    val invites = mutableStateListOf(
        CheckinInvite("i1", "tour", "Sep 12", "R. Aquino"),
    )

    val requests = mutableStateListOf(
        CheckinRequest("r1", "mission", "Sep 13", "M. Santos", mine = true),
        CheckinRequest("r2", "qc", "Sep 11", "J. Dela Cruz", mine = false),
    )

    val tickets = mutableStateListOf(
        CheckinTicket("A-038", "A. Cruz", "Standard", "09:00"),
        CheckinTicket("A-040", "B. Reyes", "Deep Tissue", "10:30"),
        CheckinTicket("A-041", "Walk-in guest", "Standard", "11:15"),
        CheckinTicket("A-043", "F. Garcia", "Sports", "16:00"),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var dayStatus by mutableStateOf(CheckinDayStatus.OPEN)
    var clockedIn by mutableStateOf(false)
    var operationalDate by mutableStateOf("Sep 10, 2026")

    private var seq = 100
    private var ticketSeq = 44

    val currentUser: CheckinUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: CheckinBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun audit(
        who: String,
        action: String,
        target: String,
        reason: String? = null,
    ) {
        seq += 1
        audits.add(
            0,
            CheckinAudit("a$seq", "now", who, action, target, reason),
        )
    }

    fun notify(
        title: String,
        body: String,
    ) {
        seq += 1
        notifications.add(0, CheckinNotification("n$seq", title, body, false))
    }

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit(user.name, "LOGIN", "check-in lobby")
    }

    fun logout() {
        currentUser?.let { audit(it.name, "LOGOUT", "check-in lobby") }
        currentUserId = null
        clockedIn = false
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == CheckinSessionStatus.PENDING && !it.voided }

    fun queueAhead(): Int =
        sessions.count { it.branchId == currentBranchId && it.status == CheckinSessionStatus.PENDING && !it.voided }

    fun dayCompletedTotal(): Int =
        sessions.filter {
            it.branchId == currentBranchId && it.status == CheckinSessionStatus.COMPLETED && !it.voided
        }.sumOf { it.price }

    fun nextTicketNo(): String {
        val no = "A-%03d".format(ticketSeq)
        ticketSeq += 1
        return no
    }

    fun setSessionStatus(
        id: String,
        next: CheckinSessionStatus,
    ): String? {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return "Session not found."
        val current = sessions[index]
        if (current.walkIn && (next == CheckinSessionStatus.NO_SHOW || next == CheckinSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (current.status != CheckinSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[index] = current.copy(status = next)
        audit(currentUser?.name ?: "lobby", "STATUS", "Session $id -> $next")
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
            currentUser?.name ?: "lobby",
            if (voided) "VOID" else "UNVOID",
            "Session $id",
            reason,
        )
        return null
    }

    fun checkIn(
        guestLabel: String,
        type: String,
        price: Int,
        returningClientId: String?,
    ): CheckinTicket {
        seq += 1
        val ticketNo = nextTicketNo()
        val id = "w$seq"
        val clientId = returningClientId ?: "c-w$seq".also {
            clients.add(0, CheckinClient(it, guestLabel, "M", 30))
        }
        sessions.add(
            0,
            CheckinSession(
                id = id,
                clientId = clientId,
                clientName = guestLabel,
                branchId = currentBranchId,
                bookedTime = "now",
                type = type,
                status = CheckinSessionStatus.PENDING,
                price = price,
                walkIn = returningClientId == null,
                ticketNo = ticketNo,
                practitioner = currentUser?.name ?: "Front desk",
            ),
        )
        val ticket = CheckinTicket(ticketNo, guestLabel, type, "now")
        tickets.add(0, ticket)
        audit(currentUser?.name ?: "lobby", "CREATE", "Arrival $id ($type, $ticketNo)")
        notify("Guest checked in", "$guestLabel took $ticketNo for $type at ${currentBranch.name}.")
        return ticket
    }

    fun toggleAnonymized(clientId: String) {
        val index = clients.indexOfFirst { it.id == clientId }
        if (index < 0) return
        val current = clients[index]
        clients[index] = current.copy(anonymized = !current.anonymized)
        audit(currentUser?.name ?: "lobby", "ANONYMIZE", "Client $clientId -> ${!current.anonymized}")
    }

    fun submitRemittance(kind: CheckinRemitKind): String? {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state == CheckinRemitState.SUBMITTED) return "Already submitted."
        seq += 1
        remittances[index] = current.copy(
            state = CheckinRemitState.SUBMITTED,
            snapshotId = "CK-$seq",
            snapshotTotal = current.draftTotal,
            submittedAt = "now",
        )
        audit(currentUser?.name ?: "lobby", "SUBMIT", "$kind remittance CK-$seq")
        notify("Remittance snapshot sealed", "$kind snapshot CK-$seq is now immutable.")
        return null
    }

    fun undoRemittance(
        kind: CheckinRemitKind,
        reason: String,
    ): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "Remittance not found."
        val current = remittances[index]
        if (current.state != CheckinRemitState.SUBMITTED) return "Only submitted remittances can be undone."
        remittances[index] = current.copy(
            state = CheckinRemitState.DRAFT,
            snapshotId = null,
            snapshotTotal = null,
            undoReason = reason,
        )
        audit(currentUser?.name ?: "lobby", "UNDO", "$kind remittance ${current.snapshotId}", reason)
        return null
    }

    fun adjustDraft(
        kind: CheckinRemitKind,
        delta: Int,
    ) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == CheckinRemitState.SUBMITTED) return
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
            currentUser?.name ?: "lobby",
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
        audit(currentUser?.name ?: "lobby", decision.uppercase(), "Request $id")
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
