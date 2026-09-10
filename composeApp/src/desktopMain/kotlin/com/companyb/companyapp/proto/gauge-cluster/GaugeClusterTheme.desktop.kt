package com.companyb.companyapp.proto.gaugecluster

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// #834 — gauge-cluster theme: vintage instrument panel. Mahogany fascia, brass
// bezels, cream dial faces, red needles. Deliberately distinct from flat cards.

object GaugeColors {
    val Fascia = Color(0xFF2A1A12)
    val FasciaDeep = Color(0xFF1C100B)
    val Bezel = Color(0xFFB08D3E)
    val BezelDark = Color(0xFF7A5F26)
    val Face = Color(0xFFF4E9CE)
    val FaceDim = Color(0xFFE4D3AC)
    val Ink = Color(0xFF2B2118)
    val InkSoft = Color(0xFF6B5B45)
    val Needle = Color(0xFFC0272D)
    val LampGreen = Color(0xFF4CAF50)
    val LampAmber = Color(0xFFFFA000)
    val LampRed = Color(0xFFE53935)
    val LampBlue = Color(0xFF64B5F6)
    val Plate = Color(0xFF3A241A)
    val PlateLine = Color(0xFF6B4A2E)
    val Cream = Color(0xFFF6EFDB)
    val CreamDim = Color(0xFFC9B78F)
}

fun GaugeDayStatus.lamp(): Color = when (this) {
    GaugeDayStatus.OPEN -> GaugeColors.LampGreen
    GaugeDayStatus.PAST -> GaugeColors.LampAmber
    GaugeDayStatus.REMITTED -> GaugeColors.LampBlue
}

fun GaugeSessionStatus.lamp(): Color = when (this) {
    GaugeSessionStatus.PENDING -> GaugeColors.LampAmber
    GaugeSessionStatus.COMPLETED -> GaugeColors.LampGreen
    GaugeSessionStatus.NO_SHOW -> GaugeColors.BezelDark
    GaugeSessionStatus.CANCELLED -> GaugeColors.LampRed
}

@Composable
fun GaugeDial(
    label: String,
    value: Int,
    max: Int,
    unit: String,
    size: Dp = 168.dp,
    redlineFrom: Float = 0.85f,
) {
    val frac = if (max <= 0) 0f else (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size)
                .clip(RoundedCornerShape(size / 2))
                .background(GaugeColors.Bezel)
                .padding(7.dp)
                .clip(RoundedCornerShape(size / 2))
                .background(GaugeColors.Face)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val r = this.size.minDimension / 2f
                val start = 135f
                val sweep = 270f
                drawArc(
                    color = GaugeColors.FaceDim,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = r * 0.10f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = GaugeColors.LampRed,
                    startAngle = start + sweep * redlineFrom,
                    sweepAngle = sweep * (1f - redlineFrom),
                    useCenter = false,
                    style = Stroke(width = r * 0.10f, cap = StrokeCap.Butt),
                )
                for (i in 0..10) {
                    val a = Math.toRadians((start + sweep * i / 10f).toDouble())
                    val outer = Offset(
                        (c.x + cos(a).toFloat() * r * 0.86f),
                        (c.y + sin(a).toFloat() * r * 0.86f),
                    )
                    val inner = Offset(
                        (c.x + cos(a).toFloat() * r * (if (i % 5 == 0) 0.62f else 0.74f)),
                        (c.y + sin(a).toFloat() * r * (if (i % 5 == 0) 0.62f else 0.74f)),
                    )
                    drawLine(GaugeColors.Ink, inner, outer, strokeWidth = if (i % 5 == 0) 5f else 2.5f)
                }
                val na = Math.toRadians((start + sweep * frac).toDouble())
                val tip = Offset(
                    (c.x + cos(na).toFloat() * r * 0.78f),
                    (c.y + sin(na).toFloat() * r * 0.78f),
                )
                drawLine(GaugeColors.Needle, c, tip, strokeWidth = 6f, cap = StrokeCap.Round)
                drawCircle(GaugeColors.Ink, radius = r * 0.09f, center = c)
                drawCircle(GaugeColors.Bezel, radius = r * 0.045f, center = c)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(size * 0.30f))
                Text(
                    "$value / $max $unit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GaugeColors.Ink,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GaugeColors.Cream)
        Text(
            if (frac >= 1f) "AT LIMIT" else if (frac >= redlineFrom) "REDLINE" else "NORMAL",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (frac >= redlineFrom) GaugeColors.LampRed else GaugeColors.CreamDim,
        )
    }
}

@Composable
fun GaugePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GaugeColors.Plate)
            .border(1.dp, GaugeColors.PlateLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = GaugeColors.Bezel,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun GaugeLamp(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GaugeColors.Cream)
    }
}

@Composable
fun GaugeButton(label: String, onClick: () -> Unit, primary: Boolean = true) {
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = GaugeColors.Bezel,
                contentColor = GaugeColors.FasciaDeep,
            ),
        ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GaugeColors.Cream),
        ) { Text(label, fontSize = 13.sp) }
    }
}

@Composable
fun GaugeLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = GaugeColors.Bezel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GaugeNote(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GaugeColors.FasciaDeep)
            .border(1.dp, GaugeColors.PlateLine, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, fontSize = 13.sp, color = GaugeColors.CreamDim, lineHeight = 18.sp)
    }
}

@Composable
fun GaugeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = GaugeColors.CreamDim)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GaugeColors.Cream)
    }
}

@Composable
fun GaugeChipRow(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) GaugeColors.Bezel else GaugeColors.FasciaDeep)
                    .border(1.dp, GaugeColors.PlateLine, RoundedCornerShape(20.dp))
                    .clickable { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) GaugeColors.FasciaDeep else GaugeColors.CreamDim,
                )
            }
        }
    }
}

@Composable
fun RowScope.GaugeCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(GaugeColors.FasciaDeep)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GaugeColors.Cream)
        Text(label, fontSize = 11.sp, color = GaugeColors.CreamDim)
    }
}
