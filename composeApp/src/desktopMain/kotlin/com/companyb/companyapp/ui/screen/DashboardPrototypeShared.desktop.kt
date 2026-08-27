package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

private val PrototypeAvatarSize = 36.dp

@Suppress("MagicNumber")
private val CompletedStatusColor = Color(0xFF27A644)

@Suppress("MagicNumber")
private val PendingStatusColor = Color(0xFFF0B429)

@Composable
internal fun DashboardMetric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
    }
}

@Composable
internal fun DashboardSectionTitle(
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
    }
}

@Composable
internal fun DashboardStatusBadge(status: FakeSessionStatus) {
    val color = dashboardStatusColor(status)
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun dashboardStatusColor(status: FakeSessionStatus): Color =
    when (status) {
        FakeSessionStatus.COMPLETED -> CompletedStatusColor
        FakeSessionStatus.PENDING -> PendingStatusColor
        FakeSessionStatus.NO_SHOW, FakeSessionStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }

@Composable
internal fun DashboardAvatar(
    initials: String,
    selected: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .size(PrototypeAvatarSize)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun DashboardSessionRow(
    session: FakeDashboardSession,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.md),
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(session.time, style = MaterialTheme.typography.labelLarge, color = InkSubtle)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.client,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${session.type}  /  ${session.practitioner}  /  ${session.concern}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            DashboardStatusBadge(session.status)
            Text(
                session.finalPrice,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun DashboardStaffLine(
    staff: FakeDashboardStaff,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(CornerRadius.md),
        color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DashboardAvatar(staff.initials, selected)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    staff.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${staff.role}  /  ${staff.slot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(staff.attendance, style = MaterialTheme.typography.labelSmall, color = attendanceColor(staff))
                Text("${staff.sessionCount} sessions", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            }
        }
    }
}

@Composable
internal fun attendanceColor(staff: FakeDashboardStaff): Color =
    when {
        staff.isRelief -> MaterialTheme.colorScheme.primary
        staff.attendance == "Clocked in" -> CompletedStatusColor
        else -> InkSubtle
    }

@Composable
internal fun DashboardReliefCard(card: FakeReliefCard) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    card.action,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(card.detail, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            Text(
                card.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
internal fun NotificationBadge(count: Int) {
    Surface(
        modifier = Modifier.padding(start = Spacing.xs),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}
