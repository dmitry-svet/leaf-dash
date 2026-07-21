package com.leafdash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leafdash.BuildConfig
import com.leafdash.poll.DashState
import com.leafdash.trip.TripWindow

@Composable
fun DashboardScreen(
    state: DashState,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onResetTrip: () -> Unit,
    onToggleUnits: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // status + controls
        val status = when {
            state.connecting -> "Connecting..."
            state.connected -> "Connected"
            state.error != null -> state.error
            else -> "Disconnected"
        }
        val statusColor = when {
            state.connecting -> Color(0xFFB58900)   // amber
            state.connected -> Color(0xFF2E7D32)    // green
            else -> Color(0xFFC62828)               // red
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$status  v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.connecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            if (state.connected || state.connecting) {
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                Button(onClick = onConnect) { Text("Connect") }
                TextButton(onClick = onDemo) { Text("Demo") }
            }
            TextButton(onClick = onToggleUnits) {
                Text(if (state.odoMiles) "mi" else "km")
            }
        }
        if (state.connecting && state.connectMsg != null) {
            Text(state.connectMsg, style = MaterialTheme.typography.bodyMedium)
        }

        // live tiles
        val leaf = state.leaf
        val tempStr = if (leaf.batteryTempsC.isEmpty()) "--"
            else leaf.batteryTempsC.joinToString(" / ") { "%.0f".format(it) } + " C"

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("SOH", fmt(leaf.sohPercent, 0, "%"), Modifier.weight(1f))
            Tile("Hx", fmt(leaf.hx, 1, "%"), Modifier.weight(1f))
            Tile("Odo km", state.odoKm?.let { "%.0f".format(it) } ?: "--", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("Bat temp", tempStr, Modifier.weight(1f))
            Tile("Ext temp", fmt(leaf.ambientTempC, 0, " C"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("SOC", fmt(leaf.socPercent, 1, "%"), Modifier.weight(1f), big = true)
            Tile("Battery", fmt(leaf.kwhRemaining, 1, " kWh"), Modifier.weight(1f), big = true)
        }

        // energy economy: km from odometer, kWh from battery drop.
        // Always shown - keeps last values after disconnect.
        Text(
            "Energy economy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        // stable efficiency for short windows: prefer the all-time lifetime
        // average, else the smoothed EMA
        val refEff = state.lifetime.kwhPer100?.takeIf { state.lifetime.km >= 1.0 }
            ?: state.avgKwhPer100
        TripCard("Lifetime", state.lifetime, leaf.kwhRemaining, refEff)
        TripCard("Since last charge", state.lastCharge, leaf.kwhRemaining, refEff)
        TripCard("Since car on", state.carOn, leaf.kwhRemaining, refEff)
        TripCard("Trip", state.trip, leaf.kwhRemaining, refEff)
        OutlinedButton(onClick = onResetTrip) { Text("Reset trip") }

        // connection / debug info
        if (state.debug.isNotEmpty()) {
            Text(
                "Debug",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    state.debug.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // diagnostic: raw ISO-TP responses (active mode, for offset mapping)
        if (state.raw.isNotEmpty()) {
            Text(
                "Raw (diagnostic)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.raw.forEach { (group, hex) ->
                        Column {
                            Text(group, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                hex.ifBlank { "(no reply)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = if (big) MaterialTheme.typography.headlineLarge
                else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TripCard(title: String, w: TripWindow, kwhRemaining: Double?, refEff: Double) {
    // range: use this window's efficiency once it has enough distance, else the
    // stable reference efficiency (avoids absurd range on short/downhill windows)
    val eff = w.kwhPer100?.takeIf { w.km >= 3.0 && it > 0 } ?: refEff
    val range = kwhRemaining?.let { if (eff > 0) it / eff * 100.0 else null }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("km", fmt(w.km, 1), Modifier.weight(1f))
                Metric("kWh", fmt(w.kwh, 2), Modifier.weight(1f))
                Metric("kWh/100", fmt(eff, 1), Modifier.weight(1f))
                Metric("range km", fmt(range, 0), Modifier.weight(1f))
            }
        }
    }
}

/** One column: small legend on top, value below at tile size. */
@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun fmt(v: Double?, digits: Int, suffix: String = ""): String =
    if (v == null) "--" else "%.${digits}f".format(v) + suffix
