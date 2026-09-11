package com.vorht.capture.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Vorht design system — synced with landing/app/globals.css and DESIGN.md
 * (Air.inc style reference: midnight sky, flat surfaces, restrained type).
 *
 * Type: single sans (system) at weight 500; cursive italic accent for emphasis.
 * Radius: cards 12, buttons 8, inputs 4, pills full.
 * No elevation — surfaces separate by background + 1px borders only.
 */
object VorhtColors {
    // Sky backgrounds (globals.css :root)
    val SkyBody = Color(0xFF13396A)      // body background
    val SkyHero = Color(0xFF1C3F7A)      // --sky-hero
    val SkyMid = Color(0xFF1D3F72)       // --sky-mid — raised surfaces
    val SkyDeep = Color(0xFF0D1B3E)      // --sky-dark — deep sections

    // Air tokens
    val Whiteout = Color(0xFFFFFFFF)     // --color-whiteout — primary text on sky
    val Haze = Color(0xFFF5F5F5)         // --color-haze — light cards / inputs
    val Ink = Color(0xFF1B1B1B)          // --color-ink — text on light surfaces
    val Void = Color(0xFF000000)         // --color-void
    val TwilightBlue = Color(0xFF426188) // --color-twilight — headings on dark
    val SignalBlue = Color(0xFF2B7FFF)   // --color-signal — links/accents only

    // Derived
    val Hairline = Color(0x33FFFFFF)     // 1px borders on sky (whiteout 20%)
    val PillSurface = Color(0x1A000000)  // pill toggle: ~10% black
    val OkGreen = Color(0xFF7EE08B)
    val WarnAmber = Color(0xFFF2B75C)
    val ErrRed = Color(0xFFF26D6D)
}

private val VorhtScheme = darkColorScheme(
    primary = VorhtColors.Whiteout,
    onPrimary = VorhtColors.Ink,
    background = VorhtColors.SkyBody,
    onBackground = VorhtColors.Whiteout,
    surface = VorhtColors.SkyMid,
    onSurface = VorhtColors.Whiteout,
    surfaceVariant = VorhtColors.Haze,
    onSurfaceVariant = VorhtColors.Ink,
    surfaceContainer = VorhtColors.SkyMid,
    surfaceContainerHigh = VorhtColors.SkyHero,
    surfaceContainerHighest = VorhtColors.SkyDeep,
    outline = VorhtColors.Hairline,
    secondary = VorhtColors.TwilightBlue,
    onSecondary = VorhtColors.Whiteout,
    error = VorhtColors.ErrRed,
    tertiary = VorhtColors.SignalBlue,
)

// Radius scale from DESIGN.md: inputs 4, buttons 8, cards 12
private val VorhtShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // inputs
    small = RoundedCornerShape(8.dp),      // buttons, chips
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),     // cards
    extraLarge = RoundedCornerShape(12.dp),
)

// Weight-500 sans carries everything; 400 for long-form body.
private fun vorhtTypography(base: Typography): Typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight(500)),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight(500)),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight(500)),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight(500)),
    bodyMedium = base.bodyMedium.copy(fontWeight = FontWeight(400), lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontWeight = FontWeight(400)),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight(500)),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight(500)),
)

/** Cursive italic accent (Caveat-style) for emphasized words — use sparingly. */
val CursiveAccent = TextStyle(
    fontFamily = FontFamily.Cursive,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight(500),
)

@Composable
fun VorhtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VorhtScheme,
        typography = vorhtTypography(Typography()),
        shapes = VorhtShapes,
        content = content,
    )
}
