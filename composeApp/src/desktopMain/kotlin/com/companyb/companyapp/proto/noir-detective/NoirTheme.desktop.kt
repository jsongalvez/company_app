package com.companyb.companyapp.proto.noirdetective

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
object NoirPalette {
    val NightRain = Color(0xFF080D1A)
    val Precinct = Color(0xFF0D1528)
    val CaseFile = Color(0xFF121D36)
    val FileRaised = Color(0xFF182642)
    val BlindDark = Color(0xFF0A1122)
    val BlindLight = Color(0xFF1B2A4A)
    val Ink = Color(0xFFE8E4D8)
    val Dim = Color(0xFF9AA3B8)
    val Faint = Color(0xFF5D6880)
    val LampAmber = Color(0xFFE8A33D)
    val NeonBlue = Color(0xFF6EA8FF)
    val SirenRed = Color(0xFFE5484D)
    val EvidenceGreen = Color(0xFF46C08A)
    val ChalkLine = Color(0xFF2A3A5E)
}

private val NoirScheme =
    darkColorScheme(
        background = NoirPalette.NightRain,
        surface = NoirPalette.Precinct,
        surfaceVariant = NoirPalette.CaseFile,
        primary = NoirPalette.LampAmber,
        secondary = NoirPalette.NeonBlue,
        tertiary = NoirPalette.EvidenceGreen,
        error = NoirPalette.SirenRed,
        onBackground = NoirPalette.Ink,
        onSurface = NoirPalette.Ink,
        onSurfaceVariant = NoirPalette.Dim,
        onPrimary = NoirPalette.NightRain,
        outline = NoirPalette.ChalkLine,
        outlineVariant = NoirPalette.ChalkLine,
    )

private val SerifFamily = FontFamily.Serif
private val MonoFamily = FontFamily.Monospace

private fun headline(
    size: Int,
    weight: FontWeight,
    color: Color = NoirPalette.Ink,
): TextStyle =
    TextStyle(
        fontFamily = SerifFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 8).sp,
        letterSpacing = 0.5.sp,
        color = color,
    )

private fun dossierStyle(
    size: Int,
    weight: FontWeight,
    color: Color = NoirPalette.Ink,
): TextStyle =
    TextStyle(
        fontFamily = MonoFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        letterSpacing = 1.2.sp,
        color = color,
    )

internal val NoirType =
    Typography(
        displaySmall = headline(24, FontWeight.Bold, NoirPalette.LampAmber),
        titleLarge = headline(18, FontWeight.Bold),
        titleMedium = headline(15, FontWeight.Bold),
        titleSmall = dossierStyle(12, FontWeight.Bold, NoirPalette.Dim),
        bodyLarge = dossierStyle(13, FontWeight.Normal),
        bodyMedium = dossierStyle(12, FontWeight.Normal),
        bodySmall = dossierStyle(11, FontWeight.Normal, NoirPalette.Dim),
        labelLarge = dossierStyle(12, FontWeight.Bold),
        labelMedium = dossierStyle(11, FontWeight.Bold, NoirPalette.Dim),
        labelSmall = dossierStyle(10, FontWeight.Bold, NoirPalette.Dim),
    )

internal val NoirPadSm = 8.dp
internal val NoirPadMd = 12.dp
internal val NoirPadLg = 16.dp
internal val NoirRailWidth = 216.dp
internal val NoirDetailWidth = 348.dp

@Composable
internal fun NoirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoirScheme,
        typography = NoirType,
        content = content,
    )
}
