package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(6.dp)

@Composable
internal fun QuestSectionTitle(
    title: String,
    flavor: String,
) {
    Text(title, style = QuestLogType.displaySmall)
    Text(flavor, style = QuestLogType.bodySmall)
    Spacer(Modifier.height(QuestPadMd))
}

@Composable
internal fun QuestCard(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(QuestLogPalette.Tavern)
                .border(1.dp, QuestLogPalette.Border, CardShape)
                .padding(QuestPadMd),
    ) {
        Column { content() }
    }
}

@Composable
internal fun QuestBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(BadgeShape)
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.6f), BadgeShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = QuestLogType.labelMedium.copy(color = color))
    }
}

@Composable
internal fun QuestBadgeClickable(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val color = if (active) QuestLogPalette.Gold else QuestLogPalette.Faint
    Box(
        modifier =
            Modifier
                .clip(BadgeShape)
                .background(if (active) color.copy(alpha = 0.22f) else QuestLogPalette.TavernRaised)
                .border(1.dp, color, BadgeShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = QuestLogType.labelMedium.copy(color = color))
    }
}

@Composable
internal fun QuestXpBar(progress: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(QuestLogPalette.Night)
                .border(1.dp, QuestLogPalette.GoldDeep, RoundedCornerShape(5.dp)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(QuestLogPalette.XpGreen),
        )
    }
}

@Composable
internal fun QuestDayBanner(repo: QuestLogRepo) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(QuestLogPalette.Tavern)
                .padding(horizontal = QuestPadMd, vertical = QuestPadSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DAY CHAIN", style = QuestLogType.labelSmall)
            Spacer(Modifier.width(QuestPadSm))
            repo.days.forEachIndexed { index, day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuestBadgeClickable(day.date + " · " + day.state.label, index == repo.dayIndex) {
                        repo.dayIndex = index
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            QuestBadge(repo.currentBranch.name + " · " + repo.currentBranch.kind, QuestLogPalette.RareViolet)
            Spacer(Modifier.width(6.dp))
            QuestBadge(
                if (repo.clockedIn) "IN THE FIELD" else "AT CAMP",
                if (repo.clockedIn) QuestLogPalette.XpGreen else QuestLogPalette.Faint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Link ${repo.currentDay.date} · ${repo.currentDay.state.label} — ${repo.currentDay.note}. " +
                "The day turns at 04:00 Asia/Manila, never at midnight.",
            style = QuestLogType.bodySmall,
        )
    }
}

@Composable
internal fun QuestNavRail(repo: QuestLogRepo) {
    Column(
        modifier =
            Modifier
                .width(QuestRailWidth)
                .fillMaxHeight()
                .background(QuestLogPalette.Tavern)
                .padding(QuestPadSm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("QUEST LOG", style = QuestLogType.labelSmall)
        Spacer(Modifier.height(2.dp))
        QuestScreen.entries.forEach { screen ->
            val active = repo.screen == screen
            val suffix =
                when (screen) {
                    QuestScreen.QUESTS -> if (repo.pendingCount > 0) " (${repo.pendingCount})" else ""
                    QuestScreen.RAVEN -> if (repo.unreadCount > 0) " (${repo.unreadCount})" else ""
                    else -> ""
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) QuestLogPalette.Gold.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) QuestLogPalette.Gold else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { repo.screen = screen }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        screen.title + suffix,
                        style =
                            QuestLogType.labelLarge.copy(
                                color = if (active) QuestLogPalette.Gold else QuestLogPalette.Parchment,
                            ),
                    )
                    Text(screen.hint, style = QuestLogType.bodySmall)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = QuestLogPalette.Border)
        Spacer(Modifier.height(4.dp))
        Text(
            "Lv ${repo.heroLevel()} · ${repo.completedXp()} XP",
            style = QuestLogType.labelMedium.copy(color = QuestLogPalette.XpGreen),
        )
        Spacer(Modifier.height(4.dp))
        QuestXpBar(repo.heroLevelProgress())
        Spacer(Modifier.height(4.dp))
        Text("XP is commission turned in.", style = QuestLogType.bodySmall)
    }
}

@Composable
internal fun QuestStatusBar(repo: QuestLogRepo) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(QuestLogPalette.Tavern)
                .padding(horizontal = QuestPadMd, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${repo.currentUser.name} · ${repo.currentUser.role}",
            style = QuestLogType.labelMedium,
        )
        Spacer(Modifier.width(QuestPadMd))
        Text("party on duty: ${repo.clockCrew().size}", style = QuestLogType.bodySmall)
        Spacer(Modifier.weight(1f))
        Text("fake ledger · no network", style = QuestLogType.bodySmall)
    }
}

@Composable
internal fun QuestPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = QuestLogPalette.Gold,
                contentColor = QuestLogPalette.Night,
                disabledContainerColor = QuestLogPalette.TavernRaised,
                disabledContentColor = QuestLogPalette.Faint,
            ),
    ) {
        Text(text, style = QuestLogType.labelLarge)
    }
}

@Composable
internal fun QuestGhostButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(text, style = QuestLogType.labelLarge)
    }
}

@Composable
internal fun QuestLinkButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, style = QuestLogType.labelLarge.copy(color = QuestLogPalette.ManaBlue))
    }
}
