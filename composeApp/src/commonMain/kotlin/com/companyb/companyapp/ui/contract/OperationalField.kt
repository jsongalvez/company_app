package com.companyb.companyapp.ui.contract

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * #670 — shared field behavior: the contract's fourth owner alongside action, feedback,
 * and dialog. Fields keep their platform shape ([OutlinedTextField])
 * and task structure; the contract contributes the visible 2dp focus ring, full-width
 * geometry, and the shared error-text slot (12sp floor via bodySmall, error token).
 *
 * Proven on Login, Accept invite, Forgot password, BranchSelect invite search, and the
 * delegate pickers' read-only fields. Feature-wide field adoption stays with downstream
 * tickets; this file pins the behavior they adopt.
 */
fun Modifier.operationalField(): Modifier = this.fillMaxWidth().operationalFocusRing()

@Composable
fun FieldErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
