package com.companyb.companyapp.proto.beaconstatus

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

enum class BsSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }

enum class BsDayState { OPEN, PAST, REMITTED }

enum class BsRemitKind { SESSION, PRODUCT }

enum class BsRemitStatus { DRAFT, SUBMITTED }

enum class BsLamp { GREEN, AMBER, RED }

data class BsBranch(
    val id: String,
    val name: String,
    val kind: String,
    val berth: String,
)

data class BsUser(
    val id: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val onboarding: Boolean = false,
)

data class BsSession(
    val id: String,
    val signalNo: String,
    val clientName: String,
    val branchId: String,
    val status: BsSessionStatus,
    val service: String,
    val price: Int,
    val walkIn: Boolean,
    val lane: String = "",
    val voided: Boolean = false,
    val voidReason: String = "",
    val time: String,
)

data class BsClient(
    val id: String,
    val name: String,
    val detail: String,
    val genderAge: String,
    val anonymized: Boolean = false,
)

data class BsMail(
    val id: String,
    val title: String,
    val body: String,
    val day: String,
    val read: Boolean = false,
)

data class BsAudit(
    val id: String,
    val actor: String,
    val action: String,
    val record: String,
    val whenText: String,
    val reason: String = "",
)

data class BsRemittance(
    val id: String,
    val kind: BsRemitKind,
    val status: BsRemitStatus,
    val amount: Int,
    val dayLabel: String,
    val snapshotNo: String = "",
    val note: String = "",
)

data class BsInvite(
    val id: String,
    val branchName: String,
    val day: String,
    val state: String = "OPEN",
)

data class BsRequest(
    val id: String,
    val branchName: String,
    val day: String,
    val mine: Boolean,
    val state: String = "OPEN",
)

object BeaconStatusFakeRepo {
    val branches =
        listOf(
            BsBranch("b-qc", "QC Central", "CLINIC", "BERTH 01"),
            BsBranch("b-laguna", "Laguna Tour Stop 3", "PROVINCIAL_TOUR", "BERTH 02"),
            BsBranch("b-tondo", "Tondo Medical Mission", "MEDICAL_MISSION", "BERTH 03"),
        )

    val directory =
        listOf(
            BsUser("u-coord", "R. Dizon", "Coordinator", "b-qc"),
            BsUser("u-prac-a", "J. Ramos", "Practitioner", "b-qc"),
            BsUser("u-prac-b", "K. Aquino", "Practitioner", "b-qc"),
            BsUser("u-mgr", "M. Sy", "MANAGER", "b-laguna"),
            BsUser("u-acct", "L. Tan", "Accountant", "b-qc"),
            BsUser("u-onb", "New Hire", "ONBOARDING", "b-qc", onboarding = true),
        )

    val lanes = listOf("Lane 1 · J. Ramos", "Lane 2 · K. Aquino", "Lane 3 · Relief cover")

    val currentUser = mutableStateOf(directory[0])
    val clockedIn = mutableStateOf(false)
    val clockedBranchId = mutableStateOf("b-qc")
    val reliefEdit = mutableStateOf(false)
    val dayState = mutableStateOf(BsDayState.OPEN)

    val sessions =
        mutableStateListOf(
            BsSession(
                "s-301",
                "S-301",
                "C. Aquino",
                "b-qc",
                BsSessionStatus.PENDING,
                "Follow-up",
                850,
                walkIn = false,
                time = "09:00",
            ),
            BsSession(
                "s-302",
                "S-302",
                "D. Cruz",
                "b-qc",
                BsSessionStatus.PENDING,
                "Initial",
                1200,
                walkIn = true,
                time = "09:12",
            ),
            BsSession(
                "s-303",
                "S-303",
                "E. Santos",
                "b-qc",
                BsSessionStatus.PENDING,
                "Follow-up",
                850,
                walkIn = false,
                lane = "Lane 1 · J. Ramos",
                time = "09:25",
            ),
            BsSession(
                "s-304",
                "S-304",
                "F. Reyes",
                "b-qc",
                BsSessionStatus.PENDING,
                "Maintenance",
                700,
                walkIn = true,
                time = "09:40",
            ),
            BsSession(
                "s-305",
                "S-305",
                "G. Lim",
                "b-qc",
                BsSessionStatus.COMPLETED,
                "Initial",
                1200,
                walkIn = false,
                lane = "Lane 2 · K. Aquino",
                time = "08:10",
            ),
            BsSession(
                "s-306",
                "S-306",
                "H. Uy",
                "b-laguna",
                BsSessionStatus.NO_SHOW,
                "Follow-up",
                850,
                walkIn = false,
                time = "08:30",
            ),
            BsSession(
                "s-307",
                "S-307",
                "I. Valencia",
                "b-tondo",
                BsSessionStatus.CANCELLED,
                "Mission screen",
                0,
                walkIn = false,
                time = "08:45",
            ),
            BsSession(
                "s-308",
                "S-308",
                "J. Padilla",
                "b-laguna",
                BsSessionStatus.PENDING,
                "Field screen",
                600,
                walkIn = true,
                time = "10:05",
            ),
        )

    val clients =
        mutableStateListOf(
            BsClient("c-01", "C. Aquino", "F · QC · 3 visits", "F · 34"),
            BsClient("c-02", "D. Cruz", "M · Walk-in · 1 visit", "M · 28"),
            BsClient("c-03", "E. Santos", "F · QC · 6 visits", "F · 41"),
            BsClient("c-04", "G. Lim", "M · QC · 2 visits", "M · 52"),
            BsClient("c-05", "H. Uy", "M · Laguna · 4 visits", "M · 37"),
            BsClient("c-06", "I. Valencia", "F · Tondo · mission list", "F · 63", anonymized = true),
        )

    val remittances =
        mutableStateListOf(
            BsRemittance("r-11", BsRemitKind.SESSION, BsRemitStatus.DRAFT, 3250, "Today · QC Central"),
            BsRemittance("r-12", BsRemitKind.PRODUCT, BsRemitStatus.DRAFT, 1400, "Today · QC Central"),
            BsRemittance(
                "r-10",
                BsRemitKind.SESSION,
                BsRemitStatus.SUBMITTED,
                9800,
                "Yesterday · QC Central",
                snapshotNo = "SN-1042",
            ),
        )

    val mailbox =
        mutableStateListOf(
            BsMail(
                "m-1",
                "Relief bell from Laguna",
                "Laguna Tour Stop 3 rings for one relief lane tomorrow.",
                "Today",
                read = false,
            ),
            BsMail(
                "m-2",
                "Snapshot SN-1042 sealed",
                "Yesterday SESSION drawer sealed by R. Dizon.",
                "Yesterday",
                read = false,
            ),
            BsMail(
                "m-3",
                "Mission ferry manifest",
                "Tondo mission ferry leaves 06:00. Two lanes confirmed.",
                "Yesterday",
                read = true,
            ),
        )

    val audit =
        mutableStateListOf(
            BsAudit("a-1", "R. Dizon", "SEAL", "SN-1042", "Yesterday 18:02", "Day close"),
            BsAudit("a-2", "J. Ramos", "COMPLETE", "S-305", "Today 08:40"),
            BsAudit("a-3", "R. Dizon", "MARK NO_SHOW", "S-306", "Today 09:05", "Booked, never arrived"),
        )

    val invites =
        mutableStateListOf(
            BsInvite("i-1", "Laguna Tour Stop 3", "Tomorrow"),
            BsInvite("i-2", "Tondo Medical Mission", "Saturday"),
        )

    val requests =
        mutableStateListOf(
            BsRequest("q-1", "Laguna Tour Stop 3", "Tomorrow", mine = false),
            BsRequest("q-2", "QC Central", "Today", mine = true),
        )

    var signalSeq = 309
    var remitSeq = 13
    var auditSeq = 4
    var mailSeq = 4

    fun branchName(id: String): String = branches.firstOrNull { it.id == id }?.name ?: id

    fun pendingFor(branchId: String): Int =
        sessions.count { it.branchId == branchId && it.status == BsSessionStatus.PENDING }

    fun noShowFor(branchId: String): Int =
        sessions.count { it.branchId == branchId && it.status == BsSessionStatus.NO_SHOW }

    fun lampFor(branchId: String): Pair<BsLamp, String> {
        if (dayState.value == BsDayState.REMITTED) {
            val snap = remittances.firstOrNull { it.status == BsRemitStatus.SUBMITTED }?.snapshotNo ?: "sealed"
            return BsLamp.RED to "REMITTED · $snap · hands off"
        }
        if (noShowFor(branchId) >= 2) return BsLamp.RED to "${noShowFor(branchId)} no-shows · harbor master eyes on"
        if (dayState.value == BsDayState.PAST) return BsLamp.AMBER to "PAST day · seal the drawer"
        if (pendingFor(branchId) >= 3) return BsLamp.AMBER to "${pendingFor(branchId)} pending signals"
        return BsLamp.GREEN to if (pendingFor(branchId) == 0) "clear water" else "${pendingFor(branchId)} pending"
    }

    fun log(
        actor: String,
        action: String,
        record: String,
        reason: String = "",
    ) {
        audit.add(0, BsAudit("a-${auditSeq++}", actor, action, record, "Today now", reason))
    }
}
