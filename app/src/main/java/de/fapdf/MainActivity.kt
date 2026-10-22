package de.fapdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import de.fapdf.ui.AppNav
import de.fapdf.ui.AppViewModel
import de.fapdf.ui.theme.FaPdfTheme
import de.fapdf.ui.theme.FaColors
import de.fapdf.data.ThemeMode
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** FragmentActivity (statt ComponentActivity), weil BiometricPrompt sie benötigt. */
class MainActivity : FragmentActivity() {
    private val appVm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) appVm.handleIncoming(intent)
        setContent {
            val settings by appVm.container.settings.settings.collectAsState(initial = null)
            val mode = settings?.themeMode ?: ThemeMode.SYSTEM
            FaPdfTheme(mode) {
                // Statusleisten-Symbole passend zum gewählten Design
                val dark = FaColors.dark
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                if (settings != null) AppNav(appVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appVm.handleIncoming(intent)
    }
}
