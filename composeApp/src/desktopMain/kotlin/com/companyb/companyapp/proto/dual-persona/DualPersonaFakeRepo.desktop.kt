package com.companyb.companyapp.proto.dualpersona

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #781 — dual-persona prototype fake domain: local only, no network, no shared contracts.
// One record set; Practitioner and Coordinator lenses read the same objects.

enum class DpPersona {
    PRACTITIONER,
    COORDINATOR,
}

enum class DpDayStatus {
    OPEN,
    PAST,
    REMITTED,
}

enum class DpSessionStatus {
    PENDING,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
}

enum class DpRole {
    ONBOARDING,
    PRACTITIONER,
    COORDINATOR,
    MANAGER,
    ACCOUNTANT,
}

enum class DpBranchKind {
    CLINIC,
    PROVINCIAL_TOUR,
    MEDICAL_MISSION,
}

enum class DpCapability {
    CLOCK,
    SCHEDULE,
    SESSION_TREAT,
    SESSION_MANAGE,
    CLIENT_VIEW,
    RELIEF_REQUEST,
    RELIEF_MANAGE,
    FINANCE_VIEW,
    REMIT_SUBMIT,
    TEAM_VIEW,
    AUDIT_VIEW,
}

enum class DpRemitKind {
    SESSION,
    PRODUCT,
}

enum class DpRemitState {
    DRAFT,
    SUBMITTED,
}

enum class DpReliefKind {
    DUTY,
    INVITE,
    REQUEST,
}

enum class DpReliefState {
    OPEN,
    CLAIMED,
    DECLINED,
    APPROVED,
}

data class DpBranch(val id: String, val name: String, val kind: DpBranchKind)

data class DpUser(val id: String, val name: String, val role: DpRole, val homeBranchId: String)

data class DpDay(val id: String, val label: String, val dateLabel: String, val status: DpDayStatus, val isToday: Boolean = false)

data class DpSession(
    val id: String,
    val branchId: String,
    val dayId: String,
    val clientId: String,
    val clientName: String,
    val time: String,
    val type: String,
    val status: DpSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class DpClient(val id: String, val name: String, val contact: String, val pendingCount: Int)

data class DpRemittance(
    val kind: DpRemitKind,
    val state: DpRemitState = DpRemitState.DRAFT,
    val draftTotal: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val undoReason: String? = null,
)

data class DpNotification(val id: String, val title: String, val body: String, var read: Boolean)

data class DpAudit(val id: String, val time: String, val actor: String, val action: String)

data class DpRelief(
    val id: String,
    val branchName: String,
    val date: String,
    val shift: String,
    val kind: DpReliefKind,
    var state: DpReliefState,
    val mine: Boolean,
    val note: String,
)

class DpFakeRepo {
    var persona by mutableStateOf(DpPersona.PRACTITIONER)
    var currentUserId by mutableStateOf<String?>(null)
    var selectedBranchId by mutableStateOf("b1")
    var selectedDayId by mutableStateOf("d2")
    var clockedIn by mutableStateOf(false)
    var clockInAt by mutableStateOf("—")

    val branches = listOf(
        DpBranch("b1", "Makati Clinic", DpBranchKind.CLINIC),
        DpBranch("b2", "Cavite Tour", DpBranchKind.PROVINCIAL_TOUR),
        DpBranch("b3", "Mission Post", DpBranchKind.MEDICAL_MISSION),
    )

    val users = listOf(
        DpUser("u-onb", "J. Rivera (new hire)", DpRole.ONBOARDING, "b1"),
        DpUser("u-prac", "M. Santos", DpRole.PRACTITIONER, "b1"),
        DpUser("u-coord", "A. Villanueva", DpRole.COORDINATOR, "b1"),
        DpUser("u-mgr", "R. Cruz", DpRole.MANAGER, "b2"),
        DpUser("u-acct", "L. Tan", DpRole.ACCOUNTANT, "b1"),
    )

    val days = listOf(
        DpDay("d1", "Sat", "Sep 5", DpDayStatus.REMITTED),
        DpDay("d2", "Mon", "Sep 7", DpDayStatus.OPEN, isToday = true),
        DpDay("d3", "Tue", "Sep 8", DpDayStatus.PAST),
    )

    val sessions = mutableStateListOf(
        DpSession("S-101", "b1", "d2", "C-101", "Dela Cruz, M.", "09:00", "Deep Tissue 60m", DpSessionStatus.PENDING, 1200, false, "M. Santos"),
        DpSession("S-102", "b1", "d2", "C-102", "Aquino, J.", "10:30", "Swedish 90m", DpSessionStatus.PENDING, 1500, true, "M. Santos"),
        DpSession("S-103", "b1", "d2", "C-103", "Reyes, K.", "13:00", "Hot Stone 60m", DpSessionStatus.COMPLETED, 1400, false, "M. Santos"),
        DpSession("S-104", "b1", "d3", "C-104", "Bautista, L.", "09:30", "Foot 45m", DpSessionStatus.NO_SHOW, 800, false, "M. Santos"),
        DpSession("S-105", "b2", "d2", "C-105", "Ocampo, R.", "11:00", "Swedish 60m", DpSessionStatus.CANCELLED, 1100, false, "R. Cruz"),
        DpSession("S-106", "b1", "d2", "C-106", "Garcia, P.", "15:00", "Deep Tissue 90m", DpSessionStatus.PENDING, 1800, false, "M. Santos"),
    )

    val clients = listOf(
        DpClient("C-101", "Dela Cruz, M.", "0917-•••-4401", 1),
        DpClient("C-102", "Aquino, J.", "walk-in · no file", 0),
        DpClient("C-103", "Reyes, K.", "0918-•••-2210", 0),
        DpClient("C-104", "Bautista, L.", "0920-•••-8871", 0),
        DpClient("C-105", "Ocampo, R.", "0915-•••-3092", 0),
        DpClient("C-106", "Garcia, P.", "0927-•••-5516", 1),
    )

    val remittances = mutableStateListOf(
        DpRemittance(DpRemitKind.SESSION, draftTotal = 4100),
        DpRemittance(DpRemitKind.PRODUCT, draftTotal = 2350),
    )

    val notifications = mutableStateListOf(
        DpNotification("n1", "Relief invite", "Cavite Tour Sat shift needs cover — tap Relief to answer.", false),
        DpNotification("n2", "Remittance snapshot", "Sep 5 SESSION snapshot R-0905 posted by L. Tan.", false),
        DpNotification("n3", "No-show recorded", "S-104 marked NO_SHOW. Slot opened for walk-ins.", true),
        DpNotification("n4", "Branch day past due", "Sep 8 day is PAST — submit PRODUCT draft before remit.", true),
    )

    val audit = mutableStateListOf(
        DpAudit("a1", "08:02", "M. Santos", "Clocked in at Makati Clinic"),
        DpAudit("a2", "08:20", "A. Villanueva", "Opened Sep 7 branch day (OPEN)"),
        DpAudit("a3", "13:40", "M. Santos", "Completed S-103 Hot Stone 60m"),
        DpAudit("a4", "14:05", "L. Tan", "Posted snapshot R-0905 SESSION ₱9,800"),
    )

    val relief = mutableStateListOf(
        DpRelief("r1", "Cavite Tour", "Sat Sep 12", "09:00–15:00", DpReliefKind.INVITE, DpReliefState.OPEN, true, "Cover for R. Cruz"),
        DpRelief("r2", "Makati Clinic", "Mon Sep 7", "15:00–21:00", DpReliefKind.DUTY, DpReliefState.CLAIMED, true, "Evening cover, 2 chairs"),
        DpRelief("r3", "Mission Post", "Sun Sep 13", "08:00–12:00", DpReliefKind.REQUEST, DpReliefState.OPEN, false, "Needs 1 practitioner"),
    )

    val currentUser: DpUser? get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: DpBranch get() = branches.firstOrNull { it.id == selectedBranchId } ?: branches.first()
    val selectedDay: DpDay get() = days.firstOrNull { it.id == selectedDayId } ?: days.first()
    val branchSessions: List<DpSession> get() = sessions.filter { it.branchId == selectedBranchId && it.dayId == selectedDayId }

    fun capabilities(): Set<DpCapability> = when (persona) {
        DpPersona.PRACTITIONER -> setOf(
            DpCapability.CLOCK, DpCapability.SCHEDULE, DpCapability.SESSION_TREAT,
            DpCapability.CLIENT_VIEW, DpCapability.RELIEF_REQUEST,
        )
        DpPersona.COORDINATOR -> setOf(
            DpCapability.CLOCK, DpCapability.SCHEDULE, DpCapability.SESSION_MANAGE,
            DpCapability.CLIENT_VIEW, DpCapability.RELIEF_MANAGE,
            DpCapability.FINANCE_VIEW, DpCapability.REMIT_SUBMIT,
            DpCapability.TEAM_VIEW, DpCapability.AUDIT_VIEW,
        )
    }

    fun gatedCount(): Int = DpCapability.entries.size - capabilities().size

    fun anonymized(name: String): String {
        val parts = name.split(",")
        val initial = parts.firstOrNull()?.trim()?.firstOrNull() ?: '•'
        return "Client $initial••• (masked)"
    }

    fun log(actor: String, action: String) {
        audit.add(0, DpAudit("a${audit.size + 1}", "now", actor, action))
    }

    fun updateSession(id: String, transform: (DpSession) -> DpSession) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) sessions[i] = transform(sessions[i])
    }
}
