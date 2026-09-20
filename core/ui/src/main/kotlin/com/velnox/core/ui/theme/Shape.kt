package com.velnox.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape scale.
 *
 * Web uses `rounded-2xl` (16 dp) for cards and `rounded-[10px]` for controls
 * (`VELNOX_DESIGN_THEME.md`), with `--radius: 0.75rem` (12 dp) as the base token.
 * Those three values anchor the scale so a card and a button look like the same
 * system on both platforms.
 */
val VelnoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Spacing scale.
 *
 * The web rhythm is `px-4 py-8 sm:px-6 sm:py-10` for page gutters and
 * `gap-2`–`gap-4`, `p-5`–`p-6` inside cards. Exposed as a CompositionLocal-backed
 * object (see [VelnoxTheme]) so screens never hard-code a magic number and a single
 * change reflows all three apps coherently.
 */
data class VelnoxSpacing(
    /** Page gutter. */
    val screenHorizontal: Dp = 16.dp,
    /** Top padding under the app bar. */
    val screenTop: Dp = 16.dp,
    /** Bottom padding above the tab bar. */
    val screenBottom: Dp = 24.dp,
    val cardPadding: Dp = 20.dp,
    val gapTiny: Dp = 4.dp,
    val gapSmall: Dp = 8.dp,
    val gap: Dp = 12.dp,
    val gapLarge: Dp = 16.dp,
    val gapSection: Dp = 24.dp,
    /** Height of the app bar. */
    val appBarHeight: Dp = 56.dp,
    /** Height of a primary action button — 48 dp is the touch-target floor. */
    val buttonHeight: Dp = 48.dp,
)

val DefaultVelnoxSpacing = VelnoxSpacing()
