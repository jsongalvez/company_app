package com.companyb.companyapp.proto.a11ymax

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class MaxDayStatus { OPEN, PAST, REMITTED }

enum class MaxSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class MaxRemitKind { SESSION, PRODUCT }

enum class MaxRemitStatus { DRAFT, SUBMITTED }

enum class MaxTab { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class MaxBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class MaxUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class MaxSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: MaxSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class MaxClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class MaxMail(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class MaxAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class MaxRemittance(
    val id: String,
    val kind: MaxRemitKind,
    val status: MaxRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class MaxRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

object A11yMaxFakeRepo {
    val branches = mutableStateListOf<MaxBranch>()
    val users = mutableStateListOf<MaxUser>()
    val sessions = mutableStateListOf<MaxSession>()
    val clients = mutableStateListOf<MaxClient>()
    val mailbox = mutableStateListOf<MaxMail>()
    val audits = mutableStateListOf<MaxAudit>()
    val remittances = mutableStateListOf<MaxRemittance>()
    val relief = mutableStateListOf<MaxRelief>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val dayStatus = mutableStateOf(MaxDayStatus.OPEN)
    val currentUserName = mutableStateOf("Maya Santos")
    val textScale = mutableStateOf(A11yTextScale.STANDARD)
    val announcement = mutableStateOf("A11y Max prototype ready. Press Alt plus a number key to move between sections.")
    val unreadCount: Int get() = mailbox.count { !it.read }

    private var auditSeq = 0
    private var remitSeq = 0
    private var sessionSeq = 0

    init {
        reset()
    }

    fun say(message: String) {
        announcement.value = message
    }

    fun audit(actor: String, action: String, record: String, reason: String = "") {
        auditSeq += 1
        audits.add(
            0,
            MaxAudit(
                id = "a$auditSeq",
                actor = actor,
                action = action,
                record = record,
                whenText = "Today 16:0${auditSeq % 10} Manila",
                reason = reason,
            ),
        )
    }

    fun reset() {
        auditSeq = 0
        remitSeq = 0
        sessionSeq = 100
        branches.clear()
        branches.addAll(
            listOf(
                MaxBranch("b1", "Sunrise Clinic", "CLINIC"),
                MaxBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR"),
                MaxBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION"),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                MaxUser("u1", "Maya Santos", "Practitioner", "b1"),
                MaxUser("u2", "Jose Ramos", "Coordinator", "b1"),
                MaxUser("u3", "Lena Cruz", "MANAGER", "b2"),
                MaxUser("u4", "Omar Reyes", "Accountant", "b1"),
                MaxUser("u5", "Rina Dela Pena", "ONBOARDING", "b1", onboarding = true),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                MaxSession("s1", "Ana Villanueva", "b1", MaxSessionStatus.PENDING, "Follow-up", 850, false, time = "09:00"),
                MaxSession("s2", "Walk-in guest 12", "b1", MaxSessionStatus.PENDING, "First visit", 950, true, time = "09:40"),
                MaxSession("s3", "Ben Aquino", "b1", MaxSessionStatus.COMPLETED, "Follow-up", 850, false, time = "08:00"),
                MaxSession("s4", "Cara Lim", "b2", MaxSessionStatus.NO_SHOW, "First visit", 950, false, time = "10:20"),
                MaxSession("s5", "Dan Torres", "b1", MaxSessionStatus.CANCELLED, "Follow-up", 850, false, time = "11:00"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                MaxClient("c1", "Ana Villanueva", "F, 34. Global record.", hasPending = true),
                MaxClient("c2", "Ben Aquino", "M, 41. Global record.", hasPending = false),
                MaxClient("c3", "Cara Lim", "F, 28. Global record.", hasPending = false),
                MaxClient("c4", "Dan Torres", "M, 52. Global record.", hasPending = false),
            ),
        )
        mailbox.clear()
        mailbox.addAll(
            listOf(
                MaxMail("m1", "Relief granted at Sunrise Clinic", "Jose Ramos granted your relief request for today.", false, "today"),
                MaxMail("m2", "Session reminder", "Ana Villanueva, 09:00 at Sunrise Clinic.", false, "today"),
                MaxMail("m3", "Remittance submitted", "SESSION remittance for yesterday sealed a snapshot.", true, "yesterday"),
            ),
        )
        audits.clear()
        audits.addAll(
            listOf(
                MaxAudit("a1", "Jose Ramos", "SUBMIT_REMITTANCE", "SESSION draft #41", "Yesterday 18:02 Manila"),
                MaxAudit("a2", "Maya Santos", "CLOCK_IN", "Sunrise Clinic branch day", "Today 07:58 Manila"),
            ),
        )
        auditSeq = 2
        remittances.clear()
        remittances.addAll(
            listOf(
                MaxRemittance("r1", MaxRemitKind.SESSION, MaxRemitStatus.DRAFT, 12400, "Today", snapshot = ""),
                MaxRemittance("r2", MaxRemitKind.PRODUCT, MaxRemitStatus.DRAFT, 3200, "Today", snapshot = ""),
            ),
        )
        remitSeq = 2
        relief.clear()
        relief.addAll(
            listOf(
                MaxRelief("f1", "Relief duty", "Maya Santos", "Harbor Provincial Tour", "today", "VIEW ONLY"),
                MaxRelief("f2", "Relief request", "Omar Reyes", "Sunrise Clinic", "today", "AWAITING GRANT"),
                MaxRelief("f3", "Relief invite", "Lena Cruz", "Lingap Medical Mission", "Saturday", "INVITED"),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        dayStatus.value = MaxDayStatus.OPEN
        currentUserName.value = "Maya Santos"
        announcement.value = "Demo data reset. You are signed in as Maya Santos, Practitioner."
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun setSessionStatus(id: String, status: MaxSessionStatus) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = sessions[index]
        if (current.walkIn && (status == MaxSessionStatus.NO_SHOW || status == MaxSessionStatus.CANCELLED)) {
            say("Blocked. Walk-in sessions cannot be marked NO SHOW or CANCELLED.")
            return
        }
        sessions[index] = current.copy(status = status)
        audit(currentUserName.value, "SESSION_${status.name}", "${current.clientName} ${current.id}")
        say("Session for ${current.clientName} is now ${status.name.replace('_', ' ')}.")
    }

    fun voidSession(id: String, reason: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(voided = true, voidReason = reason)
        audit(currentUserName.value, "VOID_SESSION", "${current.clientName} ${current.id}", reason)
        say("Session for ${current.clientName} voided. Reason recorded.")
    }

    fun unvoidSession(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = sessions[index]
        sessions[index] = current.copy(voided = false, voidReason = "")
        audit(currentUserName.value, "UNVOID_SESSION", "${current.clientName} ${current.id}")
        say("Void removed from session for ${current.clientName}.")
    }

    fun addWalkIn() {
        sessionSeq += 1
        sessions.add(
            0,
            MaxSession(
                "s$sessionSeq",
                "Walk-in guest $sessionSeq",
                clockedBranchId.value,
                MaxSessionStatus.PENDING,
                "First visit",
                950,
                true,
                time = "now",
            ),
        )
        audit(currentUserName.value, "CREATE_SESSION", "Walk-in guest $sessionSeq")
        say("Walk-in session created and PENDING.")
    }

    fun toggleRead(id: String) {
        val index = mailbox.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = mailbox[index]
        mailbox[index] = current.copy(read = !current.read)
        say(if (current.read) "Message marked unread." else "Message marked read.")
    }

    fun markAllRead() {
        for (i in mailbox.indices) mailbox[i] = mailbox[i].copy(read = true)
        say("All mailbox messages marked read.")
    }

    fun submitRemittance(id: String) {
        val index = remittances.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = remittances[index]
        remitSeq += 1
        remittances[index] = current.copy(
            status = MaxRemitStatus.SUBMITTED,
            snapshot = "SNAP-$remitSeq sealed ${current.amount} pesos for ${current.dayLabel}",
            submittedAt = "within 48h window",
        )
        audit(currentUserName.value, "SUBMIT_REMITTANCE", "${current.kind} ${current.id}")
        say("${current.kind} remittance submitted. Snapshot sealed. Undo stays available for 48 hours.")
    }

    fun undoRemittance(id: String, reason: String) {
        val index = remittances.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = remittances[index]
        remittances[index] = current.copy(status = MaxRemitStatus.DRAFT, snapshot = "", submittedAt = "")
        audit(currentUserName.value, "UNDO_REMITTANCE", "${current.kind} ${current.id}", reason)
        say("${current.kind} remittance returned to draft. Snapshot deleted. Reason recorded.")
    }
}
