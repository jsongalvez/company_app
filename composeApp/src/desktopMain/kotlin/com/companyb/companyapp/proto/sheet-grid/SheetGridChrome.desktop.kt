package com.companyb.companyapp.proto.sheetgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #817 — sheet-grid chrome: frozen banner row, formula bar, sheet tabs, status bar,
// plus the shared arrow-key navigation modifier.

val SG_SHEETS = listOf("HOME", "SESSIONS", "CLIENTS", "FINANCE", "TEAM", "MAIL", "AUDIT", "PROFILE")

fun Modifier.sgArrowKeys(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEnter: () -> Unit = {},
): Modifier =
    this.onPreviewKeyEvent {
        when (it.key) {
            Key.DirectionUp -> {
                onUp()
                true
            }

            Key.DirectionDown -> {
                onDown()
                true
            }

            Key.Enter, Key.NumPadEnter -> {
                onEnter()
                true
            }

            else -> {
                false
            }
        }
    }

fun Modifier.sgFocusableGrid(
    focus: FocusRequester,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEnter: () -> Unit = {},
): Modifier =
    this
        .focusRequester(focus)
        .focusable()
        .sgArrowKeys(onUp, onDown, onEnter)

@Composable
fun SgBanner(store: SgStore) {
    val day = store.currentDay()
    Row(
        modifier = Modifier.fillMaxWidth().background(SgGreenDark).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "▦ SHEET-GRID",
            fontFamily = SgMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.width(12.dp))
        store.days.forEachIndexed { i, d ->
            val sel = i == store.dayIndex
            Box(
                modifier =
                    Modifier
                        .background(if (sel) Color.White else Color.Transparent)
                        .border(1.dp, Color.White.copy(alpha = 0.6f))
                        .clickable {
                            store.dayIndex = i
                            store.activeRow = 1
                        }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "${d.label} ${d.status}",
                    fontFamily = SgMono,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) SgGreenDark else Color.White,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Branch day flips at 04:00 Asia/Manila · status: ${day.status}",
            fontFamily = SgMono,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgWash)
                .border(0.5.dp, SgGridLine)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            store.branchName(store.currentBranchId),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = SgInk,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            store.currentUser?.let { "${it.name} · ${it.role}" } ?: "—",
            fontFamily = SgMono,
            fontSize = 11.sp,
            color = SgMuted,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .background(if (store.clockedIn) SgToneGreenBg else SgToneAmberBg)
                    .border(1.dp, SgGridLine)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                if (store.clockedIn) "● CLOCKED IN" else "○ CLOCKED OUT",
                fontFamily = SgMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (store.clockedIn) SgToneGreenFg else SgToneAmberFg,
            )
        }
    }
}

@Composable
fun SgFormulaBar(
    address: String,
    value: String,
    hint: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SgPaper).border(0.5.dp, SgGridLine),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .background(SgHeaderFill)
                    .border(0.5.dp, SgGridLine)
                    .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(address, fontFamily = SgMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SgInk)
        }
        Box(
            modifier =
                Modifier
                    .width(52.dp)
                    .background(SgGreen)
                    .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                SG_ADDR_GLYPH,
                fontFamily = SgMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            value,
            fontFamily = SgMono,
            fontSize = 12.sp,
            color = SgInk,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (hint.isNotEmpty()) {
            Text(
                hint,
                fontFamily = SgMono,
                fontSize = 10.5.sp,
                color = SgMuted,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
    }
}

@Composable
fun SgTabBar(store: SgStore) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SgTabIdle).padding(start = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        SG_SHEETS.forEach { name ->
            val sel = store.sheet == name
            Box(
                modifier =
                    Modifier
                        .background(if (sel) SgTabActive else SgTabIdle)
                        .border(0.5.dp, SgGridLine)
                        .clickable {
                            store.sheet = name
                            store.activeRow = 1
                            store.mailJump = ""
                        }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    name,
                    fontFamily = SgMono,
                    fontSize = 11.5.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) SgGreenDark else SgMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "fake workbook · no network",
            fontFamily = SgMono,
            fontSize = 10.sp,
            color = SgMuted,
            modifier = Modifier.padding(end = 10.dp, bottom = 6.dp),
        )
    }
}

@Composable
fun SgStatusBar(
    store: SgStore,
    detail: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgGreenDark)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("READY", fontFamily = SgMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(14.dp))
        Text(detail, fontFamily = SgMono, fontSize = 11.sp, color = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.weight(1f))
        Text(
            "↑↓ move · Enter open · 1280×800",
            fontFamily = SgMono,
            fontSize = 10.5.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
fun SgMiniAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(if (enabled) SgPaper else SgWash)
                .border(1.dp, if (enabled) SgGreen else SgGridLine)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            fontFamily = SgMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) SgGreenDark else SgMuted,
        )
    }
}

@Composable
fun SgActionStrip(
    label: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SgWash)
                .border(0.5.dp, SgGridLine)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = SgMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SgMuted)
        Spacer(Modifier.width(10.dp))
        actions()
    }
}

@Composable
fun SgWorkbookShell(
    store: SgStore,
    address: String,
    formula: String,
    status: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SgBanner(store)
        SgFormulaBar(address, formula, "↑↓ rows · Enter acts")
        content()
        Spacer(Modifier.weight(1f))
        SgTabBar(store)
        SgStatusBar(store, status)
    }
}
