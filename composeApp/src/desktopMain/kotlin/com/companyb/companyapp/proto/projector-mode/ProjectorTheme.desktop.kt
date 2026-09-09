package com.companyb.companyapp.proto.projectormode

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

// #777 — projector presentation theme: pure black stage, paper-white ink, signal-yellow
// focus color, giant black-weight type sized to read from 5 meters. Minimal chrome on
// purpose — one focus card at a time, no dense tables, no Linear tokens.

object PmColors {
    val Stage = Color(0xFF000000)
    val Card = Color(0xFF0D0D0D)
    val CardEdge = Color(0xFFFFFFFF)
    val Focus = Color(0xFFFFE600)
    val FocusInk = Color(0xFF000000)
    val Ink = Color(0xFFFFFFFF)
    val Muted = Color(0xFFCFCFCF)
    val Green = Color(0xFF00E676)
    val Red = Color(0xFFFF5252)
    val Cyan = Color(0xFF40C8FF)
}

fun PmDayStatus.band(): Color = when (this) {
    PmDayStatus.OPEN -> PmColors.Green
    PmDayStatus.PAST -> PmColors.Focus
    PmDayStatus.REMITTED -> PmColors.Ink
}

fun PmSessionStatus.dot(): Color = when (this) {
    PmSessionStatus.PENDING -> PmColors.Focus
    PmSessionStatus.COMPLETED -> PmColors.Green
    PmSessionStatus.NO_SHOW -> PmColors.Red
    PmSessionStatus.CANCELLED -> PmColors.Muted
}

@Composable
fun PmPanel(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(PmColors.Card, RoundedCornerShape(14.dp))
            .border(3.dp, PmColors.CardEdge, RoundedCornerShape(14.dp))
            .padding(24.dp),
    ) {
        content()
    }
}

@Composable
fun PmSectionTitle(text: String) {
    Text(text.uppercase(), color = PmColors.Focus, fontSize = 20.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun PmGiant(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = PmColors.Ink, fontSize = 96.sp, fontWeight = FontWeight.Black)
        Text(label.uppercase(), color = PmColors.Focus, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PmChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PmColors.Focus else Color.Transparent
    val fg = if (selected) PmColors.FocusInk else PmColors.Ink
    Box(
        Modifier.background(bg, RoundedCornerShape(28.dp))
            .border(2.dp, if (selected) PmColors.Focus else PmColors.Ink, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text.uppercase(), color = fg, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PmPrimary(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = PmColors.Focus,
            contentColor = PmColors.FocusInk,
            disabledContainerColor = PmColors.Muted,
            disabledContentColor = PmColors.FocusInk,
        ),
    ) {
        Text(text.uppercase(), fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
fun PmGhost(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmColors.Ink),
        border = androidx.compose.foundation.BorderStroke(2.dp, PmColors.Ink),
    ) {
        Text(text.uppercase(), color = PmColors.Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
fun PmLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text.uppercase(), color = PmColors.Cyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun PmDayBanner(
    status: PmDayStatus,
    onPick: (PmDayStatus) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(status.band()).padding(horizontal = 28.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "BRANCH DAY: ${status.name}",
                color = Color.Black,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
            )
            Text("04:00 Asia/Manila", color = Color.Black.copy(alpha = 0.75f), fontSize = 20.sp,
                fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            PmDayStatus.entries.forEach { option ->
                val active = option == status
                Box(
                    Modifier.background(
                        if (active) Color.Black else Color.Black.copy(alpha = 0.16f),
                        RoundedCornerShape(24.dp),
                    )
                        .clickable(onClick = { onPick(option) })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(option.name, color = if (active) status.band() else Color.Black, fontSize = 20.sp,
                        fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun PmFieldError(text: String?) {
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, color = PmColors.Red, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun PmNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = PmColors.Muted, fontSize = 20.sp)
}

@Composable
fun PmSpacerRow() {
    Spacer(Modifier.width(12.dp))
}
