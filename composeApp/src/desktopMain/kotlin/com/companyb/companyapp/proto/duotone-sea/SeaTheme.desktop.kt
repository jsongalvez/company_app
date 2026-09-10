package com.companyb.companyapp.proto.duotonesea

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("MagicNumber")
object SeaPalette {
    val Abyss = Color(0xFF04222B)
    val DeepSea = Color(0xFF07333E)
    val Lagoon = Color(0xFF0C4350)
    val LagoonRaised = Color(0xFF105263)
    val ReefLine = Color(0xFF1B5F71)
    val Foam = Color(0xFFEFF8F6)
    val Mist = Color(0xFF93B9B6)
    val Faint = Color(0xFF5E8380)
    val Coral = Color(0xFFFF6F61)
    val CoralDeep = Color(0xFFD95548)
    val SeaGlass = Color(0xFF45C4B5)
    val Kelp = Color(0xFF4CC38A)
    val SunBuoy = Color(0xFFFFC857)
    val Siren = Color(0xFFE5484D)
}

private val SeaScheme =
    darkColorScheme(
        background = SeaPalette.Abyss,
        surface = SeaPalette.DeepSea,
        surfaceVariant = SeaPalette.Lagoon,
        primary = SeaPalette.Coral,
        secondary = SeaPalette.SeaGlass,
        tertiary = SeaPalette.Kelp,
        error = SeaPalette.Siren,
        onBackground = SeaPalette.Foam,
        onSurface = SeaPalette.Foam,
        onSurfaceVariant = SeaPalette.Mist,
        onPrimary = SeaPalette.Abyss,
        onSecondary = SeaPalette.Abyss,
        outline = SeaPalette.ReefLine,
        outlineVariant = SeaPalette.ReefLine,
    )

private val RoundFamily = FontFamily.SansSerif
private val TideFamily = FontFamily.Monospace

private fun headline(
    size: Int,
    weight: FontWeight,
    color: Color = SeaPalette.Foam,
): TextStyle =
    TextStyle(
        fontFamily = RoundFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 8).sp,
        letterSpacing = 0.4.sp,
        color = color,
    )

private fun tideStyle(
    size: Int,
    weight: FontWeight,
    color: Color = SeaPalette.Foam,
): TextStyle =
    TextStyle(
        fontFamily = TideFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        letterSpacing = 1.1.sp,
        color = color,
    )

internal val SeaType =
    Typography(
        displaySmall = headline(24, FontWeight.ExtraBold, SeaPalette.Coral),
        titleLarge = headline(18, FontWeight.Bold),
        titleMedium = headline(15, FontWeight.Bold),
        titleSmall = tideStyle(12, FontWeight.Bold, SeaPalette.Mist),
        bodyLarge = tideStyle(13, FontWeight.Normal),
        bodyMedium = tideStyle(12, FontWeight.Normal),
        bodySmall = tideStyle(11, FontWeight.Normal, SeaPalette.Mist),
        labelLarge = tideStyle(12, FontWeight.Bold),
        labelMedium = tideStyle(11, FontWeight.Bold, SeaPalette.Mist),
        labelSmall = tideStyle(10, FontWeight.Bold, SeaPalette.Mist),
    )

internal val SeaPadSm = 8.dp
internal val SeaPadMd = 12.dp
internal val SeaPadLg = 16.dp
internal val SeaRailWidth = 216.dp
internal val SeaDetailWidth = 348.dp

@Composable
internal fun SeaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SeaScheme,
        typography = SeaType,
        content = content,
    )
}
