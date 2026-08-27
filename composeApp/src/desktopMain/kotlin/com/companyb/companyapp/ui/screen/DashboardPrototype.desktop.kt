package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.LinearTheme

// Prototype question: three desktop dashboard layouts, switchable in one local run.

internal enum class DashboardPrototypeVariant(
    val key: String,
    val title: String,
) {
    PULSE_BOARD("A", "Pulse board"),
    COMMAND_CENTER("B", "Command center"),
    TEAM_WORKSPACE("C", "Team workspace"),
}

internal enum class DashboardPrototypeDataset(
    val key: String,
    val title: String,
) {
    SPARSE("S", "Sparse day"),
    BUSY("B", "Busy day"),
}

internal enum class DashboardPrototypeSection(
    val label: String,
) {
    TODAY("Today"),
    ROSTER("Roster"),
    RELIEF("Relief"),
}

internal enum class FakeSessionStatus(
    val label: String,
) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    NO_SHOW("No-show"),
    CANCELLED("Cancelled"),
}

private const val BUSY_COORDINATOR_SESSION_COUNT = 3
private val PrototypeBottomPadding = 76.dp

internal data class FakeDashboardSession(
    val id: String,
    val time: String,
    val client: String,
    val type: String,
    val status: FakeSessionStatus,
    val finalPrice: String,
    val practitioner: String,
    val concern: String,
    val booked: Boolean,
)

internal data class FakeDashboardStaff(
    val id: String,
    val initials: String,
    val name: String,
    val role: String,
    val slot: String,
    val attendance: String,
    val shift: String,
    val sessionCount: Int,
    val isRelief: Boolean,
)

internal data class FakeReliefCard(
    val id: String,
    val title: String,
    val detail: String,
    val status: String,
    val action: String,
)

internal data class FakeDashboardData(
    val date: String,
    val branch: String,
    val completedIncome: String,
    val commission: String,
    val sessionsLabel: String,
    val unreadNotifications: Int,
    val sessions: List<FakeDashboardSession>,
    val staff: List<FakeDashboardStaff>,
    val reliefCards: List<FakeReliefCard>,
)

internal class DashboardPrototypeState {
    var selectedStaffId by mutableStateOf("lia")
    var selectedSessionId by mutableStateOf("s1")
    var activeSection by mutableStateOf(DashboardPrototypeSection.TODAY)
    var textFieldFocused by mutableStateOf(false)
}

internal data class DashboardPrototypeContext(
    val state: DashboardPrototypeState,
    val data: FakeDashboardData,
    val selectedStaff: FakeDashboardStaff,
    val selectedSession: FakeDashboardSession,
    val onStaffSelect: (FakeDashboardStaff) -> Unit,
    val onSessionSelect: (FakeDashboardSession) -> Unit,
)

internal fun previousDashboardPrototypeVariant(index: Int): Int {
    val variants = DashboardPrototypeVariant.values()
    return if (index == variants.first().ordinal) variants.lastIndex else index.dec()
}

internal fun nextDashboardPrototypeVariant(index: Int): Int {
    val variants = DashboardPrototypeVariant.values()
    return if (index == variants.lastIndex) variants.first().ordinal else index.inc()
}

internal val sparseDashboardData =
    FakeDashboardData(
        date = "Thursday, 27 Aug 2026",
        branch = "Makati Central",
        completedIncome = "PHP 650",
        commission = "PHP 180",
        sessionsLabel = "3 sessions",
        unreadNotifications = 2,
        sessions =
            listOf(
                FakeDashboardSession(
                    id = "s1",
                    time = "09:00",
                    client = "Maya Santos",
                    type = "Regular",
                    status = FakeSessionStatus.COMPLETED,
                    finalPrice = "PHP 650",
                    practitioner = "Lia Ramos",
                    concern = "Shoulder pain",
                    booked = true,
                ),
                FakeDashboardSession(
                    id = "s2",
                    time = "11:30",
                    client = "Leonardo Reyes",
                    type = "Second",
                    status = FakeSessionStatus.PENDING,
                    finalPrice = "PHP 800",
                    practitioner = "Noel Garcia",
                    concern = "Lower back pain",
                    booked = true,
                ),
                FakeDashboardSession(
                    id = "s3",
                    time = "15:00",
                    client = "Ana Cruz",
                    type = "Regular",
                    status = FakeSessionStatus.NO_SHOW,
                    finalPrice = "PHP 650",
                    practitioner = "Lia Ramos",
                    concern = "Post-op recovery",
                    booked = true,
                ),
            ),
        staff =
            listOf(
                FakeDashboardStaff(
                    id = "lia",
                    initials = "LR",
                    name = "Lia Ramos",
                    role = "Coordinator",
                    slot = "Slot 01",
                    attendance = "Clocked in",
                    shift = "08:00 - 17:00",
                    sessionCount = 2,
                    isRelief = false,
                ),
                FakeDashboardStaff(
                    id = "noel",
                    initials = "NG",
                    name = "Noel Garcia",
                    role = "Practitioner",
                    slot = "Slot 02",
                    attendance = "Clocked in",
                    shift = "09:00 - 14:00",
                    sessionCount = 1,
                    isRelief = false,
                ),
                FakeDashboardStaff(
                    id = "juno",
                    initials = "JV",
                    name = "Juno Velasco",
                    role = "Practitioner",
                    slot = "Relief",
                    attendance = "Relief duty",
                    shift = "10:00 - 16:00",
                    sessionCount = 0,
                    isRelief = true,
                ),
            ),
        reliefCards =
            listOf(
                FakeReliefCard(
                    id = "r1",
                    title = "Juno requested edit access",
                    detail = "Today / Makati Central",
                    status = "Awaiting grant",
                    action = "Review",
                ),
                FakeReliefCard(
                    id = "r2",
                    title = "Shift invite accepted",
                    detail = "Mara Lim / tomorrow",
                    status = "Accepted",
                    action = "View",
                ),
            ),
    )

internal val busyDashboardData =
    FakeDashboardData(
        date = "Thursday, 27 Aug 2026",
        branch = "Makati Central",
        completedIncome = "PHP 3,950",
        commission = "PHP 760",
        sessionsLabel = "8 sessions",
        unreadNotifications = 6,
        sessions =
            listOf(
                FakeDashboardSession(
                    "s1",
                    "08:30",
                    "Maya Santos",
                    "Regular",
                    FakeSessionStatus.COMPLETED,
                    "PHP 650",
                    "Lia Ramos",
                    "Shoulder pain",
                    true,
                ),
                FakeDashboardSession(
                    "s2",
                    "09:15",
                    "Leo Reyes",
                    "Second",
                    FakeSessionStatus.COMPLETED,
                    "PHP 800",
                    "Noel Garcia",
                    "Lower back pain",
                    true,
                ),
                FakeDashboardSession(
                    "s3",
                    "10:00",
                    "Ana Cruz",
                    "Subsequent",
                    FakeSessionStatus.PENDING,
                    "PHP 650",
                    "Priya Dela Cruz",
                    "Post-op recovery",
                    false,
                ),
                FakeDashboardSession(
                    "s4",
                    "10:45",
                    "Noah Garcia",
                    "Regular",
                    FakeSessionStatus.PENDING,
                    "PHP 650",
                    "Juno Velasco",
                    "Knee stiffness",
                    true,
                ),
                FakeDashboardSession(
                    "s5",
                    "11:30",
                    "Bea Navarro",
                    "Second",
                    FakeSessionStatus.COMPLETED,
                    "PHP 800",
                    "Lia Ramos",
                    "Neck tension",
                    true,
                ),
                FakeDashboardSession(
                    "s6",
                    "13:00",
                    "Iris Tan",
                    "Subsequent",
                    FakeSessionStatus.PENDING,
                    "PHP 800",
                    "Priya Dela Cruz",
                    "Hip mobility",
                    true,
                ),
                FakeDashboardSession(
                    "s7",
                    "14:15",
                    "Owen Lim",
                    "Regular",
                    FakeSessionStatus.NO_SHOW,
                    "PHP 650",
                    "Noel Garcia",
                    "Ankle recovery",
                    true,
                ),
                FakeDashboardSession(
                    "s8",
                    "16:00",
                    "Nina Flores",
                    "Second",
                    FakeSessionStatus.CANCELLED,
                    "PHP 800",
                    "Juno Velasco",
                    "Wrist pain",
                    true,
                ),
            ),
        staff =
            listOf(
                FakeDashboardStaff(
                    "lia",
                    "LR",
                    "Lia Ramos",
                    "Coordinator",
                    "Slot 01",
                    "Clocked in",
                    "08:00 - 17:00",
                    BUSY_COORDINATOR_SESSION_COUNT,
                    false,
                ),
                FakeDashboardStaff(
                    "noel",
                    "NG",
                    "Noel Garcia",
                    "Practitioner",
                    "Slot 02",
                    "Clocked in",
                    "08:30 - 16:30",
                    2,
                    false,
                ),
                FakeDashboardStaff(
                    "priya",
                    "PD",
                    "Priya Dela Cruz",
                    "Practitioner",
                    "Slot 03",
                    "Clocked in",
                    "09:00 - 18:00",
                    2,
                    false,
                ),
                FakeDashboardStaff(
                    "juno",
                    "JV",
                    "Juno Velasco",
                    "Practitioner",
                    "Relief",
                    "Relief duty",
                    "10:00 - 16:00",
                    2,
                    true,
                ),
                FakeDashboardStaff(
                    "mara",
                    "ML",
                    "Mara Lim",
                    "Coordinator",
                    "Slot 04",
                    "Clocked in",
                    "08:00 - 15:00",
                    0,
                    false,
                ),
                FakeDashboardStaff(
                    "ravi",
                    "RS",
                    "Ravi Santos",
                    "Practitioner",
                    "Slot 05",
                    "Not clocked in",
                    "-",
                    0,
                    false,
                ),
            ),
        reliefCards =
            listOf(
                FakeReliefCard(
                    "r1",
                    "Juno requested edit access",
                    "Today / Makati Central",
                    "Awaiting grant",
                    "Review",
                ),
                FakeReliefCard(
                    "r2",
                    "Ravi invited to relief duty",
                    "Tomorrow / Makati Central",
                    "Pending response",
                    "Open invite",
                ),
                FakeReliefCard("r3", "Relief access granted", "Juno / today", "Active until 04:00", "View duty"),
            ),
    )

internal fun dashboardPrototypeData(dataset: DashboardPrototypeDataset): FakeDashboardData =
    when (dataset) {
        DashboardPrototypeDataset.SPARSE -> sparseDashboardData
        DashboardPrototypeDataset.BUSY -> busyDashboardData
    }

@Composable
internal fun DashboardPrototypeScreen(
    variantIndex: Int,
    datasetIndex: Int,
    onVariantChange: (Int) -> Unit,
    onDatasetChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state = remember { DashboardPrototypeState() }
    val currentVariant =
        DashboardPrototypeVariant.values().getOrElse(variantIndex) {
            DashboardPrototypeVariant.PULSE_BOARD
        }
    val currentDataset =
        DashboardPrototypeDataset.values().getOrElse(datasetIndex) {
            DashboardPrototypeDataset.BUSY
        }
    val data = dashboardPrototypeData(currentDataset)
    val selectedStaff = data.staff.firstOrNull { it.id == state.selectedStaffId } ?: data.staff.first()
    val selectedSession = data.sessions.firstOrNull { it.id == state.selectedSessionId } ?: data.sessions.first()
    val context =
        DashboardPrototypeContext(
            state = state,
            data = data,
            selectedStaff = selectedStaff,
            selectedSession = selectedSession,
            onStaffSelect = { staff -> state.selectedStaffId = staff.id },
            onSessionSelect = { session -> state.selectedSessionId = session.id },
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .dashboardPrototypeKeyHandler(state, currentVariant, onVariantChange),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = PrototypeBottomPadding),
        ) {
            DashboardPrototypeHeader(data = data, onBack = onBack)
            DashboardDatasetSwitcher(currentDataset = currentDataset, onDatasetChange = onDatasetChange)
            when (currentVariant) {
                DashboardPrototypeVariant.PULSE_BOARD -> DashboardPrototypeVariantA(context)
                DashboardPrototypeVariant.COMMAND_CENTER -> DashboardPrototypeVariantB(context)
                DashboardPrototypeVariant.TEAM_WORKSPACE -> DashboardPrototypeVariantC(context)
            }
        }
        DashboardPrototypeSwitcher(currentVariant = currentVariant, onVariantChange = onVariantChange)
    }
}

@Composable
internal fun DashboardPrototypeApp(onBack: () -> Unit) {
    var variantIndex by rememberSaveable { mutableStateOf(0) }
    var datasetIndex by rememberSaveable { mutableStateOf(1) }
    LinearTheme {
        DashboardPrototypeScreen(
            variantIndex = variantIndex,
            datasetIndex = datasetIndex,
            onVariantChange = { variantIndex = it },
            onDatasetChange = { datasetIndex = it },
            onBack = onBack,
        )
    }
}

private fun Modifier.dashboardPrototypeKeyHandler(
    state: DashboardPrototypeState,
    currentVariant: DashboardPrototypeVariant,
    onVariantChange: (Int) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (state.textFieldFocused || event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> {
                    onVariantChange(previousDashboardPrototypeVariant(currentVariant.ordinal))
                    true
                }

                Key.DirectionRight -> {
                    onVariantChange(nextDashboardPrototypeVariant(currentVariant.ordinal))
                    true
                }

                else -> {
                    false
                }
            }
        }
    }
