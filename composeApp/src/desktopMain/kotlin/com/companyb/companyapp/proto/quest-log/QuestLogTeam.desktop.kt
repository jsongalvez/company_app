package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogTeam(repo: QuestLogRepo) {
    QuestSectionTitle(
        "The guild roster",
        "Users, roles, and the capabilities each rank carries.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
        repo.users.forEach { hero ->
            QuestCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(hero.name, style = QuestLogType.titleMedium)
                        Text(
                            hero.capabilities.joinToString(" · ").ifEmpty { "no capabilities — trial unsworn" },
                            style = QuestLogType.bodySmall,
                        )
                    }
                    QuestBadge(hero.role, if (hero.locked) QuestLogPalette.Faint else QuestLogPalette.ManaBlue)
                    Spacer(Modifier.width(6.dp))
                    if (repo.currentUserId == hero.id) QuestBadge("YOU", QuestLogPalette.Gold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when (hero.role) {
                        "MANAGER" -> "Guildmaster: rewrites sealed links, seals remittance, anoints users."
                        "Coordinator" -> "Steward: keeps the board true, seals remittance, answers horns."
                        "Practitioner" -> "Warden: turns in quests, banks XP, climbs the levels."
                        else -> "Squire: benched until the trial is sworn. The board is only a rumor."
                    },
                    style = QuestLogType.bodyMedium,
                )
            }
        }
        QuestCard {
            Text("Ranks of the realm", style = QuestLogType.titleSmall)
            Text("MANAGER · Coordinator · Practitioner · ONBOARDING — each gate in the app bows to rank.", style = QuestLogType.bodyMedium)
        }
    }
}
