package com.companyb.companyapp.proto.branchcompare

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

val CompareInk = Color(0xFF141B2E)
val CompareInkDeep = Color(0xFF0D1322)
val ComparePaper = Color(0xFFF6F2E9)
val ComparePaperEdge = Color(0xFFE4DCC8)
val CompareLine = Color(0xFF2A3350)
val CompareOnInk = Color(0xFFF4F1E6)
val CompareMuted = Color(0xFF9AA3BD)
val CompareBody = Color(0xFF2B3040)
val CompareSoft = Color(0xFF6E7488)

val SunriseAmber = Color(0xFFE09A1F)
val HarborTeal = Color(0xFF14908A)
val LingapPlum = Color(0xFF8A4E9B)

val DayOpen = Color(0xFF1F8A5B)
val DayPast = Color(0xFF5B6472)
val DayRemitted = Color(0xFFB97E0C)

val StatusPending = Color(0xFFB97E0C)
val StatusCompleted = Color(0xFF1F8A5B)
val StatusNoShow = Color(0xFF7C5CBF)
val StatusCancelled = Color(0xFFC0392B)

fun branchAccent(branchId: String): Color = when (branchId) {
    "b1" -> SunriseAmber
    "b2" -> HarborTeal
    else -> LingapPlum
}

private val BranchCompareColors = lightColorScheme(
    primary = SunriseAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDCB),
    onPrimaryContainer = Color(0xFF5B3D05),
    secondary = HarborTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2EFF0),
    onSecondaryContainer = Color(0xFF073E3C),
    tertiary = LingapPlum,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAD9F2),
    onTertiaryContainer = Color(0xFF3E1C49),
    background = CompareInk,
    onBackground = CompareOnInk,
    surface = ComparePaper,
    onSurface = CompareBody,
    surfaceVariant = ComparePaperEdge,
    onSurfaceVariant = CompareSoft,
    error = StatusCancelled,
    onError = Color.White,
)

private val BranchCompareType = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
)

@Composable
fun BranchCompareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BranchCompareColors,
        typography = BranchCompareType,
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
