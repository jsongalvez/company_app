package com.companyb.companyapp.proto.departuresboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

// #830 — departures-board theme: station split-flap board, near-black canvas,
// amber flip glyphs, platform flags. Deliberately distinct from wallboard TV console.

object BoardColors {
    val Yard = Color(0xFF0C0E08)
    val YardDeep = Color(0xFF070805)
    val Flap = Color(0xFF181B12)
    val FlapLine = Color(0xFF33381F)
    val Panel = Color(0xFF14170E)
    val PanelLine = Color(0xFF33381F)
    val Amber = Color(0xFFFFB300)
    val AmberInk = Color(0xFF241500)
    val Cream = Color(0xFFF2EAD8)
    val CreamDim = Color(0xFFB9AE94)
    val Faint = Color(0xFF8A8268)
    val Ink = Color(0xFF1A1C12)
    val Green = Color(0xFF3DDC84)
    val Red = Color(0xFFFF5252)
    val Sky = Color(0xFF7CC4FF)
    val Violet = Color(0xFFB39DDB)
}

fun BoardDayStatus.band(): Color = when (this) {
    BoardDayStatus.OPEN -> BoardColors.Green
    BoardDayStatus.PAST -> BoardColors.Amber
    BoardDayStatus.REMITTED -> BoardColors.Sky
}

fun BoardSessionStatus.flag(): Color = when (this) {
    BoardSessionStatus.PENDING -> BoardColors.Amber
    BoardSessionStatus.COMPLETED -> BoardColors.Green
    BoardSessionStatus.NO_SHOW -> BoardColors.Violet
    BoardSessionStatus.CANCELLED -> BoardColors.Red
}

fun BoardSessionStatus.label(): String = when (this) {
    BoardSessionStatus.PENDING -> "BOARDING"
    BoardSessionStatus.COMPLETED -> "DEPARTED"
    BoardSessionStatus.NO_SHOW -> "NO SHOW"
    BoardSessionStatus.CANCELLED -> "CANCELLED"
}

@Composable
fun BoardPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BoardColors.Panel)
            .border(1.dp, BoardColors.PanelLine, RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        content()
    }
}

@Composable
fun BoardHeading(
    text: String,
    size: Int = 28,
) {
    Text(
        text,
        color = BoardColors.Cream,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun BoardSub(
    text: String,
    size: Int = 16,
) {
    Text(
        text,
        color = BoardColors.CreamDim,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun BoardNoteCard(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BoardColors.YardDeep)
            .border(1.dp, BoardColors.PanelLine, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(text, color = BoardColors.Faint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BoardFlagChip(
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    selected: Boolean = true,
) {
    val bg = if (selected) color else BoardColors.YardDeep
    val fg = if (selected) BoardColors.YardDeep else color
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun BoardFlapCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BoardColors.Flap)
            .border(1.dp, BoardColors.FlapLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = BoardColors.Amber,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun BoardStationLabel(text: String) {
    Text(
        text.uppercase(),
        color = BoardColors.Amber,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun BoardPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = BoardColors.Amber,
            contentColor = BoardColors.AmberInk,
            disabledContainerColor = BoardColors.Flap,
            disabledContentColor = BoardColors.Faint,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

@Composable
fun BoardGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(text, color = BoardColors.Cream, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun BoardLinkButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, color = BoardColors.Sky, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun BoardRowActions(
    vararg pairs: Pair<String, () -> Unit>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pairs.forEach { (label, action) ->
            BoardGhostButton(text = label, onClick = action)
        }
    }
}
