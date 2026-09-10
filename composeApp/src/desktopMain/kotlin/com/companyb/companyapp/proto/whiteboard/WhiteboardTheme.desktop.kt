package com.companyb.companyapp.proto.whiteboard

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

// #844 — whiteboard theme: marker strokes on warm paper, magnetic index cards,
// washi-tape accents. Standup-ready day layout, deliberately unlike Linear.

object BoardColors {
    val Paper = Color(0xFFFAF6EC)
    val PaperDeep = Color(0xFFF1E9D6)
    val Frame = Color(0xFF3E3A34)
    val FrameSoft = Color(0xFF6E675C)
    val Card = Color(0xFFFFFFFF)
    val CardLine = Color(0xFFD9CFB8)
    val MarkerBlue = Color(0xFF1D4ED8)
    val MarkerRed = Color(0xFFDC2626)
    val MarkerGreen = Color(0xFF15803D)
    val MarkerPurple = Color(0xFF7C3AED)
    val MarkerOrange = Color(0xFFEA580C)
    val MagnetYellow = Color(0xFFFFC93C)
    val MagnetPink = Color(0xFFFF8FAB)
    val MagnetTeal = Color(0xFF2DD4BF)
    val MagnetSky = Color(0xFF7DD3FC)
    val Ink = Color(0xFF292524)
    val InkSoft = Color(0xFF78716C)
    val Tape = Color(0xFFFDE68A)
}

fun BoardDayStatus.accent(): Color = when (this) {
    BoardDayStatus.OPEN -> BoardColors.MarkerGreen
    BoardDayStatus.PAST -> BoardColors.MarkerOrange
    BoardDayStatus.REMITTED -> BoardColors.MarkerBlue
}

fun BoardSessionStatus.accent(): Color = when (this) {
    BoardSessionStatus.PENDING -> BoardColors.MarkerOrange
    BoardSessionStatus.COMPLETED -> BoardColors.MarkerGreen
    BoardSessionStatus.NO_SHOW -> BoardColors.MarkerRed
    BoardSessionStatus.CANCELLED -> BoardColors.InkSoft
}

fun BoardSessionStatus.label(): String = when (this) {
    BoardSessionStatus.PENDING -> "PENDING"
    BoardSessionStatus.COMPLETED -> "COMPLETED"
    BoardSessionStatus.NO_SHOW -> "NO_SHOW"
    BoardSessionStatus.CANCELLED -> "CANCELLED"
}

@Composable
fun BoardPanel(
    modifier: Modifier = Modifier,
    tape: Color = BoardColors.Tape,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BoardColors.Card)
                .border(2.dp, BoardColors.Frame, RoundedCornerShape(14.dp))
                .padding(top = 14.dp, start = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
            content()
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tape.copy(alpha = 0.85f))
                .padding(horizontal = 26.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun MagnetCard(
    magnet: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var card = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(BoardColors.Card)
        .border(2.dp, BoardColors.Frame, RoundedCornerShape(12.dp))
        .padding(14.dp)
    if (onClick != null) {
        card = card.clickable(onClick = onClick)
    }
    Column(modifier.then(card)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(magnet)
                    .border(2.dp, BoardColors.Frame, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
fun MarkerTitle(text: String, color: Color = BoardColors.MarkerBlue) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 22.sp,
        fontWeight = FontWeight.Black,
    )
    Box(
        Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .height(4.dp)
            .fillMaxWidth(0.22f),
    ) {}
}

@Composable
fun BoardNote(text: String) {
    Text(text, color = BoardColors.InkSoft, fontSize = 13.sp)
}

@Composable
fun BoardChip(
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    var chip = Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(color.copy(alpha = 0.16f))
        .border(1.dp, color, RoundedCornerShape(20.dp))
        .padding(horizontal = 12.dp, vertical = 5.dp)
    if (onClick != null) {
        chip = chip.clickable(onClick = onClick)
    }
    Box(chip) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoardCta(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = BoardColors.Frame,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoardGhost(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoardLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, color = BoardColors.MarkerBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoardSectionRow(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = BoardColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            action()
        }
    }
}

@Composable
fun BoardTabPills(
    tabs: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) BoardColors.Frame else Color.Transparent)
                    .border(2.dp, BoardColors.Frame, RoundedCornerShape(20.dp))
                    .clickable { onPick(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    color = if (active) Color.White else BoardColors.Frame,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
