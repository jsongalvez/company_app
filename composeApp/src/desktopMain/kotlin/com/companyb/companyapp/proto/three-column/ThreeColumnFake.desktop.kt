package com.companyb.companyapp.proto.threecolumn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #809 — three-column command prototype fake data. Local only: no ApiClient,
// no Ktor, no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class TcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class TcDayStatus { OPEN, PAST, REMITTED }
enum class TcScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

sealed interface TcSel {
    data object None : TcSel
    data class Session(val id: String) : TcSel
    data class Client(val id: String) : TcSel
    data class Remit(val id: String) : TcSel
    data class Notice(val id: String) : TcSel
    data class Relief(val id: String) : TcSel
    data class User(val id: String) : TcSel
}

data class TcUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class TcBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: TcDayStatus,
)

data class TcSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: TcSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class TcClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class TcRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val ageHours: Int? = null,
)

data class TcNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class TcAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class TcReliefItem(
    val id: String,
    val kind: String,
    val text: String,
    var state: String,
)

class ThreeColumnRepo {
    val users = listOf(
        TcUser(
            "u-ana", "ana", "Ana Reyes", "Practitioner", "b-makati",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "CLOCK_IN"),
        ),
        TcUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-makati",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS", "CLOCK_IN"),
        ),
        TcUser(
            "u-cara", "cara", "Cara Lim", "MANAGER", "b-bgc",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_PAST", "CLOCK_IN"),
        ),
        TcUser("u-dan", "dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        TcUser("u-eli", "eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        TcBranch("b-makati", "Makati Command", "CLINIC", TcDayStatus.OPEN),
        TcBranch("b-bgc", "BGC Command", "CLINIC", TcDayStatus.PAST),
        TcBranch("b-cebu", "Cebu Circuit", "PROVINCIAL_TOUR", TcDayStatus.REMITTED),
        TcBranch("b-tondo", "Tondo Post", "MEDICAL_MISSION", TcDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        TcSession(
            "s-01", "09:00", "Maria Clara", "b-makati", "Follow-up",
            false, TcSessionStatus.COMPLETED, 1200, "Ana Reyes",
        ),
        TcSession(
            "s-02", "10:00", "Jose Rizal", "b-makati", "Initial",
            false, TcSessionStatus.PENDING, 1500, "Ana Reyes",
        ),
        TcSession(
            "s-03", "11:30", "Walk-in Guest", "b-makati", "Walk-in",
            true, TcSessionStatus.PENDING, 1000, "Ana Reyes",
        ),
        TcSession(
            "s-04", "13:00", "Liza S.", "b-makati", "Follow-up",
            false, TcSessionStatus.NO_SHOW, 1200, "Ana Reyes",
        ),
        TcSession(
            "s-05", "14:30", "Nora A.", "b-makati", "Initial",
            false, TcSessionStatus.CANCELLED, 1500, "Ben Cruz",
        ),
        TcSession("s-06", "15:00", "Felipe P.", "b-bgc", "Follow-up", false, TcSessionStatus.PENDING, 1200, "Cara Lim"),
        TcSession("s-07", "09:30", "Circuit Walker", "b-cebu", "Walk-in", true, TcSessionStatus.COMPLETED, 900, "Ben Cruz"),
    )

    val clients = mutableStateListOf(
        TcClient("c-01", "Maria Clara", "0917-111-0001", 0),
        TcClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        TcClient("c-03", "Liza S.", "0917-111-0003", 0),
        TcClient("c-04", "Seedling #A17", "withheld", 0, anonymized = true, gender = "F", age = 42),
        TcClient("c-05", "Nora A.", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        TcRemittance("r-01", "SESSION", "DRAFT", 18400, "Makati Command"),
        TcRemittance("r-02", "PRODUCT", "DRAFT", 5200, "Makati Command"),
        TcRemittance("r-03", "SESSION", "SEALED", 24100, "Cebu Circuit", ageHours = 30),
        TcRemittance("r-04", "SESSION", "SEALED", 19800, "BGC Command", ageHours = 80),
    )

    val notices = mutableStateListOf(
        TcNotice("n-01", "Relief grant", "Cara Lim granted you relief edit access at BGC Command for today.", false),
        TcNotice("n-02", "Session reminder", "Session s-02 Jose Rizal starts 10:00 at Makati Command.", false),
        TcNotice("n-03", "Remittance sealed", "Cebu Circuit SESSION snapshot frozen. Undo window 48h.", true),
    )

    val audit = mutableStateListOf(
        TcAuditEntry(3, "system", "dawn: three-column board opened"),
        TcAuditEntry(2, "cara", "grant relief edit @BGC Command day=today"),
        TcAuditEntry(1, "ana", "login command terminal"),
    )

    val reliefDuties = mutableStateListOf(
        TcReliefItem("d-01", "DUTY", "BGC Command relief duty today — edit access granted", "active"),
    )
    val reliefRequests = mutableStateListOf(
        TcReliefItem("q-01", "REQUEST", "broadcast: relief @Cebu Circuit 2026-09-12 (one live per date)", "live"),
    )
    val reliefInvites = mutableStateListOf(
        TcReliefItem("i-01", "INVITE", "Cara Lim invites you: relief @BGC Command 2026-09-13", "pending"),
    )

    var clockedIn by mutableStateOf(false)
    var sessionCounter by mutableStateOf(8)
    var draftCounter by mutableStateOf(5)
    var anonymizedView by mutableStateOf(false)

    private var seq = 3

    fun log(actor: String, action: String) {
        seq += 1
        audit.add(0, TcAuditEntry(seq, actor, action))
    }
}
