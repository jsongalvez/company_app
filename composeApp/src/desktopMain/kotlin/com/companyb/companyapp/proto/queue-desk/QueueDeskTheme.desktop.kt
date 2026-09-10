package com.companyb.companyapp.proto.queuedesk

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BeltDark = Color(0xFF23262B)
val BeltDeep = Color(0xFF17191D)
val BeltSteel = Color(0xFF3A3F47)
val TicketPaper = Color(0xFFFBF6E9)
val TicketEdge = Color(0xFFE4D8BC)
val BoardCream = Color(0xFFF4EEDF)
val InkQueue = Color(0xFF26221A)
val InkSoft = Color(0xFF7A7261)
val ClaimBlue = Color(0xFF1D5BBF)
val StampRed = Color(0xFFB3261E)
val StampGreen = Color(0xFF2E7D46)
val StampAmber = Color(0xFF9A6B00)
val SlipPending = Color(0xFFFFD964)
val SlipCompleted = Color(0xFFBDE8B5)
val SlipNoShow = Color(0xFFD8D2E8)
val SlipCancelled = Color(0xFFF2B8B0)
val VoidWash = Color(0xFFE9E2D2)

private val QueueDeskColors = lightColorScheme(
    primary = ClaimBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = InkQueue,
    secondary = BeltSteel,
    onSecondary = Color.White,
    secondaryContainer = TicketEdge,
    onSecondaryContainer = InkQueue,
    tertiary = StampGreen,
    onTertiary = Color.White,
    tertiaryContainer = SlipCompleted,
    onTertiaryContainer = InkQueue,
    background = BeltDeep,
    onBackground = Color.White,
    surface = TicketPaper,
    onSurface = InkQueue,
    surfaceVariant = TicketEdge,
    onSurfaceVariant = InkSoft,
    outline = InkSoft,
)

private val Stamper = FontFamily.Monospace

private val QueueDeskType = Typography(
    displaySmall = TextStyle(fontFamily = Stamper, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Stamper, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Stamper, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = Stamper, fontSize = 11.sp, lineHeight = 15.sp),
)

private val QueueDeskShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun QueueDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QueueDeskColors,
        typography = QueueDeskType,
        shapes = QueueDeskShapes,
        content = content,
    )
}
