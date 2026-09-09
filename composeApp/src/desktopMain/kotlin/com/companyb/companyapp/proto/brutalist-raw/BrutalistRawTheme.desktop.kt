package com.companyb.companyapp.proto.brutalistraw

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RawPaper = Color(0xFFD6D3CC)
val RawSlab = Color(0xFFB9B5AB)
val RawGravel = Color(0xFF8F8B81)
val RawDark = Color(0xFF2B2B29)
val RawInk = Color(0xFF111111)
val RawWhite = Color(0xFFF4F2ED)
val RawHazard = Color(0xFFFF4D00)
val RawHazardSoft = Color(0xFFFFD9C7)
val RawTar = Color(0xFF1A1A19)
val RawLine = Color(0xFF111111)

private val RawLightColors = lightColorScheme(
    primary = RawInk,
    onPrimary = RawWhite,
    primaryContainer = RawTar,
    onPrimaryContainer = RawWhite,
    secondary = RawHazard,
    onSecondary = RawWhite,
    secondaryContainer = RawHazardSoft,
    onSecondaryContainer = RawInk,
    tertiary = RawDark,
    onTertiary = RawWhite,
    tertiaryContainer = RawSlab,
    onTertiaryContainer = RawInk,
    background = RawPaper,
    onBackground = RawInk,
    surface = RawWhite,
    onSurface = RawInk,
    surfaceVariant = RawSlab,
    onSurfaceVariant = RawInk,
    outline = RawLine,
    outlineVariant = RawGravel,
)

val RawShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

private val RawTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 48.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

@Composable
fun BrutalistRawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RawLightColors,
        shapes = RawShapes,
        typography = RawTypography,
        content = content,
    )
}
