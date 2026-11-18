package uk.doughmination.devicereporter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import gay.doughmination.design.Backdrop
import gay.doughmination.design.DoughminationTheme
import gay.doughmination.design.Eyebrow
import gay.doughmination.design.GlassSection
import gay.doughmination.design.Line
import gay.doughmination.design.Muted
import gay.doughmination.design.Ok
import gay.doughmination.design.Warn
import gay.doughmination.design.enableDoughminationEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    // Screen state. Text fields and switches are saved on Save, Start and Send test.
    private var baseUrl by mutableStateOf("")
    private var apiKey by mutableStateOf("")
    private var deviceName by mutableStateOf("")
    private var watchConnected by mutableStateOf(false)
    private var airpodsEnabled by mutableStateOf(false)
    private var airpodsDevice by mutableStateOf("No device selected")
    private var running by mutableStateOf(false)
    private var lastReport by mutableStateOf("Nothing sent yet.")
    private var batteryOk by mutableStateOf(true)
    private var locationFine by mutableStateOf(true)
    private var locationOk by mutableStateOf(true)
    private var alarmsOk by mutableStateOf(true)
    private var askBackgroundLocation by mutableStateOf<(() -> Unit)?>(null)
    private var bluetoothChoices by mutableStateOf<List<Pair<String, BluetoothDevice>>?>(null)

    // Background location has to be asked for on its own, after foreground location is
    // granted, so the foreground request chains into it.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) requestBackgroundLocation()
            refreshBackgroundRows()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshBackgroundRows() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_LAST_REPORT) runOnUiThread { showLastReport() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableDoughminationEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        loadFromPrefs()
        setContent {
            DoughminationTheme {
                Box(Modifier.fillMaxSize()) {
                    Backdrop()
                    Screen()
                }
            }
        }
        requestRuntimePermissions()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerListener(prefsListener)
        refreshServiceState()
        refreshBackgroundRows()
        showLastReport()
    }

    override fun onPause() {
        prefs.unregisterListener(prefsListener)
        super.onPause()
    }

    private fun loadFromPrefs() {
        baseUrl = prefs.baseUrl
        apiKey = prefs.apiKey
        deviceName = prefs.deviceName
        watchConnected = prefs.watchConnected
        airpodsEnabled = prefs.airpodsEnabled
        updateAirpodsLabel()
    }

    private fun save() {
        prefs.baseUrl = baseUrl.trim().ifBlank { Prefs.DEFAULT_BASE_URL }
        prefs.apiKey = apiKey.trim()
        prefs.deviceName = deviceName.trim().ifBlank { Prefs.DEFAULT_DEVICE }
        prefs.watchConnected = watchConnected
        prefs.airpodsEnabled = airpodsEnabled
    }

    // --- screen ------------------------------------------------------------

    @Composable
    private fun Screen() {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("DOUGHMINATION.UK", style = Eyebrow)
                Text("Device Reporter", style = MaterialTheme.typography.headlineMedium)
                Text("Tells your API how this phone is doing.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                StatusPill(if (running) "Reporting" else "Stopped", if (running) Ok else Muted)
            }

            GlassSection("Endpoint") {
                Code("POST /v2/devices")
                Hint("Only device is required; the API updates just the fields it receives. Your key goes in the X-Battery-Key header.")
            }

            GlassSection("Configuration") {
                Field("Base URL", baseUrl, "https://doughmination.uk/v2", KeyboardType.Uri) { baseUrl = it }
                Field("X-Battery-Key", apiKey, "your device key", KeyboardType.Password, secret = true) { apiKey = it }
                Field("Device name", deviceName, "phone") { deviceName = it }
            }

            GlassSection("Peripherals") {
                SwitchRow("Watch connected", watchConnected) { watchConnected = it }
                Hint("Manual: an Apple Watch can't pair with Android, so flip this yourself.")
                HorizontalDivider(color = Line)
                SwitchRow("Report AirPods", airpodsEnabled) { airpodsEnabled = it }
                Hint(airpodsDevice)
                OutlinedButton(onClick = ::pickAirpodsDevice) { Text("Choose Bluetooth device") }
            }

            GlassSection("Background") {
                PermissionRow(
                    "Battery: unrestricted", batteryOk,
                    ok = "Allowed",
                    missing = "One UI may stop the service while the screen's off",
                    onAllow = ::requestBatteryExemption,
                )
                PermissionRow(
                    "Location: all the time", locationOk,
                    ok = "Allowed",
                    missing = if (!locationFine) "Needed for the Wi-Fi name and location"
                    else "Only while open: background reports won't have Wi-Fi name or location",
                    onAllow = ::requestLocation,
                )
                PermissionRow(
                    "On-time heartbeat", alarmsOk,
                    ok = "Allowed: every 5 minutes, even asleep",
                    missing = "Without it, idle gaps can stretch to ~10 minutes",
                    onAllow = ::requestExactAlarms,
                )
                Hint("On Samsung, also keep this app off the sleeping lists: Settings › Battery › Background usage limits.")
            }

            if (running) {
                OutlinedButton(onClick = ::stopReporting, modifier = Modifier.fillMaxWidth()) { Text("Stop reporting") }
            } else {
                Button(onClick = ::startReporting, modifier = Modifier.fillMaxWidth()) { Text("Start reporting") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { save(); toast("Saved") }, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = ::sendTestReport, modifier = Modifier.weight(1f)) { Text("Send test") }
            }

            GlassSection("Last report") {
                SelectionContainer { Code(lastReport) }
            }
        }

        askBackgroundLocation?.let { ask ->
            AlertDialog(
                onDismissRequest = { askBackgroundLocation = null },
                title = { Text("Location all the time") },
                text = {
                    Text(
                        "Reports sent while the app is closed need location to include your Wi-Fi name " +
                            "and city. On the next screen, choose \"Allow all the time\".",
                    )
                },
                confirmButton = { TextButton(onClick = { askBackgroundLocation = null; ask() }) { Text("Continue") } },
                dismissButton = { TextButton(onClick = { askBackgroundLocation = null }) { Text("Not now") } },
            )
        }

        bluetoothChoices?.let { choices ->
            AlertDialog(
                onDismissRequest = { bluetoothChoices = null },
                title = { Text("Choose your AirPods") },
                text = {
                    Column {
                        choices.forEach { (name, device) ->
                            Text(
                                name,
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { bluetoothChoices = null; chooseAirpods(name, device) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { bluetoothChoices = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun StatusPill(text: String, colour: Color) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colour.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
            Spacer(Modifier.width(6.dp))
            Text(text, color = colour, style = MaterialTheme.typography.labelLarge)
        }
    }

    @Composable
    private fun Code(text: String) {
        Text(
            text,
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(10.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }

    @Composable
    private fun Hint(text: String, colour: Color = Muted) =
        Text(text, color = colour, style = MaterialTheme.typography.bodySmall)

    @Composable
    private fun Field(
        label: String,
        value: String,
        placeholder: String,
        type: KeyboardType = KeyboardType.Text,
        secret: Boolean = false,
        onChange: (String) -> Unit,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun PermissionRow(label: String, granted: Boolean, ok: String, missing: String, onAllow: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Hint(if (granted) ok else missing, if (granted) Ok else Warn)
            }
            if (!granted) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onAllow) { Text("Allow") }
            }
        }
    }

    // --- status ------------------------------------------------------------

    private fun startReporting() {
        save()
        if (!prefs.isConfigured()) {
            toast("Set a base URL and device name first")
            return
        }
        requestRuntimePermissions()
        prefs.reportingEnabled = true
        ReporterService.start(this)
        // The service flips isRunning in onCreate, a moment after this returns.
        window.decorView.post { refreshServiceState() }
    }

    private fun stopReporting() {
        prefs.reportingEnabled = false
        ReporterService.stop(this)
        refreshServiceState()
    }

    private fun refreshServiceState() {
        running = ReporterService.isRunning
    }

    private fun showLastReport() {
        val last = prefs.lastReport
        if (last.isNotBlank()) lastReport = last
    }

    /** One UI needs all three of these for the service to keep running and report on time. */
    private fun refreshBackgroundRows() {
        val pm = getSystemService(PowerManager::class.java)
        batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        locationFine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        locationOk = locationFine && background
        alarmsOk = ReporterService.canScheduleExact(this)
    }

    // --- permissions -------------------------------------------------------

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !has(Manifest.permission.POST_NOTIFICATIONS)
        ) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !has(Manifest.permission.BLUETOOTH_CONNECT)
        ) needed.add(Manifest.permission.BLUETOOTH_CONNECT)

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun requestLocation() {
        when {
            !has(Manifest.permission.ACCESS_FINE_LOCATION) -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            else -> requestBackgroundLocation(explain = true)
        }
    }

    /**
     * Android sends background location to its own settings page ("Allow all the time").
     * Once the user has declined it there, the request does nothing, so go to app settings.
     */
    private fun requestBackgroundLocation(explain: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return
        askBackgroundLocation = {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) || !explain) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                openAppSettings()
            }
        }
    }

    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")),
        )
    }

    // --- airpods -----------------------------------------------------------

    private fun updateAirpodsLabel() {
        airpodsDevice = if (prefs.airpodsAddress.isNotBlank()) {
            "${prefs.airpodsLabel} · ${prefs.airpodsAddress}"
        } else {
            "No device selected"
        }
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked first; SecurityException is caught
    private fun pickAirpodsDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !has(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            toast("Grant Bluetooth permission, then tap again")
            return
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            toast("Turn Bluetooth on first")
            return
        }

        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            toast("Bluetooth permission missing")
            return
        }
        if (bonded.isEmpty()) {
            toast("No paired Bluetooth devices found")
            return
        }
        bluetoothChoices = bonded.map { (runCatching { it.name }.getOrNull() ?: it.address) to it }
    }

    private fun chooseAirpods(name: String, device: BluetoothDevice) {
        prefs.airpodsAddress = device.address
        prefs.airpodsLabel = name
        prefs.airpodsEnabled = true
        airpodsEnabled = true
        updateAirpodsLabel()
        toast("Saved: $name")
    }

    // --- test --------------------------------------------------------------

    private fun sendTestReport() {
        save()
        if (!prefs.isConfigured()) {
            toast("Set a base URL and device name first")
            return
        }
        lastReport = "Sending test report…"
        lifecycleScope.launch {
            val (url, result) = withContext(Dispatchers.IO) {
                val snap = DeviceState.snapshot(this@MainActivity, prefs, includeLocation = true)
                Reporter.buildUrl(prefs, snap) to Reporter.report(prefs, snap)
            }
            lastReport = buildString {
                append(if (result.ok) "Test OK\n" else "Test FAILED: ${result.message}\n")
                append("\nRequest:\n$url")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
