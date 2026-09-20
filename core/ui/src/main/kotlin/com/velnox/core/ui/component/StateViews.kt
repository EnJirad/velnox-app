package com.velnox.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.velnox.core.common.error.AppError
import com.velnox.core.ui.R
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens

/**
 * The project's mandatory screen states.
 *
 * The brief requires every data screen to be able to render Loading, Error, Retry,
 * Offline, Empty and Success, and forbids showing fabricated content when the API
 * fails. [VelnoxScreenState] is how that rule is enforced structurally: a screen can
 * only present content by first passing through an explicit state, so "forgot the
 * error case" becomes a compile-time omission rather than a blank screen.
 *
 * Nothing in here ever renders sample data for a failed load. When [Error] has no
 * message of its own it uses generic copy, never the last successful payload.
 */
sealed interface VelnoxScreenState<out T> {
    data object Loading : VelnoxScreenState<Nothing>
    data object Empty : VelnoxScreenState<Nothing>
    data class Content<T>(val value: T, val isCached: Boolean = false) : VelnoxScreenState<T>
    data class Failure(val error: AppError) : VelnoxScreenState<Nothing>
}

@Composable
fun VelnoxLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = VelnoxColors.Emerald,
                strokeWidth = 3.dp,
            )
            Text(
                text = label ?: stringResource(R.string.velnox_state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = VelnoxColors.OnSurfaceMuted,
            )
        }
    }
}

/**
 * The single failure screen.
 *
 * The kind of error drives the copy and the icon, so an offline device never shows
 * "server error" and a 403 never shows a retry button that cannot possibly work:
 *
 *  * [AppError.Offline] / [AppError.Timeout] → offline wording, retry offered.
 *  * [AppError.Forbidden] → access-denied wording, retry pointless.
 *  * [AppError.NotFound] → not-found wording, retry pointless.
 *  * everything else → generic error, retry offered.
 *
 * The backend's own message is preferred when it exists, because the backend already
 * localises it; [fallbackMessage] is used only when it does not.
 */
@Composable
fun VelnoxErrorState(
    error: AppError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fallbackMessage: String? = null,
) {
    val offline = error is AppError.Offline || error is AppError.Timeout
    val forbidden = error is AppError.Forbidden
    val notFound = error is AppError.NotFound

    val icon: ImageVector = when {
        offline -> Icons.Filled.CloudOff
        forbidden -> Icons.Filled.Lock
        notFound -> Icons.Filled.SearchOff
        else -> Icons.Filled.WarningAmber
    }

    val title = when {
        offline -> stringResource(R.string.velnox_state_offline_title)
        forbidden -> stringResource(R.string.velnox_state_forbidden_title)
        notFound -> stringResource(R.string.velnox_state_not_found_title)
        else -> stringResource(R.string.velnox_state_error_title)
    }

    val body = when {
        offline -> stringResource(R.string.velnox_state_offline_body)
        forbidden -> stringResource(R.string.velnox_state_forbidden_body)
        // Prefer the server's own (already localised) message when it exists.
        else -> error.serverMessage
            ?: fallbackMessage
            ?: stringResource(R.string.velnox_state_error_body)
    }

    VelnoxMessageState(
        icon = icon,
        title = title,
        body = body,
        modifier = modifier,
        onRetry = if (forbidden || notFound) null else onRetry,
    )
}

@Composable
fun VelnoxEmptyState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.velnox_state_empty_title),
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    VelnoxMessageState(
        icon = Icons.Filled.Inbox,
        title = title,
        body = body,
        modifier = modifier,
        onRetry = onAction,
        retryLabel = actionLabel,
    )
}

/** Shared layout for every non-content state, so they cannot drift apart. */
@Composable
private fun VelnoxMessageState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)?,
    retryLabel: String? = null,
) {
    val spacing = VelnoxTokens.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.gap),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VelnoxColors.OnSurfaceDisabled,
                modifier = Modifier.padding(bottom = spacing.gapTiny),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = VelnoxColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = spacing.gapSmall),
                ) {
                    Text(retryLabel ?: stringResource(R.string.velnox_state_retry))
                }
            }
        }
    }
}

/**
 * Inline offline strip.
 *
 * Sits above content that is being shown from the cache. It is informational: the
 * cached rows keep the screen useful, but the user is told they are not live.
 */
@Composable
fun VelnoxOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.velnox_state_offline_title),
    onRetry: (() -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        color = VelnoxColors.WarningSurface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = VelnoxColors.WarningOnSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.WarningOnSurface,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.velnox_state_retry))
                }
            }
        }
    }
}

/** Renders one of the mandatory states around [content]. */
@Composable
fun <T> VelnoxStateHost(
    state: VelnoxScreenState<T>,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    emptyTitle: String = stringResource(R.string.velnox_state_empty_title),
    emptyBody: String? = null,
    content: @Composable (T, Boolean) -> Unit,
) {
    when (state) {
        is VelnoxScreenState.Loading -> VelnoxLoadingState(modifier = modifier)
        is VelnoxScreenState.Empty -> VelnoxEmptyState(
            modifier = modifier,
            title = emptyTitle,
            body = emptyBody,
        )
        is VelnoxScreenState.Failure -> VelnoxErrorState(
            error = state.error,
            onRetry = onRetry,
            modifier = modifier,
        )
        is VelnoxScreenState.Content -> content(state.value, state.isCached)
    }
}
