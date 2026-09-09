package com.companyb.companyapp.proto.warmcare

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class WarmDayStatus { OPEN, PAST, REMITTED }

enum class WarmSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class WarmRemitKind { SESSION, PRODUCT }

enum class WarmRemitStatus { DRAFT, SUBMITTED }

data class WarmBranch(
    val id: String,
    val name: String,
    val kind: String,
)

data class WarmUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class WarmSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: WarmSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class WarmClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class WarmNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class WarmAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class WarmRemittance(
    val id: String,
    val kind: WarmRemitKind,
    val status: WarmRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class WarmReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val date: String,
    val state: String,
)

object WarmCareFakeRepo {
    val branches = mutableStateListOf<WarmBranch>()
    val users = mutableStateListOf<WarmUser>()
    val sessions = mutableStateListOf<WarmSession>()
    val clients = mutableStateListOf<WarmClient>()
    val notes = mutableStateListOf<WarmNote>()
    val audits = mutableStateListOf<WarmAudit>()
    val remittances = mutableStateListOf<WarmRemittance>()
    val relief = mutableStateListOf<WarmReliefItem>()

    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b1")
    val dayStatus = mutableStateOf(WarmDayStatus.OPEN)
    val currentUserName = mutableStateOf("Maya Santos")

    init {
        reset()
    }

    fun reset() {
        branches.clear()
        branches.addAll(
            listOf(
                WarmBranch("b1", "Sunrise Clinic", "CLINIC"),
                WarmBranch("b2", "Harbor Provincial Tour", "PROVINCIAL_TOUR"),
                WarmBranch("b3", "Lingap Medical Mission", "MEDICAL_MISSION"),
            ),
        )
        users.clear()
        users.addAll(
            listOf(
                WarmUser("u1", "Maya Santos", "Practitioner", "b1"),
                WarmUser("u2", "Jose Ramos", "Coordinator", "b1"),
                WarmUser("u3", "Ana Lim", "MANAGER", "b2"),
                WarmUser("u4", "Rico Tan", "Accountant", "b1"),
                WarmUser("u5", "Lena Cruz", "ONBOARDING", "b1", onboarding = true),
            ),
        )
        sessions.clear()
        sessions.addAll(
            listOf(
                WarmSession("s1", "Rosa D.", "b1", WarmSessionStatus.PENDING, "Follow-up", 800, false, time = "09:00"),
                WarmSession(
                    "s2", "Marco P.", "b1", WarmSessionStatus.COMPLETED, "Initial", 1200, false,
                    time = "10:30",
                ),
                WarmSession(
                    "s3", "Walk-in neighbor", "b1", WarmSessionStatus.PENDING, "Walk-in", 600, true,
                    time = "11:15",
                ),
                WarmSession("s4", "Lita G.", "b1", WarmSessionStatus.NO_SHOW, "Follow-up", 800, false, time = "13:00"),
                WarmSession(
                    "s5", "Paolo V.", "b1", WarmSessionStatus.CANCELLED, "Initial", 1200, false,
                    time = "14:30",
                ),
                WarmSession("s6", "Nena R.", "b2", WarmSessionStatus.PENDING, "Follow-up", 750, false, time = "09:45"),
            ),
        )
        clients.clear()
        clients.addAll(
            listOf(
                WarmClient("c1", "Rosa D.", "F, 54 - Sunrise Clinic regular", true),
                WarmClient("c2", "Marco P.", "M, 41 - two visits this month", false),
                WarmClient("c3", "Lita G.", "F, 63 - prefers mornings", false),
                WarmClient(
                    "c4", "Anonymized record", "Record kept for reporting; personal details removed",
                    false, anonymized = true,
                ),
            ),
        )
        notes.clear()
        notes.addAll(
            listOf(
                WarmNote(
                    "n1", "Relief approved for Saturday",
                    "Sunrise Clinic welcomed you for Jun 14. See you there!", false, "Jun 14",
                ),
                WarmNote(
                    "n2", "Snapshot sealed for Jun 10",
                    "SESSION remittance submitted. Undo stays open for 48 hours.", false, "Jun 10",
                ),
                WarmNote(
                    "n3", "Welcome to the care team",
                    "Your branch assignment is set. Your coordinator will confirm your role.", true,
                ),
            ),
        )
        audits.clear()
        audits.addAll(
            listOf(
                WarmAudit("a1", "Jose Ramos", "SUBMIT", "Remittance r1", "Jun 10, 18:02", "End-of-day close"),
                WarmAudit("a2", "Maya Santos", "VOID", "Session s5", "Jun 10, 15:40", "Duplicate entry"),
                WarmAudit("a3", "Ana Lim", "GRANT", "Relief w1", "Jun 09, 11:20", "Saturday cover"),
            ),
        )
        remittances.clear()
        remittances.addAll(
            listOf(
                WarmRemittance(
                    "r1", WarmRemitKind.SESSION, WarmRemitStatus.SUBMITTED, 12400, "Jun 10",
                    "Sealed Jun 10, 18:02", "Jun 10, 18:02",
                ),
                WarmRemittance("r2", WarmRemitKind.SESSION, WarmRemitStatus.DRAFT, 3600, "Jun 11"),
                WarmRemittance("r3", WarmRemitKind.PRODUCT, WarmRemitStatus.DRAFT, 2150, "Jun 11"),
            ),
        )
        relief.clear()
        relief.addAll(
            listOf(
                WarmReliefItem("w1", "Invite", "Rico Tan", "Sunrise Clinic", "Jun 14", "Accepted"),
                WarmReliefItem(
                    "w2", "Request", "Maya Santos", "Harbor Provincial Tour", "Jun 15",
                    "Waiting for a branch member",
                ),
            ),
        )
        clockedIn.value = false
        clockedBranchId.value = "b1"
        dayStatus.value = WarmDayStatus.OPEN
        currentUserName.value = "Maya Santos"
    }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun toggleNoteRead(id: String) {
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = notes[index]
            notes[index] = current.copy(read = !current.read)
        }
    }

    fun markAllNotesRead() {
        for (index in notes.indices) {
            notes[index] = notes[index].copy(read = true)
        }
    }

    fun voidSession(id: String, reason: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = sessions[index]
            sessions[index] = current.copy(voided = true, voidReason = reason)
            audits.add(
                0,
                WarmAudit(
                    id = "a${audits.size + 1}",
                    actor = currentUserName.value,
                    action = "VOID",
                    record = "Session $id",
                    whenText = "Just now",
                    reason = reason,
                ),
            )
        }
    }

    fun unvoidSession(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = sessions[index]
            sessions[index] = current.copy(voided = false, voidReason = "")
            audits.add(
                0,
                WarmAudit(
                    id = "a${audits.size + 1}",
                    actor = currentUserName.value,
                    action = "UNVOID",
                    record = "Session $id",
                    whenText = "Just now",
                    reason = "Entered in error",
                ),
            )
        }
    }

    fun completeSession(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index >= 0) {
            sessions[index] = sessions[index].copy(status = WarmSessionStatus.COMPLETED)
        }
    }

    fun submitRemittance(id: String) {
        val index = remittances.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = remittances[index]
            remittances[index] = current.copy(
                status = WarmRemitStatus.SUBMITTED,
                snapshot = "Sealed just now - immutable P&L freeze",
                submittedAt = "Just now",
            )
            audits.add(
                0,
                WarmAudit(
                    id = "a${audits.size + 1}",
                    actor = currentUserName.value,
                    action = "SUBMIT",
                    record = "Remittance $id",
                    whenText = "Just now",
                    reason = "End-of-day close",
                ),
            )
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val index = remittances.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = remittances[index]
            remittances[index] = current.copy(status = WarmRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            audits.add(
                0,
                WarmAudit(
                    id = "a${audits.size + 1}",
                    actor = currentUserName.value,
                    action = "UNDO",
                    record = "Remittance $id",
                    whenText = "Just now",
                    reason = reason,
                ),
            )
        }
    }
}
