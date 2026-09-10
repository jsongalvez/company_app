package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("MagicNumber")
object DaybreakPalette {
    val Paper = Color(0xFFFFF6E8)
    val PaperDeep = Color(0xFFF7E8CF)
    val Card = Color(0xFFFFFDF6)
    val Ink = Color(0xFF332619)
    val SoftInk = Color(0xFF6B5942)
    val Faint = Color(0xFF9A8668)
    val Line = Color(0xFFE4D3B4)
    val LineStrong = Color(0xFFC9AE7F)
    val Sunrise = Color(0xFFD96C2B)
    val SunriseDeep = Color(0xFFA84E1B)
    val Rose = Color(0xFFB94A48)
    val Sage = Color(0xFF5F8455)
    val Sky = Color(0xFF3E7FA6)
    val Violet = Color(0xFF7A5FBF)
    val Gold = Color(0xFF9A7418)
    val Night = Color(0xFF2B3A55)
}

private val DaybreakScheme =
    lightColorScheme(
        background = DaybreakPalette.Paper,
        surface = DaybreakPalette.Card,
        surfaceVariant = DaybreakPalette.PaperDeep,
        primary = DaybreakPalette.Sunrise,
        onPrimary = Color.White,
        secondary = DaybreakPalette.Sage,
        tertiary = DaybreakPalette.Sky,
        error = DaybreakPalette.Rose,
        onBackground = DaybreakPalette.Ink,
        onSurface = DaybreakPalette.Ink,
        onSurfaceVariant = DaybreakPalette.SoftInk,
        outline = DaybreakPalette.Line,
        outlineVariant = DaybreakPalette.Line,
    )

private fun dawnStyle(
    size: Int,
    weight: FontWeight,
    color: Color = DaybreakPalette.Ink,
): TextStyle =
    TextStyle(
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        color = color,
    )

internal val DaybreakType =
    Typography(
        displaySmall = dawnStyle(24, FontWeight.ExtraBold),
        titleLarge = dawnStyle(18, FontWeight.Bold),
        titleMedium = dawnStyle(15, FontWeight.Bold),
        titleSmall = dawnStyle(12, FontWeight.Bold, DaybreakPalette.SoftInk),
        bodyLarge = dawnStyle(14, FontWeight.Normal),
        bodyMedium = dawnStyle(13, FontWeight.Normal),
        bodySmall = dawnStyle(12, FontWeight.Normal, DaybreakPalette.SoftInk),
        labelLarge = dawnStyle(13, FontWeight.Bold),
        labelMedium = dawnStyle(11, FontWeight.Bold, DaybreakPalette.SoftInk),
        labelSmall = dawnStyle(10, FontWeight.Bold, DaybreakPalette.Faint),
    )

internal val DawnPadSm = 8.dp
internal val DawnPadMd = 12.dp
internal val DawnPadLg = 16.dp
internal val DawnRailWidth = 212.dp

@Composable
internal fun DaybreakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DaybreakScheme,
        typography = DaybreakType,
        content = content,
    )
}
