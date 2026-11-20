/**
 * شفرة الظل — Shadow Code
 * المالك والمطوّر الرسمي: مجاهد مهند مقدادي (mojahed mohanad migdady)
 */
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.AppSettings
import com.example.core.SettingsRepository
import com.example.core.TacticalFeedback
import com.example.offline.OfflineGameViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OfflineModeHost
import com.example.ui.screens.OnlineModeHost
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ShadowCodeTheme
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_RULES = "rules"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_OFFLINE = "offline"
private const val ROUTE_ONLINE = "online"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val settingsRepo = remember { SettingsRepository(context.applicationContext) }
            val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val feedback = remember { TacticalFeedback(context.applicationContext) }
            val navController = rememberNavController()
            val offlineViewModel: OfflineGameViewModel = viewModel()

            LaunchedEffect(settings.soundEnabled, settings.hapticsEnabled) {
                feedback.soundEnabled = settings.soundEnabled
                feedback.hapticsEnabled = settings.hapticsEnabled
            }

            ShadowCodeTheme {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            onPlayOffline = { navController.navigate(ROUTE_OFFLINE) },
                            onPlayOnline = { navController.navigate(ROUTE_ONLINE) },
                            onOpenRules = { navController.navigate(ROUTE_RULES) },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                    composable(ROUTE_RULES) {
                        RulesScreen(onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            onToggleSound = { enabled ->
                                scope.launch { settingsRepo.setSoundEnabled(enabled) }
                            },
                            onToggleHaptics = { enabled ->
                                scope.launch { settingsRepo.setHapticsEnabled(enabled) }
                            },
                            onToggleEnglish = { enabled ->
                                scope.launch { settingsRepo.setShowEnglishSubtitles(enabled) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_OFFLINE) {
                        OfflineModeHost(
                            viewModel = offlineViewModel,
                            feedback = feedback,
                            onExitToHome = {
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            }
                        )
                    }
                    composable(ROUTE_ONLINE) {
                        OnlineModeHost(
                            feedback = feedback,
                            onSwitchToOffline = {
                                navController.navigate(ROUTE_OFFLINE) {
                                    popUpTo(ROUTE_HOME)
                                }
                            },
                            onExitToHome = {
                                navController.popBackStack(ROUTE_HOME, inclusive = false)
                            }
                        )
                    }
                }
            }
        }
    }
}
