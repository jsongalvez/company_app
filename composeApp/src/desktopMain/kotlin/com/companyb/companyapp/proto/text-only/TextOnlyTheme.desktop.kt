package com.companyb.companyapp.proto.textonly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #853 — plaintext terminal tokens: near-black paper, phosphor ink, amber flags,
// signal red errors. Monospace everywhere, square corners, ASCII structure.
// Fake data only; no backend or network imports.

object TxTerm {
    val Bg = Color(0xFF0E120E)
    val Panel = Color(0xFF141A14)
    val Ink = Color(0xFFC9F2D4)
    val Bright = Color(0xFFE8FFF0)
    val Dim = Color(0xFF7E9184)
    val Faint = Color(0xFF54645A)
    val Line = Color(0xFF263026)
    val Amber = Color(0xFFFFC857)
    val AmberDim = Color(0xFF8A6D2B)
    val Red = Color(0xFFFF7A6B)
    val Green = Color(0xFF7BE39A)
    val Mono: FontFamily = FontFamily.Monospace
}

fun txPeso(amount: Int): String {
    val digits =
        amount
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    return "PHP $digits"
}

@Composable
fun T(
    text: String,
    color: Color = TxTerm.Ink,
    size: Int = 13,
    bold: Boolean = false,
    dim: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (dim) TxTerm.Dim else color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = TxTerm.Mono,
        lineHeight = (size + 6).sp,
        modifier = modifier,
    )
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TxTerm.Line))
}

@Composable
fun Head(
    index: String,
    title: String,
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("[$index] ", color = TxTerm.Amber, bold = true)
            T(title.uppercase(), color = TxTerm.Bright, size = 16, bold = true)
        }
        if (hint != null) T(hint, size = 12, dim = true)
        T("----------------------------------------------------------------", color = TxTerm.Faint, size = 12)
    }
}

@Composable
fun Note(text: String) {
    T("note: $text", size = 12, dim = true)
}

@Composable
fun TxError(text: String) {
    T("error: $text", size = 12, color = TxTerm.Red)
}

@Composable
fun Tag(
    label: String,
    color: Color = TxTerm.Dim,
) {
    Box(
        Modifier.border(1.dp, color).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        T("[$label]", size = 11, bold = true, color = color)
    }
}

@Composable
fun Cmd(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val color =
        when {
            !enabled -> TxTerm.Faint
            danger -> TxTerm.Red
            else -> TxTerm.Green
        }
    T(
        text = "[ $label ]",
        color = color,
        bold = true,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
fun KV(
    key: String,
    value: String,
    valueColor: Color = TxTerm.Ink,
) {
    Row(Modifier.fillMaxWidth()) {
        T(key.padEnd(18, ' '), size = 13, dim = true)
        T(value, size = 13, color = valueColor)
    }
}

@Composable
fun TxPanel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TxTerm.Panel)
            .border(1.dp, TxTerm.Line)
            .padding(10.dp),
        content = { content() },
    )
}
