package com.companyb.companyapp.proto.daybreakroutine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DaybreakMailbox(repo: DaybreakRepo) {
    Column(Modifier.fillMaxWidth()) {
        DawnSectionHeader("Mailbox", "${repo.unreadCount} unread") {
            OutlinedButton(onClick = { repo.markAllNoticesRead() }) { Text("Drain all") }
        }
        repo.notices.forEach { notice ->
            DawnPanel(
                modifier = Modifier.fillMaxWidth().clickable { repo.markNoticeRead(notice.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notice.title, style = DaybreakType.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    if (!notice.read) DawnBadge("NEW", DaybreakPalette.Sunrise)
                    Spacer(Modifier.weight(1f))
                    Text(notice.dayRef, style = DaybreakType.labelMedium)
                }
                Text(notice.body, style = DaybreakType.bodySmall)
                Text("tap to mark read · kept forever as history", style = DaybreakType.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
        }
        DawnSectionHeader("Audit log", "${repo.audits.size} entries · newest first")
        repo.audits.forEach { entry ->
            DawnPanel(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${entry.seq} ${entry.action}", style = DaybreakType.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(entry.clock, style = DaybreakType.labelMedium)
                }
                Text(entry.actor + " · " + entry.detail, style = DaybreakType.bodySmall)
            }
        }
    }
}
