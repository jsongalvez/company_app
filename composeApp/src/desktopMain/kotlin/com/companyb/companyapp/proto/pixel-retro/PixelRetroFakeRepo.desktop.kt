package com.companyb.companyapp.proto.pixelretro

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class PxDayStatus { OPEN, PAST, REMITTED }

enum class PxSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class PxRemitKind { SESSION, PRODUCT }

enum class PxRemitStatus { DRAFT, SUBMITTED }

data class PxBranch(
    val id: String,
    val name: String,
    val zone: String,
    val flavor: String,
)

data class PxUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class PxSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: PxSessionStatus,
    val kind: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class PxClient(
    val id: String,
    val name: String,
    val note: String,
    val hasPending: Boolean,
    val masked: Boolean = false,
)

data class PxLetter(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class PxAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class PxRemittance(
    val id: String,
    val kind: PxRemitKind,
    val status: PxRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
)

data class PxRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val slot: String,
    val note: String,
)

class PixelRetroFakeRepo {
    val branches =
        mutableStateListOf(
            PxBranch("b1", "Pixel Plaza", "Zone 1", "Neon arcade flagship, 8 cabinets"),
            PxBranch("b2", "Sprite Springs", "Zone 2", "Quiet garden outpost, 2 cabinets"),
            PxBranch("b3", "Boss Keep", "Zone 3", "Night raid hall, extended hours"),
        )

    val users =
        mutableStateListOf(
            PxUser("u1", "Maya Santos", "Practitioner", "b1"),
            PxUser("u2", "Rico Dela Cruz", "Coordinator", "b1"),
            PxUser("u3", "Lena Villanueva", "MANAGER", "b2"),
            PxUser("u4", "Omar Tan", "Accountant", "b1"),
            PxUser("u5", "Newbie Quinn", "ONBOARDING", "b1", onboarding = true),
        )

    val sessions =
        mutableStateListOf(
            PxSession("s1", "Imelda Ramos", "b1", PxSessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
            PxSession("s2", "Walk-in sprite", "b1", PxSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
            PxSession("s3", "Felipe Aquino", "b1", PxSessionStatus.COMPLETED, "Filling", 1200, false, time = "08:00"),
            PxSession("s4", "Rosa Lim", "b2", PxSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
            PxSession("s5", "Tomas Reyes", "b1", PxSessionStatus.CANCELLED, "Extraction", 1500, false, time = "11:00"),
        )

    val clients =
        mutableStateListOf(
            PxClient("c1", "Imelda Ramos", "Stage 2, prefers morning slots", hasPending = true),
            PxClient("c2", "Felipe Aquino", "Filling done, recall in 6 moons", hasPending = false),
            PxClient("c3", "Rosa Lim", "Missed twice, send a courier pigeon", hasPending = false),
            PxClient("c4", "Guest Sprite", "Registry entry, masked on the board", hasPending = false, masked = true),
        )

    val letters =
        mutableStateListOf(
            PxLetter("n1", "Relief invite: Sprite Springs", "Lena invites Maya to cover SAT 09:00-13:00.", "Day 041"),
            PxLetter("n2", "Remittance sealed", "Day 040 SESSION coffer submitted. Snapshot #040-S.", "Day 040", read = true),
            PxLetter("n3", "Patch notes: void policy", "Void needs a reason note. Unvoid restores the run.", "Day 041"),
        )

    val audits =
        mutableStateListOf(
            PxAudit("a1", "Rico", "clock-in", "Pixel Plaza / Day 041", "07:58"),
            PxAudit("a2", "Maya", "complete", "Session s3 / Felipe Aquino", "08:45"),
            PxAudit("a3", "Lena", "submit", "Remittance Day 040 SESSION", "18:02"),
        )

    val remittances =
        mutableStateListOf(
            PxRemittance("r1", PxRemitKind.SESSION, PxRemitStatus.SUBMITTED, 12500, "Day 040", snapshot = "#040-S"),
            PxRemittance("r2", PxRemitKind.PRODUCT, PxRemitStatus.DRAFT, 3200, "Day 041"),
        )

    val reliefs =
        mutableStateListOf(
            PxRelief("f1", "DUTY", "Maya Santos", "Pixel Plaza", "Day 041 AM", "Front desk + chair 2"),
            PxRelief("f2", "INVITE", "Lena Villanueva", "Sprite Springs", "SAT 09:00-13:00", "Cover the garden rush"),
            PxRelief("f3", "REQUEST", "Rico Dela Cruz", "Boss Keep", "FRI night", "Need a second pair of hands"),
        )

    val branchId = mutableStateOf("b1")
    val clockedIn = mutableStateOf(false)
    val dayStatus = mutableStateOf(PxDayStatus.OPEN)
    val me = mutableStateOf("Pixel Cadet")
    val seq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun nextId(prefix: String): String {
        val n = seq.value
        seq.value = n + 1
        return "$prefix$n"
    }

    fun log(event: String) {
        logInfo("PixelRetro", event)
    }

    fun audit(
        actor: String,
        action: String,
        record: String,
        reason: String = "",
    ) {
        audits.add(0, PxAudit(nextId("a"), actor, action, record, "Day 041", reason))
    }

    fun toggleClock() {
        clockedIn.value = !clockedIn.value
        audit(me.value, if (clockedIn.value) "clock-in" else "clock-out", "${branchName(branchId.value)} / Day 041")
        log(if (clockedIn.value) "player one joined" else "player one left")
    }

    fun requestRelief(note: String) {
        val text = note.ifBlank { "Extra hands wanted" }
        reliefs.add(0, PxRelief(nextId("f"), "REQUEST", me.value, branchName(branchId.value), "Day 041", text))
        audit(me.value, "relief-request", text)
    }

    fun inviteRelief(note: String) {
        val text = note.ifBlank { "Join my party" }
        reliefs.add(0, PxRelief(nextId("f"), "INVITE", me.value, branchName(branchId.value), "Day 041", text))
        audit(me.value, "relief-invite", text)
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = PxSessionStatus.COMPLETED)
            audit(me.value, "complete", "Session $id / ${s.clientName}")
        }
    }

    fun voidSession(
        id: String,
        reason: String,
    ) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = true, voidReason = reason.ifBlank { "No reason given" })
            audit(me.value, "void", "Session $id / ${s.clientName}", reason.ifBlank { "No reason given" })
        }
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = false, voidReason = "")
            audit(me.value, "unvoid", "Session $id / ${s.clientName}")
        }
    }

    fun addWalkIn() {
        val id = nextId("s")
        sessions.add(0, PxSession(id, "Walk-in sprite", branchId.value, PxSessionStatus.PENDING, "Checkup", 500, true, time = "now"))
        audit(me.value, "walk-in", "Session $id / Walk-in sprite")
    }

    fun toggleMask(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) {
            val c = clients[i]
            clients[i] = c.copy(masked = !c.masked)
        }
    }

    fun addDraft(
        kind: PxRemitKind,
        amount: Int,
    ) {
        remittances.add(0, PxRemittance(nextId("r"), kind, PxRemitStatus.DRAFT, amount, "Day 041"))
        audit(me.value, "draft", "$kind coffer $amount")
    }

    fun submitRemit(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = PxRemitStatus.SUBMITTED, snapshot = "#041-${r.kind}")
            audit(me.value, "submit", "Remittance $id / ${r.kind} ${r.amount}")
        }
    }

    fun undoRemit(
        id: String,
        reason: String,
    ) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = PxRemitStatus.DRAFT, snapshot = "")
            audit(me.value, "undo-48h", "Remittance $id / ${r.kind}", reason.ifBlank { "Misclick, within 48h" })
        }
    }

    fun markRead(id: String) {
        val i = letters.indexOfFirst { it.id == id }
        if (i >= 0) {
            val l = letters[i]
            letters[i] = l.copy(read = true)
        }
    }

    fun markAllRead() {
        for (i in letters.indices) {
            letters[i] = letters[i].copy(read = true)
        }
    }

    fun reset() {
        branchId.value = "b1"
        clockedIn.value = false
        dayStatus.value = PxDayStatus.OPEN
        log("demo reset to stage 1")
        audit("System", "reset", "Demo board wiped to stage 1")
    }

    fun unreadCount(): Int = letters.count { !it.read }
}
