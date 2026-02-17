package me.weishu.kernelsu.ui.screen.appprofile

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

@Composable
fun AppProfileScreen(packageName: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AppProfileScreenMiuix(packageName)
        UiMode.Material -> AppProfileScreenMaterial(packageName)
    }
}
