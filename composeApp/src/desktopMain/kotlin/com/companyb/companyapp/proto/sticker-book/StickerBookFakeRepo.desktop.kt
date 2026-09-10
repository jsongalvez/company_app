package com.companyb.companyapp.proto.stickerbook

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class SbDayStatus { OPEN, PAST, REMITTED }

enum class SbSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class SbRemitKind { SESSION, PRODUCT }

enum class SbRemitStatus { DRAFT, SUBMITTED }

enum class SbReliefState { OPEN, TAKEN, RELEASED }

data class SbBranch(
    val id: String,
    val name: String,
    val pride: String,
    val dayStatus: SbDayStatus,
    val target: Int,
    val gross: Int,
    val onShift: Int,
)

data class SbUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class SbSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: SbSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class SbClient(
    val id: String,
    val code: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class SbRemit(
    val id: String,
    val branchId: String,
    val kind: SbRemitKind,
    val amount: Int,
    val status: SbRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class SbNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class SbRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: SbReliefState = SbReliefState.OPEN,
)

data class SbSticker(val id: String, val title: String, val page: String)

class StickerBookFakeRepo {
    val branches = mutableStateListOf(
        SbBranch("sunbeam", "Sunbeam Strip", "Brightest smiles crew", SbDayStatus.OPEN, 90000, 71200, 6),
        SbBranch("coralbay", "Coral Bay", "Wave-makers crew", SbDayStatus.OPEN, 60000, 38400, 4),
        SbBranch("meadow", "Meadow Walk", "Early-bird crew", SbDayStatus.PAST, 45000, 42150, 3),
        SbBranch("starhill", "Star Hill", "Gold-star crew", SbDayStatus.REMITTED, 75000, 76800, 5),
    )
    val users = mutableStateListOf(
        SbUser("u-mgr", "Mara Villanueva", "MANAGER", "sunbeam", clockedIn = true),
        SbUser("u-prac", "Jonas Reyes", "Practitioner", "coralbay", clockedIn = true),
        SbUser("u-coord", "Lena Cruz", "Coordinator", "meadow"),
        SbUser("u-acct", "Paolo Santos", "Accountant", "sunbeam"),
        SbUser("u-ob", "Rina Aquino", "ONBOARDING", "coralbay", onboarding = true),
    )
    val sessions = mutableStateListOf(
        SbSession("s-101", "Client A-17", "sunbeam", SbSessionStatus.PENDING, "Glow Session 60", 1200, walkIn = false),
        SbSession("s-102", "Walk-in 04", "sunbeam", SbSessionStatus.PENDING, "Spark Visit 30", 600, walkIn = true),
        SbSession("s-103", "Client B-02", "sunbeam", SbSessionStatus.COMPLETED, "Star Session 90", 1800, walkIn = false),
        SbSession("s-104", "Client C-11", "coralbay", SbSessionStatus.PENDING, "Wave Session 60", 1100, walkIn = false),
        SbSession("s-105", "Client D-09", "coralbay", SbSessionStatus.NO_SHOW, "Shine Hour 75", 1500, walkIn = false),
        SbSession("s-106", "Client E-21", "meadow", SbSessionStatus.COMPLETED, "Dawn Visit", 400, walkIn = true),
        SbSession("s-107", "Client F-33", "meadow", SbSessionStatus.CANCELLED, "Bloom Time 60", 1300, walkIn = false),
        SbSession("s-108", "Client G-08", "starhill", SbSessionStatus.COMPLETED, "Gold Session 120", 2600, walkIn = false),
        SbSession("s-109", "Client H-14", "starhill", SbSessionStatus.PENDING, "Duo Star 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        SbClient("c-a17", "A-17", "Client A-17", "Regular, prefers mornings", 1),
        SbClient("c-b02", "B-02", "Client B-02", "Member since 2024", 0),
        SbClient("c-c11", "C-11", "Client C-11", "Coral Bay regular", 1),
        SbClient("c-d09", "D-09", "Client D-09", "Two past no-shows", 0),
        SbClient("c-g08", "G-08", "Client G-08", "Star Hill member", 0),
    )
    val remits = mutableStateListOf(
        SbRemit("r-sun-s", "sunbeam", SbRemitKind.SESSION, 71200, SbRemitStatus.DRAFT),
        SbRemit("r-sun-p", "sunbeam", SbRemitKind.PRODUCT, 9800, SbRemitStatus.DRAFT),
        SbRemit("r-star-s", "starhill", SbRemitKind.SESSION, 76800, SbRemitStatus.SUBMITTED,
            snapshot = "SNAP-STAR-0117 pasted 06:12"),
        SbRemit("r-star-p", "starhill", SbRemitKind.PRODUCT, 12400, SbRemitStatus.SUBMITTED,
            snapshot = "SNAP-STAR-0118 pasted 06:12"),
        SbRemit("r-coral-s", "coralbay", SbRemitKind.SESSION, 38400, SbRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        SbNotice("n-1", "Relief pasted at Coral Bay", "Jonas covers the Coral evening book, 18:00-22:00."),
        SbNotice("n-2", "Star Hill page sealed", "SESSION + PRODUCT sealed as SNAP-STAR-0117/0118.", read = true),
        SbNotice("n-3", "Meadow Walk day closed PAST", "Paste the remittance before the 04:00 boundary to remit cleanly."),
    )
    val reliefs = mutableStateListOf(
        SbRelief("rf-1", "coralbay", "Lena Cruz", "Evening book needs one practitioner, 18:00-22:00."),
        SbRelief("rf-2", "meadow", "Mara Villanueva", "Meadow Saturday wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Sunbeam Strip",
        "08:04 Jonas clocked in at Coral Bay",
        "09:15 s-103 earned COMPLETED at Sunbeam Strip",
        "10:40 Relief request pasted for Coral evening",
    )
    val invites = mutableStateListOf(
        "Invite pasted to Jonas Reyes for Coral evening book - waiting for a stick",
    )
    val stickers = mutableStateListOf(
        SbSticker("welcome", "First Day", "Sunbeam Strip"),
    )
    val meClockedIn = mutableStateOf(true)
    val branchFilter = mutableStateOf("sunbeam")
    val anonymized = mutableStateOf(false)
    val seq = mutableStateOf(200)

    fun currentBranch(): SbBranch = branches.firstOrNull { it.id == branchFilter.value } ?: branches.first()

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun selectBranch(id: String) {
        branchFilter.value = id
        audit.add(0, "Opened pride page: ${branchName(id)}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        meClockedIn.value = inNow
        val entry = if (inNow) "clocked in at ${branchName(branchId)}" else "clocked out at ${branchName(branchId)}"
        audit.add(0, "11:02 Mara $entry")
    }

    fun takeRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = SbReliefState.TAKEN)
        audit.add(0, "Relief duty pasted on: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun releaseRelief(id: String) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        reliefs[i] = reliefs[i].copy(state = SbReliefState.RELEASED)
        audit.add(0, "Relief duty peeled off: ${reliefs[i].asker} at ${branchName(reliefs[i].branchId)}")
    }

    fun addReliefRequest(branchId: String, note: String) {
        seq.value += 1
        reliefs.add(0, SbRelief("rf-${seq.value}", branchId, "Mara Villanueva", note.ifBlank { "Open book call" }))
        audit.add(0, "Relief request pasted for ${branchName(branchId)}")
    }

    fun sendInvite(name: String, branchId: String, note: String) {
        seq.value += 1
        val line = "Invite pasted to ${name.ifBlank { "open roster" }} for ${branchName(branchId)}" +
            if (note.isBlank()) " - waiting for a stick" else " ($note) - waiting for a stick"
        invites.add(0, line)
        audit.add(0, line)
    }

    fun setSessionStatus(id: String, status: SbSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == SbSessionStatus.NO_SHOW || status == SbSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status, voided = false, voidReason = "")
        audit.add(0, "${s.id} earned $status at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "${s.id} peeled off (voided): $reason")
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        sessions[i] = s.copy(voided = false, voidReason = "")
        audit.add(0, "${s.id} stuck back on, status ${s.status}")
    }

    fun bookSession(branchId: String, type: String) {
        seq.value += 1
        sessions.add(0, SbSession("s-${seq.value}", "Walk-in ${seq.value}", branchId,
            SbSessionStatus.PENDING, type.ifBlank { "Spark Visit 30" }, 600, walkIn = true))
        audit.add(0, "s-${seq.value} pasted as PENDING at ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = remits[i]
        seq.value += 1
        remits[i] = r.copy(status = SbRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} pasted 11:20")
        audit.add(0, "${r.kind} page ${r.id} submitted, snapshot SNAP-${seq.value}")
    }

    fun undoRemit(id: String, reason: String) {
        val i = remits.indexOfFirst { it.id == id }
        if (i < 0 || reason.isBlank()) return
        val r = remits[i]
        remits[i] = r.copy(status = SbRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "Page for ${r.id} peeled off within 48h: $reason")
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

    fun flipDayStatus(branchId: String, status: SbDayStatus) {
        val i = branches.indexOfFirst { it.id == branchId }
        if (i < 0) return
        branches[i] = branches[i].copy(dayStatus = status)
        audit.add(0, "${branchName(branchId)} pride page set to $status")
    }

    fun grantPractitioner() {
        val i = users.indexOfFirst { it.id == "u-ob" }
        if (i < 0) return
        users[i] = users[i].copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino earned Practitioner, out of ONBOARDING")
    }

    fun earnSticker(id: String, title: String, page: String) {
        if (stickers.any { it.id == id }) return
        stickers.add(SbSticker(id, title, page))
        audit.add(0, "Sticker earned: $title on the $page page")
    }

    fun resetDemo() {
        meClockedIn.value = true
        anonymized.value = false
        audit.add(0, "Album reset to the opening page")
    }
}
