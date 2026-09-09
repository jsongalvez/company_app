package com.companyb.companyapp.proto.auditordense

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class ADSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

enum class ADDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

enum class ADBranchKind(
    val label: String,
) {
    CLINIC("CLINIC"),
    PROVINCIAL_TOUR("PROVINCIAL_TOUR"),
    MEDICAL_MISSION("MEDICAL_MISSION"),
}

enum class ADRemitKind(
    val label: String,
) {
    SESSION("SESSION"),
    PRODUCT("PRODUCT"),
}

enum class ADSubmission(
    val label: String,
) {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
}

enum class ADRole(
    val label: String,
    val summary: String,
) {
    ONBOARDING(
        "ONBOARDING",
        "Empty capability bundle — locked out of every surface until MANAGE_USERS grants a real role.",
    ),
    PRACTITIONER(
        "Practitioner",
        "Logs sessions, manages inventory, views clients. Full access at home branches and checked-in relief days.",
    ),
    COORDINATOR(
        "Coordinator",
        "Owns finance and remittance. Sole editor of PAST and REMITTED records.",
    ),
    MANAGER(
        "Manager",
        "Coordinator powers plus user management and delegate assignment.",
    ),
    ACCOUNTANT(
        "Accountant",
        "Read-only sales and data across all branches. No edit capabilities.",
    ),
}

enum class ADReliefState(
    val label: String,
) {
    OPEN("OPEN"),
    GRANTED("GRANTED"),
    DENIED("DENIED"),
    WITHDRAWN("WITHDRAWN"),
    ACCEPTED("ACCEPTED"),
    DECLINED("DECLINED"),
    REVOKED("REVOKED"),
}

data class ADUser(
    val id: String,
    val name: String,
    val role: ADRole,
    val homeBranchId: String,
    val slot: Int,
)

data class ADBranch(
    val id: String,
    val name: String,
    val kind: ADBranchKind,
)

data class ADSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val dayDate: String,
    val type: String,
    val status: ADSessionStatus,
    val price: Int,
    val practitioners: String,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class ADClient(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val phone: String,
    val anonymized: Boolean = false,
)

data class ADRemittance(
    val id: String,
    val kind: ADRemitKind,
    val branchId: String,
    val dayDate: String,
    val state: ADSubmission,
    val deductions: Int,
    val snapshotId: String? = null,
    val undoReason: String? = null,
)

data class ADProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
) {
    val total: Int get() = qty * unitPrice
}

data class ADNotification(
    val id: String,
    val title: String,
    val body: String,
    val branchId: String?,
    val dayDate: String?,
    val read: Boolean = false,
)

data class ADAuditEntry(
    val seq: Int,
    val hash: String,
    val time: String,
    val actor: String,
    val action: String,
    val table: String,
    val record: String,
    val detail: String,
)

data class ADReliefItem(
    val id: String,
    val direction: String,
    val branchId: String,
    val dayDate: String,
    val state: ADReliefState,
    val note: String,
)

class ADFakeStore {
    val users = mutableStateListOf<ADUser>()
    val branches = mutableStateListOf<ADBranch>()
    val sessions = mutableStateListOf<ADSession>()
    val clients = mutableStateListOf<ADClient>()
    val remittances = mutableStateListOf<ADRemittance>()
    val productLines = mutableStateListOf<ADProductLine>()
    val notifications = mutableStateListOf<ADNotification>()
    val audit = mutableStateListOf<ADAuditEntry>()
    val relief = mutableStateListOf<ADReliefItem>()
    val clockRoster = mutableStateListOf<String>()

    val currentUser = mutableStateOf<ADUser?>(null)
    val currentBranchId = mutableStateOf("b-manila")
    val currentDayDate = mutableStateOf("2026-09-10")
    val dayStates = mutableMapOf<String, ADDayState>()
    val clockedIn = mutableStateOf(false)
    val reliefEdit = mutableStateOf(false)
    val selectedTab = mutableStateOf(ADTab.REGISTER)

    private var auditSeq = 0
    private var clock = 0

    private fun stamp(): String {
        clock += 1
        return "09:1${clock / 10}:${clock % 10}0 PHT"
    }

    fun record(
        action: String,
        table: String,
        record: String,
        detail: String,
    ) {
        auditSeq += 1
        val actor = currentUser.value?.name ?: "signed-out"
        val hash = (auditSeq * 2654435761L).toString(16).uppercase().takeLast(8).padStart(8, '0')
        audit.add(
            0,
            ADAuditEntry(
                seq = auditSeq,
                hash = "A-$hash",
                time = stamp(),
                actor = actor,
                action = action,
                table = table,
                record = record,
                detail = detail,
            ),
        )
    }

    fun dayState(branchId: String, date: String): ADDayState =
        dayStates["$branchId|$date"] ?: ADDayState.OPEN

    fun setDay(date: String) {
        currentDayDate.value = date
        record("UPDATE", "branch_day", "${currentBranchId.value}|$date", "Operational day switched")
    }

    fun login(user: ADUser) {
        currentUser.value = user
        currentBranchId.value = user.homeBranchId
        clockedIn.value = false
        reliefEdit.value = false
        selectedTab.value = ADTab.REGISTER
        record("LOGIN", "app_user", user.id, "${user.name} signed in as ${user.role.label}")
    }

    fun logout() {
        val name = currentUser.value?.name ?: "signed-out"
        record("LOGOUT", "app_user", currentUser.value?.id ?: "-", "$name signed out")
        currentUser.value = null
        clockedIn.value = false
        reliefEdit.value = false
    }

    fun clockIn() {
        clockedIn.value = true
        record("INSERT", "attendance", currentBranchId.value, "Clocked in at ${branchName(currentBranchId.value)}")
    }

    fun clockOut() {
        clockedIn.value = false
        reliefEdit.value = false
        record("UPDATE", "attendance", currentBranchId.value, "Clocked out")
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun clientPending(clientId: String): ADSession? =
        sessions.firstOrNull { it.clientId == clientId && it.status == ADSessionStatus.PENDING && !it.voided }

    fun sessionGross(branchId: String, date: String): Int =
        sessions.filter {
            it.branchId == branchId && it.dayDate == date &&
                it.status == ADSessionStatus.COMPLETED && !it.voided
        }.sumOf { it.price }

    fun transitionSession(id: String, next: ADSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = sessions[i]
        sessions[i] = old.copy(status = next)
        record("UPDATE", "session", id, "Status ${old.status.label} → ${next.label}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = true, voidReason = reason)
        record("UPDATE", "session", id, "Voided — excluded from totals. Reason: $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = false, voidReason = null)
        record("UPDATE", "session", id, "Unvoided — restored into totals")
    }

    fun createSession(
        clientId: String,
        type: String,
        price: Int,
        walkIn: Boolean,
    ): String? {
        val client = clients.firstOrNull { it.id == clientId } ?: return "Unknown client."
        val pending = clientPending(clientId)
        if (pending != null) return "Blocked: ${client.name} already holds PENDING ${pending.id}."
        val id = "S-${1100 + sessions.size}"
        sessions.add(
            0,
            ADSession(
                id = id,
                time = "16:30",
                clientId = clientId,
                clientName = client.name,
                branchId = currentBranchId.value,
                dayDate = currentDayDate.value,
                type = type,
                status = ADSessionStatus.PENDING,
                price = price,
                practitioners = currentUser.value?.name ?: "—",
                walkIn = walkIn,
            ),
        )
        record("INSERT", "session", id, "Booked $type for ${client.name} at ₱$price")
        return null
    }

    fun anonymizeClient(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = clients[i]
        clients[i] = old.copy(name = "Anonymized ${old.id}", phone = "—", anonymized = true)
        record("UPDATE", "client", id, "Anonymized — PII nulled, gender ${old.gender} + age ${old.age} kept")
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = remittances[i]
        remittances[i] = old.copy(state = ADSubmission.SUBMITTED, snapshotId = "SNAP-${old.id}")
        record("SUBMIT", "remittance", id, "Snapshot SNAP-${old.id} frozen within 48h undo window")
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        val old = remittances[i]
        remittances[i] = old.copy(state = ADSubmission.DRAFT, snapshotId = null, undoReason = reason)
        record("UNDO", "remittance", id, "Reopened to draft, snapshot deleted. Reason: $reason")
    }

    fun bumpQty(lineId: String, delta: Int) {
        val i = productLines.indexOfFirst { it.id == lineId }
        if (i < 0) return
        val old = productLines[i]
        val next = (old.qty + delta).coerceAtLeast(0)
        productLines[i] = old.copy(qty = next)
        record("UPDATE", "product_line", lineId, "Qty ${old.qty} → $next")
    }

    fun markRead(id: String) {
        val i = notifications.indexOfFirst { it.id == id }
        if (i < 0) return
        if (!notifications[i].read) {
            notifications[i] = notifications[i].copy(read = true)
            record("READ", "notification", id, "Mailbox message marked read")
        }
    }

    fun markAllRead() {
        var count = 0
        notifications.forEachIndexed { i, n ->
            if (!n.read) {
                notifications[i] = n.copy(read = true)
                count += 1
            }
        }
        record("READ", "notification", "all", "$count messages marked read")
    }

    fun openNotification(n: ADNotification) {
        markRead(n.id)
        if (n.branchId != null) {
            currentBranchId.value = n.branchId
            if (n.dayDate != null) currentDayDate.value = n.dayDate
            selectedTab.value = ADTab.REGISTER
            record("OPEN", "branch_day", n.branchId, "Tapped through from mailbox to branch day")
        }
    }

    fun requestRelief(branchId: String, date: String) {
        val id = "RL-${100 + relief.size}"
        relief.add(ADReliefItem(id, "OUTGOING REQUEST", branchId, date, ADReliefState.OPEN, "Broadcast to branch"))
        record("INSERT", "relief_request", id, "Relief requested at ${branchName(branchId)} for $date")
    }

    fun grantRelief(id: String) {
        mutateRelief(id, ADReliefState.GRANTED, "Branch member granted edit access")
        reliefEdit.value = true
    }

    fun withdrawRelief(id: String) {
        mutateRelief(id, ADReliefState.WITHDRAWN, "Requester withdrew before clock-in")
    }

    fun acceptInvite(id: String) {
        mutateRelief(id, ADReliefState.ACCEPTED, "Invite accepted — day grant written")
    }

    fun declineInvite(id: String) {
        mutateRelief(id, ADReliefState.DECLINED, "Invite declined")
    }

    fun revokeInvite(id: String) {
        mutateRelief(id, ADReliefState.REVOKED, "Branch revoked accepted duty before clock-in")
    }

    private fun mutateRelief(id: String, next: ADReliefState, detail: String) {
        val i = relief.indexOfFirst { it.id == id }
        if (i < 0) return
        relief[i] = relief[i].copy(state = next)
        record("UPDATE", "relief", id, detail)
    }

    fun seed() {
        branches.addAll(
            listOf(
                ADBranch("b-manila", "Manila Central", ADBranchKind.CLINIC),
                ADBranch("b-cebu", "Cebu Seaside", ADBranchKind.CLINIC),
                ADBranch("b-tour", "Laguna Pop-up", ADBranchKind.PROVINCIAL_TOUR),
                ADBranch("b-mission", "Rizal Mission", ADBranchKind.MEDICAL_MISSION),
            ),
        )
        users.addAll(
            listOf(
                ADUser("u-audit", "A. Reyes", ADRole.MANAGER, "b-manila", 1),
                ADUser("u-coord", "C. Villanueva", ADRole.COORDINATOR, "b-manila", 2),
                ADUser("u-phys", "J. Santos", ADRole.PRACTITIONER, "b-manila", 3),
                ADUser("u-phys2", "M. Cruz", ADRole.PRACTITIONER, "b-cebu", 1),
                ADUser("u-acct", "R. Lim", ADRole.ACCOUNTANT, "b-manila", 4),
                ADUser("u-new", "N. Bautista", ADRole.ONBOARDING, "b-manila", 9),
            ),
        )
        clients.addAll(
            listOf(
                ADClient("c-101", "L. Fernandez", 54, "F", "0917-100-0101"),
                ADClient("c-102", "D. Ramos", 41, "M", "0917-100-0102"),
                ADClient("c-103", "S. Aquino", 63, "F", "0917-100-0103"),
                ADClient("c-104", "P. Navarro", 37, "M", "0917-100-0104"),
                ADClient("c-105", "G. Torres", 48, "F", "0917-100-0105"),
                ADClient("c-106", "Anonymized c-106", 52, "M", "—", anonymized = true),
            ),
        )
        sessions.addAll(
            listOf(
                ADSession("S-1001", "08:00", "c-101", "L. Fernandez", "b-manila", "2026-09-10",
                    "REHAB", ADSessionStatus.COMPLETED, 1200, "J. Santos", false),
                ADSession("S-1002", "09:00", "c-102", "D. Ramos", "b-manila", "2026-09-10",
                    "FOLLOW-UP", ADSessionStatus.COMPLETED, 900, "J. Santos", true),
                ADSession("S-1003", "10:00", "c-103", "S. Aquino", "b-manila", "2026-09-10",
                    "INITIAL", ADSessionStatus.PENDING, 1500, "M. Cruz", false),
                ADSession("S-1004", "11:00", "c-104", "P. Navarro", "b-manila", "2026-09-10",
                    "FOLLOW-UP", ADSessionStatus.PENDING, 900, "J. Santos", true),
                ADSession("S-1005", "13:00", "c-105", "G. Torres", "b-manila", "2026-09-09",
                    "REHAB", ADSessionStatus.NO_SHOW, 1200, "J. Santos", false),
                ADSession("S-1006", "14:00", "c-102", "D. Ramos", "b-manila", "2026-09-09",
                    "REHAB", ADSessionStatus.CANCELLED, 1200, "M. Cruz", false),
                ADSession("S-1007", "15:00", "c-106", "Anonymized c-106", "b-manila", "2026-09-09",
                    "FOLLOW-UP", ADSessionStatus.COMPLETED, 900, "J. Santos", false,
                    voided = true, voidReason = "Duplicate entry"),
            ),
        )
        remittances.addAll(
            listOf(
                ADRemittance("R-201", ADRemitKind.SESSION, "b-manila", "2026-09-10", ADSubmission.DRAFT, 450),
                ADRemittance("R-202", ADRemitKind.SESSION, "b-manila", "2026-09-09", ADSubmission.SUBMITTED,
                    600, snapshotId = "SNAP-R-202"),
                ADRemittance("R-203", ADRemitKind.PRODUCT, "b-manila", "2026-09-10", ADSubmission.DRAFT, 0),
            ),
        )
        productLines.addAll(
            listOf(
                ADProductLine("p-1", "Herbal compress 500g", 6, 350),
                ADProductLine("p-2", "Support bandage", 12, 180),
                ADProductLine("p-3", "Liniment 120ml", 9, 220),
            ),
        )
        notifications.addAll(
            listOf(
                ADNotification("n-1", "Relief granted — Cebu Seaside",
                    "Cebu Seaside approved edit access for 2026-09-11. Tap to open the branch day.",
                    "b-cebu", "2026-09-11", false),
                ADNotification("n-2", "Remittance R-202 submitted",
                    "Session remittance for 2026-09-09 froze snapshot SNAP-R-202. Undo window: 48h.",
                    "b-manila", "2026-09-09", false),
                ADNotification("n-3", "Invite — Laguna Pop-up",
                    "Manila Central invited you to relief duty on 2026-09-12.", "b-tour", "2026-09-12", true),
            ),
        )
        relief.addAll(
            listOf(
                ADReliefItem("RL-01", "INCOMING INVITE", "b-tour", "2026-09-12", ADReliefState.OPEN,
                    "From Manila Central — accept to write the day grant"),
                ADReliefItem("RL-02", "OUTGOING REQUEST", "b-cebu", "2026-09-11", ADReliefState.GRANTED,
                    "Broadcast approved by a branch member"),
            ),
        )
        clockRoster.addAll(listOf("A. Reyes", "J. Santos", "C. Villanueva"))
        dayStates["b-manila|2026-09-10"] = ADDayState.OPEN
        dayStates["b-manila|2026-09-09"] = ADDayState.PAST
        dayStates["b-manila|2026-09-08"] = ADDayState.REMITTED
        record("SEED", "prototype", "auditor-dense", "Fake ledger loaded — no network, no backend")
    }
}

enum class ADTab(
    val label: String,
) {
    REGISTER("Register"),
    SESSIONS("Sessions"),
    CLIENTS("Clients"),
    FINANCE("Finance"),
    AUDIT("Audit log"),
    TEAM("Team"),
    MAILBOX("Mailbox"),
    PROFILE("Profile"),
}
