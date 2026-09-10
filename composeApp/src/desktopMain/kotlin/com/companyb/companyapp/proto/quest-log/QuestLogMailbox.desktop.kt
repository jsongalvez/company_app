package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogMailbox(repo: QuestLogRepo) {
    QuestSectionTitle(
        "Raven post",
        "Notifications roost here; the audit chronicle keeps every deed — ${repo.unreadCount} unread.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(QuestPadMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            Row {
                Text("Ravens (${repo.notices.size})", style = QuestLogType.titleSmall)
                Spacer(Modifier.weight(1f))
                QuestLinkButton("Shush all (mark read)") {
                    repo.notices.forEach { it.read = true }
                    repo.audit(repo.currentUser.name, "NOTICE.READ_ALL", "all ravens shushed")
                }
            }
            repo.notices.forEach { notice ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (notice.read) QuestLogPalette.Tavern else QuestLogPalette.Gold.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (notice.read) QuestLogPalette.Border else QuestLogPalette.GoldDeep,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                notice.read = true
                                repo.audit(repo.currentUser.name, "NOTICE.READ", "${notice.id} read")
                            }
                            .padding(QuestPadMd),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QuestBadge(notice.kind.uppercase(), QuestLogPalette.ManaBlue)
                            Spacer(Modifier.width(6.dp))
                            Text(notice.title, style = QuestLogType.titleMedium)
                            Spacer(Modifier.weight(1f))
                            if (!notice.read) QuestBadge("NEW", QuestLogPalette.Gold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(notice.body, style = QuestLogType.bodyMedium)
                        Text("day ${notice.dayRef}", style = QuestLogType.bodySmall)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            Text("Audit chronicle (${repo.audits.size} deeds)", style = QuestLogType.titleSmall)
            repo.audits.forEach { deed ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(QuestLogPalette.Tavern)
                            .border(1.dp, QuestLogPalette.Border, RoundedCornerShape(8.dp))
                            .padding(horizontal = QuestPadMd, vertical = QuestPadSm),
                ) {
                    Column {
                        Text("#${deed.seq} · ${deed.action}", style = QuestLogType.labelMedium)
                        Text(deed.detail, style = QuestLogType.bodyMedium)
                        Text("${deed.clock} · ${deed.actor}", style = QuestLogType.bodySmall)
                    }
                }
            }
        }
    }
}
