package com.companyb.companyapp.proto.client360

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// #773 — client-360 prototype fake data. Local only: no ApiClient, no Ktor, no backend.

enum class C360SessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class C360DayStatus { OPEN, PAST, REMITTED }

fun C360SessionStatus.seal(): Color = when (this) {
    C360SessionStatus.PENDING -> C360Colors.Brass
    C360SessionStatus.COMPLETED -> C360Colors.Teal
    C360SessionStatus.NO_SHOW -> C360Colors.StampRed
    C360SessionStatus.CANCELLED -> C360Colors.Slate
}

data class C360User(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class C360Branch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: C360DayStatus,
)

data class C360Client(
    val id: String,
    val name: String,
    val contact: String,
    val since: String,
    val tags: List<String>,
    var anonymized: Boolean = false,
)

data class C360Session(
    val id: String,
    val clientId: String,
    val time: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: C360SessionStatus,
    val price: Int,
    val practitioner: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class C360Remittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amount: Int,
    val branchName: String,
    val submittedHoursAgo: Int? = null,
)

data class C360Notice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class C360ReliefInvite(
    val id: String,
    val fromBranch: String,
    val date: String,
    var state: String = "OPEN",
)

data class C360ReliefRequest(
    val id: String,
    val branch: String,
    val date: String,
    var state: String = "OPEN",
)

class Client360Repo {
    val users = listOf(
        C360User("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        C360User("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        C360User("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        C360User("u-dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA"), locked = false),
        C360User("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        C360Branch("b-makati", "Makati", "CLINIC", C360DayStatus.OPEN),
        C360Branch("b-bgc", "BGC", "CLINIC", C360DayStatus.PAST),
        C360Branch("b-cebu", "Cebu", "PROVINCIAL_TOUR", C360DayStatus.OPEN),
        C360Branch("b-tondo", "Tondo", "MEDICAL_MISSION", C360DayStatus.REMITTED),
    )

    val clients = listOf(
        C360Client("c-santos", "Maria Santos", "+63 917 111 2233", "Mar 2024", listOf("VIP", "Sensitive skin")),
        C360Client("c-delacruz", "Jose dela Cruz", "+63 918 222 3344", "Jun 2024", listOf("Walk-in regular")),
        C360Client("c-reyes", "Liza Reyes", "+63 927 333 4455", "Jan 2025", listOf("Post-care plan")),
        C360Client("c-tan", "Mark Tan", "+63 935 444 5566", "Nov 2023", listOf("VIP", "Corporate")),
        C360Client("c-garcia", "Ana Garcia", "+63 956 555 6677", "Aug 2025", listOf("First-timer")),
    )

    val sessions = mutableStateListOf(
        C360Session("s-101", "c-santos", "Tue 09:00", "b-makati", "Signature facial", false, C360SessionStatus.COMPLETED, 2500, "Ana Reyes"),
        C360Session("s-102", "c-santos", "Tue 14:30", "b-bgc", "Laser consult", false, C360SessionStatus.COMPLETED, 1200, "Cara Lim"),
        C360Session("s-103", "c-santos", "Wed 10:00", "b-makati", "Chemical peel", false, C360SessionStatus.PENDING, 3800, "Ana Reyes"),
        C360Session("s-104", "c-delacruz", "Mon 11:00", "b-cebu", "Walk-in cleanup", true, C360SessionStatus.COMPLETED, 950, "Ben Cruz"),
        C360Session("s-105", "c-delacruz", "Wed 09:30", "b-makati", "Acne program", false, C360SessionStatus.NO_SHOW, 1800, "Ana Reyes"),
        C360Session("s-106", "c-reyes", "Tue 16:00", "b-makati", "Post-care review", false, C360SessionStatus.CANCELLED, 0, "Ana Reyes"),
        C360Session("s-107", "c-reyes", "Thu 09:00", "b-makati", "Hydra boost", false, C360SessionStatus.PENDING, 2900, "Ana Reyes"),
        C360Session("s-108", "c-tan", "Mon 15:00", "b-tondo", "Mission screening", true, C360SessionStatus.COMPLETED, 0, "Dan Uy"),
        C360Session("s-109", "c-tan", "Wed 13:00", "b-bgc", "Executive facial", false, C360SessionStatus.COMPLETED, 4200, "Cara Lim"),
        C360Session("s-110", "c-garcia", "Thu 11:30", "b-makati", "First consult", false, C360SessionStatus.PENDING, 800, "Ana Reyes"),
    )

    val remittances = mutableStateListOf(
        C360Remittance("r-201", "SESSION", "SNAPSHOT", 18400, "Makati", submittedHoursAgo = 30),
        C360Remittance("r-202", "PRODUCT", "DRAFT", 6350, "Makati"),
        C360Remittance("r-203", "SESSION", "SUBMITTED", 22100, "BGC", submittedHoursAgo = 60),
        C360Remittance("r-204", "PRODUCT", "DRAFT", 2980, "Cebu"),
    )

    val notices = mutableStateListOf(
        C360Notice("n-1", "Relief invite: Cebu Sat duty", "Cebu PROVINCIAL_TOUR requests cover for Saturday duty.", false),
        C360Notice("n-2", "Snapshot r-201 sealed", "Makati SESSION snapshot sealed. Undo window closes at 48h.", false),
        C360Notice("n-3", "Branch day closed: BGC", "BGC day moved to PAST. Edits are read-only pending remittance.", true),
    )

    val audit = mutableStateListOf(
        "09:41 Dan sealed remittance r-201 (SESSION snapshot, Makati)",
        "09:12 Ana completed session s-101 (Maria Santos, Makati)",
        "08:58 Ben clocked in at Makati",
        "08:31 System rolled BGC branch day to PAST at 04:00 Asia/Manila",
    )

    val reliefDuty = listOf("Sat Cebu cover — Ana (confirmed)", "Sun Makati floor — Ben (confirmed)")
    val reliefRequests = mutableStateListOf(
        C360ReliefRequest("q-1", "Cebu", "Sat"),
        C360ReliefRequest("q-2", "Tondo", "Sun"),
    )
    val reliefInvites = mutableStateListOf(
        C360ReliefInvite("v-1", "Cebu", "Sat"),
        C360ReliefInvite("v-2", "BGC", "Sun"),
    )

    var currentUser: C360User? by mutableStateOf(null)
    var currentBranchId: String by mutableStateOf("")
    var selectedClientId: String by mutableStateOf("c-santos")
    var clockedIn: Boolean by mutableStateOf(false)
    var recordTab: Int by mutableStateOf(0)
    var opsTab: Int by mutableStateOf(0)
    var sessionCounter: Int by mutableStateOf(111)
    var maskAll: Boolean by mutableStateOf(false)
    var statusMessage: String by mutableStateOf("")

    fun branch(id: String): C360Branch = branches.first { it.id == id }
    fun currentBranch(): C360Branch = branch(currentBranchId)
    fun selectedClient(): C360Client = clients.first { it.id == selectedClientId }

    fun log(entry: String) {
        audit.add(0, entry)
    }

    fun clientSessions(clientId: String): List<C360Session> =
        sessions.filter { it.clientId == clientId }.sortedByDescending { it.time }

    fun pendingFor(clientId: String): C360Session? =
        sessions.firstOrNull { it.clientId == clientId && it.status == C360SessionStatus.PENDING && !it.voided }

    fun displayName(client: C360Client): String =
        if (client.anonymized || maskAll) "Client •••${client.id.takeLast(4).uppercase()}" else client.name

    fun displayContact(client: C360Client): String =
        if (client.anonymized || maskAll) "+63 ••• ••• ${client.contact.takeLast(4)}" else client.contact

    fun spendFor(clientId: String): Int =
        sessions.filter { it.clientId == clientId && it.status == C360SessionStatus.COMPLETED && !it.voided }
            .sumOf { it.price }

    fun branchNamesFor(clientId: String): List<String> =
        sessions.filter { it.clientId == clientId }.map { branch(it.branchId).name }.distinct()

    fun bookSession(client: C360Client) {
        val pending = pendingFor(client.id)
        if (pending != null) {
            statusMessage = "Guard held: ${displayName(client)} already holds PENDING ${pending.id}. Complete it first."
            return
        }
        sessionCounter += 1
        val id = "s-$sessionCounter"
        sessions.add(
            C360Session(
                id, client.id, "Thu 15:00", currentBranchId, "Follow-up consult",
                false, C360SessionStatus.PENDING, 1500, currentUser?.name.orEmpty(),
            ),
        )
        log("${currentUser?.name} booked $id for ${client.name} (${currentBranch().name}, PENDING)")
        statusMessage = "Booked $id as PENDING for ${displayName(client)}."
    }

    fun transition(session: C360Session, next: C360SessionStatus) {
        val from = session.status
        session.status = next
        log("${currentUser?.name} moved ${session.id} ${from.name} → ${next.name}")
        statusMessage = "${session.id} is now ${next.name}."
    }

    fun voidSession(session: C360Session, reason: String) {
        session.voided = true
        session.voidReason = reason
        log("${currentUser?.name} voided ${session.id} (reason: $reason)")
        statusMessage = "${session.id} voided."
    }

    fun unvoidSession(session: C360Session) {
        session.voided = false
        log("${currentUser?.name} restored ${session.id} from void")
        statusMessage = "${session.id} restored."
    }
}
