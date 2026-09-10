package com.companyb.companyapp.proto.lunchrush

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LunchRushMailbox(repo: LunchRushRepo) {
    RushSectionHeader("Pass rail", "${repo.unreadCount} unread") {
        OutlinedButton(onClick = { repo.markAllNoticesRead() }) { Text("Drain all") }
    }
    repo.notices.forEach { notice ->
        RushTicketCard(hot = !notice.read, onClick = { repo.markNoticeRead(notice.id) }) {
            RushRow(
                left = {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RushBadge(notice.kind, LunchRushPalette.Turmeric)
                            RushBadge(notice.dayRef, LunchRushPalette.Faint)
                            if (!notice.read) RushBadge("UNREAD", LunchRushPalette.Char)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(notice.title, style = LunchRushType.titleMedium)
                        Text(notice.body, style = LunchRushType.bodyMedium)
                    }
                },
                right = {},
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(RushPadMd))
    RushSectionHeader("Audit log", "newest first — every action signs actor + clock")
    Column(Modifier.fillMaxWidth()) {
        repo.audits.forEach { entry ->
            RushRow(
                left = {
                    Column(Modifier.weight(1f)) {
                        Text("#${entry.seq} ${entry.action}", style = LunchRushType.labelLarge)
                        Text(entry.detail, style = LunchRushType.bodySmall)
                    }
                },
                right = {
                    Column {
                        Text(entry.clock, style = LunchRushType.labelSmall)
                        Text(entry.actor, style = LunchRushType.labelSmall)
                    }
                },
            )
        }
    }
}
