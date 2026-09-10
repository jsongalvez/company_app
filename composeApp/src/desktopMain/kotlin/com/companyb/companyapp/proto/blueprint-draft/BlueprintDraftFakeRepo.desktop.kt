package com.companyb.companyapp.proto.blueprintdraft

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class BpDayStatus { OPEN, PAST, REMITTED }

enum class BpSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BpRemitKind { SESSION, PRODUCT }

enum class BpRemitStatus { DRAFT, SUBMITTED }

enum class BpReliefState { OPEN, CLAIMED, RELEASED }

data class BpBranch(
    val id: String,
    val name: String,
    val district: String,
    val dayStatus: BpDayStatus,
    val target: Int,
    val gross: Int,
    val onShift: Int,
    val sheetNo: String,
)

data class BpUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class BpSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: BpSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class BpClient(
    val id: String,
    val code: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class BpRemit(
    val id: String,
    val branchId: String,
    val kind: BpRemitKind,
    val amount: Int,
    val status: BpRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class BpNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class BpRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: BpReliefState = BpReliefState.OPEN,
)

class BlueprintDraftFakeRepo {
    val branches = mutableStateListOf(
        BpBranch("ortigas", "Ortigas Draft Hall", "ELEV-A", BpDayStatus.OPEN, 90000, 71200, 6, "A-101"),
        BpBranch("makati", "Makati Line Office", "ELEV-B", BpDayStatus.OPEN, 60000, 38400, 4, "A-102"),
        BpBranch("qc", "QC Measure Room", "ELEV-C", BpDayStatus.PAST, 45000, 42150, 3, "A-103"),
        BpBranch("alabang", "Alabang Print Vault", "ELEV-D", BpDayStatus.REMITTED, 75000, 76800, 5, "A-104"),
    )
    val users = mutableStateListOf(
        BpUser("u-mgr", "Mara Villanueva", "MANAGER", "ortigas", clockedIn = true),
        BpUser("u-prac", "Jonas Reyes", "Practitioner", "makati", clockedIn = true),
        BpUser("u-coord", "Lena Cruz", "Coordinator", "qc"),
        BpUser("u-acct", "Paolo Santos", "Accountant", "ortigas"),
        BpUser("u-ob", "Rina Aquino", "ONBOARDING", "makati", onboarding = true),
    )
    val sessions = mutableStateListOf(
        BpSession("S-101", "Client A-17", "ortigas", BpSessionStatus.PENDING, "Draft Review 60", 1200, walkIn = false),
        BpSession("S-102", "Walk-in 04", "ortigas", BpSessionStatus.PENDING, "Measure Visit 30", 600, walkIn = true),
        BpSession("S-103", "Client B-02", "ortigas", BpSessionStatus.COMPLETED, "Full Print 90", 1800, walkIn = false),
        BpSession("S-104", "Client C-11", "makati", BpSessionStatus.PENDING, "Line Check 60", 1100, walkIn = false),
        BpSession("S-105", "Client D-09", "makati", BpSessionStatus.NO_SHOW, "Survey Hour 75", 1500, walkIn = false),
        BpSession("S-106", "Client E-21", "qc", BpSessionStatus.COMPLETED, "Walk-in Measure", 400, walkIn = true),
        BpSession("S-107", "Client F-33", "qc", BpSessionStatus.CANCELLED, "Full Print 60", 1300, walkIn = false),
        BpSession("S-108", "Client G-08", "alabang", BpSessionStatus.COMPLETED, "Vault Session 120", 2600, walkIn = false),
        BpSession("S-109", "Client H-14", "alabang", BpSessionStatus.PENDING, "Duo Draft 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        BpClient("c-a17", "A-17", "Client A-17", "Female, 34 - prefers mornings", 1),
        BpClient("c-b02", "B-02", "Client B-02", "Male, 41 - on file since 2024", 0),
        BpClient("c-c11", "C-11", "Client C-11", "Female, 28 - Makati regular", 1),
        BpClient("c-d09", "D-09", "Client D-09", "Male, 52 - two past no-shows", 0),
        BpClient("c-g08", "G-08", "Client G-08", "Female, 47 - Alabang member", 0),
    )
    val remits = mutableStateListOf(
        BpRemit("R-ORT-S", "ortigas", BpRemitKind.SESSION, 71200, BpRemitStatus.DRAFT),
        BpRemit("R-ORT-P", "ortigas", BpRemitKind.PRODUCT, 9800, BpRemitStatus.DRAFT),
        BpRemit("R-ALA-S", "alabang", BpRemitKind.SESSION, 76800, BpRemitStatus.SUBMITTED, snapshot = "SNAP-ALA-0117 sealed 06:12"),
        BpRemit("R-ALA-P", "alabang", BpRemitKind.PRODUCT, 12400, BpRemitStatus.SUBMITTED, snapshot = "SNAP-ALA-0118 sealed 06:12"),
        BpRemit("R-MAK-S", "makati", BpRemitKind.SESSION, 38400, BpRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        BpNotice("n-1", "Relief claimed at Makati", "Jonas covers the Makati evening sheet, 18:00-22:00."),
        BpNotice("n-2", "Alabang sheet sealed", "SESSION + PRODUCT sealed as SNAP-ALA-0117/0118.", read = true),
        BpNotice("n-3", "QC sheet closed PAST", "File the remittance before the 04:00 boundary to remit cleanly."),
    )
    val reliefs = mutableStateListOf(
        BpRelief("RF-1", "makati", "Lena Cruz", "Evening sheet needs one practitioner, 18:00-22:00."),
        BpRelief("RF-2", "qc", "Mara Villanueva", "QC Saturday sheet wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Ortigas Draft Hall",
        "08:04 Jonas clocked in at Makati Line Office",
        "09:15 S-103 redlined to COMPLETED at Ortigas Draft Hall",
        "10:40 Relief request drafted for Makati evening sheet",
    )
    val invites = mutableStateListOf(
        "Invite drafted to Jonas Reyes for Makati evening sheet - awaiting countersign",
    )
    val meClockedIn = mutableStateOf(true)
    val branchFilter = mutableStateOf("ortigas")
    val anonymized = mutableStateOf(false)
    val seq = mutableStateOf(200)

    fun currentBranch(): BpBranch = branches.firstOrNull { it.id == branchFilter.value } ?: branches.first()

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun selectBranch(id: String) {
        branchFilter.value = id
        audit.add(0, "Sheet switched to drafting table: ${branchName(id)}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        meClockedIn.value = inNow
        val entry = if (inNow) "clocked in at ${branchName(branchId)}" else "clocked out at ${branchName(branchId)}"
        audit.add(0, "11:02 Mara $entry")
    }

    fun claimRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BpReliefState.CLAIMED)
        audit.add(0, "Relief duty claimed: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun releaseRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = BpReliefState.RELEASED)
        audit.add(0, "Relief duty released: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun addReliefRequest(branchId: String, note: String) {
        seq.value += 1
        reliefs.add(0, BpRelief("RF-${seq.value}", branchId, "Mara Villanueva", note.ifBlank { "Open call on the board" }))
        audit.add(0, "Relief request drafted for ${branchName(branchId)}")
    }

    fun sendInvite(name: String, branchId: String, note: String) {
        val line = "Invite drafted to ${name.ifBlank { "open roster" }} for ${branchName(branchId)}" +
            if (note.isBlank()) " - awaiting countersign" else " ($note) - awaiting countersign"
        invites.add(0, line)
        audit.add(0, line)
    }

    fun setSessionStatus(id: String, status: BpSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == BpSessionStatus.NO_SHOW || status == BpSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status, voided = false, voidReason = "")
        audit.add(0, "${s.id} redlined to $status at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "${s.id} clouded out (voided): $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit.add(0, "${s.id} revision cloud lifted, status ${s.status}")
    }

    fun bookSession(branchId: String, type: String) {
        seq.value += 1
        sessions.add(
            0,
            BpSession(
                "S-${seq.value}", "Walk-in ${seq.value}", branchId,
                BpSessionStatus.PENDING, type.ifBlank { "Measure Visit 30" }, 600, walkIn = true,
            ),
        )
        audit.add(0, "S-${seq.value} drafted as PENDING at ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        seq.value += 1
        remits[i] = r.copy(status = BpRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} sealed 11:20")
        audit.add(0, "${r.kind} sheet ${r.id} submitted, snapshot SNAP-${seq.value}")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val r = remits[i]
        remits[i] = r.copy(status = BpRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "Seal lifted on ${r.id} within 48h: $reason")
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

    fun flipDayStatus(branchId: String, status: BpDayStatus) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        branches[i] = branches[i].copy(dayStatus = status)
        audit.add(0, "${branchName(branchId)} title block set to $status")
    }

    fun grantPractitioner() {
        val i = users.indexOfFirst { it.id == "u-ob" }
        if (i < 0) return
        users[i] = users[i].copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino countersigned from ONBOARDING to Practitioner")
    }

    fun resetDemo() {
        meClockedIn.value = true
        anonymized.value = false
        audit.add(0, "Drafting table reset to the opening sheet")
    }
}
