package com.companyb.companyapp.proto.blockbuilder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BbCream = Color(0xFFFFF6E9)
val BbCreamDeep = Color(0xFFF1E4C8)
val BbInk = Color(0xFF23242B)
val BbInkSoft = Color(0xFF4B4E5A)
val BbMuted = Color(0xFF7A7D8C)
val BbBrickRed = Color(0xFFE03131)
val BbBrickRedDeep = Color(0xFFB02525)
val BbBrickBlue = Color(0xFF1971C2)
val BbBrickBlueDeep = Color(0xFF125A9C)
val BbBrickYellow = Color(0xFFFFC300)
val BbBrickYellowDeep = Color(0xFFD9A400)
val BbBaseGreen = Color(0xFF2F9E44)
val BbCard = Color(0xFFFFFFFF)
val BbUnread = Color(0xFFFFF3BF)
val BbOpen = Color(0xFF1971C2)
val BbPast = Color(0xFF4B4E5A)
val BbRemitted = Color(0xFFFFC300)

private val BbTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun BlockBuilderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BbBrickRed,
            onPrimary = Color.White,
            secondary = BbBrickBlue,
            tertiary = BbBrickYellow,
            background = BbCream,
            surface = BbCard,
            onBackground = BbInk,
            onSurface = BbInk,
        ),
        typography = BbTypography,
        content = content,
    )
}

@Composable
fun BbStuds(
    color: Color,
    count: Int = 4,
    modifier: Modifier = Modifier,
    studSize: Dp = 14.dp,
) {
    val n = count.coerceIn(1, 8)
    Canvas(modifier = modifier) {
        val d = studSize.toPx()
        val gap = d * 0.55f
        val totalW = n * d + (n - 1) * gap
        var x = (size.width - totalW) / 2f
        val cy = size.height / 2f
        repeat(n) {
            drawCircle(color = color, radius = d / 2f, center = androidx.compose.ui.geometry.Offset(x + d / 2f, cy))
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = d / 5f,
                center = androidx.compose.ui.geometry.Offset(x + d / 2f - d * 0.12f, cy - d * 0.12f),
            )
            x += d + gap
        }
    }
}

@Composable
fun BbBrickSwatch(
    color: Color,
    deep: Color,
    modifier: Modifier = Modifier,
    studs: Int = 2,
) {
    Canvas(modifier = modifier.size(40.dp, 30.dp)) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.28f
        drawRoundRect(
            color = deep,
            topLeft = androidx.compose.ui.geometry.Offset(0f, bodyTop + 3f),
            size = androidx.compose.ui.geometry.Size(w, h - bodyTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, bodyTop),
            size = androidx.compose.ui.geometry.Size(w, h - bodyTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
        val d = w / (studs * 2f)
        var x = (w - (studs * d + (studs - 1) * d * 0.6f)) / 2f
        repeat(studs) {
            drawCircle(color = color, radius = d / 2f, center = androidx.compose.ui.geometry.Offset(x + d / 2f, bodyTop - 2f))
            x += d * 1.6f
        }
    }
}

@Composable
fun BbStudRow(color: Color, count: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        BbStuds(color = color, count = count, modifier = Modifier.size(120.dp, 16.dp))
    }
}

fun BbDayStatus.brick(): Color = when (this) {
    BbDayStatus.OPEN -> BbBrickBlue
    BbDayStatus.PAST -> BbInkSoft
    BbDayStatus.REMITTED -> BbBrickYellow
}

fun BbDayStatus.deep(): Color = when (this) {
    BbDayStatus.OPEN -> BbBrickBlueDeep
    BbDayStatus.PAST -> BbInk
    BbDayStatus.REMITTED -> BbBrickYellowDeep
}

fun BbSessionStatus.chip(): Color = when (this) {
    BbSessionStatus.PENDING -> BbBrickRed
    BbSessionStatus.COMPLETED -> BbBaseGreen
    BbSessionStatus.NO_SHOW -> BbInkSoft
    BbSessionStatus.CANCELLED -> BbBrickYellowDeep
}
