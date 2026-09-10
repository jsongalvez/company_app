package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun peso(amount: Int): String =
    "P" +
        amount
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

@Composable
internal fun RushBadge(
    text: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = LunchRushType.labelSmall, color = color)
    }
}

internal fun statusColor(status: RushSessionStatus): Color =
    when (status) {
        RushSessionStatus.PENDING -> LunchRushPalette.Turmeric
        RushSessionStatus.COMPLETED -> LunchRushPalette.Leaf
        RushSessionStatus.NO_SHOW -> LunchRushPalette.Steam
        RushSessionStatus.CANCELLED -> LunchRushPalette.Char
    }

internal fun dayColor(state: RushDayState): Color =
    when (state) {
        RushDayState.OPEN -> LunchRushPalette.Leaf
        RushDayState.PAST -> LunchRushPalette.Turmeric
        RushDayState.REMITTED -> LunchRushPalette.Steam
    }

@Composable
internal fun RushFlag(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LunchRushPalette.Char.copy(alpha = 0.10f))
                .border(1.dp, LunchRushPalette.Char.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FIRE ", style = LunchRushType.labelLarge, color = LunchRushPalette.Char)
        Text(text, style = LunchRushType.bodyMedium, color = LunchRushPalette.CharDeep)
    }
}

@Composable
internal fun RushTicketCard(
    modifier: Modifier = Modifier,
    hot: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var cardModifier =
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (hot) LunchRushPalette.CardHot else LunchRushPalette.Card)
            .border(
                1.dp,
                if (hot) LunchRushPalette.TicketEdge.copy(alpha = 0.6f) else LunchRushPalette.Border,
                RoundedCornerShape(10.dp),
            ).padding(RushPadMd)
    if (onClick != null) cardModifier = cardModifier.clickable(onClick = onClick)
    Column(modifier = cardModifier) {
        if (hot) {
            Row {
                repeat(12) {
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(3.dp)
                            .background(LunchRushPalette.TicketEdge.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(RushPadSm))
        }
        content()
    }
}

@Composable
internal fun RushSectionHeader(
    title: String,
    detail: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = RushPadSm), verticalAlignment = Alignment.CenterVertically) {
        Text("## ", style = LunchRushType.titleMedium, color = LunchRushPalette.Char)
        Text(title, style = LunchRushType.titleMedium)
        Spacer(Modifier.width(RushPadSm))
        Text(detail, style = LunchRushType.bodySmall)
        Spacer(Modifier.weight(1f))
        trailing()
    }
    HorizontalDivider(color = LunchRushPalette.Border)
    Spacer(Modifier.height(RushPadSm))
}

@Composable
internal fun RushNote(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LunchRushPalette.CardHot)
                .border(1.dp, LunchRushPalette.Border, RoundedCornerShape(8.dp))
                .padding(RushPadMd),
    ) {
        Text(text, style = LunchRushType.bodySmall)
    }
}

@Composable
internal fun RushMeter(
    level: Int,
    max: Int = 8,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("RUSH METER", style = LunchRushType.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("$level/$max tickets of heat", style = LunchRushType.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (level.coerceAtMost(max)).toFloat() / max.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (level >= 5) LunchRushPalette.Char else LunchRushPalette.Turmeric,
            trackColor = LunchRushPalette.Border.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun RushRow(
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        left()
        right()
    }
}
