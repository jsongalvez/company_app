package com.companyb.companyapp.proto.warmcare

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WarmCream = Color(0xFFFDF6EE)
val WarmSand = Color(0xFFF6EBDD)
val WarmCard = Color(0xFFFFFFFF)
val WarmLine = Color(0xFFE5D5C0)
val WarmEspresso = Color(0xFF3E2F23)
val WarmMuted = Color(0xFF8A7560)
val WarmTerracotta = Color(0xFFB4552D)
val WarmTerracottaDark = Color(0xFF8F3F1F)
val WarmHoney = Color(0xFFD9A441)
val WarmSage = Color(0xFF6B7F59)
val WarmSageSoft = Color(0xFFE6EBDD)
val WarmBlush = Color(0xFFF3DDD2)
val WarmSky = Color(0xFFDDE8F0)

private val WarmLightColors = lightColorScheme(
    primary = WarmTerracotta,
    onPrimary = Color.White,
    primaryContainer = WarmBlush,
    onPrimaryContainer = WarmEspresso,
    secondary = WarmSage,
    onSecondary = Color.White,
    secondaryContainer = WarmSageSoft,
    onSecondaryContainer = WarmEspresso,
    tertiary = WarmHoney,
    background = WarmCream,
    onBackground = WarmEspresso,
    surface = WarmCard,
    onSurface = WarmEspresso,
    surfaceVariant = WarmSand,
    onSurfaceVariant = WarmMuted,
    outline = WarmLine,
    outlineVariant = WarmLine,
)

private val WarmDarkColors = darkColorScheme(
    primary = Color(0xFFE89A6B),
    onPrimary = Color(0xFF2A170C),
    secondary = Color(0xFFA9BE94),
    background = Color(0xFF241A12),
    surface = Color(0xFF2E2117),
)

val WarmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val WarmTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun WarmCareTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) WarmDarkColors else WarmLightColors,
        shapes = WarmShapes,
        typography = WarmTypography,
        content = content,
    )
}
