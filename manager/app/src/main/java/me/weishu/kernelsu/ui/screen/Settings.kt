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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Update
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppProfileTemplateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
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
import me.weishu.kernelsu.ui.component.ExpressiveList
import me.weishu.kernelsu.ui.component.ExpressiveSwitchItem
import me.weishu.kernelsu.ui.component.ExpressiveListItem
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.rememberCustomDialog
import me.weishu.kernelsu.ui.component.rememberLoadingDialog
import me.weishu.kernelsu.ui.util.LocalSnackbarHost
import me.weishu.kernelsu.ui.util.checkFileExist
import me.weishu.kernelsu.ui.util.execKsud
import me.weishu.kernelsu.ui.util.getBugreportFile
import me.weishu.kernelsu.ui.util.toggleFileState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author weishu
 * @date 2023/1/1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
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
                                UninstallType.PERMANENT -> navigator.navigate(
                                    FlashScreenDestination(FlashIt.FlashUninstall)
                                )
                                UninstallType.RESTORE_STOCK_IMAGE -> navigator.navigate(
                                    FlashScreenDestination(FlashIt.FlashRestore)
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }

            ExpressiveList(
                modifier = Modifier.padding(top = 8.dp),
                content = listOf(
//                    {
//                        var checkUpdate by rememberSaveable {
//                            mutableStateOf(
//                                prefs.getBoolean("check_update", true)
//                            )
//                        }
//                        ExpressiveSwitchItem(
//                            icon = Icons.Filled.Update,
//                            title = stringResource(id = R.string.settings_check_update),
//                            summary = stringResource(id = R.string.settings_check_update_summary),
//                            checked = checkUpdate,
//                            onCheckedChange = { bool ->
//                                prefs.edit { putBoolean("check_update", bool) }
//                                checkUpdate = bool
//                            }
//                        )
//                    },
                    {
                        var officialAppName by rememberSaveable {
                            mutableStateOf(
                                prefs.getBoolean("use_official_app_name", false)
                            )
                        }
                        ExpressiveSwitchItem(
                            icon = Icons.Filled.Title,
                            title = stringResource(id = R.string.settings_use_official_app_name),
                            summary = stringResource(id = R.string.settings_use_official_app_name_summary),
                            checked = officialAppName,
                            onCheckedChange = { checked ->
                                prefs.edit { putBoolean("use_official_app_name", checked) }
                                officialAppName = checked
                            }
                        )
                    }
                )
            )

            val profileTemplate = stringResource(id = R.string.settings_profile_template)
            KsuIsValid() {
                ExpressiveList(
                    content = listOf {
                        ExpressiveListItem(
                            onClick = { navigator.navigate(AppProfileTemplateScreenDestination) },
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

            KsuIsValid() {
                val modeItems = listOf(
                    stringResource(id = R.string.settings_mode_default),
                    stringResource(id = R.string.settings_mode_temp_enable),
                    stringResource(id = R.string.settings_mode_always_enable),
                )

                ExpressiveList(
                    content = listOf(
                        {
                            var enhancedSecurityMode by rememberSaveable {
                                mutableIntStateOf(
                                    run {
                                        val currentEnabled = Natives.isEnhancedSecurityEnabled()
                                        val savedPersist = prefs.getInt("enhanced_security_mode", 0)
                                        if (savedPersist == 2) 2 else if (currentEnabled) 1 else 0
                                    }
                                )
                            }
                            ExpressiveDropdownItem(
                                icon = Icons.Filled.EnhancedEncryption,
                                title = stringResource(id = R.string.settings_enable_enhanced_security),
                                summary = stringResource(id = R.string.settings_enable_enhanced_security_summary),
                                items = modeItems,
                                selectedIndex = enhancedSecurityMode,
                                onItemSelected = { index ->
                                    when (index) {
                                        // Default: disable and save to persist
                                        0 -> if (Natives.setEnhancedSecurityEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("enhanced_security_mode", 0) }
                                            enhancedSecurityMode = 0
                                        }

                                        // Temporarily enable: save disabled state first, then enable
                                        1 -> if (Natives.setEnhancedSecurityEnabled(false)) {
                                            execKsud("feature save", true)
                                            if (Natives.setEnhancedSecurityEnabled(true)) {
                                                prefs.edit { putInt("enhanced_security_mode", 0) }
                                                enhancedSecurityMode = 1
                                            }
                                        }

                                        // Permanently enable: enable and save
                                        2 -> if (Natives.setEnhancedSecurityEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("enhanced_security_mode", 2) }
                                            enhancedSecurityMode = 2
                                        }
                                    }
                                }
                            )
                        },
                        {
                            var suCompatMode by rememberSaveable {
                                mutableIntStateOf(
                                    run {
                                        val currentEnabled = Natives.isSuEnabled()
                                        val savedPersist = prefs.getInt("su_compat_mode", 0)
                                        if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                    }
                                )
                            }
                            ExpressiveDropdownItem(
                                icon = Icons.Filled.RemoveModerator,
                                title = stringResource(id = R.string.settings_disable_su),
                                summary = stringResource(id = R.string.settings_disable_su_summary),
                                items = modeItems,
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
                            var kernelUmountMode by rememberSaveable {
                                mutableIntStateOf(
                                    run {
                                        val currentEnabled = Natives.isKernelUmountEnabled()
                                        val savedPersist = prefs.getInt("kernel_umount_mode", 0)
                                        if (savedPersist == 2) 2 else if (!currentEnabled) 1 else 0
                                    }
                                )
                            }
                            ExpressiveDropdownItem(
                                icon = Icons.Filled.RemoveCircle,
                                title = stringResource(id = R.string.settings_disable_kernel_umount),
                                summary = stringResource(id = R.string.settings_disable_kernel_umount_summary),
                                items = modeItems,
                                selectedIndex = kernelUmountMode,
                                onItemSelected = { index ->
                                    when (index) {
                                        // Default: enable and save to persist
                                        0 -> if (Natives.setKernelUmountEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("kernel_umount_mode", 0) }
                                            kernelUmountMode = 0
                                        }

                                        // Temporarily disable: save enabled state first, then disable
                                        1 -> if (Natives.setKernelUmountEnabled(true)) {
                                            execKsud("feature save", true)
                                            if (Natives.setKernelUmountEnabled(false)) {
                                                prefs.edit { putInt("kernel_umount_mode", 0) }
                                                kernelUmountMode = 1
                                            }
                                        }

                                        // Permanently disable: disable and save
                                        2 -> if (Natives.setKernelUmountEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("kernel_umount_mode", 2) }
                                            kernelUmountMode = 2
                                        }
                                    }
                                }
                            )
                        },
                        {
                            var avcSpoofMode by rememberSaveable {
                                mutableIntStateOf(
                                    prefs.getInt(
                                        "avc_spoof_mode", if (!Natives.isAvcSpoofEnabled()) 1 else 0
                                    )
                                )
                            }
                            ExpressiveDropdownItem(
                                icon = Icons.AutoMirrored.Filled.Article,
                                title = stringResource(id = R.string.settings_enable_avc_spoof),
                                summary = stringResource(id = R.string.settings_enable_avc_spoof_summary),
                                items = modeItems,
                                selectedIndex = avcSpoofMode,
                                onItemSelected = { index ->
                                    when (index) {
                                        // Default: enable and save to persist
                                        0 -> if (Natives.setAvcSpoofEnabled(true)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("avc_spoof_mode", 0) }
                                            avcSpoofMode = 0
                                        }

                                        // Temporarily disable: save enabled state first, then disable
                                        1 -> if (Natives.setAvcSpoofEnabled(true)) {
                                            execKsud("feature save", true)
                                            if (Natives.setAvcSpoofEnabled(false)) {
                                                prefs.edit { putInt("avc_spoof_mode", 1) }
                                                avcSpoofMode = 1
                                            }
                                        }

                                        // Permanently disable: disable and save
                                        2 -> if (Natives.setAvcSpoofEnabled(false)) {
                                            execKsud("feature save", true)
                                            prefs.edit { putInt("avc_spoof_mode", 2) }
                                            avcSpoofMode = 2
                                        }
                                    }
                                }
                            )
                        }
                    )
                )
            }

            KsuIsValid() {
                ExpressiveList(
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
                            var noMountEnabled by rememberSaveable { mutableStateOf(false) }
                            val noMountFlag = "/data/adb/ksu/.nomount"
                            LaunchedEffect(Unit) {
                                withContext(Dispatchers.IO) {
                                    noMountEnabled = checkFileExist(noMountFlag)
                                }
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Filled.ExtensionOff,
                                title = stringResource(id = R.string.settings_enable_nomount),
                                summary = stringResource(id = R.string.settings_enable_nomount_summary),
                                checked = noMountEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch(Dispatchers.IO) {
                                        val result = toggleFileState(noMountFlag)
                                        if (result) {
                                            noMountEnabled = checked
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                snackBarHost.showSnackbar(context.getString(R.string.settings_failed_to_update_nomount))
                                            }
                                        }
                                    }
                                }
                            )
                        },
                        {
                            var noTmpfsEnabled by rememberSaveable { mutableStateOf(false) }
                            val noTmpfsFlag = "/data/adb/ksu/.notmpfs"
                            LaunchedEffect(Unit) {
                                withContext(Dispatchers.IO) {
                                    noTmpfsEnabled = checkFileExist(noTmpfsFlag)
                                }
                            }
                            ExpressiveSwitchItem(
                                icon = Icons.Filled.LayersClear,
                                title = stringResource(id = R.string.settings_enable_notmpfs),
                                summary = stringResource(id = R.string.settings_enable_notmpfs_summary),
                                checked = noTmpfsEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch(Dispatchers.IO) {
                                        val result = toggleFileState(noTmpfsFlag)
                                        if (result) {
                                            noTmpfsEnabled = checked
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                snackBarHost.showSnackbar(context.getString(R.string.settings_failed_to_update_notmpfs))
                                            }
                                        }
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

            var showBottomsheet by remember { mutableStateOf(false) }
            ExpressiveList(
                modifier = Modifier.padding(bottom = 8.dp),
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
                        val lkmMode = Natives.isLkmMode
                        if (lkmMode) {
                            val uninstall = stringResource(id = R.string.settings_uninstall)
                            ExpressiveListItem(
                                onClick = { uninstallDialog.show() },
                                headlineContent = { Text(uninstall) },
                                leadingContent = { Icon(Icons.Filled.Delete, uninstall) }
                            )
                        }
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

@Preview
@Composable
private fun SettingsPreview() {
    SettingScreen(EmptyDestinationsNavigator)
}