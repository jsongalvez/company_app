package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #818 — owner-desk theme: private walnut-and-brass study. Dark wood desk,
// engraved brass nameplate, cream ledger paper, oxblood risk ink. Deliberately
// distinct from every other desktop variant: a single owner's desk, not a shop floor.

object OwnerColors {
    val DeskTop = Color(0xFF2A1D12)
    val DeskBottom = Color(0xFF17100A)
    val Rail = Color(0xFF241811)
    val RailEdge = Color(0xFF4A3421)
    val Nameplate = Color(0xFFC9A227)
    val NameplateInk = Color(0xFF2A1D12)
    val Paper = Color(0xFFF7F0DC)
    val PaperEdge = Color(0xFFD9C89A)
    val PaperRule = Color(0xFFE4D5AE)
    val Ink = Color(0xFF2B2118)
    val InkSoft = Color(0xFF7A6A52)
    val Brass = Color(0xFFB98A1D)
    val BrassDeep = Color(0xFF8A6410)
    val Money = Color(0xFF2E6B34)
    val MoneyBg = Color(0xFFDCEBD9)
    val People = Color(0xFF4A5FA5)
    val PeopleBg = Color(0xFFDDE3F5)
    val Risk = Color(0xFF8C1D18)
    val RiskBg = Color(0xFFF3D9D7)
    val Seal = Color(0xFF8C1D18)
    val Mint = Color(0xFF2E6B34)
    val Sky = Color(0xFF2F7FD1)
    val Sunny = Color(0xFFB98A1D)
}

fun OwnerDayStatus.ribbon(): Color =
    when (this) {
        OwnerDayStatus.OPEN -> OwnerColors.Money
        OwnerDayStatus.PAST -> OwnerColors.BrassDeep
        OwnerDayStatus.REMITTED -> OwnerColors.Sky
    }

fun OwnerSessionStatus.dot(): Color =
    when (this) {
        OwnerSessionStatus.PENDING -> OwnerColors.Brass
        OwnerSessionStatus.COMPLETED -> OwnerColors.Money
        OwnerSessionStatus.NO_SHOW -> OwnerColors.Risk
        OwnerSessionStatus.CANCELLED -> OwnerColors.InkSoft
    }

val OwnerSerif = FontFamily.Serif

@Composable
fun OwnerLedger(
    title: String,
    subtitle: String? = null,
    seal: String? = null,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(OwnerColors.Paper)
            .border(1.dp, OwnerColors.PaperEdge, RoundedCornerShape(8.dp)),
    ) {
        Spacer(
            Modifier
                .width(10.dp)
                .background(OwnerColors.Brass),
        )
        Column(Modifier.weight(1f).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title.uppercase(),
                        color = OwnerColors.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = OwnerSerif,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = OwnerColors.InkSoft,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (seal != null) {
                    Text(
                        seal,
                        color = OwnerColors.Seal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .border(2.dp, OwnerColors.Seal, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(OwnerColors.PaperRule))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun OwnerHeading(
    text: String,
    size: Int = 34,
    color: Color = OwnerColors.Paper,
) {
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontWeight = FontWeight.Black,
        fontFamily = OwnerSerif,
    )
}

@Composable
fun OwnerSub(text: String) {
    Text(
        text,
        color = OwnerColors.Paper.copy(alpha = 0.72f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun OwnerInkSub(text: String) {
    Text(
        text,
        color = OwnerColors.InkSoft,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun OwnerNoteCard(text: String) {
    Text(
        text,
        color = OwnerColors.InkSoft,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OwnerColors.Paper.copy(alpha = 0.12f))
            .border(1.dp, OwnerColors.Brass.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(14.dp),
    )
}

@Composable
fun OwnerPaperNote(text: String) {
    Text(
        text,
        color = OwnerColors.InkSoft,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, OwnerColors.PaperEdge, RoundedCornerShape(8.dp))
            .padding(14.dp),
    )
}

@Composable
fun OwnerBigButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = OwnerColors.Brass,
                contentColor = OwnerColors.NameplateInk,
                disabledContainerColor = OwnerColors.PaperEdge,
                disabledContentColor = OwnerColors.InkSoft,
            ),
    ) {
        Text(label.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = OwnerSerif)
    }
}

@Composable
fun OwnerGhostButton(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = OwnerColors.Nameplate,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, OwnerColors.Brass, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

@Composable
fun OwnerPickRow(
    title: String,
    subtitle: String,
    picked: Boolean,
    onPick: () -> Unit,
    trailing: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (picked) OwnerColors.Ink else Color.White.copy(alpha = 0.92f))
            .border(1.dp, if (picked) OwnerColors.Brass else OwnerColors.PaperEdge, RoundedCornerShape(8.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) OwnerColors.Paper else OwnerColors.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                color = if (picked) OwnerColors.Paper.copy(alpha = 0.75f) else OwnerColors.InkSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            trailing,
            color = if (picked) OwnerColors.Nameplate else OwnerColors.BrassDeep,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .border(1.dp, if (picked) OwnerColors.Brass else OwnerColors.PaperEdge, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun OwnerLaneStat(
    label: String,
    value: String,
    tint: Color,
    bg: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = tint, fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = OwnerSerif)
        Text(label.uppercase(), color = OwnerColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}
