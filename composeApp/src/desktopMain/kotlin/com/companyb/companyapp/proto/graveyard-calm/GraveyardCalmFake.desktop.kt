package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class CalmSessionStatus(
    val label: String,
) {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    NO_SHOW("NO_SHOW"),
    CANCELLED("CANCELLED"),
}

internal enum class CalmDayState(
    val label: String,
) {
    OPEN("OPEN"),
    PAST("PAST"),
    REMITTED("REMITTED"),
}

internal enum class CalmScreen(
    val key: String,
    val title: String,
    val hint: String,
) {
    HOME("1", "night desk", "clock-in + relief + handover"),
    SESSIONS("2", "quiet queue", "sessions + void"),
    CLIENTS("3", "sleeping registry", "global clients"),
    FINANCE("4", "night till", "remittance"),
    TEAM("5", "skeleton crew", "users + roles"),
    MAIL("6", "whisper mail", "notices + audit"),
    PROFILE("7", "night lamp", "me + logout"),
}

internal data class CalmBranchDay(
    val date: String,
    val state: CalmDayState,
    val note: String,
)

internal data class CalmUser(
    val id: String,
    val name: String,
    val role: String,
    val capabilities: List<String>,
    val locked: Boolean,
)

internal data class CalmBranch(
    val id: String,
    val name: String,
    val kind: String,
)

internal data class CalmSession(
    val id: String,
    val time: String,
    val clientId: String,
    val clientName: String,
    val walkIn: Boolean,
    val service: String,
    val status: CalmSessionStatus,
    val price: Int,
    val practitioner: String,
    val voided: Boolean = false,
    val voidReason: String = "",
)

internal data class CalmClient(
    val id: String,
    val name: String,
    val gender: String,
    val age: Int,
    val phone: String,
    val anonymized: Boolean = false,
)

internal data class CalmStaff(
    val id: String,
    val name: String,
    val role: String,
    val slot: Int,
    val home: String,
    val relief: Boolean,
    val clockedIn: Boolean,
)

internal data class CalmNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val dayRef: String,
    var read: Boolean,
)

internal data class CalmAudit(
    val seq: Int,
    val clock: String,
    val actor: String,
    val action: String,
    val detail: String,
)

internal data class CalmProductLine(
    val id: String,
    val name: String,
    val qty: Int,
    val unitPrice: Int,
)

internal data class CalmSnapshot(
    val id: String,
    val kind: String,
    val total: Int,
    val clock: String,
    val by: String,
    val undone: Boolean = false,
    val undoReason: String = "",
)

internal data class CalmHandover(
    val id: String,
    val clock: String,
    val author: String,
    val text: String,
)

internal class CalmRepo {
    var authed by mutableStateOf(false)
    var userIndex by mutableStateOf(0)
    var branchPicked by mutableStateOf(false)
    var branchIndex by mutableStateOf(0)
    var dayIndex by mutableStateOf(1)
    var screen by mutableStateOf(CalmScreen.HOME)
    var shortcutsOpen by mutableStateOf(false)
    var clockedIn by mutableStateOf(false)
    var sessionFilter by mutableStateOf<String?>(null)
    var selectedSessionId by mutableStateOf<String?>(null)
    var selectedClientId by mutableStateOf<String?>(null)
    var voidDialogFor by mutableStateOf<String?>(null)
    var voidReason by mutableStateOf("")
    var createOpen by mutableStateOf(false)
    var undoDialogFor by mutableStateOf<String?>(null)
    var undoReason by mutableStateOf("")
    var sessionComp by mutableStateOf("0")
    var sessionExpense by mutableStateOf("0")
    var newProductName by mutableStateOf("")
    var newProductQty by mutableStateOf("1")
    var newProductPrice by mutableStateOf("0")
    var handoverDraft by mutableStateOf("")

    val users =
        mutableStateListOf(
            CalmUser("u-mara", "Mara Villanueva", "Coordinator", listOf("session.write", "client.read", "remittance.draft", "relief.accept"), false),
            CalmUser("u-juno", "Juno Salazar", "Practitioner", listOf("session.write", "relief.accept"), false),
            CalmUser("u-isa", "Isa Ramos", "MANAGER", listOf("session.write", "remittance.submit", "remittance.undo", "team.manage"), false),
            CalmUser("u-noa", "Noa Aquino", "ONBOARDING", emptyList(), true),
        )
    val currentUser: CalmUser get() = users[userIndex]

    val branches =
        mutableStateListOf(
            CalmBranch("b-quiet", "Ermita Night Clinic", "CLINIC"),
            CalmBranch("b-van", "Laguna Night Van", "PROVINCIAL_TOUR"),
            CalmBranch("b-mission", "Port Area Mission Post", "MEDICAL_MISSION"),
        )
    val currentBranch: CalmBranch get() = branches[branchIndex]

    val days =
        mutableStateListOf(
            CalmBranchDay("2026-09-08", CalmDayState.REMITTED, "sealed by night audit"),
            CalmBranchDay("2026-09-09", CalmDayState.OPEN, "tonight — skeleton crew of 3"),
            CalmBranchDay("2026-09-10", CalmDayState.PAST, "awaiting remittance"),
        )
    val currentDay: CalmBranchDay get() = days[dayIndex]

    val sessions =
        mutableStateListOf(
            CalmSession("s-101", "22:15", "c-ana", "Ana D.", false, "Night facial", CalmSessionStatus.COMPLETED, 850, "Juno Salazar"),
            CalmSession("s-102", "23:00", "c-ben", "Ben T.", true, "Walk-in cleanup", CalmSessionStatus.PENDING, 600, "Juno Salazar"),
            CalmSession("s-103", "23:40", "c-cora", "Cora L.", false, "Back treatment", CalmSessionStatus.NO_SHOW, 1200, "Mara Villanueva"),
            CalmSession("s-104", "00:20", "c-dan", "Dan P.", false, "Night facial", CalmSessionStatus.CANCELLED, 850, "Mara Villanueva"),
            CalmSession("s-105", "01:05", "c-ana", "Ana D.", false, "Follow-up massage", CalmSessionStatus.PENDING, 950, "Juno Salazar"),
            CalmSession("s-106", "02:30", "c-eli", "Eli M.", true, "Walk-in trim", CalmSessionStatus.PENDING, 400, "Mara Villanueva"),
        )

    val clients =
        mutableStateListOf(
            CalmClient("c-ana", "Ana D.", "F", 34, "0917-000-1101"),
            CalmClient("c-ben", "Ben T.", "M", 29, "0917-000-1102"),
            CalmClient("c-cora", "Cora L.", "F", 41, "0917-000-1103"),
            CalmClient("c-dan", "Dan P.", "M", 37, "0917-000-1104"),
            CalmClient("c-eli", "Eli M.", "X", 26, "0917-000-1105"),
        )

    val staff =
        mutableStateListOf(
            CalmStaff("u-mara", "Mara Villanueva", "Coordinator", 1, "Ermita Night Clinic", false, true),
            CalmStaff("u-juno", "Juno Salazar", "Practitioner", 2, "Ermita Night Clinic", false, true),
            CalmStaff("u-teo", "Teo Bautista", "Practitioner", 3, "Laguna Night Van", true, false),
            CalmStaff("u-noa", "Noa Aquino", "ONBOARDING", 4, "Ermita Night Clinic", false, false),
        )

    val notices =
        mutableStateListOf(
            CalmNotice("n-1", "relief", "Relief request from Laguna Night Van", "Teo asks for one pair of hands 01:00-03:00.", "2026-09-09", false),
            CalmNotice("n-2", "hush", "Noise curfew at 01:00", "Keep the waiting playlist under a whisper after 01:00.", "2026-09-09", false),
            CalmNotice("n-3", "audit", "Night audit sealed 2026-09-08", "Snapshot N-08-A accepted. Nothing to redo.", "2026-09-08", true),
            CalmNotice("n-4", "client", "Cora L. sent a handover note", "Prefers unscented oil next visit.", "2026-09-09", false),
        )

    val audits = mutableStateListOf(CalmAudit(1, "22:02", "system", "branch-day.open", "2026-09-09 opened under 04:00 Asia/Manila boundary"))
    val productLines =
        mutableStateListOf(
            CalmProductLine("p-1", "Unscented oil 100ml", 2, 350),
            CalmProductLine("p-2", "Sleep mask", 1, 220),
        )
    val snapshots =
        mutableStateListOf(
            CalmSnapshot("N-08-A", "SESSION", 2450, "2026-09-08 03:52", "Isa Ramos"),
        )
    val handovers =
        mutableStateListOf(
            CalmHandover("h-1", "23:55", "Mara Villanueva", "Ben T. walk-in is half-asleep in chair 2 — keep voices down."),
            CalmHandover("h-2", "00:40", "Juno Salazar", "Back room diffuser refilled. Oil stock: 3 bottles left."),
        )

    private var seq = 1
    private var tickMin = 4
    private var sessionSeq = 107
    private var handoverSeq = 3
    private var productSeq = 3
    private var snapshotSeq = 9

    fun stamp(): String {
        tickMin += 1
        val hour = 1 + (tickMin / 60)
        val min = tickMin % 60
        val hh = hour.toString().padStart(2, '0')
        val mm = min.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun audit(action: String, detail: String) {
        seq += 1
        audits.add(0, CalmAudit(seq, stamp(), currentUser.name, action, detail))
    }

    fun actorName(): String = if (authed) currentUser.name else "night-desk"

    fun login(index: Int) {
        userIndex = index
        authed = true
        clockedIn = false
        audit("auth.login", "${users[index].name} signed in (${users[index].role})")
    }

    fun logout() {
        audit("auth.logout", "${currentUser.name} hung up the lamp")
        authed = false
        branchPicked = false
        clockedIn = false
        screen = CalmScreen.HOME
    }

    fun pickBranch(index: Int) {
        branchIndex = index
        branchPicked = true
        audit("branch.select", "night desk moved to ${branches[index].name}")
    }

    fun clockIn() {
        clockedIn = true
        audit("duty.clock-in", "${currentUser.name} clocked in at ${currentBranch.name}")
    }

    fun clockOut() {
        clockedIn = false
        audit("duty.clock-out", "${currentUser.name} clocked out")
    }

    fun sessionById(id: String): CalmSession? = sessions.firstOrNull { it.id == id }

    fun pendingForClient(clientId: String): Int =
        sessions.count { it.clientId == clientId && it.status == CalmSessionStatus.PENDING && !it.voided }

    fun setStatus(id: String, status: CalmSessionStatus) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (status == CalmSessionStatus.NO_SHOW || status == CalmSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = status)
        audit("session.${status.name.lowercase()}", "$id -> ${status.label}")
    }

    fun voidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val reason = voidReason.trim().ifEmpty { "no reason given" }
        sessions[i] = sessions[i].copy(voided = true, voidReason = reason)
        audit("session.void", "$id voided: $reason")
        voidDialogFor = null
        voidReason = ""
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(voided = false, voidReason = "")
        audit("session.unvoid", "$id restored to queue")
    }

    fun addSession(clientName: String, service: String, price: Int, walkIn: Boolean, time: String) {
        val id = "s-${sessionSeq++}"
        sessions.add(CalmSession(id, time.ifBlank { stamp() }, "c-walk-$id", clientName.ifBlank { "Nameless guest" }, walkIn, service.ifBlank { "Night consult" }, CalmSessionStatus.PENDING, price, currentUser.name))
        audit("session.create", "$id booked ($service) ${if (walkIn) "walk-in" else "booked"}")
        createOpen = false
    }

    fun anonymizeClient(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clients[i]
        clients[i] = c.copy(name = "Guest ${c.id}", phone = "", anonymized = true)
        audit("client.anonymize", "${c.id} veiled (gender/age kept)")
    }

    fun sessionDraftNet(): Int {
        val done = sessions.filter { it.status == CalmSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }
        return done - (sessionComp.toIntOrNull() ?: 0) - (sessionExpense.toIntOrNull() ?: 0)
    }

    fun productDraftTotal(): Int = productLines.sumOf { it.qty * it.unitPrice }

    fun addProductLine() {
        val name = newProductName.trim().ifEmpty { "Night serum" }
        val qty = newProductQty.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val price = newProductPrice.toIntOrNull()?.coerceAtLeast(0) ?: 0
        productLines.add(CalmProductLine("p-${productSeq++}", name, qty, price))
        audit("remittance.product-add", "$name x$qty @ $price")
        newProductName = ""
        newProductQty = "1"
        newProductPrice = "0"
    }

    fun removeProductLine(id: String) {
        productLines.removeAll { it.id == id }
        audit("remittance.product-remove", "$id removed from draft")
    }

    fun submitSnapshot(kind: String, total: Int) {
        snapshotSeq += 1
        val id = "N-09-${if (kind == "SESSION") "S" else "P"}$snapshotSeq"
        snapshots.add(0, CalmSnapshot(id, kind, total, "2026-09-09 ${stamp()}", currentUser.name))
        audit("remittance.submit", "$id $kind sealed: $total")
    }

    fun undoSnapshot(id: String) {
        val i = snapshots.indexOfFirst { it.id == id }
        if (i < 0) return
        val reason = undoReason.trim().ifEmpty { "no reason given" }
        snapshots[i] = snapshots[i].copy(undone = true, undoReason = reason)
        audit("remittance.undo", "$id reopened: $reason (48h window)")
        undoDialogFor = null
        undoReason = ""
    }

    fun markRead(id: String) {
        val n = notices.firstOrNull { it.id == id } ?: return
        if (!n.read) {
            n.read = true
            audit("notice.read", "$id marked read")
        }
    }

    fun markAllRead() {
        notices.forEach { it.read = true }
        audit("notice.drain", "mailbox drained to silence")
    }

    fun acceptRelief(staffId: String) {
        val i = staff.indexOfFirst { it.id == staffId }
        if (i < 0) return
        staff[i] = staff[i].copy(relief = false, clockedIn = true)
        audit("relief.accept", "${staff[i].name} joined the night desk")
    }

    fun addHandover() {
        val text = handoverDraft.trim()
        if (text.isEmpty()) return
        handovers.add(CalmHandover("h-${handoverSeq++}", stamp(), currentUser.name, text))
        audit("handover.note", text.take(60))
        handoverDraft = ""
    }

    val unreadCount: Int get() = notices.count { !it.read }
    val pendingCount: Int get() = sessions.count { it.status == CalmSessionStatus.PENDING && !it.voided }
    val voidedCount: Int get() = sessions.count { it.voided }
}

@Composable
internal fun rememberCalmRepo(): CalmRepo = remember { CalmRepo() }
