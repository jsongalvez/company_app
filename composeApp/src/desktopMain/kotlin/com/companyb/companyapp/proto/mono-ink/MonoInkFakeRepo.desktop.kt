package com.companyb.companyapp.proto.monoink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MonoDay { OPEN, PAST, REMITTED }

enum class MonoStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class MonoKind { BOOKED, WALK_IN }

enum class MonoDraftKind { SESSION, PRODUCT }

enum class MonoReliefKind { REQUEST, INVITE }

data class MonoBranch(
    val id: String,
    val name: String,
    val place: String,
    val day: MonoDay,
)

data class MonoSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: MonoKind,
    val status: MonoStatus,
    val amount: Double,
    val practitioner: String,
    val voidReason: String = "",
)

data class MonoClient(
    val id: String,
    val name: String,
    val code: String,
    val pending: Int,
    val visits: Int,
    val note: String,
)

data class MonoDraft(
    val id: String,
    val kind: MonoDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class MonoMate(
    val name: String,
    val role: String,
    val note: String,
    val locked: Boolean = false,
)

data class MonoNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    val read: Boolean,
)

data class MonoAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class MonoRelief(
    val id: String,
    val kind: MonoReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    val answered: String = "",
)

class MonoFakeRepo {
    var email by mutableStateOf("")
    var branchId by mutableStateOf("sunrise")
    var clockedIn by mutableStateOf(false)

    val branches = mutableStateListOf<MonoBranch>()
    val sessions = mutableStateListOf<MonoSession>()
    val clients = mutableStateListOf<MonoClient>()
    val drafts = mutableStateListOf<MonoDraft>()
    val mates = mutableStateListOf<MonoMate>()
    val notices = mutableStateListOf<MonoNotice>()
    val audits = mutableStateListOf<MonoAudit>()
    val relief = mutableStateListOf<MonoRelief>()

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
                MonoBranch("sunrise", "Sunrise Clinic", "Makati", MonoDay.OPEN),
                MonoBranch("harbor", "Harbor Tour", "Cebu", MonoDay.PAST),
                MonoBranch("lingap", "Lingap Mission", "Davao", MonoDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                MonoSession("s1", "sunrise", "Mara Villanueva", "Deep Tissue 60", "Today · 10:00", MonoKind.BOOKED, MonoStatus.PENDING, 1200.0, "You"),
                MonoSession("s2", "sunrise", "Walk-in ····07", "Chair 20", "Today · 11:30", MonoKind.WALK_IN, MonoStatus.PENDING, 350.0, "You"),
                MonoSession("s3", "sunrise", "Jose Ramos", "Aromatherapy 45", "Today · 08:00", MonoKind.BOOKED, MonoStatus.COMPLETED, 950.0, "You"),
                MonoSession("s4", "harbor", "Lena Cruz", "Foot Ritual 30", "Mon · 15:00", MonoKind.BOOKED, MonoStatus.NO_SHOW, 600.0, "Nadia"),
                MonoSession("s5", "harbor", "Paolo Lim", "Deep Tissue 60", "Mon · 16:30", MonoKind.BOOKED, MonoStatus.CANCELLED, 1200.0, "Nadia"),
                MonoSession("s6", "lingap", "Ama Reyes", "Community Circle", "Sun · 09:00", MonoKind.BOOKED, MonoStatus.PENDING, 0.0, "Theo"),
                MonoSession("s7", "sunrise", "Irene Santos", "Hot Stone 75", "Today · 13:00", MonoKind.BOOKED, MonoStatus.PENDING, 1500.0, "You"),
                MonoSession("s8", "sunrise", "Walk-in ····11", "Scalp 15", "Today · 14:00", MonoKind.WALK_IN, MonoStatus.COMPLETED, 250.0, "You"),
            ),
        )
        clients.addAll(
            listOf(
                MonoClient("c1", "Mara Villanueva", "CLI-0042", 1, 12, "Prefers mornings; sensitive shoulders."),
                MonoClient("c2", "Jose Ramos", "CLI-0017", 0, 30, "Member since 2023; gift card balance."),
                MonoClient("c3", "Walk-in ····07", "WLK-0007", 1, 1, "First visit; patch-test oils."),
                MonoClient("c4", "Lena Cruz", "CLI-0091", 0, 5, "No-show twice; confirm by SMS."),
                MonoClient("c5", "Ama Reyes", "CLI-0103", 0, 8, "Community rate; anonymize in reports."),
            ),
        )
        drafts.addAll(
            listOf(
                MonoDraft("d1", MonoDraftKind.SESSION, "Tue session takings", 2500.0, 1, false),
                MonoDraft("d2", MonoDraftKind.PRODUCT, "Arnica oil × 3", 450.0, 3, false),
                MonoDraft("d3", MonoDraftKind.SESSION, "Mon session takings", 3100.0, 1, true, snapshot = "SNAP-0912-A"),
            ),
        )
        mates.addAll(
            listOf(
                MonoMate("You", "Practitioner", "Hands today: 4 booked, 2 walk-in.", false),
                MonoMate("Nadia", "Coordinator", "Owns the book; approves relief.", false),
                MonoMate("Ramon", "MANAGER", "Signs remittance; reads audit.", false),
                MonoMate("Aida", "Accountant", "Reconciles snapshots; 48h undo window.", false),
                MonoMate("Newcomer", "ONBOARDING", "Locked: observes only, no capabilities.", true),
            ),
        )
        notices.addAll(
            listOf(
                MonoNotice("n1", "Relief invite — Harbor Tour", "Nadia asks cover for Sat 14:00.", "Harbor Tour", "Mon", false),
                MonoNotice("n2", "Snapshot sealed", "SNAP-0912-A filed by Ramon.", "Sunrise Clinic", "Mon", true),
                MonoNotice("n3", "Walk-in surge", "3 walk-ins before noon; open Chair 2.", "Sunrise Clinic", "Tue", false),
            ),
        )
        audits.addAll(
            listOf(
                MonoAudit("Tue 08:02", "You", "CLOCK_IN", "Sunrise Clinic · sheet OPEN"),
                MonoAudit("Tue 08:20", "Nadia", "RELIEF_INVITE", "Harbor Tour Sat 14:00 → You"),
                MonoAudit("Mon 18:40", "Ramon", "REMIT_SUBMIT", "SNAP-0912-A · SESSION 3100.00"),
                MonoAudit("Mon 15:05", "Nadia", "VOID", "s5 · client asked to rebook"),
            ),
        )
        relief.addAll(
            listOf(
                MonoRelief("r1", MonoReliefKind.INVITE, "Harbor Tour", "Sat 14:00", "Nadia needs cover; 2 bookings.", ""),
                MonoRelief("r2", MonoReliefKind.REQUEST, "Sunrise Clinic", "Wed 10:00", "You asked: dental appointment.", "Pending coordinator"),
            ),
        )
    }

    fun currentBranch(): MonoBranch = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun branchSessions(): List<MonoSession> = sessions.filter { it.branchId == branchId }

    fun clock(inNow: Boolean) {
        clockedIn = inNow
        audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, if (inNow) "CLOCK_IN" else "CLOCK_OUT", currentBranch().name))
    }

    fun moveDay(day: MonoDay) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i >= 0) {
            val b = branches[i]
            branches[i] = b.copy(day = day)
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "DAY_MOVE", "${b.name} → ${day.name}"))
        }
    }

    fun setStatus(id: String, status: MonoStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = status)
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "SESSION_${status.name}", "${s.client} · ${s.service}"))
        }
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = MonoStatus.CANCELLED, voidReason = reason)
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "VOID", "${s.client} · $reason"))
        }
    }

    fun unvoidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = MonoStatus.PENDING, voidReason = "")
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "UNVOID", "${s.client} · $reason"))
        }
    }

    fun addWalkIn(name: String, service: String) {
        seq += 1
        sessions.add(0, MonoSession("s$seq", branchId, name.ifBlank { "Walk-in ····$seq" }, service.ifBlank { "Chair 20" }, "Today · now", MonoKind.WALK_IN, MonoStatus.PENDING, 350.0, "You"))
        audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "WALK_IN", name.ifBlank { "Walk-in ····$seq" }))
    }

    fun addDraft(kind: MonoDraftKind, label: String, amount: Double, qty: Int) {
        seq += 1
        drafts.add(0, MonoDraft("d$seq", kind, label.ifBlank { "Untitled draft" }, amount, qty, false))
        audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "DRAFT_ADD", label.ifBlank { "Untitled draft" }))
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            seq += 1
            drafts[i] = d.copy(submitted = true, snapshot = "SNAP-0912-$seq")
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "REMIT_SUBMIT", "${d.label} · SNAP-0912-$seq"))
        }
    }

    fun undoDraft(id: String, reason: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            drafts[i] = d.copy(undone = true, undoReason = reason)
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "REMIT_UNDO", "${d.label} · $reason (within 48h)"))
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
            audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "RELIEF_$answer", "${r.branch} · ${r.day}"))
        }
    }

    fun askRelief(note: String) {
        seq += 1
        relief.add(0, MonoRelief("r$seq", MonoReliefKind.REQUEST, currentBranch().name, "Next sheet", note.ifBlank { "Cover needed" }, "Pending coordinator"))
        audits.add(0, MonoAudit(stamp(), email.ifBlank { "You" }, "RELIEF_REQUEST", note.ifBlank { "Cover needed" }))
    }
}
