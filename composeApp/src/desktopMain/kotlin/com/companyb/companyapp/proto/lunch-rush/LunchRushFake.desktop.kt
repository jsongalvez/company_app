package com.companyb.companyapp.proto.lunchrush

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class RushSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class RushDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class RushScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    HOME("1", "rush board", "clock-in + relief call"),
    SESSIONS("2", "tickets", "queue + void"),
    CLIENTS("3", "regulars", "global registry"),
    FINANCE("4", "till", "remittance"),
    TEAM("5", "crew", "users + roles"),
    MAIL("6", "pass", "notices + audit"),
    PROFILE("7", "my apron", "me + logout"),
}

internal data class RushBranchDay(
    val date: String,
    val state: RushDayState,
    val note: String,
)

internal data class RushUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class RushBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class RushSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: RushSessionStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class RushClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class RushStaff(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class RushNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class RushAudit(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class RushProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class RushSnapshot(
    val id: String,
    val kind: String,
    val submittedAt: String,
    val total: Int,
    val undoable: Boolean,
)

internal class LunchRushRepo {
    var authed by mutableStateOf(false)
    var branchPicked by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-coord")
    var branchId by mutableStateOf("b-makati")
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(RushScreen.HOME)
    var shortcutsOpen by mutableStateOf(false)
    var selectedSessionId by mutableStateOf("s-201")
    var selectedClientId by mutableStateOf("c-01")
    var sessionFilter by mutableStateOf("ALL")
    var voidTargetId by mutableStateOf<String?>(null)
    var voidReason by mutableStateOf("")
    var undoTargetId by mutableStateOf<String?>(null)
    var undoReason by mutableStateOf("")
    var createOpen by mutableStateOf(false)
    var createName by mutableStateOf("")
    var createService by mutableStateOf("Hot Stone 60")
    var createWalkIn by mutableStateOf(false)
    var productDraftName by mutableStateOf("Ginger Tea")
    var productDraftQty by mutableStateOf("2")
    var reliefCallSent by mutableStateOf(false)
    var auditSeq by mutableStateOf(7)

    val users =
        listOf(
            RushUser("u-coord", "M. Santos", "Coordinator", listOf("session.write", "remittance.submit", "relief.grant", "branch.read"), false),
            RushUser("u-manage", "J. Reyes", "MANAGER", listOf("session.write", "remittance.submit", "remittance.undo", "user.manage", "relief.grant"), false),
            RushUser("u-prac", "A. Villanueva", "Practitioner", listOf("session.write", "branch.read"), false),
            RushUser("u-onboard", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            RushBranch("b-makati", "Makati Flagship", "CLINIC"),
            RushBranch("b-cebu", "Cebu Tour Van", "PROVINCIAL_TOUR"),
            RushBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            RushBranchDay("2026-09-08", RushDayState.REMITTED, "locked, snapshot filed"),
            RushBranchDay("2026-09-09", RushDayState.OPEN, "midday rush live"),
            RushBranchDay("2026-09-10", RushDayState.PAST, "awaiting remittance"),
        )

    val sessions =
        mutableStateListOf(
            RushSession("s-201", "11:00", "c-01", "L. Aquino", false, "Hot Stone 60", RushSessionStatus.COMPLETED, 1200, "A. Villanueva"),
            RushSession("s-202", "11:30", "c-02", "R. Cruz", true, "Express 30", RushSessionStatus.PENDING, 600, "A. Villanueva"),
            RushSession("s-203", "12:00", "c-03", "D. Ocampo", false, "Deep Tissue 90", RushSessionStatus.PENDING, 1500, "M. Santos"),
            RushSession("s-204", "12:15", "c-01", "L. Aquino", false, "Foot Reflex 45", RushSessionStatus.PENDING, 800, "A. Villanueva"),
            RushSession("s-205", "12:30", "c-04", "S. Lim", true, "Express 30", RushSessionStatus.PENDING, 600, "M. Santos"),
            RushSession("s-206", "10:00", "c-05", "P. Garcia", false, "Swedish 60", RushSessionStatus.NO_SHOW, 1100, "A. Villanueva"),
            RushSession("s-207", "09:30", "c-02", "R. Cruz", false, "Hot Stone 60", RushSessionStatus.CANCELLED, 1200, "M. Santos"),
            RushSession("s-208", "13:00", "c-03", "D. Ocampo", false, "Aroma 60", RushSessionStatus.PENDING, 1300, "A. Villanueva"),
        )

    val clients =
        mutableStateListOf(
            RushClient("c-01", "L. Aquino", "F", 34, "+63 917 111 0101"),
            RushClient("c-02", "R. Cruz", "M", 41, "+63 917 111 0102"),
            RushClient("c-03", "D. Ocampo", "F", 29, "+63 917 111 0103"),
            RushClient("c-04", "S. Lim", "F", 52, "+63 917 111 0104"),
            RushClient("c-05", "P. Garcia", "M", 37, "+63 917 111 0105"),
        )

    val staff =
        mutableStateListOf(
            RushStaff("t-1", "M. Santos", "Coordinator", 1, "Makati Flagship", false, true),
            RushStaff("t-2", "A. Villanueva", "Practitioner", 2, "Makati Flagship", false, true),
            RushStaff("t-3", "J. Reyes", "MANAGER", 3, "Makati Flagship", false, false),
            RushStaff("t-4", "R. Bautista", "Practitioner", 4, "Cebu Tour Van", true, false),
            RushStaff("t-5", "K. Dela Pena", "ONBOARDING", 5, "Makati Flagship", false, false),
        )

    val notices =
        mutableStateListOf(
            RushNotice("n-1", "RUSH", "Peak load 12:00-13:00", "5 tickets overlap the lunch hour. Call relief before the queue burns.", "2026-09-09", false),
            RushNotice("n-2", "RELIEF", "R. Bautista offered relief", "Cebu van crew can cover Makati 12:30-15:00. Accept from the rush board.", "2026-09-09", false),
            RushNotice("n-3", "FINANCE", "Yesterday remitted", "2026-09-08 snapshot filed. Undo window closes 48h after submit.", "2026-09-08", true),
        )

    val audits =
        mutableStateListOf(
            RushAudit(1, "08:55", "m.santos", "BRANCH_DAY.OPEN", "2026-09-09 opened at Makati Flagship"),
            RushAudit(2, "09:02", "m.santos", "CLOCK_IN", "M. Santos CLOCK_IN at Makati Flagship"),
            RushAudit(3, "10:05", "a.villanu", "SESSION.NO_SHOW", "s-206 -> NO_SHOW"),
            RushAudit(4, "10:40", "m.santos", "SESSION.CREATE", "s-205 walk-in booked for S. Lim"),
            RushAudit(5, "11:05", "a.villanu", "SESSION.COMPLETED", "s-201 -> COMPLETED"),
            RushAudit(6, "11:20", "m.santos", "NOTICE.READ", "n-3 acknowledged"),
        )

    val productLines =
        mutableStateListOf(
            RushProductLine("p-1", "Ginger Tea", 4, 120),
            RushProductLine("p-2", "Cool Towel Pack", 2, 250),
        )

    val snapshots =
        mutableStateListOf(
            RushSnapshot("SR-2401", "SESSION", "2026-09-08 17:40", 8600, false),
        )

    val currentUser: RushUser get() = users.first { it.id == currentUserId }
    val currentBranch: RushBranch get() = branches.first { it.id == branchId }
    val currentDay: RushBranchDay get() = days[dayIndex]

    val pendingSessions: List<RushSession> get() = sessions.filter { it.status == RushSessionStatus.PENDING && !it.voided }
    val unreadCount: Int get() = notices.count { !it.read }
    val clockedCrew: Int get() = staff.count { it.clockedIn }

    val soloCoverage: Boolean get() = clockedCrew < 2

    val rushLevel: Int get() = pendingSessions.size + (if (soloCoverage) 2 else 0)

    val bottleneckFlags: List<String>
        get() =
            buildList {
                if (pendingSessions.size >= 4) add("QUEUE OVERLOAD — ${pendingSessions.size} tickets pending")
                if (pendingSessions.count { it.time.startsWith("12:") } >= 2) add("LUNCH-HOUR STACK — 12:00 block overbooked")
                if (soloCoverage) add("SOLO COVERAGE — fewer than 2 crew clocked in")
                val bookedClients = pendingSessions.map { it.clientId }.toSet()
                if (pendingSessions.size != bookedClients.size) add("DOUBLE-BOOKED REGULARS — one client holds 2+ pending tickets")
            }

    val surgeActive: Boolean get() = rushLevel >= 5

    fun sessionNet(): Int {
        val done = sessions.filter { it.status == RushSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }
        return done - 400 - 150
    }

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun audit(
        actor: String,
        action: String,
        detail: String,
    ) {
        audits.add(0, RushAudit(auditSeq++, "12:45", actor, action, detail))
    }
}

@Composable
internal fun rememberLunchRushRepo(): LunchRushRepo = remember { LunchRushRepo() }
