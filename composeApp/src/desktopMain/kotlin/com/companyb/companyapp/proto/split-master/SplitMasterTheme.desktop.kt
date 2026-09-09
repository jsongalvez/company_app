package com.companyb.companyapp.proto.splitmaster

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #765 — split-master prototype theme: drafting-split ledger. The master column is
// deep ink; the detail pane is warm paper. A signal-amber spine marks selection.
// Deliberately distinct from Linear and all sibling prototypes.

object SmColors {
    val Ink = Color(0xFF14181F)
    val InkSoft = Color(0xFF1E242E)
    val InkLine = Color(0xFF2C3440)
    val InkText = Color(0xFFF2EDE2)
    val InkFaded = Color(0xFF9AA3B2)
    val Paper = Color(0xFFFAF6EE)
    val Card = Color(0xFFFFFDF8)
    val CardEdge = Color(0xFFE3D9C4)
    val DetailInk = Color(0xFF23201A)
    val DetailFaded = Color(0xFF7A7264)
    val Amber = Color(0xFFF5A524)
    val AmberDim = Color(0xFF8A5E14)
    val Teal = Color(0xFF1F7A6D)
    val Oxblood = Color(0xFF9C3324)
    val Slate = Color(0xFF6B7280)
    val Gold = Color(0xFF9A6B12)
}

private val SmLightScheme = lightColorScheme(
    primary = SmColors.AmberDim,
    onPrimary = Color.White,
    background = SmColors.Paper,
    onBackground = SmColors.DetailInk,
    surface = SmColors.Card,
    onSurface = SmColors.DetailInk,
    surfaceVariant = SmColors.CardEdge,
    onSurfaceVariant = SmColors.DetailFaded,
)

private val SmDarkScheme = darkColorScheme(
    primary = SmColors.Amber,
    onPrimary = SmColors.Ink,
    background = SmColors.Ink,
    onBackground = SmColors.InkText,
    surface = SmColors.InkSoft,
    onSurface = SmColors.InkText,
    surfaceVariant = SmColors.InkLine,
    onSurfaceVariant = SmColors.InkFaded,
)

private val SmType = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private val SmShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

/** Master column (dark ink) wrapper. */
@Composable
fun SmMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SmDarkScheme, typography = SmType, shapes = SmShapes, content = content)
}

/** Detail pane (warm paper) wrapper. */
@Composable
fun SmDetailTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SmLightScheme, typography = SmType, shapes = SmShapes, content = content)
}
