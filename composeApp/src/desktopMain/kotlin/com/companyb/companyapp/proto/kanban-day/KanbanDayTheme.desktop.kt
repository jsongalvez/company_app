package com.companyb.companyapp.proto.kanday

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val CorkBoard = Color(0xFFB98A5E)
val CorkDeep = Color(0xFF9A6F45)
val LanePaper = Color(0xFFF7F0DC)
val LaneEdge = Color(0xFFE3D5B4)
val TapeStrip = Color(0xFFE8DCC0)
val InkBoard = Color(0xFF3A2E22)
val InkSoft = Color(0xFF7A6A56)
val NotePending = Color(0xFFFFD964)
val NoteCompleted = Color(0xFFBDE8B5)
val NoteNoShow = Color(0xFFD8D2E8)
val NoteCancelled = Color(0xFFF2B8B0)
val PinRed = Color(0xFFC0392B)
val RailDark = Color(0xFF4A3826)
val BoardCream = Color(0xFFFFFBEC)
val WipAlert = Color(0xFFB3261E)

private val KanbanDayColors = lightColorScheme(
    primary = RailDark,
    onPrimary = Color.White,
    primaryContainer = TapeStrip,
    onPrimaryContainer = InkBoard,
    secondary = CorkDeep,
    onSecondary = Color.White,
    secondaryContainer = LanePaper,
    onSecondaryContainer = InkBoard,
    tertiary = Color(0xFF5B7F3C),
    onTertiary = Color.White,
    tertiaryContainer = NoteCompleted,
    onTertiaryContainer = InkBoard,
    background = CorkBoard,
    onBackground = Color.White,
    surface = LanePaper,
    onSurface = InkBoard,
    surfaceVariant = LaneEdge,
    onSurfaceVariant = InkSoft,
    outline = InkBoard,
)

private val Mono = FontFamily.Monospace

private val KanbanDayType = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
)

private val KanbanDayShapes = Shapes(
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(5.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun KanbanDayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KanbanDayColors,
        typography = KanbanDayType,
        shapes = KanbanDayShapes,
        content = content,
    )
}

fun noteColorFor(status: KbSessionStatus): Color = when (status) {
    KbSessionStatus.PENDING -> NotePending
    KbSessionStatus.COMPLETED -> NoteCompleted
    KbSessionStatus.NO_SHOW -> NoteNoShow
    KbSessionStatus.CANCELLED -> NoteCancelled
}
