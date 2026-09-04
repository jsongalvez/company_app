package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * Queue derivations hoisted out of [NotificationsScreen] for the #462 LongMethod burn-down.
 * Lives here (not same-file) because NotificationsScreen.kt sits at the detekt file-function
 * wall — a same-file helper trips TooManyFunctions. Self-sufficient host: collects the five
 * queue flows itself (duplicate StateFlow subscriptions cheap — LoginNoticeEffect precedent)
 * so the Screen keeps one slim call; `notificationsState` stays collected at the Screen (its
 * status-when + entry log live there). Derivations verbatim (no remember keys in the source —
 * recomputed per composition, identical semantics).
 *
 * 1 param so it stays LongParameterList-clean outside the LPL-excluded Screen file.
 * FunctionNaming ignores @Composable so the camelCase value-returning host is lint-clean.
 */
@Composable
internal fun rememberNotificationsDerived(viewModel: NotificationViewModel): NotificationsDerived {
    val freshestUnread by viewModel.freshestNotifications.collectAsState()
    val readThisSession by viewModel.readThisSession.collectAsState()
    val historyState by viewModel.history.collectAsState()
    val markReadState by viewModel.markReadResult.collectAsState()
    val markAllState by viewModel.markAllResult.collectAsState()

    // Action failures surface inline (#135 round-1 precedent — silent network-failure paths are a
    // bug class, not a design choice): a failed markRead/markAll must be visible, and the next
    // attempt's Loading pre-set clears the line automatically. 404-on-markRead is NOT an error —
    // the VM handles it internally (absent-row defense reload), so it never reaches this state.
    val markReadError = (markReadState as? UiState.Error)?.message
    val markAllError = (markAllState as? UiState.Error)?.message
    val historyError = (historyState as? UiState.Error)?.message
    // ComplexCondition carve-out (#462 burn): the error OR lives in a named val so the
    // render gate stays a single condition. Invite accept/decline errors render in
    // ReliefInvitesSection (it owns those flows); only the queue errors gate here.
    val hasActionError = markAllError != null || markReadError != null
    // D5 + #97 Q5 silent-refresh: cold-start spinner only while there's nothing to show; once a
    // list has content, a reload (re-entry, post-markAll arrival) must not flash a spinner over
    // it. `unread` derives from the VM's freshest flow (keep-last-results — the #162 KeepLast
    // unifier; VM-side so it survives composition re-entries; audit #141 pass-5). Cold start
    // keeps the spinner: the freshest flow is null until the first Success lands. The
    // empty-list reload case (All caught up → re-entry → reload) still flashes the spinner —
    // nothing is on screen, so D5's "nothing to show → spinner" clause covers it.
    val unread = freshestUnread.orEmpty()
    // #356 — server-backed history: every past row, read + unread, newest first. Rows the live
    // sections already render are filtered out so nothing appears twice; what remains is
    // display-only (tap-to-read stays the unread queue's job).
    val historyRows = (historyState as? UiState.Success)?.data.orEmpty()
    val visibleHistory =
        historyRows.filterNot { row ->
            unread.any { it.id == row.id } || readThisSession.any { it.id == row.id }
        }
    val hasContent = unread.isNotEmpty() || readThisSession.isNotEmpty() || visibleHistory.isNotEmpty()
    return NotificationsDerived(
        markReadError = markReadError,
        markAllError = markAllError,
        historyError = historyError,
        hasActionError = hasActionError,
        unread = unread,
        readThisSession = readThisSession,
        visibleHistory = visibleHistory,
        hasContent = hasContent,
        markAllBusy = markAllState is UiState.Loading,
    )
}
