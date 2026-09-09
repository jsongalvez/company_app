package com.companyb.companyapp.proto.reportpack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #787 — report-pack fake domain: local only, no network, no shared contracts.
// Vocabulary follows CONTEXT.md: Branch, Session PENDING->COMPLETED/NO_SHOW/CANCELLED,
// Client global with at-most-one-PENDING, Branch Day OPEN/PAST/REMITTED with the
// 04:00 Asia/Manila boundary, Remittance SESSION/PRODUCT + Snapshot/Undo 48h,
// Void, Commission Split, Relief Duty/Request/Invite, Notification, Audit Log.

enum class RpDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class RpSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class RpRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class RpBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class RpRemitKind {
    SESSION,
    PRODUCT,
}

enum class RpRemitState {
    DRAFT,
    SUBMITTED,
}

enum class RpPeriod {
    WEEK,
    MONTH,
}

data class RpBranch(
    val id: String,
    val name: String,
    val kind: RpBranchKind,
)

data class RpUser(
    val id: String,
    val name: String,
    val role: RpRole,
    val homeBranchId: String,
)

data class RpDay(
    val id: String,
    val dow: String,
    val dateLabel: String,
    val status: RpDayStatus,
    val inWeek: Boolean,
    val isToday: Boolean = false,
)

data class RpSession(
    val id: String,
    val dayId: String,
    val branchId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: RpSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioners: String = "",
)

data class RpClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val branchNote: String,
    val anonymized: Boolean = false,
)

data class RpRemittance(
    val id: String,
    val dayId: String,
    val branchId: String,
    val kind: RpRemitKind,
    val state: RpRemitState,
    val amount: Int,
    val lines: Int,
    val snapshot: String? = null,
    val undoable: Boolean = true,
)

data class RpNotification(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean = false,
)

data class RpAudit(
    val id: String,
    val actor: String,
    val action: String,
    val target: String,
    val reason: String = "",
    val stamp: String,
)

data class RpInvite(
    val id: String,
    val fromBranch: String,
    val shift: String,
    var state: String = "PENDING",
)

data class RpRequest(
    val id: String,
    val branch: String,
    val shift: String,
    val mine: Boolean,
    var state: String = "OPEN",
)

class ReportPackFakeRepo {
    val branches = mutableStateListOf(
        RpBranch("b-qc", "QC Central", RpBranchKind.CLINIC),
        RpBranch("b-lag", "Laguna Tour Stop 3", RpBranchKind.PROVINCIAL_TOUR),
        RpBranch("b-ton", "Tondo Medical Mission", RpBranchKind.MEDICAL_MISSION),
    )

    val users = mutableStateListOf(
        RpUser("u-onb", "R. Nuevo", RpRole.ONBOARDING, "b-qc"),
        RpUser("u-prax", "M. Santos", RpRole.PRACTITIONER, "b-qc"),
        RpUser("u-coord", "J. Reyes", RpRole.COORDINATOR, "b-qc"),
        RpUser("u-mgr", "A. Villanueva", RpRole.MANAGER, "b-lag"),
        RpUser("u-acct", "C. Lim", RpRole.ACCOUNTANT, "b-qc"),
    )

    val days = mutableStateListOf(
        RpDay("d-mon", "MON", "Sep 01", RpDayStatus.REMITTED, inWeek = false),
        RpDay("d-tue", "TUE", "Sep 02", RpDayStatus.REMITTED, inWeek = false),
        RpDay("d-wed", "WED", "Sep 03", RpDayStatus.PAST, inWeek = false),
        RpDay("d-thu", "THU", "Sep 04", RpDayStatus.PAST, inWeek = true),
        RpDay("d-fri", "FRI", "Sep 05", RpDayStatus.PAST, inWeek = true),
        RpDay("d-sat", "SAT", "Sep 06", RpDayStatus.OPEN, inWeek = true, isToday = true),
        RpDay("d-sun", "SUN", "Sep 07", RpDayStatus.OPEN, inWeek = true),
    )

    val sessions = mutableStateListOf(
        RpSession("s-01", "d-mon", "b-qc", "c-01", "L. Aquino", "09:00", "STR", RpSessionStatus.COMPLETED, 1200, false, practitioners = "M. Santos"),
        RpSession("s-02", "d-mon", "b-qc", "c-02", "D. Cruz", "10:30", "MASS", RpSessionStatus.COMPLETED, 850, false, practitioners = "M. Santos"),
        RpSession("s-03", "d-tue", "b-lag", "c-03", "P. Ramos", "13:00", "REHAB", RpSessionStatus.COMPLETED, 1500, false, practitioners = "A. Villanueva"),
        RpSession("s-04", "d-tue", "b-lag", "c-04", "S. Uy", "14:00", "STR", RpSessionStatus.NO_SHOW, 1200, false),
        RpSession("s-05", "d-wed", "b-qc", "c-05", "K. Tan", "09:30", "MASS", RpSessionStatus.CANCELLED, 850, false),
        RpSession("s-06", "d-wed", "b-ton", "c-06", "R. Dizon", "11:00", "SCREEN", RpSessionStatus.COMPLETED, 0, true, practitioners = "M. Santos"),
        RpSession("s-07", "d-thu", "b-qc", "c-01", "L. Aquino", "09:00", "STR", RpSessionStatus.COMPLETED, 1200, false, practitioners = "M. Santos"),
        RpSession("s-08", "d-thu", "b-qc", "c-07", "N. Garcia", "10:00", "REHAB", RpSessionStatus.COMPLETED, 1500, true, practitioners = "J. Reyes"),
        RpSession("s-09", "d-fri", "b-lag", "c-03", "P. Ramos", "13:00", "REHAB", RpSessionStatus.PENDING, 1500, false, practitioners = "A. Villanueva"),
        RpSession("s-10", "d-fri", "b-qc", "c-02", "D. Cruz", "15:30", "MASS", RpSessionStatus.PENDING, 850, false, practitioners = "M. Santos"),
        RpSession("s-11", "d-sat", "b-qc", "c-08", "E. Navarro", "09:00", "STR", RpSessionStatus.PENDING, 1200, false, practitioners = "M. Santos"),
        RpSession("s-12", "d-sat", "b-ton", "c-09", "F. Ocampo", "10:30", "SCREEN", RpSessionStatus.PENDING, 0, true),
        RpSession("s-13", "d-sun", "b-qc", "c-05", "K. Tan", "11:00", "MASS", RpSessionStatus.PENDING, 850, false),
        RpSession("s-14", "d-thu", "b-qc", "c-04", "S. Uy", "16:00", "STR", RpSessionStatus.COMPLETED, 1200, false, practitioners = "M. Santos", voided = true, voidReason = "Duplicate entry"),
    )

    val clients = mutableStateListOf(
        RpClient("c-01", "L. Aquino", "F", 34, "QC regular, STR plan"),
        RpClient("c-02", "D. Cruz", "M", 41, "QC regular, MASS plan"),
        RpClient("c-03", "P. Ramos", "M", 29, "Laguna tour cohort"),
        RpClient("c-04", "S. Uy", "F", 52, "Cross-branch, QC + Laguna"),
        RpClient("c-05", "K. Tan", "F", 26, "QC regular"),
        RpClient("c-06", "R. Dizon", "M", 60, "Tondo mission screening"),
        RpClient("c-07", "N. Garcia", "F", 38, "Walk-in Sep 04"),
        RpClient("c-08", "E. Navarro", "M", 45, "New referral"),
        RpClient("c-09", "F. Ocampo", "F", 31, "Tondo mission screening"),
    )

    val remittances = mutableStateListOf(
        RpRemittance("r-01", "d-mon", "b-qc", RpRemitKind.SESSION, RpRemitState.SUBMITTED, 2050, 2, snapshot = "RC-0901-A · sealed Sep 01 18:02", undoable = false),
        RpRemittance("r-02", "d-mon", "b-qc", RpRemitKind.PRODUCT, RpRemitState.SUBMITTED, 640, 3, snapshot = "RC-0901-B · sealed Sep 01 18:05", undoable = false),
        RpRemittance("r-03", "d-tue", "b-lag", RpRemitKind.SESSION, RpRemitState.SUBMITTED, 1500, 1, snapshot = "RC-0902-A · sealed Sep 02 17:40", undoable = true),
        RpRemittance("r-04", "d-thu", "b-qc", RpRemitKind.SESSION, RpRemitState.DRAFT, 3900, 3),
        RpRemittance("r-05", "d-thu", "b-qc", RpRemitKind.PRODUCT, RpRemitState.DRAFT, 920, 4),
    )

    val notifications = mutableStateListOf(
        RpNotification("n-1", "Week pack ready to share", "Sep 04 pack totals reconciled for QC Central.", false),
        RpNotification("n-2", "Relief invite: Laguna Tour", "A. Villanueva invited you to cover Sat 13:00.", false),
        RpNotification("n-3", "Remittance sealed", "RC-0902-A sealed for Laguna Tour Stop 3.", true),
        RpNotification("n-4", "Undo window closing", "RC-0902-A leaves the 48h undo window tomorrow.", true),
    )

    val audits = mutableStateListOf(
        RpAudit("a-1", "J. Reyes", "SUBMIT_REMITTANCE", "RC-0902-A · Laguna", "", "Sep 02 17:40"),
        RpAudit("a-2", "M. Santos", "VOID_SESSION", "s-14 · duplicate entry", "Duplicate entry", "Sep 04 16:20"),
        RpAudit("a-3", "A. Villanueva", "SHARE_PACK", "Aug month pack · 3 branches", "", "Sep 01 09:12"),
    )

    val invites = mutableStateListOf(
        RpInvite("i-1", "Laguna Tour Stop 3", "Sat 13:00–17:00"),
        RpInvite("i-2", "Tondo Medical Mission", "Sun 09:00–12:00"),
    )

    val requests = mutableStateListOf(
        RpRequest("q-1", "QC Central", "Sun 14:00–18:00", mine = true),
        RpRequest("q-2", "Laguna Tour Stop 3", "Sat 13:00–17:00", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d-sat")
    var period by mutableStateOf(RpPeriod.WEEK)
    var clockedIn by mutableStateOf(false)
    var dutyBranchId by mutableStateOf<String?>(null)
    var anonymizedView by mutableStateOf(false)
    var shareLog by mutableStateOf("")

    private var seq = 100

    val currentUser: RpUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: RpBranch get() = branches.first { it.id == currentBranchId }
    val selectedDay: RpDay get() = days.first { it.id == selectedDayId }
    val locked: Boolean get() = currentUser?.role == RpRole.ONBOARDING

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun dayIdsInScope(): Set<String> = when (period) {
        RpPeriod.WEEK -> days.filter { it.inWeek }.map { it.id }.toSet()
        RpPeriod.MONTH -> days.map { it.id }.toSet()
    }

    fun sessionsInScope(): List<RpSession> {
        val scope = dayIdsInScope()
        return sessions.filter { it.dayId in scope }
    }

    fun pendingFor(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == RpSessionStatus.PENDING && !it.voided }

    fun displayName(c: RpClient): String {
        if (c.anonymized) return "ANON-${c.id.takeLast(2).uppercase()}"
        if (anonymizedView) return c.name.first() + ". " + "••••••"
        return c.name
    }

    private fun audit(action: String, target: String, reason: String = "") {
        audits.add(
            0,
            RpAudit("a-${seq++}", currentUser?.name ?: "signed out", action, target, reason, "Sep 06 09:41"),
        )
    }

    fun login(id: String) {
        currentUserId = id
        clockedIn = false
        dutyBranchId = null
        audit("LOGIN", users.first { it.id == id }.name)
    }

    fun logout() {
        audit("LOGOUT", currentUser?.name ?: "?")
        currentUserId = null
        clockedIn = false
        dutyBranchId = null
    }

    fun grantPractitioner() {
        val u = users.firstOrNull { it.id == currentUserId } ?: return
        users[users.indexOf(u)] = u.copy(role = RpRole.PRACTITIONER)
        audit("GRANT_ROLE", "${u.name} -> PRACTITIONER", "MANAGE_USERS grant")
    }

    fun clockIn() {
        clockedIn = true
        dutyBranchId = currentBranchId
        audit("CLOCK_IN", currentBranch.name)
    }

    fun clockOut() {
        clockedIn = false
        audit("CLOCK_OUT", dutyBranchId?.let(::branchName) ?: currentBranch.name)
        dutyBranchId = null
    }

    fun setSessionStatus(id: String, status: RpSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(status = status)
        audit("SESSION_${status.name}", "$id · ${s.clientName}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit("VOID_SESSION", "$id · ${s.clientName}", reason)
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = null)
        audit("UNVOID_SESSION", "$id · ${s.clientName}")
    }

    fun addWalkIn(clientName: String, branchId: String, type: String, price: Int) {
        val day = selectedDay
        val cid = "c-${seq++}"
        clients.add(RpClient(cid, clientName.ifBlank { "Walk-in guest" }, "F", 30, "Walk-in ${day.dateLabel}"))
        sessions.add(
            RpSession("s-${seq++}", day.id, branchId, cid, clientName.ifBlank { "Walk-in guest" }, "17:30", type, RpSessionStatus.PENDING, price, walkIn = true),
        )
        audit("WALK_IN", "$clientName · ${branchName(branchId)}")
    }

    fun anonymizeClient(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clients[i]
        clients[i] = c.copy(anonymized = !c.anonymized)
        audit(if (c.anonymized) "REVEAL_CLIENT" else "ANONYMIZE_CLIENT", c.name)
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remittances[i]
        remittances[i] = r.copy(state = RpRemitState.SUBMITTED, snapshot = "RC-0906-${r.kind.name} · sealed Sep 06 09:41", undoable = true)
        audit("SUBMIT_REMITTANCE", "$id · ${r.kind.name} ${r.amount.php()}")
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remittances[i]
        remittances[i] = r.copy(state = RpRemitState.DRAFT, snapshot = null, undoable = true)
        audit("UNDO_REMITTANCE", "$id · ${r.kind.name}", reason)
    }

    fun addDraft(kind: RpRemitKind, amount: Int, lines: Int) {
        remittances.add(RpRemittance("r-${seq++}", selectedDayId, currentBranchId, kind, RpRemitState.DRAFT, amount, lines))
        audit("DRAFT_REMITTANCE", "${kind.name} ${amount.php()} · ${currentBranch.name}")
    }

    fun markRead(id: String) {
        val n = notifications.firstOrNull { it.id == id } ?: return
        n.read = true
    }

    fun markAllRead() {
        notifications.forEach { it.read = true }
    }

    fun inviteAction(id: String, accept: Boolean) {
        val v = invites.firstOrNull { it.id == id } ?: return
        v.state = if (accept) "ACCEPTED" else "DECLINED"
        if (accept) {
            val b = branches.firstOrNull { it.name == v.fromBranch }
            if (b != null) dutyBranchId = b.id
        }
        audit(if (accept) "ACCEPT_INVITE" else "DECLINE_INVITE", v.fromBranch)
    }

    fun coverRequest(id: String) {
        val q = requests.firstOrNull { it.id == id } ?: return
        q.state = "COVERED"
        audit("COVER_REQUEST", "${q.branch} · ${q.shift}")
    }

    fun withdrawRequest(id: String) {
        val q = requests.firstOrNull { it.id == id } ?: return
        q.state = "WITHDRAWN"
        audit("WITHDRAW_REQUEST", "${q.branch} · ${q.shift}")
    }

    fun reopenRequest(id: String) {
        val q = requests.firstOrNull { it.id == id } ?: return
        q.state = "OPEN"
        audit("REOPEN_REQUEST", "${q.branch} · ${q.shift}")
    }

    fun sharePack(note: String) {
        shareLog = note
        audit("SHARE_PACK", "${period.name.lowercase().replaceFirstChar { it.uppercase() }} pack · ${currentBranch.name}")
    }
}
