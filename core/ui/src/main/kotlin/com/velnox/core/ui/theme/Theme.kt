package com.velnox.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Velnox theme.
 *
 * ## Light only, on purpose
 *
 * Velnox V2 is a white-surface system: `#f8fafc` page, white cards, slate text. The
 * web apps ship exactly one theme (the `.dark` block in `index.css` exists for the
 * shadcn base, but no Velnox app enables it). Following the system dark setting would
 * produce a look that exists nowhere in the product, which the design rules forbid.
 * The dark mapping below is a legibility floor, not a second brand.
 *
 * ## Why a CompositionLocal for spacing
 *
 * Spacing is the one part of the system Material3 does not carry. Exposing it as
 * [LocalVelnoxSpacing] keeps every screen on one rhythm and makes "does this still
 * look like Velnox?" answerable in a single file.
 */
private val VelnoxLightColors = lightColorScheme(
    primary = VelnoxColors.Primary,
    onPrimary = VelnoxColors.OnPrimary,
    primaryContainer = VelnoxColors.SurfaceMuted,
    onPrimaryContainer = VelnoxColors.OnSurface,
    secondary = VelnoxColors.Emerald,
    onSecondary = VelnoxColors.OnPrimary,
    secondaryContainer = VelnoxColors.EmeraldSurface,
    onSecondaryContainer = VelnoxColors.EmeraldOnSurface,
    tertiary = VelnoxColors.Info,
    onTertiary = VelnoxColors.OnPrimary,
    tertiaryContainer = VelnoxColors.InfoSurface,
    onTertiaryContainer = VelnoxColors.InfoOnSurface,
    background = VelnoxColors.Background,
    onBackground = VelnoxColors.OnSurface,
    surface = VelnoxColors.Surface,
    onSurface = VelnoxColors.OnSurface,
    surfaceVariant = VelnoxColors.SurfaceMuted,
    onSurfaceVariant = VelnoxColors.OnSurfaceMuted,
    error = VelnoxColors.Destructive,
    onError = VelnoxColors.OnPrimary,
    errorContainer = VelnoxColors.DestructiveSurface,
    onErrorContainer = VelnoxColors.Destructive,
    outline = VelnoxColors.BorderStrong,
    outlineVariant = VelnoxColors.Border,
)

/** Legibility floor only — mirrors the web `.dark` token block, unused in production. */
private val VelnoxDarkColors = darkColorScheme(
    primary = VelnoxColors.OnPrimary,
    onPrimary = VelnoxColors.OnSurface,
    secondary = VelnoxColors.Emerald,
    onSecondary = VelnoxColors.OnSurface,
    background = Color(0xFF0B1120),
    onBackground = VelnoxColors.OnPrimary,
    surface = Color(0xFF111827),
    onSurface = VelnoxColors.OnPrimary,
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = VelnoxColors.OnSurfaceDisabled,
    error = VelnoxColors.Destructive,
    onError = VelnoxColors.OnPrimary,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1F2937),
)

val LocalVelnoxSpacing = staticCompositionLocalOf { DefaultVelnoxSpacing }

val LocalVelnoxDarkMode = staticCompositionLocalOf { false }

@Composable
fun VelnoxTheme(
    /**
     * Defaults to `false`: Velnox is a light-only product. Pass `true` only from a
     * preview that needs to prove the fallback palette stays legible.
     */
    darkTheme: Boolean = false,
    spacing: VelnoxSpacing = DefaultVelnoxSpacing,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVelnoxSpacing provides spacing,
        LocalVelnoxDarkMode provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) VelnoxDarkColors else VelnoxLightColors,
            typography = VelnoxTypography,
            shapes = VelnoxShapes,
            content = content,
        )
    }
}

/** Non-composable accessors, mirroring the familiar `MaterialTheme.*` shape. */
object VelnoxTokens {
    val spacing: VelnoxSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalVelnoxSpacing.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalVelnoxDarkMode.current
}
