package com.companyb.companyapp.proto.belldesk

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

val WalnutDeep = Color(0xFF241A13)
val WalnutCounter = Color(0xFF33261B)
val WalnutPanel = Color(0xFF453424)
val MarbleCream = Color(0xFFFFF8EC)
val MarbleCard = Color(0xFFFFFDF6)
val MarbleEdge = Color(0xFFE7D9BE)
val InkLobby = Color(0xFF2E2118)
val InkSoftLobby = Color(0xFF8A7761)
val Brass = Color(0xFFC9972B)
val BrassDeep = Color(0xFF9A7215)
val BrassWash = Color(0xFFF7E8C4)
val BellRed = Color(0xFFB3261E)
val BellGreen = Color(0xFF2E7D46)
val BellAmber = Color(0xFF9A6B00)
val BellLavender = Color(0xFF6B5B95)
val SlipBellPending = Color(0xFFFFE08A)
val VoidLobby = Color(0xFFEFE6D2)

private val BellDeskColors = lightColorScheme(
    primary = BrassDeep,
    onPrimary = Color.White,
    primaryContainer = BrassWash,
    onPrimaryContainer = InkLobby,
    secondary = WalnutPanel,
    onSecondary = Color.White,
    secondaryContainer = MarbleEdge,
    onSecondaryContainer = InkLobby,
    tertiary = BellGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEACD),
    onTertiaryContainer = InkLobby,
    background = WalnutDeep,
    onBackground = MarbleCream,
    surface = MarbleCream,
    onSurface = InkLobby,
    surfaceVariant = MarbleEdge,
    onSurfaceVariant = InkSoftLobby,
    outline = InkSoftLobby,
)

private val BellSerif = FontFamily.Serif
private val BellMono = FontFamily.Monospace

private val BellDeskType = Typography(
    displaySmall = TextStyle(fontFamily = BellSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = BellSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = BellMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BellMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = BellMono, fontSize = 11.sp, lineHeight = 15.sp),
)

private val BellDeskShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun BellDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BellDeskColors,
        typography = BellDeskType,
        shapes = BellDeskShapes,
        content = content,
    )
}
