package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun ColumnScope.PrototypeVariantB(context: SessionCreatePrototypeContext) {
    val state = context.state
    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        BStepRail(state)
        Column(
            modifier = Modifier.fillMaxHeight().weight(1f).padding(Spacing.lg),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (state.step) {
                    0 -> BClientStep(context)
                    1 -> BSessionStep(context)
                    else -> BReviewStep(context)
                }
            }
            BStepActions(context)
        }
    }
}

@Composable
private fun BStepRail(state: SessionCreatePrototypeState) {
    val labels = listOf("Client", "Session", "Review")
    Surface(
        modifier = Modifier.fillMaxHeight().width(220.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = "New session",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "One decision at a time",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
            labels.forEachIndexed { index, label ->
                val current = state.step == index
                Surface(
                    onClick = { state.step = index },
                    shape = RoundedCornerShape(CornerRadius.md),
                    color =
                        if (current) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    border =
                        if (current) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else {
                            null
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "0${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else InkSubtle,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BClientStep(context: SessionCreatePrototypeContext) {
    val state = context.state
    Text(
        text = "Who are you seeing?",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Choose an existing client or clear the search to browse recent records.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkSubtle,
    )
    OutlinedTextField(
        value = state.search,
        onValueChange = { state.search = it },
        label = { Text("Search name or phone") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().trackPrototypeTextFocus(state),
    )
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
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "Selected: ${context.selectedClient.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

@Composable
private fun BSessionStep(context: SessionCreatePrototypeContext) {
    val state = context.state
    Text(
        text = "Shape today's session",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = context.selectedClient.name,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    BSessionType(context)
    BConcernOptions(context)
    BSessionMeta(context)
}

@Composable
private fun BSessionType(context: SessionCreatePrototypeContext) {
    val state = context.state
    PrototypeSectionLabel("Session start")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf("Walk-in", "Booked").forEach { option ->
            PrototypeChip(option, state.sessionKind == option) { state.sessionKind = option }
        }
    }
}

@Composable
private fun BConcernOptions(context: SessionCreatePrototypeContext) {
    val state = context.state
    PrototypeSectionLabel("Concern")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        sessionCreatePrototypeConcerns.forEach { concern ->
            Surface(
                onClick = { state.selectedConcern = concern },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.md),
                color =
                    if (state.selectedConcern == concern) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                border =
                    if (state.selectedConcern == concern) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    } else {
                        null
                    },
            ) {
                Text(
                    text = concern,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun BSessionMeta(context: SessionCreatePrototypeContext) {
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
}

@Composable
private fun BReviewStep(context: SessionCreatePrototypeContext) {
    val state = context.state
    Text(
        text = "Ready to start",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "Review the intent before creating the session.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkSubtle,
    )
    Surface(
        shape = RoundedCornerShape(CornerRadius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ReviewLine("Client", context.selectedClient.name)
            ReviewLine("Start", state.sessionKind)
            ReviewLine("Concern", state.selectedConcern)
            ReviewLine("Final price", "PHP ${state.price}")
            ReviewLine("Practitioner", state.practitioner)
        }
    }
    OutlinedTextField(
        value = state.remarks,
        onValueChange = { state.remarks = it },
        label = { Text("Remarks (optional)") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().trackPrototypeTextFocus(state),
    )
    PrototypeSubmissionNotice(state.submitted)
}

@Composable
private fun ReviewLine(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.width(130.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BStepActions(context: SessionCreatePrototypeContext) {
    val state = context.state
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Step ${state.step + 1} of 3",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        Spacer(Modifier.weight(1f))
        if (state.step > 0) {
            TextButton(onClick = { state.step-- }) {
                Text("Back")
            }
        }
        Button(
            onClick = {
                if (state.step == 2) context.finishPreview() else state.step++
            },
        ) {
            Text(if (state.step == 2) "Start session" else "Continue")
        }
    }
}
