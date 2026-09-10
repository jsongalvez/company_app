package com.companyb.companyapp.proto.roundscart

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class RcSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class RcDayState { OPEN, PAST, REMITTED }

enum class RcRemitKind { SESSION, PRODUCT }

enum class RcRemitStatus { DRAFT, SUBMITTED }

data class RcBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class RcUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class RcSession(
    val id: String,
    val stopNo: String,
    val clientName: String,
    val branchId: String,
    val status: RcSessionStatus,
    val service: String,
    val price: Int,
    val walkIn: Boolean,
    val time: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class RcClient(
    val id: String,
    val name: String,
    val detail: String,
    val genderAge: String,
    val hasPending: Boolean = false,
    val anonymized: Boolean = false,
)

data class RcMail(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class RcAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class RcRemittance(
    val id: String,
    val kind: RcRemitKind,
    val status: RcRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshotNo: String = "",
    val note: String = "",
)

data class RcInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val state: String = "OPEN",
)

data class RcRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val state: String = "OPEN",
)

object RoundsCartFakeRepo {
    val branches = listOf(
        RcBranch("b-qc", "QC Central", "CLINIC"),
        RcBranch("b-laguna", "Laguna Tour Stop 3", "PROVINCIAL_TOUR"),
        RcBranch("b-tondo", "Tondo Medical Mission", "MEDICAL_MISSION"),
    )

    val directory = listOf(
        RcUser("u-coord", "Mara Co", "Coordinator", "b-qc"),
        RcUser("u-doc1", "Dr. Ivo Reyes", "Practitioner", "b-qc"),
        RcUser("u-doc2", "Dr. Lena Cruz", "Practitioner", "b-laguna"),
        RcUser("u-manage", "Ramon Sy", "MANAGER", "b-qc"),
        RcUser("u-acct", "Ana Lim", "Accountant", "b-qc"),
        RcUser("u-onb", "New Hire Ona", "ONBOARDING", "b-qc", onboarding = true),
    )

    val roleBoard = listOf(
        Triple("Practitioner", "3", "Owns the cart route — chart at hand, next Client first."),
        Triple("Coordinator", "1", "Counts the cart drawer, seals SESSION + PRODUCT."),
        Triple("MANAGER", "1", "Coordinator powers plus user management and delegates."),
        Triple("Accountant", "1", "Read-only across all Branches, no edits."),
        Triple("ONBOARDING", "1", "Zero capabilities — locked cart, nothing pushes."),
    )

    val sessions = mutableStateListOf(
        RcSession(
            "s-101", "01", "Jose Ramos", "b-qc", RcSessionStatus.PENDING, "Knee rehab", 1200,
            walkIn = false, time = "09:00",
        ),
        RcSession(
            "s-102", "02", "Maria Santos", "b-qc", RcSessionStatus.PENDING, "Back care", 1500,
            walkIn = true, time = "09:40",
        ),
        RcSession(
            "s-103", "03", "Paolo Aquino", "b-qc", RcSessionStatus.PENDING, "Shoulder circuit", 1100,
            walkIn = false, time = "10:20",
        ),
        RcSession(
            "s-104", "04", "Liza Tan", "b-qc", RcSessionStatus.COMPLETED, "Post-op gait", 1800,
            walkIn = false, time = "08:10",
        ),
        RcSession(
            "s-105", "05", "Ken Uy", "b-qc", RcSessionStatus.NO_SHOW, "Neck session", 900,
            walkIn = false, time = "08:40",
        ),
        RcSession(
            "s-106", "06", "Rosa Diaz", "b-qc", RcSessionStatus.CANCELLED, "Ankle sprain", 1000,
            walkIn = false, time = "07:50",
        ),
        RcSession(
            "s-107", "07", "Walk-in Guest 3", "b-qc", RcSessionStatus.PENDING, "Triage + eval", 800,
            walkIn = true, time = "11:00",
        ),
    )

    val clients = mutableStateListOf(
        RcClient("c-1", "Jose Ramos", "Knee rehab · 3rd visit · prefers mornings", "M · 54", hasPending = true),
        RcClient("c-2", "Maria Santos", "Back care · walk-in triage", "F · 41", hasPending = true),
        RcClient("c-3", "Paolo Aquino", "Shoulder circuit · 2nd visit", "M · 33", hasPending = true),
        RcClient("c-4", "Liza Tan", "Post-op gait · cleared for load", "F · 62"),
        RcClient("c-5", "Ken Uy", "Neck session · missed last visit", "M · 28"),
        RcClient(
            "c-6", "File 0771 (anonymized)", "PII nullified · retained for reporting", "F · 47",
            anonymized = true,
        ),
    )

    val remittances = mutableStateListOf(
        RcRemittance("r-s1", RcRemitKind.SESSION, RcRemitStatus.DRAFT, 4600, "QC Central · today"),
        RcRemittance("r-p1", RcRemitKind.PRODUCT, RcRemitStatus.DRAFT, 1800, "QC Central · today"),
        RcRemittance(
            "r-s0", RcRemitKind.SESSION, RcRemitStatus.SUBMITTED, 9200, "QC Central · yesterday",
            snapshotNo = "SN-1042",
        ),
    )

    val invites = mutableStateListOf(
        RcInvite("i-1", "Laguna Tour Stop 3", "Sat"),
        RcInvite("i-2", "Tondo Medical Mission", "Sun"),
    )

    val requests = mutableStateListOf(
        RcRequest("q-1", "Laguna Tour Stop 3", "today", mine = false),
        RcRequest("q-2", "QC Central", "today", mine = true),
    )

    val mailbox = mutableStateListOf(
        RcMail(
            "m-1", "Relief invite: Laguna Tour Stop 3 (Sat)",
            "Any branch member invited you — accept to write the day grant.", "today",
        ),
        RcMail(
            "m-2", "Session s-104 completed",
            "Liza Tan · post-op gait · ₱1800 posted to the drawer.", "today", read = true,
        ),
        RcMail(
            "m-3", "Snapshot SN-1042 sealed",
            "SESSION yesterday submitted — immutable unless undone within 48h.", "yesterday", read = true,
        ),
    )

    val audits = mutableStateListOf(
        RcAudit("a-1", "Dr. Ivo Reyes", "COMPLETE_SESSION", "s-104 Liza Tan", "08:55"),
        RcAudit("a-2", "Mara Co", "SUBMIT_REMITTANCE", "SESSION yesterday SN-1042", "09:10"),
        RcAudit("a-3", "Ramon Sy", "GRANT_RELIEF", "Laguna Tour Stop 3 · Sat", "09:20"),
    )

    val currentUser = mutableStateOf(directory[1])
    val clockedBranchId = mutableStateOf("b-qc")
    val clockedIn = mutableStateOf(false)
    val dayState = mutableStateOf(RcDayState.OPEN)
    val chartSessionId = mutableStateOf("s-101")
    val snapshotSeq = mutableStateOf(1043)

    val unreadCount: Int get() = mailbox.count { !it.read }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun waitingCount(branchId: String): Int =
        sessions.count { it.branchId == branchId && it.status == RcSessionStatus.PENDING }

    fun nextStop(): RcSession? =
        sessions.filter { it.branchId == clockedBranchId.value && it.status == RcSessionStatus.PENDING }
            .minByOrNull { it.stopNo }

    fun routeStops(): List<RcSession> =
        sessions.filter { it.branchId == clockedBranchId.value }.sortedBy { it.stopNo }

    fun nextSnapshot(): String = "SN-${snapshotSeq.value++}"

    fun stamp(actor: String, action: String, record: String, reason: String = "") {
        audits.add(0, RcAudit("a-${audits.size + 1}", actor, action, record, "now", reason))
    }

    fun markSession(id: String, status: RcSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) sessions[i] = sessions[i].copy(status = status)
    }
}
