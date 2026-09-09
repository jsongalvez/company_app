package com.companyb.companyapp.client

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
