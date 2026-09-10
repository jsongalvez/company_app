package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #819 — owner-desk prototype fake data. Local only: no ApiClient, no Ktor, no backend.

enum class OdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class OdDayStatus { OPEN, PAST, REMITTED }

data class OdUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class OdBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: OdDayStatus,
    val takings: Int,
)

data class OdSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: OdSessionStatus,
    val price: Int,
    val practitioner: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class OdClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
)

data class OdRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val submittedHoursAgo: Int? = null,
)

data class OdNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class OdRelief(
    val id: String,
    val kind: String,
    var state: String,
    val detail: String,
)

class OwnerDeskRepo {
    val users = listOf(
        OdUser("u-owner", "Amara Villanueva", "Owner", "b-flagship", listOf("VIEW_ALL_MONEY", "VIEW_ALL_BRANCHES", "MANAGE_USERS", "SUBMIT_REMITTANCE", "UNDO_REMITTANCE")),
        OdUser("u-ana", "Ana Reyes", "Practitioner", "b-flagship", listOf("LOG_SESSIONS", "VIEW_CLIENTS")),
        OdUser("u-ben", "Ben Cruz", "Coordinator", "b-flagship", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        OdUser("u-cara", "Cara Lim", "MANAGER", "b-riverside", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        OdUser("u-dan", "Dan Uy", "Accountant", "b-flagship", listOf("VIEW_BRANCH_DATA")),
        OdUser("u-eli", "Eli Santos", "ONBOARDING", "b-flagship", emptyList(), locked = true),
    )

    val branches = listOf(
        OdBranch("b-flagship", "Flagship", "CLINIC", OdDayStatus.OPEN, 18400),
        OdBranch("b-riverside", "Riverside", "CLINIC", OdDayStatus.PAST, 22100),
        OdBranch("b-highland", "Highland Tour", "PROVINCIAL_TOUR", OdDayStatus.REMITTED, 15750),
        OdBranch("b-harbor", "Harbor Mission", "MEDICAL_MISSION", OdDayStatus.OPEN, 6300),
    )

    val sessions = mutableStateListOf(
        OdSession("s-01", "09:00", "Maria Clara", "b-flagship", "Follow-up", false, OdSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        OdSession("s-02", "10:00", "Jose Rizal", "b-flagship", "Initial", false, OdSessionStatus.PENDING, 1500, "Ana Reyes"),
        OdSession("s-03", "11:30", "Walk-in guest", "b-flagship", "Walk-in", true, OdSessionStatus.PENDING, 1000, "Ana Reyes"),
        OdSession("s-04", "13:00", "Liza Soberano", "b-flagship", "Follow-up", false, OdSessionStatus.NO_SHOW, 1200, "Ana Reyes"),
        OdSession("s-05", "14:30", "Nora Aunor", "b-flagship", "Initial", false, OdSessionStatus.CANCELLED, 1500, "Ben Cruz"),
        OdSession("s-06", "15:00", "Felipe Agoncillo", "b-riverside", "Follow-up", false, OdSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        OdClient("c-01", "Maria Clara", "0917-111-0001", 0),
        OdClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        OdClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        OdClient("c-04", "File #A17 (sealed)", "withheld", 0, anonymized = true),
        OdClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        OdRemittance("r-01", "SESSION", "Snapshot", 18400, "Flagship", submittedHoursAgo = 5),
        OdRemittance("r-02", "PRODUCT", "Draft", 3200, "Flagship"),
        OdRemittance("r-03", "SESSION", "Snapshot", 22100, "Riverside", submittedHoursAgo = 72),
    )

    val notices = mutableStateListOf(
        OdNotice("n-01", "Relief request approved", "Riverside granted edit access for today.", false),
        OdNotice("n-02", "Remittance snapshot sealed", "SESSION snapshot r-01 is immutable; Undo open for 48h.", false),
        OdNotice("n-03", "Branch day turned PAST", "Riverside passed the 04:00 Manila boundary.", true),
    )

    val audit = mutableStateListOf(
        "08:55 Owner opened the desk — morning brief sealed",
        "09:40 Session s-01 COMPLETED by Ana Reyes",
        "13:05 Session s-04 marked NO_SHOW by Ana Reyes",
        "16:20 Ben submitted SESSION remittance r-01 (snapshot sealed)",
    )

    val relief = mutableStateListOf(
        OdRelief("f-01", "Invite", "Pending your accept", "Flagship invites cover for Saturday gala"),
        OdRelief("f-02", "Request", "APPROVED", "Edit access at Riverside for today"),
        OdRelief("f-03", "Duty", "View-only, expired", "Cover at Highland Tour — closed at 04:00 Manila"),
    )

    var currentUser by mutableStateOf<OdUser?>(null)
    var currentBranchId by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var sessionSeq by mutableStateOf(7)
    var remitSeq by mutableStateOf(4)
    var noticeSeq by mutableStateOf(4)
    var anonymizeClients by mutableStateOf(false)

    fun branch(id: String): OdBranch = branches.first { it.id == id }
    fun currentBranch(): OdBranch = branch(currentBranchId)

    fun branchSessions(branchId: String): List<OdSession> =
        sessions.filter { it.branchId == branchId }.sortedBy { it.time }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun advance(id: String, next: OdSessionStatus) {
        val s = sessions.first { it.id == id }
        if (s.walkIn && (next == OdSessionStatus.NO_SHOW || next == OdSessionStatus.CANCELLED)) return
        s.status = next
        sessions.remove(s)
        sessions.add(s)
        log("Session $id moved to ${next.name} by ${currentUser?.name ?: "?"}")
        if (next != OdSessionStatus.PENDING) {
            notices.add(0, OdNotice("n-fake-$noticeSeq", "Session ${next.name.lowercase()}", "$id is now ${next.name}.", false))
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
        sessions.add(OdSession(id, time, client, branchId, kind, walkIn, OdSessionStatus.PENDING, price, currentUser?.name ?: "?"))
        log("Session $id created (PENDING) for $client")
    }

    fun addRemittance(flow: String, amount: Int, branchName: String) {
        val id = "r-%02d".format(remitSeq++)
        remittances.add(OdRemittance(id, flow, "Draft", amount, branchName))
        log("$flow remittance $id drafted ($branchName)")
    }

    fun submitRemittance(id: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Snapshot"
        remittances.remove(r)
        remittances.add(r)
        log("${r.flow} remittance $id submitted — snapshot sealed")
    }

    fun undoRemittance(id: String, reason: String) {
        val r = remittances.first { it.id == id }
        r.stage = "Draft"
        remittances.remove(r)
        remittances.add(r)
        log("Remittance $id undone within 48h ($reason) — days unlocked, snapshot deleted")
    }

    fun markAllRead() {
        val copy = notices.map { it.copy(read = true) }
        notices.clear()
        notices.addAll(copy)
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun riskFlags(): List<String> {
        val flags = mutableListOf<String>()
        val noShows = sessions.count { it.status == OdSessionStatus.NO_SHOW }
        if (noShows > 0) flags.add("$noShows NO_SHOW session${if (noShows > 1) "s" else ""} need${if (noShows > 1) "" else "s"} review")
        val pastOpen = branches.filter { it.dayStatus == OdDayStatus.PAST }
        pastOpen.forEach { flags.add("${it.name} day is PAST and not yet remitted") }
        val pendingRelief = relief.count { it.state.startsWith("Pending") }
        if (pendingRelief > 0) flags.add("$pendingRelief relief invite awaiting answer")
        val oldSnapshots = remittances.filter { it.stage == "Snapshot" && (it.submittedHoursAgo ?: 0) >= 48 }
        oldSnapshots.forEach { flags.add("Snapshot ${it.id} past Undo window — permanent") }
        return flags
    }
}
