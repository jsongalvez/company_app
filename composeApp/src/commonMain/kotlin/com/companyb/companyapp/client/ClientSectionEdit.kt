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

    for (field in section.fields()) {
        if (field == ClientField.BP_PAIR) continue
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
        if (unchanged) continue
        val patch = patchFor(field, draftTrimmed) { errors[field] = it }
        if (patch == null) continue
        hasChanges = true
        patch.firstName?.let { firstName = it }
        patch.middleName?.let { middleName = it }
        patch.lastName?.let { lastName = it }
        patch.suffix?.let { suffix = it }
        patch.phoneNumber?.let { phoneNumber = it }
        patch.address?.let { address = it }
        patch.gender?.let { gender = it }
        patch.age?.let { age = it }
        patch.medicalConditions?.let { medical = it }
        clears.addAll(patch.clearFields)
    }

    if (section == ClientSection.HEALTH) {
        val seedSys = client.systolicBp?.toString().orEmpty()
        val seedDia = client.diastolicBp?.toString().orEmpty()
        val sys = bpSystolic.trim()
        val dia = bpDiastolic.trim()
        if (sys != seedSys || dia != seedDia) {
            if (sys.isEmpty() && dia.isEmpty()) {
                hasChanges = true
                clears.add(ClientPatchField.SYSTOLIC_BP)
                clears.add(ClientPatchField.DIASTOLIC_BP)
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
                    errors[ClientField.BP_PAIR] = bpError
                } else {
                    hasChanges = true
                    systolic = sysVal
                    diastolic = diaVal
                }
            }
        }
    }

    if (errors.isNotEmpty()) return SectionPatchResult(null, errors, true)
    if (!hasChanges) return SectionPatchResult(null, emptyMap(), false)
    return SectionPatchResult(
        request =
            UpdateClientRequest(
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                suffix = suffix,
                phoneNumber = phoneNumber,
                address = address,
                gender = gender,
                age = age,
                systolicBp = systolic,
                diastolicBp = diastolic,
                medicalConditions = medical,
                clearFields = clears,
            ),
        fieldErrors = emptyMap(),
        hasChanges = true,
    )
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
        for (field in section.fields()) {
            if (field == ClientField.BP_PAIR) continue
            val current = currentFieldValue(client, field)
            val draft = (drafts[field] ?: current).trim()
            // AGE compares numerically so a leading-zero alias is not a modification.
            if (field == ClientField.AGE) {
                if (draft.toIntOrNull() != current.toIntOrNull()) return true
            } else if (draft != current) {
                return true
            }
        }
        if (section == ClientSection.HEALTH) {
            val seedSys = client.systolicBp?.toString().orEmpty()
            val seedDia = client.diastolicBp?.toString().orEmpty()
            if (bpDraft.systolic.trim() != seedSys || bpDraft.diastolic.trim() != seedDia) return true
        }
        return false
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
        if (result.fieldErrors.isNotEmpty()) {
            fieldErrors.clear()
            fieldErrors.putAll(result.fieldErrors)
            return false
        }
        if (!result.hasChanges) {
            exitEdit()
            return true
        }
        lastRequest = result.request
        pendingSection = section
        deps.onPatch(result.request!!)
        return true
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
