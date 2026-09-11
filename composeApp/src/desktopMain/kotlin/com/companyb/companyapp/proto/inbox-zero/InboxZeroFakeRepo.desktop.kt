package com.companyb.companyapp.proto.inboxzero

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #856 — inbox-zero fake data only; isolated from networking and backend.
// The morning inbox is derived from repo state: every actionable row below is
// real domain state, and clearing a row performs (or deliberately snoozes) the
// underlying action. Dismissed rows stay visible in their home tab.

enum class IzSessionStatus(val label: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class IzDayState(val label: String) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class IzRole(val label: String) {
    ONBOARDING("Onboarding"),
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("Manager"),
    ACCOUNTANT("Accountant"),
}

enum class IzRemitKind(val label: String) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class IzRemitState(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    UNDONE("Undone"),
}

data class IzUser(
    val id: String,
    val name: String,
    var role: IzRole,
    val homeBranch: String,
    var clockedIn: Boolean = false,
)

data class IzBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayDate: String,
)

data class IzSession(
    val id: String,
    val service: String,
    val clientId: String,
    val client: String,
    var branch: String,
    var practitioner: String,
    val walkIn: Boolean,
    val amount: Int,
    var status: IzSessionStatus,
    var voided: Boolean = false,
    var voidReason: String? = null,
)

data class IzClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    var anonymized: Boolean = false,
)

data class IzRemittance(
    val id: String,
    val kind: IzRemitKind,
    val branchDay: String,
    val amount: Int,
    var state: IzRemitState,
    var snapshotId: String? = null,
    var undoReason: String? = null,
)

data class IzPayout(
    val id: String,
    val staff: String,
    var paid: Boolean = false,
)

data class IzNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean = false,
)

data class IzAudit(
    val seq: Int,
    val stamp: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class IzInvite(
    val id: String,
    val branch: String,
    val day: String,
    var state: String = "PENDING",
)

data class IzReliefAsk(
    val id: String,
    val branch: String,
    val day: String,
    val mine: Boolean,
    var state: String = "PENDING",
)

// Navigation target. Inbox rows point at these; the stage renders them.
data class IzSel(val kind: String, val id: String? = null)

// One triage row in the morning inbox. clearLabel names the one-tap resolve;
// rows that need human judgment use "Snooze" (dismiss, stays in its home tab).
data class IzInboxItem(
    val id: String,
    val kind: String,
    val title: String,
    val sub: String,
    val target: IzSel,
    val clearLabel: String,
)

class IzFakeRepo {
    val users = mutableStateListOf(
        IzUser("U-SAM", "Sam Rivera", IzRole.ONBOARDING, "QC Central"),
        IzUser("U-ANN", "Ann Reyes", IzRole.PRACTITIONER, "QC Central", clockedIn = true),
        IzUser("U-JOY", "Joy Cruz", IzRole.COORDINATOR, "QC Central", clockedIn = true),
        IzUser("U-MIA", "Mia Santos", IzRole.MANAGER, "Laguna Tour Stop 3"),
        IzUser("U-ROB", "Rob Dela Cruz", IzRole.ACCOUNTANT, "QC Central"),
    )
    val branches = mutableStateListOf(
        IzBranch("B-QC", "QC Central", "Clinic", "Thu · Sep 10, 2026"),
        IzBranch("B-LAG", "Laguna Tour Stop 3", "Provincial tour", "Thu · Sep 10, 2026"),
        IzBranch("B-TON", "Tondo Medical Mission", "Medical mission", "Thu · Sep 10, 2026"),
    )
    val sessions = mutableStateListOf(
        IzSession("S-101", "PT Rehab 45m", "C-01", "Liza Aquino", "QC Central", "Ann Reyes", false, 1200, IzSessionStatus.PENDING),
        IzSession("S-102", "Sports Massage", "C-02", "Mark Villanueva", "QC Central", "Ann Reyes", true, 900, IzSessionStatus.PENDING),
        IzSession("S-103", "Dry Needling", "C-03", "Katrina Uy", "QC Central", "Joy Cruz", false, 1500, IzSessionStatus.COMPLETED),
        IzSession("S-104", "PT Rehab 45m", "C-04", "Ramon Sy", "Laguna Tour Stop 3", "Mia Santos", false, 1200, IzSessionStatus.NO_SHOW),
        IzSession("S-105", "Wellness Stretch", "C-05", "Bea Lim", "QC Central", "Ann Reyes", false, 750, IzSessionStatus.CANCELLED),
        IzSession("S-106", "Post-op Rehab", "C-06", "Dan Fernandez", "QC Central", "Ann Reyes", false, 1800, IzSessionStatus.PENDING),
    )
    val clients = mutableStateListOf(
        IzClient("C-01", "Liza Aquino", "F", 34),
        IzClient("C-02", "Mark Villanueva", "M", 41),
        IzClient("C-03", "Katrina Uy", "F", 29),
        IzClient("C-04", "Ramon Sy", "M", 55),
        IzClient("C-05", "Bea Lim", "F", 23),
        IzClient("C-06", "Dan Fernandez", "M", 47),
    )
    val remittances = mutableStateListOf(
        IzRemittance("R-21", IzRemitKind.SESSION, "QC Central · Sep 09", 18450, IzRemitState.SUBMITTED, snapshotId = "SNAP-8841"),
        IzRemittance("R-22", IzRemitKind.PRODUCT, "QC Central · Sep 09", 6300, IzRemitState.DRAFT),
    )
    val payouts = mutableStateListOf(
        IzPayout("P-1", "Ann Reyes", paid = true),
        IzPayout("P-2", "Joy Cruz"),
    )
    val notices = mutableStateListOf(
        IzNotice("N-1", "Relief invite · Tondo Medical Mission · Sep 11", "QC Central offers you relief access for Sep 11. Accept to write the day grant."),
        IzNotice("N-2", "Remittance R-21 sealed · snapshot SNAP-8841", "SESSION remittance for QC Central · Sep 09 is now immutable. Undo window: 48h."),
        IzNotice("N-3", "Relief request from Mia Santos · Laguna Tour Stop 3 · today", "Mia asks for relief access today. Any active branch member can grant or deny.", read = true),
    )
    val audits = mutableStateListOf(
        IzAudit(1, "08:02", "Joy Cruz", "SUBMIT_REMITTANCE", "R-21 · SNAP-8841"),
        IzAudit(2, "08:20", "Ann Reyes", "COMPLETE_SESSION", "S-103"),
        IzAudit(3, "09:15", "Mia Santos", "RELIEF_REQUEST", "Laguna Tour Stop 3 · today"),
    )
    val invites = mutableStateListOf(
        IzInvite("I-1", "Tondo Medical Mission", "Sep 11"),
    )
    val asks = mutableStateListOf(
        IzReliefAsk("Q-1", "Laguna Tour Stop 3", "today", mine = false),
        IzReliefAsk("Q-2", "QC Central", "today", mine = true),
    )
    val dismissed = mutableStateListOf<String>()

    var actorId by mutableStateOf<String?>(null)
    var branchId by mutableStateOf("B-QC")
    var dayState by mutableStateOf(IzDayState.OPEN)
    var clockedIn by mutableStateOf(false)
    // Plain-var rows are not snapshot-observable, so every mutation funnels
    // through audit() and bumps this; the shell reads it to recompose.
    var version by mutableStateOf(0)
    private var seq = 4
    private var sessionSeq = 107
    private var remitSeq = 23
    private var snapSeq = 8842

    val actor: IzUser? get() = users.firstOrNull { it.id == actorId }
    val currentBranch: IzBranch get() = branches.firstOrNull { it.id == branchId } ?: branches.first()
    fun actorName(): String = actor?.name ?: "Signed out"
    fun pendingCount(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == IzSessionStatus.PENDING }

    // Morning inbox: derived triage queue. Dismissed rows drop out of the
    // inbox but stay fully actionable in their home tab.
    fun inbox(): List<IzInboxItem> {
        val out = mutableListOf<IzInboxItem>()
        fun add(item: IzInboxItem) {
            if (!dismissed.contains(item.id)) out.add(item)
        }
        if (!clockedIn) {
            add(IzInboxItem("clock", "Clock in", "You are off shift at ${currentBranch.name}", "Clock in to start the day", IzSel("home"), "Clock in"))
        }
        users.filter { it.role == IzRole.ONBOARDING }.forEach { u ->
            add(IzInboxItem("grant-${u.id}", "Onboarding", "${u.name} is locked (ONBOARDING)", "Zero capabilities until a role grant", IzSel("team", u.id), "Snooze"))
        }
        invites.filter { it.state == "PENDING" }.forEach { i ->
            add(IzInboxItem("inv-${i.id}", "Relief invite", "Relief invite · ${i.branch} · ${i.day}", "Accept to write the day grant", IzSel("home", i.id), "Accept"))
        }
        asks.filter { !it.mine && it.state == "PENDING" }.forEach { q ->
            add(IzInboxItem("ask-${q.id}", "Relief request", "Relief request · ${q.branch} · ${q.day}", "Any active branch member can grant or deny", IzSel("home", q.id), "Grant"))
        }
        sessions.filter { it.status == IzSessionStatus.PENDING && it.branch == currentBranch.name }.forEach { s ->
            add(IzInboxItem("ses-${s.id}", if (s.walkIn) "Walk-in" else "Session", "${s.id} · ${s.client} · ${s.service}", "${izPeso(s.amount)} · ${if (s.walkIn) "walk-in" else "booked"} · needs a verdict", IzSel("session", s.id), "Snooze"))
        }
        remittances.filter { it.state == IzRemitState.DRAFT }.forEach { r ->
            add(IzInboxItem("rem-${r.id}", "Remittance", "Draft ${r.id} · ${r.kind.label} · ${izPeso(r.amount)}", "Submit seals an immutable snapshot", IzSel("finance", r.id), "Snooze"))
        }
        notices.filter { !it.read }.forEach { n ->
            add(IzInboxItem("note-${n.id}", "Notice", n.title, n.body, IzSel("notices", n.id), "Mark read"))
        }
        return out
    }

    // One-tap triage resolve. Returns true when the row leaves the inbox.
    fun clear(item: IzInboxItem): Boolean {
        when {
            item.id == "clock" -> setClock(true)
            item.id.startsWith("note-") -> notices.firstOrNull { it.id == item.id.removePrefix("note-") }?.let { it.read = true }
            item.id.startsWith("inv-") -> invites.firstOrNull { it.id == item.id.removePrefix("inv-") }?.let { it.state = "ACCEPTED" }
            item.id.startsWith("ask-") -> asks.firstOrNull { it.id == item.id.removePrefix("ask-") }?.let { it.state = "GRANTED" }
            else -> dismissed.add(item.id)
        }
        audit("INBOX_CLEAR", "${item.kind} · ${item.title}")
        return true
    }

    fun snooze(item: IzInboxItem) {
        dismissed.add(item.id)
        audit("INBOX_SNOOZE", "${item.kind} · ${item.title}")
    }

    fun audit(action: String, target: String, reason: String? = null) {
        audits.add(0, IzAudit(seq++, "now", actorName(), action, target, reason))
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
        users.firstOrNull { it.id == id }?.let { it.role = IzRole.PRACTITIONER }
        audit("GRANT_ROLE", "$id -> Practitioner", "MANAGE_USERS grant from inbox-zero triage")
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
            IzDayState.OPEN -> IzDayState.PAST
            IzDayState.PAST -> IzDayState.REMITTED
            IzDayState.REMITTED -> IzDayState.OPEN
        }
        audit("CYCLE_DAY", "${currentBranch.name} -> ${dayState.label}")
    }

    fun transitionSession(s: IzSession, next: IzSessionStatus): Boolean {
        if (s.walkIn && (next == IzSessionStatus.NO_SHOW || next == IzSessionStatus.CANCELLED)) return false
        s.status = next
        audit(next.name + "_SESSION", "${s.id} · ${s.client}")
        return true
    }

    fun voidSession(s: IzSession, reason: String) {
        s.voided = true
        s.voidReason = reason.ifBlank { "no reason given" }
        audit("VOID_SESSION", s.id, s.voidReason)
    }

    fun unvoidSession(s: IzSession, reason: String) {
        s.voided = false
        s.voidReason = null
        audit("UNVOID_SESSION", s.id, reason.ifBlank { "entered in error" })
    }

    fun bookSession(clientId: String, service: String, walkIn: Boolean): IzSession? {
        val c = clients.firstOrNull { it.id == clientId } ?: return null
        if (!walkIn && pendingCount(clientId) >= 1) return null
        val s = IzSession(
            "S-${sessionSeq++}", service, c.id, c.name,
            currentBranch.name, actorName(), walkIn, 950, IzSessionStatus.PENDING,
        )
        sessions.add(0, s)
        audit(if (walkIn) "BOOK_WALKIN" else "BOOK_SESSION", "${s.id} · ${c.name}")
        return s
    }

    fun addWalkInClient(name: String): IzClient {
        val c = IzClient("C-%02d".format(clients.size + 1), name.ifBlank { "Walk-in guest" }, "F", 30)
        clients.add(0, c)
        audit("REGISTER_CLIENT", "${c.id} · ${c.name}")
        return c
    }

    fun anonymize(c: IzClient) {
        c.anonymized = true
        audit("ANONYMIZE_CLIENT", c.id, "PII nullified; gender + age kept for reporting")
    }

    fun reveal(c: IzClient) {
        c.anonymized = false
        audit("REVEAL_CLIENT", c.id)
    }

    fun newDraft(kind: IzRemitKind): IzRemittance {
        val r = IzRemittance("R-$remitSeq", kind, "${currentBranch.name} · today", 4000 + remitSeq * 350, IzRemitState.DRAFT)
        remitSeq++
        remittances.add(0, r)
        audit("DRAFT_REMITTANCE", "${r.id} · ${kind.label}")
        return r
    }

    fun submitRemit(r: IzRemittance) {
        r.state = IzRemitState.SUBMITTED
        r.snapshotId = "SNAP-${snapSeq++}"
        audit("SUBMIT_REMITTANCE", "${r.id} · ${r.snapshotId}")
    }

    fun undoRemit(r: IzRemittance, reason: String) {
        r.state = IzRemitState.UNDONE
        r.snapshotId = null
        r.undoReason = reason.ifBlank { "entered in error" }
        audit("UNDO_REMITTANCE", r.id, "${r.undoReason} · within 48h window")
    }

    fun markAllRead() {
        notices.forEach { it.read = true }
        audit("MARK_ALL_READ", "notifications mailbox")
    }

    fun acceptInvite(i: IzInvite) {
        i.state = "ACCEPTED"
        audit("ACCEPT_INVITE", "${i.branch} · ${i.day}")
    }

    fun declineInvite(i: IzInvite) {
        i.state = "DECLINED"
        audit("DECLINE_INVITE", "${i.branch} · ${i.day}")
    }

    fun grantAsk(q: IzReliefAsk) {
        q.state = "GRANTED"
        audit("GRANT_RELIEF", "${q.branch} · ${q.day}")
    }

    fun denyAsk(q: IzReliefAsk) {
        q.state = "DENIED"
        audit("DENY_RELIEF", "${q.branch} · ${q.day}")
    }

    fun withdrawAsk(q: IzReliefAsk) {
        q.state = "WITHDRAWN"
        audit("WITHDRAW_RELIEF", "${q.branch} · ${q.day}")
    }

    fun newAsk(branch: String) {
        asks.add(0, IzReliefAsk("Q-${seq++}", branch, "today", mine = true))
        audit("RELIEF_REQUEST", "$branch · today")
    }

    fun togglePaid(p: IzPayout) {
        p.paid = !p.paid
        audit(if (p.paid) "MARK_PAID" else "REOPEN_PAYOUT", p.staff)
    }
}

fun izPeso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}
