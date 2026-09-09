package com.companyb.companyapp.proto.glasspanels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class GpDayStatus { OPEN, PAST, REMITTED }

enum class GpSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class GpRemitKind { SESSION, PRODUCT }

enum class GpRemitStatus { DRAFT, SUBMITTED }

data class GpBranch(
    val id: String,
    val name: String,
    val kind: String,
    val shimmer: String,
)

data class GpUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class GpSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: GpSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class GpClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class GpNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class GpAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class GpRemittance(
    val id: String,
    val kind: GpRemitKind,
    val status: GpRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class GpReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class GlassPanelsFakeRepo {
    val branches = mutableStateListOf(
        GpBranch("b1", "Lumen Flagship", "CLINIC", "sheet 01 / harbor light"),
        GpBranch("b2", "Drift Provincial Tour", "PROVINCIAL_TOUR", "sheet 02 / moving pane"),
        GpBranch("b3", "Halo Medical Mission", "MEDICAL_MISSION", "sheet 03 / open air"),
    )

    val users = mutableStateListOf(
        GpUser("u1", "J. Reyes", "Practitioner", "b1"),
        GpUser("u2", "M. Cruz", "Coordinator", "b1"),
        GpUser("u3", "S. Villanueva", "Manager", "b2"),
        GpUser("u4", "L. Tan", "Accountant", "b1"),
        GpUser("u5", "New Practitioner", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        GpSession("s1", "Dela Pena, A.", "b1", GpSessionStatus.PENDING, "Adjustment", 800, false, time = "09:00"),
        GpSession("s2", "Walk-in 004", "b1", GpSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
        GpSession("s3", "Santos, B.", "b1", GpSessionStatus.COMPLETED, "Rehab", 1200, false, time = "08:00"),
        GpSession("s4", "Dela Cruz, P.", "b2", GpSessionStatus.NO_SHOW, "Eval", 2500, false, time = "10:00"),
        GpSession("s5", "Aquino, M.", "b1", GpSessionStatus.CANCELLED, "Follow-up", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        GpClient("c1", "Dela Pena, A.", "2nd visit / left knee", hasPending = true),
        GpClient("c2", "Santos, B.", "Rehab done / recall 6 mo", hasPending = false),
        GpClient("c3", "Dela Cruz, P.", "No-show x2 / call first", hasPending = false),
        GpClient("c4", "File 0044", "Anonymized / F, 52 / kept for reports", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        GpNote("n1", "Relief invite / Drift Tour", "S. Villanueva wants J. Reyes on Sat 09:00-13:00.", day = "Sat"),
        GpNote("n2", "Remittance sealed", "Lumen Flagship session draft submitted. Snapshot #07.", read = true, day = "Fri"),
        GpNote("n3", "Pane notice", "Everything here is fake. Float freely.", day = "Today"),
    )

    val audits = mutableStateListOf(
        GpAudit("a1", "M. Cruz", "INSERT", "remittance R1", "Fri 17:02", "Sealed session draft"),
        GpAudit("a2", "J. Reyes", "UPDATE", "session S3", "Fri 08:40", "Marked completed"),
    )

    val remittances = mutableStateListOf(
        GpRemittance("R1", GpRemitKind.SESSION, GpRemitStatus.SUBMITTED, 18400, "Fri", "SNAP-07", "Fri 17:02"),
        GpRemittance("R2", GpRemitKind.PRODUCT, GpRemitStatus.DRAFT, 3200, "Sat", "", ""),
    )

    val reliefBoard = mutableStateListOf(
        GpReliefItem("r1", "Request", "J. Reyes", "Drift Provincial Tour", "Sat", "Needs cover / edit access gap"),
        GpReliefItem("r2", "Invite", "S. Villanueva", "Drift Provincial Tour", "Sat", "Invites J. Reyes 09:00-13:00"),
        GpReliefItem("r3", "Duty", "L. Tan", "Lumen Flagship", "Fri", "View-only until granted"),
    )

    val branchId = mutableStateOf("b1")
    val clockedIn = mutableStateOf(false)
    val dayStatus = mutableStateOf(GpDayStatus.OPEN)
    val clock = mutableStateOf(7)
    val me = mutableStateOf("J. Reyes")

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(actor: String, action: String, record: String, reason: String) {
        clock.value += 1
        val t = "Day ${clock.value}:0${clock.value % 10}"
        audits.add(0, GpAudit("a${clock.value}-${audits.size}", actor, action, record, t, reason))
        logInfo("GlassPanels", "$action $record ($reason)")
    }

    fun clockIn() {
        clockedIn.value = true
        stamp(me.value, "CLOCK-IN", branchName(branchId.value), "Shift start")
    }

    fun clockOut() {
        clockedIn.value = false
        stamp(me.value, "CLOCK-OUT", branchName(branchId.value), "Shift end")
    }

    fun updateSession(id: String, op: (GpSession) -> GpSession, record: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            sessions[i] = op(sessions[i])
            stamp(me.value, "UPDATE", record, reason)
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-w"
        sessions.add(GpSession(id, name, branchId.value, GpSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp(me.value, "INSERT", "session $id", "Walk-in logged")
    }

    fun toggleAnonymized(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) {
            val c = clients[i]
            clients[i] = c.copy(anonymized = !c.anonymized)
            stamp("M. Cruz", "UPDATE", "client $id", if (c.anonymized) "Revealed" else "Anonymized")
        }
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            val snap = "SNAP-${10 + remittances.size + clock.value}"
            remittances[i] = r.copy(status = GpRemitStatus.SUBMITTED, snapshot = snap, submittedAt = "Today")
            notes.add(0, GpNote("n${notes.size + 1}", "Remittance sealed", "${r.kind} $id submitted. $snap.", day = "Today"))
            stamp("M. Cruz", "SUBMIT", "remittance $id", "Snapshot $snap frozen")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = GpRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("M. Cruz", "UNDO", "remittance $id", "48h window / $reason")
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
        stamp("S. Villanueva", "UPDATE", "relief $id", verdict)
    }
}
