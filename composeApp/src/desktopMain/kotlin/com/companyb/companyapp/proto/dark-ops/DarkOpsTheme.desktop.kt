package com.companyb.companyapp.proto.darkops

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
object DarkOpsPalette {
    val Void = Color(0xFF0A0C10)
    val Panel = Color(0xFF11141A)
    val PanelRaised = Color(0xFF161B24)
    val Border = Color(0xFF262D3A)
    val BorderBright = Color(0xFF39424F)
    val Ink = Color(0xFFD8DFE9)
    val Dim = Color(0xFF8E97A6)
    val Faint = Color(0xFF5B6472)
    val Phosphor = Color(0xFF3DDC84)
    val PhosphorDim = Color(0xFF1D5C3B)
    val Amber = Color(0xFFF0B429)
    val Red = Color(0xFFF0524D)
    val Cyan = Color(0xFF4FD1E8)
    val Violet = Color(0xFF9A8CFF)
}

private val DarkOpsScheme =
    darkColorScheme(
        background = DarkOpsPalette.Void,
        surface = DarkOpsPalette.Panel,
        surfaceVariant = DarkOpsPalette.PanelRaised,
        primary = DarkOpsPalette.Phosphor,
        secondary = DarkOpsPalette.Cyan,
        tertiary = DarkOpsPalette.Violet,
        error = DarkOpsPalette.Red,
        onBackground = DarkOpsPalette.Ink,
        onSurface = DarkOpsPalette.Ink,
        onSurfaceVariant = DarkOpsPalette.Dim,
        onPrimary = DarkOpsPalette.Void,
        outline = DarkOpsPalette.Border,
        outlineVariant = DarkOpsPalette.Border,
    )

private val MonoFamily = FontFamily.Monospace

private fun monoStyle(
    size: Int,
    weight: FontWeight,
    color: Color = DarkOpsPalette.Ink,
): TextStyle =
    TextStyle(
        fontFamily = MonoFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        color = color,
    )

internal val DarkOpsType =
    Typography(
        displaySmall = monoStyle(22, FontWeight.Bold),
        titleLarge = monoStyle(17, FontWeight.Bold),
        titleMedium = monoStyle(14, FontWeight.Bold),
        titleSmall = monoStyle(12, FontWeight.Bold, DarkOpsPalette.Dim),
        bodyLarge = monoStyle(13, FontWeight.Normal),
        bodyMedium = monoStyle(12, FontWeight.Normal),
        bodySmall = monoStyle(11, FontWeight.Normal, DarkOpsPalette.Dim),
        labelLarge = monoStyle(12, FontWeight.Bold),
        labelMedium = monoStyle(11, FontWeight.Bold, DarkOpsPalette.Dim),
        labelSmall = monoStyle(10, FontWeight.Bold, DarkOpsPalette.Faint),
    )

internal val OpsPadSm = 8.dp
internal val OpsPadMd = 12.dp
internal val OpsPadLg = 16.dp
internal val OpsRailWidth = 208.dp
internal val OpsDetailWidth = 340.dp

@Composable
internal fun DarkOpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkOpsScheme,
        typography = DarkOpsType,
        content = content,
    )
}
