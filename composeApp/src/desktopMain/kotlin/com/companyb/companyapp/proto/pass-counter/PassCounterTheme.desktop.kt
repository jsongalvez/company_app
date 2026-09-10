package com.companyb.companyapp.proto.passcounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #832 — pass-counter expo theme: heat-lamp amber over charcoal steel, cream paper
// tickets pinned on the pass rail, brass bell, red waste log. Reads like a kitchen
// pass, not a Linear board.

object PcColors {
    val Char = Color(0xFF1B1512)
    val Steel = Color(0xFF2A2320)
    val SteelSoft = Color(0xFF3E3630)
    val Paper = Color(0xFFF3EAD6)
    val Ticket = Color(0xFFFFF8E8)
    val Ink = Color(0xFF241A12)
    val Muted = Color(0xFF8A7A64)
    val Line = Color(0xFFD8C9A8)
    val Lamp = Color(0xFFE8A020)
    val LampSoft = Color(0xFFF7DEA8)
    val Brass = Color(0xFFC9A227)
    val Fire = Color(0xFFC2410C)
    val FireSoft = Color(0xFFF9D9C4)
    val Serve = Color(0xFF2F7D4F)
    val ServeSoft = Color(0xFFD5E9D8)
    val Waste = Color(0xFFB3372F)
    val WasteSoft = Color(0xFFF3CFCB)
    val Past = Color(0xFF64748B)
    val PastSoft = Color(0xFFE2E8F0)
    val Remitted = Color(0xFF9A6B0F)
    val RemittedSoft = Color(0xFFF3E3B3)
    val Cream = Color(0xFFFFFDF4)
}

val PcSlip: FontFamily = FontFamily.Monospace
val PcBoard: FontFamily = FontFamily.SansSerif

private val PcTypography = Typography(
    displaySmall = TextStyle(fontFamily = PcBoard, fontWeight = FontWeight.Black, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = PcBoard, fontWeight = FontWeight.Black, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = PcBoard, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    titleMedium = TextStyle(fontFamily = PcBoard, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

@Composable
fun PassCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PcColors.Fire,
            onPrimary = PcColors.Ticket,
            secondary = PcColors.Brass,
            tertiary = PcColors.Serve,
            background = PcColors.Paper,
            surface = PcColors.Ticket,
            onBackground = PcColors.Ink,
            onSurface = PcColors.Ink,
        ),
        typography = PcTypography,
        content = content,
    )
}

fun PcSessionStatusRail(): Color = PcColors.Fire

@Composable
fun PcTicketCard(
    pinned: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(PcColors.Ticket, RoundedCornerShape(8.dp))
            .border(1.dp, if (pinned) PcColors.Brass else PcColors.Line, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun PcDarkCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(PcColors.Steel, RoundedCornerShape(8.dp))
            .border(1.dp, PcColors.SteelSoft, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun PcSectionTitle(
    text: String,
    dark: Boolean = false,
) {
    Text(
        text.uppercase(),
        color = if (dark) PcColors.Lamp else PcColors.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = PcSlip,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PcChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PcColors.Char else PcColors.Cream
    val fg = if (selected) PcColors.LampSoft else PcColors.Ink
    Box(
        Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) PcColors.Char else PcColors.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PcSlip)
    }
}

@Composable
fun PcFireButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = PcColors.Fire, contentColor = PcColors.Ticket),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PcSteelButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = PcColors.Char, contentColor = PcColors.LampSoft),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PcGhost(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = PcColors.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PcLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, color = PcColors.Fire, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PcTag(
    text: String,
    color: Color,
) {
    Box(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = PcSlip)
    }
}

@Composable
fun PcRowButtons(vararg buttons: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { it() }
    }
}

@Composable
fun PcFieldRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = PcColors.Muted, fontWeight = FontWeight.Bold, fontFamily = PcSlip)
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 13.sp, color = PcColors.Ink)
    }
}
