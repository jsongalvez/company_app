package com.companyb.companyapp.proto.tradingdesk

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

val PitBlack = Color(0xFF060907)
val PitPanel = Color(0xFF0C120D)
val PitPanel2 = Color(0xFF111913)
val PitLine = Color(0xFF243024)
val TapeGreen = Color(0xFF00E065)
val TapeGreenDim = Color(0xFF0A7A3E)
val TapeAmber = Color(0xFFFFB000)
val TapeRed = Color(0xFFFF5252)
val TapeCyan = Color(0xFF41C7FF)
val TapePaper = Color(0xFFD9E8DA)
val TapeFaint = Color(0xFF7D9181)
val BidGreen = Color(0xFF0E2E1A)
val AskRed = Color(0xFF331010)
val HoldAmber = Color(0xFF2E230A)
val FlatGrey = Color(0xFF1B221C)

private val TradingDeskColors = darkColorScheme(
    primary = TapeGreen,
    onPrimary = PitBlack,
    primaryContainer = BidGreen,
    onPrimaryContainer = TapePaper,
    secondary = TapeAmber,
    onSecondary = PitBlack,
    secondaryContainer = HoldAmber,
    onSecondaryContainer = TapePaper,
    tertiary = TapeCyan,
    onTertiary = PitBlack,
    tertiaryContainer = Color(0xFF0C2632),
    onTertiaryContainer = TapePaper,
    error = TapeRed,
    onError = PitBlack,
    errorContainer = AskRed,
    onErrorContainer = TapePaper,
    background = PitBlack,
    onBackground = TapePaper,
    surface = PitPanel,
    onSurface = TapePaper,
    surfaceVariant = PitPanel2,
    onSurfaceVariant = TapeFaint,
    outline = PitLine,
)

private val Terminal = FontFamily.Monospace

private val TradingDeskType = Typography(
    displaySmall = TextStyle(fontFamily = Terminal, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Terminal, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Terminal, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Terminal, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = Terminal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = Terminal, fontSize = 12.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontFamily = Terminal, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = Terminal, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = Terminal, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = Terminal, fontSize = 10.sp, lineHeight = 13.sp),
)

private val TradingDeskShapes = Shapes(
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
)

@Composable
fun TradingDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TradingDeskColors,
        typography = TradingDeskType,
        shapes = TradingDeskShapes,
        content = content,
    )
}
