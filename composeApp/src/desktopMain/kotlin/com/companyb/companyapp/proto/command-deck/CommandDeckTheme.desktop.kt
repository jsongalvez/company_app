package com.companyb.companyapp.proto.commanddeck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #828 — command-deck starship theme. Cold void black, steel deck panels,
// cyan console glow, phosphor-green confirmations, amber cautions. Klaxon red
// is rationed: it fires only for the two CdStore.klaxons() conditions, never
// for routine counts — glowing clusters carry the normal load instead.

val CdVoid = Color(0xFF060810)
val CdDeck = Color(0xFF0E1424)
val CdDeckSoft = Color(0xFF131B30)
val CdConsole = Color(0xFF0A0F1E)
val CdLine = Color(0xFF22314F)
val CdGlow = Color(0xFF5AD6FF)
val CdGlowDim = Color(0xFF2A6E8A)
val CdPhos = Color(0xFF5CFF9D)
val CdAmber = Color(0xFFFFB224)
val CdKlaxon = Color(0xFFFF3B5C)
val CdFog = Color(0xFF8C9BB8)
val CdPaper = Color(0xFFEAF2FF)
val CdInk = Color(0xFF0B1220)
val CdInkSoft = Color(0xFF4A5A78)

val CdMono: FontFamily = FontFamily.Monospace
val CdSans: FontFamily = FontFamily.SansSerif

val CdDeckScheme
    @Composable
    get() = MaterialTheme.colorScheme.copy(
        primary = CdGlow,
        onPrimary = CdVoid,
        surface = CdDeckSoft,
        onSurface = CdPaper,
        background = CdVoid,
        onBackground = CdPaper,
    )

@Composable
fun CdGlowTag(text: String, tone: Color = CdGlow) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, tone, RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0F1E))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontFamily = CdMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone)
    }
}

@Composable
fun CdSectionHead(kicker: String, title: String, hint: String = "") {
    Text(kicker, fontFamily = CdMono, fontSize = 11.sp, color = CdGlow, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = CdSans, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CdPaper)
        if (hint.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(hint, fontFamily = CdMono, fontSize = 11.sp, color = CdFog)
        }
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(CdLine))
    Spacer(Modifier.height(10.dp))
}

@Composable
fun CdNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CdDeckSoft)
            .border(1.dp, CdLine, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text, fontFamily = CdMono, fontSize = 12.sp, color = CdFog, lineHeight = 17.sp)
    }
}

@Composable
fun CdPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CdDeck)
            .border(1.dp, CdLine, RoundedCornerShape(10.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun CdConsoleCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CdConsole)
            .border(1.dp, CdGlowDim, RoundedCornerShape(10.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun CdPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CdGlow, contentColor = CdVoid),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(label, fontFamily = CdMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CdGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CdPaper),
    ) {
        Text(label, fontFamily = CdMono, fontSize = 12.sp)
    }
}

@Composable
fun CdLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontFamily = CdMono, fontSize = 12.sp, color = CdGlow)
    }
}

@Composable
fun CdChipRow(items: List<String>, active: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == active
            OutlinedButton(
                onClick = { onPick(item) },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (on) CdGlow else Color.Transparent,
                    contentColor = if (on) CdVoid else CdPaper,
                ),
            ) {
                Text(item, fontFamily = CdMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RowScope.CdCluster(number: String, label: String, tone: Color = CdGlow) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(CdConsole)
            .border(1.dp, tone, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(number, fontFamily = CdMono, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tone)
        Text(label, fontFamily = CdMono, fontSize = 10.sp, color = CdFog)
    }
}

@Composable
fun CdDeckDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(CdLine))
        Text("  ▪ ▪ ▪  ", fontSize = 10.sp, color = CdGlowDim)
        Box(Modifier.weight(1f).height(1.dp).background(CdLine))
    }
}
