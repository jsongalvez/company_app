package com.companyb.companyapp.proto.arcadecabinet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CabBlack = Color(0xFF0A0A12)
val CabPanel = Color(0xFF12121E)
val CabPanelHi = Color(0xFF1B1B2C)
val CabChrome = Color(0xFF9AA3B2)
val CabChromeHi = Color(0xFFD7DEE8)
val CabNeonPink = Color(0xFFFF2E88)
val CabNeonCyan = Color(0xFF00E5FF)
val CabNeonYellow = Color(0xFFFFD400)
val CabNeonGreen = Color(0xFF39FF6A)
val CabNeonPurple = Color(0xFF9D4DFF)
val CabInk = Color(0xFFF2F4FA)
val CabMuted = Color(0xFF8E96AB)
val CabDanger = Color(0xFFFF4D5E)
val CabCoin = Color(0xFFFFB800)

private val CabColors = darkColorScheme(
    primary = CabNeonPink,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A1030),
    onPrimaryContainer = Color(0xFFFFC7E2),
    secondary = CabNeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0B3540),
    onSecondaryContainer = Color(0xFFBFF3FF),
    tertiary = CabNeonYellow,
    onTertiary = Color.Black,
    background = CabBlack,
    onBackground = CabInk,
    surface = CabPanel,
    onSurface = CabInk,
    surfaceVariant = CabPanelHi,
    onSurfaceVariant = CabMuted,
    outline = Color(0xFF2E2E44),
    outlineVariant = Color(0xFF23232F),
    error = CabDanger,
)

val CabShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val CabTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
    headlineSmall = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
)

@Composable
fun ArcadeCabinetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CabColors,
        shapes = CabShapes,
        typography = CabTypography,
        content = content,
    )
}
