package com.companyb.companyapp.proto.ribbontabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RibbonDay { OPEN, PAST, REMITTED }

enum class RibbonStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class RibbonKind { BOOKED, WALK_IN }

enum class RibbonDraftKind { SESSION, PRODUCT }

enum class RibbonReliefKind { REQUEST, INVITE }

data class RibbonBranch(
    val id: String,
    val name: String,
    val place: String,
    val day: RibbonDay,
)

data class RibbonSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: RibbonKind,
    val status: RibbonStatus,
    val amount: Double,
    val practitioner: String,
    val voidReason: String = "",
)

data class RibbonClient(
    val id: String,
    val name: String,
    val code: String,
    val pending: Int,
    val visits: Int,
    val note: String,
)

data class RibbonDraft(
    val id: String,
    val kind: RibbonDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class RibbonMate(
    val name: String,
    val role: String,
    val note: String,
    val locked: Boolean = false,
)

data class RibbonNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    val read: Boolean,
)

data class RibbonAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class RibbonRelief(
    val id: String,
    val kind: RibbonReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    val answered: String = "",
)

class RibbonFakeRepo {
    var email by mutableStateOf("")
    var branchId by mutableStateOf("sunrise")
    var clockedIn by mutableStateOf(false)

    val branches = mutableStateListOf<RibbonBranch>()
    val sessions = mutableStateListOf<RibbonSession>()
    val clients = mutableStateListOf<RibbonClient>()
    val drafts = mutableStateListOf<RibbonDraft>()
    val mates = mutableStateListOf<RibbonMate>()
    val notices = mutableStateListOf<RibbonNotice>()
    val audits = mutableStateListOf<RibbonAudit>()
    val relief = mutableStateListOf<RibbonRelief>()

    private var seq = 100
    private var tick = 20

    init {
        seed()
    }

    private fun stamp(): String {
        tick += 1
        return "Tue 09:${tick.toString().padStart(2, '0')} · Manila"
    }

    private fun seed() {
        branches.addAll(
            listOf(
                RibbonBranch("sunrise", "Sunrise Clinic", "Makati", RibbonDay.OPEN),
                RibbonBranch("harbor", "Harbor Tour", "Cebu", RibbonDay.PAST),
                RibbonBranch("lingap", "Lingap Mission", "Davao", RibbonDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                RibbonSession("s1", "sunrise", "Mara Villanueva", "Deep Tissue 60", "Today · 10:00", RibbonKind.BOOKED, RibbonStatus.PENDING, 1200.0, "You"),
                RibbonSession("s2", "sunrise", "Walk-in ····07", "Chair 20", "Today · 11:30", RibbonKind.WALK_IN, RibbonStatus.PENDING, 350.0, "You"),
                RibbonSession("s3", "sunrise", "Jose Ramos", "Aromatherapy 45", "Today · 08:00", RibbonKind.BOOKED, RibbonStatus.COMPLETED, 950.0, "You"),
                RibbonSession("s4", "harbor", "Lena Cruz", "Foot Ritual 30", "Mon · 15:00", RibbonKind.BOOKED, RibbonStatus.NO_SHOW, 600.0, "Nadia"),
                RibbonSession("s5", "harbor", "Paolo Lim", "Deep Tissue 60", "Mon · 16:30", RibbonKind.BOOKED, RibbonStatus.CANCELLED, 1200.0, "Nadia"),
                RibbonSession("s6", "lingap", "Ama Reyes", "Community Circle", "Sun · 09:00", RibbonKind.BOOKED, RibbonStatus.PENDING, 0.0, "Theo"),
                RibbonSession("s7", "sunrise", "Irene Santos", "Hot Stone 75", "Today · 13:00", RibbonKind.BOOKED, RibbonStatus.PENDING, 1500.0, "You"),
                RibbonSession("s8", "sunrise", "Walk-in ····11", "Scalp 15", "Today · 14:00", RibbonKind.WALK_IN, RibbonStatus.COMPLETED, 250.0, "You"),
            ),
        )
        clients.addAll(
            listOf(
                RibbonClient("c1", "Mara Villanueva", "CLI-0042", 1, 12, "Prefers mornings; sensitive shoulders."),
                RibbonClient("c2", "Jose Ramos", "CLI-0017", 0, 30, "Member since 2023; gift card balance."),
                RibbonClient("c3", "Walk-in ····07", "WLK-0007", 1, 1, "First visit; patch-test oils."),
                RibbonClient("c4", "Lena Cruz", "CLI-0091", 0, 5, "No-show twice; confirm by SMS."),
                RibbonClient("c5", "Ama Reyes", "CLI-0103", 0, 8, "Community rate; anonymize in reports."),
            ),
        )
        drafts.addAll(
            listOf(
                RibbonDraft("d1", RibbonDraftKind.SESSION, "Tue session takings", 2500.0, 1, false),
                RibbonDraft("d2", RibbonDraftKind.PRODUCT, "Arnica oil × 3", 450.0, 3, false),
                RibbonDraft("d3", RibbonDraftKind.SESSION, "Mon session takings", 3100.0, 1, true, snapshot = "SNAP-0912-A"),
            ),
        )
        mates.addAll(
            listOf(
                RibbonMate("You", "Practitioner", "Hands today: 4 booked, 2 walk-in.", false),
                RibbonMate("Nadia", "Coordinator", "Owns the book; approves relief.", false),
                RibbonMate("Ramon", "MANAGER", "Signs remittance; reads audit.", false),
                RibbonMate("Aida", "Accountant", "Reconciles snapshots; 48h undo window.", false),
                RibbonMate("Newcomer", "ONBOARDING", "Locked: observes only, no capabilities.", true),
            ),
        )
        notices.addAll(
            listOf(
                RibbonNotice("n1", "Relief invite — Harbor Tour", "Nadia asks cover for Sat 14:00.", "Harbor Tour", "Mon", false),
                RibbonNotice("n2", "Snapshot sealed", "SNAP-0912-A filed by Ramon.", "Sunrise Clinic", "Mon", true),
                RibbonNotice("n3", "Walk-in surge", "3 walk-ins before noon; open Chair 2.", "Sunrise Clinic", "Tue", false),
            ),
        )
        audits.addAll(
            listOf(
                RibbonAudit("Tue 08:02", "You", "CLOCK_IN", "Sunrise Clinic · sheet OPEN"),
                RibbonAudit("Tue 08:20", "Nadia", "RELIEF_INVITE", "Harbor Tour Sat 14:00 → You"),
                RibbonAudit("Mon 18:40", "Ramon", "REMIT_SUBMIT", "SNAP-0912-A · SESSION 3100.00"),
                RibbonAudit("Mon 15:05", "Nadia", "VOID", "s5 · client asked to rebook"),
            ),
        )
        relief.addAll(
            listOf(
                RibbonRelief("r1", RibbonReliefKind.INVITE, "Harbor Tour", "Sat 14:00", "Nadia needs cover; 2 bookings.", ""),
                RibbonRelief("r2", RibbonReliefKind.REQUEST, "Sunrise Clinic", "Wed 10:00", "You asked: dental appointment.", "Pending coordinator"),
            ),
        )
    }

    fun currentBranch(): RibbonBranch = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun branchSessions(): List<RibbonSession> = sessions.filter { it.branchId == branchId }

    fun clock(inNow: Boolean) {
        clockedIn = inNow
        audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, if (inNow) "CLOCK_IN" else "CLOCK_OUT", currentBranch().name))
    }

    fun moveDay(day: RibbonDay) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i >= 0) {
            val b = branches[i]
            branches[i] = b.copy(day = day)
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "DAY_MOVE", "${b.name} → ${day.name}"))
        }
    }

    fun setStatus(id: String, status: RibbonStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = status)
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "SESSION_${status.name}", "${s.client} · ${s.service}"))
        }
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = RibbonStatus.CANCELLED, voidReason = reason)
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "VOID", "${s.client} · $reason"))
        }
    }

    fun unvoidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = RibbonStatus.PENDING, voidReason = "")
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "UNVOID", "${s.client} · $reason"))
        }
    }

    fun addWalkIn(name: String, service: String) {
        seq += 1
        sessions.add(0, RibbonSession("s$seq", branchId, name.ifBlank { "Walk-in ····$seq" }, service.ifBlank { "Chair 20" }, "Today · now", RibbonKind.WALK_IN, RibbonStatus.PENDING, 350.0, "You"))
        audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "WALK_IN", name.ifBlank { "Walk-in ····$seq" }))
    }

    fun addDraft(kind: RibbonDraftKind, label: String, amount: Double, qty: Int) {
        seq += 1
        drafts.add(0, RibbonDraft("d$seq", kind, label.ifBlank { "Untitled draft" }, amount, qty, false))
        audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "DRAFT_ADD", label.ifBlank { "Untitled draft" }))
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            seq += 1
            drafts[i] = d.copy(submitted = true, snapshot = "SNAP-0912-$seq")
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "REMIT_SUBMIT", "${d.label} · SNAP-0912-$seq"))
        }
    }

    fun undoDraft(id: String, reason: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            drafts[i] = d.copy(undone = true, undoReason = reason)
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "REMIT_UNDO", "${d.label} · $reason (within 48h)"))
        }
    }

    fun toggleNotice(id: String) {
        val i = notices.indexOfFirst { it.id == id }
        if (i >= 0) {
            val n = notices[i]
            notices[i] = n.copy(read = !n.read)
        }
    }

    fun markAllRead() {
        for (i in notices.indices) notices[i] = notices[i].copy(read = true)
    }

    fun answerRelief(id: String, answer: String) {
        val i = relief.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = relief[i]
            relief[i] = r.copy(answered = answer)
            audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "RELIEF_$answer", "${r.branch} · ${r.day}"))
        }
    }

    fun askRelief(note: String) {
        seq += 1
        relief.add(0, RibbonRelief("r$seq", RibbonReliefKind.REQUEST, currentBranch().name, "Next sheet", note.ifBlank { "Cover needed" }, "Pending coordinator"))
        audits.add(0, RibbonAudit(stamp(), email.ifBlank { "You" }, "RELIEF_REQUEST", note.ifBlank { "Cover needed" }))
    }
}
