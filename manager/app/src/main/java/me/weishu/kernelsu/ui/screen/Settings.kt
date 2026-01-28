package me.weishu.kernelsu.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.AboutDialog
import me.weishu.kernelsu.ui.component.ConfirmResult
import me.weishu.kernelsu.ui.component.DialogHandle
import me.weishu.kernelsu.ui.component.ExpressiveDropdownItem
import me.weishu.kernelsu.ui.component.ExpressiveColumn
import me.weishu.kernelsu.ui.component.ExpressiveListItem
import me.weishu.kernelsu.ui.component.ExpressiveSwitchItem
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.rememberCustomDialog
import me.weishu.kernelsu.ui.component.rememberLoadingDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.LocalSnackbarHost
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.ui.util.getBugreportFile
import me.weishu.kernelsu.ui.util.getFeaturePersistValue
import me.weishu.kernelsu.ui.util.getFeatureStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author weishu
 * @date 2023/1/1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(navigator: Navigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current

    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        val aboutDialog = rememberCustomDialog {
            AboutDialog(it)
        }
        val loadingDialog = rememberLoadingDialog()
        val uninstallConfirmDialog = rememberConfirmDialog()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            val exportBugreportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/gzip")
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch(Dispatchers.IO) {
                    loadingDialog.show()
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        getBugreportFile(context).inputStream().use {
                            it.copyTo(output)
                        }
                    }
                    loadingDialog.hide()
                    snackBarHost.showSnackbar(context.getString(R.string.log_saved))
                }
            }

            val uninstallDialog = rememberUninstallDialog { uninstallType ->
                scope.launch {
                    val result = uninstallConfirmDialog.awaitConfirm(
                        title = context.getString(uninstallType.title),
                        content = context.getString(uninstallType.message)
                    )
                    if (result == ConfirmResult.Confirmed) {
                        loadingDialog.withLoading {
                            when (uninstallType) {
                                UninstallType.PERMANENT -> navigator.push(
                                    Route.Flash(FlashIt.FlashUninstall)
                                )
                                UninstallType.RESTORE_STOCK_IMAGE -> navigator.push(
                                    Route.Flash(FlashIt.FlashRestore)
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            KsuIsValid {
                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
//                        {
//                            var checkUpdate by rememberSaveable {
//                                mutableStateOf(
//                                    prefs.getBoolean("check_update", true)
//                                )
//                            }
//                            ExpressiveSwitchItem(
//                                icon = Icons.Filled.Update,
//                                title = stringResource(id = R.string.settings_check_update),
//                                summary = stringResource(id = R.string.settings_check_update_summary),
//                                checked = checkUpdate,
//                                onCheckedChange = { bool ->
//                                    prefs.edit { putBoolean("check_update", bool)z }
//                                    checkUpdate = bool
//                                }
//                            )
//                        },
                        {
                            var checkModuleUpdate by rememberSaveable {
                                mutableStateOf(prefs.getBoolean("module_check_update", true))
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Rounded.UploadFile,
                                title = stringResource(id = R.string.settings_module_check_update),
                                summary = stringResource(id = R.string.settings_check_update_summary),
                                checked = checkModuleUpdate,
                                onCheckedChange = { bool ->
                                    prefs.edit { putBoolean("module_check_update", bool) }
                                    checkModuleUpdate = bool
                                }
                            )
                        }
                    )
                )
            }

            val theme = stringResource(id = R.string.settings_theme)
            ExpressiveColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    ExpressiveListItem(
                        onClick = { navigator.push(Route.ColorPalette) },
                        headlineContent = { Text(theme) },
                        supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                        leadingContent = { Icon(Icons.Filled.Palette, theme) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null
                            )
                        }
                    )
                }
            )

            val profileTemplate = stringResource(id = R.string.settings_profile_template)
            KsuIsValid {
                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf {
                        ExpressiveListItem(
                            onClick = { navigator.push(Route.AppProfileTemplate) },
                            headlineContent = { Text(profileTemplate) },
                            supportingContent = { Text(stringResource(id = R.string.settings_profile_template_summary)) },
                            leadingContent = { Icon(Icons.Filled.Fence, profileTemplate) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                )
            }

            KsuIsValid {
                val suCompatModeItems = listOf(
                    stringResource(id = R.string.settings_mode_enable_by_default),
                    stringResource(id = R.string.settings_mode_disable_until_reboot),
                    stringResource(id = R.string.settings_mode_disable_always),
                )

                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            val currentSuEnabled = Natives.isSuEnabled()
                            var suCompatMode by rememberSaveable { mutableIntStateOf(if (!currentSuEnabled) 1 else 0) }
                            val suPersistValue by produceState(initialValue = null as Long?) {
                                value = getFeaturePersistValue("su_compat")
                            }
                            LaunchedEffect(suPersistValue) {
                                suPersistValue?.let { v ->
                                    suCompatMode = if (v == 0L) 2 else if (!currentSuEnabled) 1 else 0
                                }
                            }
                            val suStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("su_compat")
                            }
                            val suSummary = when (suStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_sucompat_summary)
                            }
                            ExpressiveDropdownItem(
                                icon = Icons.Filled.RemoveModerator,
                                title = stringResource(id = R.string.settings_sucompat),
                                summary = suSummary,
                                items = suCompatModeItems,
                                enabled = suStatus == "supported",
                                selectedIndex = suCompatMode,
                                onItemSelected = { index ->
                                    when (index) {
                                        // Default: enable and save to persist
                                        0 -> if (Natives.setSuEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("su_compat_mode", 0) }
                                            suCompatMode = 0
                                        }

                                        // Temporarily disable: save enabled state first, then disable
                                        1 -> if (Natives.setSuEnabled(true)) {
                                            execKsud("feature save", true)
                                            if (Natives.setSuEnabled(false)) {
                                                prefs.edit { putInt("su_compat_mode", 0) }
                                                suCompatMode = 1
                                            }
                                        }

                                        // Permanently disable: disable and save
                                        2 -> if (Natives.setSuEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("su_compat_mode", 2) }
                                            suCompatMode = 2
                                        }
                                    }
                                }
                            )
                        },
                        {
                            var isKernelUmountEnabled by rememberSaveable { mutableStateOf(Natives.isKernelUmountEnabled()) }
                            val umountStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("kernel_umount")
                            }
                            val umountSummary = when (umountStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_kernel_umount_summary)
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Filled.RemoveCircle,
                                title = stringResource(id = R.string.settings_kernel_umount),
                                summary = umountSummary,
                                enabled = umountStatus == "supported",
                                checked = isKernelUmountEnabled,
                            ) {
                                if (Natives.setKernelUmountEnabled(it)) {
                                    execKsud("feature save", true)
                                    isKernelUmountEnabled = it
                                }
                            }
                        },
                        {
                            var isAvcSpoofEnabled by rememberSaveable { mutableStateOf(Natives.isAvcSpoofEnabled()) }
                            val avcSpoofStatus by produceState(initialValue = "") {
                                value = getFeatureStatus("avc_spoof")
                            }
                            val avcSpoofSummary = when (avcSpoofStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                else -> stringResource(id = R.string.settings_avc_spoof_summary)
                            }
                            if (avcSpoofStatus == "supported") ExpressiveSwitchItem(
                                icon = Icons.AutoMirrored.Filled.Article,
                                title = stringResource(id = R.string.settings_avc_spoof),
                                summary = avcSpoofSummary,
                                enabled = avcSpoofStatus == "supported",
                                checked = isAvcSpoofEnabled,
                            ) {
                                if (Natives.setAvcSpoofEnabled(it)) {
                                    execKsud("feature save", true)
                                    isAvcSpoofEnabled = it
                                }
                            }
                        }
                    )
                )

                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            var umountChecked by rememberSaveable {
                                mutableStateOf(Natives.isDefaultUmountModules())
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Filled.FolderDelete,
                                title = stringResource(id = R.string.settings_umount_modules_default),
                                summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                                checked = umountChecked,
                                onCheckedChange = {
                                    if (Natives.setDefaultUmountModules(it)) {
                                        umountChecked = it
                                    }
                                }
                            )
                        },
                        {
                            var enableWebDebugging by rememberSaveable {
                                mutableStateOf(
                                    prefs.getBoolean("enable_web_debugging", false)
                                )
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Filled.DeveloperMode,
                                title = stringResource(id = R.string.enable_web_debugging),
                                summary = stringResource(id = R.string.enable_web_debugging_summary),
                                checked = enableWebDebugging,
                                onCheckedChange = {
                                    prefs.edit { putBoolean("enable_web_debugging", it) }
                                    enableWebDebugging = it
                                }
                            )
                        }
                    )
                )
            }

            if (Natives.isLkmMode) {
                ExpressiveColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            val uninstall = stringResource(id = R.string.settings_uninstall)
                            ExpressiveListItem(
                                onClick = { uninstallDialog.show() },
                                headlineContent = { Text(uninstall) },
                                leadingContent = { Icon(Icons.Filled.Delete, uninstall) }
                            )
                        }
                    )
                )
            }

            var showBottomsheet by remember { mutableStateOf(false) }
            ExpressiveColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        ExpressiveListItem(
                            onClick = { showBottomsheet = true },
                            headlineContent = { Text(stringResource(id = R.string.send_log)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.BugReport,
                                    stringResource(id = R.string.send_log)
                                )
                            },
                        )
                    },
                    {
                        ExpressiveListItem(
                            onClick = { aboutDialog.show() },
                            headlineContent = { Text(stringResource(id = R.string.about)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.ContactPage,
                                    stringResource(id = R.string.about)
                                )
                            },
                        )
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showBottomsheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomsheet = false },
                    content = {
                        Row(
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.CenterHorizontally)

                        ) {
                            Box {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .clickable {
                                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                                            val current = LocalDateTime.now().format(formatter)
                                            exportBugreportLauncher.launch("KernelSU_bugreport_${current}.tar.gz")
                                            showBottomsheet = false
                                        }
                                ) {
                                    Icon(
                                        Icons.Filled.Save,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.save_log),
                                        modifier = Modifier.padding(top = 16.dp),
                                        textAlign = TextAlign.Center.also {
                                            LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        }

                                    )
                                }
                            }
                            Box {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .clickable {
                                            scope.launch {
                                                val bugreport = loadingDialog.withLoading {
                                                    withContext(Dispatchers.IO) {
                                                        getBugreportFile(context)
                                                    }
                                                }

                                                val uri: Uri =
                                                    FileProvider.getUriForFile(
                                                        context,
                                                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                                                        bugreport
                                                    )

                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    setDataAndType(uri, "application/gzip")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }

                                                context.startActivity(
                                                    Intent.createChooser(
                                                        shareIntent,
                                                        context.getString(R.string.send_log)
                                                    )
                                                )
                                            }
                                        }
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.send_log),
                                        modifier = Modifier.padding(top = 16.dp),
                                        textAlign = TextAlign.Center.also {
                                            LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.Filled.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.Filled.Undo
    ),
    NONE(0, 0, Icons.Filled.Delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUninstallDialog(onSelected: (UninstallType) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val options = listOf(
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
        val listOptions = options.map {
            ListOption(
                titleText = stringResource(it.title),
                subtitleText = if (it.message != 0) stringResource(it.message) else null,
                icon = IconSource(it.icon)
            )
        }

        var selection = UninstallType.NONE
        ListDialog(state = rememberUseCaseState(visible = true, onFinishedRequest = {
            if (selection != UninstallType.NONE) {
                onSelected(selection)
            }
        }, onCloseRequest = {
            dismiss()
        }), header = Header.Default(
            title = stringResource(R.string.settings_uninstall),
        ), selection = ListSelection.Single(
            showRadioButtons = false,
            options = listOptions,
        ) { index, _ ->
            selection = options[index]
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
