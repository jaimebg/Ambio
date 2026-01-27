package com.jbgsoft.ambio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jbgsoft.ambio.feature.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // HomeScreen handles its own theming based on selected sound
        // This enables the dynamic theme feature where entire UI recolors per sound
        setContent {
            HomeScreen()
        }
    }
}
