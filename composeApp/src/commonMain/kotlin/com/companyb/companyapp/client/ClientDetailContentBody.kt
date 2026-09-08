package com.companyb.companyapp.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #673 — profile shows Identity, Contact and Health sections with one labeled Edit per
 * editable section. Section editing is deliberate: Save changes / Cancel, one section
 * at a time, focus lands on the first field, Tab/blur never writes, Save emits one
 * request ([ClientSectionSession.saveSection]). Name stays the primary heading;
 * clinical facts stay readable (Health always mounted, never hidden in a menu).
 */
internal data class SectionScreenCallbacks(
    val onStartSection: (ClientSection) -> Unit,
    val onCancelSection: () -> Unit,
    val onSaveSection: () -> Unit,
    val onRetrySection: () -> Unit,
    val onReloadLatest: () -> Unit,
    val onDraftChange: (ClientField, String) -> Unit,
    val onBpChange: (Boolean, String) -> Unit,
    val onAnonymizeClick: () -> Unit,
)

@Composable
internal fun ClientDetailContentBody(
    client: ClientResponse,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    anonymizeState: UiState<Unit>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    callbacks: SectionScreenCallbacks,
) {
    ClientDetailContentHeader(client)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        ClientDetailLayout(
            identity = {
                ClientIdentitySection(client, session, updateState, changedNotice, navigationLocked, callbacks)
                ClientContactSection(client, session, updateState, changedNotice, navigationLocked, callbacks)
            },
            contactHealth = {
                ClientHealthSection(client, session, updateState, changedNotice, navigationLocked, callbacks)
            },
            actions = {
                ClientDetailMoreActions(
                    anonymizeState = anonymizeState,
                    navigationLocked = navigationLocked,
                    onAnonymizeClick = callbacks.onAnonymizeClick,
                )
            },
        )
    }
}

@Composable
private fun ClientDetailContentHeader(client: ClientResponse) {
    Text(
        text = clientPrimaryName(client),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.size(Spacing.xs))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ClientIdentitySection(
    client: ClientResponse,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    callbacks: SectionScreenCallbacks,
) {
    ClientSectionFrame(
        section = ClientSection.IDENTITY,
        session = session,
        updateState = updateState,
        changedNotice = changedNotice,
        navigationLocked = navigationLocked,
        callbacks = callbacks,
    ) { editing ->
        val firstFocus = remember { FocusRequester() }
        if (editing) LaunchedEffect(Unit) { firstFocus.requestFocus() }
        // P3 HARD-1 — fields render drafts only for the editing section; siblings stay
        // display-only even though the drafts map may hold other sections' keys.
        val editingThis = session.editingSection == ClientSection.IDENTITY
        SectionTextField(
            label = "First name",
            value = client.firstName.orEmpty(),
            draft = session.drafts[ClientField.FIRST_NAME]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.FIRST_NAME]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            focusRequester = firstFocus,
            onDraftChange = { callbacks.onDraftChange(ClientField.FIRST_NAME, it) },
        )
        SectionTextField(
            label = "Middle name",
            value = client.middleName.orEmpty(),
            draft = session.drafts[ClientField.MIDDLE_NAME]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.MIDDLE_NAME]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.MIDDLE_NAME, it) },
        )
        SectionTextField(
            label = "Last name",
            value = client.lastName.orEmpty(),
            draft = session.drafts[ClientField.LAST_NAME]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.LAST_NAME]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.LAST_NAME, it) },
        )
        SectionTextField(
            label = "Suffix",
            value = client.suffix.orEmpty(),
            draft = session.drafts[ClientField.SUFFIX]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.SUFFIX]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.SUFFIX, it) },
        )
        SectionGenderField(
            value = client.gender.displayName(),
            draft = session.drafts[ClientField.GENDER]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.GENDER]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.GENDER, it) },
        )
        SectionTextField(
            label = "Age",
            value = client.age.toString(),
            draft = session.drafts[ClientField.AGE]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.AGE]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            keyboardType = KeyboardType.Number,
            onDraftChange = { callbacks.onDraftChange(ClientField.AGE, it) },
        )
    }
}

@Composable
private fun ClientContactSection(
    client: ClientResponse,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    callbacks: SectionScreenCallbacks,
) {
    ClientSectionFrame(
        section = ClientSection.CONTACT,
        session = session,
        updateState = updateState,
        changedNotice = changedNotice,
        navigationLocked = navigationLocked,
        callbacks = callbacks,
    ) { editing ->
        val firstFocus = remember { FocusRequester() }
        if (editing) LaunchedEffect(Unit) { firstFocus.requestFocus() }
        val editingThis = session.editingSection == ClientSection.CONTACT
        SectionTextField(
            label = "Phone",
            value = client.phoneNumber.orEmpty(),
            draft = session.drafts[ClientField.PHONE]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.PHONE]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            keyboardType = KeyboardType.Phone,
            focusRequester = firstFocus,
            onDraftChange = { callbacks.onDraftChange(ClientField.PHONE, it) },
        )
        SectionTextField(
            label = "Address",
            value = client.address.orEmpty(),
            draft = session.drafts[ClientField.ADDRESS]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.ADDRESS]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.ADDRESS, it) },
        )
    }
}

@Composable
private fun ClientHealthSection(
    client: ClientResponse,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    callbacks: SectionScreenCallbacks,
) {
    ClientSectionFrame(
        section = ClientSection.HEALTH,
        session = session,
        updateState = updateState,
        changedNotice = changedNotice,
        navigationLocked = navigationLocked,
        callbacks = callbacks,
    ) { editing ->
        val sysFocus = remember { FocusRequester() }
        if (editing) LaunchedEffect(Unit) { sysFocus.requestFocus() }
        val editingThis = session.editingSection == ClientSection.HEALTH
        SectionBpPair(
            systolic = client.systolicBp,
            diastolic = client.diastolicBp,
            sysDraft = if (editingThis) session.bpDraft.systolic else null,
            diaDraft = if (editingThis) session.bpDraft.diastolic else null,
            error = session.fieldErrors[ClientField.BP_PAIR]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            sysFocus = sysFocus,
            onSysChange = { callbacks.onBpChange(true, it) },
            onDiaChange = { callbacks.onBpChange(false, it) },
        )
        SectionTextField(
            label = "Medical conditions",
            value = client.medicalConditions.orEmpty(),
            draft = session.drafts[ClientField.MEDICAL_CONDITIONS]?.takeIf { editingThis },
            error = session.fieldErrors[ClientField.MEDICAL_CONDITIONS]?.takeIf { editingThis },
            enabled = !navigationLocked && editingThis,
            onDraftChange = { callbacks.onDraftChange(ClientField.MEDICAL_CONDITIONS, it) },
        )
    }
}

@Composable
private fun ClientSectionFrame(
    section: ClientSection,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    callbacks: SectionScreenCallbacks,
    fields: @Composable (editing: Boolean) -> Unit,
) {
    val editing = session.editingSection == section
    val otherEditing = session.editingSection != null && !editing
    val saving = updateState is UiState.Loading && session.pendingSection == section
    val failure = (updateState as? UiState.Error)?.takeIf { session.pendingSection == section }

    Spacer(Modifier.size(Spacing.sm))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = section.title(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!editing) {
            TertiaryActionButton(
                label = "Edit",
                onClick = { callbacks.onStartSection(section) },
                enabled = !navigationLocked && !otherEditing,
            )
        }
    }
    fields(editing)
    if (editing) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PrimaryActionButton(
                label = "Save changes",
                onClick = callbacks.onSaveSection,
                enabled = !navigationLocked,
                isBusy = saving,
            )
            SecondaryActionButton(
                label = "Cancel",
                onClick = callbacks.onCancelSection,
                enabled = !navigationLocked && !saving,
            )
        }
        // Pending save preserves draft, selection, error location and bounds: the
        // section stays mounted with drafts intact; only the busy slot animates.
        if (saving) {
            InlineStatus(message = "Saving…", kind = InlineStatusKind.UPDATING)
        }
        if (failure != null) {
            InlineStatus(
                message = failure.message,
                kind = InlineStatusKind.FAILURE,
                onRetry = callbacks.onRetrySection,
            )
        }
        if (changedNotice && session.pendingSection == section) {
            // Version conflict preserves entered values and offers Reload latest for
            // review — never blind overwrites (drafts survive the authoritative reload).
            InlineStatus(
                message = "Client was updated elsewhere — your entries are kept. Review the latest, then retry.",
                kind = InlineStatusKind.STALE,
                onRetry = callbacks.onReloadLatest,
                retryLabel = "Reload latest",
            )
        }
    } else if (changedNotice && session.editingSection == null) {
        InlineStatus(
            message = "Client was updated elsewhere — changes reloaded",
            kind = InlineStatusKind.STALE,
        )
    }
}

@Composable
private fun ClientDetailMoreActions(
    anonymizeState: UiState<Unit>,
    navigationLocked: Boolean,
    onAnonymizeClick: () -> Unit,
) {
    // #673 — Anonymize lives in More; the confirmation names the client and the
    // irreversible effect (see ClientDetailScreen). Disabled while a POST is in
    // flight — re-tapping mid-anonymize would fire a second destructive request.
    var moreOpen by remember { mutableStateOf(false) }
    Box {
        SecondaryActionButton(
            label = "More",
            onClick = { moreOpen = true },
            enabled = !navigationLocked && anonymizeState !is UiState.Loading,
        )
        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
            DropdownMenuItem(
                text = { Text("Anonymize", color = MaterialTheme.colorScheme.error) },
                enabled = !navigationLocked && anonymizeState !is UiState.Loading,
                onClick = {
                    moreOpen = false
                    onAnonymizeClick()
                },
            )
        }
    }
    if (anonymizeState is UiState.Error) {
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = anonymizeState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
