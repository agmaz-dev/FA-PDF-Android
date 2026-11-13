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

    // ---------- Suchindex, Kategorien und KI-Namen (läuft leise im Hintergrund) ----------
    private var indexJob: kotlinx.coroutines.Job? = null
    var namingBusy by mutableStateOf<Long?>(null); private set

    init {
        viewModelScope.launch { runCatching { container.settings.nanoDefaultOnce() } }
        viewModelScope.launch { documents.collect { if (it.isNotEmpty()) scheduleIndex() } }
    }

    private fun scheduleIndex() {
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.delay(2500) // App erst fertig starten lassen
            val tmp = java.io.File(getApplication<Application>().cacheDir, "index").apply { mkdirs() }
            while (true) {
                val idx = container.index
                val d = documents.value.sortedByDescending { it.importedAt }
                    .firstOrNull { !it.encrypted && it.pageCount > 0 && !idx.hasText(it.id) } ?: break
                val text = runCatching {
                    de.fapdf.ai.DocumentText.extract(java.io.File(d.filePath), tmp, maxChars = 60_000, maxOcrPages = 3).text
                }.getOrDefault("")
                idx.setText(d.id, text.ifBlank { " " })
                if (idx.category(d.id) == null) de.fapdf.data.DocIndex.guessCategory(text)?.let { idx.setCategory(d.id, it) }
                if (idx.deadline(d.id) == null) de.fapdf.data.DocIndex.findDeadline(text)?.let { (t, txt) -> idx.setDeadline(d.id, t, txt, "") }
                // Frisch geöffnete Dokumente mit unverständlichem Namen automatisch benennen
                val fresh = System.currentTimeMillis() - d.importedAt < 2 * 24 * 3600_000L
                if (fresh && text.isNotBlank() && de.fapdf.data.DocIndex.isGenericName(d.displayName) && !idx.isNamed(d.id) && aiAllowed()) {
                    runCatching { nameWithAi(d, text) }
                }
            }
            // Ältere, kleine Vorschaubilder für die großen Karten schärfer neu erzeugen
            documents.value.forEach { d ->
                val p = d.thumbnailPath ?: return@forEach
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(p, o)
                if (o.outWidth in 1..399) runCatching { container.documents.refreshThumbnail(d.id) }
            }
        }
    }

    /** KI darf ohne Nachfrage arbeiten: auf dem Handy oder nach einmaliger Zustimmung. */
    suspend fun aiAllowed(): Boolean {
        val p = container.settings.current().aiProvider
        return p.isLocal || getApplication<Application>().getSharedPreferences("ai_consent", android.content.Context.MODE_PRIVATE).getBoolean(p.name, false)
    }

    fun consentGiven() = viewModelScope.launch {
        val p = container.settings.current().aiProvider
        getApplication<Application>().getSharedPreferences("ai_consent", android.content.Context.MODE_PRIVATE).edit().putBoolean(p.name, true).apply()
    }

    /** Fragt die KI nach Name und Kategorie. Gibt den neuen Namen zurück oder wirft eine verständliche Meldung. */
    private suspend fun nameWithAi(d: DocumentEntity, text: String): String {
        val ans = de.fapdf.ai.AiRouter.complete(
            container.settings,
            "Du gibst Briefen kurze Dateinamen. Antworte NUR mit zwei Zeilen, ohne Sternchen und ohne Erklärung:\n" +
                "TITEL: Absender – Art des Schreibens – Datum\n" +
                "KATEGORIE: eine aus: " + de.fapdf.data.DocIndex.CATEGORIES.joinToString(", ") + "\n" +
                "Beispiel:\nTITEL: HEK – Kontoauszug – 19.10.2026\nKATEGORIE: Krankenkasse",
            listOf(de.fapdf.ai.ChatMessage("user", "Brief:\n" + text.take(4000))),
            120,
        )
        val clean = de.fapdf.ai.AiText.clean(ans.text).replace("*", "").replace("#", "").replace("`", "")
        val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val titleLine = lines.firstOrNull { it.contains(Regex("(?i)^\\W*(titel|dateiname|name)\\s*:")) }?.substringAfter(":")
            ?: lines.firstOrNull { !it.contains(Regex("(?i)kategorie")) }
        val title = titleLine
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "-")?.replace(Regex("\\s+"), " ")?.trim()?.trim('-', ' ', '"', '„', '“', '.')?.take(80)
        val cat = de.fapdf.data.DocIndex.CATEGORIES.firstOrNull { c -> lines.any { it.contains(Regex("(?i)kategorie")) && it.contains(c, ignoreCase = true) } }
            ?: de.fapdf.data.DocIndex.guessCategory(text)
        if (cat != null) container.index.setCategory(d.id, cat)
        if (title.isNullOrBlank() || title.length < 5) throw IllegalStateException("Die KI (" + de.fapdf.ai.AiRouter.shortName(ans.provider) + ") hat keinen brauchbaren Namen geliefert.")
        container.documents.rename(d.id, title)
        container.index.markNamed(d.id)
        return title
    }

    /** „Mit KI benennen“ aus dem Dokument-Menü. Meldet immer ein Ergebnis zurück. */
    fun nameNow(id: Long, onDone: (String) -> Unit) = viewModelScope.launch {
        val d = documents.value.firstOrNull { it.id == id } ?: return@launch
        namingBusy = id
        try {
            val tmp = java.io.File(getApplication<Application>().cacheDir, "index").apply { mkdirs() }
            var text = container.index.text(id)?.takeIf { it.isNotBlank() }
            if (text == null) {
                text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    de.fapdf.ai.DocumentText.extract(java.io.File(d.filePath), tmp, maxChars = 60_000, maxOcrPages = 3).text
                }
                container.index.setText(id, text.ifBlank { " " })
            }
            if (text.replace(Regex("\\[Seite \\d+]"), "").isBlank()) onDone("Im Dokument wurde kein Text gefunden.")
            else onDone("Neuer Name: " + nameWithAi(d, text))
        } catch (e: Exception) {
            onDone(e.message ?: "Benennen fehlgeschlagen")
        } finally {
            namingBusy = null
        }
    }

    fun setCategory(id: Long, c: String?) = container.index.setCategory(id, c)

    fun rename(id: Long, name: String) = viewModelScope.launch { container.documents.rename(id, name) }
    fun setFavorite(id: Long, fav: Boolean) = viewModelScope.launch { container.documents.setFavorite(id, fav) }
    fun removeFromHistory(id: Long) = viewModelScope.launch { container.index.forget(id); container.documents.removeFromHistory(id) }
    fun deleteCompletely(id: Long, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        container.index.forget(id)
        onResult(container.documents.deleteCompletely(id))
    }
}
