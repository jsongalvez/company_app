package com.companyb.companyapp.proto.franchisemap

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class FmDayStatus { OPEN, PAST, REMITTED }

enum class FmSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class FmRemitKind { SESSION, PRODUCT }

enum class FmRemitStatus { DRAFT, SUBMITTED }

enum class FmReliefState { OPEN, GRANTED, FOLDED }

data class FmBranch(
    val id: String,
    val name: String,
    val territory: String,
    val dayStatus: FmDayStatus,
    val target: Int,
    val gross: Int,
    val mapX: Float,
    val mapY: Float,
    val onShift: Int,
)

data class FmUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val clockedIn: Boolean = false,
    val onboarding: Boolean = false,
)

data class FmSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: FmSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
)

data class FmClient(
    val id: String,
    val name: String,
    val detail: String,
    val pendingCount: Int,
)

data class FmRemit(
    val id: String,
    val branchId: String,
    val kind: FmRemitKind,
    val amount: Int,
    val status: FmRemitStatus,
    val snapshot: String = "",
    val undoReason: String = "",
)

data class FmNotice(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
)

data class FmRelief(
    val id: String,
    val branchId: String,
    val asker: String,
    val note: String,
    val state: FmReliefState = FmReliefState.OPEN,
)

class FranchiseMapFakeRepo {
    val branches = mutableStateListOf(
        FmBranch("sunrise", "Sunrise Clinic", "North Ridge", FmDayStatus.OPEN, 60000, 41500, 0.30f, 0.28f, 4),
        FmBranch("uptown", "Uptown Flagship", "North Ridge", FmDayStatus.REMITTED, 90000, 92500, 0.62f, 0.24f, 6),
        FmBranch("harbor", "Harbor Bay Post", "Harbor Bay", FmDayStatus.OPEN, 45000, 18900, 0.70f, 0.64f, 2),
        FmBranch("lingap", "Lingap Mission Tent", "Metro South", FmDayStatus.PAST, 30000, 27400, 0.40f, 0.74f, 3),
    )
    val users = mutableStateListOf(
        FmUser("u-mgr", "Mara Villanueva", "MANAGER", "uptown", clockedIn = true),
        FmUser("u-prac", "Jonas Reyes", "Practitioner", "sunrise", clockedIn = true),
        FmUser("u-coord", "Lena Cruz", "Coordinator", "harbor"),
        FmUser("u-acct", "Paolo Santos", "Accountant", "uptown"),
        FmUser("u-ob", "Rina Aquino", "ONBOARDING", "sunrise", onboarding = true),
    )
    val sessions = mutableStateListOf(
        FmSession("s-101", "Client A-17", "sunrise", FmSessionStatus.PENDING, "Deep Tissue 60", 1200, walkIn = false),
        FmSession("s-102", "Walk-in 04", "sunrise", FmSessionStatus.PENDING, "Foot Ritual 30", 600, walkIn = true),
        FmSession("s-103", "Client B-02", "sunrise", FmSessionStatus.COMPLETED, "Aroma 90", 1800, walkIn = false),
        FmSession("s-104", "Client C-11", "harbor", FmSessionStatus.PENDING, "Swedish 60", 1100, walkIn = false),
        FmSession("s-105", "Client D-09", "harbor", FmSessionStatus.NO_SHOW, "Hot Stone 75", 1500, walkIn = false),
        FmSession("s-106", "Client E-21", "lingap", FmSessionStatus.COMPLETED, "Community Pass", 400, walkIn = true),
        FmSession("s-107", "Client F-33", "lingap", FmSessionStatus.CANCELLED, "Aroma 60", 1300, walkIn = false),
        FmSession("s-108", "Client G-08", "uptown", FmSessionStatus.COMPLETED, "Signature 120", 2600, walkIn = false),
        FmSession("s-109", "Client H-14", "uptown", FmSessionStatus.PENDING, "Couples 90", 3200, walkIn = false),
    )
    val clients = mutableStateListOf(
        FmClient("c-a17", "Client A-17", "Female, 34 - prefers mornings", 1),
        FmClient("c-b02", "Client B-02", "Male, 41 - member since 2024", 0),
        FmClient("c-c11", "Client C-11", "Female, 28 - Harbor regular", 1),
        FmClient("c-d09", "Client D-09", "Male, 52 - two past no-shows", 0),
        FmClient("c-g08", "Client G-08", "Female, 47 - flagship member", 0),
    )
    val remits = mutableStateListOf(
        FmRemit("r-sun-s", "sunrise", FmRemitKind.SESSION, 41500, FmRemitStatus.DRAFT),
        FmRemit("r-sun-p", "sunrise", FmRemitKind.PRODUCT, 6300, FmRemitStatus.DRAFT),
        FmRemit("r-upt-s", "uptown", FmRemitKind.SESSION, 92500, FmRemitStatus.SUBMITTED, snapshot = "SNAP-UPT-0441 sealed 06:12"),
        FmRemit("r-upt-p", "uptown", FmRemitKind.PRODUCT, 14800, FmRemitStatus.SUBMITTED, snapshot = "SNAP-UPT-0442 sealed 06:12"),
        FmRemit("r-hbr-s", "harbor", FmRemitKind.SESSION, 18900, FmRemitStatus.DRAFT),
    )
    val notices = mutableStateListOf(
        FmNotice("n-1", "Relief granted at Harbor", "Jonas covers the Harbor evening line, 18:00-22:00."),
        FmNotice("n-2", "Uptown snapshot sealed", "SESSION + PRODUCT sealed as SNAP-UPT-0441/0442.", read = true),
        FmNotice("n-3", "Lingap day closed PAST", "Submit remittance before the 04:00 boundary to remit cleanly."),
    )
    val reliefs = mutableStateListOf(
        FmRelief("rf-1", "harbor", "Lena Cruz", "Evening line needs one practitioner, 18:00-22:00."),
        FmRelief("rf-2", "lingap", "Mara Villanueva", "Mission tent Saturday wants a coordinator."),
    )
    val audit = mutableStateListOf(
        "07:58 Mara clocked in at Uptown Flagship",
        "08:04 Jonas clocked in at Sunrise Clinic",
        "09:15 s-103 marked COMPLETED at Sunrise",
        "10:40 Relief request opened for Harbor evening line",
    )
    val meClockedIn = mutableStateOf(true)
    val dayFilter = mutableStateOf("sunrise")
    val seq = mutableStateOf(200)

    fun currentBranch(): FmBranch = branches.firstOrNull { it.id == dayFilter.value } ?: branches.first()

    fun selectBranch(id: String) {
        dayFilter.value = id
        audit.add(0, "Viewing territory pin: ${branches.firstOrNull { it.id == id }?.name ?: id}")
    }

    fun setClockedIn(branchId: String, inNow: Boolean) {
        val me = users.firstOrNull { it.id == "u-mgr" } ?: return
        users[users.indexOf(me)] = me.copy(clockedIn = inNow)
        meClockedIn.value = inNow
        val name = branches.firstOrNull { it.id == branchId }?.name ?: branchId
        audit.add(0, "Mara clocked ${if (inNow) "in" else "out"} at $name")
    }

    fun grantOnboarding() {
        val trainee = users.firstOrNull { it.onboarding } ?: return
        users[users.indexOf(trainee)] = trainee.copy(role = "Practitioner", onboarding = false)
        audit.add(0, "Rina Aquino granted Practitioner capability bundle")
    }

    fun moveSession(id: String, to: FmSessionStatus) {
        val s = sessions.firstOrNull { it.id == id } ?: return
        if (s.walkIn && (to == FmSessionStatus.NO_SHOW || to == FmSessionStatus.CANCELLED)) return
        sessions[sessions.indexOf(s)] = s.copy(status = to)
        audit.add(0, "$id marked $to at ${branchName(s.branchId)}")
    }

    fun voidSession(id: String, reason: String) {
        val s = sessions.firstOrNull { it.id == id } ?: return
        if (reason.isBlank()) return
        sessions[sessions.indexOf(s)] = s.copy(voided = true, voidReason = reason)
        audit.add(0, "$id voided: $reason")
    }

    fun unvoidSession(id: String) {
        val s = sessions.firstOrNull { it.id == id } ?: return
        sessions[sessions.indexOf(s)] = s.copy(voided = false, voidReason = "")
        audit.add(0, "$id void lifted")
    }

    fun bookSession(branchId: String, name: String, type: String) {
        if (name.isBlank()) return
        seq.value += 1
        sessions.add(FmSession("s-${seq.value}", name, branchId, FmSessionStatus.PENDING, type, 1000, walkIn = false))
        audit.add(0, "Booked $name at ${branchName(branchId)} (PENDING)")
    }

    fun draftRemit(branchId: String, kind: FmRemitKind, amount: Int) {
        seq.value += 1
        remits.add(FmRemit("r-${seq.value}", branchId, kind, amount, FmRemitStatus.DRAFT))
        audit.add(0, "Drafted $kind remittance P$amount for ${branchName(branchId)}")
    }

    fun submitRemit(id: String) {
        val r = remits.firstOrNull { it.id == id } ?: return
        seq.value += 1
        remits[remits.indexOf(r)] = r.copy(status = FmRemitStatus.SUBMITTED, snapshot = "SNAP-${seq.value} sealed 18:40")
        audit.add(0, "${r.id} submitted, snapshot sealed")
    }

    fun undoRemit(id: String, reason: String) {
        val r = remits.firstOrNull { it.id == id } ?: return
        if (reason.isBlank()) return
        remits[remits.indexOf(r)] = r.copy(status = FmRemitStatus.DRAFT, snapshot = "", undoReason = reason)
        audit.add(0, "${r.id} undone within 48h: $reason")
    }

    fun flipDay(branchId: String, to: FmDayStatus) {
        val b = branches.firstOrNull { it.id == branchId } ?: return
        branches[branches.indexOf(b)] = b.copy(dayStatus = to)
        audit.add(0, "${b.name} day flipped to $to")
    }

    fun toggleNotice(id: String) {
        val n = notices.firstOrNull { it.id == id } ?: return
        notices[notices.indexOf(n)] = n.copy(read = !n.read)
    }

    fun markAllRead() {
        notices.replaceAll { it.copy(read = true) }
    }

    fun setRelief(id: String, to: FmReliefState) {
        val r = reliefs.firstOrNull { it.id == id } ?: return
        reliefs[reliefs.indexOf(r)] = r.copy(state = to)
        audit.add(0, "Relief $id ${to.name.lowercase()} (${r.asker})")
    }

    fun openRelief(branchId: String, note: String) {
        if (note.isBlank()) return
        seq.value += 1
        reliefs.add(FmRelief("rf-${seq.value}", branchId, "Mara Villanueva", note))
        audit.add(0, "Relief request broadcast for ${branchName(branchId)}")
    }

    fun unreadCount(): Int = notices.count { !it.read }

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun reset() {
        val fresh = FranchiseMapFakeRepo()
        branches.clear(); branches.addAll(fresh.branches)
        users.clear(); users.addAll(fresh.users)
        sessions.clear(); sessions.addAll(fresh.sessions)
        clients.clear(); clients.addAll(fresh.clients)
        remits.clear(); remits.addAll(fresh.remits)
        notices.clear(); notices.addAll(fresh.notices)
        reliefs.clear(); reliefs.addAll(fresh.reliefs)
        audit.clear(); audit.addAll(fresh.audit)
        meClockedIn.value = true
        dayFilter.value = "sunrise"
    }
}
