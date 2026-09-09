package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #779 — metrics-mosaic prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class MmSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class MmDayStatus { OPEN, PAST, REMITTED }
enum class MmScreen { MOSAIC, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class MmUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class MmBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: MmDayStatus,
    val weekSessions: List<Float>,
    val weekRevenue: List<Float>,
    val weekNoShow: List<Float>,
)

data class MmSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: MmSessionStatus,
    val price: Int,
    val practitioner: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class MmClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val gender: String,
    val age: Int,
)

data class MmRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val hoursOld: Int,
)

data class MmRelief(
    val id: String,
    val branchName: String,
    val day: String,
    var state: String,
)

data class MmNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

class MmRepo {
    val users = listOf(
        MmUser("u-ana", "ana", "Ana Santos", "Practitioner", "b-makati", listOf("session.write", "client.read")),
        MmUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-bgc",
            listOf("session.write", "relief.manage", "client.read"),
        ),
        MmUser("u-cara", "cara", "Cara Lim", "MANAGER", "b-makati", listOf("all")),
        MmUser("u-dan", "dan", "Dan Reyes", "Accountant", "b-bgc", listOf("finance.write", "audit.read")),
        MmUser("u-eli", "eli", "Eli Navarro", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        MmBranch(
            "b-makati", "MAKATI CLINIC", "Branch", MmDayStatus.OPEN,
            weekSessions = listOf(9f, 12f, 11f, 14f, 16f, 8f, 13f),
            weekRevenue = listOf(42f, 55f, 51f, 63f, 71f, 30f, 58f),
            weekNoShow = listOf(2f, 1f, 3f, 1f, 2f, 0f, 1f),
        ),
        MmBranch(
            "b-bgc", "BGC CLINIC", "Branch", MmDayStatus.OPEN,
            weekSessions = listOf(7f, 9f, 10f, 12f, 15f, 6f, 11f),
            weekRevenue = listOf(35f, 44f, 48f, 57f, 66f, 24f, 50f),
            weekNoShow = listOf(1f, 2f, 1f, 0f, 3f, 1f, 2f),
        ),
        MmBranch(
            "b-cebu", "CEBU-TOUR", "PROVINCIAL_TOUR", MmDayStatus.PAST,
            weekSessions = listOf(4f, 5f, 6f, 7f, 9f, 3f, 5f),
            weekRevenue = listOf(18f, 22f, 26f, 30f, 38f, 12f, 21f),
            weekNoShow = listOf(0f, 1f, 0f, 2f, 1f, 0f, 1f),
        ),
        MmBranch(
            "b-tondo", "TONDO-MISSION", "MEDICAL_MISSION", MmDayStatus.REMITTED,
            weekSessions = listOf(12f, 15f, 18f, 20f, 22f, 10f, 16f),
            weekRevenue = listOf(8f, 10f, 12f, 14f, 15f, 6f, 11f),
            weekNoShow = listOf(3f, 4f, 2f, 5f, 3f, 1f, 2f),
        ),
    )

    val sessions = mutableStateListOf(
        MmSession("s-101", "09:00", "J. Dela Cruz", "b-makati", "SESSION", false, MmSessionStatus.PENDING, 1200, "ana"),
        MmSession("s-102", "10:30", "Walk-in guest", "b-makati", "SESSION", true, MmSessionStatus.PENDING, 800, "ana"),
        MmSession("s-103", "11:00", "M. Aquino", "b-makati", "SESSION", false, MmSessionStatus.COMPLETED, 1500, "ana"),
        MmSession(
            "s-104", "13:00", "R. Villanueva", "b-makati", "FOLLOW_UP",
            false, MmSessionStatus.NO_SHOW, 900, "cara",
        ),
        MmSession(
            "s-105", "14:30", "L. Fernandez", "b-makati", "SESSION",
            false, MmSessionStatus.CANCELLED, 1100, "cara",
        ),
        MmSession("s-201", "09:30", "K. Tan", "b-bgc", "SESSION", false, MmSessionStatus.PENDING, 1300, "ben"),
        MmSession("s-202", "12:00", "Walk-in guest", "b-bgc", "SESSION", true, MmSessionStatus.COMPLETED, 800, "ben"),
        MmSession("s-301", "10:00", "P. Ramos", "b-cebu", "SESSION", false, MmSessionStatus.COMPLETED, 1000, "ben"),
        MmSession("s-401", "08:00", "Community line", "b-tondo", "SESSION", true, MmSessionStatus.COMPLETED, 0, "ana"),
    )

    val clients = mutableStateListOf(
        MmClient("c-1", "J. Dela Cruz", "0917-000-1101", pendingCount = 1, gender = "F", age = 34),
        MmClient("c-2", "M. Aquino", "0917-000-1102", pendingCount = 0, gender = "M", age = 41),
        MmClient("c-3", "R. Villanueva", "0917-000-1103", pendingCount = 0, gender = "F", age = 28),
        MmClient("c-4", "L. Fernandez", "0917-000-1104", pendingCount = 1, gender = "F", age = 52),
        MmClient("c-5", "K. Tan", "0917-000-1105", pendingCount = 0, gender = "M", age = 36),
    )

    val remittances = mutableStateListOf(
        MmRemittance("r-1", "SESSION", "DRAFT", 5400, hoursOld = 5),
        MmRemittance("r-2", "PRODUCT", "DRAFT", 2300, hoursOld = 5),
        MmRemittance("r-3", "SESSION", "SUBMITTED", 6100, hoursOld = 30),
        MmRemittance("r-4", "PRODUCT", "SNAPSHOT", 1950, hoursOld = 80),
    )

    val relief = mutableStateListOf(
        MmRelief("d-1", "BGC CLINIC", "2026-09-11", "OPEN"),
        MmRelief("d-2", "CEBU-TOUR", "2026-09-12", "OPEN"),
    )
    var inviteState by mutableStateOf("Invite from MANAGER cara: cover BGC 2026-09-11 (PENDING)")
    var broadcastState by mutableStateOf("No live broadcast")

    val notices = mutableStateListOf(
        MmNotice("n-1", "Relief invite", "cara invites you to cover BGC CLINIC on 2026-09-11.", false),
        MmNotice("n-2", "Remittance sealed", "SESSION snapshot r-4 sealed; undo window closed after 48h.", false),
        MmNotice("n-3", "Branch Day PAST", "CEBU-TOUR day moved to PAST; edits locked except MANAGER.", true),
    )

    val audit = mutableStateListOf(
        "08:12 ana clocked in at MAKATI CLINIC",
        "08:40 ben submitted SESSION draft r-3",
        "09:05 cara voided s-105 (client rescheduled)",
    )

    var clockedIn by mutableStateOf(false)

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun branchSessions(branchId: String): List<MmSession> = sessions.filter { it.branchId == branchId }

    fun exceptions(branchId: String): List<MmSession> =
        branchSessions(branchId).filter {
            it.voided || ((it.status == MmSessionStatus.NO_SHOW ||
                it.status == MmSessionStatus.CANCELLED) && !it.walkIn)
        }

    fun branchById(id: String): MmBranch = branches.first { it.id == id }
}
