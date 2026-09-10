package com.companyb.companyapp.proto.tradingdesk

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class TdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class TdDayState { OPEN, PAST, REMITTED }

enum class TdRemitKind { SESSION, PRODUCT }

enum class TdRemitStatus { DRAFT, SUBMITTED }

data class TdBranch(
    val id: String,
    val name: String,
    val kind: String,
    val code: String,
)

data class TdUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val slot: Int,
    val onboarding: Boolean = false,
)

data class TdSession(
    val id: String,
    val symbol: String,
    val clientName: String,
    val branchId: String,
    val status: TdSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val practitioner: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class TdClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
    val note: String = "",
)

data class TdMail(
    val id: String,
    val wire: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class TdAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class TdRemittance(
    val id: String,
    val kind: TdRemitKind,
    val status: TdRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshotNo: String = "",
    val note: String = "",
    val submittedAt: String = "",
)

data class TdProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

data class TdReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val state: String = "OPEN",
)

data class TdReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val state: String = "OPEN",
)

object TradingDeskFakeRepo {
    val branches = listOf(
        TdBranch("b-qc", "QC Central", "CLINIC", "QC"),
        TdBranch("b-laguna", "Laguna Tour Stop 3", "PROVINCIAL_TOUR", "LAG"),
        TdBranch("b-tondo", "Tondo Medical Mission", "MEDICAL_MISSION", "TON"),
    )

    val directory = listOf(
        TdUser("u-coord", "R. Dizon", "Coordinator", "b-qc", 1),
        TdUser("u-prac", "J. Ramos", "Practitioner", "b-qc", 2),
        TdUser("u-prac2", "K. Uy", "Practitioner", "b-laguna", 2),
        TdUser("u-mgr", "M. Sy", "MANAGER", "b-laguna", 1),
        TdUser("u-acct", "L. Tan", "Accountant", "b-qc", 3),
        TdUser("u-onb", "New Hire", "ONBOARDING", "b-qc", 9, onboarding = true),
    )

    val currentUser = mutableStateOf(directory[0])
    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b-qc")
    val dayState = mutableStateOf(TdDayState.OPEN)
    val reliefEdit = mutableStateOf(false)

    val sessions = mutableStateListOf(
        TdSession("s-101", "QC.S101", "C. Aquino", "b-qc", TdSessionStatus.PENDING, "Follow-up", 850, false, "J. Ramos", false, "", "09:00"),
        TdSession("s-102", "QC.S102", "D. Cruz", "b-qc", TdSessionStatus.PENDING, "Initial", 1200, true, "", false, "", "09:20"),
        TdSession("s-103", "QC.S103", "E. Santos", "b-qc", TdSessionStatus.PENDING, "Follow-up", 850, false, "R. Dizon", false, "", "09:40"),
        TdSession("s-104", "QC.S104", "F. Reyes", "b-qc", TdSessionStatus.PENDING, "Maintenance", 700, true, "", false, "", "10:00"),
        TdSession("s-105", "QC.S105", "G. Lim", "b-qc", TdSessionStatus.COMPLETED, "Follow-up", 850, false, "R. Dizon", false, "", "08:00"),
        TdSession("s-106", "QC.S106", "H. Uy", "b-qc", TdSessionStatus.NO_SHOW, "Initial", 1200, false, "", false, "", "08:20"),
        TdSession("s-107", "QC.S107", "I. Gomez", "b-qc", TdSessionStatus.CANCELLED, "Maintenance", 700, false, "", false, "", "08:40"),
        TdSession("s-108", "QC.S108", "J. Cruz", "b-qc", TdSessionStatus.COMPLETED, "Initial", 1200, false, "J. Ramos", true, "Duplicate log", "08:55"),
        TdSession("s-201", "LAG.S201", "K. Sy", "b-laguna", TdSessionStatus.PENDING, "Follow-up", 900, false, "K. Uy", false, "", "10:30"),
        TdSession("s-202", "LAG.S202", "L. Go", "b-laguna", TdSessionStatus.COMPLETED, "Maintenance", 750, true, "K. Uy", false, "", "09:10"),
        TdSession("s-301", "TON.S301", "M. Dela", "b-tondo", TdSessionStatus.PENDING, "Initial", 0, true, "", false, "", "11:00"),
    )

    val clients = mutableStateListOf(
        TdClient("c-1", "C. Aquino", "F", 34),
        TdClient("c-2", "D. Cruz", "M", 41),
        TdClient("c-3", "E. Santos", "F", 28),
        TdClient("c-4", "F. Reyes", "M", 55),
        TdClient("c-5", "G. Lim", "F", 47),
        TdClient("c-6", "H. Uy", "M", 30, note = "Prefers morning slots"),
        TdClient("c-7", "X. Anon", "F", 39, anonymized = true),
        TdClient("c-8", "K. Sy", "M", 36),
    )

    val grossSeries = mapOf(
        "b-qc" to listOf(4200, 5100, 4800, 6300, 5900, 7100, 6800),
        "b-laguna" to listOf(1800, 2200, 2050, 2600, 2400, 2900, 2750),
        "b-tondo" to listOf(0, 400, 0, 650, 300, 800, 450),
    )

    val productLines = mutableStateListOf(
        TdProductLine("p-1", "Liniment 60ml", 6, 250),
        TdProductLine("p-2", "Hot pack rental", 3, 150),
        TdProductLine("p-3", "Support band", 2, 400),
    )

    val sessionDraft = mutableStateOf(1500)

    val remittances = mutableStateListOf(
        TdRemittance("r-1", TdRemitKind.SESSION, TdRemitStatus.SUBMITTED, 12400, "QC 09-09", "TD-0042", "Evening drop", "09-09 22:40"),
        TdRemittance("r-2", TdRemitKind.PRODUCT, TdRemitStatus.DRAFT, 0, "QC 09-10", "", "", ""),
    )

    val mailbox = mutableStateListOf(
        TdMail("m-1", "RELIEF", "Relief invite: Laguna Tour Stop 3", "M. Sy invites you for Sat 09-13. Accept to write the day grant.", "09-10", false),
        TdMail("m-2", "SESSION", "S105 filled at 08:00", "G. Lim COMPLETED +850. Tape updated.", "09-10", false),
        TdMail("m-3", "REMIT", "Snapshot TD-0042 sealed", "SESSION remittance for QC 09-09 submitted. Undo window 48h.", "09-09", true),
        TdMail("m-4", "RELIEF", "Request approved: QC Central", "R. Dizon granted your relief request for 09-10.", "09-10", true),
        TdMail("m-5", "SYSTEM", "Branch day rolls at 04:00", "Asia/Manila boundary. OPEN stays editable until rollover.", "09-09", true),
    )

    val audits = mutableStateListOf(
        TdAudit("a-1", "R. Dizon", "SUBMIT_REMITTANCE", "r-1 / TD-0042", "09-09 22:40", "Evening drop"),
        TdAudit("a-2", "J. Ramos", "UPDATE_SESSION", "s-105 -> COMPLETED", "09-10 08:02"),
        TdAudit("a-3", "J. Ramos", "VOID_SESSION", "s-108 voided", "09-10 08:58", "Duplicate log"),
        TdAudit("a-4", "M. Sy", "GRANT_RELIEF", "relief invite ri-1", "09-10 07:30", "Cover Saturday surge"),
        TdAudit("a-5", "System", "DAY_STATE", "QC 09-10 -> OPEN", "09-10 04:00", "04:00 Asia/Manila rollover"),
    )

    val invites = mutableStateListOf(
        TdReliefInvite("ri-1", "Laguna Tour Stop 3", "Sat 09-13"),
    )

    val requests = mutableStateListOf(
        TdReliefRequest("rr-1", "QC Central", "Today 09-10", true, "GRANTED"),
        TdReliefRequest("rr-2", "Tondo Medical Mission", "Sun 09-14", true),
    )

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun branchCode(id: String): String = branches.firstOrNull { it.id == id }?.code ?: "??"

    fun pendingFor(clientName: String): Int =
        sessions.count { it.clientName == clientName && it.status == TdSessionStatus.PENDING && !it.voided }

    fun dayGross(branchId: String): Int =
        sessions.filter { it.branchId == branchId && it.status == TdSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }

    fun stamp(actor: String, action: String, record: String, reason: String = "") {
        audits.add(0, TdAudit("a-${audits.size + 1}-${System.currentTimeMillis() % 1000}", actor, action, record, "09-10 now", reason))
    }
}
