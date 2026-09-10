package com.companyb.companyapp.proto.sheetgrid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

// #817 — sheet-grid fake domain. Local only: no network client, no remote calls,
// no backend, no shared contracts. Seeded store drives every sheet in the workbook.

enum class SgDayStatus { OPEN, PAST, REMITTED }

enum class SgSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class SgRole { ONBOARDING, PRACTITIONER, COORDINATOR, MANAGER, ACCOUNTANT }

enum class SgBranchKind { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION }

enum class SgRemitKind { SESSION, PRODUCT }

enum class SgRemitState { DRAFT, SUBMITTED }

enum class SgReliefKind { DUTY, REQUEST, INVITE }

enum class SgReliefState { OPEN, GRANTED, DENIED, ACCEPTED, DECLINED }

data class SgBranch(
    val id: String,
    val name: String,
    val kind: SgBranchKind,
    val line: String,
)

data class SgUser(
    val id: String,
    val name: String,
    val role: SgRole,
    val homeBranchId: String,
    val slot: Int,
)

data class SgSession(
    val id: String,
    val clientId: String,
    val clientName: String,
    val branchId: String,
    val bookedTime: String,
    val type: String,
    val status: SgSessionStatus,
    val price: Int,
    val walkIn: Boolean,
    val ticketNo: String? = null,
    val voided: Boolean = false,
    val voidReason: String? = null,
    val practitioner: String = "M. Santos",
)

data class SgClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val anonymized: Boolean = false,
)

data class SgProductLine(
    val name: String,
    val qty: Int,
    val price: Int,
)

data class SgRemit(
    val kind: SgRemitKind,
    val state: SgRemitState = SgRemitState.DRAFT,
    val draftGross: Int = 0,
    val draftDeductions: Int = 0,
    val snapshotId: String? = null,
    val snapshotTotal: Int? = null,
    val submittedAt: String? = null,
    val undoReason: String? = null,
)

data class SgNotif(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class SgAudit(
    val id: String,
    val whenLabel: String,
    val who: String,
    val action: String,
    val target: String,
    val reason: String? = null,
)

data class SgRelief(
    val id: String,
    val kind: SgReliefKind,
    val who: String,
    val detail: String,
    val state: SgReliefState = SgReliefState.OPEN,
)

data class SgDay(
    val label: String,
    val status: SgDayStatus,
)

class SgStore {
    var phase by mutableStateOf("LOGIN")
    var currentUser by mutableStateOf<SgUser?>(null)
    var loginSecret by mutableStateOf("")
    var loginError by mutableStateOf("")
    var currentBranchId by mutableStateOf("b-makati")
    var dayIndex by mutableStateOf(1)
    var sheet by mutableStateOf("HOME")
    var clockedIn by mutableStateOf(false)
    var activeRow by mutableStateOf(1)
    var sessionFilter by mutableStateOf("ALL")
    var mailFilter by mutableStateOf("ALL")
    var voidTarget by mutableStateOf<SgSession?>(null)
    var voidReason by mutableStateOf("")
    var showVoid by mutableStateOf(false)
    var showBook by mutableStateOf(false)
    var bookName by mutableStateOf("")
    var bookType by mutableStateOf("Swedish 60")
    var bookWalkIn by mutableStateOf(false)
    var undoTarget by mutableStateOf<SgRemitKind?>(null)
    var undoReason by mutableStateOf("")
    var showUndo by mutableStateOf(false)
    var remitNote by mutableStateOf("")
    var mailJump by mutableStateOf("")

    val days =
        mutableStateListOf(
            SgDay("2026-09-08", SgDayStatus.REMITTED),
            SgDay("2026-09-09", SgDayStatus.OPEN),
            SgDay("2026-09-10", SgDayStatus.PAST),
        )
    val branches =
        mutableStateListOf(
            SgBranch("b-makati", "Makati Clinic", SgBranchKind.CLINIC, "L1 · Est. 2019"),
            SgBranch("b-cebu", "Cebu Provincial Tour", SgBranchKind.PROVINCIAL_TOUR, "L2 · Tour leg 4"),
            SgBranch("b-tondo", "Tondo Medical Mission", SgBranchKind.MEDICAL_MISSION, "L3 · Mission wk 2"),
        )
    val users =
        mutableStateListOf(
            SgUser("u-mgr", "R. Aquino", SgRole.MANAGER, "b-makati", 1),
            SgUser("u-coord", "M. Santos", SgRole.COORDINATOR, "b-makati", 2),
            SgUser("u-prac", "J. Ramos", SgRole.PRACTITIONER, "b-cebu", 3),
            SgUser("u-acct", "L. Villanueva", SgRole.ACCOUNTANT, "b-makati", 4),
            SgUser("u-onb", "K. Dela Pena", SgRole.ONBOARDING, "b-makati", 5),
        )
    val sessions = mutableStateListOf<SgSession>()
    val clients = mutableStateListOf<SgClient>()
    val productLines: SnapshotStateList<SgProductLine> = mutableStateListOf()
    val notifs = mutableStateListOf<SgNotif>()
    val audits = mutableStateListOf<SgAudit>()
    val reliefs = mutableStateListOf<SgRelief>()
    var sessionRemit by mutableStateOf(SgRemit(SgRemitKind.SESSION))
    var productRemit by mutableStateOf(SgRemit(SgRemitKind.PRODUCT))
    var auditSeq by mutableStateOf(100)

    fun currentDay(): SgDay = days[dayIndex]

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun isManager(): Boolean = currentUser?.role == SgRole.MANAGER

    fun audit(
        action: String,
        target: String,
        reason: String? = null,
    ) {
        auditSeq += 1
        audits.add(
            0,
            SgAudit(
                "a$auditSeq",
                "${currentDay().label} 09:${(auditSeq % 50) + 10} PHT",
                currentUser?.name ?: "—",
                action,
                target,
                reason,
            ),
        )
    }

    fun login(user: SgUser) {
        if (user.role == SgRole.ONBOARDING) {
            currentUser = user
            phase = "LOCKED"
            audit("LOGIN_BLOCKED", user.name, "ONBOARDING locked: no capabilities")
            return
        }
        if (loginSecret.isBlank()) {
            loginError = "E2: secret required — any value signs in (fake auth)."
            return
        }
        loginError = ""
        currentUser = user
        currentBranchId = user.homeBranchId
        phase = "BRANCH"
        audit("LOGIN", user.name)
    }

    fun logout() {
        audit("LOGOUT", currentUser?.name ?: "—")
        currentUser = null
        loginSecret = ""
        loginError = ""
        phase = "LOGIN"
    }

    fun enterBranch(id: String) {
        currentBranchId = id
        phase = "BOOK"
        sheet = "HOME"
        activeRow = 1
        audit("BRANCH_SELECT", branchName(id))
    }

    fun toggleClock() {
        clockedIn = !clockedIn
        audit(if (clockedIn) "CLOCK_IN" else "CLOCK_OUT", branchName(currentBranchId))
    }

    fun setSessionStatus(
        id: String,
        status: SgSessionStatus,
    ): String {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return ""
        val s = sessions[i]
        if (s.walkIn && (status == SgSessionStatus.NO_SHOW || status == SgSessionStatus.CANCELLED)) {
            return "Walk-in ${s.ticketNo ?: s.id} cannot take NO_SHOW/CANCELLED — walk-ins are served or voided only."
        }
        sessions[i] = s.copy(status = status)
        audit("SESSION_$status", "${s.clientName} · ${s.id}")
        return ""
    }

    fun voidSession(reason: String) {
        val t = voidTarget ?: return
        val i = sessions.indexOfFirst { it.id == t.id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = true, voidReason = reason.ifBlank { "no reason given" })
        audit("SESSION_VOID", "${t.clientName} · ${t.id}", reason.ifBlank { "no reason given" })
        voidTarget = null
        voidReason = ""
        showVoid = false
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = false, voidReason = null)
        audit("SESSION_UNVOID", "${sessions[i].clientName} · $id")
    }

    fun bookSession() {
        if (bookName.isBlank()) return
        val n = sessions.size + 1
        val newId = "s-${110 + n}"
        sessions.add(
            SgSession(
                newId,
                "c-new-$n",
                bookName,
                currentBranchId,
                "16:30",
                bookType,
                SgSessionStatus.PENDING,
                950,
                bookWalkIn,
                ticketNo = if (bookWalkIn) "W-${220 + n}" else null,
                practitioner = currentUser?.name ?: "M. Santos",
            ),
        )
        if (bookName !in clients.map { it.name }) {
            clients.add(SgClient("c-new-$n", bookName, "—", 0))
        }
        audit("SESSION_BOOK", "$bookName · $newId")
        bookName = ""
        bookWalkIn = false
        showBook = false
    }

    fun anonymize(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clients[i]
        clients[i] = c.copy(name = "Anon-${c.id.takeLast(4)}", anonymized = true)
        audit("CLIENT_ANONYMIZE", c.name)
    }

    fun pendingClientIds(): Set<String> {
        val ids = sessions.filter { it.status == SgSessionStatus.PENDING && !it.voided }.map { it.clientId }.toSet()
        return ids
    }

    fun submitRemit(kind: SgRemitKind) {
        if (kind == SgRemitKind.SESSION) {
            val net = sessionRemit.draftGross - sessionRemit.draftDeductions
            sessionRemit =
                sessionRemit.copy(
                    state = SgRemitState.SUBMITTED,
                    snapshotId = "SNAP-S-$auditSeq",
                    snapshotTotal = net,
                    submittedAt = "${currentDay().label} 18:02 PHT",
                    undoReason = null,
                )
            audit("REMIT_SUBMIT", "SESSION $net")
        } else {
            val total = productLines.sumOf { it.qty * it.price }
            productRemit =
                productRemit.copy(
                    state = SgRemitState.SUBMITTED,
                    snapshotId = "SNAP-P-$auditSeq",
                    snapshotTotal = total,
                    submittedAt = "${currentDay().label} 18:05 PHT",
                    undoReason = null,
                )
            audit("REMIT_SUBMIT", "PRODUCT $total")
        }
        remitNote = ""
    }

    fun undoRemit(
        kind: SgRemitKind,
        reason: String,
    ) {
        if (kind == SgRemitKind.SESSION) {
            sessionRemit = sessionRemit.copy(state = SgRemitState.DRAFT, undoReason = reason.ifBlank { "correction" })
            audit("REMIT_UNDO", "SESSION ${sessionRemit.snapshotId ?: ""}", reason.ifBlank { "correction" })
        } else {
            productRemit = productRemit.copy(state = SgRemitState.DRAFT, undoReason = reason.ifBlank { "correction" })
            audit("REMIT_UNDO", "PRODUCT ${productRemit.snapshotId ?: ""}", reason.ifBlank { "correction" })
        }
        undoTarget = null
        undoReason = ""
        showUndo = false
    }

    fun markRead(id: String) {
        val i = notifs.indexOfFirst { it.id == id }
        if (i < 0) return
        notifs[i] = notifs[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notifs.indices) notifs[i] = notifs[i].copy(read = true)
        audit("MAIL_READ_ALL", "${notifs.size} messages")
    }

    fun reliefAct(
        id: String,
        accept: Boolean,
    ) {
        val i = reliefs.indexOfFirst { it.id == id }
        if (i < 0) return
        val r = reliefs[i]
        val next =
            when (r.kind) {
                SgReliefKind.REQUEST -> if (accept) SgReliefState.GRANTED else SgReliefState.DENIED
                SgReliefKind.INVITE -> if (accept) SgReliefState.ACCEPTED else SgReliefState.DECLINED
                SgReliefKind.DUTY -> if (accept) SgReliefState.ACCEPTED else SgReliefState.DECLINED
            }
        reliefs[i] = r.copy(state = next)
        audit("RELIEF_$next", "${r.who} · ${r.kind}")
    }
}

fun seedSgStore(): SgStore {
    val s = SgStore()
    s.sessions.addAll(
        listOf(
            SgSession(
                "s-101",
                "c-1",
                "A. Reyes",
                "b-makati",
                "09:00",
                "Swedish 60",
                SgSessionStatus.COMPLETED,
                1200,
                false,
                practitioner = "J. Ramos",
            ),
            SgSession(
                "s-102",
                "c-2",
                "B. Cruz",
                "b-makati",
                "10:00",
                "Shiatsu 45",
                SgSessionStatus.PENDING,
                950,
                false,
            ),
            SgSession(
                "s-103",
                "c-3",
                "C. Lim",
                "b-makati",
                "10:30",
                "Walk-in 30",
                SgSessionStatus.PENDING,
                600,
                true,
                ticketNo = "W-221",
            ),
            SgSession(
                "s-104",
                "c-4",
                "D. Ocampo",
                "b-makati",
                "11:00",
                "Hot Stone 90",
                SgSessionStatus.NO_SHOW,
                1800,
                false,
            ),
            SgSession(
                "s-105",
                "c-5",
                "E. Navarro",
                "b-makati",
                "13:00",
                "Swedish 60",
                SgSessionStatus.CANCELLED,
                1200,
                false,
            ),
            SgSession(
                "s-106",
                "c-6",
                "F. Garcia",
                "b-cebu",
                "09:30",
                "Tour circuit",
                SgSessionStatus.COMPLETED,
                800,
                false,
                practitioner = "J. Ramos",
            ),
            SgSession("s-107", "c-2", "B. Cruz", "b-makati", "14:00", "Foot 30", SgSessionStatus.PENDING, 500, false),
            SgSession(
                "s-108",
                "c-7",
                "G. Torres",
                "b-tondo",
                "08:00",
                "Mission line",
                SgSessionStatus.COMPLETED,
                0,
                true,
                ticketNo = "W-204",
            ),
            SgSession(
                "s-109",
                "c-8",
                "H. Mendoza",
                "b-makati",
                "15:00",
                "Deep Tissue 60",
                SgSessionStatus.PENDING,
                1350,
                false,
                voided = true,
                voidReason = "double-booked, rebooked to s-107",
            ),
            SgSession(
                "s-110",
                "c-9",
                "I. Salazar",
                "b-makati",
                "16:00",
                "Aroma 60",
                SgSessionStatus.PENDING,
                1400,
                false,
            ),
        ),
    )
    s.clients.addAll(
        listOf(
            SgClient("c-1", "A. Reyes", "F", 34),
            SgClient("c-2", "B. Cruz", "M", 41),
            SgClient("c-3", "C. Lim", "F", 28),
            SgClient("c-4", "D. Ocampo", "M", 52),
            SgClient("c-5", "E. Navarro", "F", 47),
            SgClient("c-6", "F. Garcia", "M", 36),
            SgClient("c-7", "G. Torres", "F", 61),
            SgClient("c-8", "H. Mendoza", "M", 44),
            SgClient("c-9", "I. Salazar", "F", 30),
        ),
    )
    s.productLines.addAll(
        listOf(
            SgProductLine("VCO 250ml", 3, 350),
            SgProductLine("Ginger balm", 5, 220),
            SgProductLine("Gift card 1k", 2, 1000),
        ),
    )
    s.reliefs.addAll(
        listOf(
            SgRelief("r-1", SgReliefKind.DUTY, "J. Ramos", "Cebu tour cover · 13:00–18:00"),
            SgRelief("r-2", SgReliefKind.REQUEST, "M. Santos", "Swap Sat shift with R. Aquino"),
            SgRelief("r-3", SgReliefKind.INVITE, "L. Villanueva", "Mission count help · Tondo Sat"),
            SgRelief("r-4", SgReliefKind.REQUEST, "J. Ramos", "Early-out Fri 16:00"),
        ),
    )
    s.notifs.addAll(
        listOf(
            SgNotif("n-1", "Branch day past due", "2026-09-10 flipped PAST at 04:00 Asia/Manila.", "2026-09-10", false),
            SgNotif("n-2", "Relief request", "M. Santos asked to swap Saturday.", "2026-09-09", false),
            SgNotif("n-3", "Snapshot sealed", "SNAP-S-88 sealed SESSION at 18:02 PHT.", "2026-09-08", true),
            SgNotif("n-4", "Walk-in queue", "3 walk-ins waiting at Makati front desk.", "2026-09-09", false),
            SgNotif("n-5", "Undo window", "SNAP-P-91 undo window closes in 6h (48h rule).", "2026-09-09", true),
        ),
    )
    s.audits.addAll(
        listOf(
            SgAudit("a3", "2026-09-09 18:02 PHT", "R. Aquino", "REMIT_SUBMIT", "SESSION 18400"),
            SgAudit(
                "a2",
                "2026-09-09 14:44 PHT",
                "M. Santos",
                "SESSION_VOID",
                "D. Ocampo · s-104",
                "client migraine, rebook",
            ),
            SgAudit("a1", "2026-09-09 08:01 PHT", "R. Aquino", "CLOCK_IN", "Makati Clinic"),
        ),
    )
    s.sessionRemit = SgRemit(SgRemitKind.SESSION, draftGross = 21200, draftDeductions = 2800)
    return s
}
