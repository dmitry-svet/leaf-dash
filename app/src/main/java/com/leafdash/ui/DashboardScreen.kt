package com.leafdash.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leafdash.BuildConfig
import com.leafdash.poll.DashState
import com.leafdash.trip.TripWindow
import com.leafdash.update.UpdateCheck

@Composable
fun DashboardScreen(
    state: DashState,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onResetTrip: () -> Unit,
    onOpenSettings: () -> Unit,
    showDiag: Boolean = false,
    updateVersion: String? = null,
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
            state.connecting -> t("Connecting...", "З'єднання...")
            state.connected -> t("Connected", "З'єднано")
            state.error != null -> state.error
            else -> t("Disconnected", "Відключено")
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
                TextButton(onClick = onDisconnect) { Text(t("Disconnect", "Відключити")) }
            } else {
                Button(onClick = onConnect) { Text(t("Connect", "Підключити")) }
                TextButton(onClick = onDemo) { Text(t("Demo", "Демо")) }
            }
            TextButton(onClick = onOpenSettings) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)   // vertical ellipsis
            }
        }
        if (state.connecting && state.connectMsg != null) {
            Text(state.connectMsg, style = MaterialTheme.typography.bodyMedium)
        }
        if (updateVersion != null) {
            val ctx = LocalContext.current
            Text(
                t("Update v", "Оновлення v") + updateVersion + t(" available · tap to download", " доступне · тисни, щоб завантажити"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { openUrl(ctx, UpdateCheck.apkUrl(updateVersion)) },
            )
        }
        // live tiles + energy economy: side by side in landscape, stacked in
        // portrait
        val landscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            // equal-height blocks: the taller one sets the height, rows in the
            // other spread out to match
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiveTiles(state, Modifier.weight(1f).fillMaxHeight(), stretch = true)
                EnergyEconomy(state, onResetTrip, Modifier.weight(1.4f).fillMaxHeight(), compact = true)
            }
        } else {
            LiveTiles(state)
            EnergyEconomy(state, onResetTrip)
        }

        // connection / debug info (only when diagnostics/log is enabled)
        if (showDiag && state.debug.isNotEmpty()) {
            Text(
                t("Debug", "Відладка"),
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
        if (showDiag && state.raw.isNotEmpty()) {
            Text(
                t("Raw (diagnostic)", "Сирі дані (діагностика)"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.raw.forEach { (group, hex) ->
                        Column {
                            Text(group, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                hex.ifBlank { t("(no reply)", "(немає відповіді)") },
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
private fun LiveTiles(state: DashState, modifier: Modifier = Modifier, stretch: Boolean = false) {
    val leaf = state.leaf
    val tempStr = if (leaf.batteryTempsC.isEmpty()) "--"
        else leaf.batteryTempsC.joinToString(" / ") { "%.0f".format(it) } + " C"

    Column(
        modifier,
        verticalArrangement = if (stretch) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile("SOH", fmt(leaf.sohPercent, 1, "%"), Modifier.weight(1f))
            Tile("Hx", fmt(leaf.hx, 1, "%"), Modifier.weight(1f))
            Tile(t("Odo km", "Пробіг км"), state.odoKm?.let { "%.0f".format(it) } ?: "--", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(t("Bat temp", "Батарея"), tempStr, Modifier.weight(1.4f))
            Tile(t("Ext temp", "Зовні"), fmt(leaf.ambientTempC, 0, " C"), Modifier.weight(0.8f))
            Tile("12V", fmt(leaf.aux12V, 1, " V"), Modifier.weight(0.8f))
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tile("SOC", fmt(leaf.socPercent, 1, "%"), Modifier.weight(0.8f).fillMaxHeight(), big = true)
            Tile(t("Usable", "Доступно"), fmt(usableKwh(state), 1, " kWh"), Modifier.weight(1f).fillMaxHeight(), big = true)
            // weakest cell V (tenths) over min-max spread (mV), no legend;
            // red = weakest cell dictates power cut (turtle) / cells diverging
            CellsTile(leaf.cellMinV, leaf.cellMaxV, Modifier.weight(0.8f).fillMaxHeight())
        }
    }
}

// energy economy: km from odometer, kWh from battery drop.
// Always shown - keeps last values after disconnect.
@Composable
private fun EnergyEconomy(
    state: DashState,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // stable efficiency for short windows: prefer the all-time lifetime
    // average, else the smoothed EMA
    val refEff = state.lifetime.kwhPer100?.takeIf { state.lifetime.km >= 1.0 }
        ?: state.avgKwhPer100
    val kwhRemaining = usableKwh(state)
    // tap the efficiency column to flip kWh/100 km <-> km/kWh
    var perKwh by rememberSaveable { mutableStateOf(false) }
    val flip = { perKwh = !perKwh }
    if (compact) {
        // landscape: one table card, window name in the first column, metric
        // legend once on top — all 4 windows fit the screen
        Card(modifier) {
            Column(
                Modifier.fillMaxHeight().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1.5f))
                    for (label in listOf(t("km", "км"), t("kWh", "кВт·г"), effLabel(perKwh), t("range km", "запас км"))) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).clickable(onClick = flip),
                        )
                    }
                }
                TripRow(t("Lifetime", "Загалом"), state.lifetime, kwhRemaining, refEff, perKwh, flip, kmDigits = 0, kwhDigits = 0)
                TripRow(t("Since last charge", "З останньої зарядки"), state.lastCharge, kwhRemaining, refEff, perKwh, flip)
                TripRow(t("Since car on", "З увімкнення"), state.carOn, kwhRemaining, refEff, perKwh, flip)
                TripRow(t("Trip", "Поїздка"), state.trip, kwhRemaining, refEff, perKwh, flip, onReset = onResetTrip)
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t("Energy economy", "Витрата енергії"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TripCard(t("Lifetime", "Загалом"), state.lifetime, kwhRemaining, refEff, perKwh, flip, kmDigits = 0, kwhDigits = 0)
            TripCard(t("Since last charge", "З останньої зарядки"), state.lastCharge, kwhRemaining, refEff, perKwh, flip)
            TripCard(t("Since car on", "З увімкнення"), state.carOn, kwhRemaining, refEff, perKwh, flip)
            TripCard(t("Trip", "Поїздка"), state.trip, kwhRemaining, refEff, perKwh, flip, onReset = onResetTrip)
        }
    }
}

/** One compact table line: window name + values, no per-line legend. */
@Composable
private fun TripRow(
    title: String,
    w: TripWindow,
    kwhRemaining: Double?,
    refEff: Double,
    perKwh: Boolean,
    onFlipEff: () -> Unit,
    onReset: (() -> Unit)? = null,
    kmDigits: Int = 1,
    kwhDigits: Int = 2,
) {
    val (eff, range) = tripEffRange(w, kwhRemaining, refEff)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onReset != null) {
                Text(
                    t("Reset", "Скинути"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        FitText(fmt(w.km, kmDigits), MaterialTheme.typography.headlineMedium, Modifier.weight(1f))
        FitText(fmt(w.kwh, kwhDigits), MaterialTheme.typography.headlineMedium, Modifier.weight(1f))
        FitText(effText(w, eff, perKwh), MaterialTheme.typography.headlineMedium, Modifier.weight(1f).clickable(onClick = onFlipEff))
        FitText(fmt(range, 0), MaterialTheme.typography.headlineMedium, Modifier.weight(1f))
    }
}

/** Single-line bold value that shrinks its font until it fits the width. */
@Composable
private fun FitText(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    var scale by remember(value) { mutableFloatStateOf(1f) }
    Text(
        value,
        style = style,
        fontSize = style.fontSize * scale,
        fontWeight = FontWeight.Bold,
        color = color ?: Color.Unspecified,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.4f) scale *= 0.9f },
        modifier = modifier,
    )
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FitText(
                value,
                style = if (big) MaterialTheme.typography.headlineLarge
                else MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

/** Weakest cell voltage over cell spread, stacked, no legend. */
@Composable
private fun CellsTile(minV: Double?, maxV: Double?, modifier: Modifier = Modifier) {
    val spreadMv = if (minV != null && maxV != null) (maxV - minV) * 1000.0 else null
    val danger = Color(0xFFC62828)
    Card(modifier) {
        Column(
            Modifier.fillMaxHeight().padding(10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            FitText(
                fmt(minV, 1, "V"),
                MaterialTheme.typography.titleLarge,
                color = danger.takeIf { minV != null && minV < CELL_MIN_DANGER_V },
            )
            FitText(
                spreadMv?.let { "%.0fmV".format(it) } ?: "--",
                MaterialTheme.typography.titleLarge,
                color = danger.takeIf { spreadMv != null && spreadMv > CELL_SPREAD_DANGER_MV },
            )
        }
    }
}

// weakest cell near cutoff = turtle imminent; spread this wide = weak cells
// already diving under load (turtle came at 340 mV, 105 mV seen at 18% SOC)
private const val CELL_MIN_DANGER_V = 3.15
private const val CELL_SPREAD_DANGER_MV = 200.0

@Composable
private fun TripCard(
    title: String,
    w: TripWindow,
    kwhRemaining: Double?,
    refEff: Double,
    perKwh: Boolean,
    onFlipEff: () -> Unit,
    onReset: (() -> Unit)? = null,
    kmDigits: Int = 1,
    kwhDigits: Int = 2,
) {
    val (eff, range) = tripEffRange(w, kwhRemaining, refEff)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (onReset != null) {
                    Text(
                        t("Reset", "Скинути"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(t("km", "км"), fmt(w.km, kmDigits), Modifier.weight(1f))
                Metric(t("kWh", "кВт·г"), fmt(w.kwh, kwhDigits), Modifier.weight(1f))
                Metric(effLabel(perKwh), effText(w, eff, perKwh), Modifier.weight(1f).clickable(onClick = onFlipEff))
                Metric(t("range km", "запас км"), fmt(range, 0), Modifier.weight(1f))
            }
        }
    }
}

/** One column: small legend on top, value below at tile size. */
@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        FitText(value, style = MaterialTheme.typography.headlineMedium)
    }
}

// efficiency: this window's own once it has a first km of real data, else the
// stable reference. No range prediction until the window has its first km
// (fresh windows have nothing real to predict from); efficiency floored/capped
// like the EMA so a downhill/regen start can't show absurd range.
// weak cells make the pack bottom unusable: remaining energy minus the
// user's reserve setting, for the tile and range predictions
private fun usableKwh(state: DashState): Double? =
    state.leaf.kwhRemaining?.let { (it - state.reserveKwh).coerceAtLeast(0.0) }

private fun effLabel(perKwh: Boolean) = if (perKwh) t("km/kWh", "км/кВт·г") else t("kWh/100", "кВт·г/100")

private fun effText(w: TripWindow, eff: Double, perKwh: Boolean): String =
    if (w.km < 1.0) "--" else if (perKwh) (if (eff > 0) fmt(100.0 / eff, 2) else "--") else fmt(eff, 1)

private fun tripEffRange(w: TripWindow, kwhRemaining: Double?, refEff: Double): Pair<Double, Double?> {
    val eff = w.kwhPer100?.takeIf { w.km >= 1.0 && it > 0 } ?: refEff
    val range = if (w.km < 1.0) null
        else kwhRemaining?.let { it / eff.coerceIn(5.0, 60.0) * 100.0 }
    return eff to range
}

private fun fmt(v: Double?, digits: Int, suffix: String = ""): String =
    if (v == null) "--" else "%.${digits}f".format(v) + suffix
