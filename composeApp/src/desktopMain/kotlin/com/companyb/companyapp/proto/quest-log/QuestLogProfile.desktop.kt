package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogProfile(repo: QuestLogRepo) {
    val hero = repo.currentUser
    QuestSectionTitle(
        "The hero's mirror",
        "Who stands before the board, and how the tale pauses.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
        QuestCard {
            Text(hero.name, style = QuestLogType.displaySmall)
            Spacer(Modifier.height(4.dp))
            Row {
                QuestBadge(hero.role, QuestLogPalette.ManaBlue)
                Spacer(Modifier.width(6.dp))
                QuestBadge(
                    if (repo.clockedIn) "IN THE FIELD" else "AT CAMP",
                    if (repo.clockedIn) QuestLogPalette.XpGreen else QuestLogPalette.Faint,
                )
                Spacer(Modifier.width(6.dp))
                QuestBadge("Lv ${repo.heroLevel()} · ${repo.completedXp()} XP", QuestLogPalette.Gold)
            }
            Spacer(Modifier.height(QuestPadSm))
            QuestXpBar(repo.heroLevelProgress())
            Spacer(Modifier.height(QuestPadSm))
            Text(
                "Capabilities: ${hero.capabilities.joinToString(" · ").ifEmpty { "none yet" }}",
                style = QuestLogType.bodyMedium,
            )
            Text(
                "Outpost: ${repo.currentBranch.name} (${repo.currentBranch.kind}) · " +
                    "chain link ${repo.currentDay.date} ${repo.currentDay.state.label}",
                style = QuestLogType.bodyMedium,
            )
        }
        QuestCard {
            Text("End the session", style = QuestLogType.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Strike camp to stop the clock, or ride home to the guild door entirely. " +
                    "Both deeds are inked into the audit chronicle.",
                style = QuestLogType.bodyMedium,
            )
            Spacer(Modifier.height(QuestPadSm))
            Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                if (repo.clockedIn) {
                    QuestGhostButton("Strike camp (clock out)") {
                        repo.clockedIn = false
                        repo.audit(hero.name, "CLOCK_OUT", "clocked out at ${repo.currentBranch.name}")
                    }
                } else {
                    QuestGhostButton("Take up arms (clock in)") {
                        repo.clockedIn = true
                        repo.audit(hero.name, "CLOCK_IN", "clocked in at ${repo.currentBranch.name}")
                    }
                }
                QuestGhostButton("Change outpost") {
                    repo.branchPicked = false
                    repo.audit(hero.name, "BRANCH.RETURN", "returned to the crossroads")
                }
                QuestPrimaryButton("Ride home (logout)") {
                    repo.audit(hero.name, "AUTH.SIGN_OUT", "${hero.name} left the board")
                    repo.authed = false
                    repo.branchPicked = false
                }
            }
        }
    }
}
