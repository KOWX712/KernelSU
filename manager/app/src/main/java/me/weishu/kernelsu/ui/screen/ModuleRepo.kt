package me.weishu.kernelsu.ui.screen

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleRepoDetailScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ConfirmDialogHandle
import me.weishu.kernelsu.ui.component.GithubMarkdown
import me.weishu.kernelsu.ui.component.SearchAppBar
import me.weishu.kernelsu.ui.component.rememberConfirmDialog
import me.weishu.kernelsu.ui.util.DownloadListener
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.isNetworkAvailable
import me.weishu.kernelsu.ui.util.module.fetchModuleDetail
import me.weishu.kernelsu.ui.viewmodel.ModuleRepoViewModel
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import java.text.Collator
import java.util.Locale

@Parcelize
data class ReleaseAssetArg(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int
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
    val latestRelease: String,
    val latestReleaseTime: String,
    val releases: List<ReleaseArg>
) : Parcelable

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>
fun ModuleRepoScreen(
    navigator: DestinationsNavigator
) {
    val viewModel = viewModel<ModuleRepoViewModel>()
    val installedVm = viewModel<ModuleViewModel>()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val repoSortByNameState = remember { mutableStateOf(prefs.getBoolean("module_repo_sort_name", false)) }
    val listState = rememberLazyListState()

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

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(text = stringResource(R.string.module_repos)) },
                onBackClick = { navigator.popBackStack() },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = TextFieldValue("") },
                scrollBehavior = scrollBehavior,
                dropdownContent = {
                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.module_repos_sort_name)) },
                                trailingIcon = { Checkbox(repoSortByNameState.value, null) },
                                onClick = {
                                    repoSortByNameState.value = !repoSortByNameState.value
                                    prefs.edit {
                                        putBoolean("module_repo_sort_name", repoSortByNameState.value)
                                    }
                                }
                            )
                        }
                    }
                }
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
                        Button(
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
            val displayModules = run {
                var base = viewModel.modules.value
                val searchText = viewModel.search.text.trim()
                if (searchText.isNotEmpty()) {
                    base = base.filter { module ->
                        module.moduleName.contains(searchText, ignoreCase = true) ||
                        module.moduleId.contains(searchText, ignoreCase = true) ||
                        module.authors.contains(searchText, ignoreCase = true) ||
                        module.summary.contains(searchText, ignoreCase = true)
                    }
                }
                val sortByName = repoSortByNameState.value
                val collator = Collator.getInstance(Locale.getDefault())
                if (!sortByName) base else base.sortedWith(compareBy(collator) { it.moduleName })
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(innerPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(all = 16.dp),
            ) {
                items(displayModules, key = { it.moduleId }) { module ->
                    val latestReleaseTime = remember(module.latestReleaseTime) { module.latestReleaseTime }
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
                            if (module.moduleName.isNotEmpty()) {
                                Text(
                                    text = module.moduleName,
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                                )
                            }
                            if (module.moduleId.isNotEmpty()) {
                                Text(
                                    text = "ID: ${module.moduleId}",
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "$moduleAuthor: ${module.authors}",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (module.summary.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = module.summary,
                                    color = MaterialTheme.colorScheme.outline,
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
                                if (module.stargazerCount > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = "stars",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = module.stargazerCount.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (latestReleaseTime.isNotEmpty()) {
                                    Text(
                                        text = latestReleaseTime,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                        fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
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

    var readmeHtml by remember(module.moduleId) { mutableStateOf<String?>(null) }
    var readmeLoaded by remember(module.moduleId) { mutableStateOf(false) }
    var detailReleases by remember(module.moduleId) { mutableStateOf<List<ReleaseArg>>(emptyList()) }
    var webUrl by remember(module.moduleId) { mutableStateOf("https://modules.kernelsu.org/module/${module.moduleId}") }
    var sourceUrl by remember(module.moduleId) { mutableStateOf("https://github.com/KernelSU-Modules-Repo/${module.moduleId}") }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = module.moduleName) },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (webUrl.isNotEmpty()) {
                        IconButton(onClick = { uriHandler.openUri(webUrl) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =  MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LaunchedEffect(module.moduleId) {
            if (module.moduleId.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val detail = fetchModuleDetail(module.moduleId)
                        if (detail != null) {
                            readmeHtml = detail.readmeHtml
                            if (detail.sourceUrl.isNotEmpty()) sourceUrl = detail.sourceUrl
                            detailReleases = detail.releases.map { r ->
                                ReleaseArg(
                                    tagName = r.tagName,
                                    name = r.name,
                                    publishedAt = r.publishedAt,
                                    assets = r.assets.map { a -> ReleaseAssetArg(a.name, a.downloadUrl, a.size, a.downloadCount) },
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
        val tabs = listOf(
            stringResource(R.string.tab_readme),
            stringResource(R.string.tab_releases),
            stringResource(R.string.tab_info)
        )
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
        val layoutDirection = LocalLayoutDirection.current
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val paddedInnerPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 56.dp + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
                when (page) {
                    0 -> ReadmePage(
                        readmeHtml = readmeHtml,
                        readmeLoaded = readmeLoaded,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior
                    )
                    1 -> ReleasesPage(
                        detailReleases = detailReleases,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        confirmTitle = confirmTitle,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        onInstallModule = onInstallModule,
                        context = context,
                        setPendingDownload = { pendingDownload = it }
                    )
                    2 -> InfoPage(
                        module = module,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        uriHandler = uriHandler,
                        sourceUrl = sourceUrl
                    )
                }
            }
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DownloadListener(context, onInstallModule)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadmePage(
    readmeHtml: String?,
    readmeLoaded: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val isLoading = remember { mutableStateOf(true) }
        if (isLoading.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        if (readmeLoaded && readmeHtml != null) {
            val layoutDirection = LocalLayoutDirection.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = innerPadding.calculateBottomPadding(),
                    )
                    .padding(horizontal = 16.dp)
            ) {
                GithubMarkdown(
                    content = readmeHtml,
                    isLoading = isLoading,
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@Composable
fun ReleasesPage(
    detailReleases: List<ReleaseArg>,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    onInstallModule: (Uri) -> Unit,
    context: Context,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (detailReleases.isNotEmpty()) {
            items(
                items = detailReleases,
                key = { it.tagName },
            ) { rel ->
                val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }
                ElevatedCard {
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
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = rel.publishedAt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.Top)
                            )
                        }

                        AnimatedVisibility(
                            visible = rel.descriptionHTML.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    thickness = Dp.Hairline,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                                GithubMarkdown(content = rel.descriptionHTML)
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
                                    val sizeAndDownloads =
                                        remember(sizeText, asset.downloadCount) { "$sizeText · ${asset.downloadCount} downloads" }
                                    var isDownloading by remember(fileName, asset.downloadUrl) { mutableStateOf(false) }
                                    var progress by remember(fileName, asset.downloadUrl) { mutableIntStateOf(0) }
                                    val onClickDownload = remember(fileName, asset.downloadUrl) {
                                        {
                                            val startText = context.getString(R.string.module_start_downloading, fileName)
                                            setPendingDownload {
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
                                            confirmDialog.showConfirm(title = confirmTitle, content = startText)
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
                                                text = sizeAndDownloads,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoPage(
    module: RepoModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    uriHandler: UriHandler,
    sourceUrl: String,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (module.authorsList.isNotEmpty()) {
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_author),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
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
                                val clickable = author.link.isNotEmpty()
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
        if (sourceUrl.isNotEmpty()) {
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 22.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.module_repos_source_code),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = sourceUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            FilledTonalButton(
                                modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                                onClick = {
                                    uriHandler.openUri(sourceUrl)
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
                    }
                }
            }
        }
    }
}
