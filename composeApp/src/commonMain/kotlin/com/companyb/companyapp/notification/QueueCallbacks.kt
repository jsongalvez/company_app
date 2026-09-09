package com.companyb.companyapp.notification

import androidx.compose.ui.focus.FocusRequester
import com.companyb.companyapp.contracts.notification.NotificationResponse

/**
 * Stable callbacks for the notification queue (#679). Bundled so the lazy host stays
 * LongParameterList-clean; [lastOpenedId] restores focus to the invoking row after Back.
 */
internal data class QueueCallbacks(
    val onNotificationClick: (NotificationResponse) -> Unit,
    val onAcceptInvite: (String) -> Unit,
    val onDeclineInvite: (String) -> Unit,
    val onRetryReceived: () -> Unit,
    val lastOpenedId: String?,
    val focusRequesters: MutableMap<String, FocusRequester>,
)
