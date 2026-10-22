package de.fa.taximeter.domain.tariff

import java.math.BigDecimal

/**
 * Kilometerstaffel. Gilt abschnittsweise für die Strecke von [fromM] bis [toM] (exklusiv der
 * Obergrenze). [toM] == null bedeutet "ohne Obergrenze".
 */
data class DistanceBand(
    val fromM: Long,
    val toM: Long?,
    val pricePerKm: BigDecimal,
) {
    init {
        require(fromM >= 0) { "fromM < 0" }
        require(toM == null || toM > fromM) { "toM <= fromM" }
        require(pricePerKm.signum() > 0) { "pricePerKm <= 0" }
    }
}

/** Quellenangabe zu einem Tarifwert. */
data class TariffSource(val title: String, val url: String)

/**
 * Berechnungsmodus. Nur ein Modus ist implementiert; der Name sagt bewusst "Schätzung",
 * weil die amtliche Verordnung die Umschaltung Zeit/Strecke nicht im Detail regelt
 * (siehe README, Abschnitt "Ungeklärte Punkte").
 */
enum class CalculationMode(val label: String) {
    ESTIMATE_SWITCH_SPEED(
        "Schätzung: Strecke oder Zeit je Intervall nach Umschaltgeschwindigkeit, " +
            "Wartezeit nach 1 Minute je Halt, Fortschaltung in 0,20-€-Schritten"
    ),
}

/**
 * Tarifprofil. Trennt
 *  - interne Kennung ([id] + [version]),
 *  - amtliche Bezeichnung ([officialName], z. B. "Tarifstufe 2"),
 *  - sichtbare Anzeige-Nummer ([displayNumber], z. B. 4).
 *
 * Ein laufender oder gespeicherter Fahrtdatensatz enthält immer eine vollständige Kopie
 * des Profils, damit spätere Tarifänderungen ihn nicht verändern.
 */
data class TariffProfile(
    val id: String,
    val version: String,
    val displayNumber: Int,
    val displayName: String,
    val officialName: String,
    val scope: String,
    val baseFare: BigDecimal,
    /** Im Grundpreis bereits enthaltener erster Fortschaltbetrag (amtlich: 0,20 €). */
    val baseIncludesFirstStep: Boolean,
    /** Fortschaltbetrag je angefangener Teilstrecke (amtlich: 0,20 €). */
    val stepAmount: BigDecimal,
    val bands: List<DistanceBand>,
    val waitingPerHour: BigDecimal,
    /** Freie Wartezeit je Halt in Millisekunden (amtlich: mehr als 1 Minute wird berechnet). */
    val waitingGraceMs: Long,
    val calculationMode: CalculationMode,
    val sources: List<TariffSource>,
    val verifiedOn: String,
    val legalBasis: String,
    val notes: List<String>,
    val isCustom: Boolean,
) {
    init {
        require(bands.isNotEmpty()) { "Keine Kilometerstaffel" }
        require(bands.first().fromM == 0L) { "Erste Staffel muss bei 0 m beginnen" }
        for (i in 1 until bands.size) {
            require(bands[i - 1].toM == bands[i].fromM) { "Staffeln müssen lückenlos sein" }
        }
        require(bands.last().toM == null) { "Letzte Staffel braucht offene Obergrenze" }
        require(stepAmount.signum() > 0) { "stepAmount <= 0" }
        require(baseFare.signum() >= 0) { "baseFare < 0" }
        require(waitingPerHour.signum() >= 0) { "waitingPerHour < 0" }
        require(waitingGraceMs >= 0) { "waitingGraceMs < 0" }
        require(!baseIncludesFirstStep || baseFare >= stepAmount) { "Grundpreis < Schaltbetrag" }
    }

    /** Staffel, in der die Position [odometerM] liegt. */
    fun bandAt(odometerM: Double): DistanceBand =
        bands.firstOrNull { odometerM >= it.fromM && (it.toM == null || odometerM < it.toM) }
            ?: bands.last()

    val label: String get() = "$displayNumber – $displayName"
}
