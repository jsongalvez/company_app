package com.companyb.companyapp.proto.listeverything

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #854 — list-everything fake data only; isolated from networking and backend.
// Plain rows plus index-copy writes so snapshot lists notify correctly.

enum class LeSessionStatus(val label: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

enum class LeDayState(val label: String) {
    OPEN("Open"),
    PAST("Past"),
    REMITTED("Remitted"),
}

enum class LeRole(val label: String) {
    ONBOARDING("Onboarding"),
    PRACTITIONER("Practitioner"),
    COORDINATOR("Coordinator"),
    MANAGER("Manager"),
    ACCOUNTANT("Accountant"),
}

enum class LeRemitKind(val label: String) {
    SESSION("Session"),
    PRODUCT("Product"),
}

enum class LeRemitState(val label: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    UNDONE("Undone"),
}

data class LeUser(
    val id: String,
    val name: String,
    val role: LeRole,
    val branch: String,
)

data class LeBranch(
    val id: String,
    val name: String,
    val kind: String,
    val dayDate: String,
)

data class LeSession(
    val id: String,
    val service: String,
    val client: String,
    val branch: String,
    val practitioner: String,
    val walkIn: Boolean,
    val amount: Int,
    val status: LeSessionStatus,
    val voided: Boolean = false,
    val voidReason: String? = null,
)

data class LeClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val homeBranch: String,
    val pendingCount: Int,
    val anonymized: Boolean = false,
)

data class LeRemittance(
    val id: String,
    val kind: LeRemitKind,
    val branchDay: String,
    val amount: Int,
    val state: LeRemitState,
    val snapshotId: String? = null,
    val undoReason: String? = null,
)

data class LePayout(
    val id: String,
    val staff: String,
    val role: String,
    val completed: Int,
    val share: Int,
    val paid: Boolean = false,
)

data class LeNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class LeAudit(
    val seq: Int,
    val stamp: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class LeInvite(
    val id: String,
    val fromBranch: String,
    val shift: String,
    val state: String = "PENDING",
)

data class LeReliefAsk(
    val id: String,
    val by: String,
    val shift: String,
    val mine: Boolean,
    val state: String = "PENDING",
)

class ListEverythingFakeRepo {
    val users = mutableStateListOf(
        LeUser("U-SAM", "Sam Rivera", LeRole.ONBOARDING, "QC Central"),
        LeUser("U-ANN", "Ann Reyes", LeRole.PRACTITIONER, "QC Central"),
        LeUser("U-JOY", "Joy Cruz", LeRole.COORDINATOR, "QC Central"),
        LeUser("U-MIA", "Mia Santos", LeRole.MANAGER, "Laguna Tour Stop 3"),
        LeUser("U-ROB", "Rob Dela Cruz", LeRole.ACCOUNTANT, "QC Central"),
    )
    val branches = mutableStateListOf(
        LeBranch("B-QC", "QC Central", "Clinic", "Thu · Sep 10, 2026"),
        LeBranch("B-LAG", "Laguna Tour Stop 3", "Provincial tour", "Thu · Sep 10, 2026"),
        LeBranch("B-TON", "Tondo Medical Mission", "Medical mission", "Fri · Sep 11, 2026"),
    )
    val sessions = mutableStateListOf(
        LeSession("S-101", "Deep-tissue massage", "Liza Mercado", "QC Central", "Ann Reyes", false, 850, LeSessionStatus.PENDING),
        LeSession("S-102", "Facial treatment", "Ara Villanueva", "QC Central", "Ann Reyes", true, 1200, LeSessionStatus.PENDING),
        LeSession("S-103", "Haircut + shave", "Mark Aquino", "QC Central", "Sam Rivera", false, 450, LeSessionStatus.COMPLETED),
        LeSession("S-104", "Hot-stone therapy", "Nadia Ramos", "Laguna Tour Stop 3", "Joy Cruz", false, 1500, LeSessionStatus.NO_SHOW),
        LeSession("S-105", "Manicure set", "Bea Lim", "QC Central", "Ann Reyes", true, 600, LeSessionStatus.CANCELLED),
        LeSession("S-106", "Back scrub", "Ken Watanabe", "Tondo Medical Mission", "Mia Santos", false, 700, LeSessionStatus.COMPLETED),
    )
    val clients = mutableStateListOf(
        LeClient("C-01", "Liza Mercado", "F", 34, "QC Central", 1),
        LeClient("C-02", "Ara Villanueva", "F", 28, "QC Central", 1),
        LeClient("C-03", "Mark Aquino", "M", 41, "QC Central", 0),
        LeClient("C-04", "Nadia Ramos", "F", 52, "Laguna Tour Stop 3", 0),
        LeClient("C-05", "Bea Lim", "F", 23, "QC Central", 0),
        LeClient("C-06", "Ken Watanabe", "M", 47, "Tondo Medical Mission", 0),
    )
    val remittances = mutableStateListOf(
        LeRemittance("R-SESS-1", LeRemitKind.SESSION, "QC Central · Sep 10", 4950, LeRemitState.DRAFT),
        LeRemittance("R-PROD-1", LeRemitKind.PRODUCT, "QC Central · Sep 10", 2300, LeRemitState.SUBMITTED, snapshotId = "SNAP-881"),
    )
    val payouts = mutableStateListOf(
        LePayout("P-1", "Ann Reyes", "Practitioner", 3, 2100),
        LePayout("P-2", "Joy Cruz", "Coordinator", 1, 900, paid = true),
        LePayout("P-3", "Sam Rivera", "Onboarding", 0, 0),
    )
    val notices = mutableStateListOf(
        LeNotice("N-1", "Relief invite", "Laguna Tour Stop 3 needs cover Thu 14:00–22:00."),
        LeNotice("N-2", "Snapshot sealed", "R-PROD-1 sealed as SNAP-881.", read = true),
        LeNotice("N-3", "Void recorded", "S-105 cancelled with reason on file."),
    )
    val invites = mutableStateListOf(
        LeInvite("I-1", "Laguna Tour Stop 3", "Thu 14:00–22:00"),
    )
    val reliefAsks = mutableStateListOf(
        LeReliefAsk("Q-1", "Joy Cruz", "Fri 09:00–17:00", mine = true),
        LeReliefAsk("Q-2", "Rob Dela Cruz", "Sat 10:00–18:00", mine = false),
    )

    var currentUserId by mutableStateOf<String?>(null)
    var currentBranchId by mutableStateOf("B-QC")
    var dayState by mutableStateOf(LeDayState.OPEN)
    var walkInCounter by mutableStateOf(107)
    private var auditSeq by mutableStateOf(0)

    val clockedIn = mutableStateMapOf<String, Boolean>()
    val audits = mutableStateListOf<LeAudit>()

    val currentUser: LeUser?
        get() = users.firstOrNull { it.id == currentUserId }
    val currentBranch: LeBranch
        get() = branches.firstOrNull { it.id == currentBranchId } ?: branches.first()

    fun actorName(): String = currentUser?.name ?: "kiosk"

    fun record(action: String, target: String, reason: String? = null) {
        auditSeq += 1
        audits.add(
            0,
            LeAudit(auditSeq, "T+$auditSeq", actorName(), action, target, reason),
        )
    }

    fun login(id: String) {
        currentUserId = id
        record("login", users.first { it.id == id }.name)
    }

    fun logout() {
        record("logout", actorName())
        currentUserId = null
    }

    fun selectBranch(id: String) {
        currentBranchId = id
        record("branch.select", branches.first { it.id == id }.name)
    }

    fun grantPractitioner(id: String) {
        val i = users.indexOfFirst { it.id == id }
        if (i < 0) return
        users[i] = users[i].copy(role = LeRole.PRACTITIONER)
        record("role.grant", "${users[i].name} → Practitioner")
    }

    fun clockToggle(id: String) {
        val on = !(clockedIn[id] ?: false)
        if (on) clockedIn[id] = true else clockedIn.remove(id)
        record(if (on) "clock.in" else "clock.out", users.first { it.id == id }.name)
    }

    fun setSessionStatus(id: String, status: LeSessionStatus): Boolean {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return true
        val s = sessions[i]
        if (s.walkIn && (status == LeSessionStatus.NO_SHOW || status == LeSessionStatus.CANCELLED)) return false
        sessions[i] = s.copy(status = status)
        record("session.${status.name.lowercase()}", "$id · ${s.service}")
        return true
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason.trim())
        record("session.void", id, reason.trim())
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = false, voidReason = null)
        record("session.unvoid", id)
    }

    fun addWalkIn() {
        val id = "S-$walkInCounter"
        walkInCounter += 1
        sessions.add(
            0,
            LeSession(id, "Walk-in consult", "Walk-in guest", currentBranch.name, actorName(), true, 500, LeSessionStatus.PENDING),
        )
        record("session.walkin", "$id · ${currentBranch.name}")
    }

    fun toggleAnonymize(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        clients[i] = clients[i].copy(anonymized = !clients[i].anonymized)
        record(if (clients[i].anonymized) "client.anonymize" else "client.reveal", id)
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0) return
        remittances[i] = remittances[i].copy(state = LeRemitState.SUBMITTED, snapshotId = "SNAP-${880 + i}")
        record("remit.submit", "$id → ${remittances[i].snapshotId}")
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        remittances[i] = remittances[i].copy(state = LeRemitState.UNDONE, undoReason = reason.trim())
        record("remit.undo", id, reason.trim())
    }

    fun markPaid(id: String, paid: Boolean) {
        val i = payouts.indexOfFirst { it.id == id }
        if (i < 0) return
        payouts[i] = payouts[i].copy(paid = paid)
        record(if (paid) "payout.paid" else "payout.reopen", payouts[i].staff)
    }

    fun setNoticeRead(id: String, read: Boolean) {
        val i = notices.indexOfFirst { it.id == id }
        if (i < 0) return
        notices[i] = notices[i].copy(read = read)
    }

    fun markAllRead() {
        for (i in notices.indices) notices[i] = notices[i].copy(read = true)
        record("mailbox.read-all", "${notices.size} notices")
    }

    fun setInvite(id: String, state: String) {
        val i = invites.indexOfFirst { it.id == id }
        if (i < 0) return
        invites[i] = invites[i].copy(state = state)
        record("relief.invite.${state.lowercase()}", "$id · ${invites[i].fromBranch}")
    }

    fun setAsk(id: String, state: String) {
        val i = reliefAsks.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefAsks[i] = reliefAsks[i].copy(state = state)
        record("relief.ask.${state.lowercase()}", "$id · ${reliefAsks[i].by}")
    }

    fun cycleDay() {
        dayState = when (dayState) {
            LeDayState.OPEN -> LeDayState.PAST
            LeDayState.PAST -> LeDayState.REMITTED
            LeDayState.REMITTED -> LeDayState.OPEN
        }
        record("branchday.cycle", "${currentBranch.name} → ${dayState.label}")
    }

    fun displayName(c: LeClient): String = if (c.anonymized) "Client ${c.id} (anonymized)" else c.name
}
