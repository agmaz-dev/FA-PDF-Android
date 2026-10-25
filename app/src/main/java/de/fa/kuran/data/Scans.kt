package de.fa.kuran.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile

/**
 * Kullanıcının kendi taradığı mushaf sayfaları (Osmanlı hattı).
 * Klasördeki dosya adındaki son sayı sayfa numarası kabul edilir: 001.jpg, sayfa_604.png ...
 */
object Scans {
    private val imageExt = Regex(".*\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)
    private val number = Regex("(\\d+)")

    fun index(ctx: Context, tree: Uri): Map<Int, Uri> {
        val dir = DocumentFile.fromTreeUri(ctx, tree) ?: return emptyMap()
        val map = HashMap<Int, Uri>()
        for (f in dir.listFiles()) {
            val name = f.name ?: continue
            if (!f.isFile || !imageExt.matches(name)) continue
            val n = number.findAll(name.substringBeforeLast('.')).lastOrNull()?.value?.toIntOrNull() ?: continue
            if (n in 1..604) map[n] = f.uri
        }
        return map
    }

    fun load(ctx: Context, uri: Uri, maxDim: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    }.getOrNull()
}
