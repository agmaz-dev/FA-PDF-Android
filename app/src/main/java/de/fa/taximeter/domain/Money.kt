package de.fa.taximeter.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Geldhilfen. Intern wird mit BigDecimal ohne Zwischenrundung gerechnet;
 * gerundet wird ausschließlich für die Anzeige bzw. nach der Tarifregel
 * "jede angefangene Teilstrecke = 0,20 Euro".
 */
object Money {
    val MC: MathContext = MathContext.DECIMAL128
    val ZERO: BigDecimal = BigDecimal.ZERO

    fun eur(value: String): BigDecimal = BigDecimal(value)

    /** Auf Cent gerundet (HALF_UP) – nur für Anzeige/Abschluss. */
    fun cents(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    /** Deutsches Format ohne Währungszeichen: 1234,50 */
    fun formatDe(value: BigDecimal): String = cents(value).toPlainString().replace('.', ',')

    /** Deutsches Format mit Eurozeichen: 43,80 € */
    fun formatEuro(value: BigDecimal): String = formatDe(value) + " €"

    /** Parst "1,50" oder "1.50" – null bei ungültiger Eingabe oder negativem Wert. */
    fun parseDe(text: String): BigDecimal? {
        val t = text.trim().replace("€", "").replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        return try {
            val v = BigDecimal(t)
            if (v.signum() < 0) null else v
        } catch (_: NumberFormatException) {
            null
        }
    }
}
