package com.companyb.companyapp.proto.newsroomdesk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #833 — newsroom-desk fake domain: assignment-desk budget meeting over clinic ops,
// local only, no network. Stories are sessions, editors assign.

enum class DeskDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class DeskSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class DeskRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class DeskBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class DeskRemitKind {
    SESSION,
    PRODUCT,
}

enum class DeskRemitState {
    DRAFT,
    SUBMITTED,
}

data class DeskBranch(
    val id: String,
    val name: String,
    val kind: DeskBranchKind,
    val deskNo: String,
    val bureauNote: String,
)

data class DeskUser(
    val id: String,
    val name: String,
    val role: DeskRole,
    val homeBranchId: String,
    val slot: Int,
)

data class DeskSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val slugAt: String,
    val service: String,
    val status: DeskSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val urgent: Boolean = false,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class DeskClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class DeskRemittance(
    val kind: DeskRemitKind,
    val state: DeskRemitState = DeskRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
    val submittedAt: String? = null,
)

data class DeskNotification(
    val id: String,
    val title: String,
    val body: String,
    val at: String,
    val read: Boolean = false,
)

data class DeskAudit(
    val id: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String?,
    val at: String,
)

data class DeskInvite(
    val id: String,
    val branchName: String,
    val person: String,
    val day: String,
    val accepted: Boolean? = null,
)

data class DeskRequest(
    val id: String,
    val branchName: String,
    val requester: String,
    val day: String,
    val state: String = "LIVE",
)

class DeskFakeRepo {
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("qc")
    var clockedIn by mutableStateOf(false)
    var reliefBranchId by mutableStateOf<String?>(null)
    var reliefEditGranted by mutableStateOf(false)
    var dayStatus by mutableStateOf(DeskDayStatus.OPEN)
    var operationalDate by mutableStateOf("2026-09-10")
    var onboardGranted by mutableStateOf(false)
    var sessionFilter by mutableStateOf<DeskSessionStatus?>(null)
    var clientsPrivate by mutableStateOf(true)
    var tipCounter by mutableStateOf(44)

    val branches = mutableStateListOf(
        DeskBranch("qc", "QC Central", DeskBranchKind.CLINIC, "1", "City desk — full budget, all beats"),
        DeskBranch("laguna", "Laguna Tour Stop 3", DeskBranchKind.PROVINCIAL_TOUR, "2", "Tour desk — stringer file, limited beats"),
        DeskBranch("tondo", "Tondo Medical Mission", DeskBranchKind.MEDICAL_MISSION, "3", "Mission desk — community file, free list"),
    )

    val users = mutableStateListOf(
        DeskUser("u-onb", "R. Nuevo", DeskRole.ONBOARDING, "qc", 9),
        DeskUser("u-prac", "M. Santos", DeskRole.PRACTITIONER, "qc", 1),
        DeskUser("u-coord", "J. Reyes", DeskRole.COORDINATOR, "qc", 2),
        DeskUser("u-mgr", "A. Villanueva", DeskRole.MANAGER, "laguna", 1),
        DeskUser("u-acct", "K. Tan", DeskRole.ACCOUNTANT, "qc", 5),
    )

    val sessions = mutableStateListOf(
        DeskSession("S-101", "c-ana", "Ana D.", "qc", "08:00", "PT-Back", DeskSessionStatus.PENDING, 1200, false, urgent = false, practitioner = "M. Santos"),
        DeskSession("S-102", "c-ben", "Ben C.", "qc", "08:40", "PT-Knee", DeskSessionStatus.PENDING, 1500, true, urgent = true, practitioner = "M. Santos"),
        DeskSession("S-103", "c-cora", "Cora L.", "laguna", "09:15", "Tour Screen", DeskSessionStatus.COMPLETED, 800, false, practitioner = "A. Villanueva"),
        DeskSession("S-104", "c-dante", "Dante R.", "qc", "10:05", "PT-Shoulder", DeskSessionStatus.NO_SHOW, 1200, false, practitioner = "M. Santos"),
        DeskSession("S-105", "c-ella", "Ella M.", "tondo", "11:30", "Mission Check", DeskSessionStatus.CANCELLED, 0, false, practitioner = "J. Reyes"),
        DeskSession("S-106", "c-finn", "Finn P.", "qc", "13:00", "PT-Ankle", DeskSessionStatus.PENDING, 1350, false, urgent = true, practitioner = "J. Reyes"),
    )

    val clients = mutableStateListOf(
        DeskClient("c-ana", "Ana D.", "F", 41),
        DeskClient("c-ben", "Ben C.", "M", 55),
        DeskClient("c-cora", "Cora L.", "F", 38),
        DeskClient("c-dante", "Dante R.", "M", 47),
        DeskClient("c-ella", "Ella M.", "F", 29),
        DeskClient("c-finn", "Finn P.", "M", 63),
    )

    val remittances = mutableStateListOf(
        DeskRemittance(DeskRemitKind.SESSION, draftTotal = 4350),
        DeskRemittance(DeskRemitKind.PRODUCT, draftTotal = 2100),
    )

    val notifications = mutableStateListOf(
        DeskNotification("n1", "Stringer invite: Laguna Tour Stop 3", "A. Villanueva invites M. Santos for 2026-09-11.", "08:12", false),
        DeskNotification("n2", "S-102 needs a rewrite", "Walk-in story flagged URGENT on desk 1.", "08:31", false),
        DeskNotification("n3", "Edition locked", "SESSION remittance snapshot RS-8812 frozen.", "07:58", true),
    )

    val audits = mutableStateListOf(
        DeskAudit("a1", "J. Reyes", "SUBMIT", "SESSION remittance RS-8812", null, "07:58"),
        DeskAudit("a2", "M. Santos", "FLAG_URGENT", "S-102", "walk-in queue long", "08:31"),
        DeskAudit("a3", "system", "GRANT", "relief invite laguna / M. Santos", null, "08:12"),
    )

    val invites = mutableStateListOf(
        DeskInvite("i1", "Laguna Tour Stop 3", "M. Santos", "2026-09-11"),
        DeskInvite("i2", "Tondo Medical Mission", "J. Reyes", "2026-09-12", accepted = true),
    )

    val requests = mutableStateListOf(
        DeskRequest("r1", "Laguna Tour Stop 3", "K. Tan", "2026-09-11"),
    )

    val currentUser: DeskUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: DeskBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()
    val effectiveRole: DeskRole
        get() {
            val user = currentUser ?: return DeskRole.ONBOARDING
            if (user.role == DeskRole.ONBOARDING && onboardGranted) return DeskRole.PRACTITIONER
            return user.role
        }

    fun displayName(client: DeskClient): String {
        if (!client.anonymized) return client.name
        return "${client.gender}-${client.age} (private)"
    }

    fun pendingCountFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == DeskSessionStatus.PENDING && !it.voided }

    fun stamp(who: String, action: String, target: String, reason: String? = null) {
        audits.add(
            0,
            DeskAudit("a${audits.size + 100}", who, action, target, reason, "now"),
        )
    }

    fun notify(title: String, body: String) {
        notifications.add(0, DeskNotification("n${notifications.size + 100}", title, body, "now", false))
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
        tipCounter += 1
        val id = "S-${100 + sessions.size + 1}"
        val clientId = "c-w$tipCounter"
        val at = "14:${(10 + tipCounter % 49).toString().padStart(2, '0')}"
        clients.add(DeskClient(clientId, clientLabel.ifBlank { "Walk-in $tipCounter" }, "F", 30 + tipCounter % 30))
        sessions.add(
            0,
            DeskSession(
                id = id,
                clientId = clientId,
                clientName = clientLabel.ifBlank { "Walk-in $tipCounter" },
                branchId = currentBranchId,
                slugAt = at,
                service = service,
                status = DeskSessionStatus.PENDING,
                price = 1200,
                walkIn = true,
            ),
        )
        stamp(currentUser?.name ?: "?", "WALK_IN", "$id / TIP-${tipCounter.toString().padStart(3, '0')}")
        notify("Walk-in slugged", "$id slugged $at at desk ${currentBranch.deskNo}.")
    }

    fun setStatus(sessionId: String, next: DeskSessionStatus): String? {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return "missing"
        val current = sessions[index]
        if (current.walkIn && (next == DeskSessionStatus.NO_SHOW || next == DeskSessionStatus.CANCELLED)) {
            return "Walk-in stories cannot take NO_SHOW or CANCELLED — re-slug or void instead."
        }
        sessions[index] = current.copy(status = next)
        stamp(currentUser?.name ?: "?", "STATUS", "$sessionId -> ${next.name}")
        return null
    }

    fun toggleUrgent(sessionId: String) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(urgent = !current.urgent)
        stamp(currentUser?.name ?: "?", if (current.urgent) "CLEAR_URGENT" else "FLAG_URGENT", sessionId)
    }

    fun voidSession(sessionId: String, reason: String): String? {
        if (reason.isBlank()) return "A reason is required to spike (void)."
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

    fun bumpDraft(kind: DeskRemitKind, delta: Int) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        if (current.state == DeskRemitState.SUBMITTED) return
        remittances[index] = current.copy(draftTotal = (current.draftTotal + delta).coerceAtLeast(0))
    }

    fun submit(kind: DeskRemitKind) {
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return
        val current = remittances[index]
        val snap = "RS-${8800 + remittances.indexOf(current) * 7 + draftSalt(kind)}"
        remittances[index] = current.copy(
            state = DeskRemitState.SUBMITTED,
            snapshotId = snap,
            snapshotTotal = current.draftTotal,
            submittedAt = "now",
        )
        dayStatus = DeskDayStatus.REMITTED
        stamp(currentUser?.name ?: "?", "SUBMIT", "${kind.name} $snap")
        notify("Edition locked", "${kind.name} remittance snapshot $snap frozen.")
    }

    private fun draftSalt(kind: DeskRemitKind): Int = if (kind == DeskRemitKind.SESSION) 12 else 31

    fun undo(kind: DeskRemitKind, reason: String): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val index = remittances.indexOfFirst { it.kind == kind }
        if (index < 0) return "missing"
        val current = remittances[index]
        remittances[index] = current.copy(state = DeskRemitState.DRAFT, snapshotId = null, snapshotTotal = null, undoReason = reason)
        dayStatus = DeskDayStatus.PAST
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
