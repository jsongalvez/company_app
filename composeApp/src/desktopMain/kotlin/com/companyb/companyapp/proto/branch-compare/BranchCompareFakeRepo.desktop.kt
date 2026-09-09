package com.companyb.companyapp.proto.branchcompare

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class CmpDayStatus { OPEN, PAST, REMITTED }

enum class CmpSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class CmpRemitKind { SESSION, PRODUCT }

enum class CmpRemitStatus { DRAFT, SUBMITTED }

data class CmpBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: CmpDayStatus,
    val target: Int,
)

data class CmpUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class CmpSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: CmpSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class CmpClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class CmpMail(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class CmpAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class CmpRemittance(
    val id: String,
    val branchId: String,
    val kind: CmpRemitKind,
    val status: CmpRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class CmpRelief(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

object BranchCompareFakeRepo {
    val branches = mutableStateListOf<CmpBranch>()
    val users = mutableStateListOf<CmpUser>()
    val sessions = mutableStateListOf<CmpSession>()
    val clients = mutableStateListOf<CmpClient>()
    val mailbox = mutableStateListOf<CmpMail>()
    val audits = mutableStateListOf<CmpAudit>()
    val remittances = mutableStateListOf<CmpRemittance>()
    val relief = mutableStateListOf<CmpRelief>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val focusedBranchId = mutableStateOf("b1")
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
                CmpBranch("b1", "Sunrise Clinic", "CLINIC", CmpDayStatus.OPEN, 12000),
                CmpBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR", CmpDayStatus.PAST, 9000),
                CmpBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION", CmpDayStatus.REMITTED, 6000),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                CmpUser("u1", "Maya Santos", "Practitioner", "b1", clockedIn = true),
                CmpUser("u2", "Jose Ramos", "Coordinator", "b1", clockedIn = true),
                CmpUser("u3", "Ana Lim", "MANAGER", "b2"),
                CmpUser("u4", "Rico Tan", "Accountant", "b1"),
                CmpUser("u5", "Lena Cruz", "ONBOARDING", "b1", onboarding = true),
                CmpUser("u6", "Nadia Reyes", "Practitioner", "b2", clockedIn = true),
                CmpUser("u7", "Paolo Aquino", "Coordinator", "b3"),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                CmpSession("s1", "Rosa D.", "b1", CmpSessionStatus.PENDING, "Follow-up", 800, false, time = "09:00"),
                CmpSession("s2", "Marco P.", "b1", CmpSessionStatus.COMPLETED, "Initial", 1200, false, time = "10:30"),
                CmpSession("s3", "Walk-in neighbor", "b1", CmpSessionStatus.PENDING, "Walk-in", 600, true, time = "11:15"),
                CmpSession("s4", "Lita G.", "b1", CmpSessionStatus.NO_SHOW, "Follow-up", 800, false, time = "13:00"),
                CmpSession("s5", "Paolo V.", "b2", CmpSessionStatus.PENDING, "Initial", 1100, false, time = "09:45"),
                CmpSession("s6", "Nena R.", "b2", CmpSessionStatus.COMPLETED, "Follow-up", 750, false, time = "11:00"),
                CmpSession("s7", "Walk-in fisherman", "b2", CmpSessionStatus.PENDING, "Walk-in", 500, true, time = "14:00"),
                CmpSession("s8", "Cora M.", "b3", CmpSessionStatus.COMPLETED, "Mission care", 400, false, time = "08:30"),
                CmpSession("s9", "Dante S.", "b3", CmpSessionStatus.CANCELLED, "Mission care", 400, false, time = "10:00"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                CmpClient("c1", "Rosa D.", "F, 54 - regular since 2023", hasPending = true),
                CmpClient("c2", "Marco P.", "M, 41 - initial done", hasPending = false),
                CmpClient("c3", "Lita G.", "F, 63 - two visits", hasPending = false),
                CmpClient("c4", "Paolo V.", "M, 29 - tour client", hasPending = false),
                CmpClient("c5", "Cora M.", "F, 47 - mission client", hasPending = false),
            ),
        )
        mailbox.clear()
        mailbox.addAll(
            listOf(
                CmpMail("m1", "Relief granted at Sunrise Clinic", "Jose Ramos approved your relief for today.", day = "Today"),
                CmpMail("m2", "Reminder: Rosa D. at 09:00", "Session s1 is PENDING at Sunrise Clinic.", read = true, day = "Today"),
                CmpMail("m3", "Relief invite: Harbor Provincial Tour", "Ana Lim invited you for Saturday.", day = "Sat"),
                CmpMail("m4", "Lingap day remitted", "Snapshot sealed for the mission branch.", read = true, day = "Yesterday"),
            ),
        )
        audits.clear()
        remittances.clear()
        remittances.addAll(
            listOf(
                CmpRemittance("r1", "b1", CmpRemitKind.SESSION, CmpRemitStatus.DRAFT, 4600, "Today at Sunrise Clinic"),
                CmpRemittance("r2", "b1", CmpRemitKind.PRODUCT, CmpRemitStatus.DRAFT, 1800, "Today at Sunrise Clinic"),
                CmpRemittance("r3", "b3", CmpRemitKind.SESSION, CmpRemitStatus.SUBMITTED, 3200, "Yesterday at Lingap Mission", snapshot = "SNAP-1042 sealed", submittedAt = "Yesterday 18:02"),
            ),
        )
        relief.clear()
        relief.addAll(
            listOf(
                CmpRelief("f1", "Relief request", "Maya Santos", "Harbor Provincial Tour", "Today", "Broadcast - waiting for grant"),
                CmpRelief("f2", "Relief invite", "Ana Lim", "Harbor Provincial Tour", "Sat", "Invite waiting for reply"),
                CmpRelief("f3", "Relief duty", "Nadia Reyes", "Sunrise Clinic", "Sun", "Covering morning shift"),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        focusedBranchId.value = "b1"
        currentUserName.value = "Maya Santos"
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun branch(id: String): CmpBranch? = branches.firstOrNull { it.id == id }

    fun sessionsFor(branchId: String): List<CmpSession> = sessions.filter { it.branchId == branchId }

    fun grossFor(branchId: String): Int =
        sessions.filter { it.branchId == branchId && it.status == CmpSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }

    fun pendingCount(branchId: String): Int =
        sessions.count { it.branchId == branchId && it.status == CmpSessionStatus.PENDING && !it.voided }

    fun staffOn(branchId: String): Int =
        users.count { it.homeBranchId == branchId && it.clockedIn && !it.onboarding }

    fun setDayStatus(branchId: String, status: CmpDayStatus) {
        val idx = branches.indexOfFirst { it.id == branchId }
        if (idx >= 0) branches[idx] = branches[idx].copy(dayStatus = status)
    }

    fun log(action: String, record: String, reason: String = "") {
        auditSeq += 1
        audits.add(
            0,
            CmpAudit(
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
