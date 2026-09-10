package com.companyb.companyapp.proto.chalkboard

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

val ChalkSlate = Color(0xFF232D38)
val ChalkSlateDeep = Color(0xFF1A222C)
val ChalkBoard = Color(0xFF2B3644)
val ChalkTray = Color(0xFF314052)
val ChalkLine = Color(0xFF43566B)
val ChalkSmudge = Color(0xFF5B7186)
val ChalkWhite = Color(0xFFF4F1E8)
val ChalkFaint = Color(0xFFB9C4D0)
val ChalkDim = Color(0xFF8A99AB)
val ChalkYellow = Color(0xFFF2D06B)
val ChalkYellowSoft = Color(0xFF4A4132)
val ChalkPink = Color(0xFFF092A8)
val ChalkPinkSoft = Color(0xFF4D3540)
val ChalkMint = Color(0xFF8FD8B2)
val ChalkMintSoft = Color(0xFF2E4439)
val ChalkBlue = Color(0xFF8FB8F0)
val ChalkBlueSoft = Color(0xFF31435C)
val ChalkOrange = Color(0xFFF0A868)
val ChalkOrangeSoft = Color(0xFF4E3E2E)

private val ChalkColors = darkColorScheme(
    primary = ChalkYellow,
    onPrimary = ChalkSlateDeep,
    primaryContainer = ChalkYellowSoft,
    onPrimaryContainer = ChalkWhite,
    secondary = ChalkMint,
    onSecondary = ChalkSlateDeep,
    secondaryContainer = ChalkMintSoft,
    onSecondaryContainer = ChalkWhite,
    tertiary = ChalkBlue,
    onTertiary = ChalkSlateDeep,
    tertiaryContainer = ChalkBlueSoft,
    onTertiaryContainer = ChalkWhite,
    background = ChalkSlateDeep,
    onBackground = ChalkWhite,
    surface = ChalkBoard,
    onSurface = ChalkWhite,
    surfaceVariant = ChalkTray,
    onSurfaceVariant = ChalkFaint,
    outline = ChalkLine,
    outlineVariant = ChalkSmudge,
    error = ChalkPink,
    onError = ChalkSlateDeep,
)

val ChalkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val ChalkTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ChalkboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChalkColors,
        shapes = ChalkShapes,
        typography = ChalkTypography,
        content = content,
    )
}
