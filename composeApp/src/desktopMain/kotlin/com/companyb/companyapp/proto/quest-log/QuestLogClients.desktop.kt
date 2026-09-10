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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private fun maskName(name: String): String {
    val parts = name.split(" ")
    if (parts.isEmpty()) return "···"
    val first = parts.first().take(1) + "."
    return "$first ···"
}

@Composable
internal fun QuestLogClients(repo: QuestLogRepo) {
    QuestSectionTitle(
        "Hall of allies",
        "Clients are global — every outpost reads the same hall. Tread kindly.",
    )
    val pendingIds = repo.pendingClientIds()
    QuestCard {
        Text("Hall law", style = QuestLogType.titleSmall)
        Text(
            "An ally may carry at most one PENDING quest at a time. " +
                "Names marked with a horn below already hold an open quest — parley before posting another.",
            style = QuestLogType.bodyMedium,
        )
        Spacer(Modifier.height(QuestPadSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = repo.anonymizedView, onCheckedChange = { repo.anonymizedView = it })
            Text("Veiled view — mask names and numbers", style = QuestLogType.bodyMedium)
        }
    }
    Spacer(Modifier.height(QuestPadSm))
    repo.clients.forEach { ally ->
        val picked = repo.selectedClientId == ally.id
        val hasPending = pendingIds.contains(ally.id)
        val showName =
            when {
                ally.anonymized || repo.anonymizedView -> maskName(ally.name) + " (veiled)"
                else -> ally.name
            }
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
                    .clickable { repo.selectedClientId = ally.id }
                    .padding(QuestPadMd),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ally.id} · $showName", style = QuestLogType.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (hasPending) {
                        QuestBadge("OPEN QUEST", QuestLogPalette.Gold)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (ally.anonymized) QuestBadge("VEILED", QuestLogPalette.ManaBlue)
                }
                Text(
                    "${ally.gender} · ${ally.age} winters · " +
                        if (ally.anonymized || repo.anonymizedView) "···-···-${ally.phone.takeLast(4)}" else ally.phone,
                    style = QuestLogType.bodySmall,
                )
                if (picked) {
                    Spacer(Modifier.height(4.dp))
                    val quests = repo.sessions.filter { it.clientId == ally.id }
                    if (quests.isEmpty()) {
                        Text("No quests yet bear this ally's name.", style = QuestLogType.bodyMedium)
                    } else {
                        quests.forEach { quest ->
                            Text(
                                "${quest.id} · ${quest.time} · ${quest.service} · ${quest.status.label}" +
                                    if (quest.voided) " · VOIDED" else "",
                                style = QuestLogType.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                        QuestGhostButton("Post quest for ally") {
                            repo.newClient = if (ally.anonymized) "" else ally.name
                            repo.createOpen = true
                            repo.screen = QuestScreen.QUESTS
                        }
                        QuestLinkButton(if (ally.anonymized) "Lift the veil" else "Draw the veil") {
                            val index = repo.clients.indexOfFirst { it.id == ally.id }
                            if (index >= 0) {
                                repo.clients[index] = ally.copy(anonymized = !ally.anonymized)
                                repo.audit(
                                    repo.currentUser.name,
                                    if (ally.anonymized) "CLIENT.REVEAL" else "CLIENT.ANONYMIZE",
                                    "${ally.id} veil toggled",
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(QuestPadSm))
    }
}
