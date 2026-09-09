package com.companyb.companyapp.proto.terminalgreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #768 — terminal-green prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class TgSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class TgDayStatus { OPEN, PAST, REMITTED }
enum class TgScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class TgUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class TgBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: TgDayStatus,
)

data class TgSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: TgSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class TgClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class TgRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val ageHours: Int? = null,
)

data class TgNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class TgAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class TgReliefItem(
    val id: String,
    val kind: String,
    val text: String,
    var state: String,
)

class TerminalGreenRepo {
    val users = listOf(
        TgUser(
            "u-ana", "ana", "Ana Reyes", "Practitioner", "b-makati",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY"),
        ),
        TgUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-makati",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS"),
        ),
        TgUser(
            "u-cara", "cara", "Cara Lim", "MANAGER", "b-bgc",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_PAST"),
        ),
        TgUser("u-dan", "dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        TgUser("u-eli", "eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        TgBranch("b-makati", "MAKATI", "CLINIC", TgDayStatus.OPEN),
        TgBranch("b-bgc", "BGC", "CLINIC", TgDayStatus.PAST),
        TgBranch("b-cebu", "CEBU-TOUR", "PROVINCIAL_TOUR", TgDayStatus.REMITTED),
        TgBranch("b-tondo", "TONDO-MISSION", "MEDICAL_MISSION", TgDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        TgSession(
            "s-01", "09:00", "MARIA CLARA", "b-makati", "Follow-up",
            false, TgSessionStatus.COMPLETED, 1200, "ANA REYES",
        ),
        TgSession(
            "s-02", "10:00", "JOSE RIZAL", "b-makati", "Initial",
            false, TgSessionStatus.PENDING, 1500, "ANA REYES",
        ),
        TgSession(
            "s-03", "11:30", "WALK-IN GUEST", "b-makati", "Walk-in",
            true, TgSessionStatus.PENDING, 1000, "ANA REYES",
        ),
        TgSession(
            "s-04", "13:00", "LIZA S.", "b-makati", "Follow-up",
            false, TgSessionStatus.NO_SHOW, 1200, "ANA REYES",
        ),
        TgSession(
            "s-05", "14:30", "NORA A.", "b-makati", "Initial",
            false, TgSessionStatus.CANCELLED, 1500, "BEN CRUZ",
        ),
        TgSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, TgSessionStatus.PENDING, 1200, "CARA LIM"),
    )

    val clients = mutableStateListOf(
        TgClient("c-01", "MARIA CLARA", "0917-111-0001", 0),
        TgClient("c-02", "JOSE RIZAL", "0917-111-0002", 1),
        TgClient("c-03", "LIZA S.", "0917-111-0003", 0),
        TgClient("c-04", "ANON #A17", "--redacted--", 0, anonymized = true, gender = "F", age = 42),
        TgClient("c-05", "NORA A.", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        TgRemittance("r-01", "SESSION", "DRAFT", 18400, "MAKATI"),
        TgRemittance("r-02", "PRODUCT", "DRAFT", 5200, "MAKATI"),
        TgRemittance("r-03", "SESSION", "SUBMITTED", 24100, "CEBU-TOUR", ageHours = 30),
        TgRemittance("r-04", "SESSION", "SUBMITTED", 19800, "BGC", ageHours = 80),
    )

    val notices = mutableStateListOf(
        TgNotice("n-01", "RELIEF GRANT", "CARA LIM granted you relief edit access at BGC for today.", false),
        TgNotice("n-02", "REMINDER", "Session s-02 JOSE RIZAL starts 10:00 at MAKATI.", false),
        TgNotice("n-03", "REMITTANCE SEALED", "CEBU-TOUR SESSION snapshot frozen. Undo window 48h.", true),
    )

    val audit = mutableStateListOf(
        TgAuditEntry(3, "system", "boot: terminal-green console attached"),
        TgAuditEntry(2, "cara", "grant relief edit @BGC day=today"),
        TgAuditEntry(1, "ana", "login tty0"),
    )

    val reliefDuties = mutableStateListOf(
        TgReliefItem("d-01", "DUTY", "BGC relief duty today — edit access GRANTED", "active"),
    )
    val reliefRequests = mutableStateListOf(
        TgReliefItem("q-01", "REQUEST", "broadcast: relief @CEBU-TOUR 2026-09-12 (one live per date)", "live"),
    )
    val reliefInvites = mutableStateListOf(
        TgReliefItem("i-01", "INVITE", "CARA LIM invites you: relief @BGC 2026-09-13", "pending"),
    )

    private var seq = 3
    var sessionCounter by mutableStateOf(7)

    fun log(actor: String, action: String) {
        seq += 1
        audit.add(0, TgAuditEntry(seq, actor, action))
    }
}
