package com.companyb.companyapp.proto.sessionscribe

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class SDayStatus { OPEN, PAST, REMITTED }

enum class SSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class SRemitKind { SESSION, PRODUCT }

enum class SRemitStatus { DRAFT, SUBMITTED }

enum class SInviteKind { INVITE, REQUEST }

enum class SInviteStatus { PENDING, ACCEPTED, DECLINED, GRANTED, DENIED, REVOKED }

data class SBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: SDayStatus,
    val code: String,
)

data class SUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val slot: Int,
    val onboarding: Boolean = false,
    val clockedBranchId: String? = null,
    val reliefEdit: Boolean = false,
)

data class SSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val status: SSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val time: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class SClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val branchNote: String,
    val hasPending: Boolean,
    val lastType: String,
    val lastPrice: Int,
    val visits: Int,
    val anonymized: Boolean = false,
)

data class SInvite(
    val id: String,
    val kind: SInviteKind,
    val direction: String,
    val who: String,
    val branchId: String,
    val day: String,
    var status: SInviteStatus,
)

data class SRemitLine(
    val id: String,
    val label: String,
    val qty: Int,
    val price: Int,
)

data class SRemittance(
    val id: String,
    val kind: SRemitKind,
    var status: SRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
)

data class SNote(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String,
    val day: String,
    var read: Boolean = false,
)

data class SAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

class SessionScribeFakeRepo {
    val branches = mutableStateListOf<SBranch>()
    val users = mutableStateListOf<SUser>()
    val sessions = mutableStateListOf<SSession>()
    val clients = mutableStateListOf<SClient>()
    val invites = mutableStateListOf<SInvite>()
    val remitLines = mutableStateListOf<SRemitLine>()
    val remittances = mutableStateListOf<SRemittance>()
    val notes = mutableStateListOf<SNote>()
    val audits = mutableStateListOf<SAudit>()

    val currentUserId = mutableStateOf("u-nadia")
    val currentBranchId = mutableStateOf("b-quiapo")
    val seq = mutableStateOf(100)
    val auditSeq = mutableStateOf(1)

    init {
        reset()
    }

    fun currentUser(): SUser = users.first { it.id == currentUserId.value }

    fun currentBranch(): SBranch = branches.first { it.id == currentBranchId.value }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun audit(actor: String, action: String, record: String, reason: String = "") {
        audits.add(
            0,
            SAudit(
                id = "a-${auditSeq.value}",
                actor = actor,
                action = action,
                record = record,
                whenText = "Day log · 15:${(10 + auditSeq.value % 45).toString().padStart(2, '0')} Manila",
                reason = reason,
            ),
        )
        auditSeq.value += 1
    }

    fun reset() {
        branches.clear()
        users.clear()
        sessions.clear()
        clients.clear()
        invites.clear()
        remitLines.clear()
        remittances.clear()
        notes.clear()
        audits.clear()
        currentUserId.value = "u-nadia"
        currentBranchId.value = "b-quiapo"
        seq.value = 100
        auditSeq.value = 1
        branches.addAll(
            listOf(
                SBranch("b-quiapo", "Quiapo Clinic", "CLINIC", SDayStatus.OPEN, "CLN-001"),
                SBranch("b-marikina", "Marikina Tour Stop", "PROVINCIAL_TOUR", SDayStatus.PAST, "TOUR-007"),
                SBranch("b-lingap", "Lingap Mission Day", "MEDICAL_MISSION", SDayStatus.REMITTED, "MSN-014"),
            ),
        )
        users.addAll(
            listOf(
                SUser("u-nadia", "Nadia R.", "Practitioner", "b-quiapo", 1, clockedBranchId = "b-quiapo"),
                SUser("u-jolo", "Jolo M.", "Practitioner", "b-quiapo", 2, clockedBranchId = "b-quiapo"),
                SUser("u-cora", "Cora L.", "Coordinator", "b-quiapo", 3, clockedBranchId = "b-quiapo"),
                SUser("u-ves", "Ves A.", "MANAGER", "b-quiapo", 4),
                SUser("u-iko", "Iko T.", "Practitioner", "b-marikina", 1, clockedBranchId = "b-quiapo", reliefEdit = true),
                SUser("u-ono", "Ono B.", "Accountant", "b-marikina", 9),
                SUser("u-new", "Jojo K.", "ONBOARDING", "b-quiapo", 0, onboarding = true),
            ),
        )
        clients.addAll(
            listOf(
                SClient("c-01", "A. Reyes", "F", 34, "Prefers mornings", true, "Follow-up", 650, 6),
                SClient("c-02", "M. Santos", "M", 41, "First visit intake done", false, "First visit", 800, 1),
                SClient("c-03", "J. Cruz", "F", 29, "Knee program", false, "Follow-up", 650, 4),
                SClient("c-04", "R. Aquino", "M", 52, "Back program", false, "Follow-up", 700, 3),
                SClient("c-05", "T. Lim", "F", 45, "Walk-in regular", false, "First visit", 900, 2),
                SClient("c-06", "P. Gomez", "M", 38, "Anonymized on request", false, "Follow-up", 650, 5, anonymized = true),
            ),
        )
        sessions.addAll(
            listOf(
                SSession("s-101", "c-01", "A. Reyes", "b-quiapo", SSessionStatus.PENDING, "Follow-up", 650, false, "09:00"),
                SSession("s-102", "c-02", "M. Santos", "b-quiapo", SSessionStatus.PENDING, "First visit", 800, true, "09:20"),
                SSession("s-103", "c-03", "J. Cruz", "b-quiapo", SSessionStatus.COMPLETED, "Follow-up", 650, false, "08:00"),
                SSession("s-104", "c-04", "R. Aquino", "b-quiapo", SSessionStatus.NO_SHOW, "Follow-up", 700, false, "08:30"),
                SSession("s-105", "c-05", "T. Lim", "b-marikina", SSessionStatus.PENDING, "First visit", 900, false, "10:00"),
                SSession("s-106", "c-04", "R. Aquino", "b-marikina", SSessionStatus.CANCELLED, "Follow-up", 700, false, "10:30"),
                SSession("s-107", "c-03", "J. Cruz", "b-lingap", SSessionStatus.COMPLETED, "Mission pass", 0, true, "07:30"),
            ),
        )
        invites.addAll(
            listOf(
                SInvite("i-1", SInviteKind.INVITE, "IN", "Iko T.", "b-quiapo", "Today", SInviteStatus.PENDING),
                SInvite("i-2", SInviteKind.REQUEST, "OUT", "Nadia R.", "b-marikina", "Tomorrow", SInviteStatus.PENDING),
                SInvite("i-3", SInviteKind.REQUEST, "IN", "Sari M.", "b-quiapo", "Today", SInviteStatus.GRANTED),
            ),
        )
        remitLines.addAll(
            listOf(
                SRemitLine("l-1", "Herbal pack", 4, 250),
                SRemitLine("l-2", "Support wrap", 2, 180),
            ),
        )
        remittances.addAll(
            listOf(
                SRemittance("r-1", SRemitKind.SESSION, SRemitStatus.DRAFT, 1950, "Quiapo · today"),
                SRemittance("r-2", SRemitKind.PRODUCT, SRemitStatus.DRAFT, 1360, "Quiapo · today"),
                SRemittance(
                    "r-0",
                    SRemitKind.SESSION,
                    SRemitStatus.SUBMITTED,
                    4200,
                    "Lingap · yesterday",
                    "SNAP-8814 · frozen P&L",
                ),
            ),
        )
        notes.addAll(
            listOf(
                SNote("n-1", "Relief invite: Iko T.", "Quiapo needs cover today — tap Team to decide.", "b-quiapo", "Today"),
                SNote("n-2", "Reminder: A. Reyes 09:00", "Follow-up · smart defaults loaded from 6 visits.", "b-quiapo", "Today", read = true),
                SNote("n-3", "Remittance submitted", "Lingap SESSION snapshot SNAP-8814 frozen.", "b-lingap", "Yesterday", read = true),
            ),
        )
        audits.addAll(
            listOf(
                SAudit("a-seed-1", "Cora L.", "SUBMIT", "Remittance r-0", "Yesterday · 18:02 Manila", "End-of-day close"),
                SAudit("a-seed-2", "Nadia R.", "COMPLETE", "Session s-103", "Today · 08:40 Manila"),
            ),
        )
    }

    fun pendingFor(branchId: String): List<SSession> =
        sessions.filter { it.branchId == branchId && it.status == SSessionStatus.PENDING }

    fun clockIn(branchId: String) {
        val me = currentUser()
        users[users.indexOf(me)] = me.copy(clockedBranchId = branchId)
        logInfo("ScribeHome", "clock in ${me.name} at $branchId")
        audit(me.name, "CLOCK_IN", branchName(branchId))
    }

    fun clockOut() {
        val me = currentUser()
        users[users.indexOf(me)] = me.copy(clockedBranchId = null)
        logInfo("ScribeHome", "clock out ${me.name}")
        audit(me.name, "CLOCK_OUT", branchName(currentBranchId.value))
    }

    fun sendInvite(name: String, branchId: String) {
        seq.value += 1
        invites.add(
            0,
            SInvite("i-${seq.value}", SInviteKind.INVITE, "OUT", name, branchId, "Today", SInviteStatus.PENDING),
        )
        audit(currentUser().name, "INVITE", "$name · ${branchName(branchId)}")
    }

    fun broadcastRequest(branchId: String) {
        seq.value += 1
        invites.add(
            0,
            SInvite(
                "i-${seq.value}",
                SInviteKind.REQUEST,
                "OUT",
                currentUser().name,
                branchId,
                "Today",
                SInviteStatus.PENDING,
            ),
        )
        audit(currentUser().name, "REQUEST", "relief · ${branchName(branchId)}")
    }

    fun decideInvite(id: String, accept: Boolean) {
        val item = invites.firstOrNull { it.id == id } ?: return
        item.status = when {
            accept && item.kind == SInviteKind.INVITE -> SInviteStatus.ACCEPTED
            accept -> SInviteStatus.GRANTED
            item.kind == SInviteKind.INVITE -> SInviteStatus.DECLINED
            else -> SInviteStatus.DENIED
        }
        audit(currentUser().name, if (accept) "GRANT" else "DENY", "${item.who} · ${branchName(item.branchId)}")
    }

    fun revokeInvite(id: String) {
        val item = invites.firstOrNull { it.id == id } ?: return
        item.status = SInviteStatus.REVOKED
        audit(currentUser().name, "REVOKE", "${item.who} · ${branchName(item.branchId)}")
    }

    fun quickLog(clientId: String, branchId: String): String? {
        val client = clients.firstOrNull { it.id == clientId } ?: return "Unknown client."
        if (client.hasPending) return "${client.name} already holds the one live PENDING — finish it first."
        seq.value += 1
        val id = "s-${seq.value}"
        sessions.add(
            0,
            SSession(
                id = id,
                clientId = client.id,
                clientName = client.name,
                branchId = branchId,
                status = SSessionStatus.PENDING,
                type = client.lastType,
                price = client.lastPrice,
                walkIn = false,
                time = "Now",
            ),
        )
        setPending(client.id, true)
        audit(currentUser().name, "CREATE", "Session $id · ${client.name} defaults ${client.lastType}/${client.lastPrice}")
        return null
    }

    fun transition(id: String, next: SSessionStatus): String? {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return "Session gone."
        val s = sessions[idx]
        if (s.walkIn && (next == SSessionStatus.NO_SHOW || next == SSessionStatus.CANCELLED)) {
            return "Walk-in sessions cannot be NO_SHOW or CANCELLED."
        }
        sessions[idx] = s.copy(status = next)
        if (next != SSessionStatus.PENDING) {
            setPending(s.clientId, sessions.any { it.clientId == s.clientId && it.status == SSessionStatus.PENDING })
        }
        audit(currentUser().name, next.name, "Session $id")
        return null
    }

    fun setVoid(id: String, reason: String): String? {
        if (reason.isBlank()) return "A void reason is required."
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return "Session gone."
        val s = sessions[idx]
        sessions[idx] = s.copy(voided = true, voidReason = reason.trim())
        audit(currentUser().name, "VOID", "Session $id", reason.trim())
        return null
    }

    fun unvoid(id: String) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return
        val s = sessions[idx]
        sessions[idx] = s.copy(voided = false, voidReason = "")
        audit(currentUser().name, "UNVOID", "Session $id")
    }

    private fun setPending(clientId: String, value: Boolean) {
        val idx = clients.indexOfFirst { it.id == clientId }
        if (idx < 0) return
        val c = clients[idx]
        clients[idx] = c.copy(hasPending = value)
    }

    fun markRead(id: String, read: Boolean) {
        val item = notes.firstOrNull { it.id == id } ?: return
        item.read = read
    }

    fun submitRemit(id: String) {
        val idx = remittances.indexOfFirst { it.id == id }
        if (idx < 0) return
        val r = remittances[idx]
        remittances[idx] = r.copy(status = SRemitStatus.SUBMITTED, snapshot = "SNAP-${8900 + idx * 7} · frozen P&L")
        audit(currentUser().name, "SUBMIT", "Remittance $id")
    }

    fun undoRemit(id: String, reason: String): String? {
        if (reason.isBlank()) return "Undo needs a reason for the audit trail."
        val idx = remittances.indexOfFirst { it.id == id }
        if (idx < 0) return "Remittance gone."
        val r = remittances[idx]
        remittances[idx] = r.copy(status = SRemitStatus.DRAFT, snapshot = "")
        audit(currentUser().name, "UNDO", "Remittance $id", reason.trim())
        return null
    }
}
