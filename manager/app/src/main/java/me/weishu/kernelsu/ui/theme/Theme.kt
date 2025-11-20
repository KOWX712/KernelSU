package me.weishu.kernelsu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import me.weishu.kernelsu.ui.webui.MonetColorsProvider.updateCss
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun KernelSUTheme(
    colorMode: Int = 0,
    keyColor: Color? = null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val controller = when (colorMode) {
        1 -> ThemeController(ColorSchemeMode.Light)
        2 -> ThemeController(ColorSchemeMode.Dark)
        3 -> ThemeController(
            ColorSchemeMode.MonetSystem,
            keyColor = keyColor,
            isDark = isDark
        )

        4 -> ThemeController(
            ColorSchemeMode.MonetLight,
            keyColor = keyColor,
        )

        5 -> ThemeController(
            ColorSchemeMode.MonetDark,
            keyColor = keyColor,
        )

        else -> ThemeController(ColorSchemeMode.System)
    }
    val isDarkTheme = when (colorMode) {
        1 -> false // Light
        2 -> true  // Dark
        3 -> isSystemInDarkTheme() // MonetSystem
        4 -> false // MonetLight
        5 -> true  // MonetDark
        else -> isSystemInDarkTheme() // System
    }
    return MiuixTheme(
        controller = controller,
        content = {
            val activity = LocalActivity.current
            val window = activity?.window
            if (window != null) {
                SideEffect {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
                    windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                    windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
            updateCss()
            content()
        }
    )
}
