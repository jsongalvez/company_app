package com.companyb.companyapp.proto.guestpass

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

object GuestPassColors {
    val Paper = Color(0xFFF3EAD3)
    val Stub = Color(0xFFFFFDF4)
    val StubEdge = Color(0xFFE3D5B4)
    val Ink = Color(0xFF2B2118)
    val Muted = Color(0xFF7A6A54)
    val Perfo = Color(0xFFC0AE87)
    val Oxblood = Color(0xFF7A2E2E)
    val OxbloodBg = Color(0xFFF5DEDC)
    val StampGreen = Color(0xFF1D6E3E)
    val StampGreenBg = Color(0xFFDFF0E2)
    val Amber = Color(0xFF8A5E00)
    val AmberBg = Color(0xFFF7E9C4)
    val Plum = Color(0xFF4E2A5E)
    val PlumBg = Color(0xFFEADDF0)
    val Slate = Color(0xFF4A5560)
    val SlateBg = Color(0xFFE4E8EC)
}

@Composable
fun PassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GuestPassColors.Stub)
            .border(1.dp, GuestPassColors.StubEdge, RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
fun SectionTitle(
    title: String,
    note: String? = null,
) {
    Column {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GuestPassColors.Ink)
        if (note != null) {
            Text(note, fontSize = 13.sp, color = GuestPassColors.Muted)
        }
    }
}

enum class StampKind { GREEN, RED, AMBER, PLUM, SLATE }

@Composable
fun StampChip(
    text: String,
    kind: StampKind,
) {
    val (fg, bg) = when (kind) {
        StampKind.GREEN -> GuestPassColors.StampGreen to GuestPassColors.StampGreenBg
        StampKind.RED -> GuestPassColors.Oxblood to GuestPassColors.OxbloodBg
        StampKind.AMBER -> GuestPassColors.Amber to GuestPassColors.AmberBg
        StampKind.PLUM -> GuestPassColors.Plum to GuestPassColors.PlumBg
        StampKind.SLATE -> GuestPassColors.Slate to GuestPassColors.SlateBg
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(2.dp, fg, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = fg,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun Perforation() {
    Text(
        "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
        fontSize = 11.sp,
        color = GuestPassColors.Perfo,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ViewOnlyRibbon(branchName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GuestPassColors.AmberBg)
            .border(1.dp, GuestPassColors.Amber, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StampChip("view only", StampKind.AMBER)
            Spacer(Modifier.width(10.dp))
            Text(
                "Outsider eyes at $branchName — edits need a relief grant.",
                fontSize = 13.sp,
                color = GuestPassColors.Ink,
            )
        }
    }
}

@Composable
fun GrantCelebration(note: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GuestPassColors.StampGreenBg)
            .border(2.dp, GuestPassColors.StampGreen, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StampChip("granted", StampKind.GREEN)
            Spacer(Modifier.width(10.dp))
            Text("★ $note", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GuestPassColors.Ink)
        }
    }
}

@Composable
fun PassButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val bg = if (primary) GuestPassColors.Ink else GuestPassColors.Stub
    val fg = if (primary) Color.White else GuestPassColors.Ink
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else GuestPassColors.SlateBg)
            .border(1.dp, if (enabled) GuestPassColors.Ink else GuestPassColors.Muted, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (enabled) fg else GuestPassColors.Muted)
    }
}

@Composable
fun MonoId(text: String) {
    Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = GuestPassColors.Muted)
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = GuestPassColors.Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GuestPassColors.Ink)
    }
}

@Composable
fun Hairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GuestPassColors.StubEdge))
}
