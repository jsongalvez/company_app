package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.viewmodel.UiState

/** Editable fields on the client detail screen (D4) — identity + contact/health, per-field edit. */
internal enum class ClientField {
    FIRST_NAME,
    MIDDLE_NAME,
    LAST_NAME,
    SUFFIX,
    GENDER,
    AGE,
    PHONE,
    ADDRESS,

    // D4 — BP is a pair, edited + committed together (both-or-neither rule).
    BP_PAIR,
    MEDICAL_CONDITIONS,
}

/**
 * Shared edit snapshot for one field row (#476 LPL burn — the row editors read one carrier
 * instead of four separate params).
 */
internal data class ClientFieldEditState(
    val editingField: ClientField?,
    val draftValue: String,
    val fieldError: String?,
    val navigationLocked: Boolean,
)

/** Field identity + display for [ClientFieldEditor] (#476 LPL burn). */
internal data class ClientFieldSpec(
    val label: String,
    val value: String,
    val field: ClientField,
    val keyboardType: KeyboardType = KeyboardType.Text,
)

/**
 * Edit callbacks for the content-body subtree (#476 LPL burn — one carrier for the section
 * and row editors; each reader documents which half it uses).
 */
internal data class ClientDetailCallbacks(
    val onDraftChange: (String) -> Unit,
    val onStartEdit: (ClientField) -> Unit,
    val onCommit: (ClientField) -> Unit,
    val onCancel: () -> Unit,
    val onCommitBp: () -> Unit,
    val onBpDraftChanged: () -> Unit,
    val onAnonymizeClick: () -> Unit,
)

/** Landing-resolution callbacks for [ClientDetailUpdateEffects] (#476 LPL burn — 6 params → 4). */
internal data class ClientUpdateCallbacks(
    val onEditClear: () -> Unit,
    val onClearPending: () -> Unit,
    val onFieldError: (String) -> Unit,
)

/** Display + draft snapshot for the BP-pair editor (#476 LPL burn — 10 params → 2). */
internal data class BpPairState(
    val systolic: Short?,
    val diastolic: Short?,
    val editing: Boolean,
    val fieldError: String?,
    val draft: BpDraftState,
    val enabled: Boolean,
)

/**
 * Live-commit dependencies for the edit session (#476 Cyclomatic burn — the commit/start
 * helpers left [ClientDetailContent] for here, reading explicit deps instead of composable
 * captures). The `live*` reads are synchronous flow values (no composition lag — the
 * disposal-blur and supersede gates depend on it; see [shouldAbandonFailedDraft]).
 */
internal data class ClientEditDeps(
    val navigationLocked: Boolean,
    val updateState: UiState<ClientResponse>,
    val anonymizeState: UiState<Unit>,
    val liveDetailState: () -> UiState<ClientResponse>,
    val liveUpdateState: () -> UiState<*>,
    val onPatch: (UpdateClientRequest) -> Unit,
)

/**
 * Hoisted edit-session state for [ClientDetailContent] (#476 Cyclomatic burn — the local commit
 * helpers carried the content composable to 35/15; as members each owns its own budget).
 * Remembered by the content; snapshots ([ClientFieldEditState]) and callbacks flow down.
 */
internal class ClientEditSession {
    var editingField by mutableStateOf<ClientField?>(null)
    var draftValue by mutableStateOf("")
    var fieldError by mutableStateOf<String?>(null)
    var pendingEditField by mutableStateOf<ClientField?>(null)
    var lastDispatched by mutableStateOf<DispatchedDraft?>(null)
    val bpDraft = BpDraftState()

    // H4 — mutual exclusion with in-flight mutations: a new edit can't open while a PATCH or the
    // anonymize POST is in flight (an anonymize in flight must never race a fresh PATCH).
    // Switching fields commits the superseded field's draft first — its typed value must not be
    // silently dropped by the disposal-blur (which the editingField != field guard would drop).
    fun exitEdit() {
        editingField = null
        draftValue = ""
        fieldError = null
        lastDispatched = null
    }

    // Typing clears the live error: a draft modified after a failed PATCH is a fresh attempt
    // (the supersede gate compares the dispatch record), and a validation error vanishes once
    // the input is corrected.
    fun handleDraftChange(value: String) {
        draftValue = value
        fieldError = null
    }

    fun clearBpError() {
        fieldError = null
    }

    /** Snapshot for the row editors — rebuilt every composition from live session state. */
    fun snapshot(navigationLocked: Boolean): ClientFieldEditState =
        ClientFieldEditState(
            editingField = editingField,
            draftValue = draftValue,
            fieldError = fieldError,
            navigationLocked = navigationLocked,
        )

    /** Subtree callbacks bound to [client] + [deps] (#476 — keeps ClientDetailContent short). */
    fun callbacks(
        client: ClientResponse,
        deps: ClientEditDeps,
        onAnonymizeClick: () -> Unit,
    ): ClientDetailCallbacks =
        ClientDetailCallbacks(
            onDraftChange = ::handleDraftChange,
            onStartEdit = { startEdit(it, client, deps) },
            onCommit = { commitEdit(it, client, deps) },
            onCancel = ::exitEdit,
            onCommitBp = { commitBpDrafts(client, deps) },
            onBpDraftChanged = ::clearBpError,
            onAnonymizeClick = onAnonymizeClick,
        )

    // Synchronous record of the last dispatched PATCH's payload — the supersede gate compares
    // the current draft against it (no composition-lagged reads in the gate).
    fun recordDispatchedDraft(
        field: ClientField,
        value: String,
        bpDiastolic: String = "",
    ) {
        lastDispatched = DispatchedDraft(field = field, value = value, bpDiastolic = bpDiastolic)
    }

    // Returns false when the draft is invalid — the caller (startEdit's supersede) then aborts
    // the field switch so the error stays visible on the field that owns it (the draft is not
    // silently dropped, and the error write is not instantly wiped).
    fun commitEdit(
        field: ClientField,
        client: ClientResponse,
        deps: ClientEditDeps,
    ): Boolean {
        if (editingField != field) return true
        if (deps.navigationLocked) return true
        if (deps.updateState is UiState.Loading) return true
        if (deps.anonymizeState is UiState.Loading) return true
        // 409/404 reload guard: the reload's own PATCH already failed and the detail is being
        // re-fetched (clientDetail Loading). The composed updateState has already flipped to
        // Idle by the time the disposal-blur fires on the reload's unmount, so the Loading
        // guard above passes — without this, the identical failed payload would re-dispatch,
        // clobber the reloaded (elsewhere-changed) record and clear the changed-elsewhere
        // banner. The reload owns the exit (the unmount discards the edit state). Live flow
        // value — synchronous, no composition lag.
        if (shouldBailOnReload(deps.liveDetailState())) return true
        val trimmed = draftValue.trim()
        if (trimmed == currentFieldValue(client, field)) {
            exitEdit()
            return true
        }
        val patch = patchFor(field, trimmed) { fieldError = it }
        if (patch == null) return false
        recordDispatchedDraft(field, trimmed)
        pendingEditField = field
        deps.onPatch(patch)
        return true
    }

    // D4 — BP pair commit: validates the hoisted drafts, builds the request; unchanged pair →
    // silent exit. Enter/blur/supersede all route through this. Returns false on invalid drafts
    // (see [commitEdit]).
    fun commitBpDrafts(
        client: ClientResponse,
        deps: ClientEditDeps,
    ): Boolean {
        if (editingField != ClientField.BP_PAIR) return true
        if (deps.navigationLocked) return true
        if (deps.updateState is UiState.Loading) return true
        if (deps.anonymizeState is UiState.Loading) return true
        if (shouldBailOnReload(deps.liveDetailState())) return true
        val sys = bpDraft.systolic.trim()
        val dia = bpDraft.diastolic.trim()
        // Unchanged vs the seeded values (string compare — drafts equal the seed, e.g. the
        // untouched null-BP pair: empty drafts vs null values). The blank-validation would
        // otherwise misreport the untouched null-BP pair as "Both BP fields are required" and
        // abort a supersede, trapping the user in the editor (no Esc on mobile). Note the
        // string compare is stricter than a numeric one: a leading-zero alias of the seed
        // ("0120" vs "120") counts as an edit and dispatches a redundant-but-idempotent PATCH,
        // and clearing a seeded pair reports "Both BP fields are required" (not "valid
        // number") — both acceptable; the abandon gate canonicalizes numerically downstream.
        val seedSystolic = client.systolicBp?.toString().orEmpty()
        val seedDiastolic = client.diastolicBp?.toString().orEmpty()
        val sysVal = sys.toShortOrNull()
        val diaVal = dia.toShortOrNull()
        if (sys == seedSystolic && dia == seedDiastolic) {
            exitEdit()
        } else {
            fieldError =
                when {
                    sys.isEmpty() || dia.isEmpty() -> "Both BP fields are required"
                    sysVal == null || diaVal == null -> "Enter a valid number"
                    else -> null
                }
            if (fieldError != null) return false
            recordDispatchedDraft(ClientField.BP_PAIR, sys, dia)
            pendingEditField = ClientField.BP_PAIR
            deps.onPatch(UpdateClientRequest(systolicBp = sysVal!!, diastolicBp = diaVal!!))
        }
        return true
    }

    fun startEdit(
        field: ClientField,
        client: ClientResponse,
        deps: ClientEditDeps,
    ) {
        if (deps.navigationLocked) return
        if (deps.updateState is UiState.Loading) return
        if (deps.anonymizeState is UiState.Loading) return
        if (editingField != null && editingField != field) {
            // Commit the superseded field's draft first — its typed value must not be silently
            // dropped by the disposal-blur (which the editingField != field guard would drop).
            // The pair routes through its own commit: its drafts live in [bpDraft], not the
            // shared single-field draft, so commitEdit would see an "unchanged" empty draft.
            // An invalid draft aborts the switch — the error stays on the field that owns it.
            // An UNCHANGED failed draft abandons on switch: re-dispatching the identical failed
            // value would be a phantom retry whose second failure is suppressed after the switch
            // (resolvesCurrentEdit false) — the attempted value and error would vanish with zero
            // feedback, breaching D4. The gate is synchronous (the VM's live flow value + the
            // dispatch record + the current draft — no composition-lagged reads): a draft
            // modified since its failure dispatches as a fresh attempt; a validation error
            // (never dispatched) can never match the record, so it aborts instead.
            val committed =
                when {
                    shouldAbandonFailedDraft(
                        updateState = deps.liveUpdateState(),
                        lastDispatchedField = lastDispatched?.field,
                        editingField = editingField,
                        draftMatchesLastDispatched =
                            lastDispatched?.matches(draftValue, bpDraft.systolic, bpDraft.diastolic) == true,
                    ) -> true

                    editingField == ClientField.BP_PAIR -> commitBpDrafts(client, deps)

                    else -> commitEdit(editingField!!, client, deps)
                }
            if (!committed) return
        }
        editingField = field
        draftValue = currentFieldValue(client, field)
        fieldError = null
    }
}

/**
 * Reload bail guard: while the detail flow is Loading, a commit must not dispatch — the
 * 409/404 reload's unmount fires the editing field's disposal-blur, and the composed
 * updateState has already flipped to Idle by then (the 409 handler wrote Idle + triggered the
 * reload in one turn), so the commit's Loading guard alone would let the identical failed
 * payload re-dispatch and clobber the reloaded record. The reload owns the exit. Internal for
 * the unit test (commonTest friend path).
 */
internal fun shouldBailOnReload(detailState: UiState<*>): Boolean = detailState is UiState.Loading

/**
 * Supersede gate: abandon (don't re-dispatch) a draft whose PATCH already failed UNCHANGED.
 *
 * [updateState] must be the VM's LIVE flow value (not a composed snapshot) and
 * [draftMatchesLastDispatched] must compare the current draft against the last dispatched
 * PATCH's payload — both are synchronous reads at the switch. A stale Error from a superseded
 * field (lastDispatchedField ≠ editingField) or a modified draft (no match) falls through to
 * the normal commit path; a validation error never dispatches, so it can never match.
 *
 * Internal for the unit test (commonTest friend path).
 */
internal fun shouldAbandonFailedDraft(
    updateState: UiState<*>,
    lastDispatchedField: ClientField?,
    editingField: ClientField?,
    draftMatchesLastDispatched: Boolean,
): Boolean =
    updateState is UiState.Error &&
        lastDispatchedField != null &&
        lastDispatchedField == editingField &&
        draftMatchesLastDispatched

/**
 * Synchronous record of the last dispatched PATCH's payload (see [shouldAbandonFailedDraft]).
 * For BP pairs, [value] holds the systolic and [bpDiastolic] the diastolic; for single fields,
 * [value] holds the field's value and [bpDiastolic] is unused. The comparison canonicalizes
 * numeric fields by parsing (a leading-zero alias like "0121" vs "121" is the same payload, not
 * a modification) and trims before comparing (trailing spaces tolerated on both numeric and
 * string drafts), so a cosmetic draft difference can't masquerade as a modification. INVARIANT:
 * [value] and [bpDiastolic] always hold validated, parseable payloads — both dispatch sites
 * record only after validation, which is what makes the parse-based comparison null-safe.
 * Internal for the unit test (commonTest friend path).
 */
internal data class DispatchedDraft(
    val field: ClientField,
    val value: String,
    val bpDiastolic: String = "",
) {
    fun matches(
        draftValue: String,
        bpSystolic: String,
        bpDiastolic: String,
    ): Boolean =
        when (field) {
            ClientField.BP_PAIR -> {
                bpSystolic.trim().toShortOrNull() == value.toShortOrNull() &&
                    bpDiastolic.trim().toShortOrNull() == this.bpDiastolic.toShortOrNull()
            }

            ClientField.AGE -> {
                draftValue.trim().toIntOrNull() == value.toIntOrNull()
            }

            else -> {
                draftValue.trim() == value
            }
        }
}

/**
 * D4 — blood-pressure pair draft state, hoisted into the edit session: a field-switch
 * supersede must be able to commit the pair's typed drafts (the editor's local state would be
 * unreachable from `startEdit`). The pair is one logical field: both inputs or neither
 * (backend 400s otherwise); commits both values in one partial PATCH; enterable from null.
 *
 * Blur-commit fires only when BOTH sides were typed in this edit session: (a) with no typing at
 * all, tapping systolic→diastolic blurs field 1 with both drafts holding the seeded values —
 * commitPair would see "unchanged" and cancel the edit before the user typed anything; (b) with
 * only one side typed, tapping the other side would blur-commit the pair with the seeded value
 * for the side the user is on their way to edit — same premature-commit class. With both sides
 * typed, blur commits (or surfaces the inline pair-required error, matching the single-field
 * "Value required" on blank). Enter (Done) always commits explicitly, unchanged pair included
 * (silent exit).
 */
internal class BpDraftState {
    var systolic by mutableStateOf("")
    var diastolic by mutableStateOf("")
    var dirtySystolic by mutableStateOf(false)
    var dirtyDiastolic by mutableStateOf(false)

    fun seed(
        systolic: Short?,
        diastolic: Short?,
    ) {
        this.systolic = systolic?.toString().orEmpty()
        this.diastolic = diastolic?.toString().orEmpty()
        dirtySystolic = false
        dirtyDiastolic = false
    }
}

/** D4 — the field's current display value; also the draft's starting value + unchanged check. */
internal fun currentFieldValue(
    client: ClientResponse,
    field: ClientField,
): String =
    when (field) {
        ClientField.FIRST_NAME -> client.firstName.orEmpty()
        ClientField.MIDDLE_NAME -> client.middleName.orEmpty()
        ClientField.LAST_NAME -> client.lastName.orEmpty()
        ClientField.SUFFIX -> client.suffix.orEmpty()
        ClientField.GENDER -> client.gender.name
        ClientField.AGE -> client.age.toString()
        ClientField.PHONE -> client.phoneNumber.orEmpty()
        ClientField.ADDRESS -> client.address.orEmpty()
        ClientField.BP_PAIR -> ""
        ClientField.MEDICAL_CONDITIONS -> client.medicalConditions.orEmpty()
    }

/**
 * D4 — build the partial PATCH for one committed field (only changed fields are sent; the request
 * is all-nullable). Validation errors are surfaced inline via [onError] and return null (no PATCH).
 *
 * Blank commits are rejected for every field: the backend's UpdateClientRequest treats null as
 * "don't update" (kotlinx serialization explicitNulls), so clearing a value is inexpressible —
 * "Value required" is the honest message rather than a silent no-op. Blank *names* additionally
 * 400 on the backend. BP commits as a pair via [BpPairEditor] (both-or-neither), so it has no
 * branch here.
 */
internal fun patchFor(
    field: ClientField,
    trimmed: String,
    onError: (String) -> Unit,
): UpdateClientRequest? =
    when (field) {
        ClientField.FIRST_NAME -> {
            requireNonBlank(trimmed, "Name can't be blank", onError) { UpdateClientRequest(firstName = it) }
        }

        ClientField.LAST_NAME -> {
            requireNonBlank(trimmed, "Name can't be blank", onError) { UpdateClientRequest(lastName = it) }
        }

        ClientField.MIDDLE_NAME -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(middleName = it) }
        }

        ClientField.SUFFIX -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(suffix = it) }
        }

        ClientField.PHONE -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(phoneNumber = it) }
        }

        ClientField.ADDRESS -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(address = it) }
        }

        ClientField.MEDICAL_CONDITIONS -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(medicalConditions = it) }
        }

        ClientField.GENDER -> {
            UpdateClientRequest(gender = if (trimmed == Gender.M.name) Gender.M else Gender.F)
        }

        ClientField.AGE -> {
            val age = trimmed.toIntOrNull()
            if (age == null) {
                onError("Enter a valid age")
                null
            } else {
                UpdateClientRequest(age = age)
            }
        }

        ClientField.BP_PAIR -> {
            null
        }
    }

private inline fun requireNonBlank(
    value: String,
    onError: (String) -> Unit,
    build: (String) -> UpdateClientRequest,
): UpdateClientRequest? =
    if (value.isEmpty()) {
        onError("Value required")
        null
    } else {
        build(value)
    }

private inline fun requireNonBlank(
    value: String,
    errorMessage: String,
    onError: (String) -> Unit,
    build: (String) -> UpdateClientRequest,
): UpdateClientRequest? =
    if (value.isEmpty()) {
        onError(errorMessage)
        null
    } else {
        build(value)
    }
