package com.companyb.companyapp.proto.whiteboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #844 — whiteboard fake domain: full day flows on local state, no network, no shared contracts.

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
    val standupLine: String,
)

data class BoardUser(
    val id: String,
    val name: String,
    var role: BoardRole,
    val homeBranchId: String,
)

data class BoardSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val slot: String,
    val type: String,
    var status: BoardSessionStatus,
    var price: Int,
    val walkIn: Boolean,
    var voided: Boolean = false,
    var voidReason: String? = null,
    val practitioner: String,
)

data class BoardClient(
    val id: String,
    var name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
)

data class BoardRemittance(
    val kind: BoardRemitKind,
    var state: BoardRemitState = BoardRemitState.DRAFT,
    var draftTotal: Int = 0,
    var snapshotId: String? = null,
    var snapshotTotal: Int? = null,
    var submittedAt: String? = null,
    var undoReason: String? = null,
    var windowElapsed: Boolean = false,
)

data class BoardNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean = false,
)

data class BoardAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class BoardInvite(
    val id: String,
    val branchId: String,
    val day: String,
    val fromUser: String,
    var accepted: Boolean? = null,
)

data class BoardRequest(
    val id: String,
    val branchId: String,
    val day: String,
    val requester: String,
    var decided: String? = null,
)

class WhiteboardFakeRepo {
    val branches = listOf(
        BoardBranch("b-makati", "Makati Flagship", BoardBranchKind.CLINIC, "Standup at 08:45 by the board"),
        BoardBranch("b-tour", "Laguna Pop-up Tour", BoardBranchKind.PROVINCIAL_TOUR, "Two tables, one extension cord"),
        BoardBranch("b-mission", "Payatas Mission Day", BoardBranchKind.MEDICAL_MISSION, "Free slots pinned in green"),
    )

    val users = mutableStateListOf(
        BoardUser("u-onboard", "Sam Reyes", BoardRole.ONBOARDING, "b-makati"),
        BoardUser("u-prax", "M. Santos", BoardRole.PRACTITIONER, "b-makati"),
        BoardUser("u-coord", "J. Cruz", BoardRole.COORDINATOR, "b-makati"),
        BoardUser("u-mgr", "A. Villanueva", BoardRole.MANAGER, "b-tour"),
        BoardUser("u-acct", "R. Ocampo", BoardRole.ACCOUNTANT, "b-makati"),
    )

    val sessions = mutableStateListOf(
        BoardSession("s-101", "c-1", "Liza Navarro", "b-makati", "09:00", "Follow-up", BoardSessionStatus.PENDING, 1200, false, practitioner = "M. Santos"),
        BoardSession("s-102", "c-2", "Kenji Uy", "b-makati", "10:00", "Initial", BoardSessionStatus.PENDING, 1500, true, practitioner = "M. Santos"),
        BoardSession("s-103", "c-3", "Ama Serna", "b-makati", "11:00", "Follow-up", BoardSessionStatus.COMPLETED, 1200, false, practitioner = "M. Santos"),
        BoardSession("s-104", "c-4", "Paolo Lim", "b-makati", "13:00", "Sports", BoardSessionStatus.NO_SHOW, 1500, false, practitioner = "J. Cruz"),
        BoardSession("s-105", "c-5", "Rhea Daza", "b-tour", "09:30", "Screening", BoardSessionStatus.PENDING, 800, true, practitioner = "A. Villanueva"),
        BoardSession("s-106", "c-6", "Isko Marasigan", "b-mission", "10:30", "Mission slot", BoardSessionStatus.CANCELLED, 0, false, practitioner = "M. Santos"),
    )

    val clients = mutableStateListOf(
        BoardClient("c-1", "Liza Navarro", "F", 34),
        BoardClient("c-2", "Kenji Uy", "M", 29),
        BoardClient("c-3", "Ama Serna", "F", 41),
        BoardClient("c-4", "Paolo Lim", "M", 37),
        BoardClient("c-5", "Rhea Daza", "F", 26),
        BoardClient("c-6", "Isko Marasigan", "M", 52),
    )

    val remittances = mutableStateListOf(
        BoardRemittance(BoardRemitKind.SESSION, draftTotal = 3900),
        BoardRemittance(BoardRemitKind.PRODUCT, draftTotal = 1450),
    )

    val notices = mutableStateListOf(
        BoardNotice("n-1", "Relief grant approved", "J. Cruz granted you edit access at Makati Flagship for today."),
        BoardNotice("n-2", "Snapshot frozen", "SESSION remittance snapshot WB-2201 locked the P&L state.", read = true),
        BoardNotice("n-3", "Mission roster pinned", "Payatas Mission Day volunteer slots are on the board in green."),
    )

    val audits = mutableStateListOf(
        BoardAudit("a-1", "08:02", "M. Santos", "CLOCK_IN", "Makati Flagship"),
        BoardAudit("a-2", "08:15", "J. Cruz", "REMIT_DRAFT", "SESSION draft 3900"),
        BoardAudit("a-3", "08:40", "A. Villanueva", "INVITE_SENT", "R. Ocampo to Laguna Pop-up Tour", "extra hands"),
    )

    val invites = mutableStateListOf(
        BoardInvite("i-1", "b-tour", "2026-09-11", "A. Villanueva"),
    )

    val requests = mutableStateListOf(
        BoardRequest("r-1", "b-makati", "2026-09-10", "R. Ocampo"),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var clockedIn by mutableStateOf(false)
    var clockBranchId by mutableStateOf("b-makati")
    var currentBranchId by mutableStateOf("b-makati")
    var dayStatus by mutableStateOf(BoardDayStatus.OPEN)
    var operationalDate by mutableStateOf("2026-09-10")
    var reliefEdit by mutableStateOf(false)
    var clockCounter by mutableStateOf(2)

    val currentUser: BoardUser?
        get() = users.firstOrNull { it.id == currentUserId }

    val currentBranch: BoardBranch
        get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun login(userId: String) {
        currentUserId = userId
        val user = users.first { it.id == userId }
        audit("LOGIN", user.name)
        notify("Welcome back", "${user.name} pinned onto the board as ${user.role}.")
    }

    fun logout() {
        val name = currentUser?.name ?: "Guest"
        audit("LOGOUT", name)
        currentUserId = null
        clockedIn = false
        reliefEdit = false
    }

    fun clockIn(branchId: String, relief: Boolean) {
        clockedIn = true
        clockBranchId = branchId
        currentBranchId = branchId
        clockCounter += 1
        val me = currentUser?.name ?: "Guest"
        if (relief) {
            audit("RELIEF_CLOCK_IN", branchName(branchId), "view-only until grant")
        } else {
            audit("CLOCK_IN", branchName(branchId))
        }
        notify("Clocked in", "$me on duty at ${branchName(branchId)}.")
    }

    fun clockOut() {
        val me = currentUser?.name ?: "Guest"
        audit("CLOCK_OUT", branchName(clockBranchId))
        notify("Clocked out", "$me off duty. Cards stay pinned for standup.")
        clockedIn = false
        reliefEdit = false
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == BoardSessionStatus.PENDING && !it.voided }

    fun setStatus(session: BoardSession, next: BoardSessionStatus) {
        val before = session.status
        session.status = next
        audit("SESSION_${next}", "${session.clientName} ${session.slot}", "was $before")
    }

    fun voidSession(session: BoardSession, reason: String) {
        session.voided = true
        session.voidReason = reason.ifBlank { "No reason given" }
        audit("SESSION_VOID", "${session.clientName} ${session.slot}", session.voidReason)
    }

    fun unvoidSession(session: BoardSession) {
        session.voided = false
        audit("SESSION_UNVOID", "${session.clientName} ${session.slot}", session.voidReason)
        session.voidReason = null
    }

    fun addSession(clientName: String, slot: String, type: String, price: Int, walkIn: Boolean, branchId: String) {
        val id = "s-${110 + sessions.size}"
        val clientId = "c-new-${sessions.size}"
        if (clients.none { it.name == clientName }) {
            clients.add(BoardClient(clientId, clientName, "F", 30))
        }
        val owner = clients.firstOrNull { it.name == clientName }
        sessions.add(
            BoardSession(
                id = id,
                clientId = owner?.id ?: clientId,
                clientName = clientName,
                branchId = branchId,
                slot = slot,
                type = type,
                status = BoardSessionStatus.PENDING,
                price = price,
                walkIn = walkIn,
                practitioner = currentUser?.name ?: "M. Santos",
            ),
        )
        audit("SESSION_CREATE", "$clientName $slot", if (walkIn) "walk-in" else "booked")
    }

    fun anonymize(client: BoardClient) {
        client.anonymized = true
        client.name = "Anonymized Client ${client.id.takeLast(3)}"
        audit("CLIENT_ANONYMIZE", client.id, "PII nullified, gender and age kept")
    }

    fun submitRemittance(remit: BoardRemittance) {
        remit.state = BoardRemitState.SUBMITTED
        remit.snapshotId = "WB-${2200 + remittances.indexOf(remit)}"
        remit.snapshotTotal = remit.draftTotal
        remit.submittedAt = "just now"
        remit.windowElapsed = false
        audit("REMIT_SUBMIT", "${remit.kind} ${remit.snapshotId}", "snapshot ${remit.snapshotTotal} frozen")
        notify("Remittance submitted", "${remit.kind} snapshot ${remit.snapshotId} frozen.")
    }

    fun undoRemittance(remit: BoardRemittance, reason: String) {
        val id = remit.snapshotId ?: "draft"
        remit.state = BoardRemitState.DRAFT
        remit.snapshotId = null
        remit.snapshotTotal = null
        remit.submittedAt = null
        remit.undoReason = reason.ifBlank { "No reason given" }
        audit("REMIT_UNDO", "${remit.kind} $id", remit.undoReason)
        notify("Remittance reopened", "${remit.kind} $id returned to draft.")
    }

    fun audit(action: String, target: String, reason: String? = null) {
        val who = currentUser?.name ?: "Prototype"
        audits.add(0, BoardAudit("a-${audits.size + 100}", "now", who, action, target, reason))
    }

    fun notify(title: String, body: String) {
        notices.add(0, BoardNotice("n-${notices.size + 100}", title, body))
    }
}
