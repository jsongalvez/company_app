package com.companyb.companyapp.proto.neurosoft

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

val SoftBg = Color(0xFFE6E9F0)
val SoftRaised = Color(0xFFEBEEF4)
val SoftPressed = Color(0xFFDDE2EB)
val SoftCard = Color(0xFFEBEEF4)
val SoftLine = Color(0xFFD1D8E4)
val SoftShadowDark = Color(0xFFB9C2D4)
val SoftShadowLight = Color(0xFFFFFFFF)
val SoftInk = Color(0xFF4A5568)
val SoftMuted = Color(0xFF8A94A8)
val SoftPrimary = Color(0xFF6E8FB2)
val SoftPrimaryDeep = Color(0xFF4E6A8A)
val SoftPrimarySoft = Color(0xFFD3E0F0)
val SoftMint = Color(0xFF7FB69E)
val SoftMintDeep = Color(0xFF4E8A72)
val SoftMintSoft = Color(0xFFD2E8DD)
val SoftPeach = Color(0xFFD9A58B)
val SoftPeachDeep = Color(0xFFA9735A)
val SoftPeachSoft = Color(0xFFF3DDD1)
val SoftLilac = Color(0xFF9A93C9)
val SoftLilacDeep = Color(0xFF6B6499)
val SoftLilacSoft = Color(0xFFDDD9F0)
val SoftButter = Color(0xFFC9A86A)
val SoftButterSoft = Color(0xFFF0E4C3)
val SoftRose = Color(0xFFB87E8F)
val SoftRoseSoft = Color(0xFFEED3DB)
val SoftSky = Color(0xFFA8C3E0)
val SoftSkySoft = Color(0xFFD9E6F5)

private val SoftLightColors = lightColorScheme(
    primary = SoftPrimary,
    onPrimary = Color.White,
    primaryContainer = SoftPrimarySoft,
    onPrimaryContainer = SoftInk,
    secondary = SoftMint,
    onSecondary = Color.White,
    secondaryContainer = SoftMintSoft,
    onSecondaryContainer = SoftInk,
    tertiary = SoftLilac,
    onTertiary = Color.White,
    tertiaryContainer = SoftLilacSoft,
    onTertiaryContainer = SoftInk,
    background = SoftBg,
    onBackground = SoftInk,
    surface = SoftRaised,
    onSurface = SoftInk,
    surfaceVariant = SoftPressed,
    onSurfaceVariant = SoftMuted,
    outline = SoftLine,
    outlineVariant = SoftLine,
)

val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val SoftTypography = Typography(
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun NeuroSoftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoftLightColors,
        shapes = SoftShapes,
        typography = SoftTypography,
        content = content,
    )
}
