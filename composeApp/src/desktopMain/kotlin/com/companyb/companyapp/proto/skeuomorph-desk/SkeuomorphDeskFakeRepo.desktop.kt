package com.companyb.companyapp.proto.skeuomorphdesk

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class DeskDayStatus { OPEN, PAST, REMITTED }

enum class DeskSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class DeskRemitKind { SESSION, PRODUCT }

enum class DeskRemitStatus { DRAFT, SUBMITTED }

data class DeskBranch(
    val id: String,
    val name: String,
    val kind: String,
    val drawer: String,
)

data class DeskUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class DeskSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: DeskSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class DeskClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class DeskNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class DeskAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class DeskRemittance(
    val id: String,
    val kind: DeskRemitKind,
    val status: DeskRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class DeskReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class SkeuomorphDeskFakeRepo {
    val branches = mutableStateListOf(
        DeskBranch("b1", "Mahogany HQ", "CLINIC", "Drawer A / ₱18,400"),
        DeskBranch("b2", "Cedar Tour", "PROVINCIAL_TOUR", "Cash box / ₱6,150"),
        DeskBranch("b3", "Teak Mission", "MEDICAL_MISSION", "Mission tin / ₱0"),
    )

    val users = mutableStateListOf(
        DeskUser("u1", "J. Reyes", "Practitioner", "b1"),
        DeskUser("u2", "M. Cruz", "Coordinator", "b1"),
        DeskUser("u3", "S. Villanueva", "MANAGER", "b2"),
        DeskUser("u4", "L. Tan", "Accountant", "b1"),
        DeskUser("u5", "New Hand", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        DeskSession("s1", "Dela Pena, A.", "b1", DeskSessionStatus.PENDING, "Adjustment", 800, false, time = "09:00"),
        DeskSession("s2", "Walk-in 004", "b1", DeskSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
        DeskSession("s3", "Santos, B.", "b1", DeskSessionStatus.COMPLETED, "Rehab", 1200, false, time = "08:00"),
        DeskSession("s4", "Dela Cruz, P.", "b2", DeskSessionStatus.NO_SHOW, "Eval", 2500, false, time = "10:00"),
        DeskSession("s5", "Aquino, M.", "b1", DeskSessionStatus.CANCELLED, "Follow-up", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        DeskClient("c1", "Dela Pena, A.", "2nd visit / left knee", hasPending = true),
        DeskClient("c2", "Santos, B.", "Rehab done / recall 6 mo", hasPending = false),
        DeskClient("c3", "Dela Cruz, P.", "No-show x2 / call first", hasPending = false),
        DeskClient("c4", "File 0044", "Anonymized / F, 52 / kept for reports", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        DeskNote("n1", "Relief invite / Cedar Tour", "S. Villanueva asks J. Reyes to cover Sat 09:00-13:00.", day = "Sat"),
        DeskNote("n2", "Remittance filed", "Mahogany HQ session draft filed. Folio #07.", read = true, day = "Fri"),
        DeskNote("n3", "Desk memo", "Everything on this desk is pretend. Stamp freely.", day = "Today"),
    )

    val audits = mutableStateListOf(
        DeskAudit("a1", "M. Cruz", "INSERT", "remittance R1", "Fri 17:02", "Filed session draft"),
        DeskAudit("a2", "J. Reyes", "UPDATE", "session S3", "Fri 08:40", "Marked COMPLETED"),
    )

    val remittances = mutableStateListOf(
        DeskRemittance("R1", DeskRemitKind.SESSION, DeskRemitStatus.SUBMITTED, 18400, "Fri", "Folio-07", "Fri 17:02"),
        DeskRemittance("R2", DeskRemitKind.PRODUCT, DeskRemitStatus.DRAFT, 3200, "Sat", "", ""),
    )

    val reliefBoard = mutableStateListOf(
        DeskReliefItem("r1", "Request", "J. Reyes", "Cedar Tour", "Sat", "Needs edit access / cover gap"),
        DeskReliefItem("r2", "Invite", "S. Villanueva", "Cedar Tour", "Sat", "Invites J. Reyes 09:00-13:00"),
        DeskReliefItem("r3", "Duty", "L. Tan", "Mahogany HQ", "Fri", "View-only until granted"),
    )

    val branchId = mutableStateOf("b1")
    val clockedIn = mutableStateOf(false)
    val dayStatus = mutableStateOf(DeskDayStatus.OPEN)
    val stampCount = mutableStateOf(7)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun fileEntry(actor: String, action: String, record: String, reason: String) {
        stampCount.value += 1
        val t = "Folio ${stampCount.value}:0${stampCount.value % 10}"
        audits.add(0, DeskAudit("a${stampCount.value}-${audits.size}", actor, action, record, t, reason))
        logInfo("SkeuomorphDesk", "$action $record ($reason)")
    }

    fun clockIn() {
        clockedIn.value = true
        fileEntry("J. Reyes", "CLOCK-IN", branchName(branchId.value), "Shift start")
    }

    fun clockOut() {
        clockedIn.value = false
        fileEntry("J. Reyes", "CLOCK-OUT", branchName(branchId.value), "Shift end")
    }

    fun updateSession(id: String, op: (DeskSession) -> DeskSession, record: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            sessions[i] = op(sessions[i])
            fileEntry("J. Reyes", "UPDATE", record, reason)
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-w"
        sessions.add(DeskSession(id, name, branchId.value, DeskSessionStatus.PENDING, type, price, true, time = "Now"))
        fileEntry("J. Reyes", "INSERT", "session $id", "Walk-in entered in ledger")
    }

    fun toggleAnonymized(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) {
            val c = clients[i]
            clients[i] = c.copy(anonymized = !c.anonymized)
            fileEntry("M. Cruz", "UPDATE", "client $id", if (c.anonymized) "Revealed" else "Anonymized")
        }
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            val snap = "Folio-${10 + remittances.size + stampCount.value}"
            remittances[i] = r.copy(status = DeskRemitStatus.SUBMITTED, snapshot = snap, submittedAt = "Today")
            notes.add(0, DeskNote("n${notes.size + 1}", "Remittance filed", "${r.kind} $id filed. $snap.", day = "Today"))
            fileEntry("M. Cruz", "SUBMIT", "remittance $id", "Snapshot $snap sealed")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = DeskRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            fileEntry("M. Cruz", "UNDO", "remittance $id", "48h window / $reason")
        }
    }

    fun markRead(id: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = notes[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notes.indices) notes[i] = notes[i].copy(read = true)
    }

    fun settleRelief(id: String, verdict: String) {
        reliefBoard.removeAll { it.id == id }
        fileEntry("S. Villanueva", "UPDATE", "relief $id", verdict)
    }
}
