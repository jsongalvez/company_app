package com.companyb.companyapp.proto.blockbuilder

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class BbDayStatus { OPEN, PAST, REMITTED }

enum class BbSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BbRemitKind { SESSION, PRODUCT }

enum class BbRemitStatus { DRAFT, SUBMITTED }

enum class BbReliefState { OPEN, SNAPPED, UNSNAPPED }

data class BbBranch(
    val id: String,
    val name: String,
    val district: String,
    val dayStatus: BbDayStatus,
    val target: Int,
    val gross: Int,
    val onShift: Int,
)

data class BbUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class BbSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: BbSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class BbClient(
    val id: String,
    val code: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class BbRemit(
    val id: String,
    val branchId: String,
    val kind: BbRemitKind,
    val amount: Int,
    val status: BbRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class BbNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class BbRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: BbReliefState = BbReliefState.OPEN,
)

class BlockBuilderFakeRepo {
    val branches = mutableStateListOf(
        BbBranch("redbrick", "Redbrick Row", "Red Plate", BbDayStatus.OPEN, 90000, 71200, 6),
        BbBranch("blueblock", "Blueblock Bay", "Blue Plate", BbDayStatus.OPEN, 60000, 38400, 4),
        BbBranch("yellowyard", "Yellow Yard", "Yellow Plate", BbDayStatus.PAST, 45000, 42150, 3),
        BbBranch("baseplate", "Baseplate Barn", "Green Baseplate", BbDayStatus.REMITTED, 75000, 76800, 5),
    )
    val users = mutableStateListOf(
        BbUser("u-mgr", "Mara Villanueva", "MANAGER", "redbrick", clockedIn = true),
        BbUser("u-prac", "Jonas Reyes", "Practitioner", "blueblock", clockedIn = true),
        BbUser("u-coord", "Lena Cruz", "Coordinator", "yellowyard"),
        BbUser("u-acct", "Paolo Santos", "Accountant", "redbrick"),
        BbUser("u-ob", "Rina Aquino", "ONBOARDING", "blueblock", onboarding = true),
    )
    val sessions = mutableStateListOf(
        BbSession("b-101", "Client A-17", "redbrick", BbSessionStatus.PENDING, "Starter Stack 60", 1200, walkIn = false),
        BbSession("b-102", "Walk-in 04", "redbrick", BbSessionStatus.PENDING, "Snap Visit 30", 600, walkIn = true),
        BbSession("b-103", "Client B-02", "redbrick", BbSessionStatus.COMPLETED, "Big Build 90", 1800, walkIn = false),
        BbSession("b-104", "Client C-11", "blueblock", BbSessionStatus.PENDING, "Tower Time 60", 1100, walkIn = false),
        BbSession("b-105", "Client D-09", "blueblock", BbSessionStatus.NO_SHOW, "Arch Hour 75", 1500, walkIn = false),
        BbSession("b-106", "Client E-21", "yellowyard", BbSessionStatus.COMPLETED, "Free Stack", 400, walkIn = true),
        BbSession("b-107", "Client F-33", "yellowyard", BbSessionStatus.CANCELLED, "Big Build 60", 1300, walkIn = false),
        BbSession("b-108", "Client G-08", "baseplate", BbSessionStatus.COMPLETED, "Master Stack 120", 2600, walkIn = false),
        BbSession("b-109", "Client H-14", "baseplate", BbSessionStatus.PENDING, "Duo Build 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        BbClient("c-a17", "A-17", "Client A-17", "Female, 34 - prefers mornings", 1),
        BbClient("c-b02", "B-02", "Client B-02", "Male, 41 - stacking since 2024", 0),
        BbClient("c-c11", "C-11", "Client C-11", "Female, 28 - Blueblock regular", 1),
        BbClient("c-d09", "D-09", "Client D-09", "Male, 52 - two past no-shows", 0),
        BbClient("c-g08", "G-08", "Client G-08", "Female, 47 - Baseplate member", 0),
    )
    val remits = mutableStateListOf(
        BbRemit("r-red-s", "redbrick", BbRemitKind.SESSION, 71200, BbRemitStatus.DRAFT),
        BbRemit("r-red-p", "redbrick", BbRemitKind.PRODUCT, 9800, BbRemitStatus.DRAFT),
        BbRemit("r-base-s", "baseplate", BbRemitKind.SESSION, 76800, BbRemitStatus.SUBMITTED,
            snapshot = "SNAP-BASE-0117 stacked 06:12"),
        BbRemit("r-base-p", "baseplate", BbRemitKind.PRODUCT, 12400, BbRemitStatus.SUBMITTED,
            snapshot = "SNAP-BASE-0118 stacked 06:12"),
        BbRemit("r-blue-s", "blueblock", BbRemitKind.SESSION, 38400, BbRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        BbNotice("n-1", "Relief snapped at Blueblock", "Jonas covers the Blueblock evening stack, 18:00-22:00."),
        BbNotice("n-2", "Baseplate stack sealed", "SESSION + PRODUCT sealed as SNAP-BASE-0117/0118.", read = true),
        BbNotice("n-3", "Yellow Yard day closed PAST", "Stack the remittance before the 04:00 boundary to remit cleanly."),
    )
    val reliefs = mutableStateListOf(
        BbRelief("rf-1", "blueblock", "Lena Cruz", "Evening stack needs one practitioner, 18:00-22:00."),
        BbRelief("rf-2", "yellowyard", "Mara Villanueva", "Yellow plate Saturday wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Redbrick Row",
        "08:04 Jonas clocked in at Blueblock Bay",
        "09:15 b-103 snapped to COMPLETED at Redbrick Row",
        "10:40 Relief request stacked for Blueblock evening",
    )
    val invites = mutableStateListOf(
        "Invite snapped to Jonas Reyes for Blueblock evening stack - waiting for click",
    )
    val meClockedIn = mutableStateOf(true)
    val branchFilter = mutableStateOf("redbrick")
    val anonymized = mutableStateOf(false)
    val seq = mutableStateOf(200)

    fun currentBranch(): BbBranch = branches.firstOrNull { it.id == branchFilter.value } ?: branches.first()

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun selectBranch(id: String) {
        branchFilter.value = id
        audit.add(0, "Snapped onto build plate: ${branchName(id)}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        meClockedIn.value = inNow
        val entry = if (inNow) "clocked in at ${branchName(branchId)}" else "clocked out at ${branchName(branchId)}"
        audit.add(0, "11:02 Mara $entry")
    }

    fun snapRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BbReliefState.SNAPPED)
        audit.add(0, "Relief duty snapped on: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun unsnapRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BbReliefState.UNSNAPPED)
        audit.add(0, "Relief duty unsnapped: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun addReliefRequest(branchId: String, note: String) {
        seq.value += 1
        reliefs.add(0, BbRelief("rf-${seq.value}", branchId, "Mara Villanueva", note.ifBlank { "Open stack call" }))
        audit.add(0, "Relief request stacked for ${branchName(branchId)}")
    }

    fun sendInvite(name: String, branchId: String, note: String) {
        seq.value += 1
        val line = "Invite snapped to ${name.ifBlank { "open roster" }} for ${branchName(branchId)}" +
            if (note.isBlank()) " - waiting for click" else " ($note) - waiting for click"
        invites.add(0, line)
        audit.add(0, line)
    }

    fun setSessionStatus(id: String, status: BbSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == BbSessionStatus.NO_SHOW || status == BbSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status, voided = false, voidReason = "")
        audit.add(0, "${s.id} snapped to $status at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "${s.id} popped off (voided): $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit.add(0, "${s.id} snapped back on, status ${s.status}")
    }

    fun bookSession(branchId: String, type: String) {
        seq.value += 1
        sessions.add(0, BbSession("b-${seq.value}", "Walk-in ${seq.value}", branchId,
            BbSessionStatus.PENDING, type.ifBlank { "Snap Visit 30" }, 600, walkIn = true))
        audit.add(0, "b-${seq.value} stacked as PENDING at ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        seq.value += 1
        remits[i] = r.copy(status = BbRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} stacked 11:20")
        audit.add(0, "${r.kind} brick ${r.id} submitted, snapshot SNAP-${seq.value}")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val r = remits[i]
        remits[i] = r.copy(status = BbRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "Stack for ${r.id} popped off within 48h: $reason")
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

    fun flipDayStatus(branchId: String, status: BbDayStatus) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        branches[i] = branches[i].copy(dayStatus = status)
        audit.add(0, "${branchName(branchId)} build plate set to $status")
    }

    fun grantPractitioner() {
        val i = users.indexOfFirst { it.id == "u-ob" }
        if (i < 0) return
        users[i] = users[i].copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino snapped from ONBOARDING to Practitioner")
    }

    fun resetDemo() {
        meClockedIn.value = true
        anonymized.value = false
        audit.add(0, "Toy box reset to the opening stack")
    }
}
