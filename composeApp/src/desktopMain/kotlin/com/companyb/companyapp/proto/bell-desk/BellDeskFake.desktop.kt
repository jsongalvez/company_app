package com.companyb.companyapp.proto.belldesk

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class BdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BdDayState { OPEN, PAST, REMITTED }

enum class BdRemitKind { SESSION, PRODUCT }

enum class BdRemitStatus { DRAFT, SUBMITTED }

data class BdBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class BdUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class BdSession(
    val id: String,
    val bellNo: String,
    val clientName: String,
    val branchId: String,
    val status: BdSessionStatus,
    val service: String,
    val price: Int,
    val walkIn: Boolean,
    val lane: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class BdClient(
    val id: String,
    val name: String,
    val detail: String,
    val genderAge: String,
    val anonymized: Boolean = false,
)

data class BdMail(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class BdAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class BdRemittance(
    val id: String,
    val kind: BdRemitKind,
    val status: BdRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshotNo: String = "",
    val note: String = "",
)

data class BdInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val state: String = "OPEN",
)

data class BdRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val state: String = "OPEN",
)

object BellDeskFakeRepo {
    val branches = listOf(
        BdBranch("b-qc", "QC Central", "CLINIC"),
        BdBranch("b-laguna", "Laguna Tour Stop 3", "PROVINCIAL_TOUR"),
        BdBranch("b-tondo", "Tondo Medical Mission", "MEDICAL_MISSION"),
    )

    val directory = listOf(
        BdUser("u-coord", "R. Dizon", "Coordinator", "b-qc"),
        BdUser("u-prac-a", "J. Ramos", "Practitioner", "b-qc"),
        BdUser("u-prac-b", "K. Aquino", "Practitioner", "b-qc"),
        BdUser("u-mgr", "M. Sy", "MANAGER", "b-laguna"),
        BdUser("u-acct", "L. Tan", "Accountant", "b-qc"),
        BdUser("u-onb", "New Hire", "ONBOARDING", "b-qc", onboarding = true),
    )

    val lanes = listOf("Lane 1 · J. Ramos", "Lane 2 · K. Aquino", "Lane 3 · Relief cover")

    val currentUser = mutableStateOf(directory[0])
    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b-qc")
    val dayState = mutableStateOf(BdDayState.OPEN)

    val sessions = mutableStateListOf(
        BdSession("s-201", "B-201", "C. Aquino", "b-qc", BdSessionStatus.PENDING, "Follow-up", 850, walkIn = false, time = "09:00"),
        BdSession("s-202", "B-202", "D. Cruz", "b-qc", BdSessionStatus.PENDING, "Initial", 1200, walkIn = true, time = "09:12"),
        BdSession("s-203", "B-203", "E. Santos", "b-qc", BdSessionStatus.PENDING, "Follow-up", 850, walkIn = false, lane = "Lane 1 · J. Ramos", time = "09:25"),
        BdSession("s-204", "B-204", "F. Reyes", "b-qc", BdSessionStatus.PENDING, "Maintenance", 700, walkIn = true, time = "09:40"),
        BdSession("s-205", "B-205", "G. Lim", "b-qc", BdSessionStatus.COMPLETED, "Follow-up", 850, walkIn = false, lane = "Lane 1 · J. Ramos", time = "08:00"),
        BdSession("s-206", "B-206", "H. Uy", "b-qc", BdSessionStatus.NO_SHOW, "Initial", 1200, walkIn = false, time = "08:20"),
        BdSession("s-207", "B-207", "I. Gomez", "b-qc", BdSessionStatus.CANCELLED, "Maintenance", 700, walkIn = false, time = "08:40"),
        BdSession("s-208", "B-208", "J. Perez", "b-laguna", BdSessionStatus.PENDING, "Initial", 1100, walkIn = false, time = "09:10"),
    )

    val clients = mutableStateListOf(
        BdClient("c-1", "C. Aquino", "Returning, knee program", "F · 41"),
        BdClient("c-2", "D. Cruz", "Walk-in, shoulder check", "M · 29"),
        BdClient("c-3", "E. Santos", "Returning, back program", "F · 55"),
        BdClient("c-4", "X. Anon-118", "Anonymized record, history kept", "M · 60", anonymized = true),
        BdClient("c-5", "F. Reyes", "Walk-in, ankle sprain", "M · 23"),
    )

    val mailbox = mutableStateListOf(
        BdMail("m-1", "Relief invite: Laguna Tour Stop 3", "A branch member invited you for Sat duty. Accept or decline from the bell counter.", "Sat"),
        BdMail("m-2", "Bell B-202 is waiting", "D. Cruz walked in at 09:12. Three taps: arrival, lane, ring.", "Today"),
        BdMail("m-3", "Snapshot SN-5510 sealed", "SESSION remittance for Fri sealed. Undo window runs 48h with a reason.", "Fri", read = true),
    )

    val auditTrail = mutableStateListOf(
        BdAudit("a-1", "R. Dizon", "CLOCK_IN", "Branch Day b-qc / today", "08:55"),
        BdAudit("a-2", "R. Dizon", "BELL_RING", "Walk-in B-202 D. Cruz", "09:12"),
        BdAudit("a-3", "M. Sy", "SUBMIT_REMITTANCE", "SESSION Fri / SN-5510", "18:02"),
    )

    val remittances = mutableStateListOf(
        BdRemittance("r-sess", BdRemitKind.SESSION, BdRemitStatus.DRAFT, 12400, "Today QC Central"),
        BdRemittance("r-prod", BdRemitKind.PRODUCT, BdRemitStatus.DRAFT, 3600, "Today QC Central"),
        BdRemittance("r-old", BdRemitKind.SESSION, BdRemitStatus.SUBMITTED, 18900, "Fri QC Central", snapshotNo = "SN-5510"),
    )

    val invites = mutableStateListOf(
        BdInvite("i-1", "Laguna Tour Stop 3", "Sat"),
        BdInvite("i-2", "Tondo Medical Mission", "Sun"),
    )

    val requests = mutableStateListOf(
        BdRequest("q-1", "Laguna Tour Stop 3", "Sat", mine = false),
        BdRequest("q-2", "QC Central", "Today", mine = true),
    )

    val team = listOf(
        Triple("Coordinator", 2, "Bell counter + remittance drawer"),
        Triple("Practitioner", 6, "Service lanes 1-3 plus relief cover"),
        Triple("MANAGER", 1, "Delegates + user grants"),
        Triple("Accountant", 1, "Read-only sales view"),
        Triple("ONBOARDING", 1, "Locked: 0 capabilities until MANAGE_USERS grants a role"),
    )

    private var bellSeq = 209
    private var auditSeq = 4
    private var snapshotSeq = 5511

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun pendingCountFor(clientName: String): Int =
        sessions.count { it.clientName == clientName && it.status == BdSessionStatus.PENDING }

    fun stamp(actor: String, action: String, record: String, reason: String = "") {
        auditTrail.add(0, BdAudit("a-${auditSeq++}", actor, action, record, "now", reason))
    }

    fun nextBell(): String = "B-${bellSeq++}"

    fun nextSnapshot(): String = "SN-${snapshotSeq++}"

    fun updateSession(id: String, change: (BdSession) -> BdSession) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) sessions[index] = change(sessions[index])
    }
}
