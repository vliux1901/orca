package org.vliux.orca.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue80,
    onPrimary = SkyBlue20,
    secondary = TealBlue80,
    onSecondary = TealBlue20,
    tertiary = CoralAccent80,
    onTertiary = CoralAccent20,
    background = BackgroundDark,
    onBackground = SurfaceLight,
    surface = SurfaceDark,
    onSurface = SurfaceLight,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SkyBlue80,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = SkyBlue40,
    onPrimary = SurfaceLight,
    secondary = TealBlue40,
    onSecondary = SurfaceLight,
    tertiary = CoralAccent40,
    onTertiary = SurfaceLight,
    background = BackgroundLight,
    onBackground = TealBlue20,
    surface = SurfaceLight,
    onSurface = TealBlue20,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TealBlue40,
    outline = OutlineLight
)

@Composable
fun OrcaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
