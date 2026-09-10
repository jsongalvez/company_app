package com.companyb.companyapp.proto.queuedesk

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class QdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class QdDayState { OPEN, PAST, REMITTED }

enum class QdRemitKind { SESSION, PRODUCT }

enum class QdRemitStatus { DRAFT, SUBMITTED }

data class QdBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class QdUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class QdSession(
    val id: String,
    val ticketNo: String,
    val clientName: String,
    val branchId: String,
    val status: QdSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val claimedBy: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class QdClient(
    val id: String,
    val name: String,
    val detail: String,
    val genderAge: String,
    val anonymized: Boolean = false,
)

data class QdMail(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class QdAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class QdRemittance(
    val id: String,
    val kind: QdRemitKind,
    val status: QdRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshotNo: String = "",
    val note: String = "",
)

data class QdReliefInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val state: String = "OPEN",
)

data class QdReliefRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val state: String = "OPEN",
)

object QueueDeskFakeRepo {
    val branches = listOf(
        QdBranch("b-qc", "QC Central", "CLINIC"),
        QdBranch("b-laguna", "Laguna Tour Stop 3", "PROVINCIAL_TOUR"),
        QdBranch("b-tondo", "Tondo Medical Mission", "MEDICAL_MISSION"),
    )

    val directory = listOf(
        QdUser("u-coord", "R. Dizon", "Coordinator", "b-qc"),
        QdUser("u-prac", "J. Ramos", "Practitioner", "b-qc"),
        QdUser("u-mgr", "M. Sy", "MANAGER", "b-laguna"),
        QdUser("u-acct", "L. Tan", "Accountant", "b-qc"),
        QdUser("u-onb", "New Hire", "ONBOARDING", "b-qc", onboarding = true),
    )

    val currentUser = mutableStateOf(directory[0])
    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b-qc")
    val dayState = mutableStateOf(QdDayState.OPEN)

    val sessions = mutableStateListOf(
        QdSession("s-101", "Q-101", "C. Aquino", "b-qc", QdSessionStatus.PENDING, "Follow-up", 850, walkIn = false, time = "09:00"),
        QdSession("s-102", "Q-102", "D. Cruz", "b-qc", QdSessionStatus.PENDING, "Initial", 1200, walkIn = true, time = "09:20"),
        QdSession("s-103", "Q-103", "E. Santos", "b-qc", QdSessionStatus.PENDING, "Follow-up", 850, walkIn = false, claimedBy = "R. Dizon", time = "09:40"),
        QdSession("s-104", "Q-104", "F. Reyes", "b-qc", QdSessionStatus.PENDING, "Maintenance", 700, walkIn = true, time = "10:00"),
        QdSession("s-105", "Q-105", "G. Lim", "b-qc", QdSessionStatus.COMPLETED, "Follow-up", 850, walkIn = false, claimedBy = "R. Dizon", time = "08:00"),
        QdSession("s-106", "Q-106", "H. Uy", "b-qc", QdSessionStatus.NO_SHOW, "Initial", 1200, walkIn = false, time = "08:20"),
        QdSession("s-107", "Q-107", "I. Gomez", "b-qc", QdSessionStatus.CANCELLED, "Maintenance", 700, walkIn = false, time = "08:40"),
        QdSession("s-108", "Q-108", "J. Perez", "b-laguna", QdSessionStatus.PENDING, "Initial", 1100, walkIn = false, time = "09:10"),
    )

    val clients = mutableStateListOf(
        QdClient("c-1", "C. Aquino", "Returning, knee program", "F · 41"),
        QdClient("c-2", "D. Cruz", "Walk-in, shoulder check", "M · 29"),
        QdClient("c-3", "E. Santos", "Returning, back program", "F · 55"),
        QdClient("c-4", "X. Anon-118", "Anonymized record, history kept", "M · 60", anonymized = true),
        QdClient("c-5", "F. Reyes", "Walk-in, ankle sprain", "M · 23"),
    )

    val mailbox = mutableStateListOf(
        QdMail("m-1", "Relief invite: Laguna Tour Stop 3", "Any branch member invited you for Sat duty. Accept or decline from the counter.", "Sat"),
        QdMail("m-2", "Reminder: Q-101 at 09:00", "C. Aquino is first in the triage list. Claim it to start.", "Today"),
        QdMail("m-3", "Snapshot SN-8812 sealed", "SESSION remittance for Fri sealed. Undo window runs 48h with a reason.", "Fri", read = true),
    )

    val auditTrail = mutableStateListOf(
        QdAudit("a-1", "R. Dizon", "CLOCK_IN", "Branch Day b-qc / today", "08:55"),
        QdAudit("a-2", "R. Dizon", "CLAIM", "Session Q-103", "09:35"),
        QdAudit("a-3", "M. Sy", "SUBMIT_REMITTANCE", "SESSION Fri / SN-8812", "18:02"),
    )

    val remittances = mutableStateListOf(
        QdRemittance("r-sess", QdRemitKind.SESSION, QdRemitStatus.DRAFT, 12400, "Today QC Central"),
        QdRemittance("r-prod", QdRemitKind.PRODUCT, QdRemitStatus.DRAFT, 3600, "Today QC Central"),
        QdRemittance("r-old", QdRemitKind.SESSION, QdRemitStatus.SUBMITTED, 18900, "Fri QC Central", snapshotNo = "SN-8812"),
    )

    val invites = mutableStateListOf(
        QdReliefInvite("i-1", "Laguna Tour Stop 3", "Sat"),
        QdReliefInvite("i-2", "Tondo Medical Mission", "Sun"),
    )

    val requests = mutableStateListOf(
        QdReliefRequest("q-1", "Laguna Tour Stop 3", "Sat", mine = false),
        QdReliefRequest("q-2", "QC Central", "Today", mine = true),
    )

    val team = listOf(
        Triple("Coordinator", 2, "Finance + remittance counter"),
        Triple("Practitioner", 6, "Session lanes"),
        Triple("MANAGER", 1, "Delegates + user grants"),
        Triple("Accountant", 1, "Read-only sales view"),
        Triple("ONBOARDING", 1, "Locked: 0 capabilities until MANAGE_USERS grants a role"),
    )

    private var ticketSeq = 109
    private var auditSeq = 4
    private var snapshotSeq = 8813

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun pendingCountFor(clientName: String): Int =
        sessions.count { it.clientName == clientName && it.status == QdSessionStatus.PENDING }

    fun stamp(actor: String, action: String, record: String, reason: String = "") {
        auditTrail.add(0, QdAudit("a-${auditSeq++}", actor, action, record, "now", reason))
    }

    fun nextTicket(): String = "Q-${ticketSeq++}"

    fun nextSnapshot(): String = "SN-${snapshotSeq++}"

    fun updateSession(id: String, change: (QdSession) -> QdSession) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) sessions[index] = change(sessions[index])
    }
}
