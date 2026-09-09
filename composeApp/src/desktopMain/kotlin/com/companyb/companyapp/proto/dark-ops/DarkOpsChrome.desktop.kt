package com.companyb.companyapp.proto.darkops

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
internal fun OpsBadge(
    text: String,
    color: Color,
    faint: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = if (faint) 0.12f else 0.18f))
                .border(
                    1.dp,
                    color.copy(alpha = 0.5f),
                    RoundedCornerShape(3.dp),
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = DarkOpsType.labelSmall, color = color)
    }
}

internal fun statusColor(status: OpsSessionStatus): Color =
    when (status) {
        OpsSessionStatus.PENDING -> DarkOpsPalette.Amber
        OpsSessionStatus.COMPLETED -> DarkOpsPalette.Phosphor
        OpsSessionStatus.NO_SHOW -> DarkOpsPalette.Cyan
        OpsSessionStatus.CANCELLED -> DarkOpsPalette.Red
    }

internal fun dayColor(state: OpsDayState): Color =
    when (state) {
        OpsDayState.OPEN -> DarkOpsPalette.Phosphor
        OpsDayState.PAST -> DarkOpsPalette.Amber
        OpsDayState.REMITTED -> DarkOpsPalette.Violet
    }

@Composable
internal fun OpsKbd(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(DarkOpsPalette.PanelRaised)
                .border(
                    1.dp,
                    DarkOpsPalette.BorderBright,
                    RoundedCornerShape(3.dp),
                ).padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, style = DarkOpsType.labelSmall, color = DarkOpsPalette.Dim)
    }
}

@Composable
internal fun OpsSectionHeader(
    title: String,
    detail: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = OpsPadSm), verticalAlignment = Alignment.CenterVertically) {
        Text("// ", style = DarkOpsType.titleMedium, color = DarkOpsPalette.Phosphor)
        Text(title, style = DarkOpsType.titleMedium)
        Spacer(Modifier.width(OpsPadSm))
        Text(detail, style = DarkOpsType.bodySmall)
        Spacer(Modifier.weight(1f))
        trailing()
    }
    HorizontalDivider(color = DarkOpsPalette.Border)
    Spacer(Modifier.width(0.dp))
}

@Composable
internal fun OpsPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(DarkOpsPalette.Panel)
                .border(1.dp, DarkOpsPalette.Border, RoundedCornerShape(6.dp))
                .padding(OpsPadMd),
    ) {
        content()
    }
}

@Composable
internal fun OpsRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val bg = if (selected) DarkOpsPalette.PhosphorDim.copy(alpha = 0.35f) else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = OpsPadSm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun OpsNote(text: String) {
    Text("> " + text, style = DarkOpsType.bodySmall, color = DarkOpsPalette.Faint)
}

internal fun Modifier.opsListKeys(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEnter: () -> Unit = {},
): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.J -> {
                onDown()
                true
            }

            Key.K -> {
                onUp()
                true
            }

            Key.Enter, Key.NumPadEnter -> {
                onEnter()
                true
            }

            else -> {
                false
            }
        }
    }

@Composable
internal fun OpsBadgeClickable(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val color = if (active) DarkOpsPalette.Phosphor else DarkOpsPalette.Faint
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = DarkOpsType.labelSmall, color = color)
    }
}

@Composable
internal fun OpsEmpty(
    what: String,
    hint: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(OpsPadLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("[ empty ]", style = DarkOpsType.titleMedium, color = DarkOpsPalette.Faint)
        Text(what, style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Dim)
        Text(hint, style = DarkOpsType.bodySmall)
    }
}
