package me.weishu.kernelsu.ui.screen

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.TemplateEditorScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.getOr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.ExpressiveLazyList
import me.weishu.kernelsu.ui.component.ExpressiveListItem
import me.weishu.kernelsu.ui.viewmodel.TemplateViewModel

/**
 * @author weishu
 * @date 2023/10/20.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun AppProfileTemplateScreen(
    navigator: DestinationsNavigator,
    resultRecipient: ResultRecipient<TemplateEditorScreenDestination, Boolean>
) {
    val viewModel = viewModel<TemplateViewModel>()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (viewModel.templateList.isEmpty()) {
            viewModel.fetchTemplates()
        }
    }

    // handle result from TemplateEditorScreen, refresh if needed
    resultRecipient.onNavResult { result ->
        if (result.getOr { false }) {
            scope.launch { viewModel.fetchTemplates() }
        }
    }

    val onRefresh: () -> Unit = {
        scope.launch {
            viewModel.fetchTemplates()
        }
    }

    val scaleFraction = {
        if (viewModel.isRefreshing) 1f
        else LinearOutSlowInEasing.transform(pullToRefreshState.distanceFraction).coerceIn(0f, 1f)
    }

    Scaffold(
        modifier = Modifier.pullToRefresh(
            state = pullToRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = onRefresh,
        ),
        topBar = {
            val clipboard = LocalClipboard.current
            val context = LocalContext.current
            val showToast = fun(msg: String) {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                onSync = {
                    scope.launch { viewModel.fetchTemplates(true) }
                },
                onImport = {
                    scope.launch {
                        clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()?.let {
                            if (it.isEmpty()) {
                                showToast(context.getString(R.string.app_profile_template_import_empty))
                                return@let
                            }
                            viewModel.importTemplates(
                                it,
                                {
                                    showToast(context.getString(R.string.app_profile_template_import_success))
                                    viewModel.fetchTemplates(false)
                                },
                                showToast
                            )
                        }
                    }
                },
                onExport = {
                    scope.launch {
                        viewModel.exportTemplates(
                            {
                                showToast(context.getString(R.string.app_profile_template_export_empty))
                            },
                            {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("template", it)))
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navigator.navigate(
                        TemplateEditorScreenDestination(
                            TemplateViewModel.TemplateInfo(),
                            false
                        )
                    )
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(id = R.string.app_profile_template_create)) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            val templateList = viewModel.templateList
            ExpressiveLazyList(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 16.dp + 56.dp + 16.dp /* Scaffold Fab Spacing + Fab container height */
                ),
                items = templateList,
                itemContent = { template ->
                    TemplateItem(navigator, template)
                }
            )
            Box(
                modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                    scaleX = scaleFraction()
                    scaleY = scaleFraction()
                }
            ) {
                PullToRefreshDefaults.LoadingIndicator(state = pullToRefreshState, isRefreshing = viewModel.isRefreshing)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateItem(
    navigator: DestinationsNavigator,
    template: TemplateViewModel.TemplateInfo
) {
    ExpressiveListItem(
        onClick = {
            navigator.navigate(TemplateEditorScreenDestination(template, !template.local))
        },
        headlineContent = { Text(template.name) },
        supportingContent = {
            Column {
                Text(
                    text = "${template.id}${if (template.author.isEmpty()) "" else "@${template.author}"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
                Text(template.description, color = MaterialTheme.colorScheme.outline)
                FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                    LabelText(label = "UID: ${template.uid}")
                    LabelText(label = "GID: ${template.gid}")
                    LabelText(label = template.context)
                    if (template.local) {
                        LabelText(label = "local")
                    } else {
                        LabelText(label = "remote")
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit,
    onSync: () -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(stringResource(R.string.settings_profile_template))
        },
        navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
        actions = {
            var showDropdown by remember { mutableStateOf(false) }
            IconButton(onClick = {
                showDropdown = true
            }) {
                Icon(
                    imageVector = Icons.Filled.ImportExport,
                    contentDescription = stringResource(id = R.string.app_profile_import_export)
                )

                DropdownMenu(expanded = showDropdown, onDismissRequest = {
                    showDropdown = false
                }) {
                    DropdownMenuItem(text = {
                        Text(stringResource(id = R.string.app_profile_import_from_clipboard))
                    }, onClick = {
                        onImport()
                        showDropdown = false
                    })
                    DropdownMenuItem(text = {
                        Text(stringResource(id = R.string.app_profile_export_to_clipboard))
                    }, onClick = {
                        onExport()
                        showDropdown = false
                    })
                }
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}
