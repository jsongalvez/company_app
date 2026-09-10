package com.companyb.companyapp.proto.listeverything

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #854 — list-everything universal outliner tokens + outline primitives.
// Whole branch reads as one nested outline: carets, indent levels, bullets.
// Paper index-card look, deliberately far from the Linear console style.

object LeColors {
    val Bg = Color(0xFFF4F1E6)
    val Paper = Color(0xFFFFFDF4)
    val CardLine = Color(0xFFE2D9BE)
    val Ink = Color(0xFF2B2620)
    val Muted = Color(0xFF7A6F5C)
    val Faint = Color(0xFFB3A88F)
    val Accent = Color(0xFF5B4B9E)
    val AccentWash = Color(0xFFE9E4F7)
    val Green = Color(0xFF2F6B3C)
    val GreenWash = Color(0xFFE2F0E2)
    val Amber = Color(0xFF8A5200)
    val AmberWash = Color(0xFFF7E8C8)
    val Red = Color(0xFF9E2B1E)
    val RedWash = Color(0xFFF7DDD6)
    val Slate = Color(0xFF4C5A66)
    val SlateWash = Color(0xFFE6EBEE)
}

fun lePeso(amount: Int): String {
    val digits = amount.toString().reversed().chunked(3).joinToString(",").reversed()
    return "₱$digits"
}

// Collapse/expand memory for every outline node, plus the global
// expand-all / collapse-all switch and the search query.
class LeTreeState {
    private val open = mutableStateMapOf<String, Boolean>()
    var expandAllDefault by mutableStateOf(true)
    var query by mutableStateOf("")

    fun isOpen(id: String, default: Boolean = true): Boolean {
        if (query.isNotBlank()) return true
        return open.getOrElse(id) { default }
    }

    fun toggle(id: String, default: Boolean = true) {
        open[id] = !isOpen(id, default)
    }

    fun expandAll() {
        open.clear()
        expandAllDefault = true
    }

    fun collapseAll() {
        open.clear()
        expandAllDefault = false
    }

    // Leaf filter: visible when no query, or any haystack contains the query.
    fun matches(vararg haystacks: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        return haystacks.any { it.contains(q, ignoreCase = true) }
    }
}

// One collapsible outline row. Header is always visible; body renders indented
// one level deeper when the node is open.
@Composable
fun LeNode(
    tree: LeTreeState,
    id: String,
    level: Int,
    label: String,
    meta: String? = null,
    badge: String? = null,
    badgeTone: LeTone = LeTone.ACCENT,
    defaultOpen: Boolean = true,
    body: @Composable () -> Unit = {},
) {
    val open = tree.isOpen(id, defaultOpen)
    Column(
        Modifier.fillMaxWidth()
            .padding(start = (level * 22).dp)
            .padding(vertical = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (level == 0) LeColors.Paper else Color.Transparent)
                .border(
                    width = if (level == 0) 1.dp else 0.dp,
                    color = LeColors.CardLine,
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable { tree.toggle(id, defaultOpen) }
                .padding(horizontal = 10.dp, vertical = if (level == 0) 9.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (open) "▾" else "▸",
                fontSize = if (level == 0) 15.sp else 12.sp,
                fontWeight = FontWeight.Black,
                color = LeColors.Accent,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontSize = if (level == 0) 16.sp else 13.5.sp,
                fontWeight = if (level == 0) FontWeight.Black else FontWeight.SemiBold,
                color = LeColors.Ink,
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                LeBadge(badge, tone = badgeTone)
            }
            if (meta != null) {
                Spacer(Modifier.width(8.dp))
                Text(meta, fontSize = 12.sp, color = LeColors.Muted)
            }
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
                body()
            }
        }
    }
}

// Plain indented bullet leaf (no children).
@Composable
fun LeLeaf(
    level: Int,
    bullet: String = "•",
    label: String,
    meta: String? = null,
    badge: String? = null,
    badgeTone: LeTone = LeTone.SLATE,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = (level * 22 + 10).dp, end = 10.dp)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bullet, fontSize = 12.sp, color = LeColors.Faint, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = LeColors.Ink)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            LeBadge(badge, tone = badgeTone)
        }
        if (meta != null) {
            Spacer(Modifier.width(8.dp))
            Text(meta, fontSize = 12.sp, color = LeColors.Muted)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

enum class LeTone(val fg: Color, val wash: Color) {
    SLATE(LeColors.Slate, LeColors.SlateWash),
    GREEN(LeColors.Green, LeColors.GreenWash),
    AMBER(LeColors.Amber, LeColors.AmberWash),
    RED(LeColors.Red, LeColors.RedWash),
    ACCENT(LeColors.Accent, LeColors.AccentWash),
}

@Composable
fun LeBadge(text: String, tone: LeTone = LeTone.ACCENT) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(tone.wash)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone.fg)
    }
}

@Composable
fun LeNote(level: Int, text: String) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = (level * 22 + 10).dp, end = 10.dp)
            .padding(vertical = 2.dp),
    ) {
        Text("◦", fontSize = 12.sp, color = LeColors.Faint, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = LeColors.Muted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun LeRule() {
    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .height(1.dp)
            .background(LeColors.CardLine),
    )
}

@Composable
fun LeAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(label, fontSize = 12.sp)
    }
}
