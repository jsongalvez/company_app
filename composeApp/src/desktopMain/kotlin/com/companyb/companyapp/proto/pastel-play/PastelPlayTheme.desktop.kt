package com.companyb.companyapp.proto.pastelplay

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

val PlayMilk = Color(0xFFFFF6FA)
val PlayCream = Color(0xFFFFF3E4)
val PlayCard = Color(0xFFFFFFFF)
val PlayCotton = Color(0xFFFBE9F1)
val PlayLine = Color(0xFFF3D3E2)
val PlayInk = Color(0xFF43283C)
val PlayMuted = Color(0xFF9A7E93)
val PlayCandy = Color(0xFFF0508C)
val PlayCandyDeep = Color(0xFFC23368)
val PlayCandySoft = Color(0xFFFFD9E8)
val PlayMint = Color(0xFF1FA97C)
val PlayMintSoft = Color(0xFFC9F2E2)
val PlaySky = Color(0xFF4A9DFF)
val PlaySkySoft = Color(0xFFD6E9FF)
val PlayLilac = Color(0xFF8B7CF6)
val PlayLilacSoft = Color(0xFFE3DEFF)
val PlayLemon = Color(0xFFE0A400)
val PlayLemonSoft = Color(0xFFFFE9A8)
val PlayPeach = Color(0xFFFF9D7A)
val PlayPeachSoft = Color(0xFFFFE0D2)

private val PlayLightColors = lightColorScheme(
    primary = PlayCandy,
    onPrimary = Color.White,
    primaryContainer = PlayCandySoft,
    onPrimaryContainer = PlayInk,
    secondary = PlayMint,
    onSecondary = Color.White,
    secondaryContainer = PlayMintSoft,
    onSecondaryContainer = PlayInk,
    tertiary = PlaySky,
    onTertiary = Color.White,
    tertiaryContainer = PlaySkySoft,
    onTertiaryContainer = PlayInk,
    background = PlayMilk,
    onBackground = PlayInk,
    surface = PlayCard,
    onSurface = PlayInk,
    surfaceVariant = PlayCotton,
    onSurfaceVariant = PlayMuted,
    outline = PlayLine,
    outlineVariant = PlayLine,
)

val PlayShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val PlayTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PastelPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlayLightColors,
        shapes = PlayShapes,
        typography = PlayTypography,
        content = content,
    )
}
