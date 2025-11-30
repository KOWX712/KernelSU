package me.weishu.kernelsu.ui.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleRepoDetailScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.GithubMarkdownContent
import me.weishu.kernelsu.ui.component.MarkdownContent
import me.weishu.kernelsu.ui.component.SearchAppBar
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.DownloadListener
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import me.weishu.kernelsu.ui.util.module.UpdateState
import me.weishu.kernelsu.ui.util.module.compareVersionCode
import me.weishu.kernelsu.ui.util.module.fetchModuleDetail
import me.weishu.kernelsu.ui.util.module.fetchReleaseDescriptionHtml
import me.weishu.kernelsu.ui.viewmodel.ModuleRepoViewModel
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel

@Parcelize
data class ReleaseAssetArg(
    val name: String,
    val downloadUrl: String,
    val size: Long
) : Parcelable

@Parcelize
data class ReleaseArg(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val assets: List<ReleaseAssetArg>,
    val descriptionHTML: String
) : Parcelable

@Parcelize
data class AuthorArg(
    val name: String,
    val link: String,
) : Parcelable

@Parcelize
data class RepoModuleArg(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorsList: List<AuthorArg>,
    val homepageUrl: String,
    val sourceUrl: String,
    val latestRelease: String,
    val latestReleaseTime: String,
    val releases: List<ReleaseArg>
) : Parcelable

@SuppressLint("StringFormatInvalid")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>
fun ModuleRepoScreen(
    navigator: DestinationsNavigator
) {
    val viewModel = viewModel<ModuleRepoViewModel>()
    val installedVm = viewModel<ModuleViewModel>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val offline = !isNetworkAvailable(context)

    LaunchedEffect(Unit) {
        if (viewModel.modules.value.isEmpty()) {
            viewModel.refresh()
        }
        if (installedVm.moduleList.isEmpty()) {
            installedVm.fetchModuleList()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val onInstallModule: (Uri) -> Unit = { uri ->
        navigator.navigate(FlashScreenDestination(FlashIt.FlashModules(listOf(uri)))) {
            launchSingleTop = true
        }
    }

    val confirmTitle = stringResource(R.string.module_install)
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(text = stringResource(R.string.module_repo)) },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = TextFieldValue("") },
                onBackClick = { navigator.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val isLoading = viewModel.modules.value.isEmpty()

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (offline) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.network_offline), color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth(),
                            onClick = { viewModel.refresh() },
                        ) {
                            Text(stringResource(R.string.network_retry))
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(all = 16.dp),
            ) {
                items(viewModel.filteredModules, key = { it.moduleId }) { module ->
                    val latestReleaseTime = remember(module.latestReleaseTime) { module.latestReleaseTime }
                    val latestTag = remember(module.latestRelease) { module.latestRelease }
                    val latestAsset = module.latestAsset
                    val moduleAuthor = stringResource(id = R.string.module_author)

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            val args = RepoModuleArg(
                                moduleId = module.moduleId,
                                moduleName = module.moduleName,
                                authors = module.authors,
                                authorsList = module.authorList.map { AuthorArg(it.name, it.link) },
                                homepageUrl = module.homepageUrl,
                                sourceUrl = module.sourceUrl,
                                latestRelease = module.latestRelease,
                                latestReleaseTime = module.latestReleaseTime,
                                releases = emptyList()
                            )
                            navigator.navigate(ModuleRepoDetailScreenDestination(args)) {
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp, 18.dp, 22.dp, 12.dp)
                        ) {
                            if (module.moduleName.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = module.moduleName,
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                        fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                                    )
                                }
                            }
                            if (module.moduleId.isNotBlank()) {
                                Text(
                                    text = "ID: ${module.moduleId}",
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                )
                            }
                            Text(
                                text = "$moduleAuthor: ${module.authors}",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            )
                            if (module.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = module.summary,
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 4,
                                )
                            }

                            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (module.metamodule) LabelText("META")
                            }
                            HorizontalDivider(thickness = Dp.Hairline)
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = latestTag,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                        fontWeight = MaterialTheme.typography.titleSmall.fontWeight,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                    )
                                    if (latestReleaseTime.isNotBlank()) {
                                        Text(
                                            text = latestReleaseTime,
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                            fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                if (latestAsset != null) {
                                    val fileName = latestAsset.name
                                    var isDownloading by remember(fileName, latestAsset.downloadUrl) { mutableStateOf(false) }
                                    var progress by remember(fileName, latestAsset.downloadUrl) { mutableIntStateOf(0) }
                                    val installed = installedVm.moduleList.firstOrNull { it.id == module.moduleId }
                                    val repoCode = module.latestVersionCode
                                    val state = compareVersionCode(installed?.versionCode, repoCode)
                                    val canUpdateByCode = state == UpdateState.CAN_UPDATE
                                    val equalByCode = state == UpdateState.EQUAL
                                    val olderByCode = state == UpdateState.OLDER
                                    val updateBg = MaterialTheme.colorScheme.tertiaryContainer
                                    val updateTint = MaterialTheme.colorScheme.onTertiaryContainer

                                    val updateTitle = stringResource(R.string.module_update)
                                    val installTitle = stringResource(R.string.module_install)
                                    val updateText = stringResource(R.string.module_update)
                                    val reinstallText = stringResource(R.string.module_reinstall)
                                    val installText = stringResource(R.string.install)

                                    Button(
                                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                                        colors = if (canUpdateByCode) ButtonDefaults.buttonColors(
                                            containerColor = updateBg,
                                            contentColor = updateTint
                                        ) else ButtonDefaults.buttonColors(),
                                        enabled = !isDownloading && !olderByCode,
                                        onClick = {
                                            val startDownloadingText = context.getString(R.string.module_start_downloading, fileName)
                                            if (canUpdateByCode) {
                                                var confirmContent = startDownloadingText
                                                var confirmHtml = false
                                                scope.launch(Dispatchers.IO) {
                                                    runCatching {
                                                        val html = fetchReleaseDescriptionHtml(module.moduleId, latestTag)
                                                        if (html != null) {
                                                            confirmContent = html
                                                            confirmHtml = true
                                                        }
                                                    }.onSuccess {
                                                        withContext(Dispatchers.Main) {
                                                            confirmDialog.showConfirm(
                                                                title = updateTitle,
                                                                content = confirmContent,
                                                                html = confirmHtml
                                                            )
                                                        }
                                                    }.onFailure {
                                                        withContext(Dispatchers.Main) {
                                                            confirmDialog.showConfirm(
                                                                title = installTitle,
                                                                content = startDownloadingText
                                                            )
                                                        }
                                                    }
                                                }
                                                pendingDownload = {
                                                    isDownloading = true
                                                    scope.launch(Dispatchers.IO) {
                                                        download(
                                                            url = latestAsset.downloadUrl,
                                                            fileName = fileName,
                                                            onDownloaded = onInstallModule,
                                                            onDownloading = { isDownloading = true },
                                                            onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } }
                                                        )
                                                    }
                                                }
                                            } else {
                                                confirmDialog.showConfirm(
                                                    title = confirmTitle,
                                                    content = startDownloadingText
                                                )
                                                pendingDownload = {
                                                    isDownloading = true
                                                    scope.launch(Dispatchers.IO) {
                                                        download(
                                                            url = latestAsset.downloadUrl,
                                                            fileName = fileName,
                                                            onDownloaded = onInstallModule,
                                                            onDownloading = { isDownloading = true },
                                                            onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } }
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                progress = { progress / 100f },
                                                gapSize = 20.dp,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                modifier = Modifier.size(20.dp),
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = when {
                                                    canUpdateByCode -> updateText
                                                    equalByCode -> reinstallText
                                                    else -> installText
                                                }
                                            )
                                            Text(
                                                modifier = Modifier.padding(start = 7.dp),
                                                fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                text = when {
                                                    canUpdateByCode -> updateText
                                                    equalByCode -> reinstallText
                                                    else -> installText
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
        DownloadListener(context, onInstallModule)
    }
}

@SuppressLint("StringFormatInvalid", "DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>
fun ModuleRepoDetailScreen(
    navigator: DestinationsNavigator,
    module: RepoModuleArg
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val confirmTitle = stringResource(R.string.module_install)
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })
    val onInstallModule: (Uri) -> Unit = { uri ->
        navigator.navigate(FlashScreenDestination(FlashIt.FlashModules(listOf(uri)))) {
            launchSingleTop = true
        }
    }

    var readmeText by remember(module.moduleId) { mutableStateOf<String?>(null) }
    var readmeLoaded by remember(module.moduleId) { mutableStateOf(false) }
    var detailReleases by remember(module.moduleId) { mutableStateOf<List<ReleaseArg>>(emptyList()) }
    var detailLatestTag by remember(module.moduleId) { mutableStateOf("") }
    var detailLatestTime by remember(module.moduleId) { mutableStateOf("") }
    var detailLatestAsset by remember(module.moduleId) { mutableStateOf<ReleaseAssetArg?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = module.moduleId) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (module.homepageUrl.isNotBlank()) {
                        IconButton(onClick = { uriHandler.openUri(module.homepageUrl) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                                contentDescription = null,
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        LaunchedEffect(module.moduleId) {
            if (module.moduleId.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val detail = fetchModuleDetail(module.moduleId)
                        if (detail != null) {
                            readmeText = detail.readme
                            detailLatestTag = detail.latestTag
                            detailLatestTime = detail.latestTime
                            detailLatestAsset = detail.latestAssetUrl?.let { url ->
                                val fname = detail.latestAssetName ?: url.substringAfterLast('/')
                                ReleaseAssetArg(name = fname, downloadUrl = url, size = 0L)
                            }
                            detailReleases = detail.releases.map { r ->
                                ReleaseArg(
                                    tagName = r.tagName,
                                    name = r.name,
                                    publishedAt = r.publishedAt,
                                    assets = r.assets.map { a -> ReleaseAssetArg(a.name, a.downloadUrl, a.size) },
                                    descriptionHTML = r.descriptionHTML
                                )
                            }
                        } else {
                            detailReleases = emptyList()
                        }
                    }.onSuccess {
                        readmeLoaded = true
                    }.onFailure {
                        readmeLoaded = true
                        detailReleases = emptyList()
                    }
                }
            } else {
                readmeLoaded = true
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            item {
                AnimatedVisibility(
                    visible = readmeLoaded && readmeText != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Text(
                            text = "README",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                        ElevatedCard(modifier = Modifier.padding(bottom = 16.dp)) {
                            Column(
                                modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                            ) {
                                MarkdownContent(content = readmeText!!)
                            }
                        }
                    }
                }
            }
            if (module.authorsList.isNotEmpty()) {
                item {
                    Text(
                        text = "AUTHORS",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    val uriHandler = LocalUriHandler.current
                    ElevatedCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                        ) {
                            module.authorsList.forEachIndexed { index, author ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = author.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val clickable = author.link.isNotBlank()
                                    FilledTonalButton(
                                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                                        enabled = clickable,
                                        onClick = {
                                            if (clickable) {
                                                uriHandler.openUri(author.link)
                                            }
                                        },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = null
                                        )
                                    }
                                }
                                if (index != module.authorsList.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = Dp.Hairline
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (detailReleases.isNotEmpty()) {
                item {
                    Text(
                        text = "RELEASES",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
                items(
                    detailReleases,
                    key = { it.tagName }
                ) { rel ->
                    val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }
                    ElevatedCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = rel.tagName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = rel.publishedAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            AnimatedVisibility(
                                visible = rel.descriptionHTML.isNotBlank(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    GithubMarkdownContent(content = rel.descriptionHTML)
                                }
                            }

                            AnimatedVisibility(
                                visible = rel.assets.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = Dp.Hairline
                                    )

                                    rel.assets.forEachIndexed { index, asset ->
                                        val fileName = asset.name
                                        val sizeText = remember(asset.size) {
                                            val s = asset.size
                                            when {
                                                s >= 1024L * 1024L * 1024L -> String.format("%.1f GB", s / (1024f * 1024f * 1024f))
                                                s >= 1024L * 1024L -> String.format("%.1f MB", s / (1024f * 1024f))
                                                s >= 1024L -> String.format("%.0f KB", s / 1024f)
                                                else -> "$s B"
                                            }
                                        }
                                        var isDownloading by remember(fileName, asset.downloadUrl) { mutableStateOf(false) }
                                        var progress by remember(fileName, asset.downloadUrl) { mutableIntStateOf(0) }
                                        val onClickDownload = remember(fileName, asset.downloadUrl) {
                                            {
                                                pendingDownload = {
                                                    isDownloading = true
                                                    scope.launch(Dispatchers.IO) {
                                                        download(
                                                            asset.downloadUrl,
                                                            fileName,
                                                            onDownloaded = onInstallModule,
                                                            onDownloading = { isDownloading = true },
                                                            onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } }
                                                        )
                                                    }
                                                }
                                                val confirmContent = context.getString(R.string.module_install_prompt_with_name, fileName)
                                                confirmDialog.showConfirm(
                                                    title = confirmTitle,
                                                    content = confirmContent
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = sizeText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = onClickDownload,
                                                contentPadding = ButtonDefaults.TextButtonContentPadding
                                            ) {
                                                if (isDownloading) {
                                                    CircularProgressIndicator(
                                                        progress = { progress / 100f },
                                                        gapSize = 20.dp,
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        modifier = Modifier.size(20.dp),
                                                        imageVector = Icons.Outlined.Download,
                                                        contentDescription = stringResource(R.string.install)
                                                    )
                                                    Text(
                                                        modifier = Modifier.padding(start = 7.dp),
                                                        text = stringResource(R.string.install),
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                            }
                                        }

                                        if (index != rel.assets.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                thickness = Dp.Hairline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (detailLatestAsset != null || detailLatestTag.isNotBlank()) {
                item {
                    Text(
                        text = "RELEASES",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
                item {
                    val relTitle = detailLatestTag
                    ElevatedCard(modifier = Modifier.padding(bottom = 16.dp)) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = relTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = relTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = detailLatestTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (detailLatestAsset != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = Dp.Hairline
                                )
                                val fileName = detailLatestAsset!!.name
                                var isDownloading by remember(fileName, detailLatestAsset!!.downloadUrl) { mutableStateOf(false) }
                                var progress by remember(fileName, detailLatestAsset!!.downloadUrl) { mutableIntStateOf(0) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            pendingDownload = {
                                                isDownloading = true
                                                scope.launch(Dispatchers.IO) {
                                                    download(
                                                        detailLatestAsset!!.downloadUrl,
                                                        fileName,
                                                        onDownloaded = onInstallModule,
                                                        onDownloading = { isDownloading = true },
                                                        onProgress = { p -> scope.launch(Dispatchers.Main) { progress = p } }
                                                    )
                                                }
                                            }
                                            val confirmContent = context.getString(R.string.module_install_prompt_with_name, fileName)
                                            confirmDialog.showConfirm(
                                                title = confirmTitle,
                                                content = confirmContent
                                            )
                                        },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                progress = { progress / 100f },
                                                gapSize = 20.dp,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                modifier = Modifier.size(20.dp),
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = stringResource(R.string.install)
                                            )
                                            Text(
                                                modifier = Modifier.padding(start = 7.dp),
                                                text = stringResource(R.string.install),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        DownloadListener(context, onInstallModule)
    }
}
