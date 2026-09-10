package com.companyb.companyapp.proto.comicbold

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BoomPaper = Color(0xFFFFFBEF)
val BoomCard = Color(0xFFFFFFFF)
val BoomPanel = Color(0xFFFFF3C4)
val BoomInk = Color(0xFF141414)
val BoomMuted = Color(0xFF5B5B5B)
val BoomBurst = Color(0xFFFFD400)
val BoomBurstSoft = Color(0xFFFFEC9E)
val BoomPow = Color(0xFFE93030)
val BoomPowDeep = Color(0xFFB01212)
val BoomPowSoft = Color(0xFFFFD2D2)
val BoomZap = Color(0xFF1E50FF)
val BoomZapSoft = Color(0xFFD3DEFF)
val BoomKapow = Color(0xFF00A651)
val BoomKapowSoft = Color(0xFFC9F2D8)
val BoomPurple = Color(0xFF7B2FF7)
val BoomPurpleSoft = Color(0xFFE4D4FF)
val BoomOrange = Color(0xFFFF6B00)
val BoomOrangeSoft = Color(0xFFFFDFC2)

private val BoomLightColors = lightColorScheme(
    primary = BoomPow,
    onPrimary = Color.White,
    primaryContainer = BoomPowSoft,
    onPrimaryContainer = BoomInk,
    secondary = BoomZap,
    onSecondary = Color.White,
    secondaryContainer = BoomZapSoft,
    onSecondaryContainer = BoomInk,
    tertiary = BoomBurst,
    onTertiary = BoomInk,
    tertiaryContainer = BoomBurstSoft,
    onTertiaryContainer = BoomInk,
    background = BoomPaper,
    onBackground = BoomInk,
    surface = BoomCard,
    onSurface = BoomInk,
    surfaceVariant = BoomPanel,
    onSurfaceVariant = BoomMuted,
    outline = BoomInk,
    outlineVariant = BoomInk,
)

val BoomShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

private val BoomTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Black),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun ComicBoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BoomLightColors,
        shapes = BoomShapes,
        typography = BoomTypography,
        content = content,
    )
}
