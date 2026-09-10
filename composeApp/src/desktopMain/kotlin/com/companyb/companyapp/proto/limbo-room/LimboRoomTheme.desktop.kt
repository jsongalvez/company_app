package com.companyb.companyapp.proto.limboroom

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

// #824 — limbo-room waiting-hall theme. Night-indigo hall, amber ticket glow,
// velvet-rope red, stamped cream paper. Arches and stubs, never grids.

val LrNight = Color(0xFF1A1433)
val LrNightSoft = Color(0xFF241D47)
val LrNightLine = Color(0xFF3A2F63)
val LrAmber = Color(0xFFFFB224)
val LrAmberDim = Color(0xFF8A5E12)
val LrCream = Color(0xFFFFF6E5)
val LrCreamDim = Color(0xFFE8DCC2)
val LrRope = Color(0xFFC2374B)
val LrMint = Color(0xFF7BD88F)
val LrSky = Color(0xFF8FB8FF)
val LrMuted = Color(0xFFB9AEE0)
val LrInk = Color(0xFF2A2145)

val LrMono: FontFamily = FontFamily.Monospace
val LrSans: FontFamily = FontFamily.SansSerif

val LrHallScheme
    @Composable
    get() = MaterialTheme.colorScheme.copy(
        primary = LrAmber,
        onPrimary = LrNight,
        surface = LrNightSoft,
        onSurface = LrCream,
        background = LrNight,
        onBackground = LrCream,
    )

@Composable
fun LrStamp(text: String, tone: Color = LrAmber) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, tone, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontFamily = LrMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone)
    }
}

@Composable
fun LrSectionTitle(kicker: String, title: String, hint: String = "") {
    Text(kicker, fontFamily = LrMono, fontSize = 11.sp, color = LrAmber, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = LrSans, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LrCream)
        if (hint.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(hint, fontFamily = LrMono, fontSize = 11.sp, color = LrMuted)
        }
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(LrNightLine))
    Spacer(Modifier.height(10.dp))
}

@Composable
fun LrNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2C2350))
            .border(1.dp, LrNightLine, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, fontFamily = LrMono, fontSize = 12.sp, color = LrCreamDim, lineHeight = 17.sp)
    }
}

@Composable
fun LrPaperCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LrCream)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun LrHallCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LrNightSoft)
            .border(1.dp, LrNightLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun LrPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = LrAmber, contentColor = LrNight),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontFamily = LrMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LrGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LrCream),
    ) {
        Text(label, fontFamily = LrMono, fontSize = 12.sp)
    }
}

@Composable
fun LrLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontFamily = LrMono, fontSize = 12.sp, color = LrSky)
    }
}

@Composable
fun LrChipRow(items: List<String>, active: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == active
            OutlinedButton(
                onClick = { onPick(item) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (on) LrAmber else Color.Transparent,
                    contentColor = if (on) LrNight else LrCream,
                ),
            ) {
                Text(item, fontFamily = LrMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RowScope.LrStat(number: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2C2350))
            .border(1.dp, LrNightLine, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(number, fontFamily = LrMono, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LrAmber)
        Text(label, fontFamily = LrMono, fontSize = 10.sp, color = LrMuted)
    }
}

@Composable
fun LrRopeDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(2.dp).background(LrRope))
        Text("  ◆  ", fontSize = 10.sp, color = LrRope)
        Box(Modifier.weight(1f).height(2.dp).background(LrRope))
    }
}
