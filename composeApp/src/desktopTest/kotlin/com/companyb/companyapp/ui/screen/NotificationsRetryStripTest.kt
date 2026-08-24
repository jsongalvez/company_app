package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.dto.ReliefInviteResponse
import com.companyb.companyapp.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #410 — the NotificationsScreen state→strip mapping: a failed invites read surfaces its
 * message with a retry on every cache state (keep-last rows never masquerade as fresh; an
 * empty cache must not hide the Accept/Decline actions), and a failed unread refresh degrades
 * to the inline retry strip only when content is on screen — a bare failure keeps the
 * in-place ErrorCard.
 */
class NotificationsRetryStripTest {
    @Test
    fun `failed invites read surfaces its message`() {
        assertEquals("boom", receivedInvitesErrorLine(invitesError("boom")))
    }

    @Test
    fun `non-error invites states surface nothing`() {
        assertNull(receivedInvitesErrorLine(UiState.Idle))
        assertNull(receivedInvitesErrorLine(UiState.Loading))
        assertNull(receivedInvitesErrorLine(UiState.Success(emptyList())))
    }

    @Test
    fun `failed unread refresh degrades to the strip only over content`() {
        assertEquals("boom", unreadRefreshErrorLine(unreadError("boom"), hasContent = true))
    }

    @Test
    fun `unread failure with nothing on screen stays on the ErrorCard path`() {
        assertNull(unreadRefreshErrorLine(unreadError("boom"), hasContent = false))
    }

    @Test
    fun `non-error unread states never render the strip`() {
        assertNull(unreadRefreshErrorLine(UiState.Success(emptyList()), hasContent = true))
        assertNull(unreadRefreshErrorLine(UiState.Loading, hasContent = true))
    }

    private fun invitesError(message: String): UiState<List<ReliefInviteResponse>> = UiState.Error(message)

    private fun unreadError(message: String): UiState<List<NotificationResponse>> = UiState.Error(message)
}
