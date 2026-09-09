package com.companyb.companyapp.proto.nightshift

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #780 — night-shift prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class NsSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class NsDayStatus { OPEN, PAST, REMITTED }
enum class NsScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class NsUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    var locked: Boolean = false,
)

data class NsBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: NsDayStatus,
)

class NsSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    status: NsSessionStatus,
    val price: Int,
    val practitioners: String,
    voided: Boolean = false,
    voidReason: String = "",
) {
    var status by mutableStateOf(status)
    var voided by mutableStateOf(voided)
    var voidReason by mutableStateOf(voidReason)
}

class NsClient(
    val id: String,
    val name: String,
    val contact: String,
    val gender: String,
    val age: Int,
    pending: Int,
) {
    var pendingCount by mutableStateOf(pending)
}

class NsRemittance(
    val id: String,
    val flow: String,
    val branchName: String,
    val amount: Int,
    val ageHours: Int?,
    stage: String,
) {
    var stage by mutableStateOf(stage)
}

class NsNotice(
    val id: String,
    val title: String,
    val body: String,
    read: Boolean,
) {
    var read by mutableStateOf(read)
}

data class NsAuditEntry(val seq: Int, val actor: String, val action: String)

class NsReliefItem(val id: String, val kind: String, val text: String, state: String) {
    var state by mutableStateOf(state)
}

class NightShiftRepo {
    var rev by mutableStateOf(0)
    var dim by mutableStateOf(2)
    var quietHours by mutableStateOf(true)
    var clockedIn by mutableStateOf(false)
    var clockInAt by mutableStateOf("22:00")
    var anonymized by mutableStateOf(false)

    val users = listOf(
        NsUser(
            "u-ana", "ana", "Ana Reyes", "Practitioner", "b-makati",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY"),
        ),
        NsUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-makati",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS"),
        ),
        NsUser(
            "u-cara", "cara", "Cara Lim", "MANAGER", "b-bgc",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_PAST"),
        ),
        NsUser("u-dan", "dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        NsUser("u-eli", "eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        NsBranch("b-makati", "MAKATI", "CLINIC", NsDayStatus.OPEN),
        NsBranch("b-bgc", "BGC", "CLINIC", NsDayStatus.PAST),
        NsBranch("b-cebu", "CEBU-TOUR", "PROVINCIAL_TOUR", NsDayStatus.REMITTED),
        NsBranch("b-tondo", "TONDO-MISSION", "MEDICAL_MISSION", NsDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        NsSession("s-01", "21:00", "MARIA CLARA", "b-makati", "Follow-up", false, NsSessionStatus.COMPLETED, 1200, "ANA REYES"),
        NsSession("s-02", "22:30", "JOSE RIZAL", "b-makati", "Initial", false, NsSessionStatus.PENDING, 1500, "ANA REYES"),
        NsSession("s-03", "23:15", "WALK-IN GUEST", "b-makati", "Walk-in", true, NsSessionStatus.PENDING, 800, "ANA REYES"),
        NsSession("s-04", "01:00", "ANDRES BONIFACIO", "b-makati", "Follow-up", false, NsSessionStatus.NO_SHOW, 1200, "BEN CRUZ"),
        NsSession("s-05", "22:00", "GABRIELA SILANG", "b-bgc", "Initial", false, NsSessionStatus.PENDING, 1500, "CARA LIM"),
        NsSession("s-06", "00:30", "APOLINARIO MABINI", "b-bgc", "Follow-up", false, NsSessionStatus.CANCELLED, 1200, "CARA LIM"),
        NsSession("s-07", "21:30", "MELCHORA AQUINO", "b-cebu", "Tour visit", false, NsSessionStatus.COMPLETED, 1000, "DAN UY"),
        NsSession("s-08", "02:00", "WALK-IN GUEST", "b-tondo", "Mission", true, NsSessionStatus.PENDING, 0, "ANA REYES"),
    )

    val clients = mutableStateListOf(
        NsClient("c-01", "Maria Clara", "+63 917 111 0001", "F", 34, 1),
        NsClient("c-02", "Jose Rizal", "+63 917 111 0002", "M", 41, 0),
        NsClient("c-03", "Gabriela Silang", "+63 917 111 0003", "F", 29, 0),
        NsClient("c-04", "Andres Bonifacio", "+63 917 111 0004", "M", 52, 0),
        NsClient("c-05", "Melchora Aquino", "+63 917 111 0005", "F", 63, 0),
    )

    val remittances = mutableStateListOf(
        NsRemittance("r-01", "SESSION", "MAKATI", 5900, null, "DRAFT"),
        NsRemittance("r-02", "PRODUCT", "MAKATI", 2400, null, "DRAFT"),
        NsRemittance("r-03", "SESSION", "BGC", 8100, 30, "SUBMITTED"),
        NsRemittance("r-04", "PRODUCT", "CEBU-TOUR", 3200, 72, "SEALED"),
    )

    val notices = mutableStateListOf(
        NsNotice("n-01", "Relief accepted", "Ben Cruz accepted relief duty at MAKATI for tonight.", false),
        NsNotice("n-02", "Remittance sealed", "CEBU-TOUR product snapshot sealed. Undo window closed.", false),
        NsNotice("n-03", "Quiet hours on", "Alerts stay dim until 06:00 Asia/Manila.", true),
        NsNotice("n-04", "Branch day PAST", "BGC branch day moved PAST the 04:00 boundary.", true),
    )

    val audit = mutableStateListOf(
        NsAuditEntry(3, "system", "branch day boundary evaluated at 04:00 Asia/Manila"),
        NsAuditEntry(2, "cara", "sealed remittance r-04 (PRODUCT, CEBU-TOUR)"),
        NsAuditEntry(1, "ana", "completed session s-01"),
    )
    private var seq = 3

    val relief = mutableStateListOf(
        NsReliefItem("d-01", "DUTY", "Cover TONDO-MISSION 01:00-03:00 medication round", "OPEN"),
        NsReliefItem("i-01", "INVITE", "Cara Lim invites you to BGC overnight stocktake", "OPEN"),
        NsReliefItem("q-01", "REQUEST", "Your broadcast request for MAKATI graveyard cover", "LIVE"),
    )

    fun log(actor: String, action: String) {
        seq += 1
        audit.add(0, NsAuditEntry(seq, actor, action))
        rev += 1
    }

    fun touch() {
        rev += 1
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun displayName(c: NsClient): String = if (anonymized) "CLIENT " + c.id.uppercase() else c.name
}
