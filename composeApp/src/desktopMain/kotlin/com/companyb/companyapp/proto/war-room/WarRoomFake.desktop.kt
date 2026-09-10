package com.companyb.companyapp.proto.warroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #836 — war-room fake domain: exception-only incident state, local only, no network.

enum class WarDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class WarSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class WarRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class WarBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class WarRemitKind {
    SESSION,
    PRODUCT,
}

enum class WarRemitState {
    DRAFT,
    SUBMITTED,
}

data class WarBranch(
    val id: String,
    val name: String,
    val kind: WarBranchKind,
    val sector: String,
    val watchNote: String,
)

data class WarUser(
    val id: String,
    val name: String,
    val role: WarRole,
    val homeBranchId: String,
    val slot: Int,
)

data class WarSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val startsAt: String,
    val service: String,
    val status: WarSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val variance: Boolean = false,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class WarClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class WarRemittance(
    val kind: WarRemitKind,
    val state: WarRemitState = WarRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class WarNotification(
    val id: String,
    val title: String,
    val body: String,
    val at: String,
    val read: Boolean = false,
)

data class WarAudit(
    val id: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String?,
    val at: String,
)

data class WarInvite(
    val id: String,
    val branchName: String,
    val person: String,
    val day: String,
    val accepted: Boolean? = null,
)

data class WarRequest(
    val id: String,
    val branchName: String,
    val requester: String,
    val day: String,
    val state: String = "LIVE",
)

class WarFakeRepo {
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var clockedIn by mutableStateOf(false)
    var reliefBranchId by mutableStateOf<String?>(null)
    var reliefEditGranted by mutableStateOf(false)
    var dayStatus by mutableStateOf(WarDayStatus.OPEN)
    var operationalDate by mutableStateOf("2026-09-10")
    var onboardGranted by mutableStateOf(false)
    var sessionFilter by mutableStateOf<WarSessionStatus?>(null)
    var clientsPrivate by mutableStateOf(true)
    var ticketCounter by mutableStateOf(44)

    val branches = mutableStateListOf(
        WarBranch("qc", "QC Central", WarBranchKind.CLINIC, "Sector 1", "Full clinic roster — most exceptions surface here"),
        WarBranch("laguna", "Laguna Tour Stop 3", WarBranchKind.PROVINCIAL_TOUR, "Sector 2", "Tour line — one relief gap open"),
        WarBranch("tondo", "Tondo Medical Mission", WarBranchKind.MEDICAL_MISSION, "Sector 3", "Mission line — free services, void-heavy"),
    )

    val users = mutableStateListOf(
        WarUser("u-onb", "R. Nuevo", WarRole.ONBOARDING, "qc", 9),
        WarUser("u-prac", "M. Santos", WarRole.PRACTITIONER, "qc", 1),
        WarUser("u-coord", "J. Reyes", WarRole.COORDINATOR, "qc", 2),
        WarUser("u-mgr", "A. Villanueva", WarRole.MANAGER, "laguna", 1),
        WarUser("u-acct", "K. Tan", WarRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        WarSession("S-101", "c-ana", "Ana D.", "qc", "08:00", "PT-Back", WarSessionStatus.PENDING, 1200, false, variance = false, practitioner = "M. Santos"),
        WarSession("S-102", "c-ben", "Ben C.", "qc", "08:40", "PT-Knee", WarSessionStatus.PENDING, 1500, true, variance = true, practitioner = "M. Santos"),
        WarSession("S-103", "c-cora", "Cora L.", "laguna", "09:15", "Tour Screen", WarSessionStatus.COMPLETED, 800, false, practitioner = "A. Villanueva"),
        WarSession("S-104", "c-dante", "Dante R.", "qc", "10:05", "PT-Shoulder", WarSessionStatus.NO_SHOW, 1200, false, variance = true, practitioner = "M. Santos"),
        WarSession("S-105", "c-ella", "Ella M.", "tondo", "11:30", "Mission Check", WarSessionStatus.CANCELLED, 0, false, voided = true, voidReason = "Duplicate booking", practitioner = "J. Reyes"),
        WarSession("S-106", "c-finn", "Finn P.", "qc", "13:00", "PT-Ankle", WarSessionStatus.PENDING, 1350, false, variance = true, practitioner = "J. Reyes"),
    )

    val clients = mutableStateListOf(
        WarClient("c-ana", "Ana D.", "F", 41),
        WarClient("c-ben", "Ben C.", "M", 55),
        WarClient("c-cora", "Cora L.", "F", 38),
        WarClient("c-dante", "Dante R.", "M", 47),
        WarClient("c-ella", "Ella M.", "F", 29),
        WarClient("c-finn", "Finn P.", "M", 63),
    )

    val remittances = mutableStateListOf(
        WarRemittance(WarRemitKind.SESSION, draftTotal = 4350),
        WarRemittance(WarRemitKind.PRODUCT, draftTotal = 2100),
    )

    val notifications = mutableStateListOf(
        WarNotification("n1", "Relief gap: Laguna Tour Stop 3", "A. Villanueva invites M. Santos for 2026-09-11.", "08:12", false),
        WarNotification("n2", "Variance on S-102", "Walk-in price drift flagged for review.", "08:31", false),
        WarNotification("n3", "Snapshot sealed", "SESSION remittance snapshot RS-8812 frozen.", "07:58", true),
    )

    val audits = mutableStateListOf(
        WarAudit("a1", "J. Reyes", "SUBMIT", "SESSION remittance RS-8812", null, "07:58"),
        WarAudit("a2", "M. Santos", "FLAG_VARIANCE", "S-102", "walk-in price drift", "08:31"),
        WarAudit("a3", "system", "GRANT", "relief invite laguna / M. Santos", null, "08:12"),
    )

    val invites = mutableStateListOf(
        WarInvite("i1", "Laguna Tour Stop 3", "M. Santos", "2026-09-11"),
        WarInvite("i2", "Tondo Medical Mission", "J. Reyes", "2026-09-12", accepted = true),
    )

    val requests = mutableStateListOf(
        WarRequest("r1", "Laguna Tour Stop 3", "K. Tan", "2026-09-11"),
    )

    val currentUser: WarUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: WarBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()
    val effectiveRole: WarRole
        get() {
            val user = currentUser ?: return WarRole.ONBOARDING
            if (user.role == WarRole.ONBOARDING && onboardGranted) return WarRole.PRACTITIONER
            return user.role
        }

    val voidQueue: List<WarSession> get() = sessions.filter { it.voided }
    val varianceQueue: List<WarSession> get() = sessions.filter { it.variance && !it.voided }
    val reliefGapCount: Int get() = invites.count { it.accepted == null } + requests.count { it.state == "LIVE" }
    val undoWindows: List<WarRemittance> get() = remittances.filter { it.state == WarRemitState.SUBMITTED }
    val exceptionCount: Int get() = voidQueue.size + varianceQueue.size + reliefGapCount + undoWindows.size

    fun displayName(client: WarClient): String {
        if (!client.anonymized) return client.name
        return "${client.gender}-${client.age} (private)"
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == WarSessionStatus.PENDING && !it.voided }

    fun stamp(who: String, action: String, target: String, reason: String? = null) {
        audits.add(
            0,
            WarAudit("a${audits.size + 100}", who, action, target, reason, "now"),
        )
    }

    fun notify(title: String, body: String) {
        notifications.add(0, WarNotification("n${notifications.size + 100}", title, body, "now", false))
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
        clients.add(WarClient(clientId, clientLabel.ifBlank { "Walk-in $ticketCounter" }, "F", 30 + ticketCounter % 30))
        sessions.add(
            0,
            WarSession(
                id = id,
                clientId = clientId,
                clientName = clientLabel.ifBlank { "Walk-in $ticketCounter" },
                branchId = currentBranchId,
                startsAt = at,
                service = service,
                status = WarSessionStatus.PENDING,
                price = 1200,
                walkIn = true,
                variance = true,
            ),
        )
        stamp(currentUser?.name ?: "?", "WALK_IN", "$id / W-${ticketCounter.toString().padStart(3, '0')}")
        notify("Walk-in triaged", "$id starts $at in ${currentBranch.name}; variance watch on.")
    }

    fun setStatus(sessionId: String, next: WarSessionStatus): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "missing"
        val current = sessions[index]
        if (current.walkIn && (next == WarSessionStatus.NO_SHOW || next == WarSessionStatus.CANCELLED)) {
            return "Walk-in rows cannot take NO_SHOW or CANCELLED — rebook or void instead."
        }
        sessions[index] = current.copy(status = next)
        stamp(currentUser?.name ?: "?", "STATUS", "$sessionId -> ${next.name}")
        return null
    }

    fun toggleVariance(sessionId: String) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(variance = !current.variance)
        stamp(currentUser?.name ?: "?", if (current.variance) "CLEAR_VARIANCE" else "FLAG_VARIANCE", sessionId)
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

    fun bumpDraft(kind: WarRemitKind, delta: Int) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == WarRemitState.SUBMITTED) return
        remittances[index] = current.copy(draftTotal = (current.draftTotal + delta).coerceAtLeast(0))
    }

    fun submit(kind: WarRemitKind) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        val snap = "RS-${8800 + index * 7 + draftSalt(kind)}"
        remittances[index] = current.copy(
            state = WarRemitState.SUBMITTED,
            snapshotId = snap,
            snapshotTotal = current.draftTotal,
            submittedAt = "now",
        )
        dayStatus = WarDayStatus.REMITTED
        stamp(currentUser?.name ?: "?", "SUBMIT", "${kind.name} $snap")
        notify("Snapshot sealed", "${kind.name} remittance snapshot $snap frozen.")
    }

    private fun draftSalt(kind: WarRemitKind): Int = if (kind == WarRemitKind.SESSION) 12 else 31

    fun undo(kind: WarRemitKind, reason: String): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "missing"
        val current = remittances[index]
        remittances[index] = current.copy(
            state = WarRemitState.DRAFT,
            snapshotId = null,
            snapshotTotal = null,
            undoReason = reason,
        )
        dayStatus = WarDayStatus.PAST
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
