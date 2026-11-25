package com.leafdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leafdash.can.Load
import com.leafdash.poll.DashState
import kotlin.math.ceil
import kotlin.math.floor

private val MOT = Color(0xFF6650A4)
private val REG = Color(0xFF2E8C46)
private val AC = Color(0xFF1E78C8)
private val HEAT = Color(0xFFD95A1A)
private val AUX = Color(0xFF0F8A8A)
private val OTH = Color(0xFF827C88)

private const val WINDOW_MS = 120_000L

private class Sample(val t: Long, val battery: Double?, val motor: Double?, val ac: Double, val heat: Double, val aux: Double, val other: Double)

/**
 * Live load: battery power split into motor (traction / regen), climate
 * (A/C + heater) and aux + other, as dials or bars ([bars], toggled in the
 * header), plus current consumption, remaining energy and a 2-minute chart.
 * While open the caller puts the poller in fast mode; the header shows the
 * measured update interval. Swipe left to go back.
 */
@Composable
fun LoadScreen(state: DashState, bars: Boolean, onBars: (Boolean) -> Unit, onBack: () -> Unit) {
    val load = Load.of(state.leaf)
    val history = remember { mutableStateListOf<Sample>() }
    var lastMs by remember { mutableStateOf(0L) }
    var intervalMs by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(state.cycle) {
        if (!state.connected) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (lastMs > 0) {
            val dt = (now - lastMs).toDouble()
            if (dt < 30_000) intervalMs = intervalMs?.let { it + 0.3 * (dt - it) } ?: dt
        }
        lastMs = now
        history.add(Sample(now, load.batteryKw, load.motorKw, load.acKw ?: 0.0, load.heaterKw ?: 0.0, load.auxKw ?: 0.0, load.otherKw ?: 0.0))
        while (history.isNotEmpty() && history.first().t < now - WINDOW_MS) history.removeAt(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 120.dp.toPx()
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -threshold) onBack() },
                ) { _, delta -> dx += delta }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onBack) { Text(t("‹ Back", "‹ Назад")) }
            Text(t("Load", "Навантаження"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(if (state.connected) REG else Color(0xFFC62828), CircleShape))
            Text(intervalMs?.let { t("%.1f s", "%.1f с").format(it / 1000) } ?: "--", style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(t("Dials", "Циферблати"), !bars, Modifier.weight(1f)) { onBars(false) }
            Pill(t("Bars", "Смужки"), bars, Modifier.weight(1f)) { onBars(true) }
        }
        TopPlates(state, load)
        val regenMax = history.mapNotNull { it.motor }.minOrNull()?.takeIf { it < 0 }
        if (bars) {
            MotorBars(load, state)
            StackBar(t("Climate", "Клімат"), 5.0, listOf(t("A/C", "Кондиціонер") to load.acKw, t("Heater", "Обігрів") to load.heaterKw), AC, HEAT)
            StackBar(t("Other", "Інше"), 2.0, listOf(t("Aux", "Допоміжні") to load.auxKw, t("Other", "Інше") to load.otherKw), AUX, OTH)
        } else {
            MotorDial(load, state, regenMax)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StackDial(t("Climate", "Клімат"), 5.0, listOf(t("A/C", "Кондиціонер") to load.acKw, t("Heater", "Обігрів") to load.heaterKw), AC, HEAT, Modifier.weight(1f))
                    StackDial(t("Other", "Інше"), 2.0, listOf(t("Aux", "Допоміжні") to load.auxKw, t("Other", "Інше") to load.otherKw), AUX, OTH, Modifier.weight(1f))
                }
            }
        }
        HistoryChart(history)
        val l = state.leaf
        Text(
            t("Battery ", "Батарея ") + fmt(load.batteryKw, 1) + t(" kW · ", " кВт · ") + fmt(l.packAmps, 1) + t(" A · ", " А · ") +
                fmt(l.packVolts, 1) + t(" V · 12V ", " В · 12V ") + fmt(l.aux12V, 1) + t(" V", " В"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else c,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .background(if (selected) c else Color.Transparent, RoundedCornerShape(50))
            .border(1.5.dp, c, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** Current consumption and remaining energy / range. */
@Composable
private fun TopPlates(state: DashState, load: Load) {
    val usable = state.leaf.kwhRemaining?.let { (it - state.reserveKwh).coerceAtLeast(0.0) }
    val refEff = (state.lifetime.kwhPer100?.takeIf { state.lifetime.km >= 1.0 } ?: state.avgKwhPer100).coerceIn(5.0, 60.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Plate(t("Consumption now", "Витрата зараз"), fmt(load.kwhPer100, 1), t("kWh/100", "кВт·г/100"),
            fmt(state.leaf.speedKmh, 0) + t(" km/h", " км/год"), MOT, Modifier.weight(1f))
        Plate(t("Remaining", "Залишок"), fmt(usable, 1), t("kWh", "кВт·г"),
            "≈ " + fmt(usable?.let { it / refEff * 100 }, 0) + t(" km", " км"), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun Plate(label: String, value: String, unit: String, sub: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(unit, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

private const val ARC_START = 150f
private const val ARC_SWEEP = 240f

private fun DrawScope.arc(color: Color, from: Float, sweep: Float, w: Float) {
    val r = size.minDimension / 2 - w / 2
    drawArc(color, from, sweep, false, Offset(size.width / 2 - r, size.height / 2 - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Butt))
}

@Composable
private fun MotorDial(load: Load, state: DashState, regenMax: Double?) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val lo = -30.0
    val hi = 80.0
    val v = load.motorKw
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(t("Motor", "Мотор"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.3f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.minDimension * 0.09f
                        fun ang(x: Double) = ARC_START + ARC_SWEEP * ((x.coerceIn(lo, hi) - lo) / (hi - lo)).toFloat()
                        arc(track, ARC_START, ARC_SWEEP, w)
                        if (v != null) {
                            if (v >= 0) arc(MOT, ang(0.0), ang(v) - ang(0.0), w)
                            else arc(REG, ang(v), ang(0.0) - ang(v), w)
                        }
                        arc(Color.Black.copy(alpha = 0.6f), ang(0.0) - 0.7f, 1.4f, w * 1.3f)   // zero tick
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt(v, 1), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = if ((v ?: 0.0) < 0) REG else MOT)
                        Text(t("kW", "кВт"), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(t("RPM", "Оберти"), rpm(state.leaf.motorRpm), t("rpm", "об/хв"))
                    Stat(t("Torque", "Момент"), fmt(state.leaf.torqueNm, 0), t("N·m", "Н·м"))
                    Stat(t("Regen max", "Рекуп. макс"), fmt(regenMax, 1), t("kW, 2 min", "кВт, 2 хв"), REG)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
        }
    }
}

/** One dial, two stacked segments from 0; total in the centre, both values below. */
@Composable
private fun StackDial(title: String, hi: Double, parts: List<Pair<String, Double?>>, c1: Color, c2: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val total = parts.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.sum()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension * 0.1f
                arc(track, ARC_START, ARC_SWEEP, w)
                var acc = 0.0
                for ((i, p) in parts.withIndex()) {
                    val v = p.second ?: 0.0
                    val a0 = (acc / hi).coerceAtMost(1.0)
                    val a1 = ((acc + v) / hi).coerceAtMost(1.0)
                    if (a1 > a0) arc(if (i == 0) c1 else c2, ARC_START + ARC_SWEEP * a0.toFloat(), ARC_SWEEP * (a1 - a0).toFloat(), w)
                    acc += v
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(fmt(total, 2), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(t("kW", "кВт"), style = MaterialTheme.typography.labelMedium)
            }
        }
        for ((i, p) in parts.withIndex()) Legend(p.first, p.second, if (i == 0) c1 else c2, 26)
    }
}

@Composable
private fun Legend(label: String, v: Double?, color: Color, size: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp).weight(1f), maxLines = 1)
        Text(fmt(v, 2), fontSize = size.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

/** Motor power as a big number over a bar centred on 0: regen left, traction right. */
@Composable
private fun MotorBars(load: Load, state: DashState) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val zeroC = MaterialTheme.colorScheme.onSurface
    val lo = -30.0
    val hi = 80.0
    val v = load.motorKw
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Motor", "Мотор"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(v, 1), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if ((v ?: 0.0) < 0) REG else MOT)
                Text(
                    if ((v ?: 0.0) < 0) t(" kW regen", " кВт рекуп.") else t(" kW traction", " кВт тяга"),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp).weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(rpm(state.leaf.motorRpm), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(t("rpm · ", "об/хв · ") + fmt(state.leaf.torqueNm, 0) + t(" N·m", " Н·м"), style = MaterialTheme.typography.labelMedium)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                fun x(k: Double) = (size.width * (k - lo) / (hi - lo)).toFloat()
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                if (v != null) {
                    val a = x(minOf(0.0, v.coerceIn(lo, hi)))
                    val b = x(maxOf(0.0, v.coerceIn(lo, hi)))
                    drawRect(if (v < 0) REG else MOT, Offset(a, 0f), Size(b - a, size.height))
                }
                drawRect(zeroC, Offset(x(0.0) - 1.5f, -4f), Size(3f, size.height + 8f))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("← " + t("regen", "рекуперація") + "  −30", style = MaterialTheme.typography.labelSmall, color = REG, modifier = Modifier.weight(1f))
                Text("80  " + t("traction", "тяга") + " →", style = MaterialTheme.typography.labelSmall, color = MOT)
            }
        }
    }
}

/** Two values stacked in one bar from 0 to [hi]; total top right, values below. */
@Composable
private fun StackBar(title: String, hi: Double, parts: List<Pair<String, Double?>>, c1: Color, c2: Color) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val total = parts.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.sum()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(t("kW ", "кВт "), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
                Text(fmt(total, 2), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                var acc = 0.0
                for ((i, p) in parts.withIndex()) {
                    val v = p.second ?: 0.0
                    val a = (size.width * (acc / hi).coerceAtMost(1.0)).toFloat()
                    val b = (size.width * ((acc + v) / hi).coerceAtMost(1.0)).toFloat()
                    if (b > a) drawRect(if (i == 0) c1 else c2, Offset(a, 0f), Size(b - a, size.height))
                    acc += v
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("%.0f".format(hi), style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for ((i, p) in parts.withIndex()) {
                    Box(Modifier.weight(1f)) { Legend(p.first, p.second, if (i == 0) c1 else c2, 30) }
                }
            }
        }
    }
}

/**
 * Last 2 minutes, kW: battery power line (green below 0 = regen) over
 * stacked aux / other / A/C / heater areas. Y range grows to fit.
 */
@Composable
private fun HistoryChart(history: List<Sample>) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val axis = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val bat = history.mapNotNull { it.battery }
    val lo = floor(minOf(-10.0, bat.minOrNull() ?: 0.0) / 10) * 10
    val hi = ceil(maxOf(20.0, bat.maxOrNull() ?: 0.0) / 10) * 10
    val step = if (hi - lo > 80) 20.0 else 10.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(t("Last 2 min, kW", "Останні 2 хв, кВт"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (bat.isNotEmpty()) {
                    Text(t("min ", "мін ") + fmt(bat.min(), 1) + t("  max ", "  макс ") + fmt(bat.max(), 1), style = MaterialTheme.typography.labelMedium)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
                val padL = 30.dp.toPx()
                val w = size.width - padL
                val h = size.height - 16.dp.toPx()
                fun y(kw: Double) = (h * (hi - kw) / (hi - lo)).toFloat()
                val now = history.lastOrNull()?.t ?: System.currentTimeMillis()
                fun x(t: Long) = padL + w * (1f - (now - t).toFloat() / WINDOW_MS)
                var g = lo
                while (g <= hi + 1e-9) {
                    drawLine(if (g == 0.0) axis else grid, Offset(padL, y(g)), Offset(size.width, y(g)), strokeWidth = if (g == 0.0) 2f else 1f)
                    val tl = measurer.measure("%.0f".format(g), labelStyle)
                    drawText(tl, topLeft = Offset(padL - tl.size.width - 4.dp.toPx(), y(g) - tl.size.height / 2))
                    g += step
                }
                for ((i, s) in history.withIndex()) {
                    if (i == 0) continue
                    val x0 = x(history[i - 1].t)
                    val x1 = x(s.t)
                    var acc = 0.0
                    for ((v, c) in listOf(s.aux to AUX, s.other to OTH, s.ac to AC, s.heat to HEAT)) {
                        if (v > 0) drawRect(c, Offset(x0, y(acc + v)), Size(x1 - x0, y(acc) - y(acc + v)))
                        acc += v
                    }
                }
                for (i in 1 until history.size) {
                    val a = history[i - 1].battery ?: continue
                    val b = history[i].battery ?: continue
                    drawLine(if (a < 0) REG else MOT, Offset(x(history[i - 1].t), y(a)), Offset(x(history[i].t), y(b)), strokeWidth = 3.dp.toPx())
                }
                for ((label, t) in listOf("−2:00" to now - WINDOW_MS, "−1:00" to now - WINDOW_MS / 2, t("now", "зараз") to now)) {
                    val tl = measurer.measure(label, labelStyle)
                    val cx = (x(t) - tl.size.width / 2).coerceIn(padL, size.width - tl.size.width)
                    drawText(tl, topLeft = Offset(cx, h + 2.dp.toPx()))
                }
            }
        }
    }
}

private fun rpm(v: Int?) = v?.let { "%,d".format(it).replace(',', ' ') } ?: "--"

private fun fmt(v: Double?, digits: Int): String = if (v == null) "--" else "%.${digits}f".format(v)
