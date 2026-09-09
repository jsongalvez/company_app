package com.companyb.companyapp.proto.guided_onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #766 — guided-onboarding prototype fake data. Local only: no ApiClient, no
// Ktor, no backend. resetSampleData restores every seed list so the human can
// replay the wizard from a clean first-run state.

enum class GdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class GdDayStatus { OPEN, PAST, REMITTED }
enum class GdStepState { DONE, CURRENT, NEXT }

enum class GdStep(val label: String, val coach: String) {
    WELCOME("Welcome & sample data", "Meet the journey, learn the reset button."),
    ROLES("Roles & your locked start", "Why ONBOARDING sees nothing until MANAGE_USERS grants a role."),
    BRANCH_DAY("Branch & day tour", "Pick a branch, read the day banner, clock in."),
    SESSIONS("Your first session", "PENDING flows, walk-in rule, void with reason."),
    CLIENTS("Clients are global", "One PENDING at a time, anonymized reporting view."),
    FINANCE("Finance & remittance", "SESSION + PRODUCT drafts, snapshot, Undo 48h."),
    TEAM("Team & relief", "Roles, slots, relief duty, requests, invites."),
    GRADUATE("Inbox, audit & graduate", "Mailbox, audit trail, light your last lantern."),
}

data class GdUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val explainer: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class GdBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: GdDayStatus,
    val tour: String,
)

data class GdSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: GdSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class GdClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class GdRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val submittedHoursAgo: Int? = null,
)

data class GdNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class GdMember(
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean = false,
)

private fun seedSessions() = listOf(
    GdSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, GdSessionStatus.COMPLETED, 1200, "Ana Reyes"),
    GdSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, GdSessionStatus.PENDING, 1500, "Ana Reyes"),
    GdSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, GdSessionStatus.PENDING, 1000, "Ana Reyes"),
    GdSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, GdSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
    GdSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, GdSessionStatus.CANCELLED, 1500, "Ana Reyes"),
    GdSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, GdSessionStatus.PENDING, 1200, "Cara Lim"),
)

private fun seedClients() = listOf(
    GdClient("c-01", "Maria Clara", "0917-111-0001", 0),
    GdClient("c-02", "Jose Rizal", "0917-111-0002", 1),
    GdClient("c-03", "Liza Soberano", "0917-111-0003", 0),
    GdClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
    GdClient("c-05", "Nora Aunor", "0917-111-0005", 0),
)

private fun seedRemittances() = listOf(
    GdRemittance("r-01", "SESSION", "Snapshot", 18400, "Makati", submittedHoursAgo = 5),
    GdRemittance("r-02", "PRODUCT", "Draft", 3200, "Makati"),
    GdRemittance("r-03", "SESSION", "Snapshot", 22100, "Cebu Tour", submittedHoursAgo = 72),
)

private fun seedNotices() = listOf(
    GdNotice("n-01", "Relief request approved", "BGC granted you edit access for today.", false),
    GdNotice("n-02", "Remittance snapshot sealed", "SESSION snapshot r-01 is immutable; Undo open for 48h.", false),
    GdNotice("n-03", "Branch day turned PAST", "BGC passed the 04:00 Manila boundary.", true),
)

private fun seedAudit() = listOf(
    "08:55 Ana clocked in at Makati",
    "09:40 Session s-01 COMPLETED by Ana Reyes",
    "13:05 Session s-04 marked NO_SHOW by Ana Reyes",
    "16:20 Ben submitted SESSION remittance r-01 (snapshot sealed)",
)

private fun seedRelief() = listOf(
    "Duty: Relief at Cebu Tour last Friday — view-only, expired 04:00 Manila",
    "Request: You asked BGC for edit access today — APPROVED",
    "Invite: Makati invites you (Practitioner) for Saturday — pending your accept",
)

class GuidedRepo {
    val users = listOf(
        GdUser(
            "u-ana", "Ana Reyes", "Practitioner", "b-makati",
            "Logs sessions, manages inventory, views clients. Full access at home branches.",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY"),
        ),
        GdUser(
            "u-ben", "Ben Cruz", "Coordinator", "b-makati",
            "Handles finance and remittance. Sole editor of PAST and REMITTED records.",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS"),
        ),
        GdUser(
            "u-cara", "Cara Lim", "MANAGER", "b-bgc",
            "A Coordinator superset: finance plus user management and delegate assignment.",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE"),
        ),
        GdUser(
            "u-dan", "Dan Uy", "Accountant", "b-makati",
            "Read-only across all branches. Sees sales and data, edits nothing.",
            listOf("VIEW_BRANCH_DATA"),
        ),
        GdUser(
            "u-eli", "Eli Santos", "ONBOARDING", "b-makati",
            "Freshly registered: the capability bundle is empty, so nothing derives — " +
                "locked out even with a branch assignment until MANAGE_USERS grants a role.",
            emptyList(), locked = true,
        ),
    )

    val branches = listOf(
        GdBranch("b-makati", "Makati", "CLINIC", GdDayStatus.OPEN, "Your home clinic — busy rail, OPEN day."),
        GdBranch("b-bgc", "BGC", "CLINIC", GdDayStatus.PAST, "Yesterday's rail — PAST, Coordinator edits only."),
        GdBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", GdDayStatus.REMITTED, "Off-site event — REMITTED and sealed."),
        GdBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", GdDayStatus.OPEN, "Free mission event — OPEN day."),
    )

    val members = listOf(
        GdMember("Ana Reyes", "Practitioner", 1, "Makati"),
        GdMember("Ben Cruz", "Coordinator", 2, "Makati"),
        GdMember("Cara Lim", "MANAGER", 1, "BGC"),
        GdMember("Dan Uy", "Accountant", 3, "Makati"),
        GdMember("Ramon Diaz", "Practitioner", 4, "Makati", relief = true),
    )

    val sessions = mutableStateListOf<GdSession>()
    val clients = mutableStateListOf<GdClient>()
    val remittances = mutableStateListOf<GdRemittance>()
    val notices = mutableStateListOf<GdNotice>()
    val audit = mutableStateListOf<String>()
    val reliefBoard = mutableStateListOf<String>()

    var currentUser by mutableStateOf<GdUser?>(null)
    var currentBranchId by mutableStateOf("b-makati")
    var clockedIn by mutableStateOf(false)
    var everClockedIn by mutableStateOf(false)
    var sessionMoved by mutableStateOf(false)
    var financeTouched by mutableStateOf(false)
    var anonViewed by mutableStateOf(false)
    var graduated by mutableStateOf(false)
    var sessionSeq by mutableStateOf(7)
    var remitSeq by mutableStateOf(4)
    var noticeSeq by mutableStateOf(4)
    val visited = mutableStateListOf<GdStep>()

    init {
        resetSampleData(keepIdentity = false)
    }

    fun resetSampleData(keepIdentity: Boolean = true) {
        val user = if (keepIdentity) currentUser else null
        val branchId = if (keepIdentity) currentBranchId else "b-makati"
        sessions.clear()
        sessions.addAll(seedSessions())
        clients.clear()
        clients.addAll(seedClients())
        remittances.clear()
        remittances.addAll(seedRemittances())
        notices.clear()
        notices.addAll(seedNotices())
        audit.clear()
        audit.addAll(seedAudit())
        reliefBoard.clear()
        reliefBoard.addAll(seedRelief())
        clockedIn = false
        everClockedIn = false
        sessionMoved = false
        financeTouched = false
        anonViewed = false
        graduated = false
        sessionSeq = 7
        remitSeq = 4
        noticeSeq = 4
        visited.clear()
        visited.add(GdStep.WELCOME)
        currentUser = user
        currentBranchId = branchId
        log("Sample data reset — fresh first-run state")
    }

    fun branch(id: String): GdBranch = branches.first { it.id == id }
    fun currentBranch(): GdBranch = branch(currentBranchId)

    fun branchSessions(branchId: String): List<GdSession> =
        sessions.filter { it.branchId == branchId }.sortedBy { it.time }

    fun visit(step: GdStep) {
        if (!visited.contains(step)) visited.add(step)
    }

    fun stepDone(step: GdStep): Boolean = when (step) {
        GdStep.WELCOME -> true
        GdStep.ROLES -> visited.contains(step)
        GdStep.BRANCH_DAY -> everClockedIn
        GdStep.SESSIONS -> sessionMoved
        GdStep.CLIENTS -> anonViewed
        GdStep.FINANCE -> financeTouched
        GdStep.TEAM -> visited.contains(step)
        GdStep.GRADUATE -> graduated
    }

    fun doneCount(): Int = GdStep.entries.count { stepDone(it) }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun clockIn() {
        clockedIn = true
        everClockedIn = true
        log("${currentUser?.name} clocked in at ${currentBranch().name}")
    }

    fun clockOut() {
        clockedIn = false
        log("${currentUser?.name} clocked out")
    }

    fun advance(id: String, next: GdSessionStatus) {
        val s = sessions.first { it.id == id }
        if (s.walkIn && (next == GdSessionStatus.NO_SHOW || next == GdSessionStatus.CANCELLED)) return
        s.status = next
        sessions.remove(s)
        sessions.add(s)
        sessionMoved = true
        log("Session $id moved to ${next.name} by ${currentUser?.name ?: "?"}")
        if (next != GdSessionStatus.PENDING) {
            notices.add(0, GdNotice("n-fake-$noticeSeq", "Session ${next.name.lowercase()}", "$id is now ${next.name}.", false))
            noticeSeq++
        }
    }

    fun voidSession(id: String, reason: String) {
        val s = sessions.first { it.id == id }
        s.voided = true
        s.voidReason = reason
        sessions.remove(s)
        sessions.add(s)
        log("Session $id VOIDED ($reason)")
    }

    fun unvoidSession(id: String) {
        val s = sessions.first { it.id == id }
        s.voided = false
        s.voidReason = ""
        sessions.remove(s)
        sessions.add(s)
        log("Session $id unvoided — record restored")
    }

    fun addSession(time: String, client: String, branchId: String, kind: String, walkIn: Boolean, price: Int) {
        val id = "s-%02d".format(sessionSeq++)
        sessions.add(GdSession(id, time, client, branchId, kind, walkIn, GdSessionStatus.PENDING, price, currentUser?.name ?: "?"))
        log("Session $id created (PENDING) for $client")
    }

    fun submitRemittance(id: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Snapshot"
        remittances.remove(r)
        remittances.add(r)
        financeTouched = true
        log("${r.flow} remittance $id submitted — snapshot sealed")
    }

    fun undoRemittance(id: String, reason: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Draft"
        remittances.remove(r)
        remittances.add(r)
        financeTouched = true
        log("Remittance $id undone within 48h ($reason) — days unlocked, snapshot deleted")
    }

    fun newDraft(flow: String) {
        val id = "r-%02d".format(remitSeq++)
        remittances.add(GdRemittance(id, flow, "Draft", 1500, currentBranch().name))
        log("$flow remittance $id drafted at ${currentBranch().name}")
    }

    fun markAllRead() {
        notices.forEach { it.read = true }
        val copy = notices.toList()
        notices.clear()
        notices.addAll(copy)
    }
}
