package com.companyb.companyapp.proto.splitmaster

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// #765 — split-master shared split primitives: every destination renders two panes.

const val SM_MASTER_WIDTH = 480

/** Two-pane scaffold: fixed 480dp dark master list + flexible paper detail. */
@Composable
fun SmSplit(
    master: @Composable ColumnScope.() -> Unit,
    detail: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        SmMasterTheme {
            Column(
                Modifier.width(SM_MASTER_WIDTH.dp).fillMaxHeight().background(SmColors.Ink).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = master,
            )
        }
        SmDetailTheme {
            Column(
                Modifier.weight(1f).fillMaxHeight().background(SmColors.Paper).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = detail,
            )
        }
    }
}

@Composable
fun <T> ColumnScope.SmMasterList(
    items: List<T>,
    key: (T) -> String,
    selectedKey: String,
    onSelect: (T) -> Unit,
    scroll: LazyListState,
    row: @Composable RowScope.(T, Boolean) -> Unit,
) {
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(items, key = { key(it) }) { item ->
            val selected = key(item) == selectedKey
            SmMasterTheme {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) SmColors.InkSoft else SmColors.Ink)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SmColors.Amber else SmColors.InkLine,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(item) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Box(Modifier.width(4.dp).height(40.dp).background(SmColors.Amber, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    row(item, selected)
                }
            }
        }
    }
}

@Composable
fun SmMasterHead(kicker: String, title: String, hint: String) {
    SmMasterTheme {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kicker, color = SmColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(title, color = SmColors.InkText, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(hint, color = SmColors.InkFaded, fontSize = 12.sp)
        }
    }
}

@Composable
fun SmDetailCard(content: @Composable ColumnScope.() -> Unit) {
    SmDetailTheme {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SmColors.Card)
                .border(1.dp, SmColors.CardEdge, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun SmHeadline(text: String) {
    SmDetailTheme {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = SmColors.DetailInk)
    }
}

@Composable
fun SmSubhead(text: String) {
    SmDetailTheme {
        Text(text, style = MaterialTheme.typography.titleMedium, color = SmColors.DetailInk)
    }
}

@Composable
fun SmNote(text: String) {
    SmDetailTheme {
        Text(text, color = SmColors.DetailFaded, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun SmBody(text: String) {
    SmDetailTheme {
        Text(text, color = SmColors.DetailInk, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun SmMono(text: String, selected: Boolean = false) {
    SmMasterTheme {
        Text(
            text,
            color = if (selected) SmColors.Amber else SmColors.InkFaded,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SmPrimary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    SmDetailTheme {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = SmColors.AmberDim, contentColor = androidx.compose.ui.graphics.Color.White),
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SmGhost(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    SmDetailTheme {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SmMasterAction(label: String, onClick: () -> Unit) {
    SmMasterTheme {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = SmColors.Amber, contentColor = SmColors.Ink),
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SmMasterGhost(label: String, onClick: () -> Unit) {
    SmMasterTheme {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SmColors.InkText),
        ) {
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
fun SmChip(text: String, dark: Boolean = false) {
    if (dark) {
        SmMasterTheme {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(SmColors.InkLine).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text, color = SmColors.InkText, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    } else {
        SmDetailTheme {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(SmColors.CardEdge).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text, color = SmColors.DetailInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun SmStatusChip(status: SmSessionStatus) {
    val (bg, fg) = when (status) {
        SmSessionStatus.PENDING -> SmColors.Gold to androidx.compose.ui.graphics.Color.White
        SmSessionStatus.COMPLETED -> SmColors.Teal to androidx.compose.ui.graphics.Color.White
        SmSessionStatus.NO_SHOW -> SmColors.Oxblood to androidx.compose.ui.graphics.Color.White
        SmSessionStatus.CANCELLED -> SmColors.Slate to androidx.compose.ui.graphics.Color.White
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(status.name, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun SmDayBanner(branch: SmBranch) {
    SmDetailTheme {
        val label = when (branch.dayStatus) {
            SmDayStatus.OPEN -> "Branch day OPEN"
            SmDayStatus.PAST -> "Branch day PAST"
            SmDayStatus.REMITTED -> "Branch day REMITTED"
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SmColors.Ink)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = SmColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text(branch.name + " · " + branch.kind, color = SmColors.InkText, fontSize = 12.sp)
            }
            Text(
                "Day boundary 04:00 Asia/Manila. OPEN accepts edits, PAST is read-only history, REMITTED is sealed.",
                color = SmColors.InkFaded,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun SmTextLink(label: String, onClick: () -> Unit) {
    SmDetailTheme {
        TextButton(onClick = onClick) {
            Text(label, color = SmColors.AmberDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
