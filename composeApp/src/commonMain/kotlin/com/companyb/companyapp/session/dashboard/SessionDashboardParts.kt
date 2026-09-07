package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.screen.centsToMoney
import com.companyb.companyapp.ui.screen.commissionLabel
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun SummaryCardsRow(
    grossCents: Long,
    commissionCents: Long,
    productSalesCount: Int,
    sessionCount: Int,
) {
    Row(
        // IntrinsicSize.Min: the VerticalDivider's fillMaxHeight must size the Row to the
        // cards' intrinsic height — otherwise the divider forces the Row to the full pane
        // and the list below starves to zero height (the #144 layout class; pass-2 HARD).
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // #97 Q2 Variant A — equal peers: equal-weight cards + thin vertical hairlines
        // between them, hairline border each (pass-2 restored the locked treatment).
        // #446 — third peer: session count from the same dashboard fetch.
        SummaryCard(
            label = "Gross income",
            value = "₱${centsToMoney(grossCents)}",
            sublabel = "Today · completed, non-voided",
            // fillMaxHeight (within the IntrinsicSize.Min row): equal card heights so the
            // divider spans flush even when sublabels wrap to different line counts.
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxHeight(),
        )
        SummaryCard(
            label = "Your commission",
            value = "₱${centsToMoney(commissionCents)}",
            sublabel = commissionLabel(productSalesCount),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        VerticalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxHeight(),
        )
        SummaryCard(
            label = "Sessions",
            value = sessionCount.toString(),
            sublabel = "Today · total sessions",
            modifier = Modifier.weight(1f).fillMaxHeight(),
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
        // Q2 — hairline border on the card surface.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
internal fun InPlaceCard(
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
