package com.example.musicplayer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val Accents = listOf(
    Color(0xFF7C5CFF), Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047),
    Color(0xFFFB8C00), Color(0xFFD81B60), Color(0xFF00ACC1)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (Store.theme) {
        1 -> false
        2, 3 -> true
        else -> sysDark
    }
    val accent = Accents[Store.accent.coerceIn(0, Accents.lastIndex)]
    val scheme = if (dark) {
        val amoled = Store.theme == 3
        darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            onPrimary = Color.White,
            background = if (amoled) Color.Black else Color(0xFF121212),
            surface = if (amoled) Color.Black else Color(0xFF1C1C1E),
            surfaceVariant = if (amoled) Color(0xFF1A1A1A) else Color(0xFF2C2C2E)
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            onPrimary = Color.White,
            background = Color(0xFFFAFAFA),
            surface = Color.White,
            surfaceVariant = Color(0xFFE8E8EC)
        )
    }
    val d = LocalDensity.current
    // масштаб всего интерфейса: меняем плотность, поэтому растут и dp, и sp
    CompositionLocalProvider(LocalDensity provides Density(d.density * Store.scale, d.fontScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
