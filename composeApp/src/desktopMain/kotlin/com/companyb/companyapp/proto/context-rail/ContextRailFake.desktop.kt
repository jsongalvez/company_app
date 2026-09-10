package com.companyb.companyapp.proto.contextrail

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class RailSessionStatus(val label: String) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

enum class RailDayState(val label: String) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

enum class RailRole(val label: String, val locked: Boolean = false) {
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("MANAGER"),
    ACCOUNTANT("Accountant"),
    ONBOARDING("ONBOARDING", locked = true),
}

enum class RailBranchKind(val label: String) {
    CLINIC("CLINIC"),
    PROVINCIAL_TOUR("PROVINCIAL_TOUR"),
    MEDICAL_MISSION("MEDICAL_MISSION"),
}

enum class RailScreen(val label: String) {
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAIL("Mailbox"),
    PROFILE("Profile"),
}

enum class RailRemitKind(val label: String) {
    SESSION("SESSION"),
    PRODUCT("PRODUCT"),
}

enum class RailSubmission(val label: String) {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
}

enum class RailReliefKind(val label: String) {
    DUTY("DUTY"),
    REQUEST("REQUEST"),
    INVITE("INVITE"),
}

data class RailUser(
    val id: String,
    val name: String,
    val role: RailRole,
    val homeBranchId: String,
    val slot: Int,
    val capabilities: List<String>,
)

data class RailBranch(
    val id: String,
    val name: String,
    val kind: RailBranchKind,
)

data class RailBranchDay(
    val date: String,
    val branchId: String,
    val state: RailDayState,
)

data class RailSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: RailSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class RailClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val pendingSessionId: String? = null,
    val anonymized: Boolean = false,
)

data class RailStaff(
    val id: String,
    val name: String,
    val role: RailRole,
    val slot: Int,
    val homeBranchId: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

data class RailNotice(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String?,
    val dayDate: String?,
    val read: Boolean = false,
)

data class RailAudit(
    val id: String,
    val time: String,
    val actor: String,
    val action: String,
    val target: String,
    val detail: String,
)

data class RailProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

data class RailRemittance(
    val id: String,
    val kind: RailRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: RailSubmission,
    val gross: Int,
    val deductions: Int,
    val note: String,
    val snapshotId: String? = null,
    val submittedAt: String? = null,
)

data class RailRelief(
    val id: String,
    val kind: RailReliefKind,
    val person: String,
    val branchId: String,
    val date: String,
    val note: String,
)

class ContextRailStore {
    val currentUserId = mutableStateOf<String?>(null)
    val currentBranchId = mutableStateOf<String?>(null)
    val currentDay = mutableStateOf("2026-09-09")
    val currentScreen = mutableStateOf(RailScreen.HOME)
    val clockedIn = mutableStateOf(false)
    val reliefEdit = mutableStateOf(false)

    val selectedSessionId = mutableStateOf<String?>(null)
    val selectedClientId = mutableStateOf<String?>(null)
    val selectedStaffId = mutableStateOf<String?>(null)
    val selectedNoticeId = mutableStateOf<String?>(null)
    val selectedRemitId = mutableStateOf<String?>(null)
    val selectedReliefId = mutableStateOf<String?>(null)
    val sessionFilter = mutableStateOf<String?>(null)

    val users = mutableStateListOf<RailUser>()
    val branches = mutableStateListOf<RailBranch>()
    val days = mutableStateListOf<RailBranchDay>()
    val sessions = mutableStateListOf<RailSession>()
    val clients = mutableStateListOf<RailClient>()
    val staff = mutableStateListOf<RailStaff>()
    val notices = mutableStateListOf<RailNotice>()
    val audits = mutableStateListOf<RailAudit>()
    val remittances = mutableStateListOf<RailRemittance>()
    val productLines = mutableStateListOf<RailProductLine>()
    val relief = mutableStateListOf<RailRelief>()
    val commissionIncluded = mutableStateListOf<String>()

    private var auditSeq = 40
    private var noteSeq = 40
    private var sessionSeq = 200
    private var remitSeq = 60

    fun currentUser(): RailUser? = users.firstOrNull { it.id == currentUserId.value }

    fun currentBranch(): RailBranch? = branches.firstOrNull { it.id == currentBranchId.value }

    fun branchDay(): RailBranchDay? =
        days.firstOrNull { it.branchId == currentBranchId.value && it.date == currentDay.value }

    fun audit(actor: String, action: String, target: String, detail: String) {
        auditSeq += 1
        audits.add(
            0,
            RailAudit(
                id = "A$auditSeq",
                time = "09:41 Asia/Manila",
                actor = actor,
                action = action,
                target = target,
                detail = detail,
            ),
        )
    }

    fun notify(title: String, body: String, branchId: String? = null, dayDate: String? = null) {
        noteSeq += 1
        notices.add(
            0,
            RailNotice(id = "N$noteSeq", title = title, body = body, branchId = branchId, dayDate = dayDate),
        )
    }

    fun nextSessionId(): String {
        sessionSeq += 1
        return "S-$sessionSeq"
    }

    fun nextRemitId(): String {
        remitSeq += 1
        return "R-$remitSeq"
    }
}

fun peso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

fun roleCapabilities(role: RailRole): List<String> = when (role) {
    RailRole.PRACTITIONER -> listOf("VIEW_BRANCH_DATA", "EDIT_BRANCH_DATA", "LOG_SESSION")
    RailRole.COORDINATOR -> listOf("VIEW_BRANCH_DATA", "EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "EDIT_PAST_DAY")
    RailRole.MANAGER -> listOf("VIEW_BRANCH_DATA", "EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "MANAGE_USERS", "ASSIGN_DELEGATE")
    RailRole.ACCOUNTANT -> listOf("VIEW_BRANCH_DATA")
    RailRole.ONBOARDING -> emptyList<String>()
}

fun seedContextRailStore(): ContextRailStore {
    val store = ContextRailStore()
    store.users.addAll(
        listOf(
            RailUser("u-amara", "Dra. Amara Santos", RailRole.PRACTITIONER, "b-makati", 1, roleCapabilities(RailRole.PRACTITIONER)),
            RailUser("u-maria", "Maria Cruz", RailRole.COORDINATOR, "b-makati", 2, roleCapabilities(RailRole.COORDINATOR)),
            RailUser("u-liam", "Liam Tan", RailRole.MANAGER, "b-cebu", 1, roleCapabilities(RailRole.MANAGER)),
            RailUser("u-ana", "Ana Reyes", RailRole.ACCOUNTANT, "b-makati", 3, roleCapabilities(RailRole.ACCOUNTANT)),
            RailUser("u-new", "K. Dela Pena", RailRole.ONBOARDING, "b-makati", 4, emptyList()),
        ),
    )
    store.branches.addAll(
        listOf(
            RailBranch("b-makati", "Makati Clinic", RailBranchKind.CLINIC),
            RailBranch("b-cebu", "Cebu Provincial Tour", RailBranchKind.PROVINCIAL_TOUR),
            RailBranch("b-mission", "Tondo Medical Mission", RailBranchKind.MEDICAL_MISSION),
        ),
    )
    store.days.addAll(
        listOf(
            RailBranchDay("2026-09-08", "b-makati", RailDayState.REMITTED),
            RailBranchDay("2026-09-09", "b-makati", RailDayState.OPEN),
            RailBranchDay("2026-09-10", "b-makati", RailDayState.PAST),
            RailBranchDay("2026-09-09", "b-cebu", RailDayState.OPEN),
            RailBranchDay("2026-09-09", "b-mission", RailDayState.OPEN),
        ),
    )
    store.sessions.addAll(
        listOf(
            RailSession("S-101", "09:00", "c-1", "Josefa Ramos", "b-makati", "2026-09-09", "Follow-up", RailSessionStatus.COMPLETED, 1200, "Dra. Amara Santos", false),
            RailSession("S-102", "10:00", "c-2", "Mark Villanueva", "b-makati", "2026-09-09", "Initial assessment", RailSessionStatus.PENDING, 1500, "Dra. Amara Santos", false),
            RailSession("S-103", "10:30", "c-3", "Walk-in guest", "b-makati", "2026-09-09", "Walk-in", RailSessionStatus.PENDING, 800, "Maria Cruz", true),
            RailSession("S-104", "11:00", "c-4", "Lorna Dy", "b-makati", "2026-09-09", "Follow-up", RailSessionStatus.NO_SHOW, 1200, "Dra. Amara Santos", false),
            RailSession("S-105", "13:00", "c-5", "Paolo Aquino", "b-makati", "2026-09-08", "Follow-up", RailSessionStatus.CANCELLED, 1200, "Maria Cruz", false),
            RailSession("S-106", "14:00", "c-6", "Irene Ocampo", "b-makati", "2026-09-08", "Follow-up", RailSessionStatus.COMPLETED, 1200, "Dra. Amara Santos", false, voided = true, voidReason = "Duplicate entry — kept for record."),
        ),
    )
    store.clients.addAll(
        listOf(
            RailClient("c-1", "Josefa Ramos", 54, "F", "0917-111-2233"),
            RailClient("c-2", "Mark Villanueva", 38, "M", "0918-222-3344", pendingSessionId = "S-102"),
            RailClient("c-3", "Walk-in guest", 41, "M", "0900-000-0000", pendingSessionId = "S-103"),
            RailClient("c-4", "Lorna Dy", 47, "F", "0919-333-4455"),
            RailClient("c-5", "Paolo Aquino", 29, "M", "0920-444-5566"),
            RailClient("c-7", "Record #12", 60, "F", "withheld", anonymized = true),
        ),
    )
    store.staff.addAll(
        listOf(
            RailStaff("s-1", "Dra. Amara Santos", RailRole.PRACTITIONER, 1, "b-makati", false, true),
            RailStaff("s-2", "Maria Cruz", RailRole.COORDINATOR, 2, "b-makati", false, true),
            RailStaff("s-3", "Ana Reyes", RailRole.ACCOUNTANT, 3, "b-makati", false, false),
            RailStaff("s-4", "K. Dela Pena", RailRole.ONBOARDING, 4, "b-makati", false, false),
            RailStaff("s-5", "J. Ramos (relief)", RailRole.PRACTITIONER, 9, "b-cebu", true, true),
        ),
    )
    store.remittances.addAll(
        listOf(
            RailRemittance("R-31", RailRemitKind.SESSION, "b-makati", "2026-09-08", RailSubmission.DRAFT, 2400, 900, "Tuesday session income"),
            RailRemittance("R-32", RailRemitKind.PRODUCT, "b-makati", "2026-09-08", RailSubmission.SUBMITTED, 3600, 0, "Liniment + supports", "SNAP-8812", "2026-09-08 22:10 Asia/Manila"),
            RailRemittance("R-30", RailRemitKind.SESSION, "b-makati", "2026-09-07", RailSubmission.SUBMITTED, 4800, 1400, "Monday session income", "SNAP-8790", "2026-09-07 21:02 Asia/Manila"),
        ),
    )
    store.productLines.addAll(
        listOf(
            RailProductLine("p-1", "Herbal liniment 100ml", 8, 350),
            RailProductLine("p-2", "Knee support", 2, 400),
        ),
    )
    store.notices.addAll(
        listOf(
            RailNotice("N-3", "Relief request approved", "Maria Cruz granted your relief duty at Makati Clinic for 2026-09-09.", "b-makati", "2026-09-09"),
            RailNotice("N-2", "Session reminder", "S-102 with Mark Villanueva starts at 10:00 today.", "b-makati", "2026-09-09", read = true),
            RailNotice("N-1", "Remittance submitted", "R-32 product remittance for 2026-09-08 was submitted. Snapshot SNAP-8812 frozen.", "b-makati", "2026-09-08", read = true),
        ),
    )
    store.audits.addAll(
        listOf(
            RailAudit("A-9", "08:52", "Maria Cruz", "INSERT", "sessions:S-103", "Walk-in session created at Makati Clinic."),
            RailAudit("A-8", "08:31", "Dra. Amara Santos", "UPDATE", "sessions:S-101", "PENDING -> COMPLETED, price 1200."),
            RailAudit("A-7", "08:05", "Dra. Amara Santos", "CLOCK_IN", "attendance:b-makati", "Clocked in at home branch."),
        ),
    )
    store.relief.addAll(
        listOf(
            RailRelief("r-1", RailReliefKind.DUTY, "J. Ramos (relief)", "b-makati", "2026-09-09", "Edit access granted"),
            RailRelief("r-2", RailReliefKind.REQUEST, "Maria Cruz", "b-cebu", "2026-09-10", "Pending branch grant"),
            RailRelief("r-3", RailReliefKind.INVITE, "Ana Reyes", "b-makati", "2026-09-10", "Invite sent — awaiting accept"),
        ),
    )
    store.commissionIncluded.addAll(listOf("Dra. Amara Santos", "Maria Cruz"))
    store.selectedSessionId.value = "S-102"
    store.selectedClientId.value = "c-2"
    store.selectedStaffId.value = "s-1"
    store.selectedNoticeId.value = "N-3"
    store.selectedRemitId.value = "R-31"
    store.selectedReliefId.value = "r-1"
    return store
}
