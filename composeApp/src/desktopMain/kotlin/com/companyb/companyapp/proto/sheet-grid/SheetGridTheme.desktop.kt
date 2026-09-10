package com.companyb.companyapp.proto.sheetgrid

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #817 — sheet-grid theme: everything is a spreadsheet. Dense 1px grids,
// frozen header rows, row numbers, column letters, a green formula bar,
// bottom sheet tabs and a status bar. No rounded cards as structure.

val SgPaper = Color(0xFFFFFFFF)
val SgWash = Color(0xFFF3F5F3)
val SgGridLine = Color(0xFFD3D8D3)
val SgHeaderFill = Color(0xFFE9EDE9)
val SgRowNumFill = Color(0xFFF1F3F1)
val SgRowNumInk = Color(0xFF5F6368)
val SgInk = Color(0xFF1F2421)
val SgMuted = Color(0xFF5F6368)
val SgGreen = Color(0xFF107C41)
val SgGreenDark = Color(0xFF0B5C30)
val SgGreenTint = Color(0xFFE2EFE5)
val SgActiveBorder = Color(0xFF188038)
val SgTabActive = Color(0xFFFFFFFF)
val SgTabIdle = Color(0xFFDDE3DD)
val SgNoteBg = Color(0xFFFFF8E1)
val SgNoteLine = Color(0xFFE3C766)
val SgNoteInk = Color(0xFF6B5300)

val SgToneGreenFg = Color(0xFF0B6B4F)
val SgToneGreenBg = Color(0xFFDFF3E7)
val SgToneAmberFg = Color(0xFF8A5E00)
val SgToneAmberBg = Color(0xFFFFF1CF)
val SgToneRedFg = Color(0xFFB3261E)
val SgToneRedBg = Color(0xFFFDECEA)
val SgToneBlueFg = Color(0xFF1D5FC2)
val SgToneBlueBg = Color(0xFFE7EFFD)
val SgToneGreyFg = Color(0xFF57534E)
val SgToneGreyBg = Color(0xFFEDEBE4)
val SgToneVioletFg = Color(0xFF5B3DF0)
val SgToneVioletBg = Color(0xFFEAE6FD)

val SgScheme =
    lightColorScheme(
        primary = SgGreen,
        onPrimary = Color.White,
        surface = SgPaper,
        onSurface = SgInk,
        surfaceVariant = SgWash,
        onSurfaceVariant = SgMuted,
        outline = SgGridLine,
    )

val SgMono = FontFamily.Monospace
const val SG_ADDR_GLYPH = "fx"

fun sgTone(status: String): Pair<Color, Color> =
    when (status) {
        "COMPLETED", "OPEN", "SUBMITTED", "READ", "CLOCKED_IN", "GRANTED", "ACCEPTED" -> SgToneGreenFg to SgToneGreenBg
        "PENDING", "DRAFT", "INVITE", "REQUEST", "PAST" -> SgToneAmberFg to SgToneAmberBg
        "NO_SHOW", "CANCELLED", "VOIDED", "REMITTED", "DENIED", "LOCKED" -> SgToneRedFg to SgToneRedBg
        "DUTY", "SESSION", "CLINIC" -> SgToneBlueFg to SgToneBlueBg
        "PRODUCT", "PROVINCIAL_TOUR" -> SgToneVioletFg to SgToneVioletBg
        else -> SgToneGreyFg to SgToneGreyBg
    }

fun columnLetters(count: Int): List<String> {
    val out = ArrayList<String>(count)
    var n = 0
    while (out.size < count) {
        var v = n
        var s = ""
        do {
            s = ('A'.code + (v % 26)).toChar() + s
            v = v / 26 - 1
        } while (v >= 0)
        out.add(s)
        n++
    }
    return out
}

@Composable
fun SgStatusChip(text: String) {
    val (fg, bg) = sgTone(text)
    Box(
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(3.dp))
                .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, fontFamily = SgMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun RowScope.SgHeaderCell(
    label: String,
    weight: Float,
) {
    Box(
        modifier =
            Modifier
                .weight(weight)
                .background(SgHeaderFill)
                .border(0.5.dp, SgGridLine)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            fontFamily = SgMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SgColumnLetters(weights: List<Float>) {
    Row(modifier = Modifier.fillMaxWidth().background(SgHeaderFill)) {
        Box(
            modifier =
                Modifier
                    .width(52.dp)
                    .background(SgRowNumFill)
                    .border(0.5.dp, SgGridLine)
                    .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("", fontSize = 10.sp)
        }
        columnLetters(weights.size).forEachIndexed { i, letter ->
            Box(
                modifier =
                    Modifier
                        .weight(weights[i])
                        .background(SgHeaderFill)
                        .border(0.5.dp, SgGridLine)
                        .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, fontFamily = SgMono, fontSize = 10.sp, color = SgRowNumInk)
            }
        }
    }
}

@Composable
fun RowScope.SgCell(
    text: String,
    weight: Float,
    active: Boolean = false,
    bg: Color = SgPaper,
    fg: Color = SgInk,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Left,
    onClick: (() -> Unit)? = null,
) {
    var mod =
        Modifier
            .weight(weight)
            .background(bg)
            .border(0.5.dp, SgGridLine)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    if (active) mod = mod.border(2.dp, SgActiveBorder)
    Box(
        modifier = mod,
        contentAlignment =
            if (align ==
                TextAlign.Right
            ) {
                Alignment.CenterEnd
            } else {
                Alignment.CenterStart
            },
    ) {
        Text(
            text,
            fontFamily = SgMono,
            fontSize = 12.sp,
            color = fg,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SgRowNum(
    n: Int,
    active: Boolean = false,
) {
    var mod =
        Modifier
            .width(52.dp)
            .background(if (active) SgGreenTint else SgRowNumFill)
            .border(0.5.dp, SgGridLine)
            .padding(vertical = 7.dp)
    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Text(
            "$n",
            fontFamily = SgMono,
            fontSize = 11.sp,
            color = if (active) SgGreenDark else SgRowNumInk,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun SgNote(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgNoteBg)
                .border(0.5.dp, SgNoteLine)
                .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text, fontFamily = SgMono, fontSize = 11.5.sp, color = SgNoteInk)
    }
}

@Composable
fun SgSectionTitle(
    cell: String,
    title: String,
    right: String = "",
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgWash)
                .border(0.5.dp, SgGridLine)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            cell,
            fontFamily = SgMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SgGreenDark,
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SgInk)
        Spacer(Modifier.weight(1f))
        if (right.isNotEmpty()) Text(right, fontFamily = SgMono, fontSize = 11.sp, color = SgMuted)
    }
}

@Composable
fun SgPrimary(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = SgGreen, contentColor = Color.White),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(label, fontFamily = SgMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SgSecondary(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(4.dp)) {
        Text(label, fontFamily = SgMono, fontSize = 12.sp, color = SgGreenDark)
    }
}

@Composable
fun SgLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, fontFamily = SgMono, fontSize = 12.sp, color = SgGreenDark, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ColumnScope.SgSheetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgPaper)
                .border(1.dp, SgGridLine)
                .padding(0.dp),
        content = content,
    )
}

@Composable
fun SgEmptyRow(label: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(SgPaper)
                    .border(0.5.dp, SgGridLine)
                    .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontFamily = SgMono, fontSize = 12.sp, color = SgMuted)
        }
    }
}

@Composable
fun SgStatStrip(cells: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SgPaper).border(0.5.dp, SgGridLine),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        cells.forEach { (k, v) ->
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(v, fontFamily = SgMono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SgInk)
                Text(k, fontFamily = SgMono, fontSize = 10.sp, color = SgMuted)
            }
        }
    }
}
