package me.weishu.kernelsu.ui.screen

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppProfileTemplateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.TemplateEditorScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.SwitchItem
import me.weishu.kernelsu.ui.component.profile.AppProfileConfig
import me.weishu.kernelsu.ui.component.profile.RootProfileConfig
import me.weishu.kernelsu.ui.component.profile.TemplateConfig
import me.weishu.kernelsu.ui.util.LocalSnackbarHost
import me.weishu.kernelsu.ui.util.forceStopApp
import me.weishu.kernelsu.ui.util.getSepolicy
import me.weishu.kernelsu.ui.util.launchApp
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.util.pickPrimary
import me.weishu.kernelsu.ui.util.restartApp
import me.weishu.kernelsu.ui.util.setSepolicy
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import me.weishu.kernelsu.ui.viewmodel.getTemplateInfoById

/**
 * @author weishu
 * @date 2023/5/16.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AppProfileScreen(
    navigator: DestinationsNavigator,
    appInfo: SuperUserViewModel.AppInfo,
) {
    val viewModel: SuperUserViewModel = viewModel()
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    val failToUpdateAppProfile = stringResource(R.string.failed_to_update_app_profile).format(appInfo.label)
    val failToUpdateSepolicy = stringResource(R.string.failed_to_update_sepolicy).format(appInfo.label)
    val suNotAllowed = stringResource(R.string.su_not_allowed).format(appInfo.label)

    val packageName = appInfo.packageName
    val sameUidApps = remember(appInfo.uid) {
        SuperUserViewModel.apps.filter { it.uid == appInfo.uid }
    }
    val isUidGroup = sameUidApps.size > 1
    val primaryForIcon = remember(appInfo.uid, sameUidApps) {
        runCatching { pickPrimary(sameUidApps) }.getOrNull() ?: appInfo
    }
    val sharedUserId = remember(appInfo.uid, sameUidApps, primaryForIcon) {
        primaryForIcon.packageInfo.sharedUserId
            ?: sameUidApps.firstOrNull { it.packageInfo.sharedUserId != null }?.packageInfo?.sharedUserId
            ?: ""
    }
    val initialProfile = Natives.getAppProfile(packageName, appInfo.uid)
    if (initialProfile.allowSu) {
        initialProfile.rules = getSepolicy(packageName)
    }
    var profile by rememberSaveable {
        mutableStateOf(initialProfile)
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                scrollBehavior = scrollBehavior,
                isUidGroup = isUidGroup,
                packageName = appInfo.packageName
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackBarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        AppProfileInner(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            packageName = if (isUidGroup) "" else appInfo.packageName,
            appLabel = if (isUidGroup) ownerNameForUid(appInfo.uid) else appInfo.label,
            appIcon = {
                val iconApp = if (isUidGroup) primaryForIcon else appInfo
                AppIconImage(
                    packageInfo = iconApp.packageInfo,
                    label = iconApp.label,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(48.dp)
                )
            },
            appUid = appInfo.uid,
            sharedUserId = if (isUidGroup) sharedUserId else "",
            appVersionName = if (isUidGroup) "" else (appInfo.packageInfo.versionName ?: ""),
            appVersionCode = if (isUidGroup) 0L else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appInfo.packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                appInfo.packageInfo.versionCode.toLong()
            },
            profile = profile,
            isUidGroup = isUidGroup,
            affectedApps = sameUidApps,
            onViewTemplate = {
                getTemplateInfoById(it)?.let { info ->
                    navigator.navigate(TemplateEditorScreenDestination(info))
                }
            },
            onManageTemplate = {
                navigator.navigate(AppProfileTemplateScreenDestination())
            },
            onProfileChange = {
                scope.launch {
                    if (it.allowSu) {
                        // sync with allowlist.c - forbid_system_uid
                        if (appInfo.uid < 2000 && appInfo.uid != 1000) {
                            snackBarHost.showSnackbar(suNotAllowed)
                            return@launch
                        }
                        if (!it.rootUseDefault && it.rules.isNotEmpty() && !setSepolicy(profile.name, it.rules)) {
                            snackBarHost.showSnackbar(failToUpdateSepolicy)
                            return@launch
                        }
                    }
                    if (!Natives.setAppProfile(it)) {
                        snackBarHost.showSnackbar(failToUpdateAppProfile.format(appInfo.uid))
                    } else {
                        profile = it
                        viewModel.loadAppList()
                    }
                }
            },
        )
    }
}

@Composable
private fun AppProfileInner(
    modifier: Modifier = Modifier,
    packageName: String,
    appLabel: String,
    appIcon: @Composable (() -> Unit),
    appUid: Int,
    sharedUserId: String = "",
    appVersionName: String,
    appVersionCode: Long,
    profile: Natives.Profile,
    isUidGroup: Boolean = false,
    affectedApps: List<SuperUserViewModel.AppInfo> = emptyList(),
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit,
) {
    val isRootGranted = profile.allowSu

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(appLabel) },
                supportingContent = {
                    Column {
                        if (!isUidGroup) {
                            Text("$appVersionName ($appVersionCode)", color = MaterialTheme.colorScheme.outline)
                            Text(packageName, color = MaterialTheme.colorScheme.outline)
                        } else {
                            if (sharedUserId.isNotEmpty()) {
                                Text(text = sharedUserId, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(text = stringResource(R.string.group_contains_apps, affectedApps.size), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                leadingContent = appIcon,
                trailingContent = { 
                    LabelText(
                        label = "UID$appUid",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onTertiary,
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            )
        }

        SwitchItem(
            icon = Icons.Filled.Security,
            title = stringResource(id = R.string.superuser),
            checked = isRootGranted,
            onCheckedChange = { onProfileChange(profile.copy(allowSu = it)) },
        )

        Crossfade(targetState = isRootGranted, label = "") { current ->
            Column(
                modifier = Modifier.padding(bottom = 6.dp + 48.dp + 6.dp /* SnackBar height */)
            ) {
                if (current) {
                    val initialMode = if (profile.rootUseDefault) {
                        Mode.Default
                    } else if (profile.rootTemplate != null) {
                        Mode.Template
                    } else {
                        Mode.Custom
                    }
                    var mode by rememberSaveable {
                        mutableStateOf(initialMode)
                    }
                    ProfileBox(mode, true) {
                        // template mode shouldn't change profile here!
                        if (it == Mode.Default || it == Mode.Custom) {
                            onProfileChange(
                                profile.copy(
                                    rootUseDefault = it == Mode.Default,
                                    rootTemplate = null
                                )
                            )
                        }
                        mode = it
                    }
                    Crossfade(targetState = mode, label = "") { currentMode ->
                        if (currentMode == Mode.Template) {
                            TemplateConfig(
                                profile = profile,
                                onViewTemplate = onViewTemplate,
                                onManageTemplate = onManageTemplate,
                                onProfileChange = onProfileChange
                            )
                        } else if (mode == Mode.Custom) {
                            RootProfileConfig(
                                fixedName = true,
                                profile = profile,
                                onProfileChange = onProfileChange
                            )
                        }
                    }
                } else {
                    val mode = if (profile.nonRootUseDefault) Mode.Default else Mode.Custom
                    ProfileBox(mode, false) {
                        onProfileChange(profile.copy(nonRootUseDefault = (it == Mode.Default)))
                    }
                    Crossfade(targetState = mode, label = "") { currentMode ->
                        val modifyEnabled = currentMode == Mode.Custom
                        AppProfileConfig(
                            fixedName = true,
                            profile = profile,
                            enabled = modifyEnabled,
                            onProfileChange = onProfileChange
                        )
                    }
                }
                if (isUidGroup) {
                    Text(
                        text = stringResource(R.string.app_profile_affects_following_apps),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            affectedApps.forEach { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    AppIconImage(
                                        packageInfo = app.packageInfo,
                                        label = app.label,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .weight(1f)
                                    ) {
                                        Text(text = app.label)
                                        Text(text = app.packageName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Mode(private val res: Int) {
    Default(R.string.profile_default), Template(R.string.profile_template), Custom(R.string.profile_custom);

    val text: String
        @Composable get() = stringResource(res)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    isUidGroup: Boolean = false,
    packageName: String = "",
) {
    TopAppBar(
        title = { Text(stringResource(R.string.profile)) },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
        actions = {
            if (!isUidGroup) {
                var showDropdown by remember { mutableStateOf(false) }

                IconButton(
                    onClick = { showDropdown = true },
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(id = R.string.settings)
                    )
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.launch_app)) },
                            onClick = {
                                showDropdown = false
                                launchApp(packageName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.force_stop_app)) },
                            onClick = {
                                showDropdown = false
                                forceStopApp(packageName)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.restart_app)) },
                            onClick = {
                                showDropdown = false
                                restartApp(packageName)
                            },
                        )
                    }
                }
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun ProfileBox(
    mode: Mode,
    hasTemplate: Boolean,
    onModeChange: (Mode) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.profile)) },
        supportingContent = { Text(mode.text, color = MaterialTheme.colorScheme.outline) },
        leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
    )
    HorizontalDivider(thickness = Dp.Hairline)
    ListItem(headlineContent = {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = mode == Mode.Default,
                label = { Text(stringResource(R.string.profile_default)) },
                onClick = { onModeChange(Mode.Default) },
            )
            if (hasTemplate) {
                FilterChip(
                    selected = mode == Mode.Template,
                    label = { Text(stringResource(R.string.profile_template)) },
                    onClick = { onModeChange(Mode.Template) },
                )
            }
            FilterChip(
                selected = mode == Mode.Custom,
                label = { Text(stringResource(R.string.profile_custom)) },
                onClick = { onModeChange(Mode.Custom) },
            )
        }
    })
}

@Preview
@Composable
private fun AppProfilePreview() {
    var profile by remember { mutableStateOf(Natives.Profile("")) }
    AppProfileInner(
        packageName = "icu.nullptr.test",
        appLabel = "Test",
        appIcon = { Icon(Icons.Filled.Android, null) },
        appUid = 1,
        appVersionName = "v1.0.0",
        appVersionCode = 12345,
        profile = profile,
        onProfileChange = {
            profile = it
        },
    )
}
