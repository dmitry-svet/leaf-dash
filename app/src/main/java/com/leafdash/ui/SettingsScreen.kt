package com.leafdash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Settings screen. Currently: car odometer units (km / mi) as a dropdown.
 */
@Composable
fun SettingsScreen(
    odoMiles: Boolean,
    onSetUnits: (Boolean) -> Unit,
    logEnabled: Boolean,
    onSetLog: (Boolean) -> Unit,
    logPath: String,
    logUrl: String,
    onSetLogUrl: (String) -> Unit,
    streamEnabled: Boolean,
    onSetStream: (Boolean) -> Unit,
    reserveKwh: Double,
    onSetReserve: (Double) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Car units", style = MaterialTheme.typography.titleMedium)
            UnitsDropdown(odoMiles, onSetUnits)
        }
        Text(
            "How the car's odometer reports distance. Set 'mi' if the app's " +
                "odometer reads lower than the dash.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Distance log", style = MaterialTheme.typography.titleMedium)
            Switch(checked = logEnabled, onCheckedChange = onSetLog)
        }
        if (logEnabled) {
            Text(logPath, style = MaterialTheme.typography.bodySmall)
        }

        var reserveTxt by remember(reserveKwh) { mutableStateOf(trimZeros(reserveKwh)) }
        OutlinedTextField(
            value = reserveTxt,
            onValueChange = { txt ->
                reserveTxt = txt
                txt.replace(',', '.').toDoubleOrNull()
                    ?.takeIf { it in 0.0..20.0 }?.let(onSetReserve)
            },
            label = { Text("Unusable capacity, kWh") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Bottom-of-pack energy the car can't use (weak cells cut power " +
                "early). Subtracted from range predictions.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Stream log to PC", style = MaterialTheme.typography.titleMedium)
            Checkbox(checked = streamEnabled, onCheckedChange = onSetStream)
        }
        OutlinedTextField(
            value = logUrl,
            onValueChange = onSetLogUrl,
            label = { Text("Log server URL") },
            singleLine = true,
            enabled = streamEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "POST log lines to this endpoint (tools/serve_log_ngrok.sh on the PC).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "2.0" -> "2", "1.5" -> "1.5" for the text field's initial value. */
private fun trimZeros(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun UnitsDropdown(odoMiles: Boolean, onSetUnits: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(if (odoMiles) "mi ▾" else "km ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("km") }, onClick = { onSetUnits(false); open = false })
            DropdownMenuItem(text = { Text("mi") }, onClick = { onSetUnits(true); open = false })
        }
    }
}
