package com.companyb.companyapp.proto.swissgrid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #793 — swiss-grid fake domain. Mirrors CONTEXT.md vocabulary, no network.

enum class SgDay { OPEN, PAST, REMITTED }

enum class SgStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class SgKind { BOOKED, WALK_IN }

enum class SgDraftKind { SESSION, PRODUCT }

enum class SgReliefKind { REQUEST, INVITE }

data class SgBranch(
    val id: String,
    val name: String,
    val kind: String,
    val place: String,
    val day: SgDay,
)

data class SgSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: SgKind,
    val status: SgStatus,
    val amount: Double,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class SgClient(
    val id: String,
    val name: String,
    val code: String,
    val gender: String,
    val age: Int,
    val pending: Int,
    val visits: Int,
    val anonymized: Boolean = false,
)

data class SgDraft(
    val id: String,
    val kind: SgDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean = false,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class SgMate(
    val name: String,
    val role: String,
    val capability: String,
    val locked: Boolean = false,
)

data class SgNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    var read: Boolean,
)

data class SgAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class SgRelief(
    val id: String,
    val kind: SgReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    var answered: String = "",
)

class SgFakeRepo {
    var email by mutableStateOf("")
    var userName by mutableStateOf("A. Practitioner")
    var branchId by mutableStateOf("ermita")
    var clockedIn by mutableStateOf(false)
    var loggedIn by mutableStateOf(false)
    var onboarded by mutableStateOf(false)

    val branches = mutableStateListOf<SgBranch>()
    val sessions = mutableStateListOf<SgSession>()
    val clients = mutableStateListOf<SgClient>()
    val drafts = mutableStateListOf<SgDraft>()
    val mates = mutableStateListOf<SgMate>()
    val notices = mutableStateListOf<SgNotice>()
    val audits = mutableStateListOf<SgAudit>()
    val relief = mutableStateListOf<SgRelief>()

    private var seq = 100
    private var tick = 4

    init {
        seed()
    }

    private fun stamp(): String {
        tick += 1
        return "09:${tick.toString().padStart(2, '0')} · Manila"
    }

    private fun audit(action: String, detail: String) {
        audits.add(0, SgAudit(stamp(), userName, action, detail))
    }

    private fun seed() {
        branches.addAll(
            listOf(
                SgBranch("ermita", "Ermita Clinic", "CLINIC", "Manila", SgDay.OPEN),
                SgBranch("tagaytay", "Tagaytay Tour", "PROVINCIAL_TOUR", "Cavite", SgDay.PAST),
                SgBranch("payatas", "Payatas Mission", "MEDICAL_MISSION", "Quezon City", SgDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                SgSession("s1", "ermita", "Mara Villanueva", "Deep Tissue 60", "10:00", SgKind.BOOKED, SgStatus.PENDING, 1200.0, "You"),
                SgSession("s2", "ermita", "Walk-in 07", "Chair 20", "11:30", SgKind.WALK_IN, SgStatus.PENDING, 350.0, "You"),
                SgSession("s3", "ermita", "Jose Ramos", "Aromatherapy 45", "08:00", SgKind.BOOKED, SgStatus.COMPLETED, 950.0, "You"),
                SgSession("s4", "tagaytay", "Lena Cruz", "Foot Ritual 30", "15:00", SgKind.BOOKED, SgStatus.NO_SHOW, 600.0, "Nadia"),
                SgSession("s5", "tagaytay", "Paolo Lim", "Deep Tissue 60", "16:30", SgKind.BOOKED, SgStatus.CANCELLED, 1200.0, "Nadia"),
                SgSession("s6", "payatas", "Ama Reyes", "Community Circle", "09:00", SgKind.BOOKED, SgStatus.COMPLETED, 0.0, "Theo"),
                SgSession("s7", "ermita", "Irene Ocampo", "Hot Stone 90", "14:00", SgKind.BOOKED, SgStatus.PENDING, 1600.0, "You"),
            ),
        )
        clients.addAll(
            listOf(
                SgClient("c1", "Mara Villanueva", "CL-0041", "F", 34, 1, 12),
                SgClient("c2", "Jose Ramos", "CL-0018", "M", 47, 0, 21),
                SgClient("c3", "Lena Cruz", "CL-0102", "F", 29, 0, 4),
                SgClient("c4", "Walk-in 07", "CL-0207", "M", 38, 1, 1),
                SgClient("c5", "Ama Reyes", "CL-0007", "F", 61, 0, 30),
            ),
        )
        drafts.addAll(
            listOf(
                SgDraft("d1", SgDraftKind.SESSION, "Ermita · session income", 4750.0, 1),
                SgDraft("d2", SgDraftKind.PRODUCT, "Recovery balm × 12", 450.0, 12),
                SgDraft("d3", SgDraftKind.SESSION, "Tagaytay · submitted batch", 8200.0, 1, submitted = true, snapshot = "SNAP-8814 · frozen"),
            ),
        )
        mates.addAll(
            listOf(
                SgMate("You", "Practitioner", "EDIT_BRANCH_DATA · home slots 1–3"),
                SgMate("R. Dizon", "Coordinator", "SUBMIT_REMITTANCE · PAST/REMITTED editor"),
                SgMate("M. Uy", "MANAGER", "MANAGE_USERS · delegate assignment"),
                SgMate("J. Tan", "Accountant", "VIEW_BRANCH_DATA · read-only all branches"),
                SgMate("New hire", "ONBOARDING", "Empty capability bundle · locked", locked = true),
            ),
        )
        notices.addAll(
            listOf(
                SgNotice("n1", "Relief granted", "R. Dizon granted your relief edit at Tagaytay Tour.", "Tagaytay Tour", "Thu", false),
                SgNotice("n2", "Session reminder", "Irene Ocampo · Hot Stone 90 at 14:00.", "Ermita Clinic", "Today", false),
                SgNotice("n3", "Snapshot sealed", "SNAP-8814 frozen for Tagaytay batch.", "Tagaytay Tour", "Mon", true),
            ),
        )
        audits.addAll(
            listOf(
                SgAudit("08:55 · Manila", "R. Dizon", "SUBMIT", "Remittance d3 submitted · SNAP-8814"),
                SgAudit("08:12 · Manila", "You", "CLOCK_IN", "Clocked in at Ermita Clinic"),
                SgAudit("07:40 · Manila", "M. Uy", "GRANT", "Relief grant · Tagaytay Tour · Thu"),
            ),
        )
        relief.addAll(
            listOf(
                SgRelief("r1", SgReliefKind.INVITE, "Tagaytay Tour", "Thu", "Branch invites you for Thu 14:00 cover."),
                SgRelief("r2", SgReliefKind.REQUEST, "Payatas Mission", "Sat", "Your ask for Sat triage cover is pending."),
            ),
        )
    }

    fun currentBranch(): SgBranch = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun branchSessions(): List<SgSession> = sessions.filter { it.branchId == branchId }

    fun pendingCount(): Int = sessions.count { it.branchId == branchId && it.status == SgStatus.PENDING && !it.voided }

    fun completedTotal(): Double = sessions.filter { it.branchId == branchId && it.status == SgStatus.COMPLETED }.sumOf { it.amount }

    fun login(address: String) {
        email = address.ifBlank { "practitioner@company.app" }
        loggedIn = true
        audit("LOGIN", "Login · $email")
    }

    fun logout() {
        audit("LOGOUT", "Logout · $email")
        loggedIn = false
        clockedIn = false
    }

    fun clock(inNow: Boolean) {
        clockedIn = inNow
        audit(if (inNow) "CLOCK_IN" else "CLOCK_OUT", "${if (inNow) "Clocked in" else "Clocked out"} · ${currentBranch().name}")
    }

    fun moveDay(day: SgDay) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i >= 0) {
            val b = branches[i]
            branches[i] = b.copy(day = day)
            audit("DAY_MOVE", "${b.name} → ${day.name}")
        }
    }

    fun setStatus(id: String, next: SgStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.kind == SgKind.WALK_IN && (next == SgStatus.NO_SHOW || next == SgStatus.CANCELLED)) return
        sessions[i] = s.copy(status = next)
        audit("STATUS", "Session ${s.id} → ${next.name}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason.ifBlank { "No reason given" })
        audit("VOID", "Session ${s.id} voided · ${sessions[i].voidReason}")
    }

    fun unvoidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit("UNVOID", "Session ${s.id} unvoided · ${reason.ifBlank { "entered in error" }}")
    }

    fun addWalkIn(name: String) {
        seq += 1
        val label = name.ifBlank { "Walk-in $seq" }
        sessions.add(0, SgSession("s$seq", branchId, label, "Chair 20", "Now", SgKind.WALK_IN, SgStatus.PENDING, 350.0, "You"))
        audit("CREATE", "Walk-in session $label · $branchId")
    }

    fun anonymize(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clients[i]
        clients[i] = c.copy(name = "Anonymized ${c.code}", anonymized = true)
        audit("ANONYMIZE", "Client ${c.code} anonymized · gender/age retained")
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0) return
        val d = drafts[i]
        seq += 1
        drafts[i] = d.copy(submitted = true, snapshot = "SNAP-$seq · frozen")
        audit("SUBMIT", "Remittance ${d.id} submitted · ${drafts[i].snapshot}")
    }

    fun undoDraft(id: String, reason: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i < 0) return
        val d = drafts[i]
        drafts[i] = d.copy(submitted = false, undone = true, undoReason = reason.ifBlank { "entered in error" }, snapshot = "")
        audit("UNDO", "Remittance ${d.id} undone <48h · ${drafts[i].undoReason}")
    }

    fun toggleNotice(id: String) {
        val n = notices.firstOrNull { it.id == id } ?: return
        n.read = !n.read
    }

    fun markAllRead() {
        notices.forEach { it.read = true }
        audit("MAIL", "Mailbox marked all read")
    }

    fun answerRelief(id: String, verdict: String) {
        val r = relief.firstOrNull { it.id == id } ?: return
        r.answered = verdict
        audit("RELIEF", "Relief $id $verdict · ${r.branch} ${r.day}")
    }

    fun askRelief(branch: String, note: String) {
        seq += 1
        val b = branch.ifBlank { currentBranch().name }
        relief.add(SgRelief("r$seq", SgReliefKind.REQUEST, b, "Today", note.ifBlank { "Cover request" }))
        audit("RELIEF_ASK", "Relief request · $b")
    }

    fun reset() {
        branches.clear()
        sessions.clear()
        clients.clear()
        drafts.clear()
        notices.clear()
        audits.clear()
        relief.clear()
        clockedIn = false
        loggedIn = true
        seed()
        audit("RESET", "Demo data reset")
    }
}
