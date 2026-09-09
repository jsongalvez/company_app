package com.companyb.companyapp.proto.franchisemap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

val FmParchment = Color(0xFFF4ECDA)
val FmParchmentDeep = Color(0xFFE7D9BB)
val FmInk = Color(0xFF22301F)
val FmRail = Color(0xFF16211A)
val FmRailSoft = Color(0xFF243428)
val FmBrass = Color(0xFFA87B1F)
val FmGold = Color(0xFFC9A227)
val FmOpen = Color(0xFF2E7D46)
val FmOpenSoft = Color(0xFFDDEBD9)
val FmPast = Color(0xFF64748B)
val FmPastSoft = Color(0xFFE2E8F0)
val FmRemitted = Color(0xFF9A6B0F)
val FmRemittedSoft = Color(0xFFF3E3B3)
val FmCard = Color(0xFFFBF7EA)
val FmRidge = Color(0xFFDCE5CF)
val FmBay = Color(0xFFD2E2E4)
val FmSouth = Color(0xFFEAD9B8)
val FmRoad = Color(0xFF8A7B52)

val FmDisplay = FontFamily.Serif
val FmFigures = FontFamily.Monospace

private val FmTypography = Typography(
    displaySmall = TextStyle(fontFamily = FmDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontFamily = FmDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FmDisplay, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FmDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

@Composable
fun FranchiseMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FmInk,
            onPrimary = FmParchment,
            secondary = FmBrass,
            tertiary = FmOpen,
            background = FmParchment,
            surface = FmCard,
            onBackground = FmInk,
            onSurface = FmInk,
        ),
        typography = FmTypography,
        content = content,
    )
}

fun FmDayStatus.dayColor(): Color = when (this) {
    FmDayStatus.OPEN -> FmOpen
    FmDayStatus.PAST -> FmPast
    FmDayStatus.REMITTED -> FmRemitted
}

fun FmDayStatus.daySoft(): Color = when (this) {
    FmDayStatus.OPEN -> FmOpenSoft
    FmDayStatus.PAST -> FmPastSoft
    FmDayStatus.REMITTED -> FmRemittedSoft
}

fun FmSessionStatus.chipColor(): Color = when (this) {
    FmSessionStatus.PENDING -> FmBrass
    FmSessionStatus.COMPLETED -> FmOpen
    FmSessionStatus.NO_SHOW -> FmPast
    FmSessionStatus.CANCELLED -> Color(0xFF9B2C2C)
}
