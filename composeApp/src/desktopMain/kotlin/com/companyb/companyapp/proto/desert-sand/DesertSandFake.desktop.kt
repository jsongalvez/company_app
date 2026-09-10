package com.companyb.companyapp.proto.desertsand

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #808 — desert-sand prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class DsSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class DsDayStatus { OPEN, PAST, REMITTED }
enum class DsScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class DsUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class DsBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: DsDayStatus,
)

data class DsSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: DsSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class DsClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class DsRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val ageHours: Int? = null,
)

data class DsNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class DsAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class DsReliefItem(
    val id: String,
    val kind: String,
    val detail: String,
    var state: String,
)

class DsFakeRepo {
    var loggedInUser by mutableStateOf<DsUser?>(null)
    var loginName by mutableStateOf("")
    var loginError by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var currentBranchId by mutableStateOf("b1")
    var screen by mutableStateOf(DsScreen.HOME)
    var sessionFilter by mutableStateOf("ALL")
    var selectedSessionId by mutableStateOf<String?>(null)
    var voidDraft by mutableStateOf("")
    var clientQuery by mutableStateOf("")
    var showAnonymized by mutableStateOf(false)
    var financeFlow by mutableStateOf("SESSION")
    var auditSeq by mutableStateOf(100)

    val users = mutableStateListOf(
        DsUser("u1", "maria.coord", "Maria Santos", "Coordinator", "b1", listOf("session.create", "session.void", "remit.draft")),
        DsUser("u2", "jose.prac", "Jose Ramos", "Practitioner", "b1", listOf("session.complete")),
        DsUser("u3", "ana.mgr", "Ana Villanueva", "MANAGER", "b2", listOf("branch.close", "remit.submit", "user.manage")),
        DsUser("u4", "luis.acct", "Luis Cruz", "Accountant", "b1", listOf("remit.review", "snapshot.undo")),
        DsUser("u5", "new.orb", "Ramon Onboard", "ONBOARDING", "b1", emptyList(), locked = true),
    )

    val branches = mutableStateListOf(
        DsBranch("b1", "Mesa Central", "flagship", DsDayStatus.OPEN),
        DsBranch("b2", "Dune East", "satellite", DsDayStatus.OPEN),
        DsBranch("b3", "Cactus West", "satellite", DsDayStatus.PAST),
        DsBranch("b4", "Oasis North", "kiosk", DsDayStatus.REMITTED),
    )

    val sessions = mutableStateListOf(
        DsSession("s1", "09:00", "Luz Fernandez", "b1", "booking", false, DsSessionStatus.COMPLETED, 1200, "Jose Ramos"),
        DsSession("s2", "10:30", "Walk-in Guest", "b1", "walk-in", true, DsSessionStatus.PENDING, 800, "Jose Ramos"),
        DsSession("s3", "11:00", "Nadia Aquino", "b1", "booking", false, DsSessionStatus.PENDING, 1500, "Maria Santos"),
        DsSession("s4", "13:00", "Paolo Gutierrez", "b2", "booking", false, DsSessionStatus.NO_SHOW, 1000, "Ana Villanueva"),
        DsSession("s5", "14:30", "Irene Ocampo", "b2", "walk-in", true, DsSessionStatus.COMPLETED, 900, "Ana Villanueva"),
        DsSession("s6", "15:00", "Marco Salazar", "b1", "booking", false, DsSessionStatus.CANCELLED, 1100, "Jose Ramos"),
    )

    val clients = mutableStateListOf(
        DsClient("c1", "Luz Fernandez", "0917-111-2233", 0, gender = "F", age = 34),
        DsClient("c2", "Nadia Aquino", "0918-222-3344", 1, gender = "F", age = 29),
        DsClient("c3", "Paolo Gutierrez", "0919-333-4455", 0, gender = "M", age = 41),
        DsClient("c4", "Irene Ocampo", "0920-444-5566", 0, gender = "F", age = 52),
        DsClient("c5", "Marco Salazar", "0921-555-6677", 0, gender = "M", age = 37),
        DsClient("c6", "Veiled Record 7", "withheld", 0, anonymized = true),
    )

    val remittances = mutableStateListOf(
        DsRemittance("r1", "SESSION", "draft", 3200, "Mesa Central"),
        DsRemittance("r2", "PRODUCT", "draft", 1450, "Mesa Central"),
        DsRemittance("r3", "SESSION", "submitted", 5100, "Dune East"),
        DsRemittance("r4", "SESSION", "snapshot", 4800, "Oasis North", ageHours = 30),
        DsRemittance("r5", "PRODUCT", "snapshot", 2100, "Oasis North", ageHours = 60),
    )

    val notices = mutableStateListOf(
        DsNotice("n1", "Relief invite", "Mesa Central needs cover 14:00-18:00 today.", false),
        DsNotice("n2", "Snapshot window", "Oasis North SESSION snapshot can Undo within 48h.", false),
        DsNotice("n3", "Branch Day closed", "Cactus West rolled to PAST at the 04:00 boundary.", true),
    )

    val audit = mutableStateListOf(
        DsAuditEntry(98, "ana.mgr", "closed Branch Day for Oasis North"),
        DsAuditEntry(99, "maria.coord", "voided session s6 (client asked to rebook)"),
        DsAuditEntry(100, "luis.acct", "took SESSION snapshot r4"),
    )

    val relief = mutableStateListOf(
        DsReliefItem("d1", "duty", "Mesa Central front desk 09:00-13:00", "open"),
        DsReliefItem("i1", "invite", "Cover Dune East 14:00-18:00", "open"),
        DsReliefItem("q1", "request", "Swapped Oct 3 rest day with Jose", "open"),
    )

    fun auditAs(user: String, action: String) {
        auditSeq += 1
        audit.add(0, DsAuditEntry(auditSeq, user, action))
    }

    fun currentBranch(): DsBranch = branches.first { it.id == currentBranchId }

    fun branchSessions(): List<DsSession> =
        sessions.filter { it.branchId == currentBranchId }
            .filter { sessionFilter == "ALL" || it.status.name == sessionFilter }

    fun filteredClients(): List<DsClient> {
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
