package com.sublingo.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sublingo.app.ui.HomeScreen
import com.sublingo.app.ui.HomeViewModel
import com.sublingo.app.ui.components.BottomDestination
import com.sublingo.app.ui.components.SoftBottomBar
import com.sublingo.app.ui.components.SoftScaffold
import com.sublingo.app.ui.download.DownloadScreen
import com.sublingo.app.ui.download.DownloadViewModel
import com.sublingo.app.ui.library.LibraryScreen
import com.sublingo.app.ui.library.LibraryViewModel
import com.sublingo.app.ui.player.PlayerScreen
import com.sublingo.app.ui.review.ReviewScreen
import com.sublingo.app.ui.settings.SettingsScreen
import com.sublingo.app.ui.settings.SettingsViewModel
import com.sublingo.app.ui.transcript.TranscriptScreen

private val PlayerEnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val PlayerExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
private val BottomNavigationTopInset = 94.dp

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isPlayer = backStackEntry?.destination?.route?.startsWith(Routes.PLAYER) == true
    val isTranscript = backStackEntry?.destination?.route?.startsWith(Routes.TRANSCRIPT) == true
    val view = LocalView.current
    DisposableEffect(isPlayer) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val color = if (isPlayer) android.graphics.Color.BLACK else android.graphics.Color.rgb(253, 250, 240)
            window.statusBarColor = color
            window.navigationBarColor = color
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isPlayer
                isAppearanceLightNavigationBars = !isPlayer
            }
        }
        onDispose { }
    }
    SoftScaffold(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        containerColor = if (isPlayer) androidx.compose.ui.graphics.Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars),
        ) {
            val showsBottomBar = !isPlayer && !isTranscript
            if (showsBottomBar) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // 72dp capsule + 10dp SoftBottomBar outer bottom padding + 12dp host
                        // bottom padding. This top edge is shared by the route content boundary.
                        .height(BottomNavigationTopInset)
                        .background(Color(0xFFFDFAF0))
                        .zIndex(0f),
                )
            }
            Column(modifier = androidx.compose.ui.Modifier.fillMaxSize().zIndex(1f)) {
                Box(modifier = androidx.compose.ui.Modifier.weight(1f).zIndex(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = androidx.compose.ui.Modifier.background(if (isPlayer) androidx.compose.ui.graphics.Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background),
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None },
                    ) {
                        composable(Routes.HOME) {
                            PaddedDestination {
                                HomeScreen(
                                    viewModel = hiltViewModel<HomeViewModel>(),
                                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                                    onOpenVideo = { videoId -> navController.navigate("${Routes.PLAYER}/$videoId/0") },
                                )
                            }
                        }
                        composable(Routes.LIBRARY) {
                            PaddedDestination {
                                LibraryScreen(
                                    viewModel = hiltViewModel<LibraryViewModel>(),
                                    onOpenPlayer = { videoId -> navController.navigate("${Routes.PLAYER}/$videoId/0") },
                                )
                            }
                        }
                        composable(Routes.DOWNLOAD) {
                            PaddedDestination { DownloadScreen(viewModel = hiltViewModel<DownloadViewModel>()) }
                        }
                        composable(Routes.SETTINGS) {
                            PaddedDestination {
                                SettingsScreen(viewModel = hiltViewModel<SettingsViewModel>())
                            }
                        }
                        composable(Routes.REVIEW) {
                            PaddedDestination(bottomPadding = 0.dp) {
                                ReviewScreen(
                                    onOpenSource = { videoId, startPositionMs ->
                                        navController.navigate("${Routes.PLAYER}/$videoId/$startPositionMs")
                                    },
                                )
                            }
                        }
                        composable(
                            route = "${Routes.TRANSCRIPT}/{videoId}",
                            arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                            enterTransition = { fadeIn(tween(260, delayMillis = 20, easing = PlayerEnterEasing)) },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { fadeOut(tween(180)) },
                        ) {
                            PaddedDestination {
                                TranscriptScreen(
                                    onBack = navController::popBackStack,
                                )
                            }
                        }
                        composable(
                            route = "${Routes.PLAYER}/{videoId}/{startPositionMs}",
                            arguments = listOf(
                                navArgument("videoId") { type = NavType.StringType },
                                navArgument("startPositionMs") { type = NavType.LongType },
                            ),
                            enterTransition = {
                                slideInVertically(initialOffsetY = { it / 20 }, animationSpec = tween(320, easing = PlayerEnterEasing))
                            },
                            exitTransition = { fadeOut(tween(220, easing = PlayerExitEasing)) },
                            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(300, easing = PlayerExitEasing)) +
                                    scaleOut(
                                        targetScale = 0.92f,
                                        animationSpec = tween(320, easing = PlayerExitEasing),
                                    )
                            },
                        ) {
                            PlayerScreen(
                                onBack = navController::popBackStack,
                                onOpenTranscript = { videoId -> navController.navigate("${Routes.TRANSCRIPT}/$videoId") },
                            )
                        }
                    }
                }
                if (showsBottomBar) Spacer(Modifier.height(BottomNavigationTopInset))
            }
            if (showsBottomBar) {
                    val selectedDestination = when (backStackEntry?.destination?.route) {
                        Routes.SETTINGS -> BottomDestination.Settings
                        Routes.REVIEW -> BottomDestination.Review
                        else -> BottomDestination.Videos
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            .zIndex(2f),
                    ) {
                        SoftBottomBar(
                            selected = selectedDestination,
                            onVideosClick = { navController.navigate(Routes.HOME) },
                            onReviewClick = { navController.navigate(Routes.REVIEW) },
                            onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun PaddedDestination(bottomPadding: androidx.compose.ui.unit.Dp = 12.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = bottomPadding)) {
        content()
    }
}
