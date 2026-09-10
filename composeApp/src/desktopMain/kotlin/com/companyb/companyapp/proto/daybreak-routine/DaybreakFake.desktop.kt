package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class DawnSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class DawnDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class DawnScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    RITUAL("1", "opener", "unlock + drawer + review"),
    SESSIONS("2", "sessions", "roster + void"),
    CLIENTS("3", "clients", "global registry"),
    FINANCE("4", "finance", "remittance"),
    TEAM("5", "team", "users + roles"),
    MAIL("6", "mail", "notices + audit"),
    PROFILE("7", "profile", "me + logout"),
}

internal data class DawnBranchDay(
    val date: String,
    val state: DawnDayState,
    val note: String,
)

internal data class DawnUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class DawnBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class DawnSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: DawnSessionStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class DawnClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class DawnStaff(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class DawnNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class DawnAudit(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class DawnProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class DawnSnapshot(
    val id: String,
    val kind: String,
    val clock: String,
    val total: Int,
    val withinUndo: Boolean,
)

internal class DaybreakRepo {
    var authed by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-coord")
    var branchId by mutableStateOf("b-makati")
    var branchPicked by mutableStateOf(false)
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(DawnScreen.RITUAL)
    var clockedIn by mutableStateOf(false)
    var detailSessionId by mutableStateOf<String?>(null)
    var detailClientId by mutableStateOf<String?>(null)
    var createOpen by mutableStateOf(false)
    var voidTargetId by mutableStateOf<String?>(null)
    var voidReason by mutableStateOf("")
    var undoTargetId by mutableStateOf<String?>(null)
    var undoReason by mutableStateOf("")
    var drawerCounted by mutableStateOf(false)
    var drawerAmount by mutableStateOf("")
    var dayReviewed by mutableStateOf(false)
    var firstClientStarted by mutableStateOf(false)
    var ritualDone by mutableStateOf(false)
    var reliefFilter by mutableStateOf(false)
    var auditSeq by mutableStateOf(7)

    val users =
        listOf(
            DawnUser("u-manage", "R. Villanueva", "MANAGER", listOf("EDIT_BRANCH_DATA", "MANAGE_USERS"), false),
            DawnUser("u-coord", "M. Santos", "Coordinator", listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE"), false),
            DawnUser("u-phys", "J. Cruz", "Practitioner", listOf("LOG_SESSION"), false),
            DawnUser("u-onboard", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            DawnBranch("b-makati", "Makati", "CLINIC"),
            DawnBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR"),
            DawnBranch("b-mission", "Tondo Mission", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            DawnBranchDay("2026-09-08", DawnDayState.REMITTED, "covered by SR-2401"),
            DawnBranchDay("2026-09-09", DawnDayState.OPEN, "editable until 04:00 Asia/Manila"),
            DawnBranchDay("2026-09-10", DawnDayState.PAST, "locked past 04:00 boundary"),
        )

    val sessions =
        mutableStateListOf(
            DawnSession(
                "s-101",
                "08:30",
                "c-01",
                "A. Reyes",
                false,
                "Sunrise 60m",
                DawnSessionStatus.COMPLETED,
                1200,
                "J. Cruz",
            ),
            DawnSession(
                "s-102",
                "09:00",
                "c-02",
                "B. Lim",
                false,
                "Sunrise 45m",
                DawnSessionStatus.PENDING,
                950,
                "J. Cruz",
            ),
            DawnSession(
                "s-103",
                "09:20",
                "c-03",
                "C. Tan",
                true,
                "Walk-in 30m",
                DawnSessionStatus.PENDING,
                600,
                "J. Cruz",
            ),
            DawnSession(
                "s-104",
                "10:00",
                "c-04",
                "D. Aquino",
                false,
                "Sunrise 60m",
                DawnSessionStatus.NO_SHOW,
                1200,
                "M. Santos",
            ),
            DawnSession(
                "s-105",
                "11:00",
                "c-05",
                "E. Ramos",
                false,
                "Consult",
                DawnSessionStatus.CANCELLED,
                500,
                "J. Cruz",
            ),
            DawnSession(
                "s-106",
                "13:00",
                "c-06",
                "F. Navarro",
                false,
                "Sunrise 45m",
                DawnSessionStatus.COMPLETED,
                950,
                "M. Santos",
                true,
                "duplicate entry",
            ),
        )

    val clients =
        mutableStateListOf(
            DawnClient("c-01", "A. Reyes", "F", 41, "0917-000-0101"),
            DawnClient("c-02", "B. Lim", "M", 35, "0917-000-0102"),
            DawnClient("c-03", "C. Tan", "F", 28, "0917-000-0103"),
            DawnClient("c-04", "D. Aquino", "M", 52, "0917-000-0104"),
            DawnClient("c-05", "E. Ramos", "F", 47, "0917-000-0105"),
            DawnClient("c-06", "F. Navarro", "M", 39, "0917-000-0106"),
            DawnClient("c-07", "G. Ocampo", "F", 61, "0917-000-0107", true),
        )

    val staff =
        mutableStateListOf(
            DawnStaff("u-manage", "R. Villanueva", "MANAGER", 1, "Makati", false, true),
            DawnStaff("u-coord", "M. Santos", "Coordinator", 2, "Makati", false, true),
            DawnStaff("u-phys", "J. Cruz", "Practitioner", 3, "Makati", false, false),
            DawnStaff("u-relief", "P. Gomez", "Practitioner", 9, "Cebu Tour", true, true),
            DawnStaff("u-onboard", "K. Dela Pena", "ONBOARDING", 0, "—", false, false),
        )

    val notices =
        mutableStateListOf(
            DawnNotice(
                "n-1",
                "relief",
                "Relief request: P. Gomez",
                "Cebu Tour duty for 2026-09-10 awaits a grant.",
                "2026-09-10",
                false,
            ),
            DawnNotice(
                "n-2",
                "remittance",
                "SR-2401 submitted",
                "SESSION remittance for 2026-09-08 locked a snapshot.",
                "2026-09-08",
                false,
            ),
            DawnNotice(
                "n-3",
                "session",
                "First client ready",
                "B. Lim holds the 09:00 opener slot.",
                "2026-09-09",
                true,
            ),
            DawnNotice(
                "n-4",
                "system",
                "Boundary reminder",
                "Branch day rolls at 04:00 Asia/Manila, not midnight.",
                "2026-09-09",
                true,
            ),
        )

    val audits =
        mutableStateListOf(
            DawnAudit(1, "06:58", "system", "BRANCH_DAY.OPEN", "2026-09-09 opened at Makati"),
            DawnAudit(2, "07:02", "m.santos", "CLOCK_IN", "M. Santos unlocked the doors"),
            DawnAudit(3, "08:47", "j.cruz", "SESSION.COMPLETE", "s-101 completed at 1200"),
            DawnAudit(4, "10:05", "m.santos", "SESSION.NO_SHOW", "s-104 marked NO_SHOW"),
            DawnAudit(5, "11:20", "r.villanueva", "SESSION.VOID", "s-106 voided: duplicate entry"),
            DawnAudit(6, "15:44", "r.villanueva", "REMITTANCE.SUBMIT", "SR-2401 SESSION 2026-09-08"),
            DawnAudit(7, "16:02", "system", "CLIENT.ANONYMIZE", "c-07 anonymized on request"),
        )

    val productLines =
        mutableStateListOf(
            DawnProductLine("p-1", "Kinesio tape", 4, 250),
            DawnProductLine("p-2", "Heat patch x10", 2, 180),
        )

    val snapshots =
        mutableStateListOf(
            DawnSnapshot("SR-2401", "SESSION", "2026-09-08 22:14", 18400, false),
        )

    var sessionExpense by mutableStateOf(1500)
    var sessionComp by mutableStateOf(6200)
    var drawerExpected by mutableStateOf(12500)

    val currentUser: DawnUser get() = users.first { it.id == currentUserId }
    val currentBranch: DawnBranch get() = branches.first { it.id == branchId }
    val currentDay: DawnBranchDay get() = days[dayIndex]
    val canEditPast: Boolean get() = currentUser.role == "MANAGER" || currentUser.role == "Coordinator"
    val dayLocked: Boolean get() = currentDay.state != DawnDayState.OPEN && !canEditPast
    val unreadCount: Int get() = notices.count { !it.read }
    val pendingCount: Int get() = sessions.count { it.status == DawnSessionStatus.PENDING && !it.voided }

    val unlockDone: Boolean get() = clockedIn
    val drawerDone: Boolean get() = drawerCounted
    val reviewDone: Boolean get() = dayReviewed
    val firstDone: Boolean get() = firstClientStarted
    val ritualStepsDone: Int get() = listOf(unlockDone, drawerDone, reviewDone, firstDone).count { it }

    fun audit(
        actor: String,
        action: String,
        detail: String,
    ) {
        auditSeq += 1
        audits.add(0, DawnAudit(auditSeq, "07:${(10 + auditSeq % 49)}", actor, action, detail))
    }

    fun pendingClientIds(): Set<String> =
        sessions.filter { it.status == DawnSessionStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    fun sessionNet(): Int =
        sessions.filter { it.status == DawnSessionStatus.COMPLETED && !it.voided }.sumOf { it.price } -
            sessionExpense - sessionComp

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun commissionPool(): Int = (sessionNet() * 5) / 100

    fun clockCrew(): List<DawnStaff> = staff.filter { it.clockedIn }
}

@Composable
internal fun rememberDaybreakRepo(): DaybreakRepo = remember { DaybreakRepo() }
