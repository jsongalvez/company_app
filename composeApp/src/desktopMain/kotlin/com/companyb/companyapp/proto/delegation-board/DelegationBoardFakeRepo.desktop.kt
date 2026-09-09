package com.companyb.companyapp.proto.delegationboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class DDayStatus { OPEN, PAST, REMITTED }

enum class DSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class DRemitKind { SESSION, PRODUCT }

enum class DRemitStatus { DRAFT, SUBMITTED }

enum class DInviteKind { INVITE, REQUEST }

enum class DInviteStatus { PENDING, ACCEPTED, DECLINED, GRANTED, DENIED, REVOKED }

data class DBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: DDayStatus,
    val code: String,
)

data class DUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val slot: Int,
    val onboarding: Boolean = false,
    val clockedBranchId: String? = null,
    val reliefEdit: Boolean = false,
)

data class DSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: DSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val time: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class DClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val branchNote: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class DCoverageGap(
    val branchId: String,
    val need: Int,
    val have: Int,
    val roles: String,
)

data class DInvite(
    val id: String,
    val kind: DInviteKind,
    val direction: String,
    val who: String,
    val branchId: String,
    val day: String,
    var status: DInviteStatus,
)

data class DRemitLine(
    val id: String,
    val label: String,
    val qty: Int,
    val price: Int,
)

data class DRemittance(
    val id: String,
    val kind: DRemitKind,
    var status: DRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class DNote(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String,
    val day: String,
    var read: Boolean = false,
)

data class DAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

class DelegationBoardFakeRepo {
    val branches = mutableStateListOf<DBranch>()
    val users = mutableStateListOf<DUser>()
    val sessions = mutableStateListOf<DSession>()
    val clients = mutableStateListOf<DClient>()
    val gaps = mutableStateListOf<DCoverageGap>()
    val invites = mutableStateListOf<DInvite>()
    val remitLines = mutableStateListOf<DRemitLine>()
    val remittances = mutableStateListOf<DRemittance>()
    val notes = mutableStateListOf<DNote>()
    val audits = mutableStateListOf<DAudit>()

    val currentUserId = mutableStateOf("u-rhea")
    val currentBranchId = mutableStateOf("b-lingap")
    val sessionExpense = mutableStateOf("450")
    val clockSeq = mutableStateOf(0)

    init {
        reset()
    }

    fun currentUser(): DUser = users.first { it.id == currentUserId.value }

    fun currentBranch(): DBranch = branches.first { it.id == currentBranchId.value }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun audit(
        actor: String,
        action: String,
        record: String,
        reason: String = "",
    ) {
        audits.add(
            0,
            DAudit(
                id = "a-${audits.size + 1}-${clockSeq.value}",
                actor = actor,
                action = action,
                record = record,
                whenText = "Day 14 · 15:${(10 + audits.size % 45).toString().padStart(2, '0')} Manila",
                reason = reason,
            ),
        )
        clockSeq.value += 1
    }

    fun reset() {
        branches.clear()
        users.clear()
        sessions.clear()
        clients.clear()
        gaps.clear()
        invites.clear()
        remitLines.clear()
        remittances.clear()
        notes.clear()
        audits.clear()
        currentUserId.value = "u-rhea"
        currentBranchId.value = "b-lingap"
        sessionExpense.value = "450"
        branches.addAll(
            listOf(
                DBranch("b-lingap", "Lingap Medical Mission", "MEDICAL_MISSION", DDayStatus.OPEN, "MSN-014"),
                DBranch("b-sunrise", "Sunrise Clinic", "CLINIC", DDayStatus.OPEN, "CLN-003"),
                DBranch("b-harbor", "Harbor Provincial Tour", "PROVINCIAL_TOUR", DDayStatus.PAST, "TOUR-011"),
            ),
        )
        users.addAll(
            listOf(
                DUser("u-rhea", "Rhea D.", "MANAGER", "b-lingap", 1, clockedBranchId = "b-lingap"),
                DUser("u-marco", "Marco S.", "Practitioner", "b-lingap", 2, clockedBranchId = "b-lingap"),
                DUser("u-lena", "Lena P.", "Coordinator", "b-lingap", 3, clockedBranchId = "b-lingap"),
                DUser("u-iko", "Iko T.", "Practitioner", "b-sunrise", 1, clockedBranchId = "b-sunrise"),
                DUser(
                    "u-sari",
                    "Sari M.",
                    "Practitioner",
                    "b-sunrise",
                    2,
                    reliefEdit = true,
                    clockedBranchId = "b-lingap",
                ),
                DUser("u-ono", "Ono B.", "Accountant", "b-sunrise", 9),
                DUser("u-new", "Jojo K.", "ONBOARDING", "b-lingap", 0, onboarding = true),
            ),
        )
        sessions.addAll(
            listOf(
                DSession("s-101", "A. Reyes", "b-lingap", DSessionStatus.PENDING, "First visit", 800, false, "09:00"),
                DSession("s-102", "M. Santos", "b-lingap", DSessionStatus.PENDING, "Follow-up", 650, true, "09:30"),
                DSession("s-103", "J. Cruz", "b-lingap", DSessionStatus.COMPLETED, "Follow-up", 650, false, "08:00"),
                DSession("s-104", "R. Aquino", "b-lingap", DSessionStatus.NO_SHOW, "First visit", 800, false, "08:30"),
                DSession("s-105", "T. Lim", "b-sunrise", DSessionStatus.PENDING, "First visit", 900, false, "10:00"),
                DSession("s-106", "P. Gomez", "b-sunrise", DSessionStatus.CANCELLED, "Follow-up", 700, false, "10:30"),
                DSession("s-107", "D. Ramos", "b-harbor", DSessionStatus.COMPLETED, "Mission pass", 0, true, "07:30"),
            ),
        )
        clients.addAll(
            listOf(
                DClient("c-1", "A. Reyes", "F", 34, "Seen at Lingap + Sunrise", true),
                DClient("c-2", "M. Santos", "M", 41, "Walk-in today at Lingap", true),
                DClient("c-3", "J. Cruz", "F", 29, "Regular at Lingap", false),
                DClient("c-4", "R. Aquino", "M", 55, "Missed today — follow up", false),
                DClient("c-5", "T. Lim", "F", 47, "Sunrise regular", true),
            ),
        )
        gaps.addAll(
            listOf(
                DCoverageGap("b-lingap", need = 5, have = 3, roles = "2 Practitioners"),
                DCoverageGap("b-sunrise", need = 3, have = 2, roles = "1 Coordinator"),
                DCoverageGap("b-harbor", need = 2, have = 2, roles = "Covered"),
            ),
        )
        invites.addAll(
            listOf(
                DInvite(
                    "i-1",
                    DInviteKind.INVITE,
                    "IN",
                    "Sari M.",
                    "b-lingap",
                    "Day 14 (today)",
                    DInviteStatus.PENDING,
                ),
                DInvite(
                    "i-2",
                    DInviteKind.INVITE,
                    "IN",
                    "Iko T.",
                    "b-lingap",
                    "Day 15 (tomorrow)",
                    DInviteStatus.PENDING,
                ),
                DInvite(
                    "i-3",
                    DInviteKind.REQUEST,
                    "IN",
                    "Ono B.",
                    "b-lingap",
                    "Day 14 (today)",
                    DInviteStatus.PENDING,
                ),
                DInvite(
                    "i-4",
                    DInviteKind.INVITE,
                    "OUT",
                    "Marco S.",
                    "b-sunrise",
                    "Day 15 (tomorrow)",
                    DInviteStatus.ACCEPTED,
                ),
                DInvite("i-5", DInviteKind.REQUEST, "OUT", "Rhea D.", "b-harbor", "Day 16", DInviteStatus.PENDING),
            ),
        )
        remitLines.addAll(
            listOf(
                DRemitLine("p-1", "Relief balm", 4, 250),
                DRemitLine("p-2", "Support wrap", 2, 180),
            ),
        )
        remittances.addAll(
            listOf(
                DRemittance(
                    "r-day13",
                    DRemitKind.SESSION,
                    DRemitStatus.SUBMITTED,
                    4820,
                    "Day 13",
                    "SNAP-DAY13-9F2K",
                    "Day 13 · 18:02 Manila",
                ),
                DRemittance("r-day14-s", DRemitKind.SESSION, DRemitStatus.DRAFT, 0, "Day 14"),
                DRemittance("r-day14-p", DRemitKind.PRODUCT, DRemitStatus.DRAFT, 0, "Day 14"),
            ),
        )
        notes.addAll(
            listOf(
                DNote(
                    "n-1",
                    "Relief invite: Sari M. accepted",
                    "Sari M. accepted relief duty at Lingap Medical Mission for Day 14 (today). Day grant written.",
                    "b-lingap",
                    "Day 14 (today)",
                ),
                DNote(
                    "n-2",
                    "Relief request needs a grant",
                    "Ono B. asks for relief access at Lingap Medical Mission for Day 14 (today). Any branch member may grant or deny.",
                    "b-lingap",
                    "Day 14 (today)",
                ),
                DNote(
                    "n-3",
                    "Day 13 remittance submitted",
                    "SESSION remittance for Day 13 sealed snapshot SNAP-DAY13-9F2K. Undo stays open 48h.",
                    "b-lingap",
                    "Day 13",
                    true,
                ),
                DNote(
                    "n-4",
                    "Coverage gap at Sunrise",
                    "Sunrise Clinic still needs 1 Coordinator for Day 14. Invite help from the board.",
                    "b-sunrise",
                    "Day 14 (today)",
                    true,
                ),
            ),
        )
        audits.addAll(
            listOf(
                DAudit("a-3", "Rhea D.", "SUBMIT", "Remittance r-day13", "Day 13 · 18:02 Manila", "End-of-day close"),
                DAudit("a-2", "Lena P.", "INSERT", "Session s-102 (walk-in)", "Day 14 · 09:12 Manila"),
                DAudit("a-1", "Rhea D.", "CLOCK_IN", "Duty at b-lingap", "Day 14 · 07:55 Manila"),
            ),
        )
        logInfo("DelegationBoard", "fake repo seeded")
    }

    fun clockIn(branchId: String) {
        val me = currentUser()
        val idx = users.indexOfFirst { it.id == me.id }
        val relief = branchId != me.homeBranchId
        users[idx] = me.copy(clockedBranchId = branchId, reliefEdit = if (relief) false else me.reliefEdit)
        audit(me.name, "CLOCK_IN", "Duty at $branchId", if (relief) "Relief duty — view only until grant" else "")
        val gapIdx = gaps.indexOfFirst { it.branchId == branchId }
        if (gapIdx >= 0) {
            val gap = gaps[gapIdx]
            gaps[gapIdx] = gap.copy(have = gap.have + 1)
        }
    }

    fun clockOut() {
        val me = currentUser()
        val idx = users.indexOfFirst { it.id == me.id }
        val was = me.clockedBranchId
        users[idx] = me.copy(clockedBranchId = null, reliefEdit = false)
        audit(me.name, "CLOCK_OUT", "Duty at ${was ?: "?"}")
    }

    fun decideInvite(
        id: String,
        accept: Boolean,
    ) {
        val item = invites.first { it.id == id }
        item.status =
            when {
                item.kind == DInviteKind.INVITE && accept -> DInviteStatus.ACCEPTED
                item.kind == DInviteKind.INVITE -> DInviteStatus.DECLINED
                accept -> DInviteStatus.GRANTED
                else -> DInviteStatus.DENIED
            }
        audit(currentUser().name, if (accept) "GRANT" else "DENY", "Invite $id (${item.who})")
        if (accept && item.kind == DInviteKind.INVITE) {
            val uIdx = users.indexOfFirst { it.name == item.who }
            if (uIdx >= 0) {
                val u = users[uIdx]
                users[uIdx] = u.copy(clockedBranchId = item.branchId, reliefEdit = true)
            }
        }
    }

    fun revokeInvite(id: String) {
        val item = invites.first { it.id == id }
        item.status = DInviteStatus.REVOKED
        audit(currentUser().name, "REVOKE", "Invite $id (${item.who})", "Duty not yet clocked in")
    }

    fun sendInvite(
        who: String,
        branchId: String,
    ) {
        invites.add(
            0,
            DInvite(
                "i-${invites.size + 1}-n",
                DInviteKind.INVITE,
                "OUT",
                who,
                branchId,
                "Day 15 (tomorrow)",
                DInviteStatus.PENDING,
            ),
        )
        audit(currentUser().name, "INVITE", "$who to $branchId")
    }

    fun broadcastRequest(branchId: String) {
        invites.add(
            0,
            DInvite(
                "i-${invites.size + 1}-n",
                DInviteKind.REQUEST,
                "OUT",
                currentUser().name,
                branchId,
                "Day 14 (today)",
                DInviteStatus.PENDING,
            ),
        )
        audit(currentUser().name, "REQUEST", "Relief access at $branchId", "Broadcast to branch")
    }

    fun moveSession(
        id: String,
        next: DSessionStatus,
    ) {
        val idx = sessions.indexOfFirst { it.id == id }
        val s = sessions[idx]
        sessions[idx] = s.copy(status = next)
        audit(currentUser().name, "UPDATE", "Session $id -> $next")
    }

    fun voidSession(
        id: String,
        reason: String,
    ) {
        val idx = sessions.indexOfFirst { it.id == id }
        val s = sessions[idx]
        sessions[idx] = s.copy(voided = true, voidReason = reason)
        audit(currentUser().name, "VOID", "Session $id", reason)
    }

    fun unvoidSession(id: String) {
        val idx = sessions.indexOfFirst { it.id == id }
        val s = sessions[idx]
        sessions[idx] = s.copy(voided = false, voidReason = "")
        audit(currentUser().name, "UNVOID", "Session $id", "Voided in error")
    }

    fun addWalkIn(
        client: String,
        type: String,
        price: Int,
    ) {
        val id = "s-${200 + sessions.size}"
        sessions.add(
            DSession(id, client, currentBranchId.value, DSessionStatus.PENDING, type, price, true, "now"),
        )
        audit(currentUser().name, "INSERT", "Session $id (walk-in)")
    }

    fun toggleAnonymized(id: String) {
        val idx = clients.indexOfFirst { it.id == id }
        val c = clients[idx]
        clients[idx] = c.copy(anonymized = !c.anonymized)
        audit(currentUser().name, "UPDATE", "Client $id anonymized=${!c.anonymized}")
    }

    fun sessionDraftTotal(): Int {
        val ids = currentBranchId.value
        val gross = sessions.filter { it.branchId == ids && !it.voided }.sumOf { it.price }
        return gross - (sessionExpense.value.toIntOrNull() ?: 0)
    }

    fun productDraftTotal(): Int = remitLines.sumOf { it.qty * it.price }

    fun submitRemit(kind: DRemitKind) {
        val idx = remittances.indexOfFirst { it.kind == kind && it.status == DRemitStatus.DRAFT }
        if (idx < 0) return
        val r = remittances[idx]
        val amount = if (kind == DRemitKind.SESSION) sessionDraftTotal() else productDraftTotal()
        remittances[idx] =
            r.copy(
                status = DRemitStatus.SUBMITTED,
                amount = amount,
                snapshot = "SNAP-${r.dayLabel.uppercase().replace(' ', '-')}-D${1000 + clockSeq.value}",
                submittedAt = "Day 14 · now Manila",
            )
        audit(currentUser().name, "SUBMIT", "Remittance ${r.id}", "Snapshot sealed")
    }

    fun undoRemit(
        id: String,
        reason: String,
    ) {
        val idx = remittances.indexOfFirst { it.id == id }
        val r = remittances[idx]
        remittances[idx] = r.copy(status = DRemitStatus.DRAFT, snapshot = "", submittedAt = "")
        audit(currentUser().name, "UNDO", "Remittance $id", reason)
    }

    fun markAllRead() {
        notes.forEach { it.read = true }
    }

    fun flipDay(
        branchId: String,
        next: DDayStatus,
    ) {
        val idx = branches.indexOfFirst { it.id == branchId }
        val b = branches[idx]
        branches[idx] = b.copy(dayStatus = next)
        audit(currentUser().name, "UPDATE", "Branch day $branchId -> $next", "Demo control")
    }

    fun grantRole(
        userId: String,
        role: String,
    ) {
        val idx = users.indexOfFirst { it.id == userId }
        val u = users[idx]
        users[idx] = u.copy(role = role, onboarding = false)
        audit(currentUser().name, "GRANT_ROLE", "User $userId -> $role", "MANAGE_USERS")
    }
}
