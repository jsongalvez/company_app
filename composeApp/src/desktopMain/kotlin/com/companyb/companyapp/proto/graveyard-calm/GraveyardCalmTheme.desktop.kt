package com.companyb.companyapp.proto.graveyardcalm

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
object GraveyardCalmPalette {
    val Night = Color(0xFF0C1016)
    val Desk = Color(0xFF131A24)
    val Card = Color(0xFF182130)
    val CardSoft = Color(0xFF1E2839)
    val Border = Color(0xFF2A3648)
    val BorderFaint = Color(0xFF212C3D)
    val Ink = Color(0xFFDCE4EF)
    val Dim = Color(0xFF93A1B5)
    val Faint = Color(0xFF5D6B80)
    val Lamp = Color(0xFFD9A441)
    val LampDeep = Color(0xFF8A6420)
    val Moon = Color(0xFF7FA6C9)
    val Sage = Color(0xFF7FB98A)
    val Ember = Color(0xFFC96A5A)
    val Violet = Color(0xFF9A8FD0)
    val Rail = Color(0xFF0A0E13)
}

private val GraveyardCalmScheme =
    darkColorScheme(
        background = GraveyardCalmPalette.Night,
        surface = GraveyardCalmPalette.Desk,
        surfaceVariant = GraveyardCalmPalette.Card,
        primary = GraveyardCalmPalette.Lamp,
        secondary = GraveyardCalmPalette.Moon,
        tertiary = GraveyardCalmPalette.Sage,
        error = GraveyardCalmPalette.Ember,
        onBackground = GraveyardCalmPalette.Ink,
        onSurface = GraveyardCalmPalette.Ink,
        onSurfaceVariant = GraveyardCalmPalette.Dim,
        onPrimary = GraveyardCalmPalette.Night,
        outline = GraveyardCalmPalette.Border,
        outlineVariant = GraveyardCalmPalette.BorderFaint,
    )

private val CalmFamily = FontFamily.SansSerif
private val MonoFamily = FontFamily.Monospace

private fun calmStyle(
    size: Int,
    weight: FontWeight,
    color: Color = GraveyardCalmPalette.Ink,
): TextStyle =
    TextStyle(
        fontFamily = CalmFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        color = color,
    )

private fun monoStyle(
    size: Int,
    weight: FontWeight,
    color: Color = GraveyardCalmPalette.Dim,
): TextStyle =
    TextStyle(
        fontFamily = MonoFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 5).sp,
        color = color,
    )

internal val GraveyardCalmType =
    Typography(
        displaySmall = calmStyle(24, FontWeight.Black),
        titleLarge = calmStyle(18, FontWeight.Bold),
        titleMedium = calmStyle(15, FontWeight.Bold),
        titleSmall = monoStyle(12, FontWeight.Bold, GraveyardCalmPalette.Faint),
        bodyLarge = calmStyle(14, FontWeight.Normal),
        bodyMedium = calmStyle(13, FontWeight.Normal),
        bodySmall = calmStyle(12, FontWeight.Normal, GraveyardCalmPalette.Dim),
        labelLarge = calmStyle(13, FontWeight.Bold),
        labelMedium = monoStyle(12, FontWeight.Bold),
        labelSmall = monoStyle(11, FontWeight.Bold, GraveyardCalmPalette.Faint),
    )

internal val CalmPadSm = 8.dp
internal val CalmPadMd = 12.dp
internal val CalmPadLg = 16.dp
internal val CalmRailWidth = 208.dp
internal val CalmDetailWidth = 360.dp

@Composable
internal fun GraveyardCalmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraveyardCalmScheme,
        typography = GraveyardCalmType,
        content = content,
    )
}
