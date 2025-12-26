package me.weishu.kernelsu.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.screen.BottomBarDestination
import me.weishu.kernelsu.ui.screen.FlashIt
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.util.LocalSnackbarHost
import me.weishu.kernelsu.ui.util.install
import me.weishu.kernelsu.ui.util.rootAvailable

class MainActivity : ComponentActivity() {

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

        // Check if launched with a ZIP file
        val zipUri: ArrayList<Uri>? = if (intent.data != null) {
            arrayListOf(intent.data!!)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("uris", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("uris")
            }
        }

        setContent {
            KernelSUTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                LaunchedEffect(zipUri) {
                    if (!zipUri.isNullOrEmpty()) {
                        navigator.navigate(
                            FlashScreenDestination(
                                FlashIt.FlashModules(zipUri)
                            )
                        )
                    }
                }

                val configuration = LocalConfiguration.current
                val defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            // If the target is a detail page (not a bottom navigation page), slide in from the right
                            if (targetState.destination.route !in bottomBarRoutes) {
                                slideInHorizontally(initialOffsetX = { it })
                            } else {
                                // Otherwise (switching between bottom navigation pages), use fade in
                                fadeIn(animationSpec = tween(340))
                            }
                        }

                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            // If navigating from the home page (bottom navigation page) to a detail page, slide out to the left
                            if (initialState.destination.route in bottomBarRoutes && targetState.destination.route !in bottomBarRoutes) {
                                slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                            } else {
                                // Otherwise (switching between bottom navigation pages), use fade out
                                fadeOut(animationSpec = tween(340))
                            }
                        }

                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                        {
                            // If returning to the home page (bottom navigation page), slide in from the left
                            if (targetState.destination.route in bottomBarRoutes) {
                                slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                            } else {
                                // Otherwise (e.g., returning between multiple detail pages), use default fade in
                                fadeIn(animationSpec = tween(340))
                            }
                        }

                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                        {
                            // If returning from a detail page (not a bottom navigation page), scale down and fade out
                            if (initialState.destination.route !in bottomBarRoutes) {
                                scaleOut(targetScale = 0.9f) + fadeOut()
                            } else {
                                // Otherwise, use default fade out
                                fadeOut(animationSpec = tween(340))
                            }
                        }
                }
                Scaffold(
                    bottomBar = {
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            BottomBar(navController)
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))) {
                                SideBar(navController = navController, modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)))
                                DestinationsNavHost(
                                    modifier = Modifier.weight(1f),
                                    navGraph = NavGraphs.root,
                                    navController = navController,
                                    defaultTransitions = defaultTransitions
                                )
                            }
                        } else {
                            DestinationsNavHost(
                                modifier = Modifier.padding(innerPadding),
                                navGraph = NavGraphs.root,
                                navController = navController,
                                defaultTransitions = defaultTransitions
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val navigator = navController.rememberDestinationsNavigator()
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val bottomBarRoutes = remember {
        BottomBarDestination.entries.map { it.direction.route }.toSet()
    }
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    NavigationBar(
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        BottomBarDestination.entries.forEach { destination ->
            if (!fullFeatured && destination.rootRequired) return@forEach
            val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)
            NavigationBarItem(
                selected = isCurrentDestOnBackStack,
                onClick = {
                    if (isCurrentDestOnBackStack) {
                        navigator.popBackStack(destination.direction, false)
                    } else {
                        val isFromNonBottom = currentRoute !in bottomBarRoutes
                        navigator.navigate(destination.direction) {
                            if (isFromNonBottom) {
                                popUpTo(NavGraphs.root) { inclusive = true }
                            } else {
                                popUpTo(NavGraphs.root) { saveState = true }
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        if (isCurrentDestOnBackStack) destination.iconSelected else destination.iconNotSelected,
                        stringResource(destination.label)
                    )
                },
                label = { Text(stringResource(destination.label)) },
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun SideBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val navigator = navController.rememberDestinationsNavigator()
    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val bottomBarRoutes = remember {
        BottomBarDestination.entries.map { it.direction.route }.toSet()
    }
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            BottomBarDestination.entries.forEach { destination ->
                if (!fullFeatured && destination.rootRequired) return@forEach
                val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)
                NavigationRailItem(
                    selected = isCurrentDestOnBackStack,
                    onClick = {
                        if (isCurrentDestOnBackStack) {
                            navigator.popBackStack(destination.direction, false)
                        } else {
                            val isFromNonBottom = currentRoute !in bottomBarRoutes
                            navigator.navigate(destination.direction) {
                                if (isFromNonBottom) {
                                    popUpTo(NavGraphs.root) { inclusive = true }
                                } else {
                                    popUpTo(NavGraphs.root) { saveState = true }
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            if (isCurrentDestOnBackStack) destination.iconSelected else destination.iconNotSelected,
                            stringResource(destination.label)
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    alwaysShowLabel = false,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
