package com.companyb.companyapp.proto.daybreakroutine

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun dawnPeso(amount: Int): String =
    "P" +
        amount
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()

@Composable
internal fun DawnBadge(
    text: String,
    color: Color,
    faint: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = if (faint) 0.12f else 0.2f))
                .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = DaybreakType.labelSmall, color = color)
    }
}

internal fun dawnStatusColor(status: DawnSessionStatus): Color =
    when (status) {
        DawnSessionStatus.PENDING -> DaybreakPalette.Gold
        DawnSessionStatus.COMPLETED -> DaybreakPalette.Sage
        DawnSessionStatus.NO_SHOW -> DaybreakPalette.Sky
        DawnSessionStatus.CANCELLED -> DaybreakPalette.Rose
    }

internal fun dawnDayColor(state: DawnDayState): Color =
    when (state) {
        DawnDayState.OPEN -> DaybreakPalette.Sage
        DawnDayState.PAST -> DaybreakPalette.Gold
        DawnDayState.REMITTED -> DaybreakPalette.Violet
    }

@Composable
internal fun DawnSectionHeader(
    title: String,
    detail: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = DawnPadSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("~ ", style = DaybreakType.titleMedium, color = DaybreakPalette.Sunrise)
        Text(title, style = DaybreakType.titleMedium)
        Spacer(Modifier.width(DawnPadSm))
        Text(detail, style = DaybreakType.bodySmall)
        Spacer(Modifier.weight(1f))
        trailing()
    }
    HorizontalDivider(color = DaybreakPalette.Line)
    Spacer(Modifier.height(DawnPadSm))
}

@Composable
internal fun DawnPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DaybreakPalette.Card)
                .border(1.dp, DaybreakPalette.Line, RoundedCornerShape(10.dp))
                .padding(DawnPadMd),
    ) {
        content()
    }
}

@Composable
internal fun DawnNote(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DaybreakPalette.PaperDeep)
                .border(1.dp, DaybreakPalette.Line, RoundedCornerShape(8.dp))
                .padding(DawnPadSm),
    ) {
        Text(text, style = DaybreakType.bodySmall)
    }
}

@Composable
internal fun DawnRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    var mod = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Row(mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = DaybreakType.bodyMedium)
        Text(value, style = DaybreakType.labelMedium)
    }
}

@Composable
internal fun DayBanner(repo: DaybreakRepo) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DaybreakPalette.Ink).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DAYBREAK  ", style = DaybreakType.labelSmall, color = Color(0xFFF2C879))
        DawnBadge(repo.currentDay.date, Color.White)
        Spacer(Modifier.width(8.dp))
        DawnBadge(repo.currentDay.state.label, dawnDayColor(repo.currentDay.state))
        Spacer(Modifier.width(8.dp))
        Text(
            repo.currentBranch.name + " · " + repo.currentBranch.kind,
            style = DaybreakType.bodySmall,
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        Text("04:00 Asia/Manila boundary", style = DaybreakType.labelSmall, color = Color(0xFFB9AE9C))
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    DaybreakPalette.PaperDeep,
                ).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repo.days.forEachIndexed { index, day ->
            val selected = index == repo.dayIndex
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color.White else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) DaybreakPalette.LineStrong else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        ).clickable { repo.dayIndex = index }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(day.date + " " + day.state.label, style = DaybreakType.labelMedium)
            }
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(repo.currentDay.note, style = DaybreakType.bodySmall)
    }
}
