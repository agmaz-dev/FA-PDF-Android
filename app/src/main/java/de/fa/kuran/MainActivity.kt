package de.fa.kuran

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.fa.kuran.data.AppState
import de.fa.kuran.data.Quran
import de.fa.kuran.data.Scans
import de.fa.kuran.ui.IndexScreen
import de.fa.kuran.ui.ReaderScreen
import de.fa.kuran.ui.SettingsScreen
import de.fa.kuran.ui.WelcomeScreen
import de.fa.kuran.ui.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val st = AppState(applicationContext)
        setContent { App(st, ::applyImmersive) }
    }

    private fun applyImmersive(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (on) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
    }
}

enum class Screen { READER, INDEX, SETTINGS }

@Composable
fun App(st: AppState, immersive: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var quran by remember { mutableStateOf<Quran?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by rememberSaveable { mutableStateOf(Screen.READER) }

    LaunchedEffect(Unit) {
        try {
            quran = withContext(Dispatchers.Default) { Quran.load(ctx) }
        } catch (e: Exception) {
            error = e.toString()
        }
    }
    LaunchedEffect(st.scanUri) {
        val u = st.scanUri ?: return@LaunchedEffect
        st.scans = withContext(Dispatchers.IO) { runCatching { Scans.index(ctx, Uri.parse(u)) }.getOrDefault(emptyMap()) }
    }

    MaterialTheme {
        val pal = palette(st.theme)
        val q = quran
        when {
            error != null -> Box(Modifier.fillMaxSize().background(pal.app).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Kur'an verisi yüklenemedi:\n$error", color = pal.ink)
            }
            q == null -> Box(Modifier.fillMaxSize().background(pal.app), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = pal.accent)
                    Text("Kur'an-ı Kerim yükleniyor…", color = pal.muted)
                }
            }
            st.tefsir == null -> WelcomeScreen(st) { st.tefsir = it }
            screen == Screen.INDEX -> {
                BackHandler { screen = Screen.READER }
                IndexScreen(q!!, st, onGo = { p -> st.lastPage = p; st.jump = p; screen = Screen.READER }, onBack = { screen = Screen.READER })
            }
            screen == Screen.SETTINGS -> {
                BackHandler { screen = Screen.READER }
                SettingsScreen(q!!, st, onBack = { screen = Screen.READER })
            }
            else -> {
                DisposableEffect(Unit) {
                    immersive(true)
                    onDispose { immersive(false) }
                }
                ReaderScreen(q!!, st, onIndex = { screen = Screen.INDEX }, onSettings = { screen = Screen.SETTINGS })
            }
        }
    }
}

@Composable
@Suppress("unused")
private fun Placeholder() = Unit
