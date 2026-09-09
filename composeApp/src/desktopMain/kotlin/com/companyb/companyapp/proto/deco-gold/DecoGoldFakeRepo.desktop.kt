package com.companyb.companyapp.proto.decogold

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.companyb.companyapp.util.logInfo

enum class GoldDayStatus { OPEN, PAST, REMITTED }

enum class GoldSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class GoldRemitKind { SESSION, PRODUCT }

enum class GoldRemitStatus { DRAFT, SUBMITTED }

data class GoldBranch(
    val id: String,
    val name: String,
    val kind: String,
    val hallNote: String,
)

data class GoldUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class GoldSession(
    val id: String,
    val clientName: String,
    val branchId: String,
    val status: GoldSessionStatus,
    val type: String,
    val price: Int,
    val walkIn: Boolean,
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class GoldClient(
    val id: String,
    val name: String,
    val detail: String,
    val hasPending: Boolean,
    val anonymized: Boolean = false,
)

data class GoldNote(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val day: String = "",
)

data class GoldAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class GoldRemittance(
    val id: String,
    val kind: GoldRemitKind,
    val status: GoldRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshot: String = "",
    val submittedAt: String = "",
)

data class GoldReliefItem(
    val id: String,
    val kind: String,
    val who: String,
    val branch: String,
    val day: String,
    val note: String,
)

class DecoGoldFakeRepo {
    val branches =
        mutableStateListOf(
            GoldBranch("b1", "Grand Meridian", "Flagship", "Marble hall, brass doors"),
            GoldBranch("b2", "Gilded Annex", "Satellite", "Mirror gallery, velvet ropes"),
            GoldBranch("b3", "Night Pavilion", "Outreach", "Lantern court, evening shift"),
        )

    val users =
        mutableStateListOf(
            GoldUser("u1", "Aurelia Santos", "Practitioner", "b1"),
            GoldUser("u2", "Bram Dela Cruz", "Coordinator", "b1"),
            GoldUser("u3", "Cassia Villanueva", "MANAGER", "b2"),
            GoldUser("u4", "Dorian Tan", "Accountant", "b1"),
            GoldUser("u5", "Novice Quinn", "ONBOARDING", "b1", onboarding = true),
        )

    val sessions =
        mutableStateListOf(
            GoldSession("s1", "Imelda Ramos", "b1", GoldSessionStatus.PENDING, "Cleaning", 800, false, time = "09:00"),
            GoldSession("s2", "Walk-in patron", "b1", GoldSessionStatus.PENDING, "Checkup", 500, true, time = "09:30"),
            GoldSession(
                "s3",
                "Felipe Aquino",
                "b1",
                GoldSessionStatus.COMPLETED,
                "Filling",
                1200,
                false,
                time = "08:00",
            ),
            GoldSession("s4", "Rosa Lim", "b2", GoldSessionStatus.NO_SHOW, "Whitening", 2500, false, time = "10:00"),
            GoldSession(
                "s5",
                "Tomas Reyes",
                "b1",
                GoldSessionStatus.CANCELLED,
                "Extraction",
                1500,
                false,
                time = "11:00",
            ),
        )

    val clients =
        mutableStateListOf(
            GoldClient("c1", "Imelda Ramos", "Second visit, prefers morning appointments", hasPending = true),
            GoldClient("c2", "Felipe Aquino", "Completed filling, recall in 6 months", hasPending = false),
            GoldClient("c3", "Rosa Lim", "No-show twice, courtesy call owed", hasPending = false),
            GoldClient(
                "c4",
                "Guest Patron",
                "Registry entry, anonymized for the grand board",
                hasPending = false,
                anonymized = true,
            ),
        )

    val notes =
        mutableStateListOf(
            GoldNote(
                "n1",
                "Relief invite: Gilded Annex",
                "Cassia invites Aurelia to cover Sat 09:00-13:00.",
                day = "Sat",
            ),
            GoldNote(
                "n2",
                "Remittance sealed",
                "Grand Meridian SESSION draft submitted, snapshot No. 42.",
                read = true,
                day = "Fri",
            ),
            GoldNote(
                "n3",
                "Welcome to the grand hall",
                "All figures here are gilded fiction. Take the tour.",
                day = "Today",
            ),
        )

    val audits =
        mutableStateListOf(
            GoldAudit("a1", "Bram Dela Cruz", "SUBMIT", "Remittance No. 42", "Fri 17:02", "End-of-day seal"),
            GoldAudit("a2", "Aurelia Santos", "VOID", "Session s5", "Fri 11:20", "Client asked to rebook"),
        )

    val remittances =
        mutableStateListOf(
            GoldRemittance("r1", GoldRemitKind.SESSION, GoldRemitStatus.DRAFT, 3200, "Today"),
            GoldRemittance("r2", GoldRemitKind.PRODUCT, GoldRemitStatus.DRAFT, 1450, "Today"),
            GoldRemittance(
                "r0",
                GoldRemitKind.SESSION,
                GoldRemitStatus.SUBMITTED,
                9800,
                "Yesterday",
                snapshot = "No. 42 sealed",
                submittedAt = "Fri 17:02",
            ),
        )

    val relief =
        mutableStateListOf(
            GoldReliefItem("f1", "Request", "Aurelia Santos", "Grand Meridian", "Sat", "Cover needed 09:00-13:00"),
            GoldReliefItem(
                "f2",
                "Invite",
                "Cassia Villanueva",
                "Gilded Annex",
                "Sun",
                "Gala clinic needs one more chair",
            ),
            GoldReliefItem("f3", "Duty", "Bram Dela Cruz", "Grand Meridian", "Today", "Front-hall ledger duty"),
        )

    val dayStatus = mutableStateOf(GoldDayStatus.OPEN)
    val clockedIn = mutableStateOf(false)
    val branchId = mutableStateOf("b1")
    val me = mutableStateOf(users[0])
    val auditSeq = mutableStateOf(100)

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun stamp(
        action: String,
        record: String,
        reason: String,
    ) {
        auditSeq.value += 1
        audits.add(
            0,
            GoldAudit("a${auditSeq.value}", me.value.name, action, record, "Today matinee", reason),
        )
    }

    fun voidSession(
        id: String,
        reason: String,
    ) {
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
            stamp("UNVOID", "Session $id", "Restored to the evening program")
        }
    }

    fun completeSession(id: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i >= 0) {
            val s = sessions[i]
            sessions[i] = s.copy(status = GoldSessionStatus.COMPLETED)
            stamp("COMPLETE", "Session $id", "Service rendered in full")
        }
    }

    fun addWalkIn(
        name: String,
        type: String,
        price: Int,
    ) {
        val id = "s${sessions.size + 1}-gilt"
        sessions.add(GoldSession(id, name, branchId.value, GoldSessionStatus.PENDING, type, price, true, time = "Now"))
        stamp("WALK_IN", "Session $id", "$name entered off the boulevard")
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
            remittances[i] =
                r.copy(
                    status = GoldRemitStatus.SUBMITTED,
                    snapshot = "No. gilt-${r.id}",
                    submittedAt = "Today",
                )
            stamp("SUBMIT", "Remittance ${r.id}", "Sealed under the house stamp")
            logInfo("DecoGold", "submitRemittance id=${r.id} kind=${r.kind}")
        }
    }

    fun undoRemittance(
        id: String,
        reason: String,
    ) {
        val i = remittances.indexOfFirst { it.id == id }
        if (i >= 0) {
            val r = remittances[i]
            remittances[i] = r.copy(status = GoldRemitStatus.DRAFT, snapshot = "", submittedAt = "")
            stamp("UNDO", "Remittance ${r.id}", reason)
        }
    }

    fun addRelief(
        kind: String,
        note: String,
    ) {
        relief.add(
            0,
            GoldReliefItem("f${relief.size + 1}-gilt", kind, me.value.name, branchName(branchId.value), "Today", note),
        )
        stamp(kind.uppercase(), "Relief board", note)
    }

    fun clockToggle() {
        clockedIn.value = !clockedIn.value
        stamp(if (clockedIn.value) "CLOCK_IN" else "CLOCK_OUT", branchName(branchId.value), "House shift beat")
    }

    fun reset() {
        dayStatus.value = GoldDayStatus.OPEN
        clockedIn.value = false
        branchId.value = "b1"
        me.value = users[0]
        logInfo("DecoGold", "reset demo data")
    }
}
