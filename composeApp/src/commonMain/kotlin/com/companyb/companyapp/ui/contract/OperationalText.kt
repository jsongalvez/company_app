package com.companyb.companyapp.ui.contract

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * #670 — readable hierarchy: large type is reserved for page/current-object/decisive
 * values; secondary information recedes. Large headings keep Inter via the Linear
 * title/body slots (auth screens previously read M3-default headlineLarge).
 *
 * Desktop body 14sp, touch body 16sp; secondary labels floor at 12sp per the contract.
 *
 * Headings contribute no composed animation; type changes apply instantly.
 */
@Composable
fun PageHeading(
    text: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    // #670 — compact step-down is viewport-driven: callers stay declarative while 390px
    // layouts automatically take the 24sp tier (downstream adaptive shell #671 owns grouping).
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = isCompact ?: OperationalUiContract.isCompactViewport(maxWidth)
        Text(
            text = text,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = OperationalUiContract.pageHeadingSize(compact),
                    lineHeight = OperationalUiContract.pageHeadingLineHeight(compact),
                ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CurrentObjectHeading(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = OperationalUiContract.currentObject,
            ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SecondaryLabel(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
