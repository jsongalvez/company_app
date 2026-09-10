package com.companyb.companyapp.proto.forestcalm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #807 — forest-calm prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly.

enum class FcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class FcDayStatus { OPEN, PAST, REMITTED }
enum class FcScreen { HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class FcUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class FcBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: FcDayStatus,
)

data class FcSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: FcSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class FcClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class FcRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val ageHours: Int? = null,
)

data class FcNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class FcAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class FcReliefItem(
    val id: String,
    val kind: String,
    val text: String,
    var state: String,
)

class ForestCalmRepo {
    val users = listOf(
        FcUser(
            "u-ana", "ana", "Ana Reyes", "Practitioner", "b-makati",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "CLOCK_IN"),
        ),
        FcUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-makati",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS", "CLOCK_IN"),
        ),
        FcUser(
            "u-cara", "cara", "Cara Lim", "MANAGER", "b-bgc",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_PAST", "CLOCK_IN"),
        ),
        FcUser("u-dan", "dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        FcUser("u-eli", "eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        FcBranch("b-makati", "Makati Grove", "CLINIC", FcDayStatus.OPEN),
        FcBranch("b-bgc", "BGC Clearing", "CLINIC", FcDayStatus.PAST),
        FcBranch("b-cebu", "Cebu Trail", "PROVINCIAL_TOUR", FcDayStatus.REMITTED),
        FcBranch("b-tondo", "Tondo Understory", "MEDICAL_MISSION", FcDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        FcSession(
            "s-01", "09:00", "Maria Clara", "b-makati", "Follow-up",
            false, FcSessionStatus.COMPLETED, 1200, "Ana Reyes",
        ),
        FcSession(
            "s-02", "10:00", "Jose Rizal", "b-makati", "Initial",
            false, FcSessionStatus.PENDING, 1500, "Ana Reyes",
        ),
        FcSession(
            "s-03", "11:30", "Walk-in Guest", "b-makati", "Walk-in",
            true, FcSessionStatus.PENDING, 1000, "Ana Reyes",
        ),
        FcSession(
            "s-04", "13:00", "Liza S.", "b-makati", "Follow-up",
            false, FcSessionStatus.NO_SHOW, 1200, "Ana Reyes",
        ),
        FcSession(
            "s-05", "14:30", "Nora A.", "b-makati", "Initial",
            false, FcSessionStatus.CANCELLED, 1500, "Ben Cruz",
        ),
        FcSession("s-06", "15:00", "Felipe P.", "b-bgc", "Follow-up", false, FcSessionStatus.PENDING, 1200, "Cara Lim"),
        FcSession("s-07", "09:30", "Trail Walker", "b-cebu", "Walk-in", true, FcSessionStatus.COMPLETED, 900, "Ben Cruz"),
    )

    val clients = mutableStateListOf(
        FcClient("c-01", "Maria Clara", "0917-111-0001", 0),
        FcClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        FcClient("c-03", "Liza S.", "0917-111-0003", 0),
        FcClient("c-04", "Seedling #A17", "withheld", 0, anonymized = true, gender = "F", age = 42),
        FcClient("c-05", "Nora A.", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        FcRemittance("r-01", "SESSION", "DRAFT", 18400, "Makati Grove"),
        FcRemittance("r-02", "PRODUCT", "DRAFT", 5200, "Makati Grove"),
        FcRemittance("r-03", "SESSION", "SEALED", 24100, "Cebu Trail", ageHours = 30),
        FcRemittance("r-04", "SESSION", "SEALED", 19800, "BGC Clearing", ageHours = 80),
    )

    val notices = mutableStateListOf(
        FcNotice("n-01", "Relief grant", "Cara Lim granted you relief edit access at BGC Clearing for today.", false),
        FcNotice("n-02", "Session reminder", "Session s-02 Jose Rizal starts 10:00 at Makati Grove.", false),
        FcNotice("n-03", "Remittance sealed", "Cebu Trail SESSION snapshot frozen. Undo window 48h.", true),
    )

    val audit = mutableStateListOf(
        FcAuditEntry(3, "system", "dawn: forest-calm floor opened"),
        FcAuditEntry(2, "cara", "grant relief edit @BGC Clearing day=today"),
        FcAuditEntry(1, "ana", "login grove terminal"),
    )

    val reliefDuties = mutableStateListOf(
        FcReliefItem("d-01", "DUTY", "BGC Clearing relief duty today — edit access granted", "active"),
    )
    val reliefRequests = mutableStateListOf(
        FcReliefItem("q-01", "REQUEST", "broadcast: relief @Cebu Trail 2026-09-12 (one live per date)", "live"),
    )
    val reliefInvites = mutableStateListOf(
        FcReliefItem("i-01", "INVITE", "Cara Lim invites you: relief @BGC Clearing 2026-09-13", "pending"),
    )

    var clockedIn by mutableStateOf(false)
    var sessionCounter by mutableStateOf(8)
    var draftCounter by mutableStateOf(5)
    var anonymizedView by mutableStateOf(false)

    private var seq = 3

    fun log(actor: String, action: String) {
        seq += 1
        audit.add(0, FcAuditEntry(seq, actor, action))
    }
}
