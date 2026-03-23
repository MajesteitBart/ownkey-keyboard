package nl.bartvandermeeren.ownkey.wear.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val AppBackground = Color(0xFFF6F3EE)
val AppSurface = Color(0xFFFFFCF8)
val AppSurfaceMuted = Color(0xFFF0E9DF)
val AppSurfaceStrong = Color(0xFFE7DDD0)

val AppPrimary = Color(0xFFBE7C47)
val AppPrimaryContainer = Color(0xFFF3D9C2)
val AppSecondary = Color(0xFF4B6A5D)
val AppTertiary = Color(0xFF5B6F92)

val AppOnSurface = Color(0xFF201A16)
val AppOnSurfaceMuted = Color(0xFF5A5049)
val AppOnPrimary = Color(0xFFFFFFFF)

val OwnkeyWearColorScheme = lightColorScheme(
    background = AppBackground,
    onBackground = AppOnSurface,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = AppSurfaceMuted,
    onSurfaceVariant = AppOnSurfaceMuted,
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    primaryContainer = AppPrimaryContainer,
    onPrimaryContainer = AppOnSurface,
    secondary = AppSecondary,
    tertiary = AppTertiary,
)
