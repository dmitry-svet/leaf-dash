package com.leafdash.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leafdash.stats.Bucket
import com.leafdash.stats.Period
import com.leafdash.stats.Stats
import com.leafdash.trip.TripRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Trip-log statistics: totals and a bar chart per day for the last week and
 * month, per week for the last year. Tap a chart to flip km <-> kWh.
 */
@Composable
fun StatsScreen(trips: List<TripRecord>, onBack: () -> Unit) {
    var showKwh by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val week = remember(trips, today) { Stats.week(trips, today, zone) }
    val month = remember(trips, today) { Stats.month(trips, today, zone) }
    val year = remember(trips, today) { Stats.year(trips, today, zone) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(t("‹ Back", "‹ Назад")) }
            Text(t("Statistics", "Статистика"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        PeriodCard(t("Last 7 days", "Останні 7 днів"), week, showKwh, labelEvery = 1) { showKwh = !showKwh }
        PeriodCard(t("Last 30 days", "Останні 30 днів"), month, showKwh, labelEvery = 5) { showKwh = !showKwh }
        PeriodCard(t("Last 52 weeks", "Останні 52 тижні"), year, showKwh, labelEvery = 8) { showKwh = !showKwh }
        Text(
            t("Tap a chart to switch km / kWh. App-connected trips only.",
              "Тисни на графік, щоб перемкнути км / кВт·год. Лише поїздки з підключеною апкою."),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PeriodCard(title: String, p: Period, showKwh: Boolean, labelEvery: Int, onFlip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Total(t("km", "км"), "%.0f".format(p.totalKm), Modifier.weight(1f))
                Total(t("kWh", "кВт·г"), "%.1f".format(p.totalWh / 1000.0), Modifier.weight(1f))
                Total(t("Wh/km", "Вт·г/км"), p.whPerKm?.let { "%.0f".format(it) } ?: "--", Modifier.weight(1f))
                Total(t("trips", "поїздок"), p.trips.toString(), Modifier.weight(1f))
            }
            BarChart(p.buckets, showKwh, labelEvery, Modifier.fillMaxWidth().clickable(onClick = onFlip))
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Thin rounded bars from a common baseline; x labels under every [labelEvery]th bucket. */
@Composable
private fun BarChart(buckets: List<Bucket>, showKwh: Boolean, labelEvery: Int, modifier: Modifier = Modifier) {
    val values = buckets.map { if (showKwh) it.wh / 1000.0 else it.km }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val unit = if (showKwh) t("kWh", "кВт·г") else t("km", "км")
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Text(
            "${if (showKwh) "%.1f".format(max) else "%.0f".format(max)} $unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size
            val gap = 2.dp.toPx()
            val slot = size.width / n
            val w = (slot - gap).coerceAtLeast(1f)
            val r = CornerRadius(minOf(4.dp.toPx(), w / 2))
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v / max * size.height).toFloat()
                drawRoundRect(barColor, Offset(i * slot + gap / 2, size.height - h), Size(w, h), r)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { i, b ->
                val last = i == buckets.lastIndex
                Text(
                    if (last || (buckets.lastIndex - i) % labelEvery == 0) b.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
