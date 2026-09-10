package com.companyb.companyapp.proto.beaconstatus

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

val HarborNight = Color(0xFF0A1628)
val HarborDeep = Color(0xFF0E1E36)
val HarborPanel = Color(0xFF14294A)
val HarborCard = Color(0xFF1A3358)
val HarborEdge = Color(0xFF2C4A76)
val FogWhite = Color(0xFFEAF2FB)
val FogDim = Color(0xFF9DB2CC)
val BeaconGold = Color(0xFFFFC94D)
val BeaconGoldDeep = Color(0xFFB98A1F)
val BeaconWash = Color(0xFF3A2E10)
val LampGreen = Color(0xFF35D07F)
val LampAmber = Color(0xFFFFB020)
val LampRed = Color(0xFFFF5A5A)
val LampOff = Color(0xFF3A4A63)
val SeaFoam = Color(0xFF57C7D4)
val ChartLine = Color(0xFF24406B)

private val BeaconStatusColors =
    darkColorScheme(
        primary = BeaconGold,
        onPrimary = HarborNight,
        primaryContainer = BeaconWash,
        onPrimaryContainer = FogWhite,
        secondary = SeaFoam,
        onSecondary = HarborNight,
        secondaryContainer = HarborEdge,
        onSecondaryContainer = FogWhite,
        tertiary = LampGreen,
        onTertiary = HarborNight,
        tertiaryContainer = HarborCard,
        onTertiaryContainer = FogWhite,
        background = HarborNight,
        onBackground = FogWhite,
        surface = HarborDeep,
        onSurface = FogWhite,
        surfaceVariant = HarborPanel,
        onSurfaceVariant = FogDim,
        outline = HarborEdge,
    )

private val BeaconDisplay = FontFamily.SansSerif
private val BeaconMono = FontFamily.Monospace

private val BeaconStatusType =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                lineHeight = 32.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = BeaconDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = BeaconMono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = BeaconMono,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
    )

private val BeaconStatusShapes =
    Shapes(
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(16.dp),
    )

@Composable
fun BeaconStatusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeaconStatusColors,
        typography = BeaconStatusType,
        shapes = BeaconStatusShapes,
        content = content,
    )
}
