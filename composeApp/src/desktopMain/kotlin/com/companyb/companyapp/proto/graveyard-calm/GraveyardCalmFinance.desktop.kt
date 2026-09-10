package com.companyb.companyapp.proto.graveyardcalm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GraveyardCalmFinance(repo: CalmRepo) {
    CalmSection("night till", "draft → submit → snapshot · undo within 48h")
    CalmTwoCol(
        left = {
            CalmCard {
                CalmSection("SESSION draft", "completed tonight, minus comps + expenses")
                val done = repo.sessions.filter { it.status == CalmSessionStatus.COMPLETED && !it.voided }
                Text(
                    "Completed: ${done.size} session(s) = ₱${done.sumOf { it.price }}",
                    style = GraveyardCalmType.bodyMedium,
                    color = GraveyardCalmPalette.Ink,
                )
                Spacer(Modifier.height(4.dp))
                CalmMoneyField("comps ₱", repo.sessionComp) { repo.sessionComp = it }
                CalmMoneyField("expenses ₱", repo.sessionExpense) { repo.sessionExpense = it }
                Text(
                    "net ₱${repo.sessionDraftNet()}",
                    style = GraveyardCalmType.titleLarge,
                    color = GraveyardCalmPalette.Lamp,
                )
                Spacer(Modifier.height(6.dp))
                CalmPrimary("seal SESSION ₱${repo.sessionDraftNet()}") {
                    repo.submitSnapshot("SESSION", repo.sessionDraftNet())
                }
            }
            Spacer(Modifier.height(8.dp))
            CalmCard {
                CalmSection("PRODUCT draft", "${repo.productLines.size} line(s)")
                repo.productLines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, style = GraveyardCalmType.bodyMedium, color = GraveyardCalmPalette.Ink)
                            Text(
                                "x${line.qty} @ ₱${line.unitPrice}",
                                style = GraveyardCalmType.labelSmall,
                                color = GraveyardCalmPalette.Faint,
                            )
                        }
                        Text("₱${line.qty * line.unitPrice}", style = GraveyardCalmType.bodyMedium, color = GraveyardCalmPalette.Ink)
                        CalmQuiet("drop") { repo.removeProductLine(line.id) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                CalmMoneyField("item name", repo.newProductName) { repo.newProductName = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        CalmMoneyField("qty", repo.newProductQty) { repo.newProductQty = it }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        CalmMoneyField("unit ₱", repo.newProductPrice) { repo.newProductPrice = it }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalmGhost("add line") { repo.addProductLine() }
                    CalmPrimary("seal PRODUCT ₱${repo.productDraftTotal()}") {
                        repo.submitSnapshot("PRODUCT", repo.productDraftTotal())
                    }
                }
            }
        },
        right = {
            CalmCard {
                CalmSection("sealed snapshots", "immutable once sealed — undo reopens")
                if (repo.snapshots.isEmpty()) {
                    CalmEmpty("no seals yet tonight")
                }
                repo.snapshots.forEach { snap ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(snap.id, style = GraveyardCalmType.labelMedium, color = GraveyardCalmPalette.Lamp)
                            Spacer(Modifier.weight(1f))
                            CalmTag(snap.kind, GraveyardCalmPalette.Moon)
                            if (snap.undone) CalmTag("UNDONE", GraveyardCalmPalette.Ember)
                        }
                        Text(
                            "₱${snap.total} · ${snap.clock} · by ${snap.by}",
                            style = GraveyardCalmType.bodySmall,
                            color = GraveyardCalmPalette.Dim,
                        )
                        if (snap.undone) {
                            Text("reopened: ${snap.undoReason}", style = GraveyardCalmType.bodySmall, color = GraveyardCalmPalette.Ember)
                        } else {
                            CalmQuiet("undo with reason…") { repo.undoDialogFor = snap.id }
                        }
                        CalmDivider()
                    }
                }
            }
            if (repo.undoDialogFor != null) {
                Spacer(Modifier.height(8.dp))
                CalmCard {
                    CalmSection("reopen ${repo.undoDialogFor}?")
                    TextField(
                        value = repo.undoReason,
                        onValueChange = { repo.undoReason = it },
                        placeholder = { Text("e.g. miscounted the oil bottles…", style = GraveyardCalmType.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = GraveyardCalmPalette.CardSoft,
                                unfocusedContainerColor = GraveyardCalmPalette.CardSoft,
                                focusedTextColor = GraveyardCalmPalette.Ink,
                                unfocusedTextColor = GraveyardCalmPalette.Ink,
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalmPrimary("reopen it") { repo.undoSnapshot(repo.undoDialogFor!!) }
                        CalmGhost("leave sealed") {
                            repo.undoDialogFor = null
                            repo.undoReason = ""
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            CalmNote("Undo window: 48h from seal. Older seals need the dawn MANAGER + accountant pair.")
            Spacer(Modifier.height(6.dp))
            CalmNote("Commission split: 5% of the SESSION seal is split evenly across clocked-in crew — settled outside remittance, noted in audit.")
        },
    )
}

@Composable
private fun CalmMoneyField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = GraveyardCalmType.labelSmall, color = GraveyardCalmPalette.Faint)
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = GraveyardCalmPalette.CardSoft,
                    unfocusedContainerColor = GraveyardCalmPalette.CardSoft,
                    focusedTextColor = GraveyardCalmPalette.Ink,
                    unfocusedTextColor = GraveyardCalmPalette.Ink,
                ),
        )
        Spacer(Modifier.height(4.dp))
    }
}
