package com.companyb.companyapp.proto.terminalgreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #768 — terminal-green prototype theme: phosphor CRT retro. Near-black tube,
// phosphor-green glyphs, amber warnings, monospace everything. Scanlines implied
// by hairline rules, never by images. Distinct from Linear and all siblings.

object TgColors {
    val Tube = Color(0xFF060A06)
    val Panel = Color(0xFF0B120B)
    val PanelEdge = Color(0xFF1E3A24)
    val Phosphor = Color(0xFF33FF66)
    val PhosphorDim = Color(0xFF1E9E44)
    val PhosphorFaint = Color(0xFF0F5A28)
    val Cursor = Color(0xFFCCFFD6)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF5555)
    val Cyan = Color(0xFF55FFFF)
    val Muted = Color(0xFF6FA87E)
    val Faint = Color(0xFF3E6B4B)
}

val TgMono = FontFamily.Monospace

fun tgScheme() = darkColorScheme(
    primary = TgColors.Phosphor,
    onPrimary = TgColors.Tube,
    background = TgColors.Tube,
    onBackground = TgColors.Phosphor,
    surface = TgColors.Panel,
    onSurface = TgColors.Phosphor,
    surfaceVariant = TgColors.PanelEdge,
    secondary = TgColors.Amber,
    error = TgColors.Red,
)

@Composable
fun TgRoot(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = tgScheme()) {
        Box(Modifier.fillMaxSize().background(TgColors.Tube)) {
            content()
        }
    }
}

@Composable
fun TgText(
    text: String,
    size: Int = 13,
    weight: FontWeight = FontWeight.Normal,
    color: Color = TgColors.Phosphor,
    modifier: Modifier = Modifier,
) {
    Text(
        text, color = color, fontSize = size.sp, fontFamily = TgMono,
        fontWeight = weight, lineHeight = (size + 6).sp, modifier = modifier,
    )
}

@Composable
fun TgTitle(text: String) {
    TgText("> " + text, size = 20, weight = FontWeight.Bold, color = TgColors.Cursor)
}

@Composable
fun TgSection(text: String) {
    TgText(":: " + text.uppercase(), size = 12, weight = FontWeight.Bold, color = TgColors.Amber)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun TgNote(text: String) {
    TgText("# " + text, size = 12, color = TgColors.Muted)
}

@Composable
fun TgRule() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(TgColors.PhosphorFaint))
}

@Composable
fun TgPanel(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(TgColors.Panel, RoundedCornerShape(4.dp))
            .border(1.dp, TgColors.PanelEdge, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
fun TgTag(label: String, color: Color = TgColors.PhosphorDim) {
    Box(
        Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
            .border(1.dp, color, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        TgText("[" + label + "]", size = 11, weight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TgButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = TgColors.Phosphor,
            contentColor = TgColors.Tube,
            disabledContainerColor = TgColors.PhosphorFaint,
            disabledContentColor = TgColors.Tube,
        ),
        shape = RoundedCornerShape(3.dp),
    ) {
        Text("[ " + label + " ]", fontFamily = TgMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TgGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TgColors.Phosphor),
    ) {
        Text("< " + label + " >", fontFamily = TgMono, fontSize = 13.sp)
    }
}

@Composable
fun TgDanger(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TgColors.Red),
    ) {
        Text("< " + label + " >", fontFamily = TgMono, fontSize = 13.sp)
    }
}

@Composable
fun TgLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("_" + label + "_", fontFamily = TgMono, fontSize = 13.sp, color = TgColors.Cyan)
    }
}

@Composable
fun TgRowLink(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) TgColors.Phosphor.copy(alpha = 0.14f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TgText(if (selected) ">" else " ", size = 13, weight = FontWeight.Bold, color = TgColors.Amber)
        Spacer(Modifier.width(8.dp))
        TgText(label, size = 13, weight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

fun TgStatusColor(status: TgSessionStatus): Color = when (status) {
    TgSessionStatus.PENDING -> TgColors.Amber
    TgSessionStatus.COMPLETED -> TgColors.Phosphor
    TgSessionStatus.NO_SHOW -> TgColors.Cyan
    TgSessionStatus.CANCELLED -> TgColors.Muted
}

fun TgDayColor(status: TgDayStatus): Color = when (status) {
    TgDayStatus.OPEN -> TgColors.Phosphor
    TgDayStatus.PAST -> TgColors.Amber
    TgDayStatus.REMITTED -> TgColors.Cyan
}
