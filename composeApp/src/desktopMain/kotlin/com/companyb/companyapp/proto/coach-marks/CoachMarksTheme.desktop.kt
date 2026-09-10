package com.companyb.companyapp.proto.coachmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CmInk = Color(0xFF0F1420)
val CmPanel = Color(0xFF1A2233)
val CmPanel2 = Color(0xFF222C42)
val CmHairline = Color(0xFF2E3A55)
val CmText = Color(0xFFF2F5FA)
val CmMuted = Color(0xFF9AA6BE)
val CmAmber = Color(0xFFFFC53D)
val CmAmberDeep = Color(0xFFB97E0C)
val CmAmberInk = Color(0xFF3A2A00)
val CmBubble = Color(0xFFFFF6E0)
val CmGood = Color(0xFF43D39A)
val CmWarn = Color(0xFFFFB020)
val CmBad = Color(0xFFFF6B6B)
val CmInfo = Color(0xFF6EA8FF)

private val CmScheme =
    darkColorScheme(
        primary = CmAmber,
        onPrimary = CmAmberInk,
        secondary = CmInfo,
        background = CmInk,
        surface = CmPanel,
        onBackground = CmText,
        onSurface = CmText,
        surfaceVariant = CmPanel2,
        onSurfaceVariant = CmMuted,
        outline = CmHairline,
        error = CmBad,
    )

@Composable
fun CoachMarksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CmScheme, content = content)
}

fun peso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

fun statusColor(status: CmSessionStatus): Color =
    when (status) {
        CmSessionStatus.PENDING -> CmWarn
        CmSessionStatus.COMPLETED -> CmGood
        CmSessionStatus.NO_SHOW -> CmInfo
        CmSessionStatus.CANCELLED -> CmBad
    }

fun dayColor(state: CmDayState): Color =
    when (state) {
        CmDayState.OPEN -> CmGood
        CmDayState.PAST -> CmWarn
        CmDayState.REMITTED -> CmInfo
    }

@Composable
fun CmChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CmBeacon(number: Int, done: Boolean = false) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (done) CmGood else CmAmber),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (done) "✓" else number.toString(),
            color = if (done) CmInk else CmAmberInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CmCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    beaconNumber: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CmPanel)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) CmAmber else CmHairline,
                shape = shape,
            )
            .padding(16.dp),
    ) {
        if (highlighted && beaconNumber != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CmBeacon(beaconNumber)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Guide is pointing here",
                    color = CmAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(8.dp))
        }
        content()
    }
}

@Composable
fun CmSectionTitle(text: String) {
    Text(text, color = CmText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun CmHint(text: String) {
    Text(text, color = CmMuted, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun CmRuleNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CmAmber.copy(alpha = 0.10f))
            .border(1.dp, CmAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(text, color = CmAmber, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
