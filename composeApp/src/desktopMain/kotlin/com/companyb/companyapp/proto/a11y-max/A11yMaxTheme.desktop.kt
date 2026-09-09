package com.companyb.companyapp.proto.a11ymax

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

val A11yBlack = Color(0xFF000000)
val A11yPanel = Color(0xFF0D0D0D)
val A11yPaper = Color(0xFFFFFFFF)
val A11yPaperEdge = Color(0xFFE8E8E8)
val A11yInk = Color(0xFF000000)
val A11yInkSoft = Color(0xFF1A1A1A)
val A11yOnDark = Color(0xFFFFFFFF)
val A11yDim = Color(0xFFEDEDED)
val A11yFocus = Color(0xFFFFD60A)
val A11yAction = Color(0xFFFFD60A)
val A11yOnAction = Color(0xFF000000)
val A11yGood = Color(0xFF00E676)
val A11yBad = Color(0xFFFF5252)
val A11yWarn = Color(0xFFFFD60A)
val A11yInfo = Color(0xFF82B1FF)
val A11yOutline = Color(0xFFFFFFFF)

private val A11yMaxColors = darkColorScheme(
    primary = A11yAction,
    onPrimary = A11yOnAction,
    primaryContainer = A11yAction,
    onPrimaryContainer = A11yOnAction,
    secondary = A11yOnDark,
    onSecondary = A11yBlack,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = A11yOnDark,
    tertiary = A11yInfo,
    onTertiary = A11yBlack,
    tertiaryContainer = Color(0xFF1C2B45),
    onTertiaryContainer = A11yOnDark,
    background = A11yBlack,
    onBackground = A11yOnDark,
    surface = A11yPanel,
    onSurface = A11yOnDark,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = A11yDim,
    error = A11yBad,
    onError = A11yBlack,
    errorContainer = A11yBad,
    onErrorContainer = A11yBlack,
    outline = A11yOutline,
    outlineVariant = Color(0xFF9A9A9A),
)

enum class A11yTextScale(val factor: Float, val label: String) {
    STANDARD(1.0f, "Standard text"),
    LARGE(1.18f, "Large text"),
    EXTRA(1.36f, "Extra-large text"),
}

private fun scaledType(scale: Float): Typography {
    fun s(base: Int): Int = (base * scale).toInt()
    return Typography(
        displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = s(30).sp, lineHeight = s(34).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = s(23).sp, lineHeight = s(29).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = s(19).sp, lineHeight = s(25).sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = s(17).sp, lineHeight = s(23).sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = s(17).sp, lineHeight = s(24).sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = s(16).sp, lineHeight = s(23).sp),
        bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = s(14).sp, lineHeight = s(20).sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = s(16).sp, lineHeight = s(22).sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = s(14).sp, lineHeight = s(20).sp),
    )
}

private val A11yMaxShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
)

@Composable
fun A11yMaxTheme(scale: A11yTextScale = A11yTextScale.STANDARD, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = A11yMaxColors,
        typography = scaledType(scale.factor),
        shapes = A11yMaxShapes,
        content = content,
    )
}
