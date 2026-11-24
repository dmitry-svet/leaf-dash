package com.leafdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leafdash.stats.Bucket
import com.leafdash.stats.Period
import com.leafdash.stats.Stats
import com.leafdash.stats.StatsView
import com.leafdash.trip.TripRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trip-log statistics: totals and a bar chart per day for the last week and
 * month, per week for the last year. Header toggles: bars km <-> kWh,
 * consumption kWh/100 km <-> km/kWh, and checkboxes for the consumption, SOH
 * and Hx lines ([view], persisted by the caller). Swipe right to go back.
 * Chart height follows the screen so all three cards fit; y grid lines at a
 * nice step, labelled on the left (unit on the top one); weekends / even
 * months shaded; consumption max/min on the far left, SOH and Hx on the right.
 */
@Composable
fun StatsScreen(trips: List<TripRecord>, view: StatsView, onView: (StatsView) -> Unit, onBack: () -> Unit) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val week = remember(trips, today) { Stats.week(trips, today, zone) }
    val month = remember(trips, today) { Stats.month(trips, today, zone) }
    val year = remember(trips, today) { Stats.year(trips, today, zone) }
    // everything but the bars is fixed-height (~510 dp for header, toggle
    // row, 3 card chromes with unit captions, paddings); the rest is split
    // between the three charts
    val chartH = ((LocalConfiguration.current.screenHeightDp - 510) / 3).dp.coerceIn(40.dp, 110.dp)
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 120.dp.toPx()
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx > threshold) onBack() },
                ) { _, delta -> dx += delta }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(t("‹ Back", "‹ Назад")) }
            Text(t("Statistics", "Статистика"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            UnitToggle(if (view.barsKwh) t("kWh", "кВт·г") else t("km", "км"), MaterialTheme.colorScheme.primary) {
                onView(view.copy(barsKwh = !view.barsKwh))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineCheck(t("Consumption", "Витрата"), CON_COLOR, view.consumption) { onView(view.copy(consumption = it)) }
            LineCheck("SOH", SOH_COLOR, view.soh) { onView(view.copy(soh = it)) }
            LineCheck("Hx", HX_COLOR, view.hx) { onView(view.copy(hx = it)) }
            Spacer(Modifier.weight(1f))
            UnitToggle(if (view.perKwh) t("km/kWh", "км/кВт·г") else t("kWh/100 km", "кВт·г/100 км"), CON_COLOR) {
                onView(view.copy(perKwh = !view.perKwh))
            }
        }
        PeriodCard(t("Last 7 days", "Останні 7 днів"), week, view, labelEvery = 1, chartH)
        PeriodCard(t("Last 30 days", "Останні 30 днів"), month, view, labelEvery = 5, chartH)
        PeriodCard(t("Last 52 weeks", "Останні 52 тижні"), year, view, labelEvery = 8, chartH)
    }
}

/** Outlined pill showing the current unit; tap flips it. */
@Composable
private fun UnitToggle(text: String, color: Color, onClick: () -> Unit) {
    Text(
        "$text ⇄",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .border(1.5.dp, color, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Checkbox in the line's colour plus its name; the whole pair is tappable. */
@Composable
private fun LineCheck(label: String, color: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clickable { onChange(!checked) }.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color))
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** Consumption in the selected unit: kWh/100 km or km/kWh. */
private fun consumption(whPerKm: Double, perKwh: Boolean) = if (perKwh) 1000.0 / whPerKm else whPerKm / 10.0

@Composable
private fun PeriodCard(title: String, p: Period, view: StatsView, labelEvery: Int, chartH: Dp) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                Total(t("km", "км"), "%.0f".format(p.totalKm), Modifier.weight(1f))
                Total(t("kWh", "кВт·г"), "%.1f".format(p.totalWh / 1000.0), Modifier.weight(1f))
                Total(
                    if (view.perKwh) t("km/kWh", "км/кВт·г") else t("kWh/100", "кВт·г/100"),
                    p.whPerKm?.let { "%.1f".format(consumption(it, view.perKwh)) } ?: "--",
                    Modifier.weight(1f),
                )
                Total(t("trips", "поїздок"), p.trips.toString(), Modifier.weight(1f))
            }
            BarChart(p.buckets, view, labelEvery, chartH, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Thin rounded bars from a common baseline, y grid at [Stats.gridStep] with
 * value labels in a left margin (unit as a caption above them; every other
 * label is skipped when the chart is too short for all), x labels under every
 * [labelEvery]th bucket. Shaded buckets get a darker background column.
 * SOH (upper band), Hx (lower band) and consumption (middle 70%) are drawn
 * as lines over the bars, each stretched over its own min..max and shown only
 * if enabled in [view]; SOH/Hx max/min sit in a right margin under a name
 * caption, consumption
 * max/min in a narrow left margin, pushed apart so they never overlap.
 */
@Composable
private fun BarChart(buckets: List<Bucket>, view: StatsView, labelEvery: Int, chartH: Dp, modifier: Modifier = Modifier) {
    val showKwh = view.barsKwh
    val values = buckets.map { if (showKwh) it.wh / 1000.0 else it.km }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val step = Stats.gridStep(max)
    val lines = Math.ceil(max / step).toInt()
    val top = lines * step
    val unit = if (showKwh) t("kWh", "кВт·г") else t("km", "км")
    val yLabels = (1..lines).map { i ->
        val v = i * step
        if (step < 1) "%.1f".format(v) else "%.0f".format(v)
    }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val shadeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val yLayouts = yLabels.map { measurer.measure(it, labelStyle, softWrap = false) }
    val unitLayout = measurer.measure(unit, labelStyle, softWrap = false)
    val series = listOfNotNull(
        if (view.soh) Series("SOH", buckets.map { it.soh }, SOH_COLOR, 0.55f, 0.92f) else null,
        if (view.hx) Series("Hx", buckets.map { it.hx }, HX_COLOR, 0.08f, 0.45f) else null,
    ).filter { it.points.isNotEmpty() }
    val con = if (view.consumption) {
        Series("", buckets.map { b -> b.whPerKm?.let { consumption(it, view.perKwh) } }, CON_COLOR, 0.15f, 0.85f)
            .takeIf { it.points.isNotEmpty() }
    } else null
    val leftStyle = labelStyle.copy(color = CON_COLOR, fontWeight = FontWeight.Bold)
    val leftLayouts = con?.let { c ->
        listOfNotNull(
            measurer.measure("%.1f".format(c.hi), leftStyle, softWrap = false),
            if (c.flat) null else measurer.measure("%.1f".format(c.lo), leftStyle, softWrap = false),
        )
    } ?: emptyList()
    // per series: name caption, max, min (min dropped for a flat line)
    val rightLayouts = series.map { s ->
        val st = labelStyle.copy(color = s.color)
        listOfNotNull(
            measurer.measure(s.name, st, softWrap = false),
            measurer.measure("%.1f".format(s.hi), st, softWrap = false),
            if (s.flat) null else measurer.measure("%.1f".format(s.lo), st, softWrap = false),
        )
    }
    val density = LocalDensity.current
    val axisW = with(density) { (yLayouts + unitLayout).maxOf { it.size.width }.toDp() } + 4.dp
    val rightW = if (rightLayouts.isEmpty()) 0.dp else with(density) { rightLayouts.flatten().maxOf { it.size.width }.toDp() } + 4.dp
    // room above the top grid line for the unit caption over the half-height top label
    val padTop = with(density) { (unitLayout.size.height + yLayouts[0].size.height / 2).toDp() }
    val leftW = if (leftLayouts.isEmpty()) 0.dp else with(density) { leftLayouts.maxOf { it.size.width }.toDp() } + 2.dp
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(chartH + padTop)) {
            val pad = padTop.toPx()
            val ph = size.height - pad                       // plot height below the caption room
            fun yOf(frac: Float) = size.height - frac * ph
            val x0 = (leftW + axisW).toPx()
            val x1 = size.width - rightW.toPx()
            val n = values.size
            val gap = 2.dp.toPx()
            val slot = (x1 - x0) / n
            val w = (slot - gap).coerceAtLeast(1f)
            val r = CornerRadius(minOf(4.dp.toPx(), w / 2))
            buckets.forEachIndexed { i, b ->
                if (b.shade) drawRect(shadeColor, Offset(x0 + i * slot, pad), Size(slot, ph))
            }
            val rowH = ph / lines
            val labelH = yLayouts[0].size.height
            val labelEveryY = Math.ceil(labelH * 1.1 / rowH).toInt().coerceAtLeast(1)
            for (i in 0..lines) {
                val y = size.height - i * rowH
                drawLine(gridColor, Offset(x0, y), Offset(x1, y), strokeWidth = 1.dp.toPx())
                if (i > 0 && (lines - i) % labelEveryY == 0) {
                    val l = yLayouts[i - 1]
                    drawText(l, topLeft = Offset(x0 - 4.dp.toPx() - l.size.width, y - l.size.height / 2))
                }
            }
            drawText(unitLayout, topLeft = Offset(x0 - 4.dp.toPx() - unitLayout.size.width, 0f))
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v / top * ph).toFloat()
                drawRoundRect(barColor, Offset(x0 + i * slot + gap / 2, size.height - h), Size(w, h), r)
            }
            // overlay lines; max/min labels collected first, then spread apart
            // layouts: optional name caption (sits right above max), max, min
            fun drawSeries(s: Series, layouts: List<TextLayoutResult>, named: Boolean, labels: MutableList<Pair<TextLayoutResult, Float>>) {
                val pts = s.points.map { (i, v) -> Offset(x0 + i * slot + slot / 2, yOf(s.frac(v))) }
                for (k in 1 until pts.size) drawLine(s.color, pts[k - 1], pts[k], strokeWidth = 2.dp.toPx())
                pts.forEach { drawCircle(s.color, 2.5.dp.toPx(), it) }
                val values = if (named) layouts.drop(1) else layouts
                val hiTop = yOf(s.frac(s.hi)) - values[0].size.height / 2
                if (named) labels += layouts[0] to hiTop - layouts[0].size.height
                labels += values[0] to hiTop
                if (!s.flat) labels += values[1] to yOf(s.frac(s.lo)) - values[1].size.height / 2
            }
            val right = mutableListOf<Pair<TextLayoutResult, Float>>()   // layout, desired top
            series.forEachIndexed { k, s -> drawSeries(s, rightLayouts[k], named = true, right) }
            spread(right, size.height).forEachIndexed { k, y -> drawText(right[k].first, topLeft = Offset(x1 + 4.dp.toPx(), y)) }
            if (con != null) {
                val left = mutableListOf<Pair<TextLayoutResult, Float>>()
                drawSeries(con, leftLayouts, named = false, left)
                spread(left, size.height).forEachIndexed { k, y -> drawText(left[k].first, topLeft = Offset(0f, y)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = rightW)) {
            Spacer(Modifier.width(leftW + axisW))
            buckets.forEachIndexed { i, b ->
                val last = i == buckets.lastIndex
                Text(
                    if (last || (buckets.lastIndex - i) % labelEvery == 0) b.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    // unbounded: a label wider than its slot overflows instead of clipping
                    modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/** Label tops for (layout, desired top) pairs ordered top-down, pushed apart so none overlap and all fit in [height]. */
private fun spread(labels: List<Pair<TextLayoutResult, Float>>, height: Float): FloatArray {
    val ys = labels.map { it.second }.toFloatArray()
    if (ys.isNotEmpty()) ys[0] = ys[0].coerceAtLeast(0f)
    for (k in 1 until ys.size) ys[k] = maxOf(ys[k], ys[k - 1] + labels[k - 1].first.size.height)
    for (k in ys.indices.reversed()) {
        val limit = if (k == ys.lastIndex) height - labels[k].first.size.height else ys[k + 1] - labels[k].first.size.height
        ys[k] = minOf(ys[k], limit)
    }
    return ys
}

private val CON_COLOR = Color(0xFFC8285A)
private val SOH_COLOR = Color(0xFFD9731A)
private val HX_COLOR = Color(0xFF0F8A8A)

/** An overlay line: non-null [values] stretched over [lo]..[hi] into the band [b0]..[b1] of the chart height. */
private class Series(val name: String, values: List<Double?>, val color: Color, private val b0: Float, private val b1: Float) {
    val points: List<Pair<Int, Double>> = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
    val lo = points.minOfOrNull { it.second } ?: 0.0
    val hi = points.maxOfOrNull { it.second } ?: 0.0
    val flat = hi == lo

    /** Fraction of the chart height (0 = baseline) for value [v]. */
    fun frac(v: Double): Float = if (flat) (b0 + b1) / 2 else b0 + (b1 - b0) * ((v - lo) / (hi - lo)).toFloat()
}
