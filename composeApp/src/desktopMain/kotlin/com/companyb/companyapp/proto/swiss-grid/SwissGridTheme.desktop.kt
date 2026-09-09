package com.companyb.companyapp.proto.swissgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #793 — swiss-grid purist theme: paper white, black rules, one red.
// Helvetica-voice = system sans, uppercase micro-labels, large numerals, whitespace.

val SgPaper = Color(0xFFFFFFFF)
val SgWash = Color(0xFFF4F4F2)
val SgInk = Color(0xFF111111)
val SgGrey = Color(0xFF6E6E6E)
val SgHairline = Color(0xFFE1E1E1)
val SgBlack = Color(0xFF000000)
val SgRed = Color(0xFFE30613)

val SgSans: FontFamily = FontFamily.SansSerif

@Composable
fun SgLabel(text: String, red: Boolean = false) {
    Text(
        text = text.uppercase(),
        fontFamily = SgSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = if (red) SgRed else SgGrey,
    )
}

@Composable
fun SgHead(text: String) {
    Text(
        text = text,
        fontFamily = SgSans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 34.sp,
        color = SgInk,
    )
}

@Composable
fun SgNumeral(text: String, red: Boolean = false) {
    Text(
        text = text,
        fontFamily = SgSans,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-1).sp,
        lineHeight = 44.sp,
        color = if (red) SgRed else SgInk,
    )
}

@Composable
fun SgBody(text: String) {
    Text(
        text = text,
        fontFamily = SgSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = SgInk,
    )
}

@Composable
fun SgQuiet(text: String) {
    Text(
        text = text,
        fontFamily = SgSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = SgGrey,
    )
}

@Composable
fun SgThickRule() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(SgBlack))
}

@Composable
fun SgHairRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SgHairline))
}

@Composable
fun SgPage(
    index: String,
    title: String,
    kicker: String,
    onBack: (() -> Unit)? = null,
    topSlot: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(SgPaper).verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SgLabel("$index / $kicker", red = index == "01")
            if (onBack != null) {
                Text(
                    text = "← BACK",
                    fontFamily = SgSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = SgInk,
                    modifier = Modifier.clickable(onClick = onBack).padding(6.dp),
                )
            }
        }
        Box(Modifier.height(12.dp))
        SgHead(title)
        Box(Modifier.height(10.dp))
        SgThickRule()
        if (topSlot != null) {
            Box(Modifier.height(14.dp))
            topSlot()
        }
        Box(Modifier.height(14.dp))
        content()
        Box(Modifier.height(28.dp))
        SgHairRule()
        Box(Modifier.height(8.dp))
        SgQuiet("Swiss grid · 12 columns · fake data only · nothing leaves this machine.")
    }
}

@Composable
fun SgSection(number: String, title: String, note: String = "") {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = number,
                fontFamily = SgSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = SgRed,
            )
            SgLabel(title)
        }
        if (note.isNotEmpty()) {
            Box(Modifier.height(4.dp))
            SgQuiet(note)
        }
        Box(Modifier.height(8.dp))
        SgHairRule()
    }
}

@Composable
fun SgSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SgPaper).padding(vertical = 12.dp),
    ) {
        content()
    }
    SgHairRule()
}

@Composable
fun SgRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SgLabel(label)
        Text(
            text = value,
            fontFamily = SgSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = SgInk,
        )
    }
}

@Composable
fun SgPrimary(label: String, onClick: () -> Unit, red: Boolean = false, enabled: Boolean = true) {
    val bg = when {
        !enabled -> SgHairline
        red -> SgRed
        else -> SgBlack
    }
    val fg = if (!enabled) SgGrey else SgPaper
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).background(bg).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = SgSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            color = fg,
        )
    }
}

@Composable
fun SgGhost(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(
            text = "→  " + label.uppercase(),
            fontFamily = SgSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.1.sp,
            color = SgInk,
        )
    }
}

@Composable
fun SgField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
fun SgGridRuler() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 1..12) {
                Text(
                    text = i.toString().padStart(2, '0'),
                    fontFamily = SgSans,
                    fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                    color = if (i == 1) SgRed else SgGrey,
                )
            }
        }
        Box(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 1..12) {
                Box(
                    Modifier.weight(1f).height(if (i == 1) 3.dp else 1.dp)
                        .background(if (i == 1) SgRed else SgHairline),
                )
                if (i < 12) Box(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun SgTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(SgPaper),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            SgTab(label = t, active = i == selected, onClick = { onSelect(i) })
        }
    }
}

@Composable
fun RowScope.SgTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = SgSans,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = if (active) SgInk else SgGrey,
        )
        Box(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(if (active) 3.dp else 1.dp).background(if (active) SgRed else SgHairline))
    }
}
