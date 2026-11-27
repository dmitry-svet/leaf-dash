package com.leafdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leafdash.can.Load
import com.leafdash.can.LoadPoint
import com.leafdash.can.motorChartRange
import com.leafdash.poll.DashState

private val MOT = Color(0xFF6650A4)
private val REG = Color(0xFF2E8C46)
private val AC = Color(0xFF1E78C8)
private val HEAT = Color(0xFFD95A1A)
private val AUX = Color(0xFF0F8A8A)
private val OTH = Color(0xFF827C88)


/**
 * Live load: battery power split into motor (traction / regen), climate
 * (A/C + heater) and aux + other, as dials or bars ([bars], toggled by the
 * icon next to the title), plus trip average consumption, remaining kWh and
 * range. Motor and climate cards carry a 5 min chart in the background;
 * every card shows its energy this trip (traction and regen for the motor).
 * While open the caller puts the poller in fast mode; the header shows the
 * measured update interval. Swipe left to go back.
 */
@Composable
fun LoadScreen(state: DashState, bars: Boolean, onBars: (Boolean) -> Unit, onBack: () -> Unit) {
    val load = Load.of(state.leaf)
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
    }

    val tab = tabletLandscape()
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
            .then(if (tab) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(horizontal = if (tab) 8.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onBack) { Text(t("‹ Back", "‹ Назад")) }
            Text(t("Load", "Навантаження"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            StyleIcon(bars) { onBars(!bars) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(if (state.connected) REG else Color(0xFFC62828), CircleShape))
            Text(intervalMs?.let { t("%.1f s", "%.1f с").format(it / 1000) } ?: "--", style = MaterialTheme.typography.labelMedium)
        }
        if (!tab) TopPlates(state)
        val e = state.loadEnergy
        val climate = listOf(t("A/C", "Кондиціонер") to load.acKw, t("Heater", "Обігрів") to load.heaterKw)
        val other = listOf(t("Aux", "Допоміжні") to load.auxKw, t("Other", "Інше") to load.otherKw)
        val hist = state.loadHistory
        val climateChart: DrawScope.() -> Unit = {
            areaChart(hist, 0.0, 5.0, listOf<(LoadPoint) -> Double?>({ it.acKw }, { it.heaterKw })) { i, _ -> if (i == 0) AC else HEAT }
        }
        if (tab) {
            // landscape tablet: plates + motor on the left, climate over other on the right, no scroll
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopPlates(state)
                    if (bars) MotorBars(load, state, Modifier.fillMaxWidth().weight(1f), big = true)
                    else MotorDial(load, state, Modifier.fillMaxWidth().weight(1f), big = true)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val m = Modifier.fillMaxWidth().weight(1f)
                    if (bars) {
                        StackBar(t("Climate", "Клімат"), 5.0, climate, AC, HEAT, e.climateKwh, climateChart, m, big = true)
                        StackBar(t("Other", "Інше"), 2.0, other, AUX, OTH, e.otherKwh, null, m, big = true)
                    } else {
                        DialCard(t("Climate", "Клімат"), 5.0, climate, AC, HEAT, e.climateKwh, climateChart, m, big = true)
                        DialCard(t("Other", "Інше"), 2.0, other, AUX, OTH, e.otherKwh, null, m, big = true)
                    }
                }
            }
        } else if (bars) {
            MotorBars(load, state)
            StackBar(t("Climate", "Клімат"), 5.0, climate, AC, HEAT, e.climateKwh, climateChart)
            StackBar(t("Other", "Інше"), 2.0, other, AUX, OTH, e.otherKwh, null)
        } else {
            MotorDial(load, state)
            DialCard(t("Climate", "Клімат"), 5.0, climate, AC, HEAT, e.climateKwh, climateChart)
            DialCard(t("Other", "Інше"), 2.0, other, AUX, OTH, e.otherKwh, null)
        }
        val l = state.leaf
        Text(
            t("Battery ", "Батарея ") + fmt(load.batteryKw, 1) + t(" kW · ", " кВт · ") + fmt(l.packAmps, 1) + t(" A · ", " А · ") +
                fmt(l.packVolts, 1) + t(" V · 12V ", " В · 12V ") + fmt(l.aux12V, 1) + t(" V", " В"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Icon of the other style (dial when showing bars, bars when showing dials); tap switches. */
@Composable
private fun StyleIcon(bars: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(40.dp).clickable(onClick = onClick).padding(8.dp)) {
        val w = size.minDimension * 0.14f
        if (bars) {
            arc(c, ARC_START, ARC_SWEEP, w)
            val center = Offset(size.width / 2, size.height / 2)
            val a = Math.toRadians(300.0)
            val r = size.minDimension / 2 * 0.7f
            drawLine(c, center, center + Offset((r * Math.cos(a)).toFloat(), (r * Math.sin(a)).toFloat()), strokeWidth = w, cap = StrokeCap.Round)
        } else {
            for ((i, f) in listOf(0.9f, 0.55f, 0.75f).withIndex()) {
                val y = size.height * (0.2f + 0.3f * i)
                drawLine(c, Offset(w / 2, y), Offset(w / 2 + (size.width - w) * f, y), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}

/** Trip average consumption, remaining energy and range. */
@Composable
private fun TopPlates(state: DashState) {
    val usable = state.leaf.kwhRemaining?.let { (it - state.reserveKwh).coerceAtLeast(0.0) }
    val refEff = (state.lifetime.kwhPer100?.takeIf { state.lifetime.km >= 1.0 } ?: state.avgKwhPer100).coerceIn(5.0, 60.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Plate(t("Trip avg", "Витрата поїздки"), fmt(state.trip.kwhPer100, 1), t("kWh/100", "кВт·г/100"), MOT, Modifier.weight(1f))
        Plate(t("Remaining", "Залишок"), fmt(usable, 1), t("kWh", "кВт·г"), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        Plate(t("Range", "Запас"), fmt(usable?.let { it / refEff * 100 }, 0), t("km", "км"), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun Plate(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
private fun MotorDial(load: Load, state: DashState, modifier: Modifier = Modifier.fillMaxWidth(), big: Boolean = false) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val lo = -30.0
    val hi = 80.0
    val v = load.motorKw
    val (cLo, cHi) = motorChartRange(state.loadHistory)
    ChartCard({ motorChart(state.loadHistory, cLo, cHi) }, motorLabel(cLo, cHi), modifier, fill = big) {
        Column(Modifier.padding(12.dp).then(if (big) Modifier.fillMaxHeight() else Modifier)) {
            Text(t("Motor", "Мотор"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.then(if (big) Modifier.weight(1f) else Modifier), verticalAlignment = Alignment.CenterVertically) {
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
                        Text(fmt(v, 1), fontSize = if (big) 72.sp else 52.sp, fontWeight = FontWeight.Bold, color = if ((v ?: 0.0) < 0) REG else MOT)
                        Text(t("kW", "кВт"), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(if (big) 20.dp else 10.dp)) {
                    Stat(t("RPM", "Оберти"), rpm(state.leaf.motorRpm), t("rpm", "об/хв"), big = big)
                    Stat(t("Traction, trip", "Тяга за поїздку"), fmt(state.loadEnergy.tractionKwh, 2), t("kWh", "кВт·г"), MOT, big)
                    Stat(t("Regen, trip", "Рекуп. за поїздку"), fmt(state.loadEnergy.regenKwh, 2), t("kWh", "кВт·г"), REG, big)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color = MaterialTheme.colorScheme.onSurface, big: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = if (big) 44.sp else 30.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1)
        }
    }
}

/** One dial, two stacked segments from 0 (total in the centre); values and trip energy beside it. */
@Composable
private fun DialCard(
    title: String, hi: Double, parts: List<Pair<String, Double?>>, c1: Color, c2: Color, tripKwh: Double, chart: (DrawScope.() -> Unit)?,
    modifier: Modifier = Modifier.fillMaxWidth(), big: Boolean = false,
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val total = parts.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.sum()
    ChartCard(chart, modifier = modifier, fill = big) {
        Row(Modifier.padding(12.dp).then(if (big) Modifier.fillMaxHeight() else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = big), contentAlignment = Alignment.Center) {
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
                    Text(fmt(total, 2), fontSize = if (big) 44.sp else 34.sp, fontWeight = FontWeight.Bold)
                    Text(t("kW", "кВт"), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((i, p) in parts.withIndex()) {
                    if (big) LegendAbove(p.first, p.second, if (i == 0) c1 else c2, big = true)
                    else Legend(p.first, p.second, if (i == 0) c1 else c2, 26)
                }
                Stat(t("This trip", "За поїздку"), fmt(tripKwh, 2), t("kWh", "кВт·г"), big = big)
            }
        }
    }
}

/** Colour box and label on one line, the value under it (narrow columns). */
@Composable
private fun LegendAbove(label: String, v: Double?, color: Color, big: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
        }
        Text(fmt(v, 2), fontSize = if (big) 44.sp else 30.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
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
private fun MotorBars(load: Load, state: DashState, modifier: Modifier = Modifier.fillMaxWidth(), big: Boolean = false) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val zeroC = MaterialTheme.colorScheme.onSurface
    val lo = -30.0
    val hi = 80.0
    val v = load.motorKw
    val (cLo, cHi) = motorChartRange(state.loadHistory)
    Card(modifier) {
        ChartBox(
            { motorChart(state.loadHistory, cLo, cHi) }, motorLabel(cLo, cHi), Alignment.BottomStart,
            Modifier.fillMaxWidth().then(if (big) Modifier.weight(1f) else Modifier),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Motor", "Мотор"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(v, 1), fontSize = if (big) 96.sp else 64.sp, fontWeight = FontWeight.Bold, color = if ((v ?: 0.0) < 0) REG else MOT)
                    Text(
                        if ((v ?: 0.0) < 0) t(" kW regen", " кВт рекуп.") else t(" kW traction", " кВт тяга"),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp).weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rpm(state.leaf.motorRpm), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(t("rpm", "об/хв"), style = MaterialTheme.typography.labelMedium)
                        Text(fmt(state.loadEnergy.tractionKwh, 2), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MOT)
                        Text(t("kWh traction, trip", "кВт·г тяга за поїздку"), style = MaterialTheme.typography.labelMedium, color = MOT)
                        Text(fmt(state.loadEnergy.regenKwh, 2), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = REG)
                        Text(t("kWh regen, trip", "кВт·г рекуп. за поїздку"), style = MaterialTheme.typography.labelMedium, color = REG)
                    }
                }
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun StackBar(
    title: String, hi: Double, parts: List<Pair<String, Double?>>, c1: Color, c2: Color, tripKwh: Double, chart: (DrawScope.() -> Unit)?,
    modifier: Modifier = Modifier.fillMaxWidth(), big: Boolean = false,
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val total = parts.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.sum()
    ChartCard(chart, modifier = modifier, fill = big) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(if (big) 12.dp else 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(t("kW ", "кВт "), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
                Text(fmt(total, 2), fontSize = if (big) 56.sp else 40.sp, fontWeight = FontWeight.Bold)
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
                    Box(Modifier.weight(1f)) { LegendAbove(p.first, p.second, if (i == 0) c1 else c2, big) }
                }
                Box(Modifier.weight(1f)) { Stat(t("This trip", "За поїздку"), fmt(tripKwh, 2), t("kWh", "кВт·г"), big = big) }
            }
        }
    }
}

/** Card with an optional chart drawn behind its content (the card clips it to its shape). */
@Composable
private fun ChartCard(
    chart: (DrawScope.() -> Unit)?, label: String = t("5 min", "5 хв"), modifier: Modifier = Modifier.fillMaxWidth(),
    fill: Boolean = false, content: @Composable () -> Unit,
) {
    Card(modifier) { ChartBox(chart, label, Alignment.BottomEnd, if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), content) }
}

/** [content] over an optional [chart] filling the same box, with a small [label] at [labelAt]. */
@Composable
private fun ChartBox(
    chart: (DrawScope.() -> Unit)?, label: String, labelAt: Alignment, modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        if (chart != null) {
            Canvas(Modifier.matchParentSize()) { chart() }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(labelAt).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        content()
    }
}

/** Chart caption for the motor: window and the auto scale's ends. */
private fun motorLabel(lo: Double, hi: Double) = t("5 min", "5 хв") + "  ↑" + "%.0f".format(hi) + "  ↓" + "%.0f".format(-lo) + t(" kW", " кВт")

private const val CHART_MS = 300_000L
private const val CHART_GAP_MS = 30_000L
private const val CHART_ALPHA = 0.25f

/**
 * Stacked area chart of the last 5 min over the whole draw area, [lo]..[hi]
 * bottom to top; layer values stack from 0 (a negative first layer goes
 * below 0). Time runs to the newest point at the right edge; gaps over
 * [CHART_GAP_MS] are left empty.
 */
private fun DrawScope.areaChart(pts: List<LoadPoint>, lo: Double, hi: Double, layers: List<(LoadPoint) -> Double?>, color: (Int, Double) -> Color) {
    if (pts.size < 2) return
    val end = pts.last().tMs
    fun x(t: Long) = size.width * (1f - (end - t).toFloat() / CHART_MS)
    fun y(v: Double) = size.height * (1f - ((v.coerceIn(lo, hi) - lo) / (hi - lo)).toFloat())
    for (k in 0 until pts.size - 1) {
        val p = pts[k]
        val q = pts[k + 1]
        if (q.tMs - p.tMs > CHART_GAP_MS) continue
        var accP = 0.0
        var accQ = 0.0
        for ((i, f) in layers.withIndex()) {
            val a = f(p) ?: 0.0
            val b = f(q) ?: 0.0
            val path = Path().apply {
                moveTo(x(p.tMs), y(accP)); lineTo(x(q.tMs), y(accQ))
                lineTo(x(q.tMs), y(accQ + b)); lineTo(x(p.tMs), y(accP + a)); close()
            }
            drawPath(path, color(i, a).copy(alpha = CHART_ALPHA))
            accP += a
            accQ += b
        }
    }
    if (lo < 0) drawLine(Color.Gray.copy(alpha = 0.5f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 2f)
}

private fun DrawScope.motorChart(pts: List<LoadPoint>, lo: Double, hi: Double) =
    areaChart(pts, lo, hi, listOf<(LoadPoint) -> Double?>({ it.motorKw })) { _, v -> if (v < 0) REG else MOT }

private fun rpm(v: Int?) = v?.let { "%,d".format(it).replace(',', ' ') } ?: "--"

private fun fmt(v: Double?, digits: Int): String = if (v == null) "--" else "%.${digits}f".format(v)
