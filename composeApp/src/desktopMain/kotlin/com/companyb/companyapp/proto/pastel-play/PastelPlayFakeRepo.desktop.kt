package com.companyb.companyapp.proto.pastelplay

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class PlayDayStatus { OPEN, PAST, REMITTED }

enum class PlaySessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class PlayRemitKind { SESSION, PRODUCT }

enum class PlayRemitStatus { DRAFT, SUBMITTED }

data class PlayBranch(
    val id: String,
    val name: String,
    val kind: String,
    val colorNote: String,
)

data class PlayUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class PlaySession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: PlaySessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class PlayClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class PlayNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class PlayAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class PlayRemittance(
    val id: String,
    val kind: PlayRemitKind,
    val status: PlayRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class PlayReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class PastelPlayFakeRepo {
    val branches = mutableStateListOf(
        PlayBranch("b1", "Blush Clinic", "Flagship", "Candy-pink playroom"),
        PlayBranch("b2", "Mint Tour", "Mobile", "Mint-green pop-up tour"),
        PlayBranch("b3", "Lilac Mission", "Outreach", "Lilac outreach tent"),
    )

    val users = mutableStateListOf(
        PlayUser("u1", "Poppy Reyes", "Practitioner", "b1"),
        PlayUser("u2", "Mint Cruz", "Coordinator", "b1"),
        PlayUser("u3", "Sky Villanueva", "MANAGER", "b2"),
        PlayUser("u4", "Lemon Tan", "Accountant", "b1"),
        PlayUser("u5", "Bubbles New", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        PlaySession("s1", "Cherry Ann", "b1", PlaySessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
        PlaySession("s2", "Walk-in cutie", "b1", PlaySessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
        PlaySession("s3", "Berry Santos", "b1", PlaySessionStatus.COMPLETED, "Filling", 1200, false, time = "08:00"),
        PlaySession("s4", "Peach Dela Cruz", "b2", PlaySessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
        PlaySession("s5", "Mint Aquino", "b1", PlaySessionStatus.CANCELLED, "Extraction", 1500, false, time = "11:00"),
    )

    val clients = mutableStateListOf(
        PlayClient("c1", "Cherry Ann", "Second visit, loves stickers", hasPending = true),
        PlayClient("c2", "Berry Santos", "Completed filling, recall in 6 months", hasPending = false),
        PlayClient("c3", "Peach Dela Cruz", "No-show twice, needs a gentle call", hasPending = false),
        PlayClient("c4", "Sunny Ramos", "New chart, anonymized for the wallboard", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        PlayNote("n1", "Relief invite: Mint Tour", "Sky invites Poppy to cover Sat 09:00-13:00.", day = "Sat"),
        PlayNote("n2", "Remittance sealed", "Blush Clinic SESSION draft submitted, snapshot #42.", read = true, day = "Fri"),
        PlayNote("n3", "Welcome to pastel-play", "Everything here is candy and confetti. Tap around!", day = "Today"),
    )

    val audits = mutableStateListOf(
        PlayAudit("a1", "Mint Cruz", "SUBMIT", "Remittance #42", "Fri 17:02", "End-of-day seal"),
        PlayAudit("a2", "Poppy Reyes", "VOID", "Session s5", "Fri 11:20", "Client asked to rebook"),
    )

    val remittances = mutableStateListOf(
        PlayRemittance("r1", PlayRemitKind.SESSION, PlayRemitStatus.DRAFT, 3200, "Today"),
        PlayRemittance("r2", PlayRemitKind.PRODUCT, PlayRemitStatus.DRAFT, 1450, "Today"),
        PlayRemittance(
            "r0",
            PlayRemitKind.SESSION,
            PlayRemitStatus.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "#42 sealed",
            submittedAt = "Fri 17:02",
        ),
    )

    val relief = mutableStateListOf(
        PlayReliefItem("f1", "Request", "Poppy Reyes", "Blush Clinic", "Sat", "Needs cover 09:00-13:00"),
        PlayReliefItem("f2", "Invite", "Sky Villanueva", "Mint Tour", "Sun", "Pop-up needs one more smile"),
        PlayReliefItem("f3", "Duty", "Mint Cruz", "Blush Clinic", "Today", "Front-desk confetti duty"),
    )

    val dayStatus = mutableStateOf(PlayDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            PlayAudit("a${auditSeq.value}", me.value.name, action, record, "Today playtime", reason),
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
            stamp("UNVOID", "Session $id", "Back on the playboard")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = PlaySessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "Sticker earned")
        }
    }

    fun addWalkIn(name: String, type: String, price: Int) {
        val id = "s${sessions.size + 1}-play"
        sessions.add(PlaySession(id, name, branchId.value, PlaySessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name walked in smiling")
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
            remittances[i] = r.copy(status = PlayRemitStatus.SUBMITTED, snapshot = "#play-${r.id}", submittedAt = "Today")
            stamp("SUBMIT", "Remittance ${r.id}", "Sealed with a sticker")
            logInfo("PastelPlay", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = PlayRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            PlayReliefItem("f${relief.size + 1}-play", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "Playful shift beat")
    }

    fun reset() {
        dayStatus.value = PlayDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("PastelPlay", "reset demo data")
    }
}
