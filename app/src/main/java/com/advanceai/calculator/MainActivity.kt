package com.advanceai.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.advanceai.calculator.ads.Ads
import com.advanceai.calculator.data.Prefs
import com.advanceai.calculator.ui.AppRoot
import com.advanceai.calculator.ui.CalculatorViewModel
import com.advanceai.calculator.ui.theme.MCalculatorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // AdMob: bring the SDK up on a background thread and settle the consent
        // form if this part of the world needs one. Nothing is shown until both
        // have finished, and nothing is shown at all if either fails.
        Ads.start(this)
        setContent {
            // The palette lives here so the "Appearance" switch in the menu can
            // change it instantly, and it is remembered across launches.
            val prefs = remember { Prefs(applicationContext) }
            var themeMode by remember { mutableStateOf(prefs.themeMode) }

            MCalculatorTheme(mode = themeMode) {
                val viewModel: CalculatorViewModel = viewModel()
                AppRoot(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.themeMode = mode
                    }
                )
            }
        }
    }
}
