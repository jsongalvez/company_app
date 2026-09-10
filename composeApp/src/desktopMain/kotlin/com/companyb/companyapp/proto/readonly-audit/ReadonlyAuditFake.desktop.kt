package com.companyb.companyapp.proto.readonlyaudit

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class RaSessionStatus(val label: String) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

enum class RaDayState(val label: String) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

enum class RaRole(val label: String, val locked: Boolean = false) {
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("MANAGER"),
    ACCOUNTANT("Accountant"),
    ONBOARDING("ONBOARDING", locked = true),
}

enum class RaBranchKind(val label: String) {
    CLINIC("CLINIC"),
    PROVINCIAL_TOUR("PROVINCIAL_TOUR"),
    MEDICAL_MISSION("MEDICAL_MISSION"),
}

enum class RaScreen(val label: String) {
    HOME("Home"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    TEAM("Team"),
    MAIL("Mailbox"),
    AUDIT("Audit Log"),
    PROFILE("Profile"),
}

enum class RaRemitKind(val label: String) {
    SESSION("SESSION"),
    PRODUCT("PRODUCT"),
}

enum class RaReliefKind(val label: String) {
    DUTY("DUTY"),
    REQUEST("REQUEST"),
    INVITE("INVITE"),
}

data class RaUser(
    val id: String,
    val name: String,
    val role: RaRole,
    val homeBranchId: String,
    val slot: Int,
    val relief: Boolean = false,
    val capabilities: List<String> = emptyList(),
)

data class RaBranch(val id: String, val name: String, val kind: RaBranchKind)

data class RaBranchDay(val id: String, val date: String, val branchId: String, val state: RaDayState)

data class RaSession(
    val id: String,
    val clientName: String,
    val branchDayId: String,
    val status: RaSessionStatus,
    val walkIn: Boolean,
    val practitioners: String,
    val price: String,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class RaClient(
    val id: String,
    val name: String?,
    val anonCode: String?,
    val gender: String,
    val age: Int,
    val branchSeen: String,
    val hasPending: Boolean,
)

data class RaRemittance(
    val id: String,
    val kind: RaRemitKind,
    val branchDayId: String,
    val submitted: Boolean,
    val gross: String,
    val deductions: String,
    val net: String,
    val lines: List<String> = emptyList(),
    val submittedAgoHours: Int? = null,
    val snapshotId: String? = null,
)

data class RaRelief(
    val id: String,
    val kind: RaReliefKind,
    val who: String,
    val branchDay: String,
    val detail: String,
)

data class RaNotification(
    val id: String,
    val title: String,
    val body: String,
    val dayId: String?,
    var read: Boolean,
)

data class RaAuditEntry(
    val id: String,
    val table: String,
    val record: String,
    val action: String,
    val caller: String,
    val why: String,
)

class ReadonlyAuditRepo {
    val users = listOf(
        RaUser("u-acc", "R. Villanueva", RaRole.ACCOUNTANT, "b-makati", 0,
            capabilities = listOf("VIEW_BRANCH_DATA (all branches)")),
        RaUser("u-coord", "M. Santos", RaRole.COORDINATOR, "b-makati", 1,
            capabilities = listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "MANAGE_CLIENTS")),
        RaUser("u-prac", "J. Ramos", RaRole.PRACTITIONER, "b-makati", 2,
            capabilities = listOf("EDIT_BRANCH_DATA (home branches + checked-in day)")),
        RaUser("u-onb", "K. Dela Pena", RaRole.ONBOARDING, "b-makati", 0, capabilities = emptyList()),
    )

    val branches = listOf(
        RaBranch("b-makati", "Makati Clinic", RaBranchKind.CLINIC),
        RaBranch("b-cebu", "Cebu Provincial Tour", RaBranchKind.PROVINCIAL_TOUR),
        RaBranch("b-tondo", "Tondo Medical Mission", RaBranchKind.MEDICAL_MISSION),
    )

    val days = listOf(
        RaBranchDay("d-0908", "2026-09-08", "b-makati", RaDayState.REMITTED),
        RaBranchDay("d-0909", "2026-09-09", "b-makati", RaDayState.PAST),
        RaBranchDay("d-0910", "2026-09-10", "b-makati", RaDayState.OPEN),
    )

    val sessions = listOf(
        RaSession("s-101", "A. Reyes", "d-0910", RaSessionStatus.PENDING, false, "J. Ramos", "₱850"),
        RaSession("s-102", "D. Cruz", "d-0910", RaSessionStatus.PENDING, true, "J. Ramos", "₱850"),
        RaSession("s-103", "M. Lim", "d-0910", RaSessionStatus.COMPLETED, false, "J. Ramos + T. Uy", "₱1,200"),
        RaSession("s-104", "S. Ocampo", "d-0910", RaSessionStatus.COMPLETED, false, "T. Uy", "₱850",
            voided = true, voidReason = "Duplicate entry — rebooked as s-108"),
        RaSession("s-105", "P. Garcia", "d-0909", RaSessionStatus.NO_SHOW, false, "J. Ramos", "₱850"),
        RaSession("s-106", "L. Torres", "d-0909", RaSessionStatus.CANCELLED, false, "T. Uy", "₱850"),
        RaSession("s-107", "R. Aquino", "d-0908", RaSessionStatus.COMPLETED, false, "J. Ramos", "₱1,200"),
        RaSession("s-108", "S. Ocampo", "d-0910", RaSessionStatus.PENDING, false, "T. Uy", "₱850"),
    )

    val clients = listOf(
        RaClient("c-01", "A. Reyes", null, "F", 34, "Makati Clinic", hasPending = true),
        RaClient("c-02", "D. Cruz", null, "M", 41, "Makati Clinic", hasPending = true),
        RaClient("c-03", "M. Lim", null, "F", 29, "Cebu Provincial Tour", hasPending = false),
        RaClient("c-04", "S. Ocampo", null, "F", 52, "Makati Clinic", hasPending = true),
        RaClient("c-05", "P. Garcia", null, "M", 47, "Makati Clinic", hasPending = false),
        RaClient("c-06", null, "Anonymized #A-104", "M", 38, "Tondo Medical Mission", hasPending = false),
    )

    val remittances = listOf(
        RaRemittance("r-draft-s", RaRemitKind.SESSION, "d-0910", false,
            gross = "₱4,600", deductions = "₱920", net = "₱3,680"),
        RaRemittance("r-draft-p", RaRemitKind.PRODUCT, "d-0910", false,
            gross = "₱2,150", deductions = "₱0", net = "₱2,150",
            lines = listOf("Herbal balm × 6 — ₱1,200", "Heat patch × 19 — ₱950")),
        RaRemittance("r-snap-s", RaRemitKind.SESSION, "d-0908", true,
            gross = "₱7,900", deductions = "₱1,580", net = "₱6,320",
            submittedAgoHours = 6, snapshotId = "snap_9f2c41"),
        RaRemittance("r-snap-p", RaRemitKind.PRODUCT, "d-0908", true,
            gross = "₱3,400", deductions = "₱0", net = "₱3,400",
            submittedAgoHours = 72, snapshotId = "snap_77bd10",
            lines = listOf("Herbal balm × 11 — ₱2,200", "Heat patch × 24 — ₱1,200")),
    )

    val commissionPool = "₱1,140"
    val commissionCrew = listOf("J. Ramos (slot 2, home)", "T. Uy (slot 3, home)", "D. Flores (relief, sorts last)")
    val commissionNote = "Product commissions pool per Branch day and split equally among " +
        "Practitioners and Coordinators clocked in at sold_at time. " +
        "D. Flores excluded by manual override (half-day relief). " +
        "Commission sits outside remittance — it never enters the snapshot."

    val team = listOf(
        RaUser("u-coord", "M. Santos", RaRole.COORDINATOR, "b-makati", 1,
            capabilities = listOf("EDIT_BRANCH_DATA", "SUBMIT_REMITTANCE", "MANAGE_CLIENTS")),
        RaUser("u-prac", "J. Ramos", RaRole.PRACTITIONER, "b-makati", 2,
            capabilities = listOf("EDIT_BRANCH_DATA (home branches + checked-in day)")),
        RaUser("u-prac2", "T. Uy", RaRole.PRACTITIONER, "b-makati", 3,
            capabilities = listOf("EDIT_BRANCH_DATA (home branches + checked-in day)")),
        RaUser("u-relief", "D. Flores", RaRole.PRACTITIONER, "b-cebu", 9, relief = true,
            capabilities = listOf("VIEW_BRANCH_DATA (relief view-only — grant pending)")),
        RaUser("u-onb", "K. Dela Pena", RaRole.ONBOARDING, "b-makati", 0, capabilities = emptyList()),
    )

    val reliefBoard = listOf(
        RaRelief("rf-1", RaReliefKind.DUTY, "D. Flores", "Makati · 2026-09-10",
            "Clocked in as relief — view-only until a branch member grants edit access."),
        RaRelief("rf-2", RaReliefKind.REQUEST, "D. Flores", "Makati · 2026-09-11",
            "Broadcast request for edit access — any active branch member may grant or deny."),
        RaRelief("rf-3", RaReliefKind.INVITE, "T. Uy → P. Garcia", "Cebu Tour · 2026-09-12",
            "Branch-initiated invite — the invitee accepts or declines; revokable until clock-in."),
    )

    val mailbox = mutableStateListOf(
        RaNotification("n-1", "Relief request — Makati · 2026-09-11",
            "D. Flores asks for edit access. Any active branch member may grant or deny.", "d-0910", false),
        RaNotification("n-2", "Relief invite accepted — Cebu Tour · 2026-09-12",
            "P. Garcia accepted the invite from T. Uy. Day grant written.", "d-0910", false),
        RaNotification("n-3", "Session reminder — s-101",
            "A. Reyes is PENDING today at Makati Clinic.", "d-0910", false),
        RaNotification("n-4", "Remittance submitted — Makati · 2026-09-08",
            "SESSION snapshot snap_9f2c41 frozen by M. Santos.", "d-0908", true),
        RaNotification("n-5", "Void recorded — s-104",
            "T. Uy voided a COMPLETED session: duplicate entry.", "d-0910", true),
    )

    val auditTrail = listOf(
        RaAuditEntry("a-1", "remittance", "r-snap-s", "INSERT", "M. Santos", "submit SESSION 2026-09-08"),
        RaAuditEntry("a-2", "session", "s-104", "UPDATE", "T. Uy", "void: duplicate entry"),
        RaAuditEntry("a-3", "session", "s-108", "INSERT", "T. Uy", "rebook after void of s-104"),
        RaAuditEntry("a-4", "relief_grant", "rf-3", "INSERT", "T. Uy", "invite P. Garcia for 2026-09-12"),
        RaAuditEntry("a-5", "session", "s-105", "UPDATE", "J. Ramos", "mark NO_SHOW 2026-09-09"),
        RaAuditEntry("a-6", "client", "c-06", "UPDATE", "M. Santos", "anonymize: retain gender + age"),
        RaAuditEntry("a-7", "remittance", "r-snap-p", "INSERT", "M. Santos", "submit PRODUCT 2026-09-08"),
    )

    var currentBranchId by mutableStateOf("b-makati")
    var currentDayId by mutableStateOf("d-0910")
    var currentUserId by mutableStateOf("u-acc")

    val currentBranch: RaBranch get() = branches.first { it.id == currentBranchId }
    val currentDay: RaBranchDay get() = days.first { it.id == currentDayId }
    val currentUser: RaUser get() = users.first { it.id == currentUserId }
    val unreadCount: Int get() = mailbox.count { !it.read }

    fun sessionsForDay(dayId: String): List<RaSession> = sessions.filter { it.branchDayId == dayId }
    fun remittancesForDay(dayId: String): List<RaRemittance> = remittances.filter { it.branchDayId == dayId }

    fun markRead(id: String) {
        val i = mailbox.indexOfFirst { it.id == id }
        if (i >= 0) mailbox[i] = mailbox[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in mailbox.indices) mailbox[i] = mailbox[i].copy(read = true)
    }

    fun lockLine(screen: RaScreen): String = when (screen) {
        RaScreen.HOME -> "Clock-in needs an ACTIVE branch assignment — audit seats are never rostered."
        RaScreen.SESSIONS -> "Status changes need EDIT_BRANCH_DATA on an OPEN day — this seat holds VIEW only."
        RaScreen.CLIENTS -> "Anonymize needs MANAGE_CLIENTS (Coordinator) — this seat holds VIEW only."
        RaScreen.FINANCE -> "Submit and Undo need SUBMIT_REMITTANCE (Coordinator) — this seat holds VIEW only."
        RaScreen.TEAM -> "Role grants need MANAGE_USERS (MANAGER) — this seat holds VIEW only."
        RaScreen.MAIL -> "Reading is the one action this seat owns — everything here is already unlocked."
        RaScreen.AUDIT -> "The trail is append-only — nobody edits it, so there is nothing to lock."
        RaScreen.PROFILE -> "Identity actions need a rostered user — audit seats cannot clock in."
    }
}
