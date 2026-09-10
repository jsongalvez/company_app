package com.companyb.companyapp.proto.noirdetective

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class CaseStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class DayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class NoirScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    DOSSIER("1", "dossier", "clock-in + relief"),
    CASES("2", "case files", "sessions + void"),
    PERSONS("3", "persons", "client registry"),
    LEDGER("4", "ledger", "remittance"),
    SQUAD("5", "squad", "users + roles"),
    WIRE("6", "wire", "notices + audit"),
    BADGE("7", "badge", "me + logout"),
}

internal data class BranchDay(
    val date: String,
    val state: DayState,
    val note: String,
)

internal data class NoirUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class NoirBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class CaseFile(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: CaseStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class Person(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class SquadMember(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class WireMessage(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class LogEntry(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class LedgerLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class SealedEnvelope(
    val id: String,
    val kind: String,
    val submittedAt: String,
    val total: Int,
    val undoable: Boolean,
)

internal class NoirRepo {
    var authed by mutableStateOf(false)
    var branchPicked by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-captain")
    var branchId by mutableStateOf("b-malate")
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(NoirScreen.DOSSIER)
    var selectedCaseId by mutableStateOf("c-101")
    var selectedPersonId by mutableStateOf("p-01")
    var caseFilter by mutableStateOf("ALL")
    var voidTargetId by mutableStateOf<String?>(null)
    var undoTargetId by mutableStateOf<String?>(null)
    var fileOpen by mutableStateOf(false)
    var productDraftName by mutableStateOf("")
    var auditSeq by mutableStateOf(7)

    val users =
        listOf(
            NoirUser(
                "u-captain",
                "R. Villanueva",
                "MANAGER",
                listOf("EDIT_BRANCH_DATA", "MANAGE_USERS", "SUBMIT_REMITTANCE"),
                false,
            ),
            NoirUser("u-sergeant", "M. Santos", "Coordinator", listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE"), false),
            NoirUser("u-detective", "J. Cruz", "Practitioner", listOf("LOG_SESSION"), false),
            NoirUser("u-rookie", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            NoirBranch("b-malate", "Malate", "CLINIC"),
            NoirBranch("b-cebu", "Cebu Stakeout", "PROVINCIAL_TOUR"),
            NoirBranch("b-tondo", "Tondo Outreach", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            BranchDay("2026-09-08", DayState.REMITTED, "sealed under envelope SR-2401"),
            BranchDay("2026-09-09", DayState.OPEN, "editable until 04:00 Asia/Manila"),
            BranchDay("2026-09-10", DayState.PAST, "locked past the 04:00 boundary"),
        )

    val cases =
        mutableStateListOf(
            CaseFile("c-101", "09:00", "p-01", "A. Reyes", false, "Rehab 60m", CaseStatus.COMPLETED, 1200, "J. Cruz"),
            CaseFile("c-102", "10:00", "p-02", "B. Lim", false, "Rehab 45m", CaseStatus.PENDING, 950, "J. Cruz"),
            CaseFile("c-103", "10:30", "p-03", "C. Tan", true, "Walk-in 30m", CaseStatus.PENDING, 600, "J. Cruz"),
            CaseFile("c-104", "11:00", "p-04", "D. Aquino", false, "Rehab 60m", CaseStatus.NO_SHOW, 1200, "M. Santos"),
            CaseFile("c-105", "13:00", "p-05", "E. Ramos", false, "Consult", CaseStatus.CANCELLED, 500, "J. Cruz"),
            CaseFile(
                "c-106",
                "14:00",
                "p-06",
                "F. Navarro",
                false,
                "Rehab 45m",
                CaseStatus.COMPLETED,
                950,
                "M. Santos",
                true,
                "duplicate entry",
            ),
        )

    val persons =
        mutableStateListOf(
            Person("p-01", "A. Reyes", "F", 41, "0917-000-0101"),
            Person("p-02", "B. Lim", "M", 35, "0917-000-0102"),
            Person("p-03", "C. Tan", "F", 28, "0917-000-0103"),
            Person("p-04", "D. Aquino", "M", 52, "0917-000-0104"),
            Person("p-05", "E. Ramos", "F", 47, "0917-000-0105"),
            Person("p-06", "F. Navarro", "M", 39, "0917-000-0106"),
            Person("p-07", "G. Ocampo", "F", 61, "0917-000-0107", true),
        )

    val squad =
        mutableStateListOf(
            SquadMember("u-captain", "R. Villanueva", "MANAGER", 1, "Malate", false, true),
            SquadMember("u-sergeant", "M. Santos", "Coordinator", 2, "Malate", false, true),
            SquadMember("u-detective", "J. Cruz", "Practitioner", 3, "Malate", false, false),
            SquadMember("u-relief", "P. Gomez", "Practitioner", 9, "Cebu Stakeout", true, true),
            SquadMember("u-rookie", "K. Dela Pena", "ONBOARDING", 0, "—", false, false),
        )

    val wire =
        mutableStateListOf(
            WireMessage(
                "w-1",
                "relief",
                "Relief request: P. Gomez",
                "Cebu Stakeout duty for 2026-09-10 awaits a grant.",
                "2026-09-10",
                false,
            ),
            WireMessage(
                "w-2",
                "remittance",
                "SR-2401 sealed",
                "SESSION remittance for 2026-09-08 locked a snapshot.",
                "2026-09-08",
                false,
            ),
            WireMessage(
                "w-3",
                "session",
                "NO_SHOW on the blotter",
                "D. Aquino missed the 11:00 slot; roster freed.",
                "2026-09-09",
                true,
            ),
            WireMessage(
                "w-4",
                "system",
                "Night boundary",
                "The branch day rolls at 04:00 Asia/Manila, not midnight.",
                "2026-09-09",
                true,
            ),
        )

    val log =
        mutableStateListOf(
            LogEntry(1, "08:02", "system", "BRANCH_DAY.OPEN", "2026-09-09 opened at Malate"),
            LogEntry(2, "08:31", "m.santos", "CLOCK_IN", "M. Santos clocked in at Malate"),
            LogEntry(3, "09:47", "j.cruz", "SESSION.COMPLETE", "c-101 closed at 1200"),
            LogEntry(4, "11:05", "m.santos", "SESSION.NO_SHOW", "c-104 marked NO_SHOW"),
            LogEntry(5, "13:20", "r.villanueva", "SESSION.VOID", "c-106 voided: duplicate entry"),
            LogEntry(6, "15:44", "r.villanueva", "REMITTANCE.SUBMIT", "SR-2401 SESSION 2026-09-08"),
            LogEntry(7, "16:02", "system", "CLIENT.ANONYMIZE", "p-07 anonymized on request"),
        )

    val productLines =
        mutableStateListOf(
            LedgerLine("l-1", "Kinesio tape", 4, 250),
            LedgerLine("l-2", "Heat patch x10", 2, 180),
        )

    val envelopes =
        mutableStateListOf(
            SealedEnvelope("SR-2401", "SESSION", "2026-09-08 22:14", 18400, true),
        )

    var sessionExpense by mutableStateOf(1500)
    var sessionComp by mutableStateOf(6200)

    val currentUser: NoirUser get() = users.first { it.id == currentUserId }
    val currentBranch: NoirBranch get() = branches.first { it.id == branchId }
    val currentDay: BranchDay get() = days[dayIndex]
    val canEditPast: Boolean get() = currentUser.role == "MANAGER" || currentUser.role == "Coordinator"
    val dayLocked: Boolean get() = currentDay.state != DayState.OPEN && !canEditPast
    val unreadCount: Int get() = wire.count { !it.read }
    val pendingCount: Int get() = cases.count { it.status == CaseStatus.PENDING && !it.voided }

    fun appendLog(
        actor: String,
        action: String,
        detail: String,
    ) {
        auditSeq += 1
        log.add(0, LogEntry(auditSeq, "17:${10 + auditSeq % 49}", actor, action, detail))
    }

    fun pendingPersonIds(): Set<String> =
        cases.filter { it.status == CaseStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    fun sessionNet(): Int =
        cases.filter { it.status == CaseStatus.COMPLETED && !it.voided }.sumOf { it.price } -
            sessionExpense - sessionComp

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun commissionPool(): Int = (sessionNet() * 5) / 100

    fun clockedCrew(): List<SquadMember> = squad.filter { it.clockedIn }
}

@Composable
internal fun rememberNoirRepo(): NoirRepo = remember { NoirRepo() }
