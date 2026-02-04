package me.weishu.kernelsu.ui.component

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.Route

enum class BottomBar(
    val route: Route,
    @StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val rootRequired: Boolean,
) {
    Home(Route.Home, R.string.home, Icons.Filled.Home, Icons.Outlined.Home, false),
    SuperUser(Route.SuperUser, R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield, true),
    Module(Route.Module, R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension, true),
    Settings(Route.Settings, R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings, false)
}