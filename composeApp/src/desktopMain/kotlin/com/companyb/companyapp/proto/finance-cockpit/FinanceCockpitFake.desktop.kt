package com.companyb.companyapp.proto.financecockpit

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class CockpitRole(
    val label: String,
    val summary: String,
) {
    ONBOARDING(
        "Onboarding",
        "Empty capability bundle — locked out of every surface until a real role is granted.",
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

enum class CockpitSessionStatus(
    val label: String,
) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class CockpitDayState(
    val label: String,
) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class CockpitBranchKind(
    val label: String,
) {
    CLINIC("Clinic"),
    PROVINCIAL_TOUR("Provincial tour"),
    MEDICAL_MISSION("Medical mission"),
}

enum class CockpitRemitKind(
    val label: String,
) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class CockpitSubmission(
    val label: String,
) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
}

enum class CockpitReliefState(
    val label: String,
) {
    LIVE("Live"),
    GRANTED("Granted"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn"),
}

enum class CockpitScreen(
    val label: String,
) {
    HOME("Cockpit"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit log"),
    PROFILE("Profile"),
}

data class CockpitUser(
    val id: String,
    val name: String,
    val role: CockpitRole,
    val homeBranchId: String,
    val slot: Int,
)

data class CockpitBranch(
    val id: String,
    val name: String,
    val kind: CockpitBranchKind,
)

data class CockpitBranchDay(
    val date: String,
    val branchId: String,
    val state: CockpitDayState,
)

data class CockpitSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: CockpitSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class CockpitClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val anonymized: Boolean = false,
)

data class CockpitProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

data class CockpitRemittance(
    val id: String,
    val kind: CockpitRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: CockpitSubmission,
    val frozenTotal: Int? = null,
    val submittedAgo: String? = null,
    val undoLeft: String? = null,
)

data class CockpitNotification(
    val id: String,
    val toUserId: String,
    val title: String,
    val body: String,
    val branchId: String? = null,
    val dayDate: String? = null,
    val read: Boolean = false,
)

data class CockpitAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val detail: String,
)

data class CockpitInvite(
    val id: String,
    val branchId: String,
    val toUserId: String,
    val date: String,
    val accepted: Boolean? = null,
)

data class CockpitRequest(
    val id: String,
    val requesterId: String,
    val branchId: String,
    val date: String,
    val state: CockpitReliefState,
)

class CockpitStore {
    val currentUserId = mutableStateOf<String?>(null)
    val currentBranchId = mutableStateOf<String?>(null)
    val selectedDay = mutableStateOf("2026-09-09")
    val screen = mutableStateOf(CockpitScreen.HOME)
    val clockedIn = mutableStateListOf<String>()
    val reliefEdit = mutableStateListOf<String>()
    val users = mutableStateListOf<CockpitUser>()
    val branches = mutableStateListOf<CockpitBranch>()
    val days = mutableStateListOf<CockpitBranchDay>()
    val sessions = mutableStateListOf<CockpitSession>()
    val clients = mutableStateListOf<CockpitClient>()
    val productLines = mutableStateListOf<CockpitProductLine>()
    val remittances = mutableStateListOf<CockpitRemittance>()
    val notifications = mutableStateListOf<CockpitNotification>()
    val audits = mutableStateListOf<CockpitAudit>()
    val invites = mutableStateListOf<CockpitInvite>()
    val requests = mutableStateListOf<CockpitRequest>()
    val includedInSplit = mutableStateListOf<String>()

    fun currentUser(): CockpitUser? = users.firstOrNull { it.id == currentUserId.value }

    fun currentBranch(): CockpitBranch? = branches.firstOrNull { it.id == currentBranchId.value }

    fun dayState(branchId: String, date: String): CockpitDayState =
        days.firstOrNull { it.branchId == branchId && it.date == date }?.state ?: CockpitDayState.OPEN

    fun audit(actor: String, action: String, record: String, detail: String) {
        audits.add(
            0,
            CockpitAudit(
                id = "a${audits.size + 1}-${System.nanoTime() % 100000}",
                actor = actor,
                action = action,
                record = record,
                detail = detail,
            ),
        )
    }

    fun pendingFor(clientId: String): CockpitSession? =
        sessions.firstOrNull { it.clientId == clientId && it.status == CockpitSessionStatus.PENDING && !it.voided }

    fun sessionIncome(branchId: String, date: String): Int =
        sessions
            .filter { it.branchId == branchId && it.dayDate == date && !it.voided && it.status == CockpitSessionStatus.COMPLETED }
            .sumOf { it.price }

    fun productIncome(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun markAllInboxRead(userId: String) {
        for (i in notifications.indices) {
            val n = notifications[i]
            if (n.toUserId == userId && !n.read) notifications[i] = n.copy(read = true)
        }
    }
}

fun seedCockpitStore(): CockpitStore {
    val store = CockpitStore()
    store.branches.addAll(
        listOf(
            CockpitBranch("b-manila", "Manila Flagship", CockpitBranchKind.CLINIC),
            CockpitBranch("b-cebu", "Cebu Provincial Tour", CockpitBranchKind.PROVINCIAL_TOUR),
        ),
    )
    store.users.addAll(
        listOf(
            CockpitUser("u-onboard", "Sam Reyes", CockpitRole.ONBOARDING, "b-manila", 5),
            CockpitUser("u-prax", "Dr. Lina Cruz", CockpitRole.PRACTITIONER, "b-manila", 1),
            CockpitUser("u-coord", "Mara Villanueva", CockpitRole.COORDINATOR, "b-manila", 2),
            CockpitUser("u-mgr", "Jose Ramos", CockpitRole.MANAGER, "b-cebu", 1),
            CockpitUser("u-acct", "Ana Lim", CockpitRole.ACCOUNTANT, "b-manila", 3),
        ),
    )
    store.days.addAll(
        listOf(
            CockpitBranchDay("2026-09-09", "b-manila", CockpitDayState.OPEN),
            CockpitBranchDay("2026-09-08", "b-manila", CockpitDayState.PAST),
            CockpitBranchDay("2026-09-07", "b-manila", CockpitDayState.REMITTED),
            CockpitBranchDay("2026-09-09", "b-cebu", CockpitDayState.OPEN),
            CockpitBranchDay("2026-09-08", "b-cebu", CockpitDayState.PAST),
        ),
    )
    store.clients.addAll(
        listOf(
            CockpitClient("c-1", "Rosa Aquino", 54, "F", "0917-100-0001"),
            CockpitClient("c-2", "Ben Torres", 41, "M", "0917-100-0002"),
            CockpitClient("c-3", "Cora Diaz", 37, "F", "0917-100-0003"),
            CockpitClient("c-4", "Dan Padilla", 29, "M", "0917-100-0004"),
            CockpitClient("c-9", "Anonymized record #9", 45, "F", "withheld", anonymized = true),
        ),
    )
    store.sessions.addAll(
        listOf(
            CockpitSession("s-1", "09:00", "c-1", "Rosa Aquino", "b-manila", "2026-09-09", "Standard", CockpitSessionStatus.PENDING, 1200, "Lina Cruz", false),
            CockpitSession("s-2", "10:00", "c-2", "Ben Torres", "b-manila", "2026-09-09", "Follow-up", CockpitSessionStatus.COMPLETED, 950, "Lina Cruz", false),
            CockpitSession("s-3", "11:00", "c-3", "Cora Diaz", "b-manila", "2026-09-09", "Walk-in", CockpitSessionStatus.PENDING, 800, "Mara Villanueva", true),
            CockpitSession("s-4", "13:00", "c-4", "Dan Padilla", "b-manila", "2026-09-08", "Standard", CockpitSessionStatus.NO_SHOW, 1200, "Lina Cruz", false),
            CockpitSession("s-5", "14:00", "c-1", "Rosa Aquino", "b-manila", "2026-09-08", "Follow-up", CockpitSessionStatus.CANCELLED, 950, "Lina Cruz", false),
            CockpitSession("s-6", "15:00", "c-2", "Ben Torres", "b-manila", "2026-09-09", "Standard", CockpitSessionStatus.COMPLETED, 1100, "Jose Ramos", false, voided = true, voidReason = "Duplicate entry"),
        ),
    )
    store.productLines.addAll(
        listOf(
            CockpitProductLine("p-1", "Herbal balm 50g", 6, 350),
            CockpitProductLine("p-2", "Support strap", 3, 850),
            CockpitProductLine("p-3", "Heat patch x10", 10, 120),
        ),
    )
    store.remittances.addAll(
        listOf(
            CockpitRemittance("r-sess", CockpitRemitKind.SESSION, "b-manila", "2026-09-09", CockpitSubmission.DRAFT),
            CockpitRemittance("r-prod", CockpitRemitKind.PRODUCT, "b-manila", "2026-09-09", CockpitSubmission.DRAFT),
            CockpitRemittance("r-old", CockpitRemitKind.SESSION, "b-manila", "2026-09-07", CockpitSubmission.SUBMITTED, frozenTotal = 8450, submittedAgo = "submitted 6h ago", undoLeft = "42h left to undo"),
        ),
    )
    store.notifications.addAll(
        listOf(
            CockpitNotification("n-1", "u-coord", "Relief request: Cebu needs cover", "Lina Cruz asks for relief access at Cebu Provincial Tour on 2026-09-10.", "b-cebu", "2026-09-10"),
            CockpitNotification("n-2", "u-coord", "Snapshot frozen: 2026-09-07 SESSION", "Remittance r-old submitted. Frozen at ₱8,450. Undo window open for 48h with a reason."),
            CockpitNotification("n-3", "u-coord", "Branch day turned PAST", "2026-09-08 at Manila Flagship is now PAST. Coordinator-only edits.", "b-manila", "2026-09-08", read = true),
            CockpitNotification("n-4", "u-prax", "Reminder: Rosa Aquino at 09:00", "PENDING session s-1 today at Manila Flagship.", "b-manila", "2026-09-09"),
        ),
    )
    store.invites.addAll(
        listOf(
            CockpitInvite("i-1", "b-cebu", "u-coord", "2026-09-10"),
        ),
    )
    store.requests.addAll(
        listOf(
            CockpitRequest("q-1", "u-prax", "b-cebu", "2026-09-10", CockpitReliefState.LIVE),
        ),
    )
    store.includedInSplit.addAll(listOf("u-prax", "u-coord"))
    store.audits.addAll(
        listOf(
            CockpitAudit("a2", "Mara Villanueva", "SUBMIT", "remittance:r-old", "SESSION snapshot frozen at ₱8,450 for 2026-09-07."),
            CockpitAudit("a1", "System", "SEED", "branch-day:2026-09-08", "Day turned PAST at the 04:00 Asia/Manila boundary."),
        ),
    )
    return store
}
