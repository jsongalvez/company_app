package com.companyb.companyapp.proto.coachmarks

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class CmSessionStatus(
    val label: String,
) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class CmDayState(
    val label: String,
) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class CmBranchKind(
    val label: String,
) {
    CLINIC("Clinic"),
    PROVINCIAL_TOUR("Provincial tour"),
    MEDICAL_MISSION("Medical mission"),
}

enum class CmRemitKind(
    val label: String,
) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class CmSubmission(
    val label: String,
) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
}

enum class CmRole(
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

enum class CmReliefKind(
    val label: String,
) {
    DUTY("Relief duty"),
    REQUEST("Relief request"),
    INVITE("Relief invite"),
}

enum class CmScreen(
    val label: String,
) {
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit log"),
    PROFILE("Profile"),
    TOUR("Tour"),
}

data class CmUser(
    val id: String,
    val name: String,
    val role: CmRole,
    val homeBranchId: String,
    val slot: Int,
)

data class CmBranch(
    val id: String,
    val name: String,
    val kind: CmBranchKind,
)

data class CmBranchDay(
    val date: String,
    val branchId: String,
    val state: CmDayState,
)

data class CmSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: CmSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class CmClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val anonymized: Boolean = false,
    val pendingSessionId: String? = null,
)

data class CmRemittance(
    val id: String,
    val kind: CmRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: CmSubmission,
    val gross: Int,
    val deductions: Int,
    val note: String,
    val snapshotId: String? = null,
    val submittedAt: String? = null,
    val undoReason: String? = null,
) {
    val net: Int get() = gross - deductions
}

data class CmProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
) {
    val total: Int get() = qty * unitPrice
}

data class CmNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String? = null,
    val dayDate: String? = null,
    val read: Boolean = false,
)

data class CmAuditEntry(
    val id: String,
    val time: String,
    val actor: String,
    val action: String,
    val target: String,
    val detail: String,
)

data class CmRelief(
    val id: String,
    val kind: CmReliefKind,
    val person: String,
    val branchId: String,
    val dayDate: String,
    val state: String,
)

data class CmTourStep(
    val id: String,
    val screen: CmScreen,
    val title: String,
    val body: String,
    val tryIt: String,
)

val coachTourSteps =
    listOf(
        CmTourStep(
            id = "welcome",
            screen = CmScreen.HOME,
            title = "Welcome to the night desk",
            body = "This dashboard runs the whole branch day: clock-in, sessions, clients, " +
                "finance, team, mailbox and audit. I will walk you through each region. " +
                "Skip any step — nothing here can break.",
            tryIt = "Take the tour, or end it and explore freely.",
        ),
        CmTourStep(
            id = "branchday",
            screen = CmScreen.HOME,
            title = "One branch, one day",
            body = "The banner pins the working branch day and its state: OPEN days edit freely, " +
                "PAST days are coordinator-only, REMITTED days are frozen under a snapshot. " +
                "A branch day rolls over at 04:00 Asia/Manila, never at midnight.",
            tryIt = "Flip the banner between Open, Past and Remitted.",
        ),
        CmTourStep(
            id = "clockin",
            screen = CmScreen.HOME,
            title = "Clock in to start the day",
            body = "Clock in at your home branch for full access. At any other branch you land " +
                "on relief duty with view-only access until someone grants edit rights — ask " +
                "with a relief request, or accept a relief invite.",
            tryIt = "Press Clock in below.",
        ),
        CmTourStep(
            id = "sessions",
            screen = CmScreen.SESSIONS,
            title = "Sessions move Pending forward",
            body = "Every session travels PENDING to COMPLETED, NO_SHOW or CANCELLED. " +
                "Walk-in sessions can never be marked NO_SHOW or CANCELLED — the buttons " +
                "stay disabled and the rule note says why.",
            tryIt = "Open a Pending session and complete it.",
        ),
        CmTourStep(
            id = "voidrule",
            screen = CmScreen.SESSIONS,
            title = "Void keeps the record",
            body = "Voiding excludes a session from money math but never deletes it. A reason " +
                "is required, and an accidental void can be unvoided just as easily.",
            tryIt = "Void a session with a reason, then unvoid it.",
        ),
        CmTourStep(
            id = "clients",
            screen = CmScreen.CLIENTS,
            title = "Clients are global",
            body = "One person record shared across every branch, with at most one PENDING " +
                "session at a time — booking is blocked while one is open. Anonymized " +
                "records keep gender and age for reporting and nothing else.",
            tryIt = "Search the list and open the anonymized record.",
        ),
        CmTourStep(
            id = "finance",
            screen = CmScreen.FINANCE,
            title = "Remittance freezes a snapshot",
            body = "SESSION and PRODUCT flows each run draft to submitted. Submitting freezes " +
                "an immutable snapshot; within 48 hours it can be undone with a reason, " +
                "after that it is permanent.",
            tryIt = "Submit a draft, then undo it inside the window.",
        ),
        CmTourStep(
            id = "commission",
            screen = CmScreen.FINANCE,
            title = "Commissions split over who is here",
            body = "Product commissions pool per branch day and split equally across everyone " +
                "clocked in when the sale lands. Manual inclusions and exclusions override " +
                "the split and every override is audited.",
            tryIt = "Toggle someone out of the split pool.",
        ),
        CmTourStep(
            id = "mailbox",
            screen = CmScreen.MAILBOX,
            title = "Mailbox taps through",
            body = "Relief events name the branch and the day and jump straight to that branch " +
                "day. Unread dots clear on open; read rows are kept forever as history.",
            tryIt = "Open an unread relief message.",
        ),
        CmTourStep(
            id = "finish",
            screen = CmScreen.TEAM,
            title = "People, then you are done",
            body = "Team lists every user with role bundles, home branches and slots. " +
                "The audit log records each move you just made, and your profile holds " +
                "clock-out and sign-out. Restart this tour any time from the Tour tab.",
            tryIt = "Open the Tour tab to review or replay.",
        ),
    )

class CoachMarksStore {
    val currentUserId = mutableStateOf<String?>(null)
    val currentBranchId = mutableStateOf<String?>(null)
    val clockedIn = mutableStateOf(false)
    val reliefEdit = mutableStateOf(false)
    val homeDayFilter = mutableStateOf("2026-09-09")
    val currentScreen = mutableStateOf(CmScreen.HOME)

    val users = mutableStateListOf<CmUser>()
    val branches = mutableStateListOf<CmBranch>()
    val days = mutableStateListOf<CmBranchDay>()
    val sessions = mutableStateListOf<CmSession>()
    val clients = mutableStateListOf<CmClient>()
    val remittances = mutableStateListOf<CmRemittance>()
    val productLines = mutableStateListOf<CmProductLine>()
    val notifications = mutableStateListOf<CmNotification>()
    val audit = mutableStateListOf<CmAuditEntry>()
    val relief = mutableStateListOf<CmRelief>()

    val commissionIncluded = mutableStateListOf<String>()

    val tourActive = mutableStateOf(false)
    val tourIndex = mutableStateOf(0)
    val tourDone = mutableStateListOf<String>()
    val tourSkipped = mutableStateListOf<String>()
    val tourEverStarted = mutableStateOf(false)
    val dontShowAgain = mutableStateOf(false)
    val tourGuideOpen = mutableStateOf(false)

    private var auditSeq = 100
    private var noteSeq = 100

    fun currentUser(): CmUser? = users.firstOrNull { it.id == currentUserId.value }

    fun currentBranch(): CmBranch? = branches.firstOrNull { it.id == currentBranchId.value }

    fun branchDay(branchId: String, date: String): CmBranchDay? =
        days.firstOrNull { it.branchId == branchId && it.date == date }

    fun audit(actor: String, action: String, target: String, detail: String) {
        auditSeq += 1
        audit.add(
            0,
            CmAuditEntry(
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
            CmNotification(id = "N$noteSeq", title = title, body = body, branchId = branchId, dayDate = dayDate),
        )
    }

    fun currentStep(): CmTourStep? {
        if (!tourActive.value) return null
        val idx = tourIndex.value
        if (idx !in coachTourSteps.indices) return null
        return coachTourSteps[idx]
    }

    fun tourProgress(): Pair<Int, Int> {
        val finished = (tourDone + tourSkipped).distinct().size
        return finished to coachTourSteps.size
    }

    fun startTour() {
        tourDone.clear()
        tourSkipped.clear()
        tourIndex.value = 0
        tourActive.value = true
        tourEverStarted.value = true
        tourGuideOpen.value = true
    }

    fun restartTour() {
        startTour()
        audit(currentUser()?.name ?: "Guide", "TOUR_START", "tour:coach-marks", "Coach-marks tour restarted.")
    }

    fun resumeTour() {
        val firstOpen = coachTourSteps.indexOfFirst { it.id !in tourDone && it.id !in tourSkipped }
        tourIndex.value = if (firstOpen >= 0) firstOpen else 0
        if (firstOpen < 0) tourDone.clear()
        tourActive.value = true
        tourGuideOpen.value = true
    }

    fun endTour(completed: Boolean) {
        tourActive.value = false
        tourGuideOpen.value = false
        audit(
            currentUser()?.name ?: "Guide",
            "TOUR_END",
            "tour:coach-marks",
            if (completed) "Coach-marks tour finished." else "Coach-marks tour ended early — progress kept.",
        )
    }

    fun stepNumber(id: String): Int = coachTourSteps.indexOfFirst { it.id == id } + 1

    fun markCurrentDone() {
        val step = currentStep() ?: return
        if (step.id !in tourDone) tourDone.add(step.id)
        advance()
    }

    fun skipCurrent() {
        val step = currentStep() ?: return
        if (step.id !in tourDone && step.id !in tourSkipped) tourSkipped.add(step.id)
        advance()
    }

    private fun advance() {
        val next = tourIndex.value + 1
        if (next >= coachTourSteps.size) {
            endTour(completed = true)
        } else {
            tourIndex.value = next
            tourGuideOpen.value = true
        }
    }

    fun backOne() {
        if (tourIndex.value > 0) tourIndex.value -= 1
        tourGuideOpen.value = true
    }

    fun maybeAdvance(stepId: String) {
        val step = currentStep() ?: return
        if (step.id == stepId) markCurrentDone()
    }
}

fun seedCoachMarksStore(): CoachMarksStore {
    val store = CoachMarksStore()
    store.users.addAll(
        listOf(
            CmUser("u-amara", "Dra. Amara Santos", CmRole.PRACTITIONER, "b-makati", 1),
            CmUser("u-maria", "Maria Cruz", CmRole.COORDINATOR, "b-makati", 2),
            CmUser("u-liam", "Liam Tan", CmRole.MANAGER, "b-cebu", 1),
            CmUser("u-ana", "Ana Reyes", CmRole.ACCOUNTANT, "b-makati", 3),
            CmUser("u-new", "Ramon Dela Cruz", CmRole.ONBOARDING, "b-makati", 4),
        ),
    )
    store.branches.addAll(
        listOf(
            CmBranch("b-makati", "Makati Clinic", CmBranchKind.CLINIC),
            CmBranch("b-cebu", "Cebu Provincial Tour", CmBranchKind.PROVINCIAL_TOUR),
            CmBranch("b-mission", "Pasay Medical Mission", CmBranchKind.MEDICAL_MISSION),
        ),
    )
    store.days.addAll(
        listOf(
            CmBranchDay("2026-09-09", "b-makati", CmDayState.OPEN),
            CmBranchDay("2026-09-08", "b-makati", CmDayState.PAST),
            CmBranchDay("2026-09-07", "b-makati", CmDayState.REMITTED),
            CmBranchDay("2026-09-09", "b-cebu", CmDayState.OPEN),
            CmBranchDay("2026-09-09", "b-mission", CmDayState.OPEN),
        ),
    )
    store.sessions.addAll(
        listOf(
            CmSession(
                id = "S-101", time = "09:00", clientId = "c-1", clientName = "Josefa Ramos",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Follow-up",
                status = CmSessionStatus.COMPLETED, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            CmSession(
                id = "S-102", time = "10:00", clientId = "c-2", clientName = "Mark Villanueva",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Initial assessment",
                status = CmSessionStatus.PENDING, price = 1500,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            CmSession(
                id = "S-103", time = "10:30", clientId = "c-3", clientName = "Walk-in guest",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Walk-in",
                status = CmSessionStatus.PENDING, price = 800,
                practitioners = "Maria Cruz", walkIn = true,
            ),
            CmSession(
                id = "S-104", time = "11:00", clientId = "c-4", clientName = "Lorna Dy",
                branchId = "b-makati", dayDate = "2026-09-09", type = "Follow-up",
                status = CmSessionStatus.NO_SHOW, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
            ),
            CmSession(
                id = "S-105", time = "13:00", clientId = "c-5", clientName = "Paolo Aquino",
                branchId = "b-makati", dayDate = "2026-09-08", type = "Follow-up",
                status = CmSessionStatus.CANCELLED, price = 1200,
                practitioners = "Maria Cruz", walkIn = false,
            ),
            CmSession(
                id = "S-106", time = "14:00", clientId = "c-6", clientName = "Irene Ocampo",
                branchId = "b-makati", dayDate = "2026-09-08", type = "Follow-up",
                status = CmSessionStatus.COMPLETED, price = 1200,
                practitioners = "Dra. Amara Santos", walkIn = false,
                voided = true, voidReason = "Duplicate entry — kept for record.",
            ),
        ),
    )
    store.clients.addAll(
        listOf(
            CmClient("c-1", "Josefa Ramos", 54, "F", "0917-111-2233", pendingSessionId = null),
            CmClient("c-2", "Mark Villanueva", 38, "M", "0918-222-3344", pendingSessionId = "S-102"),
            CmClient("c-3", "Walk-in guest", 41, "M", "0900-000-0000", pendingSessionId = "S-103"),
            CmClient("c-4", "Lorna Dy", 47, "F", "0919-333-4455"),
            CmClient("c-5", "Paolo Aquino", 29, "M", "0920-444-5566"),
            CmClient("c-7", "Anonymized record #12", 60, "F", "withheld", anonymized = true),
        ),
    )
    store.remittances.addAll(
        listOf(
            CmRemittance(
                id = "R-31", kind = CmRemitKind.SESSION, branchId = "b-makati",
                dayDate = "2026-09-08", state = CmSubmission.DRAFT,
                gross = 2400, deductions = 900, note = "Tuesday session income",
            ),
            CmRemittance(
                id = "R-32", kind = CmRemitKind.PRODUCT, branchId = "b-makati",
                dayDate = "2026-09-08", state = CmSubmission.SUBMITTED,
                gross = 3600, deductions = 0, note = "Liniment + supports",
                snapshotId = "SNAP-8812", submittedAt = "2026-09-08 22:10 Asia/Manila",
            ),
            CmRemittance(
                id = "R-30", kind = CmRemitKind.SESSION, branchId = "b-makati",
                dayDate = "2026-09-07", state = CmSubmission.SUBMITTED,
                gross = 4800, deductions = 1400, note = "Monday session income",
                snapshotId = "SNAP-8790", submittedAt = "2026-09-07 21:02 Asia/Manila",
            ),
        ),
    )
    store.productLines.addAll(
        listOf(
            CmProductLine("p-1", "Herbal liniment 100ml", 8, 350),
            CmProductLine("p-2", "Knee support", 2, 400),
        ),
    )
    store.notifications.addAll(
        listOf(
            CmNotification(
                "N-3", "Relief request approved",
                "Maria Cruz granted your relief request at Makati Clinic for 2026-09-09.",
                branchId = "b-makati", dayDate = "2026-09-09",
            ),
            CmNotification(
                "N-2", "Session reminder",
                "S-102 with Mark Villanueva starts at 10:00 today.",
                branchId = "b-makati", dayDate = "2026-09-09", read = true,
            ),
            CmNotification(
                "N-1", "Remittance submitted",
                "R-32 product remittance for 2026-09-08 was submitted. Snapshot SNAP-8812 frozen.",
                branchId = "b-makati", dayDate = "2026-09-08", read = true,
            ),
        ),
    )
    store.audit.addAll(
        listOf(
            CmAuditEntry("A-9", "08:52", "Maria Cruz", "INSERT", "sessions:S-103", "Walk-in session created at Makati Clinic."),
            CmAuditEntry("A-8", "08:31", "Dra. Amara Santos", "UPDATE", "sessions:S-101", "PENDING -> COMPLETED, price 1200."),
            CmAuditEntry("A-7", "08:05", "Dra. Amara Santos", "CLOCK_IN", "attendance:b-makati", "Clocked in at home branch."),
        ),
    )
    store.relief.addAll(
        listOf(
            CmRelief("r-1", CmReliefKind.DUTY, "Dra. Amara Santos", "b-cebu", "2026-09-09", "Edit access"),
            CmRelief("r-2", CmReliefKind.REQUEST, "Maria Cruz", "b-cebu", "2026-09-10", "Pending branch grant"),
            CmRelief("r-3", CmReliefKind.INVITE, "Ana Reyes", "b-makati", "2026-09-10", "Invite sent"),
        ),
    )
    store.commissionIncluded.addAll(listOf("Dra. Amara Santos", "Maria Cruz"))
    return store
}
