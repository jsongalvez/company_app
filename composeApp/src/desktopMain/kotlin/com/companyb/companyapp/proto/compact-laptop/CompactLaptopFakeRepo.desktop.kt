package com.companyb.companyapp.proto.compactlaptop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #778 — compact-laptop fake data only. No ApiClient, no Ktor, no backend imports.
// Domain terms follow CONTEXT.md exactly.

enum class ClRole(val bundle: String) {
    ONBOARDING("empty bundle — locked out until MANAGE_USERS grants a real role"),
    PRACTITIONER("sessions + inventory + clients at home and checked-in branches"),
    COORDINATOR("sole editor of PAST/REMITTED finance for assigned branches"),
    MANAGER("coordinator superset + user management + delegate assignment"),
    ACCOUNTANT("read-only sales and data for all branches"),
}

enum class ClBranchKind { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION }

enum class ClSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class ClDayStatus { OPEN, PAST, REMITTED }

enum class ClRemitKind { SESSION, PRODUCT }

enum class ClRemitState { DRAFT, SUBMITTED }

data class ClUser(val id: String, val name: String, val role: ClRole, val homeBranchId: String)

data class ClBranch(val id: String, val name: String, val kind: ClBranchKind)

data class ClSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayId: String,
    val time: String,
    val status: ClSessionStatus,
    val walkIn: Boolean,
    val price: Int,
    val practitioners: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class ClClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class ClDay(val id: String, val label: String, val status: ClDayStatus)

data class ClRemit(
    val id: String,
    val kind: ClRemitKind,
    val branchId: String,
    val dayId: String,
    val state: ClRemitState,
    val total: Int,
    val snapshot: String = "",
)

data class ClNotif(val id: String, val title: String, val body: String, val read: Boolean)

data class ClAudit(val id: String, val who: String, val action: String, val target: String, val reason: String)

data class ClInvite(val id: String, val branchId: String, val invitee: String, val day: String, val state: String)

data class ClReliefRequest(val id: String, val requester: String, val branchId: String, val day: String, val state: String)

class CompactLaptopFakeRepo {
    val users = listOf(
        ClUser("u-ob", "R. Nuevo", ClRole.ONBOARDING, "b-qc"),
        ClUser("u-pr", "J. Santos", ClRole.PRACTITIONER, "b-qc"),
        ClUser("u-co", "M. Reyes", ClRole.COORDINATOR, "b-qc"),
        ClUser("u-mg", "A. Villanueva", ClRole.MANAGER, "b-laguna"),
        ClUser("u-ac", "K. Tan", ClRole.ACCOUNTANT, "b-qc"),
    )
    val branches = listOf(
        ClBranch("b-qc", "QC Central", ClBranchKind.CLINIC),
        ClBranch("b-laguna", "Laguna Tour Stop 3", ClBranchKind.PROVINCIAL_TOUR),
        ClBranch("b-tondo", "Tondo Medical Mission", ClBranchKind.MEDICAL_MISSION),
    )
    val days = listOf(
        ClDay("d1", "Mon Sep 7", ClDayStatus.REMITTED),
        ClDay("d2", "Tue Sep 8", ClDayStatus.PAST),
        ClDay("d3", "Wed Sep 9 (today)", ClDayStatus.OPEN),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var selectedBranchId by mutableStateOf("b-qc")
    var selectedDayId by mutableStateOf("d3")
    var clockedIn by mutableStateOf(false)
    var clockBranchId by mutableStateOf("b-qc")
    var reliefEdit by mutableStateOf(false)
    var grantedPractitioner by mutableStateOf(false)
    var auditSeq by mutableStateOf(100)

    val sessions = mutableStateListOf(
        ClSession("s1", "c1", "Dela Cruz, M.", "b-qc", "d3", "09:00", ClSessionStatus.PENDING, false, 850, "J. Santos"),
        ClSession("s2", "c2", "Aquino, R.", "b-qc", "d3", "10:30", ClSessionStatus.PENDING, true, 850, "J. Santos"),
        ClSession("s3", "c3", "Bautista, L.", "b-qc", "d3", "13:00", ClSessionStatus.COMPLETED, false, 950, "J. Santos"),
        ClSession("s4", "c4", "Ocampo, D.", "b-qc", "d2", "11:00", ClSessionStatus.NO_SHOW, false, 850, "M. Reyes"),
        ClSession("s5", "c5", "Garcia, P.", "b-qc", "d2", "15:00", ClSessionStatus.CANCELLED, false, 850, "M. Reyes"),
        ClSession("s6", "c1", "Dela Cruz, M.", "b-laguna", "d3", "09:30", ClSessionStatus.PENDING, false, 700, "A. Villanueva"),
    )
    val clients = mutableStateListOf(
        ClClient("c1", "Dela Cruz, Maria", "F", 42),
        ClClient("c2", "Aquino, Ramon", "M", 35),
        ClClient("c3", "Bautista, Luz", "F", 58),
        ClClient("c4", "Ocampo, Danilo", "M", 47),
        ClClient("c5", "Garcia, Pilar", "F", 29),
    )
    val remits = mutableStateListOf(
        ClRemit("r1", ClRemitKind.SESSION, "b-qc", "d2", ClRemitState.SUBMITTED, 4250, "SNAP d2 SESSION b-qc P4,250 sealed"),
        ClRemit("r2", ClRemitKind.PRODUCT, "b-qc", "d2", ClRemitState.DRAFT, 1180),
        ClRemit("r3", ClRemitKind.SESSION, "b-qc", "d3", ClRemitState.DRAFT, 1800),
        ClRemit("r4", ClRemitKind.PRODUCT, "b-qc", "d3", ClRemitState.DRAFT, 640),
    )
    val notifs = mutableStateListOf(
        ClNotif("n1", "Relief invite", "QC Central invites you for Thu Sep 10 cover.", false),
        ClNotif("n2", "Remittance sealed", "Tue Sep 8 SESSION snapshot sealed by M. Reyes.", false),
        ClNotif("n3", "Roster change", "A. Villanueva granted your relief request for Wed.", true),
    )
    val audits = mutableStateListOf(
        ClAudit("a1", "M. Reyes", "SUBMIT", "Tue SESSION remittance", "end-of-day seal"),
        ClAudit("a2", "J. Santos", "COMPLETE", "Session s3", "walk-out billed"),
        ClAudit("a3", "System", "GRANT", "relief request q2", "branch member approval"),
    )
    val invites = mutableStateListOf(
        ClInvite("i1", "b-qc", "J. Santos", "Thu Sep 10", "PENDING"),
        ClInvite("i2", "b-laguna", "J. Santos", "Fri Sep 11", "ACCEPTED"),
    )
    val reliefRequests = mutableStateListOf(
        ClReliefRequest("q1", "J. Santos", "b-laguna", "Wed Sep 9", "PENDING"),
        ClReliefRequest("q2", "K. Tan", "b-qc", "Wed Sep 9", "GRANTED"),
    )

    val currentUser: ClUser? get() = users.firstOrNull { it.id == currentUserId }
    val effectiveRole: ClRole
        get() {
            val u = currentUser ?: return ClRole.ONBOARDING
            if (u.role == ClRole.ONBOARDING && grantedPractitioner) return ClRole.PRACTITIONER
            return u.role
        }
    val locked: Boolean get() = currentUserId == null || effectiveRole == ClRole.ONBOARDING
    val selectedBranch: ClBranch get() = branches.first { it.id == selectedBranchId }
    val selectedDay: ClDay get() = days.first { it.id == selectedDayId }
    val daySessions: List<ClSession> get() = sessions.filter { it.branchId == selectedBranchId && it.dayId == selectedDayId }
    fun pendingCount(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == ClSessionStatus.PENDING && !it.voided }

    private fun audit(action: String, target: String, reason: String) {
        auditSeq += 1
        audits.add(0, ClAudit("a$auditSeq", currentUser?.name ?: "Proto", action, target, reason))
    }

    fun login(id: String) {
        currentUserId = id
        audit("LOGIN", users.first { it.id == id }.name, "fake directory sign-in")
    }

    fun logout() {
        audit("LOGOUT", currentUser?.name ?: "?", "profile sign-out")
        currentUserId = null
        clockedIn = false
        reliefEdit = false
    }

    fun grantPractitioner() {
        grantedPractitioner = true
        audit("GRANT_ROLE", "R. Nuevo -> Practitioner", "MANAGE_USERS simulation")
    }

    fun clockIn(relief: Boolean) {
        clockedIn = true
        clockBranchId = selectedBranchId
        reliefEdit = !relief && clockBranchId == (currentUser?.homeBranchId ?: clockBranchId) || reliefEdit
        if (relief) reliefEdit = false
        audit(if (relief) "CLOCK_IN_RELIEF" else "CLOCK_IN", selectedBranch.name, if (relief) "view-only start" else "home duty start")
    }

    fun clockOut() {
        clockedIn = false
        reliefEdit = false
        audit("CLOCK_OUT", selectedBranch.name, "duty end")
    }

    fun setSessionStatus(id: String, status: ClSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == ClSessionStatus.NO_SHOW || status == ClSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status)
        audit("SESSION_${status.name}", id, "status move")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = true, voidReason = reason)
        audit("VOID", id, reason.ifBlank { "no reason given" })
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = false, voidReason = "")
        audit("UNVOID", id, "void lifted")
    }

    fun addWalkIn(name: String, time: String) {
        val n = sessions.size + 1
        val cid = "c$n"
        if (clients.none { it.id == cid }) clients.add(ClClient(cid, name, "F", 30))
        sessions.add(ClSession("s$n", cid, name, selectedBranchId, selectedDayId, time, ClSessionStatus.PENDING, true, 850, currentUser?.name ?: "Proto"))
        audit("WALK_IN", name, "booked at $time")
    }

    fun anonymize(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        clients[i] = clients[i].copy(anonymized = true)
        audit("ANONYMIZE", id, "PII nullified, gender+age kept")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        remits[i] = r.copy(state = ClRemitState.SUBMITTED, snapshot = "SNAP ${r.dayId} ${r.kind} ${r.branchId} P${r.total} sealed")
        audit("REMIT_SUBMIT", id, "snapshot sealed immutable")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        remits[i] = r.copy(state = ClRemitState.DRAFT, snapshot = "")
        audit("REMIT_UNDO", id, reason.ifBlank { "within 48h window" })
    }

    fun markRead(id: String) {
        val i = notifs.indexOfFirst { it.id == id }
        if (i < 0) return
        notifs[i] = notifs[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notifs.indices) notifs[i] = notifs[i].copy(read = true)
        audit("MAILBOX_READ_ALL", "notifications", "cleared unread")
    }

    fun inviteAnswer(id: String, accept: Boolean) {
        val i = invites.indexOfFirst { it.id == id }
        if (i < 0) return
        invites[i] = invites[i].copy(state = if (accept) "ACCEPTED" else "DECLINED")
        audit(if (accept) "INVITE_ACCEPT" else "INVITE_DECLINE", id, "invitee decision")
    }

    fun requestDecide(id: String, grant: Boolean) {
        val i = reliefRequests.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefRequests[i] = reliefRequests[i].copy(state = if (grant) "GRANTED" else "DENIED")
        if (grant) reliefEdit = true
        audit(if (grant) "REQUEST_GRANT" else "REQUEST_DENY", id, "branch member decision")
    }

    fun requestWithdraw(id: String) {
        val i = reliefRequests.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefRequests[i] = reliefRequests[i].copy(state = "WITHDRAWN")
        audit("REQUEST_WITHDRAW", id, "requester retraction")
    }
}
