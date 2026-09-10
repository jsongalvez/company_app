package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogSessions(repo: QuestLogRepo) {
    QuestSectionTitle(
        "Quest roster",
        "Sessions are quests. Turn them in, mark the abandoned, recall the mistaken — ${repo.currentDay.date}",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL", "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED").forEach { filter ->
            QuestBadgeClickable(filter, repo.sessionFilter == filter) { repo.sessionFilter = filter }
        }
        Spacer(Modifier.weight(1f))
        QuestLinkButton("+ Post a quest") { repo.createOpen = true }
    }
    Spacer(Modifier.height(QuestPadSm))
    val shown =
        repo.sessions.filter { quest ->
            (repo.sessionFilter == "ALL" || quest.status.label == repo.sessionFilter)
        }
    if (shown.isEmpty()) {
        QuestCard { Text("No quests under this banner. Post one above.", style = QuestLogType.bodyMedium) }
    }
    shown.forEach { quest ->
        val picked = repo.selectedSessionId == quest.id
        val rank = questRank(quest.price)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (picked) QuestLogPalette.Gold.copy(alpha = 0.10f) else QuestLogPalette.Tavern)
                    .border(
                        1.dp,
                        if (picked) QuestLogPalette.Gold else QuestLogPalette.Border,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { repo.selectedSessionId = quest.id }
                    .padding(QuestPadMd),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuestBadge("RANK $rank", questRankColor(rank))
                    Spacer(Modifier.width(6.dp))
                    Text("${quest.time} · ${quest.id}", style = QuestLogType.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (quest.voided) {
                        QuestBadge("VOIDED", QuestLogPalette.HpRed)
                    } else {
                        QuestBadge(quest.status.label, questStatusColor(quest.status))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${quest.clientName} — ${quest.service}" +
                        if (quest.walkIn) " · WALK-IN" else "",
                    style = QuestLogType.bodyMedium,
                )
                Text(
                    "${quest.price} XP · warden ${quest.practitioner}" +
                        if (quest.voided) " · void: ${quest.voidReason}" else "",
                    style = QuestLogType.bodySmall,
                )
                if (picked) {
                    Spacer(Modifier.height(QuestPadSm))
                    QuestDetailActions(repo, quest)
                }
            }
        }
        Spacer(Modifier.height(QuestPadSm))
    }
    QuestCard {
        Text("Ranger law", style = QuestLogType.titleSmall)
        Text(
            "A WALK-IN quest is sworn on the spot: it may only stand PENDING or be turned in COMPLETED. " +
                "NO_SHOW and CANCELLED are barred for walk-ins — the buttons below stay sheathed.",
            style = QuestLogType.bodyMedium,
        )
    }
}

@Composable
private fun QuestDetailActions(
    repo: QuestLogRepo,
    quest: QuestSession,
) {
    val index = repo.sessions.indexOfFirst { it.id == quest.id }
    if (index < 0) return
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            QuestPrimaryButton("Turn in", quest.status != QuestStatus.COMPLETED && !quest.voided) {
                repo.sessions[index] = quest.copy(status = QuestStatus.COMPLETED)
                repo.audit(repo.currentUser.name, "SESSION.COMPLETE", "${quest.id} turned in at ${quest.price} XP")
            }
            QuestGhostButton(
                "Abandon",
                quest.status != QuestStatus.NO_SHOW && !quest.voided && !quest.walkIn,
            ) {
                repo.sessions[index] = quest.copy(status = QuestStatus.NO_SHOW)
                repo.audit(repo.currentUser.name, "SESSION.NO_SHOW", "${quest.id} marked NO_SHOW")
            }
            QuestGhostButton(
                "Recall",
                quest.status != QuestStatus.CANCELLED && !quest.voided && !quest.walkIn,
            ) {
                repo.sessions[index] = quest.copy(status = QuestStatus.CANCELLED)
                repo.audit(repo.currentUser.name, "SESSION.CANCEL", "${quest.id} marked CANCELLED")
            }
        }
        Spacer(Modifier.height(QuestPadSm))
        Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            if (quest.voided) {
                QuestGhostButton("Lift the void") {
                    repo.sessions[index] = quest.copy(voided = false, voidReason = "")
                    repo.audit(repo.currentUser.name, "SESSION.UNVOID", "${quest.id} unvoided")
                }
            } else {
                QuestGhostButton("Void this quest") {
                    repo.voidReason = ""
                    repo.voidTargetId = quest.id
                }
            }
        }
    }
}

@Composable
internal fun QuestVoidDialog(
    repo: QuestLogRepo,
    questId: String,
) {
    val index = repo.sessions.indexOfFirst { it.id == questId }
    AlertDialog(
        onDismissRequest = { repo.voidTargetId = null },
        title = { Text("Void $questId?", style = QuestLogType.titleLarge) },
        text = {
            Column {
                Text(
                    "A void strikes the quest from the ledger but keeps its ghost for the audit ravens. " +
                        "Speak the reason — it is required.",
                    style = QuestLogType.bodyMedium,
                )
                Spacer(Modifier.height(QuestPadSm))
                OutlinedTextField(
                    value = repo.voidReason,
                    onValueChange = { repo.voidReason = it },
                    label = { Text("Reason for the void") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            QuestPrimaryButton("Strike it", repo.voidReason.isNotBlank() && index >= 0) {
                val quest = repo.sessions[index]
                repo.sessions[index] = quest.copy(voided = true, voidReason = repo.voidReason.trim())
                repo.audit(repo.currentUser.name, "SESSION.VOID", "$questId voided: ${repo.voidReason.trim()}")
                repo.voidTargetId = null
            }
        },
        dismissButton = {
            QuestLinkButton("Keep it") { repo.voidTargetId = null }
        },
    )
}

@Composable
internal fun QuestCreateDialog(repo: QuestLogRepo) {
    AlertDialog(
        onDismissRequest = { repo.createOpen = false },
        title = { Text("Post a new quest", style = QuestLogType.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = repo.newTime,
                    onValueChange = { repo.newTime = it },
                    label = { Text("Hour (e.g. 15:00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = repo.newClient,
                    onValueChange = { repo.newClient = it },
                    label = { Text("Ally name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = repo.newService,
                    onValueChange = { repo.newService = it },
                    label = { Text("Trial (service)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = repo.newPrice,
                    onValueChange = { repo.newPrice = it.filter { c -> c.isDigit() } },
                    label = { Text("XP bounty (price)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repo.newWalkIn, onCheckedChange = { repo.newWalkIn = it })
                    Text("WALK-IN — sworn on the spot (no NO_SHOW / CANCELLED)", style = QuestLogType.bodyMedium)
                }
            }
        },
        confirmButton = {
            QuestPrimaryButton(
                "Pin to board",
                repo.newClient.isNotBlank() && (repo.newPrice.toIntOrNull() ?: 0) > 0,
            ) {
                repo.questSeq += 1
                val id = "q-${repo.questSeq}"
                val price = repo.newPrice.toIntOrNull() ?: 0
                repo.sessions.add(
                    QuestSession(
                        id,
                        repo.newTime.ifBlank { "15:00" },
                        "c-new",
                        repo.newClient.trim(),
                        repo.newWalkIn,
                        repo.newService.ifBlank { "Consult" },
                        QuestStatus.PENDING,
                        price,
                        repo.currentUser.name,
                    ),
                )
                repo.selectedSessionId = id
                repo.audit(repo.currentUser.name, "SESSION.CREATE", "$id posted for ${repo.newClient.trim()}")
                repo.newClient = ""
                repo.newWalkIn = false
                repo.createOpen = false
            }
        },
        dismissButton = {
            QuestLinkButton("Burn the draft") { repo.createOpen = false }
        },
    )
}
