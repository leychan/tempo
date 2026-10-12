package io.github.leychan.tempo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.leychan.tempo.domain.ThemeMode

data class TempoColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val primaryContainer: Color,
    val controlSurface: Color,
    val outline: Color,
    val timerCard: Color,
    val timerText: Color = Color(0xFFF2F7F8),
    val timerMuted: Color = Color(0xFFA7B8BF),
)

private val DayColors = TempoColors(
    isDark = false, background = Color(0xFFF5F6F8), surface = Color.White,
    text = Color(0xFF1B2B36), muted = Color(0xFF657781), primary = Color(0xFF087F72),
    primaryContainer = Color(0xFFE0F3EF), controlSurface = Color(0xFFF0F5F4),
    outline = Color(0xFFD6E0E4), timerCard = Color(0xFF1B2B36),
)
private val NightColors = TempoColors(
    isDark = true, background = Color(0xFF10171C), surface = Color(0xFF1B262D),
    text = Color(0xFFE8F0F2), muted = Color(0xFFA6B8C1), primary = Color(0xFF83D9C7),
    primaryContainer = Color(0xFF173C36), controlSurface = Color(0xFF25363F),
    outline = Color(0xFF3C505B), timerCard = Color(0xFF14232D),
)
val LocalTempoColors = staticCompositionLocalOf { DayColors }

@Composable
fun TempoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) NightColors else DayColors
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalTempoColors provides colors) {
      MaterialTheme(
        colorScheme = scheme.copy(
            primary = colors.primary,
            onPrimary = if (dark) Color(0xFF063D33) else Color.White,
            primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.primary,
            secondary = colors.primary, onSecondary = if (dark) Color(0xFF063D33) else Color.White,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text, surfaceTint = Color.Transparent,
            surfaceVariant = colors.controlSurface, onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.controlSurface,
            outline = colors.outline, outlineVariant = colors.outline,
            error = if (dark) Color(0xFFFFB4AB) else Color(0xFFB44444),
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
        ),
        content = content,
    )
    }
}
