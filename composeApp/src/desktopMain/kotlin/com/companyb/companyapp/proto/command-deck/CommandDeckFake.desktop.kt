package com.companyb.companyapp.proto.commanddeck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

// #828 — command-deck fake domain. Local only: no network client, no remote calls,
// no backend, no shared contracts. A seeded deck store drives every station.

enum class CdDayStatus { OPEN, PAST, REMITTED }

enum class CdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class CdRole { ONBOARDING, PRACTITIONER, COORDINATOR, MANAGER, ACCOUNTANT }

enum class CdBranchKind { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION }

enum class CdRemitKind { SESSION, PRODUCT }

enum class CdRemitState { DRAFT, SUBMITTED }

enum class CdReliefKind { DUTY, REQUEST, INVITE }

enum class CdReliefState { OPEN, GRANTED, DENIED, ACCEPTED, DECLINED, REVOKED }

enum class CdStation { HELM, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class CdBranch(
    val id: String,
    val name: String,
    val kind: CdBranchKind,
    val line: String,
    val dayStatus: CdDayStatus,
    val dayLabel: String,
)

data class CdUser(
    val id: String,
    val name: String,
    val role: CdRole,
    val homeBranchId: String,
    val slot: Int,
)

data class CdSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    var status: CdSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    var ticketNo: String? = null,
    var voided: Boolean = false,
    var voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class CdClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
)

data class CdProductLine(
    val id: String,
    val name: String,
    var qty: Int,
    val price: Int,
)

data class CdRelief(
    val id: String,
    val kind: CdReliefKind,
    val who: String,
    val branchId: String,
    val day: String,
    var state: CdReliefState,
)

data class CdNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String,
    val day: String,
    var read: Boolean = false,
)

data class CdAudit(
    val id: String,
    val at: String,
    val actor: String,
    val action: String,
)

fun cdCaps(role: CdRole): List<String> =
    when (role) {
        CdRole.MANAGER -> listOf("sessions.write", "void.approve", "remit.submit", "remit.undo", "team.grant", "branch.close")
        CdRole.COORDINATOR -> listOf("sessions.write", "remit.submit", "remit.undo", "branch.close")
        CdRole.PRACTITIONER -> listOf("sessions.write", "clients.read")
        CdRole.ACCOUNTANT -> listOf("sales.read", "audit.read")
        CdRole.ONBOARDING -> emptyList()
    }

class CdStore {
    var phase by mutableStateOf("LOGIN")
    var station by mutableStateOf(CdStation.HELM)
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
    var undoDialogFor by mutableStateOf<CdRemitKind?>(null)
    var undoReasonText by mutableStateOf("")
    var mailFilter by mutableStateOf("ALL")
    var newProductName by mutableStateOf("")
    var powerDampened by mutableStateOf(false)

    val branches = mutableStateListOf<CdBranch>()
    val users = mutableStateListOf<CdUser>()
    val sessions: SnapshotStateList<CdSession> = mutableStateListOf()
    val clients: SnapshotStateList<CdClient> = mutableStateListOf()
    val productLines: SnapshotStateList<CdProductLine> = mutableStateListOf()
    val sessionRemit = CdRemitStateHolder(CdRemitKind.SESSION)
    val productRemit = CdRemitStateHolder(CdRemitKind.PRODUCT)
    val reliefs: SnapshotStateList<CdRelief> = mutableStateListOf()
    val notifications: SnapshotStateList<CdNotification> = mutableStateListOf()
    val audits: SnapshotStateList<CdAudit> = mutableStateListOf()

    val currentUser: CdUser?
        get() = users.firstOrNull { it.id == currentUserId }

    val currentBranch: CdBranch?
        get() = branches.firstOrNull { it.id == currentBranchId }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun login(user: CdUser) {
        if (loginSecret.isBlank()) {
            loginError = "The deck needs a cipher — any value turns the key."
            return
        }
        loginError = ""
        currentUserId = user.id
        currentBranchId = user.homeBranchId
        audits.add(0, CdAudit("a${audits.size + 1}", "09:${10 + audits.size % 49} AM", user.name, "took the deck at the gangway"))
        if (user.role == CdRole.ONBOARDING) {
            phase = "LOCKED"
        } else {
            phase = "VESSEL"
        }
    }

    fun logout() {
        val name = currentUser?.name ?: "guest"
        audits.add(0, CdAudit("a${audits.size + 1}", "now", name, "left the deck (signed out)"))
        currentUserId = null
        clockedIn = false
        clockBranchId = null
        loginSecret = ""
        loginError = ""
        station = CdStation.HELM
        phase = "LOGIN"
    }

    fun enterBranch(id: String) {
        currentBranchId = id
        station = CdStation.HELM
        phase = "DECK"
        audits.add(
            0,
            CdAudit(
                "a${audits.size + 1}",
                "now",
                currentUser?.name ?: "?",
                "boarded ${branchName(id)} command deck",
            ),
        )
    }

    fun toggleClock() {
        clockedIn = !clockedIn
        clockBranchId = if (clockedIn) currentBranchId else null
        audits.add(
            0,
            CdAudit(
                "a${audits.size + 1}",
                "now",
                currentUser?.name ?: "?",
                if (clockedIn) "clocked in on ${branchName(currentBranchId)} deck" else "clocked out",
            ),
        )
    }

    fun pendingClientIds(): Set<String> =
        sessions.filter { it.status == CdSessionStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    // Klaxon restraint: at most the two conditions that truly need a human now.
    fun klaxons(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val limbo = users.count { it.role == CdRole.ONBOARDING }
        if (limbo > 0) out.add("KLAXON" to "$limbo ONBOARDING crew waiting on the observation deck — a MANAGER grant clears it")
        val pastOpen = dayFilter != "2026-09-09" &&
            (sessionRemit.state == CdRemitState.DRAFT || productRemit.state == CdRemitState.DRAFT)
        if (pastOpen) out.add("CAUTION" to "day $dayFilter is off the live clock with a DRAFT counter still open — seal or stand down")
        return out
    }
}

class CdRemitStateHolder(val kind: CdRemitKind) {
    var state by mutableStateOf(CdRemitState.DRAFT)
    var draftGross by mutableStateOf(0)
    var draftDeductions by mutableStateOf(0)
    var snapshotId by mutableStateOf<String?>(null)
    var snapshotTotal by mutableStateOf<Int?>(null)
    var submittedAt by mutableStateOf<String?>(null)
    var undoReason by mutableStateOf<String?>(null)
}

fun seedCdStore(): CdStore {
    val s = CdStore()
    s.branches.addAll(
        listOf(
            CdBranch("b-makati", "Makati Clinic", CdBranchKind.CLINIC, "anela Bldg, Poblacion", CdDayStatus.OPEN, "2026-09-09"),
            CdBranch("b-cebu", "Cebu Provincial Tour", CdBranchKind.PROVINCIAL_TOUR, "off-site tent, Plaza Sugbo", CdDayStatus.PAST, "2026-09-08"),
            CdBranch("b-tondo", "Tondo Medical Mission", CdBranchKind.MEDICAL_MISSION, "covered court, Smokey Mt.", CdDayStatus.REMITTED, "2026-09-07"),
        ),
    )
    s.users.addAll(
        listOf(
            CdUser("u-onb", "K. Dela Pena", CdRole.ONBOARDING, "b-makati", 9),
            CdUser("u-prac", "M. Santos", CdRole.PRACTITIONER, "b-makati", 2),
            CdUser("u-coor", "J. Reyes", CdRole.COORDINATOR, "b-makati", 1),
            CdUser("u-mgr", "A. Villanueva", CdRole.MANAGER, "b-cebu", 1),
            CdUser("u-acct", "R. Ocampo", CdRole.ACCOUNTANT, "b-makati", 4),
        ),
    )
    s.sessions.addAll(
        listOf(
            CdSession("s-101", "c-1", "L. Aquino", "b-makati", "09:00", "OTC-2", CdSessionStatus.PENDING, 850, false, ticketNo = "A-101"),
            CdSession("s-102", "c-2", "D. Ramos", "b-makati", "09:40", "OTC-1", CdSessionStatus.COMPLETED, 650, false, ticketNo = "A-102"),
            CdSession("s-103", "c-3", "Walk-in guest", "b-makati", "10:10", "WALK-IN", CdSessionStatus.PENDING, 500, true, ticketNo = "W-07"),
            CdSession("s-104", "c-4", "P. Navarro", "b-makati", "11:00", "OTC-3", CdSessionStatus.NO_SHOW, 850, false, ticketNo = "A-104"),
            CdSession("s-105", "c-5", "G. Torres", "b-cebu", "02:00", "TOUR-1", CdSessionStatus.CANCELLED, 400, false, ticketNo = "B-014"),
            CdSession("s-106", "c-6", "S. Lim", "b-makati", "01:30", "OTC-2", CdSessionStatus.COMPLETED, 850, false, ticketNo = "A-106", voided = true, voidReason = "duplicate entry"),
        ),
    )
    s.clients.addAll(
        listOf(
            CdClient("c-1", "L. Aquino", "F", 34),
            CdClient("c-2", "D. Ramos", "M", 51),
            CdClient("c-3", "Walk-in guest", "M", 29),
            CdClient("c-4", "P. Navarro", "F", 44),
            CdClient("c-5", "G. Torres", "M", 38),
            CdClient("c-6", "S. Lim", "F", 27),
            CdClient("c-7", "R. Salazar", "M", 61, anonymized = true),
        ),
    )
    s.productLines.addAll(
        listOf(
            CdProductLine("p-1", "Herbal Balm", 3, 250),
            CdProductLine("p-2", "Hot Pack", 1, 450),
        ),
    )
    s.sessionRemit.draftGross = 2350
    s.sessionRemit.draftDeductions = 420
    s.productRemit.draftGross = 1200
    s.productRemit.draftDeductions = 0
    s.reliefs.addAll(
        listOf(
            CdRelief("r-1", CdReliefKind.DUTY, "M. Santos → Cebu Provincial Tour", "b-cebu", "2026-09-09", CdReliefState.GRANTED),
            CdRelief("r-2", CdReliefKind.REQUEST, "J. Reyes asks Makati cover", "b-makati", "2026-09-10", CdReliefState.OPEN),
            CdRelief("r-3", CdReliefKind.INVITE, "A. Villanueva invites R. Ocampo", "b-cebu", "2026-09-11", CdReliefState.OPEN),
        ),
    )
    s.notifications.addAll(
        listOf(
            CdNotification("n-1", "Relief granted", "M. Santos may edit at Cebu Provincial Tour for 2026-09-09.", "b-cebu", "2026-09-09", read = false),
            CdNotification("n-2", "Session signal", "L. Aquino (A-101) is PENDING at 09:00 in Makati Clinic.", "b-makati", "2026-09-09", read = false),
            CdNotification("n-3", "Remittance sealed", "Tondo Medical Mission 2026-09-07 snapshot sealed by J. Reyes.", "b-tondo", "2026-09-07", read = true),
            CdNotification("n-4", "Crew waiting", "K. Dela Pena is on the observation deck — a manager grant is needed.", "b-makati", "2026-09-09", read = false),
        ),
    )
    s.audits.addAll(
        listOf(
            CdAudit("a-1", "08:58 AM", "system", "branch day 2026-09-09 opened at 04:00 Asia/Manila"),
            CdAudit("a-2", "09:02 AM", "J. Reyes", "clocked in on Makati Clinic deck"),
            CdAudit("a-3", "09:15 AM", "M. Santos", "completed session A-102 for D. Ramos"),
            CdAudit("a-4", "09:31 AM", "J. Reyes", "voided session A-106 (duplicate entry)"),
        ),
    )
    return s
}

fun cdPeso(amount: Int): String {
    val neg = amount < 0
    val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-₱" else "₱") + digits
}
