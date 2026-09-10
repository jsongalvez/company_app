package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogFinance(repo: QuestLogRepo) {
    QuestSectionTitle(
        "The vault",
        "Remittance seals the day's spoils. Draft SESSION and PRODUCT scrolls, seal them, undo within 48h.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(QuestPadMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            QuestCard {
                Text("SESSION scroll — ${repo.currentDay.date}", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("Turned-in quests: ${repo.completedXp()} XP bounty", style = QuestLogType.bodyMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repo.sessionExpense.toString(),
                    onValueChange = { repo.sessionExpense = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                    label = { Text("Provisions (expense)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repo.sessionComp.toString(),
                    onValueChange = { repo.sessionComp = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                    label = { Text("Charter fees (comp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("Session net: ${repo.sessionNet()}", style = QuestLogType.titleMedium)
                Spacer(Modifier.height(QuestPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    QuestPrimaryButton("Seal SESSION", !repo.dayLocked) {
                        val seq = 2400 + repo.snapshots.size + 1
                        val id = "SR-$seq"
                        repo.snapshots.add(0, QuestSnapshot(id, "SESSION", "${repo.currentDay.date} 22:14", repo.sessionNet(), 0, true))
                        repo.audit(repo.currentUser.name, "REMITTANCE.SUBMIT", "$id SESSION ${repo.currentDay.date}")
                    }
                }
                if (repo.dayLocked) {
                    Spacer(Modifier.height(4.dp))
                    Text("The link is ${repo.currentDay.state.label} — sealing needs a MANAGER or Coordinator.", style = QuestLogType.bodySmall)
                }
            }
            QuestCard {
                Text("PRODUCT scroll", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                repo.productLines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, style = QuestLogType.bodyMedium)
                            Text("${line.qty} × ${line.unitPrice} = ${line.qty * line.unitPrice}", style = QuestLogType.bodySmall)
                        }
                        QuestLinkButton("Strike") {
                            repo.productLines.remove(line)
                            repo.audit(repo.currentUser.name, "PRODUCT.REMOVE", "${line.id} struck from draft")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedTextField(
                    value = repo.productDraftName,
                    onValueChange = { repo.productDraftName = it },
                    label = { Text("Ware name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    OutlinedTextField(
                        value = repo.productDraftQty,
                        onValueChange = { repo.productDraftQty = it.filter { c -> c.isDigit() } },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = repo.productDraftPrice,
                        onValueChange = { repo.productDraftPrice = it.filter { c -> c.isDigit() } },
                        label = { Text("Unit price") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(QuestPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    Text("Draft total: ${repo.productTotal()}", style = QuestLogType.titleMedium)
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    QuestPrimaryButton(
                        "Add ware",
                        repo.productDraftName.isNotBlank() && (repo.productDraftQty.toIntOrNull() ?: 0) > 0,
                    ) {
                        val id = "p-${repo.productLines.size + 1}"
                        repo.productLines.add(
                            QuestProductLine(
                                id,
                                repo.productDraftName.trim(),
                                repo.productDraftQty.toIntOrNull() ?: 1,
                                repo.productDraftPrice.toIntOrNull() ?: 0,
                            ),
                        )
                        repo.audit(repo.currentUser.name, "PRODUCT.ADD", "${repo.productDraftName.trim()} added")
                        repo.productDraftName = ""
                    }
                    QuestGhostButton("Seal PRODUCT", !repo.dayLocked) {
                        val seq = 2400 + repo.snapshots.size + 1
                        val id = "SR-$seq"
                        repo.snapshots.add(0, QuestSnapshot(id, "PRODUCT", "${repo.currentDay.date} 22:14", repo.productTotal(), 0, true))
                        repo.audit(repo.currentUser.name, "REMITTANCE.SUBMIT", "$id PRODUCT ${repo.currentDay.date}")
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            QuestCard {
                Text("Commission split — the guild tithe", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Of every session net, 60% rides home with the wardens (Practitioners) as XP " +
                        "and 40% stays with the hall. Turned-in XP this link: ${repo.completedXp()}; " +
                        "warden share of net: ${repo.practitionerShare()}.",
                    style = QuestLogType.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                QuestXpBar(
                    if (repo.sessionNet() > 0) {
                        repo.practitionerShare().toFloat() / repo.sessionNet().toFloat()
                    } else {
                        0f
                    },
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    QuestBadge("WARDENS 60%", QuestLogPalette.XpGreen)
                    Spacer(Modifier.width(6.dp))
                    QuestBadge("HALL 40%", QuestLogPalette.Gold)
                }
            }
            QuestCard {
                Text("Sealed scrolls (snapshots)", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (repo.snapshots.isEmpty()) {
                    Text("No scrolls sealed yet.", style = QuestLogType.bodyMedium)
                }
                repo.snapshots.forEach { snap ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${snap.id} · ${snap.kind}", style = QuestLogType.bodyMedium)
                                Text(
                                    "${snap.submittedAt} · total ${snap.total} · ${snap.ageHours}h old",
                                    style = QuestLogType.bodySmall,
                                )
                            }
                            if (snap.undoable && snap.ageHours < 48) {
                                QuestGhostButton("Unseal (Undo)") {
                                    val index = repo.snapshots.indexOfFirst { it.id == snap.id }
                                    if (index >= 0) repo.snapshots[index] = snap.copy(undoable = false)
                                    repo.snapshots.removeAll { it.id == snap.id }
                                    repo.audit(repo.currentUser.name, "REMITTANCE.UNDO", "${snap.id} unsealed within 48h")
                                }
                            } else {
                                QuestBadge("LOCKED", QuestLogPalette.Faint)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Undo law: a seal may be broken only within 48 hours of sealing.",
                            style = QuestLogType.bodySmall,
                        )
                        Spacer(Modifier.height(QuestPadSm))
                    }
                }
            }
        }
    }
}
