package de.fa.taximeter.domain.persist

import de.fa.taximeter.domain.gps.GapReason
import de.fa.taximeter.domain.gps.GapRecord
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.gps.TrackerState
import de.fa.taximeter.domain.tariff.CalculationMode
import de.fa.taximeter.domain.tariff.DistanceBand
import de.fa.taximeter.domain.tariff.MeterState
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.tariff.TariffSource
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.PaymentMethod
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.domain.trip.SurchargeDef
import de.fa.taximeter.domain.trip.SurchargeSelection
import de.fa.taximeter.domain.trip.TripRecord
import de.fa.taximeter.domain.trip.TripRun
import java.math.BigDecimal

/**
 * JSON-Abbildung aller gespeicherten Domänenobjekte. Geldbeträge als String (verlustfrei).
 * Jede Datei trägt eine Formatversion, damit spätere Änderungen erkannt werden.
 */
object Codecs {

    const val FORMAT_VERSION = 1

    // ------------------------------------------------------------------ Hilfen

    private fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: throw MiniJson.ParseException("Feld $k fehlt")
    private fun Map<String, Any?>.strOrNull(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.dec(k: String): BigDecimal = BigDecimal(str(k))
    private fun Map<String, Any?>.num(k: String): MiniJson.JsonNumber =
        this[k] as? MiniJson.JsonNumber ?: throw MiniJson.ParseException("Zahl $k fehlt")
    private fun Map<String, Any?>.long(k: String): Long = num(k).toLong()
    private fun Map<String, Any?>.int(k: String): Int = num(k).toInt()
    private fun Map<String, Any?>.dbl(k: String): Double = num(k).toDouble()
    private fun Map<String, Any?>.dblOrNull(k: String): Double? = (this[k] as? MiniJson.JsonNumber)?.toDouble()
    private fun Map<String, Any?>.longOrNull(k: String): Long? = (this[k] as? MiniJson.JsonNumber)?.toLong()
    private fun Map<String, Any?>.bool(k: String): Boolean = this[k] as? Boolean ?: false

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(k: String): Map<String, Any?> =
        this[k] as? Map<String, Any?> ?: throw MiniJson.ParseException("Objekt $k fehlt")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objOrNull(k: String): Map<String, Any?>? = this[k] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objList(k: String): List<Map<String, Any?>> =
        (this[k] as? List<Any?> ?: emptyList()).map { it as Map<String, Any?> }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.strList(k: String): List<String> =
        (this[k] as? List<Any?> ?: emptyList()).map { it as String }

    @Suppress("UNCHECKED_CAST")
    private fun asObj(v: Any?): Map<String, Any?> = v as? Map<String, Any?> ?: throw MiniJson.ParseException("Objekt erwartet")

    // ------------------------------------------------------------------ Tarif

    fun tariffToMap(p: TariffProfile): Map<String, Any?> = linkedMapOf(
        "id" to p.id,
        "version" to p.version,
        "displayNumber" to p.displayNumber,
        "displayName" to p.displayName,
        "officialName" to p.officialName,
        "scope" to p.scope,
        "baseFare" to p.baseFare.toPlainString(),
        "baseIncludesFirstStep" to p.baseIncludesFirstStep,
        "stepAmount" to p.stepAmount.toPlainString(),
        "bands" to p.bands.map {
            linkedMapOf("fromM" to it.fromM, "toM" to it.toM, "pricePerKm" to it.pricePerKm.toPlainString())
        },
        "waitingPerHour" to p.waitingPerHour.toPlainString(),
        "waitingGraceMs" to p.waitingGraceMs,
        "calculationMode" to p.calculationMode.name,
        "sources" to p.sources.map { linkedMapOf("title" to it.title, "url" to it.url) },
        "verifiedOn" to p.verifiedOn,
        "legalBasis" to p.legalBasis,
        "notes" to p.notes,
        "isCustom" to p.isCustom,
    )

    fun tariffFromMap(m: Map<String, Any?>): TariffProfile = TariffProfile(
        id = m.str("id"),
        version = m.str("version"),
        displayNumber = m.int("displayNumber"),
        displayName = m.str("displayName"),
        officialName = m.str("officialName"),
        scope = m.str("scope"),
        baseFare = m.dec("baseFare"),
        baseIncludesFirstStep = m.bool("baseIncludesFirstStep"),
        stepAmount = m.dec("stepAmount"),
        bands = m.objList("bands").map {
            DistanceBand(it.long("fromM"), it.longOrNull("toM"), it.dec("pricePerKm"))
        },
        waitingPerHour = m.dec("waitingPerHour"),
        waitingGraceMs = m.long("waitingGraceMs"),
        calculationMode = CalculationMode.valueOf(m.str("calculationMode")),
        sources = m.objList("sources").map { TariffSource(it.str("title"), it.str("url")) },
        verifiedOn = m.str("verifiedOn"),
        legalBasis = m.str("legalBasis"),
        notes = m.strList("notes"),
        isCustom = m.bool("isCustom"),
    )

    // ------------------------------------------------------------------ Zuschläge

    fun surchargeToMap(s: SurchargeDef): Map<String, Any?> = linkedMapOf(
        "id" to s.id, "label" to s.label, "amount" to s.amount.toPlainString(),
        "condition" to s.condition, "legalRef" to s.legalRef, "custom" to s.custom,
    )

    fun surchargeFromMap(m: Map<String, Any?>) = SurchargeDef(
        id = m.str("id"), label = m.str("label"), amount = m.dec("amount"),
        condition = m.str("condition"), legalRef = m.str("legalRef"), custom = m.bool("custom"),
    )

    // ------------------------------------------------------------------ GPS

    fun fixToMap(f: GpsFix): Map<String, Any?> = linkedMapOf(
        "lat" to f.lat, "lon" to f.lon, "acc" to f.accuracyM, "t" to f.elapsedMs,
        "wall" to f.wallTimeMs, "speed" to f.speedMps, "mock" to f.isMock,
    )

    fun fixFromMap(m: Map<String, Any?>) = GpsFix(
        lat = m.dbl("lat"), lon = m.dbl("lon"), accuracyM = m.dblOrNull("acc"), elapsedMs = m.long("t"),
        wallTimeMs = m.long("wall"), speedMps = m.dblOrNull("speed"), isMock = m.bool("mock"),
    )

    fun gapToMap(g: GapRecord): Map<String, Any?> = linkedMapOf(
        "start" to g.startWallMs, "duration" to g.durationMs, "bridgedM" to g.bridgedM,
        "straightM" to g.straightLineM, "reason" to g.reason.name,
    )

    fun gapFromMap(m: Map<String, Any?>) = GapRecord(
        startWallMs = m.long("start"), durationMs = m.long("duration"), bridgedM = m.dbl("bridgedM"),
        straightLineM = m.dblOrNull("straightM"), reason = GapReason.valueOf(m.str("reason")),
    )

    private fun trackerToMap(t: TrackerState): Map<String, Any?> = linkedMapOf(
        "anchor" to t.anchor?.let { fixToMap(it) },
        "last" to t.lastAccepted?.let { fixToMap(it) },
        "cursor" to t.cursorMs,
        "lastSpeed" to t.lastSpeedMps,
        "jumps" to t.consecutiveJumps,
    )

    private fun trackerFromMap(m: Map<String, Any?>) = TrackerState(
        anchor = m.objOrNull("anchor")?.let { fixFromMap(it) },
        lastAccepted = m.objOrNull("last")?.let { fixFromMap(it) },
        cursorMs = m.long("cursor"),
        lastSpeedMps = m.dblOrNull("lastSpeed"),
        consecutiveJumps = m.int("jumps"),
    )

    // ------------------------------------------------------------------ Zähler

    private fun meterToMap(s: MeterState): Map<String, Any?> = linkedMapOf(
        "odometerM" to s.odometerM,
        "distanceChargedM" to s.distanceChargedM,
        "distanceCharge" to s.distanceCharge.toPlainString(),
        "timeCharge" to s.timeCharge.toPlainString(),
        "chargedWaitingMs" to s.chargedWaitingMs,
        "runningMs" to s.runningMs,
        "gapMs" to s.gapMs,
        "haltActive" to s.haltActive,
        "haltElapsedMs" to s.haltElapsedMs,
        "fastStreakMs" to s.fastStreakMs,
    )

    private fun meterFromMap(m: Map<String, Any?>) = MeterState(
        odometerM = m.dbl("odometerM"),
        distanceChargedM = m.dbl("distanceChargedM"),
        distanceCharge = m.dec("distanceCharge"),
        timeCharge = m.dec("timeCharge"),
        chargedWaitingMs = m.long("chargedWaitingMs"),
        runningMs = m.long("runningMs"),
        gapMs = m.long("gapMs"),
        haltActive = m.bool("haltActive"),
        haltElapsedMs = m.long("haltElapsedMs"),
        fastStreakMs = m.long("fastStreakMs"),
    )

    // ------------------------------------------------------------------ Fahrt

    private fun runToMap(r: TripRun): Map<String, Any?> = linkedMapOf(
        "tripId" to r.tripId,
        "profile" to tariffToMap(r.profile),
        "startWallMs" to r.startWallMs,
        "simulation" to r.simulation,
        "meter" to meterToMap(r.meter),
        "tracker" to trackerToMap(r.tracker),
        "surcharges" to r.surcharges.items.map { surchargeToMap(it) },
        "gaps" to r.gaps.map { gapToMap(it) },
        "restored" to r.restoredAfterProcessDeath,
        "fareFloor" to r.fareFloor.toPlainString(),
        "showTotal" to r.showTotal,
        "pendingPaymentId" to r.pendingPaymentId,
        "paymentAttempts" to r.paymentAttempts,
        "pendingTip" to r.pendingTip.toPlainString(),
    )

    private fun runFromMap(m: Map<String, Any?>) = TripRun(
        tripId = m.str("tripId"),
        profile = tariffFromMap(m.obj("profile")),
        startWallMs = m.long("startWallMs"),
        simulation = m.bool("simulation"),
        meter = meterFromMap(m.obj("meter")),
        tracker = trackerFromMap(m.obj("tracker")),
        surcharges = SurchargeSelection(m.objList("surcharges").map { surchargeFromMap(it) }),
        gaps = m.objList("gaps").map { gapFromMap(it) },
        restoredAfterProcessDeath = m.bool("restored"),
        fareFloor = m.strOrNull("fareFloor")?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        showTotal = m.bool("showTotal"),
        pendingPaymentId = m.strOrNull("pendingPaymentId"),
        paymentAttempts = (m["paymentAttempts"] as? MiniJson.JsonNumber)?.toInt() ?: 0,
        pendingTip = m.strOrNull("pendingTip")?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
    )

    fun recordToMap(r: TripRecord): Map<String, Any?> = linkedMapOf(
        "format" to FORMAT_VERSION,
        "id" to r.id,
        "profile" to tariffToMap(r.profile),
        "startWallMs" to r.startWallMs,
        "endWallMs" to r.endWallMs,
        "durationMs" to r.durationMs,
        "distanceM" to r.distanceM,
        "waitingMs" to r.waitingMs,
        "gapMs" to r.gapMs,
        "fare" to r.fare.toPlainString(),
        "surcharges" to r.surcharges.map { surchargeToMap(it) },
        "surchargeTotal" to r.surchargeTotal.toPlainString(),
        "total" to r.total.toPlainString(),
        "gaps" to r.gaps.map { gapToMap(it) },
        "simulation" to r.simulation,
        "restored" to r.restoredAfterProcessDeath,
        "payment" to r.payment.name,
        "paymentRef" to r.paymentRef,
        "tip" to r.tip.toPlainString(),
    )

    fun recordFromMap(m: Map<String, Any?>) = TripRecord(
        id = m.str("id"),
        profile = tariffFromMap(m.obj("profile")),
        startWallMs = m.long("startWallMs"),
        endWallMs = m.long("endWallMs"),
        durationMs = m.long("durationMs"),
        distanceM = m.dbl("distanceM"),
        waitingMs = m.long("waitingMs"),
        gapMs = m.long("gapMs"),
        fare = m.dec("fare"),
        surcharges = m.objList("surcharges").map { surchargeFromMap(it) },
        surchargeTotal = m.dec("surchargeTotal"),
        total = m.dec("total"),
        gaps = m.objList("gaps").map { gapFromMap(it) },
        simulation = m.bool("simulation"),
        restoredAfterProcessDeath = m.bool("restored"),
        payment = m.strOrNull("payment")?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() } ?: PaymentMethod.BAR,
        paymentRef = m.strOrNull("paymentRef"),
        tip = m.strOrNull("tip")?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
    )

    // ------------------------------------------------------------------ Gesamtzustand

    fun machineToJson(s: MeterMachineState): String = MiniJson.write(
        linkedMapOf(
            "format" to FORMAT_VERSION,
            "phase" to s.phase.name,
            "selectedTariff" to tariffToMap(s.selectedTariff),
            "pendingSurcharges" to s.pendingSurcharges.items.map { surchargeToMap(it) },
            "run" to s.run?.let { runToMap(it) },
            "completed" to s.completed?.let { recordToMap(it) },
            "savedWallMs" to s.savedWallMs,
        )
    )

    fun machineFromJson(json: String): MeterMachineState {
        val m = asObj(MiniJson.parse(json))
        return MeterMachineState(
            phase = Phase.valueOf(m.str("phase")),
            selectedTariff = tariffFromMap(m.obj("selectedTariff")),
            pendingSurcharges = SurchargeSelection(m.objList("pendingSurcharges").map { surchargeFromMap(it) }),
            run = m.objOrNull("run")?.let { runFromMap(it) },
            completed = m.objOrNull("completed")?.let { recordFromMap(it) },
            savedWallMs = m.long("savedWallMs"),
        )
    }

    fun historyToJson(records: List<TripRecord>): String =
        MiniJson.write(linkedMapOf("format" to FORMAT_VERSION, "trips" to records.map { recordToMap(it) }))

    fun historyFromJson(json: String): List<TripRecord> =
        asObj(MiniJson.parse(json)).objList("trips").map { recordFromMap(it) }

    fun tariffToJson(p: TariffProfile): String = MiniJson.write(tariffToMap(p))
    fun tariffFromJson(json: String): TariffProfile = tariffFromMap(asObj(MiniJson.parse(json)))

    fun surchargesToJson(list: List<SurchargeDef>): String = MiniJson.write(list.map { surchargeToMap(it) })

    @Suppress("UNCHECKED_CAST")
    fun surchargesFromJson(json: String): List<SurchargeDef> =
        (MiniJson.parse(json) as List<Any?>).map { surchargeFromMap(asObj(it)) }
}
