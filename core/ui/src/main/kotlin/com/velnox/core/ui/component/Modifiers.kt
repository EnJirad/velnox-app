package com.velnox.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Clickable text without the ripple that a full `clickable` would add to a label.
 *
 * Used for inline "see all" actions where a ripple across a single word looks like a
 * rendering bug rather than feedback. Still announces itself to accessibility
 * services because `onClick` is provided.
 */
fun Modifier.clickableText(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
