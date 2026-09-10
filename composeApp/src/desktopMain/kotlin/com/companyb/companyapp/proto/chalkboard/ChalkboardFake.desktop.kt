package com.companyb.companyapp.proto.chalkboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class ChalkDayStatus { OPEN, PAST, REMITTED }

enum class ChalkSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class ChalkRemitKind { SESSION, PRODUCT }

enum class ChalkRemitStatus { DRAFT, SUBMITTED }

data class ChalkBranch(
    val id: String,
    val name: String,
    val kind: String,
    val roomNote: String,
)

data class ChalkUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class ChalkSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: ChalkSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class ChalkClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class ChalkNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class ChalkAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class ChalkRemittance(
    val id: String,
    val kind: ChalkRemitKind,
    val status: ChalkRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class ChalkReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class ChalkboardFakeRepo {
    val branches = mutableStateListOf(
        ChalkBranch("b1", "Homeroom Clinic", "CLINIC", "Room 1 — main slate wall"),
        ChalkBranch("b2", "Field-Trip Tour", "PROVINCIAL_TOUR", "Room 2 — chalk cart on wheels"),
        ChalkBranch("b3", "Open-Day Mission", "MEDICAL_MISSION", "Room 3 — gym wall, free chairs"),
    )

    val users = mutableStateListOf(
        ChalkUser("u1", "Ms. Reyes", "Practitioner", "b1"),
        ChalkUser("u2", "Mr. Cruz", "Coordinator", "b1"),
        ChalkUser("u3", "Capt. Villanueva", "MANAGER", "b2"),
        ChalkUser("u4", "Ms. Tan", "Accountant", "b1"),
        ChalkUser("u5", "New Enrollee", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        ChalkSession("s1", "Ana Ramos", "b1", ChalkSessionStatus.PENDING, "First sitting", 800, false, time = "Period 1 · 09:00"),
        ChalkSession("s2", "Walk-in pupil", "b1", ChalkSessionStatus.PENDING, "Drop-in check", 500, true, time = "Period 2 · 09:30"),
        ChalkSession("s3", "Ben Santos", "b1", ChalkSessionStatus.COMPLETED, "Second sitting", 1200, false, time = "Period 1 · 08:00"),
        ChalkSession("s4", "Cora Dela Cruz", "b2", ChalkSessionStatus.NO_SHOW, "Filed absence", 2500, false, time = "Period 3 · 10:00"),
        ChalkSession("s5", "Dan Aquino", "b1", ChalkSessionStatus.CANCELLED, "Erased sitting", 1500, false, time = "Period 4 · 11:00"),
    )

    val clients = mutableStateListOf(
        ChalkClient("c1", "Ana Ramos", "Second sitting, sits front row", hasPending = true),
        ChalkClient("c2", "Ben Santos", "Finished second sitting, recall next term", hasPending = false),
        ChalkClient("c3", "Cora Dela Cruz", "Absent twice, send a kind note home", hasPending = false),
        ChalkClient("c4", "Sam Reyes", "Record anonymized for the wall display", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        ChalkNote("n1", "Relief invite: Field-Trip Tour", "Capt. Villanueva invites Ms. Reyes to cover Sat at Field-Trip Tour.", day = "Sat"),
        ChalkNote("n2", "Remittance chalked in", "Homeroom Clinic SESSION draft submitted, snapshot #17.", read = true, day = "Fri"),
        ChalkNote("n3", "Welcome to the chalkboard", "Everything here is chalk dust. Tap every corner.", day = "Today"),
    )

    val audits = mutableStateListOf(
        ChalkAudit("a1", "Mr. Cruz", "SUBMIT", "Remittance #17", "Fri 17:02", "End-of-day chalk-in"),
        ChalkAudit("a2", "Ms. Reyes", "VOID", "Session s5", "Fri 11:20", "Pupil asked to rebook"),
    )

    val remittances = mutableStateListOf(
        ChalkRemittance("r1", ChalkRemitKind.SESSION, ChalkRemitStatus.DRAFT, 3200, "Today"),
        ChalkRemittance("r2", ChalkRemitKind.PRODUCT, ChalkRemitStatus.DRAFT, 1450, "Today"),
        ChalkRemittance(
            "r0",
            ChalkRemitKind.SESSION,
            ChalkRemitStatus.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "#17 sealed",
            submittedAt = "Fri 17:02",
        ),
    )

    val relief = mutableStateListOf(
        ChalkReliefItem("f1", "Request", "Ms. Reyes", "Homeroom Clinic", "Sat", "Needs cover Period 1-3"),
        ChalkReliefItem("f2", "Invite", "Capt. Villanueva", "Field-Trip Tour", "Sun", "Tour cart needs one more hand"),
        ChalkReliefItem("f3", "Duty", "Mr. Cruz", "Homeroom Clinic", "Today", "Front-desk chalk duty"),
    )

    val dayStatus = mutableStateOf(ChalkDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            ChalkAudit("a${auditSeq.value}", me.value.name, action, record, "Today after class", reason),
        )
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = true, voidReason = reason)
            stamp("VOID", "Session $id", reason)
        }
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = false, voidReason = "")
            stamp("UNVOID", "Session $id", "Chalked back in")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = ChalkSessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "Lesson done, star on the wall")
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-chalk"
        sessions.add(ChalkSession(id, name, branchId.value, ChalkSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name walked in off the hall")
    }

    fun toggleAnonymized(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) clients[i] = clients[i].copy(anonymized = !clients[i].anonymized)
    }

    fun markRead(id: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = notes[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notes.indices) notes[i] = notes[i].copy(read = true)
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = ChalkRemitStatus.SUBMITTED, snapshot = "#chalk-${r.id}", submittedAt = "Today")
            stamp("SUBMIT", "Remittance ${r.id}", "Chalked into the ledger")
            logInfo("Chalkboard", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = ChalkRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            ChalkReliefItem("f${relief.size + 1}-chalk", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "Chalked the time book")
    }

    fun reset() {
        dayStatus.value = ChalkDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("Chalkboard", "reset demo data")
    }
}
