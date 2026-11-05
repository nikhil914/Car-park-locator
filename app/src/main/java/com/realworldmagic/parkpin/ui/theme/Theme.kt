package com.realworldmagic.parkpin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Indigo80,
    onSecondary = Indigo20,
    secondaryContainer = Indigo30,
    onSecondaryContainer = Indigo90,
    tertiary = Magenta80,
    onTertiary = Magenta30,
    tertiaryContainer = Magenta30,
    onTertiaryContainer = Magenta90,
    background = Night,
    onBackground = Indigo90,
    surface = Night,
    onSurface = Indigo90,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Indigo80,
    surfaceContainerLowest = Indigo10,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = Indigo30,
    error = ErrorRedLight,
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    secondary = Indigo40,
    onSecondary = Color.White,
    secondaryContainer = Indigo90,
    onSecondaryContainer = Indigo20,
    tertiary = Magenta40,
    onTertiary = Color.White,
    tertiaryContainer = Magenta90,
    onTertiaryContainer = Magenta30,
    background = Mist,
    onBackground = Indigo10,
    surface = Mist,
    onSurface = Indigo10,
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = Indigo30,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun ParkPinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = ParkPinTypography, content = content)
}
