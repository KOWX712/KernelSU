package me.weishu.kernelsu.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.BottomBar
import me.weishu.kernelsu.ui.navigation3.HandleDeepLink
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.navigation3.rememberNavigator
import me.weishu.kernelsu.ui.screen.AppProfileScreen
import me.weishu.kernelsu.ui.screen.AppProfileTemplateScreen
import me.weishu.kernelsu.ui.screen.ColorPaletteScreen
import me.weishu.kernelsu.ui.screen.ExecuteModuleActionScreen
import me.weishu.kernelsu.ui.screen.FlashIt
import me.weishu.kernelsu.ui.screen.FlashScreen
import me.weishu.kernelsu.ui.screen.HomeScreen
import me.weishu.kernelsu.ui.screen.InstallScreen
import me.weishu.kernelsu.ui.screen.ModuleRepoDetailScreen
import me.weishu.kernelsu.ui.screen.ModuleRepoScreen
import me.weishu.kernelsu.ui.screen.ModuleScreen
import me.weishu.kernelsu.ui.screen.SettingScreen
import me.weishu.kernelsu.ui.screen.SuperUserScreen
import me.weishu.kernelsu.ui.screen.TemplateEditorScreen
import me.weishu.kernelsu.ui.theme.AppSettings
import me.weishu.kernelsu.ui.theme.ColorMode
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.theme.ThemeController
import me.weishu.kernelsu.ui.util.LocalSnackbarHost
import me.weishu.kernelsu.ui.util.install
import me.weishu.kernelsu.ui.util.rootAvailable
import me.weishu.kernelsu.ui.webui.WebUIActivity

class MainActivity : ComponentActivity() {
    private val intentState = MutableStateFlow(0)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        val isManager = Natives.isManager
        if (isManager && !Natives.requireNewKernel()) install()

        val appSettingsSaver = Saver<AppSettings, IntArray>(
            save = { intArrayOf(it.colorMode.value, it.keyColor) },
            restore = { AppSettings(ColorMode.fromValue(it[0]), it[1]) }
        )

        setContent {
            val appSettingsState = rememberSaveable(stateSaver = appSettingsSaver) {
                mutableStateOf(ThemeController.getAppSettings(this@MainActivity))
            }

            val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
            val prefsListener = remember {
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "color_mode" || key == "key_color") {
                        appSettingsState.value = ThemeController.getAppSettings(this@MainActivity)
                    }
                }
            }

            DisposableEffect(Unit) {
                prefs.registerOnSharedPreferenceChangeListener(prefsListener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
                }
            }

            KernelSUTheme(appSettings = appSettingsState.value) {
                val snackBarHostState = remember { SnackbarHostState() }
                val navigator = rememberNavigator(Route.Home)

                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalSnackbarHost provides snackBarHostState,
                ) {
                    HandleDeepLink(
                        intentState = intentState.collectAsState(),
                    )

                    ZipFileIntentHandler(
                        intentState = intentState,
                        isManager = isManager,
                    )
                    ShortcutIntentHandler(
                        intentState = intentState,
                    )

                    val configuration = LocalConfiguration.current
                    val bottomBarRouteNames = setOf("Home", "SuperUser", "Module", "Settings")
                    val navDisplayContent: @Composable (Modifier) -> Unit = { modifier ->
                        NavDisplay(
                            modifier = modifier,
                            backStack = navigator.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            onBack = {
                                when (val top = navigator.backStack.lastOrNull()) {
                                    is Route.TemplateEditor -> {
                                        if (!top.readOnly) {
                                            navigator.setResult("template_edit", true)
                                        } else {
                                            navigator.pop()
                                        }
                                    }

                                    else -> navigator.pop()
                                }
                            },
                            transitionSpec = {
                                val targetRouteName = targetState.key.toString()
                                val initialRouteName = initialState.key.toString()

                                // enterTransition
                                val enter = if (targetRouteName !in bottomBarRouteNames) {
                                    // If the target is a detail page (not a bottom navigation page), slide in from the right
                                    slideInHorizontally(initialOffsetX = { it })
                                } else {
                                    // Otherwise (switching between bottom navigation pages), use fade in
                                    fadeIn(animationSpec = tween(340))
                                }

                                // exitTransition
                                val exit = if (initialRouteName in bottomBarRouteNames && targetRouteName !in bottomBarRouteNames) {
                                    // If navigating from the home page (bottom navigation page) to a detail page, slide out to the left
                                    slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                                } else {
                                    // Otherwise (switching between bottom navigation pages), use fade out
                                    fadeOut(animationSpec = tween(340))
                                }

                                enter togetherWith exit
                            },
                            popTransitionSpec = {
                                val targetRouteName = targetState.key.toString()
                                val initialRouteName = initialState.key.toString()

                                // popEnterTransition
                                val enter = if (targetRouteName in bottomBarRouteNames) {
                                    // If returning to the home page (bottom navigation page), slide in from the left
                                    slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                                } else {
                                    // Otherwise (e.g., returning between multiple detail pages), use default fade in
                                    fadeIn(animationSpec = tween(340))
                                }

                                // popExitTransition
                                val exit = if (initialRouteName !in bottomBarRouteNames) {
                                    // If returning from a detail page (not a bottom navigation page), scale down and fade out
                                    scaleOut(targetScale = 0.9f) + fadeOut()
                                } else {
                                    // Otherwise, use default fade out
                                    fadeOut(animationSpec = tween(340))
                                }

                                enter togetherWith exit
                            },
                            predictivePopTransitionSpec = { _ ->
                                val targetRouteName = targetState.key.toString()
                                val initialRouteName = initialState.key.toString()

                                // Same as popTransitionSpec for consistency
                                val enter = if (targetRouteName in bottomBarRouteNames) {
                                    slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                                } else {
                                    fadeIn(animationSpec = tween(340))
                                }

                                val exit = if (initialRouteName !in bottomBarRouteNames) {
                                    scaleOut(targetScale = 0.9f) + fadeOut()
                                } else {
                                    fadeOut(animationSpec = tween(340))
                                }

                                enter togetherWith exit
                            },
                            entryProvider = entryProvider {
                                entry<Route.AppProfileTemplate> { AppProfileTemplateScreen() }
                                entry<Route.TemplateEditor> { key -> TemplateEditorScreen(key.template, key.readOnly) }
                                entry<Route.ColorPalette> { ColorPaletteScreen() }
                                entry<Route.AppProfile> { key -> AppProfileScreen(key.packageName) }
                                entry<Route.ModuleRepo> { ModuleRepoScreen() }
                                entry<Route.ModuleRepoDetail> { key -> ModuleRepoDetailScreen(key.module) }
                                entry<Route.Install> { InstallScreen() }
                                entry<Route.Flash> { key -> FlashScreen(key.flashIt) }
                                entry<Route.ExecuteModuleAction> { key -> ExecuteModuleActionScreen(key.moduleId) }
                                entry<Route.Home> { HomeScreen(navigator) }
                                entry<Route.SuperUser> { SuperUserScreen(navigator) }
                                entry<Route.Module> { ModuleScreen(navigator) }
                                entry<Route.Settings> { SettingScreen(navigator) }
                            }
                        )
                    }

                    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                SideBar(navigator = navigator)
                                navDisplayContent(
                                    Modifier
                                        .weight(1f)
                                        .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                                )
                            }
                        }
                    } else {
                        Scaffold(
                            bottomBar = {
                                BottomBar(navigator)
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) { innerPadding ->
                            navDisplayContent(Modifier.padding(innerPadding))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Increment intentState to trigger LaunchedEffect re-execution
        intentState.value += 1
    }
}

@Composable
private fun BottomBar(navigator: Navigator) {
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val backStack = navigator.backStack
    val bottomBarRoutes = setOf(Route.Home, Route.SuperUser, Route.Module, Route.Settings)
    val currentRoute = backStack.lastOrNull { it in bottomBarRoutes }

    NavigationBar(
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        BottomBar.entries.forEach { destination ->
            if (!fullFeatured && destination.rootRequired) return@forEach
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) { return@NavigationBarItem }
                    backStack.clear()
                    backStack.add(destination.route)
                },
                icon = {
                    if (selected) {
                        Icon(destination.iconSelected, stringResource(destination.label))
                    } else {
                        Icon(destination.iconNotSelected, stringResource(destination.label))
                    }
                },
                label = { Text(stringResource(destination.label)) },
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun SideBar(navigator: Navigator, modifier: Modifier = Modifier) {
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val backStack = navigator.backStack
    val bottomBarRoutes = setOf(Route.Home, Route.SuperUser, Route.Module, Route.Settings)
    val currentRoute = backStack.lastOrNull { it in bottomBarRoutes }

    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            BottomBar.entries.forEach { destination ->
                if (!fullFeatured && destination.rootRequired) return@forEach
                val selected = currentRoute == destination.route
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (selected) {
                            return@NavigationRailItem
                        }
                        backStack.clear()
                        backStack.add(destination.route)
                    },
                    icon = {
                        Icon(
                            if (selected) destination.iconSelected else destination.iconNotSelected,
                            stringResource(destination.label)
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    alwaysShowLabel = false
                )
            }
        }
    }
}

/**
 * Handles ZIP file installation from external apps (e.g., file managers).
 * - In normal mode: Shows a confirmation dialog before installation
 * - In safe mode: Shows a Toast notification and prevents installation
 */
@SuppressLint("StringFormatInvalid", "LocalContextGetResourceValueCall")
@Composable
private fun ZipFileIntentHandler(
    intentState: MutableStateFlow<Int>,
    isManager: Boolean,
) {
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val isSafeMode = Natives.isSafeMode
    val navigator = LocalNavigator.current

    val intentStateValue by intentState.collectAsState()
    LaunchedEffect(intentStateValue) {
        val currentIntent = activity.intent
        val uri = currentIntent?.data ?: return@LaunchedEffect

        if (!isManager || uri.scheme != "content" || currentIntent.type != "application/zip") {
            return@LaunchedEffect
        }

        if (isSafeMode) {
            Toast.makeText(
                context,
                context.getString(R.string.safe_mode_module_disabled), Toast.LENGTH_SHORT
            )
                .show()
        } else {
            val component = currentIntent.component?.className
            val flashIt = when {
                component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(uri)
                else -> FlashIt.FlashModules(listOf(uri))
            }
            navigator.push(Route.Flash(flashIt))
        }
    }
}

@Composable
private fun ShortcutIntentHandler(
    intentState: MutableStateFlow<Int>,
) {
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val intentStateValue by intentState.collectAsState()
    val navigator = LocalNavigator.current
    LaunchedEffect(intentStateValue) {
        val intent = activity.intent
        val type = intent?.getStringExtra("shortcut_type") ?: return@LaunchedEffect
        when (type) {
            "module_action" -> {
                val moduleId = intent.getStringExtra("module_id") ?: return@LaunchedEffect
                navigator.push(Route.ExecuteModuleAction(moduleId))
            }

            "module_webui" -> {
                val moduleId = intent.getStringExtra("module_id") ?: return@LaunchedEffect
                val webIntent = Intent(context, WebUIActivity::class.java)
                    .setData("kernelsu://webui/$moduleId".toUri())
                context.startActivity(webIntent)
            }

            else -> return@LaunchedEffect
        }
    }
}
