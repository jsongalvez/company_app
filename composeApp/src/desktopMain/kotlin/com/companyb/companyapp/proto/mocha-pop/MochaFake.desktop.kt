package com.companyb.companyapp.proto.mochapop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class MochaDayStatus { OPEN, PAST, REMITTED }

enum class MochaSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class MochaRemitKind { SESSION, PRODUCT }

enum class MochaRemitStatus { DRAFT, SUBMITTED }

data class MochaBranch(
    val id: String,
    val name: String,
    val kind: String,
    val candyNote: String,
)

data class MochaUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class MochaSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: MochaSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class MochaClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class MochaNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class MochaAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class MochaRemittance(
    val id: String,
    val kind: MochaRemitKind,
    val status: MochaRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class MochaReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

data class MochaProductLine(
    val id: String,
    val label: String,
    val amount: Int,
)

class MochaFakeRepo {
    val branches = mutableStateListOf(
        MochaBranch("b1", "Mocha Evenings HQ", "Flagship", "Candy-lit flagship counter!"),
        MochaBranch("b2", "Caramel Tour Van", "Provincial Tour", "Sweet pop-up on wheels!"),
        MochaBranch("b3", "Berry Outreach Post", "Medical Mission", "Cozy mission-night stall!"),
    )

    val users = mutableStateListOf(
        MochaUser("u1", "Mika Santos", "Practitioner", "b1"),
        MochaUser("u2", "Jo Reyes", "Coordinator", "b1"),
        MochaUser("u3", "Ada Villanueva", "Manager", "b2"),
        MochaUser("u4", "Rin Aquino", "Accountant", "b1"),
        MochaUser("u5", "New Bean", "ONBOARDING", "b1", onboarding = true),
    )

    val sessions = mutableStateListOf(
        MochaSession("s1", "Cherry Ann", "b1", MochaSessionStatus.PENDING, "Cleaning", 800, false, time = "18:00"),
        MochaSession("s2", "Walk-in Cocoa", "b1", MochaSessionStatus.PENDING, "Checkup", 500, true, time = "18:20"),
        MochaSession("s3", "Berry Dela Cruz", "b1", MochaSessionStatus.COMPLETED, "Filling", 1200, false, time = "17:00"),
        MochaSession("s4", "Peach Ramos", "b2", MochaSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "16:30"),
        MochaSession("s5", "Mint Bautista", "b1", MochaSessionStatus.CANCELLED, "Extraction", 1500, false, time = "15:10"),
    )

    val clients = mutableStateListOf(
        MochaClient("c1", "Cherry Ann", "Second evening visit, loves caramel...", hasPending = true),
        MochaClient("c2", "Berry Dela Cruz", "Filling done, recall in 6 months", hasPending = false),
        MochaClient("c3", "Peach Ramos", "No-show twice, needs a cozy call", hasPending = false),
        MochaClient("c4", "Sunny Co", "New chart, masked on the board", hasPending = false, anonymized = true),
    )

    val notes = mutableStateListOf(
        MochaNote("n1", "Caramel invite: Tour Van", "Ada invites Mika to cover Sat 18:00-21:00.", day = "Sat"),
        MochaNote("n2", "Snapshot sealed", "Mocha SESSION draft submitted, swirl #12.", read = true, day = "Fri"),
        MochaNote("n3", "Welcome to mocha-pop", "Warm candy evening ops. Tap every truffle!", day = "Today"),
    )

    val audits = mutableStateListOf(
        MochaAudit("a1", "Jo Reyes", "SUBMIT", "Remittance #12", "Fri 20:02", "Evening close"),
        MochaAudit("a2", "Mika Santos", "VOID", "Session s5", "Fri 15:20", "Client asked to rebook"),
    )

    val remittances = mutableStateListOf(
        MochaRemittance("r1", MochaRemitKind.SESSION, MochaRemitStatus.DRAFT, 3200, "Today"),
        MochaRemittance("r2", MochaRemitKind.PRODUCT, MochaRemitStatus.DRAFT, 1450, "Today"),
        MochaRemittance(
            "r0",
            MochaRemitKind.SESSION,
            MochaRemitStatus.SUBMITTED,
            9800,
            "Yesterday",
            snapshot = "swirl #12 sealed",
            submittedAt = "Fri 20:02",
        ),
    )

    val productLines = mutableStateListOf(
        MochaProductLine("p1", "Candy polish kit", 650),
        MochaProductLine("p2", "Caramel rinse", 800),
    )

    val relief = mutableStateListOf(
        MochaReliefItem("f1", "Request", "Mika Santos", "Mocha Evenings HQ", "Sat", "Need cover 18:00-21:00!"),
        MochaReliefItem("f2", "Invite", "Ada Villanueva", "Caramel Tour Van", "Sun", "Pop-up needs one more hand!"),
        MochaReliefItem("f3", "Duty", "Jo Reyes", "Mocha Evenings HQ", "Today", "Evening front-desk truffles!"),
    )

    val dayStatus = mutableStateOf(MochaDayStatus.OPEN)
    val dayLabel = mutableStateOf("2026-09-09")
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(action: String, record: String, reason: String) {
        auditSeq.value += 1
        audits.add(
            0,
            MochaAudit("a${auditSeq.value}", me.value.name, action, record, "Today, candy shift", reason),
        )
    }

    fun setDay(status: MochaDayStatus, label: String) {
        dayStatus.value = status
        dayLabel.value = label
        stamp("DAY", "$label ${status.name}", "04:00 Asia/Manila boundary")
    }

    fun setSessionStatus(id: String, next: MochaSessionStatus, reason: String = "") {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        val s = sessions[i]
        if (s.walkIn && (next == MochaSessionStatus.NO_SHOW || next == MochaSessionStatus.CANCELLED)) return
        sessions[i] = s.copy(status = next)
        stamp(next.name, "Session $id", reason.ifEmpty { "Evening update" })
    }

    fun voidSession(id: String, reason: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = true, voidReason = reason)
            stamp("VOID", "Session $id", reason)
        }
    }

    fun unvoidSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(voided = false, voidReason = "")
            stamp("UNVOID", "Session $id", "Back on the candy tray!")
        }
    }

    fun pendingCountForClient(clientName: String): Int =
        sessions.count { it.clientName == clientName && it.status == MochaSessionStatus.PENDING && !it.voided }

    fun addSession(clientName: String, type: String, price: Int, walkIn: Boolean): String {
        if (!walkIn && sessions.any { it.status == MochaSessionStatus.PENDING && !it.voided }) {
            return "Only one PENDING booking at a time — finish the live truffle first."
        }
        val id = "s${sessions.size + 1}-mocha"
        sessions.add(MochaSession(id, clientName, branchId.value, MochaSessionStatus.PENDING, type, price, walkIn, time = "Now"))
        clients.firstOrNull { it.name == clientName } ?: run {
            clients.add(MochaClient("c${clients.size + 1}-mocha", clientName, "Evening intake", hasPending = true))
        }
        stamp(if (walkIn) "WALK_IN" else "BOOK", "Session $id", "$clientName joined the tray!")
        return ""
    }

    fun toggleAnonymized(id: String) {
        val i = clients.indexOfFirst { it.id == id }
        if (i >= 0) clients[i] = clients[i].copy(anonymized = !clients[i].anonymized)
    }

    fun markRead(id: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = notes[i].copy(read = true)
    }

    fun markAllRead() {
        for (i in notes.indices) notes[i] = notes[i].copy(read = true)
    }

    fun submitRemittance(id: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = MochaRemitStatus.SUBMITTED, snapshot = "swirl-${r.id}", submittedAt = "Today 21:00")
            stamp("SUBMIT", "Remittance ${r.id}", "Sealed with a candy swirl!")
            logInfo("MochaPop", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(id: String, reason: String) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = MochaRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addProductLine(label: String, amount: Int) {
        productLines.add(MochaProductLine("p${productLines.size + 1}-mocha", label, amount))
        stamp("ADD_LINE", label, "Sweet shelf restock")
    }

    fun removeProductLine(id: String) {
        productLines.removeAll { it.id == id }
        stamp("DROP_LINE", id, "Shelf tidy")
    }

    fun addRelief(kind: String, note: String) {
        relief.add(
            0,
            MochaReliefItem("f${relief.size + 1}-mocha", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "Candy-shift beat!")
    }

    fun sessionNet(): Int =
        sessions.filter { it.status == MochaSessionStatus.COMPLETED && !it.voided }.sumOf { it.price }

    fun productNet(): Int = productLines.sumOf { it.amount }

    fun reset() {
        dayStatus.value = MochaDayStatus.OPEN
        dayLabel.value = "2026-09-09"
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("MochaPop", "reset demo data")
    }
}
