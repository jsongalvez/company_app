package com.companyb.companyapp.proto.zenfocus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ZenDay { OPEN, PAST, REMITTED }

enum class ZenStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class ZenKind { BOOKED, WALK_IN }

enum class ZenDraftKind { SESSION, PRODUCT }

enum class ZenReliefKind { REQUEST, INVITE }

data class ZenBranch(
    val id: String,
    val name: String,
    val place: String,
    val day: ZenDay,
)

data class ZenSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: ZenKind,
    val status: ZenStatus,
    val amount: Double,
    val practitioner: String,
    val voidReason: String = "",
)

data class ZenClient(
    val id: String,
    val name: String,
    val code: String,
    val pending: Int,
    val visits: Int,
    val note: String,
)

data class ZenDraft(
    val id: String,
    val kind: ZenDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class ZenMate(
    val name: String,
    val role: String,
    val note: String,
    val locked: Boolean = false,
)

data class ZenNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    val read: Boolean,
)

data class ZenAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class ZenRelief(
    val id: String,
    val kind: ZenReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    val answered: String = "",
)

class ZenFakeRepo {
    var email by mutableStateOf("")
    var branchId by mutableStateOf("sunrise")
    var clockedIn by mutableStateOf(false)

    val branches = mutableStateListOf<ZenBranch>()
    val sessions = mutableStateListOf<ZenSession>()
    val clients = mutableStateListOf<ZenClient>()
    val drafts = mutableStateListOf<ZenDraft>()
    val mates = mutableStateListOf<ZenMate>()
    val notices = mutableStateListOf<ZenNotice>()
    val audits = mutableStateListOf<ZenAudit>()
    val relief = mutableStateListOf<ZenRelief>()

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
                ZenBranch("sunrise", "Sunrise Clinic", "Makati", ZenDay.OPEN),
                ZenBranch("harbor", "Harbor Tour", "Cebu", ZenDay.PAST),
                ZenBranch("lingap", "Lingap Mission", "Davao", ZenDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                ZenSession(
                    "s1", "sunrise", "Mara Villanueva", "Deep Tissue 60", "Today · 10:00",
                    ZenKind.BOOKED, ZenStatus.PENDING, 1200.0, "You",
                ),
                ZenSession(
                    "s2", "sunrise", "Walk-in ····07", "Chair 20", "Today · 11:30",
                    ZenKind.WALK_IN, ZenStatus.PENDING, 350.0, "You",
                ),
                ZenSession(
                    "s3", "sunrise", "Jose Ramos", "Aromatherapy 45", "Today · 08:00",
                    ZenKind.BOOKED, ZenStatus.COMPLETED, 950.0, "You",
                ),
                ZenSession(
                    "s4", "harbor", "Lena Cruz", "Foot Ritual 30", "Mon · 15:00",
                    ZenKind.BOOKED, ZenStatus.NO_SHOW, 600.0, "Nadia",
                ),
                ZenSession(
                    "s5", "harbor", "Paolo Lim", "Deep Tissue 60", "Mon · 16:30",
                    ZenKind.BOOKED, ZenStatus.CANCELLED, 1200.0, "Nadia",
                ),
                ZenSession(
                    "s6", "lingap", "Ama Reyes", "Community Circle", "Sun · 09:00",
                    ZenKind.BOOKED, ZenStatus.PENDING, 0.0, "Theo",
                ),
                ZenSession(
                    "s7", "lingap", "Bea Santos", "Aromatherapy 45", "Sun · 10:30",
                    ZenKind.WALK_IN, ZenStatus.COMPLETED, 400.0, "Theo",
                ),
            ),
        )
        clients.addAll(
            listOf(
                ZenClient("c1", "Mara Villanueva", "Client ····42", 1, 6, "Prefers quiet rooms."),
                ZenClient("c2", "Jose Ramos", "Client ····17", 0, 11, "Standing monthly visit."),
                ZenClient("c3", "Lena Cruz", "Client ····88", 0, 3, "Missed Monday; be gentle."),
                ZenClient("c4", "Ama Reyes", "Client ····05", 1, 2, "Mission guest."),
                ZenClient("c5", "Bea Santos", "Client ····63", 0, 4, "Walk-in regular."),
            ),
        )
        drafts.addAll(
            listOf(
                ZenDraft("d1", ZenDraftKind.SESSION, "Tue session net", 2500.0, 1, submitted = false),
                ZenDraft("d2", ZenDraftKind.PRODUCT, "Ginger oil × 3", 450.0, 3, submitted = false),
                ZenDraft(
                    "d3", ZenDraftKind.SESSION, "Mon session net", 4100.0, 1,
                    submitted = true, snapshot = "SNAP-0041",
                ),
            ),
        )
        mates.addAll(
            listOf(
                ZenMate(
                    "You", "Practitioner",
                    "Holds today's mat. Can begin and complete sessions.",
                ),
                ZenMate(
                    "Nadia Flores", "Coordinator",
                    "Keeps the book: branches, relief, remittance drafts.",
                ),
                ZenMate("Marco Uy", "Manager", "Moves branch days and reviews snapshots."),
                ZenMate("Iris Tan", "Accountant", "Reads sealed snapshots; never edits drafts."),
                ZenMate("R. Aquino", "Onboarding", "Observes only — no capabilities yet.", locked = true),
            ),
        )
        notices.addAll(
            listOf(
                ZenNotice(
                    "n1", "Harbor asks for cover", "Thursday 14:00 needs one practitioner.",
                    "Harbor Tour", "Thu", read = false,
                ),
                ZenNotice(
                    "n2", "Snapshot sealed", "SNAP-0041 was sealed by Nadia.",
                    "Sunrise Clinic", "Tue", read = false,
                ),
                ZenNotice(
                    "n3", "Lingap day remitted", "Sunday's day is remitted and resting.",
                    "Lingap Mission", "Sun", read = true,
                ),
                ZenNotice(
                    "n4", "Oil stock low", "Ginger oil has three bottles left.",
                    "Sunrise Clinic", "Tue", read = true,
                ),
            ),
        )
        audits.addAll(
            listOf(
                ZenAudit(
                    "Tue 08:02 · Manila", "Nadia", "sealed snapshot",
                    "SNAP-0041 for Monday session net.",
                ),
                ZenAudit(
                    "Tue 08:40 · Manila", "You", "clocked in",
                    "Sunrise Clinic, Tuesday branch day.",
                ),
            ),
        )
        relief.addAll(
            listOf(
                ZenRelief(
                    "r1", ZenReliefKind.INVITE, "Harbor Tour", "Thu 14:00",
                    "One practitioner to hold the afternoon mat.",
                ),
                ZenRelief(
                    "r2", ZenReliefKind.REQUEST, "Lingap Mission", "Sat 09:00",
                    "An opener for the community circle.",
                ),
            ),
        )
    }

    fun reset() {
        email = ""
        branchId = "sunrise"
        clockedIn = false
        seq = 100
        tick = 20
        branches.clear()
        sessions.clear()
        clients.clear()
        drafts.clear()
        mates.clear()
        notices.clear()
        audits.clear()
        relief.clear()
        seed()
    }

    fun currentBranch(): ZenBranch = branches.first { it.id == branchId }

    fun branchSessions(): List<ZenSession> = sessions.filter { it.branchId == branchId }

    fun oneTask(): ZenSession? =
        branchSessions().firstOrNull { it.status == ZenStatus.PENDING && it.voidReason.isEmpty() }

    fun cycleDay() {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        val next =
            when (branches[i].day) {
                ZenDay.OPEN -> ZenDay.PAST
                ZenDay.PAST -> ZenDay.REMITTED
                ZenDay.REMITTED -> ZenDay.OPEN
            }
        branches[i] = branches[i].copy(day = next)
        audit("You", "moved branch day", "${branches[i].name} is now ${next.name}.")
    }

    fun setStatus(
        id: String,
        status: ZenStatus,
    ) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(status = status)
        val action = "marked ${status.name.lowercase().replace('_', '-')}"
        audit("You", action, "${sessions[i].client} · ${sessions[i].service}.")
    }

    fun voidSession(
        id: String,
        reason: String,
    ) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voidReason = reason.ifBlank { "No reason given" })
        audit("You", "voided session", "${sessions[i].client} · reason: ${sessions[i].voidReason}.")
    }

    fun unvoidSession(
        id: String,
        reason: String,
    ) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val kept = sessions[i].client
        sessions[i] = sessions[i].copy(voidReason = "")
        audit("You", "unvoided session", "$kept · reason: ${reason.ifBlank { "No reason given" }}.")
    }

    fun addDraft(
        kind: ZenDraftKind,
        label: String,
        amount: Double,
        qty: Int,
    ) {
        seq += 1
        drafts.add(ZenDraft("d$seq", kind, label.ifBlank { "Untitled" }, amount, qty, submitted = false))
        audit("You", "drafted ${kind.name.lowercase()}", label.ifBlank { "Untitled" } + ".")
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0 || drafts[i].submitted) return
        seq += 1
        val snap = "SNAP-${"%04d".format(40 + seq % 60)}"
        drafts[i] = drafts[i].copy(submitted = true, snapshot = snap)
        audit("You", "submitted remittance", "${drafts[i].label} sealed as $snap.")
    }

    fun undoDraft(
        id: String,
        reason: String,
    ) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0 || !drafts[i].submitted || drafts[i].undone) return
        drafts[i] = drafts[i].copy(undone = true, undoReason = reason.ifBlank { "No reason given" })
        audit("You", "undid within 48h", "${drafts[i].snapshot} reopened · reason: ${drafts[i].undoReason}.")
    }

    fun markRead(id: String) {
        val i = notices.indexOfFirst { it.id == id }
        if (i < 0) return
        notices[i] = notices[i].copy(read = !notices[i].read)
    }

    fun markAllRead() {
        for (i in notices.indices) {
            notices[i] = notices[i].copy(read = true)
        }
    }

    fun answerRelief(
        id: String,
        answer: String,
    ) {
        val i = relief.indexOfFirst { it.id == id }
        if (i < 0) return
        relief[i] = relief[i].copy(answered = answer)
        audit("You", answer.lowercase(), "${relief[i].branch} · ${relief[i].day}.")
    }

    fun askRelief(
        branch: String,
        note: String,
    ) {
        seq += 1
        relief.add(
            ZenRelief(
                "r$seq",
                ZenReliefKind.REQUEST,
                branch.ifBlank { currentBranch().name },
                "Open day",
                note.ifBlank { "Cover requested." },
            ),
        )
        audit("You", "asked for relief", branch.ifBlank { currentBranch().name } + ".")
    }

    fun clock(inside: Boolean) {
        clockedIn = inside
        audit("You", if (inside) "clocked in" else "clocked out", currentBranch().name + ".")
    }

    private fun audit(
        actor: String,
        action: String,
        detail: String,
    ) {
        audits.add(0, ZenAudit(stamp(), actor, action, detail))
    }
}
