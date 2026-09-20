package com.velnox.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Velnox typography.
 *
 * The web system uses Inter for Latin and Noto Sans Thai / Myanmar for the Thai and
 * Burmese translations (`--font-sans` in `index.css`). Android ships Roboto, which
 * has no Thai or Burmese coverage, so the platform fallback chain handles those
 * scripts — Android substitutes Noto Sans Thai automatically. Rather than bundling
 * ~2 MB of webfonts into three APKs for a marginal gain, the family is left as the
 * system default and the *metrics* below are matched to the web scale, which is what
 * actually determines whether a screen feels like Velnox.
 *
 * Two deliberate adjustments for Thai and Burmese, taken from the web rule
 * `:lang(th), :lang(my) { line-height: 1.55 }`:
 *
 *  * Every style carries extra line height. Thai stacks vowels and tone marks above
 *    the baseline and Burmese has deep descenders; tight leading clips both.
 *  * `LineHeightStyle.Trim.None` prevents Compose from trimming that leading away.
 */
private val velnoxLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun velnoxStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = velnoxLineHeightStyle,
)

/**
 * Type scale derived from the web classes actually used across the four apps:
 * page titles `text-xl font-bold`, card titles `text-base font-semibold`, body
 * `text-sm`, meta `text-xs`, prices `text-lg font-bold`.
 */
val VelnoxTypography = Typography(
    displaySmall = velnoxStyle(size = 28, lineHeight = 38, weight = FontWeight.Bold, letterSpacing = -0.4),
    headlineMedium = velnoxStyle(size = 24, lineHeight = 34, weight = FontWeight.Bold, letterSpacing = -0.3),
    headlineSmall = velnoxStyle(size = 20, lineHeight = 30, weight = FontWeight.Bold, letterSpacing = -0.2),
    titleLarge = velnoxStyle(size = 18, lineHeight = 28, weight = FontWeight.SemiBold),
    titleMedium = velnoxStyle(size = 16, lineHeight = 26, weight = FontWeight.SemiBold),
    titleSmall = velnoxStyle(size = 14, lineHeight = 22, weight = FontWeight.SemiBold),
    bodyLarge = velnoxStyle(size = 16, lineHeight = 26, weight = FontWeight.Normal),
    bodyMedium = velnoxStyle(size = 14, lineHeight = 23, weight = FontWeight.Normal),
    bodySmall = velnoxStyle(size = 12, lineHeight = 20, weight = FontWeight.Normal),
    labelLarge = velnoxStyle(size = 14, lineHeight = 22, weight = FontWeight.SemiBold),
    labelMedium = velnoxStyle(size = 12, lineHeight = 20, weight = FontWeight.Medium),
    labelSmall = velnoxStyle(size = 11, lineHeight = 18, weight = FontWeight.Medium, letterSpacing = 0.2),
)

/** Price emphasis: the heaviest weight in the app, matching `text-lg font-bold`. */
val PriceTextStyle: TextStyle = velnoxStyle(size = 18, lineHeight = 26, weight = FontWeight.Bold)

/** Hero price for a product detail page. */
val HeroPriceTextStyle: TextStyle = velnoxStyle(size = 24, lineHeight = 32, weight = FontWeight.Bold)
