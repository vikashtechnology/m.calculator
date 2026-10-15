package com.advanceai.calculator

import android.content.Intent
import android.net.Uri
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

    /**
     * Text another app shared with this one ("share" from a chat, a note, a
     * browser). Held as state so the UI reacts when it arrives, including when
     * the app is already running.
     */
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText = sharedFrom(intent)
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
                    },
                    sharedText = sharedText,
                    onSharedHandled = { sharedText = null }
                )
            }
        }
    }

    /** A second share arrives here while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText = sharedFrom(intent)
    }

    /**
     * The text an incoming intent carries: another app's "share" payload, or
     * the expression in a `calc:2+3` link. Null for an ordinary launch.
     */
    private fun sharedFrom(intent: Intent?): String? {
        if (intent == null) return null
        val text = when {
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true ->
                intent.getStringExtra(Intent.EXTRA_TEXT)

            intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "calc" ->
                intent.data?.schemeSpecificPart
                    ?.removePrefix("//")
                    ?.let { Uri.decode(it) }

            else -> null
        }
        return text?.takeIf { it.isNotBlank() }?.take(2000)
    }
}
