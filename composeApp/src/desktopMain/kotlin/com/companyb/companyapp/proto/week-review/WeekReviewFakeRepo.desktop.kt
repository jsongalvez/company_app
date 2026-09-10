package com.companyb.companyapp.proto.weekreview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #850 — week-review fake data only; isolated from networking and backend.
// Immutable rows + index-copy writes so SnapshotStateList notifies correctly.

enum class WrSessionStatus(val label: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class WrDayState(val label: String) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class WrRole(val label: String) {
    ONBOARDING("Onboarding"),
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("Manager"),
    ACCOUNTANT("Accountant"),
}

enum class WrRemitKind(val label: String) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class WrRemitState(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    UNDONE("Undone"),
}

data class WrUser(
    val id: String,
    val name: String,
    val role: WrRole,
    val branch: String,
)

data class WrBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayDate: String,
)

data class WrSession(
    val id: String,
    val service: String,
    val client: String,
    val branch: String,
    val day: String,
    val practitioner: String,
    val walkIn: Boolean,
    val amount: Int,
    val status: WrSessionStatus,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class WrClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val homeBranch: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
)

data class WrRemittance(
    val id: String,
    val kind: WrRemitKind,
    val branchDay: String,
    val amount: Int,
    val state: WrRemitState,
    val snapshotId: String? = null,
    val undoReason: String? = null,
)

data class WrPayout(
    val id: String,
    val staff: String,
    val role: String,
    val completed: Int,
    val share: Int,
    val paid: Boolean = false,
)

data class WrSeed(
    val id: String,
    val title: String,
    val owner: String,
    val day: String,
    val planted: Boolean = false,
)

data class WrNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class WrAudit(
    val seq: Int,
    val stamp: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class WrInvite(
    val id: String,
    val fromBranch: String,
    val shift: String,
    val state: String = "PENDING",
)

data class WrReliefAsk(
    val id: String,
    val by: String,
    val shift: String,
    val mine: Boolean,
    val state: String = "PENDING",
)

class WeekReviewFakeRepo {
    val users = mutableStateListOf(
        WrUser("U-SAM", "Sam Rivera", WrRole.ONBOARDING, "QC Central"),
        WrUser("U-ANN", "Ann Reyes", WrRole.PRACTITIONER, "QC Central"),
        WrUser("U-JOY", "Joy Cruz", WrRole.COORDINATOR, "QC Central"),
        WrUser("U-MIA", "Mia Santos", WrRole.MANAGER, "QC Central"),
        WrUser("U-ROB", "Rob Dela Cruz", WrRole.ACCOUNTANT, "QC Central"),
    )
    val branches = mutableStateListOf(
        WrBranch("B-QC", "QC Central", "Clinic", "Fri · Sep 11, 2026"),
        WrBranch("B-LAG", "Laguna Tour Stop 3", "Provincial tour", "Fri · Sep 11, 2026"),
        WrBranch("B-TON", "Tondo Medical Mission", "Medical mission", "Sat · Sep 12, 2026"),
    )
    val sessions = mutableStateListOf(
        WrSession("S-101", "Lash Lift", "A. Villanueva", "QC Central", "Mon", "Ann Reyes", false, 1200, WrSessionStatus.COMPLETED),
        WrSession("S-102", "Brow Lamination", "J. Ocampo", "QC Central", "Mon", "Ann Reyes", false, 950, WrSessionStatus.COMPLETED),
        WrSession("S-103", "Signature Facial", "Walk-in guest", "QC Central", "Mon", "Joy Cruz", true, 1500, WrSessionStatus.COMPLETED),
        WrSession("S-104", "Hair Color", "M. Aquino", "Laguna Tour Stop 3", "Tue", "Ann Reyes", false, 2800, WrSessionStatus.NO_SHOW),
        WrSession("S-105", "Swedish Massage", "R. Torres", "QC Central", "Tue", "Joy Cruz", false, 1100, WrSessionStatus.COMPLETED),
        WrSession("S-106", "Gel Nails", "Walk-in guest", "QC Central", "Tue", "Ann Reyes", true, 800, WrSessionStatus.COMPLETED),
        WrSession("S-107", "Signature Facial", "L. Garcia", "QC Central", "Wed", "Joy Cruz", false, 1500, WrSessionStatus.CANCELLED),
        WrSession("S-108", "Lash Fill", "A. Villanueva", "QC Central", "Wed", "Ann Reyes", false, 700, WrSessionStatus.COMPLETED),
        WrSession("S-109", "Skin Consultation", "J. Ocampo", "QC Central", "Wed", "Joy Cruz", false, 500, WrSessionStatus.PENDING),
        WrSession("S-110", "Swedish Massage", "M. Aquino", "Laguna Tour Stop 3", "Thu", "Ann Reyes", false, 1100, WrSessionStatus.COMPLETED),
        WrSession("S-111", "Haircut", "Walk-in guest", "Tondo Medical Mission", "Thu", "Joy Cruz", true, 350, WrSessionStatus.PENDING),
        WrSession("S-112", "Brow Shape", "R. Torres", "QC Central", "Thu", "Ann Reyes", false, 450, WrSessionStatus.COMPLETED, voided = true, voidReason = "Double-booked chair"),
        WrSession("S-113", "Signature Facial", "L. Garcia", "QC Central", "Fri", "Joy Cruz", false, 1500, WrSessionStatus.PENDING),
        WrSession("S-114", "Hot Stone Massage", "A. Villanueva", "QC Central", "Fri", "Ann Reyes", false, 1600, WrSessionStatus.PENDING),
    )
    val clients = mutableStateListOf(
        WrClient("C-01", "A. Villanueva", "F", 34, "QC Central", 0),
        WrClient("C-02", "J. Ocampo", "M", 41, "QC Central", 1),
        WrClient("C-03", "M. Aquino", "F", 29, "Laguna Tour Stop 3", 0),
        WrClient("C-04", "R. Torres", "F", 52, "QC Central", 0),
        WrClient("C-05", "L. Garcia", "F", 37, "QC Central", 1),
        WrClient("C-06", "D. Mendoza", "M", 45, "Tondo Medical Mission", 0),
        WrClient("C-07", "S. Lim", "F", 26, "QC Central", 0),
        WrClient("C-08", "P. Navarro", "F", 61, "Laguna Tour Stop 3", 0),
    )
    val remittances = mutableStateListOf(
        WrRemittance("R-SES-0911", WrRemitKind.SESSION, "QC Central · Fri Sep 11", 18400, WrRemitState.DRAFT),
        WrRemittance("R-PRD-0911", WrRemitKind.PRODUCT, "QC Central · Fri Sep 11", 6250, WrRemitState.DRAFT),
    )
    val payouts = mutableStateListOf(
        WrPayout("P-01", "Ann Reyes", "Practitioner", 7, 4200, paid = true),
        WrPayout("P-02", "Joy Cruz", "Coordinator", 5, 3150),
        WrPayout("P-03", "Mia Santos", "Manager", 2, 2850),
        WrPayout("P-04", "Rob Dela Cruz", "Accountant", 0, 2400, paid = true),
    )
    val seeds = mutableStateListOf(
        WrSeed("D-01", "Confirm Laguna supplier delivery", "Joy Cruz", "Mon", planted = true),
        WrSeed("D-02", "Restock lash adhesive + brow tint", "Ann Reyes", "Mon"),
        WrSeed("D-03", "Rebook Tue no-show: M. Aquino", "Joy Cruz", "Tue"),
        WrSeed("D-04", "Mission consent forms to print", "Mia Santos", "Wed"),
    )
    val notices = mutableStateListOf(
        WrNotice("N-1", "Friday review is ready", "Wins, misses and payouts for Sep 7–11 are tallied.", read = false),
        WrNotice("N-2", "Relief invite: Laguna Tour", "Sat Sep 12 evening shift needs one practitioner.", read = false),
        WrNotice("N-3", "Snapshot sealed", "SESSION remittance for Thu Sep 10 was sealed at close.", read = true),
        WrNotice("N-4", "No-show follow-up", "M. Aquino (Tue hair color) is queued for rebooking.", read = true),
        WrNotice("N-5", "Welcome to the Friday ritual", "Read the front page top to bottom, then plant seeds.", read = true),
    )
    val ledger = mutableStateListOf(
        WrAudit(2, "Fri 08:00", "system", "BRANCH_DAY", "QC Central · Fri Sep 11 opened", null),
        WrAudit(1, "Fri 08:00", "system", "WEEK", "Review week Sep 7–11 opened", null),
    )
    val invites = mutableStateListOf(
        WrInvite("I-1", "Laguna Tour Stop 3", "Sat Sep 12 · evening"),
        WrInvite("I-2", "Tondo Medical Mission", "Sat Sep 12 · morning"),
    )
    val reliefAsks = mutableStateListOf(
        WrReliefAsk("Q-1", "Ann Reyes", "Fri Sep 11 · night cover", mine = true),
        WrReliefAsk("Q-2", "D. Mendoza", "Sun Sep 13 · swap", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var branchId by mutableStateOf("B-QC")
    var dayStatus by mutableStateOf(WrDayState.OPEN)
    var clockedIn by mutableStateOf(false)
    private var auditSeq = 2
    private var seedSeq = 4
    private var sessionSeq = 114
    private var askSeq = 2

    val currentUser: WrUser? get() = users.firstOrNull { it.id == currentUserId }
    val branch: WrBranch get() = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun capabilitiesOf(role: WrRole): String =
        when (role) {
            WrRole.ONBOARDING -> "Locked: no capabilities until a Manager grants a role."
            WrRole.PRACTITIONER -> "Sessions (own), clock-in/out, relief duty."
            WrRole.COORDINATOR -> "Sessions, clients, branch-day banner, relief invites."
            WrRole.MANAGER -> "All coordinator rights + users/roles, void, remittance submit."
            WrRole.ACCOUNTANT -> "Finance read, remittance draft/submit, payout envelopes."
        }

    private fun audit(
        action: String,
        target: String,
        reason: String? = null,
    ) {
        auditSeq += 1
        ledger.add(
            0,
            WrAudit(auditSeq, "Fri 15:0${auditSeq % 10}", currentUser?.name ?: "kiosk", action, target, reason),
        )
    }

    fun login(id: String) {
        currentUserId = id
        clockedIn = false
        audit("LOGIN", users.first { it.id == id }.name)
    }

    fun logout() {
        audit("LOGOUT", currentUser?.name ?: "kiosk")
        currentUserId = null
        clockedIn = false
    }

    fun grantRole(
        id: String,
        role: WrRole,
    ) {
        val i = users.indexOfFirst { it.id == id }
        if (i < 0) return
        users[i] = users[i].copy(role = role)
        audit("GRANT_ROLE", "${users[i].name} -> ${role.label}")
    }

    fun clockToggle() {
        clockedIn = !clockedIn
        audit(if (clockedIn) "CLOCK_IN" else "CLOCK_OUT", currentUser?.name ?: "kiosk")
    }

    fun pickBranch(id: String) {
        branchId = id
        audit("BRANCH_SELECT", branches.first { it.id == id }.name)
    }

    fun setDay(status: WrDayState) {
        dayStatus = status
        audit("BRANCH_DAY", "${branch.name} -> ${status.label}")
    }

    fun setSessionStatus(
        id: String,
        next: WrSessionStatus,
    ): String? {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return "Session not found."
        val cur = sessions[i]
        if (cur.walkIn && (next == WrSessionStatus.NO_SHOW || next == WrSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (cur.status != WrSessionStatus.PENDING) return "Only PENDING sessions change status."
        sessions[i] = cur.copy(status = next)
        audit("STATUS", "Session $id -> ${next.label}")
        return null
    }

    fun setVoid(
        id: String,
        voided: Boolean,
        reason: String,
    ): String? {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return "Session not found."
        if (voided && reason.isBlank()) return "A void reason is required."
        val cur = sessions[i]
        sessions[i] = cur.copy(voided = voided, voidReason = if (voided) reason else null)
        audit(if (voided) "VOID" else "UNVOID", "Session $id", if (voided) reason else null)
        return null
    }

    fun addWalkIn(
        service: String,
        practitioner: String,
    ): String? {
        if (service.isBlank()) return "Describe the walk-in service."
        sessionSeq += 1
        val id = "S-$sessionSeq"
        sessions.add(
            WrSession(id, service.trim(), "Walk-in guest", branch.name, "Fri", practitioner, true, 600, WrSessionStatus.PENDING),
        )
        audit("WALK_IN", "$id $service")
        return null
    }

    fun anonymize(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = clients[i]
        clients[i] = cur.copy(anonymized = !cur.anonymized)
        audit(if (cur.anonymized) "DEANONYMIZE" else "ANONYMIZE", "Client $id")
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = remittances[i]
        if (cur.state != WrRemitState.DRAFT) return
        remittances[i] = cur.copy(state = WrRemitState.SUBMITTED, snapshotId = "SNAP-${cur.id}")
        audit("REMIT_SUBMIT", "$id sealed as SNAP-${cur.id}")
    }

    fun undoRemittance(
        id: String,
        reason: String,
    ): String? {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return "Remittance not found."
        if (reason.isBlank()) return "An undo reason is required (48h window)."
        val cur = remittances[i]
        if (cur.state != WrRemitState.SUBMITTED) return "Only submitted snapshots can be undone."
        remittances[i] = cur.copy(state = WrRemitState.UNDONE, undoReason = reason)
        audit("REMIT_UNDO", id, reason)
        return null
    }

    fun markPaid(id: String) {
        val i = payouts.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = payouts[i]
        payouts[i] = cur.copy(paid = !cur.paid)
        audit(if (cur.paid) "PAYOUT_REOPEN" else "PAYOUT_PAID", "${cur.staff} ${peso(cur.share)}")
    }

    fun addSeed(
        title: String,
        owner: String,
        day: String,
    ): String? {
        if (title.isBlank()) return "Name the seed."
        seedSeq += 1
        seeds.add(WrSeed("D-0$seedSeq", title.trim(), owner.ifBlank { "Unassigned" }, day))
        audit("SEED_PLANT", title.trim())
        return null
    }

    fun toggleSeed(id: String) {
        val i = seeds.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = seeds[i]
        seeds[i] = cur.copy(planted = !cur.planted)
        audit(if (cur.planted) "SEED_REOPEN" else "SEED_DONE", cur.title)
    }

    fun answerInvite(
        id: String,
        accept: Boolean,
    ) {
        val i = invites.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = invites[i]
        invites[i] = cur.copy(state = if (accept) "ACCEPTED" else "DECLINED")
        audit(if (accept) "INVITE_ACCEPT" else "INVITE_DECLINE", "${cur.fromBranch} ${cur.shift}")
    }

    fun answerAsk(
        id: String,
        grant: Boolean,
    ) {
        val i = reliefAsks.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = reliefAsks[i]
        reliefAsks[i] = cur.copy(state = if (grant) "GRANTED" else if (cur.mine) "WITHDRAWN" else "DENIED")
        audit(if (grant) "RELIEF_GRANT" else "RELIEF_CLOSE", "${cur.by} ${cur.shift}")
    }

    fun raiseAsk(shift: String): String? {
        if (shift.isBlank()) return "Describe the shift cover needed."
        askSeq += 1
        reliefAsks.add(WrReliefAsk("Q-$askSeq", currentUser?.name ?: "kiosk", shift.trim(), mine = true))
        audit("RELIEF_ASK", shift.trim())
        return null
    }

    fun markNotice(
        id: String,
        read: Boolean,
    ) {
        val i = notices.indexOfFirst { it.id == id }
        if (i < 0) return
        notices[i] = notices[i].copy(read = read)
    }

    fun markAllRead() {
        for (i in notices.indices) notices[i] = notices[i].copy(read = true)
        audit("MAILBOX", "all notices marked read")
    }

    fun weekCompleted(): List<WrSession> = sessions.filter { it.status == WrSessionStatus.COMPLETED && !it.voided }

    fun weekMisses(): List<WrSession> =
        sessions.filter {
            it.status == WrSessionStatus.NO_SHOW || it.status == WrSessionStatus.CANCELLED || it.voided
        }

    fun daySummary(): List<Pair<String, Pair<Int, Int>>> {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        return days.map { d ->
            val done = sessions.count { it.day == d && it.status == WrSessionStatus.COMPLETED && !it.voided }
            val total = sessions.count { it.day == d }
            d to (done to total)
        }
    }
}
