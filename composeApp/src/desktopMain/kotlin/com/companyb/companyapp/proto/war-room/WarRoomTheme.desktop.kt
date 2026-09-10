package com.companyb.companyapp.proto.warroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #836 — war-room theme: exception-only incident room. Near-black bunker canvas,
// siren red for voids, amber for variances, violet for undo windows. No Linear cards.

object WarColors {
    val Bunker = Color(0xFF0B0B0E)
    val BunkerDeep = Color(0xFF060608)
    val Panel = Color(0xFF16151C)
    val PanelLine = Color(0xFF2E2B3A)
    val Siren = Color(0xFFFF3B5C)
    val SirenDim = Color(0xFF5A1622)
    val Amber = Color(0xFFFFB020)
    val AmberInk = Color(0xFF2A1A00)
    val Violet = Color(0xFFB388FF)
    val VioletDim = Color(0xFF2C2148)
    val Ok = Color(0xFF2FD08C)
    val Sky = Color(0xFF7CC4FF)
    val Ink = Color(0xFFF4F1EA)
    val InkDim = Color(0xFFC9C3B4)
    val Faint = Color(0xFF8E8A9B)
    val Quiet = Color(0xFF1D1C24)
}

fun WarDayStatus.band(): Color = when (this) {
    WarDayStatus.OPEN -> WarColors.Ok
    WarDayStatus.PAST -> WarColors.Amber
    WarDayStatus.REMITTED -> WarColors.Sky
}

fun WarSessionStatus.siren(): Color = when (this) {
    WarSessionStatus.PENDING -> WarColors.Amber
    WarSessionStatus.COMPLETED -> WarColors.Ok
    WarSessionStatus.NO_SHOW -> WarColors.Violet
    WarSessionStatus.CANCELLED -> WarColors.Siren
}

fun WarSessionStatus.tag(): String = when (this) {
    WarSessionStatus.PENDING -> "LIVE RISK"
    WarSessionStatus.COMPLETED -> "CLEARED"
    WarSessionStatus.NO_SHOW -> "NO SHOW"
    WarSessionStatus.CANCELLED -> "KILLED"
}

@Composable
fun WarPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WarColors.Panel)
            .border(1.dp, accent ?: WarColors.PanelLine, RoundedCornerShape(12.dp))
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun WarKicker(text: String) {
    Text(
        text.uppercase(),
        color = WarColors.Siren,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun WarHeading(text: String) {
    Text(text, color = WarColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
}

@Composable
fun WarSub(text: String) {
    Text(text, color = WarColors.InkDim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun WarNoteCard(text: String, tone: Color = WarColors.Amber) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WarColors.Quiet)
            .border(1.dp, tone.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp).background(tone).padding(vertical = 14.dp, horizontal = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = WarColors.InkDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WarChip(text: String, color: Color, filled: Boolean = true) {
    val bg = if (filled) color else Color.Transparent
    val fg = if (filled) {
        if (color == WarColors.Amber) WarColors.AmberInk else WarColors.BunkerDeep
    } else {
        color
    }
    Text(
        text.uppercase(),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, color, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun WarSirenCell(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WarColors.SirenDim)
            .border(1.dp, WarColors.Siren, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = WarColors.Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun WarPrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = WarColors.Siren, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun WarGhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarColors.Ink),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun WarLinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text.uppercase(), color = WarColors.Sky, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun WarTriageRow(label: String, count: String, tone: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WarColors.BunkerDeep)
            .border(1.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            count,
            color = tone,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(52.dp),
        )
        Text(label.uppercase(), color = WarColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Text("OPEN >", color = tone, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun RowScope.WarStatCell(label: String, value: String, tone: Color) {
    Column(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(WarColors.BunkerDeep)
            .border(1.dp, WarColors.PanelLine, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = tone, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label.uppercase(), color = WarColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WarExceptionHeader(title: String, blurb: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WarKicker("exception ● $title")
        WarHeading(blurb)
        Spacer(Modifier.height(2.dp))
    }
}
