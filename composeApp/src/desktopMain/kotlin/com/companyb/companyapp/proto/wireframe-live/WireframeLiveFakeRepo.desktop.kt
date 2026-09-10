package com.companyb.companyapp.proto.wireframelive

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class WfDayStatus { OPEN, PAST, REMITTED }

enum class WfSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class WfRemitKind { SESSION, PRODUCT }

enum class WfRemitStatus { DRAFT, SUBMITTED }

enum class WfReliefState { OPEN, CLAIMED, RELEASED }

data class WfBranch(
    val id: String,
    val name: String,
    val district: String,
    val dayStatus: WfDayStatus,
    val target: Int,
    val gross: Int,
    val onShift: Int,
    val boxNo: String,
)

data class WfUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class WfSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: WfSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class WfClient(
    val id: String,
    val code: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class WfRemit(
    val id: String,
    val branchId: String,
    val kind: WfRemitKind,
    val amount: Int,
    val status: WfRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class WfNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class WfRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: WfReliefState = WfReliefState.OPEN,
)

class WireframeLiveFakeRepo {
    val branches = mutableStateListOf(
        WfBranch("ortigas", "Ortigas Frame Hall", "GRID-A", WfDayStatus.OPEN, 90000, 71200, 6, "BOX-B01"),
        WfBranch("makati", "Makati Fill Office", "GRID-B", WfDayStatus.OPEN, 60000, 38400, 4, "BOX-B02"),
        WfBranch("qc", "QC Gray Room", "GRID-C", WfDayStatus.PAST, 45000, 42150, 3, "BOX-B03"),
        WfBranch("alabang", "Alabang Box Vault", "GRID-D", WfDayStatus.REMITTED, 75000, 76800, 5, "BOX-B04"),
    )
    val users = mutableStateListOf(
        WfUser("u-mgr", "Mara Villanueva", "MANAGER", "ortigas", clockedIn = true),
        WfUser("u-prac", "Jonas Reyes", "Practitioner", "makati", clockedIn = true),
        WfUser("u-coord", "Lena Cruz", "Coordinator", "qc"),
        WfUser("u-acct", "Paolo Santos", "Accountant", "ortigas"),
        WfUser("u-ob", "Rina Aquino", "ONBOARDING", "makati", onboarding = true),
    )
    val sessions = mutableStateListOf(
        WfSession("S-101", "Client A-17", "ortigas", WfSessionStatus.PENDING, "Frame Review 60", 1200, walkIn = false),
        WfSession("S-102", "Walk-in 04", "ortigas", WfSessionStatus.PENDING, "Fill Visit 30", 600, walkIn = true),
        WfSession("S-103", "Client B-02", "ortigas", WfSessionStatus.COMPLETED, "Full Fill 90", 1800, walkIn = false),
        WfSession("S-104", "Client C-11", "makati", WfSessionStatus.PENDING, "Box Check 60", 1100, walkIn = false),
        WfSession("S-105", "Client D-09", "makati", WfSessionStatus.NO_SHOW, "Gray Hour 75", 1500, walkIn = false),
        WfSession("S-106", "Client E-21", "qc", WfSessionStatus.COMPLETED, "Walk-in Fill", 400, walkIn = true),
        WfSession("S-107", "Client F-33", "qc", WfSessionStatus.CANCELLED, "Full Fill 60", 1300, walkIn = false),
        WfSession("S-108", "Client G-08", "alabang", WfSessionStatus.COMPLETED, "Vault Fill 120", 2600, walkIn = false),
        WfSession("S-109", "Client H-14", "alabang", WfSessionStatus.PENDING, "Duo Fill 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        WfClient("c-a17", "A-17", "Client A-17", "Female, 34 - prefers mornings", 1),
        WfClient("c-b02", "B-02", "Client B-02", "Male, 41 - on file since 2024", 0),
        WfClient("c-c11", "C-11", "Client C-11", "Female, 28 - Makati regular", 1),
        WfClient("c-d09", "D-09", "Client D-09", "Male, 52 - two past no-shows", 0),
        WfClient("c-g08", "G-08", "Client G-08", "Female, 47 - Alabang member", 0),
    )
    val remits = mutableStateListOf(
        WfRemit("R-ORT-S", "ortigas", WfRemitKind.SESSION, 71200, WfRemitStatus.DRAFT),
        WfRemit("R-ORT-P", "ortigas", WfRemitKind.PRODUCT, 9800, WfRemitStatus.DRAFT),
        WfRemit("R-ALA-S", "alabang", WfRemitKind.SESSION, 76800, WfRemitStatus.SUBMITTED, snapshot = "SNAP-ALA-0117 filled 06:12"),
        WfRemit("R-ALA-P", "alabang", WfRemitKind.PRODUCT, 12400, WfRemitStatus.SUBMITTED, snapshot = "SNAP-ALA-0118 filled 06:12"),
        WfRemit("R-MAK-S", "makati", WfRemitKind.SESSION, 38400, WfRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        WfNotice("n-1", "Relief claimed at Makati", "Jonas covers the Makati evening box, 18:00-22:00."),
        WfNotice("n-2", "Alabang boxes sealed", "SESSION + PRODUCT filled as SNAP-ALA-0117/0118.", read = true),
        WfNotice("n-3", "QC box closed PAST", "File the remittance before the 04:00 boundary to fill cleanly."),
    )
    val reliefs = mutableStateListOf(
        WfRelief("RF-1", "makati", "Lena Cruz", "Evening box needs one practitioner, 18:00-22:00."),
        WfRelief("RF-2", "qc", "Mara Villanueva", "QC Saturday box wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Ortigas Frame Hall",
        "08:04 Jonas clocked in at Makati Fill Office",
        "09:15 S-103 filled to COMPLETED at Ortigas Frame Hall",
        "10:40 Relief request boxed for Makati evening fill",
    )
    val invites = mutableStateListOf(
        "Invite boxed to Jonas Reyes for Makati evening fill - awaiting flag-off",
    )
    val meClockedIn = mutableStateOf(true)
    val branchFilter = mutableStateOf("ortigas")
    val anonymized = mutableStateOf(false)
    val showFlags = mutableStateOf(true)
    val seq = mutableStateOf(200)

    fun currentBranch(): WfBranch = branches.firstOrNull { it.id == branchFilter.value } ?: branches.first()

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun selectBranch(id: String) {
        branchFilter.value = id
        audit.add(0, "Box switched to fill table: ${branchName(id)}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        meClockedIn.value = inNow
        val entry = if (inNow) "clocked in at ${branchName(branchId)}" else "clocked out at ${branchName(branchId)}"
        audit.add(0, "11:02 Mara $entry")
    }

    fun claimRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = WfReliefState.CLAIMED)
        audit.add(0, "Relief box claimed: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun releaseRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = WfReliefState.RELEASED)
        audit.add(0, "Relief box released: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun addReliefRequest(branchId: String, note: String) {
        seq.value += 1
        reliefs.add(0, WfRelief("RF-${seq.value}", branchId, "Mara Villanueva", note.ifBlank { "Open call on the board" }))
        audit.add(0, "Relief request boxed for ${branchName(branchId)}")
    }

    fun sendInvite(name: String, branchId: String, note: String) {
        val line = "Invite boxed to ${name.ifBlank { "open roster" }} for ${branchName(branchId)}" +
            if (note.isBlank()) " - awaiting flag-off" else " ($note) - awaiting flag-off"
        invites.add(0, line)
        audit.add(0, line)
    }

    fun setSessionStatus(id: String, status: WfSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == WfSessionStatus.NO_SHOW || status == WfSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status, voided = false, voidReason = "")
        audit.add(0, "${s.id} filled to $status at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "${s.id} boxed out (voided): $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit.add(0, "${s.id} void box lifted, status ${s.status}")
    }

    fun bookSession(branchId: String, type: String) {
        seq.value += 1
        sessions.add(
            0,
            WfSession(
                "S-${seq.value}", "Walk-in ${seq.value}", branchId,
                WfSessionStatus.PENDING, type.ifBlank { "Fill Visit 30" }, 600, walkIn = true,
            ),
        )
        audit.add(0, "S-${seq.value} boxed as PENDING at ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        seq.value += 1
        remits[i] = r.copy(status = WfRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} filled 11:20")
        audit.add(0, "${r.kind} box ${r.id} submitted, snapshot SNAP-${seq.value}")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val r = remits[i]
        remits[i] = r.copy(status = WfRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "Fill lifted on ${r.id} within 48h: $reason")
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

    fun flipDayStatus(branchId: String, status: WfDayStatus) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        branches[i] = branches[i].copy(dayStatus = status)
        audit.add(0, "${branchName(branchId)} day box set to $status")
    }

    fun grantPractitioner() {
        val i = users.indexOfFirst { it.id == "u-ob" }
        if (i < 0) return
        users[i] = users[i].copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino flagged from ONBOARDING to Practitioner")
    }

    fun resetDemo() {
        meClockedIn.value = true
        anonymized.value = false
        audit.add(0, "Wireframe reset to the opening boxes")
    }
}
