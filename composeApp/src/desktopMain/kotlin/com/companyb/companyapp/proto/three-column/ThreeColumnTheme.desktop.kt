package com.companyb.companyapp.proto.threecolumn

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #809 — three-column command theme. Day-shift ops board: navy command rail,
// light steel deck, white inspector, hard 1px rules, LED status dots, mono
// data readouts, uppercase microcaps labels. Dense, tabular, distinct.

val TcDeck = Color(0xFFEDF1F6)
val TcPanel = Color(0xFFFFFFFF)
val TcLine = Color(0xFFD3DCE7)
val TcInk = Color(0xFF0F1F33)
val TcInkSoft = Color(0xFF5B6B82)
val TcSteel = Color(0xFF8FA1B8)
val TcRail = Color(0xFF0E1E33)
val TcRailHi = Color(0xFF1B3355)
val TcRailDim = Color(0xFF93A5BE)
val TcSignal = Color(0xFFE4572E)
val TcGo = Color(0xFF1E7F4F)
val TcAmber = Color(0xFFB7791F)
val TcRed = Color(0xFFC0392B)
val TcLedBg = Color(0xFFE4EAF2)

val TcSans = FontFamily.SansSerif
val TcMono = FontFamily.Monospace

@Composable
fun TcRoot(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = TcDeck,
            surface = TcPanel,
            surfaceVariant = TcLedBg,
            primary = TcSignal,
            onPrimary = Color.White,
            secondary = TcRail,
            tertiary = TcAmber,
            error = TcRed,
            onBackground = TcInk,
            onSurface = TcInk,
            outline = TcLine,
        ),
        content = content,
    )
}

@Composable
fun TcMicro(text: String, color: Color = TcInkSoft) {
    Text(
        text = text.uppercase(),
        fontFamily = TcSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = color,
    )
}

@Composable
fun TcRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TcLine))
}

@Composable
fun TcPanelBox(selected: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TcPanel)
            .border(if (selected) 2.dp else 1.dp, if (selected) TcSignal else TcLine, RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
fun TcLed(status: String) {
    val dot = when (status) {
        "COMPLETED", "OPEN", "SUBMITTED", "SEALED", "active", "read" -> TcGo
        "PENDING", "DRAFT", "live", "pending" -> TcAmber
        "NO_SHOW", "PAST" -> TcSteel
        "CANCELLED", "REMITTED", "VOIDED", "withdrawn", "declined" -> TcRed
        else -> TcSteel
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(
            text = status,
            fontFamily = TcMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TcInk,
        )
    }
}

@Composable
fun TcMonoLine(text: String, soft: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = TcMono,
        fontSize = 12.sp,
        color = if (soft) TcInkSoft else TcInk,
        modifier = modifier,
    )
}

@Composable
fun TcNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TcLedBg)
            .border(1.dp, TcLine, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Text(text = "// " + text, fontFamily = TcMono, fontSize = 11.sp, color = TcInkSoft)
    }
}

@Composable
fun TcNavItem(label: String, code: String, selected: Boolean, unread: Int = 0, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) TcRailHi else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) TcSignal else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = code,
            fontFamily = TcMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) TcSignal else TcRailDim,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = label,
            fontFamily = TcSans,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TcSignal)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(text = unread.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
    if (selected) {
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .width(3.dp)
                .height(2.dp)
                .background(Color.Transparent),
        )
    }
}

@Composable
fun TcRowShell(selected: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TcPanel)
            .border(if (selected) 2.dp else 1.dp, if (selected) TcSignal else TcLine, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
