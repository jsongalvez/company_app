package com.companyb.companyapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

// Material-standard dark-theme hover state-layer alpha (material3's HoverStateLayer): a
// low-alpha ink wash that reads as a one-step surface lift over every ladder rung
// (surface…surface-4), voided-tint rows included — no palette color beyond DESIGN.md's tokens.
private const val ROW_HOVER_STATE_LAYER = 0.08f

/**
 * #398 — visible pointer-hover indication for clickable row-class surfaces.
 *
 * Bare `Modifier.clickable` rides the default ripple, whose hover state layer is
 * imperceptible on the dark canvas; this adds an explicit state layer while hovered. Owns a
 * private interaction source via [hoverable], so call sites keep their existing clickable
 * chains untouched. Inert without pointer hover — touch/mobile behavior is unchanged.
 *
 * Place directly after `.clickable(...)` and before paddings/overlays that belong under the
 * wash — except where the row paints its own opaque background after the clickable (e.g.
 * Finance DayRow's selection fill), where it goes last so the background cannot cover it.
 */
@Composable
fun Modifier.rowHover(
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
): Modifier {
    if (!enabled) {
        return this
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    return this.hoverable(interactionSource = interactionSource).then(
        if (isHovered) {
            Modifier.background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ROW_HOVER_STATE_LAYER),
                shape = shape,
            )
        } else {
            Modifier
        },
    )
}
