package com.companyb.companyapp.ui.drawer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius

/**
 * #96 Q3 — alert-not-status pill. Inverts #97's "zero is valid business information" axis:
 * badge is a call-to-attention (alert-present → badge / alert-absent → silence), so the
 * CALLER never composes this for `unreadCount == null || unreadCount == 0`. State plumbing
 * lands in ticket B; this file is the layout + contrast commitment (error/onError =
 * #CF6679/#000000 = 5.83:1, passes AA at 12sp `bodySmall`). ">99" cap per ticket 11.
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        val display = if (count > 99) "99+" else count.toString()
        Text(
            text = display,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
