package com.companyb.companyapp.proto.opsfeed

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #790 — ops-feed dispatch-log theme: paper log sheet, ink type, monospace time rail,
// one colored pip per event domain. Deliberately not Linear: it reads like a radio log.

object OfColors {
    val Paper = Color(0xFFF6F1E5)
    val Card = Color(0xFFFFFDF6)
    val Ink = Color(0xFF20242B)
    val Muted = Color(0xFF7A7466)
    val Line = Color(0xFFDCD4BF)
    val Rail = Color(0xFFC9BFA6)
    val Accent = Color(0xFFB3541E)
    val Session = Color(0xFF2F7D4F)
    val Relief = Color(0xFF6C4FC4)
    val Remit = Color(0xFFB3541E)
    val Audit = Color(0xFF5B6672)
    val Note = Color(0xFF1F6F9B)
    val System = Color(0xFF20242B)
    val Good = Color(0xFF2F7D4F)
    val Warn = Color(0xFF9A6A00)
    val Bad = Color(0xFFB3372F)
}

val OfMono: FontFamily = FontFamily.Monospace

fun OfDomainColor(domain: OfDomain): Color = when (domain) {
    OfDomain.SESSION -> OfColors.Session
    OfDomain.RELIEF -> OfColors.Relief
    OfDomain.REMIT -> OfColors.Remit
    OfDomain.AUDIT -> OfColors.Audit
    OfDomain.NOTE -> OfColors.Note
    OfDomain.SYSTEM -> OfColors.System
}

@Composable
fun OfCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(OfColors.Card, RoundedCornerShape(10.dp))
            .border(1.dp, OfColors.Line, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun OfSectionTitle(text: String) {
    Text(text.uppercase(), color = OfColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun OfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) OfColors.Ink else OfColors.Card
    val fg = if (selected) OfColors.Paper else OfColors.Ink
    Box(
        Modifier.background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) OfColors.Ink else OfColors.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfPrimary(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = OfColors.Ink, contentColor = OfColors.Paper),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfGhost(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = OfColors.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfLink(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(label, color = OfColors.Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfTag(
    text: String,
    color: Color,
) {
    Box(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun OfRowButtons(vararg buttons: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEachIndexed { index, button ->
            if (index > 0) Spacer(Modifier.width(0.dp))
            button()
        }
    }
}
