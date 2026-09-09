package com.companyb.companyapp.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientPatchField
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.UpdateClientRequest

/**
 * #673 — deliberate section editing: Identity / Contact / Health, one labeled Edit per
 * section, Save changes / Cancel. Only one section edits at a time; Tab/blur only moves
 * focus, never sends a write. Save submits only changed fields through the existing
 * null/clear semantics ([patchFor]); BP validates as its existing paired value.
 *
 * Pure merge ([buildSectionPatch]) reuses [patchFor] per field so #523 clear semantics,
 * required-name errors and the BP both-or-neither rule stay identical — one request per
 * save, unchanged fields omitted.
 */
internal enum class ClientSection {
    IDENTITY,
    CONTACT,
    HEALTH,
}

internal fun ClientSection.title(): String =
    when (this) {
        ClientSection.IDENTITY -> "Identity"
        ClientSection.CONTACT -> "Contact"
        ClientSection.HEALTH -> "Health"
    }

internal fun ClientSection.fields(): List<ClientField> =
    when (this) {
        ClientSection.IDENTITY -> {
            listOf(
                ClientField.FIRST_NAME,
                ClientField.MIDDLE_NAME,
                ClientField.LAST_NAME,
                ClientField.SUFFIX,
                ClientField.GENDER,
                ClientField.AGE,
            )
        }

        ClientSection.CONTACT -> {
            listOf(ClientField.PHONE, ClientField.ADDRESS)
        }

        ClientSection.HEALTH -> {
            listOf(ClientField.BP_PAIR, ClientField.MEDICAL_CONDITIONS)
        }
    }

internal data class SectionPatchResult(
    val request: UpdateClientRequest?,
    val fieldErrors: Map<ClientField, String>,
    val hasChanges: Boolean,
)

/**
 * Builds the single PATCH for a section save: only changed fields, existing null/clear
 * semantics, BP paired. Returns errors per field on validation failure (no dispatch),
 * [hasChanges] false when every draft equals the committed record (silent exit).
 */
internal fun buildSectionPatch(
    section: ClientSection,
    drafts: Map<ClientField, String>,
    bpSystolic: String,
    bpDiastolic: String,
    client: ClientResponse,
): SectionPatchResult {
    // #695 single-exit fold: per-field merge + BP pair accumulate into one holder;
    // the loop carries zero jumps (early exits live in the helper's guard returns),
    // and the three terminal answers share one `when` exit.
    val acc = SectionPatchAccumulator()
    for (field in section.fields()) {
        accumulateNonBpField(field, drafts, client, acc)
    }
    applyHealthBpPair(section, bpSystolic, bpDiastolic, client, acc)
    val result =
        when {
            acc.errors.isNotEmpty() -> {
                SectionPatchResult(null, acc.errors, true)
            }

            !acc.hasChanges -> {
                SectionPatchResult(null, emptyMap(), false)
            }

            else -> {
                SectionPatchResult(
                    request =
                        UpdateClientRequest(
                            firstName = acc.firstName,
                            lastName = acc.lastName,
                            middleName = acc.middleName,
                            suffix = acc.suffix,
                            phoneNumber = acc.phoneNumber,
                            address = acc.address,
                            gender = acc.gender,
                            age = acc.age,
                            systolicBp = acc.systolic,
                            diastolicBp = acc.diastolic,
                            medicalConditions = acc.medical,
                            clearFields = acc.clears,
                        ),
                    fieldErrors = emptyMap(),
                    hasChanges = true,
                )
            }
        }
    return result
}

/** #695 mutable holder so the per-field + BP helpers stay small without changing merge semantics. */
private class SectionPatchAccumulator {
    val errors = mutableMapOf<ClientField, String>()
    var firstName: String? = null
    var middleName: String? = null
    var lastName: String? = null
    var suffix: String? = null
    var phoneNumber: String? = null
    var address: String? = null
    var gender: com.companyb.companyapp.contracts.client.Gender? = null
    var age: Int? = null
    var systolic: Short? = null
    var diastolic: Short? = null
    var medical: String? = null
    val clears = mutableSetOf<String>()
    var hasChanges = false
}

private fun accumulateNonBpField(
    field: ClientField,
    drafts: Map<ClientField, String>,
    client: ClientResponse,
    acc: SectionPatchAccumulator,
) {
    val patch = changedPatchFor(field, drafts, client, acc) ?: return
    acc.hasChanges = true
    mergePatch(patch, acc)
}

private fun changedPatchFor(
    field: ClientField,
    drafts: Map<ClientField, String>,
    client: ClientResponse,
    acc: SectionPatchAccumulator,
): UpdateClientRequest? {
    if (field == ClientField.BP_PAIR) return null
    val current = currentFieldValue(client, field)
    val draftTrimmed = drafts[field]?.trim() ?: current
    // AGE compares numerically so a leading-zero alias ("030" vs "30") is not a
    // modification (matches DispatchedDraft's canonicalization downstream).
    val unchanged =
        if (field == ClientField.AGE) {
            draftTrimmed.toIntOrNull() == current.toIntOrNull()
        } else {
            draftTrimmed == current
        }
    if (unchanged) return null
    return patchFor(field, draftTrimmed) { acc.errors[field] = it }
}

private fun mergePatch(
    patch: UpdateClientRequest,
    acc: SectionPatchAccumulator,
) {
    patch.firstName?.let { acc.firstName = it }
    patch.middleName?.let { acc.middleName = it }
    patch.lastName?.let { acc.lastName = it }
    patch.suffix?.let { acc.suffix = it }
    patch.phoneNumber?.let { acc.phoneNumber = it }
    patch.address?.let { acc.address = it }
    patch.gender?.let { acc.gender = it }
    patch.age?.let { acc.age = it }
    patch.medicalConditions?.let { acc.medical = it }
    acc.clears.addAll(patch.clearFields)
}

private fun applyHealthBpPair(
    section: ClientSection,
    bpSystolic: String,
    bpDiastolic: String,
    client: ClientResponse,
    acc: SectionPatchAccumulator,
) {
    if (section != ClientSection.HEALTH) return
    val seedSys = client.systolicBp?.toString().orEmpty()
    val seedDia = client.diastolicBp?.toString().orEmpty()
    val sys = bpSystolic.trim()
    val dia = bpDiastolic.trim()
    if (sys == seedSys && dia == seedDia) return
    if (sys.isEmpty() && dia.isEmpty()) {
        acc.hasChanges = true
        acc.clears.add(ClientPatchField.SYSTOLIC_BP)
        acc.clears.add(ClientPatchField.DIASTOLIC_BP)
    } else {
        val sysVal = sys.toShortOrNull()
        val diaVal = dia.toShortOrNull()
        val bpError =
            when {
                sys.isEmpty() || dia.isEmpty() -> "Both BP fields are required"
                sysVal == null || diaVal == null -> "Enter a valid number"
                else -> null
            }
        if (bpError != null) {
            acc.errors[ClientField.BP_PAIR] = bpError
        } else {
            acc.hasChanges = true
            acc.systolic = sysVal
            acc.diastolic = diaVal
        }
    }
}

/**
 * Hoisted section-edit session for the profile (remembered above the detail Loading
 * branch so a 409/404 reload never discards entered values). Drafts survive pending
 * saves, failures and conflicts; only an explicit Save-dispatch, Cancel/Discard or a
 * Reload-latest re-seed replaces them.
 */
internal class ClientSectionSession {
    var editingSection by mutableStateOf<ClientSection?>(null)
    val drafts = mutableStateMapOf<ClientField, String>()
    val fieldErrors = mutableStateMapOf<ClientField, String>()
    var pendingSection by mutableStateOf<ClientSection?>(null)
    var lastRequest by mutableStateOf<UpdateClientRequest?>(null)
    val bpDraft = BpDraftState()

    fun isDirty(client: ClientResponse): Boolean {
        val section = editingSection ?: return false
        // #695 single-exit fold: `any` lambda returns are excluded from ReturnCount,
        // so the field scan + BP check share one terminal exit with identical semantics.
        val nonBpDirty =
            section.fields().any { field ->
                if (field == ClientField.BP_PAIR) {
                    false
                } else {
                    val current = currentFieldValue(client, field)
                    val draft = (drafts[field] ?: current).trim()
                    // AGE compares numerically so a leading-zero alias is not a modification.
                    if (field == ClientField.AGE) {
                        draft.toIntOrNull() != current.toIntOrNull()
                    } else {
                        draft != current
                    }
                }
            }
        val bpDirty =
            section == ClientSection.HEALTH &&
                run {
                    val seedSys = client.systolicBp?.toString().orEmpty()
                    val seedDia = client.diastolicBp?.toString().orEmpty()
                    bpDraft.systolic.trim() != seedSys || bpDraft.diastolic.trim() != seedDia
                }
        return nonBpDirty || bpDirty
    }

    fun startSection(
        section: ClientSection,
        client: ClientResponse,
    ) {
        editingSection = section
        fieldErrors.clear()
        // Seed only this section's drafts: sibling sections must keep rendering display
        // values (P3 HARD-1 — seeding all sections made every field editable at once).
        for (field in section.fields()) {
            if (field == ClientField.BP_PAIR) continue
            drafts[field] = currentFieldValue(client, field)
        }
        if (section == ClientSection.HEALTH) bpDraft.seed(client.systolicBp, client.diastolicBp)
    }

    fun reseed(client: ClientResponse) {
        val section = editingSection ?: return
        fieldErrors.clear()
        for (field in section.fields()) {
            if (field == ClientField.BP_PAIR) continue
            drafts[field] = currentFieldValue(client, field)
        }
        bpDraft.seed(client.systolicBp, client.diastolicBp)
    }

    fun handleDraftChange(
        field: ClientField,
        value: String,
    ) {
        drafts[field] = value
        fieldErrors.remove(field)
    }

    fun exitEdit() {
        editingSection = null
        drafts.clear()
        fieldErrors.clear()
        lastRequest = null
        // Clearing the edit also retires its pending marker: a Cancel/Discard after a
        // leaked pending (mid-flight gate) must not leave a dangling pendingSection
        // that misattributes the next save or resurrects a stale conflict banner.
        pendingSection = null
    }

    fun clearPending() {
        pendingSection = null
    }

    /**
     * Section save: validates changed fields, merges into one request, dispatches once.
     * Returns false on validation failure (inline errors, no dispatch); true on
     * dispatch or silent unchanged exit.
     */
    fun saveSection(
        client: ClientResponse,
        deps: ClientEditDeps,
    ): Boolean {
        val section = editingSection ?: return true
        if (deps.navigationLocked) return true
        if (deps.updateState is UiState.Loading) return true
        if (deps.anonymizeState is UiState.Loading) return true
        if (shouldBailOnReload(deps.liveDetailState())) return true
        val result =
            buildSectionPatch(
                section = section,
                drafts = drafts.toMap(),
                bpSystolic = bpDraft.systolic,
                bpDiastolic = bpDraft.diastolic,
                client = client,
            )
        // #695 single-exit fold: guards above stay excluded; the three terminal
        // answers (invalid / unchanged / dispatch) share one exit below.
        val dispatched: Boolean =
            if (result.fieldErrors.isNotEmpty()) {
                fieldErrors.clear()
                fieldErrors.putAll(result.fieldErrors)
                false
            } else if (!result.hasChanges) {
                exitEdit()
                true
            } else {
                lastRequest = result.request
                pendingSection = section
                deps.onPatch(result.request!!)
                true
            }
        return dispatched
    }

    fun retryPending(deps: ClientEditDeps): Boolean {
        val request = lastRequest ?: return false
        if (deps.navigationLocked) return false
        if (deps.updateState is UiState.Loading) return false
        if (deps.anonymizeState is UiState.Loading) return false
        pendingSection = editingSection
        deps.onPatch(request)
        return true
    }
}
