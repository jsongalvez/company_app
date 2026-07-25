package com.companyb.companyapp.ui.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * #96 Q3c — #24: hamburger-tap-to-open (mobile) with annotation badge.
 *
 * Material3 has no hamburger-badge API; named one-off. Badge sits aligned to the IconButton's
 * TopEnd then offset (-6dp, +6dp) so its top-right lands 6dp inside the 48dp touch target box
 * bounds — i.e., ~25% past the inner 24dp hamburger visual's top-right corner (Q3c
 * "~25% outside icon bbox" — annotation reading, not part of the icon). State plumbing wires
 * in ticket B; `unreadCount == null || unreadCount == 0` ⟹ caller-side closure (Q3a).
 *
 * Hamburger drawn inline (Canvas) rather than `Icons.Default.Menu` because the project does
 * not depend on `org.jetbrains.compose.material:material-icons-*`; hand-rolled keeps the icon
 * close to the only call site and avoids adding a dep for a single icon.
 */
@Composable
fun HamburgerWithBadge(
    onClick: () -> Unit,
    unreadCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        IconButton(onClick = onClick) {
            HamburgerIcon()
        }
        if (unreadCount != null && unreadCount > 0) {
            NotificationBadge(
                count = unreadCount,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -6.dp, y = 6.dp),
            )
        }
    }
}

@Composable
private fun HamburgerIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val yStart = size.height * 0.27f
        val yMiddle = size.height * 0.5f
        val yEnd = size.height * 0.73f
        val xStart = size.width * 0.15f
        val xEnd = size.width * 0.85f
        for (y in listOf(yStart, yMiddle, yEnd)) {
            drawLine(
                color = color,
                start = Offset(xStart, y),
                end = Offset(xEnd, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
