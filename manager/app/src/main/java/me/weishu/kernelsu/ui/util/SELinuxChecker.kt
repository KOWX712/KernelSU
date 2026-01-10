package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.Shell
import me.weishu.kernelsu.R

enum class SELinuxMode {
    Enforcing,
    Permissive,
    Disabled,
    Unknown
}

fun getSELinuxModeRaw(): SELinuxMode {
    val shell = Shell.Builder.create().build("sh")

    val stdoutList = ArrayList<String>()
    val stderrList = ArrayList<String>()
    val result = shell.use {
        it.newJob().add("getenforce").to(stdoutList, stderrList).exec()
    }
    val stdout = stdoutList.joinToString("\n").trim()
    val stderr = stderrList.joinToString("\n").trim()

    if (result.isSuccess) {
        return when (stdout) {
            "Enforcing" -> SELinuxMode.Enforcing
            "Permissive" -> SELinuxMode.Permissive
            "Disabled" -> SELinuxMode.Disabled
            else -> SELinuxMode.Unknown
        }
    }

    return if (stderr.endsWith("Permission denied")) {
        SELinuxMode.Enforcing
    } else {
        SELinuxMode.Unknown
    }
}

fun setSELinuxMode(mode: SELinuxMode): Boolean {
    if (mode == SELinuxMode.Unknown || mode == SELinuxMode.Disabled) {
        return false
    }

    val modeValue = when (mode) {
        SELinuxMode.Enforcing -> "1"
        SELinuxMode.Permissive -> "0"
        else -> return false
    }

    val result = Shell.cmd("setenforce $modeValue").exec()
    return result.isSuccess
}

@Composable
fun getSELinuxStatus(): String {
    val shell = Shell.Builder.create().build("sh")

    val stdoutList = ArrayList<String>()
    val stderrList = ArrayList<String>()
    val result = shell.use {
        it.newJob().add("getenforce").to(stdoutList, stderrList).exec()
    }
    val stdout = stdoutList.joinToString("\n").trim()
    val stderr = stderrList.joinToString("\n").trim()

    if (result.isSuccess) {
        return when (stdout) {
            "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
            "Permissive" -> stringResource(R.string.selinux_status_permissive)
            "Disabled" -> stringResource(R.string.selinux_status_disabled)
            else -> stringResource(R.string.selinux_status_unknown)
        }
    }

    return if (stderr.endsWith("Permission denied")) {
        stringResource(R.string.selinux_status_enforcing)
    } else {
        stringResource(R.string.selinux_status_unknown)
    }
}
