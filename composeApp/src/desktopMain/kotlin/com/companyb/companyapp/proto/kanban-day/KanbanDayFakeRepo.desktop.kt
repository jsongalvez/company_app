package com.companyb.companyapp.proto.kanday

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class KbDayStatus { OPEN, PAST, REMITTED }

enum class KbSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class KbRemitKind { SESSION, PRODUCT }

enum class KbRemitStatus { DRAFT, SUBMITTED }

val KbLaneOrder = listOf(
    KbSessionStatus.PENDING,
    KbSessionStatus.COMPLETED,
    KbSessionStatus.NO_SHOW,
    KbSessionStatus.CANCELLED,
)

const val KbPendingWipLimit = 4

data class KbBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class KbUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class KbSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: KbSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class KbClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class KbMail(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class KbAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class KbRemittance(
    val id: String,
    val kind: KbRemitKind,
    val status: KbRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
    val undoLocked: Boolean = false,
)

data class KbRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

object KanbanDayFakeRepo {
    val branches = mutableStateListOf<KbBranch>()
    val users = mutableStateListOf<KbUser>()
    val sessions = mutableStateListOf<KbSession>()
    val clients = mutableStateListOf<KbClient>()
    val mailbox = mutableStateListOf<KbMail>()
    val audits = mutableStateListOf<KbAudit>()
    val remittances = mutableStateListOf<KbRemittance>()
    val relief = mutableStateListOf<KbRelief>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val dayStatus = mutableStateOf(KbDayStatus.OPEN)
    val currentUserName = mutableStateOf("Maya Santos")

    private var auditSeq = 0
    private var remitSeq = 0
    private var sessionSeq = 100

    init {
        reset()
    }

    fun reset() {
        auditSeq = 0
        remitSeq = 0
        sessionSeq = 100
        branches.clear()
        branches.addAll(
            listOf(
                KbBranch("b1", "Sunrise Clinic", "CLINIC"),
                KbBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR"),
                KbBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION"),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                KbUser("u1", "Maya Santos", "Practitioner", "b1"),
                KbUser("u2", "Jose Ramos", "Coordinator", "b1"),
                KbUser("u3", "Ana Lim", "MANAGER", "b2"),
                KbUser("u4", "Rico Tan", "Accountant", "b1"),
                KbUser("u5", "Lena Cruz", "ONBOARDING", "b1", onboarding = true),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                KbSession("s1", "Rosa D.", "b1", KbSessionStatus.PENDING, "Follow-up", 800, false, time = "09:00"),
                KbSession("s2", "Nena R.", "b1", KbSessionStatus.PENDING, "Initial", 1200, false, time = "09:45"),
                KbSession("s3", "Marco P.", "b1", KbSessionStatus.COMPLETED, "Initial", 1200, false, time = "10:30"),
                KbSession("s4", "Walk-in neighbor", "b1", KbSessionStatus.PENDING, "Walk-in", 600, true, time = "11:15"),
                KbSession("s5", "Lita G.", "b1", KbSessionStatus.NO_SHOW, "Follow-up", 800, false, time = "13:00"),
                KbSession("s6", "Paolo V.", "b1", KbSessionStatus.CANCELLED, "Initial", 1200, false, time = "14:30"),
                KbSession("s7", "Sario T.", "b2", KbSessionStatus.PENDING, "Follow-up", 750, false, time = "09:30"),
                KbSession("s8", "Mila Q.", "b1", KbSessionStatus.PENDING, "Follow-up", 800, false, time = "15:15"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                KbClient("c1", "Rosa D.", "F, 54 - regular since 2023", hasPending = true),
                KbClient("c2", "Marco P.", "M, 41 - initial done", hasPending = false),
                KbClient("c3", "Lita G.", "F, 63 - two visits", hasPending = false),
                KbClient("c4", "Paolo V.", "M, 29 - tour client", hasPending = false),
            ),
        )
        mailbox.clear()
        mailbox.addAll(
            listOf(
                KbMail("m1", "Relief Duty granted at Sunrise Clinic", "Jose Ramos approved your relief for today.", day = "Today"),
                KbMail("m2", "Reminder: Rosa D. at 09:00", "Session s1 is PENDING at Sunrise Clinic.", read = true, day = "Today"),
                KbMail("m3", "Relief Invite: Harbor Provincial Tour", "Ana Lim invited you for Saturday.", day = "Sat"),
            ),
        )
        audits.clear()
        remittances.clear()
        remittances.addAll(
            listOf(
                KbRemittance("r1", KbRemitKind.SESSION, KbRemitStatus.DRAFT, 4600, "Today at Sunrise Clinic"),
                KbRemittance("r2", KbRemitKind.PRODUCT, KbRemitStatus.DRAFT, 1800, "Today at Sunrise Clinic"),
                KbRemittance(
                    "r0",
                    KbRemitKind.SESSION,
                    KbRemitStatus.SUBMITTED,
                    5200,
                    "Yesterday at Sunrise Clinic",
                    snapshot = "SNAP-0912-SESSION-5200",
                    submittedAt = "submitted 2h ago",
                ),
                KbRemittance(
                    "r9",
                    KbRemitKind.PRODUCT,
                    KbRemitStatus.SUBMITTED,
                    900,
                    "3 days ago at Sunrise Clinic",
                    snapshot = "SNAP-0909-PRODUCT-0900",
                    submittedAt = "submitted 3 days ago",
                    undoLocked = true,
                ),
            ),
        )
        relief.clear()
        relief.addAll(
            listOf(
                KbRelief("f1", "Relief Request", "Maya Santos", "Harbor Provincial Tour", "Today", "Broadcast - waiting for grant"),
                KbRelief("f2", "Relief Invite", "Ana Lim", "Harbor Provincial Tour", "Sat", "Invite waiting for reply"),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        dayStatus.value = KbDayStatus.OPEN
        currentUserName.value = "Maya Santos"
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun log(action: String, record: String, reason: String = "") {
        auditSeq += 1
        audits.add(
            0,
            KbAudit(
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

    fun nextSessionId(): String {
        sessionSeq += 1
        return "sx$sessionSeq"
    }

    fun moveSession(id: String, to: KbSessionStatus) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = sessions[index]
        if (current.status == to) return
        sessions[index] = current.copy(status = to)
        log("Pull ${current.status} -> $to", "Session ${current.id} (${current.clientName})")
    }

    fun pendingCount(): Int = sessions.count { it.status == KbSessionStatus.PENDING && !it.voided }
}
