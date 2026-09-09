package com.companyb.companyapp.proto.splitmaster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #765 — split-master prototype fake data. Local only: no ApiClient, no Ktor, no backend.

enum class SmSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class SmDayStatus { OPEN, PAST, REMITTED }

enum class SmDest(val label: String) {
    HOME("Home & Clock"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

data class SmUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class SmBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: SmDayStatus,
)

data class SmSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: SmSessionStatus,
    val price: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class SmClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class SmRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val submittedHoursAgo: Int? = null,
)

data class SmNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class SmReliefItem(
    val id: String,
    val lane: String,
    val title: String,
    val body: String,
    var state: String,
)

class SplitMasterRepo {
    val users = listOf(
        SmUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        SmUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        SmUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        SmUser("u-dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        SmUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        SmBranch("b-makati", "Makati", "CLINIC", SmDayStatus.OPEN),
        SmBranch("b-bgc", "BGC", "CLINIC", SmDayStatus.PAST),
        SmBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", SmDayStatus.REMITTED),
        SmBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", SmDayStatus.OPEN),
    )

    val sessions = mutableStateListOf(
        SmSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, SmSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        SmSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, SmSessionStatus.PENDING, 1500, "Ana Reyes"),
        SmSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, SmSessionStatus.PENDING, 1000, "Ana Reyes"),
        SmSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, SmSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
        SmSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, SmSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        SmSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, SmSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        SmClient("c-01", "Maria Clara", "0917-111-0001", 0),
        SmClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        SmClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        SmClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
        SmClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        SmRemittance("r-01", "SESSION", "Snapshot", 18400, "Makati", submittedHoursAgo = 5),
        SmRemittance("r-02", "PRODUCT", "Draft", 3200, "Makati"),
        SmRemittance("r-03", "SESSION", "Snapshot", 22100, "Cebu Tour", submittedHoursAgo = 72),
    )

    val notices = mutableStateListOf(
        SmNotice("n-01", "Relief request approved", "BGC granted you edit access for today.", false),
        SmNotice("n-02", "Remittance snapshot sealed", "SESSION snapshot r-01 is immutable; Undo open for 48h.", false),
        SmNotice("n-03", "Branch day turned PAST", "BGC passed the 04:00 Manila boundary.", true),
    )

    val relief = mutableStateListOf(
        SmReliefItem("d-01", "Duty", "Duty: BGC cover (Practitioner)", "Today 13:00–17:00. Edit granted; pay from the relief drawer.", "ACTIVE"),
        SmReliefItem("q-01", "Requests", "Request: you asked BGC for edit access", "Outsider-initiated broadcast. One live request per requester per branch per date.", "APPROVED"),
        SmReliefItem("q-02", "Requests", "Request: you asked Cebu Tour for edit access", "Pending a grant from any active branch member.", "PENDING"),
        SmReliefItem("i-01", "Invites", "Invite: Makati Saturday (Practitioner)", "Branch-initiated single future day. Accepting writes the day grant.", "PENDING"),
    )

    val audit = mutableStateListOf(
        "16:20 Ben submitted SESSION remittance r-01 (snapshot sealed)",
        "13:05 Session s-04 marked NO_SHOW by Ana Reyes",
        "09:40 Session s-01 COMPLETED by Ana Reyes",
        "08:55 Ana clocked in at Makati",
    )

    var currentUser by mutableStateOf<SmUser?>(null)
    var currentBranchId by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var dest by mutableStateOf(SmDest.HOME)
    var remitSeq by mutableStateOf(4)
    var sessionSeq by mutableStateOf(7)

    // Retained master-detail selection per destination; survives dest switches.
    var selectedSessionId by mutableStateOf("s-02")
    var selectedClientId by mutableStateOf("c-01")
    var selectedRemitId by mutableStateOf("r-01")
    var selectedNoticeId by mutableStateOf("n-01")
    var selectedReliefId by mutableStateOf("d-01")
    var selectedMemberId by mutableStateOf("u-ana")

    fun branch(id: String): SmBranch = branches.first { it.id == id }

    fun currentBranch(): SmBranch = branch(if (currentBranchId.isBlank()) currentUser?.homeBranchId ?: "b-makati" else currentBranchId)

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun transitionSession(id: String, next: SmSessionStatus) {
        val s = sessions.first { it.id == id }
        s.status = next
        log("Session $id ${next.name} by ${currentUser?.name ?: "?"}")
    }

    fun voidSession(id: String, reason: String) {
        val s = sessions.first { it.id == id }
        s.voided = true
        s.voidReason = reason
        log("Session $id voided ($reason)")
    }

    fun unvoidSession(id: String) {
        val s = sessions.first { it.id == id }
        s.voided = false
        log("Session $id unvoided — record restored")
    }

    fun addSession(clientName: String, walkIn: Boolean, time: String) {
        val id = "s-%02d".format(sessionSeq++)
        sessions.add(SmSession(id, time, clientName, currentBranchId.ifBlank { "b-makati" }, if (walkIn) "Walk-in" else "Initial", walkIn, SmSessionStatus.PENDING, 1200, currentUser?.name ?: "?"))
        selectedSessionId = id
        log("Session $id logged PENDING by ${currentUser?.name ?: "?"}")
    }

    fun submitRemittance(id: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Snapshot"
        remittances.remove(r)
        remittances.add(0, r.copy(submittedHoursAgo = 0))
        log("${r.flow} remittance $id submitted (snapshot sealed)")
    }

    fun undoRemittance(id: String, reason: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Draft"
        remittances.remove(r)
        remittances.add(0, r.copy(submittedHoursAgo = null))
        log("Remittance $id undone within 48h ($reason) — back to Draft")
    }

    fun markAllNoticesRead() {
        notices.forEach { it.read = true }
        log("Mailbox marked all read")
    }

    fun signOut() {
        log("${currentUser?.name} signed out")
        currentUser = null
        currentBranchId = ""
        clockedIn = false
        dest = SmDest.HOME
    }
}
