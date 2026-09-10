package com.companyb.companyapp.proto.ownerdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #819 — owner-desk private-ledger theme: mahogany desk, brass plate, ledger cream,
// felt green for money, oxblood for risk. Serif mastheads, never Linear.

object OdColors {
    val Mahogany = Color(0xFF241A12)
    val DeskEdge = Color(0xFF3A2A1B)
    val Ledger = Color(0xFFF3EBD3)
    val LedgerDim = Color(0xFFE4D7B8)
    val Ink = Color(0xFF2B2118)
    val Faded = Color(0xFF7A6A52)
    val Brass = Color(0xFFC9A227)
    val BrassDeep = Color(0xFF8A6D1A)
    val Felt = Color(0xFF1E4D3A)
    val FeltDeep = Color(0xFF143526)
    val Oxblood = Color(0xFF7A2A22)
    val Seal = Color(0xFF2F6B4F)
}

val OdSerif = FontFamily.Serif

@Composable
fun OdMasthead(title: String, sub: String) {
    Column {
        Text(title, color = OdColors.Brass, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = OdSerif)
        Text(sub, color = OdColors.Faded, fontSize = 12.sp)
    }
}

@Composable
fun OdHeadline(text: String) {
    Text(text, color = OdColors.Ledger, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = OdSerif)
}

@Composable
fun OdNote(text: String) {
    Text(text, color = OdColors.Faded, fontSize = 12.sp)
}

@Composable
fun OdLedgerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OdColors.Ledger, RoundedCornerShape(10.dp))
            .border(1.dp, OdColors.BrassDeep, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun OdLedgerTitle(text: String) {
    Text(text, color = OdColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = OdSerif)
}

@Composable
fun OdLedgerText(text: String) {
    Text(text, color = OdColors.Ink, fontSize = 13.sp)
}

@Composable
fun OdLedgerFaint(text: String) {
    Text(text, color = OdColors.Faded, fontSize = 12.sp)
}

@Composable
fun OdBrassButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = OdColors.Brass, contentColor = OdColors.Mahogany),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun OdGhostButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = OdColors.Ledger, fontSize = 13.sp)
    }
}

@Composable
fun OdLedgerButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = OdColors.Felt, contentColor = Color.White),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun OdLedgerGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, color = OdColors.Ink, fontSize = 13.sp)
    }
}

@Composable
fun OdMoneyCell(label: String, value: String) {
    Column(
        Modifier
            .background(OdColors.FeltDeep, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(label, color = OdColors.Brass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = OdSerif)
    }
}

@Composable
fun OdRiskCell(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OdColors.Oxblood, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!  ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun OdNavItem(label: String, selected: Boolean, badge: String = "", onClick: () -> Unit) {
    val bg = if (selected) OdColors.Brass else Color.Transparent
    val fg = if (selected) OdColors.Mahogany else OdColors.Ledger
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (badge.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(if (selected) OdColors.Mahogany else OdColors.Oxblood, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OdRule() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OdColors.BrassDeep),
    )
}

@Composable
fun RowScope.OdStat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, color = OdColors.Faded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = OdColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = OdSerif)
    }
}
