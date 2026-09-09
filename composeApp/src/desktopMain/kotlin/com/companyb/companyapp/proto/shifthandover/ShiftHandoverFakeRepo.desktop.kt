package com.companyb.companyapp.proto.shifthandover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #771 — shift-handover prototype fake data. Local only: no ApiClient, no Ktor, no backend.
// Every mutation appends an audit entry; mutable fields are snapshot-observable.

enum class ShSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class ShDayStatus { OPEN, PAST, REMITTED }
enum class ShOpenState { OPEN, ACKED, DONE }
enum class ShReliefState { NONE, DUTY_VIEW_ONLY, GRANTED, REQUESTED, INVITED }

data class ShUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class ShBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: ShDayStatus,
    val shiftLabel: String,
)

class ShSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    initialStatus: ShSessionStatus,
    val price: Int,
    val practitioners: String,
) {
    var status by mutableStateOf(initialStatus)
    var voided by mutableStateOf(false)
    var voidReason by mutableStateOf("")
}

class ShClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

class ShRemittance(
    val id: String,
    val flow: String,
    val branchName: String,
    val amount: Int,
    initialStage: String,
    val submittedHoursAgo: Int? = null,
) {
    var stage by mutableStateOf(initialStage)
}

class ShNotice(
    val id: String,
    val title: String,
    val body: String,
    isRead: Boolean = false,
) {
    var read by mutableStateOf(isRead)
}

class ShOpenItem(
    val id: String,
    val text: String,
    val fromShift: String,
    initialState: ShOpenState = ShOpenState.OPEN,
) {
    var state by mutableStateOf(initialState)
}

class ShHandover(
    val id: String,
    val branchName: String,
    val dayLabel: String,
    val fromCrew: String,
    val toCrew: String,
    val note: String,
    val carriedCount: Int,
    val reliefContext: String,
    isAcked: Boolean = false,
) {
    var acked by mutableStateOf(isAcked)
}

class ShReliefLine(
    val id: String,
    val kind: String,
    val who: String,
    val branchName: String,
    val day: String,
    initialState: String,
) {
    var state by mutableStateOf(initialState)
}

data class ShAudit(val seq: Int, val actor: String, val action: String)

class ShiftRepo {
    val users = listOf(
        ShUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS", "MANAGE_INVENTORY")),
        ShUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS")),
        ShUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE")),
        ShUser("u-dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA")),
        ShUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        ShBranch("b-makati", "Makati", "CLINIC", ShDayStatus.OPEN, "Day 08:00–16:00 → Night 16:00–00:00"),
        ShBranch("b-bgc", "BGC", "CLINIC", ShDayStatus.PAST, "Day 08:00–16:00 → Night 16:00–00:00"),
        ShBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", ShDayStatus.REMITTED, "Event 09:00–18:00 single shift"),
        ShBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", ShDayStatus.OPEN, "Mission 07:00–15:00 single shift"),
    )

    val sessions = mutableStateListOf(
        ShSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, ShSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        ShSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, ShSessionStatus.PENDING, 1500, "Ana Reyes"),
        ShSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, ShSessionStatus.PENDING, 1000, "Ana Reyes"),
        ShSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, ShSessionStatus.NO_SHOW, 1200, "Ben Cruz"),
        ShSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, ShSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        ShSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, ShSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        ShClient("c-01", "Maria Clara", "0917-111-0001", 0),
        ShClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        ShClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        ShClient("c-04", "Anonymized #A17", "—", 0, anonymized = true, gender = "F", age = 42),
        ShClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        ShRemittance("r-01", "SESSION", "Makati", 8450, "DRAFT"),
        ShRemittance("r-02", "PRODUCT", "Makati", 3200, "DRAFT"),
        ShRemittance("r-03", "SESSION", "BGC", 12100, "SUBMITTED", submittedHoursAgo = 5),
        ShRemittance("r-04", "PRODUCT", "Cebu Tour", 4100, "SUBMITTED", submittedHoursAgo = 60),
    )

    val notices = mutableStateListOf(
        ShNotice("n-01", "Relief granted · Makati · today", "Cara Lim granted your relief request for Makati, today. Edit access active until 04:00 Manila."),
        ShNotice("n-02", "Handover from Day shift", "Ana Reyes clocked out and left a handover: 3 open items carried to your Night shift.", isRead = true),
        ShNotice("n-03", "Remittance snapshot frozen", "SESSION remittance for BGC submitted 5h ago. Undo window closes in 43h."),
    )

    val openItems = mutableStateListOf(
        ShOpenItem("o-01", "Jose Rizal 10:00 session still PENDING — confirm before 16:00", "Day shift · Ana"),
        ShOpenItem("o-02", "Walk-in guest receipt reprint requested at desk", "Day shift · Ana"),
        ShOpenItem("o-03", "SESSION draft ₱8,450 not yet submitted", "Day shift · Ben", ShOpenState.ACKED),
    )

    val handovers = mutableStateListOf(
        ShHandover(
            "h-01", "Makati", "Today (04:00 Manila boundary)", "Day shift · Ana Reyes", "Night shift · You",
            "Busy morning, calm afternoon. s-02 still pending — client running late, call at 15:30. Drawer counted, matches.",
            3, "Ben Cruz on relief (granted) covers rooms 1–2 until close.",
        ),
    )

    val relief = mutableStateListOf(
        ShReliefLine("f-01", "DUTY", "Ben Cruz", "BGC", "today", "GRANTED — edit access"),
        ShReliefLine("f-02", "REQUEST", "You → Makati", "Makati", "tomorrow", "PENDING — broadcast to branch"),
        ShReliefLine("f-03", "INVITE", "Cara Lim → You", "BGC", "Saturday", "ACCEPTED — grant written on accept"),
    )

    val audit = mutableStateListOf(
        ShAudit(1, "Ana Reyes", "CLOCK_OUT Makati day shift + handover h-01 written (3 items carried)"),
        ShAudit(2, "Ben Cruz", "UPDATE s-02 price override ₱1,500 → ₱1,200"),
        ShAudit(3, "Cara Lim", "GRANT relief request · Makati · today"),
        ShAudit(4, "Ben Cruz", "SUBMIT SESSION remittance r-03 · BGC · snapshot frozen"),
    )

    var currentUser by mutableStateOf<ShUser?>(null)
    var currentBranchId by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var onRelief by mutableStateOf(false)
    var reliefAccess by mutableStateOf(ShReliefState.NONE)
    var handoverCount by mutableStateOf(1)
    private var auditSeq = 4

    fun branch(id: String) = branches.first { it.id == id }
    fun currentBranch() = if (currentBranchId.isBlank()) null else branch(currentBranchId)
    fun isHomeBranch() = currentUser?.homeBranchId == currentBranchId

    fun log(action: String) {
        auditSeq += 1
        audit.add(0, ShAudit(auditSeq, currentUser?.name ?: "—", action))
    }

    fun clockOut(note: String, carryRelief: Boolean): ShHandover {
        handoverCount += 1
        val carried = openItems.count { it.state != ShOpenState.DONE }
        val user = currentUser
        val h = ShHandover(
            "h-%02d".format(handoverCount), currentBranch()?.name ?: "—", "Today (04:00 Manila boundary)",
            "Outgoing · ${user?.name ?: "—"}", "Incoming · next crew", note.ifBlank { "No note left." },
            carried,
            if (carryRelief) "Relief context carried: ${relief.joinToString { "${it.who} (${it.state})" }}" else "No relief on this shift.",
        )
        handovers.add(0, h)
        notices.add(0, ShNotice("n-h$handoverCount", "Handover from ${user?.name ?: "—"}", "${user?.name} clocked out: $carried open items carried to the next shift."))
        log("CLOCK_OUT ${currentBranch()?.name} + handover ${h.id} written ($carried items carried)")
        clockedIn = false
        return h
    }

    fun pendingSessions(branchId: String) = sessions.filter { it.branchId == branchId && it.status == ShSessionStatus.PENDING }
}

fun ShSessionStatus.seal(): androidx.compose.ui.graphics.Color = when (this) {
    ShSessionStatus.PENDING -> ShColors.Tape
    ShSessionStatus.COMPLETED -> ShColors.Incoming
    ShSessionStatus.NO_SHOW -> ShColors.Outgoing
    ShSessionStatus.CANCELLED -> ShColors.Faded
}

fun ShDayStatus.banner(): androidx.compose.ui.graphics.Color = when (this) {
    ShDayStatus.OPEN -> ShColors.BannerOpen
    ShDayStatus.PAST -> ShColors.BannerPast
    ShDayStatus.REMITTED -> ShColors.BannerRemitted
}
