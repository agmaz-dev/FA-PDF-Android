package de.fa.taximeter.data

import android.content.Context
import android.os.SystemClock
import de.fa.taximeter.domain.Money
import de.fa.taximeter.domain.gps.DistanceTracker
import de.fa.taximeter.domain.gps.GpsFilterConfig
import de.fa.taximeter.domain.gps.GpsFix
import de.fa.taximeter.domain.gps.GpsQuality
import de.fa.taximeter.domain.tariff.TariffProfile
import de.fa.taximeter.domain.trip.CommandResult
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.MeterMachineState
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.domain.trip.SurchargeDef
import de.fa.taximeter.domain.trip.TripRecord
import de.fa.taximeter.location.GpsHub
import de.fa.taximeter.location.MeterService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** GPS-Zustand für die Anzeige. */
data class GpsStatus(
    val lastRaw: GpsFix? = null,
    val lastAcceptedMs: Long? = null,
    val providerEnabled: Boolean = true,
    val lastReject: String? = null,
)

/**
 * Zentrale Zustandsverwaltung (eine Instanz pro Prozess, nur auf dem Main-Thread benutzt).
 * Überlebt Bildschirmdrehung und Activity-Neuaufbau; sichert regelmäßig auf den Datenträger.
 */
class MeterRepository(private val app: Context) {

    val settings = AppSettings(app)
    private val store = FileStore(app)

    private val _state: MutableStateFlow<MeterMachineState>
    val state: StateFlow<MeterMachineState>

    private val _gps = MutableStateFlow(GpsStatus())
    val gps: StateFlow<GpsStatus> = _gps

    private val _history = MutableStateFlow(store.loadHistory())
    val history: StateFlow<List<TripRecord>> = _history

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    /** Hinweis nach Wiederherstellung (bis der Nutzer ihn schließt). */
    private val _restoreNotice = MutableStateFlow<String?>(null)
    val restoreNotice: StateFlow<String?> = _restoreNotice

    val gpsHub = GpsHub(app, ::onFix) { enabled -> _gps.value = _gps.value.copy(providerEnabled = enabled) }

    private var lastSaveElapsed = 0L

    init {
        val loaded = store.loadState()
        val now = System.currentTimeMillis()
        val initial = when {
            loaded == null -> MeterMachine.initial(settings.current.profileFor(settings.current.lastTariffNumber))
            loaded.phase == Phase.BESETZT -> {
                // Der vorige Prozess wurde während einer laufenden Fahrt beendet.
                val restored = MeterMachine.restoreAfterProcessDeath(loaded, now)
                val gap = restored.run?.gaps?.lastOrNull()
                val min = (gap?.durationMs ?: 0L) / 60_000L
                _restoreNotice.value = "Fahrt nach App-Neustart wiederhergestellt (Zustand STOPP). " +
                    "Nicht gemessene Zeit: ca. $min min – ohne Strecke und ohne Wartezeit als Messlücke vermerkt. " +
                    "BESETZT setzt fort, KASSE schließt ab."
                restored
            }
            loaded.phase == Phase.STOPP -> {
                _restoreNotice.value = "Gesicherte Fahrt im Zustand STOPP wiederhergestellt. BESETZT setzt fort, KASSE schließt ab."
                loaded
            }
            loaded.phase == Phase.FREI ->
                // Im FREI-Zustand gilt immer das aktuell eingestellte Profil.
                loaded.copy(selectedTariff = settings.current.profileFor(loaded.selectedTariff.displayNumber))
            else -> loaded
        }
        _state = MutableStateFlow(initial)
        state = _state
        gpsHub.setSimulation(isSimulationActive(initial))
        save(force = true)
    }

    fun filterConfig(): GpsFilterConfig = GpsFilterConfig(allowMock = isSimulationActive(_state.value))

    /** Simulation gilt für die laufende Fahrt fest; im FREI-Zustand folgt sie der Einstellung. */
    private fun isSimulationActive(s: MeterMachineState): Boolean = when (s.phase) {
        Phase.BESETZT, Phase.STOPP -> s.run?.simulation ?: false
        Phase.KASSE -> s.completed?.simulation ?: false
        Phase.FREI -> settings.current.testMode
    }

    val simulationActive: Boolean get() = isSimulationActive(_state.value)

    fun quality(nowMs: Long = SystemClock.elapsedRealtime()): GpsQuality {
        val g = _gps.value
        val acceptedMs = if (_state.value.phase == Phase.BESETZT) _state.value.run?.tracker?.cursorMs else g.lastRaw?.elapsedMs
        return DistanceTracker.quality(g.lastRaw, acceptedMs, nowMs, filterConfig())
    }

    // ---------------------------------------------------------------- GPS

    private fun onFix(fix: GpsFix) {
        val now = SystemClock.elapsedRealtime()
        val s = _state.value
        if (s.phase == Phase.BESETZT) {
            val r = MeterMachine.onFix(s, fix, now, filterConfig())
            _gps.value = _gps.value.copy(lastRaw = fix, lastReject = if (r.changed && r.message == null) null else r.message)
            if (r.changed) {
                _state.value = r.state
                if (now - lastSaveElapsed >= SAVE_INTERVAL_MS) save()
            }
        } else {
            _gps.value = _gps.value.copy(lastRaw = fix, lastReject = null)
        }
    }

    // ---------------------------------------------------------------- Bedienung

    fun pressBesetzt() {
        val before = _state.value.phase
        val now = SystemClock.elapsedRealtime()
        val r = MeterMachine.pressBesetzt(
            _state.value, _gps.value.lastRaw, now, System.currentTimeMillis(), filterConfig(),
            simulation = isSimulationActive(_state.value),
        ) { UUID.randomUUID().toString() }
        apply(r)
        if (r.changed && r.state.phase == Phase.BESETZT) {
            if (before == Phase.STOPP) _restoreNotice.value = null
            startService()
        }
    }

    fun pressStopp() {
        val now = SystemClock.elapsedRealtime()
        apply(MeterMachine.pressStopp(_state.value, now, System.currentTimeMillis(), filterConfig()))
    }

    fun pressKasse() {
        val r = MeterMachine.pressKasse(_state.value, System.currentTimeMillis())
        if (r.changed) {
            val rec = r.state.completed!!
            _history.value = listOf(rec) + _history.value.filterNot { it.id == rec.id }
            store.saveHistory(_history.value)
            _restoreNotice.value = null
        }
        apply(r)
        if (r.changed) MeterService.stop(app)
    }

    fun newTrip() {
        val r = MeterMachine.newTrip(_state.value)
        if (r.changed) {
            val fresh = r.state.copy(selectedTariff = settings.current.profileFor(r.state.selectedTariff.displayNumber))
            apply(CommandResult(fresh))
            gpsHub.setSimulation(isSimulationActive(fresh))
        }
    }

    fun selectTariff(number: Int) {
        val profile = settings.current.profileFor(number)
        val r = MeterMachine.selectTariff(_state.value, profile)
        apply(r)
        if (r.changed) settings.setLastTariff(number)
    }

    fun toggleSurcharge(def: SurchargeDef) = apply(MeterMachine.toggleSurcharge(_state.value, def))

    fun dismissRestoreNotice() {
        _restoreNotice.value = null
    }

    /** Einstellungen haben sich geändert: im FREI-Zustand Profil und Modus übernehmen. */
    fun onSettingsChanged() {
        val s = _state.value
        if (s.phase == Phase.FREI) {
            val p: TariffProfile = settings.current.profileFor(s.selectedTariff.displayNumber)
            if (p != s.selectedTariff) apply(CommandResult(s.copy(selectedTariff = p)))
            gpsHub.setSimulation(settings.current.testMode)
            _gps.value = GpsStatus(providerEnabled = _gps.value.providerEnabled)
        }
    }

    fun deleteTrip(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        store.saveHistory(_history.value)
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    /** Wird vom Service periodisch aufgerufen, damit auch ohne neue Messungen gesichert wird. */
    fun periodicSave() {
        if (SystemClock.elapsedRealtime() - lastSaveElapsed >= SAVE_INTERVAL_MS) save()
    }

    fun notificationText(): String {
        val s = _state.value
        val fare = Money.formatEuro(MeterMachine.liveFare(s))
        val km = String.format(java.util.Locale.GERMANY, "%.2f km", (s.run?.meter?.odometerM ?: 0.0) / 1000.0)
        return when (s.phase) {
            Phase.BESETZT -> "BESETZT · $fare · $km"
            Phase.STOPP -> "STOPP · $fare · $km"
            else -> "FA Taximeter"
        }
    }

    private fun apply(r: CommandResult) {
        if (r.changed) {
            _state.value = r.state
            save(force = true)
        }
        r.message?.let { _messages.tryEmit(it) }
    }

    private fun save(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSaveElapsed < SAVE_INTERVAL_MS) return
        lastSaveElapsed = now
        val withTime = _state.value.copy(savedWallMs = System.currentTimeMillis())
        _state.value = withTime
        store.saveState(withTime)
    }

    private fun startService() {
        try {
            MeterService.start(app)
        } catch (e: Exception) {
            _messages.tryEmit("Hintergrundmessung konnte nicht gestartet werden – App bitte geöffnet lassen.")
        }
    }

    /** Nach Prozessneustart läuft keine Fahrt im Hintergrund; Service nur bei BESETZT nötig. */
    fun ensureServiceState() {
        if (_state.value.phase == Phase.BESETZT) startService()
    }

    companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
