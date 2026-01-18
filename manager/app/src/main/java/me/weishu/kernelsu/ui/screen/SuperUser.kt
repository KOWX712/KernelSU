package me.weishu.kernelsu.ui.screen

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppProfileScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.SearchAppBar
import me.weishu.kernelsu.ui.util.ownerNameForUid
import me.weishu.kernelsu.ui.util.pickPrimary
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    LaunchedEffect(key1 = navigator) {
        viewModel.search = ""
        when {
            viewModel.appList.isEmpty() -> {
                viewModel.loadAppList()
            }
            viewModel.isNeedRefresh -> {
                viewModel.loadAppList()
            }
        }
    }

    LaunchedEffect(viewModel.search) {
        if (viewModel.search.isEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.superuser)) },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = "" },
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_system_apps)) },
                                trailingIcon = { Checkbox(viewModel.showSystemApps, null) },
                                onClick = {
                                    viewModel.updateShowSystemApps(!viewModel.showSystemApps)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(innerPadding),
            onRefresh = {
                scope.launch { viewModel.loadAppList() }
            },
            isRefreshing = viewModel.isRefreshing
        ) {
            val filteredApps = remember(SuperUserViewModel.apps) {
                SuperUserViewModel.apps.filter { it.packageName != ksuApp.packageName }
            }
            val allGroups = remember(filteredApps) { buildGroups(filteredApps) }
            val visibleUidSet = remember(viewModel.appList) { viewModel.appList.map { it.uid }.toSet() }
            val expandedUids = remember { mutableStateOf(setOf<Int>()) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                items(allGroups, key = { it.uid }) { group ->
                    val isVisible = visibleUidSet.contains(group.uid)
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            val expanded = expandedUids.value.contains(group.uid)
                            GroupItem(
                                group = group,
                                onToggleExpand = {
                                    if (group.apps.size > 1) {
                                        expandedUids.value =
                                            if (expanded) expandedUids.value - group.uid else expandedUids.value + group.uid
                                    }
                                }
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
                                    group.apps.forEach { app ->
                                        SimpleAppItem(app)
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

@Composable
private fun SimpleAppItem(
    app: SuperUserViewModel.AppInfo,
) {
    ListItem(
        headlineContent = { Text(app.label) },
        supportingContent = { Text(app.packageName) },
        leadingContent = {
            AppIconImage(
                packageInfo = app.packageInfo,
                label = app.label,
                modifier = Modifier
                    .padding(4.dp)
                    .size(48.dp)
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClickPrimary,
            onLongClick = if (group.apps.size > 1) onToggleExpand else null,
        ),
        headlineContent = { Text(if (group.apps.size > 1) "${ownerNameForUid(group.uid)} (${group.uid})" else group.primary.label) },
        supportingContent = {
            Column {
                Text(summaryText)
                FlowRow {
                    val userId = group.uid / 100000
                    val packageInfo = group.primary.packageInfo
                    val applicationInfo = packageInfo.applicationInfo

                    if (group.anyAllowSu) {
                        LabelText(label = "ROOT")
                    } else if (Natives.uidShouldUmount(group.uid)) {
                        LabelText(label = "UMOUNT")
                    }
                    if (group.anyCustom) {
                        LabelText(label = "CUSTOM")
                    }
                    if (userId != 0) {
                        LabelText(label = "UID$userId")
                    }
                    if (applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
                        || applicationInfo.flags.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                        LabelText(label = "SYSTEM")
                    }
                    if (!packageInfo.sharedUserId.isNullOrEmpty()) {
                        LabelText(label = "SHARED UID")
                    }
                }
            }
        },
        leadingContent = {
            AppIconImage(
                packageInfo = group.primary.packageInfo,
                label = group.primary.label,
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(40.dp)
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
        return@Comparator when (ra) {
            2 -> a.uid.compareTo(b.uid)
            else -> a.primary.label.lowercase().compareTo(b.primary.label.lowercase())
        }
    })
}

@Composable
fun LabelText(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 5.dp),
            style = TextStyle(
                fontSize = 8.sp,
                color = Color.White,
            )
        )
    }
}
