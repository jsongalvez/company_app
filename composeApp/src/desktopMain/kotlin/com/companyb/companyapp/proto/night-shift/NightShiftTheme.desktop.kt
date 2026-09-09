package com.companyb.companyapp.proto.nightshift

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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #780 — night-shift prototype theme: true-black OLED night mode for overnight
// crews. Warm red-shift palette (no blue light), dimmable serif numerals, quiet
// low-contrast alerts. Deliberately distinct from Linear and every sibling.

object NsColors {
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF0D0B09)
    val PanelSoft = Color(0xFF141109)
    val Edge = Color(0xFF2E2417)
    val Ember = Color(0xFFE8A33D)
    val EmberDeep = Color(0xFF9A6420)
    val Glow = Color(0xFFFFE9D6)
    val Rose = Color(0xFFD97B6C)
    val Sage = Color(0xFF9DBE8C)
    val Blush = Color(0xFFC98A8A)
    val Taupe = Color(0xFF8A7B6C)
    val Faint = Color(0xFF4A4036)
    val Quiet = Color(0xFF6E6257)
}

val NsSerif = FontFamily.Serif
val NsSans = FontFamily.SansSerif

fun nsScheme() = darkColorScheme(
    primary = NsColors.Ember,
    onPrimary = NsColors.Black,
    background = NsColors.Black,
    onBackground = NsColors.Glow,
    surface = NsColors.Panel,
    onSurface = NsColors.Glow,
    surfaceVariant = NsColors.Edge,
    secondary = NsColors.Rose,
    error = NsColors.Rose,
)

@Composable
fun NsRoot(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = nsScheme()) {
        Box(Modifier.fillMaxSize().background(NsColors.Black)) {
            content()
        }
    }
}

@Composable
fun NsText(
    text: String,
    size: Int = 13,
    weight: FontWeight = FontWeight.Normal,
    color: Color = NsColors.Glow,
    modifier: Modifier = Modifier,
) {
    Text(text, fontSize = size.sp, fontWeight = weight, color = color, fontFamily = NsSans, modifier = modifier)
}

@Composable
fun NsTitle(text: String) {
    Text(
        text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = NsColors.Glow,
        fontFamily = NsSerif,
    )
}

@Composable
fun NsNote(text: String) {
    Text(text, fontSize = 12.sp, color = NsColors.Taupe, fontFamily = NsSans)
}

// Dimmable numeral: alpha follows the crew dimmer so overnight eyes stay fresh.
@Composable
fun NsNumeral(text: String, dim: Int, size: Int = 44) {
    val glow = when (dim) {
        0 -> 0.32f
        1 -> 0.55f
        2 -> 0.8f
        else -> 1.0f
    }
    Text(
        text,
        fontSize = size.sp,
        fontWeight = FontWeight.Light,
        color = NsColors.Glow,
        fontFamily = NsSerif,
        modifier = Modifier.alpha(glow),
    )
}

@Composable
fun NsRule() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(NsColors.Edge))
}

@Composable
fun NsPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .border(1.dp, NsColors.Edge, RoundedCornerShape(14.dp))
            .background(NsColors.Panel, RoundedCornerShape(14.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun NsTag(text: String, color: Color) {
    Box(
        Modifier
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .background(Color.Transparent, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = NsSans)
    }
}

// Quiet alert: deliberately low-contrast — overnight crews are never shouted at.
@Composable
fun NsQuietAlert(text: String, dimmed: Boolean) {
    val color = if (dimmed) NsColors.Quiet else NsColors.Rose
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, NsColors.Edge, RoundedCornerShape(12.dp))
            .background(NsColors.PanelSoft, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = color, fontFamily = NsSans)
    }
}

@Composable
fun NsButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = NsColors.Ember,
            contentColor = NsColors.Black,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontFamily = NsSans)
    }
}

@Composable
fun NsGhost(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(1.dp, NsColors.EmberDeep),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, color = NsColors.Ember, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NsLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = NsColors.Taupe, fontSize = 12.sp)
    }
}

@Composable
fun NsRailButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) NsColors.Ember else Color.Transparent
    val fg = if (selected) NsColors.Black else NsColors.Taupe
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(1.dp, if (selected) NsColors.Ember else NsColors.Edge, RoundedCornerShape(10.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
            fontFamily = NsSans,
        )
    }
}

@Composable
fun NsRowButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

fun nsStatusColor(status: String): Color = when (status) {
    "PENDING" -> NsColors.Ember
    "COMPLETED" -> NsColors.Sage
    "NO_SHOW" -> NsColors.Blush
    "CANCELLED" -> NsColors.Taupe
    "OPEN" -> NsColors.Ember
    "PAST" -> NsColors.Taupe
    "REMITTED" -> NsColors.Sage
    "DRAFT" -> NsColors.Ember
    "SUBMITTED" -> NsColors.Rose
    "SEALED" -> NsColors.Sage
    else -> NsColors.Glow
}
