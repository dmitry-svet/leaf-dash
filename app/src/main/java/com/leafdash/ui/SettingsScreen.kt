package com.leafdash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Settings screen: UI language, car odometer units, logs, reserve, tire pressure.
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
    onOpenTripLog: () -> Unit,
    tiresPsi: List<Double?>,
    onSendDataLog: () -> Unit,
    dataLogEnabled: Boolean,
    onSetDataLog: (Boolean) -> Unit,
    onSetLang: (Boolean) -> Unit,
    dataLogPath: String,
    onExportDataLog: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(t("‹ Back", "‹ Назад")) }
            Text(t("Settings", "Налаштування"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("Language", "Мова"), style = MaterialTheme.typography.titleMedium)
            LangDropdown(onSetLang)
        }

        OutlinedButton(onClick = onOpenTripLog, modifier = Modifier.fillMaxWidth()) {
            Text(t("Trip log", "Журнал поїздок"))
        }
        Text(t("Tire pressure, bar", "Тиск у шинах, бар"), style = MaterialTheme.typography.titleMedium)
        // poller order: FL, FR, RR, RL
        for ((rowLabel, idx) in listOf(t("Front", "Передні") to listOf(0, 1), t("Rear", "Задні") to listOf(3, 2))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rowLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                for (i in idx) {
                    val psi = tiresPsi.getOrNull(i)
                    Text(
                        if (psi == null) "--" else "%.2f".format(psi * 0.0689476),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(t("From the car's TPMS (CAN 0x385), read every 5th poll cycle (~25 s) on a live connection.",
            "З TPMS авто (CAN 0x385), читається кожен 5-й цикл (~25 с) при живому з'єднанні."), style = MaterialTheme.typography.bodySmall)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("Car units", "Одиниці авто"), style = MaterialTheme.typography.titleMedium)
            UnitsDropdown(odoMiles, onSetUnits)
        }
        Text(
            t(
                "How the car's odometer reports distance. Set 'mi' if the app's odometer reads lower than the dash.",
                "Як одометр авто передає відстань. Постав 'mi', якщо пробіг у апці менший, ніж на панелі.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("Data log (LeafSpy format)", "Лог даних (формат LeafSpy)"), style = MaterialTheme.typography.titleMedium)
            Switch(checked = dataLogEnabled, onCheckedChange = onSetDataLog)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSetDataLog(!dataLogEnabled) }, modifier = Modifier.weight(1f)) {
                Text(
                    if (dataLogEnabled) t("\u25A0 Stop log", "\u25A0 Стоп лог") else t("\u25CF Start log", "\u25CF Старт лог"),
                    color = if (dataLogEnabled) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = onSendDataLog, modifier = Modifier.weight(1f)) { Text(t("Send log", "Надіслати лог")) }
        }
        Text(
            t(
                "One row per sample in LeafSpy's CSV layout, a file per Start..Stop (buttons on the dashboard): ",
                "Один рядок на семпл у CSV-форматі LeafSpy, файл на кожен Старт..Стоп (кнопки на дашборді): ",
            ) + dataLogPath,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onExportDataLog, modifier = Modifier.fillMaxWidth()) {
            Text(t("Export data log CSV", "Експорт логу даних CSV"))
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("Distance log", "Лог відстані"), style = MaterialTheme.typography.titleMedium)
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
            label = { Text(t("Unusable capacity, kWh", "Недоступна ємність, кВт·год")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            t(
                "Bottom-of-pack energy the car can't use (weak cells cut power early). Subtracted from the Usable tile and range predictions.",
                "Енергія на дні батареї, яку авто не використає (слабкі ячейки рано зрізають потужність). Віднімається від плитки Доступно та прогнозу запасу.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t("Stream log to PC", "Стрім логу на ПК"), style = MaterialTheme.typography.titleMedium)
            Checkbox(checked = streamEnabled, onCheckedChange = onSetStream)
        }
        OutlinedTextField(
            value = logUrl,
            onValueChange = onSetLogUrl,
            label = { Text(t("Log server URL", "URL лог-сервера")) },
            singleLine = true,
            enabled = streamEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            t(
                "POST log lines to this endpoint (tools/serve_log_ngrok.sh on the PC).",
                "POST рядків логу на цей ендпоінт (tools/serve_log_ngrok.sh на ПК).",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "2.0" -> "2", "1.5" -> "1.5" for the text field's initial value. */
private fun trimZeros(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun LangDropdown(onSetLang: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(if (Lang.uk) "Українська ▾" else "English ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("English") }, onClick = { onSetLang(false); open = false })
            DropdownMenuItem(text = { Text("Українська") }, onClick = { onSetLang(true); open = false })
        }
    }
}

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
