package com.atmotube.pro2.example

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.atmotube.pro2.example.ui.AtmotubeAirTheme
import com.atmotube.pro2.example.ui.DeviceSettingsScreen
import com.atmotube.pro2.example.ui.DashboardScreen
import com.atmotube.pro2.example.ui.FoundDevice
import com.atmotube.pro2.example.ui.HistoryScreen
import com.atmotube.pro2.example.ui.ScanScreen
import com.atmotube.pro2.example.ui.SettingsScreen
import com.atmotube.pro2.example.ui.ToolsScreen
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: AtmotubeBleManager
    private lateinit var historyManager: HistoryManager
    private lateinit var prefs: SharedPreferences
    private lateinit var alerts: AirAlerts

    private val scannedDevices = mutableStateListOf<FoundDevice>()
    private var isScanning by mutableStateOf(false)
    private var currentScreen by mutableStateOf(Screen.SCAN)
    private var activeDevice by mutableStateOf<BluetoothDevice?>(null)
    private var lastAddress by mutableStateOf<String?>(null)
    private var isDownloadingHistory by mutableStateOf(false)
    private var historyPending by mutableStateOf(false)
    private var history by mutableStateOf<List<HistoryMeasurement>>(emptyList())
    private var historyFile by mutableStateOf<File?>(null)
    private var profile by mutableStateOf(defaultProfile())
    private var pmHour by mutableStateOf(PmHourLog())
    private var widgetPollutants by mutableStateOf(defaultWidgetPollutants())
    private var pmStatus by mutableStateOf<PmStatus?>(null)
    private var gnssStatus by mutableStateOf<GnssStatus?>(null)
    private var deviceBusy by mutableStateOf(false)
    private var deviceMessage by mutableStateOf<String?>(null)
    private var previousLevels by mutableStateOf<Map<Pollutant, AirLevel>>(emptyMap())
    private var previousAlertEnabled by mutableStateOf<Map<Pollutant, Boolean>>(emptyMap())
    private var pendingProfile: BandProfile? = null
    private var lastWidgetEncoded: String? = null
    private var permissionRequestInFlight = false
    private var autoPrompted = false

    private val scanner = BluetoothLeScannerCompat.getScanner()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = deviceName(device) ?: return
            if (!name.contains("ATMO", ignoreCase = true)) return
            val found = FoundDevice(device, name, result.rssi)
            val index = scannedDevices.indexOfFirst { it.device.address == device.address }
            if (index >= 0) scannedDevices[index] = found else scannedDevices.add(found)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { perms ->
        permissionRequestInFlight = false
        if (perms.values.all { it }) {
            startScan()
        } else {
            Toast.makeText(this, "Bluetooth and location are needed to find the device", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingProfile
        pendingProfile = null
        if (pending == null) return@registerForActivityResult
        if (granted) {
            persistProfile(pending)
        } else {
            persistProfile(pending.copy(alerts = pending.alerts.mapValues { (_, alert) -> alert.copy(enabled = false) }))
            Toast.makeText(this, "Notifications stay off until you allow them", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = getSharedPreferences(AirStore.PREFS, MODE_PRIVATE)
        lastAddress = prefs.getString(AirStore.KEY_ADDRESS, null)
        profile = decodeProfile(prefs.getString(AirStore.KEY_BANDS, null))
        pmHour = PmHourLog.decode(prefs.getString(AirStore.KEY_PM_HOUR, null))
        widgetPollutants = decodeWidgetSelection(prefs.getString(AirStore.KEY_WIDGET_POLLUTANTS, null))
        alerts = AirAlerts(this)
        bleManager = AtmotubeBleManager(this, lifecycleScope)
        historyManager = HistoryManager(this, bleManager)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                }
            }
            AtmotubeAirTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AirApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (currentScreen == Screen.SCAN) ensureScan(fromUser = false)
    }

    override fun onStop() {
        stopScan()
        super.onStop()
    }

    override fun onDestroy() {
        stopScan()
        if (::bleManager.isInitialized) {
            bleManager.disconnect().enqueue()
        }
        super.onDestroy()
    }

    @Composable
    private fun AirApp() {
        val connectionState by bleManager.connectionState.collectAsState()
        val reading by bleManager.latestReading.collectAsState()
        val pmReading by bleManager.pmReading.collectAsState()
        val gpsReading by bleManager.gpsReading.collectAsState()
        val logs by bleManager.commandLogs.collectAsState()
        val firmware by bleManager.firmwareVersion.collectAsState()

        LaunchedEffect(connectionState) {
            if (connectionState is ConnectionState.Ready) {
                if (currentScreen == Screen.SCAN) currentScreen = Screen.DASHBOARD
                stopScan()
                activeDevice?.let { device ->
                    lastAddress = device.address
                    prefs.edit()
                        .putString(AirStore.KEY_ADDRESS, device.address)
                        .putString(AirStore.KEY_NAME, deviceName(device))
                        .apply()
                }
            }
            if (connectionState is ConnectionState.Disconnected) {
                pmStatus = null
                gnssStatus = null
            }
        }

        LaunchedEffect(currentScreen, connectionState) {
            if (currentScreen == Screen.DEVICE && connectionState is ConnectionState.Ready) {
                refreshDeviceSettings()
            }
        }

        LaunchedEffect(currentScreen) {
            if (currentScreen == Screen.SCAN && connectionState is ConnectionState.Disconnected) {
                ensureScan(fromUser = false)
            }
        }

        LaunchedEffect(bleManager) {
            bleManager.historyReady.collect { historyPending = true }
        }

        LaunchedEffect(pmReading) {
            val sample = pmReading ?: return@LaunchedEffect
            val next = pmHour.add(System.currentTimeMillis(), sample.pm1, sample.pm25, sample.pm10)
            if (next != pmHour) {
                pmHour = next
                prefs.edit().putString(AirStore.KEY_PM_HOUR, next.encode()).apply()
            }
        }

        LaunchedEffect(reading, pmReading, profile, widgetPollutants) {
            if (reading != null || pmReading != null) {
                val hourly = pmHour.hourly(System.currentTimeMillis())
                val lines = widgetLines(widgetPollutants, reading, pmReading, profile.cuts, hourly)
                val encoded = encodeWidgetLines(lines)
                if (encoded != lastWidgetEncoded) {
                    lastWidgetEncoded = encoded
                    AirWidgetProvider.publish(this@MainActivity, lines, System.currentTimeMillis())
                }
            }
            val snapshots = pollutantSnapshots(reading, pmReading, profile.cuts)
            val current = snapshots.mapValues { it.value.level }
            val previousForAlerts = Pollutant.entries.associateWith { pollutant ->
                val nowEnabled = profile.alerts[pollutant]?.enabled == true
                val wasEnabled = previousAlertEnabled[pollutant] == true
                if (nowEnabled && !wasEnabled) AirLevel.UNKNOWN else previousLevels[pollutant] ?: AirLevel.UNKNOWN
            }
            crossingAlerts(previousForAlerts, current, profile.alerts).forEach { pollutant ->
                val text = snapshots[pollutant]?.valueText ?: return@forEach
                alerts.notify(pollutant, current.getValue(pollutant), text)
            }
            recoveredAlerts(previousLevels, current, profile.alerts).forEach { alerts.cancel(it) }
            previousLevels = current
            previousAlertEnabled = profile.alerts.mapValues { it.value.enabled }
        }

        BackHandler(enabled = currentScreen == Screen.HISTORY || currentScreen == Screen.TOOLS || currentScreen == Screen.SETTINGS || currentScreen == Screen.DEVICE) {
            currentScreen = Screen.DASHBOARD
        }

        when (currentScreen) {
            Screen.SCAN -> ScanScreen(
                devices = scannedDevices,
                isScanning = isScanning,
                isConnecting = connectionState != ConnectionState.Disconnected,
                lastAddress = lastAddress,
                onScanClick = { ensureScan(fromUser = true) },
                onStopClick = { stopScan() },
                onDeviceClick = { device -> connect(device) },
            )
            Screen.DASHBOARD -> DashboardScreen(
                deviceName = activeDevice?.let { deviceName(it) } ?: prefs.getString(AirStore.KEY_NAME, null) ?: "Atmotube",
                connectionState = connectionState,
                firmwareVersion = firmware,
                reading = reading,
                pmReading = pmReading,
                gpsReading = gpsReading,
                hourlyPm = pmHour.hourly(System.currentTimeMillis()),
                historyPending = historyPending,
                hasHistory = history.isNotEmpty(),
                isDownloadingHistory = isDownloadingHistory,
                profile = profile,
                onDisconnect = {
                    bleManager.disconnect().enqueue()
                    currentScreen = Screen.SCAN
                },
                onReconnect = { activeDevice?.let { connect(it) } },
                onSyncHistory = { downloadHistory(openWhenDone = true) },
                onOpenHistory = { currentScreen = Screen.HISTORY },
                onOpenSettings = { currentScreen = Screen.SETTINGS },
                onOpenDeviceSettings = { currentScreen = Screen.DEVICE },
                onOpenTools = { currentScreen = Screen.TOOLS },
            )
            Screen.HISTORY -> HistoryScreen(
                measurements = history,
                canShare = historyFile != null,
                isDownloading = isDownloadingHistory,
                onBack = { currentScreen = Screen.DASHBOARD },
                onShare = { historyFile?.let { shareHistory(it) } },
                onSync = { downloadHistory(openWhenDone = false) },
            )
            Screen.SETTINGS -> SettingsScreen(
                profile = profile,
                widgetPollutants = widgetPollutants,
                onWidgetPollutants = { updateWidgetPollutants(it) },
                onBack = { currentScreen = Screen.DASHBOARD },
                onSave = { saveProfile(it) },
            )
            Screen.DEVICE -> DeviceSettingsScreen(
                connected = connectionState is ConnectionState.Ready,
                busy = deviceBusy,
                pm = pmStatus,
                gps = gnssStatus,
                message = deviceMessage,
                onBack = { currentScreen = Screen.DASHBOARD },
                onRefresh = { refreshDeviceSettings() },
                onPmMode = { mode -> applyPm(mode, pmStatus?.charging == true) },
                onPmCharging = { charging -> pmStatus?.let { applyPm(it.mode, charging) } },
                onGpsMode = { mode -> applyGpsMode(mode) },
                onGpsTimer = { minutes -> applyGpsTimer(minutes) },
            )
            Screen.TOOLS -> ToolsScreen(
                firmwareVersion = firmware,
                commandLogs = logs,
                commandsEnabled = connectionState is ConnectionState.Ready,
                onSendCommand = { bleManager.sendShellCommand(it) },
                onBack = { currentScreen = Screen.DASHBOARD },
            )
        }
    }

    private fun saveProfile(next: BandProfile) {
        val wantsAlerts = next.alerts.values.any { it.enabled }
        if (
            wantsAlerts &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingProfile = next
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (wantsAlerts && !alerts.canPost()) {
            Toast.makeText(this, "Allow notifications for Atmotube Air to get alerts", Toast.LENGTH_LONG).show()
        }
        persistProfile(next)
    }

    private fun persistProfile(next: BandProfile) {
        profile = next
        prefs.edit().putString(AirStore.KEY_BANDS, encodeProfile(next)).apply()
    }

    private fun updateWidgetPollutants(selected: Set<Pollutant>) {
        widgetPollutants = selected
        prefs.edit().putString(AirStore.KEY_WIDGET_POLLUTANTS, encodeWidgetSelection(selected)).apply()
    }

    private fun refreshDeviceSettings() {
        if (bleManager.connectionState.value !is ConnectionState.Ready || deviceBusy) return
        deviceBusy = true
        deviceMessage = null
        lifecycleScope.launch {
            val pm = bleManager.execShell("pm", arrayOf("status"))
            val gps = bleManager.execShell("gnss", arrayOf("status"))
            pmStatus = if (pm.ok) parsePmStatus(pm.output) else pmStatus
            gnssStatus = if (gps.ok) parseGnssStatus(gps.output) else gnssStatus
            deviceMessage = when {
                !pm.ok -> pm.output
                pmStatus == null -> "Could not read PM settings"
                !gps.ok -> gps.output
                gnssStatus == null -> "Could not read GPS settings"
                else -> null
            }
            deviceBusy = false
        }
    }

    private fun applyPm(mode: PmMode, charging: Boolean) {
        if (deviceBusy) return
        deviceBusy = true
        deviceMessage = null
        lifecycleScope.launch {
            val result = bleManager.execShell("pm", arrayOf("mode") + pmModeArgs(mode, charging))
            deviceBusy = false
            if (!result.ok) {
                deviceMessage = result.output
                return@launch
            }
            refreshDeviceSettings()
        }
    }

    private fun applyGpsMode(mode: GnssMode) {
        if (deviceBusy) return
        deviceBusy = true
        deviceMessage = null
        lifecycleScope.launch {
            val result = bleManager.execShell("gnss", arrayOf("mode") + gnssModeArg(mode))
            if (result.ok && mode == GnssMode.TIMER) {
                val minutes = gnssStatus?.let { gnssTimerEditor(it) } ?: 10
                val timer = bleManager.execShell("gnss", arrayOf("timer") + gnssTimerArg(minutes))
                deviceBusy = false
                if (!timer.ok) {
                    deviceMessage = timer.output
                    return@launch
                }
            } else {
                deviceBusy = false
                if (!result.ok) {
                    deviceMessage = result.output
                    return@launch
                }
            }
            refreshDeviceSettings()
        }
    }

    private fun applyGpsTimer(minutes: Int) {
        if (deviceBusy) return
        deviceBusy = true
        deviceMessage = null
        lifecycleScope.launch {
            val result = bleManager.execShell("gnss", arrayOf("timer") + gnssTimerArg(minutes))
            deviceBusy = false
            if (!result.ok) {
                deviceMessage = result.output
                return@launch
            }
            refreshDeviceSettings()
        }
    }

    private fun connect(device: BluetoothDevice) {
        activeDevice = device
        stopScan()
        bleManager.connectToDevice(device)
    }

    private fun ensureScan(fromUser: Boolean) {
        if (hasAllPermissions()) {
            startScan()
            return
        }
        if (permissionRequestInFlight) return
        if (!fromUser && autoPrompted) return
        autoPrompted = true
        permissionRequestInFlight = true
        permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions().all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startScan() {
        if (isScanning || !hasAllPermissions()) return
        scannedDevices.clear()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        isScanning = true
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        if (!hasAllPermissions()) return
        scanner.stopScan(scanCallback)
    }

    private fun deviceName(device: BluetoothDevice): String? {
        if (!hasAllPermissions()) return null
        return device.name
    }

    private fun downloadHistory(openWhenDone: Boolean) {
        if (isDownloadingHistory) return
        isDownloadingHistory = true
        lifecycleScope.launch {
            val export = historyManager.downloadHistory()
            isDownloadingHistory = false
            if (export != null) {
                history = export.measurements
                historyFile = export.file
                historyPending = false
                if (openWhenDone) currentScreen = Screen.HISTORY
                Toast.makeText(
                    this@MainActivity,
                    "Synced ${export.measurements.size} readings",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(this@MainActivity, "No history on the device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareHistory(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share history"))
    }

    private enum class Screen {
        SCAN, DASHBOARD, HISTORY, SETTINGS, DEVICE, TOOLS
    }
}
