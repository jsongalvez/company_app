package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #779 — metrics-mosaic calm-paper tile theme. Warm paper default, ink text,
// clay/amber/teal accents. Mosaic tiles carry sparklines; exception tiles
// surface in clay. Deliberately distinct from Linear and sibling variants.

object MmColors {
    val Paper = Color(0xFFF7F3EC)
    val Panel = Color(0xFFFFFDF8)
    val Edge = Color(0xFFE3DCCC)
    val Ink = Color(0xFF211D15)
    val Soft = Color(0xFF6E675A)
    val Faint = Color(0xFFA79D89)
    val Teal = Color(0xFF0E7C6B)
    val TealWash = Color(0xFFDDEEE8)
    val Amber = Color(0xFFB7791F)
    val AmberWash = Color(0xFFF7E8C8)
    val Clay = Color(0xFFB3402A)
    val ClayWash = Color(0xFFF6DCD3)
    val Plum = Color(0xFF6C4FA1)
    val PlumWash = Color(0xFFE6DEF4)
    val Moss = Color(0xFF5C7A2E)
    val MossWash = Color(0xFFE3EACF)
    val Bar = Color(0xFF2A2620)
}

private val MmLight = lightColorScheme(
    primary = MmColors.Teal,
    onPrimary = Color.White,
    background = MmColors.Paper,
    surface = MmColors.Panel,
    onBackground = MmColors.Ink,
    onSurface = MmColors.Ink,
)

private val MmDark = darkColorScheme(
    primary = MmColors.Teal,
    background = MmColors.Bar,
    surface = MmColors.Bar,
)

@Composable
fun MmRoot(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) MmDark else MmLight) {
        Box(Modifier.fillMaxSize().background(if (dark) MmColors.Bar else MmColors.Paper)) {
            content()
        }
    }
}

@Composable
fun MmTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MmColors.Ink)
}

@Composable
fun MmSection(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MmColors.Soft,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun MmText(text: String, color: Color = MmColors.Ink, size: Int = 14, bold: Boolean = false) {
    Text(
        text,
        fontSize = size.sp,
        color = color,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
fun MmNote(text: String) {
    Text(text, fontSize = 12.sp, color = MmColors.Soft)
}

@Composable
fun MmRule() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(MmColors.Edge))
}

@Composable
fun MmBadge(text: String, wash: Color, ink: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(wash).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

@Composable
fun MmStatusBadge(status: MmSessionStatus) {
    val (wash, ink) = when (status) {
        MmSessionStatus.PENDING -> MmColors.AmberWash to MmColors.Amber
        MmSessionStatus.COMPLETED -> MmColors.TealWash to MmColors.Teal
        MmSessionStatus.NO_SHOW -> MmColors.PlumWash to MmColors.Plum
        MmSessionStatus.CANCELLED -> MmColors.Edge to MmColors.Soft
    }
    MmBadge(status.name, wash, ink)
}

@Composable
fun MmDayBadge(status: MmDayStatus) {
    val (wash, ink) = when (status) {
        MmDayStatus.OPEN -> MmColors.TealWash to MmColors.Teal
        MmDayStatus.PAST -> MmColors.AmberWash to MmColors.Amber
        MmDayStatus.REMITTED -> MmColors.Edge to MmColors.Soft
    }
    MmBadge(status.name, wash, ink)
}

// Mosaic tile: calm panel, hairline edge, soft lift. Click drills down.
@Composable
fun MmTile(
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    var m = modifier
        .shadow(3.dp, shape, spotColor = Color(0x1A211D15))
        .clip(shape)
        .background(if (alert) MmColors.ClayWash else MmColors.Panel)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun RowScope.MmCell(
    weight: Float,
    alert: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MmTile(Modifier.weight(weight).fillMaxWidth(), alert, onClick, content)
}

@Composable
fun MmKpi(label: String, value: String, delta: String, series: List<Float>, line: Color = MmColors.Teal) {
    MmText(label.uppercase(), color = MmColors.Soft, size = 11, bold = true)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MmColors.Ink)
        Spacer(Modifier.width(8.dp))
        Text(delta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = line)
    }
    MmSparkline(series, line)
}

// Canvas sparkline with soft area fill. Values normalized to tile width.
@Composable
fun MmSparkline(series: List<Float>, line: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        if (series.size < 2) return@Canvas
        val min = series.minOrNull() ?: 0f
        val max = series.maxOrNull() ?: 1f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (series.size - 1)
        val pts = series.mapIndexed { i, v ->
            Offset(i * stepX, size.height - 4f - ((v - min) / span) * (size.height - 10f))
        }
        val area = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(area, color = line.copy(alpha = 0.16f))
        val stroke = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(stroke, color = line, style = Stroke(width = 2.5f))
        drawCircle(line, radius = 3.5f, center = pts.last())
    }
}

@Composable
fun MmButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) MmColors.Clay else MmColors.Teal,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MmGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Text(label, fontSize = 13.sp, color = MmColors.Ink)
    }
}

@Composable
fun MmField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MmColors.Panel,
            unfocusedContainerColor = MmColors.Panel,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun MmDot(color: Color) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(color))
}
