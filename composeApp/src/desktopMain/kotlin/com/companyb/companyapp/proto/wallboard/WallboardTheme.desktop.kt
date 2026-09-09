package com.companyb.companyapp.proto.wallboard

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

// #759 — wallboard TV command-center theme: phosphor amber on near-black, huge numerals,
// full-width OPEN/PAST/REMITTED color bands. Deliberately distinct from the Linear system.

object WbColors {
    val Bg = Color(0xFF0A0C0A)
    val Panel = Color(0xFF131711)
    val PanelEdge = Color(0xFF2A3325)
    val Amber = Color(0xFFFFB000)
    val AmberDim = Color(0xFF8A5F00)
    val Green = Color(0xFF35D07F)
    val Red = Color(0xFFFF5470)
    val Cyan = Color(0xFF4CC9F0)
    val Ink = Color(0xFFF2EFE3)
    val Muted = Color(0xFF9AA48F)
}

fun WbDayStatus.band(): Color = when (this) {
    WbDayStatus.OPEN -> WbColors.Green
    WbDayStatus.PAST -> WbColors.Amber
    WbDayStatus.REMITTED -> WbColors.Cyan
}

fun WbSessionStatus.dot(): Color = when (this) {
    WbSessionStatus.PENDING -> WbColors.Amber
    WbSessionStatus.COMPLETED -> WbColors.Green
    WbSessionStatus.NO_SHOW -> WbColors.Red
    WbSessionStatus.CANCELLED -> WbColors.Muted
}

@Composable
fun WbPanel(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(WbColors.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, WbColors.PanelEdge, RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
fun WbSectionTitle(text: String) {
    Text(text, color = WbColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun WbNumeral(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = WbColors.Ink, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text(label.uppercase(), color = WbColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) WbColors.Amber else Color.Transparent
    val fg = if (selected) Color.Black else WbColors.Muted
    Box(
        Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) WbColors.Amber else WbColors.PanelEdge, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbPrimary(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = WbColors.Amber, contentColor = Color.Black),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbGhost(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(text, color = WbColors.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, color = WbColors.Cyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbDayBand(
    status: WbDayStatus,
    onPick: (WbDayStatus) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(status.band()).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "BRANCH DAY: ${status.name}",
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text("boundary 04:00 Asia/Manila", color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            WbDayStatus.entries.forEach { option ->
                val active = option == status
                Box(
                    Modifier.background(
                        if (active) Color.Black else Color.Black.copy(alpha = 0.18f),
                        RoundedCornerShape(16.dp),
                    )
                        .clickable(onClick = { onPick(option) })
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(option.name, color = if (active) status.band() else Color.Black, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WbFieldError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(6.dp))
        Text(text, color = WbColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WbNote(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, color = WbColors.Muted, fontSize = 13.sp)
}

@Composable
fun WbSpacerRow() {
    Spacer(Modifier.width(8.dp))
}
