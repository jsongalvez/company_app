package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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

@Composable
internal fun ColumnScope.PrototypeVariantC(context: SessionCreatePrototypeContext) {
    Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        CClientDirectory(context)
        Column(modifier = Modifier.fillMaxHeight().weight(1f).padding(start = Spacing.lg)) {
            CSessionWorkspace(context)
        }
    }
}

@Composable
private fun CClientDirectory(context: SessionCreatePrototypeContext) {
    val state = context.state
    Surface(
        modifier = Modifier.fillMaxHeight().width(310.dp),
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clients",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "4 demo records",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                    )
                }
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = state.search,
                onValueChange = { state.search = it },
                label = { Text("Filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).trackPrototypeTextFocus(state),
            )
            Column(
                modifier = Modifier.fillMaxHeight().padding(top = Spacing.md).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (context.visibleClients.isEmpty()) {
                    PrototypeEmptySearch(onReset = { state.search = "" })
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
    }
}

@Composable
private fun CSessionWorkspace(context: SessionCreatePrototypeContext) {
    val state = context.state
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Session details",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Edit beside the client record, without losing the directory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                )
            }
            Text(
                text = "OPEN DAY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        CSelectedClient(context)
        CSessionFields(context)
        CNotesAndAction(context)
    }
}

@Composable
private fun CSelectedClient(context: SessionCreatePrototypeContext) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrototypeAvatar(context.selectedClient.initials)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    text = context.selectedClient.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${context.selectedClient.phone}  ·  ${context.selectedClient.lastVisit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        }
    }
}

@Composable
private fun CSessionFields(context: SessionCreatePrototypeContext) {
    val state = context.state
    Surface(
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrototypeSectionLabel("Start type")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("Walk-in", "Booked").forEach { option ->
                    PrototypeChip(option, state.sessionKind == option) { state.sessionKind = option }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            PrototypeSectionLabel("Primary concern")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("Shoulder pain", "Lower back pain", "Post-op recovery").forEach { concern ->
                    PrototypeChip(concern, state.selectedConcern == concern) { state.selectedConcern = concern }
                }
            }
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
        }
    }
}

@Composable
private fun CNotesAndAction(context: SessionCreatePrototypeContext) {
    val state = context.state
    OutlinedTextField(
        value = state.remarks,
        onValueChange = { state.remarks = it },
        label = { Text("Remarks (optional)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().trackPrototypeTextFocus(state),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrototypeSubmissionNotice(state.submitted)
        Spacer(Modifier.weight(1f))
        Button(onClick = context.finishPreview) {
            Text("Start session")
        }
    }
}
