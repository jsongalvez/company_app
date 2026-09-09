package com.companyb.companyapp.proto.brutalistraw

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class RawDayStatus { OPEN, PAST, REMITTED }

enum class RawSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class RawRemitKind { SESSION, PRODUCT }

enum class RawRemitStatus { DRAFT, SUBMITTED }

data class RawBranch(
    val id: String,
    val name: String,
    val kind: String,
    val slab: String,
)

data class RawUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class RawSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: RawSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class RawClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class RawNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class RawAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class RawRemittance(
    val id: String,
    val kind: RawRemitKind,
    val status: RawRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class RawReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class BrutalistRawFakeRepo {
    val branches = mutableStateListOf(
        RawBranch("b1", "CONCRETE HQ", "CLINIC", "SLAB 01 / POURED 2019"),
        RawBranch("b2", "GRAVEL TOUR", "PROVINCIAL_TOUR", "SLAB 02 / MOBILE FORMWORK"),
        RawBranch("b3", "REBAR MISSION", "MEDICAL_MISSION", "SLAB 03 / FREE POUR"),
    )

    val users = mutableStateListOf(
        RawUser("u1", "J. REYES", "Practitioner", "b1"),
        RawUser("u2", "M. CRUZ", "Coordinator", "b1"),
        RawUser("u3", "S. VILLANUEVA", "MANAGER", "b2"),
        RawUser("u4", "L. TAN", "Accountant", "b1"),
        RawUser("u5", "UNSET HAND", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        RawSession("s1", "DELA PENA, A.", "b1", RawSessionStatus.PENDING, "ADJUSTMENT", 800, false, time = "09:00"),
        RawSession("s2", "WALK-IN 004", "b1", RawSessionStatus.PENDING, "CHECKUP", 500, true, time = "09:30"),
        RawSession("s3", "SANTOS, B.", "b1", RawSessionStatus.COMPLETED, "REHAB", 1200, false, time = "08:00"),
        RawSession("s4", "DELA CRUZ, P.", "b2", RawSessionStatus.NO_SHOW, "EVAL", 2500, false, time = "10:00"),
        RawSession("s5", "AQUINO, M.", "b1", RawSessionStatus.CANCELLED, "FOLLOW-UP", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        RawClient("c1", "DELA PENA, A.", "2ND VISIT / LEFT KNEE", hasPending = true),
        RawClient("c2", "SANTOS, B.", "REHAB DONE / RECALL 6 MO", hasPending = false),
        RawClient("c3", "DELA CRUZ, P.", "NO-SHOW X2 / CALL FIRST", hasPending = false),
        RawClient("c4", "FILE 0044", "ANONYMIZED / F, 52 / KEPT FOR REPORTS", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        RawNote("n1", "RELIEF INVITE / GRAVEL TOUR", "S. VILLANUEVA WANTS J. REYES ON SAT 09:00-13:00.", day = "SAT"),
        RawNote("n2", "REMITTANCE SEALED", "CONCRETE HQ SESSION DRAFT SUBMITTED. SNAPSHOT #07.", read = true, day = "FRI"),
        RawNote("n3", "FORMWORK NOTICE", "EVERYTHING HERE IS FAKE. POUR FREELY.", day = "TODAY"),
    )

    val audits = mutableStateListOf(
        RawAudit("a1", "M. CRUZ", "INSERT", "remittance R1", "FRI 17:02", "SEALED SESSION DRAFT"),
        RawAudit("a2", "J. REYES", "UPDATE", "session S3", "FRI 08:40", "MARKED COMPLETED"),
    )

    val remittances = mutableStateListOf(
        RawRemittance("R1", RawRemitKind.SESSION, RawRemitStatus.SUBMITTED, 18400, "FRI", "SNAP-07", "FRI 17:02"),
        RawRemittance("R2", RawRemitKind.PRODUCT, RawRemitStatus.DRAFT, 3200, "SAT", "", ""),
    )

    val reliefBoard = mutableStateListOf(
        RawReliefItem("r1", "REQUEST", "J. REYES", "GRAVEL TOUR", "SAT", "NEEDS EDIT ACCESS / COVER GAP"),
        RawReliefItem("r2", "INVITE", "S. VILLANUEVA", "GRAVEL TOUR", "SAT", "INVITES J. REYES 09:00-13:00"),
        RawReliefItem("r3", "DUTY", "L. TAN", "CONCRETE HQ", "FRI", "VIEW-ONLY UNTIL GRANTED"),
    )

    val branchId = mutableStateOf("b1")
    val clockedIn = mutableStateOf(false)
    val dayStatus = mutableStateOf(RawDayStatus.OPEN)
    val clock = mutableStateOf(7)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(actor: String, action: String, record: String, reason: String) {
        clock.value += 1
        val t = "DAY ${clock.value}:0${clock.value % 10}"
        audits.add(0, RawAudit("a${clock.value}-${audits.size}", actor, action, record, t, reason))
        logInfo("BrutalistRaw", "$action $record ($reason)")
    }

    fun clockIn() {
        clockedIn.value = true
        stamp("J. REYES", "CLOCK-IN", branchName(branchId.value), "SHIFT START")
    }

    fun clockOut() {
        clockedIn.value = false
        stamp("J. REYES", "CLOCK-OUT", branchName(branchId.value), "SHIFT END")
    }

    fun updateSession(id: String, op: (RawSession) -> RawSession, record: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            sessions[i] = op(sessions[i])
            stamp("J. REYES", "UPDATE", record, reason)
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-w"
        sessions.add(RawSession(id, name, branchId.value, RawSessionStatus.PENDING, type, price, true, time = "NOW"))
        stamp("J. REYES", "INSERT", "session $id", "WALK-IN LOGGED")
    }

    fun toggleAnonymized(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) {
            val c = clients[i]
            clients[i] = c.copy(anonymized = !c.anonymized)
            stamp("M. CRUZ", "UPDATE", "client $id", if (c.anonymized) "REVEALED" else "ANONYMIZED")
        }
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            val snap = "SNAP-${10 + remittances.size + clock.value}"
            remittances[i] = r.copy(status = RawRemitStatus.SUBMITTED, snapshot = snap, submittedAt = "TODAY")
            notes.add(0, RawNote("n${notes.size + 1}", "REMITTANCE SEALED", "${r.kind} $id SUBMITTED. $snap.", day = "TODAY"))
            stamp("M. CRUZ", "SUBMIT", "remittance $id", "SNAPSHOT $snap FROZEN")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = RawRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("M. CRUZ", "UNDO", "remittance $id", "48H WINDOW / $reason")
        }
    }

    fun markRead(id: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = notes[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notes.indices) notes[i] = notes[i].copy(read = true)
    }

    fun dropRelief(id: String, verdict: String) {
        reliefBoard.removeAll { it.id == id }
        stamp("S. VILLANUEVA", "UPDATE", "relief $id", verdict)
    }
}
