package com.companyb.companyapp.proto.captainchair

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// #825 — captain-chair prototype fake data. Local only: no ApiClient, no Ktor,
// no backend. Every mutation appends an audit entry.

enum class CcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class CcDayStatus { OPEN, PAST, REMITTED }
enum class CcReliefState { NONE, DUTY_VIEW_ONLY, GRANTED, REQUESTED, INVITED }

data class CcUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val station: String,
    val locked: Boolean = false,
)

data class CcBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayStatus: CcDayStatus,
    val watchLabel: String,
)

class CcSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    initialStatus: CcSessionStatus,
    val price: Int,
    val practitioners: String,
) {
    var status by mutableStateOf(initialStatus)
    var voided by mutableStateOf(false)
    var voidReason by mutableStateOf("")
}

class CcClient(
    val id: String,
    val name: String,
    val contact: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

class CcRemittance(
    val id: String,
    val flow: String,
    val branchName: String,
    val amount: Int,
    initialStage: String,
    val submittedHoursAgo: Int? = null,
) {
    var stage by mutableStateOf(initialStage)
}

class CcNotice(
    val id: String,
    val title: String,
    val body: String,
    isRead: Boolean = false,
) {
    var read by mutableStateOf(isRead)
}

class CcException(
    val id: String,
    val spoke: String,
    val text: String,
    cleared: Boolean = false,
) {
    var cleared by mutableStateOf(cleared)
}

class CcHandover(
    val id: String,
    val branchName: String,
    val dayLabel: String,
    val fromWatch: String,
    val toWatch: String,
    val note: String,
    val carriedCount: Int,
    val reliefContext: String,
    isAcked: Boolean = false,
) {
    var acked by mutableStateOf(isAcked)
}

class CcReliefLine(
    val id: String,
    val kind: String,
    val who: String,
    val branchName: String,
    val day: String,
    initialState: String,
) {
    var state by mutableStateOf(initialState)
}

data class CcAudit(val seq: Int, val actor: String, val action: String)

class CaptainRepo {
    val users = listOf(
        CcUser("u-ana", "Ana Reyes", "Practitioner", "b-makati", listOf("LOG_SESSIONS", "VIEW_CLIENTS"), "HELM"),
        CcUser("u-ben", "Ben Cruz", "Coordinator", "b-makati", listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS"), "PORT"),
        CcUser("u-cara", "Cara Lim", "MANAGER", "b-bgc", listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE"), "STARBOARD"),
        CcUser("u-dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA"), "HELM"),
        CcUser("u-eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), "—", locked = true),
    )

    val branches = listOf(
        CcBranch("b-makati", "Makati", "CLINIC", CcDayStatus.OPEN, "Morning watch 08:00–16:00 · Night watch 16:00–00:00"),
        CcBranch("b-bgc", "BGC", "CLINIC", CcDayStatus.PAST, "Morning watch 08:00–16:00 · Night watch 16:00–00:00"),
        CcBranch("b-cebu", "Cebu Tour", "PROVINCIAL_TOUR", CcDayStatus.REMITTED, "Single watch 09:00–18:00"),
        CcBranch("b-tondo", "Tondo Mission", "MEDICAL_MISSION", CcDayStatus.OPEN, "Single watch 07:00–15:00"),
    )

    val sessions = mutableStateListOf(
        CcSession("s-01", "09:00", "Maria Clara", "b-makati", "Follow-up", false, CcSessionStatus.COMPLETED, 1200, "Ana Reyes"),
        CcSession("s-02", "10:00", "Jose Rizal", "b-makati", "Initial", false, CcSessionStatus.PENDING, 1500, "Ana Reyes"),
        CcSession("s-03", "11:30", "Walk-in guest", "b-makati", "Walk-in", true, CcSessionStatus.PENDING, 1000, "Ana Reyes"),
        CcSession("s-04", "13:00", "Liza Soberano", "b-makati", "Follow-up", false, CcSessionStatus.NO_SHOW, 1200, "Ben Cruz"),
        CcSession("s-05", "14:30", "Nora Aunor", "b-makati", "Initial", false, CcSessionStatus.CANCELLED, 1500, "Ana Reyes"),
        CcSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, CcSessionStatus.PENDING, 1200, "Cara Lim"),
    )

    val clients = mutableStateListOf(
        CcClient("c-01", "Maria Clara", "0917-111-0001", 0),
        CcClient("c-02", "Jose Rizal", "0917-111-0002", 1),
        CcClient("c-03", "Liza Soberano", "0917-111-0003", 0),
        CcClient("c-04", "Anonymized #C19", "—", 0, anonymized = true, gender = "F", age = 42),
        CcClient("c-05", "Nora Aunor", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        CcRemittance("r-01", "SESSION", "Makati", 8450, "DRAFT"),
        CcRemittance("r-02", "PRODUCT", "Makati", 3200, "DRAFT"),
        CcRemittance("r-03", "SESSION", "BGC", 12100, "SUBMITTED", submittedHoursAgo = 5),
        CcRemittance("r-04", "PRODUCT", "Cebu Tour", 4100, "SUBMITTED", submittedHoursAgo = 60),
    )

    val notices = mutableStateListOf(
        CcNotice("n-01", "Relief granted · Makati · today", "Cara Lim granted your relief request for Makati, today. Helm edit access until 04:00 Manila."),
        CcNotice("n-02", "Handover from morning watch", "Ana Reyes clocked out and left a handover: 3 open items carried to your night watch.", isRead = true),
        CcNotice("n-03", "Remittance snapshot frozen", "SESSION remittance for BGC submitted 5h ago. Undo window closes in 43h."),
    )

    val exceptions = mutableStateListOf(
        CcException("x-01", "PORT", "s-02 Jose Rizal 10:00 still PENDING — confirm before night watch"),
        CcException("x-02", "STARBOARD", "SESSION draft ₱8,450 not yet submitted"),
        CcException("x-03", "PORT", "Walk-in guest receipt reprint requested at the helm desk"),
    )

    val handovers = mutableStateListOf(
        CcHandover(
            "h-01", "Makati", "Today (04:00 Manila boundary)", "Morning watch · Ana Reyes", "Night watch · You",
            "Busy morning, calm afternoon. s-02 still pending — client running late, call at 15:30. Drawer counted, matches.",
            3, "Ben Cruz on relief (granted) holds rooms 1–2 until close.",
        ),
    )

    val relief = mutableStateListOf(
        CcReliefLine("f-01", "DUTY", "Ben Cruz", "BGC", "today", "GRANTED — helm edit access"),
        CcReliefLine("f-02", "REQUEST", "You → Makati", "Makati", "tomorrow", "PENDING — broadcast to branch"),
        CcReliefLine("f-03", "INVITE", "Cara Lim → You", "BGC", "Saturday", "ACCEPTED — grant written on accept"),
    )

    val audit = mutableStateListOf(
        CcAudit(1, "Ana Reyes", "CLOCK_OUT Makati morning watch + handover h-01 written (3 items carried)"),
        CcAudit(2, "Ben Cruz", "UPDATE s-02 price override ₱1,500 → ₱1,200"),
        CcAudit(3, "Cara Lim", "GRANT relief request · Makati · today"),
        CcAudit(4, "Ben Cruz", "SUBMIT SESSION remittance r-03 · BGC · snapshot frozen"),
    )

    var currentUser by mutableStateOf<CcUser?>(null)
    var currentBranchId by mutableStateOf("")
    var clockedIn by mutableStateOf(false)
    var onRelief by mutableStateOf(false)
    var reliefAccess by mutableStateOf(CcReliefState.NONE)
    var handoverCount by mutableStateOf(1)
    private var auditSeq = 4
    private var noticeSeq = 3

    fun branch(id: String) = branches.first { it.id == id }
    fun currentBranch() = if (currentBranchId.isBlank()) null else branch(currentBranchId)

    fun log(action: String) {
        auditSeq += 1
        audit.add(0, CcAudit(auditSeq, currentUser?.name ?: "—", action))
    }

    fun notify(title: String, body: String) {
        noticeSeq += 1
        notices.add(0, CcNotice("n-%02d".format(noticeSeq), title, body))
    }

    fun clockOut(note: String, carryRelief: Boolean): CcHandover {
        handoverCount += 1
        val carried = exceptions.count { !it.cleared }
        val user = currentUser
        val h = CcHandover(
            "h-%02d".format(handoverCount), currentBranch()?.name ?: "—", "Today (04:00 Manila boundary)",
            "Off watch · ${user?.name ?: "—"}", "On watch · next crew", note.ifBlank { "No note left." },
            carried,
            if (carryRelief) "Relief context carried: ${relief.joinToString { "${it.who} (${it.state})" }}" else "No relief on this watch.",
        )
        handovers.add(0, h)
        notify("Handover from ${user?.name ?: "—"}", "${user?.name} clocked out: $carried open items carried to the next watch.")
        log("CLOCK_OUT ${currentBranch()?.name} + handover ${h.id} written ($carried items carried)")
        clockedIn = false
        return h
    }
}

fun CcSessionStatus.seal(): Color = when (this) {
    CcSessionStatus.PENDING -> CcColors.Brass
    CcSessionStatus.COMPLETED -> CcColors.Starboard
    CcSessionStatus.NO_SHOW -> CcColors.Port
    CcSessionStatus.CANCELLED -> CcColors.Faded
}

fun CcDayStatus.banner(): Color = when (this) {
    CcDayStatus.OPEN -> CcColors.BannerOpen
    CcDayStatus.PAST -> CcColors.BannerPast
    CcDayStatus.REMITTED -> CcColors.BannerRemitted
}

fun CcDayStatus.lamp(): Color = when (this) {
    CcDayStatus.OPEN -> CcColors.Starboard
    CcDayStatus.PAST -> CcColors.Brass
    CcDayStatus.REMITTED -> CcColors.Lantern
}
