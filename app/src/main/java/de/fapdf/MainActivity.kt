package de.fapdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                // Absturz vom letzten Mal anzeigen (zum Kopieren und Weiterschicken)
                var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashLog.take(this@MainActivity)) }
                crash?.let { text ->
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { crash = null },
                        title = { androidx.compose.material3.Text("Die App ist zuletzt abgestürzt") },
                        text = {
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                androidx.compose.material3.Text(text, fontSize = 11.sp)
                            }
                        },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            clip.setText(androidx.compose.ui.text.AnnotatedString(text)); crash = null
                            android.widget.Toast.makeText(this@MainActivity, "Fehlerbericht kopiert", android.widget.Toast.LENGTH_SHORT).show()
                        }) { androidx.compose.material3.Text("Kopieren") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { crash = null }) { androidx.compose.material3.Text("Schließen") } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appVm.handleIncoming(intent)
    }
}
