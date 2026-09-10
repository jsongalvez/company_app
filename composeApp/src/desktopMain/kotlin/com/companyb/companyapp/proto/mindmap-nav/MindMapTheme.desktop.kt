package com.companyb.companyapp.proto.mindmapnav

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Abyss = Color(0xFF12101D)
val Panel = Color(0xFF1B1830)
val PanelEdge = Color(0xFF37325C)
val NodeInk = Color(0xFFEDEBFF)
val NodeDim = Color(0xFFA9A3D4)
val Vine = Color(0xFF6F66B8)
val Leaf = Color(0xFF7DD87D)
val Brook = Color(0xFF6EC1FF)
val Bloom = Color(0xFFF2B8E0)
val Honey = Color(0xFFFFD166)
val Moss = Color(0xFFB8A6FF)
val Coral = Color(0xFFFF9E7D)
val Mist = Color(0xFF8AD8D8)
val Chalk = Color(0xFFE8E6DF)

val MapSans = FontFamily.SansSerif
val MapMono = FontFamily.Monospace

private val MapScheme =
    darkColorScheme(
        primary = Moss,
        onPrimary = Abyss,
        secondary = Brook,
        onSecondary = Abyss,
        background = Abyss,
        onBackground = NodeInk,
        surface = Panel,
        onSurface = NodeInk,
        surfaceVariant = Panel,
        onSurfaceVariant = NodeDim,
        outline = PanelEdge,
        outlineVariant = PanelEdge,
    )

@Composable
fun MindMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MapScheme, content = content)
}

fun MapDay.ring(): Color =
    when (this) {
        MapDay.OPEN -> Leaf
        MapDay.PAST -> Honey
        MapDay.REMITTED -> Moss
    }

fun MapStatus.tint(): Color =
    when (this) {
        MapStatus.PENDING -> Honey
        MapStatus.COMPLETED -> Leaf
        MapStatus.NO_SHOW -> Coral
        MapStatus.CANCELLED -> NodeDim
    }

@Composable
fun MapButton(
    text: String,
    onClick: () -> Unit,
    accent: Color = Moss,
) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(accent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = MapSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Abyss)
    }
}

@Composable
fun MapGhost(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, PanelEdge, RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = MapSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NodeInk)
    }
}

@Composable
fun MapChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    accent: Color = Moss,
) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(if (on) accent else Color.Transparent)
                .border(1.dp, if (on) accent else PanelEdge, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = MapSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (on) Abyss else NodeDim,
        )
    }
}

@Composable
fun MapField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontFamily = MapSans, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = NodeInk,
                unfocusedTextColor = NodeInk,
                focusedBorderColor = Moss,
                unfocusedBorderColor = PanelEdge,
                focusedLabelColor = Moss,
                unfocusedLabelColor = NodeDim,
                cursorColor = Moss,
            ),
    )
}

@Composable
fun NoteCard(text: String, accent: Color = Vine) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, accent, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(text, fontFamily = MapSans, fontSize = 12.sp, lineHeight = 17.sp, color = NodeInk)
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    accent: Color,
) {
    Column(
        modifier = Modifier.width(148.dp).clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, PanelEdge, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        Text(value, fontFamily = MapMono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
        Text(label.uppercase(), fontFamily = MapSans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = NodeDim)
    }
}

@Composable
fun DetailScroll(
    title: String,
    crumb: String,
    onZoomOut: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(crumb, fontFamily = MapMono, fontSize = 11.sp, color = NodeDim, modifier = Modifier.weight(1f))
            MapGhost("⟨ zoom out", onClick = onZoomOut)
        }
        Text(title, fontFamily = MapSans, fontWeight = FontWeight.Black, fontSize = 26.sp, color = NodeInk)
        content()
    }
}

@Composable
fun RowScope.CardBtn(
    text: String,
    onClick: () -> Unit,
    weight: Float = 1f,
) {
    Box(
        modifier = Modifier.weight(weight).clip(RoundedCornerShape(10.dp)).border(1.dp, PanelEdge, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = MapSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = NodeInk)
    }
}

@Composable
fun Gap(h: Int = 4) {
    Spacer(Modifier.height(h.dp))
}
