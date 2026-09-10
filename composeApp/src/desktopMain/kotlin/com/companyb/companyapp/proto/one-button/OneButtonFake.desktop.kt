package com.companyb.companyapp.proto.onebutton

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

// #852 — one-button fake domain. Local only: no network client, no remote calls,
// no backend, no shared contracts. A seeded single-focus store drives every screen.

enum class ObDayStatus { OPEN, PAST, REMITTED }

enum class ObSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class ObRemitKind { SESSION, PRODUCT }

enum class ObRemitState { DRAFT, SUBMITTED }

enum class ObScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class ObBranch(
    val id: String,
    val name: String,
    val kind: String,
    val focusNote: String,
)

data class ObUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class ObSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: ObSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class ObClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class ObNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class ObAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class ObRemittance(
    val id: String,
    val kind: ObRemitKind,
    val state: ObRemitState,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
    val undoable: Boolean = true,
)

data class ObRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class OneButtonFakeRepo {
    val branches = mutableStateListOf(
        ObBranch("b1", "Solo Clinic", "CLINIC", "One chair, one lamp, one button"),
        ObBranch("b2", "Single-File Tour", "PROVINCIAL_TOUR", "One town per day, no multitasking"),
        ObBranch("b3", "One-Room Mission", "MEDICAL_MISSION", "One queue, served in order"),
    )

    val users = mutableStateListOf(
        ObUser("u1", "Ms. Reyes", "Practitioner", "b1"),
        ObUser("u2", "Mr. Cruz", "Coordinator", "b1"),
        ObUser("u3", "Capt. Villanueva", "MANAGER", "b2"),
        ObUser("u4", "Ms. Tan", "Accountant", "b1"),
        ObUser("u5", "New Enrollee", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        ObSession("s1", "Ana Ramos", "b1", ObSessionStatus.PENDING, "First sitting", 800, false, time = "09:00"),
        ObSession("s2", "Walk-in guest", "b1", ObSessionStatus.PENDING, "Drop-in check", 500, true, time = "09:30"),
        ObSession("s3", "Ben Santos", "b1", ObSessionStatus.COMPLETED, "Second sitting", 1200, false, time = "08:00"),
        ObSession("s4", "Cora Dela Cruz", "b2", ObSessionStatus.NO_SHOW, "Filed absence", 2500, false, time = "10:00"),
        ObSession("s5", "Dan Aquino", "b1", ObSessionStatus.CANCELLED, "Called off", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        ObClient("c1", "Ana Ramos", "First sitting done, recall next week", hasPending = true),
        ObClient("c2", "Ben Santos", "Second sitting finished, no follow-up", hasPending = false),
        ObClient("c3", "Cora Dela Cruz", "Missed twice, send one kind reminder", hasPending = false),
        ObClient(
            "c4",
            "Lobby Display",
            "Record anonymized for the waiting screen",
            hasPending = false,
            anonymized = true,
        ),
    )

    val notes = mutableStateListOf(
        ObNote(
            "n1",
            "Relief invite: Single-File Tour",
            "Capt. Villanueva invites Ms. Reyes to cover Sat.",
            day = "Sat",
        ),
        ObNote(
            "n2",
            "Remittance sealed",
            "Solo Clinic SESSION draft submitted, snapshot #41.",
            read = true,
            day = "Fri",
        ),
        ObNote("n3", "One button, one job", "Everything else waits behind the button. Press it.", day = "Today"),
    )

    val audits = mutableStateListOf(
        ObAudit("a1", "Mr. Cruz", "SUBMIT", "Remittance #41", "Fri 17:02", "End-of-day single press"),
        ObAudit("a2", "Ms. Reyes", "VOID", "Session s5", "Fri 11:20", "Guest asked to rebook"),
    )

    val remittances = mutableStateListOf(
        ObRemittance("r1", ObRemitKind.SESSION, ObRemitState.DRAFT, 3200, "Today"),
        ObRemittance("r2", ObRemitKind.PRODUCT, ObRemitState.DRAFT, 1450, "Today"),
        ObRemittance(
            "r0",
            ObRemitKind.SESSION,
            ObRemitState.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "#41 sealed",
            submittedAt = "Fri 17:02",
        ),
        ObRemittance(
            "r9",
            ObRemitKind.PRODUCT,
            ObRemitState.SUBMITTED,
            6100,
            "Last week",
            snapshot = "#36 sealed",
            submittedAt = "Last Tue 16:40",
            undoable = false,
        ),
    )

    val relief = mutableStateListOf(
        ObRelief("f1", "Request", "Ms. Reyes", "Solo Clinic", "Sat", "Needs cover 09:00-12:00"),
        ObRelief("f2", "Invite", "Capt. Villanueva", "Single-File Tour", "Sun", "Tour needs one more hand"),
        ObRelief("f3", "Duty", "Mr. Cruz", "Solo Clinic", "Today", "Front-desk single shift"),
    )

    val dayStatus = mutableStateOf(ObDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)
    val clientSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun nextPending(): ObSession? = sessions.firstOrNull {
        it.branchId == branchId.value && it.status == ObSessionStatus.PENDING && !it.voided
    }

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            ObAudit("a${auditSeq.value}", me.value.name, action, record, "Today, one press", reason),
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
            stamp("UNVOID", "Session $id", "Pressed back into the queue")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = ObSessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "Single sitting finished")
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-solo"
        sessions.add(ObSession(id, name, branchId.value, ObSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name walked up to the button")
    }

    fun addClient(name: String) {
        clientSeq.value += 1
        clients.add(ObClient("c${clientSeq.value}", name, "Added with one press", hasPending = false))
        stamp("ADD_CLIENT", name, "Single-press intake")
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
        stamp("READ_ALL", "Mailbox", "Cleared in one press")
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(state = ObRemitState.SUBMITTED, snapshot = "#solo-${r.id}", submittedAt = "Today")
            stamp("SUBMIT", "Remittance ${r.id}", "Sealed with one press")
            logInfo("OneButton", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(state = ObRemitState.DRAFT, snapshot = "", submittedAt = "", undoable = true)
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addDraft(kind: ObRemitKind) {
        val id = "r${remittances.size + 1}-solo"
        remittances.add(ObRemittance(id, kind, ObRemitState.DRAFT, 900, "Today"))
        stamp("DRAFT", "Remittance $id", "One more envelope on the pile")
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            ObRelief("f${relief.size + 1}-solo", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        val action = if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT"
        stamp(action, branchName(branchId.value), "One press on the time button")
    }

    fun setDayStatus(status: ObDayStatus) {
        dayStatus.value = status
        stamp("DAY_${status.name}", branchName(branchId.value), "Branch Day remote control")
    }

    fun reset() {
        dayStatus.value = ObDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("OneButton", "reset demo data")
    }
}
