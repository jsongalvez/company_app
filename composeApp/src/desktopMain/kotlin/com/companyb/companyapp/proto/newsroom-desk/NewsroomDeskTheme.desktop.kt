package com.companyb.companyapp.proto.newsroomdesk

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

// #833 — newsroom-desk theme: day-budget newsprint, paper canvas, ink rules,
// red pencil + copy-desk blue. Deliberately distinct from station/flip-board variants.

object DeskColors {
    val Paper = Color(0xFFF4F1E8)
    val PaperDeep = Color(0xFFE9E3D2)
    val Card = Color(0xFFFFFEF9)
    val CardLine = Color(0xFFD8D2BE)
    val Ink = Color(0xFF1A1A18)
    val InkSoft = Color(0xFF4A463C)
    val Faint = Color(0xFF8A8471)
    val Rule = Color(0xFFC9C2A8)
    val Pencil = Color(0xFFC1272D)
    val CopyBlue = Color(0xFF1D4ED8)
    val Highlighter = Color(0xFFFFD400)
    val Filed = Color(0xFF1B7A3D)
    val Sky = Color(0xFF0369A1)
    val Violet = Color(0xFF7C3AED)
}

fun DeskDayStatus.band(): Color = when (this) {
    DeskDayStatus.OPEN -> DeskColors.Filed
    DeskDayStatus.PAST -> DeskColors.Pencil
    DeskDayStatus.REMITTED -> DeskColors.Sky
}

fun DeskSessionStatus.flag(): Color = when (this) {
    DeskSessionStatus.PENDING -> DeskColors.CopyBlue
    DeskSessionStatus.COMPLETED -> DeskColors.Filed
    DeskSessionStatus.NO_SHOW -> DeskColors.Violet
    DeskSessionStatus.CANCELLED -> DeskColors.Pencil
}

fun DeskSessionStatus.label(): String = when (this) {
    DeskSessionStatus.PENDING -> "ON BUDGET"
    DeskSessionStatus.COMPLETED -> "FILED"
    DeskSessionStatus.NO_SHOW -> "NO SHOW"
    DeskSessionStatus.CANCELLED -> "SPIKED"
}

@Composable
fun DeskPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DeskColors.Card)
            .border(1.dp, DeskColors.CardLine, RoundedCornerShape(10.dp))
            .padding(20.dp),
    ) {
        content()
    }
}

@Composable
fun DeskHeading(
    text: String,
    size: Int = 28,
) {
    Text(
        text,
        color = DeskColors.Ink,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun DeskSub(
    text: String,
    size: Int = 16,
) {
    Text(
        text,
        color = DeskColors.InkSoft,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun DeskNoteCard(text: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeskColors.PaperDeep)
            .border(1.dp, DeskColors.Rule, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text, color = DeskColors.InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DeskFlagChip(
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    selected: Boolean = true,
) {
    val bg = if (selected) color else DeskColors.Card
    val fg = if (selected) {
        if (color == DeskColors.Highlighter) DeskColors.Ink else Color.White
    } else {
        color
    }
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun DeskSlugCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DeskColors.Ink)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = DeskColors.Highlighter,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun DeskSectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.background(DeskColors.Pencil).padding(horizontal = 5.dp, vertical = 8.dp)) {
        }
        Text(
            text.uppercase(),
            color = DeskColors.Pencil,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun DeskPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = DeskColors.Ink,
            contentColor = DeskColors.Paper,
            disabledContainerColor = DeskColors.Rule,
            disabledContentColor = DeskColors.Faint,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

@Composable
fun DeskGhostButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(text, color = DeskColors.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun DeskLinkButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, color = DeskColors.CopyBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun DeskRowActions(
    vararg pairs: Pair<String, () -> Unit>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        pairs.forEach { (label, action) ->
            DeskGhostButton(text = label, onClick = action)
        }
    }
}
