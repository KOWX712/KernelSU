package me.weishu.kernelsu.ui.screen

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppProfileScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ExpressiveLazyList
import me.weishu.kernelsu.ui.component.ExpressiveListItem
import me.weishu.kernelsu.ui.component.SearchAppBar
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.util.pickPrimary
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun SuperUserScreen(
    navigator: DestinationsNavigator,
    appProfileResultRecipient: ResultRecipient<AppProfileScreenDestination, Boolean>
) {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    val onRefresh: () -> Unit = {
        scope.launch {
            viewModel.fetchAppList()
        }
    }

    val scaleFraction = {
        if (viewModel.isRefreshing) 1f
        else LinearOutSlowInEasing.transform(pullToRefreshState.distanceFraction).coerceIn(0f, 1f)
    }

    LaunchedEffect(key1 = navigator) {
        if (viewModel.appList.isEmpty()) {
            viewModel.fetchAppList()
        }
    }

    appProfileResultRecipient.onNavResult {
        scope.launch {
            viewModel.fetchAppList()
        }
    }

    Scaffold(
        modifier = Modifier.pullToRefresh(
            state = pullToRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = onRefresh,
        ),
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = TextFieldValue("") },
                dropdownContent = {
                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.refresh))
                            }, onClick = {
                                scope.launch {
                                    viewModel.fetchAppList()
                                    listState.scrollToItem(0)
                                }
                                showDropdown = false
                            })
                            DropdownMenuItem(text = {
                                Text(
                                    if (viewModel.showSystemApps) {
                                        stringResource(R.string.hide_system_apps)
                                    } else {
                                        stringResource(R.string.show_system_apps)
                                    }
                                )
                            }, onClick = {
                                viewModel.updateShowSystemApps(!viewModel.showSystemApps)
                                showDropdown = false
                            })
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            val allGroups = remember(SuperUserViewModel.apps) { buildGroups(SuperUserViewModel.apps) }
            val visibleUids = remember(viewModel.appList) { viewModel.appList.map { it.uid }.toSet() }
            val expandedSearchUids = remember { mutableStateOf(setOf<Int>()) }
            val isSearching = viewModel.search.text.isNotEmpty()

            val visibleGroups = remember(allGroups, visibleUids) {
                allGroups.filter { it.uid in visibleUids }
            }

            ExpressiveLazyList(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                items = visibleGroups,
            ) { group ->
                val expanded = isSearching || expandedSearchUids.value.contains(group.uid)
                val onToggleExpand = {
                    if (group.apps.size > 1) {
                        expandedSearchUids.value = if (expandedSearchUids.value.contains(group.uid)) {
                            expandedSearchUids.value - group.uid
                        } else {
                            expandedSearchUids.value + group.uid
                        }
                    }
                }
                Column {
                    GroupItem(
                        group = group,
                        onToggleExpand = onToggleExpand,
                    ) {
                        navigator.navigate(AppProfileScreenDestination(group.primary)) {
                            launchSingleTop = true
                        }
                    }
                    AnimatedVisibility(
                        visible = expanded && group.apps.size > 1,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            group.apps.filter { it in viewModel.appList }.forEach { app ->
                                SimpleAppItem(app) {
                                    navigator.navigate(AppProfileScreenDestination(app)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleX = scaleFraction()
                        scaleY = scaleFraction()
                    }
            ) {
                PullToRefreshDefaults.LoadingIndicator(state = pullToRefreshState, isRefreshing = viewModel.isRefreshing)
            }
        }
    }
}

@Composable
private fun SimpleAppItem(
    app: SuperUserViewModel.AppInfo,
    onNavigate: () -> Unit,
) {
    ExpressiveListItem(
        onClick = onNavigate,
        modifier = Modifier.padding(start = 8.dp),
        headlineContent = { Text(app.label) },
        supportingContent = { Text(app.packageName) },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.packageInfo)
                    .crossfade(true)
                    .build(),
                contentDescription = app.label,
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
            )
        },
        trailingContent = { Icon(Icons.Filled.Remove, contentDescription = null)}
    )
}

@Composable
private fun GroupItem(
    group: GroupedApps,
    onToggleExpand: () -> Unit,
    onClickPrimary: () -> Unit,
) {
    val summaryText = if (group.apps.size > 1) {
        stringResource(R.string.group_contains_apps, group.apps.size)
    } else {
        group.primary.packageName
    }
    ExpressiveListItem(
        onClick = onClickPrimary,
        onLongClick = if (group.apps.size > 1) onToggleExpand else null,
        headlineContent = { Text(if (group.apps.size > 1) ownerNameForUid(group.uid) else group.primary.label) },
        supportingContent = {
            Column {
                Text(summaryText, color = MaterialTheme.colorScheme.outline)
                FlowRow {
                    val userId = group.uid / 100000
                    val packageInfo = group.primary.packageInfo
                    val applicationInfo = packageInfo.applicationInfo

                    if (group.anyAllowSu) {
                        LabelText(
                            label = "ROOT",
                            textColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.primary
                        )
                    } else if (Natives.uidShouldUmount(group.uid)) {
                        LabelText(
                            label = "UMOUNT",
                            textColor = MaterialTheme.colorScheme.onSecondary,
                            backgroundColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (group.anyCustom) {
                        LabelText(
                            label = "CUSTOM",
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                    if (userId != 0) {
                        LabelText(
                            label = "USER $userId",
                            textColor = MaterialTheme.colorScheme.onTertiary,
                            backgroundColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
                        || applicationInfo.flags.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                        LabelText(
                            label = "SYSTEM",
                            textColor = MaterialTheme.colorScheme.onTertiary,
                            backgroundColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (!packageInfo.sharedUserId.isNullOrEmpty()) {
                        LabelText(
                            label = "SHARED UID",
                            textColor = MaterialTheme.colorScheme.onTertiary,
                            backgroundColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(group.primary.packageInfo)
                    .crossfade(true)
                    .build(),
                contentDescription = group.primary.label,
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
            )
        },
    )
}

@Immutable
private data class GroupedApps(
    val uid: Int,
    val apps: List<SuperUserViewModel.AppInfo>,
    val primary: SuperUserViewModel.AppInfo,
    val anyAllowSu: Boolean,
    val anyCustom: Boolean,
)

private fun buildGroups(apps: List<SuperUserViewModel.AppInfo>): List<GroupedApps> {
    val comparator = compareBy<SuperUserViewModel.AppInfo> {
        when {
            it.allowSu -> 0
            it.hasCustomProfile -> 1
            else -> 2
        }
    }.thenBy { it.label.lowercase() }
    val groups = apps.groupBy { it.uid }.map { (uid, list) ->
        val sorted = list.sortedWith(comparator)
        val primary = pickPrimary(sorted)
        GroupedApps(
            uid = uid,
            apps = sorted,
            primary = primary,
            anyAllowSu = sorted.any { it.allowSu },
            anyCustom = sorted.any { it.hasCustomProfile },
        )
    }
    return groups.sortedWith(Comparator { a, b ->
        fun rank(g: GroupedApps): Int = when {
            g.anyAllowSu -> 0
            g.anyCustom -> 1
            g.apps.size > 1 -> 2
            Natives.uidShouldUmount(g.uid) -> 4
            else -> 3
        }

        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) return@Comparator ra - rb
        return@Comparator a.primary.label.lowercase().compareTo(b.primary.label.lowercase())
    })
}

@Composable
fun LabelText(
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
    backgroundColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 5.dp),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        )
    }
}