package de.fapdf.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.fapdf.pdf.PdfIo
import de.fapdf.pdf.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

class ImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

class DocumentRepository(private val context: Context, private val dao: DocumentDao) {
    private val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun observeAll(): Flow<List<DocumentEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<DocumentEntity?> = dao.observe(id)
    suspend fun get(id: Long): DocumentEntity? = dao.get(id)

    /** Kopiert eine fremde Datei (Gmail, Dateien-App, Freigabe) in die Bibliothek. */
    suspend fun importUri(uri: Uri, tryPersistWrite: Boolean = false): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var persisted = false
        if (tryPersistWrite) {
            persisted = runCatching {
                resolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ); true
            }.getOrElse {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                false
            }
        }
        val name = queryDisplayName(uri) ?: "Dokument.pdf"
        val target = newFile(name)
        try {
            val input = resolver.openInputStream(uri) ?: throw ImportException("Datei kann nicht gelesen werden")
            input.use { inp -> target.outputStream().use { inp.copyTo(it) } }
        } catch (e: SecurityException) {
            target.delete(); throw ImportException("Keine Leseberechtigung für diese Datei", e)
        }
        registerFile(target, name, if (persisted) uri.toString() else null)
    }

    /** Registriert eine bereits im App-Speicher liegende Datei (z. B. Ergebnis eines Werkzeugs). */
    suspend fun importFile(source: File, displayName: String): Long = withContext(Dispatchers.IO) {
        val target = newFile(displayName)
        source.copyTo(target, overwrite = true)
        registerFile(target, displayName, null)
    }

    private suspend fun registerFile(file: File, name: String, sourceUri: String?): Long {
        val info = try {
            PdfIo.inspect(file)
        } catch (e: Exception) {
            file.parentFile?.deleteRecursively()
            throw ImportException("Die Datei ist keine gültige oder eine beschädigte PDF-Datei.", e)
        }
        val now = System.currentTimeMillis()
        val id = dao.insert(
            DocumentEntity(
                displayName = ensurePdfName(name), filePath = file.absolutePath, sourceUri = sourceUri,
                importedAt = now, modifiedAt = now, lastOpenedAt = now, pageCount = info.pageCount,
                sizeBytes = file.length(), encrypted = info.encrypted,
            )
        )
        refreshThumbnail(id)
        return id
    }

    /** Ersetzt den Inhalt eines Bibliotheksdokuments durch eine neue Version. */
    suspend fun replaceContent(id: Long, newContent: File) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        val target = File(doc.filePath)
        val tmp = File(target.parentFile, target.name + ".tmp")
        newContent.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        val info = runCatching { PdfIo.inspect(target) }.getOrNull()
        dao.update(
            doc.copy(
                modifiedAt = System.currentTimeMillis(), sizeBytes = target.length(),
                pageCount = info?.pageCount ?: doc.pageCount, encrypted = info?.encrypted ?: doc.encrypted,
            )
        )
        refreshThumbnail(id)
    }

    /** Schreibt zusätzlich in die Originaldatei zurück, sofern dauerhafte Schreibrechte bestehen. */
    suspend fun writeBackToSource(id: Long): Boolean = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext false
        val uri = doc.sourceUri?.let(Uri::parse) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                File(doc.filePath).inputStream().use { it.copyTo(out) }
            } != null
        }.getOrDefault(false)
    }

    suspend fun touchOpened(id: Long) {
        val d = dao.get(id) ?: return
        dao.update(d.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: Long, newName: String) {
        val d = dao.get(id) ?: return
        dao.update(d.copy(displayName = ensurePdfName(newName.trim().ifBlank { d.displayName })))
    }

    suspend fun setFavorite(id: Long, fav: Boolean) {
        val d = dao.get(id) ?: return
        dao.update(d.copy(favorite = fav))
    }

    /** Entfernt das Dokument aus dem Verlauf und löscht die App-Kopie. Die Originaldatei bleibt erhalten. */
    suspend fun removeFromHistory(id: Long) = withContext(Dispatchers.IO) {
        val d = dao.get(id) ?: return@withContext
        File(d.filePath).parentFile?.deleteRecursively()
        d.thumbnailPath?.let { File(it).delete() }
        dao.delete(id)
    }

    /**
     * Löscht App-Kopie und – falls die App Schreibrechte auf die Originaldatei besitzt – auch das Original.
     * @return true, wenn auch das Original gelöscht wurde.
     */
    suspend fun deleteCompletely(id: Long): Boolean = withContext(Dispatchers.IO) {
        val d = dao.get(id) ?: return@withContext false
        val originalDeleted = d.sourceUri?.let { s ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(s)) }.getOrDefault(false)
        } ?: false
        removeFromHistory(id)
        originalDeleted
    }

    suspend fun refreshThumbnail(id: Long) = withContext(Dispatchers.IO) {
        val d = dao.get(id) ?: return@withContext
        val thumb = File(thumbsDir, "doc_$id.png")
        val ok = runCatching { Thumbnails.render(File(d.filePath), thumb, 240) }.getOrDefault(false)
        dao.update(d.copy(thumbnailPath = if (ok) thumb.absolutePath else null))
    }

    fun fileOf(doc: DocumentEntity) = File(doc.filePath)

    private fun newFile(name: String): File {
        val dir = File(docsDir, UUID.randomUUID().toString()).apply { mkdirs() }
        return File(dir, sanitize(ensurePdfName(name)))
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        fun ensurePdfName(name: String): String = if (name.lowercase().endsWith(".pdf")) name else "$name.pdf"
        fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        fun baseName(name: String): String = name.removeSuffix(".pdf").removeSuffix(".PDF")
    }
}

class ProfileRepository(private val dao: ProfileDao, private val crypto: CryptoManager) {
    fun observeLabels(): Flow<List<Pair<Long, String>>> = dao.observeAll().map { list -> list.map { it.id to it.label } }

    suspend fun loadAll(): List<PersonProfile> = withContext(Dispatchers.IO) {
        dao.getAll().mapNotNull { decode(it) }
    }

    suspend fun load(id: Long): PersonProfile? = withContext(Dispatchers.IO) { dao.get(id)?.let { decode(it) } }

    suspend fun save(p: PersonProfile): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = if (p.id != 0L) dao.get(p.id) else null
        dao.upsert(
            ProfileEntity(
                id = p.id, label = p.label.ifBlank { p.fullName.ifBlank { "Profil" } },
                encryptedData = crypto.encryptString(p.toJson()),
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private fun decode(e: ProfileEntity): PersonProfile? = runCatching {
        personProfileFromJson(e.id, e.label, crypto.decryptString(e.encryptedData))
    }.getOrNull()
}

class SignatureRepository(context: Context, private val dao: SignatureDao, private val crypto: CryptoManager) {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    fun observeAll(): Flow<List<SignatureEntity>> = dao.observeAll()

    suspend fun save(bitmap: Bitmap, name: String, profileId: Long?): Long = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val fileName = "sig_${UUID.randomUUID()}.bin"
        File(dir, fileName).writeBytes(crypto.encrypt(bytes))
        dao.insert(
            SignatureEntity(
                name = name.ifBlank { "Unterschrift" }, profileId = profileId, fileName = fileName,
                widthPx = bitmap.width, heightPx = bitmap.height, createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** Entschlüsselt die Unterschrift (PNG mit transparentem Hintergrund). */
    suspend fun loadBitmap(s: SignatureEntity): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = crypto.decrypt(File(dir, s.fileName).readBytes())
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    suspend fun update(s: SignatureEntity) = dao.update(s)

    suspend fun delete(s: SignatureEntity) = withContext(Dispatchers.IO) {
        File(dir, s.fileName).delete()
        dao.delete(s)
    }
}

class BookmarkRepository(private val dao: BookmarkDao) {
    fun observe(docId: Long) = dao.observeFor(docId)
    suspend fun add(docId: Long, page: Int, label: String) =
        dao.insert(BookmarkEntity(documentId = docId, pageIndex = page, label = label, createdAt = System.currentTimeMillis()))
    suspend fun delete(id: Long) = dao.delete(id)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context, private val crypto: CryptoManager) {
    private object Keys {
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val THEME = stringPreferencesKey("theme_mode")
        val PROVIDER = stringPreferencesKey("ai_provider")
        val MODEL = stringPreferencesKey("ai_model")
        val KEY = stringPreferencesKey("ai_key_enc")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val provider = p[Keys.PROVIDER]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.ANTHROPIC
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            biometricLock = p[Keys.BIOMETRIC] ?: false,
            aiProvider = provider,
            aiModel = p[Keys.MODEL] ?: provider.defaultModel,
            hasAiKey = !p[Keys.KEY].isNullOrBlank(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBiometric(on: Boolean) = context.dataStore.edit { it[Keys.BIOMETRIC] = on }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setAi(provider: AiProvider, model: String) = context.dataStore.edit {
        it[Keys.PROVIDER] = provider.name
        it[Keys.MODEL] = model.trim()
    }

    /** Der API-Schlüssel wird nur verschlüsselt (Android Keystore) gespeichert – niemals in der APK. */
    suspend fun setApiKey(key: String?) = context.dataStore.edit {
        if (key.isNullOrBlank()) it.remove(Keys.KEY)
        else it[Keys.KEY] = Base64.encodeToString(crypto.encryptString(key.trim()), Base64.NO_WRAP)
    }

    suspend fun apiKey(): String? {
        val enc = context.dataStore.data.first()[Keys.KEY] ?: return null
        return runCatching { crypto.decryptString(Base64.decode(enc, Base64.NO_WRAP)) }.getOrNull()
    }
}
