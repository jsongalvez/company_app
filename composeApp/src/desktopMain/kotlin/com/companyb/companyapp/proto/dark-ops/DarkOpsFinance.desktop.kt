package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsFinance(repo: DarkOpsRepo) {
    var kind by remember { mutableStateOf("SESSION") }
    Column {
        OpsSectionHeader("finance", "draft → submit → snapshot · undo 48h") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OpsBadgeClickable("SESSION", kind == "SESSION") { kind = "SESSION" }
                OpsBadgeClickable("PRODUCT", kind == "PRODUCT") { kind = "PRODUCT" }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadMd)) {
            Column(Modifier.weight(1f)) {
                if (kind == "SESSION") SessionDraftPanel(repo) else ProductDraftPanel(repo)
                Spacer(Modifier.height(OpsPadSm))
                CommissionPanel(repo)
            }
            Column(Modifier.width(OpsDetailWidth), verticalArrangement = Arrangement.spacedBy(OpsPadMd)) {
                SubmitPanel(repo, kind)
                SnapshotPanel(repo)
            }
        }
    }
    if (repo.undoTargetId != null) UndoDialog(repo)
}

@Composable
private fun SessionDraftPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("DRAFT // SESSION · " + repo.currentDay.date, style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        FinanceLine(
            "completed income",
            peso(
                repo.sessions
                    .filter {
                        it.status == OpsSessionStatus.COMPLETED &&
                            !it.voided
                    }.sumOf { it.price },
            ),
        )
        FinanceLine("compensation", "-" + peso(repo.sessionComp))
        FinanceLine("expenses", "-" + peso(repo.sessionExpense))
        Spacer(Modifier.height(4.dp))
        FinanceLine("NET", peso(repo.sessionNet()), DarkOpsPalette.Phosphor)
        Spacer(Modifier.height(4.dp))
        OpsNote("drafts are unconstrained and may overlap")
    }
}

@Composable
private fun FinanceLine(
    key: String,
    value: String,
    color: Color = DarkOpsPalette.Ink,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(key.padEnd(20), style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
        Spacer(Modifier.weight(1f))
        Text(value, style = DarkOpsType.bodyMedium, color = color)
    }
}

@Composable
private fun ProductDraftPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("DRAFT // PRODUCT · unit price x qty", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        repo.productLines.forEach { line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line.name.padEnd(22).take(22), style = DarkOpsType.bodyMedium, modifier = Modifier.weight(1f))
                Text("x" + line.qty, style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Dim)
                Spacer(Modifier.width(OpsPadSm))
                Text(peso(line.qty * line.unitPrice), style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Cyan)
            }
        }
        Spacer(Modifier.height(4.dp))
        FinanceLine("TOTAL", peso(repo.productTotal()), DarkOpsPalette.Cyan)
        Spacer(Modifier.height(4.dp))
        ProductAddRow(repo)
    }
}

@Composable
private fun ProductAddRow(repo: DarkOpsRepo) {
    var name by remember { mutableStateOf("Alcohol 500ml") }
    var qty by remember { mutableStateOf("3") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
            },
            label = {
                Text("item", style = DarkOpsType.bodySmall)
            },
            singleLine = true,
            textStyle = DarkOpsType.bodyMedium,
            colors = fieldColors(),
            modifier =
                Modifier.weight(
                    1f,
                ),
        )
        OutlinedTextField(
            value = qty,
            onValueChange = {
                qty =
                    it.filter { c ->
                        c.isDigit()
                    }
            },
            label = {
                Text("qty", style = DarkOpsType.bodySmall)
            },
            singleLine = true,
            textStyle = DarkOpsType.bodyMedium,
            colors = fieldColors(),
            modifier =
                Modifier.width(
                    72.dp,
                ),
        )
        Button(onClick = { repo.addProductLine(name.ifBlank { "supply" }, qty.toIntOrNull() ?: 1, 120) }) {
            Text("+", style = DarkOpsType.labelLarge)
        }
    }
}

@Composable
private fun SubmitPanel(
    repo: DarkOpsRepo,
    kind: String,
) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("SUBMIT", style = DarkOpsType.labelSmall)
        Text(
            peso(
                if (kind ==
                    "SESSION"
                ) {
                    repo.sessionNet()
                } else {
                    repo.productTotal()
                },
            ),
            style = DarkOpsType.displaySmall,
            color = DarkOpsPalette.Phosphor,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = {
            repo.submitRemittance(kind)
        }, colors = greenButton(), enabled = !repo.dayLocked, modifier = Modifier.fillMaxWidth()) {
            Text("SUBMIT " + kind, style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("submission freezes an immutable snapshot")
    }
}

@Composable
private fun SnapshotPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("SNAPSHOTS", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        if (repo.snapshots.isEmpty()) {
            OpsNote("none yet — submit to freeze one")
        } else {
            SnapshotRows(repo)
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("undo needs a reason and dies 48h after submit")
    }
}

@Composable
private fun SnapshotRows(repo: DarkOpsRepo) {
    repo.snapshots.forEach { snap ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snap.id + " · " + snap.kind, style = DarkOpsType.bodyMedium)
                Text(snap.submittedAt + " · " + peso(snap.total), style = DarkOpsType.bodySmall)
            }
            if (snap.undoable) {
                TextButton(onClick = { repo.undoTargetId = snap.id }) {
                    Text("[undo]", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Amber)
                }
            }
        }
    }
}

@Composable
private fun CommissionPanel(repo: DarkOpsRepo) {
    val crew = repo.clockCrew()
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("COMMISSION SPLIT // pooled per branch day", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        FinanceLine("pool (5% of net)", peso(repo.commissionPool()))
        FinanceLine("crew clocked in", crew.size.toString())
        crew.forEach { member ->
            Text(
                "· " + member.name + " — " + peso(if (crew.isNotEmpty()) repo.commissionPool() / crew.size else 0),
                style = DarkOpsType.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("split equally among clocked-in crew; manual overrides aside; outside remittance")
    }
}

@Composable
private fun UndoDialog(repo: DarkOpsRepo) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { repo.undoTargetId = null },
        title = { Text("undo " + (repo.undoTargetId ?: "") + " — reason required", style = DarkOpsType.titleMedium) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it
                },
                label = {
                    Text("reason", style = DarkOpsType.bodySmall)
                },
                singleLine = true,
                textStyle = DarkOpsType.bodyMedium,
                colors = fieldColors(),
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = {
                repo.undoRemittance(repo.undoTargetId ?: "", reason.ifBlank { "fat-finger" })
                repo.undoTargetId =
                    null
            }, colors = greenButton()) {
                Text("UNDO", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Void)
            }
        },
        dismissButton = {
            TextButton(onClick = { repo.undoTargetId = null }) {
                Text("keep", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Dim)
            }
        },
        containerColor = DarkOpsPalette.PanelRaised,
    )
}
