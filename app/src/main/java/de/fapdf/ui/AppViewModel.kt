package de.fapdf.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fapdf.FaPdfApp
import de.fapdf.data.DocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-weiter Zustand: Dokumentbibliothek und eingehende Dateien („Öffnen mit“, „Teilen“). */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as FaPdfApp).container

    val documents: StateFlow<List<DocumentEntity>> =
        container.documents.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Dokument, für das das Aktionsmenü angezeigt werden soll. */
    var actionDocId by mutableStateOf<Long?>(null)
    var busy by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null)

    fun import(uri: Uri, persist: Boolean, onDone: (Long) -> Unit = { actionDocId = it }) {
        viewModelScope.launch {
            busy = "PDF wird geöffnet …"
            try {
                onDone(container.documents.importUri(uri, persist))
            } catch (e: Exception) {
                error = e.message ?: "Die Datei konnte nicht geöffnet werden."
            } finally {
                busy = null
            }
        }
    }

    fun importMany(uris: List<Uri>, onDone: (List<Long>) -> Unit) {
        viewModelScope.launch {
            busy = "PDFs werden geöffnet …"
            val ids = ArrayList<Long>()
            try {
                uris.forEach { ids.add(container.documents.importUri(it, false)) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = null
                onDone(ids)
            }
        }
    }

    /** Verarbeitet VIEW/SEND/SEND_MULTIPLE-Intents von anderen Apps (z. B. Gmail). */
    fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamListExtra(intent)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        intent.action = Intent.ACTION_MAIN // nicht erneut verarbeiten (z. B. nach Drehung)
        if (uris.size == 1) import(uris.first(), persist = false)
        else importMany(uris) { ids -> ids.firstOrNull()?.let { actionDocId = it } }
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()

    fun rename(id: Long, name: String) = viewModelScope.launch { container.documents.rename(id, name) }
    fun setFavorite(id: Long, fav: Boolean) = viewModelScope.launch { container.documents.setFavorite(id, fav) }
    fun removeFromHistory(id: Long) = viewModelScope.launch { container.documents.removeFromHistory(id) }
    fun deleteCompletely(id: Long, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(container.documents.deleteCompletely(id))
    }
}
