package com.companyb.companyapp.proto.ganttbranch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #815 — gantt-branch prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class GbSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class GbDayStatus { OPEN, PAST, REMITTED }
enum class GbScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class GbUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class GbBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: GbDayStatus,
)

data class GbSession(
    val id: String,
    val day: Int,
    val startMin: Int,
    val durMin: Int,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: GbSessionStatus,
    val price: Int,
    val practitioner: String,
    var voided: Boolean = false,
    var voidReason: String = "",
) {
    fun label(): String {
        val h = startMin / 60
        val m = startMin % 60
        val hh = h.toString().padStart(2, '0')
        val mm = m.toString().padStart(2, '0')
        return "$hh:$mm"
    }
}

data class GbClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class GbRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val ageHours: Int? = null,
)

data class GbNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class GbAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class GbReliefItem(
    val id: String,
    val kind: String,
    val detail: String,
    var state: String,
)

val GbWeekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
const val GbAxisStart = 480
const val GbAxisEnd = 1200

class GbFakeRepo {
    var loggedInUser by mutableStateOf<GbUser?>(null)
    var loginName by mutableStateOf("")
    var loginError by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var currentBranchId by mutableStateOf("b1")
    var screen by mutableStateOf(GbScreen.HOME)
    var ganttDay by mutableStateOf(0)
    var sessionFilter by mutableStateOf("ALL")
    var selectedSessionId by mutableStateOf<String?>(null)
    var voidDraft by mutableStateOf("")
    var clientQuery by mutableStateOf("")
    var showAnonymized by mutableStateOf(false)
    var financeFlow by mutableStateOf("SESSION")
    var auditSeq by mutableStateOf(100)

    val practitioners = listOf("Jose Ramos", "Ana Villanueva", "Theo Bautista")

    val users = mutableStateListOf(
        GbUser("u1", "maria.coord", "Maria Santos", "Coordinator", "b1", listOf("session.create", "session.void", "remit.draft")),
        GbUser("u2", "jose.prac", "Jose Ramos", "Practitioner", "b1", listOf("session.complete")),
        GbUser("u3", "ana.mgr", "Ana Villanueva", "MANAGER", "b2", listOf("branch.close", "remit.submit", "user.manage")),
        GbUser("u4", "luis.acct", "Luis Cruz", "Accountant", "b1", listOf("remit.review", "snapshot.undo")),
        GbUser("u5", "new.orb", "Ramon Onboard", "ONBOARDING", "b1", emptyList(), locked = true),
    )

    val branches = mutableStateListOf(
        GbBranch("b1", "Mesa Central", "flagship", GbDayStatus.OPEN),
        GbBranch("b2", "Dune East", "satellite", GbDayStatus.OPEN),
        GbBranch("b3", "Cactus West", "satellite", GbDayStatus.PAST),
        GbBranch("b4", "Oasis North", "kiosk", GbDayStatus.REMITTED),
    )

    val sessions = mutableStateListOf(
        GbSession("s1", 0, 540, 60, "Luz Fernandez", "b1", "booking", false, GbSessionStatus.COMPLETED, 1200, "Jose Ramos"),
        GbSession("s2", 0, 630, 45, "Walk-in Guest", "b1", "walk-in", true, GbSessionStatus.PENDING, 800, "Jose Ramos"),
        GbSession("s3", 0, 660, 60, "Nadia Aquino", "b1", "booking", false, GbSessionStatus.PENDING, 1500, "Ana Villanueva"),
        GbSession("s4", 1, 780, 60, "Paolo Gutierrez", "b2", "booking", false, GbSessionStatus.NO_SHOW, 1000, "Ana Villanueva"),
        GbSession("s5", 1, 870, 45, "Irene Ocampo", "b2", "walk-in", true, GbSessionStatus.COMPLETED, 900, "Theo Bautista"),
        GbSession("s6", 2, 900, 60, "Marco Salazar", "b1", "booking", false, GbSessionStatus.CANCELLED, 1100, "Jose Ramos"),
        GbSession("s7", 2, 600, 90, "Sofia Reyes", "b1", "booking", false, GbSessionStatus.PENDING, 1800, "Theo Bautista"),
        GbSession("s8", 3, 720, 60, "Diego Luna", "b1", "booking", false, GbSessionStatus.PENDING, 1300, "Ana Villanueva"),
        GbSession("s9", 3, 840, 45, "Walk-in Guest", "b1", "walk-in", true, GbSessionStatus.PENDING, 800, "Jose Ramos"),
        GbSession("s10", 4, 570, 60, "Elena Torres", "b2", "booking", false, GbSessionStatus.PENDING, 1400, "Theo Bautista"),
        GbSession("s11", 4, 960, 60, "Rosa Diaz", "b1", "booking", false, GbSessionStatus.COMPLETED, 1250, "Ana Villanueva"),
    )

    val clients = mutableStateListOf(
        GbClient("c1", "Luz Fernandez", "0917-111-2233", 0, gender = "F", age = 34),
        GbClient("c2", "Nadia Aquino", "0918-222-3344", 1, gender = "F", age = 29),
        GbClient("c3", "Paolo Gutierrez", "0919-333-4455", 0, gender = "M", age = 41),
        GbClient("c4", "Irene Ocampo", "0920-444-5566", 0, gender = "F", age = 52),
        GbClient("c5", "Marco Salazar", "0921-555-6677", 0, gender = "M", age = 37),
        GbClient("c6", "Veiled Record 7", "withheld", 0, anonymized = true),
    )

    val remittances = mutableStateListOf(
        GbRemittance("r1", "SESSION", "draft", 3200, "Mesa Central"),
        GbRemittance("r2", "PRODUCT", "draft", 1450, "Mesa Central"),
        GbRemittance("r3", "SESSION", "submitted", 5100, "Dune East"),
        GbRemittance("r4", "SESSION", "snapshot", 4800, "Oasis North", ageHours = 30),
        GbRemittance("r5", "PRODUCT", "snapshot", 2100, "Oasis North", ageHours = 60),
    )

    val notices = mutableStateListOf(
        GbNotice("n1", "Relief invite", "Mesa Central needs cover 14:00-18:00 today.", false),
        GbNotice("n2", "Snapshot window", "Oasis North SESSION snapshot can Undo within 48h.", false),
        GbNotice("n3", "Branch Day closed", "Cactus West rolled to PAST at the 04:00 boundary.", true),
    )

    val audit = mutableStateListOf(
        GbAuditEntry(98, "ana.mgr", "closed Branch Day for Oasis North"),
        GbAuditEntry(99, "maria.coord", "voided session s6 (client asked to rebook)"),
        GbAuditEntry(100, "luis.acct", "took SESSION snapshot r4"),
    )

    val relief = mutableStateListOf(
        GbReliefItem("d1", "duty", "Mesa Central front desk 09:00-13:00", "open"),
        GbReliefItem("i1", "invite", "Cover Dune East 14:00-18:00", "open"),
        GbReliefItem("q1", "request", "Swapped rest day with Jose", "open"),
    )

    fun auditAs(user: String, action: String) {
        auditSeq += 1
        audit.add(0, GbAuditEntry(auditSeq, user, action))
    }

    fun currentBranch(): GbBranch = branches.first { it.id == currentBranchId }

    fun ganttSessions(): List<GbSession> =
        sessions.filter { it.branchId == currentBranchId && it.day == ganttDay }

    fun branchSessions(): List<GbSession> =
        sessions.filter { it.branchId == currentBranchId }
            .filter { sessionFilter == "ALL" || it.status.name == sessionFilter }

    fun filteredClients(): List<GbClient> {
        val q = clientQuery.trim().lowercase()
        return clients.filter {
            (q.isEmpty() || it.name.lowercase().contains(q)) &&
                (!showAnonymized || it.anonymized)
        }
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun tryLogin(): Boolean {
        val match = users.firstOrNull { it.login == loginName.trim() }
        return when {
            match == null -> {
                loginError = "Unknown login — try maria.coord"
                false
            }
            match.locked -> {
                loginError = "ONBOARDING account is locked until activation"
                false
            }
            else -> {
                loggedInUser = match
                loginError = ""
                currentBranchId = match.homeBranchId
                auditAs(match.login, "logged in")
                true
            }
        }
    }
}
