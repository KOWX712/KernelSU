package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.LocalEnableOfficialLauncher

object AppInfo {
    @Composable
    fun appName(): String {
        return if (LocalEnableOfficialLauncher.current) {
            stringResource(R.string.app_name)
        } else {
            stringResource(R.string.app_name_kowsu)
        }
    }

    @Composable
    fun appIconRes(): Int {
        return if (LocalEnableOfficialLauncher.current) {
            R.drawable.ic_launcher_foreground
        } else {
            R.drawable.ic_launcher_kowsu
        }
    }

    @Composable
    fun appIconForeground() = painterResource(id = appIconRes())

    @Composable
    fun appIconMonochrome() = painterResource(
        id = if (LocalEnableOfficialLauncher.current) {
            R.drawable.ic_launcher_monochrome
        } else {
            R.drawable.ic_launcher_kowsu
        }
    )
}
