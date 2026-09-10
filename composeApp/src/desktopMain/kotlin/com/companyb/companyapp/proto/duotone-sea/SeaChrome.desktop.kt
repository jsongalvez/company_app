package com.companyb.companyapp.proto.duotonesea

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun WaveDivider(
    crest: Color = SeaPalette.Coral,
    trough: Color = SeaPalette.SeaGlass,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val width = size.width
        val height = size.height
        val back =
            Path().apply {
                moveTo(0f, height * 0.55f)
                var x = 0f
                while (x <= width) {
                    val y = height * 0.55f + sin(x / width * 2f * PI.toFloat()) * height * 0.30f
                    lineTo(x, y)
                    x += 6f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
        drawPath(back, trough.copy(alpha = 0.35f))
        val front =
            Path().apply {
                moveTo(0f, height * 0.62f)
                var x = 0f
                while (x <= width) {
                    val y = height * 0.62f + sin(x / width * 2f * PI.toFloat() + PI.toFloat()) *
                        height * 0.30f
                    lineTo(x, y)
                    x += 6f
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
        drawPath(front, crest.copy(alpha = 0.55f))
    }
}

@Composable
internal fun TideChip(
    text: String,
    color: Color,
) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(), style = SeaType.labelMedium, color = color)
    }
}

@Composable
internal fun TideChipClickable(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val color = if (active) SeaPalette.Coral else SeaPalette.Faint
    Box(
        Modifier
            .background(if (active) SeaPalette.Coral.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(), style = SeaType.labelSmall, color = color)
    }
}

@Composable
internal fun StatusFoam(
    status: SwimStatus,
    voided: Boolean,
) {
    if (voided) {
        TideChip("voided", SeaPalette.Siren)
        return
    }
    val color =
        when (status) {
            SwimStatus.PENDING -> SeaPalette.SunBuoy
            SwimStatus.COMPLETED -> SeaPalette.Kelp
            SwimStatus.NO_SHOW -> SeaPalette.SeaGlass
            SwimStatus.CANCELLED -> SeaPalette.Faint
        }
    TideChip(status.label, color)
}

@Composable
internal fun DayFoam(state: TideState) {
    val color =
        when (state) {
            TideState.OPEN -> SeaPalette.Kelp
            TideState.PAST -> SeaPalette.SunBuoy
            TideState.REMITTED -> SeaPalette.SeaGlass
        }
    TideChip(state.label, color)
}

@Composable
internal fun LagoonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(SeaPalette.Lagoon, RoundedCornerShape(10.dp))
            .border(1.dp, SeaPalette.ReefLine, RoundedCornerShape(10.dp))
            .padding(SeaPadMd),
        content = content,
    )
}

@Composable
internal fun DriftRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) SeaPalette.Coral.copy(alpha = 0.10f) else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .border(
                    1.dp,
                    if (selected) SeaPalette.Coral else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun SectionTitle(
    title: String,
    note: String = "",
) {
    Text(title, style = SeaType.titleLarge)
    if (note.isNotEmpty()) {
        Text(note, style = SeaType.bodySmall, color = SeaPalette.Mist)
    }
    Spacer(Modifier.height(SeaPadSm))
}

@Composable
internal fun NoteLine(text: String) {
    Text("≈ " + text, style = SeaType.bodySmall, color = SeaPalette.SeaGlass)
}

@Composable
internal fun BecalmedEmpty(
    title: String,
    hint: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("≈≈≈", style = SeaType.displaySmall)
        Text(title, style = SeaType.titleMedium)
        Text(hint, style = SeaType.bodySmall, color = SeaPalette.Mist)
    }
}
