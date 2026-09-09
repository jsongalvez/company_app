package com.companyb.companyapp.proto.cashdrawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// #785 — cash-drawer prototype fake data. Local only: no ApiClient, no Ktor,
// no backend, no network. Domain terms follow CONTEXT.md exactly. Money is kept
// in centavos (Int) so counted-vs-expected math stays exact.

enum class CdSessionStatus { PENDING, COMPLETED, NO_SHOW, CANCELLED }
enum class CdDayStatus { OPEN, PAST, REMITTED }
enum class CdScreen { COUNT, HOME, SESSIONS, CLIENTS, FINANCE, TEAM, MAIL, AUDIT, PROFILE }

data class CdUser(
    val id: String,
    val login: String,
    val name: String,
    val role: String,
    val homeBranchId: String,
    val capabilities: List<String>,
    val locked: Boolean = false,
)

data class CdBranch(
    val id: String,
    val name: String,
    val kind: String,
    var dayStatus: CdDayStatus,
)

data class CdDenom(
    val code: String,
    val label: String,
    val centavos: Int,
)

data class CdCountLine(
    val denom: CdDenom,
    var expectedQty: Int,
    var countedQty: Int,
) {
    val expectedTotal: Int get() = expectedQty * denom.centavos
    val countedTotal: Int get() = countedQty * denom.centavos
    val variance: Int get() = countedTotal - expectedTotal
}

data class CdSession(
    val id: String,
    val time: String,
    val clientName: String,
    val branchId: String,
    val kind: String,
    val walkIn: Boolean,
    var status: CdSessionStatus,
    val priceCentavos: Int,
    val practitioners: String,
    var voided: Boolean = false,
    var voidReason: String = "",
)

data class CdClient(
    val id: String,
    val name: String,
    val contact: String,
    var pendingCount: Int,
    val anonymized: Boolean = false,
    val gender: String = "",
    val age: Int = 0,
)

data class CdRemittance(
    val id: String,
    val flow: String,
    var stage: String,
    val amountCentavos: Int,
    val branchName: String,
    val ageHours: Int? = null,
    val fromCount: Boolean = false,
)

data class CdNotice(
    val id: String,
    val title: String,
    val body: String,
    var read: Boolean,
)

data class CdAuditEntry(
    val seq: Int,
    val actor: String,
    val action: String,
)

data class CdReliefItem(
    val id: String,
    val kind: String,
    val text: String,
    var state: String,
)

val CdDenominations = listOf(
    CdDenom("P1000", "₱1,000 bill", 100000),
    CdDenom("P500", "₱500 bill", 50000),
    CdDenom("P200", "₱200 bill", 20000),
    CdDenom("P100", "₱100 bill", 10000),
    CdDenom("P50", "₱50 bill", 5000),
    CdDenom("P20B", "₱20 bill", 2000),
    CdDenom("P20C", "₱20 coin", 2000),
    CdDenom("P10", "₱10 coin", 1000),
    CdDenom("P5", "₱5 coin", 500),
    CdDenom("P1", "₱1 coin", 100),
    CdDenom("C25", "25¢ coin", 25),
)

class CashDrawerRepo {
    val users = listOf(
        CdUser(
            "u-ana", "ana", "Ana Reyes", "Practitioner", "b-makati",
            listOf("LOG_SESSIONS", "VIEW_CLIENTS", "COUNT_DRAWER"),
        ),
        CdUser(
            "u-ben", "ben", "Ben Cruz", "Coordinator", "b-makati",
            listOf("EDIT_PAST", "SUBMIT_REMITTANCE", "VIEW_CLIENTS", "COUNT_DRAWER"),
        ),
        CdUser(
            "u-cara", "cara", "Cara Lim", "MANAGER", "b-bgc",
            listOf("MANAGE_USERS", "ASSIGN_DELEGATES", "SUBMIT_REMITTANCE", "EDIT_PAST", "COUNT_DRAWER"),
        ),
        CdUser("u-dan", "dan", "Dan Uy", "Accountant", "b-makati", listOf("VIEW_BRANCH_DATA", "COUNT_DRAWER")),
        CdUser("u-eli", "eli", "Eli Santos", "ONBOARDING", "b-makati", emptyList(), locked = true),
    )

    val branches = listOf(
        CdBranch("b-makati", "MAKATI", "CLINIC", CdDayStatus.OPEN),
        CdBranch("b-bgc", "BGC", "CLINIC", CdDayStatus.PAST),
        CdBranch("b-cebu", "CEBU-TOUR", "PROVINCIAL_TOUR", CdDayStatus.REMITTED),
        CdBranch("b-tondo", "TONDO-MISSION", "MEDICAL_MISSION", CdDayStatus.OPEN),
    )

    val countLines = mutableStateListOf<CdCountLine>()
    var countBranchId by mutableStateOf("")
    var countSealed by mutableStateOf(false)

    fun openCount(branchId: String) {
        if (countBranchId == branchId && countLines.isNotEmpty()) return
        countBranchId = branchId
        countSealed = false
        countLines.clear()
        val seed = (branchId.hashCode() and 0x7fffffff) % 5
        val expected = listOf(14 + seed, 9, 6 + seed, 22, 11, 8, 5, 17, 12, 30, 40 - seed)
        CdDenominations.forEachIndexed { i, denom ->
            countLines.add(CdCountLine(denom, expected[i], 0))
        }
    }

    val expectedTotal: Int get() = countLines.sumOf { it.expectedTotal }
    val countedTotal: Int get() = countLines.sumOf { it.countedTotal }
    val varianceTotal: Int get() = countedTotal - expectedTotal
    val countedLines: Int get() = countLines.count { it.countedQty > 0 }

    val sessions = mutableStateListOf(
        CdSession("s-01", "09:00", "MARIA CLARA", "b-makati", "Follow-up", false, CdSessionStatus.COMPLETED, 120000, "ANA REYES"),
        CdSession("s-02", "10:00", "JOSE RIZAL", "b-makati", "Initial", false, CdSessionStatus.PENDING, 150000, "ANA REYES"),
        CdSession("s-03", "11:30", "WALK-IN GUEST", "b-makati", "Walk-in", true, CdSessionStatus.PENDING, 100000, "ANA REYES"),
        CdSession("s-04", "13:00", "LIZA S.", "b-makati", "Follow-up", false, CdSessionStatus.NO_SHOW, 120000, "ANA REYES"),
        CdSession("s-05", "14:30", "NORA A.", "b-makati", "Initial", false, CdSessionStatus.CANCELLED, 150000, "BEN CRUZ"),
        CdSession("s-06", "15:00", "FPJ", "b-bgc", "Follow-up", false, CdSessionStatus.PENDING, 120000, "CARA LIM"),
    )

    val clients = mutableStateListOf(
        CdClient("c-01", "MARIA CLARA", "0917-111-0001", 0),
        CdClient("c-02", "JOSE RIZAL", "0917-111-0002", 1),
        CdClient("c-03", "LIZA S.", "0917-111-0003", 0),
        CdClient("c-04", "SEALED #A17", "--redacted--", 0, anonymized = true, gender = "F", age = 42),
        CdClient("c-05", "NORA A.", "0917-111-0005", 0),
    )

    val remittances = mutableStateListOf(
        CdRemittance("r-01", "SESSION", "DRAFT", 1840000, "MAKATI"),
        CdRemittance("r-02", "PRODUCT", "DRAFT", 520000, "MAKATI"),
        CdRemittance("r-03", "SESSION", "SUBMITTED", 2410000, "CEBU-TOUR", ageHours = 30),
        CdRemittance("r-04", "SESSION", "SUBMITTED", 1980000, "BGC", ageHours = 80),
    )

    val notices = mutableStateListOf(
        CdNotice("n-01", "DRAWER SHORT", "MAKATI drawer counted ₱350 short at noon audit. Recount straps P500.", false),
        CdNotice("n-02", "RELIEF GRANT", "CARA LIM granted you relief edit access at BGC for today.", false),
        CdNotice("n-03", "REMITTANCE SEALED", "CEBU-TOUR SESSION snapshot frozen. Undo window 48h.", true),
    )

    val audit = mutableStateListOf(
        CdAuditEntry(3, "system", "count sheet opened for MAKATI"),
        CdAuditEntry(2, "cara", "grant relief edit @BGC day=today"),
        CdAuditEntry(1, "ana", "enter counting room"),
    )

    val reliefDuties = mutableStateListOf(
        CdReliefItem("d-01", "DUTY", "BGC relief duty today — edit access GRANTED", "active"),
    )
    val reliefRequests = mutableStateListOf(
        CdReliefItem("q-01", "REQUEST", "broadcast: relief @CEBU-TOUR 2026-09-12 (one live per date)", "live"),
    )
    val reliefInvites = mutableStateListOf(
        CdReliefItem("i-01", "INVITE", "CARA LIM invites you: relief @BGC 2026-09-13", "pending"),
    )

    private var seq = 3
    var sessionCounter by mutableStateOf(7)
    var remittanceCounter by mutableStateOf(5)
    var clockedIn by mutableStateOf(false)

    fun log(actor: String, action: String) {
        seq += 1
        audit.add(0, CdAuditEntry(seq, actor, action))
    }
}
