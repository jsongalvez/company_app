package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.companyb.companyapp.ui.theme.Spacing

// Prototype question: three desktop session-create layouts, switchable in one run from this route.

internal enum class SessionCreatePrototypeVariant(
    val key: String,
    val title: String,
) {
    QUICK_START("A", "Quick start"),
    GUIDED_STEPS("B", "Guided steps"),
    WORKSPACE("C", "Workspace"),
}

internal data class FakeSessionClient(
    val id: String,
    val initials: String,
    val name: String,
    val phone: String,
    val lastVisit: String,
    val sessionCount: String,
    val concern: String,
)

internal class SessionCreatePrototypeState {
    var selectedClientId by mutableStateOf("maya")
    var search by mutableStateOf("")
    var sessionKind by mutableStateOf("Walk-in")
    var price by mutableStateOf("650")
    var selectedConcern by mutableStateOf("Shoulder pain")
    var practitioner by mutableStateOf("No preference")
    var remarks by mutableStateOf("")
    var step by mutableStateOf(0)
    var submitted by mutableStateOf(false)
    var textFieldFocused by mutableStateOf(false)
}

internal data class SessionCreatePrototypeContext(
    val state: SessionCreatePrototypeState,
    val clients: List<FakeSessionClient>,
    val visibleClients: List<FakeSessionClient>,
    val selectedClient: FakeSessionClient,
    val selectClient: (FakeSessionClient) -> Unit,
    val finishPreview: () -> Unit,
)

internal fun previousSessionCreatePrototypeVariant(index: Int): Int {
    val variants = SessionCreatePrototypeVariant.values()
    return if (index == variants.first().ordinal) variants.lastIndex else index.dec()
}

internal fun nextSessionCreatePrototypeVariant(index: Int): Int {
    val variants = SessionCreatePrototypeVariant.values()
    return if (index == variants.lastIndex) variants.first().ordinal else index.inc()
}

internal val sessionCreatePrototypeClients =
    listOf(
        FakeSessionClient(
            id = "maya",
            initials = "MS",
            name = "Maya Santos",
            phone = "0917 555 0182",
            lastVisit = "12 Aug 2026",
            sessionCount = "6 sessions",
            concern = "Shoulder pain",
        ),
        FakeSessionClient(
            id = "leo",
            initials = "LR",
            name = "Leonardo Reyes",
            phone = "0920 555 0114",
            lastVisit = "08 Aug 2026",
            sessionCount = "2 sessions",
            concern = "Lower back pain",
        ),
        FakeSessionClient(
            id = "ana",
            initials = "AC",
            name = "Ana Cruz",
            phone = "0998 555 0240",
            lastVisit = "02 Aug 2026",
            sessionCount = "14 sessions",
            concern = "Post-op recovery",
        ),
        FakeSessionClient(
            id = "noah",
            initials = "NG",
            name = "Noah Garcia",
            phone = "0918 555 0306",
            lastVisit = "29 Jul 2026",
            sessionCount = "1 session",
            concern = "Knee stiffness",
        ),
    )

internal val sessionCreatePrototypeConcerns =
    listOf("Shoulder pain", "Lower back pain", "Post-op recovery", "Knee stiffness")

@Composable
internal fun SessionCreatePrototypeScreen(
    branchName: String?,
    variantIndex: Int,
    onVariantChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state = remember { SessionCreatePrototypeState() }
    val currentVariant =
        SessionCreatePrototypeVariant.values().getOrElse(variantIndex) {
            SessionCreatePrototypeVariant.QUICK_START
        }
    val visibleClients =
        sessionCreatePrototypeClients.filter { client ->
            state.search.isBlank() ||
                client.name.contains(state.search, ignoreCase = true) ||
                client.phone.contains(state.search, ignoreCase = true)
        }
    val selectedClient =
        sessionCreatePrototypeClients.firstOrNull { it.id == state.selectedClientId }
            ?: sessionCreatePrototypeClients.first()
    val context =
        SessionCreatePrototypeContext(
            state = state,
            clients = sessionCreatePrototypeClients,
            visibleClients = visibleClients,
            selectedClient = selectedClient,
            selectClient = { client ->
                state.selectedClientId = client.id
                state.search = ""
            },
            finishPreview = { state.submitted = true },
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .prototypeVariantKeyHandler(state, currentVariant, onVariantChange),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp),
        ) {
            PrototypeHeader(branchName = branchName, onBack = onBack)
            when (currentVariant) {
                SessionCreatePrototypeVariant.QUICK_START -> {
                    PrototypeVariantA(context)
                }

                SessionCreatePrototypeVariant.GUIDED_STEPS -> {
                    PrototypeVariantB(context)
                }

                SessionCreatePrototypeVariant.WORKSPACE -> {
                    PrototypeVariantC(context)
                }
            }
        }
        PrototypeSwitcher(
            currentVariant = currentVariant,
            onVariantChange = onVariantChange,
        )
    }
}

@Composable
internal fun SessionCreatePrototypeApp(onBack: () -> Unit) {
    var variantIndex by rememberSaveable { mutableStateOf(0) }
    LinearTheme {
        SessionCreatePrototypeScreen(
            branchName = "Demo branch · local fake data",
            variantIndex = variantIndex,
            onVariantChange = { variantIndex = it },
            onBack = onBack,
        )
    }
}

private fun Modifier.prototypeVariantKeyHandler(
    state: SessionCreatePrototypeState,
    currentVariant: SessionCreatePrototypeVariant,
    onVariantChange: (Int) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (state.textFieldFocused || event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> {
                    onVariantChange(previousSessionCreatePrototypeVariant(currentVariant.ordinal))
                    true
                }

                Key.DirectionRight -> {
                    onVariantChange(nextSessionCreatePrototypeVariant(currentVariant.ordinal))
                    true
                }

                else -> {
                    false
                }
            }
        }
    }
