package com.companyb.companyapp.proto.solarizedcalm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class CalmDay { OPEN, PAST, REMITTED }

enum class CalmStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class CalmKind { BOOKED, WALK_IN }

enum class CalmDraftKind { SESSION, PRODUCT }

enum class CalmReliefKind { REQUEST, INVITE }

data class CalmBranch(
    val id: String,
    val name: String,
    val place: String,
    val day: CalmDay,
)

data class CalmSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: CalmKind,
    val status: CalmStatus,
    val amount: Double,
    val practitioner: String,
    val voidReason: String = "",
)

data class CalmClient(
    val id: String,
    val name: String,
    val code: String,
    val pending: Int,
    val visits: Int,
    val note: String,
)

data class CalmDraft(
    val id: String,
    val kind: CalmDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class CalmMate(
    val name: String,
    val role: String,
    val note: String,
    val locked: Boolean = false,
)

data class CalmNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    val read: Boolean,
)

data class CalmAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class CalmRelief(
    val id: String,
    val kind: CalmReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    val answered: String = "",
)

class CalmFakeRepo {
    var email by mutableStateOf("")
    var branchId by mutableStateOf("solmar")
    var clockedIn by mutableStateOf(false)
    var dayOverride by mutableStateOf<CalmDay?>(null)

    val branches = mutableStateListOf<CalmBranch>()
    val sessions = mutableStateListOf<CalmSession>()
    val clients = mutableStateListOf<CalmClient>()
    val drafts = mutableStateListOf<CalmDraft>()
    val mates = mutableStateListOf<CalmMate>()
    val notices = mutableStateListOf<CalmNotice>()
    val audits = mutableStateListOf<CalmAudit>()
    val relief = mutableStateListOf<CalmRelief>()

    private var seq = 100
    private var tick = 10

    init {
        seed()
    }

    private fun stamp(): String {
        tick += 1
        return "Tue 08:${tick.toString().padStart(2, '0')} · Manila"
    }

    private fun seed() {
        branches.addAll(
            listOf(
                CalmBranch("solmar", "Solmar Clinic", "Makati", CalmDay.OPEN),
                CalmBranch("cove", "Cove Annex", "Taguig", CalmDay.PAST),
                CalmBranch("dune", "Dune Outreach", "Laguna", CalmDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                CalmSession("s1", "solmar", "Amara Villanueva", "Deep calm 60", "09:00", CalmKind.BOOKED, CalmStatus.PENDING, 1450.0, "R. Dalisay"),
                CalmSession("s2", "solmar", "Walk-in guest", "Chair rest 30", "09:40", CalmKind.WALK_IN, CalmStatus.PENDING, 600.0, "J. Ramos"),
                CalmSession("s3", "solmar", "Bianca Santos", "Warm stone 90", "10:30", CalmKind.BOOKED, CalmStatus.COMPLETED, 2200.0, "R. Dalisay"),
                CalmSession("s4", "solmar", "Cora Mendoza", "Breathwork 45", "11:15", CalmKind.BOOKED, CalmStatus.NO_SHOW, 950.0, "L. Aquino"),
                CalmSession("s5", "solmar", "Diana Cruz", "Scalp ease 30", "13:00", CalmKind.BOOKED, CalmStatus.CANCELLED, 750.0, "J. Ramos", "client asked to move"),
                CalmSession("s6", "cove", "Elias Torres", "Deep calm 60", "10:00", CalmKind.BOOKED, CalmStatus.COMPLETED, 1450.0, "M. Reyes"),
            ),
        )
        clients.addAll(
            listOf(
                CalmClient("c1", "Amara Villanueva", "CL-1042", 1, 12, "prefers low light, long sessions"),
                CalmClient("c2", "Bianca Santos", "CL-2088", 0, 7, "warm stone regular"),
                CalmClient("c3", "Cora Mendoza", "CL-3310", 0, 3, "first no-show, be gentle"),
                CalmClient("c4", "Diana Cruz", "CL-4156", 0, 9, "rescheduled twice this month"),
            ),
        )
        drafts.addAll(
            listOf(
                CalmDraft("d1", CalmDraftKind.SESSION, "Today sessions net", 3650.0, 1, false),
                CalmDraft("d2", CalmDraftKind.PRODUCT, "Calm oil × 3", 550.0, 3, false),
                CalmDraft("d3", CalmDraftKind.SESSION, "Yesterday sealed", 5100.0, 1, true, "SNAP-0912-A"),
            ),
        )
        mates.addAll(
            listOf(
                CalmMate("R. Dalisay", "Practitioner", "deep calm, warm stone"),
                CalmMate("J. Ramos", "Practitioner", "chair rest, scalp ease"),
                CalmMate("L. Aquino", "Coordinator", "roster + relief desk"),
                CalmMate("M. Uy", "MANAGER", "approvals + remittance"),
                CalmMate("S. Lim", "Accountant", "snapshots + undo window"),
                CalmMate("New joiner", "ONBOARDING", "locked — no Capability bundle yet", true),
            ),
        )
        notices.addAll(
            listOf(
                CalmNotice("n1", "Relief invite · Cove Annex", "L. Aquino invites you to cover 14:00–18:00.", "Cove Annex", "today", false),
                CalmNotice("n2", "Snapshot sealed", "SNAP-0912-A sealed for Dune Outreach.", "Dune Outreach", "yesterday", false),
                CalmNotice("n3", "Low-stock calm oil", "3 bottles left at Solmar Clinic.", "Solmar Clinic", "today", true),
            ),
        )
        audits.addAll(
            listOf(
                CalmAudit("Tue 08:02 · Manila", "L. Aquino", "clock-in", "Solmar Clinic opened calmly"),
                CalmAudit("Tue 08:05 · Manila", "M. Uy", "submit", "SNAP-0912-A sealed with reason handoff complete"),
            ),
        )
        relief.addAll(
            listOf(
                CalmRelief("r1", CalmReliefKind.INVITE, "Cove Annex", "today", "cover 14:00–18:00, quiet room 2"),
                CalmRelief("r2", CalmReliefKind.REQUEST, "Solmar Clinic", "today", "need evening cover 18:00–21:00", "waiting"),
            ),
        )
    }

    fun currentBranch(): CalmBranch {
        val base = branches.firstOrNull { it.id == branchId } ?: branches.first()
        val day = dayOverride ?: base.day
        return base.copy(day = day)
    }

    fun clock(inNow: Boolean) {
        clockedIn = inNow
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, if (inNow) "clock-in" else "clock-out", currentBranch().name))
    }

    fun moveDay(day: CalmDay) {
        dayOverride = day
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "branch-day", "day moved to ${day.name}"))
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(status = CalmStatus.CANCELLED, voidReason = reason.trim())
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "void", "${s.id} voided: ${reason.trim()}"))
    }

    fun unvoidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(status = CalmStatus.PENDING, voidReason = "")
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "unvoid", "${s.id} restored: ${reason.trim()}"))
    }

    fun addWalkIn(client: String, service: String) {
        seq += 1
        sessions.add(
            0,
            CalmSession("w$seq", branchId, client.ifBlank { "Walk-in guest" }, service.ifBlank { "Chair rest 30" }, "now", CalmKind.WALK_IN, CalmStatus.PENDING, 600.0, "relief desk"),
        )
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "walk-in", "$client seated without booking"))
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0) return
        val d = drafts[i]
        if (d.submitted) return
        seq += 1
        drafts[i] = d.copy(submitted = true, snapshot = "SNAP-09${seq}-S")
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "submit", "${d.label} sealed as SNAP-09$seq-S"))
    }

    fun undoDraft(id: String, reason: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val d = drafts[i]
        if (!d.submitted || d.undone) return
        drafts[i] = d.copy(undone = true, undoReason = reason.trim())
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "undo", "${d.snapshot} undone within 48h: ${reason.trim()}"))
    }

    fun addDraft(kind: CalmDraftKind, label: String, amount: Double, qty: Int) {
        seq += 1
        drafts.add(0, CalmDraft("n$seq", kind, label.ifBlank { "Untitled draft" }, amount, qty, false))
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "draft", "$label drafted (${kind.name})"))
    }

    fun answerRelief(id: String, answer: String) {
        val i = relief.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = relief[i]
        relief[i] = r.copy(answered = answer)
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "relief", "${r.kind.name.lowercase()} ${r.id} $answer"))
    }

    fun askRelief(note: String) {
        if (note.isBlank()) return
        seq += 1
        relief.add(0, CalmRelief("q$seq", CalmReliefKind.REQUEST, currentBranch().name, "today", note.trim(), "waiting"))
        audits.add(0, CalmAudit(stamp(), email.ifEmpty { "demo@solmar.ph" }, "relief", "request opened: ${note.trim()}"))
    }

    fun toggleNotice(id: String) {
        val i = notices.indexOfFirst { it.id == id }
        if (i < 0) return
        val n = notices[i]
        notices[i] = n.copy(read = !n.read)
    }

    fun markAllRead() {
        for (i in notices.indices) {
            notices[i] = notices[i].copy(read = true)
        }
    }

    fun reset() {
        branches.clear()
        sessions.clear()
        clients.clear()
        drafts.clear()
        mates.clear()
        notices.clear()
        audits.clear()
        relief.clear()
        branchId = "solmar"
        clockedIn = false
        dayOverride = null
        seed()
    }
}
