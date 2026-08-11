package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatTimeOfDay
import com.companyb.companyapp.viewmodel.DashboardPollStatus
import com.companyb.companyapp.viewmodel.SessionDashboardViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.math.abs
import kotlin.time.Instant

private const val SESSION_STATUS_COMPLETED = "COMPLETED"
private const val WALK_IN_DOT_SIZE = 8

/**
 * Fixed-point money helpers (commonMain has no BigDecimal): backend money strings are
 * non-negative decimal strings (parseNonNegativeBigDecimal server-side), commission at scale 4
 * ("200.0000"), prices at scale 2. Cents arithmetic keeps the gross sum exact; scale-4 inputs
 * truncate at the second decimal (display-only — the backend amount is authoritative).
 */
internal fun moneyToCents(raw: String): Long {
    val parts = raw.split('.')
    val whole = parts.firstOrNull()?.toLongOrNull() ?: return 0L
    val frac =
        parts
            .getOrNull(1)
            ?.take(2)
            ?.padEnd(2, '0')
            ?.toLongOrNull() ?: 0L
    return whole * 100 + frac
}

internal fun centsToMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absValue = abs(cents)
    return "$sign${absValue / 100}.${(absValue % 100).toString().padStart(2, '0')}"
}

/**
 * #97 Q2 — gross income excludes voided rows AND non-completed sessions at the state layer
 * ("Today · completed, non-voided"); rendering only shows voided-ness.
 */
internal fun grossIncomeCents(sessions: List<DashboardSessionResponse>): Long =
    sessions
        .filter { it.sessionStatus == SESSION_STATUS_COMPLETED && !it.isVoided }
        .sumOf { moneyToCents(it.finalPrice) }

internal fun commissionLabel(productSalesCount: Int): String =
    "from $productSalesCount product sale${if (productSalesCount == 1) "" else "s"}"

data class SessionListArgs(
    val sessions: List<DashboardSessionResponse>,
    val selectedSessionId: String?,
    val onSessionClick: (DashboardSessionResponse) -> Unit,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean,
)

// #95 — platform-split list actual: desktopMain = table with header + selectable rows
// (RemittanceScreenParts precedent); androidMain = LazyColumn cards (spec line 111).
@Composable
internal expect fun SessionList(
    args: SessionListArgs,
    modifier: Modifier = Modifier.fillMaxSize(),
)

/**
 * #97 session dashboard — summary cards + session list, both backed by the single dashboard
 * fetch (Q6c's "no impossible states if they share an endpoint"). Poll lifecycle: resumed
 * while this screen is composed, paused on leave (mobile detail push; desktop inline pane
 * never leaves). States per Q6: cold spinner / error card with Retry / empty (₱0 cards) /
 * stale banner (Q5b) / ERRORED escalation / forbidden card (attendance gate 403).
 */
@Composable
fun SessionDashboardScreen(
    viewModel: SessionDashboardViewModel,
    selectedBranchName: String?,
    onSessionClick: (DashboardSessionResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.dashboardState.collectAsState()
    val lastData by viewModel.lastData.collectAsState()
    val lastUpdatedAt by viewModel.lastUpdatedAt.collectAsState()
    val pollStatus by viewModel.pollStatus.collectAsState()
    val isForbidden by viewModel.isForbidden.collectAsState()

    DisposableEffect(Unit) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }

    Box(modifier = modifier) {
        when {
            isForbidden -> {
                InPlaceCard(
                    title = "You are no longer clocked in at this branch",
                    body =
                        "Your clock-in ended on the server (possibly clocked out elsewhere). " +
                            "Use Clock out in the drawer to return to branches.",
                    actionLabel = "Retry",
                    onAction = viewModel::retryAfterForbidden,
                )
            }

            lastData == null && (state is UiState.Idle || state is UiState.Loading) -> {
                // Q6a — cold start: full-screen spinner, no skeletons.
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            lastData == null && state is UiState.Error -> {
                val error = state as UiState.Error
                InPlaceCard(
                    title = "Couldn't load the dashboard",
                    body = error.message,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }

            else -> {
                val data = lastData
                if (data != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SummaryCardsRow(
                            grossCents = grossIncomeCents(data.sessions),
                            commissionCents = moneyToCents(data.commission.amount),
                            productSalesCount = data.commission.productSalesCount,
                        )
                        LastUpdatedRow(lastUpdatedAt)
                        if (pollStatus == DashboardPollStatus.STALE) {
                            StaleBanner()
                        }
                        if (pollStatus == DashboardPollStatus.ERRORED) {
                            // Q5b escalation — error card with Retry (last data preserved
                            // in the VM; the card replaces content until a success resets).
                            InPlaceCard(
                                title = "Dashboard updates stopped",
                                body = "Repeated refresh failures — showing the last loaded data.",
                                actionLabel = "Retry",
                                onAction = viewModel::refresh,
                            )
                        } else if (data.sessions.isEmpty()) {
                            // Q6b — empty state participates in polling (auto-transitions
                            // when sessions appear); ₱0 cards stay visible above.
                            EmptySessions(selectedBranchName)
                        } else {
                            SessionList(
                                args =
                                    SessionListArgs(
                                        sessions = data.sessions,
                                        selectedSessionId = null,
                                        onSessionClick = onSessionClick,
                                        onRefresh = viewModel::refresh,
                                        isRefreshing = state is UiState.Loading,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCardsRow(
    grossCents: Long,
    commissionCents: Long,
    productSalesCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // #97 Q2 Variant A — equal peers: two equal-weight cards, hairline border each.
        SummaryCard(
            label = "Gross income",
            value = "₱${centsToMoney(grossCents)}",
            sublabel = "Today · completed, non-voided",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Your commission",
            value = "₱${centsToMoney(commissionCents)}",
            sublabel = commissionLabel(productSalesCount),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.lg),
        colors =
            CardDefaults.cardColors(
                // surfaceVariant = Surface2 (#141516) — the Q2 card surface.
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            // eyebrow — ink-subtle labelSmall above the value (Q2 typography ladder).
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = InkSubtle,
            )
            // no lavender on values — ink only (data, not actions).
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}

@Composable
private fun LastUpdatedRow(lastUpdatedAt: Instant?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Q5a — timestamp of the LAST SUCCESSFUL refresh, never the last attempt.
            text = if (lastUpdatedAt == null) "" else "Updated ${formatTimeOfDay(lastUpdatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
private fun StaleBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.error.copy(alpha = STALE_BANNER_ALPHA),
    ) {
        Text(
            text = "Updates paused — showing the last loaded data",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}

@Composable
private fun EmptySessions(selectedBranchName: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No sessions yet today",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                "Sessions will appear here as practitioners log them." +
                    (selectedBranchName?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
    }
}

@Composable
private fun InPlaceCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            Text(actionLabel)
        }
    }
}

/**
 * Shared detail pane — desktop master-detail inline pane AND the mobile pushed
 * SessionDetail route render the same content (Q1 secondary fields: base price,
 * practitioners, next appointment, remarks, concerns).
 */
@Composable
fun SessionDetailContent(
    session: DashboardSessionResponse?,
    modifier: Modifier = Modifier,
) {
    if (session == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Select a session",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSubtle,
            )
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.clientName ?: "Unknown client",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (session.isWalkIn) {
                WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SessionTypeBadge(session)
            SessionStatusBadge(session)
            if (session.isVoided) {
                VoidedPill()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        DetailRow("Base price", "₱${session.basePrice}")
        DetailRow("Final price", "₱${session.finalPrice}")
        if (session.bookedAt != null) {
            DetailRow("Booked time", bookedTimeLabel(session.bookedAt))
        }
        session.nextAppointmentDate?.let { nextAppointment ->
            DetailRow("Next appointment", nextAppointment)
        }
        if (session.practitioners.isNotEmpty()) {
            DetailRow("Practitioners", "")
            session.practitioners.forEach { practitioner ->
                PractitionerRow(practitioner)
            }
        }
        session.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            DetailRow("Remarks", remarks)
        }
        if (session.concerns.isNotEmpty()) {
            DetailRow("Concerns", session.concerns.joinToString { it.label })
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun PractitionerRow(practitioner: DashboardPractitionerResponse) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg)) {
        Text(
            text = practitioner.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        practitioner.remarks?.takeIf { it.isNotBlank() }?.let { remarks ->
            Text(
                text = remarks,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}

@Composable
internal fun WalkInDot(
    voided: Boolean,
    modifier: Modifier = Modifier,
) {
    // Q3 — walk-in dot: lavender 70% when active, grey (InkSubtle) when voided.
    Surface(
        shape = CircleShape,
        color =
            if (voided) {
                InkSubtle
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = WALK_IN_DOT_ALPHA)
            },
        modifier = modifier.size(WALK_IN_DOT_SIZE.dp),
    ) {}
}

@Composable
internal fun SessionTypeBadge(session: DashboardSessionResponse) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (session.isVoided) {
                MaterialTheme.colorScheme.secondary.copy(alpha = MUTED_BADGE_BG_ALPHA)
            } else {
                MaterialTheme.colorScheme.secondary
            },
    ) {
        Text(
            text = session.sessionType,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (session.isVoided) {
                    InkSubtle.copy(alpha = MUTED_BADGE_FG_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun SessionStatusBadge(session: DashboardSessionResponse) {
    // Q3 — status pill stays when voided (status is real — COMPLETED is still COMPLETED),
    // just visually deferred to the VOIDED pill; the two axes are not collapsed.
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (session.isVoided) {
                MaterialTheme.colorScheme.secondary.copy(alpha = MUTED_BADGE_BG_ALPHA)
            } else {
                MaterialTheme.colorScheme.secondary
            },
    ) {
        Text(
            text = session.sessionStatus,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (session.isVoided) {
                    InkSubtle.copy(alpha = MUTED_BADGE_FG_ALPHA)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
internal fun VoidedPill() {
    // Q3 — VOIDED pill: Danger 35% bg + bright ink text (light rose, high contrast);
    // an annotation, not a colored status chip (chip vocabulary stays owned by status).
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.error.copy(alpha = VOIDED_PILL_BG_ALPHA),
    ) {
        Text(
            text = "VOIDED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/**
 * #97 Q3 — client name treatment: strikethrough + dimmest ink when voided; active rows use
 * full ink. Shared by the desktop table row and the mobile card.
 */
@Composable
internal fun ClientNameText(session: DashboardSessionResponse) {
    Text(
        text = session.clientName ?: "Unknown client",
        style = MaterialTheme.typography.bodyMedium,
        color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (session.isVoided) TextDecoration.LineThrough else TextDecoration.None,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun bookedTimeLabel(bookedAt: String?): String =
    if (bookedAt == null) {
        "—"
    } else {
        runCatching { formatTimeOfDay(Instant.parse(bookedAt)) }.getOrDefault("—")
    }

internal const val VOIDED_ROW_ALPHA = 0.22f

private const val WALK_IN_DOT_ALPHA = 0.7f
private const val MUTED_BADGE_BG_ALPHA = 0.4f
private const val MUTED_BADGE_FG_ALPHA = 0.5f
private const val VOIDED_PILL_BG_ALPHA = 0.35f
private const val STALE_BANNER_ALPHA = 0.22f
