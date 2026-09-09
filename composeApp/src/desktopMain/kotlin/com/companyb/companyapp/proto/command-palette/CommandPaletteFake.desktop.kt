package com.companyb.companyapp.proto.commandpalette

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class CpSessionStatus(
    val label: String,
) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class CpDayState(
    val label: String,
) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class CpBranchKind(
    val label: String,
) {
    CLINIC("Clinic"),
    PROVINCIAL_TOUR("Provincial tour"),
    MEDICAL_MISSION("Medical mission"),
}

enum class CpRemitKind(
    val label: String,
) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class CpSubmission(
    val label: String,
) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
}

enum class CpRole(
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

enum class CpScreen(
    val label: String,
    val hint: String,
) {
    HOME("Home", "clock in, relief duty"),
    SESSIONS("Sessions", "list, detail, void"),
    CLIENTS("Clients", "global records"),
    FINANCE("Finance", "remittance flows"),
    TEAM("Team", "users and roles"),
    MAIL("Mailbox", "notifications"),
    AUDIT("Audit log", "every mutation"),
    PROFILE("Profile", "clock out, sign out"),
}

enum class CpReliefKind(
    val label: String,
) {
    DUTY("Relief duty"),
    REQUEST("Relief request"),
    INVITE("Relief invite"),
}

data class CpUser(
    val id: String,
    val name: String,
    val role: CpRole,
    val homeBranchId: String,
    val slot: Int,
)

data class CpBranch(
    val id: String,
    val name: String,
    val kind: CpBranchKind,
)

data class CpBranchDay(
    val date: String,
    val branchId: String,
    val state: CpDayState,
)

data class CpSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: CpSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class CpClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val anonymized: Boolean = false,
    val pendingSessionId: String? = null,
)

data class CpRemittance(
    val id: String,
    val kind: CpRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: CpSubmission,
    val gross: Int,
    val deductions: Int,
    val note: String,
    val snapshotId: String? = null,
    val submittedAt: String? = null,
    val undoReason: String? = null,
) {
    val net: Int get() = gross - deductions
}

data class CpProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
) {
    val total: Int get() = qty * unitPrice
}

data class CpNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String? = null,
    val dayDate: String? = null,
    val read: Boolean = false,
)

data class CpAuditEntry(
    val id: String,
    val time: String,
    val actor: String,
    val action: String,
    val target: String,
    val detail: String,
)

data class CpRelief(
    val id: String,
    val kind: CpReliefKind,
    val person: String,
    val branchId: String,
    val dayDate: String,
    val state: String,
)

data class CpRecent(
    val kind: String,
    val label: String,
    val detail: String,
    val targetScreen: CpScreen,
    val targetId: String,
)

class CpStore {
    val currentUserId = mutableStateOf<String?>(null)
    val currentBranchId = mutableStateOf<String?>(null)
    val clockedIn = mutableStateOf(false)
    val reliefEdit = mutableStateOf(false)
    val screen = mutableStateOf(CpScreen.HOME)
    val dayFilter = mutableStateOf("2026-09-09")

    val paletteOpen = mutableStateOf(false)
    val paletteQuery = mutableStateOf("")
    val paletteIndex = mutableStateOf(0)

    val selectedSessionId = mutableStateOf<String?>(null)
    val selectedClientId = mutableStateOf<String?>(null)
    val sessionFilter = mutableStateOf<CpSessionStatus?>(null)
    val clientQuery = mutableStateOf("")
    val auditQuery = mutableStateOf("")
    val financeKind = mutableStateOf(CpRemitKind.SESSION)

    val voidTargetId = mutableStateOf<String?>(null)
    val voidReason = mutableStateOf("")
    val createOpen = mutableStateOf(false)
    val createClientId = mutableStateOf<String?>(null)
    val createTime = mutableStateOf("15:00")
    val undoTargetId = mutableStateOf<String?>(null)
    val undoReason = mutableStateOf("")
    val reliefNote = mutableStateOf("")

    val users = mutableStateListOf<CpUser>()
    val branches = mutableStateListOf<CpBranch>()
    val days = mutableStateListOf<CpBranchDay>()
    val sessions = mutableStateListOf<CpSession>()
    val clients = mutableStateListOf<CpClient>()
    val remittances = mutableStateListOf<CpRemittance>()
    val productLines = mutableStateListOf<CpProductLine>()
    val notifications = mutableStateListOf<CpNotification>()
    val audit = mutableStateListOf<CpAuditEntry>()
    val relief = mutableStateListOf<CpRelief>()
    val recent = mutableStateListOf<CpRecent>()

    val commissionIncluded = mutableStateListOf<String>()

    private var auditSeq = 100
    private var noteSeq = 100
    private var sessionSeq = 200

    fun currentUser(): CpUser? = users.firstOrNull { it.id == currentUserId.value }

    fun currentBranch(): CpBranch? = branches.firstOrNull { it.id == currentBranchId.value }

    fun branchDay(
        branchId: String,
        date: String,
    ): CpBranchDay? = days.firstOrNull { it.branchId == branchId && it.date == date }

    fun unreadCount(): Int = notifications.count { !it.read }

    fun pendingCount(): Int = sessions.count { it.status == CpSessionStatus.PENDING && !it.voided }

    fun audit(
        actor: String,
        action: String,
        target: String,
        detail: String,
    ) {
        auditSeq += 1
        audit.add(
            0,
            CpAuditEntry(
                id = "A$auditSeq",
                time = "09:41 Asia/Manila",
                actor = actor,
                action = action,
                target = target,
                detail = detail,
            ),
        )
    }

    fun notify(
        title: String,
        body: String,
        branchId: String? = null,
        dayDate: String? = null,
    ) {
        noteSeq += 1
        notifications.add(
            0,
            CpNotification(id = "N$noteSeq", title = title, body = body, branchId = branchId, dayDate = dayDate),
        )
    }

    fun touchRecent(entry: CpRecent) {
        recent.removeAll { it.targetId == entry.targetId && it.kind == entry.kind }
        recent.add(0, entry)
        while (recent.size > 6) recent.removeAt(recent.size - 1)
    }

    fun nextSessionId(): String {
        sessionSeq += 1
        return "S-$sessionSeq"
    }
}

fun fuzzyScore(
    query: String,
    text: String,
): Int? {
    val q = query.lowercase()
    val t = text.lowercase()
    if (q.isEmpty()) return 0
    var ti = 0
    var score = 0
    var lastHit = -1
    for (ch in q) {
        var found = -1
        var i = ti
        while (i < t.length) {
            if (t[i] == ch) {
                found = i
                break
            }
            i += 1
        }
        if (found < 0) return null
        if (lastHit >= 0) score += found - lastHit - 1
        score += found / 8
        lastHit = found
        ti = found + 1
    }
    return score
}

fun seedCpStore(): CpStore {
    val store = CpStore()
    store.users.addAll(
        listOf(
            CpUser("u-amara", "Dra. Amara Santos", CpRole.PRACTITIONER, "b-makati", 1),
            CpUser("u-maria", "Maria Cruz", CpRole.COORDINATOR, "b-makati", 2),
            CpUser("u-liam", "Liam Tan", CpRole.MANAGER, "b-cebu", 1),
            CpUser("u-ana", "Ana Reyes", CpRole.ACCOUNTANT, "b-makati", 3),
            CpUser("u-new", "Ramon Dela Cruz", CpRole.ONBOARDING, "b-makati", 4),
        ),
    )
    store.branches.addAll(
        listOf(
            CpBranch("b-makati", "Makati Clinic", CpBranchKind.CLINIC),
            CpBranch("b-cebu", "Cebu Provincial Tour", CpBranchKind.PROVINCIAL_TOUR),
            CpBranch("b-mission", "Pasay Medical Mission", CpBranchKind.MEDICAL_MISSION),
        ),
    )
    store.days.addAll(
        listOf(
            CpBranchDay("2026-09-09", "b-makati", CpDayState.OPEN),
            CpBranchDay("2026-09-08", "b-makati", CpDayState.PAST),
            CpBranchDay("2026-09-07", "b-makati", CpDayState.REMITTED),
            CpBranchDay("2026-09-09", "b-cebu", CpDayState.OPEN),
            CpBranchDay("2026-09-09", "b-mission", CpDayState.OPEN),
        ),
    )
    store.sessions.addAll(
        listOf(
            CpSession(
                id = "S-101",
                time = "09:00",
                clientId = "c-1",
                clientName = "Josefa Ramos",
                branchId = "b-makati",
                dayDate = "2026-09-09",
                type = "Follow-up",
                status = CpSessionStatus.COMPLETED,
                price = 1200,
                practitioners = "Dra. Amara Santos",
                walkIn = false,
            ),
            CpSession(
                id = "S-102",
                time = "10:00",
                clientId = "c-2",
                clientName = "Mark Villanueva",
                branchId = "b-makati",
                dayDate = "2026-09-09",
                type = "Initial assessment",
                status = CpSessionStatus.PENDING,
                price = 1500,
                practitioners = "Dra. Amara Santos",
                walkIn = false,
            ),
            CpSession(
                id = "S-103",
                time = "10:30",
                clientId = "c-3",
                clientName = "Walk-in guest",
                branchId = "b-makati",
                dayDate = "2026-09-09",
                type = "Walk-in",
                status = CpSessionStatus.PENDING,
                price = 800,
                practitioners = "Maria Cruz",
                walkIn = true,
            ),
            CpSession(
                id = "S-104",
                time = "11:00",
                clientId = "c-4",
                clientName = "Lorna Dy",
                branchId = "b-makati",
                dayDate = "2026-09-09",
                type = "Follow-up",
                status = CpSessionStatus.NO_SHOW,
                price = 1200,
                practitioners = "Dra. Amara Santos",
                walkIn = false,
            ),
            CpSession(
                id = "S-105",
                time = "13:00",
                clientId = "c-5",
                clientName = "Paolo Aquino",
                branchId = "b-makati",
                dayDate = "2026-09-08",
                type = "Follow-up",
                status = CpSessionStatus.CANCELLED,
                price = 1200,
                practitioners = "Maria Cruz",
                walkIn = false,
            ),
            CpSession(
                id = "S-106",
                time = "14:00",
                clientId = "c-6",
                clientName = "Irene Ocampo",
                branchId = "b-makati",
                dayDate = "2026-09-08",
                type = "Follow-up",
                status = CpSessionStatus.COMPLETED,
                price = 1200,
                practitioners = "Dra. Amara Santos",
                walkIn = false,
                voided = true,
                voidReason = "Duplicate entry — kept for record.",
            ),
        ),
    )
    store.clients.addAll(
        listOf(
            CpClient("c-1", "Josefa Ramos", 54, "F", "0917-111-2233", pendingSessionId = null),
            CpClient("c-2", "Mark Villanueva", 38, "M", "0918-222-3344", pendingSessionId = "S-102"),
            CpClient("c-3", "Walk-in guest", 41, "M", "0900-000-0000", pendingSessionId = "S-103"),
            CpClient("c-4", "Lorna Dy", 47, "F", "0919-333-4455"),
            CpClient("c-5", "Paolo Aquino", 29, "M", "0920-444-5566"),
            CpClient("c-6", "Irene Ocampo", 44, "F", "0921-555-6677"),
            CpClient("c-7", "Anonymized record #12", 60, "F", "withheld", anonymized = true),
        ),
    )
    store.remittances.addAll(
        listOf(
            CpRemittance(
                id = "R-31",
                kind = CpRemitKind.SESSION,
                branchId = "b-makati",
                dayDate = "2026-09-08",
                state = CpSubmission.DRAFT,
                gross = 2400,
                deductions = 900,
                note = "Tuesday session income",
            ),
            CpRemittance(
                id = "R-32",
                kind = CpRemitKind.PRODUCT,
                branchId = "b-makati",
                dayDate = "2026-09-08",
                state = CpSubmission.SUBMITTED,
                gross = 3600,
                deductions = 0,
                note = "Liniment + supports",
                snapshotId = "SNAP-8812",
                submittedAt = "2026-09-08 22:10 Asia/Manila",
            ),
            CpRemittance(
                id = "R-30",
                kind = CpRemitKind.SESSION,
                branchId = "b-makati",
                dayDate = "2026-09-07",
                state = CpSubmission.SUBMITTED,
                gross = 4800,
                deductions = 1400,
                note = "Monday session income",
                snapshotId = "SNAP-8790",
                submittedAt = "2026-09-07 21:02 Asia/Manila",
            ),
        ),
    )
    store.productLines.addAll(
        listOf(
            CpProductLine("p-1", "Herbal liniment 100ml", 8, 350),
            CpProductLine("p-2", "Knee support", 2, 400),
        ),
    )
    store.notifications.addAll(
        listOf(
            CpNotification(
                "N-3",
                "Relief request approved",
                "Maria Cruz granted your relief request at Makati Clinic for 2026-09-09.",
                branchId = "b-makati",
                dayDate = "2026-09-09",
            ),
            CpNotification(
                "N-2",
                "Session reminder",
                "S-102 with Mark Villanueva starts at 10:00 today.",
                branchId = "b-makati",
                dayDate = "2026-09-09",
                read = true,
            ),
            CpNotification(
                "N-1",
                "Remittance submitted",
                "R-32 product remittance for 2026-09-08 was submitted. Snapshot SNAP-8812 frozen.",
                branchId = "b-makati",
                dayDate = "2026-09-08",
                read = true,
            ),
        ),
    )
    store.audit.addAll(
        listOf(
            CpAuditEntry(
                "A-9",
                "08:52",
                "Maria Cruz",
                "INSERT",
                "sessions:S-103",
                "Walk-in session created at Makati Clinic.",
            ),
            CpAuditEntry(
                "A-8",
                "08:31",
                "Dra. Amara Santos",
                "UPDATE",
                "sessions:S-101",
                "PENDING -> COMPLETED, price 1200.",
            ),
            CpAuditEntry(
                "A-7",
                "08:05",
                "Dra. Amara Santos",
                "CLOCK_IN",
                "attendance:b-makati",
                "Clocked in at home branch.",
            ),
        ),
    )
    store.relief.addAll(
        listOf(
            CpRelief("r-1", CpReliefKind.DUTY, "Dra. Amara Santos", "b-cebu", "2026-09-09", "Edit access"),
            CpRelief("r-2", CpReliefKind.REQUEST, "Maria Cruz", "b-cebu", "2026-09-10", "Pending branch grant"),
            CpRelief("r-3", CpReliefKind.INVITE, "Ana Reyes", "b-makati", "2026-09-10", "Invite sent"),
        ),
    )
    store.commissionIncluded.addAll(listOf("Dra. Amara Santos", "Maria Cruz"))
    return store
}
