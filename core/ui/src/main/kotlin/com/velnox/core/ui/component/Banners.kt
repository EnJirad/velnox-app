package com.velnox.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.velnox.core.ui.R
import com.velnox.core.ui.theme.VelnoxColors

/**
 * Tone of an inline notice.
 *
 * Deliberately a small closed set: a screen must pick one of the product's existing
 * semantic colours, not invent a fifth one.
 */
enum class VelnoxBannerTone {
    /** Neutral information — a cached-data notice, an explanation. */
    Info,

    /** The action succeeded (added to cart, order placed, status changed). */
    Success,

    /** The action failed. [VelnoxMessageBanner] renders the backend's own message. */
    Error,

    /** Something the user must correct, but which is not a failure yet. */
    Warning,
}

/**
 * Inline, dismissible notice.
 *
 * ## Why a banner and not a Snackbar or a Toast
 *
 *  * The feedback belongs to the screen that produced it. A Snackbar is anchored to
 *    the scaffold and can be missed or obscured by the bottom bar, and the mandatory
 *    offline/loading/error states already occupy that same visual channel.
 *  * A `Toast` is outside the Compose tree, so it survives navigation and can appear
 *    over a different screen than the one that caused it — actively misleading after
 *    a checkout.
 *
 * The banner is a plain state-driven row, so it disappears exactly when the screen
 * clears the message.
 */
@Composable
fun VelnoxMessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: VelnoxBannerTone = VelnoxBannerTone.Info,
    onDismiss: (() -> Unit)? = null,
) {
    val background: Color = when (tone) {
        VelnoxBannerTone.Info -> VelnoxColors.InfoSurface
        VelnoxBannerTone.Success -> VelnoxColors.EmeraldSurface
        VelnoxBannerTone.Error -> VelnoxColors.DestructiveSurface
        VelnoxBannerTone.Warning -> VelnoxColors.WarningSurface
    }
    val content: Color = when (tone) {
        VelnoxBannerTone.Info -> VelnoxColors.InfoOnSurface
        VelnoxBannerTone.Success -> VelnoxColors.EmeraldOnSurface
        VelnoxBannerTone.Error -> VelnoxColors.Destructive
        VelnoxBannerTone.Warning -> VelnoxColors.WarningOnSurface
    }
    val icon: ImageVector = when (tone) {
        VelnoxBannerTone.Info -> Icons.Filled.Info
        VelnoxBannerTone.Success -> Icons.Filled.CheckCircle
        VelnoxBannerTone.Error -> Icons.Filled.ErrorOutline
        VelnoxBannerTone.Warning -> Icons.Filled.Info
    }

    Surface(
        color = background,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.velnox_action_close),
                        tint = content,
                    )
                }
            }
        }
    }
}
