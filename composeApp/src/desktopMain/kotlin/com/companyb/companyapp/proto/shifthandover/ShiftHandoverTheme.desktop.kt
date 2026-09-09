package com.companyb.companyapp.proto.shifthandover

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #771 — shift-handover prototype theme: night-shift changeover board. Deep depot navy,
// signal-amber tape, outgoing/incoming crew columns. Deliberately distinct from Linear.

object ShColors {
    val Depot = Color(0xFF0B1020)
    val Panel = Color(0xFF141B31)
    val PanelEdge = Color(0xFF2A3556)
    val Tape = Color(0xFFFFB224)
    val TapeInk = Color(0xFF1A1206)
    val Paper = Color(0xFFE8ECF7)
    val Faded = Color(0xFF8E99B8)
    val Outgoing = Color(0xFFFF8A5C)
    val Incoming = Color(0xFF4ADE80)
    val Teal = Color(0xFF38BDF8)
    val Danger = Color(0xFFF87171)
    val BannerOpen = Color(0xFF12351F)
    val BannerPast = Color(0xFF3A2C10)
    val BannerRemitted = Color(0xFF1C2C4E)
}

fun shScheme() = darkColorScheme(
    primary = ShColors.Tape,
    onPrimary = ShColors.TapeInk,
    background = ShColors.Depot,
    onBackground = ShColors.Paper,
    surface = ShColors.Panel,
    onSurface = ShColors.Paper,
    surfaceVariant = ShColors.PanelEdge,
    secondary = ShColors.Teal,
)

@Composable
fun ShTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = shScheme()) { content() }
}

@Composable
fun ShCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(ShColors.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, ShColors.PanelEdge, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun ShTape(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(ShColors.Tape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = ShColors.TapeInk, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ShHeadline(text: String) {
    Text(text, color = ShColors.Paper, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
}

@Composable
fun ShNote(text: String) {
    Text(text, color = ShColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun ShSection(title: String) {
    Text(title, color = ShColors.Tape, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
}

@Composable
fun ShPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ShColors.Tape, contentColor = ShColors.TapeInk),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun ShGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = ShColors.Paper)
    }
}

@Composable
fun ShLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = ShColors.Teal)
    }
}

@Composable
fun ShDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
}

@Composable
fun ShStatusChip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ShRowCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var mod = Modifier.fillMaxWidth()
        .background(ShColors.Panel, RoundedCornerShape(8.dp))
        .border(1.dp, ShColors.PanelEdge, RoundedCornerShape(8.dp))
        .padding(12.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Box(mod) { content() }
}

@Composable
fun ShCrewTag(name: String, outgoing: Boolean) {
    val color = if (outgoing) ShColors.Outgoing else ShColors.Incoming
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShDot(color)
        Text(
            (if (outgoing) "OUT " else "IN ") + name,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun ShShiftArrow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(2.dp))
        Text("OUT → IN", color = ShColors.Tape, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ShField(label: String, value: String) {
    Column {
        Text(label, color = ShColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = ShColors.Paper, fontSize = 14.sp)
    }
}
