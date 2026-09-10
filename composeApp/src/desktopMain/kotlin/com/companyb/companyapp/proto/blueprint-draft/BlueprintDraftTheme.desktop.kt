package com.companyb.companyapp.proto.blueprintdraft

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BpBlue = Color(0xFF0E3A8A)
val BpBlueDeep = Color(0xFF0A2A66)
val BpSheet = Color(0xFF16449B)
val BpSheetDeep = Color(0xFF123A85)
val BpLine = Color(0xFFFFFFFF)
val BpLineDim = Color(0xFFC9DAFF)
val BpLineFaint = Color(0xFF8FA9DC)
val BpStamp = Color(0xFFFFD84D)
val BpGood = Color(0xFF9DF0B6)
val BpWarn = Color(0xFFFFD84D)
val BpBad = Color(0xFFFF9D9D)

private val BpTypography = Typography(
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
fun BlueprintDraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BpLine,
            onPrimary = BpBlue,
            secondary = BpLineDim,
            tertiary = BpStamp,
            background = BpBlue,
            surface = BpSheet,
            onBackground = BpLine,
            onSurface = BpLine,
            surfaceVariant = BpSheetDeep,
            onSurfaceVariant = BpLineDim,
            outline = BpLine,
            outlineVariant = BpLineFaint,
        ),
        typography = BpTypography,
        content = content,
    )
}

@Composable
fun BpGridBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(BpBlue)) {
        val minor = size.width / 32f
        val majorEvery = 4
        var x = 0f
        var col = 0
        while (x <= size.width) {
            val isMajor = col % majorEvery == 0
            drawLine(
                color = if (isMajor) BpLine.copy(alpha = 0.22f) else BpLine.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.6f else 1f,
            )
            x += minor
            col += 1
        }
        val rowH = size.height / 24f
        var y = 0f
        var r = 0
        while (y <= size.height) {
            val isMajor = r % majorEvery == 0
            drawLine(
                color = if (isMajor) BpLine.copy(alpha = 0.22f) else BpLine.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (isMajor) 1.6f else 1f,
            )
            y += rowH
            r += 1
        }
        drawRect(
            color = BpLine.copy(alpha = 0.5f),
            topLeft = Offset(8f, 8f),
            size = androidx.compose.ui.geometry.Size(size.width - 16f, size.height - 16f),
            style = Stroke(width = 2f),
        )
    }
}

@Composable
fun BpKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "⌖ " + text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = BpLineDim,
        letterSpacing = 2.sp,
    )
}

@Composable
fun BpDim(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = BpLineFaint,
    )
}

@Composable
fun BpDimensionBar(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("◄", color = BpLineFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.weight(1f).height(12.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cy = size.height / 2f
                drawLine(BpLineFaint, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(BpLineFaint, Offset(x, cy - 4f), Offset(x, cy + 4f), strokeWidth = 2f)
                    x += 14f
                }
            }
        }
        Text(label, color = BpLineDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.weight(1f).height(12.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cy = size.height / 2f
                drawLine(BpLineFaint, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(BpLineFaint, Offset(x, cy - 4f), Offset(x, cy + 4f), strokeWidth = 2f)
                    x += 14f
                }
            }
        }
        Text("►", color = BpLineFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun BpSheetCard(
    modifier: Modifier = Modifier,
    sheetNo: String = "A-101",
    scale: String = "SCALE 1:50",
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(2.dp, BpLine, RoundedCornerShape(2.dp))
            .background(BpSheet.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
            .padding(14.dp),
    ) {
        Box(modifier = Modifier.matchParentSize().padding(2.dp)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val c = 14f
                val w = size.width
                val h = size.height
                drawLine(BpLine, Offset(0f, 0f), Offset(c, 0f), strokeWidth = 3f)
                drawLine(BpLine, Offset(0f, 0f), Offset(0f, c), strokeWidth = 3f)
                drawLine(BpLine, Offset(w, 0f), Offset(w - c, 0f), strokeWidth = 3f)
                drawLine(BpLine, Offset(w, 0f), Offset(w, c), strokeWidth = 3f)
                drawLine(BpLine, Offset(0f, h), Offset(c, h), strokeWidth = 3f)
                drawLine(BpLine, Offset(0f, h), Offset(0f, h - c), strokeWidth = 3f)
                drawLine(BpLine, Offset(w, h), Offset(w - c, h), strokeWidth = 3f)
                drawLine(BpLine, Offset(w, h), Offset(w, h - c), strokeWidth = 3f)
            }
        }
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BpDim("SHEET $sheetNo")
                BpDim(scale)
            }
            content()
        }
    }
}

@Composable
fun BpDashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(10.dp)) {
        val cy = size.height / 2f
        drawLine(
            color = BpLineFaint,
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

@Composable
fun BpDraftStamp(text: String = "DRAFT · PROTOTYPE — NOT FOR BUILD", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.graphicsLayer { rotationZ = -4f },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .border(3.dp, BpStamp, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = BpStamp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
        }
    }
}

@Composable
fun BpCornerTicks(box: Dp = 56.dp) {
    Canvas(modifier = Modifier.size(box)) {
        val l = size.width * 0.45f
        drawLine(BpLineFaint, Offset(0f, 0f), Offset(l, 0f), strokeWidth = 3f)
        drawLine(BpLineFaint, Offset(0f, 0f), Offset(0f, l), strokeWidth = 3f)
    }
}

fun BpDayStatusColor(status: BpDayStatus): Color = when (status) {
    BpDayStatus.OPEN -> BpGood
    BpDayStatus.PAST -> BpLineDim
    BpDayStatus.REMITTED -> BpStamp
}

fun BpSessionStatusColor(status: BpSessionStatus): Color = when (status) {
    BpSessionStatus.PENDING -> BpStamp
    BpSessionStatus.COMPLETED -> BpGood
    BpSessionStatus.NO_SHOW -> BpLineDim
    BpSessionStatus.CANCELLED -> BpBad
}
