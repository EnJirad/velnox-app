package com.velnox.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Velnox colour system.
 *
 * These are transcriptions of the real web tokens — `packages/shared/src/index.css`
 * (`:root` block) and `VELNOX_DESIGN_THEME.md` v2.0 — not a reinterpretation. Where
 * a token exists on web it keeps the same hex value here so the two surfaces stay
 * recognisably the same product.
 *
 * The semantic rules from `docs/ai/DESIGN.md` are encoded as named tokens rather
 * than left to call sites:
 *
 * | Use | Token |
 * |-----|-------|
 * | Buy Now | `VelnoxColors.BuyNow` (slate-900 on white) |
 * | Add to Cart | `VelnoxColors.AddToCart` (#10B981 on white) |
 * | Success / active | emerald |
 * | Destructive / error | red |
 * | Warning / pending | amber |
 * | Focus | emerald ring |
 *
 * Inventing a brand colour is explicitly disallowed by the design rules, so anything
 * not listed here should be composed from these tokens.
 */
object VelnoxColors {

    // ─── Surfaces ────────────────────────────────────────────────────────────
    /** `--background: #f8fafc` */
    val Background = Color(0xFFF8FAFC)

    /** `--card` / `--popover`: white surfaces are the default container. */
    val Surface = Color(0xFFFFFFFF)

    /** `--secondary` / `--muted`: subtle fills for chips, skeletons, insets. */
    val SurfaceMuted = Color(0xFFF1F5F9)

    // ─── Text ────────────────────────────────────────────────────────────────
    /** `--foreground: #0f172a` — also the primary action colour. */
    val OnSurface = Color(0xFF0F172A)

    /** `--muted-foreground: #64748b` */
    val OnSurfaceMuted = Color(0xFF64748B)

    /** `--secondary-foreground: #1e293b` */
    val OnSurfaceSecondary = Color(0xFF1E293B)

    val OnPrimary = Color(0xFFFFFFFF)

    /** Slate-400, used for placeholders and disabled labels. */
    val OnSurfaceDisabled = Color(0xFF94A3B8)

    // ─── Brand & semantic ────────────────────────────────────────────────────
    /** `--ring: #10b981` — the Velnox emerald. */
    val Emerald = Color(0xFF10B981)

    val EmeraldPressed = Color(0xFF059669)

    /** `--accent: #ecfdf5` */
    val EmeraldSurface = Color(0xFFECFDF5)

    /** `--accent-foreground: #047857` */
    val EmeraldOnSurface = Color(0xFF047857)

    /** `--primary: #0f172a` — Buy Now / primary actions. */
    val Primary = Color(0xFF0F172A)

    val PrimaryPressed = Color(0xFF1E293B)

    /** `--destructive: #dc2626` */
    val Destructive = Color(0xFFDC2626)

    val DestructiveSurface = Color(0xFFFEF2F2)

    /** Amber for pending / warning states. */
    val Warning = Color(0xFFF59E0B)

    val WarningSurface = Color(0xFFFFFBEB)

    val WarningOnSurface = Color(0xFFB45309)

    /** Info/sky, used for "under review" and shipped states. */
    val Info = Color(0xFF0EA5E9)

    val InfoSurface = Color(0xFFF0F9FF)

    val InfoOnSurface = Color(0xFF0369A1)

    // ─── Lines ───────────────────────────────────────────────────────────────
    /** `--border` / `--input: #e2e8f0` */
    val Border = Color(0xFFE2E8F0)

    val BorderStrong = Color(0xFFCBD5E1)

    /** Divider inside a card, one step lighter than [Border]. */
    val Divider = Color(0xFFF1F5F9)

    // ─── Component-specific ──────────────────────────────────────────────────
    /** Add-to-cart uses the brand emerald; Buy Now uses slate-900. */
    val AddToCart = Emerald

    val BuyNow = Primary

    /** Badge used for a verified seller/shop (V badge). */
    val VerifiedBadge = Emerald

    /** Stock-warning text; below the reorder level but still purchasable. */
    val LowStock = Warning

    val OutOfStock = OnSurfaceMuted

    /** Chart palette, matching `--chart-*` so dashboards match the web reports. */
    val ChartPalette = listOf(
        Emerald,
        Primary,
        Warning,
        OnSurfaceMuted,
        EmeraldPressed,
    )
}
