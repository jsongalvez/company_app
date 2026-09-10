package com.companyb.companyapp.proto.lunchrush

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("MagicNumber")
object LunchRushPalette {
    val Paper = Color(0xFFFFF6E8)
    val Card = Color(0xFFFFFDF7)
    val CardHot = Color(0xFFFFEED3)
    val Border = Color(0xFFE4CFA8)
    val BorderDark = Color(0xFFC9A86F)
    val Ink = Color(0xFF2B2118)
    val Dim = Color(0xFF7A6A54)
    val Faint = Color(0xFFAB9878)
    val Char = Color(0xFFC62F2F)
    val CharDeep = Color(0xFF8F1D1D)
    val Turmeric = Color(0xFFD97E06)
    val Leaf = Color(0xFF2E7D46)
    val Wok = Color(0xFF33302B)
    val Steam = Color(0xFF4E8CA8)
    val TicketEdge = Color(0xFFB4552D)
}

private val LunchRushScheme =
    lightColorScheme(
        background = LunchRushPalette.Paper,
        surface = LunchRushPalette.Card,
        surfaceVariant = LunchRushPalette.CardHot,
        primary = LunchRushPalette.Char,
        secondary = LunchRushPalette.Turmeric,
        tertiary = LunchRushPalette.Leaf,
        error = LunchRushPalette.CharDeep,
        onBackground = LunchRushPalette.Ink,
        onSurface = LunchRushPalette.Ink,
        onSurfaceVariant = LunchRushPalette.Dim,
        onPrimary = Color.White,
        outline = LunchRushPalette.Border,
        outlineVariant = LunchRushPalette.Border,
    )

private val RoundFamily = FontFamily.SansSerif

private fun rushStyle(
    size: Int,
    weight: FontWeight,
    color: Color = LunchRushPalette.Ink,
): TextStyle =
    TextStyle(
        fontFamily = RoundFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        color = color,
    )

internal val LunchRushType =
    Typography(
        displaySmall = rushStyle(24, FontWeight.Black),
        titleLarge = rushStyle(18, FontWeight.Bold),
        titleMedium = rushStyle(15, FontWeight.Bold),
        titleSmall = rushStyle(12, FontWeight.Bold, LunchRushPalette.Dim),
        bodyLarge = rushStyle(14, FontWeight.Normal),
        bodyMedium = rushStyle(13, FontWeight.Normal),
        bodySmall = rushStyle(12, FontWeight.Normal, LunchRushPalette.Dim),
        labelLarge = rushStyle(13, FontWeight.Bold),
        labelMedium = rushStyle(12, FontWeight.Bold, LunchRushPalette.Dim),
        labelSmall = rushStyle(11, FontWeight.Bold, LunchRushPalette.Faint),
    )

internal val RushPadSm = 8.dp
internal val RushPadMd = 12.dp
internal val RushPadLg = 16.dp
internal val RushRailWidth = 216.dp
internal val RushDetailWidth = 348.dp

@Composable
internal fun LunchRushTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LunchRushScheme,
        typography = LunchRushType,
        content = content,
    )
}
