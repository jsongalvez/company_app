package com.companyb.companyapp.proto.darkops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class OpsSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class OpsDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class OpsScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    HOME("1", "home", "clock-in + relief"),
    SESSIONS("2", "sessions", "roster + void"),
    CLIENTS("3", "clients", "global registry"),
    FINANCE("4", "finance", "remittance"),
    TEAM("5", "team", "users + roles"),
    MAIL("6", "mail", "notices + audit"),
    PROFILE("7", "profile", "me + logout"),
}

internal data class OpsBranchDay(
    val date: String,
    val state: OpsDayState,
    val note: String,
)

internal data class OpsUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class OpsBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class OpsSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: OpsSessionStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class OpsClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class OpsStaff(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class OpsNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class OpsAudit(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class OpsProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class OpsSnapshot(
    val id: String,
    val kind: String,
    val submittedAt: String,
    val total: Int,
    val undoable: Boolean,
)

internal class DarkOpsRepo {
    var authed by mutableStateOf(false)
    var branchPicked by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-manage")
    var branchId by mutableStateOf("b-makati")
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(OpsScreen.HOME)
    var paletteOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)
    var selectedSessionId by mutableStateOf("s-101")
    var selectedClientId by mutableStateOf("c-01")
    var selectedNoticeId by mutableStateOf("n-1")
    var sessionFilter by mutableStateOf("ALL")
    var voidTargetId by mutableStateOf<String?>(null)
    var undoTargetId by mutableStateOf<String?>(null)
    var detailSessionId by mutableStateOf<String?>(null)
    var detailClientId by mutableStateOf<String?>(null)
    var createOpen by mutableStateOf(false)
    var productDraftName by mutableStateOf("")
    var auditSeq by mutableStateOf(7)

    val users =
        listOf(
            OpsUser(
                "u-manage",
                "R. Villanueva",
                "MANAGER",
                listOf("EDIT_BRANCH_DATA", "MANAGE_USERS", "SUBMIT_REMITTANCE"),
                false,
            ),
            OpsUser("u-coord", "M. Santos", "Coordinator", listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE"), false),
            OpsUser("u-phys", "J. Cruz", "Practitioner", listOf("LOG_SESSION"), false),
            OpsUser("u-onboard", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            OpsBranch("b-makati", "Makati", "CLINIC"),
            OpsBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR"),
            OpsBranch("b-mission", "Tondo Mission", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            OpsBranchDay("2026-09-08", OpsDayState.REMITTED, "covered by SR-2401"),
            OpsBranchDay("2026-09-09", OpsDayState.OPEN, "editable until 04:00 Asia/Manila"),
            OpsBranchDay("2026-09-10", OpsDayState.PAST, "locked past 04:00 boundary"),
        )

    val sessions =
        mutableStateListOf(
            OpsSession(
                "s-101",
                "09:00",
                "c-01",
                "A. Reyes",
                false,
                " Rehab 60m",
                OpsSessionStatus.COMPLETED,
                1200,
                "J. Cruz",
            ),
            OpsSession(
                "s-102",
                "10:00",
                "c-02",
                "B. Lim",
                false,
                " Rehab 45m",
                OpsSessionStatus.PENDING,
                950,
                "J. Cruz",
            ),
            OpsSession(
                "s-103",
                "10:30",
                "c-03",
                "C. Tan",
                true,
                " Walk-in 30m",
                OpsSessionStatus.PENDING,
                600,
                "J. Cruz",
            ),
            OpsSession(
                "s-104",
                "11:00",
                "c-04",
                "D. Aquino",
                false,
                " Rehab 60m",
                OpsSessionStatus.NO_SHOW,
                1200,
                "M. Santos",
            ),
            OpsSession(
                "s-105",
                "13:00",
                "c-05",
                "E. Ramos",
                false,
                "Consult",
                OpsSessionStatus.CANCELLED,
                500,
                "J. Cruz",
            ),
            OpsSession(
                "s-106",
                "14:00",
                "c-06",
                "F. Navarro",
                false,
                " Rehab 45m",
                OpsSessionStatus.COMPLETED,
                950,
                "M. Santos",
                true,
                "duplicate entry",
            ),
        )

    val clients =
        mutableStateListOf(
            OpsClient("c-01", "A. Reyes", "F", 41, "0917-000-0101"),
            OpsClient("c-02", "B. Lim", "M", 35, "0917-000-0102"),
            OpsClient("c-03", "C. Tan", "F", 28, "0917-000-0103"),
            OpsClient("c-04", "D. Aquino", "M", 52, "0917-000-0104"),
            OpsClient("c-05", "E. Ramos", "F", 47, "0917-000-0105"),
            OpsClient("c-06", "F. Navarro", "M", 39, "0917-000-0106"),
            OpsClient("c-07", "G. Ocampo", "F", 61, "0917-000-0107", true),
        )

    val staff =
        mutableStateListOf(
            OpsStaff("u-manage", "R. Villanueva", "MANAGER", 1, "Makati", false, true),
            OpsStaff("u-coord", "M. Santos", "Coordinator", 2, "Makati", false, true),
            OpsStaff("u-phys", "J. Cruz", "Practitioner", 3, "Makati", false, false),
            OpsStaff("u-relief", "P. Gomez", "Practitioner", 9, "Cebu Tour", true, true),
            OpsStaff("u-onboard", "K. Dela Pena", "ONBOARDING", 0, "—", false, false),
        )

    val notices =
        mutableStateListOf(
            OpsNotice(
                "n-1",
                "relief",
                "Relief request: P. Gomez",
                "Cebu Tour duty for 2026-09-10 awaits a grant.",
                "2026-09-10",
                false,
            ),
            OpsNotice(
                "n-2",
                "remittance",
                "SR-2401 submitted",
                "SESSION remittance for 2026-09-08 locked a snapshot.",
                "2026-09-08",
                false,
            ),
            OpsNotice(
                "n-3",
                "session",
                "NO_SHOW recorded",
                "D. Aquino missed the 11:00 slot; roster freed.",
                "2026-09-09",
                true,
            ),
            OpsNotice(
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
            OpsAudit(1, "08:02", "system", "BRANCH_DAY.OPEN", "2026-09-09 opened at Makati"),
            OpsAudit(2, "08:31", "m.santos", "CLOCK_IN", "M. Santos clocked in at Makati"),
            OpsAudit(3, "09:47", "j.cruz", "SESSION.COMPLETE", "s-101 completed at 1200"),
            OpsAudit(4, "11:05", "m.santos", "SESSION.NO_SHOW", "s-104 marked NO_SHOW"),
            OpsAudit(5, "13:20", "r.villanueva", "SESSION.VOID", "s-106 voided: duplicate entry"),
            OpsAudit(6, "15:44", "r.villanueva", "REMITTANCE.SUBMIT", "SR-2401 SESSION 2026-09-08"),
            OpsAudit(7, "16:02", "system", "CLIENT.ANONYMIZE", "c-07 anonymized on request"),
        )

    val productLines =
        mutableStateListOf(
            OpsProductLine("p-1", "Kinesio tape", 4, 250),
            OpsProductLine("p-2", "Heat patch x10", 2, 180),
        )

    val snapshots =
        mutableStateListOf(
            OpsSnapshot("SR-2401", "SESSION", "2026-09-08 22:14", 18400, true),
        )

    var sessionExpense by mutableStateOf(1500)
    var sessionComp by mutableStateOf(6200)

    val currentUser: OpsUser get() = users.first { it.id == currentUserId }
    val currentBranch: OpsBranch get() = branches.first { it.id == branchId }
    val currentDay: OpsBranchDay get() = days[dayIndex]
    val canEditPast: Boolean get() = currentUser.role == "MANAGER" || currentUser.role == "Coordinator"
    val dayLocked: Boolean get() = currentDay.state != OpsDayState.OPEN && !canEditPast
    val unreadCount: Int get() = notices.count { !it.read }
    val pendingCount: Int get() = sessions.count { it.status == OpsSessionStatus.PENDING && !it.voided }

    fun audit(
        actor: String,
        action: String,
        detail: String,
    ) {
        auditSeq += 1
        audits.add(0, OpsAudit(auditSeq, "17:${(10 + auditSeq % 49)}", actor, action, detail))
    }

    fun pendingClientIds(): Set<String> =
        sessions.filter { it.status == OpsSessionStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    fun sessionNet(): Int =
        sessions.filter { it.status == OpsSessionStatus.COMPLETED && !it.voided }.sumOf { it.price } -
            sessionExpense - sessionComp

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun commissionPool(): Int = (sessionNet() * 5) / 100

    fun clockCrew(): List<OpsStaff> = staff.filter { it.clockedIn }
}

@Composable
internal fun rememberDarkOpsRepo(): DarkOpsRepo = remember { DarkOpsRepo() }
