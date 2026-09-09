package com.companyb.companyapp.proto.kiosktouch

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #762 — kiosk-touch theme: walk-in kiosk, 48dp+ targets, huge type, high contrast, minimal chrome.
// Paper-white kiosk with safety-orange CTA. Deliberately distinct from Linear and other variants.

object KioskColors {
    val Bg = Color(0xFFFFFEFA)
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF16130E)
    val Muted = Color(0xFF6B6257)
    val Line = Color(0xFF16130E)
    val Accent = Color(0xFFE85D10)
    val AccentInk = Color(0xFFFFFFFF)
    val Green = Color(0xFF1A7F37)
    val Red = Color(0xFFC81E1E)
    val Blue = Color(0xFF0B5FFF)
    val Amber = Color(0xFF9A6700)
    val Wash = Color(0xFFFFF3E8)
}

fun KioskDayStatus.band(): Color = when (this) {
    KioskDayStatus.OPEN -> KioskColors.Green
    KioskDayStatus.PAST -> KioskColors.Amber
    KioskDayStatus.REMITTED -> KioskColors.Blue
}

fun KioskSessionStatus.dot(): Color = when (this) {
    KioskSessionStatus.PENDING -> KioskColors.Accent
    KioskSessionStatus.COMPLETED -> KioskColors.Green
    KioskSessionStatus.NO_SHOW -> KioskColors.Red
    KioskSessionStatus.CANCELLED -> KioskColors.Muted
}

@Composable
fun KioskCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(KioskColors.Paper, RoundedCornerShape(20.dp))
            .border(2.dp, KioskColors.Line, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        content()
    }
}

@Composable
fun KioskSection(title: String) {
    Text(title.uppercase(), color = KioskColors.Muted, fontSize = 15.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun KioskNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = KioskColors.Muted, fontSize = 18.sp, lineHeight = 26.sp)
}

@Composable
fun KioskError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, color = KioskColors.Red, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KioskBigButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KioskColors.Accent,
            contentColor = KioskColors.AccentInk,
            disabledContainerColor = KioskColors.Muted,
        ),
    ) {
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun KioskSecondary(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = KioskColors.Ink)
    }
}

@Composable
fun KioskRowButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KioskColors.Ink,
            contentColor = Color.White,
        ),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun KioskGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KioskColors.Ink)
    }
}

@Composable
fun KioskChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) KioskColors.Ink else Color.Transparent
    val fg = if (selected) Color.White else KioskColors.Ink
    Box(
        Modifier.heightIn(min = 56.dp)
            .background(bg, RoundedCornerShape(28.dp))
            .border(2.dp, KioskColors.Line, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun KioskLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, color = KioskColors.Blue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KioskDayBand(
    status: KioskDayStatus,
    onPick: (KioskDayStatus) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(status.band()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "DAY ${status.name}",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            Text("04:00 Asia/Manila cut", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            KioskDayStatus.entries.forEach { option ->
                val active = option == status
                Box(
                    Modifier.background(
                        if (active) Color.White else Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(16.dp),
                    )
                        .clickable(onClick = { onPick(option) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        option.name,
                        color = if (active) status.band() else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
fun KioskGap() {
    Spacer(Modifier.height(14.dp))
}

@Composable
fun KioskRowGap() {
    Spacer(Modifier.width(10.dp))
}
