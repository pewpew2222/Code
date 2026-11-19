package com.chaptervoice.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R

/** One accent colour in a dark and a light version. */
data class Accent(
    val id: String,
    val label: String,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val light: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
)

val ACCENTS = listOf(
    Accent("blue", "Blå", Color(0xFF8AB4FF), Color(0xFF0B1B3A), Color(0xFF26355A), Color(0xFF2F5BD3), Color(0xFFDCE4FF), Color(0xFF0E2366)),
    Accent("purple", "Lila", Color(0xFFC3A6FF), Color(0xFF24104F), Color(0xFF3A2B63), Color(0xFF6A3FD1), Color(0xFFEADDFF), Color(0xFF26105E)),
    Accent("green", "Grön", Color(0xFF7FD6A8), Color(0xFF07301C), Color(0xFF1F4634), Color(0xFF1E7A4C), Color(0xFFD3F2E1), Color(0xFF06331D)),
    Accent("amber", "Bärnsten", Color(0xFFF2B880), Color(0xFF3A1F04), Color(0xFF4F3820), Color(0xFF9A5410), Color(0xFFFFE2C6), Color(0xFF3A1F04)),
    Accent("pink", "Rosa", Color(0xFFF5A3C7), Color(0xFF41102A), Color(0xFF5A2A42), Color(0xFFB0306A), Color(0xFFFFD9E8), Color(0xFF45102B)),
)

/** Literata, a typeface made for long reading, for the book's own text. */
val ReadingFont = FontFamily(Font(R.font.literata, FontWeight.Normal))

private fun darkScheme(accent: Accent): ColorScheme = darkColorScheme(
    // Accent only where something is clearly coloured (buttons, selection, highlights)...
    primary = accent.dark,
    onPrimary = accent.onDark,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondaryContainer = accent.darkContainer,
    onSecondaryContainer = Color(0xFFF2F2F2),
    // ...and pure neutral greys everywhere else, with no colour cast.
    background = Color(0xFF101010),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceTint = Color(0xFF101010),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF151515),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF2B2B2B),
    outline = Color(0xFF5E5E5E),
    outlineVariant = Color(0xFF333333),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF161616),
    scrim = Color.Black,
)

private fun lightScheme(accent: Accent): ColorScheme = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.onLightContainer,
    secondaryContainer = accent.lightContainer,
    onSecondaryContainer = accent.onLightContainer,
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF161616),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF555555),
    surfaceTint = Color(0xFFF7F7F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F1F1),
    surfaceContainer = Color(0xFFEBEBEB),
    surfaceContainerHigh = Color(0xFFE4E4E4),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFFD2D2D2),
    inverseSurface = Color(0xFF222222),
    inverseOnSurface = Color(0xFFF2F2F2),
    scrim = Color.Black,
)

/** Rounder corners everywhere, menus included. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val changes by AppSettings.themeChanges.collectAsStateWithLifecycle()
    val mode = remember(changes) { settings.themeMode }
    val accentId = remember(changes) { settings.accent }
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = if (accentId == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = ACCENTS.firstOrNull { it.id == accentId } ?: ACCENTS.first()
        if (dark) darkScheme(accent) else lightScheme(accent)
    }

    // Status and navigation bar icons follow the app's theme, not only the phone's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
