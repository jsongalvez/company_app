package com.companyb.companyapp.proto.passcounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #832 — pass-counter fake repo: the whole kitchen runs on paper tickets pinned to
// the pass. No network, no backend, no remote client. Sessions are tickets on the rail,
// COMPLETED rings the bell, voids land in the waste log.

enum class PcDayStatus { OPEN, PAST, REMITTED }

enum class PcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class PcRemitKind { SESSION, PRODUCT }

enum class PcRemitStatus { DRAFT, SUBMITTED }

enum class PcCoverState { OPEN, GRANTED, FOLDED }

data class PcBranch(
    val id: String,
    val name: String,
    val station: String,
    val dayStatus: PcDayStatus,
    val target: Int,
    val banked: Int,
    val onLine: Int,
)

data class PcUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class PcTicket(
    val id: String,
    val slip: String,
    val guest: String,
    val branchId: String,
    val fireTime: String,
    val course: String,
    val price: Int,
    val status: PcSessionStatus,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class PcGuest(
    val id: String,
    val name: String,
    val detail: String,
    val anonymized: Boolean = false,
)

data class PcRemit(
    val id: String,
    val branchId: String,
    val kind: PcRemitKind,
    val amount: Int,
    val status: PcRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
    val undoUsed: Boolean = false,
)

data class PcNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class PcCover(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: PcCoverState = PcCoverState.OPEN,
)

data class PcAudit(
    val id: String,
    val stamp: String,
    val actor: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

class PassCounterFakeRepo {
    val branches = mutableStateListOf(
        PcBranch("garde", "Garde Manger", "Cold line", PcDayStatus.OPEN, 60000, 41500, 4),
        PcBranch("grill", "Grill Station", "Hot line", PcDayStatus.OPEN, 90000, 62800, 6),
        PcBranch("tour", "Tour Cart 3", "Roaming pass", PcDayStatus.PAST, 30000, 27400, 2),
    )

    val users = mutableStateListOf(
        PcUser("u-expo", "Mara Villanueva", "MANAGER", "grill"),
        PcUser("u-line", "Jonas Reyes", "Practitioner", "garde"),
        PcUser("u-runner", "Lena Cruz", "Coordinator", "grill"),
        PcUser("u-count", "Paolo Santos", "Accountant", "garde"),
        PcUser("u-stage", "Rina Aquino", "ONBOARDING", "tour", onboarding = true),
    )

    val tickets = mutableStateListOf(
        PcTicket(
            "k-101", "K-101", "Guest A-17", "garde", "09:00",
            "Deep Tissue 60", 1200, PcSessionStatus.PENDING, false,
        ),
        PcTicket(
            "k-102", "K-102", "Walk-in 04", "garde", "09:20",
            "Foot Ritual 30", 600, PcSessionStatus.PENDING, true,
        ),
        PcTicket("k-103", "K-103", "Guest B-02", "garde", "10:00", "Aroma 90", 1800, PcSessionStatus.COMPLETED, false),
        PcTicket("k-104", "K-104", "Guest C-11", "grill", "10:30", "Swedish 60", 1100, PcSessionStatus.PENDING, false),
        PcTicket(
            "k-105", "K-105", "Guest D-09", "grill", "11:00",
            "Hot Stone 75", 1500, PcSessionStatus.NO_SHOW, false,
        ),
        PcTicket("k-106", "K-106", "Walk-in 07", "grill", "11:15", "Chair 15", 400, PcSessionStatus.COMPLETED, true),
        PcTicket("k-107", "K-107", "Guest F-33", "tour", "12:00", "Aroma 60", 1300, PcSessionStatus.CANCELLED, false),
        PcTicket(
            "k-108", "K-108", "Guest G-08", "grill", "13:00",
            "Signature 120", 2600, PcSessionStatus.COMPLETED, false,
        ),
        PcTicket("k-109", "K-109", "Guest H-14", "garde", "14:30", "Couples 90", 3200, PcSessionStatus.PENDING, false),
    )

    val guests = mutableStateListOf(
        PcGuest("g-a17", "Guest A-17", "Prefers mornings, light pressure"),
        PcGuest("g-b02", "Guest B-02", "Member since 2024, hot-stone regular"),
        PcGuest("g-c11", "Guest C-11", "Grill-station regular, gift card"),
        PcGuest("g-d09", "Guest D-09", "Two past no-shows, confirm twice"),
        PcGuest("g-g08", "Guest G-08", "Flagship member, couples bookings"),
        PcGuest("g-h14", "Guest H-14", "First visit, intake form done"),
    )

    val remits = mutableStateListOf(
        PcRemit("r-garde-s", "garde", PcRemitKind.SESSION, 41500, PcRemitStatus.DRAFT),
        PcRemit("r-garde-p", "garde", PcRemitKind.PRODUCT, 6300, PcRemitStatus.DRAFT),
        PcRemit(
            "r-grill-s", "grill", PcRemitKind.SESSION, 62800, PcRemitStatus.SUBMITTED,
            snapshot = "SNAP-GRILL-0441 sealed 18:12",
        ),
        PcRemit("r-grill-p", "grill", PcRemitKind.PRODUCT, 9400, PcRemitStatus.DRAFT),
    )

    val notices = mutableStateListOf(
        PcNotice("n1", "Bell rung 3x", "Grill station served K-106, K-108 and the tour cart special.", false),
        PcNotice("n2", "Cover granted", "Mara approved your Garde Manger cover for Sep 11.", false),
        PcNotice("n3", "Day flipped to PAST", "Tour Cart 3 closed at the 04:00 Asia/Manila boundary.", true),
    )

    val covers = mutableStateListOf(
        PcCover("v1", "garde", "Jonas Reyes", "Swap my Friday cold-line shift, family lunch.", PcCoverState.OPEN),
        PcCover("v2", "grill", "Lena Cruz", "Need a runner for the Saturday rush, 10-14h.", PcCoverState.GRANTED),
    )

    val invites = mutableStateListOf(
        PcCover("v3", "tour", "Mara Villanueva", "Stage on the tour cart Sep 12, tips split.", PcCoverState.OPEN),
    )

    val audits = mutableStateListOf(
        PcAudit("a1", "08:02", "Lena Cruz", "FIRE", "Ticket K-101 pinned to the rail", null),
        PcAudit("a2", "10:05", "Mara Villanueva", "GRANT", "Cover Garde Manger / Sep 11", null),
        PcAudit("a3", "11:00", "Jonas Reyes", "NO_SHOW", "Ticket K-105 Guest D-09", "grace window passed"),
        PcAudit("a4", "18:12", "Paolo Santos", "SUBMIT", "SESSION remit SNAP-GRILL-0441", null),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("garde")
    var serviceDate by mutableStateOf("Sep 10")
    var clockedIn by mutableStateOf(false)
    var stageGranted by mutableStateOf(false)
    var anonymizeAll by mutableStateOf(false)
    var selectedTicketId by mutableStateOf("k-101")

    private var tick = 20
    private var clockHour = 15
    private var clockMinute = 50

    val currentUser: PcUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: PcBranch get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()
    val dayStatus: PcDayStatus get() = currentBranch.dayStatus
    val selectedTicket: PcTicket? get() = tickets.firstOrNull { it.id == selectedTicketId }

    fun actorName(): String = currentUser?.name ?: "Signed out"

    fun pendingFor(branchId: String): List<PcTicket> =
        tickets.filter { it.branchId == branchId && it.status == PcSessionStatus.PENDING && !it.voided }

    fun pendingCountForGuest(guest: String): Int =
        tickets.count { it.guest == guest && it.status == PcSessionStatus.PENDING && !it.voided }

    fun bellCount(): Int = tickets.count { it.status == PcSessionStatus.COMPLETED }

    fun wasteLog(): List<PcTicket> = tickets.filter { it.voided }

    fun branchBanked(branchId: String): Int =
        tickets.filter { it.branchId == branchId && it.status == PcSessionStatus.COMPLETED }.sumOf { it.price }

    fun setDayStatus(branchId: String, status: PcDayStatus) {
        val index = branches.indexOfFirst { it.id == branchId }
        if (index >= 0) {
            val old = branches[index]
            branches[index] = old.copy(dayStatus = status)
            audit("DAY", "${old.name} -> ${status.name}", "04:00 Asia/Manila boundary")
        }
    }

    fun setTicketStatus(ticket: PcTicket, status: PcSessionStatus) {
        val index = tickets.indexOfFirst { it.id == ticket.id }
        if (index < 0) return
        tickets[index] = tickets[index].copy(status = status)
        when (status) {
            PcSessionStatus.COMPLETED -> audit("BELL", "Ticket ${ticket.slip} served", "bell rung")
            PcSessionStatus.NO_SHOW -> audit("NO_SHOW", "Ticket ${ticket.slip} ${ticket.guest}", "grace window passed")
            PcSessionStatus.CANCELLED -> audit("CANCEL", "Ticket ${ticket.slip} ${ticket.guest}", "ahead of cut-off")
            PcSessionStatus.PENDING -> audit("RE-FIRE", "Ticket ${ticket.slip} back on the rail", null)
        }
    }

    fun voidTicket(ticket: PcTicket, reason: String) {
        val index = tickets.indexOfFirst { it.id == ticket.id }
        if (index < 0) return
        tickets[index] = tickets[index].copy(voided = true, voidReason = reason)
        audit("VOID", "Ticket ${ticket.slip} ${ticket.guest}", reason)
    }

    fun unvoidTicket(ticket: PcTicket) {
        val index = tickets.indexOfFirst { it.id == ticket.id }
        if (index < 0) return
        tickets[index] = tickets[index].copy(voided = false, voidReason = "")
        audit("UNVOID", "Ticket ${ticket.slip} back from waste", null)
    }

    fun fireTicket(
        guest: String,
        course: String,
        price: Int,
        walkIn: Boolean,
    ) {
        tick += 1
        val slip = "K-1$tick"
        tickets.add(
            PcTicket(
                id = "k-live-$tick", slip = slip, guest = guest, branchId = currentBranchId,
                fireTime = stamp(), course = course, price = price,
                status = PcSessionStatus.PENDING, walkIn = walkIn,
            ),
        )
        selectedTicketId = "k-live-$tick"
        audit("FIRE", "Ticket $slip $guest pinned", if (walkIn) "walk-in" else "booked")
    }

    fun submitRemit(remit: PcRemit) {
        val index = remits.indexOfFirst { it.id == remit.id }
        if (index < 0) return
        remits[index] = remits[index].copy(
            status = PcRemitStatus.SUBMITTED,
            snapshot = "SNAP-${remit.branchId.uppercase()}-1$tick sealed ${stamp()}",
        )
        audit("SUBMIT", "${remit.kind} remit ${remit.branchId} ₱${remit.amount}", "snapshot sealed")
    }

    fun undoRemit(remit: PcRemit, reason: String) {
        val index = remits.indexOfFirst { it.id == remit.id }
        if (index < 0) return
        remits[index] = remits[index].copy(status = PcRemitStatus.DRAFT, undoReason = reason, undoUsed = true)
        audit("UNDO", "${remit.kind} remit ${remit.branchId} reopened", "$reason (within 48h)")
    }

    fun audit(
        action: String,
        target: String,
        reason: String? = null,
    ) {
        tick += 1
        audits.add(0, PcAudit("a-live-$tick", stamp(), actorName(), action, target, reason))
    }

    fun resetDemo() {
        clockedIn = false
        anonymizeAll = false
        stageGranted = false
        selectedTicketId = "k-101"
        audit("RESET", "Demo tickets re-pinned", null)
    }

    private fun stamp(): String {
        clockMinute += 3
        if (clockMinute >= 60) {
            clockMinute -= 60
            clockHour += 1
        }
        return "%02d:%02d".format(clockHour, clockMinute)
    }
}
