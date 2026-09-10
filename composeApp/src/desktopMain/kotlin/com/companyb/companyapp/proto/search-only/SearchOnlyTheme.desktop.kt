package com.companyb.companyapp.proto.searchonly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #855 — search-only midnight-spotlight tokens. One glowing command bar on
// near-black indigo; lime caret energy; deliberately far from card consoles.

object SoColors {
    val Abyss = Color(0xFF0B0D1A)
    val Panel = Color(0xFF141830)
    val PanelLine = Color(0xFF2A3160)
    val Bar = Color(0xFF1B2142)
    val Ink = Color(0xFFF2F3FF)
    val Dim = Color(0xFF9AA3D0)
    val Faint = Color(0xFF5B6395)
    val Lime = Color(0xFFC6F24E)
    val LimeInk = Color(0xFF1A2200)
    val Violet = Color(0xFF9D8CFF)
    val VioletWash = Color(0xFF26224D)
    val Green = Color(0xFF7CE3A1)
    val GreenWash = Color(0xFF173B2A)
    val Amber = Color(0xFFFFC857)
    val AmberWash = Color(0xFF42300E)
    val Red = Color(0xFFFF8A7A)
    val RedWash = Color(0xFF451D18)
    val Cyan = Color(0xFF7BDFF2)
    val CyanWash = Color(0xFF143D47)
}

enum class SoTone { LIME, VIOLET, GREEN, AMBER, RED, CYAN, GREY }

private fun SoTone.fg(): Color = when (this) {
    SoTone.LIME -> SoColors.Lime
    SoTone.VIOLET -> SoColors.Violet
    SoTone.GREEN -> SoColors.Green
    SoTone.AMBER -> SoColors.Amber
    SoTone.RED -> SoColors.Red
    SoTone.CYAN -> SoColors.Cyan
    SoTone.GREY -> SoColors.Dim
}

private fun SoTone.wash(): Color = when (this) {
    SoTone.LIME -> Color(0xFF2A3312)
    SoTone.VIOLET -> SoColors.VioletWash
    SoTone.GREEN -> SoColors.GreenWash
    SoTone.AMBER -> SoColors.AmberWash
    SoTone.RED -> SoColors.RedWash
    SoTone.CYAN -> SoColors.CyanWash
    SoTone.GREY -> Color(0xFF20264A)
}

@Composable
fun SoPill(text: String, tone: SoTone = SoTone.GREY) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(tone.wash())
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone.fg(), fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SoHitRow(
    glyph: String,
    title: String,
    sub: String,
    tone: SoTone,
    hot: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hot) SoColors.Bar else Color.Transparent)
            .border(
                if (hot) 1.dp else 0.dp,
                if (hot) SoColors.Lime else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(tone.wash())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(glyph, fontSize = 13.sp, fontWeight = FontWeight.Black, color = tone.fg())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SoColors.Ink)
            Text(sub, fontSize = 12.sp, color = SoColors.Dim, fontFamily = FontFamily.Monospace)
        }
        if (hot) SoPill("⏎ top", SoTone.LIME)
    }
}

@Composable
fun SoSection(label: String, hint: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = SoColors.Lime, fontFamily = FontFamily.Monospace)
        if (hint.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(hint, fontSize = 11.sp, color = SoColors.Faint, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun SoCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SoColors.Panel).border(1.dp, SoColors.PanelLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun SoH1(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Black, color = SoColors.Ink)
}

@Composable
fun SoBody(text: String) {
    Text(text, fontSize = 13.sp, color = SoColors.Dim)
}

@Composable
fun SoMono(text: String) {
    Text(text, fontSize = 12.sp, color = SoColors.Dim, fontFamily = FontFamily.Monospace)
}

@Composable
fun SoNote(text: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SoColors.VioletWash)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("◈ $text", fontSize = 12.sp, color = SoColors.Violet)
    }
}

@Composable
fun SoChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(SoColors.Bar)
            .border(1.dp, SoColors.PanelLine, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = SoColors.Ink, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SoBarField(query: String, onQuery: (String) -> Unit, onEnter: () -> Unit, hint: String) {
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(hint, fontSize = 17.sp, color = SoColors.Faint) },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 17.sp,
            color = SoColors.Ink,
            fontFamily = FontFamily.Monospace,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SoColors.Bar,
            unfocusedContainerColor = SoColors.Bar,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, SoColors.Lime, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
    )
}

@Composable
fun SoPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = SoColors.Lime, contentColor = SoColors.LimeInk),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = SoColors.Ink)
    }
}

@Composable
fun SoActionRow(content: @Composable RowScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        content()
        Spacer(Modifier.weight(1f))
    }
}
