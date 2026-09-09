package com.companyb.companyapp.proto.carddeck

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

val FeltGreen = Color(0xFF1E3A3A)
val FeltDeep = Color(0xFF152929)
val TableRail = Color(0xFFD9A441)
val PaperCard = Color(0xFFFFFDF4)
val PaperEdge = Color(0xFFF1E7CE)
val InkText = Color(0xFF23242B)
val InkSoft = Color(0xFF6B6E7B)
val InkLine = Color(0xFF23242B)
val SuitCoral = Color(0xFFE4572E)
val SuitSky = Color(0xFF2E86AB)
val SuitMint = Color(0xFF1F8A5B)
val SuitLilac = Color(0xFF7C5CBF)
val SuitAmber = Color(0xFFB97E0C)
val SuitSlate = Color(0xFF55606E)
val FeltOnDark = Color(0xFFF5EFE0)
val FeltMuted = Color(0xFFB9C4BC)

private val CardDeckColors = lightColorScheme(
    primary = SuitCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D6),
    onPrimaryContainer = Color(0xFF5B2312),
    secondary = SuitSky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9ECF7),
    onSecondaryContainer = Color(0xFF123C52),
    tertiary = SuitMint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F0E2),
    onTertiaryContainer = Color(0xFF0E4A2E),
    background = FeltGreen,
    onBackground = FeltOnDark,
    surface = PaperCard,
    onSurface = InkText,
    surfaceVariant = PaperEdge,
    onSurfaceVariant = InkSoft,
    outline = InkLine,
)

private val CardDeckType = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
)

private val CardDeckShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

@Composable
fun CardDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CardDeckColors,
        typography = CardDeckType,
        shapes = CardDeckShapes,
        content = content,
    )
}
