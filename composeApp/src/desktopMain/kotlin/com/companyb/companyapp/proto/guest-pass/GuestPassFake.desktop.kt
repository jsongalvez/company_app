package com.companyb.companyapp.proto.guestpass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.companyb.companyapp.util.logInfo

enum class DayState { OPEN, PAST, REMITTED }

enum class SessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class FakeRole { PRACTITIONER, COORDINATOR, MANAGER, ACCOUNTANT, ONBOARDING }

enum class RemitState { DRAFT, SUBMITTED }

data class FakeUser(
    val id: String,
    val name: String,
    val role: FakeRole,
    val homeBranchId: String,
    val capabilities: List<String>,
)

data class FakeBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class PassSession(
    val id: String,
    val code: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val practitioner: String,
    val status: SessionStatus,
    val walkIn: Boolean,
    val price: Int,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class PassClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class ProductLine(
    val id: String,
    val name: String,
    val price: Int,
    val qty: Int,
)

data class PassRemit(
    val id: String,
    val kind: String,
    val branchId: String,
    val amount: Int,
    val state: RemitState,
    val snapshot: String = "",
)

data class PassNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val branchId: String? = null,
)

data class PassAudit(
    val id: Int,
    val at: String,
    val text: String,
)

data class TeamMember(
    val name: String,
    val role: String,
    val homeBranch: String,
    val slots: String,
)

class GuestPassRepo {
    val branches = listOf(
        FakeBranch("makati", "Makati Clinic", "CLINIC"),
        FakeBranch("cebu", "Cebu Provincial Tour", "PROVINCIAL_TOUR"),
        FakeBranch("mission", "Tondo Medical Mission", "MEDICAL_MISSION"),
    )

    val users = listOf(
        FakeUser(
            "u-ria", "Ria Santos", FakeRole.PRACTITIONER, "makati",
            listOf("LOG_SESSIONS", "VIEW_BRANCH_DATA", "REQUEST_RELIEF"),
        ),
        FakeUser(
            "u-ben", "Ben Cruz", FakeRole.COORDINATOR, "makati",
            listOf("VIEW_BRANCH_DATA", "EDIT_PAST", "SUBMIT_REMITTANCE", "GRANT_RELIEF"),
        ),
        FakeUser(
            "u-amy", "Amy Lim", FakeRole.MANAGER, "cebu",
            listOf("VIEW_BRANCH_DATA", "EDIT_PAST", "SUBMIT_REMITTANCE", "GRANT_RELIEF", "MANAGE_USERS"),
        ),
        FakeUser(
            "u-oli", "Oli Reyes", FakeRole.ACCOUNTANT, "makati",
            listOf("VIEW_BRANCH_DATA"),
        ),
        FakeUser(
            "u-new", "New Recruit", FakeRole.ONBOARDING, "makati",
            emptyList(),
        ),
    )

    val team = listOf(
        TeamMember("Ria Santos", "Practitioner", "Makati Clinic", "09:00–18:00"),
        TeamMember("Ben Cruz", "Coordinator", "Makati Clinic", "08:00–17:00"),
        TeamMember("Amy Lim", "Manager", "Cebu Provincial Tour", "flex"),
        TeamMember("Oli Reyes", "Accountant", "all branches (read-only)", "flex"),
        TeamMember("New Recruit", "ONBOARDING — no capabilities", "Makati Clinic", "locked"),
    )

    val roleBundles = mapOf(
        "Practitioner" to "LOG_SESSIONS · VIEW_BRANCH_DATA · REQUEST_RELIEF",
        "Coordinator" to "VIEW_BRANCH_DATA · EDIT_PAST · SUBMIT_REMITTANCE · GRANT_RELIEF",
        "Manager" to "coordinator bundle + MANAGE_USERS + delegate assignment",
        "Accountant" to "VIEW_BRANCH_DATA (read-only, all branches)",
        "ONBOARDING" to "empty bundle — locked until MANAGE_USERS grants a role",
    )

    val sessions = mutableStateListOf(
        PassSession(
            "s-101", "SES-0101", "c-1", "Mara Villanueva", "makati",
            "Ria Santos", SessionStatus.PENDING, false, 1200,
        ),
        PassSession(
            "s-102", "SES-0102", "c-2", "Jose Ramos", "makati",
            "Ria Santos", SessionStatus.COMPLETED, false, 1200,
        ),
        PassSession(
            "s-103", "SES-0103", "c-3", "Walk-in guest 7", "makati",
            "Ria Santos", SessionStatus.PENDING, true, 900,
        ),
        PassSession(
            "s-104", "SES-0104", "c-4", "Liza Aquino", "cebu",
            "Amy Lim", SessionStatus.NO_SHOW, false, 1100,
        ),
        PassSession(
            "s-105", "SES-0105", "c-5", "Mark Dela Cruz", "cebu",
            "Amy Lim", SessionStatus.CANCELLED, false, 1100,
        ),
        PassSession(
            "s-106", "SES-0106", "c-6", "Sara Mendoza", "mission",
            "Ria Santos", SessionStatus.COMPLETED, true, 0,
        ),
    )

    val clients = mutableStateListOf(
        PassClient("c-1", "Mara Villanueva", "F", 34),
        PassClient("c-2", "Jose Ramos", "M", 41),
        PassClient("c-3", "Walk-in guest 7", "M", 29),
        PassClient("c-4", "Liza Aquino", "F", 52),
        PassClient("c-5", "Mark Dela Cruz", "M", 26),
        PassClient("c-6", "Sara Mendoza", "F", 38),
        PassClient("c-7", "Anonymized record 12", "F", 45, anonymized = true),
    )

    val productLines = mutableStateListOf(
        ProductLine("p-1", "Liniment bottle", 350, 4),
        ProductLine("p-2", "Herbal pack", 500, 2),
        ProductLine("p-3", "Support band", 250, 6),
    )

    val remittances = mutableStateListOf(
        PassRemit("r-1", "SESSION", "makati", 5400, RemitState.DRAFT),
        PassRemit("r-2", "PRODUCT", "makati", 0, RemitState.DRAFT),
        PassRemit("r-3", "SESSION", "cebu", 2200, RemitState.SUBMITTED, "SNAP-CEBU-0412 · frozen Day 12 18:24"),
    )

    val notices = mutableStateListOf(
        PassNotice(
            "n-1", "Relief invite: Cebu day 14",
            "Amy Lim invites you to relief duty at Cebu Provincial Tour.", false, "cebu",
        ),
        PassNotice(
            "n-2", "Grant approved at Tondo",
            "Your relief request for the Medical Mission was granted.", false, "mission",
        ),
        PassNotice(
            "n-3", "Snapshot frozen: Cebu session",
            "Remittance r-3 submitted — snapshot SNAP-CEBU-0412.", true, "cebu",
        ),
        PassNotice(
            "n-4", "Branch day remitted: Makati day 10",
            "Day 10 is now REMITTED — coordinator-only edits.", true, "makati",
        ),
    )

    val auditLog = mutableStateListOf<PassAudit>()
    val dayStates = mutableStateMapOf(
        "makati" to DayState.OPEN,
        "cebu" to DayState.PAST,
        "mission" to DayState.REMITTED,
    )
    val grants = mutableStateListOf<String>()

    var currentUser by mutableStateOf<FakeUser?>(null)
        private set
    var clockedIn by mutableStateOf(false)
        private set
    var clockBranchId by mutableStateOf<String?>(null)
        private set
    var selectedBranchId by mutableStateOf("makati")
    var celebration by mutableStateOf<String?>(null)
    var fakeHour by mutableStateOf(18)
    var sessionSeq by mutableStateOf(107)
        private set
    private var auditSeq = 1

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun canEdit(branchId: String): Boolean {
        val user = currentUser ?: return false
        if (!clockedIn) return false
        if (branchId == user.homeBranchId && branchId == clockBranchId) return true
        return branchId in grants
    }

    fun hoursToExpiry(): Int = (28 - fakeHour) % 24

    fun audit(text: String) {
        auditLog.add(0, PassAudit(auditSeq++, "Day 12 ${fakeHour}:00 fake", text))
    }

    fun login(user: FakeUser) {
        currentUser = user
        clockedIn = false
        clockBranchId = null
        celebration = null
        selectedBranchId = user.homeBranchId
        logInfo("GuestPass", "login as ${user.name} (${user.role})")
        audit("Signed in as ${user.name} (${user.role})")
    }

    fun logout() {
        audit("Signed out ${currentUser?.name}")
        logInfo("GuestPass", "logout")
        currentUser = null
        clockedIn = false
        clockBranchId = null
        celebration = null
    }

    fun clockIn(branchId: String, relief: Boolean) {
        clockedIn = true
        clockBranchId = branchId
        selectedBranchId = branchId
        if (relief) {
            audit("Clocked in as relief at ${branchName(branchId)} — view-only until grant")
        } else {
            audit("Clocked in at home branch ${branchName(branchId)}")
        }
    }

    fun clockOut() {
        audit("Clocked out from ${clockBranchId?.let { branchName(it) }}")
        clockedIn = false
        clockBranchId = null
    }

    fun simulateGrant(branchId: String) {
        if (branchId !in grants) grants.add(branchId)
        celebration = "Edit grant at ${branchName(branchId)} — expires 04:00 Asia/Manila"
        logInfo("GuestPass", "relief grant at $branchId")
        audit("Relief grant received at ${branchName(branchId)} (broadcast approved)")
    }

    fun acceptInvite(noticeId: String) {
        val notice = notices.firstOrNull { it.id == noticeId } ?: return
        markRead(noticeId)
        notice.branchId?.let { simulateGrant(it) }
        audit("Accepted invite: ${notice.title}")
    }

    fun withdrawRequest(branchId: String) {
        audit("Withdrew relief request at ${branchName(branchId)}")
    }

    fun markRead(noticeId: String) {
        val idx = notices.indexOfFirst { it.id == noticeId }
        if (idx >= 0) notices[idx] = notices[idx].copy(read = true)
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun clientHasPending(clientId: String): Boolean =
        sessions.any { it.clientId == clientId && it.status == SessionStatus.PENDING && !it.voided }

    fun setStatus(id: String, status: SessionStatus) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return
        sessions[idx] = sessions[idx].copy(status = status)
        audit("Session ${sessions[idx].code} → $status")
    }

    fun voidSession(
        id: String,
        reason: String,
    ) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return
        sessions[idx] = sessions[idx].copy(voided = true, voidReason = reason)
        audit("Session ${sessions[idx].code} voided: $reason")
    }

    fun unvoidSession(id: String) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return
        sessions[idx] = sessions[idx].copy(voided = false, voidReason = "")
        audit("Session ${sessions[idx].code} unvoided — restored to totals")
    }

    fun createSession(
        clientId: String,
        walkIn: Boolean,
        price: Int,
    ): String? {
        if (clientHasPending(clientId)) return "blocked: client already holds a PENDING session"
        val client = clients.firstOrNull { it.id == clientId } ?: return "blocked: unknown client"
        val code = "SES-0$sessionSeq"
        sessions.add(
            0,
            PassSession(
                "s-$sessionSeq", code, clientId, client.name, selectedBranchId,
                currentUser?.name ?: "—", SessionStatus.PENDING, walkIn, price,
            ),
        )
        sessionSeq++
        audit("Session $code created for ${client.name} at ${branchName(selectedBranchId)}")
        return null
    }

    fun cycleDayState(branchId: String) {
        val next = when (dayStates[branchId]) {
            DayState.OPEN -> DayState.PAST
            DayState.PAST -> DayState.REMITTED
            else -> DayState.OPEN
        }
        dayStates[branchId] = next
        audit("Branch day at ${branchName(branchId)} switched to $next (fake)")
    }

    fun productTotal(): Int = productLines.sumOf { it.price * it.qty }

    fun bumpQty(
        id: String,
        delta: Int,
    ) {
        val idx = productLines.indexOfFirst { it.id == id }
        if (idx < 0) return
        val line = productLines[idx]
        productLines[idx] = line.copy(qty = (line.qty + delta).coerceAtLeast(0))
    }

    fun submitRemit(id: String) {
        val idx = remittances.indexOfFirst { it.id == id }
        if (idx < 0) return
        val amount = if (remittances[idx].kind == "PRODUCT") productTotal() else remittances[idx].amount
        remittances[idx] = remittances[idx].copy(
            amount = amount,
            state = RemitState.SUBMITTED,
            snapshot = "SNAP-${id.uppercase()}-0412 · frozen Day 12 $fakeHour:00",
        )
        audit("Remittance $id (${remittances[idx].kind}) submitted — snapshot frozen")
    }

    fun undoRemit(
        id: String,
        reason: String,
    ) {
        val idx = remittances.indexOfFirst { it.id == id }
        if (idx < 0) return
        remittances[idx] = remittances[idx].copy(state = RemitState.DRAFT, snapshot = "")
        audit("Remittance $id undone within 48h: $reason")
    }

    fun checkedInStaff(): List<String> = listOf("Ria Santos", "Ben Cruz", "Relief: Oli Reyes")
}
