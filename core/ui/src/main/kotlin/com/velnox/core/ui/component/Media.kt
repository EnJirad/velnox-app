package com.velnox.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.velnox.core.ui.R
import com.velnox.core.ui.theme.VelnoxColors

/**
 * Product/catalogue image.
 *
 * The brief requires loading, error and placeholder states plus caching for every
 * image. `SubcomposeAsyncImage` is used rather than a bare `AsyncImage` because a
 * shimmer placeholder and an explicit error glyph are otherwise impossible to
 * provide — and a blank box is indistinguishable from a broken product.
 *
 * URLs always point at Cloudflare R2 (via `R2_PUBLIC_DOMAIN`, resolved by the
 * backend). The app never signs or constructs an R2 URL itself: no R2 credential
 * exists in the APK, by design.
 */
@Composable
fun VelnoxImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp,
) {
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .clip(if (cornerRadius > 0.dp) shape else MaterialTheme.shapes.extraSmall)
            .background(VelnoxColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            // No URL is not an error — it is a product without a photo yet.
            PlaceholderArtwork()
            return@Box
        }

        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = { ShimmerBox() },
            error = {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = stringResource(R.string.velnox_image_failed),
                    tint = VelnoxColors.OnSurfaceDisabled,
                )
            },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

/** Circular avatar with initials fallback, used by every profile surface. */
@Composable
fun VelnoxAvatar(
    url: String?,
    displayName: String,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VelnoxColors.EmeraldSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            if (initials.isBlank()) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = stringResource(R.string.velnox_avatar_of, displayName),
                    tint = VelnoxColors.EmeraldOnSurface,
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.EmeraldOnSurface,
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.velnox_avatar_of, displayName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlaceholderArtwork() {
    Icon(
        imageVector = Icons.Filled.Image,
        contentDescription = null,
        tint = VelnoxColors.OnSurfaceDisabled,
    )
}

/**
 * Loading state for an image.
 *
 * A flat muted box rather than an animated shimmer: a list of animated placeholders
 * is a measurable source of jank on mid-range devices, and the brief puts
 * recomposition cost and startup time ahead of decoration.
 */
@Composable
private fun ShimmerBox() {
    Box(modifier = Modifier.fillMaxSize().background(VelnoxColors.SurfaceMuted))
}

/** `true` while Coil is still fetching, for callers that need to react to it. */
val AsyncImagePainter.State.isLoadingState: Boolean
    get() = this is AsyncImagePainter.State.Loading
