package com.velnox.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velnox.core.common.domain.OrderStatus
import com.velnox.core.common.domain.PaymentStatus
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.domain.SellerStatus
import com.velnox.core.common.domain.VerificationStatus
import com.velnox.core.ui.R
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens

/** White card on the `#f8fafc` page, `rounded-2xl`, restrained shadow. */
@Composable
fun VelnoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = VelnoxColors.Surface)
    val shape = MaterialTheme.shapes.large
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)

    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, shape = shape, elevation = elevation) {
            Column(Modifier.padding(VelnoxTokens.spacing.cardPadding)) { content() }
        }
    } else {
        Card(modifier = modifier, colors = colors, shape = shape, elevation = elevation) {
            Column(Modifier.padding(VelnoxTokens.spacing.cardPadding)) { content() }
        }
    }
}

/** Section title with an optional trailing action, as used across the dashboards. */
@Composable
fun VelnoxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = VelnoxColors.OnSurface)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = VelnoxColors.EmeraldOnSurface,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickableText(onAction),
            )
        }
    }
}

/** A label/value row, used by every detail screen. */
@Composable
fun VelnoxInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = VelnoxColors.OnSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VelnoxColors.OnSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
fun VelnoxDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = VelnoxColors.Divider)
}

/** Neutral chip for short metadata (category, unit, shop name). */
@Composable
fun VelnoxChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = VelnoxColors.SurfaceMuted,
    contentColor: Color = VelnoxColors.OnSurfaceSecondary,
) {
    Surface(color = background, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ─── Status badges ───────────────────────────────────────────────────────────

/**
 * Status badges.
 *
 * Colours follow `ORDER_STATUS_META` in `packages/shared/src/lib/commerce.ts` so an
 * order looks identical in Velshop, Velseller and VelCenter, and in the web apps.
 * Labels come from string resources (never a raw wire value), except for
 * `Unknown`, which renders the backend's own value so an unexpected state is visible
 * instead of being mislabelled as a known one.
 */
@Composable
fun OrderStatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        OrderStatus.Pending -> Triple(stringResource(R.string.velnox_order_pending), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        OrderStatus.Confirmed -> Triple(stringResource(R.string.velnox_order_confirmed), VelnoxColors.InfoSurface, VelnoxColors.InfoOnSurface)
        OrderStatus.Shipped -> Triple(stringResource(R.string.velnox_order_shipped), VelnoxColors.InfoSurface, VelnoxColors.InfoOnSurface)
        OrderStatus.Delivered -> Triple(stringResource(R.string.velnox_order_delivered), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        OrderStatus.Completed -> Triple(stringResource(R.string.velnox_order_completed), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        OrderStatus.Cancelled -> Triple(stringResource(R.string.velnox_order_cancelled), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
        OrderStatus.Unknown -> Triple(stringResource(R.string.velnox_order_unknown), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
    }
    VelnoxStatusPill(label = label, background = background, contentColor = content, modifier = modifier)
}

@Composable
fun PaymentStatusBadge(status: PaymentStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        PaymentStatus.Paid -> Triple(stringResource(R.string.velnox_payment_paid), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        PaymentStatus.Unpaid -> Triple(stringResource(R.string.velnox_payment_unpaid), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
        PaymentStatus.Pending -> Triple(stringResource(R.string.velnox_payment_pending), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        PaymentStatus.PartiallyRefunded, PaymentStatus.Refunded ->
            Triple(stringResource(R.string.velnox_payment_refunded), VelnoxColors.InfoSurface, VelnoxColors.InfoOnSurface)
        PaymentStatus.Failed -> Triple(stringResource(R.string.velnox_payment_failed), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        PaymentStatus.Unknown -> Triple(stringResource(R.string.velnox_payment_unknown), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
    }
    VelnoxStatusPill(label = label, background = background, contentColor = content, modifier = modifier)
}

@Composable
fun ProductStatusBadge(status: ProductStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        ProductStatus.Published -> Triple(stringResource(R.string.velnox_product_published), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        ProductStatus.Draft -> Triple(stringResource(R.string.velnox_product_draft), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
        ProductStatus.PendingReview -> Triple(stringResource(R.string.velnox_product_pending_review), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        ProductStatus.Rejected -> Triple(stringResource(R.string.velnox_product_rejected), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        ProductStatus.Suspended -> Triple(stringResource(R.string.velnox_product_suspended), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        ProductStatus.Archived -> Triple(stringResource(R.string.velnox_product_archived), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
        ProductStatus.Unknown -> Triple(stringResource(R.string.velnox_product_unknown), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
    }
    VelnoxStatusPill(label = label, background = background, contentColor = content, modifier = modifier)
}

@Composable
fun SellerStatusBadge(status: SellerStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        SellerStatus.Approved -> Triple(stringResource(R.string.velnox_seller_approved), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        SellerStatus.Pending -> Triple(stringResource(R.string.velnox_seller_pending), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        SellerStatus.UnderReview -> Triple(stringResource(R.string.velnox_seller_under_review), VelnoxColors.InfoSurface, VelnoxColors.InfoOnSurface)
        SellerStatus.NeedsCorrection -> Triple(stringResource(R.string.velnox_seller_needs_correction), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        SellerStatus.Rejected -> Triple(stringResource(R.string.velnox_seller_rejected), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        SellerStatus.Suspended -> Triple(stringResource(R.string.velnox_seller_suspended), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        SellerStatus.Unknown -> Triple(stringResource(R.string.velnox_seller_unknown), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
    }
    VelnoxStatusPill(label = label, background = background, contentColor = content, modifier = modifier)
}

/** The V badge. Only rendered for a genuinely verified seller/shop. */
@Composable
fun VerificationBadge(status: VerificationStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        VerificationStatus.Verified -> Triple(stringResource(R.string.velnox_verified), VelnoxColors.EmeraldSurface, VelnoxColors.EmeraldOnSurface)
        VerificationStatus.Pending -> Triple(stringResource(R.string.velnox_verification_pending), VelnoxColors.WarningSurface, VelnoxColors.WarningOnSurface)
        VerificationStatus.Rejected -> Triple(stringResource(R.string.velnox_verification_rejected), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        VerificationStatus.Suspended -> Triple(stringResource(R.string.velnox_verification_suspended), VelnoxColors.DestructiveSurface, VelnoxColors.Destructive)
        VerificationStatus.Unverified, VerificationStatus.Unknown ->
            Triple(stringResource(R.string.velnox_not_verified), VelnoxColors.SurfaceMuted, VelnoxColors.OnSurfaceMuted)
    }
    VelnoxStatusPill(label = label, background = background, contentColor = content, modifier = modifier)
}

@Composable
private fun VelnoxStatusPill(
    label: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = background, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(color = contentColor, shape = CircleShape, modifier = Modifier.size(6.dp)) {}
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
