package com.companyb.companyapp.proto.mochapop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MochaNight = Color(0xFF1E100B)
val MochaPanel = Color(0xFF2A1A12)
val MochaCardSolid = Color(0xFF332016)
val MochaCream = Color(0xFFF7E8D9)
val MochaMuted = Color(0xFFC9A98F)
val MochaCandy = Color(0xFFF28BB5)
val MochaCandyDeep = Color(0xFFC14E82)
val MochaCandySoft = Color(0xFF5A2A3E)
val MochaCaramel = Color(0xFFE8A33D)
val MochaCaramelSoft = Color(0xFF5A3E1A)
val MochaMint = Color(0xFF7BD8A6)
val MochaMintSoft = Color(0xFF1E4A34)
val MochaBerry = Color(0xFFE5484D)
val MochaBerrySoft = Color(0xFF5A2226)
val MochaPlum = Color(0xFFB388EB)
val MochaPlumSoft = Color(0xFF3E2A5A)

private val MochaDarkColors = darkColorScheme(
    primary = MochaCandy,
    onPrimary = Color(0xFF2A0E1B),
    primaryContainer = MochaCandySoft,
    onPrimaryContainer = MochaCream,
    secondary = MochaCaramel,
    onSecondary = Color(0xFF2A1A08),
    secondaryContainer = MochaCaramelSoft,
    onSecondaryContainer = MochaCream,
    tertiary = MochaPlum,
    onTertiary = Color(0xFF1E1030),
    tertiaryContainer = MochaPlumSoft,
    onTertiaryContainer = MochaCream,
    background = MochaNight,
    onBackground = MochaCream,
    surface = MochaCardSolid,
    onSurface = MochaCream,
    surfaceVariant = MochaPanel,
    onSurfaceVariant = MochaMuted,
    outline = Color(0xFF6B4A38),
    outlineVariant = Color(0xFF4A3226),
    error = MochaBerry,
    onError = Color.White,
    errorContainer = MochaBerrySoft,
    onErrorContainer = Color(0xFFFFD2D2),
)

val MochaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val MochaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun MochaPopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MochaDarkColors,
        shapes = MochaShapes,
        typography = MochaTypography,
        content = content,
    )
}
