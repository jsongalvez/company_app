package com.companyb.companyapp.proto.limboroom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

// #824 — limbo-room fake domain. Local only: no network client, no remote calls,
// no backend, no shared contracts. A seeded hall store drives every room.

enum class LrDayStatus { OPEN, PAST, REMITTED }

enum class LrSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class LrRole { ONBOARDING, PRACTITIONER, COORDINATOR, MANAGER, ACCOUNTANT }

enum class LrBranchKind { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION }

enum class LrRemitKind { SESSION, PRODUCT }

enum class LrRemitState { DRAFT, SUBMITTED }

enum class LrReliefKind { DUTY, REQUEST, INVITE }

enum class LrReliefState { OPEN, GRANTED, DENIED, ACCEPTED, DECLINED, REVOKED }

enum class LrRoom { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class LrBranch(
    val id: String,
    val name: String,
    val kind: LrBranchKind,
    val line: String,
    val dayStatus: LrDayStatus,
    val dayLabel: String,
)

data class LrUser(
    val id: String,
    val name: String,
    val role: LrRole,
    val homeBranchId: String,
    val slot: Int,
)

data class LrSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    var status: LrSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    var ticketNo: String? = null,
    var voided: Boolean = false,
    var voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class LrClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
)

data class LrProductLine(
    val id: String,
    val name: String,
    var qty: Int,
    val price: Int,
)

data class LrRemit(
    val kind: LrRemitKind,
    var state: LrRemitState = LrRemitState.DRAFT,
    var draftGross: Int = 0,
    var draftDeductions: Int = 0,
    var snapshotId: String? = null,
    var snapshotTotal: Int? = null,
    var submittedAt: String? = null,
    var undoReason: String? = null,
)

data class LrRelief(
    val id: String,
    val kind: LrReliefKind,
    val who: String,
    val branchId: String,
    val day: String,
    var state: LrReliefState,
)

data class LrNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String,
    val day: String,
    var read: Boolean = false,
)

data class LrAudit(
    val id: String,
    val at: String,
    val actor: String,
    val action: String,
)

fun lrCaps(role: LrRole): List<String> =
    when (role) {
        LrRole.MANAGER -> listOf("sessions.write", "void.approve", "remit.submit", "remit.undo", "team.grant", "branch.close")
        LrRole.COORDINATOR -> listOf("sessions.write", "remit.submit", "remit.undo", "branch.close")
        LrRole.PRACTITIONER -> listOf("sessions.write", "clients.read")
        LrRole.ACCOUNTANT -> listOf("sales.read", "audit.read")
        LrRole.ONBOARDING -> emptyList()
    }

class LrStore {
    var phase by mutableStateOf("LOGIN")
    var room by mutableStateOf(LrRoom.HOME)
    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-makati")
    var clockedIn by mutableStateOf(false)
    var clockBranchId by mutableStateOf<String?>(null)
    var loginSecret by mutableStateOf("")
    var loginError by mutableStateOf("")
    var loginPick by mutableStateOf(0)
    var dayFilter by mutableStateOf("2026-09-09")
    var sessionFilter by mutableStateOf("ALL")
    var showVoided by mutableStateOf(false)
    var selectedSessionId by mutableStateOf<String?>(null)
    var voidDialogFor by mutableStateOf<String?>(null)
    var voidReasonText by mutableStateOf("")
    var bookOpen by mutableStateOf(false)
    var bookName by mutableStateOf("")
    var bookWalkIn by mutableStateOf(false)
    var undoDialogFor by mutableStateOf<LrRemitKind?>(null)
    var undoReasonText by mutableStateOf("")
    var mailFilter by mutableStateOf("ALL")
    var newProductName by mutableStateOf("")
    var adjustSession by mutableStateOf("0")
    var adjustProduct by mutableStateOf("0")

    val branches = mutableStateListOf<LrBranch>()
    val users = mutableStateListOf<LrUser>()
    val sessions: SnapshotStateList<LrSession> = mutableStateListOf()
    val clients: SnapshotStateList<LrClient> = mutableStateListOf()
    val productLines: SnapshotStateList<LrProductLine> = mutableStateListOf()
    val sessionRemit = LrRemitStateHolder(LrRemitKind.SESSION)
    val productRemit = LrRemitStateHolder(LrRemitKind.PRODUCT)
    val reliefs: SnapshotStateList<LrRelief> = mutableStateListOf()
    val notifications: SnapshotStateList<LrNotification> = mutableStateListOf()
    val audits: SnapshotStateList<LrAudit> = mutableStateListOf()

    val currentUser: LrUser?
        get() = users.firstOrNull { it.id == currentUserId }

    val currentBranch: LrBranch?
        get() = branches.firstOrNull { it.id == currentBranchId }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun login(user: LrUser) {
        if (loginSecret.isBlank()) {
            loginError = "Enter any secret to take a number — the hall stamps every entry."
            return
        }
        loginError = ""
        currentUserId = user.id
        currentBranchId = user.homeBranchId
        audits.add(0, LrAudit("a${audits.size + 1}", "09:${10 + audits.size % 49} AM", user.name, "signed in at the front desk"))
        if (user.role == LrRole.ONBOARDING) {
            phase = "LOCKED"
        } else {
            phase = "BRANCH"
        }
    }

    fun logout() {
        val name = currentUser?.name ?: "guest"
        audits.add(0, LrAudit("a${audits.size + 1}", "now", name, "left the hall (signed out)"))
        currentUserId = null
        clockedIn = false
        clockBranchId = null
        loginSecret = ""
        loginError = ""
        room = LrRoom.HOME
        phase = "LOGIN"
    }

    fun enterBranch(id: String) {
        currentBranchId = id
        room = LrRoom.HOME
        phase = "HALL"
        audits.add(
            0,
            LrAudit(
                "a${audits.size + 1}",
                "now",
                currentUser?.name ?: "?",
                "entered ${branchName(id)} waiting hall",
            ),
        )
    }

    fun toggleClock() {
        clockedIn = !clockedIn
        clockBranchId = if (clockedIn) currentBranchId else null
        audits.add(
            0,
            LrAudit(
                "a${audits.size + 1}",
                "now",
                currentUser?.name ?: "?",
                if (clockedIn) "clocked in at ${branchName(currentBranchId)}" else "clocked out",
            ),
        )
    }

    fun pendingClientIds(): Set<String> =
        sessions.filter { it.status == LrSessionStatus.PENDING && !it.voided }.map { it.clientId }.toSet()
}

class LrRemitStateHolder(val kind: LrRemitKind) {
    var state by mutableStateOf(LrRemitState.DRAFT)
    var draftGross by mutableStateOf(0)
    var draftDeductions by mutableStateOf(0)
    var snapshotId by mutableStateOf<String?>(null)
    var snapshotTotal by mutableStateOf<Int?>(null)
    var submittedAt by mutableStateOf<String?>(null)
    var undoReason by mutableStateOf<String?>(null)
}

fun seedLrStore(): LrStore {
    val s = LrStore()
    s.branches.addAll(
        listOf(
            LrBranch("b-makati", "Makati Clinic", LrBranchKind.CLINIC, "anela Bldg, Poblacion", LrDayStatus.OPEN, "2026-09-09"),
            LrBranch("b-cebu", "Cebu Provincial Tour", LrBranchKind.PROVINCIAL_TOUR, "off-site tent, Plaza Sugbo", LrDayStatus.PAST, "2026-09-08"),
            LrBranch("b-tondo", "Tondo Medical Mission", LrBranchKind.MEDICAL_MISSION, "covered court, Smokey Mt.", LrDayStatus.REMITTED, "2026-09-07"),
        ),
    )
    s.users.addAll(
        listOf(
            LrUser("u-onb", "K. Dela Pena", LrRole.ONBOARDING, "b-makati", 9),
            LrUser("u-prac", "M. Santos", LrRole.PRACTITIONER, "b-makati", 2),
            LrUser("u-coor", "J. Reyes", LrRole.COORDINATOR, "b-makati", 1),
            LrUser("u-mgr", "A. Villanueva", LrRole.MANAGER, "b-cebu", 1),
            LrUser("u-acct", "R. Ocampo", LrRole.ACCOUNTANT, "b-makati", 4),
        ),
    )
    s.sessions.addAll(
        listOf(
            LrSession("s-101", "c-1", "L. Aquino", "b-makati", "09:00", "OTC-2", LrSessionStatus.PENDING, 850, false, ticketNo = "A-101"),
            LrSession("s-102", "c-2", "D. Ramos", "b-makati", "09:40", "OTC-1", LrSessionStatus.COMPLETED, 650, false, ticketNo = "A-102"),
            LrSession("s-103", "c-3", "Walk-in guest", "b-makati", "10:10", "WALK-IN", LrSessionStatus.PENDING, 500, true, ticketNo = "W-07"),
            LrSession("s-104", "c-4", "P. Navarro", "b-makati", "11:00", "OTC-3", LrSessionStatus.NO_SHOW, 850, false, ticketNo = "A-104"),
            LrSession("s-105", "c-5", "G. Torres", "b-cebu", "02:00", "TOUR-1", LrSessionStatus.CANCELLED, 400, false, ticketNo = "B-014"),
            LrSession("s-106", "c-6", "S. Lim", "b-makati", "01:30", "OTC-2", LrSessionStatus.COMPLETED, 850, false, ticketNo = "A-106", voided = true, voidReason = "duplicate entry"),
        ),
    )
    s.clients.addAll(
        listOf(
            LrClient("c-1", "L. Aquino", "F", 34),
            LrClient("c-2", "D. Ramos", "M", 51),
            LrClient("c-3", "Walk-in guest", "M", 29),
            LrClient("c-4", "P. Navarro", "F", 44),
            LrClient("c-5", "G. Torres", "M", 38),
            LrClient("c-6", "S. Lim", "F", 27),
            LrClient("c-7", "R. Salazar", "M", 61, anonymized = true),
        ),
    )
    s.productLines.addAll(
        listOf(
            LrProductLine("p-1", "Herbal Balm", 3, 250),
            LrProductLine("p-2", "Hot Pack", 1, 450),
        ),
    )
    s.sessionRemit.draftGross = 2350
    s.sessionRemit.draftDeductions = 420
    s.productRemit.draftGross = 1200
    s.productRemit.draftDeductions = 0
    s.reliefs.addAll(
        listOf(
            LrRelief("r-1", LrReliefKind.DUTY, "M. Santos → Cebu Provincial Tour", "b-cebu", "2026-09-09", LrReliefState.GRANTED),
            LrRelief("r-2", LrReliefKind.REQUEST, "J. Reyes asks Makati cover", "b-makati", "2026-09-10", LrReliefState.OPEN),
            LrRelief("r-3", LrReliefKind.INVITE, "A. Villanueva invites R. Ocampo", "b-cebu", "2026-09-11", LrReliefState.OPEN),
        ),
    )
    s.notifications.addAll(
        listOf(
            LrNotification("n-1", "Relief granted", "M. Santos may edit at Cebu Provincial Tour for 2026-09-09.", "b-cebu", "2026-09-09", read = false),
            LrNotification("n-2", "Session reminder", "L. Aquino (A-101) is PENDING at 09:00 in Makati Clinic.", "b-makati", "2026-09-09", read = false),
            LrNotification("n-3", "Remittance sealed", "Tondo Medical Mission 2026-09-07 snapshot sealed by J. Reyes.", "b-tondo", "2026-09-07", read = true),
            LrNotification("n-4", "Role still pending", "K. Dela Pena is waiting in the limbo room — a manager grant is needed.", "b-makati", "2026-09-09", read = false),
        ),
    )
    s.audits.addAll(
        listOf(
            LrAudit("a-1", "08:58 AM", "system", "branch day 2026-09-09 opened at 04:00 Asia/Manila"),
            LrAudit("a-2", "09:02 AM", "J. Reyes", "clocked in at Makati Clinic"),
            LrAudit("a-3", "09:15 AM", "M. Santos", "completed session A-102 for D. Ramos"),
            LrAudit("a-4", "09:31 AM", "J. Reyes", "voided session A-106 (duplicate entry)"),
        ),
    )
    return s
}

fun peso(amount: Int): String {
    val neg = amount < 0
    val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-₱" else "₱") + digits
}
