package com.companyb.companyapp.proto.clinicallight

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class FakeSessionStatus(
    val label: String,
) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class FakeDayState(
    val label: String,
) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class FakeBranchKind(
    val label: String,
) {
    CLINIC("Clinic"),
    PROVINCIAL_TOUR("Provincial tour"),
    MEDICAL_MISSION("Medical mission"),
}

enum class FakeRemitKind(
    val label: String,
) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class FakeSubmission(
    val label: String,
) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
}

enum class FakeRole(
    val label: String,
    val summary: String,
) {
    ONBOARDING(
        "Onboarding",
        "Empty capability bundle — locked out of every surface until a role is granted.",
    ),
    PRACTITIONER(
        "Practitioner",
        "Logs sessions, manages inventory, views clients. Full access at home branches and checked-in relief days.",
    ),
    COORDINATOR(
        "Coordinator",
        "Owns finance and remittance. Sole editor of Past and Remitted records.",
    ),
    MANAGER(
        "Manager",
        "Coordinator powers plus user management and delegate assignment.",
    ),
    ACCOUNTANT(
        "Accountant",
        "Read-only sales and data across all branches. No edit capabilities.",
    ),
}

enum class FakeReliefKind(
    val label: String,
) {
    DUTY("Relief duty"),
    REQUEST("Relief request"),
    INVITE("Relief invite"),
}

data class FakeUser(
    val id: String,
    val name: String,
    val role: FakeRole,
    val homeBranchId: String,
    val slot: Int,
)

data class FakeBranch(
    val id: String,
    val name: String,
    val kind: FakeBranchKind,
)

data class FakeBranchDay(
    val date: String,
    val branchId: String,
    val state: FakeDayState,
)

data class FakeSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: FakeSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class FakeClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val anonymized: Boolean = false,
    val pendingSessionId: String? = null,
)

data class FakeRemittance(
    val id: String,
    val kind: FakeRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: FakeSubmission,
    val gross: Int,
    val deductions: Int,
    val note: String,
    val snapshotId: String? = null,
    val submittedAt: String? = null,
    val undoReason: String? = null,
) {
    val net: Int get() = gross - deductions
}

data class FakeProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
) {
    val total: Int get() = qty * unitPrice
}

data class FakeNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String? = null,
    val dayDate: String? = null,
    val read: Boolean = false,
)

data class FakeAuditEntry(
    val id: String,
    val time: String,
    val actor: String,
    val action: String,
    val target: String,
    val detail: String,
)

data class FakeRelief(
    val id: String,
    val kind: FakeReliefKind,
    val person: String,
    val branchId: String,
    val dayDate: String,
    val state: String,
)

class ClinicalLightStore {
    val currentUserId = mutableStateOf<String?>(null)
    val currentBranchId = mutableStateOf<String?>(null)
    val clockedIn = mutableStateOf(false)
    val reliefEdit = mutableStateOf(false)
    val homeDayFilter = mutableStateOf("2026-09-09")

    val users = mutableStateListOf<FakeUser>()
    val branches = mutableStateListOf<FakeBranch>()
    val days = mutableStateListOf<FakeBranchDay>()
    val sessions = mutableStateListOf<FakeSession>()
    val clients = mutableStateListOf<FakeClient>()
    val remittances = mutableStateListOf<FakeRemittance>()
    val productLines = mutableStateListOf<FakeProductLine>()
    val notifications = mutableStateListOf<FakeNotification>()
    val audit = mutableStateListOf<FakeAuditEntry>()
    val relief = mutableStateListOf<FakeRelief>()

    // Manual commission overrides: staff name -> included.
    val commissionIncluded = mutableStateListOf<String>()

    private var auditSeq = 100
    private var noteSeq = 100

    fun currentUser(): FakeUser? = users.firstOrNull { it.id == currentUserId.value }

    fun currentBranch(): FakeBranch? = branches.firstOrNull { it.id == currentBranchId.value }

    fun branchDay(branchId: String, date: String): FakeBranchDay? =
        days.firstOrNull { it.branchId == branchId && it.date == date }

    fun audit(actor: String, action: String, target: String, detail: String) {
        auditSeq += 1
        audit.add(
            0,
            FakeAuditEntry(
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
        notifications.add(
            0,
            FakeNotification(id = "N$noteSeq", title = title, body = body, branchId = branchId, dayDate = dayDate),
        )
    }
}

fun seedClinicalLightStore(): ClinicalLightStore {
    val store = ClinicalLightStore()
    store.users.addAll(
        listOf(
            FakeUser("u-amara", "Dra. Amara Santos", FakeRole.PRACTITIONER, "b-makati", 1),
            FakeUser("u-maria", "Maria Cruz", FakeRole.COORDINATOR, "b-makati", 2),
            FakeUser("u-liam", "Liam Tan", FakeRole.MANAGER, "b-cebu", 1),
            FakeUser("u-ana", "Ana Reyes", FakeRole.ACCOUNTANT, "b-makati", 3),
            FakeUser("u-new", "Ramon Dela Cruz", FakeRole.ONBOARDING, "b-makati", 4),
        ),
    )
    store.branches.addAll(
        listOf(
            FakeBranch("b-makati", "Makati Clinic", FakeBranchKind.CLINIC),
            FakeBranch("b-cebu", "Cebu Provincial Tour", FakeBranchKind.PROVINCIAL_TOUR),
            FakeBranch("b-mission", "Pasay Medical Mission", FakeBranchKind.MEDICAL_MISSION),
        ),
    )
    store.days.addAll(
        listOf(
            FakeBranchDay("2026-09-09", "b-makati", FakeDayState.OPEN),
            FakeBranchDay("2026-09-08", "b-makati", FakeDayState.PAST),
            FakeBranchDay("2026-09-07", "b-makati", FakeDayState.REMITTED),
            FakeBranchDay("2026-09-09", "b-cebu", FakeDayState.OPEN),
            FakeBranchDay("2026-09-09", "b-mission", FakeDayState.OPEN),
        ),
    )
    store.sessions.addAll(
        listOf(
            FakeSession(
                id = "S-101", time = "09:00", clientId = "c-1", clientName = "Josefa Ramos",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Follow-up",
                status = FakeSessionStatus.COMPLETED, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            FakeSession(
                id = "S-102", time = "10:00", clientId = "c-2", clientName = "Mark Villanueva",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Initial assessment",
                status = FakeSessionStatus.PENDING, price = 1500,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            FakeSession(
                id = "S-103", time = "10:30", clientId = "c-3", clientName = "Walk-in guest",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Walk-in",
                status = FakeSessionStatus.PENDING, price = 800,
                practitioners = "Maria Cruz", walkIn = true,
            ),
            FakeSession(
                id = "S-104", time = "11:00", clientId = "c-4", clientName = "Lorna Dy",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Follow-up",
                status = FakeSessionStatus.NO_SHOW, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            FakeSession(
                id = "S-105", time = "13:00", clientId = "c-5", clientName = "Paolo Aquino",
                branchId = "b-makati", dayDate = "2026-09-08", type = "Follow-up",
                status = FakeSessionStatus.CANCELLED, price = 1200,
                practitioners = "Maria Cruz", walkIn = false,
            ),
            FakeSession(
                id = "S-106", time = "14:00", clientId = "c-6", clientName = "Irene Ocampo",
                branchId = "b-makati", dayDate = "2026-09-08", type = "Follow-up",
                status = FakeSessionStatus.COMPLETED, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
                voided = true, voidReason = "Duplicate entry — kept for record.",
            ),
        ),
    )
    store.clients.addAll(
        listOf(
            FakeClient("c-1", "Josefa Ramos", 54, "F", "0917-111-2233", pendingSessionId = null),
            FakeClient("c-2", "Mark Villanueva", 38, "M", "0918-222-3344", pendingSessionId = "S-102"),
            FakeClient("c-3", "Walk-in guest", 41, "M", "0900-000-0000", pendingSessionId = "S-103"),
            FakeClient("c-4", "Lorna Dy", 47, "F", "0919-333-4455"),
            FakeClient("c-5", "Paolo Aquino", 29, "M", "0920-444-5566"),
            FakeClient("c-7", "Anonymized record #12", 60, "F", "withheld", anonymized = true),
        ),
    )
    store.remittances.addAll(
        listOf(
            FakeRemittance(
                id = "R-31", kind = FakeRemitKind.SESSION, branchId = "b-makati",
                dayDate = "2026-09-08", state = FakeSubmission.DRAFT,
                gross = 2400, deductions = 900, note = "Tuesday session income",
            ),
            FakeRemittance(
                id = "R-32", kind = FakeRemitKind.PRODUCT, branchId = "b-makati",
                dayDate = "2026-09-08", state = FakeSubmission.SUBMITTED,
                gross = 3600, deductions = 0, note = "Liniment + supports",
                snapshotId = "SNAP-8812", submittedAt = "2026-09-08 22:10 Asia/Manila",
            ),
            FakeRemittance(
                id = "R-30", kind = FakeRemitKind.SESSION, branchId = "b-makati",
                dayDate = "2026-09-07", state = FakeSubmission.SUBMITTED,
                gross = 4800, deductions = 1400, note = "Monday session income",
                snapshotId = "SNAP-8790", submittedAt = "2026-09-07 21:02 Asia/Manila",
            ),
        ),
    )
    store.productLines.addAll(
        listOf(
            FakeProductLine("p-1", "Herbal liniment 100ml", 8, 350),
            FakeProductLine("p-2", "Knee support", 2, 400),
        ),
    )
    store.notifications.addAll(
        listOf(
            FakeNotification(
                "N-3", "Relief request approved",
                "Maria Cruz granted your relief request at Makati Clinic for 2026-09-09.",
                branchId = "b-makati", dayDate = "2026-09-09",
            ),
            FakeNotification(
                "N-2", "Session reminder",
                "S-102 with Mark Villanueva starts at 10:00 today.",
                branchId = "b-makati", dayDate = "2026-09-09", read = true,
            ),
            FakeNotification(
                "N-1", "Remittance submitted",
                "R-32 product remittance for 2026-09-08 was submitted. Snapshot SNAP-8812 frozen.",
                branchId = "b-makati", dayDate = "2026-09-08", read = true,
            ),
        ),
    )
    store.audit.addAll(
        listOf(
            FakeAuditEntry("A-9", "08:52", "Maria Cruz", "INSERT", "sessions:S-103", "Walk-in session created at Makati Clinic."),
            FakeAuditEntry("A-8", "08:31", "Dra. Amara Santos", "UPDATE", "sessions:S-101", "PENDING -> COMPLETED, price 1200."),
            FakeAuditEntry("A-7", "08:05", "Dra. Amara Santos", "CLOCK_IN", "attendance:b-makati", "Clocked in at home branch."),
        ),
    )
    store.relief.addAll(
        listOf(
            FakeRelief("r-1", FakeReliefKind.DUTY, "Dra. Amara Santos", "b-cebu", "2026-09-09", "Edit access"),
            FakeRelief("r-2", FakeReliefKind.REQUEST, "Maria Cruz", "b-cebu", "2026-09-10", "Pending branch grant"),
            FakeRelief("r-3", FakeReliefKind.INVITE, "Ana Reyes", "b-makati", "2026-09-10", "Invite sent"),
        ),
    )
    store.commissionIncluded.addAll(listOf("Dra. Amara Santos", "Maria Cruz"))
    return store
}
