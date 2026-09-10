package com.companyb.companyapp.proto.departuresboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #830 — departures-board fake domain: station-styled clinic ops, local only, no network.

enum class BoardDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class BoardSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class BoardRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class BoardBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class BoardRemitKind {
    SESSION,
    PRODUCT,
}

enum class BoardRemitState {
    DRAFT,
    SUBMITTED,
}

data class BoardBranch(
    val id: String,
    val name: String,
    val kind: BoardBranchKind,
    val platformNo: String,
    val lineNote: String,
)

data class BoardUser(
    val id: String,
    val name: String,
    val role: BoardRole,
    val homeBranchId: String,
    val slot: Int,
)

data class BoardSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val departsAt: String,
    val service: String,
    val status: BoardSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val delayed: Boolean = false,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class BoardClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class BoardRemittance(
    val kind: BoardRemitKind,
    val state: BoardRemitState = BoardRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class BoardNotification(
    val id: String,
    val title: String,
    val body: String,
    val at: String,
    val read: Boolean = false,
)

data class BoardAudit(
    val id: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String?,
    val at: String,
)

data class BoardInvite(
    val id: String,
    val branchName: String,
    val person: String,
    val day: String,
    val accepted: Boolean? = null,
)

data class BoardRequest(
    val id: String,
    val branchName: String,
    val requester: String,
    val day: String,
    val state: String = "LIVE",
)

class BoardFakeRepo {
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var clockedIn by mutableStateOf(false)
    var reliefBranchId by mutableStateOf<String?>(null)
    var reliefEditGranted by mutableStateOf(false)
    var dayStatus by mutableStateOf(BoardDayStatus.OPEN)
    var operationalDate by mutableStateOf("2026-09-10")
    var onboardGranted by mutableStateOf(false)
    var sessionFilter by mutableStateOf<BoardSessionStatus?>(null)
    var clientsPrivate by mutableStateOf(true)
    var ticketCounter by mutableStateOf(44)

    val branches = mutableStateListOf(
        BoardBranch("qc", "QC Central", BoardBranchKind.CLINIC, "1", "North line — full clinic timetable"),
        BoardBranch("laguna", "Laguna Tour Stop 3", BoardBranchKind.PROVINCIAL_TOUR, "2", "Tour line — limited services"),
        BoardBranch("tondo", "Tondo Medical Mission", BoardBranchKind.MEDICAL_MISSION, "3", "Mission line — free services"),
    )

    val users = mutableStateListOf(
        BoardUser("u-onb", "R. Nuevo", BoardRole.ONBOARDING, "qc", 9),
        BoardUser("u-prac", "M. Santos", BoardRole.PRACTITIONER, "qc", 1),
        BoardUser("u-coord", "J. Reyes", BoardRole.COORDINATOR, "qc", 2),
        BoardUser("u-mgr", "A. Villanueva", BoardRole.MANAGER, "laguna", 1),
        BoardUser("u-acct", "K. Tan", BoardRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        BoardSession("S-101", "c-ana", "Ana D.", "qc", "08:00", "PT-Back", BoardSessionStatus.PENDING, 1200, false, delayed = false, practitioner = "M. Santos"),
        BoardSession("S-102", "c-ben", "Ben C.", "qc", "08:40", "PT-Knee", BoardSessionStatus.PENDING, 1500, true, delayed = true, practitioner = "M. Santos"),
        BoardSession("S-103", "c-cora", "Cora L.", "laguna", "09:15", "Tour Screen", BoardSessionStatus.COMPLETED, 800, false, practitioner = "A. Villanueva"),
        BoardSession("S-104", "c-dante", "Dante R.", "qc", "10:05", "PT-Shoulder", BoardSessionStatus.NO_SHOW, 1200, false, practitioner = "M. Santos"),
        BoardSession("S-105", "c-ella", "Ella M.", "tondo", "11:30", "Mission Check", BoardSessionStatus.CANCELLED, 0, false, practitioner = "J. Reyes"),
        BoardSession("S-106", "c-finn", "Finn P.", "qc", "13:00", "PT-Ankle", BoardSessionStatus.PENDING, 1350, false, delayed = true, practitioner = "J. Reyes"),
    )

    val clients = mutableStateListOf(
        BoardClient("c-ana", "Ana D.", "F", 41),
        BoardClient("c-ben", "Ben C.", "M", 55),
        BoardClient("c-cora", "Cora L.", "F", 38),
        BoardClient("c-dante", "Dante R.", "M", 47),
        BoardClient("c-ella", "Ella M.", "F", 29),
        BoardClient("c-finn", "Finn P.", "M", 63),
    )

    val remittances = mutableStateListOf(
        BoardRemittance(BoardRemitKind.SESSION, draftTotal = 4350),
        BoardRemittance(BoardRemitKind.PRODUCT, draftTotal = 2100),
    )

    val notifications = mutableStateListOf(
        BoardNotification("n1", "Relief invite: Laguna Tour Stop 3", "A. Villanueva invites M. Santos for 2026-09-11.", "08:12", false),
        BoardNotification("n2", "S-102 running late", "Walk-in departure flagged DELAYED on platform 1.", "08:31", false),
        BoardNotification("n3", "Snapshot sealed", "SESSION remittance snapshot RS-8812 frozen.", "07:58", true),
    )

    val audits = mutableStateListOf(
        BoardAudit("a1", "J. Reyes", "SUBMIT", "SESSION remittance RS-8812", null, "07:58"),
        BoardAudit("a2", "M. Santos", "FLAG_DELAY", "S-102", "walk-in queue long", "08:31"),
        BoardAudit("a3", "system", "GRANT", "relief invite laguna / M. Santos", null, "08:12"),
    )

    val invites = mutableStateListOf(
        BoardInvite("i1", "Laguna Tour Stop 3", "M. Santos", "2026-09-11"),
        BoardInvite("i2", "Tondo Medical Mission", "J. Reyes", "2026-09-12", accepted = true),
    )

    val requests = mutableStateListOf(
        BoardRequest("r1", "Laguna Tour Stop 3", "K. Tan", "2026-09-11"),
    )

    val currentUser: BoardUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: BoardBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()
    val effectiveRole: BoardRole
        get() {
            val user = currentUser ?: return BoardRole.ONBOARDING
            if (user.role == BoardRole.ONBOARDING && onboardGranted) return BoardRole.PRACTITIONER
            return user.role
        }

    fun displayName(client: BoardClient): String {
        if (!client.anonymized) return client.name
        return "${client.gender}-${client.age} (private)"
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == BoardSessionStatus.PENDING && !it.voided }

    fun stamp(who: String, action: String, target: String, reason: String? = null) {
        audits.add(
            0,
            BoardAudit("a${audits.size + 100}", who, action, target, reason, "now"),
        )
    }

    fun notify(title: String, body: String) {
        notifications.add(0, BoardNotification("n${notifications.size + 100}", title, body, "now", false))
    }

    fun login(userId: String) {
        currentUserId = userId
        clockedIn = true
        val user = users.firstOrNull { it.id == userId }
        stamp(user?.name ?: "?", "SIGN_IN", user?.name ?: userId)
    }

    fun logout() {
        val name = currentUser?.name ?: "?"
        stamp(name, "SIGN_OUT", name)
        currentUserId = null
        clockedIn = false
        reliefBranchId = null
        reliefEditGranted = false
    }

    fun grantOnboarding() {
        onboardGranted = true
        stamp("MANAGER", "GRANT_ROLE", "R. Nuevo -> PRACTITIONER")
        notify("Onboarding cleared", "R. Nuevo granted PRACTITIONER bundle.")
    }

    fun clockOut() {
        clockedIn = false
        stamp(currentUser?.name ?: "?", "CLOCK_OUT", currentBranch.name)
    }

    fun clockIn() {
        clockedIn = true
        stamp(currentUser?.name ?: "?", "CLOCK_IN", currentBranch.name)
    }

    fun startRelief(branchId: String) {
        reliefBranchId = branchId
        reliefEditGranted = false
        val name = branches.firstOrNull { it.id == branchId }?.name ?: branchId
        stamp(currentUser?.name ?: "?", "RELIEF_CLOCK_IN", name)
    }

    fun grantReliefEdit() {
        reliefEditGranted = true
        stamp(currentUser?.name ?: "?", "RELIEF_GRANT", currentBranch.name)
    }

    fun endRelief() {
        reliefBranchId = null
        reliefEditGranted = false
    }

    fun addWalkIn(clientLabel: String, service: String) {
        ticketCounter += 1
        val id = "S-${100 + sessions.size + 1}"
        val clientId = "c-w$ticketCounter"
        val at = "14:${(10 + ticketCounter % 49).toString().padStart(2, '0')}"
        clients.add(BoardClient(clientId, clientLabel.ifBlank { "Walk-in $ticketCounter" }, "F", 30 + ticketCounter % 30))
        sessions.add(
            0,
            BoardSession(
                id = id,
                clientId = clientId,
                clientName = clientLabel.ifBlank { "Walk-in $ticketCounter" },
                branchId = currentBranchId,
                departsAt = at,
                service = service,
                status = BoardSessionStatus.PENDING,
                price = 1200,
                walkIn = true,
            ),
        )
        stamp(currentUser?.name ?: "?", "WALK_IN", "$id / A-${ticketCounter.toString().padStart(3, '0')}")
        notify("Walk-in boarded", "$id departs $at from platform ${currentBranch.platformNo}.")
    }

    fun setStatus(sessionId: String, next: BoardSessionStatus): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "missing"
        val current = sessions[index]
        if (current.walkIn && (next == BoardSessionStatus.NO_SHOW || next == BoardSessionStatus.CANCELLED)) {
            return "Walk-in departures cannot take NO_SHOW or CANCELLED — rebook or void instead."
        }
        sessions[index] = current.copy(status = next)
        stamp(currentUser?.name ?: "?", "STATUS", "$sessionId -> ${next.name}")
        return null
    }

    fun toggleDelay(sessionId: String) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(delayed = !current.delayed)
        stamp(currentUser?.name ?: "?", if (current.delayed) "CLEAR_DELAY" else "FLAG_DELAY", sessionId)
    }

    fun voidSession(sessionId: String, reason: String): String? {
        if (reason.isBlank()) return "A reason is required to void."
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "missing"
        val current = sessions[index]
        sessions[index] = current.copy(voided = true, voidReason = reason)
        stamp(currentUser?.name ?: "?", "VOID", sessionId, reason)
        return null
    }

    fun unvoidSession(sessionId: String) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(voided = false, voidReason = null)
        stamp(currentUser?.name ?: "?", "UNVOID", sessionId)
    }

    fun anonymize(clientId: String) {
        val index = clients.indexOfFirst { it.id == clientId }
        if (index < 0) return
        val current = clients[index]
        clients[index] = current.copy(anonymized = true, name = "Private")
        stamp(currentUser?.name ?: "?", "ANONYMIZE", clientId)
    }

    fun bumpDraft(kind: BoardRemitKind, delta: Int) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == BoardRemitState.SUBMITTED) return
        remittances[index] = current.copy(draftTotal = (current.draftTotal + delta).coerceAtLeast(0))
    }

    fun submit(kind: BoardRemitKind) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        val snap = "RS-${8800 + remittances.indexOf(current) * 7 + draftSalt(kind)}"
        remittances[index] = current.copy(
            state = BoardRemitState.SUBMITTED,
            snapshotId = snap,
            snapshotTotal = current.draftTotal,
            submittedAt = "now",
        )
        dayStatus = BoardDayStatus.REMITTED
        stamp(currentUser?.name ?: "?", "SUBMIT", "${kind.name} $snap")
        notify("Snapshot sealed", "${kind.name} remittance snapshot $snap frozen.")
    }

    private fun draftSalt(kind: BoardRemitKind): Int = if (kind == BoardRemitKind.SESSION) 12 else 31

    fun undo(kind: BoardRemitKind, reason: String): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "missing"
        val current = remittances[index]
        remittances[index] = current.copy(state = BoardRemitState.DRAFT, snapshotId = null, snapshotTotal = null, undoReason = reason)
        dayStatus = BoardDayStatus.PAST
        stamp(currentUser?.name ?: "?", "UNDO", "${kind.name} remittance", reason)
        return null
    }

    fun markRead(id: String) {
        val index = notifications.indexOfFirst { it.id == id }
        if (index < 0) return
        notifications[index] = notifications[index].copy(read = true)
    }

    fun markAllRead() {
        for (i in notifications.indices) notifications[i] = notifications[i].copy(read = true)
    }

    fun decideInvite(id: String, accept: Boolean) {
        val index = invites.indexOfFirst { it.id == id }
        if (index < 0) return
        invites[index] = invites[index].copy(accepted = accept)
        stamp(currentUser?.name ?: "?", if (accept) "INVITE_ACCEPT" else "INVITE_DECLINE", id)
    }

    fun decideRequest(id: String, approve: Boolean) {
        val index = requests.indexOfFirst { it.id == id }
        if (index < 0) return
        requests[index] = requests[index].copy(state = if (approve) "GRANTED" else "DENIED")
        if (approve) reliefEditGranted = true
        stamp(currentUser?.name ?: "?", if (approve) "REQUEST_GRANT" else "REQUEST_DENY", id)
    }
}
