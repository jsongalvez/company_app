package com.companyb.companyapp.proto.duotonesea

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class SwimStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class TideState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class SeaScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    LAGOON("1", "lagoon", "clock-in + relief"),
    TIDES("2", "tide chart", "sessions + void"),
    DRIFT("3", "drift registry", "clients"),
    HARBOR("4", "harbor ledger", "remittance"),
    CREW("5", "crew", "users + roles"),
    BUOY("6", "signal buoy", "notices + audit"),
    DIVER("7", "diver badge", "me + logout"),
}

internal data class BranchDay(
    val date: String,
    val state: TideState,
    val note: String,
)

internal data class SeaUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class SeaBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class Swim(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: SwimStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class Drifter(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class CrewMember(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class BuoyMessage(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class HarbourLog(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class CargoLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class SealedCatch(
    val id: String,
    val kind: String,
    val submittedAt: String,
    val total: Int,
    val undoable: Boolean,
)

internal class SeaRepo {
    var authed by mutableStateOf(false)
    var branchPicked by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-harbor")
    var branchId by mutableStateOf("b-malate")
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(SeaScreen.LAGOON)
    var selectedSwimId by mutableStateOf("s-101")
    var selectedDrifterId by mutableStateOf("p-01")
    var swimFilter by mutableStateOf("ALL")
    var voidTargetId by mutableStateOf<String?>(null)
    var undoTargetId by mutableStateOf<String?>(null)
    var swimOpen by mutableStateOf(false)
    var productDraftName by mutableStateOf("")
    var auditSeq by mutableStateOf(7)
    var reliefInviteOpen by mutableStateOf(true)
    var reliefRequested by mutableStateOf(false)

    val users =
        listOf(
            SeaUser(
                "u-harbor",
                "R. Villanueva",
                "MANAGER",
                listOf("EDIT_BRANCH_DATA", "MANAGE_USERS", "SUBMIT_REMITTANCE"),
                false,
            ),
            SeaUser("u-tide", "M. Santos", "Coordinator", listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE"), false),
            SeaUser("u-diver", "J. Cruz", "Practitioner", listOf("LOG_SESSION"), false),
            SeaUser("u-foam", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            SeaBranch("b-malate", "Malate Cove", "CLINIC"),
            SeaBranch("b-cebu", "Cebu Crossing", "PROVINCIAL_TOUR"),
            SeaBranch("b-tondo", "Tondo Tide", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            BranchDay("2026-09-08", TideState.REMITTED, "sealed under catch SC-2401"),
            BranchDay("2026-09-09", TideState.OPEN, "editable until 04:00 Asia/Manila"),
            BranchDay("2026-09-10", TideState.PAST, "locked past the 04:00 boundary"),
        )

    val swims =
        mutableStateListOf(
            Swim("s-101", "09:00", "p-01", "A. Reyes", false, "Rehab 60m", SwimStatus.COMPLETED, 1200, "J. Cruz"),
            Swim("s-102", "10:00", "p-02", "B. Lim", false, "Rehab 45m", SwimStatus.PENDING, 950, "J. Cruz"),
            Swim("s-103", "10:30", "p-03", "C. Tan", true, "Walk-in 30m", SwimStatus.PENDING, 600, "J. Cruz"),
            Swim("s-104", "11:00", "p-04", "D. Aquino", false, "Rehab 60m", SwimStatus.NO_SHOW, 1200, "M. Santos"),
            Swim("s-105", "13:00", "p-05", "E. Ramos", false, "Consult", SwimStatus.CANCELLED, 500, "J. Cruz"),
            Swim(
                "s-106",
                "14:00",
                "p-06",
                "F. Navarro",
                false,
                "Rehab 45m",
                SwimStatus.COMPLETED,
                950,
                "M. Santos",
                true,
                "duplicate entry",
            ),
        )

    val drifters =
        mutableStateListOf(
            Drifter("p-01", "A. Reyes", "F", 41, "0917-000-0101"),
            Drifter("p-02", "B. Lim", "M", 35, "0917-000-0102"),
            Drifter("p-03", "C. Tan", "F", 28, "0917-000-0103"),
            Drifter("p-04", "D. Aquino", "M", 52, "0917-000-0104"),
            Drifter("p-05", "E. Ramos", "F", 47, "0917-000-0105"),
            Drifter("p-06", "F. Navarro", "M", 39, "0917-000-0106"),
            Drifter("p-07", "G. Ocampo", "F", 61, "0917-000-0107", true),
        )

    val crew =
        mutableStateListOf(
            CrewMember("u-harbor", "R. Villanueva", "MANAGER", 1, "Malate Cove", false, true),
            CrewMember("u-tide", "M. Santos", "Coordinator", 2, "Malate Cove", false, true),
            CrewMember("u-diver", "J. Cruz", "Practitioner", 3, "Malate Cove", false, false),
            CrewMember("u-relief", "P. Gomez", "Practitioner", 9, "Cebu Crossing", true, true),
            CrewMember("u-foam", "K. Dela Pena", "ONBOARDING", 0, "—", false, false),
        )

    val buoy =
        mutableStateListOf(
            BuoyMessage(
                "w-1",
                "relief",
                "Relief request: P. Gomez",
                "Cebu Crossing duty for 2026-09-10 awaits a grant.",
                "2026-09-10",
                false,
            ),
            BuoyMessage(
                "w-2",
                "remittance",
                "SC-2401 sealed",
                "SESSION remittance for 2026-09-08 locked a snapshot.",
                "2026-09-08",
                false,
            ),
            BuoyMessage(
                "w-3",
                "session",
                "NO_SHOW on the board",
                "D. Aquino missed the 11:00 slot; roster freed.",
                "2026-09-09",
                true,
            ),
            BuoyMessage(
                "w-4",
                "system",
                "Tide boundary",
                "The branch day rolls at 04:00 Asia/Manila, not midnight.",
                "2026-09-09",
                true,
            ),
        )

    val harborLog =
        mutableStateListOf(
            HarbourLog(1, "08:02", "system", "BRANCH_DAY.OPEN", "2026-09-09 opened at Malate Cove"),
            HarbourLog(2, "08:31", "m.santos", "CLOCK_IN", "M. Santos clocked in at Malate Cove"),
            HarbourLog(3, "09:47", "j.cruz", "SESSION.COMPLETE", "s-101 closed at 1200"),
            HarbourLog(4, "11:05", "m.santos", "SESSION.NO_SHOW", "s-104 marked NO_SHOW"),
            HarbourLog(5, "13:20", "r.villanueva", "SESSION.VOID", "s-106 voided: duplicate entry"),
            HarbourLog(6, "15:44", "r.villanueva", "REMITTANCE.SUBMIT", "SC-2401 SESSION 2026-09-08"),
            HarbourLog(7, "16:02", "system", "CLIENT.ANONYMIZE", "p-07 anonymized on request"),
        )

    val productLines =
        mutableStateListOf(
            CargoLine("l-1", "Kinesio tape", 4, 250),
            CargoLine("l-2", "Heat patch x10", 2, 180),
        )

    val catches =
        mutableStateListOf(
            SealedCatch("SC-2401", "SESSION", "2026-09-08 22:14", 18400, true),
        )

    var sessionExpense by mutableStateOf(1500)
    var sessionComp by mutableStateOf(6200)

    val currentUser: SeaUser get() = users.first { it.id == currentUserId }
    val currentBranch: SeaBranch get() = branches.first { it.id == branchId }
    val currentDay: BranchDay get() = days[dayIndex]
    val canEditPast: Boolean get() = currentUser.role == "MANAGER" || currentUser.role == "Coordinator"
    val dayLocked: Boolean get() = currentDay.state != TideState.OPEN && !canEditPast
    val unreadCount: Int get() = buoy.count { !it.read }
    val pendingCount: Int get() = swims.count { it.status == SwimStatus.PENDING && !it.voided }

    fun appendLog(
        actor: String,
        action: String,
        detail: String,
    ) {
        auditSeq += 1
        harborLog.add(0, HarbourLog(auditSeq, "17:${10 + auditSeq % 49}", actor, action, detail))
    }

    fun pendingDrifterIds(): Set<String> =
        swims.filter { it.status == SwimStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    fun sessionNet(): Int =
        swims.filter { it.status == SwimStatus.COMPLETED && !it.voided }.sumOf { it.price } -
            sessionExpense - sessionComp

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun commissionPool(): Int = (sessionNet() * 5) / 100

    fun clockedCrew(): List<CrewMember> = crew.filter { it.clockedIn }
}

@Composable
internal fun rememberSeaRepo(): SeaRepo = remember { SeaRepo() }
