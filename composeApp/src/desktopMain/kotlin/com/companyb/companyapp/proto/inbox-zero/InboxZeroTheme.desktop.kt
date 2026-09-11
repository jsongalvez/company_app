package com.companyb.companyapp.proto.inboxzero

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #856 — inbox-zero dawn-paper theme. Morning light: warm paper, ink text, a
// single sunrise-orange triage accent, sage for done. Scraps Linear chrome.

object IzColors {
    val Paper = Color(0xFFFAF6EE)
    val Panel = Color(0xFFFFFFFF)
    val Wash = Color(0xFFF1E9D8)
    val Ink = Color(0xFF2B2620)
    val Dim = Color(0xFF7A6F60)
    val Faint = Color(0xFFB3A892)
    val Line = Color(0xFFE3D7C0)
    val Dawn = Color(0xFFE86A22)
    val DawnDeep = Color(0xFFB34A10)
    val DawnWash = Color(0xFFFFE8D6)
    val Sage = Color(0xFF3E7C4F)
    val SageWash = Color(0xFFE2F0E4)
    val Sky = Color(0xFF2F6F9E)
    val SkyWash = Color(0xFFDFEDF7)
    val Plum = Color(0xFF7A4E9E)
    val PlumWash = Color(0xFFEADFF5)
    val Brick = Color(0xFFB23A2E)
    val BrickWash = Color(0xFFF8DFDB)
    val Gold = Color(0xFF9A7418)
    val GoldWash = Color(0xFFF6EBCB)
}

enum class IzTone { ORANGE, GREEN, BLUE, VIOLET, RED, GOLD, GREY }

@Composable
fun IzPill(text: String, tone: IzTone) {
    val (bg, fg) = when (tone) {
        IzTone.ORANGE -> IzColors.DawnWash to IzColors.DawnDeep
        IzTone.GREEN -> IzColors.SageWash to IzColors.Sage
        IzTone.BLUE -> IzColors.SkyWash to IzColors.Sky
        IzTone.VIOLET -> IzColors.PlumWash to IzColors.Plum
        IzTone.RED -> IzColors.BrickWash to IzColors.Brick
        IzTone.GOLD -> IzColors.GoldWash to IzColors.Gold
        IzTone.GREY -> IzColors.Wash to IzColors.Dim
    }
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, fontFamily = FontFamily.SansSerif)
    }
}

fun IzSessionStatus.tone(): IzTone = when (this) {
    IzSessionStatus.PENDING -> IzTone.ORANGE
    IzSessionStatus.COMPLETED -> IzTone.GREEN
    IzSessionStatus.NO_SHOW -> IzTone.GOLD
    IzSessionStatus.CANCELLED -> IzTone.GREY
}

fun IzDayState.tone(): IzTone = when (this) {
    IzDayState.OPEN -> IzTone.GREEN
    IzDayState.PAST -> IzTone.GOLD
    IzDayState.REMITTED -> IzTone.VIOLET
}

@Composable
fun IzChip(label: String, onClick: () -> Unit, primary: Boolean = false) {
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = IzColors.Dawn, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
            Text(label, fontSize = 12.sp, color = IzColors.Ink)
        }
    }
}

@Composable
fun IzTab(label: String, count: String? = null, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) IzColors.Ink else Color.Transparent
    val fg = if (active) Color.White else IzColors.Dim
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            if (count == null) label else "$label · $count",
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = fg,
        )
    }
}

@Composable
fun IzCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IzColors.Panel)
            .border(1.dp, IzColors.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column { content() }
    }
}

@Composable
fun IzSection(title: String, note: String? = null, actions: @Composable RowScope.() -> Unit = {}, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = IzColors.Ink)
            Spacer(Modifier.width(8.dp))
            if (note != null) Text(note, fontSize = 11.sp, color = IzColors.Faint)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.height(8.dp))
        body()
    }
}

@Composable
fun IzLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(IzColors.Line))
}

@Composable
fun IzKey(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, color = IzColors.Faint, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 12.sp, color = IzColors.Ink)
    }
}
