package de.fa.taximeter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import de.fa.taximeter.data.MeterRepository
import de.fa.taximeter.data.OrientationMode
import de.fa.taximeter.domain.trip.MeterMachine
import de.fa.taximeter.domain.trip.Phase
import de.fa.taximeter.ui.screens.HistoryScreen
import de.fa.taximeter.ui.screens.KasseScreen
import de.fa.taximeter.ui.screens.MeterActions
import de.fa.taximeter.ui.screens.MeterScreen
import de.fa.taximeter.ui.screens.MeterUi
import de.fa.taximeter.ui.screens.SettingsActions
import de.fa.taximeter.ui.screens.SettingsScreen
import de.fa.taximeter.ui.screens.TariffDialog
import de.fa.taximeter.ui.theme.MeterColors
import de.fa.taximeter.ui.theme.MeterTheme
import kotlinx.coroutines.delay

private enum class Screen { METER, HISTORY, DETAIL, SETTINGS }

@Composable
fun TaximeterRoot(
    repo: MeterRepository,
    onOrientation: (OrientationMode) -> Unit,
    onPermissionResult: () -> Unit,
    onLeaveToBackground: () -> Unit,
) {
    MeterTheme {
        val context = LocalContext.current
        val state by repo.state.collectAsState()
        val settings by repo.settings.settings.collectAsState()
        val gps by repo.gps.collectAsState()
        val history by repo.history.collectAsState()
        val restoreNotice by repo.restoreNotice.collectAsState()

        var screen by rememberSaveable { mutableStateOf(Screen.METER) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var showTariff by rememberSaveable { mutableStateOf(false) }
        var lastBack by remember { mutableLongStateOf(0L) }
        var permissionTick by remember { mutableIntStateOf(0) }

        // Takt für Dauer- und GPS-Statusanzeige
        var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                repo.tick()
                delay(500)
            }
        }
        LaunchedEffect(Unit) {
            repo.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(settings.orientation) { onOrientation(settings.orientation) }
        LaunchedEffect(restoreNotice) {
            restoreNotice?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                repo.dismissRestoreNotice()
            }
        }

        // Bildschirm anlassen, solange eine Fahrt aktiv ist (inkl. Abschlussanzeige)
        val keepOn = settings.keepScreenOn && state.phase != Phase.FREI
        val view = LocalView.current
        DisposableEffect(keepOn) {
            view.keepScreenOn = keepOn
            onDispose { view.keepScreenOn = false }
        }

        // Berechtigungen (bei jedem Takt neu gelesen, damit Änderungen in den Systemeinstellungen greifen)
        val fine = remember(nowMs / 2000, permissionTick) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        val coarse = remember(nowMs / 2000, permissionTick) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        var askedOnce by rememberSaveable { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionTick++
            onPermissionResult()
        }
        val requestPermissions: () -> Unit = {
            if (askedOnce && !fine) {
                // Wurde bereits abgelehnt: Systemeinstellungen der App öffnen
                val i = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(i) }
            } else {
                askedOnce = true
                val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                launcher.launch(perms.toTypedArray())
            }
        }
        LaunchedEffect(Unit) {
            if (!fine && !coarse) requestPermissions()
        }
        LaunchedEffect(fine, coarse) { repo.gpsHub.refresh() }

        // ------------------------------------------------------------ Zurück-Taste
        BackHandler(enabled = screen != Screen.METER) {
            screen = if (screen == Screen.DETAIL) Screen.HISTORY else Screen.METER
        }
        BackHandler(enabled = screen == Screen.METER && state.phase != Phase.FREI) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBack < 2500) {
                onLeaveToBackground()
            } else {
                lastBack = now
                val hint = "Fahrt läuft weiter. Nochmals ZURÜCK: App in den Hintergrund."
                Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MeterColors.Background)
                .safeDrawingPadding()
        ) {
            when (screen) {
                Screen.METER -> {
                    MeterScreen(
                        MeterUi(
                            phase = state.phase,
                            tariff = state.run?.profile ?: state.selectedTariff,
                            amount = MeterMachine.displayAmount(state, nowMs),
                            showsTotal = MeterMachine.showsTotal(state),
                            surchargeTotal = state.activeSurcharges.total,
                            gpsQuality = repo.quality(nowMs),
                            estimating = MeterMachine.isEstimating(state, nowMs),
                            simulation = repo.simulationActive,
                            providerEnabled = gps.providerEnabled,
                            fineLocation = fine,
                            anyLocation = fine || coarse || repo.simulationActive,
                        ),
                        MeterActions(
                            onTarif = {
                                if (state.phase == Phase.FREI) showTariff = true
                                else repo.message("Tarifwechsel während der Fahrt gesperrt")
                            },
                            onZuschlag = { repo.addSurchargeStep() },
                            onZuschlagLong = { repo.resetSurcharges() },
                            onFrei = { repo.pressFrei() },
                            onBesetzt = {
                                if (state.phase == Phase.FREI && !repo.simulationActive && !fine) {
                                    if (coarse) repo.message("Für die Streckenmessung wird der genaue Standort benötigt.")
                                    requestPermissions()
                                } else {
                                    repo.pressBesetzt()
                                }
                            },
                            onStopp = {
                                if (state.phase == Phase.FREI) repo.message("Keine Fahrt aktiv.")
                                else repo.pressStopp()
                            },
                            onKasse = {
                                when (state.phase) {
                                    Phase.STOPP -> repo.prepareCardPayment()?.let { intent ->
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            repo.cardPaymentNotStarted("SumUp-App nicht gefunden – bitte installieren und anmelden.")
                                        }
                                    }
                                    Phase.BESETZT -> repo.message("Erst STOPP drücken.")
                                    else -> Unit
                                }
                            },
                            onHistory = { screen = Screen.HISTORY },
                            onSettings = { screen = Screen.SETTINGS },
                            onRequestPermission = requestPermissions,
                        ),
                    )
                }

                Screen.HISTORY -> HistoryScreen(
                    trips = history,
                    onOpen = {
                        detailId = it.id
                        screen = Screen.DETAIL
                    },
                    onDelete = { repo.deleteTrip(it.id) },
                    onBack = { screen = Screen.METER },
                )

                Screen.DETAIL -> {
                    val rec = history.firstOrNull { it.id == detailId }
                    if (rec == null) {
                        LaunchedEffect(detailId) { screen = Screen.HISTORY }
                    } else {
                        KasseScreen(record = rec, primaryLabel = "ZURÜCK", onPrimary = { screen = Screen.HISTORY })
                    }
                }

                Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    phase = state.phase,
                    actions = SettingsActions(
                        setOrientation = { repo.settings.setOrientation(it) },
                        setKeepScreenOn = { repo.settings.setKeepScreenOn(it) },
                        setSumupKey = {
                            repo.settings.setSumupKey(it)
                            repo.message(if (it.isBlank()) "SumUp-Schlüssel entfernt" else "SumUp-Schlüssel gespeichert")
                        },
                        appId = context.packageName,
                        setTestMode = {
                            repo.settings.setTestMode(it)
                            repo.onSettingsChanged()
                        },
                        saveCustomTariff = { n, p ->
                            repo.settings.saveCustomTariff(n, p)
                            repo.onSettingsChanged()
                        },
                        removeCustomTariff = { n ->
                            repo.settings.removeCustomTariff(n)
                            repo.onSettingsChanged()
                        },
                        setUseCustom = { n, use ->
                            repo.settings.setUseCustom(n, use)
                            repo.onSettingsChanged()
                        },
                        removeCustomSurcharge = { repo.settings.removeCustomSurcharge(it) },
                        message = { repo.message(it) },
                        onBack = { screen = Screen.METER },
                    ),
                )
            }
        }

        // ------------------------------------------------------------ Dialoge
        if (showTariff && state.phase == Phase.FREI) {
            TariffDialog(
                options = listOf(settings.profileFor(3), settings.profileFor(4)),
                selectedNumber = state.selectedTariff.displayNumber,
                onSelect = {
                    repo.selectTariff(it)
                    showTariff = false
                },
                onDismiss = { showTariff = false },
            )
        }
    }
}
