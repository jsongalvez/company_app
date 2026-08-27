package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

private const val RECENT_CLIENT_LIMIT = 3

@Composable
internal fun ColumnScope.PrototypeVariantA(context: SessionCreatePrototypeContext) {
    val state = context.state
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
            Text(
                text = "Quick start from recent clients",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Find a familiar client first, then finish session details in one pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            OutlinedTextField(
                value = state.search,
                onValueChange = { state.search = it },
                label = { Text("Search name or phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).trackPrototypeTextFocus(state),
            )
            if (state.search.isNotBlank()) {
                VariantAFilteredClients(context)
            } else {
                VariantARecentClients(context)
            }
            VariantAForm(context)
        }
    }
}

@Composable
private fun VariantAFilteredClients(context: SessionCreatePrototypeContext) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
        PrototypeSectionLabel("Search results")
        if (context.visibleClients.isEmpty()) {
            PrototypeEmptySearch(onReset = { context.state.search = "" })
        } else {
            context.visibleClients.forEach { client ->
                PrototypeClientRow(
                    client = client,
                    selected = client.id == context.selectedClient.id,
                    onClick = { context.selectClient(client) },
                )
            }
        }
    }
}

@Composable
private fun VariantARecentClients(context: SessionCreatePrototypeContext) {
    Column(modifier = Modifier.padding(top = Spacing.lg)) {
        PrototypeSectionLabel("Recent clients")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            context.clients.take(RECENT_CLIENT_LIMIT).forEach { client ->
                Surface(
                    onClick = { context.selectClient(client) },
                    modifier = Modifier.width(210.dp),
                    shape = RoundedCornerShape(CornerRadius.lg),
                    color =
                        if (client.id == context.selectedClient.id) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    border =
                        if (client.id == context.selectedClient.id) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        },
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PrototypeAvatar(client.initials)
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                        Text(
                            text = client.concern,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSubtle,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Text(
                            text = client.lastVisit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantAForm(context: SessionCreatePrototypeContext) {
    val state = context.state
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ASelectedClient(context)
            ASessionOptions(context)
            AFormFields(context)
            AFormAction(context)
        }
    }
}

@Composable
private fun ASelectedClient(context: SessionCreatePrototypeContext) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrototypeAvatar(context.selectedClient.initials)
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(
                text = context.selectedClient.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${context.selectedClient.concern}  ·  ${context.selectedClient.sessionCount}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        Text(
            text = "Selected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AFormFields(context: SessionCreatePrototypeContext) {
    val state = context.state
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedTextField(
            value = state.price,
            onValueChange = { state.price = it },
            label = { Text("Final price (PHP)") },
            singleLine = true,
            modifier = Modifier.weight(1f).trackPrototypeTextFocus(state),
        )
        OutlinedTextField(
            value = state.practitioner,
            onValueChange = { state.practitioner = it },
            label = { Text("Requested practitioner") },
            singleLine = true,
            modifier = Modifier.weight(1f).trackPrototypeTextFocus(state),
        )
    }
    OutlinedTextField(
        value = state.remarks,
        onValueChange = { state.remarks = it },
        label = { Text("Remarks (optional)") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().trackPrototypeTextFocus(state),
    )
}

@Composable
private fun AFormAction(context: SessionCreatePrototypeContext) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrototypeSubmissionNotice(context.state.submitted)
        Spacer(Modifier.weight(1f))
        Button(onClick = context.finishPreview) {
            Text("Start session")
        }
    }
}

@Composable
private fun ASessionOptions(context: SessionCreatePrototypeContext) {
    val state = context.state
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrototypeSectionLabel("Session setup")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("Walk-in", "Booked").forEach { option ->
                PrototypeChip(
                    label = option,
                    selected = state.sessionKind == option,
                    onClick = { state.sessionKind = option },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            sessionCreatePrototypeConcerns.forEach { concern ->
                PrototypeChip(
                    label = concern,
                    selected = state.selectedConcern == concern,
                    onClick = { state.selectedConcern = concern },
                )
            }
        }
    }
}
