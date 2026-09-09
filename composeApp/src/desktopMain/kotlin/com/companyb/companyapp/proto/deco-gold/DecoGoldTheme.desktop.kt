package com.companyb.companyapp.proto.decogold

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

// #794 — deco-gold prototype theme: black-gold art deco. Obsidian canvas,
// hairline gold geometry, cream serif headlines, symmetrical panels. Sharp
// 2dp corners everywhere: deco is cut stone and brass, never bubbly.

val GoldObsidian = Color(0xFF0B0A07)
val GoldPanel = Color(0xFF14110B)
val GoldPanelHigh = Color(0xFF1D1810)
val GoldLine = Color(0xFF3A2F18)
val GoldLineBright = Color(0xFF6B5626)
val GoldPrimary = Color(0xFFC9A227)
val GoldBright = Color(0xFFE8C86A)
val GoldSoft = Color(0xFF2A2210)
val GoldCream = Color(0xFFF3EAD3)
val GoldMuted = Color(0xFF9A8A63)
val GoldFaint = Color(0xFF5C5238)
val GoldEmerald = Color(0xFF3FA97C)
val GoldRuby = Color(0xFFD9534F)
val GoldSky = Color(0xFF7FA8C9)

val GoldSerif = FontFamily.Serif

private val GoldDarkColors =
    darkColorScheme(
        primary = GoldPrimary,
        onPrimary = GoldObsidian,
        primaryContainer = GoldSoft,
        onPrimaryContainer = GoldBright,
        secondary = GoldBright,
        onSecondary = GoldObsidian,
        secondaryContainer = GoldPanelHigh,
        onSecondaryContainer = GoldCream,
        tertiary = GoldEmerald,
        onTertiary = GoldObsidian,
        tertiaryContainer = GoldPanelHigh,
        onTertiaryContainer = GoldCream,
        background = GoldObsidian,
        onBackground = GoldCream,
        surface = GoldPanel,
        onSurface = GoldCream,
        surfaceVariant = GoldPanelHigh,
        onSurfaceVariant = GoldMuted,
        outline = GoldLine,
        outlineVariant = GoldLine,
        error = GoldRuby,
        onError = GoldObsidian,
    )

val GoldShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(4.dp),
        extraLarge = RoundedCornerShape(6.dp),
    )

private val GoldTypography =
    Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = GoldSerif),
        headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = GoldSerif),
        headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = GoldSerif),
        titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = GoldSerif),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    )

@Composable
fun DecoGoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GoldDarkColors,
        shapes = GoldShapes,
        typography = GoldTypography,
        content = content,
    )
}
