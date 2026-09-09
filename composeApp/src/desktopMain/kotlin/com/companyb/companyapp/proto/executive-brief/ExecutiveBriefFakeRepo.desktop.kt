package com.companyb.companyapp.proto.executivebrief

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #770 — executive-brief prototype fake data. Local only: no ApiClient, no Ktor, no backend.
// The Owner opens a morning edition: yesterday vs target, exception-only alerts, drill-down on demand.

enum class EbSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class EbDayStatus { OPEN, PAST, REMITTED }

data class EbUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class EbBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: EbDayStatus,
    val yesterdayRevenue: Int,
    val yesterdayTarget: Int,
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    val noShows: Int,
)

data class EbSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    val status: EbSessionStatus,
    val price: Int,
    val practitioners: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class EbClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class EbRemittance(
    val id: String,
    val flow: String,
    val stage: String,
    val amount: Int,
    val branchName: String,
    val submittedHoursAgo: Int? = null,
)

data class EbNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean,
)

data class EbException(
    val id: String,
    val severity: String,
    val headline: String,
    val detail: String,
    val dest: EbDest,
)

enum class EbDest(val label: String) {
    BRIEF("Morning Brief"),
    HOME("Home & Clock"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

class EbRepo {
    val users = listOf(
        EbUser("u-olivia", "Olivia Sy", "OWNER", "b-makati", listOf("VIEW_BRANCH_DATA", "MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_BRANCH_DATA")),
        EbUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        EbUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        EbUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        EbUser("u-dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        EbUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        EbBranch("b-makati", "Makati", "CLINIC", EbDayStatus.OPEN, yesterdayRevenue = 18400, yesterdayTarget = 20000, sessionsDone = 14, sessionsPlanned = 16, noShows = 1),
        EbBranch("b-bgc", "BGC", "CLINIC", EbDayStatus.PAST, yesterdayRevenue = 9600, yesterdayTarget = 15000, sessionsDone = 8, sessionsPlanned = 12, noShows = 3),
        EbBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", EbDayStatus.REMITTED, yesterdayRevenue = 22100, yesterdayTarget = 18000, sessionsDone = 19, sessionsPlanned = 19, noShows = 0),
        EbBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", EbDayStatus.OPEN, yesterdayRevenue = 0, yesterdayTarget = 0, sessionsDone = 22, sessionsPlanned = 25, noShows = 0),
    )

    val sessions = mutableStateListOf(
        EbSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, EbSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        EbSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, EbSessionStatus.PENDING, 1500, "Ana Reyes"),
        EbSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, EbSessionStatus.PENDING, 1000, "Ana Reyes"),
        EbSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, EbSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
        EbSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, EbSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        EbSession("s-06", "09:30", "FPJ", "b-bgc", "Follow-up", false, EbSessionStatus.PENDING, 1200, "Cara Lim"),
        EbSession("s-07", "10:30", "Gloria Diaz", "b-bgc", "Initial", false, EbSessionStatus.NO_SHOW, 1500, "Cara Lim"),
        EbSession("s-08", "15:00", "Manny Pacquiao", "b-cebu", "Follow-up", false, EbSessionStatus.COMPLETED, 1200, "Ana Reyes"),
    )

    val clients = mutableStateListOf(
        EbClient("c-01", "Maria Clara", "0917-111-0001", 0),
        EbClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        EbClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        EbClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
        EbClient("c-05", "Nora Aunor", "0917-111-0005", 0),
        EbClient("c-06", "Gloria Diaz", "0917-111-0006", 1),
    )

    val remittances = mutableStateListOf(
        EbRemittance("r-01", "SESSION", "Snapshot", 18400, "Makati", submittedHoursAgo = 5),
        EbRemittance("r-02", "PRODUCT", "Draft", 3200, "Makati"),
        EbRemittance("r-03", "SESSION", "Snapshot", 22100, "Cebu Tour", submittedHoursAgo = 72),
    )

    val notices = mutableStateListOf(
        EbNotice("n-01", "Relief request approved", "BGC granted you edit access for today.", false),
        EbNotice("n-02", "Remittance snapshot sealed", "SESSION snapshot r-01 is immutable; Undo open for 48h.", false),
        EbNotice("n-03", "Branch day turned PAST", "BGC passed the 04:00 Manila boundary.", true),
    )

    val exceptions = mutableStateListOf(
        EbException("x-01", "ACTION", "BGC sits PAST with no remittance", "Yesterday closed at 04:00 Manila; the SESSION draft is still unsealed. Nudge the Coordinator.", EbDest.FINANCE),
        EbException("x-02", "WATCH", "BGC no-show rate 25% (3 of 12)", "Triple the 8% house bar. Two were first visits — consider reminder coverage.", EbDest.SESSIONS),
        EbException("x-03", "ACTION", "Eli Santos is ONBOARDING-locked", "Zero capabilities; nothing derives until MANAGE_USERS grants a real role.", EbDest.TEAM),
        EbException("x-04", "WATCH", "Snapshot r-01 Undo window closes in 43h", "After that the SESSION freeze is permanent. Confirm the P&L once.", EbDest.FINANCE),
    )

    val audit = mutableStateListOf(
        "08:55 Olivia opened the morning brief (fake)",
        "08:52 Ana clocked in at Makati",
        "09:40 Session s-01 COMPLETED by Ana Reyes",
        "13:05 Session s-04 marked NO_SHOW by Ana Reyes",
        "16:20 Ben submitted SESSION remittance r-01 (snapshot sealed)",
    )

    val reliefBoard = mutableStateListOf(
        "Duty: Relief at Cebu Tour last Friday — view-only, expired 04:00 Manila",
        "Request: You asked BGC for edit access today — APPROVED",
        "Invite: Makati invites you (Practitioner) for Saturday — pending your accept",
    )

    var currentUser by mutableStateOf<EbUser?>(null)
    var currentBranchId by mutableStateOf("b-makati")
    var clockedIn by mutableStateOf(false)
    var sessionSeq by mutableStateOf(9)
    var remitSeq by mutableStateOf(4)
    var noticeSeq by mutableStateOf(4)

    fun branch(id: String): EbBranch = branches.first { it.id == id }
    fun currentBranch(): EbBranch = branch(currentBranchId)

    fun branchSessions(branchId: String): List<EbSession> =
        sessions.filter { it.branchId == branchId }.sortedBy { it.time }

    fun totalYesterday(): Int = branches.sumOf { it.yesterdayRevenue }
    fun totalTarget(): Int = branches.sumOf { it.yesterdayTarget }
    fun unreadCount(): Int = notices.count { !it.read }
    fun openDrafts(): Int = remittances.count { it.stage == "Draft" }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    private fun replaceSession(updated: EbSession) {
        val i = sessions.indexOfFirst { it.id == updated.id }
        if (i >= 0) sessions[i] = updated
    }

    fun advance(id: String, next: EbSessionStatus) {
        val s = sessions.first { it.id == id }
        if (s.walkIn && (next == EbSessionStatus.NO_SHOW || next == EbSessionStatus.CANCELLED)) return
        replaceSession(s.copy(status = next))
        log("Session $id moved to ${next.name} by ${currentUser?.name ?: "?"}")
        if (next != EbSessionStatus.PENDING) {
            notices.add(0, EbNotice("n-fake-$noticeSeq", "Session ${next.name.lowercase()}", "$id is now ${next.name}.", false))
            noticeSeq++
        }
    }

    fun voidSession(id: String, reason: String) {
        val s = sessions.first { it.id == id }
        replaceSession(s.copy(voided = true, voidReason = reason))
        log("Session $id VOIDED ($reason)")
    }

    fun unvoidSession(id: String) {
        val s = sessions.first { it.id == id }
        replaceSession(s.copy(voided = false, voidReason = ""))
        log("Session $id unvoided — record restored")
    }

    fun addSession(time: String, client: String, branchId: String, kind: String, walkIn: Boolean, price: Int) {
        val id = "s-%02d".format(sessionSeq++)
        sessions.add(EbSession(id, time, client, branchId, kind, walkIn, EbSessionStatus.PENDING, price, currentUser?.name ?: "?"))
        log("Session $id created (PENDING) for $client")
    }

    private fun replaceRemittance(updated: EbRemittance) {
        val i = remittances.indexOfFirst { it.id == updated.id }
        if (i >= 0) remittances[i] = updated
    }

    fun submitRemittance(id: String) {
        val r = remittances.first { it.id == id }
        replaceRemittance(r.copy(stage = "Snapshot", submittedHoursAgo = 0))
        log("${r.flow} remittance $id submitted — snapshot sealed")
    }

    fun undoRemittance(id: String, reason: String) {
        val r = remittances.first { it.id == id }
        replaceRemittance(r.copy(stage = "Draft", submittedHoursAgo = null))
        log("Remittance $id undone within 48h ($reason) — days unlocked, snapshot deleted")
    }

    fun toggleNotice(id: String) {
        val i = notices.indexOfFirst { it.id == id }
        if (i >= 0) notices[i] = notices[i].copy(read = !notices[i].read)
    }

    fun markAllRead() {
        for (i in notices.indices) notices[i] = notices[i].copy(read = true)
    }

    fun dismissException(id: String) {
        exceptions.removeAll { it.id == id }
        log("Brief exception $id acknowledged by ${currentUser?.name ?: "?"}")
    }
}
