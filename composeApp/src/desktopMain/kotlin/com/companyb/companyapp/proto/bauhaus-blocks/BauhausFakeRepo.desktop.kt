package com.companyb.companyapp.proto.bauhausblocks

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class BhDayStatus { OPEN, PAST, REMITTED }

enum class BhSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BhRemitKind { SESSION, PRODUCT }

enum class BhRemitStatus { DRAFT, SUBMITTED }

enum class BhReliefState { OPEN, GRANTED, FOLDED }

data class BhBranch(
    val id: String,
    val name: String,
    val district: String,
    val dayStatus: BhDayStatus,
    val target: Int,
    val gross: Int,
    val onShift: Int,
    val shape: BhShape,
)

data class BhUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class BhSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: BhSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class BhClient(
    val id: String,
    val code: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class BhRemit(
    val id: String,
    val branchId: String,
    val kind: BhRemitKind,
    val amount: Int,
    val status: BhRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class BhNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class BhRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: BhReliefState = BhReliefState.OPEN,
)

class BauhausFakeRepo {
    val branches = mutableStateListOf(
        BhBranch("atelier", "Atelier Central", "Red Quarter", BhDayStatus.OPEN, 90000, 71200, 6, BhShape.CIRCLE),
        BhBranch("cobalt", "Cobalt Pavilion", "Blue Quarter", BhDayStatus.OPEN, 60000, 38400, 4, BhShape.SQUARE),
        BhBranch("ochre", "Ochre Workshop", "Yellow Quarter", BhDayStatus.PAST, 45000, 42150, 3, BhShape.TRIANGLE),
        BhBranch("gridhouse", "Gridhouse Annex", "Black Grid", BhDayStatus.REMITTED, 75000, 76800, 5, BhShape.HALF),
    )
    val users = mutableStateListOf(
        BhUser("u-dir", "Mara Villanueva", "MANAGER", "atelier", clockedIn = true),
        BhUser("u-form", "Jonas Reyes", "Practitioner", "cobalt", clockedIn = true),
        BhUser("u-comp", "Lena Cruz", "Coordinator", "ochre"),
        BhUser("u-acct", "Paolo Santos", "Accountant", "atelier"),
        BhUser("u-ob", "Rina Aquino", "ONBOARDING", "cobalt", onboarding = true),
    )
    val sessions = mutableStateListOf(
        BhSession("s-101", "Client A-17", "atelier", BhSessionStatus.PENDING, "Form Study 60", 1200, walkIn = false),
        BhSession("s-102", "Walk-in 04", "atelier", BhSessionStatus.PENDING, "Drop-in 30", 600, walkIn = true),
        BhSession("s-103", "Client B-02", "atelier", BhSessionStatus.COMPLETED, "Color Bath 90", 1800, walkIn = false),
        BhSession("s-104", "Client C-11", "cobalt", BhSessionStatus.PENDING, "Grid Session 60", 1100, walkIn = false),
        BhSession("s-105", "Client D-09", "cobalt", BhSessionStatus.NO_SHOW, "Circle Work 75", 1500, walkIn = false),
        BhSession("s-106", "Client E-21", "ochre", BhSessionStatus.COMPLETED, "Open Studio", 400, walkIn = true),
        BhSession("s-107", "Client F-33", "ochre", BhSessionStatus.CANCELLED, "Color Bath 60", 1300, walkIn = false),
        BhSession("s-108", "Client G-08", "gridhouse", BhSessionStatus.COMPLETED, "Master Block 120", 2600, walkIn = false),
        BhSession("s-109", "Client H-14", "gridhouse", BhSessionStatus.PENDING, "Duo Block 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        BhClient("c-a17", "A-17", "Client A-17", "Female, 34 - prefers mornings", 1),
        BhClient("c-b02", "B-02", "Client B-02", "Male, 41 - member since 2024", 0),
        BhClient("c-c11", "C-11", "Client C-11", "Female, 28 - Cobalt regular", 1),
        BhClient("c-d09", "D-09", "Client D-09", "Male, 52 - two past no-shows", 0),
        BhClient("c-g08", "G-08", "Client G-08", "Female, 47 - Gridhouse member", 0),
    )
    val remits = mutableStateListOf(
        BhRemit("r-atl-s", "atelier", BhRemitKind.SESSION, 71200, BhRemitStatus.DRAFT),
        BhRemit("r-atl-p", "atelier", BhRemitKind.PRODUCT, 9800, BhRemitStatus.DRAFT),
        BhRemit("r-grd-s", "gridhouse", BhRemitKind.SESSION, 76800, BhRemitStatus.SUBMITTED,
            snapshot = "SNAP-GRD-0117 sealed 06:12"),
        BhRemit("r-grd-p", "gridhouse", BhRemitKind.PRODUCT, 12400, BhRemitStatus.SUBMITTED,
            snapshot = "SNAP-GRD-0118 sealed 06:12"),
        BhRemit("r-cob-s", "cobalt", BhRemitKind.SESSION, 38400, BhRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        BhNotice("n-1", "Relief granted at Cobalt", "Jonas covers the Cobalt evening line, 18:00-22:00."),
        BhNotice("n-2", "Gridhouse snapshot sealed", "SESSION + PRODUCT sealed as SNAP-GRD-0117/0118.",
            read = true),
        BhNotice("n-3", "Ochre day closed PAST", "Submit remittance before the 04:00 boundary to remit cleanly."),
    )
    val reliefs = mutableStateListOf(
        BhRelief("rf-1", "cobalt", "Lena Cruz", "Evening line needs one practitioner, 18:00-22:00."),
        BhRelief("rf-2", "ochre", "Mara Villanueva", "Yellow Quarter Saturday wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Atelier Central",
        "08:04 Jonas clocked in at Cobalt Pavilion",
        "09:15 s-103 marked COMPLETED at Atelier",
        "10:40 Relief request opened for Cobalt evening line",
    )
    val invites = mutableStateListOf(
        "Invite sent to Jonas Reyes for Cobalt evening line - pending reply",
    )
    val meClockedIn = mutableStateOf(true)
    val branchFilter = mutableStateOf("atelier")
    val anonymized = mutableStateOf(false)
    val seq = mutableStateOf(200)

    fun currentBranch(): BhBranch = branches.firstOrNull { it.id == branchFilter.value } ?: branches.first()

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun selectBranch(id: String) {
        branchFilter.value = id
        audit.add(0, "Viewing composition block: ${branchName(id)}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        meClockedIn.value = inNow
        val entry = if (inNow) "Clocked in at ${branchName(branchId)}" else "Clocked out at ${branchName(branchId)}"
        audit.add(0, "07:59 Mara $entry".replace("07:59 Mara Clocked", "11:02 Mara clocked"))
    }

    fun grantRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BhReliefState.GRANTED)
        audit.add(0, "Relief duty granted: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun foldRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BhReliefState.FOLDED)
        audit.add(0, "Relief duty folded: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun addReliefRequest(branchId: String, note: String) {
        seq.value += 1
        reliefs.add(0, BhRelief("rf-${seq.value}", branchId, "Mara Villanueva", note.ifBlank { "Open call" }))
        audit.add(0, "Relief request opened for ${branchName(branchId)}")
    }

    fun sendInvite(name: String, branchId: String, note: String) {
        seq.value += 1
        val line = "Invite sent to ${name.ifBlank { "open roster" }} for ${branchName(branchId)}" +
            if (note.isBlank()) " - pending reply" else " ($note) - pending reply"
        invites.add(0, line)
        audit.add(0, line)
    }

    fun setSessionStatus(id: String, status: BhSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == BhSessionStatus.NO_SHOW || status == BhSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status, voided = false, voidReason = "")
        audit.add(0, "${s.id} marked $status at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "${s.id} voided: $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit.add(0, "${s.id} unvoided, back to ${s.status}")
    }

    fun bookSession(branchId: String, type: String) {
        seq.value += 1
        sessions.add(0, BhSession("s-${seq.value}", "Walk-in ${seq.value}", branchId,
            BhSessionStatus.PENDING, type.ifBlank { "Drop-in 30" }, 600, walkIn = true))
        audit.add(0, "s-${seq.value} booked PENDING at ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        seq.value += 1
        remits[i] = r.copy(status = BhRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} sealed 11:20")
        audit.add(0, "${r.kind} draft ${r.id} submitted, snapshot SNAP-${seq.value}")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val r = remits[i]
        remits[i] = r.copy(status = BhRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "Snapshot for ${r.id} undone within 48h: $reason")
    }

    fun toggleNotice(id: String) {
        val i = notices.indexOfFirst { it.id == id }
        if (i < 0) return
        notices[i] = notices[i].copy(read = !notices[i].read)
    }

    fun markAllRead() {
        for (i in notices.indices) notices[i] = notices[i].copy(read = true)
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun flipDayStatus(branchId: String, status: BhDayStatus) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        branches[i] = branches[i].copy(dayStatus = status)
        audit.add(0, "${branchName(branchId)} branch day set to $status")
    }

    fun grantPractitioner() {
        val i = users.indexOfFirst { it.id == "u-ob" }
        if (i < 0) return
        users[i] = users[i].copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino granted Practitioner from ONBOARDING")
    }

    fun resetDemo() {
        meClockedIn.value = true
        anonymized.value = false
        audit.add(0, "Demo composition reset to opening arrangement")
    }
}
