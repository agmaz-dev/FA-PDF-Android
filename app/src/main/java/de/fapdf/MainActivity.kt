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

/** FragmentActivity (statt ComponentActivity), weil BiometricPrompt sie benötigt. */
class MainActivity : FragmentActivity() {
    private val appVm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) appVm.handleIncoming(intent)
        setContent { FaPdfTheme { AppNav(appVm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appVm.handleIncoming(intent)
    }
}
