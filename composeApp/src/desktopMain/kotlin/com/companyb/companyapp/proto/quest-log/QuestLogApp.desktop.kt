package com.companyb.companyapp.proto.questlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.util.logInfo

private const val TAG = "QuestLog"

@Composable
internal fun QuestLogApp() {
    val repo = rememberQuestLogRepo()
    LaunchedEffect(Unit) { logInfo(TAG, "quest-log board open (fake data, no network)") }
    QuestLogTheme {
        when {
            !repo.authed -> QuestLogAuthGate(repo)
            repo.currentUser.locked -> QuestLogOnboardingLock(repo)
            !repo.branchPicked -> QuestLogBranchSelect(repo)
            else -> QuestLogShell(repo)
        }
    }
}

@Composable
private fun QuestLogShell(repo: QuestLogRepo) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(QuestLogPalette.Night),
    ) {
        Column(Modifier.fillMaxSize()) {
            QuestDayBanner(repo)
            HorizontalDivider(color = QuestLogPalette.Border)
            Row(Modifier.weight(1f)) {
                QuestNavRail(repo)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(QuestPadMd),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (repo.dayLocked) {
                            QuestCard {
                                Text(
                                    "This chain link is ${repo.currentDay.state.label}. " +
                                        "Only a MANAGER or Coordinator may rewrite it.",
                                    style = QuestLogType.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(QuestPadSm))
                        }
                        when (repo.screen) {
                            QuestScreen.BOARD -> QuestLogHome(repo)
                            QuestScreen.QUESTS -> QuestLogSessions(repo)
                            QuestScreen.ALLIES -> QuestLogClients(repo)
                            QuestScreen.VAULT -> QuestLogFinance(repo)
                            QuestScreen.GUILD -> QuestLogTeam(repo)
                            QuestScreen.RAVEN -> QuestLogMailbox(repo)
                            QuestScreen.HERO -> QuestLogProfile(repo)
                        }
                        Spacer(Modifier.height(QuestPadMd))
                    }
                }
            }
            HorizontalDivider(color = QuestLogPalette.Border)
            QuestStatusBar(repo)
        }
    }
    if (repo.createOpen) QuestCreateDialog(repo)
    val voidId = repo.voidTargetId
    if (voidId != null) QuestVoidDialog(repo, voidId)
}
