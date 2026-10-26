package com.tarun.managemyshop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors that Material 3's scheme has no slot for. */
@Immutable
data class ExtraColors(
    val positive: Color,
    val warning: Color,
    val negative: Color,
    val appBar: Color
)

private val LightExtra = ExtraColors(MoneyGreen, AlertOrange, AlertRed, AppBarLight)
private val DarkExtra = ExtraColors(MoneyGreenDark, AlertOrangeDark, Color(0xFFFFB4AB), AppBarDark)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Blue40,
    secondaryContainer = Blue90,
    background = BackgroundLight,
    surface = SurfaceWhite,
    surfaceContainer = SurfaceWhite,
    error = AlertRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo40,
    onPrimaryContainer = Indigo90,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline
)

@Composable
fun ManageMyShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

val MaterialTheme.extraColors: ExtraColors
    @Composable get() = LocalExtraColors.current
