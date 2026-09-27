package com.zcode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zcode.android.core.designsystem.ZcodeColors
import com.zcode.android.core.designsystem.ZcodeTheme
import com.zcode.android.feature.chat.ChatScreen
import com.zcode.android.feature.chat.OnboardingScreen
import com.zcode.android.feature.terminal.TerminalScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZcodeTheme {
                val startupViewModel: StartupViewModel = hiltViewModel()
                val startup by startupViewModel.startup.collectAsStateWithLifecycle()
                val onboarded = startup?.onboarded
                if (onboarded == null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(ZcodeColors.bg),
                    )
                } else {
                    val navController = rememberNavController()
                    val startDestination =
                        if (onboarded) {
                            "chat/" + (startup?.lastSessionId ?: "new")
                        } else {
                            "onboarding"
                        }
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onDone = {
                                    navController.navigate("chat/new") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable("chat/{sessionId}") {
                            ChatScreen(onOpenTerminal = { navController.navigate("terminal") })
                        }
                        composable("terminal") {
                            TerminalScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
