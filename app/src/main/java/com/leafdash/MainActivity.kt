package com.leafdash

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.leafdash.transport.BtSppTransport
import com.leafdash.transport.DemoTransport
import com.leafdash.ui.DashboardScreen
import com.leafdash.ui.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val vm: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { App(vm) } // uses platform theme colors
            }
        }
    }

    @Composable
    private fun App(vm: DashboardViewModel) {
        val state by vm.state.collectAsState()
        val context = LocalContext.current
        var showPicker by remember { mutableStateOf(false) }
        // saveable: survive the activity recreation of a screen rotation
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showTripLog by rememberSaveable { mutableStateOf(false) }
        var showPowerTest by rememberSaveable { mutableStateOf(false) }

        // keep the screen awake while a session is connected (driving dashboard)
        val activity = context as? android.app.Activity
        androidx.compose.runtime.LaunchedEffect(state.connected) {
            activity?.window?.let { w ->
                if (state.connected) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        val adapter: BluetoothAdapter? = remember {
            (context.getSystemService(BluetoothManager::class.java))?.adapter
        }

        // auto-reconnect to the last used device: on launch, after errors, and
        // whenever disconnected - retry every 10 s until connected (unless the
        // user manually disconnected)
        val lastDevice by vm.lastDevice.collectAsState()
        androidx.compose.runtime.LaunchedEffect(lastDevice) {
            val addr = lastDevice ?: return@LaunchedEffect
            if (adapter == null) return@LaunchedEffect
            while (true) {
                val s = vm.state.value
                if (vm.autoReconnect && !s.connected && !s.connecting && hasBtPermission(context)) {
                    runCatching { adapter.getRemoteDevice(addr) }.getOrNull()?.let { dev ->
                        vm.connect(BtSppTransport(dev, adapter), active = true)
                    }
                }
                kotlinx.coroutines.delay(10_000)
            }
        }

        val permLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
            // CONNECT is mandatory for RFCOMM; SCAN is optional (cancelDiscovery)
            if (result[Manifest.permission.BLUETOOTH_CONNECT] == true) showPicker = true
        }

        fun onConnect() {
            if (hasBtPermission(context)) showPicker = true
            else permLauncher.launch(
                buildList {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            )
        }

        val exportLauncher = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                val csv = vm.tripsCsv()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                }.onFailure {
                    android.widget.Toast.makeText(context, "Export failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val dataLogExport = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
            if (uri != null) scope.launch {
                val err = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { vm.exportDataLog(it) }
                    }.exceptionOrNull()
                }
                if (err != null) {
                    android.widget.Toast.makeText(context, "Export failed: ${err.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        if (showPowerTest) {
            BackHandler { showPowerTest = false }
            com.leafdash.ui.PowerTestScreen(
                state = state,
                onBack = { showPowerTest = false },
                setFast = { vm.setFastCurrent(it) },
            )
            return
        }

        if (showTripLog) {
            val trips by vm.trips.collectAsState()
            BackHandler { showTripLog = false }
            com.leafdash.ui.TripLogScreen(
                trips = trips,
                onExport = {
                    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    exportLauncher.launch("leafdash-trips-$day.csv")
                },
                onBack = { showTripLog = false },
            )
            return
        }

        if (showSettings) {
            val logOn by vm.logEnabled.collectAsState()
            val logUrl by vm.logUrl.collectAsState()
            val streamOn by vm.streamEnabled.collectAsState()
            val reserve by vm.reserveKwh.collectAsState()
            val dataLogOn by vm.dataLogEnabled.collectAsState()
            com.leafdash.ui.SettingsScreen(
                odoMiles = state.odoMiles,
                onSetUnits = { vm.setUnits(it) },
                logEnabled = logOn,
                onSetLog = { vm.setLog(it) },
                logPath = vm.logPath,
                logUrl = logUrl,
                onSetLogUrl = { vm.setLogUrl(it) },
                streamEnabled = streamOn,
                onSetStream = { vm.setStream(it) },
                reserveKwh = reserve,
                onSetReserve = { vm.setReserve(it) },
                onOpenTripLog = { showTripLog = true },
                onOpenPowerTest = { showPowerTest = true },
                onSendDataLog = { sendDataLog() },
                dataLogEnabled = dataLogOn,
                onSetDataLog = { vm.setDataLog(it) },
                dataLogPath = vm.dataLogPath,
                onExportDataLog = {
                    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    dataLogExport.launch("leafdash-datalog-$day.csv")
                },
                onBack = { showSettings = false },
            )
            return
        }

        val diagOn by vm.logEnabled.collectAsState()
        DashboardScreen(
            state = state,
            onConnect = ::onConnect,
            onDemo = { vm.connect(DemoTransport(), active = false) },
            onDisconnect = { vm.disconnect() },
            onResetTrip = { vm.resetTrip() },
            onOpenSettings = { showSettings = true },
            showDiag = diagOn,
        )

        if (showPicker && adapter != null) {
            DevicePicker(
                devices = bondedDevices(adapter),
                onDismiss = { showPicker = false },
                onPick = { dev ->
                    showPicker = false
                    vm.rememberDevice(dev.address)
                    vm.connect(BtSppTransport(dev, adapter), active = true)
                },
            )
        }
    }

    @Composable
    private fun DevicePicker(
        devices: List<BluetoothDevice>,
        onDismiss: () -> Unit,
        onPick: (BluetoothDevice) -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text("Paired devices") },
            text = {
                Column {
                    if (devices.isEmpty()) {
                        Text("No paired devices. Pair the ELM327 in Android Bluetooth settings first.")
                    }
                    devices.forEach { dev ->
                        Text(
                            deviceLabel(dev),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(dev) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }

    /** Share the running/latest data log file: Telegram if installed, else the share sheet. */
    private fun sendDataLog() {
        val file = vm.latestDataLogFile()
        if (file == null || !file.exists()) {
            android.widget.Toast.makeText(this, "No data log yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val telegram = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
            .firstOrNull { pkg -> runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess }
        if (telegram != null) send.setPackage(telegram)
        runCatching { startActivity(android.content.Intent.createChooser(send, "Send data log")) }
            .onFailure { android.widget.Toast.makeText(this, "No app to send to", android.widget.Toast.LENGTH_SHORT).show() }
    }

    // --- permission / device helpers (guarded for API level) ---

    private fun hasBtPermission(context: android.content.Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // gated by hasBtPermission before picker opens
    private fun bondedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> =
        runCatching { adapter.bondedDevices?.toList() ?: emptyList() }.getOrDefault(emptyList())

    @Suppress("MissingPermission")
    private fun deviceLabel(dev: BluetoothDevice): String =
        runCatching { dev.name ?: dev.address }.getOrDefault(dev.address)
}
