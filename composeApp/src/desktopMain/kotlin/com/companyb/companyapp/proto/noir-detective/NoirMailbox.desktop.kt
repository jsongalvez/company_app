package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.layout.Arrangement
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
internal fun NoirMailbox(repo: NoirRepo) {
    FolderTab(title = "The wire", right = repo.unreadCount.toString() + " unread intercepts") {
        OutlinedButton(onClick = { repo.markAllRead() }) {
            Text("Drain the wire", style = NoirType.labelLarge, color = NoirPalette.LampAmber)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadMd)) {
        Column(Modifier.weight(1f)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                if (repo.wire.isEmpty()) {
                    RainyEmpty("the wire is dead", "nothing came in tonight")
                }
                repo.wire.forEach { message ->
                    FileRow(selected = false, onClick = { repo.markRead(message.id) }) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (!message.read) NoirBadge("NEW", NoirPalette.SirenRed)
                                Text(message.title, style = NoirType.bodyLarge)
                            }
                            Text(message.body, style = NoirType.bodySmall, color = NoirPalette.Dim)
                            Text(
                                message.kind + " · taps through to " + message.dayRef,
                                style = NoirType.bodySmall,
                                color = NoirPalette.Faint,
                            )
                        }
                        if (!message.read) {
                            OutlinedButton(onClick = { repo.markRead(message.id) }) {
                                Text("Read", style = NoirType.labelMedium, color = NoirPalette.LampAmber)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(NoirPadSm))
            DeskLampNote("Every intercept is filed per recipient — read rows stay on the books forever.")
        }
        Column(Modifier.width(NoirDetailWidth)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                Text("NIGHT LOG — EVERY MOVE", style = NoirType.titleSmall)
                Spacer(Modifier.height(4.dp))
                repo.log.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("#" + entry.seq, style = NoirType.bodySmall, color = NoirPalette.LampAmber)
                        Column(Modifier.weight(1f)) {
                            Text(entry.action, style = NoirType.bodySmall)
                            Text(
                                entry.actor + " · " + entry.clock + " · " + entry.detail,
                                style = NoirType.bodySmall,
                                color = NoirPalette.Dim,
                            )
                        }
                    }
                }
            }
        }
    }
}
