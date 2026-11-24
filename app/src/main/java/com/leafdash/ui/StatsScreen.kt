package com.leafdash.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leafdash.stats.Bucket
import com.leafdash.stats.Period
import com.leafdash.stats.Stats
import com.leafdash.trip.TripRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trip-log statistics: totals and a bar chart per day for the last week and
 * month, per week for the last year. Tap a chart to flip km <-> kWh. Swipe
 * right to go back. Chart height follows the screen so all three cards fit;
 * y grid lines at a nice step, labelled on the left (unit on the top one);
 * weekends / even months shaded; SOH and Hx lines with max/min on the right.
 */
@Composable
fun StatsScreen(trips: List<TripRecord>, onBack: () -> Unit) {
    var showKwh by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val week = remember(trips, today) { Stats.week(trips, today, zone) }
    val month = remember(trips, today) { Stats.month(trips, today, zone) }
    val year = remember(trips, today) { Stats.year(trips, today, zone) }
    // everything but the bars is fixed-height (~420 dp for header, 3 card
    // chromes, paddings); the rest is split between the three charts
    val chartH = ((LocalConfiguration.current.screenHeightDp - 420) / 3).dp.coerceIn(40.dp, 110.dp)
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
            Text(
                t("tap chart: km/kWh", "тап: км/кВт·г"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
        PeriodCard(t("Last 7 days", "Останні 7 днів"), week, showKwh, labelEvery = 1, chartH) { showKwh = !showKwh }
        PeriodCard(t("Last 30 days", "Останні 30 днів"), month, showKwh, labelEvery = 5, chartH) { showKwh = !showKwh }
        PeriodCard(t("Last 52 weeks", "Останні 52 тижні"), year, showKwh, labelEvery = 8, chartH) { showKwh = !showKwh }
    }
}

@Composable
private fun PeriodCard(title: String, p: Period, showKwh: Boolean, labelEvery: Int, chartH: Dp, onFlip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                Total(t("km", "км"), "%.0f".format(p.totalKm), Modifier.weight(1f))
                Total(t("kWh", "кВт·г"), "%.1f".format(p.totalWh / 1000.0), Modifier.weight(1f))
                Total(t("Wh/km", "Вт·г/км"), p.whPerKm?.let { "%.0f".format(it) } ?: "--", Modifier.weight(1f))
                Total(t("trips", "поїздок"), p.trips.toString(), Modifier.weight(1f))
            }
            BarChart(p.buckets, showKwh, labelEvery, chartH, Modifier.fillMaxWidth().clickable(onClick = onFlip))
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
 * value labels in a left margin (unit on the top one; every other label is
 * skipped when the chart is too short for all), x labels under every
 * [labelEvery]th bucket. Shaded buckets get a darker background column.
 * SOH (upper band) and Hx (lower band) are drawn as lines over the bars, each
 * stretched over its own min..max; their max/min values sit in a right margin,
 * pushed apart so they never overlap.
 */
@Composable
private fun BarChart(buckets: List<Bucket>, showKwh: Boolean, labelEvery: Int, chartH: Dp, modifier: Modifier = Modifier) {
    val values = buckets.map { if (showKwh) it.wh / 1000.0 else it.km }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val step = Stats.gridStep(max)
    val lines = Math.ceil(max / step).toInt()
    val top = lines * step
    val unit = if (showKwh) t("kWh", "кВт·г") else t("km", "км")
    val yLabels = (1..lines).map { i ->
        val v = i * step
        (if (step < 1) "%.1f".format(v) else "%.0f".format(v)) + if (i == lines) " $unit" else ""
    }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val shadeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val yLayouts = yLabels.map { measurer.measure(it, labelStyle, softWrap = false) }
    val series = listOf(
        Series("SOH", buckets.map { it.soh }, SOH_COLOR, 0.55f, 0.92f),
        Series("Hx", buckets.map { it.hx }, HX_COLOR, 0.08f, 0.45f),
    ).filter { it.points.isNotEmpty() }
    val rightLayouts = series.flatMap { s ->
        listOfNotNull(
            measurer.measure("${s.name} ${"%.1f".format(s.hi)}", labelStyle.copy(color = s.color), softWrap = false),
            if (s.flat) null else measurer.measure("%.1f".format(s.lo), labelStyle.copy(color = s.color), softWrap = false),
        )
    }
    val density = LocalDensity.current
    val axisW = with(density) { yLayouts.maxOf { it.size.width }.toDp() } + 4.dp
    val rightW = if (rightLayouts.isEmpty()) 0.dp else with(density) { rightLayouts.maxOf { it.size.width }.toDp() } + 4.dp
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(chartH)) {
            val x0 = axisW.toPx()
            val x1 = size.width - rightW.toPx()
            val n = values.size
            val gap = 2.dp.toPx()
            val slot = (x1 - x0) / n
            val w = (slot - gap).coerceAtLeast(1f)
            val r = CornerRadius(minOf(4.dp.toPx(), w / 2))
            buckets.forEachIndexed { i, b ->
                if (b.shade) drawRect(shadeColor, Offset(x0 + i * slot, 0f), Size(slot, size.height))
            }
            val rowH = size.height / lines
            val labelH = yLayouts[0].size.height
            val labelEveryY = Math.ceil(labelH * 1.1 / rowH).toInt().coerceAtLeast(1)
            for (i in 0..lines) {
                val y = size.height - i * rowH
                drawLine(gridColor, Offset(x0, y), Offset(x1, y), strokeWidth = 1.dp.toPx())
                if (i > 0 && (lines - i) % labelEveryY == 0) {
                    val l = yLayouts[i - 1]
                    drawText(l, topLeft = Offset(x0 - 4.dp.toPx() - l.size.width, (y - l.size.height / 2).coerceAtLeast(0f)))
                }
            }
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v / top * size.height).toFloat()
                drawRoundRect(barColor, Offset(x0 + i * slot + gap / 2, size.height - h), Size(w, h), r)
            }
            // overlay lines; right labels collected first, then spread apart
            val labels = mutableListOf<Pair<TextLayoutResult, Float>>()   // layout, desired top
            var li = 0
            series.forEach { s ->
                val pts = s.points.map { (i, v) -> Offset(x0 + i * slot + slot / 2, size.height * (1 - s.frac(v))) }
                for (k in 1 until pts.size) drawLine(s.color, pts[k - 1], pts[k], strokeWidth = 2.dp.toPx())
                pts.forEach { drawCircle(s.color, 2.5.dp.toPx(), it) }
                val hiL = rightLayouts[li++]
                labels += hiL to size.height * (1 - s.frac(s.hi)) - hiL.size.height / 2
                if (!s.flat) {
                    val loL = rightLayouts[li++]
                    labels += loL to size.height * (1 - s.frac(s.lo)) - loL.size.height / 2
                }
            }
            val ys = labels.map { it.second }.toFloatArray()
            for (k in 1 until ys.size) ys[k] = maxOf(ys[k], ys[k - 1] + labels[k - 1].first.size.height)
            for (k in ys.indices.reversed()) {
                val limit = if (k == ys.lastIndex) size.height - labels[k].first.size.height else ys[k + 1] - labels[k].first.size.height
                ys[k] = minOf(ys[k], limit)
            }
            labels.forEachIndexed { k, (l, _) -> drawText(l, topLeft = Offset(x1 + 4.dp.toPx(), ys[k])) }
        }
        Row(Modifier.fillMaxWidth().padding(end = rightW)) {
            Spacer(Modifier.width(axisW))
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
