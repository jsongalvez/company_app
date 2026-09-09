package com.companyb.companyapp.proto.reliefnetwork

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

// #772 — fake-data only stall ledger for the relief-network prototype.
// No ApiClient, no Ktor, no backend import. Everything lives in memory.

data class RnUser(
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val caps: List<String>,
    var active: Boolean = true,
) {
    val locked: Boolean get() = role == "ONBOARDING"
}

data class RnBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayState: String = "OPEN",
)

data class RnSession(
    val id: String,
    val branchId: String,
    val client: String,
    val type: String,
    var status: String = "PENDING",
    var price: String = "850",
    val walkIn: Boolean = false,
    var voided: Boolean = false,
    var voidReason: String = "",
    val practitioner: String = "ana",
)

data class RnClient(
    val name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
    var pending: Int = 0,
) {
    fun display(): String = if (anonymized) "Anon ${gender}${age}" else name
}

data class RnReliefRequest(
    val id: String,
    val requester: String,
    val branchId: String,
    val date: String,
    var state: String = "LIVE",
)

data class RnReliefInvite(
    val id: String,
    val invitee: String,
    val branchId: String,
    val date: String,
    var state: String = "PENDING",
)

data class RnGrant(
    val id: String,
    val holder: String,
    val branchId: String,
    val date: String,
    val source: String,
)

data class RnRemittance(
    val id: String,
    val kind: String,
    var state: String = "DRAFT",
    var total: String = "0",
    var snapshot: String = "",
    var undoable: Boolean = true,
)

data class RnNotice(
    val id: String,
    val text: String,
    var read: Boolean = false,
)

class RnRepo {
    val users =
        mutableStateListOf(
            RnUser("ana", "Ana Santos", "Practitioner", "makati", listOf("LOG_SESSION", "VIEW_BRANCH_DATA", "REQUEST_RELIEF")),
            RnUser("ben", "Ben Cruz", "Coordinator", "bgc", listOf("VIEW_BRANCH_DATA", "EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "GRANT_RELIEF")),
            RnUser("cara", "Cara Lim", "MANAGER", "makati", listOf("VIEW_BRANCH_DATA", "EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "GRANT_RELIEF", "MANAGE_USERS")),
            RnUser("dan", "Dan Reyes", "Accountant", "makati", listOf("VIEW_BRANCH_DATA")),
            RnUser("eli", "Eli Navarro", "ONBOARDING", "bgc", emptyList()),
        )
    val branches =
        mutableStateListOf(
            RnBranch("makati", "MAKATI CLINIC", "CLINIC", "OPEN"),
            RnBranch("bgc", "BGC CLINIC", "CLINIC", "PAST"),
            RnBranch("cebu", "CEBU-TOUR", "PROVINCIAL_TOUR", "OPEN"),
            RnBranch("tondo", "TONDO-MISSION", "MEDICAL_MISSION", "REMITTED"),
        )
    val sessions =
        mutableStateListOf(
            RnSession("s1", "makati", "Rosa Diaz", "Follow-up", "PENDING", "850", practitioner = "ana"),
            RnSession("s2", "makati", "Jose Ramos", "Initial", "COMPLETED", "1200", practitioner = "ana"),
            RnSession("s3", "bgc", "Liza Tan", "Follow-up", "NO_SHOW", "850", practitioner = "cara"),
            RnSession("s4", "cebu", "Walk-in guest", "Walk-in", "PENDING", "600", walkIn = true, practitioner = "ana"),
            RnSession("s5", "tondo", "Mario Uy", "Mission", "CANCELLED", "0", practitioner = "ben"),
        )
    val clients =
        mutableStateListOf(
            RnClient("Rosa Diaz", "F", 34, pending = 1),
            RnClient("Jose Ramos", "M", 51),
            RnClient("Liza Tan", "F", 28),
            RnClient("Mario Uy", "M", 44, anonymized = true),
        )
    val requests =
        mutableStateListOf(
            RnReliefRequest("q1", "ana", "bgc", "Fri Sep 11", "LIVE"),
            RnReliefRequest("q2", "dan", "cebu", "Sat Sep 12", "LIVE"),
        )
    val invites =
        mutableStateListOf(
            RnReliefInvite("v1", "ana", "cebu", "Sat Sep 12", "PENDING"),
            RnReliefInvite("v2", "ben", "tondo", "Sun Sep 13", "ACCEPTED"),
        )
    val grants = mutableStateListOf(RnGrant("g1", "ben", "tondo", "Sun Sep 13", "INVITE"))
    val remittances =
        mutableStateListOf(
            RnRemittance("r1", "SESSION", "DRAFT", "12,400"),
            RnRemittance("r2", "PRODUCT", "SUBMITTED", "3,150", "SNAP-0910-PROD sealed 09:12", true),
            RnRemittance("r3", "SESSION", "SUBMITTED", "48,900", "SNAP-0905-SESS sealed 3 days ago", false),
        )
    val notices =
        mutableStateListOf(
            RnNotice("n1", "Relief invite: CEBU-TOUR Sat Sep 12 names ana — tap to open the branch day.", false),
            RnNotice("n2", "Relief request: ana asks BGC CLINIC Fri Sep 11 — grant from the board.", false),
            RnNotice("n3", "Snapshot SNAP-0905-SESS is past 48h and permanent.", true),
        )
    val audit = mutableStateListOf("seed: stall ledger opened with demo day")
    val clocked = mutableStateMapOf<String, Boolean>()
    var seq = 100

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun userName(login: String): String = users.firstOrNull { it.login == login }?.name ?: login

    private fun log(text: String) {
        audit.add(0, text)
    }

    private fun wire(text: String) {
        seq += 1
        notices.add(0, RnNotice("n$seq", text, false))
    }

    fun ticker(): String {
        val live = requests.filter { it.state == "LIVE" }
        if (live.isEmpty()) return "No live broadcasts — the wire is quiet. Shout a request to fill a stall."
        return live.joinToString("   +++   ") { "${it.requester} shouts ${branchName(it.branchId)} ${it.date}" }
    }

    fun broadcastRequest(
        requester: String,
        branchId: String,
        date: String,
    ) {
        val clash = requests.any { it.requester == requester && it.branchId == branchId && it.date == date && it.state == "LIVE" }
        if (clash) return
        seq += 1
        requests.add(0, RnReliefRequest("q$seq", requester, branchId, date, "LIVE"))
        log("$requester broadcast relief request ${branchName(branchId)} $date")
        wire("Relief request: $requester asks ${branchName(branchId)} $date — grant from the board.")
    }

    fun withdrawRequest(id: String) {
        requests.firstOrNull { it.id == id }?.let {
            it.state = "WITHDRAWN"
            log("${it.requester} withdrew request ${branchName(it.branchId)} ${it.date}")
        }
    }

    fun grantRequest(
        id: String,
        granter: String,
    ) {
        val req = requests.firstOrNull { it.id == id } ?: return
        req.state = "GRANTED"
        seq += 1
        grants.add(0, RnGrant("g$seq", req.requester, req.branchId, req.date, "REQUEST"))
        log("$granter granted ${req.requester} relief at ${branchName(req.branchId)} ${req.date}")
        wire("Relief grant: ${req.requester} holds ${branchName(req.branchId)} ${req.date}.")
    }

    fun denyRequest(
        id: String,
        denier: String,
    ) {
        requests.firstOrNull { it.id == id }?.let {
            it.state = "DENIED"
            log("$denier denied ${it.requester} at ${branchName(it.branchId)} ${it.date}")
        }
    }

    fun sendInvite(
        invitee: String,
        branchId: String,
        date: String,
        inviter: String,
    ) {
        seq += 1
        invites.add(0, RnReliefInvite("v$seq", invitee, branchId, date, "PENDING"))
        log("$inviter invited $invitee to ${branchName(branchId)} $date")
        wire("Relief invite: ${branchName(branchId)} $date names $invitee — tap to open the branch day.")
    }

    fun acceptInvite(id: String) {
        val inv = invites.firstOrNull { it.id == id } ?: return
        inv.state = "ACCEPTED"
        seq += 1
        grants.add(0, RnGrant("g$seq", inv.invitee, inv.branchId, inv.date, "INVITE"))
        log("${inv.invitee} accepted invite ${branchName(inv.branchId)} ${inv.date}")
    }

    fun declineInvite(id: String) {
        invites.firstOrNull { it.id == id }?.let {
            it.state = "DECLINED"
            log("${it.invitee} declined invite ${branchName(it.branchId)} ${it.date}")
        }
    }

    fun revokeInvite(id: String) {
        val inv = invites.firstOrNull { it.id == id } ?: return
        inv.state = "REVOKED"
        grants.removeIf { it.holder == inv.invitee && it.branchId == inv.branchId && it.date == inv.date }
        log("branch revoked ${inv.invitee} duty ${branchName(inv.branchId)} ${inv.date}")
        wire("Relief revoked: ${inv.invitee} freed from ${branchName(inv.branchId)} ${inv.date}.")
    }

    fun setStatus(
        id: String,
        status: String,
        actor: String,
    ) {
        sessions.firstOrNull { it.id == id }?.let {
            it.status = status
            log("$actor set session ${it.id} (${it.client}) to $status")
        }
    }

    fun voidSession(
        id: String,
        reason: String,
        actor: String,
    ) {
        sessions.firstOrNull { it.id == id }?.let {
            it.voided = true
            it.voidReason = reason
            log("$actor voided session ${it.id} reason=$reason")
        }
    }

    fun unvoidSession(
        id: String,
        actor: String,
    ) {
        sessions.firstOrNull { it.id == id }?.let {
            it.voided = false
            log("$actor unvoided session ${it.id}")
        }
    }

    fun addSession(
        branchId: String,
        client: String,
        walkIn: Boolean,
        actor: String,
    ) {
        seq += 1
        sessions.add(0, RnSession("s$seq", branchId, client, if (walkIn) "Walk-in" else "Follow-up", "PENDING", if (walkIn) "600" else "850", walkIn, practitioner = actor))
        clients.firstOrNull { it.name.equals(client, true) }?.let { it.pending += 1 }
        log("$actor logged session for $client at ${branchName(branchId)}")
    }

    fun anonymize(name: String) {
        clients.firstOrNull { it.name == name }?.let {
            it.anonymized = true
            log("client $name anonymized (gender+age kept)")
        }
    }

    fun submitRemittance(
        id: String,
        actor: String,
    ) {
        remittances.firstOrNull { it.id == id }?.let {
            it.state = "SUBMITTED"
            it.snapshot = "SNAP-${seq}-${it.kind} sealed just now"
            log("$actor submitted ${it.kind} remittance ${it.total}; snapshot frozen")
            wire("Snapshot sealed: ${it.kind} remittance ${it.total} — undo window 48h.")
        }
    }

    fun undoRemittance(
        id: String,
        reason: String,
        actor: String,
    ) {
        remittances.firstOrNull { it.id == id }?.let {
            if (!it.undoable) return
            it.state = "DRAFT"
            it.snapshot = ""
            log("$actor undid remittance ${it.id} reason=$reason; days unlocked")
        }
    }

    fun grantRole(
        login: String,
        actor: String,
    ) {
        val idx = users.indexOfFirst { it.login == login }
        if (idx < 0) return
        val old = users[idx]
        users[idx] = old.copy(role = "Practitioner", caps = listOf("LOG_SESSION", "VIEW_BRANCH_DATA", "REQUEST_RELIEF"))
        log("$actor granted Practitioner to $login (was ${old.role})")
    }

    fun setActive(
        login: String,
        active: Boolean,
        actor: String,
    ) {
        users.firstOrNull { it.login == login }?.let {
            it.active = active
            log("$actor ${if (active) "reactivated" else "deactivated"} $login")
        }
    }
}
