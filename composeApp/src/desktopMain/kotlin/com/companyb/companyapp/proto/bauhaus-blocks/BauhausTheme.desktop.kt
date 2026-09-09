package com.companyb.companyapp.proto.bauhausblocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BhPaper = Color(0xFFF5F0E6)
val BhPaperDeep = Color(0xFFE9E1CF)
val BhInk = Color(0xFF141414)
val BhInkSoft = Color(0xFF3A3A3A)
val BhRed = Color(0xFFE30613)
val BhBlue = Color(0xFF0057A8)
val BhYellow = Color(0xFFFFD200)
val BhCard = Color(0xFFFFFEF9)
val BhLine = Color(0xFF141414)
val BhMuted = Color(0xFF6B6B6B)
val BhOpen = Color(0xFF0057A8)
val BhPast = Color(0xFF3A3A3A)
val BhRemitted = Color(0xFF9A7B00)
val BhRemittedSoft = Color(0xFFFFF3C4)

val BhGrotesk = FontFamily.SansSerif

private val BhTypography = Typography(
    displaySmall = TextStyle(fontFamily = BhGrotesk, fontWeight = FontWeight.Black, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = BhGrotesk, fontWeight = FontWeight.Black, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = BhGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = BhGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun BauhausTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BhRed,
            onPrimary = Color.White,
            secondary = BhBlue,
            tertiary = BhYellow,
            background = BhPaper,
            surface = BhCard,
            onBackground = BhInk,
            onSurface = BhInk,
        ),
        typography = BhTypography,
        content = content,
    )
}

enum class BhShape { CIRCLE, SQUARE, TRIANGLE, HALF, BARS, ARCH }

@Composable
fun BhShapeIcon(
    shape: BhShape,
    color: Color,
    size: Dp = 22.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (shape) {
            BhShape.CIRCLE -> drawCircle(color = color, radius = w / 2f, center = Offset(w / 2f, h / 2f))
            BhShape.SQUARE -> drawRect(color = color, topLeft = Offset.Zero, size = Size(w, h))
            BhShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = path, color = color)
            }
            BhShape.HALF -> drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(0f, h * 0.12f),
                size = Size(w, h * 0.88f),
            )
            BhShape.BARS -> {
                val barW = w / 5f
                drawRect(color = color, topLeft = Offset(0f, h * 0.45f), size = Size(barW, h * 0.55f))
                drawRect(color = color, topLeft = Offset(barW * 2f, h * 0.2f), size = Size(barW, h * 0.8f))
                drawRect(color = color, topLeft = Offset(barW * 4f, 0f), size = Size(barW, h))
            }
            BhShape.ARCH -> drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(0f, h * 0.12f),
                size = Size(w, h * 0.88f),
            )
        }
    }
}

fun BhDayStatus.blockColor(): Color = when (this) {
    BhDayStatus.OPEN -> BhBlue
    BhDayStatus.PAST -> BhInk
    BhDayStatus.REMITTED -> BhYellow
}

fun BhSessionStatus.chipColor(): Color = when (this) {
    BhSessionStatus.PENDING -> BhRed
    BhSessionStatus.COMPLETED -> BhBlue
    BhSessionStatus.NO_SHOW -> BhMuted
    BhSessionStatus.CANCELLED -> BhInk
}
