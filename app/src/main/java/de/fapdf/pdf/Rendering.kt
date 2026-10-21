package de.fapdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * Seitendarstellung über den in Android eingebauten PdfRenderer (PDFium).
 * Kostenlos, ohne zusätzliche Bibliothek. Kann nur unverschlüsselte Dateien öffnen –
 * deshalb erzeugt [PreviewFactory] bei Bedarf eine entschlüsselte, abgeflachte Vorschaukopie.
 */
class PdfPageSource private constructor(
    val file: File,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val mutex = Mutex()
    val pageCount: Int = renderer.pageCount
    /** Seitengröße in Punkten (Ansicht, also nach Drehung und CropBox). */
    val pageSizes: List<Pair<Float, Float>> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { Pair(it.width.toFloat(), it.height.toFloat()) }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = mutex.withLock { renderBlocking(index, widthPx) }

    fun renderBlocking(index: Int, widthPx: Int): Bitmap = synchronized(renderer) {
        renderer.openPage(index).use { page ->
            val w = widthPx.coerceIn(16, 4000)
            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(16)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(file: File): PdfPageSource {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                return PdfPageSource(file, pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }
    }
}

object PreviewFactory {
    /**
     * Liefert eine Datei, die der PdfRenderer korrekt anzeigen kann:
     * entschlüsselt sowie mit eingebrannten Formularwerten und Kommentaren
     * (der Android-Renderer zeigt Anmerkungen nicht zuverlässig an).
     */
    fun prepare(src: File, out: File, password: String? = null): File {
        PdfIo.load(src, password).use { doc ->
            val hasForm = runCatching { doc.documentCatalog.acroForm?.fields?.isNotEmpty() == true }.getOrDefault(false)
            val hasAnnots = runCatching {
                doc.pages.any { p -> p.annotations.any { it.subtype != "Link" && it.subtype != "Popup" } }
            }.getOrDefault(false)
            if (!doc.isEncrypted && !hasForm && !hasAnnots) return src
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            runCatching { PdfOps.flattenAll(doc) }
            PdfIo.save(doc, out)
            return out
        }
    }
}

object Thumbnails {
    fun render(file: File, out: File, widthPx: Int): Boolean {
        val tmp = File(file.parentFile, ".thumb_preview.pdf")
        try {
            val renderable = runCatching { PreviewFactory.prepare(file, tmp) }.getOrNull() ?: return false
            PdfPageSource.open(renderable).use { src ->
                if (src.pageCount == 0) return false
                val bmp = src.renderBlocking(0, widthPx)
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                return true
            }
        } catch (e: Exception) {
            return false
        } finally {
            tmp.delete()
        }
    }
}
