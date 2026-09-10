package com.companyb.companyapp.proto.monoink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF1B1812)
val InkSoft = Color(0xFF1B1812).copy(alpha = 0.68f)
val InkFaint = Color(0xFF1B1812).copy(alpha = 0.42f)
val InkGhost = Color(0xFF1B1812).copy(alpha = 0.14f)
val InkWash = Color(0xFF1B1812).copy(alpha = 0.06f)
val Paper = Color(0xFFFBFAF4)
val PaperEdge = Color(0xFF1B1812).copy(alpha = 0.22f)

val PressSerif = FontFamily.Serif
val PressSans = FontFamily.SansSerif
val PressMono = FontFamily.Monospace

private val MonoScheme =
    lightColorScheme(
        primary = Ink,
        onPrimary = Paper,
        secondary = Ink,
        onSecondary = Paper,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Paper,
        onSurfaceVariant = Ink,
        outline = Ink,
        outlineVariant = PaperEdge,
    )

@Composable
fun MonoInkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoScheme, content = content)
}

@Composable
fun PressPage(
    folio: String,
    title: String,
    deck: String,
    onBack: (() -> Unit)? = null,
    topNote: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Paper)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PressMasthead(folio = folio)
        if (topNote != null) topNote()
        if (onBack != null) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) {
                Text("← BACK TO FRONT PAGE", fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
            }
        }
        Text(title.uppercase(), fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp, color = Ink)
        Text(deck, fontFamily = PressSerif, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = InkSoft)
        RuleThick()
        content()
        Spacer(modifier = Modifier.height(8.dp))
        RuleThin()
        Text(
            "Set in one ink · No. 805 · Printed on paper, not pixels",
            fontFamily = PressMono,
            fontSize = 11.sp,
            color = InkFaint,
        )
    }
}

@Composable
fun PressMasthead(folio: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("THE DAILY LEDGER", fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp, color = Ink)
            Text(folio, fontFamily = PressMono, fontSize = 12.sp, color = InkSoft)
        }
        Text("MANILA · TUESDAY EDITION · PRICE: ONE PROOF", fontFamily = PressSans, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = InkSoft)
        RuleThick()
        RuleThin()
    }
}

@Composable
fun RuleThick() {
    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Ink))
}

@Composable
fun RuleThin() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ink))
}

@Composable
fun RuleDotted() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(InkGhost))
}

@Composable
fun Kicker(text: String) {
    Text(text.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 3.sp, color = Ink)
}

@Composable
fun PressBody(text: String) {
    Text(text, fontFamily = PressSerif, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
}

@Composable
fun PressSmall(text: String) {
    Text(text, fontFamily = PressMono, fontSize = 12.sp, lineHeight = 17.sp, color = InkSoft)
}

@Composable
fun PressSection(number: String, heading: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, fontFamily = PressSerif, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ink)
        Text(heading.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, color = Ink, modifier = Modifier.padding(bottom = 4.dp))
    }
    RuleThin()
}

@Composable
fun PressPlate(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Ink, RectangleShape)
                .background(Paper)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun PressRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label.uppercase(), fontFamily = PressSans, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = InkSoft)
        Text(value, fontFamily = PressSerif, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
fun LedgerRow(label: String, value: String) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, fontFamily = PressSerif, fontSize = 14.sp, color = Ink)
            Text(value, fontFamily = PressMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        RuleDotted()
    }
}

@Composable
fun PressPrimary(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper, disabledContainerColor = InkGhost, disabledContentColor = InkFaint),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun PressSecondary(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RectangleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
fun PressField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontFamily = PressSans, fontSize = 12.sp, color = InkSoft) },
        singleLine = singleLine,
        shape = RectangleShape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Ink,
                unfocusedBorderColor = Ink,
                cursorColor = Ink,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun Stamp(text: String) {
    Box(modifier = Modifier.border(2.dp, Ink, RectangleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text.uppercase(), fontFamily = PressSans, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp, color = Ink)
    }
}

@Composable
fun DayBanner(branchName: String, day: MonoDay) {
    Column(
        modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RectangleShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Kicker("Branch day · $branchName")
            Stamp(day.name)
        }
        PressSmall(dayCopy(day))
        PressSmall("Branch days turn at 04:00 Asia/Manila. Before 04:00 counts as yesterday's sheet.")
    }
}

fun dayCopy(day: MonoDay): String =
    when (day) {
        MonoDay.OPEN -> "OPEN — the press is running. Clock in, take sessions, file remittance."
        MonoDay.PAST -> "PAST — the forme is locked. Read-only; file under today's sheet."
        MonoDay.REMITTED -> "REMITTED — the edition is printed and sealed. Snapshot on file."
    }

@Composable
fun RowScope.CellHead(text: String, weight: Float = 1f) {
    Text(
        text.uppercase(),
        fontFamily = PressSans,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = Ink,
        modifier = Modifier.weight(weight),
    )
}

@Composable
fun RowScope.CellBody(text: String, weight: Float = 1f, bold: Boolean = false) {
    Text(
        text,
        fontFamily = if (bold) PressSerif else PressMono,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        color = Ink,
        modifier = Modifier.weight(weight),
    )
}

@Composable
fun TableHead(vararg heads: Pair<String, Float>) {
    Column {
        RuleThick()
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            heads.forEach { (h, w) -> CellHead(h, w) }
        }
        RuleThin()
    }
}

@Composable
fun TableRowLine(cells: List<Pair<String, Float>>, boldFirst: Boolean = false) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { i, (c, w) -> CellBody(c, w, bold = boldFirst && i == 0) }
        }
        RuleDotted()
    }
}

@Composable
fun InkSpacer() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Ink))
        Text("❦", fontFamily = PressSerif, fontSize = 14.sp, color = Ink)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Ink))
    }
}

@Composable
fun WidthSpacer(w: Int) {
    Spacer(modifier = Modifier.width(w.dp))
}
