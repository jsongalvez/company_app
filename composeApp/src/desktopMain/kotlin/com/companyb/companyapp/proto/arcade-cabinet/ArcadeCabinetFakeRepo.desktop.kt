package com.companyb.companyapp.proto.arcadecabinet

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class CabDayStatus { OPEN, PAST, REMITTED }

enum class CabSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class CabRemitKind { SESSION, PRODUCT }

enum class CabRemitStatus { DRAFT, SUBMITTED }

data class CabBranch(
    val id: String,
    val name: String,
    val kind: String,
    val highScore: Int,
)

data class CabUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class CabSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: CabSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class CabClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class CabNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class CabAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class CabRemittance(
    val id: String,
    val kind: CabRemitKind,
    val status: CabRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
    val undoable: Boolean = true,
)

data class CabReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

data class CabProductLine(
    val id: String,
    val name: String,
    val price: Int,
    val qty: Int,
)

object ArcadeCabinetFakeRepo {
    val branches = mutableStateListOf<CabBranch>()
    val users = mutableStateListOf<CabUser>()
    val sessions = mutableStateListOf<CabSession>()
    val clients = mutableStateListOf<CabClient>()
    val notes = mutableStateListOf<CabNote>()
    val audits = mutableStateListOf<CabAudit>()
    val remittances = mutableStateListOf<CabRemittance>()
    val relief = mutableStateListOf<CabReliefItem>()
    val products = mutableStateListOf<CabProductLine>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val dayStatus = mutableStateOf(CabDayStatus.OPEN)
    val currentUserName = mutableStateOf("Maya Santos")
    val coins = mutableStateOf(0)

    init {
        reset()
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun reset() {
        branches.clear()
        branches.addAll(
            listOf(
                CabBranch("b1", "Sunrise Clinic", "CLINIC", 98250),
                CabBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR", 76400),
                CabBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION", 61100),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                CabUser("u1", "Maya Santos", "Practitioner", "b1"),
                CabUser("u2", "Jose Ramos", "Coordinator", "b1"),
                CabUser("u3", "Ana Lim", "MANAGER", "b2"),
                CabUser("u4", "Rico Tan", "Accountant", "b1"),
                CabUser("u5", "Lena Cruz", "ONBOARDING", "b1", onboarding = true),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                CabSession("s1", "Paolo Aquino", "b1", CabSessionStatus.PENDING, "Back Rehab", 1200, false, time = "09:00"),
                CabSession("s2", "Katrina Uy", "b1", CabSessionStatus.COMPLETED, "Sports Recovery", 1500, false, time = "10:30"),
                CabSession("s3", "Walk-in Player", "b1", CabSessionStatus.PENDING, "Quick Relief", 800, true, time = "11:15"),
                CabSession("s4", "Marco Dela Cruz", "b2", CabSessionStatus.NO_SHOW, "Posture Fix", 1000, false, time = "13:00"),
                CabSession("s5", "Jenny Ong", "b1", CabSessionStatus.CANCELLED, "Back Rehab", 1200, false, time = "14:00"),
                CabSession("s6", "Sam Villanueva", "b3", CabSessionStatus.COMPLETED, "Mission Screen", 0, true, time = "15:30"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                CabClient("c1", "Paolo Aquino", "M, 34 - Back Rehab history", true),
                CabClient("c2", "Katrina Uy", "F, 28 - Sports Recovery history", false),
                CabClient("c3", "Marco Dela Cruz", "M, 41 - Posture Fix history", false),
                CabClient("c4", "Jenny Ong", "F, 36 - Back Rehab history", false),
                CabClient("c5", "Sam Villanueva", "M, 25 - Mission Screen history", false),
            ),
        )
        notes.clear()
        notes.addAll(
            listOf(
                CabNote("n1", "Relief grant: Harbor day", "Ana Lim granted your relief request at Harbor Provincial Tour for today.", false, "today"),
                CabNote("n2", "Snapshot sealed", "SESSION remittance snapshot sealed for Sunrise Clinic yesterday.", true, "yesterday"),
                CabNote("n3", "Invite: Lingap mission", "You are invited to relief duty at Lingap Medical Mission tomorrow.", false, "tomorrow"),
            ),
        )
        audits.clear()
        audits.addAll(
            listOf(
                CabAudit("a1", "Jose Ramos", "REMIT_SUBMIT", "SESSION remittance r1", "08:12", ""),
                CabAudit("a2", "Maya Santos", "SESSION_VOID", "Session s5", "09:40", "Client asked to rebook"),
            ),
        )
        remittances.clear()
        remittances.addAll(
            listOf(
                CabRemittance("r1", CabRemitKind.SESSION, CabRemitStatus.SUBMITTED, 5400, "Sunrise Clinic - today", "net=5400 after comp+expenses", "08:12", true),
                CabRemittance("r2", CabRemitKind.PRODUCT, CabRemitStatus.DRAFT, 2300, "Sunrise Clinic - today"),
            ),
        )
        relief.clear()
        relief.addAll(
            listOf(
                CabReliefItem("f1", "REQUEST", "Maya Santos", "Harbor Provincial Tour", "today", "GRANTED"),
                CabReliefItem("f2", "INVITE", "Rico Tan", "Lingap Medical Mission", "tomorrow", "OPEN"),
                CabReliefItem("f3", "REQUEST", "Sam Villanueva", "Sunrise Clinic", "today", "OPEN"),
            ),
        )
        products.clear()
        products.addAll(
            listOf(
                CabProductLine("p1", "Heat pack", 450, 3),
                CabProductLine("p2", "Resistance band", 350, 2),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        dayStatus.value = CabDayStatus.OPEN
        currentUserName.value = "Maya Santos"
        coins.value = 0
    }

    fun log(actor: String, action: String, record: String, whenText: String, reason: String = "") {
        audits.add(0, CabAudit("a${audits.size + 1}-${whenText.hashCode()}", actor, action, record, whenText, reason))
    }

    fun sessionTotal(branchId: String): Int =
        sessions.filter { it.branchId == branchId && it.status == CabSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }

    fun productTotal(): Int = products.sumOf { it.price * it.qty }
}
