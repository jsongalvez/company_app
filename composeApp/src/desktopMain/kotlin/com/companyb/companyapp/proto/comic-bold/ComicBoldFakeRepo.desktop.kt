package com.companyb.companyapp.proto.comicbold

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class BoomDayStatus { OPEN, PAST, REMITTED }

enum class BoomSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BoomRemitKind { SESSION, PRODUCT }

enum class BoomRemitStatus { DRAFT, SUBMITTED }

data class BoomBranch(
    val id: String,
    val name: String,
    val kind: String,
    val powNote: String,
)

data class BoomUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class BoomSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: BoomSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class BoomClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class BoomNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class BoomAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class BoomRemittance(
    val id: String,
    val kind: BoomRemitKind,
    val status: BoomRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class BoomReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class ComicBoldFakeRepo {
    val branches = mutableStateListOf(
        BoomBranch("b1", "Powerville HQ", "Flagship", "Halftone hero hall!"),
        BoomBranch("b2", "Zap Wagon Tour", "Mobile", "Action lines on wheels!"),
        BoomBranch("b3", "Kapow Outpost", "Outreach", "Speech bubbles in the wild!"),
    )

    val users = mutableStateListOf(
        BoomUser("u1", "Dyna Reyes", "Practitioner", "b1"),
        BoomUser("u2", "Bolt Cruz", "Coordinator", "b1"),
        BoomUser("u3", "Captain Villa", "MANAGER", "b2"),
        BoomUser("u4", "Ledger Lane", "Accountant", "b1"),
        BoomUser("u5", "Rookie Bubble", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        BoomSession("s1", "Cherry Ann", "b1", BoomSessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
        BoomSession("s2", "Walk-in Wham", "b1", BoomSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
        BoomSession("s3", "Berry Santos", "b1", BoomSessionStatus.COMPLETED, "Filling", 1200, false, time = "08:00"),
        BoomSession("s4", "Peach Dela Cruz", "b2", BoomSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
        BoomSession("s5", "Mint Aquino", "b1", BoomSessionStatus.CANCELLED, "Extraction", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        BoomClient("c1", "Cherry Ann", "Second visit, collects POW stickers", hasPending = true),
        BoomClient("c2", "Berry Santos", "Completed filling, recall in 6 months", hasPending = false),
        BoomClient("c3", "Peach Dela Cruz", "No-show twice, needs a hero call", hasPending = false),
        BoomClient("c4", "Sunny Ramos", "New chart, masked for the wallboard", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        BoomNote("n1", "ZAP! Relief invite: Zap Wagon", "Captain Villa invites Dyna to cover Sat 09:00-13:00.", day = "Sat"),
        BoomNote("n2", "POW! Remittance sealed", "Powerville SESSION draft submitted, snapshot #77.", read = true, day = "Fri"),
        BoomNote("n3", "BAM! Welcome to COMIC-BOLD", "Everything here pops off the page. Tap every panel!", day = "Today"),
    )

    val audits = mutableStateListOf(
        BoomAudit("a1", "Bolt Cruz", "SUBMIT", "Remittance #77", "Fri 17:02", "End-of-day KRAKOOM"),
        BoomAudit("a2", "Dyna Reyes", "VOID", "Session s5", "Fri 11:20", "Client asked to rebook"),
    )

    val remittances = mutableStateListOf(
        BoomRemittance("r1", BoomRemitKind.SESSION, BoomRemitStatus.DRAFT, 3200, "Today"),
        BoomRemittance("r2", BoomRemitKind.PRODUCT, BoomRemitStatus.DRAFT, 1450, "Today"),
        BoomRemittance(
            "r0",
            BoomRemitKind.SESSION,
            BoomRemitStatus.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "#77 sealed",
            submittedAt = "Fri 17:02",
        ),
    )

    val relief = mutableStateListOf(
        BoomReliefItem("f1", "Request", "Dyna Reyes", "Powerville HQ", "Sat", "Need cover 09:00-13:00, bring capes!"),
        BoomReliefItem("f2", "Invite", "Captain Villa", "Zap Wagon Tour", "Sun", "Pop-up needs one more hero!"),
        BoomReliefItem("f3", "Duty", "Bolt Cruz", "Powerville HQ", "Today", "Front-desk action-line duty!"),
    )

    val dayStatus = mutableStateOf(BoomDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            BoomAudit("a${auditSeq.value}", me.value.name, action, record, "Today, splash page", reason),
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
            stamp("UNVOID", "Session $id", "Back in this issue!")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = BoomSessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "POW! Finished!")
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-boom"
        sessions.add(BoomSession(id, name, branchId.value, BoomSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name burst through the door!")
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
            remittances[i] = r.copy(status = BoomRemitStatus.SUBMITTED, snapshot = "#boom-${r.id}", submittedAt = "Today")
            stamp("SUBMIT", "Remittance ${r.id}", "KRAKOOM! Sealed!")
            logInfo("ComicBold", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = BoomRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            BoomReliefItem("f${relief.size + 1}-boom", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "Hero shift beat!")
    }

    fun reset() {
        dayStatus.value = BoomDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("ComicBold", "reset demo data")
    }
}
