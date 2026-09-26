package com.zcode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zcode.android.core.designsystem.ZcodeTheme
import com.zcode.android.ui.TokenCatalogScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZcodeTheme {
                TokenCatalogScreen()
            }
        }
    }
}
