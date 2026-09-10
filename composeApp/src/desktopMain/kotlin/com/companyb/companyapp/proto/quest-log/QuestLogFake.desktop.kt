package com.companyb.companyapp.proto.questlog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal enum class QuestStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class QuestDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class QuestScreen(
    val title: String,
    val hint: String,
) {
    BOARD("quest board", "clock-in + relief"),
    QUESTS("quests", "sessions + void"),
    ALLIES("allies", "global clients"),
    VAULT("vault", "remittance"),
    GUILD("guild", "users + roles"),
    RAVEN("raven post", "notices + audit"),
    HERO("hero", "me + logout"),
}

internal data class QuestBranchDay(
    val date: String,
    val state: QuestDayState,
    val note: String,
)

internal data class QuestUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class QuestBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class QuestSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: QuestStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class QuestClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class QuestStaff(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class QuestRelief(
    val id: String,
    val who: String,
    val where: String,
    val day: String,
    var verdict: String,
)

internal data class QuestNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class QuestAudit(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class QuestProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class QuestSnapshot(
    val id: String,
    val kind: String,
    val submittedAt: String,
    val total: Int,
    val ageHours: Int,
    val undoable: Boolean,
)

internal fun questRank(price: Int): String =
    when {
        price >= 1200 -> "S"
        price >= 900 -> "A"
        price >= 600 -> "B"
        else -> "C"
    }

internal fun questRankColor(rank: String): Color =
    when (rank) {
        "S" -> QuestLogPalette.EpicOrange
        "A" -> QuestLogPalette.Gold
        "B" -> QuestLogPalette.ManaBlue
        else -> QuestLogPalette.Dim
    }

internal fun questStatusColor(status: QuestStatus): Color =
    when (status) {
        QuestStatus.PENDING -> QuestLogPalette.Gold
        QuestStatus.COMPLETED -> QuestLogPalette.XpGreen
        QuestStatus.NO_SHOW -> QuestLogPalette.HpRed
        QuestStatus.CANCELLED -> QuestLogPalette.Faint
    }

private const val XP_PER_LEVEL = 3000

internal class QuestLogRepo {
    var authed by mutableStateOf(false)
    var branchPicked by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var currentUserId by mutableStateOf("u-coord")
    var branchId by mutableStateOf("b-makati")
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(QuestScreen.BOARD)
    var selectedSessionId by mutableStateOf("q-101")
    var selectedClientId by mutableStateOf("c-01")
    var sessionFilter by mutableStateOf("ALL")
    var voidTargetId by mutableStateOf<String?>(null)
    var voidReason by mutableStateOf("")
    var createOpen by mutableStateOf(false)
    var newTime by mutableStateOf("15:00")
    var newClient by mutableStateOf("")
    var newService by mutableStateOf("Rehab 45m")
    var newPrice by mutableStateOf("950")
    var newWalkIn by mutableStateOf(false)
    var productDraftName by mutableStateOf("")
    var productDraftQty by mutableStateOf("1")
    var productDraftPrice by mutableStateOf("250")
    var inviteWho by mutableStateOf("")
    var requestWhere by mutableStateOf("")
    var anonymizedView by mutableStateOf(false)
    var auditSeq by mutableStateOf(7)
    var questSeq by mutableStateOf(107)

    val users =
        listOf(
            QuestUser(
                "u-manage",
                "R. Villanueva",
                "MANAGER",
                listOf("EDIT_BRANCH_DATA", "MANAGE_USERS", "SUBMIT_REMITTANCE"),
                false,
            ),
            QuestUser("u-coord", "M. Santos", "Coordinator", listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE"), false),
            QuestUser("u-phys", "J. Cruz", "Practitioner", listOf("LOG_SESSION"), false),
            QuestUser("u-onboard", "K. Dela Pena", "ONBOARDING", emptyList(), true),
        )

    val branches =
        listOf(
            QuestBranch("b-makati", "Makati", "CLINIC"),
            QuestBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR"),
            QuestBranch("b-mission", "Tondo Mission", "MEDICAL_MISSION"),
        )

    val days =
        listOf(
            QuestBranchDay("2026-09-08", QuestDayState.REMITTED, "sealed under scroll SR-2401"),
            QuestBranchDay("2026-09-09", QuestDayState.OPEN, "chain links editable until 04:00 Asia/Manila"),
            QuestBranchDay("2026-09-10", QuestDayState.PAST, "links set past the 04:00 boundary"),
        )

    val sessions =
        mutableStateListOf(
            QuestSession("q-101", "09:00", "c-01", "A. Reyes", false, "Rehab 60m", QuestStatus.COMPLETED, 1200, "J. Cruz"),
            QuestSession("q-102", "10:00", "c-02", "B. Lim", false, "Rehab 45m", QuestStatus.PENDING, 950, "J. Cruz"),
            QuestSession("q-103", "10:30", "c-03", "C. Tan", true, "Walk-in 30m", QuestStatus.PENDING, 600, "J. Cruz"),
            QuestSession("q-104", "11:00", "c-04", "D. Aquino", false, "Rehab 60m", QuestStatus.NO_SHOW, 1200, "M. Santos"),
            QuestSession("q-105", "13:00", "c-05", "E. Ramos", false, "Consult", QuestStatus.CANCELLED, 500, "J. Cruz"),
            QuestSession(
                "q-106",
                "14:00",
                "c-06",
                "F. Navarro",
                false,
                "Rehab 45m",
                QuestStatus.COMPLETED,
                950,
                "M. Santos",
                true,
                "duplicate entry",
            ),
        )

    val clients =
        mutableStateListOf(
            QuestClient("c-01", "A. Reyes", "F", 41, "0917-000-0101"),
            QuestClient("c-02", "B. Lim", "M", 35, "0917-000-0102"),
            QuestClient("c-03", "C. Tan", "F", 28, "0917-000-0103"),
            QuestClient("c-04", "D. Aquino", "M", 52, "0917-000-0104"),
            QuestClient("c-05", "E. Ramos", "F", 47, "0917-000-0105"),
            QuestClient("c-06", "F. Navarro", "M", 39, "0917-000-0106"),
            QuestClient("c-07", "G. Ocampo", "F", 61, "0917-000-0107", true),
        )

    val staff =
        mutableStateListOf(
            QuestStaff("u-manage", "R. Villanueva", "MANAGER", 1, "Makati", false, true),
            QuestStaff("u-coord", "M. Santos", "Coordinator", 2, "Makati", false, true),
            QuestStaff("u-phys", "J. Cruz", "Practitioner", 3, "Makati", false, false),
            QuestStaff("u-relief", "P. Gomez", "Practitioner", 9, "Cebu Tour", true, true),
            QuestStaff("u-onboard", "K. Dela Pena", "ONBOARDING", 0, "—", false, false),
        )

    val reliefs =
        mutableStateListOf(
            QuestRelief("r-1", "P. Gomez", "Cebu Tour", "2026-09-10", "AWAITING"),
            QuestRelief("r-2", "J. Cruz", "Tondo Mission", "2026-09-11", "AWAITING"),
        )

    val notices =
        mutableStateListOf(
            QuestNotice(
                "n-1",
                "relief",
                "Relief horn: P. Gomez",
                "Cebu Tour duty for 2026-09-10 awaits a grant.",
                "2026-09-10",
                false,
            ),
            QuestNotice(
                "n-2",
                "remittance",
                "SR-2401 sealed",
                "SESSION remittance for 2026-09-08 locked a snapshot.",
                "2026-09-08",
                false,
            ),
            QuestNotice(
                "n-3",
                "session",
                "Quest abandoned",
                "D. Aquino missed the 11:00 slot; the link stands empty.",
                "2026-09-09",
                true,
            ),
            QuestNotice(
                "n-4",
                "system",
                "Boundary bell",
                "The branch day turns at 04:00 Asia/Manila, never at midnight.",
                "2026-09-09",
                true,
            ),
        )

    val audits =
        mutableStateListOf(
            QuestAudit(1, "08:02", "system", "BRANCH_DAY.OPEN", "2026-09-09 opened at Makati"),
            QuestAudit(2, "08:31", "m.santos", "CLOCK_IN", "M. Santos clocked in at Makati"),
            QuestAudit(3, "09:47", "j.cruz", "SESSION.COMPLETE", "q-101 turned in at 1200 XP"),
            QuestAudit(4, "11:05", "m.santos", "SESSION.NO_SHOW", "q-104 marked NO_SHOW"),
            QuestAudit(5, "13:20", "r.villanueva", "SESSION.VOID", "q-106 voided: duplicate entry"),
            QuestAudit(6, "15:44", "r.villanueva", "REMITTANCE.SUBMIT", "SR-2401 SESSION 2026-09-08"),
            QuestAudit(7, "16:02", "system", "CLIENT.ANONYMIZE", "c-07 anonymized on request"),
        )

    val productLines =
        mutableStateListOf(
            QuestProductLine("p-1", "Kinesio tape", 4, 250),
            QuestProductLine("p-2", "Heat patch x10", 2, 180),
        )

    val snapshots =
        mutableStateListOf(
            QuestSnapshot("SR-2401", "SESSION", "2026-09-08 22:14", 18400, 19, true),
        )

    var sessionExpense by mutableStateOf(1500)
    var sessionComp by mutableStateOf(6200)

    val currentUser: QuestUser get() = users.first { it.id == currentUserId }
    val currentBranch: QuestBranch get() = branches.first { it.id == branchId }
    val currentDay: QuestBranchDay get() = days[dayIndex]
    val canEditPast: Boolean get() = currentUser.role == "MANAGER" || currentUser.role == "Coordinator"
    val dayLocked: Boolean get() = currentDay.state != QuestDayState.OPEN && !canEditPast
    val unreadCount: Int get() = notices.count { !it.read }
    val pendingCount: Int get() = sessions.count { it.status == QuestStatus.PENDING && !it.voided }

    fun audit(
        actor: String,
        action: String,
        detail: String,
    ) {
        auditSeq += 1
        audits.add(0, QuestAudit(auditSeq, "17:${10 + auditSeq % 49}", actor, action, detail))
    }

    fun pendingClientIds(): Set<String> =
        sessions.filter { it.status == QuestStatus.PENDING && !it.voided }.map { it.clientId }.toSet()

    fun completedXp(): Int =
        sessions.filter { it.status == QuestStatus.COMPLETED && !it.voided }.sumOf { it.price }

    fun heroLevel(): Int = 1 + completedXp() / XP_PER_LEVEL

    fun heroLevelProgress(): Float {
        val into = completedXp() % XP_PER_LEVEL
        return into.toFloat() / XP_PER_LEVEL.toFloat()
    }

    fun sessionNet(): Int =
        sessions.filter { it.status == QuestStatus.COMPLETED && !it.voided }.sumOf { it.price } -
            sessionExpense - sessionComp

    fun productTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun practitionerShare(): Int = (sessionNet() * 60) / 100

    fun clockCrew(): List<QuestStaff> = staff.filter { it.clockedIn }
}

@Composable
internal fun rememberQuestLogRepo(): QuestLogRepo = remember { QuestLogRepo() }
