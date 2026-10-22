package de.fa.taximeter.domain.tariff

import de.fa.taximeter.domain.trip.SurchargeDef
import java.math.BigDecimal

/**
 * Amtliche Ausgangsprofile. Diese Daten sind bewusst vom UI-Code getrennt und werden
 * niemals automatisch überschrieben. Benutzerdefinierte Kopien entstehen nur über
 * [customCopy] und tragen isCustom = true.
 *
 * Rechtsgrundlage: Verordnung über Beförderungsentgelte im Taxenverkehr (Berlin) vom
 * 6. Dezember 2005, zuletzt geändert durch die Zwölfte Änderungsverordnung vom
 * 10.11.2022 (GVBl. S. 606, in Kraft 20.12.2022) und die Dreizehnte Änderungsverordnung
 * (Festpreise/Tarifkorridor, in Kraft 28.05.2024 – ändert §§ 4 und 5 nicht).
 */
object TariffCatalog {

    const val VERIFIED_ON = "22.10.2026"

    private const val LEGAL =
        "VO über Beförderungsentgelte im Taxenverkehr (Berlin) v. 06.12.2005, §§ 1, 2, 4, 5; " +
            "zuletzt geändert durch 12. ÄndVO v. 10.11.2022 (GVBl. S. 606) und 13. ÄndVO (2024)"

    val SOURCES = listOf(
        TariffSource(
            "Zwölfte Verordnung zur Änderung der VO über Beförderungsentgelte im Taxenverkehr, GVBl. Nr. 52 v. 22.11.2022, S. 606",
            "https://pardok.parlament-berlin.de/starweb/adis/citat/VT/19/gvbl/g22520606.pdf",
        ),
        TariffSource(
            "Konsolidierte Fassung TaxBefEntgV Berlin (Stand 07.06.2023)",
            "https://lexaris.de/book/version/documentflat/head/1254175",
        ),
        TariffSource(
            "Dreizehnte Änderungsverordnung (Festpreise/Tarifkorridor), GVBl. Nr. 18/2024",
            "https://www.berlin.de/sen/justiz/service/gesetze-und-verordnungen/2024/ausgabe-nr-18-vom-162024-s-137-144.pdf",
        ),
        TariffSource("Amtliche Gesetzessammlung Berlin", "https://gesetze.berlin.de/"),
        TariffSource("Taxi Berlin – Taxitarif", "https://www.taxi-berlin.de/taxitarif/"),
    )

    private val COMMON_NOTES = listOf(
        "Grundpreis 4,30 € enthält bereits 0,20 € für die erste Teilstrecke (§ 4 Abs. 1).",
        "Jede angefangene Teilstrecke wird mit 0,20 € berechnet (§ 4 Abs. 3). Die App leitet die Teilstreckenlänge aus 0,20 € / Kilometerpreis ab.",
        "Wartezeiten (auch verkehrsbedingte) von mehr als einer Minute je Halt werden berechnet (§ 5 Abs. 1).",
        "Die genaue Umschaltung zwischen Strecken- und Zeitberechnung ist in der Verordnung nicht geregelt (Eichrecht/Taxameter). Die App verwendet daher eine klar gekennzeichnete Schätzung.",
        "Kurzstreckenpauschaltarif (Tarifstufe 1) ist nicht implementiert.",
    )

    val BER = TariffProfile(
        id = "berlin-ts3-flughafen",
        version = "2022-12-20",
        displayNumber = 3,
        displayName = "BER",
        officialName = "Tarifstufe 3 – Flughafentarif",
        scope = "Fahrten, die am Flughafen BER beginnen, innerhalb des Tarifgebiets (Berlin sowie LDS und " +
            "die in § 1 Abs. 2 genannten Orte). Kein allgemeiner Brandenburg-Tarif.",
        baseFare = BigDecimal("4.30"),
        baseIncludesFirstStep = true,
        stepAmount = BigDecimal("0.20"),
        bands = listOf(
            DistanceBand(0, 5_000, BigDecimal("2.60")),
            DistanceBand(5_000, null, BigDecimal("2.00")),
        ),
        // Amtlich 36,00 €/h für Tarifstufe 3 (§ 5 Abs. 1 i. d. F. der 12. ÄndVO).
        // Abweichung zur Vorgabe (39,00 €/h) ist im README dokumentiert.
        waitingPerHour = BigDecimal("36.00"),
        waitingGraceMs = 60_000,
        calculationMode = CalculationMode.ESTIMATE_SWITCH_SPEED,
        sources = SOURCES,
        verifiedOn = VERIFIED_ON,
        legalBasis = LEGAL,
        notes = COMMON_NOTES + listOf(
            "Wartezeit Tarifstufe 3 laut Verordnung 36,00 €/h (nicht 39,00 €/h wie in der Ausgangsrecherche).",
        ),
        isCustom = false,
    )

    val BERLIN = TariffProfile(
        id = "berlin-ts2-normal",
        version = "2022-12-20",
        displayNumber = 4,
        displayName = "Berlin",
        officialName = "Tarifstufe 2 – Tarif für sonstige Fahrten",
        scope = "Fahrten innerhalb Berlins sowie von Berlin zum BER. Anzeige-Nummer 4 ist benutzerdefiniert; " +
            "amtlich heißt dieser Tarif Tarifstufe 2.",
        baseFare = BigDecimal("4.30"),
        baseIncludesFirstStep = true,
        stepAmount = BigDecimal("0.20"),
        bands = listOf(
            DistanceBand(0, 3_000, BigDecimal("2.80")),
            DistanceBand(3_000, 7_000, BigDecimal("2.60")),
            DistanceBand(7_000, null, BigDecimal("2.10")),
        ),
        waitingPerHour = BigDecimal("39.00"),
        waitingGraceMs = 60_000,
        calculationMode = CalculationMode.ESTIMATE_SWITCH_SPEED,
        sources = SOURCES,
        verifiedOn = VERIFIED_ON,
        legalBasis = LEGAL,
        notes = COMMON_NOTES,
        isCustom = false,
    )

    val OFFICIAL: List<TariffProfile> = listOf(BER, BERLIN)

    fun officialByNumber(number: Int): TariffProfile? = OFFICIAL.firstOrNull { it.displayNumber == number }

    /** Erzeugt eine bearbeitbare, klar gekennzeichnete Kopie. Das Original bleibt unverändert. */
    fun customCopy(
        base: TariffProfile,
        baseFare: BigDecimal,
        bands: List<DistanceBand>,
        waitingPerHour: BigDecimal,
        editedOn: String,
    ): TariffProfile = base.copy(
        id = base.id + "-custom",
        version = "custom-$editedOn",
        baseFare = baseFare,
        bands = bands,
        waitingPerHour = waitingPerHour,
        officialName = base.officialName + " (benutzerdefinierte Kopie)",
        verifiedOn = "benutzerdefiniert, bearbeitet $editedOn",
        notes = listOf("BENUTZERDEFINIERT – Werte wurden manuell geändert und sind nicht amtlich geprüft.") + base.notes,
        isCustom = true,
    )

    // ---------------------------------------------------------------- Zuschläge (§ 5 Abs. 2)

    val SURCHARGE_BER = SurchargeDef(
        id = "ber-infrastruktur",
        label = "BER-Zuschlag",
        amount = BigDecimal("1.50"),
        condition = "Nur bei Aufnahme von Fahrgästen am Flughafen BER durch Taxen, die die kostenpflichtige " +
            "Taxeninfrastruktur mit Aufruf zur Ladung benutzen (§ 5 Abs. 2 c). Nicht für Fahrten mit Ziel BER.",
        legalRef = "§ 5 Abs. 2 Buchst. c",
        custom = false,
    )

    val SURCHARGE_GROSSRAUM = SurchargeDef(
        id = "grossraum",
        label = "GROSSRAUM",
        amount = BigDecimal("6.00"),
        condition = "Pauschal bei Beförderung der 5. bis 8. Person (zwei Kinder unter 10 Jahren zählen als " +
            "eine Person) oder wenn Gepäck ein Großraumtaxi erfordert (§ 5 Abs. 2 a).",
        legalRef = "§ 5 Abs. 2 Buchst. a",
        custom = false,
    )

    val SURCHARGE_GUTSCHEIN = SurchargeDef(
        id = "zentralen-abrechnung",
        label = "GUTSCHEIN/ABRECHN.",
        amount = BigDecimal("1.50"),
        condition = "Nur bei Bezahlung über das Gutschein- bzw. Abrechnungssystem der Taxizentralen " +
            "(§ 5 Abs. 2 b). Kein Zuschlag für gewöhnliche Kartenzahlung.",
        legalRef = "§ 5 Abs. 2 Buchst. b",
        custom = false,
    )

    val SURCHARGES: List<SurchargeDef> = listOf(SURCHARGE_BER, SURCHARGE_GROSSRAUM, SURCHARGE_GUTSCHEIN)
}
