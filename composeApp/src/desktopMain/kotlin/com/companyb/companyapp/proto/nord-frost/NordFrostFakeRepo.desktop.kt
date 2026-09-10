package com.companyb.companyapp.proto.nordfrost

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class NfDayStatus { OPEN, PAST, REMITTED }

enum class NfSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class NfRemitKind { SESSION, PRODUCT }

enum class NfRemitStatus { DRAFT, SUBMITTED }

data class NfBranch(
    val id: String,
    val name: String,
    val zone: String,
    val flavor: String,
)

data class NfUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class NfSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: NfSessionStatus,
    val kind: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class NfClient(
    val id: String,
    val name: String,
    val note: String,
    val hasPending: Boolean,
    val masked: Boolean = false,
)

data class NfLetter(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class NfAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class NfRemittance(
    val id: String,
    val kind: NfRemitKind,
    val status: NfRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
)

data class NfRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val slot: String,
    val note: String,
)

class NordFrostFakeRepo {
    val branches =
        mutableStateListOf(
            NfBranch("b1", "Aurora Central", "Icefield 1", "Flagship frost hall, 6 chilled chairs"),
            NfBranch("b2", "Glacier North", "Icefield 2", "Quiet drift outpost, 2 chairs"),
            NfBranch("b3", "Snowline East", "Icefield 3", "Late-whiteout hall, extended hours"),
        )

    val users =
        mutableStateListOf(
            NfUser("u1", "Maya Santos", "Practitioner", "b1"),
            NfUser("u2", "Rico Dela Cruz", "Coordinator", "b1"),
            NfUser("u3", "Lena Villanueva", "MANAGER", "b2"),
            NfUser("u4", "Omar Tan", "Accountant", "b1"),
            NfUser("u5", "Newcomer Quinn", "ONBOARDING", "b1", onboarding = true),
        )

    val sessions =
        mutableStateListOf(
            NfSession("s1", "Imelda Ramos", "b1", NfSessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
            NfSession("s2", "Walk-in guest", "b1", NfSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
            NfSession("s3", "Felipe Aquino", "b1", NfSessionStatus.COMPLETED, "Filling", 1200, false, time = "08:00"),
            NfSession("s4", "Rosa Lim", "b2", NfSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
            NfSession("s5", "Tomas Reyes", "b1", NfSessionStatus.CANCELLED, "Extraction", 1500, false, time = "11:00"),
        )

    val clients =
        mutableStateListOf(
            NfClient("c1", "Imelda Ramos", "Prefers early frost slots, chair 2", hasPending = true),
            NfClient("c2", "Felipe Aquino", "Filling done, recall in 6 moons", hasPending = false),
            NfClient("c3", "Rosa Lim", "Missed twice, send a snow-owl reminder", hasPending = false),
            NfClient("c4", "Guest Drifter", "Registry entry, veiled on the board", hasPending = false, masked = true),
        )

    val letters =
        mutableStateListOf(
            NfLetter("n1", "Relief invite: Glacier North", "Lena invites Maya to cover SAT 09:00-13:00.", "Day 041"),
            NfLetter(
                "n2",
                "Remittance sealed",
                "Day 040 SESSION vault submitted. Snapshot #040-S.",
                "Day 040",
                read = true,
            ),
            NfLetter(
                "n3",
                "Frost memo: void policy",
                "Void needs a reason note. Restore thaws the session back.",
                "Day 041",
            ),
        )

    val audits =
        mutableStateListOf(
            NfAudit("a1", "Rico", "clock-in", "Aurora Central / Day 041", "07:58"),
            NfAudit("a2", "Maya", "complete", "Session s3 / Felipe Aquino", "08:45"),
            NfAudit("a3", "Lena", "submit", "Remittance Day 040 SESSION", "18:02"),
        )

    val remittances =
        mutableStateListOf(
            NfRemittance("r1", NfRemitKind.SESSION, NfRemitStatus.SUBMITTED, 12500, "Day 040", snapshot = "#040-S"),
            NfRemittance("r2", NfRemitKind.PRODUCT, NfRemitStatus.DRAFT, 3200, "Day 041"),
        )

    val reliefs =
        mutableStateListOf(
            NfRelief("f1", "DUTY", "Maya Santos", "Aurora Central", "Day 041 AM", "Front desk + chair 2"),
            NfRelief("f2", "INVITE", "Lena Villanueva", "Glacier North", "SAT 09:00-13:00", "Cover the morning drift"),
            NfRelief("f3", "REQUEST", "Rico Dela Cruz", "Snowline East", "FRI night", "Need a second pair of hands"),
        )

    val branchId = mutableStateOf("b1")
    val clockedIn = mutableStateOf(false)
    val dayStatus = mutableStateOf(NfDayStatus.OPEN)
    val me = mutableStateOf("Frost Keeper")
    val seq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun nextId(prefix: String): String {
        val n = seq.value
        seq.value = n + 1
        return "$prefix$n"
    }

    fun log(event: String) {
        logInfo("NordFrost", event)
    }

    fun audit(
        actor: String,
        action: String,
        record: String,
        reason: String = "",
    ) {
        audits.add(0, NfAudit(nextId("a"), actor, action, record, "Day 041", reason))
    }

    fun toggleClock() {
        clockedIn.value = !clockedIn.value
        audit(me.value, if (clockedIn.value) "clock-in" else "clock-out", "${branchName(branchId.value)} / Day 041")
        log(if (clockedIn.value) "keeper clocked in" else "keeper clocked out")
    }

    fun requestRelief(note: String) {
        val text = note.ifBlank { "Extra hands wanted" }
        reliefs.add(0, NfRelief(nextId("f"), "REQUEST", me.value, branchName(branchId.value), "Day 041", text))
        audit(me.value, "relief-request", text)
    }

    fun inviteRelief(note: String) {
        val text = note.ifBlank { "Join my watch" }
        reliefs.add(0, NfRelief(nextId("f"), "INVITE", me.value, branchName(branchId.value), "Day 041", text))
        audit(me.value, "relief-invite", text)
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = NfSessionStatus.COMPLETED)
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
        sessions.add(
            0,
            NfSession(id, "Walk-in guest", branchId.value, NfSessionStatus.PENDING, "Checkup", 500, true, time = "now"),
        )
        audit(me.value, "walk-in", "Session $id / Walk-in guest")
    }

    fun toggleMask(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) {
            val c = clients[i]
            clients[i] = c.copy(masked = !c.masked)
        }
    }

    fun addDraft(
        kind: NfRemitKind,
        amount: Int,
    ) {
        remittances.add(0, NfRemittance(nextId("r"), kind, NfRemitStatus.DRAFT, amount, "Day 041"))
        audit(me.value, "draft", "$kind vault $amount")
    }

    fun submitRemit(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = NfRemitStatus.SUBMITTED, snapshot = "#041-${r.kind}")
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
            remittances[i] = r.copy(status = NfRemitStatus.DRAFT, snapshot = "")
            audit(me.value, "undo-48h", "Remittance $id / ${r.kind}", reason.ifBlank { "Entered in error, within 48h" })
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
        dayStatus.value = NfDayStatus.OPEN
        log("frost desk reset to a clean drift")
        audit("System", "reset", "Frost desk wiped to a clean drift")
    }

    fun unreadCount(): Int = letters.count { !it.read }
}
