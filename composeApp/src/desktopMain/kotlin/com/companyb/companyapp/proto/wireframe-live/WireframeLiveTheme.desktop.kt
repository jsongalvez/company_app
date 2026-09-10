package com.companyb.companyapp.proto.wireframelive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WfPaper = Color(0xFFFFFFFF)
val WfWash = Color(0xFFF4F4F5)
val WfBox = Color(0xFFE4E4E7)
val WfFill = Color(0xFFD4D4D8)
val WfFaint = Color(0xFFA1A1AA)
val WfMid = Color(0xFF52525B)
val WfInk = Color(0xFF18181B)
val WfInkSoft = Color(0xFF27272A)
val WfGood = Color(0xFF18181B)
val WfWarn = Color(0xFF52525B)
val WfBad = Color(0xFF71717A)

private val WfTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun WireframeLiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WfInk,
            onPrimary = WfPaper,
            secondary = WfMid,
            tertiary = WfInkSoft,
            background = WfPaper,
            surface = WfWash,
            onBackground = WfInk,
            onSurface = WfInk,
            surfaceVariant = WfBox,
            onSurfaceVariant = WfMid,
            outline = WfInk,
            outlineVariant = WfFaint,
        ),
        typography = WfTypography,
        content = content,
    )
}

@Composable
fun WfGridBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(WfPaper)) {
        val minor = size.width / 40f
        var x = 0f
        var col = 0
        while (x <= size.width) {
            val isMajor = col % 5 == 0
            drawLine(
                color = if (isMajor) WfFaint.copy(alpha = 0.55f) else WfFaint.copy(alpha = 0.22f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.4f else 1f,
            )
            x += minor
            col += 1
        }
        val rowH = size.height / 30f
        var y = 0f
        var r = 0
        while (y <= size.height) {
            val isMajor = r % 5 == 0
            drawLine(
                color = if (isMajor) WfFaint.copy(alpha = 0.55f) else WfFaint.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (isMajor) 1.4f else 1f,
            )
            y += rowH
            r += 1
        }
        drawRect(
            color = WfInk.copy(alpha = 0.6f),
            topLeft = Offset(8f, 8f),
            size = androidx.compose.ui.geometry.Size(size.width - 16f, size.height - 16f),
            style = Stroke(width = 2f),
        )
    }
}

@Composable
fun WfKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "▦ " + text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = WfMid,
        letterSpacing = 2.sp,
    )
}

@Composable
fun WfDim(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = WfMid,
    )
}

@Composable
fun WfFlag(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .background(WfInk, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = WfPaper,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun WfFlagNote(number: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WfFlag(number)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = WfMid,
        )
    }
}

@Composable
fun WfRuler(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("◄", color = WfFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.weight(1f).height(12.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cy = size.height / 2f
                drawLine(WfFaint, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(WfFaint, Offset(x, cy - 4f), Offset(x, cy + 4f), strokeWidth = 2f)
                    x += 12f
                }
            }
        }
        Text(label, color = WfMid, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.weight(1f).height(12.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cy = size.height / 2f
                drawLine(WfFaint, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(WfFaint, Offset(x, cy - 4f), Offset(x, cy + 4f), strokeWidth = 2f)
                    x += 12f
                }
            }
        }
        Text("►", color = WfFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun WfBoxCard(
    modifier: Modifier = Modifier,
    boxNo: String = "BOX-00",
    flag: Int? = null,
    strip: String = "WIREFRAME · LIVE FILL",
    showFlags: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(2.dp, WfInk, RoundedCornerShape(2.dp))
            .background(WfPaper, RoundedCornerShape(2.dp)),
    ) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(WfBox, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .border(1.dp, WfInk)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showFlags && flag != null) WfFlag(flag)
                    Text(
                        boxNo,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = WfInk,
                        letterSpacing = 1.sp,
                    )
                }
                Text(
                    strip,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = WfMid,
                    letterSpacing = 1.sp,
                )
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun WfDashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        val cy = size.height / 2f
        drawLine(
            color = WfFaint,
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)),
        )
    }
}

@Composable
fun WfLiveStamp(text: String = "LIVE · BOXES FILLED WITH FAKE DATA", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(2.dp, WfInk, RoundedCornerShape(2.dp))
            .background(WfInk)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = WfPaper,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
fun WfFillBlock(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        val stripe = 10f
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = WfFill,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = 5f,
            )
            x += stripe * 1.8f
        }
        drawRect(color = WfFaint, topLeft = Offset.Zero, size = size, style = Stroke(width = 1.5f))
    }
}

fun WfDayFill(status: WfDayStatus): Color = when (status) {
    WfDayStatus.OPEN -> WfPaper
    WfDayStatus.PAST -> WfBox
    WfDayStatus.REMITTED -> WfInk
}

fun WfDayText(status: WfDayStatus): Color = when (status) {
    WfDayStatus.OPEN -> WfInk
    WfDayStatus.PAST -> WfInk
    WfDayStatus.REMITTED -> WfPaper
}
