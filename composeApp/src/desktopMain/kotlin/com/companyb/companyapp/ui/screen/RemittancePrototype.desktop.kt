package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.companyb.companyapp.util.logInfo

// Prototype question: which desktop remittance submission flow gives Coordinators the safest
// review of covered days, line totals, and the frozen financial snapshot?

internal enum class RemittancePrototypeVariant(
    val key: String,
    val title: String,
) {
    GUIDED_REVIEW("A", "Guided review"),
    CONTROL_DESK("B", "Control desk"),
    DRAFT_NAVIGATOR("C", "Draft navigator"),
}

internal enum class FakeRemittanceMethod(
    val label: String,
) {
    BANK_TRANSFER("Bank transfer"),
    HANDED_TO_ACCOUNTANT("Handed to accountant"),
}

internal data class FakeRemittanceLine(
    val kind: String,
    val label: String,
    val detail: String,
    val amount: String,
)

internal data class FakeRemittanceDraft(
    val id: String,
    val type: String,
    val range: String,
    val days: String,
    val lines: List<FakeRemittanceLine>,
    val gross: String,
    val compensation: String,
    val expenses: String,
    val net: String,
    val total: String,
)

internal data class FakeSubmittedRemittance(
    val type: String,
    val range: String,
    val submittedAt: String,
    val method: FakeRemittanceMethod,
    val total: String,
)

internal class RemittancePrototypeState {
    var selectedDraftId by mutableStateOf("healot-draft")
    var selectedRangeIndex by mutableStateOf(0)
    var method by mutableStateOf(FakeRemittanceMethod.BANK_TRANSFER)
    var note by mutableStateOf("Transfer to the main operating account")
    var activeStep by mutableStateOf(0)
    var submitted by mutableStateOf(false)
    var textFieldFocused by mutableStateOf(false)
}

internal data class RemittancePrototypeContext(
    val state: RemittancePrototypeState,
    val drafts: List<FakeRemittanceDraft>,
    val history: List<FakeSubmittedRemittance>,
    val selectedDraft: FakeRemittanceDraft,
    val selectedRange: String,
    val selectDraft: (FakeRemittanceDraft) -> Unit,
    val selectRange: (Int) -> Unit,
    val confirmSubmission: () -> Unit,
)

internal fun previousRemittancePrototypeVariant(index: Int): Int {
    val variants = RemittancePrototypeVariant.values()
    return if (index == variants.first().ordinal) variants.lastIndex else index.dec()
}

internal fun nextRemittancePrototypeVariant(index: Int): Int {
    val variants = RemittancePrototypeVariant.values()
    return if (index == RemittancePrototypeVariant.values().lastIndex) {
        RemittancePrototypeVariant.values().first().ordinal
    } else {
        index.inc()
    }
}

internal val remittancePrototypeRanges = listOf("24–27 Aug", "24–26 Aug", "27 Aug only")

internal fun remittanceDraftForRange(
    draft: FakeRemittanceDraft,
    range: String,
): FakeRemittanceDraft =
    when {
        range == remittancePrototypeRanges.first() -> {
            draft
        }

        range == "24–26 Aug" && draft.type == "HEALot" -> {
            draft.copy(
                range = "$range 2026",
                days = "3 covered days",
                lines = draft.lines.drop(2),
                gross = "₱6,000",
                compensation = "₱1,300",
                expenses = "₱450",
                net = "₱4,250",
                total = "₱6,000",
            )
        }

        range == "24–26 Aug" -> {
            draft.copy(
                range = "$range 2026",
                days = "3 covered days",
                lines = draft.lines.drop(1),
                total = "₱3,550",
            )
        }

        range == "27 Aug only" && draft.type == "HEALot" -> {
            draft.copy(
                range = "$range 2026",
                days = "1 covered day",
                lines = draft.lines.take(2),
                gross = "₱4,500",
                compensation = "₱1,100",
                expenses = "₱200",
                net = "₱3,200",
                total = "₱4,500",
            )
        }

        else -> {
            draft.copy(
                range = "$range 2026",
                days = "1 covered day",
                lines = draft.lines.take(1),
                total = "₱2,400",
            )
        }
    }

internal val remittancePrototypeDrafts =
    listOf(
        FakeRemittanceDraft(
            id = "healot-draft",
            type = "HEALot",
            range = "24–27 Aug 2026",
            days = "4 covered days",
            lines =
                listOf(
                    FakeRemittanceLine("SESSION", "Maya Santos", "27 Aug · Completed", "₱2,500"),
                    FakeRemittanceLine("SESSION", "Leonardo Reyes", "27 Aug · Completed", "₱2,000"),
                    FakeRemittanceLine("SESSION", "Ana Cruz", "26 Aug · Completed", "₱1,500"),
                    FakeRemittanceLine("SESSION", "Noah Garcia", "25 Aug · Completed", "₱2,500"),
                    FakeRemittanceLine("SESSION", "Bea Navarro", "24 Aug · Completed", "₱2,000"),
                ),
            gross = "₱10,500",
            compensation = "₱2,400",
            expenses = "₱650",
            net = "₱7,450",
            total = "₱10,500",
        ),
        FakeRemittanceDraft(
            id = "product-draft",
            type = "Product",
            range = "24–27 Aug 2026",
            days = "4 covered days",
            lines =
                listOf(
                    FakeRemittanceLine("PRODUCT", "Magnesium Spray", "4 units · 27 Aug", "₱2,400"),
                    FakeRemittanceLine("PRODUCT", "Big Roll On", "3 units · 26 Aug", "₱1,950"),
                    FakeRemittanceLine("PRODUCT", "Potassium", "2 units · 24 Aug", "₱1,600"),
                ),
            gross = "—",
            compensation = "—",
            expenses = "—",
            net = "—",
            total = "₱5,950",
        ),
    )

internal val remittancePrototypeHistory =
    listOf(
        FakeSubmittedRemittance(
            type = "HEALot",
            range = "18–23 Aug 2026",
            submittedAt = "Submitted 23 Aug · 17:42",
            method = FakeRemittanceMethod.BANK_TRANSFER,
            total = "₱12,840",
        ),
        FakeSubmittedRemittance(
            type = "Product",
            range = "18–23 Aug 2026",
            submittedAt = "Submitted 23 Aug · 17:48",
            method = FakeRemittanceMethod.HANDED_TO_ACCOUNTANT,
            total = "₱8,260",
        ),
    )

@Composable
internal fun RemittancePrototypeScreen(
    variantIndex: Int,
    onVariantChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val state = remember { RemittancePrototypeState() }
    val currentVariant =
        RemittancePrototypeVariant.values().getOrElse(variantIndex) {
            RemittancePrototypeVariant.GUIDED_REVIEW
        }
    val selectedDraftBase =
        remittancePrototypeDrafts.firstOrNull { it.id == state.selectedDraftId }
            ?: remittancePrototypeDrafts.first()
    val selectedRange =
        remittancePrototypeRanges.getOrElse(state.selectedRangeIndex) {
            remittancePrototypeRanges.first()
        }
    val selectedDraft = remittanceDraftForRange(selectedDraftBase, selectedRange)
    val context =
        RemittancePrototypeContext(
            state = state,
            drafts = remittancePrototypeDrafts,
            history = remittancePrototypeHistory,
            selectedDraft = selectedDraft,
            selectedRange = selectedRange,
            selectDraft = { draft ->
                state.selectedDraftId = draft.id
                state.selectedRangeIndex = 0
                state.submitted = false
            },
            selectRange = { index ->
                state.selectedRangeIndex = index
                state.submitted = false
            },
            confirmSubmission = { state.submitted = true },
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .remittancePrototypeKeyHandler(state, currentVariant, onVariantChange),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 78.dp),
        ) {
            RemittancePrototypeHeader(onBack = onBack)
            when (currentVariant) {
                RemittancePrototypeVariant.GUIDED_REVIEW -> RemittancePrototypeVariantA(context)
                RemittancePrototypeVariant.CONTROL_DESK -> RemittancePrototypeVariantB(context)
                RemittancePrototypeVariant.DRAFT_NAVIGATOR -> RemittancePrototypeVariantC(context)
            }
        }
        RemittancePrototypeSwitcher(
            currentVariant = currentVariant,
            onVariantChange = onVariantChange,
        )
    }
}

@Composable
internal fun RemittancePrototypeApp(onBack: () -> Unit) {
    var variantIndex by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        logInfo("RemittancePrototypeScreen", "composable entered (first composition)")
    }
    LinearTheme {
        RemittancePrototypeScreen(
            variantIndex = variantIndex,
            onVariantChange = { variantIndex = it },
            onBack = onBack,
        )
    }
}

private fun Modifier.remittancePrototypeKeyHandler(
    state: RemittancePrototypeState,
    currentVariant: RemittancePrototypeVariant,
    onVariantChange: (Int) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (state.textFieldFocused || event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> {
                    onVariantChange(previousRemittancePrototypeVariant(currentVariant.ordinal))
                    true
                }

                Key.DirectionRight -> {
                    onVariantChange(nextRemittancePrototypeVariant(currentVariant.ordinal))
                    true
                }

                else -> {
                    false
                }
            }
        }
    }
