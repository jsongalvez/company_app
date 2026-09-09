package com.companyb.companyapp.proto.darkops

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun DarkOpsMailbox(repo: DarkOpsRepo) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier =
            Modifier.focusRequester(focus).focusable().opsListKeys(onUp = {
                repo.bumpNotice(-1)
            }, onDown = { repo.bumpNotice(1) }, onEnter = { repo.markNoticeRead(repo.selectedNoticeId) }),
    ) {
        OpsSectionHeader("mail", repo.unreadCount.toString() + " unread · enter marks read") {
            TextButton(onClick = { repo.markAllNoticesRead() }) {
                Text("[drain all]", style = DarkOpsType.labelLarge, color = DarkOpsPalette.Phosphor)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OpsPadMd)) {
            Column(Modifier.weight(1f)) {
                OpsPanel(modifier = Modifier.fillMaxWidth()) {
                    NoticeRows(repo)
                }
                Spacer(Modifier.height(OpsPadSm))
                AuditPanel(repo)
            }
            OpsPanel(modifier = Modifier.width(OpsDetailWidth)) {
                NoticeDetail(repo)
            }
        }
    }
}

internal fun DarkOpsRepo.bumpNotice(delta: Int) {
    if (notices.isEmpty()) return
    val current = notices.indexOfFirst { it.id == selectedNoticeId }.takeIf { it >= 0 } ?: 0
    selectedNoticeId = notices[(current + delta + notices.size) % notices.size].id
}

@Composable
private fun NoticeRows(repo: DarkOpsRepo) {
    if (repo.notices.isEmpty()) {
        OpsEmpty("mailbox drained", "fake traffic resumes never")
        return
    }
    repo.notices.forEach { notice ->
        OpsRow(selected = notice.id == repo.selectedNoticeId, onClick = {
            repo.selectedNoticeId = notice.id
            repo.markNoticeRead(notice.id)
        }) {
            Text(if (notice.read) " " else "●", style = DarkOpsType.bodyMedium, color = DarkOpsPalette.Phosphor)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notice.title,
                    style = DarkOpsType.bodyMedium,
                    color = if (notice.read) DarkOpsPalette.Dim else DarkOpsPalette.Ink,
                )
                Text(notice.dayRef + " · " + notice.kind, style = DarkOpsType.bodySmall)
            }
            if (!notice.read) OpsBadge("NEW", DarkOpsPalette.Phosphor)
        }
    }
}

@Composable
private fun NoticeDetail(repo: DarkOpsRepo) {
    val notice = repo.notices.firstOrNull { it.id == repo.selectedNoticeId }
    if (notice == null) {
        OpsEmpty("no notice", "drained")
        return
    }
    Text("NOTICE // " + notice.id, style = DarkOpsType.labelSmall)
    Spacer(Modifier.height(4.dp))
    Text(notice.title, style = DarkOpsType.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(notice.body, style = DarkOpsType.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OpsBadge(notice.kind, DarkOpsPalette.Cyan)
        OpsBadge("day " + notice.dayRef, DarkOpsPalette.Violet)
        OpsBadge(if (notice.read) "read" else "unread", if (notice.read) DarkOpsPalette.Faint else DarkOpsPalette.Amber)
    }
    Spacer(Modifier.height(OpsPadSm))
    OpsNote("relief notices tap through to their branch day")
}

@Composable
private fun AuditPanel(repo: DarkOpsRepo) {
    OpsPanel(modifier = Modifier.fillMaxWidth()) {
        Text("AUDIT LOG // immutable, newest first", style = DarkOpsType.labelSmall)
        Spacer(Modifier.height(4.dp))
        repo.audits.take(14).forEach { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "#" + entry.seq.toString().padStart(4, '0'),
                    style = DarkOpsType.bodySmall,
                    color = DarkOpsPalette.Faint,
                )
                Text(entry.clock, style = DarkOpsType.bodySmall, color = DarkOpsPalette.Cyan)
                Text(entry.actor.padEnd(10).take(10), style = DarkOpsType.bodySmall, color = DarkOpsPalette.Dim)
                Text(
                    entry.action,
                    style = DarkOpsType.bodyMedium,
                    color = DarkOpsPalette.Amber,
                    modifier = Modifier.width(150.dp),
                )
                Text(entry.detail, style = DarkOpsType.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        OpsNote("every mutation above appends here with actor + clock")
    }
}
