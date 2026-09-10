package com.companyb.companyapp.proto.questlog

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
object QuestLogPalette {
    val Night = Color(0xFF14100B)
    val Tavern = Color(0xFF1E1710)
    val TavernRaised = Color(0xFF2A2015)
    val Border = Color(0xFF4A3A24)
    val BorderBright = Color(0xFF6B5636)
    val Parchment = Color(0xFFF0E2C4)
    val Dim = Color(0xFFB8A67E)
    val Faint = Color(0xFF7A6C50)
    val Gold = Color(0xFFE8B34B)
    val GoldDeep = Color(0xFF8A6420)
    val XpGreen = Color(0xFF7BC96F)
    val HpRed = Color(0xFFE05B4B)
    val ManaBlue = Color(0xFF6FB7E8)
    val RareViolet = Color(0xFFB39DDB)
    val EpicOrange = Color(0xFFF0923D)
}

private val QuestLogScheme =
    darkColorScheme(
        background = QuestLogPalette.Night,
        surface = QuestLogPalette.Tavern,
        surfaceVariant = QuestLogPalette.TavernRaised,
        primary = QuestLogPalette.Gold,
        secondary = QuestLogPalette.XpGreen,
        tertiary = QuestLogPalette.ManaBlue,
        error = QuestLogPalette.HpRed,
        onBackground = QuestLogPalette.Parchment,
        onSurface = QuestLogPalette.Parchment,
        onSurfaceVariant = QuestLogPalette.Dim,
        onPrimary = QuestLogPalette.Night,
        outline = QuestLogPalette.Border,
        outlineVariant = QuestLogPalette.Border,
    )

private val SerifFamily = FontFamily.Serif

private fun serifStyle(
    size: Int,
    weight: FontWeight,
    color: Color = QuestLogPalette.Parchment,
): TextStyle =
    TextStyle(
        fontFamily = SerifFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
        color = color,
    )

internal val QuestLogType =
    Typography(
        displaySmall = serifStyle(24, FontWeight.Bold, QuestLogPalette.Gold),
        titleLarge = serifStyle(18, FontWeight.Bold),
        titleMedium = serifStyle(15, FontWeight.Bold),
        titleSmall = serifStyle(13, FontWeight.Bold, QuestLogPalette.Dim),
        bodyLarge = serifStyle(14, FontWeight.Normal),
        bodyMedium = serifStyle(13, FontWeight.Normal),
        bodySmall = serifStyle(12, FontWeight.Normal, QuestLogPalette.Dim),
        labelLarge = serifStyle(13, FontWeight.Bold),
        labelMedium = serifStyle(12, FontWeight.Bold, QuestLogPalette.Dim),
        labelSmall = serifStyle(11, FontWeight.Bold, QuestLogPalette.Faint),
    )

internal val QuestPadSm = 8.dp
internal val QuestPadMd = 12.dp
internal val QuestPadLg = 16.dp
internal val QuestRailWidth = 216.dp

@Composable
internal fun QuestLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QuestLogScheme,
        typography = QuestLogType,
        content = content,
    )
}
