package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestLogHome(repo: QuestLogRepo) {
    QuestSectionTitle(
        "War camp at ${repo.currentBranch.name}",
        "Clock in, muster the party, answer relief horns — ${repo.currentDay.date} · ${repo.currentDay.state.label}",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(QuestPadMd)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            QuestCard {
                Text("Today's muster", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (repo.clockedIn) {
                        "You are IN THE FIELD. The board counts your turned-in quests as XP."
                    } else {
                        "You are AT CAMP. Clock in to let the board count this day toward your legend."
                    },
                    style = QuestLogType.bodyMedium,
                )
                Spacer(Modifier.height(QuestPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    if (repo.clockedIn) {
                        QuestGhostButton("Strike camp (clock out)") {
                            repo.clockedIn = false
                            repo.audit(repo.currentUser.name, "CLOCK_OUT", "clocked out at ${repo.currentBranch.name}")
                        }
                    } else {
                        QuestPrimaryButton("Take up arms (clock in)") {
                            repo.clockedIn = true
                            repo.audit(repo.currentUser.name, "CLOCK_IN", "clocked in at ${repo.currentBranch.name}")
                        }
                    }
                }
            }
            QuestCard {
                Text("Party on duty (${repo.clockCrew().size})", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                repo.staff.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${member.name} · ${member.role}", style = QuestLogType.bodyMedium)
                            Text(
                                "slot ${member.slot} · home ${member.home}" +
                                    if (member.relief) " · RELIEF" else "",
                                style = QuestLogType.bodySmall,
                            )
                        }
                        QuestBadge(
                            if (member.clockedIn) "ON DUTY" else "OFF",
                            if (member.clockedIn) QuestLogPalette.XpGreen else QuestLogPalette.Faint,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QuestPadSm)) {
            QuestCard {
                Text("Relief horns (${repo.reliefs.count { it.verdict == "AWAITING" }} awaiting)", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "A Relief Duty lends a hero to another outpost. Grant the horn, or sound one of your own below.",
                    style = QuestLogType.bodyMedium,
                )
                Spacer(Modifier.height(QuestPadSm))
                repo.reliefs.forEach { horn ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${horn.who} → ${horn.where}", style = QuestLogType.bodyMedium)
                                Text("${horn.day} · ${horn.verdict}", style = QuestLogType.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                            QuestPrimaryButton("Grant", horn.verdict == "AWAITING") {
                                horn.verdict = "GRANTED"
                                repo.audit(repo.currentUser.name, "RELIEF.GRANT", "${horn.who} to ${horn.where}")
                            }
                            QuestGhostButton("Decline", horn.verdict == "AWAITING") {
                                horn.verdict = "DECLINED"
                                repo.audit(repo.currentUser.name, "RELIEF.DECLINE", "${horn.who} to ${horn.where}")
                            }
                        }
                        Spacer(Modifier.height(QuestPadSm))
                    }
                }
            }
            QuestCard {
                Text("Sound a new horn", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repo.inviteWho,
                    onValueChange = { repo.inviteWho = it },
                    label = { Text("Invite hero (name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = repo.requestWhere,
                    onValueChange = { repo.requestWhere = it },
                    label = { Text("Request cover at (outpost)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(QuestPadSm))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestPadSm)) {
                    QuestPrimaryButton("Send invite", repo.inviteWho.isNotBlank()) {
                        repo.reliefs.add(0, QuestRelief("r-${repo.reliefs.size + 1}", repo.inviteWho, repo.currentBranch.name, repo.currentDay.date, "AWAITING"))
                        repo.audit(repo.currentUser.name, "RELIEF.INVITE", "invited ${repo.inviteWho}")
                        repo.inviteWho = ""
                    }
                    QuestGhostButton("Request cover", repo.requestWhere.isNotBlank()) {
                        repo.reliefs.add(0, QuestRelief("r-${repo.reliefs.size + 1}", repo.currentUser.name, repo.requestWhere, repo.currentDay.date, "AWAITING"))
                        repo.audit(repo.currentUser.name, "RELIEF.REQUEST", "cover at ${repo.requestWhere}")
                        repo.requestWhere = ""
                    }
                }
            }
            QuestCard {
                Text("Hero level — Lv ${repo.heroLevel()}", style = QuestLogType.titleSmall)
                Spacer(Modifier.height(4.dp))
                QuestXpBar(repo.heroLevelProgress())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${repo.completedXp()} XP banked · ${(QuestLogXpTarget(repo) - repo.completedXp()).coerceAtLeast(0)} XP to Lv ${repo.heroLevel() + 1}. " +
                        "XP is commission: every turned-in quest feeds the bar.",
                    style = QuestLogType.bodyMedium,
                )
                Spacer(Modifier.width(QuestPadSm))
            }
        }
    }
}

private fun QuestLogXpTarget(repo: QuestLogRepo): Int = repo.heroLevel() * 3000
