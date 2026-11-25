package com.leafdash

import android.Manifest
import com.leafdash.ui.t
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
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
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

    override fun onStart() {
        super.onStart()
        vm.checkUpdateDaily()   // the process lives long (BT session), so not only at launch
    }

    @Composable
    private fun App(vm: DashboardViewModel) {
        val state by vm.state.collectAsState()
        val context = LocalContext.current
        var showPicker by remember { mutableStateOf(false) }
        // saveable: survive the activity recreation of a screen rotation
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showTripLog by rememberSaveable { mutableStateOf(false) }
        var showStats by rememberSaveable { mutableStateOf(false) }
        var showLoad by rememberSaveable { mutableStateOf(false) }

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
                    android.widget.Toast.makeText(context, t("Export failed: ", "Експорт не вдався: ") + it.message, android.widget.Toast.LENGTH_LONG).show()
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
                    android.widget.Toast.makeText(context, t("Export failed: ", "Експорт не вдався: ") + err.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        fun importLeafSpy(uris: List<android.net.Uri>) = scope.launch {
            var parsed = 0
            var added = 0
            val err = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    for (u in uris) {
                        val text = context.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: continue
                        val (p, a) = vm.importLeafSpy(text)
                        parsed += p; added += a
                    }
                }.exceptionOrNull()
            }
            val msg = when {
                err != null -> t("Import failed: ", "Імпорт не вдався: ") + err.message
                uris.isEmpty() -> t("No TripChrgLog_*.csv found", "TripChrgLog_*.csv не знайдено")
                parsed == 0 -> t("Not a LeafSpy trip log", "Це не журнал поїздок LeafSpy")
                else -> t("LeafSpy: $parsed drives, $added new added", "LeafSpy: поїздок $parsed, нових додано $added")
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        val leafSpyFile = rememberLauncherForActivityResult(OpenDocument()) { uri ->
            if (uri != null) importLeafSpy(listOf(uri))
        }
        val leafSpyTree = rememberLauncherForActivityResult(OpenDocumentTree()) { tree ->
            if (tree != null) scope.launch {
                // keep the grant so the folder is re-checked on every Settings visit
                runCatching { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.leafdash.trip.DocumentScan.find(context.contentResolver, tree, vm::isLeafSpyTripLog)
                }
                importLeafSpy(found)
                vm.scanLeafSpy()
            }
        }

        if (showLoad) {
            BackHandler { showLoad = false }
            // fast poll (pack A/V, speed, VCM power only) while the screen is open
            androidx.compose.runtime.DisposableEffect(Unit) {
                vm.setFastLoad(true)
                onDispose { vm.setFastLoad(false) }
            }
            val bars by vm.loadBars.collectAsState()
            com.leafdash.ui.LoadScreen(state = state, bars = bars, onBars = vm::setLoadBars, onBack = { showLoad = false })
            return
        }

        if (showStats) {
            val trips by vm.trips.collectAsState()
            BackHandler { showStats = false }
            val statsView by vm.statsView.collectAsState()
            com.leafdash.ui.StatsScreen(trips = trips, view = statsView, onView = vm::setStatsView, onBack = { showStats = false })
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
            val update by vm.update.collectAsState()
            val leafSpyFound by vm.leafSpyFound.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.scanLeafSpy() }
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
                leafSpyFound = leafSpyFound,
                onImportFound = { importLeafSpy(leafSpyFound.orEmpty().map { it.uri }) },
                onImportLeafSpy = { leafSpyFile.launch(arrayOf("*/*")) },
                onScanLeafSpy = { leafSpyTree.launch(null) },
                onOpenStats = { showStats = true },
                tiresPsi = state.leaf.tiresPsi,
                onSendDataLog = { sendDataLog() },
                dataLogEnabled = dataLogOn,
                onSetDataLog = { vm.setDataLog(it) },
                onSetLang = { vm.setLangUk(it) },
                update = update,
                onCheckUpdate = { vm.checkUpdate() },
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
        val update by vm.update.collectAsState()
        DashboardScreen(
            state = state,
            onConnect = ::onConnect,
            onDemo = { vm.connect(DemoTransport(), active = false) },
            onDisconnect = { vm.disconnect() },
            onResetTrip = { vm.resetTrip() },
            onOpenSettings = { showSettings = true },
            onOpenStats = { showStats = true },
            onOpenLoad = { showLoad = true },
            showDiag = diagOn,
            updateVersion = update.newer,
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
            dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel", "Скасувати")) } },
            title = { Text(t("Paired devices", "Спарені пристрої")) },
            text = {
                Column {
                    if (devices.isEmpty()) {
                        Text(t("No paired devices. Pair the ELM327 in Android Bluetooth settings first.",
                            "Немає спарених пристроїв. Спочатку спаруйте ELM327 у Bluetooth-налаштуваннях Android."))
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
            android.widget.Toast.makeText(this, t("No data log yet", "Логу даних ще немає"), android.widget.Toast.LENGTH_SHORT).show()
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
        runCatching { startActivity(android.content.Intent.createChooser(send, t("Send data log", "Надіслати лог даних"))) }
            .onFailure { android.widget.Toast.makeText(this, t("No app to send to", "Немає куди надіслати"), android.widget.Toast.LENGTH_SHORT).show() }
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
