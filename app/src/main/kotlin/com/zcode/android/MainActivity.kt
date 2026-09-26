package com.zcode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zcode.android.core.designsystem.ZcodeTheme
import com.zcode.android.feature.terminal.TerminalScreen
import com.zcode.android.ui.TokenCatalogScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZcodeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        TokenCatalogScreen(onOpenTerminal = { navController.navigate("terminal") })
                    }
                    composable("terminal") {
                        TerminalScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
