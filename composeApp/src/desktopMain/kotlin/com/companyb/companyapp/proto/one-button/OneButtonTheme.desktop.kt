package com.companyb.companyapp.proto.onebutton

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

// #852 — one-button spotlight theme: near-black stage, one vivid amber button,
// everything else flat gray. The hero button is the only saturated element.

val ObStage = Color(0xFF0B0B0D)
val ObPanel = Color(0xFF141417)
val ObCard = Color(0xFF1B1B1F)
val ObRaised = Color(0xFF232329)
val ObLine = Color(0xFF2E2E35)
val ObFaintLine = Color(0xFF222228)
val ObInk = Color(0xFFF5F4F0)
val ObGray = Color(0xFFB9B8BE)
val ObDim = Color(0xFF7E7D87)
val ObGhost = Color(0xFF55555E)
val ObAmber = Color(0xFFFFB020)
val ObAmberDeep = Color(0xFF3A2A08)
val ObAmberInk = Color(0xFF1A1204)
val ObGreen = Color(0xFF7BD88F)
val ObGreenDeep = Color(0xFF17301F)
val ObRed = Color(0xFFF0889A)
val ObRedDeep = Color(0xFF3A1B21)
val ObBlue = Color(0xFF8FB4F5)
val ObBlueDeep = Color(0xFF1B2740)

private val ObColors = darkColorScheme(
    primary = ObAmber,
    onPrimary = ObAmberInk,
    primaryContainer = ObAmberDeep,
    onPrimaryContainer = ObInk,
    secondary = ObGray,
    onSecondary = ObStage,
    secondaryContainer = ObRaised,
    onSecondaryContainer = ObGray,
    tertiary = ObBlue,
    onTertiary = ObStage,
    tertiaryContainer = ObBlueDeep,
    onTertiaryContainer = ObInk,
    background = ObStage,
    onBackground = ObInk,
    surface = ObCard,
    onSurface = ObInk,
    surfaceVariant = ObRaised,
    onSurfaceVariant = ObGray,
    outline = ObLine,
    outlineVariant = ObFaintLine,
    error = ObRed,
    onError = ObStage,
)

val ObShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ObTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun OneButtonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ObColors, shapes = ObShapes, typography = ObTypography, content = content)
}
