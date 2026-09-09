package com.companyb.companyapp.proto.inventorycounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

// #774 — stock-counter theme: warehouse stencil ledger. Kraft shelves, ink stencil
// headers, safety-orange low-stock flags, barcode chips. Deliberately distinct
// from Linear and every sibling prototype: inventory first, sessions second.

object IcColors {
    val Kraft = Color(0xFFEFE6D2)
    val Shelf = Color(0xFFF8F2E4)
    val Card = Color(0xFFFFFBF0)
    val CardEdge = Color(0xFFD8C9A6)
    val Ink = Color(0xFF23201A)
    val Stencil = Color(0xFF4A4436)
    val Faded = Color(0xFF93876C)
    val Safety = Color(0xFFD9531E)
    val SafetyWash = Color(0xFFF9DCC8)
    val Moss = Color(0xFF3E6B34)
    val MossWash = Color(0xFFDCE8D2)
    val Steel = Color(0xFF3D5A73)
    val SteelWash = Color(0xFFD5E3EE)
    val Plum = Color(0xFF7A3B5D)
    val Slate = Color(0xFF6B6F76)
    val Counted = Color(0xFF2E7D32)
    val BannerOpen = Color(0xFFDCE8D2)
    val BannerPast = Color(0xFFF3E4C2)
    val BannerRemitted = Color(0xFFD5E3EE)
}

fun icScheme() = lightColorScheme(
    primary = IcColors.Moss,
    onPrimary = Color.White,
    background = IcColors.Kraft,
    onBackground = IcColors.Ink,
    surface = IcColors.Card,
    onSurface = IcColors.Ink,
    surfaceVariant = IcColors.CardEdge,
    secondary = IcColors.Safety,
)

fun IcSessionStatus.dot(): Color = when (this) {
    IcSessionStatus.PENDING -> IcColors.Safety
    IcSessionStatus.COMPLETED -> IcColors.Moss
    IcSessionStatus.NO_SHOW -> IcColors.Plum
    IcSessionStatus.CANCELLED -> IcColors.Slate
}

fun IcDayStatus.wash(): Color = when (this) {
    IcDayStatus.OPEN -> IcColors.BannerOpen
    IcDayStatus.PAST -> IcColors.BannerPast
    IcDayStatus.REMITTED -> IcColors.BannerRemitted
}

@Composable
fun IcCard(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(IcColors.Card, RoundedCornerShape(8.dp))
            .border(1.dp, IcColors.CardEdge, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun IcKicker(text: String) {
    Text(text.uppercase(), color = IcColors.Stencil, fontSize = 12.sp, fontWeight = FontWeight.Black)
}

@Composable
fun IcHeadline(text: String) {
    Text(text, color = IcColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
}

@Composable
fun IcNote(text: String) {
    Text(text, color = IcColors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

// SKU barcode chip: monospace-ish tag identifying a stock row everywhere.
@Composable
fun IcSku(sku: String) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(IcColors.Ink)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("▮▮▮ $sku", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IcFlag(text: String, color: Color, wash: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(wash)
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun IcPrimary(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = IcColors.Moss),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IcDanger(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = IcColors.Safety),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IcGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = IcColors.Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun IcLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = IcColors.Steel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// Ledger row: label left, value right, hairline below — the unit x quantity math rail.
@Composable
fun IcLedger(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = IcColors.Stencil, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = IcColors.Ink,
            fontSize = 13.sp,
            fontWeight = if (strong) FontWeight.Black else FontWeight.Normal,
        )
    }
}

// Clickable nav tile for the stock-counter home shelf.
@Composable
fun RowScope.IcTile(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) IcColors.Ink else IcColors.Card
    val fg = if (selected) Color.White else IcColors.Ink
    Column(
        Modifier.weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (selected) IcColors.Ink else IcColors.CardEdge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(sub, color = if (selected) IcColors.Kraft else IcColors.Faded, fontSize = 11.sp)
    }
}

@Composable
fun IcSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IcKicker(title)
        content()
    }
}

@Composable
fun IcSpacer() {
    Spacer(Modifier.height(4.dp))
}

@Composable
fun IcWideRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun IcThemeWrap(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = icScheme()) {
        Box(Modifier.fillMaxSize().background(IcColors.Kraft)) { content() }
    }
}

@Composable
fun IcScreenTitle() {
    Text(
        "STOCK COUNTER",
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun IcGap() {
    Spacer(Modifier.width(8.dp))
}
