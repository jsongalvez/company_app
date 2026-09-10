package com.companyb.companyapp.proto.textonly

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #853 — text-only fake data only; isolated from networking and backend.
// Immutable rows + index-copy writes so SnapshotStateList notifies correctly.

enum class TxSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

enum class TxDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

enum class TxRole(
    val label: String,
) {
    ONBOARDING("ONBOARDING"),
    PRACTITIONER("PRACTITIONER"),
    COORDINATOR("COORDINATOR"),
    MANAGER("MANAGER"),
    ACCOUNTANT("ACCOUNTANT"),
}

enum class TxRemitKind(
    val label: String,
) {
    SESSION("SESSION"),
    PRODUCT("PRODUCT"),
}

enum class TxRemitState(
    val label: String,
) {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
    UNDONE("UNDONE"),
}

data class TxUser(
    val id: String,
    val name: String,
    val role: TxRole,
    val branch: String,
)

data class TxBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayDate: String,
)

data class TxSession(
    val id: String,
    val service: String,
    val client: String,
    val branch: String,
    val day: String,
    val practitioner: String,
    val walkIn: Boolean,
    val amount: Int,
    val status: TxSessionStatus,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class TxClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val homeBranch: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
)

data class TxRemittance(
    val id: String,
    val kind: TxRemitKind,
    val branchDay: String,
    val amount: Int,
    val state: TxRemitState,
    val snapshotId: String? = null,
    val undoReason: String? = null,
)

data class TxPayout(
    val id: String,
    val staff: String,
    val role: String,
    val completed: Int,
    val share: Int,
    val paid: Boolean = false,
)

data class TxNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class TxAudit(
    val seq: Int,
    val stamp: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class TxInvite(
    val id: String,
    val fromBranch: String,
    val shift: String,
    val state: String = "PENDING",
)

data class TxReliefAsk(
    val id: String,
    val by: String,
    val shift: String,
    val mine: Boolean,
    val state: String = "PENDING",
)

class TextOnlyFakeRepo {
    val users =
        mutableStateListOf(
            TxUser("U-SAM", "Sam Rivera", TxRole.ONBOARDING, "QC Central"),
            TxUser("U-ANN", "Ann Reyes", TxRole.PRACTITIONER, "QC Central"),
            TxUser("U-JOY", "Joy Cruz", TxRole.COORDINATOR, "QC Central"),
            TxUser("U-MIA", "Mia Santos", TxRole.MANAGER, "QC Central"),
            TxUser("U-ROB", "Rob Dela Cruz", TxRole.ACCOUNTANT, "QC Central"),
        )
    val branches =
        mutableStateListOf(
            TxBranch("B-QC", "QC Central", "CLINIC", "Fri Sep 11 2026"),
            TxBranch("B-LAG", "Laguna Tour Stop 3", "PROVINCIAL_TOUR", "Fri Sep 11 2026"),
            TxBranch("B-TON", "Tondo Medical Mission", "MEDICAL_MISSION", "Sat Sep 12 2026"),
        )
    val sessions =
        mutableStateListOf(
            TxSession(
                "S-101",
                "Lash Lift",
                "A. Villanueva",
                "QC Central",
                "Mon",
                "Ann Reyes",
                false,
                1200,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-102",
                "Brow Lamination",
                "J. Ocampo",
                "QC Central",
                "Mon",
                "Ann Reyes",
                false,
                950,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-103",
                "Signature Facial",
                "walk-in guest",
                "QC Central",
                "Mon",
                "Joy Cruz",
                true,
                1500,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-104",
                "Hair Color",
                "M. Aquino",
                "Laguna Tour Stop 3",
                "Tue",
                "Ann Reyes",
                false,
                2800,
                TxSessionStatus.NO_SHOW,
            ),
            TxSession(
                "S-105",
                "Swedish Massage",
                "R. Torres",
                "QC Central",
                "Tue",
                "Joy Cruz",
                false,
                1100,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-106",
                "Gel Nails",
                "walk-in guest",
                "QC Central",
                "Tue",
                "Ann Reyes",
                true,
                800,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-107",
                "Signature Facial",
                "L. Garcia",
                "QC Central",
                "Wed",
                "Joy Cruz",
                false,
                1500,
                TxSessionStatus.CANCELLED,
            ),
            TxSession(
                "S-108",
                "Lash Fill",
                "A. Villanueva",
                "QC Central",
                "Wed",
                "Ann Reyes",
                false,
                700,
                TxSessionStatus.COMPLETED,
            ),
            TxSession(
                "S-109",
                "Skin Consultation",
                "J. Ocampo",
                "QC Central",
                "Wed",
                "Joy Cruz",
                false,
                500,
                TxSessionStatus.PENDING,
            ),
            TxSession(
                "S-110",
                "Swedish Massage",
                "M. Aquino",
                "Laguna Tour Stop 3",
                "Thu",
                "Ann Reyes",
                false,
                1100,
                TxSessionStatus.PENDING,
            ),
            TxSession(
                "S-111",
                "Haircut",
                "walk-in guest",
                "Tondo Medical Mission",
                "Thu",
                "Joy Cruz",
                true,
                350,
                TxSessionStatus.PENDING,
            ),
            TxSession(
                "S-112",
                "Brow Shape",
                "R. Torres",
                "QC Central",
                "Thu",
                "Ann Reyes",
                false,
                450,
                TxSessionStatus.COMPLETED,
                voided = true,
                voidReason = "Double-booked chair",
            ),
            TxSession(
                "S-113",
                "Signature Facial",
                "L. Garcia",
                "QC Central",
                "Fri",
                "Joy Cruz",
                false,
                1500,
                TxSessionStatus.PENDING,
            ),
            TxSession(
                "S-114",
                "Hot Stone Massage",
                "A. Villanueva",
                "QC Central",
                "Fri",
                "Ann Reyes",
                false,
                1600,
                TxSessionStatus.PENDING,
            ),
        )
    val clients =
        mutableStateListOf(
            TxClient("C-01", "A. Villanueva", "F", 34, "QC Central", 0),
            TxClient("C-02", "J. Ocampo", "M", 41, "QC Central", 1),
            TxClient("C-03", "M. Aquino", "F", 29, "Laguna Tour Stop 3", 0),
            TxClient("C-04", "R. Torres", "F", 52, "QC Central", 0),
            TxClient("C-05", "L. Garcia", "F", 37, "QC Central", 1),
            TxClient("C-06", "D. Mendoza", "M", 45, "Tondo Medical Mission", 0),
            TxClient("C-07", "S. Lim", "F", 26, "QC Central", 0),
            TxClient("C-08", "P. Navarro", "F", 61, "Laguna Tour Stop 3", 0),
        )
    val remittances =
        mutableStateListOf(
            TxRemittance("R-SES-0911", TxRemitKind.SESSION, "QC Central / Fri Sep 11", 18400, TxRemitState.DRAFT),
            TxRemittance("R-PRD-0911", TxRemitKind.PRODUCT, "QC Central / Fri Sep 11", 6250, TxRemitState.DRAFT),
        )
    val payouts =
        mutableStateListOf(
            TxPayout("P-01", "Ann Reyes", "PRACTITIONER", 7, 4200, paid = true),
            TxPayout("P-02", "Joy Cruz", "COORDINATOR", 5, 3150),
            TxPayout("P-03", "Mia Santos", "MANAGER", 2, 2850),
            TxPayout("P-04", "Rob Dela Cruz", "ACCOUNTANT", 0, 2400, paid = true),
        )
    val notices =
        mutableStateListOf(
            TxNotice(
                "N-1",
                "Friday close is ready",
                "Wins, misses and payouts for Sep 7-11 are tallied.",
                read = false,
            ),
            TxNotice(
                "N-2",
                "Relief invite: Laguna Tour",
                "Sat Sep 12 evening shift needs one practitioner.",
                read = false,
            ),
            TxNotice("N-3", "Snapshot sealed", "SESSION remittance for Thu Sep 10 was sealed at close.", read = true),
            TxNotice("N-4", "No-show follow-up", "M. Aquino (Tue hair color) is queued for rebooking.", read = true),
            TxNotice(
                "N-5",
                "Welcome to text-only",
                "Every screen is plain monospace text. No images ship here.",
                read = true,
            ),
        )
    val ledger =
        mutableStateListOf(
            TxAudit(2, "Fri 08:00", "system", "BRANCH_DAY", "QC Central / Fri Sep 11 opened", null),
            TxAudit(1, "Fri 08:00", "system", "SESSION", "prototype seeded 14 sessions", null),
        )
    val invites =
        mutableStateListOf(
            TxInvite("I-1", "Laguna Tour Stop 3", "Sat Sep 12 / evening"),
            TxInvite("I-2", "Tondo Medical Mission", "Sat Sep 12 / morning"),
        )
    val reliefAsks =
        mutableStateListOf(
            TxReliefAsk("Q-1", "Ann Reyes", "Fri Sep 11 / night cover", mine = true),
            TxReliefAsk("Q-2", "D. Mendoza", "Sun Sep 13 / swap", mine = false),
        )

    var currentUserId by mutableStateOf<String?>(null)
    var branchId by mutableStateOf("B-QC")
    var dayStatus by mutableStateOf(TxDayState.OPEN)
    var clockedIn by mutableStateOf(false)
    private var auditSeq = 2
    private var sessionSeq = 114
    private var askSeq = 2

    val currentUser: TxUser? get() = users.firstOrNull { it.id == currentUserId }
    val branch: TxBranch get() = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun capabilitiesOf(role: TxRole): String =
        when (role) {
            TxRole.ONBOARDING -> "LOCKED: zero capabilities until a MANAGER grants a role."
            TxRole.PRACTITIONER -> "sessions(own) clock-in/out relief-duty"
            TxRole.COORDINATOR -> "sessions clients branch-day relief-invites"
            TxRole.MANAGER -> "coordinator + users/roles void remittance-submit"
            TxRole.ACCOUNTANT -> "finance-read remittance-draft/submit payout-envelopes"
        }

    private fun audit(
        action: String,
        target: String,
        reason: String? = null,
    ) {
        auditSeq += 1
        ledger.add(
            0,
            TxAudit(auditSeq, "Fri 15:0${auditSeq % 10}", currentUser?.name ?: "kiosk", action, target, reason),
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
        role: TxRole,
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

    fun setDay(status: TxDayState) {
        dayStatus = status
        audit("BRANCH_DAY", "${branch.name} -> ${status.label}")
    }

    fun setSessionStatus(
        id: String,
        next: TxSessionStatus,
    ): String? {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return "Session not found."
        val cur = sessions[i]
        if (cur.walkIn && (next == TxSessionStatus.NO_SHOW || next == TxSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        if (cur.status != TxSessionStatus.PENDING) return "Only PENDING sessions change status."
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
            TxSession(
                id,
                service.trim(),
                "walk-in guest",
                branch.name,
                "Fri",
                practitioner,
                true,
                600,
                TxSessionStatus.PENDING,
            ),
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
        if (cur.state != TxRemitState.DRAFT) return
        remittances[i] = cur.copy(state = TxRemitState.SUBMITTED, snapshotId = "SNAP-${cur.id}")
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
        if (cur.state != TxRemitState.SUBMITTED) return "Only submitted snapshots can be undone."
        remittances[i] = cur.copy(state = TxRemitState.UNDONE, undoReason = reason)
        audit("REMIT_UNDO", id, reason)
        return null
    }

    fun markPaid(id: String) {
        val i = payouts.indexOfFirst { it.id == id }
        if (i < 0) return
        val cur = payouts[i]
        payouts[i] = cur.copy(paid = !cur.paid)
        audit(if (cur.paid) "PAYOUT_REOPEN" else "PAYOUT_PAID", "${cur.staff} ${txPeso(cur.share)}")
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
        reliefAsks[i] =
            cur.copy(
                state =
                    if (grant) {
                        "GRANTED"
                    } else if (cur.mine) {
                        "WITHDRAWN"
                    } else {
                        "DENIED"
                    },
            )
        audit(if (grant) "RELIEF_GRANT" else "RELIEF_CLOSE", "${cur.by} ${cur.shift}")
    }

    fun raiseAsk(shift: String): String? {
        if (shift.isBlank()) return "Describe the shift cover needed."
        askSeq += 1
        reliefAsks.add(TxReliefAsk("Q-$askSeq", currentUser?.name ?: "kiosk", shift.trim(), mine = true))
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

    fun weekCompleted(): List<TxSession> = sessions.filter { it.status == TxSessionStatus.COMPLETED && !it.voided }

    fun weekMisses(): List<TxSession> =
        sessions.filter {
            it.status == TxSessionStatus.NO_SHOW || it.status == TxSessionStatus.CANCELLED || it.voided
        }

    fun daySummary(): List<Pair<String, Pair<Int, Int>>> {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        return days.map { d ->
            val done = sessions.count { it.day == d && it.status == TxSessionStatus.COMPLETED && !it.voided }
            val total = sessions.count { it.day == d }
            d to (done to total)
        }
    }
}
