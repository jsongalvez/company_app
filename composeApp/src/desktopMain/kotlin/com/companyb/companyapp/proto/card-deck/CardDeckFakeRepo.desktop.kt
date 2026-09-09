package com.companyb.companyapp.proto.carddeck

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class DeckDayStatus { OPEN, PAST, REMITTED }

enum class DeckSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class DeckRemitKind { SESSION, PRODUCT }

enum class DeckRemitStatus { DRAFT, SUBMITTED }

data class DeckBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class DeckUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class DeckSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: DeckSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class DeckClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class DeckMail(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class DeckAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class DeckRemittance(
    val id: String,
    val kind: DeckRemitKind,
    val status: DeckRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class DeckRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

object CardDeckFakeRepo {
    val branches = mutableStateListOf<DeckBranch>()
    val users = mutableStateListOf<DeckUser>()
    val sessions = mutableStateListOf<DeckSession>()
    val clients = mutableStateListOf<DeckClient>()
    val mailbox = mutableStateListOf<DeckMail>()
    val audits = mutableStateListOf<DeckAudit>()
    val remittances = mutableStateListOf<DeckRemittance>()
    val relief = mutableStateListOf<DeckRelief>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val dayStatus = mutableStateOf(DeckDayStatus.OPEN)
    val currentUserName = mutableStateOf("Maya Santos")

    private var auditSeq = 0
    private var remitSeq = 0

    init {
        reset()
    }

    fun reset() {
        auditSeq = 0
        remitSeq = 0
        branches.clear()
        branches.addAll(
            listOf(
                DeckBranch("b1", "Sunrise Clinic", "CLINIC"),
                DeckBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR"),
                DeckBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION"),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                DeckUser("u1", "Maya Santos", "Practitioner", "b1"),
                DeckUser("u2", "Jose Ramos", "Coordinator", "b1"),
                DeckUser("u3", "Ana Lim", "MANAGER", "b2"),
                DeckUser("u4", "Rico Tan", "Accountant", "b1"),
                DeckUser("u5", "Lena Cruz", "ONBOARDING", "b1", onboarding = true),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                DeckSession("s1", "Rosa D.", "b1", DeckSessionStatus.PENDING, "Follow-up", 800, false, time = "09:00"),
                DeckSession("s2", "Marco P.", "b1", DeckSessionStatus.COMPLETED, "Initial", 1200, false, time = "10:30"),
                DeckSession("s3", "Walk-in neighbor", "b1", DeckSessionStatus.PENDING, "Walk-in", 600, true, time = "11:15"),
                DeckSession("s4", "Lita G.", "b1", DeckSessionStatus.NO_SHOW, "Follow-up", 800, false, time = "13:00"),
                DeckSession("s5", "Paolo V.", "b1", DeckSessionStatus.CANCELLED, "Initial", 1200, false, time = "14:30"),
                DeckSession("s6", "Nena R.", "b2", DeckSessionStatus.PENDING, "Follow-up", 750, false, time = "09:45"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                DeckClient("c1", "Rosa D.", "F, 54 - regular since 2023", hasPending = true),
                DeckClient("c2", "Marco P.", "M, 41 - initial done", hasPending = false),
                DeckClient("c3", "Lita G.", "F, 63 - two visits", hasPending = false),
                DeckClient("c4", "Paolo V.", "M, 29 - tour client", hasPending = false),
            ),
        )
        mailbox.clear()
        mailbox.addAll(
            listOf(
                DeckMail("m1", "Relief granted at Sunrise Clinic", "Jose Ramos approved your relief for today.", day = "Today"),
                DeckMail("m2", "Reminder: Rosa D. at 09:00", "Session s1 is PENDING at Sunrise Clinic.", read = true, day = "Today"),
                DeckMail("m3", "Relief invite: Harbor Provincial Tour", "Ana Lim invited you for Saturday.", day = "Sat"),
            ),
        )
        audits.clear()
        remittances.clear()
        remittances.addAll(
            listOf(
                DeckRemittance("r1", DeckRemitKind.SESSION, DeckRemitStatus.DRAFT, 4600, "Today at Sunrise Clinic"),
                DeckRemittance("r2", DeckRemitKind.PRODUCT, DeckRemitStatus.DRAFT, 1800, "Today at Sunrise Clinic"),
            ),
        )
        relief.clear()
        relief.addAll(
            listOf(
                DeckRelief("f1", "Relief request", "Maya Santos", "Harbor Provincial Tour", "Today", "Broadcast - waiting for grant"),
                DeckRelief("f2", "Relief invite", "Ana Lim", "Harbor Provincial Tour", "Sat", "Invite waiting for reply"),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        dayStatus.value = DeckDayStatus.OPEN
        currentUserName.value = "Maya Santos"
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun log(action: String, record: String, reason: String = "") {
        auditSeq += 1
        audits.add(
            0,
            DeckAudit(
                id = "a$auditSeq",
                actor = currentUserName.value,
                action = action,
                record = record,
                whenText = "Demo 09:${(10 + auditSeq).toString().padStart(2, '0')}",
                reason = reason,
            ),
        )
    }

    fun nextRemitId(): String {
        remitSeq += 1
        return "rx$remitSeq"
    }
}
