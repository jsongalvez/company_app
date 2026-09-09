package com.companyb.companyapp.proto.executivebrief

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #770 — executive-brief theme: private-club broadsheet. Deep pine hero band, brass rules,
// cream paper body. Serif posture via heavy weights + wide tracking; distinct from Linear
// and from the paper-daybook / console / TV sibling variants.

object EbColors {
    val Paper = Color(0xFFF4EEDF)
    val Card = Color(0xFFFFFBF0)
    val CardEdge = Color(0xFFDCCFA8)
    val Ink = Color(0xFF1E1B12)
    val Faded = Color(0xFF8A7F66)
    val Pine = Color(0xFF1E3A2C)
    val PineDeep = Color(0xFF142A20)
    val Brass = Color(0xFF9A7414)
    val BrassBright = Color(0xFFD9B44A)
    val Alert = Color(0xFFA63A2B)
    val Sage = Color(0xFF4F7A5B)
    val Slate = Color(0xFF6B6F76)
    val BannerOpen = Color(0xFFDDEBD6)
    val BannerPast = Color(0xFFF3E0BC)
    val BannerRemitted = Color(0xFFD9E6F2)
}

fun ebScheme() = lightColorScheme(
    primary = EbColors.Pine,
    onPrimary = Color.White,
    background = EbColors.Paper,
    onBackground = EbColors.Ink,
    surface = EbColors.Card,
    onSurface = EbColors.Ink,
    surfaceVariant = EbColors.CardEdge,
    secondary = EbColors.Brass,
)

fun EbSessionStatus.dot(): Color = when (this) {
    EbSessionStatus.PENDING -> EbColors.Brass
    EbSessionStatus.COMPLETED -> EbColors.Sage
    EbSessionStatus.NO_SHOW -> EbColors.Alert
    EbSessionStatus.CANCELLED -> EbColors.Slate
}

fun EbDayStatus.chip(): Color = when (this) {
    EbDayStatus.OPEN -> EbColors.BannerOpen
    EbDayStatus.PAST -> EbColors.BannerPast
    EbDayStatus.REMITTED -> EbColors.BannerRemitted
}

@Composable
fun EbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ebScheme()) { content() }
}

@Composable
fun EbCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var mod = Modifier.fillMaxWidth()
        .background(EbColors.Card, RoundedCornerShape(10.dp))
        .border(1.dp, EbColors.CardEdge, RoundedCornerShape(10.dp))
        .padding(14.dp)
    if (onClick != null) mod = mod.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    Box(mod) { content() }
}

@Composable
fun EbKicker(text: String, light: Boolean = false) {
    Text(
        text.uppercase(),
        color = if (light) EbColors.BrassBright else EbColors.Brass,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
    )
}

@Composable
fun EbHeadline(text: String) {
    Text(text, color = EbColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
}

@Composable
fun EbSubhead(text: String) {
    Text(text.uppercase(), color = EbColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun EbNote(text: String) {
    Text(text, color = EbColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun EbPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = EbColors.Pine),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun EbBrass(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = EbColors.Brass),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun EbGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 13.sp, color = EbColors.Ink)
    }
}

@Composable
fun EbLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = EbColors.Pine, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EbRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(EbColors.CardEdge))
}

@Composable
fun EbBar(fraction: Float, color: Color) {
    val f = fraction.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(8.dp).background(EbColors.CardEdge, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(f).height(8.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun EbDayBanner(branch: EbBranch) {
    Row(
        Modifier.fillMaxWidth()
            .background(branch.dayStatus.chip(), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${branch.name} · ${branch.dayStatus.name}",
            color = EbColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Branch day boundary 04:00 Asia/Manila — OPEN stays editable past midnight, then turns PAST lazily.",
            color = EbColors.Ink,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun EbMasthead(edition: String, right: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EbKicker("The owner's morning edition")
                Text("THE MORNING BRIEF", color = EbColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(edition, color = EbColors.Faded, fontSize = 12.sp)
            }
            Text(right, color = EbColors.Brass, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(EbColors.Brass, RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(EbColors.Brass))
    }
}

@Composable
fun EbShellNav(current: EbDest, unread: Int, onPick: (EbDest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EbDest.entries.forEach { d ->
            val selected = d == current
            val badge = if (d == EbDest.MAILBOX && unread > 0) " ($unread)" else ""
            val mod = Modifier.fillMaxWidth()
                .background(
                    if (selected) EbColors.Pine else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onPick(d) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
            Box(mod) {
                Text(
                    d.label + badge,
                    color = if (selected) Color.White else EbColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                )
            }
        }
    }
}
