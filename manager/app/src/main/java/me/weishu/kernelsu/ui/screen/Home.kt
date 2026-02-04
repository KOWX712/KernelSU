package me.weishu.kernelsu.ui.screen

import android.content.Context
import android.os.Build
import android.system.Os
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.getKernelVersion
import me.weishu.kernelsu.ui.component.RebootListPopup
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.checkNewVersion
import me.weishu.kernelsu.ui.util.getModuleCount
import me.weishu.kernelsu.ui.util.getSELinuxStatus
import me.weishu.kernelsu.ui.util.getSuperuserCount
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import me.weishu.kernelsu.ui.util.rootAvailable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigator: Navigator) {
    val kernelVersion = getKernelVersion()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isManager = Natives.isManager
            val ksuVersion = if (isManager) Natives.version else null
            val lkmMode = ksuVersion?.let {
                if (kernelVersion.isGKI()) Natives.isLkmMode else null
            }

            val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()

            StatusCard(
                kernelVersion,
                ksuVersion,
                lkmMode,
                fullFeatured,
                onClickInstall = { navigator.push(Route.Install) },
                onClickSuperuser = { navigator.push(Route.SuperUser) },
                onclickModule = { navigator.push(Route.Module) },
            )
//            if (ksuVersion != null && !Natives.isLkmMode) {
//                WarningCard(stringResource(id = R.string.home_gki_warning))
//            }
            if (isManager && Natives.requireNewKernel()) {
                WarningCard(
                    stringResource(id = R.string.require_kernel_version).format(
                        ksuVersion, Natives.MINIMAL_SUPPORTED_KERNEL
                    )
                )
            }
            if (ksuVersion != null && !rootAvailable()) {
                WarningCard(
                    stringResource(id = R.string.grant_root_failed)
                )
            }
//            val checkUpdate =
//                LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
//                    .getBoolean("check_update", true)
//            if (checkUpdate) {
//                UpdateCard()
//            }
            InfoCard()
            DonateCard()
            LearnMoreCard()
            Spacer(Modifier)
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    val newVersion by produceState(initialValue = latestVersionInfo) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion()
        }
    }

    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available).format(newVersionCode),
            MaterialTheme.colorScheme.outlineVariant
        ) {
            if (changelog.isEmpty()) {
                uriHandler.openUri(newVersionUrl)
            } else {
                updateDialog.showConfirm(
                    title = title,
                    content = changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val isOfficialEnabled = prefs.getBoolean("enable_official_launcher", false)
    val appNameId = if (isOfficialEnabled) R.string.app_name else R.string.app_name_kowsu

    TopAppBar(
        title = { Text(stringResource(appNameId)) },
        actions = { RebootListPopup() },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    kernelVersion: KernelVersion,
    ksuVersion: Int?,
    lkmMode: Boolean?,
    fullFeatured: Boolean?,
    onClickInstall: () -> Unit = {},
    onClickSuperuser: () -> Unit = {},
    onclickModule: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val classicUi = prefs.getBoolean("classic_ui", false)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp),) {
        TonalCard(
            containerColor = run {
                if (ksuVersion != null) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickInstall() }
                    .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    ksuVersion != null -> {
                        val safeMode = when {
                            Natives.isSafeMode -> stringResource(id = R.string.safe_mode)
                            else -> ""
                        }

                        val workingMode = when (lkmMode) {
                            null -> "LEGACY"
                            true -> "LKM"
                            else -> "GKI"
                        }

                        val workingText = stringResource(id = R.string.home_working)

                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.home_working))
                        Column(Modifier.padding(start = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = workingText,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (workingMode.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    LabelText(label = workingMode)
                                }
                                if (safeMode.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    LabelText(
                                        label = safeMode,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_working_version, ksuVersion),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (classicUi) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.home_superuser_count, getSuperuserCount()
                                    ), style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.home_module_count, getModuleCount()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    kernelVersion.isGKI() -> {
                        Icon(Icons.Outlined.Warning, stringResource(R.string.home_not_installed))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = stringResource(R.string.home_not_installed),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_click_to_install),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    else -> {
                        Icon(Icons.Outlined.Block, stringResource(R.string.home_unsupported))
                        Column(Modifier.padding(start = 20.dp)) {
                            Text(
                                text = stringResource(R.string.home_unsupported),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_unsupported_reason),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        if (fullFeatured == true && !classicUi) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TonalCard(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickSuperuser() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.superuser),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = getSuperuserCount().toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                TonalCard(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onclickModule() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.module),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = getModuleCount().toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarningCard(
    message: String, color: Color = MaterialTheme.colorScheme.error, onClick: (() -> Unit)? = null
) {
    TonalCard(containerColor = color) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(24.dp)
        ) {
            Text(
                text = message, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        content()
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    TonalCard {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                uriHandler.openUri(url)
            }
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current

    TonalCard {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                uriHandler.openUri("https://patreon.com/weishu")
            }
            .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_support_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    val context = LocalContext.current

    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            val contents = StringBuilder()
            val uname = Os.uname()
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isOfficial by remember { mutableStateOf(prefs.getBoolean("enable_official_launcher", false)) }
            val isClassicUi by remember { mutableStateOf(prefs.getBoolean("classic_ui", false)) }

            @Composable
            fun InfoCardItem(
                label: String,
                content: String,
                icon:@Composable () -> Unit
            ) {
                contents.appendLine(label).appendLine(content).appendLine()
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isClassicUi) {
                        icon()
                        Spacer(Modifier.width(16.dp))
                    }
                    Column {
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            @Composable
            fun InfoCardItem(icon: ImageVector, label: String, content: String) = InfoCardItem(
                label = label,
                content = content,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            InfoCardItem(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.home_kernel),
                content = uname.release
            )

            Spacer(Modifier.height(16.dp))
            val managerVersion = getManagerVersion(context)
            InfoCardItem(
                icon = {
                    Icon(
                        painter = painterResource(if (isOfficial) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_kowsu),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).wrapContentSize(unbounded = true).requiredSize(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = stringResource(R.string.home_manager_version),
                content = "${managerVersion.first} (${managerVersion.second})"
            )

            Spacer(Modifier.height(16.dp))
            InfoCardItem(
                icon = Icons.Outlined.Fingerprint,
                label = stringResource(R.string.home_fingerprint),
                content = Build.FINGERPRINT
            )

            Spacer(Modifier.height(16.dp))
            InfoCardItem(
                icon = Icons.Outlined.VerifiedUser,
                label = stringResource(R.string.home_selinux_status),
                content = getSELinuxStatus()
            )
        }
    }
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column {
        StatusCard(KernelVersion(5, 10, 101), 1, null, false)
        StatusCard(KernelVersion(5, 10, 101), 20000, true, true)
        StatusCard(KernelVersion(5, 10, 101), null, true, true)
        StatusCard(KernelVersion(4,10, 101), null, false, false)
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column {
        WarningCard(message = "Warning message")
        WarningCard(
            message = "Warning message ",
            MaterialTheme.colorScheme.outlineVariant,
            onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoCardPreview() {
    MaterialTheme {
        InfoCard()
    }
}
