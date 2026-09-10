package com.companyb.companyapp.proto.captainchair

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

// #825 — captain-chair prototype theme: shift-lead helm console. Deep bridge
// teal-black, brass helm metal, port/starboard lanterns. Deliberately distinct
// from Linear and from sibling depot/timeline variants.

object CcColors {
    val Bridge = Color(0xFF0A1414)
    val Console = Color(0xFF12211F)
    val ConsoleEdge = Color(0xFF2C4A44)
    val Brass = Color(0xFFD9A441)
    val BrassInk = Color(0xFF241703)
    val Paper = Color(0xFFEDF3EE)
    val Faded = Color(0xFF8FA6A0)
    val Port = Color(0xFFFF6B5E)
    val Starboard = Color(0xFF3DDC84)
    val Lantern = Color(0xFF57C7FF)
    val Danger = Color(0xFFFF7A7A)
    val BannerOpen = Color(0xFF0F3524)
    val BannerPast = Color(0xFF3A2E0D)
    val BannerRemitted = Color(0xFF1B2E4A)
}

fun ccScheme() = darkColorScheme(
    primary = CcColors.Brass,
    onPrimary = CcColors.BrassInk,
    background = CcColors.Bridge,
    onBackground = CcColors.Paper,
    surface = CcColors.Console,
    onSurface = CcColors.Paper,
    surfaceVariant = CcColors.ConsoleEdge,
    secondary = CcColors.Lantern,
)

@Composable
fun CcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ccScheme()) { content() }
}

@Composable
fun CcCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(CcColors.Console, RoundedCornerShape(12.dp))
            .border(1.dp, CcColors.ConsoleEdge, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun CcRowCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var mod = Modifier.fillMaxWidth()
        .background(CcColors.Console, RoundedCornerShape(10.dp))
        .border(1.dp, CcColors.ConsoleEdge, RoundedCornerShape(10.dp))
        .padding(12.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Box(mod) { content() }
}

@Composable
fun CcHelmPlate(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(CcColors.Brass)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = CcColors.BrassInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun CcHeadline(text: String) {
    Text(text, color = CcColors.Paper, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
}

@Composable
fun CcNote(text: String) {
    Text(text, color = CcColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun CcStation(title: String) {
    Text(
        title,
        color = CcColors.Brass,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
fun CcPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CcColors.Brass, contentColor = CcColors.BrassInk),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun CcGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = CcColors.Paper)
    }
}

@Composable
fun CcLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = CcColors.Lantern)
    }
}

@Composable
fun CcLamp(color: Color) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
}

@Composable
fun CcChip(text: String, color: Color) {
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
fun CcHelmWheel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CcLamp(CcColors.Port)
        Text(
            "◦—◍—◦ HELM ◦—◍—◦",
            color = CcColors.Brass,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        CcLamp(CcColors.Starboard)
    }
}

@Composable
fun CcSpoke() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CcColors.ConsoleEdge))
}

@Composable
fun CcField(label: String, value: String) {
    Column {
        Text(label, color = CcColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = CcColors.Paper, fontSize = 14.sp)
    }
}

@Composable
fun CcStationHead(spoke: String, title: String, tail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CcLamp(if (spoke == "PORT") CcColors.Port else CcColors.Starboard)
        Spacer(Modifier.width(8.dp))
        CcStation(title)
        Spacer(Modifier.weight(1f))
        Text(tail, color = CcColors.Faded, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
