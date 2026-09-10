package com.companyb.companyapp.proto.mindmapnav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MapDay { OPEN, PAST, REMITTED }

enum class MapStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class MapKind { BOOKED, WALK_IN }

enum class MapDraftKind { SESSION, PRODUCT }

enum class MapReliefKind { REQUEST, INVITE }

data class MapBranch(
    val id: String,
    val name: String,
    val place: String,
    val kind: String,
    val day: MapDay,
)

data class MapSession(
    val id: String,
    val branchId: String,
    val client: String,
    val service: String,
    val time: String,
    val kind: MapKind,
    val status: MapStatus,
    val amount: Double,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class MapClient(
    val id: String,
    val name: String,
    val code: String,
    val pending: Int,
    val visits: Int,
    val note: String,
)

data class MapDraft(
    val id: String,
    val kind: MapDraftKind,
    val label: String,
    val amount: Double,
    val qty: Int,
    val submitted: Boolean,
    val snapshot: String = "",
    val undone: Boolean = false,
    val undoReason: String = "",
)

data class MapMate(
    val name: String,
    val role: String,
    val note: String,
    val locked: Boolean = false,
)

data class MapNotice(
    val id: String,
    val title: String,
    val body: String,
    val branch: String,
    val day: String,
    val read: Boolean,
)

data class MapAudit(
    val time: String,
    val actor: String,
    val action: String,
    val detail: String,
)

data class MapRelief(
    val id: String,
    val kind: MapReliefKind,
    val branch: String,
    val day: String,
    val note: String,
    val answered: String = "",
)

class MapFakeRepo {
    var email by mutableStateOf("")
    var branchId by mutableStateOf("makati")
    var clockedIn by mutableStateOf(false)
    var anonymized by mutableStateOf(true)

    val branches = mutableStateListOf<MapBranch>()
    val sessions = mutableStateListOf<MapSession>()
    val clients = mutableStateListOf<MapClient>()
    val drafts = mutableStateListOf<MapDraft>()
    val mates = mutableStateListOf<MapMate>()
    val notices = mutableStateListOf<MapNotice>()
    val audits = mutableStateListOf<MapAudit>()
    val relief = mutableStateListOf<MapRelief>()

    private var seq = 100
    private var tick = 30

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
                MapBranch("makati", "Makati Branch", "Makati", "CLINIC", MapDay.OPEN),
                MapBranch("cebu", "Cebu Tour", "Cebu", "PROVINCIAL_TOUR", MapDay.PAST),
                MapBranch("davao", "Davao Mission", "Davao", "MEDICAL_MISSION", MapDay.REMITTED),
            ),
        )
        sessions.addAll(
            listOf(
                MapSession("S-101", "makati", "Rosa D.", "Deep Tissue 60", "09:00", MapKind.BOOKED, MapStatus.PENDING, 950.0, "J. Ramos"),
                MapSession("S-102", "makati", "Walk-in Guest", "Assessment 30", "09:40", MapKind.WALK_IN, MapStatus.PENDING, 500.0, "J. Ramos"),
                MapSession("S-103", "makati", "Miguel T.", "Rehab 45", "10:30", MapKind.BOOKED, MapStatus.COMPLETED, 800.0, "A. Cruz"),
                MapSession("S-104", "cebu", "Liza P.", "Sports 60", "11:00", MapKind.BOOKED, MapStatus.NO_SHOW, 950.0, "R. Lim"),
                MapSession("S-105", "cebu", "Kenji S.", "Stretch 30", "13:00", MapKind.BOOKED, MapStatus.CANCELLED, 500.0, "R. Lim"),
                MapSession("S-106", "davao", "Mission Guest 4", "Screening 15", "14:00", MapKind.WALK_IN, MapStatus.COMPLETED, 0.0, "A. Cruz"),
            ),
        )
        clients.addAll(
            listOf(
                MapClient("C-01", "Rosa D.", "CLI-4401", 1, 12, "Prefers mornings; left-shoulder rehab plan."),
                MapClient("C-02", "Miguel T.", "CLI-4402", 0, 8, "Marathon prep; sports track."),
                MapClient("C-03", "Liza P.", "CLI-4403", 0, 5, "Post-tour follow-up call done."),
                MapClient("C-04", "Kenji S.", "CLI-4404", 0, 3, "Visiting; needs receipt copies."),
                MapClient("C-05", "Ana V.", "CLI-4405", 0, 15, "Mission regular; anonymized in reports."),
                MapClient("C-06", "Paolo R.", "CLI-4406", 0, 1, "New intake; consent signed."),
            ),
        )
        drafts.addAll(
            listOf(
                MapDraft("D-11", MapDraftKind.SESSION, "Day session income", 2750.0, 1, false),
                MapDraft("D-12", MapDraftKind.PRODUCT, "Liniment x6", 350.0, 6, false),
                MapDraft("D-13", MapDraftKind.SESSION, "Tour session income", 1900.0, 1, true, "SNAP-8812"),
            ),
        )
        mates.addAll(
            listOf(
                MapMate("J. Ramos", "Practitioner", "Home: Makati Branch"),
                MapMate("A. Cruz", "Practitioner", "Relief at Cebu Tour today"),
                MapMate("M. Santos", "Coordinator", "Edits PAST and REMITTED records"),
                MapMate("R. Lim", "MANAGER", "Coordinator powers + user management"),
                MapMate("T. Uy", "Accountant", "Read-only across all branches"),
                MapMate("New Hire", "ONBOARDING", "Locked: empty capability bundle", locked = true),
            ),
        )
        notices.addAll(
            listOf(
                MapNotice("N-1", "Relief invite", "Cebu Tour invites you for Friday duty.", "Cebu Tour", "Today", false),
                MapNotice("N-2", "Remittance sealed", "Davao Mission snapshot SNAP-8801 submitted.", "Davao Mission", "Yesterday", true),
                MapNotice("N-3", "Void recorded", "S-099 voided with reason by M. Santos.", "Makati Branch", "Yesterday", false),
                MapNotice("N-4", "Boundary reminder", "Branch Day rolls at 04:00 Asia/Manila.", "Makati Branch", "Today", false),
                MapNotice("N-5", "New intake", "Paolo R. completed consent forms.", "Makati Branch", "Today", true),
            ),
        )
        audits.addAll(
            listOf(
                MapAudit("Tue 08:02 · Manila", "System", "seed", "Demo day opened on Makati Branch"),
                MapAudit("Tue 08:20 · Manila", "M. Santos", "submit", "Davao Mission snapshot SNAP-8801"),
            ),
        )
        relief.addAll(
            listOf(
                MapRelief("R-1", MapReliefKind.INVITE, "Cebu Tour", "Friday", "Cover two evening sessions."),
                MapRelief("R-2", MapReliefKind.REQUEST, "Davao Mission", "Saturday", "Asked to observe mission intake.", answered = ""),
            ),
        )
    }

    fun currentBranch(): MapBranch = branches.firstOrNull { it.id == branchId } ?: branches.first()

    fun branchSessions(): List<MapSession> = sessions.filter { it.branchId == branchId }

    fun unreadCount(): Int = notices.count { !it.read }

    fun pendingCount(): Int = branchSessions().count { it.status == MapStatus.PENDING }

    fun log(action: String, detail: String) {
        audits.add(0, MapAudit(stamp(), email.ifBlank { "Demo User" }, action, detail))
    }

    fun clock(inNow: Boolean) {
        clockedIn = inNow
        log(if (inNow) "clock-in" else "clock-out", currentBranch().name)
    }

    fun move(branch: MapBranch) {
        branchId = branch.id
        log("branch-select", branch.name)
    }

    fun advanceDay() {
        val branch = currentBranch()
        val next =
            when (branch.day) {
                MapDay.OPEN -> MapDay.PAST
                MapDay.PAST -> MapDay.REMITTED
                MapDay.REMITTED -> MapDay.OPEN
            }
        branches[branches.indexOf(branch)] = branch.copy(day = next)
        log("branch-day", "${branch.name} moved to $next")
    }

    fun setStatus(id: String, status: MapStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = status, voided = false, voidReason = "")
            log("session-${status.name.lowercase()}", "$id → $status")
        }
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = true, voidReason = reason)
            log("void", "$id voided: $reason")
        }
    }

    fun unvoidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = false, voidReason = "")
            log("unvoid", "$id restored: $reason")
        }
    }

    fun addSession(client: String, service: String, kind: MapKind, amount: Double) {
        seq += 1
        sessions.add(
            0,
            MapSession("S-$seq", branchId, client, service, "17:00", kind, MapStatus.PENDING, amount, "J. Ramos"),
        )
        log("session-create", "S-$seq ($client, ${kind.name})")
    }

    fun submitDraft(id: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            seq += 1
            drafts[i] = d.copy(submitted = true, snapshot = "SNAP-$seq")
            log("remit-submit", "$id sealed as SNAP-$seq")
        }
    }

    fun undoDraft(id: String, reason: String) {
        val i = drafts.indexOfFirst { it.id == id }
        if (i >= 0) {
            val d = drafts[i]
            drafts[i] = d.copy(submitted = false, undone = true, undoReason = reason, snapshot = "")
            log("remit-undo", "$id reopened: $reason")
        }
    }

    fun answerRelief(id: String, answer: String) {
        val i = relief.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = relief[i]
            relief[i] = r.copy(answered = answer)
            log("relief-$answer", "$id at ${r.branch}")
        }
    }

    fun askRelief(branch: String, day: String, note: String) {
        seq += 1
        relief.add(0, MapRelief("R-$seq", MapReliefKind.REQUEST, branch, day, note))
        log("relief-request", "$branch for $day")
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
}
