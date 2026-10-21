package de.fapdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import de.fapdf.data.AppDatabase
import de.fapdf.data.BookmarkRepository
import de.fapdf.data.CryptoManager
import de.fapdf.data.DocumentRepository
import de.fapdf.data.ProfileRepository
import de.fapdf.data.SettingsRepository
import de.fapdf.data.SignatureRepository
import de.fapdf.pdf.FontLibrary

/**
 * Einfache manuelle Dependency Injection. Bewusst ohne Hilt, damit das Projekt
 * mit möglichst wenigen Build-Plugins auskommt.
 */
class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val crypto = CryptoManager()
    val fonts = FontLibrary(app)
    val documents = DocumentRepository(app, database.documentDao())
    val profiles = ProfileRepository(database.profileDao(), crypto)
    val signatures = SignatureRepository(app, database.signatureDao(), crypto)
    val bookmarks = BookmarkRepository(database.bookmarkDao())
    val settings = SettingsRepository(app, crypto)
}

class FaPdfApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Pflicht für PdfBox-Android: lädt Glyphenlisten, CMaps und Standardschriften aus den Assets
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
