package de.fapdf.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.fapdf.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sicherung aller Dokumente, Profile und Unterschriften in EINER Datei, mit Passwort verschlüsselt (AES-256).
 * Die Datei kann z. B. auf Google Drive gespeichert und auf einem neuen Handy wiederhergestellt werden.
 */
object Backup {
    private val MAGIC = "FAPDFBK1".toByteArray()

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    data class Result(val documents: Int, val profiles: Int, val signatures: Int)

    suspend fun create(c: AppContainer, password: String, out: OutputStream): Result = withContext(Dispatchers.IO) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        out.write(MAGIC); out.write(salt); out.write(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), IvParameterSpec(iv)) }
        val docs = c.documents.observeAll().first()
        val profiles = c.profiles.loadAll()
        val sigs = c.signatures.observeAll().first()
        var sigCount = 0
        ZipOutputStream(CipherOutputStream(out, cipher)).use { zip ->
            val manifest = JSONObject()
            val dArr = JSONArray()
            docs.forEach { d ->
                val f = File(d.filePath)
                if (!f.exists()) return@forEach
                val entry = "docs/${d.id}.pdf"
                zip.putNextEntry(ZipEntry(entry)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                dArr.put(JSONObject().put("name", d.displayName).put("file", entry).put("favorite", d.favorite)
                    .put("category", c.index.category(d.id) ?: ""))
            }
            manifest.put("documents", dArr)
            manifest.put("profiles", JSONArray().apply { profiles.forEach { put(JSONObject().put("label", it.label).put("data", it.toJson())) } })
            val sArr = JSONArray()
            sigs.forEach { s ->
                val bmp = c.signatures.loadBitmap(s) ?: return@forEach
                val entry = "signatures/${s.id}.png"
                zip.putNextEntry(ZipEntry(entry)); bmp.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry()
                sArr.put(JSONObject().put("name", s.name).put("file", entry)); sigCount++
            }
            manifest.put("signatures", sArr)
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
        }
        Result(docs.size, profiles.size, sigCount)
    }

    suspend fun restore(c: AppContainer, password: String, input: InputStream, tmpDir: File): Result = withContext(Dispatchers.IO) {
        val din = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { din.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw IllegalStateException("Das ist keine FA-PDF-Sicherung.")
        val salt = ByteArray(16).also { din.readFully(it) }
        val iv = ByteArray(16).also { din.readFully(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), IvParameterSpec(iv)) }
        val dir = File(tmpDir, "restore_${System.nanoTime()}").apply { mkdirs() }
        var manifest: JSONObject? = null
        try {
            ZipInputStream(CipherInputStream(din, cipher)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == "manifest.json") manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    else if (!e.name.contains("..")) File(dir, e.name).also { it.parentFile?.mkdirs() }.outputStream().use { zip.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw IllegalStateException("Falsches Passwort oder beschädigte Datei.")
        }
        val m = manifest ?: run { dir.deleteRecursively(); throw IllegalStateException("Falsches Passwort oder beschädigte Datei.") }
        val existing = c.documents.observeAll().first().map { it.displayName to File(it.filePath).length() }.toSet()
        var nd = 0; var np = 0; var ns = 0
        m.optJSONArray("documents")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val f = File(dir, o.optString("file")); if (!f.exists()) continue
                if ((o.optString("name") to f.length()) in existing) continue // schon vorhanden
                val id = c.documents.importFile(f, o.optString("name"))
                if (o.optBoolean("favorite")) c.documents.setFavorite(id, true)
                o.optString("category").takeIf { it.isNotBlank() }?.let { c.index.setCategory(id, it) }
                nd++
            }
        }
        val haveProfiles = c.profiles.loadAll().map { it.toJson() }.toSet()
        m.optJSONArray("profiles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = personProfileFromJson(0, o.optString("label"), o.optString("data"))
                if (p.toJson() in haveProfiles) continue
                c.profiles.save(p); np++
            }
        }
        val haveSigs = c.signatures.observeAll().first().map { it.name }.toSet()
        m.optJSONArray("signatures")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("name") in haveSigs) continue
                val bmp = BitmapFactory.decodeFile(File(dir, o.optString("file")).absolutePath) ?: continue
                c.signatures.save(bmp, o.optString("name"), null); ns++
            }
        }
        dir.deleteRecursively()
        Result(nd, np, ns)
    }
}
