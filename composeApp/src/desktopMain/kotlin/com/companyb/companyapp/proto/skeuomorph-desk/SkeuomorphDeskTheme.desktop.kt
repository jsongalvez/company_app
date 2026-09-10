package com.companyb.companyapp.proto.skeuomorphdesk

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DeskWalnut = Color(0xFF3E2A1C)
val DeskWalnutDark = Color(0xFF2C1D13)
val DeskPlank = Color(0xFF4A3423)
val DeskLeather = Color(0xFF6B2D26)
val DeskLeatherDark = Color(0xFF521F1A)
val DeskPaper = Color(0xFFF5EBD0)
val DeskPaperDark = Color(0xFFE7D7AE)
val DeskPaperEdge = Color(0xFFD9C491)
val DeskBrass = Color(0xFFC9A227)
val DeskBrassDark = Color(0xFF8F6F14)
val DeskInk = Color(0xFF2A1E14)
val DeskInkSoft = Color(0xFF5C4A36)
val DeskStamp = Color(0xFFB3271E)
val DeskStampSoft = Color(0xFFF3D3C8)
val DeskFelt = Color(0xFF1E4D3B)
val DeskFeltSoft = Color(0xFFCFE3D6)

private val DeskLightColors = lightColorScheme(
    primary = DeskBrassDark,
    onPrimary = DeskPaper,
    primaryContainer = DeskLeather,
    onPrimaryContainer = DeskPaper,
    secondary = DeskStamp,
    onSecondary = DeskPaper,
    secondaryContainer = DeskStampSoft,
    onSecondaryContainer = DeskInk,
    tertiary = DeskFelt,
    onTertiary = DeskPaper,
    tertiaryContainer = DeskFeltSoft,
    onTertiaryContainer = DeskInk,
    background = DeskWalnut,
    onBackground = DeskPaper,
    surface = DeskPaper,
    onSurface = DeskInk,
    surfaceVariant = DeskPaperDark,
    onSurfaceVariant = DeskInkSoft,
    outline = DeskBrass,
    outlineVariant = DeskPaperEdge,
)

val DeskShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

private val DeskTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 56.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp),
)

@Composable
fun SkeuomorphDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeskLightColors,
        shapes = DeskShapes,
        typography = DeskTypography,
        content = content,
    )
}
