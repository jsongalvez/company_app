package com.companyb.companyapp.proto.searchonly

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #855 — search-only fake data only; isolated from networking and backend.
// One command box drives everything, so the repo doubles as the search index.

enum class SoSessionStatus(val label: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class SoDayState(val label: String) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class SoRole(val label: String) {
    ONBOARDING("Onboarding"),
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("Manager"),
    ACCOUNTANT("Accountant"),
}

enum class SoRemitKind(val label: String) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class SoRemitState(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    UNDONE("Undone"),
}

data class SoUser(
    val id: String,
    val name: String,
    var role: SoRole,
    val homeBranch: String,
    var clockedIn: Boolean = false,
    var reliefBranch: String? = null,
)

data class SoBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayDate: String,
)

data class SoSession(
    val id: String,
    val service: String,
    val clientId: String,
    val client: String,
    var branch: String,
    var practitioner: String,
    val walkIn: Boolean,
    val amount: Int,
    var status: SoSessionStatus,
    var voided: Boolean = false,
    var voidReason: String? = null,
)

data class SoClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
)

data class SoRemittance(
    val id: String,
    val kind: SoRemitKind,
    val branchDay: String,
    val amount: Int,
    var state: SoRemitState,
    var snapshotId: String? = null,
    var undoReason: String? = null,
)

data class SoPayout(
    val id: String,
    val staff: String,
    var paid: Boolean = false,
)

data class SoNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean = false,
)

data class SoAudit(
    val seq: Int,
    val stamp: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class SoInvite(
    val id: String,
    val branch: String,
    val day: String,
    var state: String = "PENDING",
)

data class SoReliefAsk(
    val id: String,
    val branch: String,
    val day: String,
    val mine: Boolean,
    var state: String = "PENDING",
)

// Selection target for the detail stage. Recents store these.
data class SoSel(val kind: String, val id: String? = null) {
    fun label(repo: SearchOnlyFakeRepo): String = when (kind) {
        "session" -> repo.sessions.firstOrNull { it.id == id }?.let { "${it.id} · ${it.client}" } ?: (id ?: "session")
        "client" -> repo.clients.firstOrNull { it.id == id }?.let { "Client ${it.name}" } ?: (id ?: "client")
        else -> when (kind) {
            "home" -> "Clock & relief"
            "finance" -> "Finance & remittance"
            "team" -> "Team & roles"
            "notices" -> "Notifications"
            "audit" -> "Audit log"
            "profile" -> "Profile"
            "branches" -> "Branches"
            "onboarding" -> "Onboarding lock"
            else -> kind.replaceFirstChar { it.uppercase() }
        }
    }
}

class SearchOnlyFakeRepo {
    val users = mutableStateListOf(
        SoUser("U-SAM", "Sam Rivera", SoRole.ONBOARDING, "QC Central"),
        SoUser("U-ANN", "Ann Reyes", SoRole.PRACTITIONER, "QC Central", clockedIn = true),
        SoUser("U-JOY", "Joy Cruz", SoRole.COORDINATOR, "QC Central", clockedIn = true),
        SoUser("U-MIA", "Mia Santos", SoRole.MANAGER, "Laguna Tour Stop 3"),
        SoUser("U-ROB", "Rob Dela Cruz", SoRole.ACCOUNTANT, "QC Central"),
    )
    val branches = mutableStateListOf(
        SoBranch("B-QC", "QC Central", "Clinic", "Thu · Sep 10, 2026"),
        SoBranch("B-LAG", "Laguna Tour Stop 3", "Provincial tour", "Thu · Sep 10, 2026"),
        SoBranch("B-TON", "Tondo Medical Mission", "Medical mission", "Thu · Sep 10, 2026"),
    )
    val sessions = mutableStateListOf(
        SoSession("S-101", "PT Rehab 45m", "C-01", "Liza Aquino", "QC Central", "Ann Reyes", false, 1200, SoSessionStatus.PENDING),
        SoSession("S-102", "Sports Massage", "C-02", "Mark Villanueva", "QC Central", "Ann Reyes", true, 900, SoSessionStatus.PENDING),
        SoSession("S-103", "Dry Needling", "C-03", "Katrina Uy", "QC Central", "Joy Cruz", false, 1500, SoSessionStatus.COMPLETED),
        SoSession("S-104", "PT Rehab 45m", "C-04", "Ramon Sy", "Laguna Tour Stop 3", "Mia Santos", false, 1200, SoSessionStatus.NO_SHOW),
        SoSession("S-105", "Wellness Stretch", "C-05", "Bea Lim", "QC Central", "Ann Reyes", false, 750, SoSessionStatus.CANCELLED),
        SoSession("S-106", "Post-op Rehab", "C-06", "Dan Fernandez", "QC Central", "Ann Reyes", false, 1800, SoSessionStatus.PENDING),
    )
    val clients = mutableStateListOf(
        SoClient("C-01", "Liza Aquino", "F", 34),
        SoClient("C-02", "Mark Villanueva", "M", 41),
        SoClient("C-03", "Katrina Uy", "F", 29),
        SoClient("C-04", "Ramon Sy", "M", 55),
        SoClient("C-05", "Bea Lim", "F", 23),
        SoClient("C-06", "Dan Fernandez", "M", 47),
    )
    val remittances = mutableStateListOf(
        SoRemittance("R-21", SoRemitKind.SESSION, "QC Central · Sep 09", 18450, SoRemitState.SUBMITTED, snapshotId = "SNAP-8841"),
        SoRemittance("R-22", SoRemitKind.PRODUCT, "QC Central · Sep 09", 6300, SoRemitState.DRAFT),
    )
    val payouts = mutableStateListOf(
        SoPayout("P-1", "Ann Reyes", paid = true),
        SoPayout("P-2", "Joy Cruz"),
    )
    val notices = mutableStateListOf(
        SoNotice("N-1", "Relief invite · Tondo Medical Mission · Sep 11", "QC Central offers you relief access for Sep 11. Accept to write the day grant."),
        SoNotice("N-2", "Remittance R-21 sealed · snapshot SNAP-8841", "SESSION remittance for QC Central · Sep 09 is now immutable. Undo window: 48h."),
        SoNotice("N-3", "Relief request from Mia Santos · Laguna Tour Stop 3 · today", "Mia asks for relief access today. Any active branch member can grant or deny.", read = true),
    )
    val audits = mutableStateListOf(
        SoAudit(1, "08:02", "Joy Cruz", "SUBMIT_REMITTANCE", "R-21 · SNAP-8841"),
        SoAudit(2, "08:20", "Ann Reyes", "COMPLETE_SESSION", "S-103"),
        SoAudit(3, "09:15", "Mia Santos", "RELIEF_REQUEST", "Laguna Tour Stop 3 · today"),
    )
    val invites = mutableStateListOf(
        SoInvite("I-1", "Tondo Medical Mission", "Sep 11"),
    )
    val asks = mutableStateListOf(
        SoReliefAsk("Q-1", "Laguna Tour Stop 3", "today", mine = false),
        SoReliefAsk("Q-2", "QC Central", "today", mine = true),
    )
    val recents = mutableStateListOf<SoSel>()

    var actorId by mutableStateOf<String?>(null)
    var branchId by mutableStateOf("B-QC")
    var dayState by mutableStateOf(SoDayState.OPEN)
    var clockedIn by mutableStateOf(false)
    // Plain-var rows are not snapshot-observable, so every mutation funnels
    // through audit() and bumps this; the shell reads it to recompose.
    var version by mutableStateOf(0)
    private var seq = 4
    private var sessionSeq = 107
    private var remitSeq = 23
    private var snapSeq = 8842

    val actor: SoUser? get() = users.firstOrNull { it.id == actorId }
    val currentBranch: SoBranch get() = branches.firstOrNull { it.id == branchId } ?: branches.first()
    fun actorName(): String = actor?.name ?: "Signed out"
    fun pendingCount(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == SoSessionStatus.PENDING }

    fun touch(sel: SoSel) {
        recents.removeAll { it == sel }
        recents.add(0, sel)
        while (recents.size > 8) recents.removeLast()
    }

    fun audit(action: String, target: String, reason: String? = null) {
        audits.add(0, SoAudit(seq++, "now", actorName(), action, target, reason))
        version++
    }

    fun login(id: String) {
        actorId = id
        val u = actor
        clockedIn = u?.clockedIn == true
        audit("LOGIN", u?.name ?: id)
    }

    fun logout() {
        audit("LOGOUT", actorName())
        actorId = null
        clockedIn = false
    }

    fun grantPractitioner(id: String) {
        users.firstOrNull { it.id == id }?.let { it.role = SoRole.PRACTITIONER }
        audit("GRANT_ROLE", "$id -> Practitioner", "MANAGE_USERS grant from search-only command")
    }

    fun setClock(inNow: Boolean) {
        clockedIn = inNow
        actor?.clockedIn = inNow
        audit(if (inNow) "CLOCK_IN" else "CLOCK_OUT", "${actorName()} · ${currentBranch.name}")
    }

    fun switchBranch(id: String) {
        branchId = id
        audit("SWITCH_BRANCH", currentBranch.name)
    }

    fun cycleDay() {
        dayState = when (dayState) {
            SoDayState.OPEN -> SoDayState.PAST
            SoDayState.PAST -> SoDayState.REMITTED
            SoDayState.REMITTED -> SoDayState.OPEN
        }
        audit("CYCLE_DAY", "${currentBranch.name} -> ${dayState.label}")
    }

    fun transitionSession(s: SoSession, next: SoSessionStatus): Boolean {
        if (s.walkIn && (next == SoSessionStatus.NO_SHOW || next == SoSessionStatus.CANCELLED)) return false
        s.status = next
        audit(next.name + "_SESSION", "${s.id} · ${s.client}")
        return true
    }

    fun voidSession(s: SoSession, reason: String) {
        s.voided = true
        s.voidReason = reason.ifBlank { "no reason given" }
        audit("VOID_SESSION", s.id, s.voidReason)
    }

    fun unvoidSession(s: SoSession, reason: String) {
        s.voided = false
        s.voidReason = null
        audit("UNVOID_SESSION", s.id, reason.ifBlank { "entered in error" })
    }

    fun bookSession(clientId: String, service: String, walkIn: Boolean): SoSession? {
        val c = clients.firstOrNull { it.id == clientId } ?: return null
        if (!walkIn && pendingCount(clientId) >= 1) return null
        val s = SoSession(
            "S-${sessionSeq++}", service, c.id, c.name,
            currentBranch.name, actorName(), walkIn, 950, SoSessionStatus.PENDING,
        )
        sessions.add(0, s)
        audit(if (walkIn) "BOOK_WALKIN" else "BOOK_SESSION", "${s.id} · ${c.name}")
        return s
    }

    fun anonymize(c: SoClient) {
        c.anonymized = true
        audit("ANONYMIZE_CLIENT", c.id, "PII nullified; gender + age kept for reporting")
    }

    fun reveal(c: SoClient) {
        c.anonymized = false
        audit("REVEAL_CLIENT", c.id)
    }

    fun newDraft(kind: SoRemitKind): SoRemittance {
        val r = SoRemittance("R-$remitSeq", kind, "${currentBranch.name} · today", 4000 + remitSeq * 350, SoRemitState.DRAFT)
        remitSeq++
        remittances.add(0, r)
        audit("DRAFT_REMITTANCE", "${r.id} · ${kind.label}")
        return r
    }

    fun submitRemit(r: SoRemittance) {
        r.state = SoRemitState.SUBMITTED
        r.snapshotId = "SNAP-${snapSeq++}"
        audit("SUBMIT_REMITTANCE", "${r.id} · ${r.snapshotId}")
    }

    fun undoRemit(r: SoRemittance, reason: String) {
        r.state = SoRemitState.UNDONE
        r.snapshotId = null
        r.undoReason = reason.ifBlank { "entered in error" }
        audit("UNDO_REMITTANCE", r.id, "${r.undoReason} · within 48h window")
    }

    fun markAllRead() {
        notices.forEach { it.read = true }
        audit("MARK_ALL_READ", "notifications mailbox")
    }

    fun acceptInvite(i: SoInvite) {
        i.state = "ACCEPTED"
        audit("ACCEPT_INVITE", "${i.branch} · ${i.day}")
    }

    fun declineInvite(i: SoInvite) {
        i.state = "DECLINED"
        audit("DECLINE_INVITE", "${i.branch} · ${i.day}")
    }

    fun grantAsk(q: SoReliefAsk) {
        q.state = "GRANTED"
        audit("GRANT_RELIEF", "${q.branch} · ${q.day}")
    }

    fun denyAsk(q: SoReliefAsk) {
        q.state = "DENIED"
        audit("DENY_RELIEF", "${q.branch} · ${q.day}")
    }

    fun withdrawAsk(q: SoReliefAsk) {
        q.state = "WITHDRAWN"
        audit("WITHDRAW_RELIEF", "${q.branch} · ${q.day}")
    }

    fun newAsk(branch: String) {
        asks.add(0, SoReliefAsk("Q-${seq++}", branch, "today", mine = true))
        audit("RELIEF_REQUEST", "$branch · today")
    }

    fun togglePaid(p: SoPayout) {
        p.paid = !p.paid
        audit(if (p.paid) "MARK_PAID" else "REOPEN_PAYOUT", p.staff)
    }
}

fun soPeso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}
