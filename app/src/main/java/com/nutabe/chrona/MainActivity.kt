package com.nutabe.chrona

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nutabe.chrona.engine.ReconcileExecutor
import com.nutabe.chrona.ui.ChronaApp
import com.nutabe.chrona.ui.theme.ChronaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        ReconcileExecutor.launch(this)
        val container = (application as App).container
        setContent { ChronaTheme { ChronaApp(container) } }
    }
}
