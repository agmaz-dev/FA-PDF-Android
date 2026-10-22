package de.fa.taximeter.domain.trip

import de.fa.taximeter.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** Definition eines Zuschlags. Benutzerdefinierte Zuschläge tragen [custom] = true. */
data class SurchargeDef(
    val id: String,
    val label: String,
    val amount: BigDecimal,
    val condition: String,
    val legalRef: String,
    val custom: Boolean,
) {
    init {
        require(id.isNotBlank()) { "id leer" }
        require(amount.signum() >= 0) { "Zuschlag negativ" }
    }

    val displayLabel: String get() = if (custom) "$label (benutzerdefiniert)" else label
}

/**
 * Ausgewählte Zuschläge einer Fahrt. Jeder Zuschlag kann höchstens einmal enthalten sein;
 * die Beträge sind beim Auswählen eingefroren.
 */
data class SurchargeSelection(val items: List<SurchargeDef> = emptyList()) {

    fun contains(id: String): Boolean = items.any { it.id == id }

    /** Fügt hinzu oder entfernt (Umschalten). Doppelbuchung ist ausgeschlossen. */
    fun toggle(def: SurchargeDef): SurchargeSelection =
        if (contains(def.id)) SurchargeSelection(items.filterNot { it.id == def.id })
        else SurchargeSelection(items + def)

    fun add(def: SurchargeDef): SurchargeSelection =
        if (contains(def.id)) this else SurchargeSelection(items + def)

    fun remove(id: String): SurchargeSelection = SurchargeSelection(items.filterNot { it.id == id })

    val total: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.amount, Money.MC) }.setScale(2, RoundingMode.HALF_UP)
}
