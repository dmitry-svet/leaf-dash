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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
 * y grid lines at a nice step, labelled on the left (unit on the top one).
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
 * [labelEvery]th bucket.
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
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val yLayouts = yLabels.map { measurer.measure(it, labelStyle, softWrap = false) }
    val axisW = with(LocalDensity.current) { yLayouts.maxOf { it.size.width }.toDp() } + 4.dp
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(chartH)) {
            val x0 = axisW.toPx()
            val n = values.size
            val gap = 2.dp.toPx()
            val slot = (size.width - x0) / n
            val w = (slot - gap).coerceAtLeast(1f)
            val r = CornerRadius(minOf(4.dp.toPx(), w / 2))
            val rowH = size.height / lines
            val labelH = yLayouts[0].size.height
            val labelEveryY = Math.ceil(labelH * 1.1 / rowH).toInt().coerceAtLeast(1)
            for (i in 0..lines) {
                val y = size.height - i * rowH
                drawLine(gridColor, Offset(x0, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
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
        }
        Row(Modifier.fillMaxWidth()) {
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
