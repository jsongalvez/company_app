package com.companyb.companyapp.proto.reportpack

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #787 — report-pack theme: export-ready owner summaries. Workbook-sheet aesthetic:
// white sheets, frozen header rows, grid rules, bottom-style sheet tabs, an export
// bar, and navy owner ink. Deliberately spreadsheet-plain, distinct from dashboards.

object RpColors {
    val Page = Color(0xFFE8ECF1)
    val Sheet = Color(0xFFFFFFFF)
    val HeaderFill = Color(0xFFF1F4F8)
    val Grid = Color(0xFFD9DEE5)
    val Ink = Color(0xFF1C2733)
    val Faint = Color(0xFF6B7A8D)
    val Navy = Color(0xFF123B6D)
    val NavySoft = Color(0xFFE3EBF6)
    val Export = Color(0xFF1E7A3C)
    val ExportSoft = Color(0xFFE2F2E7)
    val Amber = Color(0xFF9A6200)
    val AmberSoft = Color(0xFFFFF1D6)
    val Red = Color(0xFFB3261E)
    val RedSoft = Color(0xFFFBE7E5)
    val Blue = Color(0xFF0E5FA8)
    val BlueSoft = Color(0xFFE2EEF9)
    val Vio = Color(0xFF5B4B9D)
    val VioSoft = Color(0xFFEAE6F7)
}

val RpMono = FontFamily.Monospace

fun Int.php(): String {
    val neg = this < 0
    var n = kotlin.math.abs(this)
    if (n == 0) return "₱0"
    val parts = ArrayDeque<String>()
    while (n > 0) {
        parts.addFirst((n % 1000).toString().padStart(if (n >= 1000) 3 else 1, '0'))
        n /= 1000
    }
    return (if (neg) "-₱" else "₱") + parts.joinToString(",")
}

fun RpDayStatus.ink(): Color = when (this) {
    RpDayStatus.OPEN -> RpColors.Export
    RpDayStatus.PAST -> RpColors.Amber
    RpDayStatus.REMITTED -> RpColors.Blue
}

fun RpDayStatus.soft(): Color = when (this) {
    RpDayStatus.OPEN -> RpColors.ExportSoft
    RpDayStatus.PAST -> RpColors.AmberSoft
    RpDayStatus.REMITTED -> RpColors.BlueSoft
}

fun RpSessionStatus.ink(): Color = when (this) {
    RpSessionStatus.PENDING -> RpColors.Amber
    RpSessionStatus.COMPLETED -> RpColors.Export
    RpSessionStatus.NO_SHOW -> RpColors.Vio
    RpSessionStatus.CANCELLED -> RpColors.Faint
}

@Composable
fun RpSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(RpColors.Sheet, RoundedCornerShape(6.dp))
            .border(1.dp, RpColors.Grid, RoundedCornerShape(6.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun RpSheetTitle(text: String) {
    Text(text, color = RpColors.Navy, fontSize = 22.sp, fontWeight = FontWeight.Black)
}

@Composable
fun RpSheetSub(text: String) {
    Text(text, color = RpColors.Faint, fontSize = 12.sp, fontFamily = RpMono)
}

@Composable
fun RpSectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = RpColors.Faint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
fun RpNote(text: String) {
    Text(
        text,
        color = RpColors.Faint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
fun RpRule() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(RpColors.Grid))
    Spacer(Modifier.height(10.dp))
}

@Composable
fun RpGridHeader(cells: List<Pair<String, Float>>) {
    Row(
        Modifier.fillMaxWidth().background(RpColors.HeaderFill).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEach { (label, weight) ->
            Text(
                label.uppercase(),
                color = RpColors.Faint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(weight),
            )
        }
    }
}

@Composable
fun RowScope.RpCell(text: String, weight: Float, mono: Boolean = false, bold: Boolean = false, color: Color = RpColors.Ink) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (mono) RpMono else FontFamily.Default,
        modifier = Modifier.weight(weight),
    )
}

@Composable
fun RpGridRow(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var mod = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Column(Modifier.fillMaxWidth()) {
        Row(mod, verticalAlignment = Alignment.CenterVertically, content = content)
        Box(Modifier.fillMaxWidth().height(1.dp).background(RpColors.Grid))
    }
}

@Composable
fun RpPill(text: String, color: Color, soft: Color) {
    Box(
        Modifier.background(soft, RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = RpMono)
    }
}

@Composable
fun RpAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (enabled) RpColors.Navy else RpColors.Grid
    val fg = if (enabled) Color.White else RpColors.Faint
    Box(
        Modifier.background(bg, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RpGhost(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val fg = if (enabled) RpColors.Navy else RpColors.Faint
    Box(
        Modifier.border(1.dp, if (enabled) RpColors.Navy else RpColors.Grid, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RpChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) RpColors.NavySoft else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) RpColors.Navy else RpColors.Grid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (selected) RpColors.Navy else RpColors.Faint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RpMono,
        )
    }
}

@Composable
fun RpKpi(label: String, value: String, sub: String) {
    Column(
        Modifier.background(RpColors.Sheet)
            .border(1.dp, RpColors.Grid, RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text(label.uppercase(), color = RpColors.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = RpColors.Navy, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = RpMono)
        Text(sub, color = RpColors.Faint, fontSize = 11.sp, fontFamily = RpMono)
    }
}

@Composable
fun RpBar(frac: Float, color: Color) {
    val f = frac.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(10.dp).background(RpColors.HeaderFill, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(f).height(10.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun RpRibbon(dayLabel: String, branchName: String, status: RpDayStatus, onPickDay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(status.soft())
            .border(1.dp, status.ink())
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RpPill(status.name, status.ink(), Color.White)
        Spacer(Modifier.width(10.dp))
        Text(
            "$branchName  ·  $dayLabel  ·  closes 04:00 Asia/Manila",
            color = RpColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        RpGhost("Change day", onClick = onPickDay)
    }
}

@Composable
fun RpTabRail(tabs: List<Pair<String, String>>, selected: String, unread: Int, onPick: (String) -> Unit) {
    Column(Modifier.background(RpColors.Sheet).border(1.dp, RpColors.Grid).padding(vertical = 8.dp)) {
        tabs.forEach { (key, label) ->
            val sel = key == selected
            Row(
                Modifier.fillMaxWidth()
                    .background(if (sel) RpColors.NavySoft else Color.Transparent)
                    .clickable { onPick(key) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(4.dp).height(18.dp)
                        .background(if (sel) RpColors.Navy else Color.Transparent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = if (sel) RpColors.Navy else RpColors.Faint,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = RpMono,
                    modifier = Modifier.weight(1f),
                )
                if (key == "MAILBOX" && unread > 0) {
                    Box(
                        Modifier.background(RpColors.Red, RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text("$unread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RpExportBar(packName: String, onShared: (String) -> Unit) {
    var msg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(RpColors.Sheet).border(1.dp, RpColors.Grid).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SHAREABLE PACK", color = RpColors.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(packName, color = RpColors.Navy, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            RpGhost("Copy pack link") { msg = "Pack link copied: companyb.example/p/$packName"; onShared(msg) }
            Spacer(Modifier.width(8.dp))
            RpGhost("Export CSV") { msg = "CSV staged: ${packName.lowercase()}.csv (12 sheets)"; onShared(msg) }
            Spacer(Modifier.width(8.dp))
            RpAction("Send to owner") { msg = "Pack queued for owner inbox"; onShared(msg) }
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = RpColors.Export, fontSize = 12.sp, fontFamily = RpMono)
        }
    }
}
