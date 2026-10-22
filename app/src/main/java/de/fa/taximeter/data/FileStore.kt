package de.fa.taximeter.data

import android.content.Context
import android.util.Log
import de.fa.taximeter.domain.persist.Codecs
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.TripRecord
import java.io.File

/**
 * Lokale Dateiablage im privaten App-Speicher. Schreiben erfolgt atomar
 * (temporäre Datei + Umbenennen), damit ein Prozessabbruch keine halbe Datei hinterlässt.
 * Es werden keine Daten übertragen.
 */
class FileStore(context: Context) {

    private val dir: File = File(context.filesDir, "taximeter").apply { mkdirs() }
    private val stateFile = File(dir, "active_state.json")
    private val historyFile = File(dir, "history.json")

    fun loadState(): MeterMachineState? = read(stateFile)?.let {
        try {
            Codecs.machineFromJson(it)
        } catch (e: Exception) {
            Log.e(TAG, "Fahrtzustand unlesbar – wird verworfen", e)
            backupCorrupt(stateFile)
            null
        }
    }

    fun saveState(state: MeterMachineState) = writeAtomic(stateFile, Codecs.machineToJson(state))

    fun loadHistory(): List<TripRecord> = read(historyFile)?.let {
        try {
            Codecs.historyFromJson(it)
        } catch (e: Exception) {
            Log.e(TAG, "Historie unlesbar – Sicherungskopie angelegt", e)
            backupCorrupt(historyFile)
            emptyList()
        }
    } ?: emptyList()

    fun saveHistory(records: List<TripRecord>) = writeAtomic(historyFile, Codecs.historyToJson(records))

    private fun read(f: File): String? = if (f.exists()) f.readText(Charsets.UTF_8) else null

    private fun writeAtomic(f: File, text: String) {
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) throw IllegalStateException("Umbenennen fehlgeschlagen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Speichern fehlgeschlagen: ${f.name}", e)
        }
    }

    private fun backupCorrupt(f: File) {
        try {
            f.renameTo(File(f.parentFile, f.name + ".corrupt-" + System.currentTimeMillis()))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "FileStore"
    }
}
