package com.nutabe.chrona.ui.ringing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.RingingController

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BackHandler {}
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = getString(R.string.ringing_title), style = MaterialTheme.typography.headlineLarge)
                        Button(onClick = ::stopAndFinish) {
                            Text(text = getString(R.string.ringing_stop))
                        }
                        Button(onClick = ::snoozeAndFinish) {
                            Text(text = getString(R.string.ringing_snooze))
                        }
                    }
                }
            }
        }
    }

    private fun stopAndFinish() {
        RingingController.stop(this)
        finish()
    }

    private fun snoozeAndFinish() {
        RingingController.snooze(this)
        finish()
    }
}
