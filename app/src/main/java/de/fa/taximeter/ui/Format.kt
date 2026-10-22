package de.fa.taximeter.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val DE = Locale.GERMANY

    fun km(meters: Double): String = String.format(DE, "%.2f km", meters / 1000.0)

    fun meters(m: Double): String = if (m >= 1000) km(m) else String.format(DE, "%.0f m", m)

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(DE, "%d:%02d:%02d", h, m, s) else String.format(DE, "%d:%02d", m, s)
    }

    fun speedKmh(mps: Double?): String = if (mps == null) "– km/h" else String.format(DE, "%.0f km/h", mps * 3.6)

    fun dateTime(wallMs: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", DE).format(Date(wallMs))

    fun time(wallMs: Long): String = SimpleDateFormat("HH:mm:ss", DE).format(Date(wallMs))

    fun decimal(value: Double, digits: Int = 2): String = String.format(DE, "%.${digits}f", value)
}
