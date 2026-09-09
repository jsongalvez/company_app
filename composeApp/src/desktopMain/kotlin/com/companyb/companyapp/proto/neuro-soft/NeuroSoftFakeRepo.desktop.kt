package com.companyb.companyapp.proto.neurosoft

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class SoftDayStatus { OPEN, PAST, REMITTED }

enum class SoftSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class SoftRemitKind { SESSION, PRODUCT }

enum class SoftRemitStatus { DRAFT, SUBMITTED }

data class SoftBranch(
    val id: String,
    val name: String,
    val kind: String,
    val softNote: String,
)

data class SoftUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class SoftSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: SoftSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class SoftClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class SoftNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class SoftAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class SoftRemittance(
    val id: String,
    val kind: SoftRemitKind,
    val status: SoftRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class SoftReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class NeuroSoftFakeRepo {
    val branches = mutableStateListOf(
        SoftBranch("b1", "Cloud Clinic", "CLINIC", "Soft morning light, pressed panels"),
        SoftBranch("b2", "Drift Tour", "PROVINCIAL_TOUR", "Off-site calm, low tactile hum"),
        SoftBranch("b3", "Halo Mission", "MEDICAL_MISSION", "Quiet outreach, deep cushion"),
    )

    val users = mutableStateListOf(
        SoftUser("u1", "Maya Reyes", "Practitioner", "b1"),
        SoftUser("u2", "Cory Cruz", "Coordinator", "b1"),
        SoftUser("u3", "Sam Villanueva", "MANAGER", "b2"),
        SoftUser("u4", "Lex Tan", "Accountant", "b1"),
        SoftUser("u5", "Nilo New", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        SoftSession("s1", "Aria Ann", "b1", SoftSessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
        SoftSession("s2", "Walk-in Joy", "b1", SoftSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
        SoftSession("s3", "Beto Santos", "b1", SoftSessionStatus.COMPLETED, "Filling", 1200, false, time = "08:00"),
        SoftSession("s4", "Pia Dela Cruz", "b2", SoftSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
        SoftSession("s5", "Mika Aquino", "b1", SoftSessionStatus.CANCELLED, "Extraction", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        SoftClient("c1", "Aria Ann", "Second visit, prefers quiet room", hasPending = true),
        SoftClient("c2", "Beto Santos", "Completed filling, recall in 6 months", hasPending = false),
        SoftClient("c3", "Pia Dela Cruz", "No-show twice, needs a gentle call", hasPending = false),
        SoftClient("c4", "Sunny Ramos", "New chart, anonymized for the board", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        SoftNote("n1", "Relief invite: Drift Tour", "Sam invites Maya to cover Sat 09:00-13:00.", day = "Sat"),
        SoftNote("n2", "Remittance pressed", "Cloud Clinic SESSION draft submitted, snapshot #18.", read = true, day = "Fri"),
        SoftNote("n3", "Welcome to neuro-soft", "Everything here is cushion and calm. Press around.", day = "Today"),
    )

    val audits = mutableStateListOf(
        SoftAudit("a1", "Cory Cruz", "SUBMIT", "Remittance #18", "Fri 17:02", "End-of-day press"),
        SoftAudit("a2", "Maya Reyes", "VOID", "Session s5", "Fri 11:20", "Client asked to rebook"),
    )

    val remittances = mutableStateListOf(
        SoftRemittance("r1", SoftRemitKind.SESSION, SoftRemitStatus.DRAFT, 3200, "Today"),
        SoftRemittance("r2", SoftRemitKind.PRODUCT, SoftRemitStatus.DRAFT, 1450, "Today"),
        SoftRemittance(
            "r0",
            SoftRemitKind.SESSION,
            SoftRemitStatus.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "#18 pressed",
            submittedAt = "Fri 17:02",
        ),
    )

    val relief = mutableStateListOf(
        SoftReliefItem("f1", "Request", "Maya Reyes", "Cloud Clinic", "Sat", "Needs cover 09:00-13:00"),
        SoftReliefItem("f2", "Invite", "Sam Villanueva", "Drift Tour", "Sun", "Pop-up needs one more calm hand"),
        SoftReliefItem("f3", "Duty", "Cory Cruz", "Cloud Clinic", "Today", "Front-desk soft shift"),
    )

    val dayStatus = mutableStateOf(SoftDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            SoftAudit("a${auditSeq.value}", me.value.name, action, record, "Today soft hour", reason),
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
            stamp("UNVOID", "Session $id", "Pressed back into the books")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = SoftSessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "Settled softly")
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-soft"
        sessions.add(SoftSession(id, name, branchId.value, SoftSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name pressed in softly")
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
            remittances[i] = r.copy(status = SoftRemitStatus.SUBMITTED, snapshot = "#soft-${r.id}", submittedAt = "Today")
            stamp("SUBMIT", "Remittance ${r.id}", "Pressed and sealed")
            logInfo("NeuroSoft", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = SoftRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            SoftReliefItem("f${relief.size + 1}-soft", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "Soft shift press")
    }

    fun reset() {
        dayStatus.value = SoftDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("NeuroSoft", "reset demo data")
    }
}
