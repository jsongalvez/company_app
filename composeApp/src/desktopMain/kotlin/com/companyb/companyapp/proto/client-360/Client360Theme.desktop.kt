package com.companyb.companyapp.proto.client360

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #773 — client-360 prototype theme: brass-and-ink client dossier. Index-card
// clients, a stamped record header, and a cross-branch timeline spine.
// Deliberately distinct from Linear and sibling variants.

object C360Colors {
    val Paper = Color(0xFFF3ECDD)
    val Card = Color(0xFFFFFDF6)
    val CardEdge = Color(0xFFDDD0B2)
    val Ink = Color(0xFF26221A)
    val Faded = Color(0xFF8A7E66)
    val Brass = Color(0xFF9A6B0F)
    val BrassDeep = Color(0xFF7A5408)
    val StampRed = Color(0xFFB33A2B)
    val Teal = Color(0xFF22756B)
    val Slate = Color(0xFF6E6A5E)
    val Spine = Color(0xFFC9A227)
    val BannerOpen = Color(0xFFDDEEDD)
    val BannerPast = Color(0xFFF3E4C2)
    val BannerRemitted = Color(0xFFDCE9F5)
    val GuardBg = Color(0xFFF8E7C9)
    val GuardEdge = Color(0xFFD9A441)
}

fun c360Scheme() = lightColorScheme(
    primary = C360Colors.BrassDeep,
    onPrimary = Color.White,
    background = C360Colors.Paper,
    onBackground = C360Colors.Ink,
    surface = C360Colors.Card,
    onSurface = C360Colors.Ink,
    surfaceVariant = C360Colors.CardEdge,
    secondary = C360Colors.Teal,
)

@Composable
fun C360Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = c360Scheme()) {
        content()
    }
}

@Composable
fun C360Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(C360Colors.Card, RoundedCornerShape(10.dp))
            .border(1.dp, C360Colors.CardEdge, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
fun C360Headline(text: String) {
    Text(text, color = C360Colors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
}

@Composable
fun C360Title(text: String) {
    Text(text, color = C360Colors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun C360Eyebrow(text: String) {
    Text(text.uppercase(), color = C360Colors.BrassDeep, fontSize = 11.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun C360Note(text: String) {
    Text(text, color = C360Colors.Faded, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun C360Body(text: String) {
    Text(text, color = C360Colors.Ink, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
fun C360Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = C360Colors.BrassDeep),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun C360Ghost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, fontSize = 13.sp, color = C360Colors.Ink)
    }
}

@Composable
fun C360Link(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 13.sp, color = C360Colors.BrassDeep, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun C360Chip(text: String, active: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = if (active) C360Colors.BrassDeep else C360Colors.Card
    val fg = if (active) Color.White else C360Colors.Ink
    val mod = Modifier.background(bg, RoundedCornerShape(20.dp))
        .border(1.dp, if (active) C360Colors.BrassDeep else C360Colors.CardEdge, RoundedCornerShape(20.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp)
    if (onClick == null) {
        Box(mod) { Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        Box(mod.clickable(onClick = onClick)) {
            Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun C360SealDot(color: Color) {
    Box(Modifier.size(12.dp).background(color, CircleShape).border(1.dp, C360Colors.CardEdge, CircleShape))
}

@Composable
fun C360StatusLine(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        C360SealDot(color)
        Spacer(Modifier.width(8.dp))
        Text(label, color = C360Colors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun C360DayBanner(status: C360DayStatus, branchName: String) {
    val bg = when (status) {
        C360DayStatus.OPEN -> C360Colors.BannerOpen
        C360DayStatus.PAST -> C360Colors.BannerPast
        C360DayStatus.REMITTED -> C360Colors.BannerRemitted
    }
    Row(
        Modifier.fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, C360Colors.CardEdge, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        C360SealDot(
            when (status) {
                C360DayStatus.OPEN -> C360Colors.Teal
                C360DayStatus.PAST -> C360Colors.Brass
                C360DayStatus.REMITTED -> C360Colors.Slate
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$branchName — branch day ${status.name}",
                color = C360Colors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Branch-day boundary 04:00 Asia/Manila. PAST is read-only; REMITTED is sealed by snapshot.",
                color = C360Colors.Faded,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun C360Row(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = C360Colors.Faded, fontSize = 12.sp)
        Text(value, color = C360Colors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
