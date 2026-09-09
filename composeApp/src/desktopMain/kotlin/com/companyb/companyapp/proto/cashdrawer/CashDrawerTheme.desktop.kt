package com.companyb.companyapp.proto.cashdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

// #785 — cash-drawer prototype theme: night counting room. Dark walnut counter,
// kraft-paper count sheets with ink figures, brass straps per denomination,
// red/amber variance flags. Deliberately distinct from Linear and all siblings.

object CdColors {
    val Counter = Color(0xFF171009)
    val CounterDeep = Color(0xFF0F0A06)
    val Rail = Color(0xFF20150B)
    val RailEdge = Color(0xFF3A2A15)
    val Paper = Color(0xFFF5E9CF)
    val PaperDim = Color(0xFFE8D6AE)
    val Ink = Color(0xFF2A1F10)
    val InkSoft = Color(0xFF6B573A)
    val InkFaint = Color(0xFF9A8560)
    val Brass = Color(0xFFC9972B)
    val BrassDeep = Color(0xFF8A6414)
    val Copper = Color(0xFFD97B3F)
    val Cream = Color(0xFFF9F1DE)
    val CreamDim = Color(0xFFC9B78F)
    val Ok = Color(0xFF7BC47F)
    val OkDeep = Color(0xFF2E7D32)
    val Short = Color(0xFFE0665A)
    val ShortDeep = Color(0xFFB3362A)
    val Over = Color(0xFFE6A100)
    val Muted = Color(0xFF8A7550)
}

val CdFigures = FontFamily.Monospace

fun cdScheme() = darkColorScheme(
    primary = CdColors.Brass,
    onPrimary = CdColors.CounterDeep,
    background = CdColors.Counter,
    onBackground = CdColors.Cream,
    surface = CdColors.Rail,
    onSurface = CdColors.Cream,
    surfaceVariant = CdColors.RailEdge,
    secondary = CdColors.Copper,
    error = CdColors.Short,
)

@Composable
fun CdRoot(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = cdScheme()) {
        Box(Modifier.fillMaxSize().background(CdColors.Counter)) {
            content()
        }
    }
}

@Composable
fun CdTitle(text: String, ink: Boolean = false) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
        color = if (ink) CdColors.Ink else CdColors.Cream,
        letterSpacing = 1.sp,
    )
}

@Composable
fun CdSection(text: String, ink: Boolean = false) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (ink) CdColors.InkSoft else CdColors.Brass,
        letterSpacing = 2.sp,
    )
}

@Composable
fun CdNote(text: String, ink: Boolean = false) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = if (ink) CdColors.InkSoft else CdColors.CreamDim,
        lineHeight = 17.sp,
    )
}

@Composable
fun CdFiguresText(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Bold,
    color: Color = CdColors.Ink,
) {
    Text(
        text = text,
        fontFamily = CdFigures,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
    )
}

@Composable
fun CdRule(ink: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(if (ink) CdColors.InkFaint else CdColors.RailEdge),
    )
}

@Composable
fun CdDashedTape(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .background(CdColors.BrassDeep, RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = CdColors.Cream,
            letterSpacing = 3.sp,
        )
    }
}

@Composable
fun CdSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(CdColors.Paper, RoundedCornerShape(6.dp))
            .border(1.dp, CdColors.PaperDim, RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun CdBrassButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = CdColors.Brass,
            contentColor = CdColors.CounterDeep,
            disabledContainerColor = CdColors.RailEdge,
            disabledContentColor = CdColors.Muted,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun CdGhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CdColors.Brass),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun CdPaperButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = CdColors.Ink,
            contentColor = CdColors.Paper,
            disabledContainerColor = CdColors.InkFaint,
            disabledContentColor = CdColors.PaperDim,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun CdTextLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label.uppercase(), color = CdColors.Brass, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun CdFlag(kind: CdFlagKind) {
    val bg = when (kind) {
        CdFlagKind.MATCH -> CdColors.OkDeep
        CdFlagKind.SHORT -> CdColors.ShortDeep
        CdFlagKind.OVER -> CdColors.Over
    }
    val fg = if (kind == CdFlagKind.OVER) CdColors.CounterDeep else CdColors.Cream
    Box(
        Modifier.background(bg, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = fg, letterSpacing = 1.sp)
    }
}

enum class CdFlagKind { MATCH, SHORT, OVER }

fun cdFlagFor(varianceCentavos: Int): CdFlagKind = when {
    varianceCentavos == 0 -> CdFlagKind.MATCH
    varianceCentavos < 0 -> CdFlagKind.SHORT
    else -> CdFlagKind.OVER
}

@Composable
fun CdRailItem(
    label: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (selected) CdColors.Brass else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = if (selected) CdColors.CounterDeep else CdColors.CreamDim,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Box(
                Modifier.background(
                    if (selected) CdColors.CounterDeep else CdColors.ShortDeep,
                    RoundedCornerShape(8.dp),
                ).padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CdFigures,
                    color = if (selected) CdColors.Brass else CdColors.Cream,
                )
            }
        }
    }
}

@Composable
fun CdKv(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CdColors.InkSoft,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        CdFiguresText(value, size = 13)
    }
}

@Composable
fun RowScope.CdStepper(dec: () -> Unit, inc: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = dec,
        enabled = enabled,
        modifier = Modifier.width(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CdColors.Ink),
        shape = RoundedCornerShape(4.dp),
    ) { Text("−", fontWeight = FontWeight.Black, fontSize = 16.sp) }
    Spacer(Modifier.width(4.dp))
    OutlinedButton(
        onClick = inc,
        enabled = enabled,
        modifier = Modifier.width(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CdColors.Ink),
        shape = RoundedCornerShape(4.dp),
    ) { Text("+", fontWeight = FontWeight.Black, fontSize = 16.sp) }
}

fun php(centavos: Int): String {
    val neg = centavos < 0
    var rest = if (neg) -centavos else centavos
    val cent = rest % 100
    rest /= 100
    val groups = ArrayDeque<String>()
    if (rest == 0) groups.addFirst("0")
    while (rest > 0) {
        val g = (rest % 1000).toString()
        rest /= 1000
        groups.addFirst(if (rest > 0) g.padStart(3, '0') else g)
    }
    val head = groups.joinToString(",")
    val tail = if (cent == 0) "" else "." + cent.toString().padStart(2, '0')
    return (if (neg) "-₱" else "₱") + head + tail
}
