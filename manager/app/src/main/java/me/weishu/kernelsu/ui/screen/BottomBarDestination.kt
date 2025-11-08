package me.weishu.kernelsu.ui.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import me.weishu.kernelsu.R

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @StringRes val label: Int,
    @DrawableRes val iconSelected: Int,
    @DrawableRes val iconNotSelected: Int,
    val rootRequired: Boolean,
) {
    Home(
        HomeScreenDestination,
        R.string.home,
        R.drawable.home_filled,
        R.drawable.home_outlined,
        false
    ),
    SuperUser(
        SuperUserScreenDestination,
        R.string.superuser,
        R.drawable.shield_filled,
        R.drawable.shield_outlined,
        true
    ),
    Module(
        ModuleScreenDestination,
        R.string.module,
        R.drawable.extension_filled,
        R.drawable.extension_outlined,
        true
    ),
    Settings(
        SettingScreenDestination,
        R.string.settings,
        R.drawable.settings_filled,
        R.drawable.settings_outlined,
        false
    )
}
